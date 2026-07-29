package com.zame.nutrinest.data.local.entity

import androidx.annotation.StringRes
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.zame.nutrinest.R

/** Meal slots within a day. Order in the enum = display order. */
enum class MealSlot(@StringRes val labelRes: Int) {
    BREAKFAST(R.string.meal_slot_breakfast),
    MORNING_SNACK(R.string.meal_slot_morning_snack),
    LUNCH(R.string.meal_slot_lunch),
    AFTERNOON_SNACK(R.string.meal_slot_afternoon_snack),
    DINNER(R.string.meal_slot_dinner);

    companion object {
        /** Which slots make up a day for a given meals-per-day setting. */
        fun forMealsPerDay(count: Int): List<MealSlot> = when (count) {
            3 -> listOf(BREAKFAST, LUNCH, DINNER)
            4 -> listOf(BREAKFAST, LUNCH, AFTERNOON_SNACK, DINNER)
            else -> listOf(BREAKFAST, MORNING_SNACK, LUNCH, AFTERNOON_SNACK, DINNER)
        }

        /** Sensible default calorie share for each slot count. */
        fun defaultWeights(slots: List<MealSlot>): Map<MealSlot, Float> = when (slots.size) {
            3 -> mapOf(BREAKFAST to 0.30f, LUNCH to 0.40f, DINNER to 0.30f)
            4 -> mapOf(
                BREAKFAST to 0.25f, LUNCH to 0.35f,
                AFTERNOON_SNACK to 0.15f, DINNER to 0.25f
            )
            else -> mapOf(
                BREAKFAST to 0.25f, MORNING_SNACK to 0.10f, LUNCH to 0.30f,
                AFTERNOON_SNACK to 0.10f, DINNER to 0.25f
            )
        }
    }
}

/**
 * One generated week. [weekStart] is the epoch millis of Monday 00:00 local
 * time and uniquely identifies the week.
 * [slotsSpec] persists the slots + weights used, e.g.
 * "BREAKFAST:0.3,LUNCH:0.4,DINNER:0.3" so the UI and detail screens can
 * reconstruct the distribution without re-asking.
 */
@Entity(tableName = "weekly_menus", indices = [Index(value = ["weekStart"], unique = true)])
data class WeeklyMenuEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: Long,
    val slotsSpec: String,
    val createdAt: Long = System.currentTimeMillis(),
    val remoteId: String? = null
) {
    fun slotWeights(): Map<MealSlot, Float> =
        slotsSpec.split(",").mapNotNull { part ->
            val pieces = part.split(":")
            if (pieces.size == 2) {
                val slot = runCatching { MealSlot.valueOf(pieces[0]) }.getOrNull()
                val weight = pieces[1].toFloatOrNull()
                if (slot != null && weight != null) slot to weight else null
            } else null
        }.toMap()

    companion object {
        fun encodeSlots(weights: Map<MealSlot, Float>): String =
            weights.entries.joinToString(",") { "${it.key.name}:${it.value}" }
    }
}

@Entity(
    tableName = "planned_meals",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyMenuEntity::class,
            parentColumns = ["id"],
            childColumns = ["menuId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("menuId"), Index("recipeId")]
)
data class PlannedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val menuId: Long,
    /** 0 = Monday … 6 = Sunday. */
    val dayOfWeek: Int,
    val slot: MealSlot,
    val recipeId: Long,
    val isLocked: Boolean = false,
    val isCheatMeal: Boolean = false
)

/**
 * Per-person portion of a planned meal. [factor] scales the base recipe:
 * grams for this person = ingredient.grams × factor.
 */
@Entity(
    tableName = "meal_portions",
    primaryKeys = ["plannedMealId", "profileId"],
    foreignKeys = [
        ForeignKey(
            entity = PlannedMealEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannedMealId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class MealPortionEntity(
    val plannedMealId: Long,
    val profileId: Long,
    val factor: Double
)

@Entity(
    tableName = "shopping_items",
    foreignKeys = [
        ForeignKey(
            entity = WeeklyMenuEntity::class,
            parentColumns = ["id"],
            childColumns = ["menuId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IngredientEntity::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("menuId"), Index("ingredientId")]
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val menuId: Long,
    val ingredientId: Long,
    val grams: Double,
    val purchased: Boolean = false
)

// ── Relations for queries ────────────────────────────────────────────────

data class PlannedMealWithRecipe(
    @Embedded val meal: PlannedMealEntity,
    @Relation(parentColumn = "recipeId", entityColumn = "id")
    val recipe: RecipeEntity
)

data class PortionWithProfile(
    @Embedded val portion: MealPortionEntity,
    @Relation(parentColumn = "profileId", entityColumn = "id")
    val profile: ProfileEntity
)

data class ShoppingItemWithIngredient(
    @Embedded val item: ShoppingItemEntity,
    @Relation(parentColumn = "ingredientId", entityColumn = "id")
    val ingredient: IngredientEntity
)

/** Converter for the MealSlot enum. */
class MenuConverters {
    @TypeConverter fun slotToString(v: MealSlot) = v.name
    @TypeConverter fun stringToSlot(v: String) = MealSlot.valueOf(v)
}
