package com.zame.nutrinest.ui.recipes

import androidx.compose.ui.Alignment
import com.zame.nutrinest.data.local.entity.RecipeImageCrop

/** Turns the persisted crop anchor into Compose's image alignment. */
fun String.toRecipeImageAlignment(): Alignment = when (this) {
    RecipeImageCrop.TOP_START -> Alignment.TopStart
    RecipeImageCrop.TOP_CENTER -> Alignment.TopCenter
    RecipeImageCrop.TOP_END -> Alignment.TopEnd
    RecipeImageCrop.CENTER_START -> Alignment.CenterStart
    RecipeImageCrop.CENTER_END -> Alignment.CenterEnd
    RecipeImageCrop.BOTTOM_START -> Alignment.BottomStart
    RecipeImageCrop.BOTTOM_CENTER -> Alignment.BottomCenter
    RecipeImageCrop.BOTTOM_END -> Alignment.BottomEnd
    else -> Alignment.Center
}
