package com.zame.nutrinest.ui.menu.config

import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.data.local.entity.RecipeEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LockedMealRecipeSearchTest {

    private val recipes = listOf(
        RecipeEntity(
            id = 1,
            name = "Mediterranean omelette",
            nameEs = "Tortilla mediterránea",
            category = RecipeCategories.BREAKFAST,
            folder = RecipeBooks.MEDITERRANEAN
        ),
        RecipeEntity(
            id = 2,
            name = "Mediterranean lentil salad",
            nameEs = "Ensalada mediterránea de lentejas",
            category = RecipeCategories.MAIN_DISH,
            folder = RecipeBooks.MEDITERRANEAN,
            isFavorite = true
        ),
        RecipeEntity(
            id = 3,
            name = "World chicken curry",
            nameEs = "Curry de pollo",
            category = RecipeCategories.MAIN_DISH,
            folder = RecipeBooks.WORLD
        ),
        RecipeEntity(
            id = 4,
            name = "Light berry bowl",
            nameEs = "Bol ligero de frutos rojos",
            category = RecipeCategories.BREAKFAST,
            folder = RecipeBooks.LOW_CALORIE,
            isFavorite = true
        )
    )

    @Test
    fun `name book category and favorite filters combine`() {
        val result = filterLockedMealRecipes(
            recipes = recipes,
            filters = LockedMealRecipeSearchFilters(
                query = "ensalada",
                folders = setOf(RecipeBooks.MEDITERRANEAN),
                categories = setOf(RecipeCategories.MAIN_DISH),
                favoritesOnly = true
            ),
            spanish = true
        )

        assertEquals(listOf(2L), result.map { it.id })
    }

    @Test
    fun `book labels search in Spanish and multiple books remain selectable`() {
        val bySpanishBook = filterLockedMealRecipes(
            recipes = recipes,
            filters = LockedMealRecipeSearchFilters(query = "cocina del mundo"),
            spanish = true
        )
        assertEquals(listOf(3L), bySpanishBook.map { it.id })

        val twoBooks = filterLockedMealRecipes(
            recipes = recipes,
            filters = LockedMealRecipeSearchFilters(
                folders = setOf(RecipeBooks.MEDITERRANEAN, RecipeBooks.WORLD),
                categories = setOf(RecipeCategories.MAIN_DISH)
            ),
            spanish = false
        )
        assertEquals(listOf(2L, 3L), twoBooks.map { it.id })
    }

    @Test
    fun `an explicit no-book selection returns no recipes`() {
        val result = filterLockedMealRecipes(
            recipes = recipes,
            filters = LockedMealRecipeSearchFilters(folders = emptySet()),
            spanish = false
        )

        assertEquals(emptyList<Long>(), result.map { it.id })
    }
}
