package com.zame.nutrinest.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.data.local.entity.IngredientCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The migrations run against databases that already hold the user's recipes
 * and menus, so what matters is that nothing of theirs is lost on the way.
 *
 * [MigrationTestHelper] validates the resulting schema against the exported
 * one, which catches any drift between the hand-written DDL in
 * [NutriNestDatabase] and what Room expects from the entities.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test.db"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NutriNestDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate4To5_keepsRecipesAndTheirIngredients() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO ingredients (id, name, foodType, kcal, carbs, fats, protein, isCustom)
                VALUES (1, 'Egg', 'Eggs', 155.0, 1.1, 11.0, 13.0, 0),
                       (2, 'Potato', 'Vegetables', 77.0, 17.5, 0.1, 2.0, 0),
                       (3, 'Olive oil', 'Oils & Fats', 884.0, 0.0, 100.0, 0.0, 0),
                       (4, 'Abuela''s sofrito', 'Spices', 120.0, 8.0, 9.0, 1.5, 1)
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO recipes (id, name, category, createdAt, isFavorite) " +
                    "VALUES (1, 'Tortilla', 'Main Dish', 0, 0)"
            )
            db.execSQL("INSERT INTO recipe_ingredients VALUES (1, 1, 120.0), (1, 2, 300.0)")
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 5, true, NutriNestDatabase.MIGRATION_4_5
        )

        db.query("SELECT COUNT(*) FROM recipe_ingredients").use {
            it.moveToFirst()
            assertEquals("the recipe kept both of its ingredients", 2, it.getInt(0))
        }
        db.query("SELECT name FROM ingredients ORDER BY id").use { cursor ->
            val names = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            assertTrue("ingredients used by a recipe survive", "Egg" in names)
            assertTrue("ingredients used by a recipe survive", "Potato" in names)
            assertTrue("the user's own ingredient survives", "Abuela's sofrito" in names)
            assertTrue("an unused starter ingredient is dropped", "Olive oil" !in names)
        }
    }

    @Test
    fun migrate4To5_mapsLegacyFoodTypesOntoCategories() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                """
                INSERT INTO ingredients (id, name, foodType, kcal, carbs, fats, protein, isCustom)
                VALUES (1, 'Spaghetti', 'Pasta', 371.0, 74.7, 1.5, 13.0, 1),
                       (2, 'Almonds', 'Nuts & Seeds', 579.0, 21.6, 49.9, 21.2, 1),
                       (3, 'Cumin', 'Spices', 375.0, 44.2, 22.3, 17.8, 1)
                """.trimIndent()
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 5, true, NutriNestDatabase.MIGRATION_4_5
        )

        db.query("SELECT name, foodType FROM ingredients ORDER BY id").use { cursor ->
            val mapped = buildMap {
                while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1))
            }
            assertEquals(IngredientCategories.CEREALS, mapped["Spaghetti"])
            assertEquals(IngredientCategories.LEGUMES, mapped["Almonds"])
            assertEquals(IngredientCategories.MISC, mapped["Cumin"])
        }
    }

    @Test
    fun migrate4To5_backfillsTheSearchableName() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO ingredients (id, name, foodType, kcal, carbs, fats, protein, isCustom) " +
                    "VALUES (1, 'Plátano de Canarias', 'Fruit', 89.0, 22.8, 0.3, 1.1, 1)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 5, true, NutriNestDatabase.MIGRATION_4_5
        )

        db.query("SELECT nameNorm FROM ingredients WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("platano de canarias", it.getString(0))
        }
    }

    /**
     * v5 → v7 rebuilds `planned_meals` so a free slot can have no recipe. The
     * rebuild is the risky part: portions hang off those rows by id, so the
     * ids have to survive it along with everything the user planned.
     */
    @Test
    fun migrate5To7_keepsMenusLocksPortionsAndTheShoppingList() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO ingredients (id, name, foodType, kcal, carbs, fats, protein, " +
                    "isCustom, nameNorm) VALUES (1, 'Egg', 'EGGS', 155.0, 1.1, 11.0, 13.0, 0, 'egg')"
            )
            db.execSQL(
                "INSERT INTO recipes (id, name, category, createdAt, isFavorite) " +
                    "VALUES (1, 'Tortilla', 'Main Dish', 0, 0)"
            )
            db.execSQL(
                """
                INSERT INTO profiles (id, name, dateOfBirth, sex, heightCm, weightKg,
                                      activityLevel, goal, formula, unitSystem, colorIndex,
                                      createdAt, weightLossRate)
                VALUES (1, 'Ana', 0, 'FEMALE', 165.0, 60.0, 'MODERATE', 'MAINTAIN',
                        'AUTO', 'METRIC', 0, 0, 'MODERATE')
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO weekly_menus (id, weekStart, slotsSpec, createdAt) " +
                    "VALUES (1, 1000, 'BREAKFAST:0.3,LUNCH:0.4,DINNER:0.3', 0)"
            )
            db.execSQL(
                "INSERT INTO planned_meals (id, menuId, dayOfWeek, slot, recipeId, isLocked, " +
                    "isCheatMeal) VALUES (1, 1, 0, 'LUNCH', 1, 1, 0), (2, 1, 1, 'DINNER', 1, 0, 0)"
            )
            db.execSQL("INSERT INTO meal_portions VALUES (1, 1, 1.5), (2, 1, 0.8)")
            db.execSQL(
                "INSERT INTO shopping_items (id, menuId, ingredientId, grams, purchased) " +
                    "VALUES (1, 1, 1, 500.0, 1)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 7, false,
            NutriNestDatabase.MIGRATION_5_6,
            NutriNestDatabase.MIGRATION_6_7
        )

        db.query(
            "SELECT id, isLocked, course, profileId, isFree FROM planned_meals ORDER BY id"
        ).use { cursor ->
            val rows = buildList {
                while (cursor.moveToNext()) {
                    add(
                        listOf(
                            cursor.getLong(0), cursor.getInt(1), cursor.getString(2),
                            cursor.isNull(3), cursor.getInt(4)
                        )
                    )
                }
            }
            assertEquals("both meals survived", 2, rows.size)
            assertEquals("with their ids, so portions still match", 1L, rows[0][0])
            assertEquals("the lock survived", 1, rows[0][1])
            assertTrue("everything planned so far stays shared", rows.all { it[3] == true })
            assertTrue("as a single course", rows.all { it[2] == "SINGLE" })
            assertTrue("and none of it became free", rows.all { it[4] == 0 })
        }

        db.query("SELECT factor, baseFactor FROM meal_portions ORDER BY plannedMealId").use { c ->
            c.moveToFirst()
            assertEquals(1.5, c.getDouble(0), 0.0001)
            assertEquals("the planned factor is backfilled", 1.5, c.getDouble(1), 0.0001)
        }

        db.query("SELECT purchased FROM shopping_items WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("the shopping list keeps its ticks", 1, it.getInt(0))
        }

        db.query("SELECT scope, coursesSpec FROM weekly_menus WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("SHARED", it.getString(0))
            assertEquals("", it.getString(1))
        }

        // The row shape v5 could not hold: a free slot with no recipe at all.
        db.execSQL(
            "INSERT INTO planned_meals (menuId, dayOfWeek, slot, recipeId, isLocked, " +
                "isCheatMeal, course, profileId, isFree) " +
                "VALUES (1, 2, 'DINNER', NULL, 0, 0, 'SINGLE', 1, 1)"
        )
        db.query("SELECT COUNT(*) FROM planned_meals WHERE recipeId IS NULL").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun migrate7To8_keepsRecipesAndAddsOptionalSpanishText() {
        helper.createDatabase(TEST_DB, 7).use { db ->
            db.execSQL(
                "INSERT INTO recipes (id, name, category, folder, coverImageCrop, " +
                    "createdAt, isFavorite) VALUES " +
                    "(1, 'Family soup', 'Main Dish', 'My recipes', 'CENTER', 10, 1)"
            )
            db.execSQL(
                "INSERT INTO recipe_steps (id, recipeId, position, text) " +
                    "VALUES (1, 1, 0, 'Simmer gently')"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 8, true, NutriNestDatabase.MIGRATION_7_8
        )

        db.query("SELECT name, nameEs, isFavorite FROM recipes WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("Family soup", it.getString(0))
            assertTrue("a user recipe needs no fabricated translation", it.isNull(1))
            assertEquals(1, it.getInt(2))
        }
        db.query("SELECT text, textEs FROM recipe_steps WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("Simmer gently", it.getString(0))
            assertTrue("a user step needs no fabricated translation", it.isNull(1))
        }

        // The new nullable columns can hold the bundled bilingual library.
        db.execSQL("UPDATE recipes SET nameEs = 'Sopa familiar' WHERE id = 1")
        db.execSQL("UPDATE recipe_steps SET textEs = 'Cuece suavemente' WHERE id = 1")
        db.query("SELECT nameEs FROM recipes WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("Sopa familiar", it.getString(0))
        }
    }
    @Test
    fun migrate8To9_preservesEveryHistoricalCellAndIsIdempotent() {
        lateinit var tables: List<String>
        lateinit var before: Map<String, List<String>>
        helper.createDatabase("catalog-v8.db", 8).use { db ->
            CatalogTestFixtures.seed(db, 8)
            tables = CatalogTestFixtures.tables(db)
            before = CatalogTestFixtures.snapshot(db, tables)
        }
        helper.runMigrationsAndValidate("catalog-v8.db", 9, true, NutriNestDatabase.MIGRATION_8_9).use { db ->
            assertEquals(before, CatalogTestFixtures.snapshot(db, tables))
            assertEquals(2L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM ingredient_canonical_map"))
            assertEquals(0L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM ingredient_canonical_map WHERE source_head_slot IS NOT NULL"))
            assertEquals(0L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM conversion_evidence"))
            val allTables = CatalogTestFixtures.tables(db)
            val first = CatalogTestFixtures.snapshot(db, allTables)
            CanonicalCatalogStore.backfill(db)
            CanonicalCatalogStore.backfill(db)
            assertEquals(first, CatalogTestFixtures.snapshot(db, allTables))
            db.query("PRAGMA foreign_key_check").use { assertTrue(!it.moveToFirst()) }
            CatalogTestFixtures.constraints(db)
        }
    }

    @Test
    fun migrate9To10_preservesGramsUnitsNutritionAndMedia() {
        val name = "recipe-quantities-v9.db"
        lateinit var tables: List<String>
        lateinit var historical: Map<String, List<String>>
        helper.createDatabase(name, 9).use { db ->
            CatalogTestFixtures.seed(db, 9)
            db.execSQL("INSERT INTO recipe_ingredients (recipeId,ingredientId,grams,quantityUnit) VALUES (21,12,53.52,'OLD_UNKNOWN')")
            db.execSQL("UPDATE recipes SET coverImagePath='cover.jpg'")
            db.execSQL("UPDATE recipe_steps SET imagePath='step.jpg'")
            tables = CatalogTestFixtures.tables(db).filterNot { it == "recipe_ingredients" }
            historical = CatalogTestFixtures.snapshot(db, tables)
        }
        helper.runMigrationsAndValidate(name, 10, true, NutriNestDatabase.MIGRATION_9_10).use { db ->
            assertEquals(historical, CatalogTestFixtures.snapshot(db, tables))
            db.query("""
                SELECT ri.ingredientId, grams, quantityUnit, originalQuantity, quantityEvidenceId, kcal * grams / 100.0
                FROM recipe_ingredients ri JOIN ingredients i ON i.id=ri.ingredientId
                WHERE recipeId=21 ORDER BY ri.ingredientId
            """.trimIndent()).use {
                assertTrue(it.moveToNext())
                assertEquals(11L, it.getLong(0))
                assertEquals(137.123456789, it.getDouble(1), 0.0)
                assertEquals("LITRE", it.getString(2))
                assertTrue(it.isNull(3) && it.isNull(4))
                assertEquals(123.45 * 137.123456789 / 100.0, it.getDouble(5), 1e-12)
                assertTrue(it.moveToNext())
                assertEquals(12L, it.getLong(0))
                assertEquals(53.52, it.getDouble(1), 0.0)
                assertEquals("OLD_UNKNOWN", it.getString(2))
                assertTrue(it.isNull(3) && it.isNull(4))
                assertEquals(91.0 * 53.52 / 100.0, it.getDouble(5), 1e-12)
                assertTrue(!it.moveToNext())
            }
            db.query("PRAGMA foreign_key_check").use { assertTrue(!it.moveToFirst()) }
        }
    }

    @Test
    fun allExportedOldVersionsReach9WithUserReferencesIntact() {
        for (version in 4..8) {
            val name = "catalog-from-$version.db"
            helper.createDatabase(name, version).use { CatalogTestFixtures.seed(it, version) }
            val migrations = arrayOf(NutriNestDatabase.MIGRATION_4_5, NutriNestDatabase.MIGRATION_5_6,
                NutriNestDatabase.MIGRATION_6_7, NutriNestDatabase.MIGRATION_7_8, NutriNestDatabase.MIGRATION_8_9)
            helper.runMigrationsAndValidate(name, 9, true, *migrations).use { db ->
                assertEquals(137.123456789, db.query("SELECT grams FROM recipe_ingredients WHERE recipeId=21").use { it.moveToFirst(); it.getDouble(0) }, 0.0)
                assertEquals(543.210987, db.query("SELECT grams FROM shopping_items WHERE id=43").use { it.moveToFirst(); it.getDouble(0) }, 0.0)
                assertEquals(1L, CatalogTestFixtures.scalar(db, "SELECT purchased FROM shopping_items WHERE id=43"))
                assertEquals(1L, CatalogTestFixtures.scalar(db, "SELECT isFavorite FROM recipes WHERE id=21"))
                assertEquals(1.375, db.query("SELECT factor FROM meal_portions WHERE plannedMealId=42").use { it.moveToFirst(); it.getDouble(0) }, 0.0)
                val expectedBase = if (version >= 7) 0.925 else 1.375
                assertEquals(expectedBase, db.query("SELECT baseFactor FROM meal_portions WHERE plannedMealId=42").use { it.moveToFirst(); it.getDouble(0) }, 0.0)
                assertEquals(1L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM weight_entries WHERE id=32 AND profileId=31"))
                if (version >= 5) assertEquals(1L, CatalogTestFixtures.scalar(db, "SELECT isFavorite FROM ingredients WHERE id=11"))
                if (version >= 7) assertEquals(1L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM intake_entries WHERE id=51"))
                db.query("PRAGMA foreign_key_check").use { assertTrue(!it.moveToFirst()) }
            }
        }
    }

    @Test
    fun freshRoomDatabaseHasTheSameCatalogConstraints() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = androidx.room.Room.inMemoryDatabaseBuilder(context, NutriNestDatabase::class.java)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    CanonicalCatalogSchema.installValidationTriggers(db)
                }
            }).build()
        try {
                val db = room.openHelper.writableDatabase
                CatalogTestFixtures.seed(db, 9)
                CanonicalCatalogStore.backfill(db)
                CatalogTestFixtures.constraints(db)
        } finally { room.close() }
    }

    @Test
    fun failedMigrationRollsBackOnlyTheDisposableFixture() {
        helper.createDatabase("catalog-rollback.db", 8).use { db ->
            CatalogTestFixtures.seed(db, 8)
            val tables = CatalogTestFixtures.tables(db)
            val before = CatalogTestFixtures.snapshot(db, tables)
            db.beginTransaction()
            try {
                NutriNestDatabase.MIGRATION_8_9.migrate(db)
                // Simulate interruption before Room commits; deliberately do not mark successful.
            } finally { db.endTransaction() }
            assertEquals(before, CatalogTestFixtures.snapshot(db, tables))
            assertEquals(0L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM sqlite_master WHERE name='catalog_revision'"))
        }
    }

    @Test
    fun migratedInstallationSurvivesRealOnOpenImporterAndSeeder() {
        val name = "catalog-real-reopen.db"
        lateinit var tables: List<String>
        lateinit var before: Map<String, List<String>>
        helper.createDatabase(name, 8).use { db ->
            CatalogTestFixtures.seed(db, 8)
            tables = CatalogTestFixtures.tables(db).filterNot { it == "recipe_ingredients" }
            before = CatalogTestFixtures.snapshot(db, tables)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = NutriNestDatabase.build(context, name)
        try {
            val db = room.openHelper.writableDatabase
            assertEquals(1L, CatalogTestFixtures.scalar(db, "PRAGMA foreign_keys"))
            val after = CatalogTestFixtures.snapshot(db, tables)
            before.forEach { (table, rows) ->
                assertTrue("onOpen changed $table", after.getValue(table).containsAll(rows))
            }
            db.query("""
                SELECT ingredientId, grams, quantityUnit, originalQuantity, quantityEvidenceId
                FROM recipe_ingredients WHERE recipeId=21
            """.trimIndent()).use {
                assertTrue(it.moveToFirst())
                assertEquals(11L, it.getLong(0))
                assertEquals(137.123456789, it.getDouble(1), 0.0)
                assertEquals("LITRE", it.getString(2))
                assertTrue(it.isNull(3) && it.isNull(4))
                assertTrue(!it.moveToNext())
            }
            CanonicalCatalogStore.validate(db)
        } finally { room.close() }
        val reopened = NutriNestDatabase.build(context, name)
        try { CanonicalCatalogStore.validate(reopened.openHelper.writableDatabase) }
        finally { reopened.close() }
    }

}
