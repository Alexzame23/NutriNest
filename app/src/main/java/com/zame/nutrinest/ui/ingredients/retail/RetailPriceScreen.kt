package com.zame.nutrinest.ui.ingredients.retail

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zame.nutrinest.R
import com.zame.nutrinest.ui.components.PriceLink
import com.zame.nutrinest.data.prices.RetailOffer
import com.zame.nutrinest.domain.prices.ContentUnit
import com.zame.nutrinest.domain.prices.MatchKind
import com.zame.nutrinest.domain.prices.PriceDenominator
import com.zame.nutrinest.domain.prices.SaleUnit
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.zame.nutrinest.domain.prices.Confidence
import com.zame.nutrinest.ui.components.PriceAction
import com.zame.nutrinest.ui.components.PriceLoading
import com.zame.nutrinest.ui.components.PriceMetadata
import com.zame.nutrinest.ui.components.rememberEuroFormat

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
                    enabled = postalCode.trim().matches(Regex("[0-9]{5}")) && current != RetailUiState.Loading,
                    onClick = { onSearch(postalCode.trim()) }
                )
            }
            if (current is RetailUiState.Content) {
                item {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        if (current.result.simulated) Text(stringResource(R.string.retail_simulated))
                        Text(stringResource(R.string.retail_results_area, lastPostalCode))
                        Text(stringResource(R.string.retail_sort))
                        Text(stringResource(R.string.retail_warning))
                    }
                }
                items(current.result.rows, key = {
                    "${it.price.record.origin.sourceId.value}:${it.price.record.origin.recordKey}"
                }) { OfferCard(it, current.result.simulated) }
            } else if (current == RetailUiState.Loading) {
                item { PriceLoading() }
            } else {
                item {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        val message = when (current) {
                            RetailUiState.EnterArea -> R.string.retail_enter_area
                            RetailUiState.Loading -> R.string.retail_loading
                            RetailUiState.NoMatches -> R.string.retail_no_matches
                            RetailUiState.NoCoverage -> R.string.retail_no_coverage
                            RetailUiState.Stale -> R.string.retail_stale
                            RetailUiState.Offline -> R.string.retail_offline
                            is RetailUiState.Error -> R.string.retail_error
                        }
                        Text(stringResource(message))
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferCard(offer: RetailOffer, simulated: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val money = rememberEuroFormat()
    val rate = rememberEuroFormat(4)
    val number = remember(locale) { NumberFormat.getNumberInstance(locale) }
    val date = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val content = offer.pack.netContent
    val format = if (content == null) stringResource(R.string.retail_variable_weight) else {
        val unit = when (content.unit) {
            ContentUnit.G -> R.string.retail_grams
            ContentUnit.ML -> R.string.retail_millilitres
            ContentUnit.UNIT -> R.string.retail_pieces
        }
        stringResource(R.string.retail_fixed_format, number.format(content.amount),
            stringResource(unit), requireNotNull(offer.pack.unitsPerPackage))
    }
    val normalized = when (offer.denominator) {
        PriceDenominator.KG -> R.string.retail_per_kg
        PriceDenominator.L -> R.string.retail_per_litre
        PriceDenominator.UNIT -> R.string.retail_per_piece
    }
    var details by rememberSaveable(
        offer.price.record.origin.sourceId.value, offer.price.record.origin.recordKey
    ) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(offer.product.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            if (simulated) Text(stringResource(R.string.retail_simulated))
            Text(stringResource(R.string.retail_chain_brand, offer.retailer.name,
                offer.product.brand?.takeIf { it.isNotBlank() } ?: stringResource(R.string.retail_unknown_brand)))
            Text(stringResource(
                if (offer.price.per == SaleUnit.PACKAGE) R.string.retail_package_price else R.string.retail_weight_price,
                money.format(offer.price.money.amount)
            ), style = MaterialTheme.typography.titleLarge)
            Text(format)
            PriceMetadata(stringResource(R.string.retail_normalized,
                stringResource(normalized, rate.format(offer.normalized.decimal(4)))))
            PriceMetadata(stringResource(R.string.retail_observed, date.format(offer.price.record.observedOn)))
            if (offer.price.record.confidence != Confidence.HIGH) {
                PriceMetadata(stringResource(R.string.retail_limited_evidence))
            }
            Text(stringResource(R.string.retail_at_store, offer.store.store.name,
                offer.store.area.postalCode, offer.store.area.municipality))
            Text(stringResource(if (offer.match.kind == MatchKind.EXACT) R.string.retail_exact else R.string.retail_alternative))
            PriceMetadata(stringResource(R.string.retail_source, offer.source.name, offer.source.attribution))
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
