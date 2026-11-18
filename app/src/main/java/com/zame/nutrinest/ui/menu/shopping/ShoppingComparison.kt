package com.zame.nutrinest.ui.menu.shopping

import androidx.lifecycle.SavedStateHandle
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.prices.RetailBatchUnavailable
import com.zame.nutrinest.data.prices.ShoppingLinePrice
import com.zame.nutrinest.data.prices.ShoppingBasketCatalog
import com.zame.nutrinest.data.prices.ShoppingPriceDemand
import com.zame.nutrinest.data.prices.ShoppingPriceRepository
import com.zame.nutrinest.data.prices.ShoppingPrices
import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.SingleStoreBasketOptimizer
import com.zame.nutrinest.domain.prices.SingleStoreBasketResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant

internal data class ShoppingWeek(val start: Long, val items: List<ShoppingItemEntity>)

internal data class ShoppingComparisonUiState(
    val postalCode: String = "",
    val excludePurchased: Boolean = false,
    val status: ShoppingComparisonStatus = ShoppingComparisonStatus.Idle,
    val eligibleCount: Int = 0,
    val coveredCount: Int = 0,
    val selectedRetailers: Set<CommercialId>? = null
)

internal data class ShoppingBasketRequest(
    val catalog: ShoppingBasketCatalog,
    val itemIds: Set<Long>,
    val retailers: Set<CommercialId>,
    val now: Instant
)

internal data class ShoppingBasketQuote(
    val request: ShoppingBasketRequest,
    val result: SingleStoreBasketResult
)

internal fun ShoppingComparisonUiState.basketRequest(): ShoppingBasketRequest? {
    val prices = (status as? ShoppingComparisonStatus.Published)?.prices ?: return null
    val catalog = prices.basketCatalog ?: return null
    val retailers = catalog.retailers.map { it.id }.toSet()
    if (prices.rows.isEmpty() || retailers.isEmpty()) return null
    return ShoppingBasketRequest(catalog, prices.rows.keys,
        selectedRetailers?.intersect(retailers) ?: retailers, prices.pricedAt)
}

internal sealed interface ShoppingComparisonStatus {
    data object Idle : ShoppingComparisonStatus
    data object Loading : ShoppingComparisonStatus
    data object Error : ShoppingComparisonStatus
    data class Unavailable(val reason: RetailBatchUnavailable) : ShoppingComparisonStatus
    data class Published(val prices: ShoppingPrices.Published) : ShoppingComparisonStatus
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class ShoppingComparison(
    private val scope: CoroutineScope,
    private val savedState: SavedStateHandle,
    private val week: StateFlow<ShoppingWeek>,
    private val repository: ShoppingPriceRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val basketDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val postalCode = MutableStateFlow(savedState.get<String>("shoppingPricePostal").orEmpty())
    private val excludePurchased = MutableStateFlow(savedState.get<Boolean>("shoppingPriceExclude") ?: false)
    private val selectedRetailers = MutableStateFlow(savedState.get<ArrayList<String>>("shoppingPriceRetailers")
        ?.map(::CommercialId)?.toSet())
    private val quote = MutableStateFlow<Quote?>(null)
    private var request: Job? = null

    private data class Quote(
        val basis: PriceBasis,
        val status: ShoppingComparisonStatus
    )
    private data class PriceBasis(val weekStart: Long, val items: List<ShoppingPriceDemand>)

    val state = combine(week, postalCode, excludePurchased, quote, selectedRetailers) { current, postal, exclude, result, selected ->
        val eligible = current.items.filter { !exclude || !it.purchased }
        val status = result?.takeIf { it.basis == current.priceBasis() }?.status
            ?: ShoppingComparisonStatus.Idle
        val visible = if (status is ShoppingComparisonStatus.Published) {
            val ids = eligible.map { it.id }.toSet()
            ShoppingComparisonStatus.Published(status.prices.copy(rows = status.prices.rows.filterKeys { it in ids }))
        } else status
        val covered = (visible as? ShoppingComparisonStatus.Published)?.prices?.rows?.values
            ?.count { it is ShoppingLinePrice.Options } ?: 0
        ShoppingComparisonUiState(postal, exclude, visible, eligible.size, covered, selected)
    }.stateIn(scope, SharingStarted.Eagerly, ShoppingComparisonUiState(postalCode.value, excludePurchased.value,
        selectedRetailers = selectedRetailers.value))

    val basket = state.map { it.basketRequest() }.distinctUntilChanged().mapLatest { request ->
        if (request == null || request.retailers.isEmpty()) return@mapLatest null
        withContext(basketDispatcher) {
            val catalog = request.catalog
            ShoppingBasketQuote(request, SingleStoreBasketOptimizer.optimize(
                catalog.demands.filterKeys { it in request.itemIds }, request.retailers, catalog.snapshot.regionId,
                catalog.stores, catalog.snapshot, catalog.policy.copy(now = request.now),
                checkCancellation = { ensureActive() }
            ))
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun changePostalCode(value: String) {
        if (value == postalCode.value) return
        reset()
        if (postalCode.value.trim() != value.trim()) {
            selectedRetailers.value = null
            savedState.remove<ArrayList<String>>("shoppingPriceRetailers")
        }
        postalCode.value = value
        savedState["shoppingPricePostal"] = value
    }

    fun excludePurchased(value: Boolean) {
        excludePurchased.value = value
        savedState["shoppingPriceExclude"] = value
    }

    fun selectRetailer(id: CommercialId, selected: Boolean) {
        val request = state.value.basketRequest() ?: return
        val available = request.catalog.retailers.map { it.id }.toSet()
        if (id !in available) return
        val current = selectedRetailers.value?.intersect(available) ?: available
        val choices = if (selected) current + id else current - id
        selectedRetailers.value = choices.takeUnless { it == available }
        savedState["shoppingPriceRetailers"] = selectedRetailers.value?.mapTo(ArrayList()) { it.value }
    }

    fun reset() {
        request?.cancel()
        request = null
        quote.value = null
    }

    fun refresh() {
        val postal = postalCode.value.trim()
        val current = week.value
        val basis = current.priceBasis()
        if (!postal.matches(Regex("[0-9]{5}")) ||
            current.items.none { !excludePurchased.value || !it.purchased } ||
            quote.value?.let { it.status == ShoppingComparisonStatus.Loading && it.basis == basis } == true) return
        request?.cancel()
        quote.value = Quote(basis, ShoppingComparisonStatus.Loading)
        request = scope.launch {
            val result = try {
                repository.compare(basis.items, postal, clock.instant())
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                ensureActive()
                quote.value = Quote(basis, ShoppingComparisonStatus.Error)
                return@launch
            }
            ensureActive()
            when (result) {
                is ShoppingPrices.Unavailable ->
                    quote.value = Quote(basis, ShoppingComparisonStatus.Unavailable(result.reason))
                is ShoppingPrices.Published -> {
                    var published: ShoppingPrices.Published = result
                    while (clock.instant() < published.expiresAt) {
                        val now = clock.instant()
                        published = published.copy(rows = published.rows.mapValues { (_, row) -> row.validAt(now) },
                            pricedAt = now)
                        quote.value = Quote(basis, ShoppingComparisonStatus.Published(published))
                        val nextExpiry = published.rows.values.filterIsInstance<ShoppingLinePrice.Options>()
                            .flatMap { it.values }.minOfOrNull { it.expiresAt } ?: published.expiresAt
                        val nextBasketChange = published.basketCatalog?.nextChange(now) ?: published.expiresAt
                        delay(Duration.between(clock.instant(), minOf(nextExpiry, nextBasketChange)).toMillis().coerceAtLeast(1))
                    }
                    quote.value = Quote(basis, ShoppingComparisonStatus.Unavailable(RetailBatchUnavailable.Stale))
                }
            }
        }
    }

    private fun ShoppingWeek.priceBasis() = PriceBasis(
        start, items.map { ShoppingPriceDemand(it.id, it.ingredientId, it.grams) }.sortedBy { it.id }
    )

    private fun ShoppingLinePrice.validAt(now: Instant): ShoppingLinePrice {
        if (this !is ShoppingLinePrice.Options) return this
        val valid = values.filter { now < it.expiresAt }
        if (valid.isEmpty()) return ShoppingLinePrice.Stale
        return ShoppingLinePrice.Options(valid)
    }
}
