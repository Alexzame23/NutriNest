package com.zame.nutrinest.domain.prices

import java.math.BigDecimal
import java.math.BigInteger

enum class BasketMissingReason {
    NO_LOCAL_STORE, NO_EQUIVALENT, AMBIGUOUS_EQUIVALENT, INVALID_QUANTITY, NO_PRICE,
    AMBIGUOUS_PRICE, STALE_PRICE, PROMOTION_UNAVAILABLE, UNVERIFIED_CONTENT,
    WEIGHT_REQUIRED, OUT_OF_STOCK, SEARCH_LIMIT
}

data class BasketMissingItem(val itemId: Long, val reasons: Set<BasketMissingReason>)

data class BasketPurchase(
    val product: CommercialProduct,
    val pack: ProductPackage,
    val match: IngredientProductMatch,
    val price: PriceObservation,
    val promotion: VerifiedPromotion?,
    val stock: StockState,
    val count: BigInteger,
    val comparableContentPerPack: BigDecimal,
    val checkoutEur: BigDecimal
)

/** Compatible rows of the same canonical ingredient can share the purchased packs. */
data class BasketLine(
    val itemIds: List<Long>,
    val purchases: List<BasketPurchase>,
    val requiredAmount: BigDecimal,
    val purchasedAmount: BigDecimal,
    val unit: ContentUnit,
    val checkoutEur: BigDecimal,
    val proportional: EuroRatio
) {
    val hasUnconfirmedStock get() = purchases.any { it.stock != StockState.IN_STOCK }
}

data class SingleStoreBasket(
    val retailer: Retailer,
    val store: Store?,
    val lines: List<BasketLine>,
    val missing: List<BasketMissingItem>,
    /** Subtotals for covered items only. Both are absent if no items are covered. */
    val checkoutEur: BigDecimal?,
    val proportionalEur: BigDecimal?
) {
    val coveredCount get() = lines.sumOf { it.itemIds.size }
    val totalCount get() = coveredCount + missing.size
    val isComplete get() = totalCount > 0 && missing.isEmpty()
    val unconfirmedStockCount get() = lines.filter { it.hasUnconfirmedStock }.sumOf { it.itemIds.size }
}

/** One ranked alternative per selected chain, using that chain's best individual store. */
data class SingleStoreBasketResult(
    val alternatives: List<SingleStoreBasket>,
    val winner: SingleStoreBasket?,
    val rankingPending: Boolean
)
