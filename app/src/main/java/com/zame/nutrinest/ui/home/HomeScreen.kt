package com.zame.nutrinest.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zame.nutrinest.R
import com.zame.nutrinest.ui.theme.NutritionAppTheme
import com.zame.nutrinest.util.AppLanguage
import com.zame.nutrinest.util.AppLocale
import com.zame.nutrinest.util.findActivity

@Composable
fun HomeScreen(
    onRecipesClick: () -> Unit,
    onWeeklyMenuClick: () -> Unit,
    onProfilesClick: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            OrganicBackdrop()

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LanguageSwitch()
                ProfilesShortcut(onClick = onProfilesClick)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 84.dp, end = 24.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BrandHeader(onHelpClick = onHelpClick)

                Spacer(Modifier.height(28.dp))

                HomeActionCard(
                    title = stringResource(R.string.home_recipes_ingredients_title),
                    subtitle = stringResource(R.string.home_recipes_ingredients_subtitle),
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    onClick = onRecipesClick
                )

                Spacer(Modifier.height(16.dp))

                HomeActionCard(
                    title = stringResource(R.string.home_weekly_menu_title),
                    subtitle = stringResource(R.string.home_weekly_menu_subtitle),
                    icon = Icons.Rounded.CalendarMonth,
                    onClick = onWeeklyMenuClick
                )
            }
        }
    }
}

@Composable
private fun ProfilesShortcut(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = stringResource(R.string.home_profiles)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp,
        modifier = modifier.semantics { contentDescription = label }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BrandHeader(
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.nutrinest_logo),
                    contentDescription = stringResource(R.string.app_logo_description),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(92.dp)
                )
                Spacer(Modifier.size(16.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(10.dp))
                Surface(
                    onClick = onHelpClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.HelpOutline,
                            contentDescription = stringResource(R.string.home_help),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Switches the app between English and Spanish.
 *
 * Shows the language in effect rather than the one a tap would move to, so the
 * badge always reads as a statement of the current state; the menu is where the
 * choice happens. Applying it recreates the activity, which is how every string
 * on screen gets re-read.
 */
@Composable
private fun LanguageSwitch(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context.findActivity()
    // Studio's preview renderer has neither an Activity nor real preferences.
    val inPreview = LocalInspectionMode.current
    val current = if (inPreview) AppLanguage.ENGLISH else AppLocale.current(context)
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Surface(
            onClick = { expanded = true },
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Language,
                    contentDescription = stringResource(R.string.language_switch),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    current.shortLabel,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(stringResource(language.labelRes)) },
                    trailingIcon = {
                        if (language == current) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        if (language != current && activity != null) {
                            AppLocale.apply(activity, language)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cardDescription = stringResource(R.string.home_card_description, title, subtitle)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardPressScale"
    )

    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        ),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .scale(scale)
            .semantics { contentDescription = cardDescription }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.size(18.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OrganicBackdrop() {
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = accent.copy(alpha = 0.07f),
            radius = size.width * 0.45f,
            center = Offset(size.width * 1.05f, -size.height * 0.05f)
        )
        drawCircle(
            color = secondary.copy(alpha = 0.10f),
            radius = size.width * 0.35f,
            center = Offset(-size.width * 0.10f, size.height * 1.02f),
            style = Stroke(width = size.width * 0.05f)
        )
    }
}

@Preview(showBackground = true, name = "Home – Light")
@Composable
private fun HomeScreenPreviewLight() {
    NutritionAppTheme(darkTheme = false) {
        HomeScreen(
            onRecipesClick = {},
            onWeeklyMenuClick = {},
            onProfilesClick = {},
            onHelpClick = {}
        )
    }
}
