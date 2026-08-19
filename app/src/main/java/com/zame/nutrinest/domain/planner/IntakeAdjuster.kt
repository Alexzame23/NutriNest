package com.zame.nutrinest.domain.planner

import com.zame.nutrinest.data.local.entity.MealSlot

/**
 * Keeps one person's day balanced when they eat something the plan didn't
 * put there.
 *
 * The rule: the day's planned calories are fixed. Whatever has already gone —
 * the meals whose time has passed, plus everything logged ad hoc — is
 * subtracted, and what is left is spread over the meals still to come in
 * proportion to how big they were meant to be. Nothing here touches another
 * profile, and free slots are excluded: they are untracked by definition, so
 * they neither absorb a surplus nor give calories back.
 *
 * Pure arithmetic, and always recomputed from the *planned* factors rather
 * than from the last adjustment, so logging three snacks and deleting two
 * lands exactly where logging one would have.
 */
object IntakeAdjuster {

    /** Portions are allowed to shrink further than the generator would plan. */
    const val MIN_FACTOR = 0.1
    const val MAX_FACTOR = 4.0

    /**
     * One person's share of one planned dish today.
     * [recipeKcal] is the whole recipe at factor 1.
     */
    data class PlannedPortion(
        val plannedMealId: Long,
        val slot: MealSlot,
        val baseFactor: Double,
        val factor: Double,
        val recipeKcal: Double,
        val isFree: Boolean = false
    )

    /**
     * @param hourOfDay local hour, 0–23. A slot counts as still to come while
     *   its nominal hour is ahead of it.
     * @return plannedMealId → new portion factor, for the meals that move.
     */
    fun rebalance(
        portions: List<PlannedPortion>,
        loggedKcal: Double,
        hourOfDay: Int
    ): Map<Long, Double> {
        val tracked = portions.filterNot { it.isFree }
        val (upcoming, past) = tracked.partition { it.slot.nominalHour > hourOfDay }
        val upcomingPlanned = upcoming.sumOf { it.baseFactor * it.recipeKcal }
        if (upcomingPlanned <= 0.0) return emptyMap()

        val dayPlanned = tracked.sumOf { it.baseFactor * it.recipeKcal }
        val alreadyEaten = past.sumOf { it.factor * it.recipeKcal } + loggedKcal
        val scale = (dayPlanned - alreadyEaten) / upcomingPlanned

        return upcoming.associate { portion ->
            portion.plannedMealId to
                (portion.baseFactor * scale).coerceIn(MIN_FACTOR, MAX_FACTOR)
        }
    }

    /** Back to exactly what was planned — for when a day's log is emptied. */
    fun resetToBase(portions: List<PlannedPortion>): Map<Long, Double> =
        portions.filterNot { it.isFree }.associate { it.plannedMealId to it.baseFactor }
}
