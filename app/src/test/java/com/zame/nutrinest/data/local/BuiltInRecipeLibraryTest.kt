package com.zame.nutrinest.data.local

import com.zame.nutrinest.data.local.entity.RecipeBooks
import com.zame.nutrinest.data.local.entity.RecipeCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInRecipeLibraryTest {

    @Test
    fun `library has 44 varied recipes and 30 main dishes in each default book`() {
        val recipes = BuiltInRecipeLibrary.recipes

        assertEquals(132, recipes.size)
        RecipeBooks.BUILT_IN.forEach { book ->
            val inBook = recipes.filter { it.book == book }
            assertEquals("$book recipe count", 44, inBook.size)
            assertEquals(
                "$book main-dish count",
                30,
                inBook.count { it.category == RecipeCategories.MAIN_DISH }
            )
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
        assertTrue(recipes.all { it.nameEs.isNotBlank() })
        assertTrue(
            recipes.all {
                it.steps.size >= 5 &&
                    it.steps.size == it.stepsEs.size &&
                    it.steps.all(String::isNotBlank) &&
                    it.stepsEs.all(String::isNotBlank)
            }
        )
        assertTrue(recipes.all { it.ingredients.isNotEmpty() })
        assertTrue(recipes.all { recipe ->
            recipe.ingredients.all { it.grams.isFinite() && it.grams > 0.0 } &&
                recipe.ingredients.map { it.bedcaFoodId }.distinct().size ==
                recipe.ingredients.size
        })
    }
}
