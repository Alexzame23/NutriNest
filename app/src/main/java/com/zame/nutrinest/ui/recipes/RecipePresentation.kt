package com.zame.nutrinest.ui.recipes

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DinnerDining
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.RiceBowl
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.RecipeCategories
import com.zame.nutrinest.ui.theme.Amber
import com.zame.nutrinest.ui.theme.Apricot
import com.zame.nutrinest.ui.theme.Caramel
import com.zame.nutrinest.ui.theme.PumpkinDeep
import com.zame.nutrinest.ui.theme.Sage

/**
 * Presentation for the recipe courses.
 *
 * `recipes.category` stores the English wording as its key, so the label has to
 * be looked up rather than displayed — otherwise the Spanish build shows
 * "Main Dish" in the middle of a translated screen. Colours and icons mirror
 * what the ingredient categories do, so both tabs read as one section.
 */

@StringRes
fun recipeCategoryLabel(category: String?): Int = when (category) {
    RecipeCategories.BREAKFAST -> R.string.recipe_category_breakfast
    RecipeCategories.MAIN_DISH -> R.string.recipe_category_main_dish
    RecipeCategories.SIDE_DISH -> R.string.recipe_category_side_dish
    RecipeCategories.DESSERT -> R.string.recipe_category_dessert
    RecipeCategories.SNACK -> R.string.recipe_category_snack
    RecipeCategories.DRINK -> R.string.recipe_category_drink
    else -> R.string.recipe_category_other
}

fun recipeCategoryColor(category: String?): Color = when (category) {
    RecipeCategories.BREAKFAST -> Amber
    RecipeCategories.MAIN_DISH -> PumpkinDeep
    RecipeCategories.SIDE_DISH -> Sage
    RecipeCategories.DESSERT -> Apricot
    RecipeCategories.SNACK -> Caramel
    RecipeCategories.DRINK -> Color(0xFF5F7D8C)
    else -> Color(0xFF8C7B6B)
}

fun recipeCategoryIcon(category: String?): ImageVector = when (category) {
    RecipeCategories.BREAKFAST -> Icons.Rounded.WbSunny
    RecipeCategories.MAIN_DISH -> Icons.Rounded.DinnerDining
    RecipeCategories.SIDE_DISH -> Icons.Rounded.RiceBowl
    RecipeCategories.DESSERT -> Icons.Rounded.Cake
    RecipeCategories.SNACK -> Icons.Rounded.Cookie
    else -> Icons.Rounded.LocalCafe
}
