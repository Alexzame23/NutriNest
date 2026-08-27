package com.zame.nutrinest.data.local

import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.local.entity.RecipeCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInRecipeLibraryTest {

    @Test
    fun `every default book exceeds the requested breakfast main and dessert targets`() {
        val recipes = BuiltInRecipeLibrary.recipes

        assertEquals(250, recipes.size)
        RecipeBooks.BUILT_IN.forEach { book ->
            val inBook = recipes.filter { it.book == book }
            assertTrue("$book has at least 10 breakfasts", inBook.count {
                it.category == RecipeCategories.BREAKFAST
            } >= 10)
            assertTrue("$book has at least 50 main dishes", inBook.count {
                it.category == RecipeCategories.MAIN_DISH
            } >= 50)
            assertTrue("$book has at least 10 desserts", inBook.count {
                it.category == RecipeCategories.DESSERT
            } >= 10)
            assertTrue(
                "$book covers every requested meal type",
                inBook.map { it.category }.containsAll(
                    listOf(
                        RecipeCategories.BREAKFAST,
                        RecipeCategories.MAIN_DISH,
                        RecipeCategories.SIDE_DISH,
                        RecipeCategories.SNACK,
                        RecipeCategories.DESSERT
                    )
                )
            )
        }
    }

    @Test
    fun `keys names quantities and ingredient links are safe to seed`() {
        val recipes = BuiltInRecipeLibrary.recipes

        assertEquals(recipes.size, recipes.map { it.key }.distinct().size)
        assertEquals(recipes.size, recipes.map { it.name }.distinct().size)
        assertEquals(recipes.size, recipes.map { it.nameEs }.distinct().size)
        assertTrue(recipes.all { it.nameEs.isNotBlank() })
        assertTrue(recipes.all {
            it.steps.size >= 2 &&
                it.steps.size == it.stepsEs.size &&
                it.steps.all(String::isNotBlank) &&
                it.stepsEs.all(String::isNotBlank) &&
                it.steps.distinct().size == it.steps.size &&
                it.stepsEs.distinct().size == it.stepsEs.size
        })
        assertEquals(recipes.size, recipes.map { it.steps }.distinct().size)
        assertEquals(recipes.size, recipes.map { it.stepsEs }.distinct().size)
        assertTrue(recipes.filter { it.category == RecipeCategories.MAIN_DISH }.all {
            it.steps.size >= 3
        })
        assertTrue("instruction lengths should follow the dish, not a template", recipes
            .map { it.steps.size }.distinct().size >= 5)
        assertTrue(recipes.none {
            it.steps.first().startsWith("Gather and measure") ||
                it.steps.last().startsWith("Check the texture and doneness")
        })
        assertTrue(recipes.all { it.ingredients.isNotEmpty() })
        assertTrue(recipes.all { recipe ->
            recipe.ingredients.all { it.grams.isFinite() && it.grams > 0.0 } &&
                recipe.ingredients.map { it.bedcaFoodId }.distinct().size ==
                recipe.ingredients.size
        })
    }

    @Test
    fun `built in book titles display and search in both supported languages`() {
        assertEquals(
            "Cocina mediterránea",
            RecipeBooks.displayName(RecipeBooks.MEDITERRANEAN, spanish = true)
        )
        assertEquals(
            "Recetas bajas en calorías",
            RecipeBooks.displayName(RecipeBooks.LOW_CALORIE, spanish = true)
        )
        assertEquals(
            "Cocina del mundo",
            RecipeBooks.displayName(RecipeBooks.WORLD, spanish = true)
        )
        RecipeBooks.BUILT_IN.forEach { book ->
            assertEquals(book, RecipeBooks.displayName(book, spanish = false))
            assertTrue(RecipeBooks.searchableNames(book).contains(book))
            assertTrue(
                RecipeBooks.searchableNames(book).contains(
                    RecipeBooks.displayName(book, spanish = true)
                )
            )
        }
    }
}
