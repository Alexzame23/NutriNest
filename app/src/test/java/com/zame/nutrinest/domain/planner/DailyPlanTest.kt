package com.zame.nutrinest.domain.planner

import com.zame.nutrinest.data.local.entity.ActivityLevel
import com.zame.nutrinest.data.local.entity.BiologicalSex
import com.zame.nutrinest.data.local.entity.Goal
import com.zame.nutrinest.data.local.entity.MealCourse
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.ProfileMealPlanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyPlanTest {

    /** The worked example from the spec: 3000 kcal at 20 / 40 / 40. */
    private fun plan(freeSlots: Set<MealSlot> = emptySet()) = DailyPlan(
        profileId = 1,
        dailyCalories = 3000,
        shares = mapOf(
            MealSlot.BREAKFAST to 0.2f,
            MealSlot.LUNCH to 0.4f,
            MealSlot.DINNER to 0.4f
        ),
        freeSlots = freeSlots
    )

    @Test
    fun `slots get their absolute share of the day`() {
        val plan = plan()
        assertEquals(600.0, plan.slotKcal(MealSlot.BREAKFAST), 0.001)
        assertEquals(1200.0, plan.slotKcal(MealSlot.LUNCH), 0.001)
        assertEquals(3000.0, plan.plannedKcal, 0.001)
    }

    @Test
    fun `a free slot is not redistributed over the others`() {
        val plan = plan(freeSlots = setOf(MealSlot.DINNER))

        assertEquals("breakfast stays at 600", 600.0, plan.slotKcal(MealSlot.BREAKFAST), 0.001)
        assertEquals("lunch stays at 1200", 1200.0, plan.slotKcal(MealSlot.LUNCH), 0.001)
        assertEquals("1800 kcal is planned", 1800.0, plan.plannedKcal, 0.001)
        assertEquals("and 1200 is simply untracked", 1200.0, plan.untrackedKcal, 0.001)
        assertTrue(plan.isFree(MealSlot.DINNER))
    }

    @Test
    fun `courses split the slot budget and nothing else`() {
        val plan = plan()
        val courses = listOf(MealCourse.FIRST, MealCourse.MAIN)

        val first = plan.courseKcal(MealSlot.LUNCH, MealCourse.FIRST, courses)
        val main = plan.courseKcal(MealSlot.LUNCH, MealCourse.MAIN, courses)
        assertEquals(480.0, first, 0.001)
        assertEquals(720.0, main, 0.001)
        assertEquals("the slot is fully distributed", 1200.0, first + main, 0.001)
    }

    @Test
    fun `a dessert comes out of the slot, not on top of it`() {
        val plan = plan()
        val courses = listOf(MealCourse.FIRST, MealCourse.MAIN, MealCourse.DESSERT)
        val total = courses.sumOf { plan.courseKcal(MealSlot.DINNER, it, courses) }
        assertEquals(1200.0, total, 0.001)
    }

    @Test
    fun `a slot the profile never configured falls back to the menu's own split`() {
        val plan = DailyPlan.of(
            profile = profile(),
            rows = listOf(
                ProfileMealPlanEntity(profileId = 1, slot = MealSlot.BREAKFAST, share = 0.5f)
            ),
            menuSlots = PlannerFixtures.THREE_SLOTS,
            fallbackWeights = mapOf(
                MealSlot.BREAKFAST to 0.3f, MealSlot.LUNCH to 0.4f, MealSlot.DINNER to 0.3f
            )
        )

        assertEquals("the configured slot wins", 0.5f, plan.shares.getValue(MealSlot.BREAKFAST))
        assertEquals("the rest come from the menu", 0.4f, plan.shares.getValue(MealSlot.LUNCH))
    }

    @Test
    fun `free slots are read off the profile's own setup`() {
        val plan = DailyPlan.of(
            profile = profile(),
            rows = listOf(
                ProfileMealPlanEntity(1, MealSlot.LUNCH, share = 0.4f, isFree = true)
            ),
            menuSlots = PlannerFixtures.THREE_SLOTS,
            fallbackWeights = MealSlot.defaultWeights(PlannerFixtures.THREE_SLOTS)
        )
        assertEquals(setOf(MealSlot.LUNCH), plan.freeSlots)
    }

    @Test
    fun `a new profile starts on the standard split`() {
        val rows = DailyPlan.defaultRows(profileId = 3, mealsPerDay = 3)
        assertEquals(3, rows.size)
        assertEquals(1.0f, rows.map { it.share }.sum(), 0.001f)
        assertTrue("and nothing is free until the user says so", rows.none { it.isFree })
    }

    private fun profile() = ProfileEntity(
        id = 1,
        name = "Ana",
        dateOfBirth = 0,
        sex = BiologicalSex.FEMALE,
        heightCm = 165.0,
        weightKg = 60.0,
        activityLevel = ActivityLevel.MODERATE,
        goal = Goal.MAINTAIN
    )
}
