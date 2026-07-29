package com.zame.nutrinest.data.local.entity

import androidx.annotation.StringRes
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.zame.nutrinest.R
import java.util.Calendar

enum class BiologicalSex(@StringRes val labelRes: Int) {
    MALE(R.string.sex_male),
    FEMALE(R.string.sex_female)
}

/** Weekly exercise level with the standard TDEE multipliers. */
enum class ActivityLevel(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val multiplier: Double
) {
    SEDENTARY(R.string.activity_sedentary, R.string.activity_sedentary_detail, 1.2),
    LIGHT(R.string.activity_light, R.string.activity_light_detail, 1.375),
    MODERATE(R.string.activity_moderate, R.string.activity_moderate_detail, 1.55),
    ACTIVE(R.string.activity_active, R.string.activity_active_detail, 1.725),
    VERY_ACTIVE(R.string.activity_very_active, R.string.activity_very_active_detail, 1.9)
}

enum class Goal(@StringRes val labelRes: Int) {
    LOSE_WEIGHT(R.string.goal_lose_weight),
    MAINTAIN(R.string.goal_maintain),
    GAIN_WEIGHT(R.string.goal_gain_weight),
    BUILD_MUSCLE(R.string.goal_build_muscle),
    IMPROVE_HEALTH(R.string.goal_improve_health)
}

/**
 * How aggressive the calorie deficit should be when the goal is LOSE_WEIGHT.
 * Expressed as a daily deficit; the labels show the approximate weekly fat
 * loss (~7700 kcal per kg of body fat). Only applies to the losing goal.
 */
enum class WeightLossRate(@StringRes val labelRes: Int, val kcalPerDayDeficit: Int) {
    GENTLE(R.string.weight_loss_gentle, 275),
    MODERATE(R.string.weight_loss_moderate, 550),
    AGGRESSIVE(R.string.weight_loss_aggressive, 825)
}

/** User-selectable BMR formula. AUTO picks the best fit for available data. */
enum class BmrFormula(@StringRes val labelRes: Int) {
    AUTO(R.string.formula_auto),
    MIFFLIN_ST_JEOR(R.string.formula_mifflin),
    HARRIS_BENEDICT(R.string.formula_harris),
    KATCH_MCARDLE(R.string.formula_katch)
}

enum class UnitSystem(@StringRes val labelRes: Int) {
    METRIC(R.string.units_metric),
    IMPERIAL(R.string.units_imperial)
}

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Epoch millis. Age is always derived from this, never stored. */
    val dateOfBirth: Long,
    val sex: BiologicalSex,
    /** Canonical storage is ALWAYS metric; the UI converts for imperial users. */
    val heightCm: Double,
    val weightKg: Double,
    val bodyFatPercent: Double? = null,
    val activityLevel: ActivityLevel,
    val goal: Goal,
    val weightLossRate: WeightLossRate = WeightLossRate.MODERATE,
    val formula: BmrFormula = BmrFormula.AUTO,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val dietaryPreferences: String? = null,
    val allergies: String? = null,
    /** Index into the avatar palette for the profile card. */
    val colorIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Future cloud sync / community hook, same pattern as recipes. */
    val remoteId: String? = null
) {
    val ageYears: Int
        get() {
            val dob = Calendar.getInstance().apply { timeInMillis = dateOfBirth }
            val now = Calendar.getInstance()
            var age = now.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (now.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--
            return age.coerceAtLeast(0)
        }
}

/**
 * Weight history. Every time a profile's weight is updated we also append
 * a row here, so the future progress-monitoring feature has data from day one.
 */
@Entity(
    tableName = "weight_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val weightKg: Double,
    val recordedAt: Long = System.currentTimeMillis()
)

/** Room type converters for the profile enums. */
class ProfileConverters {
    @TypeConverter fun sexToString(v: BiologicalSex) = v.name
    @TypeConverter fun stringToSex(v: String) = BiologicalSex.valueOf(v)
    @TypeConverter fun activityToString(v: ActivityLevel) = v.name
    @TypeConverter fun stringToActivity(v: String) = ActivityLevel.valueOf(v)
    @TypeConverter fun goalToString(v: Goal) = v.name
    @TypeConverter fun stringToGoal(v: String) = Goal.valueOf(v)
    @TypeConverter fun lossRateToString(v: WeightLossRate) = v.name
    @TypeConverter fun stringToLossRate(v: String) = WeightLossRate.valueOf(v)
    @TypeConverter fun formulaToString(v: BmrFormula) = v.name
    @TypeConverter fun stringToFormula(v: String) = BmrFormula.valueOf(v)
    @TypeConverter fun unitToString(v: UnitSystem) = v.name
    @TypeConverter fun stringToUnit(v: String) = UnitSystem.valueOf(v)
}
