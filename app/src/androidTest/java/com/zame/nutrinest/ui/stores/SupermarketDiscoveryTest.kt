package com.zame.nutrinest.ui.stores

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zame.nutrinest.MainActivity
import com.zame.nutrinest.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupermarketDiscoveryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun mergedManifestOnlyRequestsCoarseLocation() {
        val context = compose.activity
        @Suppress("DEPRECATION")
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().filter { it.startsWith("android.permission.") && it.endsWith("_LOCATION") }
        assertEquals(listOf(Manifest.permission.ACCESS_COARSE_LOCATION), requested)
    }

    @Test fun manualDiscoveryFromHomeWithNoLocationRequest() {
        val activity = compose.activity
        compose.onNodeWithText(activity.getString(R.string.supermarkets_title)).performScrollTo().performClick()
        val query = compose.onNode(hasSetTextAction())
        val search = compose.onNodeWithText(activity.getString(R.string.supermarkets_search))
        val list = compose.onNode(hasScrollToIndexAction())
        query.performTextReplacement("08001")
        search.performClick()
        val noService = activity.getString(R.string.supermarkets_no_service)
        list.performScrollToNode(hasText(noService))
        compose.onNodeWithText(noService).assertExists()

        query.performScrollTo()
        query.performTextReplacement("28012")
        search.performClick()
        val store = isSelectable() and hasText("Carrefour Market Lavapiés")
        list.performScrollToNode(store)
        compose.onNode(store).performClick().assertIsSelected()
        val noPrices = activity.getString(R.string.supermarkets_no_store_prices)
        list.performScrollToNode(hasText(noPrices))
        compose.onNodeWithText(noPrices).assertExists()
    }
}
