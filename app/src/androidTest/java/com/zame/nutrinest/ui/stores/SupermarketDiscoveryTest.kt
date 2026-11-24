package com.zame.nutrinest.ui.stores

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
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

    @Test fun recentPostcodesAreBoundedDeduplicatedAndRestored() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val prefs = com.zame.nutrinest.data.stores.AreaPreferences(context)
        listOf("01001", "15001", "41001", "51001", "52001", "08001", "15001", "40.4,-3.7").forEach(prefs::select)
        assertEquals(listOf("15001", "08001", "52001", "51001", "41001"),
            com.zame.nutrinest.data.stores.AreaPreferences(context).recent)
    }

    @Test fun deniedApproximateLocationReturnsWithoutBlockingPostalSearch() {
        val context = compose.activity.applicationContext
        val location = com.zame.nutrinest.data.stores.ApproximateLocation(context)
        org.junit.Assume.assumeFalse(location.hasPermission())
        org.junit.Assert.assertNull(kotlinx.coroutines.runBlocking { location.locate() })
        org.junit.Assert.assertTrue(com.zame.nutrinest.domain.stores.validSpanishPostalCode("15001"))
    }

    @Test fun manualDiscoveryFromHomeWithNoLocationRequest() {
        val activity = compose.activity
        val app = activity.application as com.zame.nutrinest.NutriNestApp
        org.junit.Assume.assumeTrue(!app.container.publisherConfigured)
        compose.onNodeWithText(activity.getString(R.string.supermarkets_title)).performScrollTo().performClick()
        val query = compose.onNode(hasSetTextAction())
        val search = compose.onNodeWithText(activity.getString(R.string.supermarkets_search))
        val list = compose.onNode(hasScrollToIndexAction())
        query.performTextReplacement("08001")
        search.performClick()
        val noService = activity.getString(R.string.geo_lookup_failed)
        list.performScrollToNode(hasText(noService))
        compose.onNodeWithText(noService).assertExists()

        query.performScrollTo()
        query.performTextReplacement("28012")
        search.performClick()
        list.performScrollToNode(hasText(noService))
        compose.onNodeWithText(noService).assertExists()
    }
}
