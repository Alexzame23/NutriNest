package com.zame.nutrinest.data.prices

import com.zame.nutrinest.BuildConfig
import com.zame.nutrinest.domain.prices.*
import com.zame.nutrinest.domain.stores.validSpanishPostalCode
import com.zame.nutrinest.domain.stores.DirectoryStore
import com.zame.nutrinest.domain.stores.DiscoveryArea
import com.zame.nutrinest.domain.stores.StoreRepository
import com.zame.nutrinest.domain.stores.RetailerSelectionPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import java.time.Clock
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant

internal fun interface RetailPriceProvider {
    suspend fun publication(region: CommercialId): RetailPublication?
}

internal data class RetailPublication(val snapshot: PriceSnapshot, val simulated: Boolean)

internal fun interface RetailPriceRepository {
    suspend fun compare(ingredientId: Long, postalCode: String, now: Instant): RetailResult
    suspend fun cached(ingredientId: Long, postalCode: String, now: Instant): RetailResult? = null
}

internal sealed interface RetailResult {
    data object NoCoverage : RetailResult
    data object NoBaseUrl : RetailResult
    data object NoMatches : RetailResult
    data object NoPricesInArea : RetailResult
    data object Stale : RetailResult
    data object Offline : RetailResult
    data object ProviderError : RetailResult
    data class Offers(val rows: List<RetailOffer>, val validUntil: Instant, val simulated: Boolean, val fromCache: Boolean = false,
        val refreshIssue: RetailBatchUnavailable? = null
    ) : RetailResult
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

internal val retailSourceFreshness = mapOf(CommercialId("open-prices") to Duration.ofDays(30))
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
    private val rememberArea: (String) -> Unit = {},
    private val cache: RoomPriceSnapshotCache? = null,
    private val clock: Clock = Clock.systemUTC()
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
            is RetailBatchUnavailable -> return result.ingredientResult()
        }
        return offers(data, key, effectiveTime(now))
    }

    override suspend fun cached(ingredientId: Long, postalCode: String, now: Instant): RetailResult? {
        val data = cached(postalCode, now) ?: return null
        val key = ingredientKey(ingredientId) ?: return null
        return offers(data, key, now)
    }

    private suspend fun offers(data: RetailBatchPublication, key: RetailIngredientKeys, now: Instant): RetailResult {
        if (now >= data.snapshot.expiresAt) return RetailResult.Stale
        val selected = key.select(data.snapshot.matches.mapNotNull { it.canonicalIngredientId }.toSet())
            ?: return data.refreshIssue?.ingredientResult() ?: RetailResult.NoMatches
        return withContext(comparisonDispatcher) { compareLoaded(data, selected, now) }
    }

    private fun effectiveTime(now: Instant) = if (cache == null) now else maxOf(now, clock.instant())

    private fun compareLoaded(data: RetailBatchPublication, key: CommercialId, now: Instant): RetailResult {
        val snapshot = data.snapshot
        val matches = snapshot.matches.filter { it.ingredientKey == key }.groupBy { it.packageId }
        PriceDiagnostics.log { "stage=ingredient.matches before=${snapshot.matches.size} after=${matches.values.sumOf { it.size }} packages=${matches.size}" }
        if (matches.isEmpty()) {
            PriceDiagnostics.state("ingredient.matches", PriceDiagnosis.NO_PRODUCT_MATCH)
            return data.refreshIssue?.ingredientResult() ?: RetailResult.NoMatches
        }
        val products = snapshot.products.associateBy { it.id }
        val retailers = snapshot.retailers.associateBy { it.id }
        val packages = snapshot.packages.associateBy { it.id }
        val sources = snapshot.sources.associateBy { it.id }
        val localStores = data.localStores
        if (localStores.isEmpty() && data.refreshIssue == null) return RetailResult.NoPricesInArea
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
            val observed = record.observedInstant
            if (observed > now) return@mapNotNull null
            counts?.apply { observedByNow++ }
            val expires = observed.plus(retailSourceFreshness[record.origin.sourceId] ?: Duration.ofDays(30))
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
        if (offers.isNotEmpty()) return RetailResult.Offers(
            offers, validUntil, data.simulated, data.fromCache, data.refreshIssue
        )
        return data.refreshIssue?.ingredientResult()
            ?: if (stale) RetailResult.Stale else RetailResult.NoPricesInArea
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
            return cached(postalCode, effectiveTime(now))?.copy(refreshIssue = RetailBatchUnavailable.Offline)
                ?: RetailBatchUnavailable.Offline
        }
        var region: CommercialId? = null
        val started = effectiveTime(now)
        val publication = try {
            stores.refresh(force = cache != null)
            val entries = stores.findByArea(postalCode)
            region = entries.map { it.entry.area.regionId }.distinct().singleOrNull()
            val target = region ?: if (!stores.hasCurrentDirectory()) cached(postalCode, effectiveTime(now))?.snapshot?.regionId else null
            if (target == null) {
                PriceDiagnostics.state("area", PriceDiagnosis.NO_REGION)
                return RetailBatchUnavailable.NoCoverage
            }
            region = target
            val result = configuredProvider.publication(target)
            if (cache != null) {
                val completed = effectiveTime(now)
                val failure = when {
                    result == null -> "PUBLICATION_UNAVAILABLE"
                    result.simulated -> "UNVERIFIED_PUBLICATION"
                    completed < result.snapshot.generatedAt || completed >= result.snapshot.expiresAt -> "PUBLICATION_NOT_CURRENT"
                    else -> null
                }
                cache.recordSync(target, started, completed, failure)
                cache.pruneExpired(effectiveTime(now))
            }
            result
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            coroutineContext.ensureActive()
            cache?.recordSync(region, started, effectiveTime(now), when (error) {
                is RetailHttpError -> "HTTP_${error.status}"
                is EmptyRetailPublication -> "EMPTY_SNAPSHOT"
                is InvalidPriceDigest -> "HASH_MISMATCH"
                is java.net.SocketTimeoutException -> "TIMEOUT"
                is IOException -> "IO"
                else -> "INVALID_PUBLICATION"
            })
            val issue = if (error.isRetailNetworkFailure() || !hasNetwork()) RetailBatchUnavailable.Offline
                else RetailBatchUnavailable.ProviderError
            cached(postalCode, effectiveTime(now), region)?.let { return it.copy(refreshIssue = issue) }
            if (issue == RetailBatchUnavailable.Offline) return issue
            throw error
        } ?: return fallback(postalCode, effectiveTime(now), region, RetailBatchUnavailable.NoCoverage)
        val available = stores.findByArea(postalCode).map { it.entry }
        val snapshot = publication.snapshot
        PriceDiagnostics.log { "stage=snapshot region=${region.value} revision=${snapshot.revision} observations=${snapshot.prices.size}" }
        if (snapshot.prices.isEmpty()) PriceDiagnostics.state("snapshot", PriceDiagnosis.EMPTY_SNAPSHOT)
        require(snapshot.regionId == region)
        if (publication.simulated && (!BuildConfig.DEBUG || !allowSimulated)) {
            PriceDiagnostics.log { "stage=snapshot rejected=simulated" }
            return RetailBatchUnavailable.NoCoverage
        }
        if (effectiveTime(now) < snapshot.generatedAt) {
            PriceDiagnostics.log { "stage=snapshot rejected=future" }
            return fallback(postalCode, effectiveTime(now), region, RetailBatchUnavailable.ProviderError)
        }
        if (effectiveTime(now) >= snapshot.expiresAt) {
            PriceDiagnostics.state("snapshot", PriceDiagnosis.STALE_DATA)
            return fallback(postalCode, effectiveTime(now), region, RetailBatchUnavailable.Stale)
        }
        return batch(snapshot, postalCode, available, publication.simulated, fromCache = false)
            .copy(checkedAt = effectiveTime(now))
    }

    private suspend fun fallback(
        postalCode: String, now: Instant, region: CommercialId?, issue: RetailBatchUnavailable
    ): RetailBatch = cached(postalCode, now, region)?.copy(refreshIssue = issue) ?: issue

    suspend fun cached(postalCode: String, now: Instant): RetailBatchPublication? =
        cached(postalCode, now, null)

    private suspend fun cached(postalCode: String, now: Instant, region: CommercialId?): RetailBatchPublication? {
        if (!pricesEnabled || provider == null || cache == null || !validSpanishPostalCode(postalCode)) return null
        val available = stores.findByArea(postalCode).map { it.entry }
        val regions = available.map { it.area.regionId }.distinct()
        val target = region ?: regions.singleOrNull()
        val snapshot = if (target != null) cache.read(target, now)
            else if (regions.isEmpty() && !stores.hasCurrentDirectory()) cache.readPostal(postalCode, now) else null
        return snapshot?.takeUnless { saved -> saved.sources.any { it.id.value == "fake-prices" } }
            ?.let { batch(it, postalCode, available, simulated = false, fromCache = true) }
            ?.takeIf { it.localStores.isNotEmpty() }
    }

    private fun batch(
        snapshot: PriceSnapshot, postalCode: String, available: List<DirectoryStore>,
        simulated: Boolean, fromCache: Boolean
    ): RetailBatchPublication {
        val localStores = snapshot.stores.mapNotNull { store ->
            if (store.postalCode != null && store.postalCode != postalCode) return@mapNotNull null
            val entry = available.singleOrNull {
                it.area.regionId == snapshot.regionId &&
                    (it.store.id == store.id || (store.osmRef != null && it.store.osmRef == store.osmRef))
            } ?: if (cache != null && store.postalCode == postalCode) DirectoryStore(
                store, DiscoveryArea(snapshot.regionId, "", postalCode,
                    snapshot.generatedAt.atZone(ZoneOffset.UTC).toLocalDate()), "", null,
                store.osmRef?.let { "https://www.openstreetmap.org/$it" }
            ) else null
            entry?.let { store.id to it }
        }.toMap()
        return RetailBatchPublication(snapshot, localStores, simulated, fromCache)
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
internal enum class RetailBatchUnavailable : RetailBatch { NoCoverage, NoBaseUrl, Offline, Stale, ProviderError }
internal data class RetailBatchPublication(
    val snapshot: PriceSnapshot,
    val localStores: Map<CommercialId, DirectoryStore>,
    val simulated: Boolean,
    val fromCache: Boolean = false,
    val checkedAt: Instant? = null,
    val refreshIssue: RetailBatchUnavailable? = null
) : RetailBatch

private fun RetailBatchUnavailable.ingredientResult(): RetailResult = when (this) {
    RetailBatchUnavailable.NoCoverage -> RetailResult.NoCoverage
    RetailBatchUnavailable.NoBaseUrl -> RetailResult.NoBaseUrl
    RetailBatchUnavailable.Offline -> RetailResult.Offline
    RetailBatchUnavailable.Stale -> RetailResult.Stale
    RetailBatchUnavailable.ProviderError -> RetailResult.ProviderError
}

internal fun Throwable.isRetailNetworkFailure(): Boolean =
    this is IOException && this !is javax.net.ssl.SSLException
