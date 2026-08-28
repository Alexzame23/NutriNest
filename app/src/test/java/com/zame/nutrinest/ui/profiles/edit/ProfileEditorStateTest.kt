package com.zame.nutrinest.ui.profiles.edit

import com.zame.nutrinest.data.local.entity.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileEditorStateTest {

    @Test
    fun `meal plan rows preserve shares and free slots`() {
        val state = ProfileEditorState(
            mealsPerDay = 3,
            slotShares = mapOf(
                MealSlot.BREAKFAST to 0.2f,
                MealSlot.LUNCH to 0.4f,
                MealSlot.DINNER to 0.4f
            ),
            freeSlots = setOf(MealSlot.DINNER)
        )

        val rows = state.mealPlanRows(profileId = 42).associateBy { it.slot }

        assertEquals(42L, rows.getValue(MealSlot.BREAKFAST).profileId)
        assertEquals(0.4f, rows.getValue(MealSlot.LUNCH).share, 0.0001f)
        assertTrue(rows.getValue(MealSlot.DINNER).isFree)
    }

    @Test
    fun `meal split rejects materially incomplete or excessive days`() {
        assertTrue(ProfileEditorState().isMealSplitValid)
        assertFalse(
            ProfileEditorState(
                slotShares = MealSlot.defaultWeights(MealSlot.forMealsPerDay(3)) +
                    (MealSlot.LUNCH to 0.1f)
            ).isMealSplitValid
        )
        assertFalse(
            ProfileEditorState(
                slotShares = MealSlot.defaultWeights(MealSlot.forMealsPerDay(3)) +
                    (MealSlot.LUNCH to 0.8f)
            ).isMealSplitValid
        )
    }
}
