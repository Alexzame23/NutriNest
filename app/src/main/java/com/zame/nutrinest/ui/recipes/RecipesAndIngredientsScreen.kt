package com.zame.nutrinest.ui.recipes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zame.nutrinest.R
import com.zame.nutrinest.ui.ingredients.IngredientsTabContent
import kotlinx.coroutines.launch

private const val TAB_RECIPES = 0
private const val TAB_INGREDIENTS = 1

/**
 * Hosts the two halves of the food section: the user's recipes, and the
 * ingredient database they are built from.
 *
 * Owning the Scaffold here means the tab bar stays put while the pages swipe,
 * and each tab gets the add button that belongs to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesAndIngredientsScreen(
    onBack: () -> Unit,
    onAddRecipe: () -> Unit,
    onAddIngredient: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onOpenIngredient: (Long) -> Unit,
    onCompareIngredients: (List<Long>) -> Unit,
    startTab: Int = TAB_RECIPES,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(initialPage = startTab, pageCount = { 2 })
    val scope = rememberCoroutineScope()
    var fabMenuOpen by remember { mutableStateOf(false) }

    val tabs = listOf(R.string.tab_recipes, R.string.tab_ingredients)

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.home_recipes_ingredients_title),
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
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, labelRes ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(stringResource(labelRes)) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (pagerState.currentPage == TAB_RECIPES) {
                AddFabMenu(
                    open = fabMenuOpen,
                    onToggle = { fabMenuOpen = !fabMenuOpen },
                    onAddRecipe = { fabMenuOpen = false; onAddRecipe() },
                    onAddIngredient = { fabMenuOpen = false; onAddIngredient() }
                )
            } else {
                ExtendedFloatingActionButton(
                    onClick = onAddIngredient,
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text(stringResource(R.string.ingredients_new)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { page ->
            when (page) {
                TAB_RECIPES -> RecipesTabContent(onOpenRecipe = onOpenRecipe)
                else -> IngredientsTabContent(
                    onOpenIngredient = onOpenIngredient,
                    onCompare = onCompareIngredients
                )
            }
        }
    }
}

/** "+" FAB that expands into Add Recipe / Add Ingredient options. */
@Composable
private fun AddFabMenu(
    open: Boolean,
    onToggle: () -> Unit,
    onAddRecipe: () -> Unit,
    onAddIngredient: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = onAddRecipe,
                    icon = { Icon(Icons.Rounded.RestaurantMenu, null) },
                    text = { Text(stringResource(R.string.recipes_add_recipe)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                ExtendedFloatingActionButton(
                    onClick = onAddIngredient,
                    icon = { Icon(Icons.Rounded.Egg, null) },
                    text = { Text(stringResource(R.string.recipes_add_ingredient)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        FloatingActionButton(
            onClick = onToggle,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                if (open) Icons.Rounded.Close else Icons.Rounded.Add,
                contentDescription = stringResource(
                    if (open) R.string.action_close else R.string.action_add
                )
            )
        }
    }
}
