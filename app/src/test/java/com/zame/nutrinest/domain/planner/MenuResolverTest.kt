package com.zame.nutrinest.domain.planner

import com.zame.nutrinest.data.local.entity.MealCourse
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.PlannedMealEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuResolverTest {

    private fun meal(
        id: Long,
        day: Int,
        slot: MealSlot,
        profileId: Long? = null,
        course: MealCourse = MealCourse.SINGLE,
        free: Boolean = false
    ) = PlannedMealEntity(
        id = id,
        menuId = 1,
        dayOfWeek = day,
        slot = slot,
        recipeId = if (free) null else id,
        course = course,
        profileId = profileId,
        isFree = free
    )

    private fun resolve(meals: List<PlannedMealEntity>, profileId: Long) =
        MenuResolver.forProfile(meals, profileId) { it }.map { it.id }

    @Test
    fun `a fully shared week is everybody's week`() {
        val meals = listOf(
            meal(1, 0, MealSlot.LUNCH),
            meal(2, 0, MealSlot.DINNER)
        )
        assertEquals(listOf(1L, 2L), resolve(meals, profileId = 7))
    }

    @Test
    fun `an override replaces the household dish for that person only`() {
        val meals = listOf(
            meal(1, 1, MealSlot.LUNCH),                  // household
            meal(2, 1, MealSlot.LUNCH, profileId = 2),   // the child, at school
            meal(3, 1, MealSlot.DINNER)                  // shared again
        )
        assertEquals("the child eats their own lunch", listOf(2L, 3L), resolve(meals, 2))
        assertEquals("everybody else keeps the shared one", listOf(1L, 3L), resolve(meals, 1))
    }

    @Test
    fun `overriding a slot takes the whole slot, courses included`() {
        // Somebody eating apart at lunch should not end up with the household's
        // first course and their own main.
        val meals = listOf(
            meal(1, 0, MealSlot.LUNCH, course = MealCourse.FIRST),
            meal(2, 0, MealSlot.LUNCH, course = MealCourse.MAIN),
            meal(3, 0, MealSlot.LUNCH, profileId = 2, course = MealCourse.FIRST),
            meal(4, 0, MealSlot.LUNCH, profileId = 2, course = MealCourse.MAIN)
        )
        assertEquals(listOf(3L, 4L), resolve(meals, 2))
        assertEquals(listOf(1L, 2L), resolve(meals, 1))
    }

    @Test
    fun `a free slot hides the household dish from the person who freed it`() {
        val meals = listOf(
            meal(1, 3, MealSlot.DINNER),
            meal(2, 3, MealSlot.DINNER, profileId = 5, free = true)
        )
        assertEquals(listOf(2L), resolve(meals, 5))
        assertEquals(listOf(1L), resolve(meals, 6))
    }

    @Test
    fun `somebody else's meal is never anybody's`() {
        val meals = listOf(meal(1, 0, MealSlot.LUNCH, profileId = 9))
        assertTrue(resolve(meals, 1).isEmpty())
    }

    @Test
    fun `overrides are reported for the whole week`() {
        assertFalse(MenuResolver.hasOverrides(listOf(meal(1, 0, MealSlot.LUNCH))))
        assertTrue(
            MenuResolver.hasOverrides(
                listOf(meal(1, 0, MealSlot.LUNCH), meal(2, 0, MealSlot.DINNER, profileId = 3))
            )
        )
    }
}
