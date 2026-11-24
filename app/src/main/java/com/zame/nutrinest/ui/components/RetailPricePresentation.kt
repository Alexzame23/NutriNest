package com.zame.nutrinest.ui.components

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.zame.nutrinest.R
import com.zame.nutrinest.data.prices.RetailBatchUnavailable
import com.zame.nutrinest.domain.prices.CommercialProduct
import com.zame.nutrinest.domain.prices.Confidence
import com.zame.nutrinest.domain.prices.ContentUnit
import com.zame.nutrinest.domain.prices.PriceObservation
import com.zame.nutrinest.domain.prices.PriceSource
import com.zame.nutrinest.domain.prices.ProductPackage
import com.zame.nutrinest.domain.prices.SaleUnit
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal enum class RetailPriceStatus(@get:StringRes val message: Int) {
    Loading(R.string.retail_loading),
    LiveData(R.string.retail_live_data),
    StaleData(R.string.retail_stale),
    NoProviderCoverage(R.string.retail_no_coverage),
    NoProductMatch(R.string.retail_no_matches),
    NoPricesInArea(R.string.retail_no_prices_in_area),
    NetworkError(R.string.retail_offline),
    ProviderError(R.string.retail_error),
    LocationRequired(R.string.retail_enter_area),
    UserProvidedPrice(R.string.retail_user_provided)
}

internal fun RetailBatchUnavailable.presentation() = when (this) {
    RetailBatchUnavailable.NoCoverage -> RetailPriceStatus.NoProviderCoverage
    RetailBatchUnavailable.NoBaseUrl, RetailBatchUnavailable.ProviderError -> RetailPriceStatus.ProviderError
    RetailBatchUnavailable.Offline -> RetailPriceStatus.NetworkError
    RetailBatchUnavailable.Stale -> RetailPriceStatus.StaleData
}

@Composable
internal fun RetailStatusMessage(status: RetailPriceStatus, @StringRes message: Int = status.message) {
    if (status == RetailPriceStatus.Loading) {
        PriceLoading()
    } else {
        Text(stringResource(message), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
internal fun RetailRefreshNotice(fromCache: Boolean, issue: RetailBatchUnavailable?) {
    if (fromCache) PriceMetadata(stringResource(R.string.retail_cached))
    if (issue != null) RetailStatusMessage(issue.presentation())
}

@Composable
internal fun RetailOfferDetails(
    product: CommercialProduct,
    pack: ProductPackage,
    price: PriceObservation,
    retailerName: String,
    storeName: String,
    postalCode: String?,
    municipality: String?,
    source: PriceSource,
    simulated: Boolean = false
) {
    val locale = LocalConfiguration.current.locales[0]
    val number = remember(locale) { NumberFormat.getNumberInstance(locale) }
    val money = rememberEuroFormat()
    val rate = rememberEuroFormat(4)
    val content = pack.netContent
    Text(product.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
    if (simulated) Text(stringResource(R.string.retail_simulated))
    Text(stringResource(R.string.retail_chain_brand, retailerName,
        product.brand?.takeIf { it.isNotBlank() } ?: stringResource(R.string.retail_unknown_brand)))
    val area = listOfNotNull(postalCode, municipality?.takeIf { it.isNotBlank() }).joinToString(" ")
    Text(if (area.isEmpty()) stringResource(R.string.retail_store_only, storeName)
        else stringResource(R.string.retail_store_area, storeName, area))
    Text(if (content == null) stringResource(R.string.retail_variable_weight) else {
        val unit = when (content.unit) {
            ContentUnit.G -> R.string.retail_grams
            ContentUnit.ML -> R.string.retail_millilitres
            ContentUnit.UNIT -> R.string.retail_pieces
        }
        stringResource(R.string.retail_fixed_format, number.format(content.amount),
            stringResource(unit), requireNotNull(pack.unitsPerPackage))
    })
    Text(stringResource(
        if (price.per == SaleUnit.PACKAGE) R.string.retail_package_price else R.string.retail_weight_price,
        money.format(price.money.amount)
    ), style = MaterialTheme.typography.titleMedium)
    val unitPrice = when (content?.unit) {
        null, ContentUnit.G -> R.string.retail_per_kg
        ContentUnit.ML -> R.string.retail_per_litre
        ContentUnit.UNIT -> R.string.retail_per_piece
    }
    val normalized = if (content == null) price.money.amount else {
        val scale = if (content.unit == ContentUnit.UNIT) BigDecimal.ONE else BigDecimal("1000")
        price.money.amount.multiply(scale).divide(content.amount, 4, RoundingMode.HALF_UP)
    }
    PriceMetadata(stringResource(R.string.retail_normalized, stringResource(unitPrice, rate.format(normalized))))
    RetailObservationMetadata(price, source)
}

@Composable
internal fun RetailObservationMetadata(price: PriceObservation, source: PriceSource) {
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    PriceMetadata(stringResource(R.string.retail_observed, date.format(price.record.observedOn)))
    PriceMetadata(stringResource(R.string.retail_source, source.name, source.attribution))
    if (source.id.value == "open-prices") {
        PriceMetadata(stringResource(RetailPriceStatus.UserProvidedPrice.message))
    }
    PriceMetadata(stringResource(when (price.record.confidence) {
        Confidence.HIGH -> R.string.retail_evidence_high
        Confidence.MEDIUM -> R.string.retail_evidence_medium
        Confidence.LOW -> R.string.retail_evidence_low
    }))
}
