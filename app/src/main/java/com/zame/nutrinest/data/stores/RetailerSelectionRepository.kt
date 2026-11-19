package com.zame.nutrinest.data.stores

import android.content.Context
import android.content.SharedPreferences
import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.stores.RetailerSelectionPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class RetailerSelectionRepository(private val storage: SharedPreferences? = null) {
    private val selection = MutableStateFlow(read())
    val preferences = selection.asStateFlow()

    constructor(context: Context) : this(
        context.getSharedPreferences("retailer_selection", Context.MODE_PRIVATE)
    )

    @Synchronized
    fun update(transform: (RetailerSelectionPreferences) -> RetailerSelectionPreferences) {
        val next = transform(selection.value)
        if (next == selection.value) return
        storage?.edit()?.apply {
            putStringSet("retailers", next.selectedRetailers?.map { it.value }?.toSet())
            putStringSet("stores", next.selectedStores.flatMap { (chain, stores) ->
                stores.map { "${chain.value}:${it.value}" }.ifEmpty { listOf("${chain.value}:") }
            }.toSet())
        }?.apply()
        selection.value = next
    }

    private fun read(): RetailerSelectionPreferences {
        val retailers = storage?.getStringSet("retailers", null)?.map(::CommercialId)?.toSet()
        val stores = storage?.getStringSet("stores", null).orEmpty().map { entry ->
            CommercialId(entry.substringBefore(':')) to entry.substringAfter(':')
        }.groupBy({ it.first }, { it.second }).mapValues { (_, ids) -> ids.filter(String::isNotEmpty).map(::CommercialId).toSet() }
        return RetailerSelectionPreferences(retailers, stores)
    }
}
