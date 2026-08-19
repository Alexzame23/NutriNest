package com.zame.nutrinest.domain.planner

import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.util.TextNormalizer

/** Conservative local matching for the free-text restrictions stored on profiles. */
object DietarySafety {

    private val ALLERGY_NOISE = Regex(
        "\\b(allergy|allergic|intolerance|intolerant|allergies|alergia|alergico|" +
            "alergica|intolerancia|intolerante|to|a|al|los|las|no)\\b"
    )

    private val vegetarian = setOf("vegetarian", "vegetariano", "vegetariana")
    private val vegan = setOf("vegan", "vegano", "vegana")
    private val pescatarian = setOf("pescatarian", "pescetariano", "pescetariana")

    private val expansions = mapOf(
        "nuts" to setOf(
            "nut", "nuts", "peanut", "almond", "walnut", "hazelnut", "cashew", "pistachio",
            "nuez", "nueces", "cacahuete", "almendra", "avellana", "anacardo", "pistacho"
        ),
        "frutos secos" to setOf(
            "nut", "nuts", "peanut", "almond", "walnut", "hazelnut", "cashew", "pistachio",
            "nuez", "nueces", "cacahuete", "almendra", "avellana", "anacardo", "pistacho"
        ),
        "shellfish" to setOf("shellfish", "shrimp", "prawn", "crab", "lobster", "mollusc"),
        "marisco" to setOf("marisco", "gamba", "camaron", "cangrejo", "langosta", "molusco"),
        "gluten" to setOf("gluten", "wheat", "barley", "rye", "spelt", "trigo", "cebada", "centeno", "espelta"),
        "soy" to setOf("soy", "soya", "soja"),
        "sesame" to setOf("sesame", "sesamo"),
        "sesamo" to setOf("sesame", "sesamo")
    )

    fun recipeAllowed(recipe: PlannableRecipe, profiles: List<ProfileEntity>): Boolean =
        recipe.ingredients.none { item ->
            profiles.any { profile -> ingredientBlocked(item.ingredient, profile) }
        }

    fun ingredientBlocked(ingredient: IngredientEntity, profile: ProfileEntity): Boolean {
        val preferences = tokens(profile.dietaryPreferences)
        if (preferences.any { it in vegan } && ingredient.categoryKey in setOf(
                IngredientCategories.MEAT, IngredientCategories.FISH,
                IngredientCategories.EGGS, IngredientCategories.DAIRY
            )
        ) return true
        if (preferences.any { it in vegetarian } && ingredient.categoryKey in setOf(
                IngredientCategories.MEAT, IngredientCategories.FISH
            )
        ) return true
        if (preferences.any { it in pescatarian } &&
            ingredient.categoryKey == IngredientCategories.MEAT
        ) return true

        val allergies = allergyTokens(profile.allergies)
        if (allergies.isEmpty()) return false
        val haystack = listOfNotNull(
            ingredient.nameNorm,
            ingredient.nameEsNorm,
            ingredient.baseName?.let(TextNormalizer::normalize),
            ingredient.categoryKey.lowercase()
        ).joinToString(" ")

        return allergies.any { allergy ->
            categoryBlocked(allergy, ingredient.categoryKey) ||
                expanded(allergy).any { term -> containsTerm(haystack, term) }
        }
    }

    private fun categoryBlocked(allergy: String, category: String): Boolean = when (allergy) {
        "milk", "dairy", "lactose", "leche", "lactosa" -> category == IngredientCategories.DAIRY
        "egg", "eggs", "huevo", "huevos" -> category == IngredientCategories.EGGS
        "fish", "pescado" -> category == IngredientCategories.FISH
        else -> false
    }

    private fun expanded(term: String): Set<String> =
        expansions.entries.firstOrNull { (key, _) -> term == key }?.value ?: setOf(term)

    private fun containsTerm(haystack: String, term: String): Boolean =
        Regex("(^| )${Regex.escape(term)}( |$)").containsMatchIn(haystack)

    private fun tokens(value: String?): Set<String> = value.orEmpty()
        .split(',', ';', '\n', '|')
        .map(TextNormalizer::normalize)
        .filter { it.length >= 2 }
        .toSet()

    private fun allergyTokens(value: String?): Set<String> = tokens(value)
        .map { ALLERGY_NOISE.replace(it, " ").replace(Regex("\\s+"), " ").trim() }
        .filter { it.length >= 2 }
        .toSet()
}
