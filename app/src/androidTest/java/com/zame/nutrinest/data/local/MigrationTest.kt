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
}
