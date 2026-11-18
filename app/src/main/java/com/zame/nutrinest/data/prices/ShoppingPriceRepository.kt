package com.zame.nutrinest.data.prices

import com.zame.nutrinest.domain.nutrition.MassBasis
import com.zame.nutrinest.domain.nutrition.Quantity
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import com.zame.nutrinest.domain.prices.*
import com.zame.nutrinest.domain.stores.DirectoryStore
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

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
        val simulated: Boolean
    ) : ShoppingPrices
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
        val demands = items.mapNotNull { item ->
            identities[item.ingredientId]?.let { key ->
                item.id to PriceDemand(key, Quantity(item.grams, UnitDefinition.GRAM, MassBasis.EDIBLE))
            }
        }.toMap()
        val packages = snapshot.packages.associateBy { it.id }
        val products = snapshot.products.associateBy { it.id }
        val retailers = snapshot.retailers.associateBy { it.id }
        val sources = snapshot.sources.associateBy { it.id }
        val localPrices = snapshot.prices.filter { quote ->
            val scope = quote.record.scope as? ObservationScope.AtStore
            scope != null && scope.storeId in publication.localStores
        }
        val evidence = snapshot.matches.mapNotNull { match ->
            val mass = match.edibleMass ?: return@mapNotNull null
            val pack = packages.getValue(match.packageId)
            val upperBound = pack.netContent?.takeIf { it.unit == ContentUnit.G }?.amount
                ?: if (pack.weightMode == WeightMode.VARIABLE) BigDecimal("1000") else return@mapNotNull null
            if (mass.grams > upperBound) return@mapNotNull null
            VerifiedMassContent(pack, match, MassBasis.EDIBLE, mass.grams, mass.sourceRef)
        }.groupBy { it.match.ingredientKey }
        val policy = ComparisonPolicy(now, ZoneId.of("Europe/Madrid"))
        val compared = PriceComparisonEngine.compareBatch(demands, snapshot, policy, localPrices, evidence)
        val exactKeys = snapshot.matches.filter { it.kind == MatchKind.EXACT }.map { it.ingredientKey }.toSet()
        val rows = items.associate { item ->
            val comparison = compared[item.id]
            val options = comparison?.options.orEmpty().map { option ->
                val record = option.observation.record
                val observed = record.observedAt ?: record.observedOn.atStartOfDay(ZoneOffset.UTC).toInstant()
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
        return ShoppingPrices.Published(rows, snapshot.generatedAt, snapshot.expiresAt, publication.simulated)
    }
}
