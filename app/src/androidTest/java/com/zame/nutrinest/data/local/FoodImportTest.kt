package com.zame.nutrinest.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.data.local.entity.FoodDatasetMetaEntity
import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.local.entity.RecipeCategories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real import against the real asset on a device.
 *
 * The offline harness in `tools/verify_migration.py` covers the SQL; this
 * covers what only Android can: reading the asset out of the APK, running the
 * import from Room's `onOpen` callback, and the DAO queries built on top.
 */
@RunWith(AndroidJUnit4::class)
class FoodImportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: NutriNestDatabase

    @Before
    fun setUp() {
        // A fresh file each run, so the import is exercised from nothing.
        context.deleteDatabase(DB_NAME)
        database = NutriNestDatabase.build(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun importsTheWholeDataset() = runBlocking {
        val ingredients = database.ingredientDao().count()
        assertTrue("expected the full catalogue, got $ingredients", ingredients > 900)

        assertEquals(12, database.foodReferenceDao().observeCategories().first().size)
        assertEquals(14, database.foodReferenceDao().observeAisles().first().size)
        assertEquals(81, database.foodReferenceDao().nutrientDefs().size)
    }

    @Test
    fun everyIngredientHasAKnownCategory() = runBlocking {
        val ingredients = database.ingredientDao().observeAll().first()
        val unknown = ingredients.map { it.categoryKey }.toSet() - IngredientCategories.ALL.toSet()
        assertTrue("unexpected categories: $unknown", unknown.isEmpty())
    }

    @Test
    fun searchMatchesBothLanguagesAndSynonyms() = runBlocking {
        val dao = database.ingredientDao()
        val all = IngredientCategories.ALL

        // A Spanish name, an English one, and a synonym found in neither.
        listOf("aceite", "olive", "puerco").forEach { term ->
            val hits = dao.observeFiltered(term, all, 0, 0).first()
            assertTrue("'$term' found nothing", hits.isNotEmpty())
        }

        // Accents are optional: the stored names are accent-stripped.
        val withAccent = dao.observeFiltered("platano", all, 0, 0).first()
        assertTrue("accent-free search should still match", withAccent.isNotEmpty())
    }

    @Test
    fun categoryFilterNarrowsTheResults() = runBlocking {
        val dao = database.ingredientDao()
        val fatsAndOils = dao.observeFiltered("", listOf(IngredientCategories.FATS_OILS), 0, 0).first()
        assertTrue(fatsAndOils.isNotEmpty())
        assertTrue(
            "the filter must not leak other categories",
            fatsAndOils.all { it.categoryKey == IngredientCategories.FATS_OILS }
        )
    }

    @Test
    fun importedFoodsCarryTheirNutrientPanel() = runBlocking {
        val dao = database.ingredientDao()
        val wellCovered = dao.observeAll().first()
            .first { it.sourceKey != null && it.completenessPct > 70 }

        val nutrients = dao.observeNutrients(wellCovered.id).first()
        assertTrue("expected a nutrient panel", nutrients.size > 5)
        assertTrue("nutrients should carry their unit", nutrients.all { it.unit.isNotBlank() })
        assertTrue(
            "nutrients should be labelled in both languages",
            nutrients.all { it.nameEn.isNotBlank() && it.nameEs.isNotBlank() }
        )
    }

    @Test
    fun householdPortionsAreImported() = runBlocking {
        val dao = database.ingredientDao()
        val withPortions = dao.observeAll().first()
            .firstNotNullOf { ingredient ->
                dao.observeDetails(ingredient.id).first()?.takeIf { it.portions.isNotEmpty() }
            }
        assertTrue(withPortions.portions.all { it.grams > 0 })
        assertTrue(withPortions.portions.all { it.labelEn.isNotBlank() && it.labelEs.isNotBlank() })
    }

    @Test
    fun recordsTheDatasetVersionAndAttribution() = runBlocking {
        val reference = database.foodReferenceDao()
        assertNotNull(reference.meta(FoodDatasetMetaEntity.DATASET_VERSION))
        assertTrue(
            "BEDCA must be credited wherever its figures are shown",
            reference.meta(FoodDatasetMetaEntity.ATTRIBUTION)?.contains("BEDCA") == true
        )
    }

    @Test
    fun installsTheBedcaBackedStarterRecipeBooks() = runBlocking {
        val recipes = database.recipeDao().observeAll().first()
        val builtIns = recipes.filter { it.isBuiltIn }

        assertEquals(250, builtIns.size)
        assertEquals(RecipeBooks.BUILT_IN.toSet(), builtIns.mapNotNull { it.folder }.toSet())
        assertTrue(
            "each book should meet the expanded meal-type targets",
            RecipeBooks.BUILT_IN.all { book ->
                builtIns.count {
                    it.folder == book && it.category == RecipeCategories.BREAKFAST
                } >= 10 && builtIns.count {
                    it.folder == book && it.category == RecipeCategories.MAIN_DISH
                } >= 50 && builtIns.count {
                    it.folder == book && it.category == RecipeCategories.DESSERT
                } >= 10
            }
        )
        builtIns.forEach { recipe ->
            assertTrue("${recipe.name} has no Spanish name", !recipe.nameEs.isNullOrBlank())
            val ingredients = database.recipeDao().observeIngredientsOf(recipe.id).first()
            assertTrue("${recipe.name} has no ingredients", ingredients.isNotEmpty())
            assertTrue(
                "${recipe.name} contains a non-BEDCA ingredient",
                ingredients.all { it.ingredient.sourceKey?.startsWith("BEDCA:") == true }
            )
            if (recipe.folder == RecipeBooks.LOW_CALORIE) {
                val kcal = ingredients.sumOf { it.nutrition.kcal }
                assertTrue("${recipe.name} is not lower calorie: $kcal kcal", kcal < 600.0)
            }
            val withSteps = database.recipeDao().observeRecipeWithSteps(recipe.id).first()
            assertTrue(
                "${recipe.name} needs complete bilingual instructions",
                withSteps?.steps?.let { steps ->
                    steps.size >= 2 && steps.all {
                        it.text.isNotBlank() && !it.textEs.isNullOrBlank()
                    }
                } == true
            )
        }
    }

    @Test
    fun importedIdsStayOutOfTheRangeUsersWillGet() = runBlocking {
        val imported = database.ingredientDao().observeAll().first().filter { !it.isCustom }
        assertTrue(
            "imported ids belong in their reserved block",
            imported.all { it.id > FoodDatabaseImporter.IMPORTED_ID_OFFSET }
        )
    }

    @Test
    fun excludesHumanMilkFromTheCatalogue() = runBlocking {
        val ingredients = database.ingredientDao().observeAll().first()
        assertTrue(ingredients.none { it.sourceKey == "BEDCA:2494" })
    }

    @Test
    fun reopeningDoesNotImportTwice() = runBlocking {
        val before = database.ingredientDao().count()
        val recipesBefore = database.recipeDao().observeAll().first().size
        database.close()

        database = NutriNestDatabase.build(context)
        assertEquals(before, database.ingredientDao().count())
        assertEquals(recipesBefore, database.recipeDao().observeAll().first().size)
    }

    @Test
    fun libraryUpgradePreservesSavedRecipeStepsAndQuantities() = runBlocking {
        val original = database.recipeDao().observeAll().first().first { it.isBuiltIn }
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            "UPDATE recipes SET nameEs = NULL WHERE id = ?",
            arrayOf<Any>(original.id)
        )
        sqlite.execSQL(
            "DELETE FROM recipe_steps WHERE recipeId = ?",
            arrayOf<Any>(original.id)
        )
        sqlite.execSQL(
            "INSERT INTO recipe_steps (recipeId, position, text) VALUES (?, 0, 'Old step')",
            arrayOf<Any>(original.id)
        )
        sqlite.execSQL(
            "UPDATE food_dataset_meta SET value = '2' " +
                "WHERE `key` = 'built_in_recipe_library_version'"
        )
        sqlite.execSQL("UPDATE recipe_ingredients SET grams=grams+0.123456 WHERE recipeId=?", arrayOf<Any>(original.id))
        val quantities = CatalogTestFixtures.snapshot(sqlite, listOf("recipe_ingredients"))
        database.close()

        database = NutriNestDatabase.build(context)
        val upgraded = database.recipeDao().observeRecipeWithSteps(original.id).first()
        assertNotNull(upgraded)
        assertTrue(upgraded?.recipe?.nameEs == null)
        assertTrue(
            upgraded?.steps?.let { steps ->
                steps.size == 1 && steps.single().text == "Old step"
            } == true
        )
        assertEquals(quantities, CatalogTestFixtures.snapshot(database.openHelper.writableDatabase, listOf("recipe_ingredients")))
        assertEquals(250, database.recipeDao().observeAll().first().count { it.isBuiltIn })
    }

    @Test
    fun v9ImportAndReopenPreserveSnapshotsAndRejectChangedDataset() = runBlocking {
        database.ingredientDao().count()
        val db = database.openHelper.writableDatabase
        val tables = CatalogTestFixtures.tables(db)
        val before = CatalogTestFixtures.snapshot(db, tables)
        assertTrue(FoodDatabaseImporter.importIfNeeded(context, db))
        assertEquals(before, CatalogTestFixtures.snapshot(db, tables))
        db.execSQL("UPDATE food_dataset_meta SET value='different-version' WHERE `key`='dataset_version'")
        val changedMarker = CatalogTestFixtures.snapshot(db, tables)
        assertTrue(!FoodDatabaseImporter.importIfNeeded(context, db))
        assertEquals(changedMarker, CatalogTestFixtures.snapshot(db, tables))
        CanonicalCatalogStore.validate(db)
    }

    @Test
    fun editingCreatesANewSnapshotAndKeepsOldRecipesAndNutrients() = runBlocking {
        val dao = database.ingredientDao()
        val food = dao.observeAll().first().first()
        val db = database.openHelper.writableDatabase
        val before = CanonicalCatalogStore.ingredientFingerprint(db, food.id)
        val repository = com.zame.nutrinest.data.repository.LocalIngredientRepository(database)
        val id = repository.save(food.copy(nutrition = food.nutrition.copy(kcal = 123.456)), emptyList(), emptyList(), emptyList())
        assertTrue(id != food.id)
        assertEquals(before, CanonicalCatalogStore.ingredientFingerprint(db, food.id))
        assertEquals(food.nutrition, dao.findById(food.id)?.nutrition)
        assertEquals(123.456, dao.findById(id)!!.nutrition.kcal, 0.0)
        assertEquals("HIDDEN", database.canonicalCatalogDao().status(food.id)?.state)
        assertTrue(database.canonicalCatalogDao().mapping(id) != null)
        CanonicalCatalogStore.validate(db)
    }

    private companion object {
        const val DB_NAME = "nutrinest.db"
    }
}
