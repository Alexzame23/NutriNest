package com.zame.nutrinest.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Nutritional values expressed PER 100 g of the ingredient.
 * Embedded in [IngredientEntity]; recipe totals are computed from these.
 */
data class NutritionValues(
    val kcal: Double = 0.0,
    val carbs: Double = 0.0,   // grams
    val fats: Double = 0.0,    // grams
    val protein: Double = 0.0  // grams
) {
    operator fun plus(other: NutritionValues) = NutritionValues(
        kcal + other.kcal,
        carbs + other.carbs,
        fats + other.fats,
        protein + other.protein
    )

    /** Scale from per-100g to an arbitrary quantity in grams. */
    fun forGrams(grams: Double): NutritionValues {
        val f = grams / 100.0
        return NutritionValues(kcal * f, carbs * f, fats * f, protein * f)
    }
}

/**
 * Food types used for ingredient classification and recipe filtering.
 * Kept as a plain list of strings in the DB for forward compatibility
 * (a future community backend can introduce new types freely).
 */
object FoodTypes {
    val ALL = listOf(
        "Pasta", "Rice", "Meat", "Fish", "Vegetables", "Fruit",
        "Dairy", "Eggs", "Legumes", "Grains", "Nuts & Seeds",
        "Oils & Fats", "Spices", "Other"
    )
}

object RecipeCategories {
    val ALL = listOf(
        "Breakfast", "Main Dish", "Side Dish", "Dessert", "Snack", "Drink"
    )
}

@Entity(tableName = "ingredients")
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val foodType: String,
    @Embedded val nutrition: NutritionValues,
    val imagePath: String? = null,
    /** false = shipped built-in database, true = created by the user. */
    val isCustom: Boolean = true,
    /**
     * Reserved for the future community feature: null = local-only.
     * When syncing is added, this holds the server-side id.
     */
    val remoteId: String? = null
)
