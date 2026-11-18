package com.zame.nutrinest.ui.ingredients

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zame.nutrinest.MainActivity
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetailPriceNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun commercialNavigationReturnsToNutritionDetail() {
        val activity = compose.activity
        compose.onNodeWithText(activity.getString(R.string.home_recipes_ingredients_title))
            .performScrollTo().performClick()
        compose.onNodeWithText(activity.getString(R.string.tab_ingredients)).performClick()
        val app = activity.application as NutriNestApp
        val ingredient = runBlocking {
            app.container.ingredientRepository.observeAll().first().first()
        }
        val localized = ingredient.displayName(activity.resources.configuration.locales[0].language == "es")
        val ingredientText = hasText(localized) and !hasSetTextAction()
        val verticalList = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        compose.onNode(hasSetTextAction()).performTextReplacement(localized)
        compose.waitUntil(30_000) { compose.onAllNodes(ingredientText).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(verticalList).performScrollToNode(ingredientText)
        compose.onNode(ingredientText).performClick()
        val macros = activity.getString(R.string.ingredient_macros)
        val retail = activity.getString(R.string.retail_title)
        val list = compose.onNode(verticalList)
        compose.waitUntil(30_000) { compose.onAllNodes(verticalList).fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasText(macros))
        compose.onNodeWithText(macros).assertExists()
        list.performScrollToIndex(0)
        list.performScrollToNode(hasText(retail))
        compose.onNodeWithText(retail).performClick()
        compose.onNodeWithText(activity.getString(R.string.retail_enter_area)).assertExists()
        compose.onNodeWithContentDescription(activity.getString(R.string.action_back)).performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(verticalList).fetchSemanticsNodes().isNotEmpty() }
        list.performScrollToNode(hasText(macros))
        compose.onNodeWithText(macros).assertExists()
        compose.onNodeWithContentDescription(activity.getString(R.string.action_compare)).performClick()
        compose.onNodeWithText(activity.getString(R.string.compare_title)).assertExists()
    }
}
