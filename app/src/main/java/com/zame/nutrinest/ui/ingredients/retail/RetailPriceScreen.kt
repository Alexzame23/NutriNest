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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.zame.nutrinest.data.prices.RetailOffer
import com.zame.nutrinest.domain.prices.ContentUnit
import com.zame.nutrinest.domain.prices.MatchKind
import com.zame.nutrinest.domain.prices.PriceDenominator
import com.zame.nutrinest.domain.prices.SaleUnit
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RetailPriceScreen(
    onBack: () -> Unit,
    model: RetailPriceViewModel = viewModel(factory = RetailPriceViewModel.Factory)
) {
    val state by model.uiState.collectAsStateWithLifecycle()
    var postalCode by rememberSaveable { mutableStateOf(model.lastPostalCode) }
    val current = state
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
            item {
                OutlinedTextField(
                    value = postalCode,
                    onValueChange = { postalCode = it },
                    label = { Text(stringResource(R.string.retail_postal_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { model.search(postalCode) },
                    enabled = postalCode.trim().matches(Regex("[0-9]{5}")) && current !is RetailUiState.Loading
                ) { Text(stringResource(R.string.retail_search)) }
            }
            if (current is RetailUiState.Content) {
                item {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        if (current.result.simulated) Text(stringResource(R.string.retail_simulated))
                        Text(stringResource(R.string.retail_results_area, model.lastPostalCode))
                        Text(stringResource(R.string.retail_sort))
                        Text(stringResource(R.string.retail_warning))
                    }
                }
                items(current.result.rows) { OfferCard(it, current.result.simulated) }
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
                        if (current == RetailUiState.Loading) CircularProgressIndicator()
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
    val money = NumberFormat.getCurrencyInstance(locale).apply {
        currency = Currency.getInstance("EUR")
        maximumFractionDigits = 4
    }
    val number = NumberFormat.getNumberInstance(locale)
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
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
            Text(format)
            Text(stringResource(
                if (offer.price.per == SaleUnit.PACKAGE) R.string.retail_package_price else R.string.retail_weight_price,
                money.format(offer.price.money.amount)
            ))
            Text(stringResource(R.string.retail_normalized, stringResource(normalized, money.format(offer.normalized.decimal(4)))))
            Text(stringResource(R.string.retail_observed, date.format(offer.price.record.observedOn)))
            Text(stringResource(R.string.retail_at_store, offer.store.store.name,
                offer.store.area.postalCode, offer.store.area.municipality))
            Text(stringResource(if (offer.match.kind == MatchKind.EXACT) R.string.retail_exact else R.string.retail_alternative))
            Text(stringResource(R.string.retail_source, offer.source.name, offer.source.attribution))
            TextButton(onClick = { details = !details }) {
                Text(stringResource(if (details) R.string.retail_hide_details else R.string.retail_details))
            }
            if (details) {
                Text(stringResource(R.string.retail_mapping_version, offer.match.mappingVersion))
                Text(stringResource(if (offer.match.kind == MatchKind.EXACT) R.string.retail_exact_details
                    else R.string.retail_alternative_details))
            }
        }
    }
}
