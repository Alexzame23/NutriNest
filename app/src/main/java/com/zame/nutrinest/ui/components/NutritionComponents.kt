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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.ui.theme.Apricot
import com.zame.nutrinest.ui.theme.Caramel
import com.zame.nutrinest.ui.theme.PumpkinDeep
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
            MacroRow("Carbohydrates", nutrition.carbs, CarbsColor)
            MacroRow("Fats", nutrition.fats, FatsColor)
            MacroRow("Protein", nutrition.protein, ProteinColor)
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Total calories",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${nutrition.kcal.roundToInt()} kcal",
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

fun formatGrams(value: Double): String =
    if (value == value.roundToInt().toDouble()) "${value.roundToInt()} g"
    else "%.1f g".format(value)
