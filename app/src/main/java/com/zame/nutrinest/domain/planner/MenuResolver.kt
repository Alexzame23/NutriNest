package com.zame.nutrinest.domain.planner

import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.PlannedMealEntity

/**
 * The one rule that decides what a given person is actually eating.
 *
 * A menu holds household rows (`profileId == null`) and per-person rows side
 * by side. A person eats their own rows wherever they have any for a slot, and
 * the household's everywhere else — which is exactly what "shared by default,
 * overridden per meal" means, and falls out to the right answer for a fully
 * shared or fully individual week without special-casing either.
 */
object MenuResolver {

    private data class SlotKey(val day: Int, val slot: MealSlot)

    /** Filters [items] down to what [profileId] eats, keeping the input order. */
    fun <T> forProfile(
        items: List<T>,
        profileId: Long,
        meal: (T) -> PlannedMealEntity
    ): List<T> {
        val overridden = items.asSequence()
            .map(meal)
            .filter { it.profileId == profileId }
            .map { SlotKey(it.dayOfWeek, it.slot) }
            .toSet()

        return items.filter {
            val row = meal(it)
            when (row.profileId) {
                profileId -> true
                null -> SlotKey(row.dayOfWeek, row.slot) !in overridden
                else -> false
            }
        }
    }

    /** True when anyone in the household eats apart at some point this week. */
    fun hasOverrides(meals: List<PlannedMealEntity>): Boolean =
        meals.any { it.profileId != null }
}
