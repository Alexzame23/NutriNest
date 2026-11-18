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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.zame.nutrinest.data.repository.IngredientRepository
import com.zame.nutrinest.data.repository.MenuRepository
import com.zame.nutrinest.ui.ingredients.rememberIsSpanish
import com.zame.nutrinest.ui.menu.ShoppingQuantity
import com.zame.nutrinest.ui.menu.formatNaturalQuantity
import com.zame.nutrinest.ui.menu.formatShoppingWeight
import com.zame.nutrinest.ui.menu.observeMenuIngredientCatalog
import com.zame.nutrinest.util.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

internal data class ShoppingRow(
    val source: ShoppingItemWithIngredient,
    val nameEn: String,
    val nameEs: String,
    val quantity: ShoppingQuantity
) {
    fun name(spanish: Boolean): String = if (spanish) nameEs else nameEn
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModel(
    private val repository: MenuRepository,
    ingredientRepository: IngredientRepository
) : ViewModel() {

    private val viewingWeekStart = MutableStateFlow(WeekUtils.weekStartOf())
    val isCurrentWeek = viewingWeekStart
        .map { it == WeekUtils.weekStartOf() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    internal val rows = viewingWeekStart
        .flatMapLatest { repository.observeShoppingItems(it) }
        .combine(ingredientRepository.observeMenuIngredientCatalog()) { items, catalog ->
            items.map { row ->
                ShoppingRow(
                    row,
                    nameEn = catalog.nameOf(row.ingredient, spanish = false),
                    nameEs = catalog.nameOf(row.ingredient, spanish = true),
                    quantity = ShoppingQuantity.from(row.item.grams, catalog.measures[row.item.ingredientId].orEmpty())
                )
            }
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
                ShoppingListViewModel(app.container.menuRepository, app.container.ingredientRepository)
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
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val spanish = rememberIsSpanish()
    val grouped = remember(rows, spanish) { groupShoppingRows(rows, spanish) }
    val isCurrentWeek by viewModel.isCurrentWeek.collectAsStateWithLifecycle()

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
                        items(items.size, key = { i -> items[i].source.item.id }) { i ->
                            val item = items[i]
                            Card(
                                onClick = { viewModel.togglePurchased(item.source) },
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
                                        checked = item.source.item.purchased,
                                        onCheckedChange = { viewModel.togglePurchased(item.source) }
                                    )
                                    ShoppingItemText(item, spanish, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun groupShoppingRows(rows: List<ShoppingRow>, spanish: Boolean): List<Pair<String, List<ShoppingRow>>> {
    val collator = Collator.getInstance(if (spanish) Locale.forLanguageTag("es") else Locale.ENGLISH)
    return rows.groupBy { it.source.ingredient.categoryKey }
        .toList()
        .sortedBy { (key, _) -> IngredientCategories.ALL.indexOf(key) }
        .map { (key, items) ->
            key to items.sortedWith { a, b -> collator.compare(a.name(spanish), b.name(spanish)) }
        }
}

@Composable
internal fun ShoppingItemText(row: ShoppingRow, spanish: Boolean, modifier: Modifier = Modifier) {
    val purchased = row.source.item.purchased
    val quantity = row.quantity
    val weight = formatShoppingWeight(quantity.requiredGrams)
    val required = quantity.required?.let { formatNaturalQuantity(it, spanish) + " · " + weight } ?: weight
    Column(modifier.padding(vertical = 8.dp)) {
        Text(
            row.name(spanish),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (purchased) TextDecoration.LineThrough else null,
            color = if (purchased) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
        Text(
            stringResource(R.string.shopping_quantity_required, required),
            style = MaterialTheme.typography.bodyMedium
        )
        quantity.toBuy?.let {
            Text(
                stringResource(R.string.shopping_quantity_to_buy, formatNaturalQuantity(it, spanish)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
