package com.zame.nutrinest.ui.menu.shopping

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.local.entity.ShoppingItemWithIngredient
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import com.zame.nutrinest.ui.menu.NaturalQuantity
import com.zame.nutrinest.ui.menu.ShoppingQuantity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShoppingItemTextTest {
    @get:Rule val compose = createComposeRule()

    @Test fun checkedRowStillDisplaysNormalizedNameNeededWeightAndWholePieceEstimate() {
        val piece = NaturalQuantity(2.4, UnitDefinition.count("large"), "large egg", "huevo grande")
        val row = ShoppingRow(
            ShoppingItemWithIngredient(
                ShoppingItemEntity(id = 1, menuId = 1, ingredientId = 1, grams = 120.0, purchased = true),
                IngredientEntity(id = 1, name = "Old egg label", categoryKey = IngredientCategories.EGGS, nutrition = NutritionValues())
            ),
            "Whole chicken egg, poached",
            "Huevo entero de gallina, escalfado",
            ShoppingQuantity(120.0, piece, piece.copy(amount = 3.0))
        )
        compose.setContent { MaterialTheme { ShoppingItemText(row, spanish = true) } }

        compose.onNodeWithText("Huevo entero de gallina, escalfado").assertExists()
        compose.onNodeWithText("120 g", substring = true).assertExists()
        compose.onNodeWithText("3 × huevo grande", substring = true).assertExists()
        compose.onNodeWithText("Old egg label").assertDoesNotExist()
    }
}
