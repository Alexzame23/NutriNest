package com.zame.nutrinest.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.repository.LocalIngredientRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CuratedCatalogTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: NutriNestDatabase
    private val name = "curated-catalog-test.db"
    private val db get() = database.openHelper.writableDatabase
    private val rules get() = context.assets.open(CuratedCatalog.RULES_ASSET).bufferedReader().use { it.readText() }
    private val sourceHash get() = JSONObject(rules).getString("sourceSha256")
    private val version get() = JSONObject(rules).getString("datasetVersion")

    @Before fun setup() {
        context.deleteDatabase(name)
        database = NutriNestDatabase.build(context, name)
        db // Force production callback, import, curation and seeder.
    }

    @After fun cleanup() {
        database.close()
        context.deleteDatabase(name)
    }

    private fun manifest(): JSONObject = db.query("SELECT manifest_json FROM catalog_revision WHERE rules_version='practical-catalog-v1'").use {
        assertTrue(it.moveToFirst()); JSONObject(it.getString(0))
    }

    private fun logicalHash(): String = CatalogFingerprint.sha256(
        CatalogTestFixtures.snapshot(db, CatalogTestFixtures.tables(db)).toSortedMap().toString())

    @Test fun twoTransformationsHaveIdenticalCountsAndLogicalHashes() {
        val first = logicalHash()
        val counts = CuratedCatalog.statistics(db).toString()
        repeat(2) {
            CuratedCatalog.applyRules(db, rules, sourceHash, version)
            assertTrue(FoodDatabaseImporter.importIfNeeded(context, db))
            assertEquals(first, logicalHash())
            assertEquals(counts, CuratedCatalog.statistics(db).toString())
        }
        val manifest = manifest()
        assertEquals(923, manifest.getJSONArray("decisions").length())
        assertEquals(923, manifest.getJSONObject("before").getJSONObject("states").getInt("VISIBLE"))
        val after = manifest.getJSONObject("after")
        assertEquals(820, after.getJSONObject("states").getInt("VISIBLE"))
        assertEquals(99, after.getJSONObject("states").getInt("SECONDARY"))
        assertEquals(4, after.getJSONObject("states").getInt("REVIEW_REQUIRED"))
        assertEquals(923, after.getInt("bilingual"))
        // First transformation also retained every source cell, including aliases/portions.
        val legacyHashes = manifest.getJSONObject("legacyTableHashes")
        CanonicalCatalogStore.legacySnapshot(db).filterKeys {
            it !in setOf("food_dataset_meta", "recipes", "recipe_steps", "recipe_ingredients")
        }.forEach { (table, hash) -> assertEquals(table, legacyHashes.getString(table), hash) }
        CanonicalCatalogStore.validate(db)
        assertEquals(1L, CatalogTestFixtures.scalar(db, "PRAGMA foreign_keys"))
        db.query("PRAGMA integrity_check").use { it.moveToFirst(); assertEquals("ok", it.getString(0)) }
        File(context.filesDir, "curation-verification.json").writeText(JSONObject()
            .put("before", manifest.getJSONObject("before")).put("after", CuratedCatalog.statistics(db))
            .put("firstLogicalSha256", first).put("secondLogicalSha256", logicalHash())
            .put("repeatCount", 2).put("foreignKeys", "ok").put("integrity", "ok")
            .put("manifestBytes", manifest.toString().toByteArray(Charsets.UTF_8).size).toString(2))
    }

    @Test fun everyBuiltInRecipeKeepsExactBedcaIdsGramsAndNutrition() {
        assertEquals(250, BuiltInRecipeLibrary.recipes.size)
        var links = 0
        BuiltInRecipeLibrary.recipes.forEach { recipe ->
            val recipeId = db.query("SELECT id FROM recipes WHERE remoteId=?",
                arrayOf(RecipeBooks.BUILT_IN_ID_PREFIX + recipe.key)).use { assertTrue(it.moveToFirst()); it.getLong(0) }
            recipe.ingredients.forEach { ingredient ->
                db.query("SELECT ingredientId,grams,quantityUnit FROM recipe_ingredients WHERE recipeId=? AND ingredientId=?",
                    arrayOf<Any>(recipeId, 1_000_000L + ingredient.bedcaFoodId)).use {
                    assertTrue(recipe.key, it.moveToFirst())
                    assertEquals(ingredient.grams, it.getDouble(1), 0.0)
                    assertEquals(ingredient.quantityUnit, it.getString(2))
                }
                links++
            }
        }
        assertEquals(1421, links)
        val before = CatalogTestFixtures.snapshot(db, listOf("recipe_ingredients", "ingredients", "ingredient_nutrients", "ingredient_aliases"))
        CuratedCatalog.applyRules(db, rules, sourceHash, version)
        BuiltInRecipeSeeder.seedIfNeeded(db)
        assertEquals(before, CatalogTestFixtures.snapshot(db, before.keys.toList()))
    }

    @Test fun variantsStaySeparateAndSelectorsUseBilingualLabelsAndRanks() = runBlocking {
        val repo = LocalIngredientRepository(database)
        val all = repo.observeAll().first()
        assertEquals(923, all.size)
        assertEquals(all.map { it.catalogRank }.sorted(), all.map { it.catalogRank })
        assertEquals(0, all.single { it.id == 1_000_758L }.catalogRank)
        listOf(1173, 2125, 762).forEach { food ->
            val item = all.single { it.id == 1_000_000L + food }
            assertEquals(1, item.catalogRank)
            assertNotEquals(database.canonicalCatalogDao().mapping(1_000_758)!!.canonicalId,
                database.canonicalCatalogDao().mapping(item.id)!!.canonicalId)
        }
        listOf(1725, 1793, 2433, 2434).forEach { food ->
            assertEquals(2, all.single { it.id == 1_000_000L + food }.catalogRank)
        }
        assertEquals("Clara de huevo de gallina, cruda", all.single { it.id == 1_001_173L }.displayName(true))
        assertTrue(repo.search("whole chicken egg").first().any { it.id == 1_000_758L })
        assertTrue(repo.search("puerco").first().isNotEmpty()) // Source alias retained.
        assertEquals(923L, CatalogTestFixtures.scalar(db, "SELECT COUNT(DISTINCT canonical_id) FROM ingredient_canonical_map"))
        assertEquals(0L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM ingredient_catalog_status WHERE replacement_ingredient_id IS NOT NULL"))
    }

    @Test fun favoritesCustomEditsAliasesAndPurchasedItemsSurviveReprocessing() = runBlocking {
        val dao = database.ingredientDao()
        val repo = LocalIngredientRepository(database)
        val original = dao.findById(1_001_173)!!
        dao.setFavorite(original.id, true)
        val editedId = repo.save(original.copy(name = "My egg white", isFavorite = true,
            nutrition = original.nutrition.copy(kcal = 123.456)))
        db.execSQL("INSERT INTO weekly_menus (id,weekStart,slotsSpec,createdAt) VALUES (90001,1000,'LUNCH:1',123)")
        db.execSQL("INSERT INTO shopping_items (menuId,ingredientId,grams,purchased) VALUES (90001,1001173,123.456,1)")
        val before = logicalHash()
        repeat(2) { assertTrue(FoodDatabaseImporter.importIfNeeded(context, db)) }
        assertEquals(before, logicalHash())
        assertEquals(123.456, dao.findById(editedId)!!.nutrition.kcal, 0.0)
        assertEquals("HIDDEN", database.canonicalCatalogDao().status(original.id)!!.state)
        assertTrue(dao.observeFavorites().first().any { it.id == original.id })
        assertTrue(dao.observeFiltered("", IngredientCategories.ALL, 1, 0).first().any { it.id == original.id })
        assertTrue(dao.observeHistorical().first().any { it.id == original.id })
        assertEquals(1L, CatalogTestFixtures.scalar(db, "SELECT purchased FROM shopping_items WHERE menuId=90001"))
        CanonicalCatalogStore.validate(db)
    }

    @Test fun manualDecisionSurvivesReopenAndStaleReviewIsRejected() = runBlocking {
        val id = 1_001_725L
        val oldRevision = database.canonicalCatalogDao().status(id)!!.revisionId
        CuratedCatalog.review(db, id, oldRevision, "SECONDARY", "Test reviewer",
            "Fixture decision: keep separate; no equivalence with toasted bread")
        val before = logicalHash()
        database.close()
        database = NutriNestDatabase.build(context, name)
        assertEquals(before, logicalHash())
        assertEquals("MANUAL_REVIEW", database.canonicalCatalogDao().status(id)!!.reasonCode)
        expectFailure { CuratedCatalog.review(db, id, oldRevision, "VISIBLE", "Test reviewer", "Stale") }
        assertEquals(before, logicalHash())
    }

    @Test fun changedDatasetVersionCompositionAndNewRecordAreDeferredAtomically() {
        val file = File.createTempFile("update-fixture", ".db", context.cacheDir)
        try {
            context.assets.open("nutrinest_foods.db").use { input -> file.outputStream().use { input.copyTo(it) } }
            // Same version, different composition: must not bypass the hash check.
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("UPDATE food_macros SET kcal=kcal+1.25 WHERE food_id=1173")
            }
            val before = logicalHash()
            expectFailure { FoodDatabaseImporter.importStaged(context, db, file) }
            assertEquals(before, logicalHash())
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { asset ->
                asset.execSQL("UPDATE db_metadata SET value='2099.test' WHERE key='dataset_version'")
                val columns = asset.rawQuery("PRAGMA table_info(foods)", null).use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(1)) }
                }
                val select = columns.joinToString { if (it == "id") "99999" else "`$it`" }
                asset.execSQL("INSERT INTO foods SELECT $select FROM foods WHERE id=1173")
                asset.execSQL("UPDATE foods SET name_es='Fixture new food' WHERE id=99999")
            }
            expectFailure { FoodDatabaseImporter.importStaged(context, db, file) }
            assertEquals(before, logicalHash())
            assertEquals(0L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM ingredients WHERE sourceKey='BEDCA:99999'"))
            assertTrue(FoodDatabaseImporter.importIfNeeded(context, db))
        } finally { file.delete() }
    }

    @Test fun changedRulesWithoutVersionIncrementAreRejectedWithoutWrites() {
        val before = logicalHash()
        val changed = JSONObject(rules).put("equivalencePolicy", "changed").toString()
        expectFailure { CuratedCatalog.applyRules(db, changed, sourceHash, version) }
        assertEquals(before, logicalHash())
        expectFailure { CuratedCatalog.applyRules(db, rules, "0".repeat(64), version) }
        assertEquals(before, logicalHash())
    }

    @Test fun exactLegacySnapshotIsCuratedButEditedLegacyDataIsPreserved() {
        legacyFixture { legacy ->
            legacy.execSQL("UPDATE ingredients SET kcal=kcal+0.123 WHERE id=1002125")
            CanonicalCatalogStore.backfill(legacy)
            val before = CanonicalCatalogStore.legacySnapshot(legacy)
            CuratedCatalog.applyRules(legacy, rules, sourceHash, version)
            assertEquals(before, CanonicalCatalogStore.legacySnapshot(legacy))
            assertEquals(1L, CatalogTestFixtures.scalar(legacy, "SELECT COUNT(*) FROM ingredient_catalog_status WHERE state='SECONDARY'"))
            assertEquals(1L, CatalogTestFixtures.scalar(legacy, "SELECT COUNT(*) FROM ingredient_catalog_status WHERE reason_code='LEGACY_VISIBILITY'"))
            assertEquals(2L, CatalogTestFixtures.scalar(legacy, "SELECT COUNT(*) FROM ingredient_canonical_map WHERE source_namespace LIKE 'LEGACY:%'"))
            val first = CatalogTestFixtures.snapshot(legacy, CatalogTestFixtures.tables(legacy))
            CuratedCatalog.applyRules(legacy, rules, sourceHash, version)
            assertEquals(first, CatalogTestFixtures.snapshot(legacy, CatalogTestFixtures.tables(legacy)))
        }
    }

    @Test fun partialPublicationFailureRollsBackEveryMetadataWrite() {
        legacyFixture { legacy ->
            CanonicalCatalogStore.backfill(legacy)
            val original = CatalogTestFixtures.snapshot(legacy, CatalogTestFixtures.tables(legacy))
            val invalid = JSONObject(rules)
            val entries = invalid.getJSONArray("entries")
            for (i in 0 until entries.length()) {
                if (entries.getJSONObject(i).getLong("foodId") == 2125L) entries.getJSONObject(i).put("state", "INVALID")
            }
            expectFailure { CuratedCatalog.applyRules(legacy, invalid.toString(), sourceHash, version) }
            assertEquals(original, CatalogTestFixtures.snapshot(legacy, CatalogTestFixtures.tables(legacy)))
            CuratedCatalog.applyRules(legacy, rules, sourceHash, version)
            assertEquals(2L, CatalogTestFixtures.scalar(legacy, "SELECT COUNT(*) FROM ingredient_catalog_status WHERE state='SECONDARY'"))
        }
    }

    private fun legacyFixture(action: (androidx.sqlite.db.SupportSQLiteDatabase) -> Unit) {
        val memory = Room.inMemoryDatabaseBuilder(context, NutriNestDatabase::class.java).build()
        try {
            val legacy = memory.openHelper.writableDatabase
            CanonicalCatalogSchema.installValidationTriggers(legacy)
            val columns = mapOf("ingredient_nutrients" to "tag,value", "ingredient_portions" to "labelEn,labelEs,grams,portionType,isDefault",
                "ingredient_aliases" to "alias,aliasNorm,lang", "ingredient_quality_issues" to "code,severity,detail")
            val entries = JSONObject(rules).getJSONArray("entries")
            for (i in 0 until entries.length()) {
                val rule = entries.getJSONObject(i)
                val food = rule.getLong("foodId")
                if (food !in setOf(1173L, 2125L)) continue
                val snapshot = rule.getJSONObject("snapshot")
                val attributes = snapshot.getJSONObject("attributes")
                val names = attributes.keys().asSequence().toList()
                fun nullable(value: Any): Any? = if (value == JSONObject.NULL) null else value
                legacy.execSQL("INSERT INTO ingredients (id,${names.joinToString()}) VALUES (?,${names.joinToString { "?" }})",
                    (listOf<Any?>(food + 1_000_000) + names.map { nullable(attributes.get(it)) }).toTypedArray())
                // Nutrient definitions are required only by the source panel FK.
                val children = snapshot.getJSONObject("children")
                val nutrients = children.getJSONArray("ingredient_nutrients")
                for (n in 0 until nutrients.length()) {
                    val tag = nutrients.getJSONArray(n).getString(0)
                    legacy.execSQL("INSERT OR IGNORE INTO nutrient_defs (tag,nameEn,nameEs,unit,nutrientGroup,isPrimary,sortOrder) VALUES (?,?,?,'fixture','fixture',0,0)", arrayOf(tag, tag, tag))
                }
                columns.forEach { (table, fields) ->
                    val rows = children.getJSONArray(table)
                    for (r in 0 until rows.length()) {
                        val row = rows.getJSONArray(r)
                        legacy.execSQL("INSERT INTO $table (ingredientId,$fields) VALUES (?,${(0 until row.length()).joinToString { "?" }})",
                            (listOf<Any?>(food + 1_000_000) + (0 until row.length()).map { nullable(row.get(it)) }).toTypedArray())
                    }
                }
            }
            action(legacy)
        } finally { memory.close() }
    }

    private fun expectFailure(action: () -> Unit) {
        try { action(); fail("Expected conservative rejection") }
        catch (_: IllegalStateException) { /* explicit deferral/optimistic review conflict */ }
    }
}
