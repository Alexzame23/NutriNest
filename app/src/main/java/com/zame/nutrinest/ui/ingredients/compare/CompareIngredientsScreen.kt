package com.zame.nutrinest.ui.ingredients.compare

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.NutrientTags
import com.zame.nutrinest.ui.components.CategoryDot
import com.zame.nutrinest.ui.components.MacroBarChart
import com.zame.nutrinest.ui.components.formatNutrientValue
import com.zame.nutrinest.ui.ingredients.categoryColor
import com.zame.nutrinest.ui.ingredients.rememberIsSpanish
import com.zame.nutrinest.ui.theme.Sage

/**
 * Two or three ingredients side by side, per 100 g.
 *
 * The highest value in each row is marked, which is informative rather than a
 * judgement — most protein is good news in a chicken breast and beside the
 * point in an olive oil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareIngredientsScreen(
    onBack: () -> Unit,
    viewModel: CompareIngredientsViewModel = viewModel(factory = CompareIngredientsViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spanish = rememberIsSpanish()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.compare_title),
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (state.ingredients.size < 2 && !state.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.compare_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("headers") {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(LABEL_WIDTH))
                    state.ingredients.forEach { ingredient ->
                        Column(
                            Modifier.weight(1f).padding(horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CategoryDot(categoryColor(ingredient.categoryKey), size = 10.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                ingredient.displayName(spanish),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                            MacroBarChart(ingredient.nutrition, height = 6.dp)
                        }
                    }
                }
            }

            item("basis") {
                Text(
                    stringResource(R.string.compare_per_100g),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item("macros") {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        state.macroRows.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            ComparisonRowView(row, stringResource(macroLabel(row.tag)))
                        }
                    }
                }
            }

            if (state.sharedRows.isEmpty()) {
                item("no_shared") {
                    Text(
                        stringResource(R.string.compare_no_shared),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item("shared_heading") {
                    Text(
                        stringResource(R.string.compare_shared_nutrients),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item("shared") {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            state.sharedRows.forEachIndexed { index, row ->
                                if (index > 0) {
                                    HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                                }
                                ComparisonRowView(row, row.label(spanish))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonRowView(row: ComparisonRow, label: String) {
    val best = row.bestIndex
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(LABEL_WIDTH)
        )
        row.values.forEachIndexed { index, value ->
            Text(
                text = value?.let { "${formatNutrientValue(it)} ${row.unit}" } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (index == best) FontWeight.Bold else FontWeight.Normal,
                color = if (index == best) Sage else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
            )
        }
    }
}

private fun macroLabel(tag: String): Int = when (tag) {
    NutrientTags.ENERGY -> R.string.nutrition_total_calories
    NutrientTags.CARBS -> R.string.nutrition_carbohydrates
    NutrientTags.FAT -> R.string.nutrition_fats
    else -> R.string.nutrition_protein
}

private val LABEL_WIDTH = 108.dp
