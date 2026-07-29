package com.zame.nutrinest.ui.ingredients

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Grass
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.LunchDining
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.SetMeal
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.DataQuality
import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.NutrientGroups
import com.zame.nutrinest.data.local.entity.PrepState
import com.zame.nutrinest.data.local.entity.RawSafety
import java.util.Locale

/**
 * Turns the food dataset's data-driven labels, colours and icon keys into
 * something Compose can draw.
 *
 * Names come from the dataset (it ships Spanish and English side by side), so
 * they are chosen by locale rather than looked up in `strings.xml`. Everything
 * the app writes itself is a string resource.
 */

/** True when the device is set to Spanish, so dataset names use `nameEs`. */
@Composable
fun rememberIsSpanish(): Boolean {
    val configuration = LocalConfiguration.current
    return remember(configuration) { configuration.locales[0].language == "es" }
}

/** Non-Composable counterpart, for ViewModels that sort or filter by name. */
fun isSpanishLocale(locale: Locale = Locale.getDefault()): Boolean = locale.language == "es"

/**
 * Category colour from the dataset's `color_hex`, falling back to the built-in
 * palette when the reference table has not been read yet.
 */
fun categoryColor(categoryKey: String, colorHex: String? = null): Color {
    val parsed = colorHex?.removePrefix("#")?.takeIf { it.length == 6 }
        ?.toLongOrNull(16)?.let { 0xFF000000L or it }
    val value = parsed ?: IngredientCategories.FALLBACK_COLORS[categoryKey] ?: 0xFF8C7B6BL
    return Color(value)
}

/**
 * Icon per category. Keyed by category rather than by the dataset's `icon_key`
 * so an unfamiliar key from a future dataset degrades to a sensible default
 * instead of drawing nothing.
 */
fun categoryIcon(categoryKey: String): ImageVector = when (categoryKey) {
    IngredientCategories.CEREALS -> Icons.Rounded.Grain
    IngredientCategories.VEGETABLES -> Icons.Rounded.Eco
    IngredientCategories.FRUITS -> Icons.Rounded.Spa
    IngredientCategories.LEGUMES -> Icons.Rounded.Grass
    IngredientCategories.MEAT -> Icons.Rounded.LunchDining
    IngredientCategories.FISH -> Icons.Rounded.SetMeal
    IngredientCategories.EGGS -> Icons.Rounded.Egg
    IngredientCategories.DAIRY -> Icons.Rounded.LocalDrink
    IngredientCategories.FATS_OILS -> Icons.Rounded.WaterDrop
    IngredientCategories.SWEETS -> Icons.Rounded.Cookie
    IngredientCategories.BEVERAGES -> Icons.Rounded.LocalCafe
    else -> Icons.Rounded.Restaurant
}

@StringRes
fun rawSafetyLabel(rawSafety: String): Int = when (rawSafety) {
    RawSafety.SAFE -> R.string.raw_safety_safe
    RawSafety.CAUTION -> R.string.raw_safety_caution
    RawSafety.UNSAFE -> R.string.raw_safety_unsafe
    else -> R.string.raw_safety_unknown
}

@StringRes
fun rawSafetyDescription(rawSafety: String): Int = when (rawSafety) {
    RawSafety.SAFE -> R.string.raw_safety_safe_detail
    RawSafety.CAUTION -> R.string.raw_safety_caution_detail
    RawSafety.UNSAFE -> R.string.raw_safety_unsafe_detail
    else -> R.string.raw_safety_unknown_detail
}

@StringRes
fun prepStateLabel(prepState: String): Int = when (prepState) {
    PrepState.RAW -> R.string.prep_state_raw
    PrepState.BOILED -> R.string.prep_state_boiled
    PrepState.COOKED -> R.string.prep_state_cooked
    PrepState.FRIED -> R.string.prep_state_fried
    PrepState.GRILLED -> R.string.prep_state_grilled
    PrepState.ROASTED -> R.string.prep_state_roasted
    PrepState.STEAMED -> R.string.prep_state_steamed
    PrepState.CANNED -> R.string.prep_state_canned
    PrepState.CURED -> R.string.prep_state_cured
    PrepState.SMOKED -> R.string.prep_state_smoked
    PrepState.DRIED -> R.string.prep_state_dried
    PrepState.FROZEN -> R.string.prep_state_frozen
    PrepState.POWDERED -> R.string.prep_state_powdered
    else -> R.string.prep_state_unspecified
}

@StringRes
fun nutrientGroupLabel(group: String): Int = when (group) {
    NutrientGroups.MACRO -> R.string.nutrient_group_macro
    NutrientGroups.FAT -> R.string.nutrient_group_fat
    NutrientGroups.FATTY_ACID -> R.string.nutrient_group_fatty_acid
    NutrientGroups.SUGAR -> R.string.nutrient_group_sugar
    NutrientGroups.VITAMIN -> R.string.nutrient_group_vitamin
    NutrientGroups.MINERAL -> R.string.nutrient_group_mineral
    else -> R.string.nutrient_group_other
}

@StringRes
fun dataQualityLabel(quality: String): Int = when (quality) {
    DataQuality.SUSPECT -> R.string.data_quality_suspect
    DataQuality.INCOMPLETE -> R.string.data_quality_incomplete
    else -> R.string.data_quality_ok
}

/**
 * Human text for a data-quality code carried over from the dataset's own
 * validation pass. Unknown codes fall back to the raw code so a future dataset
 * can add checks without this needing to know about them.
 */
@StringRes
fun qualityIssueLabel(code: String): Int? = when (code) {
    "ENERGY_MISMATCH" -> R.string.quality_issue_energy_mismatch
    "MASS_BALANCE_OUT_OF_RANGE" -> R.string.quality_issue_mass_balance
    "SATFAT_EXCEEDS_FAT" -> R.string.quality_issue_satfat_exceeds_fat
    "MONOFAT_EXCEEDS_FAT" -> R.string.quality_issue_monofat_exceeds_fat
    "POLYFAT_EXCEEDS_FAT" -> R.string.quality_issue_polyfat_exceeds_fat
    "SUGARS_EXCEED_CARBS" -> R.string.quality_issue_sugars_exceed_carbs
    "STARCH_EXCEEDS_CARBS" -> R.string.quality_issue_starch_exceeds_carbs
    "SPARSE_NUTRIENT_COVERAGE" -> R.string.quality_issue_sparse_coverage
    "CARBS_FIBRE_NORMALISED" -> R.string.quality_issue_carbs_fibre_normalised
    "DUPLICATE_MERGED" -> R.string.quality_issue_duplicate_merged
    else -> null
}
