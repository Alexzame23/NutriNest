package com.zame.nutrinest.ui.menu.shopping

import androidx.lifecycle.SavedStateHandle
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.prices.RetailBatchUnavailable
import com.zame.nutrinest.data.prices.ShoppingLinePrice
import com.zame.nutrinest.data.prices.ShoppingPriceDemand
import com.zame.nutrinest.data.prices.ShoppingPriceRepository
import com.zame.nutrinest.data.prices.ShoppingPrices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration

internal data class ShoppingWeek(val start: Long, val items: List<ShoppingItemEntity>)

internal data class ShoppingComparisonUiState(
    val postalCode: String = "",
    val excludePurchased: Boolean = false,
    val status: ShoppingComparisonStatus = ShoppingComparisonStatus.Idle,
    val eligibleCount: Int = 0,
    val coveredCount: Int = 0
)

internal sealed interface ShoppingComparisonStatus {
    data object Idle : ShoppingComparisonStatus
    data object Loading : ShoppingComparisonStatus
    data object Error : ShoppingComparisonStatus
    data class Unavailable(val reason: RetailBatchUnavailable) : ShoppingComparisonStatus
    data class Published(val prices: ShoppingPrices.Published) : ShoppingComparisonStatus
}

internal class ShoppingComparison(
    private val scope: CoroutineScope,
    private val savedState: SavedStateHandle,
    private val week: StateFlow<ShoppingWeek>,
    private val repository: ShoppingPriceRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    private val postalCode = MutableStateFlow(savedState.get<String>("shoppingPricePostal").orEmpty())
    private val excludePurchased = MutableStateFlow(savedState.get<Boolean>("shoppingPriceExclude") ?: false)
    private val quote = MutableStateFlow<Quote?>(null)
    private var request: Job? = null

    private data class Quote(
        val basis: PriceBasis,
        val status: ShoppingComparisonStatus
    )
    private data class PriceBasis(val weekStart: Long, val items: List<ShoppingPriceDemand>)

    val state = combine(week, postalCode, excludePurchased, quote) { current, postal, exclude, result ->
        val eligible = current.items.filter { !exclude || !it.purchased }
        val status = result?.takeIf { it.basis == current.priceBasis() }?.status
            ?: ShoppingComparisonStatus.Idle
        val visible = if (status is ShoppingComparisonStatus.Published) {
            val ids = eligible.map { it.id }.toSet()
            ShoppingComparisonStatus.Published(status.prices.copy(rows = status.prices.rows.filterKeys { it in ids }))
        } else status
        val covered = (visible as? ShoppingComparisonStatus.Published)?.prices?.rows?.values
            ?.count { it is ShoppingLinePrice.Options } ?: 0
        ShoppingComparisonUiState(postal, exclude, visible, eligible.size, covered)
    }.stateIn(scope, SharingStarted.Eagerly, ShoppingComparisonUiState(postalCode.value, excludePurchased.value))

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
                        published = published.copy(rows = published.rows.mapValues { (_, row) -> row.validAt(now) })
                        quote.value = Quote(basis, ShoppingComparisonStatus.Published(published))
                        val nextExpiry = published.rows.values.filterIsInstance<ShoppingLinePrice.Options>()
                            .flatMap { it.values }.minOfOrNull { it.expiresAt } ?: published.expiresAt
                        delay(Duration.between(clock.instant(), nextExpiry).toMillis().coerceAtLeast(1))
                    }
                    quote.value = Quote(basis, ShoppingComparisonStatus.Unavailable(RetailBatchUnavailable.Stale))
                }
            }
        }
    }

    private fun ShoppingWeek.priceBasis() = PriceBasis(
        start, items.map { ShoppingPriceDemand(it.id, it.ingredientId, it.grams) }.sortedBy { it.id }
    )

    private fun ShoppingLinePrice.validAt(now: java.time.Instant): ShoppingLinePrice {
        if (this !is ShoppingLinePrice.Options) return this
        val valid = values.filter { now < it.expiresAt }
        if (valid.isEmpty()) return ShoppingLinePrice.Stale
        return ShoppingLinePrice.Options(valid)
    }
}
