package com.zame.nutrinest.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.data.local.entity.ActivityLevel
import com.zame.nutrinest.data.local.entity.BiologicalSex
import com.zame.nutrinest.data.local.entity.Goal
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.MenuScope
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.PlannedMealWithPortions
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.local.entity.RecipeIngredientInput
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.repository.LocalIngredientRepository
import com.zame.nutrinest.data.repository.LocalMenuRepository
import com.zame.nutrinest.data.repository.MealScopeTarget
import com.zame.nutrinest.domain.nutrition.RecipeMeasure
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import com.zame.nutrinest.domain.planner.GenerationConfig
import com.zame.nutrinest.domain.planner.GenerationResult
import com.zame.nutrinest.domain.planner.MealLock
import com.zame.nutrinest.domain.planner.MealOverride
import com.zame.nutrinest.domain.planner.MenuGenerator
import com.zame.nutrinest.domain.planner.MenuResolver
import com.zame.nutrinest.ui.menu.ShoppingQuantity
import com.zame.nutrinest.ui.menu.observeMenuIngredientCatalog
import com.zame.nutrinest.util.WeekUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class NormalizedShoppingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "normalized-shopping.db"
    private val egg = 1_000_758L
    private val milk = 1_002_493L
    private val rawRice = 1_000_904L
    private val boiledRice = 1_001_009L
    private val dinner = MealSlot.DINNER

    @Test fun plannedQuantitiesAndChecksSurviveIntakeSwapRegenerationAndReopening() = runBlocking {
        context.deleteDatabase(name)
        val current = WeekUtils.weekStartOf()
        val historical = WeekUtils.previousWeekStart(current)
        val next = WeekUtils.nextWeekStart(current)
        lateinit var expectedWeeks: Map<Long, WeekSnapshot>
        try {
            withDatabase { database ->
                val ingredients = LocalIngredientRepository(database)
                val repo = LocalMenuRepository(database, MenuGenerator(Random(7)))
                val dao = database.menuDao()
                val (first, second) = insertRecipes(database, ingredients)
                val recipeGrams = mapOf(
                    first to mapOf(egg to 50.0, milk to 515.0, rawRice to 80.0, boiledRice to 100.0),
                    second to mapOf(egg to 20.0, milk to 1030.0, rawRice to 40.0)
                )
                val a = database.profileDao().insert(profile("Ana", 60.0, BiologicalSex.FEMALE))
                val config = GenerationConfig(
                    slotWeights = mapOf(dinner to 0.35f),
                    includedFolders = setOf("Shopping fixture"),
                    maxRecipeRepeats = 20,
                    cheatMeals = 1,
                    locks = listOf(MealLock(setOf(0), dinner, first))
                )
                val solo = repo.generate(historical, config) as GenerationResult.Success
                assertTrue(solo.menu.meals.filterNot { it.isFree }.all { it.portions.keys == setOf(a) })
                assertEquals(1, solo.menu.meals.count { it.isFree })
                assertEquals(solo.menu.shoppingTotals, snapshot(database, repo, historical).grams)
                val historicalRows = repo.observeShoppingItems(historical).first()
                repo.setPurchased(historicalRows.single { it.item.ingredientId == boiledRice }.item.id, true)
                val savedHistory = snapshot(database, repo, historical)
                assertGrams(expectedGrams(savedHistory.meals, recipeGrams), savedHistory.grams)

                val b = database.profileDao().insert(profile("Luis", 85.0, BiologicalSex.MALE))
                val individual = repo.generate(
                    current, config.copy(scope = MenuScope.INDIVIDUAL, locks = emptyList())
                ) as GenerationResult.Success
                assertTrue(individual.menu.meals.filterNot { it.isFree }.all { it.portions.keys == setOf(it.profileId) })
                val savedIndividual = snapshot(database, repo, current)
                assertGrams(expectedGrams(savedIndividual.meals, recipeGrams), savedIndividual.grams)

                val mixed = config.copy(
                    scope = MenuScope.PER_MEAL,
                    overrides = setOf(MealOverride(0, dinner, b)),
                    locks = config.locks + MealLock(setOf(0), dinner, second, profileId = b)
                )
                val generated = repo.generate(next, mixed) as GenerationResult.Success
                val initial = snapshot(database, repo, next)
                assertEquals(generated.menu.shoppingTotals, initial.grams)
                assertGrams(expectedGrams(initial.meals, recipeGrams), initial.grams)
                val shared = initial.meals.first { it.portions.size == 2 }
                assertNotEquals(shared.portions[0].baseFactor, shared.portions[1].baseFactor)
                val locks = initial.meals.filter { it.meal.isLocked }
                val lockedA = locks.single { it.meal.profileId == null }
                val lockedB = locks.single { it.meal.profileId == b }
                assertEquals(first, lockedA.meal.recipeId)
                assertEquals(second, lockedB.meal.recipeId)
                assertTrue(MenuResolver.forProfile(locks, b, PlannedMealWithPortions::meal).single() == lockedB)

                val markedId = initial.items.single { it.ingredientId == egg }.id
                repo.setPurchased(markedId, true)
                val beforeIntake = snapshot(database, repo, next)
                repo.logIntake(a, next, "Fixture intake", NutritionValues(kcal = 100.0))
                val afterIntake = snapshot(database, repo, next)
                assertEquals(beforeIntake.items, afterIntake.items)
                val previousPortion = lockedA.portions.single()
                val adjusted = dao.getPortions(lockedA.meal.id).single()
                assertEquals(previousPortion.baseFactor, adjusted.baseFactor, 0.0)
                assertTrue(adjusted.factor < previousPortion.factor)
                assertEquals(lockedB.portions, dao.getPortions(lockedB.meal.id))

                // Rewriting the list for a different dish still uses the adjusted diner's base factor.
                val swap = shared.meal
                val replacement = if (swap.recipeId == first) second else first
                repo.replaceMeal(swap.id, replacement, MealScopeTarget.Household)
                val afterSwap = snapshot(database, repo, next)
                assertGrams(expectedGrams(afterSwap.meals, recipeGrams), afterSwap.grams)
                assertNotEquals(beforeIntake.grams, afterSwap.grams)
                assertEquals(adjusted, dao.getPortions(lockedA.meal.id).single())
                assertTrue(afterSwap.items.single { it.ingredientId == egg }.purchased)
                assertEquals(savedHistory, snapshot(database, repo, historical))

                assertTrue(ingredients.delete(boiledRice))
                assertFalse(ingredients.observeAll().first().any { it.id == boiledRice })
                val shopping = repo.observeShoppingItems(next).first()
                val catalog = ingredients.observeMenuIngredientCatalog().first()
                val hidden = shopping.single { it.item.ingredientId == boiledRice }
                assertEquals("Arroz integral, hervido", catalog.nameOf(hidden.ingredient, spanish = true))
                val mealIngredients = repo.observeIngredientsOf(first).first()
                assertTrue(mealIngredients.any { it.ingredient.id == boiledRice && it.grams == 100.0 })
                assertEquals("Whole chicken egg, poached",
                    catalog.nameOf(mealIngredients.single { it.ingredient.id == egg }.ingredient, spanish = false))
                val milkDemand = ShoppingQuantity.from(afterSwap.grams.getValue(milk), catalog.measures.getValue(milk))
                assertEquals(UnitDefinition.LITRE, milkDemand.required!!.unit)
                assertEquals(afterSwap.grams.getValue(milk) / 1030.0, milkDemand.required.amount, 1e-12)
                assertNull(milkDemand.toBuy)

                val rerun = repo.generate(next, mixed.copy(locks = emptyList())) as GenerationResult.Success
                val savedMixed = snapshot(database, repo, next)
                assertGrams(expectedGrams(savedMixed.meals, recipeGrams), savedMixed.grams)
                assertEquals(rerun.menu.shoppingTotals, savedMixed.grams)
                assertEquals(
                    setOf(null to first, b to second),
                    savedMixed.meals.filter { it.meal.isLocked }.map { it.meal.profileId to it.meal.recipeId }.toSet()
                )
                assertTrue(savedMixed.items.single { it.ingredientId == egg }.purchased)
                expectedWeeks = mapOf(historical to savedHistory, current to savedIndividual, next to savedMixed)
            }
            withDatabase { database ->
                val repo = LocalMenuRepository(database)
                expectedWeeks.forEach { (week, saved) ->
                    assertEquals(saved, snapshot(database, repo, week))
                }
                val history = repo.observeShoppingItems(historical).first()
                assertTrue(history.single { it.item.ingredientId == boiledRice }.item.purchased)
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private suspend fun insertRecipes(database: NutriNestDatabase, ingredients: LocalIngredientRepository): Pair<Long, Long> {
        val measures = ingredients.observeRecipeMeasures().first()
        val first = listOf(
            RecipeIngredientInput(egg, RecipeMeasure.default(measures.getValue(egg)).enter(1.0)!!),
            RecipeIngredientInput(milk, RecipeMeasure.default(measures.getValue(milk)).enter(500.0)!!),
            RecipeIngredientInput(rawRice, 80.0),
            RecipeIngredientInput(boiledRice, 100.0)
        )
        val second = listOf(
            RecipeIngredientInput(egg, 20.0),
            RecipeIngredientInput(milk, 1030.0, quantityUnit = "LITRE"),
            RecipeIngredientInput(rawRice, 40.0)
        )
        val recipes = database.recipeDao()
        val steps = listOf("Fixture instructions" to null)
        return recipes.insertFullRecipe(RecipeEntity(name = "Fixture A", category = "Main Dish", folder = "Shopping fixture"), first, steps) to
            recipes.insertFullRecipe(RecipeEntity(name = "Fixture B", category = "Main Dish", folder = "Shopping fixture"), second, steps)
    }

    private data class WeekSnapshot(val meals: List<PlannedMealWithPortions>, val items: List<ShoppingItemEntity>) {
        val grams get() = items.associate { it.ingredientId to it.grams }
    }

    private suspend fun snapshot(database: NutriNestDatabase, repo: LocalMenuRepository, week: Long): WeekSnapshot {
        val menu = repo.observeMenu(week).first()!!
        val meals = database.menuDao().getMealsWithPortions(menu.id).sortedBy { it.meal.id }
        val items = repo.observeShoppingItems(week).first().map { it.item }.sortedBy { it.id }
        return WeekSnapshot(meals, items)
    }

    private fun expectedGrams(
        meals: List<PlannedMealWithPortions>,
        recipeGrams: Map<Long, Map<Long, Double>>
    ): Map<Long, Double> = buildMap {
        for (row in meals) {
            val recipe = row.meal.recipeId ?: continue
            for (portion in row.portions) {
                for ((ingredient, grams) in recipeGrams.getValue(recipe)) {
                    put(ingredient, (get(ingredient) ?: 0.0) + grams * portion.baseFactor)
                }
            }
        }
    }

    private fun assertGrams(expected: Map<Long, Double>, actual: Map<Long, Double>) {
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (ingredient, grams) -> assertEquals(grams, actual.getValue(ingredient), 1e-9) }
    }

    private fun profile(name: String, kg: Double, sex: BiologicalSex) = ProfileEntity(
        name = name, dateOfBirth = 946684800000L, sex = sex, heightCm = 170.0, weightKg = kg,
        activityLevel = ActivityLevel.LIGHT, goal = Goal.MAINTAIN
    )

    private suspend fun withDatabase(block: suspend (NutriNestDatabase) -> Unit) {
        val database = NutriNestDatabase.build(context, name)
        try {
            block(database)
        } finally {
            database.close()
        }
    }
}
