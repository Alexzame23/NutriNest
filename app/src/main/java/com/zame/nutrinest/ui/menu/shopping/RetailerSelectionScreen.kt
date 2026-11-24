package com.zame.nutrinest.ui.menu.shopping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zame.nutrinest.R

@Composable
internal fun RetailerSelectionScreen(
    state: ShoppingComparisonUiState,
    comparison: ShoppingComparison,
    onClose: () -> Unit
) {
    val preferences = state.preferences
    val storesByChain = state.knownStores.groupBy { it.retailerId }
    val chains = (storesByChain.keys + state.chainNames.keys + preferences.selectedRetailers.orEmpty() +
        preferences.selectedStores.keys).sortedBy { state.chainNames[it] ?: it.value }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.retailer_preferences_title)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item { ShoppingCoverageStatus(state) }
                if (chains.isEmpty()) {
                    item { Text(stringResource(R.string.retailer_preferences_no_chains)) }
                }
                if (preferences.selectedRetailers?.isEmpty() == true) {
                    item { Text(stringResource(R.string.shopping_basket_select_one)) }
                }
                items(chains, key = { it.value }) { chain ->
                    val stores = storesByChain[chain].orEmpty()
                    val selected = preferences.selectedRetailers?.contains(chain) ?: true
                    val restricted = preferences.selectedStores[chain]
                    val available = stores.any { it.id in state.availableStores }
                    val priced = stores.any { it.id in state.pricedStores }
                    val name = state.chainNames[chain] ?: chain.value.replaceFirstChar { it.titlecase() }
                    RetailerChoice(name, selected) { comparison.selectRetailer(chain, it) }
                    RetailerCoverage(state, available, priced)

                    if (selected && (stores.isNotEmpty() || restricted != null)) {
                        Column(Modifier.padding(start = 16.dp, bottom = 8.dp)) {
                            RetailerChoice(stringResource(R.string.retailer_preferences_all_stores),
                                checked = restricted == null) { all ->
                                if (all) comparison.allowAllStores(chain)
                                else comparison.restrictStores(chain)
                            }
                            if (restricted != null) {
                                stores.forEach { store ->
                                    RetailerChoice(store.name, store.id in restricted) {
                                        comparison.selectStore(store, it)
                                    }
                                    RetailerCoverage(state, store.id in state.availableStores, store.id in state.pricedStores)
                                }
                                (restricted - stores.map { it.id }.toSet()).sortedBy { it.value }.forEach { id ->
                                    RetailerChoice(id.value, true) { comparison.removeStore(chain, id) }
                                    RetailerCoverage(state, available = false, priced = false)
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = comparison::resetPreferences) {
                Text(stringResource(R.string.retailer_preferences_reset))
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.retailer_preferences_done)) }
        }
    )
}

@Composable
private fun RetailerChoice(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked, onCheckedChange = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RetailerCoverage(state: ShoppingComparisonUiState, available: Boolean, priced: Boolean) {
    val publication = (state.status as? ShoppingComparisonStatus.Published)?.prices
    if (!available && publication == null) return
    Text(stringResource(when {
        priced -> R.string.retailer_preferences_priced
        available && publication != null && publication.refreshIssue == null ->
            R.string.retailer_preferences_available
        available -> R.string.retailer_preferences_known_store
        else -> R.string.retailer_preferences_unavailable
    }), style = MaterialTheme.typography.bodySmall)
}
