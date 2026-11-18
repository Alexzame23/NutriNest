package com.zame.nutrinest.ui.menu.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zame.nutrinest.R
import com.zame.nutrinest.data.prices.RetailBatchUnavailable
import com.zame.nutrinest.data.prices.ShoppingLinePrice
import com.zame.nutrinest.data.prices.ShoppingPriceOption
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency

@Composable
internal fun ShoppingPriceControls(state: ShoppingComparisonUiState, comparison: ShoppingComparison) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.shopping_prices_title), Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = state.postalCode,
            onValueChange = comparison::changePostalCode,
            label = { Text(stringResource(R.string.retail_postal_code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = state.excludePurchased, role = Role.Checkbox, onValueChange = comparison::excludePurchased
            ).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = state.excludePurchased, onCheckedChange = null)
            Text(stringResource(R.string.shopping_prices_exclude))
        }
        Button(
            onClick = comparison::refresh,
            enabled = state.eligibleCount > 0 && state.postalCode.trim().matches(Regex("[0-9]{5}")) &&
                state.status != ShoppingComparisonStatus.Loading
        ) { Text(stringResource(R.string.shopping_prices_refresh)) }
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            when (val status = state.status) {
                ShoppingComparisonStatus.Idle -> if (state.eligibleCount > 0) {
                    Text(stringResource(R.string.shopping_prices_idle))
                } else Text(stringResource(R.string.shopping_prices_no_pending))
                ShoppingComparisonStatus.Loading -> Text(stringResource(R.string.retail_loading))
                ShoppingComparisonStatus.Error -> Text(stringResource(R.string.retail_error))
                is ShoppingComparisonStatus.Unavailable -> Text(stringResource(when (status.reason) {
                    RetailBatchUnavailable.Offline -> R.string.retail_offline
                    RetailBatchUnavailable.Stale -> R.string.retail_stale
                    RetailBatchUnavailable.NoCoverage -> R.string.shopping_prices_no_coverage
                }))
                is ShoppingComparisonStatus.Published -> {
                    val date = localizedDate().format(status.prices.publishedAt.atZone(ZoneId.of("Europe/Madrid")))
                    if (status.prices.simulated) Text(stringResource(R.string.retail_simulated))
                    Text(stringResource(R.string.shopping_prices_coverage, state.coveredCount, state.eligibleCount))
                    Text(stringResource(R.string.shopping_prices_publication, state.postalCode.trim(), date))
                    Text(stringResource(R.string.shopping_prices_order))
                }
            }
        }
    }
}

@Composable
internal fun ShoppingPriceLine(line: ShoppingLinePrice) {
    Column(Modifier.padding(start = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (line) {
            ShoppingLinePrice.NoEquivalent -> Text(stringResource(R.string.shopping_prices_no_equivalent))
            ShoppingLinePrice.NoPrice -> Text(stringResource(R.string.shopping_prices_no_comparable))
            ShoppingLinePrice.Stale -> Text(stringResource(R.string.retail_stale))
            is ShoppingLinePrice.Options -> {
                var expanded by rememberSaveable { mutableStateOf(false) }
                Text(stringResource(R.string.shopping_prices_best), style = MaterialTheme.typography.labelLarge)
                ShoppingOption(line.values.first())
                if (line.values.size > 1) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(
                            if (expanded) R.string.shopping_prices_hide else R.string.shopping_prices_more
                        ))
                    }
                    if (expanded) line.values.drop(1).forEach { ShoppingOption(it) }
                }
            }
        }
    }
}

@Composable
private fun ShoppingOption(option: ShoppingPriceOption) {
    val money = NumberFormat.getCurrencyInstance(LocalConfiguration.current.locales[0]).apply {
        currency = Currency.getInstance("EUR")
    }
    val date = localizedDate().format(option.cost.observation.record.observedOn)
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(option.product.name, style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.shopping_prices_proportional, money.format(option.proportionalEur)))
        val checkout = option.cost.checkoutEur
        if (checkout != null) {
            Text(stringResource(R.string.shopping_prices_checkout, option.cost.packageCount.toString(), money.format(checkout)))
        } else {
            Text(stringResource(R.string.shopping_prices_weighed))
        }
        Text(stringResource(R.string.shopping_prices_store, option.retailer.name, option.store.store.name))
        Text(stringResource(R.string.retail_observed, date))
        Text(stringResource(R.string.retail_source, option.source.name, option.source.attribution))
    }
}

@Composable
private fun localizedDate(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0])
