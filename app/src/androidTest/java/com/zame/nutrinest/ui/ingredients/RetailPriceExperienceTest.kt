package com.zame.nutrinest.ui.ingredients

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.R
import com.zame.nutrinest.ui.ingredients.retail.RetailPriceContent
import com.zame.nutrinest.ui.ingredients.retail.RetailUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class RetailPriceExperienceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun slowRequestAnnouncesStatusThenAllowsRetryAfterNetworkFailure() {
        val state = mutableStateOf<RetailUiState>(RetailUiState.Loading)
        var retries = 0
        compose.setContent {
            MaterialTheme {
                RetailPriceContent(state.value, "28012", {}, {
                    assertEquals("28012", it)
                    retries++
                    state.value = RetailUiState.Loading
                })
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun label(id: Int) = context.getString(id)
        compose.onNodeWithText(label(R.string.retail_search)).assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(2_600)
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(label(R.string.retail_slow)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion)).assertExists()
        compose.runOnIdle { state.value = RetailUiState.Offline }
        compose.onNodeWithText(label(R.string.retail_offline)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.retail_retry)).performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            state.value = RetailUiState.Error()
        }
        compose.onNodeWithText(label(R.string.retail_error)).assertIsDisplayed()
    }

    @Test fun spanishStaleMessageAndBackRemainReachableOnCompactLargeTextLayout() {
        val back = mutableStateOf(false)
        compose.setContent {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val spanish = remember(context, configuration) {
                Configuration(configuration).apply {
                    setLocale(Locale.forLanguageTag("es-ES"))
                    orientation = Configuration.ORIENTATION_LANDSCAPE
                }
            }
            val localized = remember(context, spanish) { context.createConfigurationContext(spanish) }
            CompositionLocalProvider(
                LocalConfiguration provides spanish,
                LocalResources provides localized.resources,
                LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)
            ) {
                MaterialTheme {
                    Box(Modifier.size(520.dp, 260.dp)) {
                        RetailPriceContent(RetailUiState.Stale, "28012", { back.value = true }, {})
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("es-ES"))
        })
        val stale = localized.getString(R.string.retail_stale)
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText(stale))
        compose.onNodeWithText(stale).assertIsDisplayed()
        compose.onNodeWithContentDescription(localized.getString(R.string.action_back)).performClick()
        compose.runOnIdle { assertEquals(true, back.value) }
    }
    @Test fun disabledRouteShowsEnglishExplanationWithoutSearch() {
        compose.setContent {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val english = remember(configuration) {
                Configuration(configuration).apply { setLocale(Locale.US) }
            }
            val resources = remember(context, english) { context.createConfigurationContext(english).resources }
            CompositionLocalProvider(LocalConfiguration provides english, LocalResources provides resources) {
                MaterialTheme {
                    RetailPriceContent(RetailUiState.EnterArea, "", {}, { error("Unexpected request") },
                        enabled = false)
                }
            }
        }
        compose.onNodeWithText("Commercial prices are disabled in this version.").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

}
