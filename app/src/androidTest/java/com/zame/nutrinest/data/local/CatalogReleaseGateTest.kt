package com.zame.nutrinest.data.local

import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.data.repository.LocalIngredientRepository
import com.zame.nutrinest.domain.nutrition.ConversionError
import com.zame.nutrinest.domain.nutrition.ConversionResult
import com.zame.nutrinest.domain.nutrition.IngredientQuantityConversions
import com.zame.nutrinest.domain.nutrition.RecipeMeasure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogReleaseGateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @get:Rule val helper = MigrationTestHelper(
        instrumentation, NutriNestDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory()
    )

    @Test fun importedV8WithInterruptedLibraryBootstrapRecoversWithoutChangingHistoricalRows() = runBlocking {
        val legacyName = "gate-v8-installed.db"
        val sourceName = "gate-fresh-source.db"
        val foodTables = listOf("ingredient_categories", "shopping_aisles", "nutrient_defs", "ingredients",
            "ingredient_nutrients", "ingredient_portions", "ingredient_aliases", "ingredient_quality_issues")
        val projections = mapOf("recipe_ingredients" to "recipeId,ingredientId,grams,quantityUnit")
        context.deleteDatabase(legacyName)
        context.deleteDatabase(sourceName)
        try {
            lateinit var before: Map<String, List<String>>
            val source = NutriNestDatabase.build(context, sourceName)
            try {
                val fresh = source.openHelper.writableDatabase
                helper.createDatabase(legacyName, 8).use { legacy ->
                    legacy.beginTransaction()
                    try {
                        foodTables.forEach { transferTable(fresh, legacy, it) }
                        transferTable(fresh, legacy, "food_dataset_meta", "WHERE key NOT LIKE 'built_in_recipe_library_%'")
                        val firstRecipe = CatalogTestFixtures.scalar(fresh, "SELECT MIN(id) FROM recipes")
                        transferTable(fresh, legacy, "recipes", "WHERE id=$firstRecipe")
                        transferTable(fresh, legacy, "recipe_ingredients", "WHERE recipeId=$firstRecipe")
                        transferTable(fresh, legacy, "recipe_steps", "WHERE recipeId=$firstRecipe")
                        // The first saved book entry has personal edits. The rest never seeded.
                        legacy.execSQL("UPDATE recipe_ingredients SET grams=grams+0.125")
                        legacy.execSQL("UPDATE recipe_steps SET text='Historical step',imagePath='old-step.jpg'")
                        legacy.execSQL("UPDATE recipes SET isFavorite=1,coverImagePath='old-cover.jpg'")
                        CatalogTestFixtures.seed(legacy, 8)
                        // Old, hidden source record referenced by both a recipe and a checked list.
                        legacy.execSQL("""
                            INSERT INTO ingredients(id,name,foodType,kcal,carbs,fats,protein,isCustom,sourceKey)
                            VALUES(1002494,'Historical hidden food','DAIRY',64.25,7.1,3.4,1.1,0,'BEDCA:2494')
                        """.trimIndent())
                        legacy.execSQL("INSERT INTO recipe_ingredients VALUES(21,1002494,41.125,'UNKNOWN_OLD_UNIT')")
                        legacy.execSQL("INSERT INTO shopping_items VALUES(44,41,1002494,41.125,1)")
                        // An impostor with a familiar source key must never be selected for new books.
                        legacy.execSQL("UPDATE ingredients SET sourceKey='BEDCA:758' WHERE id=12")
                        legacy.execSQL("UPDATE ingredient_nutrients SET value=0 WHERE ingredientId=11 AND tag='FIXTURE'")
                        before = CatalogTestFixtures.snapshot(legacy, CatalogTestFixtures.tables(legacy), projections)
                        legacy.setTransactionSuccessful()
                    } finally {
                        legacy.endTransaction()
                    }
                }
            } finally {
                source.close()
            }

            repeat(2) {
                val migrated = NutriNestDatabase.build(context, legacyName)
                try {
                    val db = migrated.openHelper.writableDatabase
                    val current = CatalogTestFixtures.snapshot(db, before.keys.toList(), projections)
                    before.forEach { (table, rows) ->
                        assertTrue("Lost or altered v8 rows in $table", current.getValue(table).toHashSet().containsAll(rows))
                    }
                    db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
                    db.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
                    assertEquals(250L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM recipes WHERE remoteId LIKE 'builtin:%'"))
                    assertEquals(0L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM recipe_ingredients WHERE ingredientId=12"))
                    val ingredientRepo = LocalIngredientRepository(migrated)
                    val hidden = ingredientRepo.observeById(1002494L).first()!!
                    assertFalse(ingredientRepo.observeAll().first().any { it.id == hidden.id })
                    val oldRecipe = migrated.recipeDao().observeIngredientsOf(21L).first()
                    val historical = oldRecipe.single { it.ingredient.id == hidden.id }
                    assertEquals(41.125, historical.grams, 0.0)
                    assertEquals(hidden.nutrition.forGrams(41.125), historical.nutrition)
                    val purchased = migrated.menuDao().observeShoppingItems(1000L).first().single { it.item.ingredientId == hidden.id }
                    assertTrue(purchased.item.purchased)
                    assertEquals(41.125, purchased.item.grams, 0.0)

                    val incomplete = oldRecipe.single { it.ingredient.id == 11L }.ingredient
                    assertTrue(incomplete.hasIncompleteData)
                    assertEquals(1L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM ingredient_nutrients WHERE ingredientId=11 AND value=0"))
                    assertEquals(1L, CatalogTestFixtures.scalar(db, "SELECT COUNT(*) FROM ingredient_nutrients WHERE ingredientId=11"))
                    assertEquals(ConversionResult.Failure(ConversionError.MISSING_DENSITY),
                        IngredientQuantityConversions.toGrams(100.0, "MILLILITRE", hidden))
                    assertFalse(ingredientRepo.observeRecipeMeasures().first().containsKey(11L))
                    assertNull(RecipeMeasure.GRAMS.enter(Double.NaN))
                } finally {
                    migrated.close()
                }
            }
        } finally {
            context.deleteDatabase(legacyName)
            context.deleteDatabase(sourceName)
        }
    }

    private fun transferTable(source: SupportSQLiteDatabase, dest: SupportSQLiteDatabase, table: String, where: String = "") {
        val columns = dest.query("PRAGMA table_info(`$table`)").use { c ->
            buildList { while (c.moveToNext()) add("`${c.getString(1)}`") }
        }
        source.query("SELECT ${columns.joinToString()} FROM `$table` $where").use { c ->
            val sql = "INSERT INTO `$table` (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})"
            dest.compileStatement(sql).use { stmt ->
                while (c.moveToNext()) {
                    for (i in columns.indices) {
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> stmt.bindNull(i + 1)
                            Cursor.FIELD_TYPE_INTEGER -> stmt.bindLong(i + 1, c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> stmt.bindDouble(i + 1, c.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> stmt.bindString(i + 1, c.getString(i))
                            else -> stmt.bindBlob(i + 1, c.getBlob(i))
                        }
                    }
                    stmt.executeInsert()
                }
            }
        }
    }
}
