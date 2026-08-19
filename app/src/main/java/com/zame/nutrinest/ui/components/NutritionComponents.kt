package com.zame.nutrinest.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.domain.nutrition.MeasureConversions
import com.zame.nutrinest.data.local.entity.IngredientEntity
import com.zame.nutrinest.ui.theme.Amber
import com.zame.nutrinest.ui.theme.Apricot
import com.zame.nutrinest.ui.theme.Caramel
import com.zame.nutrinest.ui.theme.PumpkinDeep
import com.zame.nutrinest.ui.theme.Sage
import java.util.Locale
import kotlin.math.roundToInt

// Consistent macro colors across the whole app.
val CarbsColor = Apricot
val FatsColor = Caramel
val ProteinColor = PumpkinDeep

/**
 * Circular chart showing the carbs / fats / protein ratio by weight,
 * with total calories in the center. Animates when values change.
 */
@Composable
fun MacroDonutChart(
    nutrition: NutritionValues,
    modifier: Modifier = Modifier
) {
    val totalMacros = (nutrition.carbs + nutrition.fats + nutrition.protein)
        .coerceAtLeast(0.0001)
    val carbsFrac by animateFloatAsState(
        (nutrition.carbs / totalMacros).toFloat(), tween(500), label = "carbs"
    )
    val fatsFrac by animateFloatAsState(
        (nutrition.fats / totalMacros).toFloat(), tween(500), label = "fats"
    )
    val proteinFrac by animateFloatAsState(
        (nutrition.protein / totalMacros).toFloat(), tween(500), label = "protein"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val strokeWidth = size.width * 0.11f
            val inset = strokeWidth / 2
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - strokeWidth, size.height - strokeWidth
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Butt)

            // Background track (visible when there's no data yet)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = stroke)

            var start = -90f
            listOf(
                CarbsColor to carbsFrac,
                FatsColor to fatsFrac,
                ProteinColor to proteinFrac
            ).forEach { (color, frac) ->
                val sweep = frac * 360f
                if (sweep > 0.5f) {
                    drawArc(color, start, sweep, false, topLeft, arcSize, style = stroke)
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${nutrition.kcal.roundToInt()}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Legend + values below the chart: calories already in-chart, macros here. */
@Composable
fun NutritionSummary(
    nutrition: NutritionValues,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroRow(stringResource(R.string.nutrition_carbohydrates), nutrition.carbs, CarbsColor)
            MacroRow(stringResource(R.string.nutrition_fats), nutrition.fats, FatsColor)
            MacroRow(stringResource(R.string.nutrition_protein), nutrition.protein, ProteinColor)
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.nutrition_total_calories),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(R.string.kcal_value, nutrition.kcal.roundToInt()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MacroRow(label: String, grams: Double, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            formatGrams(grams),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Horizontal stacked bar of the carbs / fats / protein split.
 *
 * Used where a donut would be too heavy: comparison columns and list rows.
 * Falls back to a flat track when an ingredient records no macros at all.
 */
@Composable
fun MacroBarChart(
    nutrition: NutritionValues,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    val total = nutrition.carbs + nutrition.fats + nutrition.protein
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (total <= 0.0) return@Row
        listOf(
            CarbsColor to nutrition.carbs,
            FatsColor to nutrition.fats,
            ProteinColor to nutrition.protein
        ).forEach { (color, grams) ->
            val share = (grams / total).toFloat()
            if (share > 0f) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(share)
                        .background(color)
                )
            }
        }
    }
}

/**
 * One nutrient with its value and, where the EU defines a daily reference
 * value, a bar showing what share of it 100 g provides.
 *
 * The bar is colour-coded by density — a nutrient supplying over half a day's
 * reference reads as rich — but the number is always there too, so the colour
 * is reinforcement rather than the only signal.
 */
@Composable
fun NutrientRow(
    label: String,
    value: Double,
    unit: String,
    nrvPercent: Double? = null,
    emphasised: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                stringResource(R.string.value_with_unit, formatNutrientValue(value), unit),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (nrvPercent != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.ingredient_nrv_share, nrvPercent.roundToInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                    textAlign = TextAlign.End
                )
            }
        }
        if (nrvPercent != null) {
            Spacer(Modifier.height(4.dp))
            val fraction = (nrvPercent / 100.0).coerceIn(0.0, 1.0).toFloat()
            LinearProgressIndicator(
                progress = { fraction },
                color = nutrientDensityColor(nrvPercent),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )
        }
    }
}

/** Green when a nutrient is abundant, amber mid-range, muted when scarce. */
@Composable
fun nutrientDensityColor(nrvPercent: Double): Color = when {
    nrvPercent >= 50 -> Sage
    nrvPercent >= 15 -> Amber
    else -> MaterialTheme.colorScheme.outline
}

/** Small colour-and-icon chip, e.g. the category of an ingredient. */
@Composable
fun StatusChip(
    label: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.14f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
        }
    }
}

/** Filled circle in a category's colour, used as a list-row leading marker. */
@Composable
fun CategoryDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/** Trims trailing zeros so "12.0" reads as "12" and "0.35" keeps its precision. */
fun formatNutrientValue(value: Double): String = when {
    value == 0.0 -> "0"
    value >= 100 -> value.roundToInt().toString()
    value >= 1 -> trimZeros("%.1f".format(Locale.getDefault(), value))
    value >= 0.01 -> trimZeros("%.2f".format(Locale.getDefault(), value))
    else -> "%.3f".format(Locale.getDefault(), value)
}

private fun trimZeros(text: String): String =
    if (text.contains(',') || text.contains('.')) {
        text.trimEnd('0').trimEnd(',', '.')
    } else {
        text
    }

/** An amount in grams. See [MeasureConversions.formatGrams]. */
fun formatGrams(value: Double): String = MeasureConversions.formatGrams(value)

/** Shows a recipe ingredient in the unit selected when the recipe was created. */
fun formatRecipeQuantity(
    ingredient: IngredientEntity,
    grams: Double,
    quantityUnit: String
): String {
    val unit = MeasureConversions.MeasureUnit.entries
        .firstOrNull { it.name == quantityUnit }
        ?: MeasureConversions.MeasureUnit.GRAM
    return MeasureConversions.formatRecipeQuantity(
        grams = grams,
        unit = unit,
        isLiquid = ingredient.isLiquid,
        densityGml = ingredient.densityGml
    )
}

/**
 * An amount that may run past a kilo, e.g. a week's shopping list.
 * See [MeasureConversions.formatWeight].
 */
fun formatWeight(grams: Double): String = MeasureConversions.formatWeight(grams)
