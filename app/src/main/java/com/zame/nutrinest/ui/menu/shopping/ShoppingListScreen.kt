package com.zame.nutrinest.ui.menu.shopping

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.IngredientCategories
import com.zame.nutrinest.data.local.entity.ShoppingItemWithIngredient
import com.zame.nutrinest.data.repository.MenuRepository
import com.zame.nutrinest.ui.components.formatWeight
import com.zame.nutrinest.ui.ingredients.rememberIsSpanish
import com.zame.nutrinest.util.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModel(
    private val repository: MenuRepository
) : ViewModel() {

    private val viewingWeekStart = MutableStateFlow(WeekUtils.weekStartOf())
    val isCurrentWeek = viewingWeekStart
        .map { it == WeekUtils.weekStartOf() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * Ingredient category → items, in the food dataset's own category order
     * rather than alphabetically, so the list reads produce-first the way a
     * shop is laid out.
     */
    val groupedItems = viewingWeekStart
        .flatMapLatest { repository.observeShoppingItems(it) }
        .map { items ->
            items
                .sortedBy { it.ingredient.name.lowercase() }
                .groupBy { it.ingredient.categoryKey }
                .toList()
                .sortedBy { (key, _) -> IngredientCategories.ALL.indexOf(key) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun showCurrentWeek() { viewingWeekStart.value = WeekUtils.weekStartOf() }
    fun showNextWeek() { viewingWeekStart.value = WeekUtils.nextWeekStart() }

    fun togglePurchased(item: ShoppingItemWithIngredient) {
        viewModelScope.launch {
            repository.setPurchased(item.item.id, !item.item.purchased)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                ShoppingListViewModel(app.container.menuRepository)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    onBack: () -> Unit,
    viewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModel.Factory)
) {
    val grouped by viewModel.groupedItems.collectAsState()
    val spanish = rememberIsSpanish()
    val isCurrentWeek by viewModel.isCurrentWeek.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.shopping_list_title),
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isCurrentWeek,
                    onClick = viewModel::showCurrentWeek,
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text(stringResource(R.string.week_current)) }
                SegmentedButton(
                    selected = !isCurrentWeek,
                    onClick = viewModel::showNextWeek,
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text(stringResource(R.string.week_next)) }
            }
            Spacer(Modifier.height(12.dp))

            if (grouped.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.height(56.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.shopping_list_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.shopping_list_empty_body),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    grouped.forEach { (categoryKey, items) ->
                        item(key = "header_$categoryKey") {
                            Text(
                                IngredientCategories.shortName(categoryKey, spanish),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            )
                        }
                        items(items.size, key = { i -> items[i].item.id }) { i ->
                            val item = items[i]
                            Card(
                                onClick = { viewModel.togglePurchased(item) },
                                shape = MaterialTheme.shapes.small,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = item.item.purchased,
                                        onCheckedChange = { viewModel.togglePurchased(item) }
                                    )
                                    Text(
                                        item.ingredient.displayName(spanish),
                                        style = MaterialTheme.typography.bodyLarge,
                                        textDecoration =
                                            if (item.item.purchased) TextDecoration.LineThrough
                                            else null,
                                        color =
                                            if (item.item.purchased)
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        formatWeight(item.item.grams),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
