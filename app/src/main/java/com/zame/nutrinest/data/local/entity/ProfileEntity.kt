package com.zame.nutrinest.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.util.Calendar

enum class BiologicalSex(val label: String) {
    MALE("Male"),
    FEMALE("Female")
}

/** Weekly exercise level with the standard TDEE multipliers. */
enum class ActivityLevel(val label: String, val description: String, val multiplier: Double) {
    SEDENTARY("Sedentary", "Little or no exercise, desk job", 1.2),
    LIGHT("Lightly active", "Light exercise 1–3 days/week", 1.375),
    MODERATE("Moderately active", "Moderate exercise 3–5 days/week", 1.55),
    ACTIVE("Very active", "Hard exercise 6–7 days/week", 1.725),
    VERY_ACTIVE("Extremely active", "Hard daily exercise or physical job", 1.9)
}

enum class Goal(val label: String) {
    LOSE_WEIGHT("Lose weight"),
    MAINTAIN("Maintain weight"),
    GAIN_WEIGHT("Gain weight"),
    BUILD_MUSCLE("Build muscle"),
    IMPROVE_HEALTH("Improve overall health")
}

/**
 * How aggressive the calorie deficit should be when the goal is LOSE_WEIGHT.
 * Expressed as a daily deficit; the labels show the approximate weekly fat
 * loss (~7700 kcal per kg of body fat). Only applies to the losing goal.
 */
enum class WeightLossRate(val label: String, val kcalPerDayDeficit: Int) {
    GENTLE("Gentle · ~0.25 kg (0.5 lb)/week", 275),
    MODERATE("Moderate · ~0.5 kg (1 lb)/week", 550),
    AGGRESSIVE("Aggressive · ~0.75 kg (1.5 lb)/week", 825)
}

/** User-selectable BMR formula. AUTO picks the best fit for available data. */
enum class BmrFormula(val label: String) {
    AUTO("Automatic (recommended)"),
    MIFFLIN_ST_JEOR("Mifflin-St Jeor"),
    HARRIS_BENEDICT("Harris-Benedict (revised)"),
    KATCH_MCARDLE("Katch-McArdle (needs body fat %)")
}

enum class UnitSystem(val label: String) {
    METRIC("Metric (kg, cm)"),
    IMPERIAL("Imperial (lb, ft/in)")
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
