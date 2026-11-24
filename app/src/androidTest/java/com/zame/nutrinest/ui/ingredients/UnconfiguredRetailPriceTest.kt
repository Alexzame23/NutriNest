package com.zame.nutrinest.ui.ingredients

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.data.prices.PriceDiagnosis
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.ui.menu.shopping.ShoppingComparison
import com.zame.nutrinest.ui.menu.shopping.ShoppingPriceControls
import com.zame.nutrinest.ui.menu.shopping.ShoppingWeek
import kotlinx.coroutines.flow.MutableStateFlow
import com.zame.nutrinest.ui.ingredients.retail.RetailPriceContent
import com.zame.nutrinest.ui.ingredients.retail.RetailPriceViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class UnconfiguredRetailPriceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun coveredAreaShowsMissingPublisherWithTheInstalledConfiguration() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NutriNestApp
        val container = app.container
        assumeTrue(container.priceConfiguration == PriceDiagnosis.NO_BASE_URL)
        val postal = "28012"
        val ingredientId = runBlocking {
            val ids = container.ingredientRepository.observeAll().first().map { it.id }
            val dao = NutriNestDatabase.get(app).ingredientDao()
            ids.chunked(900).firstNotNullOf { batch ->
                dao.retailBedcaRecordKeys(batch).firstOrNull { it.recordKey.matches(Regex("[0-9]{1,32}")) }
            }.ingredientId
        }
        compose.setContent {
            val model = viewModel {
                RetailPriceViewModel(SavedStateHandle(mapOf("ingredientId" to ingredientId)), container.retailPrices)
            }
            val state by model.uiState.collectAsStateWithLifecycle()
            RetailPriceContent(state, model.lastPostalCode, {}, model::search)
        }
        compose.onNode(hasSetTextAction()).performTextReplacement(postal)
        compose.onNodeWithText(app.getString(R.string.retail_search)).performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("NO_BASE_URL")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("NO_BASE_URL").assertExists()
    }

    @Test fun shoppingControlsShowMissingPublisherForAnEligibleLine() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NutriNestApp
        val container = app.container
        assumeTrue(container.priceConfiguration == PriceDiagnosis.NO_BASE_URL)
        val ingredient = runBlocking { container.ingredientRepository.observeAll().first().first() }
        // Exercise the installed repository with one unsaved shopping line.
        val line = ShoppingItemEntity(id = 1, menuId = 1, ingredientId = ingredient.id, grams = 100.0)
        val week = MutableStateFlow(ShoppingWeek(0, listOf(line)))
        compose.setContent {
            val scope = rememberCoroutineScope()
            val comparison = remember {
                ShoppingComparison(scope, SavedStateHandle(), week, container.shoppingPrices,
                    selections = container.retailerSelectionRepository, directory = container.storeRepository)
            }
            val state by comparison.state.collectAsStateWithLifecycle()
            ShoppingPriceControls(state, comparison)
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("28012")
        compose.onNodeWithText(app.getString(R.string.shopping_prices_refresh)).performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("NO_BASE_URL")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("NO_BASE_URL").assertExists()
    }
}
