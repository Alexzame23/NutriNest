package com.zame.nutrinest.ui.menu.shopping

import com.zame.nutrinest.domain.stores.validSpanishPostalCode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zame.nutrinest.R
import com.zame.nutrinest.ui.components.PriceLink
import com.zame.nutrinest.data.prices.RetailBatchUnavailable
import com.zame.nutrinest.data.prices.ShoppingLinePrice
import com.zame.nutrinest.data.prices.ShoppingPriceOption
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.zame.nutrinest.ui.components.PriceAction
import com.zame.nutrinest.ui.components.PriceMetadata
import com.zame.nutrinest.ui.components.RetailOfferDetails
import com.zame.nutrinest.ui.components.RetailPriceStatus
import com.zame.nutrinest.ui.components.RetailRefreshNotice
import com.zame.nutrinest.ui.components.RetailStatusMessage
import com.zame.nutrinest.ui.components.presentation
import com.zame.nutrinest.ui.components.rememberEuroFormat

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
        PriceAction(
            label = stringResource(when (state.status) {
                ShoppingComparisonStatus.Error, is ShoppingComparisonStatus.Unavailable -> R.string.retail_retry
                else -> R.string.shopping_prices_refresh
            }),
            onClick = comparison::refresh,
            enabled = state.eligibleCount > 0 && validSpanishPostalCode(state.postalCode.trim()) &&
                state.status != ShoppingComparisonStatus.Loading
        )
        ShoppingCoverageStatus(state)
        val prices = (state.status as? ShoppingComparisonStatus.Published)?.prices
        if (prices != null) {
            val date = localizedDate().format(prices.publishedAt.atZone(ZoneId.of("Europe/Madrid")))
            if (prices.simulated) Text(stringResource(R.string.retail_simulated))
            Text(stringResource(R.string.shopping_prices_coverage, state.coveredCount, state.eligibleCount))
            PriceMetadata(stringResource(R.string.shopping_prices_publication, state.postalCode.trim(), date))
            if (state.coveredCount > 0) Text(stringResource(R.string.shopping_prices_order))
        }
    }
}

@Composable
internal fun ShoppingCoverageStatus(state: ShoppingComparisonUiState) {
    when (val status = state.status) {
        ShoppingComparisonStatus.Idle -> when {
            state.eligibleCount == 0 -> Text(stringResource(R.string.shopping_prices_no_pending))
            !validSpanishPostalCode(state.postalCode.trim()) -> RetailStatusMessage(RetailPriceStatus.LocationRequired)
            else -> Text(stringResource(R.string.shopping_prices_idle))
        }
        ShoppingComparisonStatus.Loading -> RetailStatusMessage(RetailPriceStatus.Loading)
        ShoppingComparisonStatus.Error -> RetailStatusMessage(RetailPriceStatus.ProviderError)
        is ShoppingComparisonStatus.Unavailable -> {
            RetailStatusMessage(status.reason.presentation())
            if (status.reason == RetailBatchUnavailable.NoBaseUrl) Text(stringResource(R.string.retail_no_base_url))
        }
        is ShoppingComparisonStatus.Published -> {
            val prices = status.prices
            RetailRefreshNotice(prices.fromCache && state.coveredCount > 0, prices.refreshIssue)
            val rows = prices.rows.values
            when {
                state.eligibleCount == 0 -> Text(stringResource(R.string.shopping_prices_no_pending))
                state.coveredCount > 0 -> {
                    if (!prices.simulated && !prices.fromCache) RetailStatusMessage(RetailPriceStatus.LiveData)
                }
                rows.isNotEmpty() && rows.all { it == ShoppingLinePrice.NoEquivalent } ->
                    RetailStatusMessage(RetailPriceStatus.NoProductMatch, R.string.shopping_prices_no_equivalent)
                rows.any { it == ShoppingLinePrice.Stale } -> RetailStatusMessage(RetailPriceStatus.StaleData)
                prices.refreshIssue == null -> RetailStatusMessage(RetailPriceStatus.NoPricesInArea)
            }
        }
    }
}

@Composable
internal fun ShoppingPriceLine(line: ShoppingLinePrice, onRetail: () -> Unit) {
    Column(Modifier.padding(start = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (line) {
            ShoppingLinePrice.NoEquivalent -> Text(stringResource(R.string.shopping_prices_no_equivalent))
            ShoppingLinePrice.NoPrice -> Text(stringResource(R.string.shopping_prices_no_comparable))
            ShoppingLinePrice.Stale -> RetailStatusMessage(RetailPriceStatus.StaleData)
            is ShoppingLinePrice.Options -> {
                var expanded by rememberSaveable { mutableStateOf(false) }
                Text(stringResource(R.string.shopping_prices_best), style = MaterialTheme.typography.labelLarge)
                ShoppingOption(line.values.first())
                if (line.values.size > 1) {
                    PriceLink(onClick = { expanded = !expanded }, label = stringResource(
                        if (expanded) R.string.shopping_prices_hide else R.string.shopping_prices_more
                    ))
                    if (expanded) line.values.drop(1).forEach { ShoppingOption(it) }
                }
            }
        }
        PriceLink(onClick = onRetail, label = stringResource(R.string.retail_open_detail))
    }
}

@Composable
private fun ShoppingOption(option: ShoppingPriceOption) {
    val money = rememberEuroFormat()
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    Column(Modifier.padding(vertical = 4.dp)) {
        RetailOfferDetails(option.product, option.cost.pack, option.cost.observation, option.retailer.name,
            option.store.store.name, option.store.area.postalCode, option.store.area.municipality, option.source,
            simulated = option.source.id.value == "fake-prices")
        val checkout = option.cost.checkoutEur
        if (checkout != null) {
            Text(stringResource(R.string.shopping_prices_checkout,
                numbers.format(option.cost.packageCount), money.format(checkout)),
                style = MaterialTheme.typography.titleMedium)
        } else {
            Text(stringResource(R.string.shopping_prices_weighed))
        }
        PriceMetadata(stringResource(R.string.shopping_prices_proportional, money.format(option.proportionalEur)))
    }
}

@Composable
private fun localizedDate(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
}
