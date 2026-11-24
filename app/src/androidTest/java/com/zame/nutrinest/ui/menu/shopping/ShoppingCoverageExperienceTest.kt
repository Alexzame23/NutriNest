package com.zame.nutrinest.ui.menu.shopping

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zame.nutrinest.data.prices.RetailBatchUnavailable
import com.zame.nutrinest.data.prices.ShoppingLinePrice
import com.zame.nutrinest.data.prices.ShoppingPrices
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ShoppingCoverageExperienceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun spanishShoppingCoverageDistinguishesAnEmptyAreaAndAnOfflineRefresh() {
        val publishedAt = Instant.parse("2026-11-19T12:00:00Z")
        val publication = ShoppingPrices.Published(
            mapOf(1L to ShoppingLinePrice.NoPrice), publishedAt, publishedAt.plusSeconds(3600),
            simulated = false
        )
        val state = mutableStateOf(ShoppingComparisonUiState(eligibleCount = 1))
        compose.setContent {
            val context = LocalContext.current
            val configuration = LocalConfiguration.current
            val spanish = remember(configuration) {
                Configuration(configuration).apply { setLocale(Locale.forLanguageTag("es-ES")) }
            }
            val resources = remember(context, spanish) { context.createConfigurationContext(spanish).resources }
            CompositionLocalProvider(LocalConfiguration provides spanish, LocalResources provides resources) {
                MaterialTheme { Column { ShoppingCoverageStatus(state.value) } }
            }
        }

        compose.onNodeWithText("Introduce un código postal", substring = true).assertIsDisplayed()
        compose.runOnIdle {
            state.value = state.value.copy(postalCode = "28012",
                status = ShoppingComparisonStatus.Published(publication))
        }
        compose.onNodeWithText("No hay precios comparables", substring = true).assertIsDisplayed()
        compose.runOnIdle {
            state.value = state.value.copy(status = ShoppingComparisonStatus.Published(
                publication.copy(fromCache = true, refreshIssue = RetailBatchUnavailable.Offline)
            ))
        }
        compose.onNodeWithText("No se ha podido conectar para actualizar los precios", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("No hay precios comparables", substring = true).assertDoesNotExist()
    }
}
