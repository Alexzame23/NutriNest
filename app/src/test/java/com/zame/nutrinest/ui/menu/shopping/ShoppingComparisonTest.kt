package com.zame.nutrinest.ui.menu.shopping

import androidx.lifecycle.SavedStateHandle
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.prices.*
import com.zame.nutrinest.domain.prices.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingComparisonTest {
    private val now = Instant.parse("2026-11-19T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val pending = ShoppingItemEntity(1, 12, 42, 675.0)
    private val bought = ShoppingItemEntity(2, 12, 54, 220.0, purchased = true)

    @Test fun refreshAndPurchaseFilterKeepOriginalShoppingRowsAndUseOneBatch() = runTest {
        val before = listOf(pending, bought)
        val week = MutableStateFlow(ShoppingWeek(10, before))
        var calls = 0
        val model = ShoppingComparison(backgroundScope, SavedStateHandle(), week, ShoppingPriceRepository { batch, cp, _ ->
            calls++
            assertEquals("28012", cp)
            assertEquals(before.map { it.id to it.grams }, batch.map { it.id to it.grams })
            ShoppingPrices.Published(batch.associate { it.id to ShoppingLinePrice.NoEquivalent },
                now, now.plusSeconds(3600), false)
        }, clock)
        model.changePostalCode("28012")
        model.refresh()
        model.refresh()
        runCurrent()
        assertEquals(1, calls)
        model.excludePurchased(true)
        runCurrent()
        val status = model.state.value.status as ShoppingComparisonStatus.Published
        assertEquals(setOf(pending.id), status.prices.rows.keys)
        assertEquals(1, model.state.value.eligibleCount)
        assertEquals(before, week.value.items)
        assertEquals(1, calls)
    }

    @Test fun weekChangeDropsOldResultAndRestartRestoresControlsAndAllowsOfflineShopping() = runTest {
        val week = MutableStateFlow(ShoppingWeek(10, listOf(pending)))
        val saved = SavedStateHandle()
        val running = ShoppingComparison(backgroundScope, saved, week, ShoppingPriceRepository { _, _, _ ->
            delay(1000)
            ShoppingPrices.Unavailable(RetailBatchUnavailable.Stale)
        }, clock)
        running.changePostalCode("28012")
        running.excludePurchased(true)
        running.refresh()
        runCurrent()
        assertEquals(ShoppingComparisonStatus.Loading, running.state.value.status)
        running.reset()
        val next = listOf(pending.copy(id = 7, menuId = 13), bought.copy(id = 8, menuId = 13))
        week.value = ShoppingWeek(17, next)
        runCurrent()
        assertEquals(ShoppingComparisonStatus.Idle, running.state.value.status)

        var calls = 0
        val restarted = ShoppingComparison(backgroundScope, saved, week, ShoppingPriceRepository { _, _, _ ->
            calls++
            ShoppingPrices.Unavailable(RetailBatchUnavailable.Offline)
        }, clock)
        runCurrent()
        assertEquals(0, calls)
        assertEquals("28012", restarted.state.value.postalCode)
        assertEquals(true, restarted.state.value.excludePurchased)
        restarted.refresh()
        runCurrent()
        assertEquals(ShoppingComparisonStatus.Unavailable(RetailBatchUnavailable.Offline), restarted.state.value.status)
        assertEquals(next, week.value.items)
    }

    @Test fun basketRecomputesForSelectedChainsAndUncheckedItemsFromTheSamePublication() = runTest {
        val week = MutableStateFlow(ShoppingWeek(10, listOf(pending, bought)))
        val source = PriceSource(CommercialId("source"), "Source", SourceKind.OPEN_DATA, "License", "Credit")
        val chains = listOf("a", "b").map { name ->
            Retailer(CommercialId(name), ProviderRecord(source.id, name), name)
        }
        val stores = chains.map {
            Store(CommercialId("store.${it.id.value}"), it.origin, it.id, it.name)
        }
        val snapshot = PriceSnapshot(1, CommercialId("region"), 1, now, now.plusSeconds(3600),
            listOf(source), chains, stores, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val catalog = ShoppingBasketCatalog(snapshot, stores.map { it.id }.toSet(),
            mapOf(pending.id to null, bought.id to null), ComparisonPolicy(now, ZoneId.of("Europe/Madrid")))
        var calls = 0
        val model = ShoppingComparison(backgroundScope, SavedStateHandle(), week, ShoppingPriceRepository { batch, _, _ ->
            calls++
            ShoppingPrices.Published(batch.associate { it.id to ShoppingLinePrice.NoEquivalent },
                now, snapshot.expiresAt, false, catalog)
        }, clock, StandardTestDispatcher(testScheduler))
        backgroundScope.launch { model.basket.collect {} }
        model.changePostalCode("28012")
        model.refresh()
        runCurrent()
        assertEquals(listOf(chains[0], chains[1]), model.basket.value?.result?.alternatives?.map { it.retailer })

        model.selectRetailer(chains[0].id, false)
        model.excludePurchased(true)
        runCurrent()
        val updated = model.basket.value?.result?.alternatives?.single()
        assertEquals(chains[1], updated?.retailer)
        assertEquals(listOf(pending.id), updated?.missing?.map { it.itemId })
        assertEquals(1, calls)
        assertEquals(listOf(pending, bought), week.value.items)
        model.selectRetailer(chains[1].id, false)
        runCurrent()
        assertEquals(emptySet<CommercialId>(), model.state.value.basketRequest()?.retailers)
        assertEquals(null, model.basket.value)
    }
}
