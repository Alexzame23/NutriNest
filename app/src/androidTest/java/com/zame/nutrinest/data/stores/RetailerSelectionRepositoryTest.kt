package com.zame.nutrinest.data.stores

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.stores.RetailerSelectionPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RetailerSelectionRepositoryTest {
    @Test
    fun selectionsSurviveReopeningAndResetRestoresDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "retailer_selection_test_${UUID.randomUUID()}"
        val storage = context.getSharedPreferences(name, Context.MODE_PRIVATE)
        val reopenedName = "${name}_reopened"
        val a = CommercialId("a")
        val b = CommercialId("b")
        val c = CommercialId("c")
        val branch = CommercialId("a.local")
        try {
            val first = RetailerSelectionRepository(storage)
            assertEquals(RetailerSelectionPreferences(), first.preferences.value)
            val twoChains = RetailerSelectionPreferences(setOf(a, b), mapOf(a to setOf(branch)))
            first.update { twoChains }
            assertTrue(storage.edit().commit())

            // Read the persisted bytes through a fresh SharedPreferences instance, bypassing Android's cache.
            val preferencesDir = File(context.applicationInfo.dataDir, "shared_prefs")
            File(preferencesDir, "$name.xml").copyTo(File(preferencesDir, "$reopenedName.xml"))
            val reopenedStorage = context.getSharedPreferences(reopenedName, Context.MODE_PRIVATE)
            val reopened = RetailerSelectionRepository(reopenedStorage)
            assertEquals(twoChains, reopened.preferences.value)
            assertTrue(reopened.preferences.value.includes(a, branch))
            assertFalse(reopened.preferences.value.includes(a, CommercialId("a.other")))
            assertFalse(reopened.preferences.value.includes(c, CommercialId("c.local")))

            val none = twoChains.copy(selectedRetailers = emptySet(), selectedStores = mapOf(b to emptySet()))
            reopened.update { none }
            assertTrue(reopenedStorage.edit().commit())
            val reopenedAgain = RetailerSelectionRepository(reopenedStorage)
            assertEquals(none, reopenedAgain.preferences.value)
            assertFalse(reopenedAgain.preferences.value.includes(b, CommercialId("b.local")))

            reopenedAgain.update { RetailerSelectionPreferences() }
            assertTrue(reopenedStorage.edit().commit())
            assertEquals(RetailerSelectionPreferences(), RetailerSelectionRepository(reopenedStorage).preferences.value)
        } finally {
            context.deleteSharedPreferences(reopenedName)
            context.deleteSharedPreferences(name)
        }
    }
}
