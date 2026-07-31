package com.zame.nutrinest.ui.menu

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.PlannedMealWithRecipe
import com.zame.nutrinest.util.WeekUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyMenuScreen(
    onBack: () -> Unit,
    onConfigure: (Long) -> Unit,
    onOpenMeal: (Long) -> Unit,
    onOpenShoppingList: () -> Unit,
    viewModel: WeeklyMenuViewModel = viewModel(factory = WeeklyMenuViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    // Day names come from the JDK for the device's locale, not from strings.xml.
    val configuration = LocalConfiguration.current
    val dayNames = remember(configuration) { WeekUtils.dayNames(configuration.locales[0]) }

    // Politely ask for notification permission once (Android 13+) so the
    // weekend planning reminder can be delivered.
    if (Build.VERSION.SDK_INT >= 33) {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* result not needed; reminder is best-effort */ }
        LaunchedEffect(Unit) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.weekly_menu_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (state.menu != null) {
                        IconButton(onClick = { onConfigure(state.weekStart) }) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = stringResource(R.string.weekly_menu_regenerate)
                            )
                        }
                    }
                    IconButton(onClick = onOpenShoppingList) {
                        Icon(
                            Icons.Rounded.ShoppingCart,
                            contentDescription = stringResource(R.string.shopping_list_title)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (state.menu == null) {
                ExtendedFloatingActionButton(
                    onClick = { onConfigure(state.weekStart) },
                    icon = { Icon(Icons.Rounded.AutoAwesome, null) },
                    text = { Text(stringResource(R.string.weekly_menu_create)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            // ── Current / next week switch ──────────────────────────
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.isCurrentWeek,
                    onClick = viewModel::showCurrentWeek,
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text(stringResource(R.string.week_current)) }
                SegmentedButton(
                    selected = !state.isCurrentWeek,
                    onClick = viewModel::showNextWeek,
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text(stringResource(R.string.week_next)) }
            }
            Spacer(Modifier.height(12.dp))

            if (state.menu == null) {
                EmptyMenuState(isCurrentWeek = state.isCurrentWeek)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Today first (only meaningful on the current week).
                    if (state.isCurrentWeek) {
                        state.mealsByDay[state.todayIndex]?.let { todayMeals ->
                            item {
                                DayHeader(
                                    stringResource(
                                        R.string.weekly_menu_today,
                                        dayNames[state.todayIndex]
                                    ),
                                    highlight = true
                                )
                            }
                            items(todayMeals.size) { i ->
                                MealRow(todayMeals[i], onOpenMeal, highlight = true)
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                    for (day in 0..6) {
                        if (state.isCurrentWeek && day == state.todayIndex) continue
                        val meals = state.mealsByDay[day] ?: continue
                        item { DayHeader(dayNames[day]) }
                        items(meals.size) { i -> MealRow(meals[i], onOpenMeal) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(text: String, highlight: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = if (highlight) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun MealRow(
    item: PlannedMealWithRecipe,
    onOpenMeal: (Long) -> Unit,
    highlight: Boolean = false
) {
    Card(
        onClick = { onOpenMeal(item.meal.id) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(item.meal.slot.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    item.recipe.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (item.meal.isCheatMeal) {
                Text(
                    stringResource(R.string.meal_cheat),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (item.meal.isLocked) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.meal_locked),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyMenuState(isCurrentWeek: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(
                    if (isCurrentWeek) R.string.weekly_menu_empty_current
                    else R.string.weekly_menu_empty_next
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.weekly_menu_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
