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
    /** Canonical English name; also the fallback for older user recipes. */
    val name: String,
    /** Optional Spanish name. Built-ins always provide it. */
    val nameEs: String? = null,
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
) {
    /** Shipped recipes use a stable remote id so seeding is idempotent. */
    val isBuiltIn: Boolean get() = remoteId?.startsWith(RecipeBooks.BUILT_IN_ID_PREFIX) == true

    fun displayName(spanish: Boolean): String =
        if (spanish) nameEs?.takeIf(String::isNotBlank) ?: name else name
}

/**
 * Stable names for the three recipe books included with a fresh installation.
 *
 * Books intentionally reuse the existing `folder` column. A user-created
 * folder and a built-in book therefore participate in the same browsing,
 * filtering and planner code instead of creating a second organisation model.
 */
object RecipeBooks {
    const val MEDITERRANEAN = "Mediterranean Cuisine"
    const val LOW_CALORIE = "Low-Calorie Recipes"
    const val WORLD = "World Cuisine"
    const val MEDITERRANEAN_ES = "Cocina mediterránea"
    const val LOW_CALORIE_ES = "Recetas bajas en calorías"
    const val WORLD_ES = "Cocina del mundo"
    const val BUILT_IN_ID_PREFIX = "builtin:"

    val BUILT_IN = listOf(MEDITERRANEAN, LOW_CALORIE, WORLD)

    fun displayName(folder: String, spanish: Boolean): String = if (!spanish) {
        folder
    } else {
        when (folder) {
            MEDITERRANEAN -> MEDITERRANEAN_ES
            LOW_CALORIE -> LOW_CALORIE_ES
            WORLD -> WORLD_ES
            else -> folder
        }
    }

    fun searchableNames(folder: String): List<String> = when (folder) {
        MEDITERRANEAN -> listOf(MEDITERRANEAN, MEDITERRANEAN_ES)
        LOW_CALORIE -> listOf(LOW_CALORIE, LOW_CALORIE_ES)
        WORLD -> listOf(WORLD, WORLD_ES)
        else -> listOf(folder)
    }
}

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
    val textEs: String? = null,
    val imagePath: String? = null
) {
    fun displayText(spanish: Boolean): String =
        if (spanish) textEs?.takeIf(String::isNotBlank) ?: text else text
}

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
