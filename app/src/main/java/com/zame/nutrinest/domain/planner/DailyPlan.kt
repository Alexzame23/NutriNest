package com.zame.nutrinest.domain.planner

import com.zame.nutrinest.data.local.entity.MealCourse
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.ProfileMealPlanEntity
import com.zame.nutrinest.domain.nutrition.NutritionCalculator

/**
 * One person's day, in calories.
 *
 * The shares are **absolute fractions of the daily target and are never
 * renormalised**. That is the whole point of a free slot: 3000 kcal split
 * 20/40/40 with dinner marked free leaves breakfast at 600 kcal and lunch at
 * 1200 kcal, and the remaining 1200 kcal is unplanned rather than pushed onto
 * the other two meals.
 *
 * Pure data — the generator, the intake rebalancer and the swap path all work
 * from this and nothing else, so "what should this person eat here" has a
 * single answer everywhere.
 */
data class DailyPlan(
    val profileId: Long,
    val dailyCalories: Int,
    /** Fraction of [dailyCalories] per slot, e.g. LUNCH to 0.4f. */
    val shares: Map<MealSlot, Float>,
    /** Slots this person leaves untracked. */
    val freeSlots: Set<MealSlot> = emptySet()
) {
    fun isFree(slot: MealSlot): Boolean = slot in freeSlots

    /** The absolute calorie budget of a slot, free or not. */
    fun slotKcal(slot: MealSlot): Double =
        dailyCalories * (shares[slot] ?: 0f).toDouble()

    /** A slot's budget split across its courses. */
    fun courseKcal(slot: MealSlot, course: MealCourse, courses: List<MealCourse>): Double =
        slotKcal(slot) * (MealCourse.shares(courses)[course] ?: 1.0)

    /** What the plan actually puts on a plate across the day. */
    val plannedKcal: Double
        get() = shares.entries.sumOf { (slot, share) ->
            if (isFree(slot)) 0.0 else dailyCalories * share.toDouble()
        }

    /** Budget that belongs to free slots: spent as the person likes, untracked. */
    val untrackedKcal: Double
        get() = shares.entries.sumOf { (slot, share) ->
            if (isFree(slot)) dailyCalories * share.toDouble() else 0.0
        }

    companion object {
        /**
         * Builds a plan from what the profile set up, filling in any slot the
         * person never configured from the menu's own distribution — so adding
         * a fifth meal to a week doesn't require every profile to be edited
         * first.
         */
        fun of(
            profile: ProfileEntity,
            rows: List<ProfileMealPlanEntity>,
            menuSlots: List<MealSlot>,
            fallbackWeights: Map<MealSlot, Float>
        ): DailyPlan {
            val configured = rows.associateBy { it.slot }
            val shares = menuSlots.associateWith { slot ->
                configured[slot]?.share ?: fallbackWeights[slot] ?: 0f
            }
            return DailyPlan(
                profileId = profile.id,
                dailyCalories = NutritionCalculator.targetsFor(profile).dailyCalories,
                shares = shares,
                freeSlots = menuSlots.filter { configured[it]?.isFree == true }.toSet()
            )
        }

        /** The rows a brand-new profile starts with, for [mealsPerDay] meals. */
        fun defaultRows(profileId: Long, mealsPerDay: Int): List<ProfileMealPlanEntity> {
            val slots = MealSlot.forMealsPerDay(mealsPerDay)
            val weights = MealSlot.defaultWeights(slots)
            return slots.map { slot ->
                ProfileMealPlanEntity(
                    profileId = profileId,
                    slot = slot,
                    share = weights[slot] ?: 0f
                )
            }
        }
    }
}
