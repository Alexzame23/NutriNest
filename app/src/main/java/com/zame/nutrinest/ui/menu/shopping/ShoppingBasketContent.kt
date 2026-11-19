package com.zame.nutrinest.ui.menu.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zame.nutrinest.R
import com.zame.nutrinest.domain.prices.BasketMissingReason
import com.zame.nutrinest.domain.prices.ContentUnit
import com.zame.nutrinest.domain.prices.MatchKind
import com.zame.nutrinest.domain.prices.SingleStoreBasket
import com.zame.nutrinest.domain.prices.SingleStoreBasketResult
import java.text.NumberFormat
import com.zame.nutrinest.ui.components.rememberEuroFormat

@Composable
internal fun ShoppingBasketControls(
    request: ShoppingBasketRequest,
    result: SingleStoreBasketResult?
) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.shopping_basket_title), Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium)
        val winner = result?.winner
        val summary = when {
            request.retailers.isEmpty() -> stringResource(R.string.retailer_preferences_no_effective_stores)
            result == null -> stringResource(R.string.shopping_basket_loading)
            result.rankingPending -> stringResource(R.string.shopping_basket_ranking_pending)
            winner == null -> stringResource(R.string.shopping_basket_no_complete)
            else -> stringResource(R.string.shopping_basket_winner, winner.store?.name ?: winner.retailer.name)
        }
        Text(summary, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleSmall)
        if (result != null && result.alternatives.size > 1) {
            Text(stringResource(R.string.shopping_basket_order))
        }
        if (request.retailers.isNotEmpty()) Text(stringResource(R.string.shopping_basket_stock_notice))
    }
}

@Composable
internal fun ShoppingBasketAlternative(basket: SingleStoreBasket, itemNames: Map<Long, String>) {
    val locale = LocalConfiguration.current.locales[0]
    val money = rememberEuroFormat()
    val quantity = remember(locale) { NumberFormat.getNumberInstance(locale) }
    var expanded by rememberSaveable(basket.store?.id?.value) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(basket.store?.let { stringResource(R.string.shopping_prices_store, basket.retailer.name, it.name) }
                ?: basket.retailer.name, style = MaterialTheme.typography.titleSmall)
            Text(pluralStringResource(
                if (basket.isComplete) R.plurals.shopping_basket_complete else R.plurals.shopping_basket_incomplete,
                basket.totalCount, basket.coveredCount, basket.totalCount
            ))
            basket.checkoutEur?.let { checkout ->
                Text(stringResource(if (basket.isComplete) R.string.shopping_basket_checkout
                    else R.string.shopping_basket_partial_checkout, money.format(checkout)),
                    style = MaterialTheme.typography.titleMedium)
            }
            basket.proportionalEur?.let { Text(stringResource(R.string.shopping_basket_proportional, money.format(it))) }
            if (basket.unconfirmedStockCount > 0) Text(stringResource(R.string.shopping_basket_stock_unconfirmed))
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) R.string.shopping_basket_hide else R.string.shopping_basket_details))
            }
            if (expanded) {
                for (missing in basket.missing) {
                    val name = itemNames[missing.itemId].orEmpty()
                    val reasons = missing.reasons.sorted().map { stringResource(it.label()) }.joinToString(" · ")
                    Text(stringResource(R.string.shopping_basket_missing, name, reasons))
                }
                for (line in basket.lines) {
                    Text(line.itemIds.mapNotNull(itemNames::get).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge)
                    for (purchase in line.purchases) {
                        val name = if (purchase.match.kind == MatchKind.ALTERNATIVE)
                            stringResource(R.string.shopping_basket_substitute, purchase.product.name) else purchase.product.name
                        val net = requireNotNull(purchase.pack.netContent)
                        val format = stringResource(when (net.unit) {
                            ContentUnit.G -> R.string.shopping_basket_grams
                            ContentUnit.ML -> R.string.shopping_basket_millilitres
                            ContentUnit.UNIT -> R.string.shopping_basket_units
                        }, quantity.format(net.amount))
                        Text(stringResource(R.string.shopping_basket_pack, quantity.format(purchase.count), name, format,
                            money.format(purchase.checkoutEur)))
                    }
                }
            }
        }
    }
}

internal fun BasketMissingReason.label() = when (this) {
    BasketMissingReason.NO_LOCAL_STORE -> R.string.shopping_basket_no_stores
    BasketMissingReason.NO_EQUIVALENT -> R.string.shopping_prices_no_equivalent
    BasketMissingReason.AMBIGUOUS_EQUIVALENT -> R.string.shopping_basket_ambiguous_equivalent
    BasketMissingReason.INVALID_QUANTITY -> R.string.shopping_basket_invalid_quantity
    BasketMissingReason.NO_PRICE -> R.string.shopping_basket_no_price
    BasketMissingReason.AMBIGUOUS_PRICE -> R.string.shopping_basket_ambiguous_price
    BasketMissingReason.STALE_PRICE -> R.string.retail_stale
    BasketMissingReason.PROMOTION_UNAVAILABLE -> R.string.shopping_basket_promotion_unavailable
    BasketMissingReason.UNVERIFIED_CONTENT -> R.string.shopping_prices_no_comparable
    BasketMissingReason.WEIGHT_REQUIRED -> R.string.shopping_prices_weighed
    BasketMissingReason.OUT_OF_STOCK -> R.string.shopping_basket_out_of_stock
    BasketMissingReason.SEARCH_LIMIT -> R.string.shopping_basket_search_limit
}
