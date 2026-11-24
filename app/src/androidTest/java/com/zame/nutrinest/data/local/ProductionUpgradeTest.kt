package com.zame.nutrinest.data.local

import android.util.Log
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ProductionUpgradeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val factory = FrameworkSQLiteOpenHelperFactory()
    @get:Rule val helper = MigrationTestHelper(instrumentation, NutriNestDatabase::class.java, emptyList(), factory)

    @Test fun fromV1() = upgrade(1)
    @Test fun fromV2() = upgrade(2)
    @Test fun fromV3() = upgrade(3)
    @Test fun fromV4() = upgrade(4)
    @Test fun fromV5() = upgrade(5)
    @Test fun fromV6() = upgrade(6)
    @Test fun fromV7() = upgrade(7)
    @Test fun fromV8() = upgrade(8)
    @Test fun fromV9() = upgrade(9)
    @Test fun fromV10() = upgrade(10)

    // Restrict comparisons to the installation's own rows, allowing the shipped catalogue to grow.
    private val historical = mapOf(
        "recipes" to "id=21", "recipe_steps" to "recipeId=21",
        "recipe_ingredients" to "recipeId=21", "profiles" to "id=31", "weight_entries" to "profileId=31",
        "weekly_menus" to "id=41", "planned_meals" to "menuId=41", "meal_portions" to "plannedMealId=42",
        "shopping_items" to "menuId=41", "intake_entries" to "profileId=31", "profile_meal_plans" to "profileId=31",
        "ingredient_nutrients" to "ingredientId=11", "ingredient_portions" to "ingredientId=11",
        "ingredient_aliases" to "ingredientId=11", "ingredient_quality_issues" to "ingredientId=11",
        "food_dataset_meta" to "key='fixture'"
    )

    private fun upgrade(version: Int) {
        val name = "qa24-v$version.db"
        val file = context.getDatabasePath(name)
        context.deleteDatabase(name)
        val archiveDir = File(context.filesDir, "qa24-originals").apply { mkdirs() }
        val archive = File(archiveDir, name)
        val projections = mutableMapOf<String, String>()
        val predicates = historical.toMutableMap()
        lateinit var before: Map<String, List<String>>
        lateinit var originalNutrition: List<List<Long>>
        fun prepare(db: SupportSQLiteDatabase) {
            seed(db, version)
            val ids = db.query("SELECT id FROM ingredients ORDER BY id").use { c ->
                buildList { while (c.moveToNext()) add(c.getLong(0)) }.joinToString(",")
            }
            predicates["ingredients"] = "id IN ($ids)"
            predicates["ingredient_canonical_map"] = "ingredient_id IN ($ids)"
            val tables = CatalogTestFixtures.tables(db).filter { it in predicates }
            for (table in tables) {
                projections[table] = db.query("PRAGMA table_info(`$table`)").use { c ->
                    buildList { while (c.moveToNext()) add("`${c.getString(1)}`") }.joinToString(",")
                }
            }
            before = CatalogTestFixtures.snapshot(db, tables, projections, predicates)
            originalNutrition = nutrition(db)
            healthy(db, version)
        }

        try {
            if (version >= 4) helper.createDatabase(name, version).use { prepare(it) }
            else {
                val ddl = instrumentation.context.assets.open("upgrade-early-schemas.json").bufferedReader().use {
                    JSONArray(it.readText())
                }
                val callback = object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        repeat(version) { stage ->
                            val sql = ddl.getJSONArray(stage)
                            repeat(sql.length()) { db.execSQL(sql.getString(it)) }
                        }
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                        error("Unexpected fixture upgrade")
                }
                val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(callback).build()
                factory.create(config).use { prepare(it.writableDatabase) }
            }
            // Copy only a closed, checkpointed SQLite file. Keep the original outside databases/.
            for (suffix in listOf("-wal", "-journal")) check(File(file.path + suffix).length() == 0L)
            val initialHash = fileHash(file)
            file.copyTo(archive, overwrite = true)
            assertEquals(initialHash, fileHash(archive))
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { output ->
                val exported = File(output, "qa24-originals").apply { mkdirs() }
                archive.copyTo(File(exported, name), overwrite = true)
            }
            context.deleteDatabase(name)
            archive.copyTo(file)
            val reference = helper.createDatabase("qa24-reference.db", 12).use { indexes(it) }
            var firstCounts = emptyMap<String, Long>()
            var firstMap = emptyList<String>()
            repeat(2) { pass ->
                val room = NutriNestDatabase.build(context, name)
                try {
                    val db = room.openHelper.writableDatabase
                    assertTrue("import deferred on v$version", FoodDatabaseImporter.importIfNeeded(context, db))
                    healthy(db, 12)
                    assertEquals(1L, scalar(db, "PRAGMA foreign_keys"))
                    assertEquals(reference, indexes(db))
                    val after = CatalogTestFixtures.snapshot(db, before.keys.toList(), projections, predicates)
                    for (table in before.keys) assertEquals("v$version/$pass/$table", before[table], after[table])
                    assertEquals(originalNutrition, nutrition(db))
                    if (version in 4..6) assertEquals(1L, scalar(db, "SELECT count(*) FROM meal_portions WHERE plannedMealId=42 AND baseFactor=factor"))
                    if (version < 10) assertEquals(0L, scalar(db, "SELECT count(*) FROM recipe_ingredients WHERE recipeId=21 AND (originalQuantity IS NOT NULL OR quantityEvidenceId IS NOT NULL)"))
                    CanonicalCatalogStore.validate(db)
                    assertEquals(250L, scalar(db, "SELECT count(DISTINCT remoteId) FROM recipes WHERE remoteId LIKE 'builtin:%'"))
                    val counts = CatalogTestFixtures.tables(db).associateWith { scalar(db, "SELECT count(*) FROM `$it`") }
                    assertEquals(251L, counts.getValue("recipes"))
                    assertEquals(counts["ingredients"], counts["ingredient_canonical_map"])
                    assertEquals(0L, counts.getValue("price_snapshot_cache"))
                    val mappings = CatalogTestFixtures.snapshot(db, listOf("ingredient_canonical_map")).values.single()
                    if (pass == 0) {
                        firstCounts = counts
                        firstMap = mappings
                        CatalogTestFixtures.rejected(db, "INSERT INTO recipe_ingredients(recipeId,ingredientId,grams) VALUES(-1,11,1)")
                        CatalogTestFixtures.rejected(db, "UPDATE ingredient_canonical_map SET record_sha256='invalid' WHERE ingredient_id=11")
                    } else {
                        assertEquals(firstCounts, counts)
                        assertEquals(firstMap, mappings)
                        val digest = CatalogFingerprint.sha256(after.toSortedMap().entries.joinToString("") { (table, rows) ->
                            CatalogFingerprint.token(table) + rows.joinToString("") { CatalogFingerprint.token(it) }
                        })
                        Log.i("UpgradeQA", "v$version rows=${after.values.sumOf { it.size }} ingredients=${counts["ingredients"]} critical=$digest original=$initialHash")
                    }
                } finally { room.close() }
            }
            assertEquals(initialHash, fileHash(archive))
        } finally { context.deleteDatabase(name) }
    }

    private fun seed(db: SupportSQLiteDatabase, version: Int) {
        CatalogTestFixtures.seed(db, version)
        db.execSQL("UPDATE ingredients SET imagePath='qa/food.jpg' WHERE id=11")
        db.execSQL("UPDATE recipes SET coverImagePath='qa/cover.jpg'")
        db.execSQL("UPDATE recipe_steps SET imagePath='qa/step.jpg'")
        if (version >= 2) db.execSQL("UPDATE profiles SET dietaryPreferences='vegetarian',allergies='fixture-allergen'")
        if (version >= 5) {
            // An edited historical panel for an active ID and a referenced deprecated BEDCA ID.
            for (source in listOf(42, 2173)) {
                val id = 1_000_000 + source
                db.execSQL("INSERT INTO ingredients(id,name,foodType,kcal,carbs,fats,protein,isCustom,sourceKey,isFavorite) " +
                    "VALUES(?,?,'MISC',75.25,9.5,1.25,3.5,0,?,1)", arrayOf<Any>(id, "Fixture $source", "BEDCA:$source"))
                db.execSQL("INSERT INTO recipe_ingredients(recipeId,ingredientId,grams) VALUES(21,?,41.125)", arrayOf(id))
                db.execSQL("INSERT INTO shopping_items(menuId,ingredientId,grams,purchased) VALUES(41,?,53.625,1)", arrayOf(id))
            }
        }
        if (version == 10) db.execSQL("UPDATE recipe_ingredients SET originalQuantity=0.12815277,quantityEvidenceId='fixture-density-v1' WHERE ingredientId=11")
        if (version >= 9) {
            CanonicalCatalogSchema.installValidationTriggers(db)
            CanonicalCatalogStore.backfill(db)
        }
    }

    private fun healthy(db: SupportSQLiteDatabase, version: Int) {
        assertEquals(version.toLong(), scalar(db, "PRAGMA user_version"))
        db.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)); assertFalse(it.moveToNext()) }
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    private fun nutrition(db: SupportSQLiteDatabase) = db.query("""
        SELECT kcal*grams/100.0, carbs*grams/100.0, fats*grams/100.0, protein*grams/100.0
        FROM recipe_ingredients r JOIN ingredients i ON i.id=r.ingredientId
        WHERE r.recipeId=21 ORDER BY i.id
    """.trimIndent()).use { c -> buildList { while (c.moveToNext()) add(List(4) { c.getDouble(it).toRawBits() }) } }

    private fun indexes(db: SupportSQLiteDatabase) =
        db.query("SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%' ORDER BY name").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }

    private fun scalar(db: SupportSQLiteDatabase, sql: String) = CatalogTestFixtures.scalar(db, sql)
    private fun fileHash(file: File) = CatalogFingerprint.hex(MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
}
