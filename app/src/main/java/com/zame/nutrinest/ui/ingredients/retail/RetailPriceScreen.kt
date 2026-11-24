package com.zame.nutrinest.ui.ingredients.retail

import com.zame.nutrinest.domain.stores.validSpanishPostalCode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zame.nutrinest.R
import com.zame.nutrinest.ui.components.PriceLink
import com.zame.nutrinest.data.prices.RetailOffer
import com.zame.nutrinest.domain.prices.MatchKind
import com.zame.nutrinest.ui.components.PriceAction
import com.zame.nutrinest.ui.components.RetailOfferDetails
import com.zame.nutrinest.ui.components.RetailPriceStatus
import com.zame.nutrinest.ui.components.RetailRefreshNotice
import com.zame.nutrinest.ui.components.RetailStatusMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RetailPriceScreen(
    onBack: () -> Unit,
    enabled: Boolean = true
) {
    if (!enabled) {
        RetailPriceContent(RetailUiState.EnterArea, "", onBack, {}, enabled = false)
        return
    }
    val model: RetailPriceViewModel = viewModel(factory = RetailPriceViewModel.Factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    RetailPriceContent(state, model.lastPostalCode, onBack, model::search)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RetailPriceContent(
    current: RetailUiState,
    lastPostalCode: String,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    enabled: Boolean = true
) {
    var postalCode by rememberSaveable { mutableStateOf(lastPostalCode) }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.retail_title)) }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
            }
        })
    }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!enabled) {
                item { Text(stringResource(R.string.retail_disabled)) }
                return@LazyColumn
            }
            item {
                OutlinedTextField(
                    value = postalCode,
                    onValueChange = { postalCode = it },
                    label = { Text(stringResource(R.string.retail_postal_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                PriceAction(
                    label = stringResource(when (current) {
                        RetailUiState.EnterArea, RetailUiState.Loading -> R.string.retail_search
                        is RetailUiState.Content -> R.string.shopping_prices_refresh
                        else -> R.string.retail_retry
                    }),
                    enabled = validSpanishPostalCode(postalCode.trim()) && current != RetailUiState.Loading,
                    onClick = { onSearch(postalCode.trim()) }
                )
            }
            if (current is RetailUiState.Content) {
                item {
                    Column {
                        if (!current.result.fromCache && !current.result.simulated) {
                            RetailStatusMessage(RetailPriceStatus.LiveData)
                        }
                        RetailRefreshNotice(current.result.fromCache, current.result.refreshIssue)
                        if (current.result.simulated) Text(stringResource(R.string.retail_simulated))
                        Text(stringResource(R.string.retail_results_area, lastPostalCode))
                        Text(stringResource(R.string.retail_sort))
                        Text(stringResource(R.string.retail_warning))
                    }
                }
                items(current.result.rows, key = {
                    "${it.price.record.origin.sourceId.value}:${it.price.record.origin.recordKey}"
                }) { OfferCard(it, current.result.simulated) }
            } else {
                item {
                    if (current == RetailUiState.NoBaseUrl) Text(stringResource(R.string.retail_no_base_url))
                    val status = when (current) {
                        RetailUiState.EnterArea -> RetailPriceStatus.LocationRequired
                        RetailUiState.Loading -> RetailPriceStatus.Loading
                        RetailUiState.NoMatches -> RetailPriceStatus.NoProductMatch
                        RetailUiState.NoCoverage -> RetailPriceStatus.NoProviderCoverage
                        RetailUiState.NoPricesInArea -> RetailPriceStatus.NoPricesInArea
                        RetailUiState.Stale -> RetailPriceStatus.StaleData
                        RetailUiState.Offline -> RetailPriceStatus.NetworkError
                        RetailUiState.NoBaseUrl, is RetailUiState.Error -> RetailPriceStatus.ProviderError
                    }
                    RetailStatusMessage(status)
                }
            }
        }
    }
}

@Composable
private fun OfferCard(offer: RetailOffer, simulated: Boolean) {
    var details by rememberSaveable(
        offer.price.record.origin.sourceId.value, offer.price.record.origin.recordKey
    ) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RetailOfferDetails(offer.product, offer.pack, offer.price, offer.retailer.name,
                offer.store.store.name, offer.store.area.postalCode, offer.store.area.municipality,
                offer.source, simulated)
            Text(stringResource(if (offer.match.kind == MatchKind.EXACT) R.string.retail_exact else R.string.retail_alternative))
            PriceLink(onClick = { details = !details },
                label = stringResource(if (details) R.string.retail_hide_details else R.string.retail_details))
            if (details) {
                Text(stringResource(R.string.retail_mapping_version, offer.match.mappingVersion))
                Text(stringResource(if (offer.match.kind == MatchKind.EXACT) R.string.retail_exact_details
                    else R.string.retail_alternative_details))
            }
        }
    }
}
