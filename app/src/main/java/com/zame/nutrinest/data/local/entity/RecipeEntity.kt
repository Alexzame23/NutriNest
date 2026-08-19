package com.zame.nutrinest.data.local.entity

import androidx.room.Embedded
import androidx.room.ColumnInfo
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
    /** Anchor used when a landscape/portrait cover is cropped into a card. */
    @ColumnInfo(defaultValue = "'CENTER'")
    val coverImageCrop: String = RecipeImageCrop.CENTER,
    val createdAt: Long = System.currentTimeMillis(),
    /** Reserved for future community sync. */
    val remoteId: String? = null,
    val isFavorite: Boolean = false
)

/** Stable, storage-friendly cover-image crop anchors. */
object RecipeImageCrop {
    const val TOP_START = "TOP_START"
    const val TOP_CENTER = "TOP_CENTER"
    const val TOP_END = "TOP_END"
    const val CENTER_START = "CENTER_START"
    const val CENTER = "CENTER"
    const val CENTER_END = "CENTER_END"
    const val BOTTOM_START = "BOTTOM_START"
    const val BOTTOM_CENTER = "BOTTOM_CENTER"
    const val BOTTOM_END = "BOTTOM_END"

    val all = listOf(
        TOP_START, TOP_CENTER, TOP_END,
        CENTER_START, CENTER, CENTER_END,
        BOTTOM_START, BOTTOM_CENTER, BOTTOM_END
    )
}

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
    /** Canonical mass used for nutrition and planning calculations. */
    val grams: Double,
    /** The unit the cook chose, retained for recipe instructions/display. */
    @ColumnInfo(defaultValue = "'GRAM'")
    val quantityUnit: String = "GRAM"
)

/** Input to an atomic recipe save. Quantities are always canonical grams. */
data class RecipeIngredientInput(
    val ingredientId: Long,
    val grams: Double,
    val quantityUnit: String = "GRAM"
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
    val grams: Double,
    val quantityUnit: String = "GRAM"
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
