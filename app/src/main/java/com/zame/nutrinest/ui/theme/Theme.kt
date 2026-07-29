package com.zame.nutrinest.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Pumpkin,
    onPrimary = Color.White,
    primaryContainer = Peach,
    onPrimaryContainer = Espresso,
    secondary = Caramel,
    onSecondary = Color.White,
    secondaryContainer = Oat,
    onSecondaryContainer = Espresso,
    background = Cream,
    onBackground = Espresso,
    surface = WarmSand,
    onSurface = Espresso,
    surfaceVariant = Oat,
    onSurfaceVariant = Cocoa,
    outline = Cocoa
)

private val DarkColors = darkColorScheme(
    primary = Apricot,
    onPrimary = DarkCoffee,
    primaryContainer = PumpkinDeep,
    onPrimaryContainer = Linen,
    secondary = Caramel,
    onSecondary = DarkCoffee,
    secondaryContainer = DarkMocha,
    onSecondaryContainer = Linen,
    background = DarkCoffee,
    onBackground = Linen,
    surface = DarkMocha,
    onSurface = Linen,
    surfaceVariant = DarkMocha,
    onSurfaceVariant = Oat,
    outline = Caramel
)

/** Generous rounding throughout for a soft, friendly feel. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun NutritionAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    // Tint the status bar to match the background for an edge-to-edge feel.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
