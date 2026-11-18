package com.zame.nutrinest.data.prices

import com.zame.nutrinest.domain.nutrition.MassBasis
import com.zame.nutrinest.domain.nutrition.Quantity
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import com.zame.nutrinest.domain.prices.*
import com.zame.nutrinest.domain.stores.DirectoryStore
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

internal fun interface ShoppingPriceRepository {
    suspend fun compare(items: List<ShoppingPriceDemand>, postalCode: String, now: Instant): ShoppingPrices
}

internal data class ShoppingPriceDemand(val id: Long, val ingredientId: Long, val grams: Double)

internal sealed interface ShoppingPrices {
    data class Unavailable(val reason: RetailBatchUnavailable) : ShoppingPrices
    data class Published(
        val rows: Map<Long, ShoppingLinePrice>,
        val publishedAt: Instant,
        val expiresAt: Instant,
        val simulated: Boolean,
        val basketCatalog: ShoppingBasketCatalog? = null,
        val pricedAt: Instant = publishedAt
    ) : ShoppingPrices
}

internal data class ShoppingBasketCatalog(
    val snapshot: PriceSnapshot,
    val stores: Set<CommercialId>,
    val demands: Map<Long, PriceDemand?>,
    val policy: ComparisonPolicy
) {
    val retailers = snapshot.retailers.filter { retailer ->
        snapshot.stores.any { it.id in stores && it.retailerId == retailer.id }
    }.sortedWith(compareBy<Retailer> { it.name }.thenBy { it.id.value })

    private val changes = (snapshot.prices.map { it.record } + snapshot.availability.map { it.record })
        .filter { (it.scope as? ObservationScope.AtStore)?.storeId in stores }
        .flatMap { listOf(it.observedInstant, it.observedInstant.plus(policy.freshness)) }
        .distinct().sorted()

    fun nextChange(now: Instant): Instant = minOf(snapshot.expiresAt, changes.firstOrNull { it > now } ?: snapshot.expiresAt)
}

internal sealed interface ShoppingLinePrice {
    data object NoEquivalent : ShoppingLinePrice
    data object NoPrice : ShoppingLinePrice
    data object Stale : ShoppingLinePrice
    data class Options(val values: List<ShoppingPriceOption>) : ShoppingLinePrice
}

internal data class ShoppingPriceOption(
    val cost: ComparedPrice,
    val product: CommercialProduct,
    val retailer: Retailer,
    val store: DirectoryStore,
    val source: PriceSource,
    val expiresAt: Instant
) {
    val proportionalEur: BigDecimal = cost.proportional.decimal(2)
}

internal class RegionalShoppingPriceRepository(
    private val load: suspend (String, Instant) -> RetailBatch,
    private val keys: suspend (Set<Long>) -> Map<Long, CommercialId>
) : ShoppingPriceRepository {
    override suspend fun compare(items: List<ShoppingPriceDemand>, postalCode: String, now: Instant): ShoppingPrices {
        val publication = when (val batch = load(postalCode, now)) {
            is RetailBatchUnavailable -> return ShoppingPrices.Unavailable(batch)
            is RetailBatchPublication -> batch
        }
        val snapshot = publication.snapshot
        val identities = keys(items.map { it.ingredientId }.toSet())
        val demands = items.associate { item ->
            item.id to identities[item.ingredientId]?.let { key ->
                PriceDemand(key, Quantity(item.grams, UnitDefinition.GRAM, MassBasis.EDIBLE))
            }
        }
        val products = snapshot.products.associateBy { it.id }
        val retailers = snapshot.retailers.associateBy { it.id }
        val sources = snapshot.sources.associateBy { it.id }
        val localPrices = snapshot.prices.filter { quote ->
            val scope = quote.record.scope as? ObservationScope.AtStore
            scope != null && scope.storeId in publication.localStores
        }
        val evidence = snapshot.reviewedMassContents().groupBy { it.match.ingredientKey }
        val policy = ComparisonPolicy(now, ZoneId.of("Europe/Madrid"))
        val compared = PriceComparisonEngine.compareBatch(demands.mapNotNull { (id, demand) ->
            demand?.let { id to it }
        }.toMap(), snapshot, policy, localPrices, evidence)
        val exactKeys = snapshot.matches.filter { it.kind == MatchKind.EXACT }.map { it.ingredientKey }.toSet()
        val rows = items.associate { item ->
            val comparison = compared[item.id]
            val options = comparison?.options.orEmpty().map { option ->
                val record = option.observation.record
                val observed = record.observedInstant
                val scope = record.scope as ObservationScope.AtStore
                ShoppingPriceOption(option, products.getValue(option.pack.productId),
                    retailers.getValue(record.retailerId), publication.localStores.getValue(scope.storeId),
                    sources.getValue(record.origin.sourceId), minOf(snapshot.expiresAt, observed.plus(policy.freshness)))
            }
            item.id to when {
                options.isNotEmpty() -> ShoppingLinePrice.Options(options)
                identities[item.ingredientId] !in exactKeys -> ShoppingLinePrice.NoEquivalent
                comparison?.excluded?.any { it.error == ComparisonError.STALE_PRICE } == true -> ShoppingLinePrice.Stale
                else -> ShoppingLinePrice.NoPrice
            }
        }
        val catalog = ShoppingBasketCatalog(snapshot, publication.localStores.keys, demands, policy)
        return ShoppingPrices.Published(rows, snapshot.generatedAt, snapshot.expiresAt, publication.simulated, catalog, now)
    }
}
