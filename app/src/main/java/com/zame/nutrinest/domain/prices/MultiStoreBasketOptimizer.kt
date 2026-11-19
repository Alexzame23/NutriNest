package com.zame.nutrinest.domain.prices

import java.math.BigDecimal

/** Fixed EUR cost per physical visit, with its user-confirmed or published evidence. */
data class VerifiedVisitCost(val eur: BigDecimal, val evidence: String) {
    init { require(eur.signum() >= 0 && eur.scale() in 0..2 && evidence.isNotBlank()) }
}

enum class TravelMode { DISABLED, VERIFIED, MISSING_COSTS }

data class BasketVisit(val store: Store, val purchases: List<BasketPurchase>, val fee: VerifiedVisitCost?) {
    val checkoutEur = purchases.sumOf { it.checkoutEur }
}

data class MultiStorePlan(
    val visits: List<BasketVisit>,
    val lines: List<BasketLine>,
    val missing: List<BasketMissingItem>,
    val travelMode: TravelMode
) {
    val checkoutEur = visits.takeIf { it.isNotEmpty() }?.sumOf { it.checkoutEur }
    val totalWithVisitsEur = checkoutEur?.takeIf { travelMode != TravelMode.DISABLED && visits.all { it.fee != null } }
        ?.plus(visits.sumOf { requireNotNull(it.fee).eur })
    val complete = lines.isNotEmpty() && missing.isEmpty()
}

data class MultiStoreBasketResult(
    val plan: MultiStorePlan,
    val singleStoreReference: MultiStorePlan?,
    val exact: Boolean,
    val evaluatedSubsets: Int
) {
    val grossSavingsEur = singleStoreReference?.checkoutEur?.let { it - requireNotNull(plan.checkoutEur) }
    val netSavingsEur = singleStoreReference?.totalWithVisitsEur?.let { reference ->
        plan.totalWithVisitsEur?.let { reference - it }
    }
}

object MultiStoreBasketOptimizer {
    private const val SUBSET_LIMIT = 256

    fun optimize(
        demands: Map<Long, PriceDemand?>, selectedRetailers: Set<CommercialId>,
        regionId: CommercialId, selectedLocalStoreIds: Set<CommercialId>, snapshot: PriceSnapshot,
        policy: ComparisonPolicy, maxVisits: Int, visitCosts: Map<CommercialId, VerifiedVisitCost>? = null,
        promotions: List<VerifiedPromotion> = emptyList(),
        authorizedSubstitutions: Set<IngredientProductMatch> = emptySet(),
        additionalMassContents: List<VerifiedMassContent> = emptyList(),
        checkCancellation: () -> Unit = {}
    ): MultiStoreBasketResult {
        require(maxVisits >= 1)
        val problem = SingleStoreBasketOptimizer.prepare(demands, selectedRetailers, regionId, selectedLocalStoreIds,
            snapshot, policy, promotions, authorizedSubstitutions, additionalMassContents, checkCancellation)
        val stores = problem.stores
        val byId = stores.associateBy { it.id }
        val travel = when {
            visitCosts == null -> TravelMode.DISABLED
            stores.all { it.id in visitCosts } -> TravelMode.VERIFIED
            else -> TravelMode.MISSING_COSTS
        }
        val empty = MultiStorePlan(emptyList(), emptyList(), demands.keys.sorted().map {
            BasketMissingItem(it, setOf(BasketMissingReason.NO_LOCAL_STORE))
        }, travel)
        if (stores.isEmpty() || demands.isEmpty()) return MultiStoreBasketResult(empty, null, true, 0)

        var evaluated = 0
        var exact = true
        fun evaluate(subset: List<Store>): MultiStorePlan {
            evaluated++
            val allocation = problem.allocate(subset.sortedBy { it.id.value }, true)
            if (allocation.limited) exact = false
            val visits = allocation.lines.flatMap { it.purchases }.groupBy {
                (it.price.record.scope as ObservationScope.AtStore).storeId
            }.entries.sortedBy { it.key.value }.map { (id, purchases) ->
                BasketVisit(byId.getValue(id), purchases, visitCosts?.get(id))
            }
            return MultiStorePlan(visits, allocation.lines, allocation.missing, travel)
        }

        val limit = minOf(maxVisits, stores.size)
        val singles = stores.map { evaluate(listOf(it)) }
        val bestSingle = singles.minWith(order)
        var best = bestSingle
        if (fitsEnumeration(stores.size, limit)) {
            fun extend(prefix: List<Store>, start: Int) {
                if (prefix.size == limit) return
                for (index in start until stores.size) {
                    val next = prefix + stores[index]
                    if (next.size > 1) best = minOf(best, evaluate(next), order)
                    extend(next, index + 1)
                }
            }
            extend(emptyList(), 0)
        } else if (limit > 1) {
            exact = false
            val chosen = mutableListOf(stores[singles.indexOf(bestSingle)])
            var trials = 0
            while (chosen.size < limit && trials < SUBSET_LIMIT) {
                var addition: Store? = null
                var candidate = best
                for (store in stores) {
                    if (store in chosen) continue
                    if (++trials > SUBSET_LIMIT) break
                    val plan = evaluate(chosen + store)
                    if (order.compare(plan, candidate) < 0) {
                        candidate = plan
                        addition = store
                    }
                }
                chosen += addition ?: break
                best = candidate
            }
        }
        return MultiStoreBasketResult(best, bestSingle.takeIf { it.complete && best.complete }, exact, evaluated)
    }

    private val order = compareBy<MultiStorePlan> { it.missing.size }
        .thenBy { if (it.travelMode == TravelMode.VERIFIED) it.totalWithVisitsEur else it.checkoutEur }
        .thenBy { it.visits.size }
        .thenBy { it.checkoutEur }
        .thenBy { it.visits.joinToString(",") { visit -> visit.store.id.value } }

    private fun fitsEnumeration(stores: Int, limit: Int): Boolean {
        var combinations = 1L
        var sum = 0L
        for (size in 1..limit) {
            combinations = combinations * (stores - size + 1) / size
            sum += combinations
            if (sum > SUBSET_LIMIT) return false
        }
        return true
    }
}
