package com.zame.nutrinest.domain.nutrition

import com.zame.nutrinest.data.local.entity.ActivityLevel
import com.zame.nutrinest.data.local.entity.BiologicalSex
import com.zame.nutrinest.data.local.entity.BmrFormula
import com.zame.nutrinest.data.local.entity.Goal
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.WeightLossRate
import kotlin.math.roundToInt

/**
 * Computed nutrition targets for a profile.
 * All energy values in kcal/day, macros in grams/day.
 */
data class NutritionTargets(
    val bmr: Int,
    val tdee: Int,
    val dailyCalories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /** The formula actually used (relevant when the profile is set to AUTO). */
    val formulaUsed: BmrFormula,
    /** Non-null when AUTO downgraded a choice, e.g. Katch-McArdle without body fat. */
    val note: String? = null
)

/**
 * Pure, stateless calculation engine. Deliberately has NO Android
 * dependencies so it's trivially unit-testable and reusable server-side
 * later (e.g. in a Cloud Function for the community features).
 *
 * To add a new formula: add an enum value, a branch in [bmrFor], done.
 */
object NutritionCalculator {

    fun targetsFor(profile: ProfileEntity): NutritionTargets {
        val (bmr, used, note) = bmrFor(profile)
        val tdee = bmr * profile.activityLevel.multiplier

        val dailyCalories = applyGoal(tdee, profile.goal, profile.weightLossRate)
        val macros = macrosFor(profile, dailyCalories)

        return NutritionTargets(
            bmr = bmr.roundToInt(),
            tdee = tdee.roundToInt(),
            dailyCalories = dailyCalories.roundToInt(),
            proteinG = macros.proteinG,
            carbsG = macros.carbsG,
            fatG = macros.fatG,
            formulaUsed = used,
            note = note
        )
    }

    // ── BMR ─────────────────────────────────────────────────────────────

    private fun bmrFor(p: ProfileEntity): Triple<Double, BmrFormula, String?> {
        val requested = p.formula
        return when (requested) {
            BmrFormula.AUTO -> {
                // Katch-McArdle is most accurate when body fat is known;
                // otherwise Mifflin-St Jeor outperforms Harris-Benedict in
                // most modern validation studies.
                if (p.bodyFatPercent != null) {
                    Triple(katchMcArdle(p), BmrFormula.KATCH_MCARDLE, null)
                } else {
                    Triple(mifflinStJeor(p), BmrFormula.MIFFLIN_ST_JEOR, null)
                }
            }
            BmrFormula.KATCH_MCARDLE -> {
                if (p.bodyFatPercent != null) {
                    Triple(katchMcArdle(p), BmrFormula.KATCH_MCARDLE, null)
                } else {
                    // Graceful fallback rather than a crash or a zero.
                    Triple(
                        mifflinStJeor(p),
                        BmrFormula.MIFFLIN_ST_JEOR,
                        "Katch-McArdle needs body fat %. Used Mifflin-St Jeor instead."
                    )
                }
            }
            BmrFormula.MIFFLIN_ST_JEOR -> Triple(mifflinStJeor(p), requested, null)
            BmrFormula.HARRIS_BENEDICT -> Triple(harrisBenedict(p), requested, null)
        }
    }

    /** Revised Harris-Benedict (Roza & Shizgal, 1984). */
    private fun harrisBenedict(p: ProfileEntity): Double = when (p.sex) {
        BiologicalSex.MALE ->
            88.362 + 13.397 * p.weightKg + 4.799 * p.heightCm - 5.677 * p.ageYears
        BiologicalSex.FEMALE ->
            447.593 + 9.247 * p.weightKg + 3.098 * p.heightCm - 4.330 * p.ageYears
    }

    /** Mifflin-St Jeor (1990) — generally the best predictor without body fat data. */
    private fun mifflinStJeor(p: ProfileEntity): Double {
        val base = 10.0 * p.weightKg + 6.25 * p.heightCm - 5.0 * p.ageYears
        return when (p.sex) {
            BiologicalSex.MALE -> base + 5
            BiologicalSex.FEMALE -> base - 161
        }
    }

    /** Katch-McArdle — uses lean body mass, so it needs body fat %. */
    private fun katchMcArdle(p: ProfileEntity): Double {
        val leanMassKg = p.weightKg * (1 - (p.bodyFatPercent ?: 0.0) / 100.0)
        return 370 + 21.6 * leanMassKg
    }

    // ── Goal adjustment ────────────────────────────────────────────────

    /**
     * Applies the goal to TDEE. For weight loss the deficit is the
     * user-selected [rate] (a fixed daily kcal deficit); gaining uses a
     * moderate surplus. Clamped so extreme inputs can't produce dangerously
     * low targets.
     */
    private fun applyGoal(tdee: Double, goal: Goal, rate: WeightLossRate): Double {
        val adjusted = when (goal) {
            Goal.LOSE_WEIGHT -> tdee - rate.kcalPerDayDeficit
            Goal.MAINTAIN -> tdee
            Goal.GAIN_WEIGHT -> tdee * 1.12
            Goal.BUILD_MUSCLE -> tdee * 1.10
            Goal.IMPROVE_HEALTH -> tdee
        }
        // Safety floor: never recommend below ~1200 kcal.
        return adjusted.coerceAtLeast(1200.0)
    }

    // ── Macros ─────────────────────────────────────────────────────────

    private data class Macros(val proteinG: Int, val carbsG: Int, val fatG: Int)

    /**
     * Protein is set in g/kg of body weight by goal (higher when losing
     * weight or building muscle to preserve/build lean mass), fat at ~27%
     * of calories, carbs fill the remainder.
     */
    private fun macrosFor(p: ProfileEntity, dailyCalories: Double): Macros {
        val proteinPerKg = when (p.goal) {
            Goal.BUILD_MUSCLE -> 2.0
            Goal.LOSE_WEIGHT -> 1.8
            Goal.GAIN_WEIGHT -> 1.6
            Goal.MAINTAIN, Goal.IMPROVE_HEALTH -> 1.4
        }
        val proteinG = (proteinPerKg * p.weightKg)
        val proteinKcal = proteinG * 4

        val fatKcal = dailyCalories * 0.27
        val fatG = fatKcal / 9

        val carbsKcal = (dailyCalories - proteinKcal - fatKcal).coerceAtLeast(0.0)
        val carbsG = carbsKcal / 4

        return Macros(proteinG.roundToInt(), carbsG.roundToInt(), fatG.roundToInt())
    }
}

// ── Unit conversion helpers (UI layer uses these for imperial input) ────

object UnitConversions {
    fun lbToKg(lb: Double) = lb * 0.45359237
    fun kgToLb(kg: Double) = kg / 0.45359237
    fun feetInchesToCm(feet: Int, inches: Double) = (feet * 12 + inches) * 2.54
    fun cmToFeetInches(cm: Double): Pair<Int, Double> {
        val totalInches = cm / 2.54
        val feet = (totalInches / 12).toInt()
        return feet to (totalInches - feet * 12)
    }
}
