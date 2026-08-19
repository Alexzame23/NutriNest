package com.zame.nutrinest.data.local.entity

import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.zame.nutrinest.R

/** Meal slots within a day. Order in the enum = display order. */
enum class MealSlot(@StringRes val labelRes: Int, val nominalHour: Int) {
    BREAKFAST(R.string.meal_slot_breakfast, 8),
    MORNING_SNACK(R.string.meal_slot_morning_snack, 11),
    LUNCH(R.string.meal_slot_lunch, 14),
    AFTERNOON_SNACK(R.string.meal_slot_afternoon_snack, 17),
    DINNER(R.string.meal_slot_dinner, 21);

    /**
     * Only the two big meals of the day take a first course or a dessert —
     * a two-course breakfast is not a thing anyone asked for.
     */
    val allowsCourses: Boolean get() = this == LUNCH || this == DINNER

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
 * How a week's menu is split across the household.
 *
 * [SHARED] and [INDIVIDUAL] are the two extremes; [PER_MEAL] is shared by
 * default with per-person exceptions, which is how real households actually
 * eat — everyone has the same dinner, but the child's Tuesday lunch is at
 * school.
 */
enum class MenuScope(@StringRes val labelRes: Int, @StringRes val descriptionRes: Int) {
    SHARED(R.string.menu_scope_shared, R.string.menu_scope_shared_note),
    INDIVIDUAL(R.string.menu_scope_individual, R.string.menu_scope_individual_note),
    PER_MEAL(R.string.menu_scope_per_meal, R.string.menu_scope_per_meal_note)
}

/**
 * Position of a dish inside one meal slot, for the Mediterranean
 * first-course-plus-main pattern. [SINGLE] is one dish filling the slot on its
 * own; every planned meal carries exactly one course.
 */
enum class MealCourse(@StringRes val labelRes: Int) {
    SINGLE(R.string.course_single),
    FIRST(R.string.course_first),
    MAIN(R.string.course_main),
    DESSERT(R.string.course_dessert);

    companion object {
        /**
         * Relative size of each course. Normalised over whichever courses a
         * slot actually has, so the slot's calorie budget is always fully
         * distributed: first + main splits 40/60, and a dessert takes ~15%
         * off the top of whatever it is added to.
         */
        private val RELATIVE_SIZE = mapOf(
            SINGLE to 1.0, FIRST to 0.4, MAIN to 0.6, DESSERT to 0.18
        )

        /** The course structure implied by the two user-facing toggles. */
        fun structure(twoCourses: Boolean, dessert: Boolean): List<MealCourse> = buildList {
            if (twoCourses) { add(FIRST); add(MAIN) } else add(SINGLE)
            if (dessert) add(DESSERT)
        }

        /** Fraction of the slot budget each course gets. Sums to 1. */
        fun shares(courses: List<MealCourse>): Map<MealCourse, Double> {
            val present = courses.ifEmpty { listOf(SINGLE) }
            val total = present.sumOf { RELATIVE_SIZE.getValue(it) }
            return present.associateWith { RELATIVE_SIZE.getValue(it) / total }
        }

        /** The course a lock or a swap means when the user doesn't say. */
        fun principal(courses: List<MealCourse>): MealCourse =
            when {
                MAIN in courses -> MAIN
                SINGLE in courses -> SINGLE
                else -> courses.firstOrNull() ?: SINGLE
            }
    }
}

/**
 * One generated week. [weekStart] is the epoch millis of Monday 00:00 local
 * time and uniquely identifies the week.
 * [slotsSpec] persists the slots + weights used, e.g.
 * "BREAKFAST:0.3,LUNCH:0.4,DINNER:0.3" so the UI and detail screens can
 * reconstruct the distribution without re-asking. [coursesSpec] does the same
 * for the course structure, e.g. "LUNCH:FIRST|MAIN|DESSERT".
 */
@Entity(tableName = "weekly_menus", indices = [Index(value = ["weekStart"], unique = true)])
data class WeeklyMenuEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStart: Long,
    val slotsSpec: String,
    // Declared defaults, so a migrated row and a freshly created one agree —
    // which is exactly what Room compares the two schemas on.
    @ColumnInfo(defaultValue = "'SHARED'") val scope: MenuScope = MenuScope.SHARED,
    @ColumnInfo(defaultValue = "''") val coursesSpec: String = "",
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

    fun slotCourses(): Map<MealSlot, List<MealCourse>> =
        coursesSpec.split(",").mapNotNull { part ->
            val pieces = part.split(":")
            if (pieces.size == 2) {
                val slot = runCatching { MealSlot.valueOf(pieces[0]) }.getOrNull()
                val courses = pieces[1].split("|")
                    .mapNotNull { runCatching { MealCourse.valueOf(it) }.getOrNull() }
                if (slot != null && courses.isNotEmpty()) slot to courses else null
            } else null
        }.toMap()

    companion object {
        fun encodeSlots(weights: Map<MealSlot, Float>): String =
            weights.entries.joinToString(",") { "${it.key.name}:${it.value}" }

        fun encodeCourses(courses: Map<MealSlot, List<MealCourse>>): String =
            courses.entries.joinToString(",") { (slot, list) ->
                "${slot.name}:${list.joinToString("|") { it.name }}"
            }
    }
}

/**
 * One dish on one day.
 *
 * [profileId] carries the scope: null means the whole household eats it,
 * a value means it belongs to that person alone and replaces whatever the
 * household is having in the same (day, slot, course). Locks inherit that
 * scope for free — a lock on a household row holds for everyone, a lock on a
 * profile row holds for that person.
 *
 * [recipeId] is null for a free (untracked) slot: the placeholder still
 * appears in the menu so the day reads correctly, but it has no ingredients
 * and contributes nothing to the shopping list.
 */
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
        ),
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("menuId"), Index("recipeId"), Index("profileId")]
)
data class PlannedMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val menuId: Long,
    /** 0 = Monday … 6 = Sunday. */
    val dayOfWeek: Int,
    val slot: MealSlot,
    val recipeId: Long?,
    val isLocked: Boolean = false,
    val isCheatMeal: Boolean = false,
    val course: MealCourse = MealCourse.SINGLE,
    /** null = the whole household; otherwise this person's own version. */
    val profileId: Long? = null,
    val isFree: Boolean = false
)

/**
 * Per-person portion of a planned meal. [factor] scales the base recipe:
 * grams for this person = ingredient.grams × factor.
 *
 * [baseFactor] is what the generator planned. [factor] is what the person is
 * actually served today, which drifts from it as they log food eaten outside
 * the plan — keeping both means every recalculation starts from the plan
 * rather than from the last adjustment.
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
    val factor: Double,
    @ColumnInfo(defaultValue = "0") val baseFactor: Double = factor
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
    val recipe: RecipeEntity?
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

/** A planned meal together with everyone's portion of it. */
data class PlannedMealWithPortions(
    @Embedded val meal: PlannedMealEntity,
    @Relation(parentColumn = "recipeId", entityColumn = "id")
    val recipe: RecipeEntity?,
    @Relation(parentColumn = "id", entityColumn = "plannedMealId")
    val portions: List<MealPortionEntity>
)

/** Converters for the menu enums. */
class MenuConverters {
    @TypeConverter fun slotToString(v: MealSlot) = v.name
    @TypeConverter fun stringToSlot(v: String) = MealSlot.valueOf(v)
    @TypeConverter fun scopeToString(v: MenuScope) = v.name
    @TypeConverter fun stringToScope(v: String) = MenuScope.valueOf(v)
    @TypeConverter fun courseToString(v: MealCourse) = v.name
    @TypeConverter fun stringToCourse(v: String) = MealCourse.valueOf(v)
}
