package com.zame.nutrinest.data.local

internal data class BilingualRecipeStep(val en: String, val es: String)

internal fun bedca(foodId: Long, grams: Number, unit: String = "GRAM") =
    BuiltInIngredient(foodId, grams.toDouble(), unit)

internal fun instruction(en: String, es: String) = BilingualRecipeStep(en, es)

internal fun expandedRecipe(
    key: String,
    name: String,
    nameEs: String,
    book: String,
    category: String,
    ingredients: List<BuiltInIngredient>,
    vararg instructions: BilingualRecipeStep
): BuiltInRecipe {
    require(instructions.size >= 2) { "$key needs complete cooking instructions" }
    require(instructions.all { it.en.isNotBlank() && it.es.isNotBlank() })
    return BuiltInRecipe(
        key = key,
        name = name,
        nameEs = nameEs,
        book = book,
        category = category,
        ingredients = ingredients,
        steps = instructions.map { it.en },
        stepsEs = instructions.map { it.es }
    )
}
