package com.zame.nutrinest.data.prices

import com.zame.nutrinest.BuildConfig
import com.zame.nutrinest.domain.prices.*
import com.zame.nutrinest.domain.stores.validSpanishPostalCode
import com.zame.nutrinest.domain.stores.DirectoryStore
import com.zame.nutrinest.domain.stores.StoreRepository
import com.zame.nutrinest.domain.stores.RetailerSelectionPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

internal fun interface RetailPriceProvider {
    suspend fun publication(region: CommercialId): RetailPublication?
}

internal data class RetailPublication(val snapshot: PriceSnapshot, val simulated: Boolean)

internal fun interface RetailPriceRepository {
    suspend fun compare(ingredientId: Long, postalCode: String, now: Instant): RetailResult
}

internal sealed interface RetailResult {
    data object NoCoverage : RetailResult
    data object NoBaseUrl : RetailResult
    data object NoMatches : RetailResult
    data object Stale : RetailResult
    data object Offline : RetailResult
    data class Offers(val rows: List<RetailOffer>, val validUntil: Instant, val simulated: Boolean) : RetailResult
}

internal data class RetailOffer(
    val product: CommercialProduct,
    val retailer: Retailer,
    val store: DirectoryStore,
    val pack: ProductPackage,
    val price: PriceObservation,
    val source: PriceSource,
    val match: IngredientProductMatch,
    val normalized: EuroRatio,
    val denominator: PriceDenominator
)

private val freshness = Duration.ofDays(30)
private val thousand = BigDecimal("1000")

internal class RegionalRetailPriceRepository(
    private val ingredientKey: suspend (Long) -> RetailIngredientKeys?,
    private val stores: StoreRepository,
    private val provider: RetailPriceProvider?,
    private val allowSimulated: Boolean = false,
    private val hasNetwork: () -> Boolean = { true },
    private val selection: () -> RetailerSelectionPreferences = { RetailerSelectionPreferences() },
    private val comparisonDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val pricesEnabled: Boolean = true,
    private val rememberArea: (String) -> Unit = {}
) : RetailPriceRepository {
    override suspend fun compare(ingredientId: Long, postalCode: String, now: Instant): RetailResult {
        if (!validSpanishPostalCode(postalCode)) return RetailResult.NoCoverage
        val key = ingredientKey(ingredientId)
        PriceDiagnostics.log { "stage=ingredient.key requested=${key?.canonical ?: key?.legacy ?: "none"}" }
        if (key == null) {
            PriceDiagnostics.state("ingredient.key", PriceDiagnosis.NO_PRODUCT_MATCH)
            return RetailResult.NoMatches
        }
        val data = when (val result = load(postalCode, now)) {
            is RetailBatchPublication -> result
            RetailBatchUnavailable.NoBaseUrl -> return RetailResult.NoBaseUrl
            RetailBatchUnavailable.Offline -> return RetailResult.Offline
            RetailBatchUnavailable.Stale -> return RetailResult.Stale
            RetailBatchUnavailable.NoCoverage -> return RetailResult.NoCoverage
        }
        val selected = key.select(data.snapshot.matches.mapNotNull { it.canonicalIngredientId }.toSet())
            ?: return RetailResult.NoMatches
        return withContext(comparisonDispatcher) { compareLoaded(data, selected, now) }
    }

    private fun compareLoaded(data: RetailBatchPublication, key: CommercialId, now: Instant): RetailResult {
        val snapshot = data.snapshot
        val matches = snapshot.matches.filter { it.ingredientKey == key }.groupBy { it.packageId }
        PriceDiagnostics.log { "stage=ingredient.matches before=${snapshot.matches.size} after=${matches.values.sumOf { it.size }} packages=${matches.size}" }
        if (matches.isEmpty()) {
            PriceDiagnostics.state("ingredient.matches", PriceDiagnosis.NO_PRODUCT_MATCH)
            return RetailResult.NoMatches
        }
        val products = snapshot.products.associateBy { it.id }
        val retailers = snapshot.retailers.associateBy { it.id }
        val packages = snapshot.packages.associateBy { it.id }
        val sources = snapshot.sources.associateBy { it.id }
        val localStores = data.localStores
        val preferences = selection()

        PriceDiagnostics.log {
            "stage=ingredient.selection retailers=${preferences.selectedRetailers?.map { it.value } ?: "all"} restrictedChains=${preferences.selectedStores.size}"
        }
        val counts = if (BuildConfig.DEBUG) FilterCounts() else null
        var stale = false
        var validUntil = snapshot.expiresAt
        val offers = snapshot.prices.mapNotNull { price ->
            val record = price.record
            val scope = record.scope as? ObservationScope.AtStore ?: return@mapNotNull null
            counts?.apply { physical++ }
            val store = localStores[scope.storeId] ?: return@mapNotNull null
            counts?.apply {
                local++
                if (preferences.selectedRetailers?.contains(store.store.retailerId) != false) selectedRetailer++
            }
            if (!preferences.includes(store.store)) return@mapNotNull null
            counts?.apply { selected++ }
            val match = matches[record.packageId]?.singleOrNull() ?: return@mapNotNull null
            counts?.apply { matched++ }
            if (price.kind != PriceKind.REGULAR) return@mapNotNull null
            counts?.apply { regular++ }
            val observed = record.observedAt ?: record.observedOn.atStartOfDay(ZoneOffset.UTC).toInstant()
            if (observed > now) return@mapNotNull null
            counts?.apply { observedByNow++ }
            val expires = observed.plus(freshness)
            if (expires <= now) {
                stale = true
                return@mapNotNull null
            }
            validUntil = minOf(validUntil, expires)
            val pack = packages.getValue(record.packageId)
            val content = pack.netContent
            val denominator = when (content?.unit) {
                null, ContentUnit.G -> PriceDenominator.KG
                ContentUnit.ML -> PriceDenominator.L
                ContentUnit.UNIT -> PriceDenominator.UNIT
            }
            val scale = if (denominator == PriceDenominator.UNIT) BigDecimal.ONE else thousand
            val normalized = if (content == null) EuroRatio(price.money.amount, BigDecimal.ONE)
                else EuroRatio(price.money.amount * scale, content.amount)
            RetailOffer(products.getValue(pack.productId), retailers.getValue(record.retailerId),
                store, pack, price, sources.getValue(record.origin.sourceId), match, normalized, denominator)
        }.sortedWith(compareBy<RetailOffer> { it.denominator }.thenBy { it.normalized }
            .thenBy { it.match.kind }.thenBy { it.pack.id.value }
            .thenBy { it.price.record.origin.sourceId.value }.thenBy { it.price.record.origin.recordKey })
        PriceDiagnostics.log {
            "stage=ingredient.filters before=${snapshot.prices.size} physical=${counts?.physical} local=${counts?.local} " +
                "afterRetailer=${counts?.selectedRetailer} afterStorePreference=${counts?.selected} matched=${counts?.matched} regular=${counts?.regular} " +
                "observedByNow=${counts?.observedByNow} fresh=${offers.size}"
        }
        PriceDiagnostics.log {
            val outcome = when {
                offers.isNotEmpty() -> PriceDiagnosis.SUCCESS
                snapshot.prices.isEmpty() -> PriceDiagnosis.EMPTY_SNAPSHOT
                stale -> PriceDiagnosis.STALE_DATA
                else -> PriceDiagnosis.FILTERED_OUT
            }
            "stage=ingredient.result state=$outcome offers=${offers.size}"
        }
        if (offers.isNotEmpty()) return RetailResult.Offers(offers, validUntil, data.simulated)
        return if (stale) RetailResult.Stale else RetailResult.NoCoverage
    }

    suspend fun load(postalCode: String, now: Instant): RetailBatch {
        if (!validSpanishPostalCode(postalCode)) return RetailBatchUnavailable.NoCoverage
        rememberArea(postalCode)
        val configuredProvider = provider ?: run {
            val reason = configurationDiagnosis(pricesEnabled, false)
            PriceDiagnostics.log { "stage=provider state=$reason httpRequests=0" }
            return if (BuildConfig.DEBUG && reason == PriceDiagnosis.NO_BASE_URL) RetailBatchUnavailable.NoBaseUrl
                else RetailBatchUnavailable.NoCoverage
        }
        if (!hasNetwork()) {
            PriceDiagnostics.state("network", PriceDiagnosis.NO_NETWORK)
            return RetailBatchUnavailable.Offline
        }
        stores.refresh()
        val available = stores.findByArea(postalCode).map { it.entry }
        val region = available.map { it.area.regionId }.distinct().singleOrNull() ?: run {
            PriceDiagnostics.state("area", PriceDiagnosis.NO_REGION)
            return RetailBatchUnavailable.NoCoverage
        }
        val publication = try {
            configuredProvider.publication(region)
        } catch (error: IOException) {
            if (!hasNetwork()) {
                PriceDiagnostics.state("network", PriceDiagnosis.NO_NETWORK)
                return RetailBatchUnavailable.Offline
            }
            PriceDiagnostics.log { "stage=transport error=${error.javaClass.simpleName}" }
            throw error
        } ?: return RetailBatchUnavailable.NoCoverage
        val snapshot = publication.snapshot
        PriceDiagnostics.log { "stage=snapshot region=${region.value} revision=${snapshot.revision} observations=${snapshot.prices.size}" }
        if (snapshot.prices.isEmpty()) PriceDiagnostics.state("snapshot", PriceDiagnosis.EMPTY_SNAPSHOT)
        require(snapshot.regionId == region)
        if (publication.simulated && !allowSimulated) {
            PriceDiagnostics.log { "stage=snapshot rejected=simulated" }
            return RetailBatchUnavailable.NoCoverage
        }
        if (now < snapshot.generatedAt) {
            PriceDiagnostics.log { "stage=snapshot rejected=future" }
            return RetailBatchUnavailable.NoCoverage
        }
        if (now >= snapshot.expiresAt) {
            PriceDiagnostics.state("snapshot", PriceDiagnosis.STALE_DATA)
            return RetailBatchUnavailable.Stale
        }
        val localStores = snapshot.stores.mapNotNull { store ->
            available.singleOrNull {
                (store.postalCode == null || store.postalCode == postalCode) &&
                    (it.store.id == store.id || (store.osmRef != null && it.store.osmRef == store.osmRef))
            }?.let { store.id to it }
        }.toMap()

        PriceDiagnostics.log {
            val chains = localStores.values.map { it.store.retailerId.value }.distinct()
            "stage=store.identity before=${snapshot.stores.size} after=${localStores.size} localRetailers=$chains"
        }
        return RetailBatchPublication(snapshot, localStores, publication.simulated)
    }

    private class FilterCounts {
        var physical = 0
        var local = 0
        var selectedRetailer = 0
        var selected = 0
        var matched = 0
        var regular = 0
        var observedByNow = 0
    }
}

internal sealed interface RetailBatch
internal enum class RetailBatchUnavailable : RetailBatch { NoCoverage, NoBaseUrl, Offline, Stale }
internal data class RetailBatchPublication(
    val snapshot: PriceSnapshot,
    val localStores: Map<CommercialId, DirectoryStore>,
    val simulated: Boolean
) : RetailBatch
