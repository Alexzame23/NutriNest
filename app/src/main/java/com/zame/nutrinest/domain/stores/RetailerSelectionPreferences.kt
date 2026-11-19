package com.zame.nutrinest.domain.stores

import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.Store

data class RetailerSelectionPreferences(
    /** Null uses the available chains automatically. An empty set explicitly selects none. */
    val selectedRetailers: Set<CommercialId>? = null,
    /** A missing entry allows any local store for that chain. */
    val selectedStores: Map<CommercialId, Set<CommercialId>> = emptyMap()
) {
    fun includes(retailer: CommercialId, store: CommercialId): Boolean =
        (selectedRetailers?.contains(retailer) != false) &&
            (selectedStores[retailer]?.contains(store) != false)

    fun includes(store: Store): Boolean = includes(store.retailerId, store.id)
}
