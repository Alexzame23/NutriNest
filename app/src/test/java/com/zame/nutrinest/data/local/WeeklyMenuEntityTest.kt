package com.zame.nutrinest.data.local

import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.WeeklyMenuEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WeeklyMenuEntityTest {

    @Test
    fun `slot weights survive a storage round trip`() {
        val weights = MealSlot.defaultWeights(MealSlot.forMealsPerDay(4))
        val decoded = WeeklyMenuEntity(
            weekStart = 0,
            slotsSpec = WeeklyMenuEntity.encodeSlots(weights)
        ).slotWeights()

        assertEquals(weights, decoded)
    }

    @Test
    fun `corrupt slot weights fall back to a safe day`() {
        val decoded = WeeklyMenuEntity(
            weekStart = 0,
            slotsSpec = "BREAKFAST:NaN,LUNCH:-1,DINNER:Infinity"
        ).slotWeights()
        val excessive = WeeklyMenuEntity(
            weekStart = 0,
            slotsSpec = "BREAKFAST:1,LUNCH:1,DINNER:1"
        ).slotWeights()
        val fallback = MealSlot.defaultWeights(MealSlot.forMealsPerDay(3))

        assertEquals(fallback, decoded)
        assertEquals(fallback, excessive)
        assertFalse(decoded.values.any { !it.isFinite() || it <= 0f })
    }
}
