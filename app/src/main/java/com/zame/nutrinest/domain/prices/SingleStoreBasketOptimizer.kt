package com.zame.nutrinest.domain.prices

import com.zame.nutrinest.domain.nutrition.MassBasis
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object SingleStoreBasketOptimizer {
    private const val MAX_TRANSITIONS = 100_000
    private val zero = BigDecimal.ZERO
    private val noPacks = BigInteger.ZERO

    /**
     * A null demand retains a list row whose canonical ingredient has no commercial key.
     * Local store IDs must be verified for the requested area in this regional publication.
     * Only store-scoped observations can contribute to a physical checkout.
     */
    fun optimize(
        demands: Map<Long, PriceDemand?>,
        selectedRetailers: Set<CommercialId>,
        regionId: CommercialId,
        localStoreIds: Set<CommercialId>,
        snapshot: PriceSnapshot,
        policy: ComparisonPolicy,
        promotions: List<VerifiedPromotion> = emptyList(),
        authorizedSubstitutions: Set<IngredientProductMatch> = emptySet(),
        additionalMassContents: List<VerifiedMassContent> = emptyList(),
        checkCancellation: () -> Unit = {}
    ): SingleStoreBasketResult {
        val retailers = snapshot.retailers.filter { it.id in selectedRetailers }
        val stores = snapshot.stores.filter {
            regionId == snapshot.regionId && it.id in localStoreIds && it.retailerId in selectedRetailers
        }.groupBy { it.retailerId }
        val eligibleStores = stores.values.flatten().map { it.id }.toSet()
        val prices = snapshot.prices.filter { it.record.storeId in eligibleStores }
        val stock = snapshot.availability.filter {
            it.record.storeId in eligibleStores && it.record.observedInstant <= policy.now &&
                it.record.observedInstant > policy.now.minus(policy.freshness)
        }.groupBy { it.record.storeId to it.record.packageId }.mapValues { (_, observations) ->
            observations.maxWith(compareBy<AvailabilityObservation> { it.record.observedInstant }
                .thenBy { stockCaution(it.state) }).state
        }
        val unresolved = mutableListOf<BasketMissingItem>()
        val groups = mutableMapOf<DemandKey, DemandGroup>()
        for ((id, demand) in demands.toSortedMap()) {
            if (demand == null || !demand.quantity.amount.isFinite() || demand.quantity.amount <= 0) {
                unresolved += BasketMissingItem(id, setOf(if (demand == null) BasketMissingReason.NO_EQUIVALENT
                    else BasketMissingReason.INVALID_QUANTITY))
                continue
            }
            val unit = ContentUnit.entries.firstNotNullOfOrNull { unit ->
                PriceComparisonEngine.baseAmount(demand, unit)?.let { unit to it }
            }
            if (unit == null) {
                unresolved += BasketMissingItem(id, setOf(BasketMissingReason.UNVERIFIED_CONTENT))
                continue
            }
            val (contentUnit, amount) = unit
            val key = DemandKey(demand.ingredientKey, contentUnit,
                demand.quantity.massBasis.takeIf { contentUnit == ContentUnit.G })
            val group = groups.getOrPut(key) { DemandGroup(demand, contentUnit) }
            group.ids += id
            group.amount += amount
        }
        val groupedDemands = groups.values.associateBy { it.ids.first() }
        val comparisons = PriceComparisonEngine.compareBatch(
            groupedDemands.mapValues { it.value.sample }, snapshot, policy, prices,
            (snapshot.reviewedMassContents() + additionalMassContents).distinct().groupBy { it.match.ingredientKey },
            promotions, authorizedSubstitutions
        ).mapValues { (_, result) ->
            StoreComparisons(result.options.groupBy { it.observation.record.storeId },
                result.excluded.groupBy { it.observation?.storeId })
        }
        val products = snapshot.products.associateBy { it.id }
        var rankingPending = false
        val alternatives = retailers.map { retailer ->
            val local = stores[retailer.id].orEmpty()
            if (local.isEmpty()) {
                SingleStoreBasket(retailer, null, emptyList(),
                    demands.keys.sorted().map { BasketMissingItem(it, setOf(BasketMissingReason.NO_LOCAL_STORE)) },
                    null, null)
            } else {
                local.map { store ->
                    checkCancellation()
                    val missing = unresolved.toMutableList()
                    val lines = mutableListOf<BasketLine>()
                    // The editorial rules require resolving rival canonicals for a SKU.
                    // Exclude any such collision that survives in this store before allocating packs.
                    val sharedPacks = comparisons.values.flatMap { comparison ->
                        comparison.options[store.id].orEmpty().map { it.pack.id }.distinct()
                    }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
                    for ((id, group) in groupedDemands) {
                        checkCancellation()
                        val comparison = comparisons.getValue(id)
                        val reasons = (comparison.excluded[null].orEmpty() + comparison.excluded[store.id].orEmpty())
                            .map { it.error.basketReason() }.toMutableSet()
                        val skus = comparison.options[store.id].orEmpty().groupBy { it.pack.id }.mapNotNull { (pack, offers) ->
                            val state = stock[store.id to pack] ?: StockState.UNKNOWN
                            val reason = when {
                                pack in sharedPacks -> BasketMissingReason.AMBIGUOUS_EQUIVALENT
                                state == StockState.OUT_OF_STOCK -> BasketMissingReason.OUT_OF_STOCK
                                offers.first().pack.weightMode == WeightMode.VARIABLE -> BasketMissingReason.WEIGHT_REQUIRED
                                else -> null
                            }
                            if (reason != null) {
                                reasons += reason
                                return@mapNotNull null
                            }
                            val regular = offers.filter { it.promotion == null }
                            val latest = regular.maxOfOrNull { it.observation.record.observedInstant }
                            val current = regular.filter { it.observation.record.observedInstant == latest }
                            val base = if (current.map { it.observation.money.amount.stripTrailingZeros() }.distinct().size > 1) {
                                reasons += BasketMissingReason.AMBIGUOUS_PRICE
                                null
                            } else current.minWithOrNull(offerIdentity)
                            val schedules = (listOfNotNull(base) + offers.filter { it.promotion != null })
                                .sortedWith(offerIdentity)
                            if (schedules.isEmpty()) return@mapNotNull null
                            val sample = schedules.first()
                            Sku(sample.massEvidence?.grams ?: requireNotNull(sample.pack.netContent).amount,
                                state, schedules)
                        }
                        val plan = if (skus.isEmpty()) null else try {
                            solve(group.amount, skus, checkCancellation)
                        } catch (_: SearchLimit) {
                            reasons.clear()
                            reasons += BasketMissingReason.SEARCH_LIMIT
                            null
                        }
                        if (plan == null) {
                            missing += group.ids.map {
                                BasketMissingItem(it, reasons.ifEmpty { setOf(BasketMissingReason.NO_PRICE) })
                            }
                        } else {
                            val purchases = generateSequence(plan) { it.previous }.mapNotNull { it.purchase }
                                .map { choice ->
                                    val offer = choice.offer
                                    BasketPurchase(products.getValue(offer.pack.productId), offer.pack, offer.match,
                                        offer.observation, offer.promotion, choice.stock, choice.count,
                                        choice.capacity, choice.checkout)
                                }.sortedBy { it.pack.id.value }.toList()
                            lines += BasketLine(group.ids, purchases, group.amount, plan.amount, group.unit, plan.checkout,
                                EuroRatio(plan.raw * group.amount, plan.amount))
                        }
                    }
                    if (missing.isNotEmpty() && missing.all { BasketMissingReason.SEARCH_LIMIT in it.reasons }) {
                        rankingPending = true
                    }
                    SingleStoreBasket(retailer, store, lines, missing.sortedBy { it.itemId },
                        lines.takeIf { it.isNotEmpty() }?.sumOf { it.checkoutEur },
                        lines.takeIf { it.isNotEmpty() }?.let(::proportionalTotal))
                }.minWith(basketOrder)
            }
        }.sortedWith(basketOrder)
        return SingleStoreBasketResult(alternatives,
            alternatives.firstOrNull()?.takeIf { it.isComplete && !rankingPending }, rankingPending)
    }

    private data class DemandKey(val ingredient: CommercialId, val unit: ContentUnit, val basis: MassBasis?)
    private class DemandGroup(val sample: PriceDemand, val unit: ContentUnit) {
        val ids = mutableListOf<Long>()
        var amount: BigDecimal = zero
    }
    private data class StoreComparisons(
        val options: Map<CommercialId?, List<ComparedPrice>>,
        val excluded: Map<CommercialId?, List<ExcludedPrice>>
    )
    private val ObservationRecord.storeId get() = (scope as? ObservationScope.AtStore)?.storeId

    private val offerIdentity = compareBy<ComparedPrice> { it.observation.record.origin.sourceId.value }
        .thenBy { it.observation.record.origin.recordKey }

    private val basketOrder = compareByDescending<SingleStoreBasket> { it.isComplete }
        .thenByDescending { it.coveredCount }
        .thenBy { if (it.isComplete) it.checkoutEur else null }
        .thenBy { if (it.isComplete) it.unconfirmedStockCount else null }
        .thenBy { if (it.isComplete) it.proportionalEur else null }
        .thenBy { it.retailer.id.value }.thenBy { it.store?.id?.value }

    private fun stockCaution(state: StockState) = when (state) {
        StockState.IN_STOCK -> 0
        StockState.UNKNOWN -> 1
        StockState.LIMITED -> 2
        StockState.OUT_OF_STOCK -> 3
    }

    private fun ComparisonError.basketReason() = when (this) {
        ComparisonError.NO_MATCH, ComparisonError.SUBSTITUTE -> BasketMissingReason.NO_EQUIVALENT
        ComparisonError.AMBIGUOUS_MATCH -> BasketMissingReason.AMBIGUOUS_EQUIVALENT
        ComparisonError.INVALID_QUANTITY -> BasketMissingReason.INVALID_QUANTITY
        ComparisonError.STALE_PRICE, ComparisonError.SNAPSHOT_NOT_CURRENT -> BasketMissingReason.STALE_PRICE
        ComparisonError.EXPIRED_PROMOTION, ComparisonError.UNVERIFIED_PROMOTION,
        ComparisonError.INELIGIBLE_PROMOTION -> BasketMissingReason.PROMOTION_UNAVAILABLE
        ComparisonError.INCOMPATIBLE_UNIT, ComparisonError.UNVERIFIED_BASIS -> BasketMissingReason.UNVERIFIED_CONTENT
        ComparisonError.INVALID_WEIGHING -> BasketMissingReason.WEIGHT_REQUIRED
        else -> BasketMissingReason.NO_PRICE
    }

    private data class Sku(val capacity: BigDecimal, val stock: StockState, val schedules: List<ComparedPrice>) {
        private val discountedGroups = schedules.mapNotNull { it.promotion }
            .filter { it.take - it.pay > 1 }.map { it.take.toBigInteger() }.distinct()
        val priceCanDrop get() = discountedGroups.isNotEmpty()

        fun coveringCounts(minimum: BigInteger): List<BigInteger> = buildSet {
            add(minimum)
            // Within each promotional group the price rises with every extra unit.
            // Only the next full group can cost less than the smallest covering count.
            for (take in discountedGroups) {
                val remainder = minimum.mod(take)
                if (remainder != noPacks) add(minimum + take - remainder)
            }
        }.sorted()

        fun purchase(count: BigInteger): Choice = schedules.map { offer ->
            val raw = offer.observation.money.amount * payablePacks(count, offer.promotion).toBigDecimal()
            Choice(offer, stock, count, capacity, raw, raw.setScale(2, RoundingMode.HALF_UP))
        }.minWith(compareBy<Choice> { it.checkout }.thenBy { it.raw })
    }

    private data class Choice(
        val offer: ComparedPrice, val stock: StockState, val count: BigInteger, val capacity: BigDecimal,
        val raw: BigDecimal, val checkout: BigDecimal
    )

    private data class Plan(
        val amount: BigDecimal = zero,
        val checkout: BigDecimal = zero,
        val unconfirmed: BigInteger = noPacks,
        val substitutions: BigInteger = noPacks,
        val packs: BigInteger = noPacks,
        val raw: BigDecimal = zero,
        val previous: Plan? = null,
        val purchase: Choice? = null
    ) {
        fun add(amount: BigDecimal, choice: Choice) = Plan(
            this.amount + amount, checkout + choice.checkout,
            unconfirmed + if (choice.stock == StockState.IN_STOCK) noPacks else choice.count,
            substitutions + if (choice.offer.match.kind == MatchKind.EXACT) noPacks else choice.count,
            packs + choice.count, raw + choice.raw, this, choice
        )
    }

    private val planOrder = compareBy<Plan> { it.checkout }.thenBy { it.unconfirmed }
        .thenBy { it.substitutions }.thenBy { it.amount }.thenBy { it.packs }.thenBy { it.raw }

    private class SearchLimit : RuntimeException()

    /** A stage picks one price schedule and a count for a single SKU. */
    private fun solve(required: BigDecimal, skus: List<Sku>, checkCancellation: () -> Unit): Plan {
        val ordered = skus.sortedWith(compareByDescending<Sku> { it.capacity }
            .thenBy { it.schedules.first().pack.id.value })
        fun countFor(amount: BigDecimal, sku: Sku) =
            amount.divide(sku.capacity, 0, RoundingMode.CEILING).toBigIntegerExact()

        var states = sortedMapOf(zero to Plan())
        var transitions = 0
        fun buy(state: Plan, sku: Sku, count: BigInteger): Plan {
            if (++transitions > MAX_TRANSITIONS) throw SearchLimit()
            if (transitions % 256 == 0) checkCancellation()
            return state.add(sku.capacity * count.toBigDecimal(), sku.purchase(count))
        }
        var best = ordered.minOfWith(planOrder) { sku ->
            sku.coveringCounts(countFor(required, sku)).minOfWith(planOrder) { buy(Plan(), sku, it) }
        }
        for ((index, sku) in ordered.withIndex()) {
            val next = sortedMapOf<BigDecimal, Plan>()
            for (state in states.values) {
                if (state.checkout > best.checkout) continue
                val limit = countFor(required - state.amount, sku)
                for (count in sku.coveringCounts(limit)) {
                    best = minOf(best, buy(state, sku, count), planOrder)
                }
                if (index == ordered.lastIndex) continue
                next.merge(state.amount, state) { a, b -> minOf(a, b, planOrder) }
                var count = BigInteger.ONE
                while (count < limit) {
                    val candidate = buy(state, sku, count)
                    if (candidate.checkout <= best.checkout) {
                        next.merge(candidate.amount, candidate) { a, b -> minOf(a, b, planOrder) }
                    } else if (!sku.priceCanDrop) {
                        break
                    }
                    count += BigInteger.ONE
                }
            }
            states = next
        }
        return best
    }

    private fun proportionalTotal(lines: List<BasketLine>): BigDecimal {
        var numerator = BigInteger.ZERO
        var denominator = BigInteger.ONE
        for (line in lines) {
            val ratio = line.proportional
            val scale = maxOf(ratio.numerator.scale(), ratio.denominator.scale())
            val n = ratio.numerator.movePointRight(scale).toBigIntegerExact()
            val d = ratio.denominator.movePointRight(scale).toBigIntegerExact()
            val shared = denominator.gcd(d)
            numerator = numerator * (d / shared) + n * (denominator / shared)
            denominator *= d / shared
            val divisor = numerator.gcd(denominator)
            numerator /= divisor
            denominator /= divisor
        }
        return numerator.toBigDecimal().divide(denominator.toBigDecimal(), 2, RoundingMode.HALF_UP)
    }
}
