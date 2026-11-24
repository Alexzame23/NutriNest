package com.zame.nutrinest.ui.menu.shopping

import com.zame.nutrinest.domain.stores.validSpanishPostalCode
import androidx.lifecycle.SavedStateHandle
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.prices.RetailBatchUnavailable
import com.zame.nutrinest.data.prices.ShoppingLinePrice
import com.zame.nutrinest.data.prices.PriceDiagnostics
import com.zame.nutrinest.data.prices.ShoppingBasketCatalog
import com.zame.nutrinest.data.prices.ShoppingPriceDemand
import com.zame.nutrinest.data.prices.ShoppingPriceRepository
import com.zame.nutrinest.data.prices.ShoppingPrices
import com.zame.nutrinest.data.prices.isRetailNetworkFailure
import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.Store
import com.zame.nutrinest.domain.stores.RetailerSelectionPreferences
import com.zame.nutrinest.domain.stores.StoreRepository
import com.zame.nutrinest.data.stores.RetailerSelectionRepository
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
    val preferences: RetailerSelectionPreferences = RetailerSelectionPreferences(),
    val chainNames: Map<CommercialId, String> = emptyMap(),
    val knownStores: List<Store> = emptyList(),
    val availableStores: Set<CommercialId> = emptySet(),
    val pricedStores: Set<CommercialId> = emptySet()
)

internal data class ShoppingBasketRequest(
    val catalog: ShoppingBasketCatalog,
    val itemIds: Set<Long>,
    val retailers: Set<CommercialId>,
    val now: Instant,
    val stores: Set<CommercialId>
)

internal data class ShoppingBasketQuote(
    val request: ShoppingBasketRequest,
    val result: SingleStoreBasketResult
)

internal fun ShoppingComparisonUiState.basketRequest(): ShoppingBasketRequest? {
    val prices = (status as? ShoppingComparisonStatus.Published)?.prices ?: return null
    val catalog = prices.basketCatalog ?: return null
    if (prices.rows.isEmpty() || catalog.retailers.isEmpty()) return null
    val stores = catalog.selectedStoreIds(preferences)
    val retailers = catalog.snapshot.stores.filter { it.id in stores }.map { it.retailerId }.toSet()
    return ShoppingBasketRequest(catalog, prices.rows.keys, retailers, prices.pricedAt, stores)
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
    private val basketDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val selections: RetailerSelectionRepository = RetailerSelectionRepository(),
    private val directory: StoreRepository? = null
) {
    private val postalCode = MutableStateFlow(savedState.get<String>("shoppingPricePostal").orEmpty())
    private val excludePurchased = MutableStateFlow(savedState.get<Boolean>("shoppingPriceExclude") ?: false)
    private val knownStores = MutableStateFlow(directory?.knownStores().orEmpty())
    private val chainNames = MutableStateFlow<Map<CommercialId, String>>(emptyMap())
    private val quote = MutableStateFlow<Quote?>(null)
    private var request: Job? = null

    private data class Quote(
        val basis: PriceBasis,
        val status: ShoppingComparisonStatus
    )
    private data class PriceBasis(val weekStart: Long, val items: List<ShoppingPriceDemand>)

    private val comparison = combine(week, postalCode, excludePurchased, quote, selections.preferences) {
            current, postal, exclude, result, preferences ->
        val eligible = current.items.filter { !exclude || !it.purchased }
        val status = result?.takeIf { it.basis == current.priceBasis() }?.status
            ?: ShoppingComparisonStatus.Idle
        val ids = eligible.map { it.id }.toSet()
        val prices = (status as? ShoppingComparisonStatus.Published)?.prices
        val rows = prices?.rows.orEmpty().filterKeys { it in ids }
        val pricedStores = rows.values.filterIsInstance<ShoppingLinePrice.Options>()
            .flatMap { it.values }.map { it.store.store.id }.toSet()
        val visible = if (prices != null) {
            ShoppingComparisonStatus.Published(prices.copy(rows = rows.mapValues { (_, row) ->
                if (row is ShoppingLinePrice.Options) {
                    val options = row.values.filter { preferences.includes(it.store.store) }
                    if (options.isEmpty()) ShoppingLinePrice.NoPrice else ShoppingLinePrice.Options(options)
                } else row
            }))
        } else status
        val covered = (visible as? ShoppingComparisonStatus.Published)?.prices?.rows?.values
            ?.count { it is ShoppingLinePrice.Options } ?: 0
        PriceDiagnostics.log {
            fun optionCount(lines: Collection<ShoppingLinePrice>) =
                lines.filterIsInstance<ShoppingLinePrice.Options>().sumOf { it.values.size }
            val visibleRows = (visible as? ShoppingComparisonStatus.Published)?.prices?.rows.orEmpty()
            val reason = (visible as? ShoppingComparisonStatus.Unavailable)?.reason
            val afterRetailer = rows.values.filterIsInstance<ShoppingLinePrice.Options>().sumOf { line ->
                line.values.count { preferences.selectedRetailers?.contains(it.store.store.retailerId) != false }
            }
            "stage=shopping.ui state=${reason ?: visible.javaClass.simpleName} items=${current.items.size} eligible=${eligible.size} " +
                "optionsBefore=${optionCount(prices?.rows.orEmpty().values)} afterPurchased=${optionCount(rows.values)} " +
                "retailers=${preferences.selectedRetailers?.map { it.value } ?: "all"} restrictedChains=${preferences.selectedStores.size} " +
                "afterRetailer=$afterRetailer optionsAfterSelection=${optionCount(visibleRows.values)} covered=$covered"
        }
        ShoppingComparisonUiState(postal, exclude, visible, eligible.size, covered, preferences,
            availableStores = directory?.findByArea(postal.trim()).orEmpty()
                .map { it.entry.store.id }.toSet() + prices?.basketCatalog?.let { catalog ->
                    catalog.stores.map { catalog.directoryStores[it]?.id ?: it }
                }.orEmpty(),
            pricedStores = pricedStores)
    }

    val state = combine(comparison, knownStores, chainNames) { current, stores, names ->
        current.copy(knownStores = stores, chainNames = names)
    }.stateIn(scope, SharingStarted.Eagerly, ShoppingComparisonUiState(postalCode.value, excludePurchased.value,
        preferences = selections.preferences.value, knownStores = knownStores.value))

    val basket = state.map { it.basketRequest() }.distinctUntilChanged().mapLatest { request ->
        if (request == null || request.retailers.isEmpty()) return@mapLatest null
        withContext(basketDispatcher) {
            val catalog = request.catalog
            ShoppingBasketQuote(request, SingleStoreBasketOptimizer.optimize(
                catalog.demands.filterKeys { it in request.itemIds }, request.retailers, catalog.snapshot.regionId,
                request.stores, catalog.snapshot, catalog.policy.copy(now = request.now),
                checkCancellation = { ensureActive() }
            ))
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    fun changePostalCode(value: String) {
        if (value == postalCode.value) return
        reset()
        postalCode.value = value
        savedState["shoppingPricePostal"] = value
    }

    fun excludePurchased(value: Boolean) {
        excludePurchased.value = value
        savedState["shoppingPriceExclude"] = value
    }

    fun selectRetailer(id: CommercialId, selected: Boolean) {
        selections.update { preferences ->
            val current = preferences.selectedRetailers ?: (chainNames.value.keys +
                knownStores.value.map { it.retailerId } + preferences.selectedStores.keys)
            preferences.copy(selectedRetailers = if (selected) current + id else current - id)
        }
    }

    fun selectStore(store: Store, selected: Boolean) = selectStore(store.retailerId, store.id, selected)

    fun removeStore(retailer: CommercialId, store: CommercialId) = selectStore(retailer, store, false)

    private fun selectStore(retailer: CommercialId, store: CommercialId, selected: Boolean) {
        selections.update { preferences ->
            val stores = preferences.selectedStores[retailer].orEmpty()
            val next = if (selected) stores + store else stores - store
            preferences.copy(selectedStores = preferences.selectedStores + (retailer to next))
        }
    }

    fun restrictStores(retailer: CommercialId) {
        selections.update { preferences ->
            val stores = knownStores.value.filter { it.retailerId == retailer }.map { it.id }.toSet()
            preferences.copy(selectedStores = preferences.selectedStores + (retailer to stores))
        }
    }

    fun allowAllStores(retailer: CommercialId) {
        selections.update { it.copy(selectedStores = it.selectedStores - retailer) }
    }

    fun resetPreferences() {
        selections.update { RetailerSelectionPreferences() }
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
        if (!validSpanishPostalCode(postal) ||
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
                quote.value = Quote(basis, if (error.isRetailNetworkFailure()) {
                    ShoppingComparisonStatus.Unavailable(RetailBatchUnavailable.Offline)
                } else ShoppingComparisonStatus.Error)
                return@launch
            }
            ensureActive()
            when (result) {
                is ShoppingPrices.Unavailable ->
                    quote.value = Quote(basis, ShoppingComparisonStatus.Unavailable(result.reason))
                is ShoppingPrices.Published -> {
                    result.basketCatalog?.let { catalog ->
                        val names = catalog.retailers.associate { it.id to it.name }
                        val localStores = catalog.snapshot.stores.filter { it.id in catalog.stores }
                            .map { published ->
                                val store = catalog.directoryStores[published.id] ?: published
                                store to names.getValue(published.retailerId)
                            }
                        chainNames.value = chainNames.value + localStores.associate { (store, name) ->
                            store.retailerId to name
                        }
                        knownStores.value = (knownStores.value + localStores.map { it.first }).distinctBy { it.id }
                    }
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
