package com.zame.nutrinest.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "recipes", indices = [Index(value = ["name"], unique = true)])
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    /** User-defined folder for organisation; null = not in any folder. */
    val folder: String? = null,
    val coverImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Reserved for future community sync. */
    val remoteId: String? = null,
    val isFavorite: Boolean = false
)

/**
 * Junction table linking a recipe to its ingredients,
 * carrying the quantity of that ingredient in grams.
 */
@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["recipeId", "ingredientId"],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("ingredientId")]
)
data class RecipeIngredientCrossRef(
    val recipeId: Long,
    val ingredientId: Long,
    val grams: Double
)

@Entity(
    tableName = "recipe_steps",
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("recipeId")]
)
data class RecipeStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val position: Int,
    val text: String,
    val imagePath: String? = null
)

/** Result row for the JOIN of ingredients with their quantity in a recipe. */
data class IngredientWithQuantity(
    @Embedded val ingredient: IngredientEntity,
    val grams: Double
) {
    val nutrition: NutritionValues get() = ingredient.nutrition.forGrams(grams)
}

/**
 * What a whole recipe adds up to, summed in SQL.
 *
 * Lets the recipe list show calories and a macro split per row without loading
 * every recipe's ingredients into memory first.
 */
data class RecipeNutrition(
    val recipeId: Long,
    @Embedded val nutrition: NutritionValues
)

/** A recipe together with its steps (ingredients fetched via a JOIN query). */
data class RecipeWithSteps(
    @Embedded val recipe: RecipeEntity,
    @Relation(parentColumn = "id", entityColumn = "recipeId")
    val steps: List<RecipeStepEntity>
)
