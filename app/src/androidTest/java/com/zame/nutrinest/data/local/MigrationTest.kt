package com.zame.nutrinest.data.local

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
 * The v4 → v5 migration runs against databases that already hold the user's
 * recipes, so what matters is that nothing of theirs is lost on the way.
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
}
