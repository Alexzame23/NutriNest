package com.zame.nutrinest.domain.planner

import com.zame.nutrinest.data.local.entity.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The day is 2000 kcal split 600 / 800 / 600. Every portion below is expressed
 * against a 500 kcal recipe, so a factor of 1.2 is 600 kcal on the plate.
 */
class IntakeAdjusterTest {

    private val recipeKcal = 500.0

    private fun portion(
        id: Long,
        slot: MealSlot,
        kcal: Double,
        served: Double = kcal,
        free: Boolean = false
    ) = IntakeAdjuster.PlannedPortion(
        plannedMealId = id,
        slot = slot,
        baseFactor = kcal / recipeKcal,
        factor = served / recipeKcal,
        recipeKcal = recipeKcal,
        isFree = free
    )

    private fun day() = listOf(
        portion(1, MealSlot.BREAKFAST, 600.0),
        portion(2, MealSlot.LUNCH, 800.0),
        portion(3, MealSlot.DINNER, 600.0)
    )

    /** Morning, before anything has been eaten. */
    private val earlyMorning = 7

    @Test
    fun `logging nothing changes nothing`() {
        val result = IntakeAdjuster.rebalance(day(), loggedKcal = 0.0, hourOfDay = earlyMorning)
        assertEquals(3, result.size)
        assertEquals(600.0 / recipeKcal, result.getValue(1), 0.0001)
        assertEquals(800.0 / recipeKcal, result.getValue(2), 0.0001)
    }

    @Test
    fun `a mid-morning snack comes out of the rest of the day`() {
        // 300 kcal at 10am: breakfast is gone, lunch and dinner absorb it.
        val result = IntakeAdjuster.rebalance(day(), loggedKcal = 300.0, hourOfDay = 10)

        assertFalse("breakfast has already been eaten", result.containsKey(1L))
        val servedLater = result.values.sumOf { it * recipeKcal }
        assertEquals(
            "600 eaten + 300 logged leaves 1100 for lunch and dinner",
            1100.0, servedLater, 0.01
        )
        assertTrue(
            "and the bigger meal takes the bigger cut",
            result.getValue(2) > result.getValue(3)
        )
    }

    @Test
    fun `the cut is proportional to how big each remaining meal was`() {
        val result = IntakeAdjuster.rebalance(day(), loggedKcal = 140.0, hourOfDay = 10)
        // 1400 kcal planned for lunch + dinner, minus 140 → a 10% haircut each.
        assertEquals(720.0 / recipeKcal, result.getValue(2), 0.0001)
        assertEquals(540.0 / recipeKcal, result.getValue(3), 0.0001)
    }

    @Test
    fun `free slots neither absorb nor give back calories`() {
        val withFreeDinner = listOf(
            portion(1, MealSlot.BREAKFAST, 600.0),
            portion(2, MealSlot.LUNCH, 800.0),
            portion(3, MealSlot.DINNER, 600.0, free = true)
        )
        val result = IntakeAdjuster.rebalance(withFreeDinner, loggedKcal = 200.0, hourOfDay = 10)

        assertFalse("the free slot is left out entirely", result.containsKey(3L))
        assertEquals(
            "so lunch alone carries the 200 kcal",
            600.0 / recipeKcal,
            result.getValue(2),
            0.0001
        )
    }

    @Test
    fun `a blown day floors the remaining portions instead of going negative`() {
        val result = IntakeAdjuster.rebalance(day(), loggedKcal = 5000.0, hourOfDay = 10)
        assertTrue(result.values.all { it == IntakeAdjuster.MIN_FACTOR })
    }

    @Test
    fun `nothing left to rebalance after the last meal of the day`() {
        assertTrue(
            IntakeAdjuster.rebalance(day(), loggedKcal = 400.0, hourOfDay = 23).isEmpty()
        )
    }

    @Test
    fun `recomputing from the plan makes repeated logging idempotent`() {
        // Two entries in one go, versus the same total logged at once.
        val together = IntakeAdjuster.rebalance(day(), loggedKcal = 250.0, hourOfDay = 10)

        // Apply the first adjustment, then recompute with the full day's total —
        // which is exactly what the repository does on every new entry.
        val firstPass = IntakeAdjuster.rebalance(day(), loggedKcal = 100.0, hourOfDay = 10)
        val afterFirst = day().map { p ->
            firstPass[p.plannedMealId]?.let { p.copy(factor = it) } ?: p
        }
        val stepwise = IntakeAdjuster.rebalance(afterFirst, loggedKcal = 250.0, hourOfDay = 10)

        assertEquals(together.keys, stepwise.keys)
        together.forEach { (id, factor) ->
            assertEquals(factor, stepwise.getValue(id), 0.0001)
        }
    }

    @Test
    fun `resetting hands every meal back its planned portion`() {
        val adjusted = day().map { it.copy(factor = 0.1) }
        val result = IntakeAdjuster.resetToBase(adjusted)
        assertEquals(600.0 / recipeKcal, result.getValue(1), 0.0001)
        assertEquals(800.0 / recipeKcal, result.getValue(2), 0.0001)
        assertEquals(600.0 / recipeKcal, result.getValue(3), 0.0001)
    }
}
