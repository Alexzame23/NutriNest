package com.zame.nutrinest.data.prices

import com.zame.nutrinest.domain.prices.*
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
    private val comparisonDispatcher: CoroutineDispatcher = Dispatchers.Default
) : RetailPriceRepository {
    override suspend fun compare(ingredientId: Long, postalCode: String, now: Instant): RetailResult {
        if (!postalCode.matches(Regex("[0-9]{5}"))) return RetailResult.NoCoverage
        val available = stores.findByArea(postalCode).map { it.entry }
        val region = available.map { it.area.regionId }.distinct().singleOrNull() ?: return RetailResult.NoCoverage
        val key = ingredientKey(ingredientId) ?: return RetailResult.NoMatches
        val data = when (val result = load(available, region, now)) {
            is RetailBatchPublication -> result
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
        if (matches.isEmpty()) return RetailResult.NoMatches
        val products = snapshot.products.associateBy { it.id }
        val retailers = snapshot.retailers.associateBy { it.id }
        val packages = snapshot.packages.associateBy { it.id }
        val sources = snapshot.sources.associateBy { it.id }
        val localStores = data.localStores
        val preferences = selection()

        var stale = false
        var validUntil = snapshot.expiresAt
        val offers = snapshot.prices.mapNotNull { price ->
            val record = price.record
            val scope = record.scope as? ObservationScope.AtStore ?: return@mapNotNull null
            val store = localStores[scope.storeId] ?: return@mapNotNull null
            if (!preferences.includes(store.store)) return@mapNotNull null
            val match = matches[record.packageId]?.singleOrNull() ?: return@mapNotNull null
            if (price.kind != PriceKind.REGULAR) return@mapNotNull null
            val observed = record.observedAt ?: record.observedOn.atStartOfDay(ZoneOffset.UTC).toInstant()
            if (observed > now) return@mapNotNull null
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
        if (offers.isNotEmpty()) return RetailResult.Offers(offers, validUntil, data.simulated)
        return if (stale) RetailResult.Stale else RetailResult.NoCoverage
    }

    suspend fun load(postalCode: String, now: Instant): RetailBatch {
        if (!postalCode.matches(Regex("[0-9]{5}"))) return RetailBatchUnavailable.NoCoverage
        val available = stores.findByArea(postalCode).map { it.entry }
        val region = available.map { it.area.regionId }.distinct().singleOrNull()
            ?: return RetailBatchUnavailable.NoCoverage
        return load(available, region, now)
    }

    private suspend fun load(available: List<DirectoryStore>, region: CommercialId, now: Instant): RetailBatch {
        val configuredProvider = provider ?: return RetailBatchUnavailable.NoCoverage
        if (!hasNetwork()) return RetailBatchUnavailable.Offline
        val publication = try {
            configuredProvider.publication(region)
        } catch (error: IOException) {
            if (!hasNetwork()) return RetailBatchUnavailable.Offline
            throw error
        } ?: return RetailBatchUnavailable.NoCoverage
        val snapshot = publication.snapshot
        require(snapshot.regionId == region)
        if (publication.simulated && !allowSimulated) return RetailBatchUnavailable.NoCoverage
        if (now < snapshot.generatedAt) return RetailBatchUnavailable.NoCoverage
        if (now >= snapshot.expiresAt) return RetailBatchUnavailable.Stale
        val localStores = snapshot.stores.mapNotNull { store ->
            available.singleOrNull {
                it.store.id == store.id || (store.osmRef != null && it.store.osmRef == store.osmRef)
            }?.let { store.id to it }
        }.toMap()

        return RetailBatchPublication(snapshot, localStores, publication.simulated)
    }
}

internal sealed interface RetailBatch
internal enum class RetailBatchUnavailable : RetailBatch { NoCoverage, Offline, Stale }
internal data class RetailBatchPublication(
    val snapshot: PriceSnapshot,
    val localStores: Map<CommercialId, DirectoryStore>,
    val simulated: Boolean
) : RetailBatch
