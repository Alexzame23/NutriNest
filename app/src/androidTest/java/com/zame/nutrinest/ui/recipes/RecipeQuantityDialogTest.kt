package com.zame.nutrinest.ui.recipes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zame.nutrinest.R
import com.zame.nutrinest.domain.nutrition.ConversionConfidence
import com.zame.nutrinest.domain.nutrition.ConversionEvidence
import com.zame.nutrinest.domain.nutrition.EvidenceStatus
import com.zame.nutrinest.domain.nutrition.FoodConversionFactor
import com.zame.nutrinest.domain.nutrition.MassBasis
import com.zame.nutrinest.domain.nutrition.RecipeMeasure
import com.zame.nutrinest.domain.nutrition.RecipeQuantity
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeQuantityDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun editingVolumeShowsEdibleWeightAndBlocksInvalidAmounts() {
        val source = ConversionEvidence(
            "fixture-density", "fixture://dialog", "test-v1",
            ConversionConfidence.HIGH, EvidenceStatus.ACCEPTED, "Synthetic edible density"
        )
        val milk = RecipeMeasure(
            UnitDefinition.MILLILITRE, "ml", isDefault = true,
            factor = FoodConversionFactor.Density(1L, 1.03, MassBasis.EDIBLE, source)
        )
        var saved: RecipeQuantity? = null
        lateinit var saveLabel: String
        compose.setContent {
            saveLabel = androidx.compose.ui.res.stringResource(R.string.action_save)
            MaterialTheme {
                EditRecipeQuantityDialog(
                    "Milk", milk.enter(200.0)!!, listOf(RecipeMeasure.GRAMS, milk),
                    spanish = false, onDismiss = {}, onSave = { saved = it }
                )
            }
        }
        val input = compose.onNode(hasSetTextAction())
        compose.onNodeWithText(saveLabel).assertIsNotEnabled()
        input.performTextReplacement("0")
        compose.onNodeWithText(saveLabel).assertIsNotEnabled()
        input.performTextReplacement("250")
        compose.onNodeWithText("250").assertExists()
        compose.onNodeWithText("257", substring = true).assertExists()
        compose.onNodeWithText(saveLabel).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(milk.enter(250.0), saved) }
    }
}
