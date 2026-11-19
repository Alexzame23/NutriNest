package com.zame.nutrinest.data.prices

import androidx.lifecycle.SavedStateHandle
import com.zame.nutrinest.data.local.entity.ShoppingItemEntity
import com.zame.nutrinest.data.stores.PilotStoreDirectory
import com.zame.nutrinest.data.stores.RetailerSelectionRepository
import com.zame.nutrinest.ui.menu.shopping.ShoppingComparison
import com.zame.nutrinest.ui.menu.shopping.ShoppingComparisonStatus
import com.zame.nutrinest.ui.menu.shopping.ShoppingWeek
import com.zame.nutrinest.ui.menu.shopping.basketRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import java.time.Clock
import java.time.ZoneOffset
import com.zame.nutrinest.domain.prices.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ShoppingPriceRepositoryTest {
    private val now = Instant.parse("2026-11-19T12:00:00Z")
    private val directory = PilotStoreDirectory.repository.findByArea("28012").first().entry
    private val store = directory.store
    private val sourceId = store.origin.sourceId
    private val first = ShoppingPriceDemand(1, 10, 300.0)
    private val second = ShoppingPriceDemand(2, 11, 300.0)
    private val third = ShoppingPriceDemand(3, 12, 300.0)

    @Test fun projectsOnePublicationForAllRowsAndUsesEdibleGramsWithWholePacks() = runTest {
        var loads = 0
        var keyReads = 0
        val repository = RegionalShoppingPriceRepository(
            load = { cp, _ -> assertEquals("28012", cp); loads++; batch() },
            keys = { ids -> keyReads++; assertEquals(setOf(10L, 11L, 12L), ids)
                ids.associateWith { CommercialId("bedca.$it") } }
        )
        val before = listOf(first, second, third)
        val result = repository.compare(before, "28012", now) as ShoppingPrices.Published
        assertEquals(1, loads)
        assertEquals(1, keyReads)
        val options = (result.rows[1L] as ShoppingLinePrice.Options).values
        assertEquals(listOf("family", "single", "weighed"), options.map { it.product.name })
        assertEquals("1.80", options[0].cost.proportional.decimal(2).toPlainString())
        assertEquals("3.00", options[0].cost.checkoutEur?.toPlainString())
        assertEquals("2", options[1].cost.packageCount.toString())
        assertEquals(null, options[2].cost.checkoutEur)
        assertEquals(ShoppingLinePrice.NoPrice, result.rows[2L])
        assertEquals(ShoppingLinePrice.NoEquivalent, result.rows[3L])
        val basket = requireNotNull(result.basketCatalog)
        assertEquals(setOf(store.id), basket.stores)
        assertEquals(listOf(store.retailerId), basket.retailers.map { it.id })
        val optimized = SingleStoreBasketOptimizer.optimize(basket.demands, setOf(store.retailerId),
            basket.snapshot.regionId, basket.stores, basket.snapshot, basket.policy)
        assertEquals("3.00", optimized.alternatives.single().checkoutEur.toString())
        assertEquals(1, optimized.alternatives.single().coveredCount)
        assertEquals(null, optimized.winner)
        assertEquals(listOf(300.0, 300.0, 300.0), before.map { it.grams })
    }

    @Test fun ignoresStaleObservationsAndSeparatesEmptyRegionalCoverage() = runTest {
        val keys: suspend (Set<Long>) -> Map<Long, CommercialId> = { ids ->
            ids.associateWith { CommercialId("bedca.$it") }
        }
        val stale = RegionalShoppingPriceRepository({ _, _ -> batch(stale = true) }, keys)
        val result = stale.compare(listOf(first), "28012", now) as ShoppingPrices.Published
        assertEquals(ShoppingLinePrice.Stale, result.rows[first.id])
        val absent = RegionalShoppingPriceRepository({ _, _ -> RetailBatchUnavailable.NoCoverage }, keys)
        assertEquals(ShoppingPrices.Unavailable(RetailBatchUnavailable.NoCoverage),
            absent.compare(listOf(first), "28012", now))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun selectingUnpricedStoreImmediatelyRemovesThePricedStoreFromRowsAndCoverage() = runTest {
        val directory = PilotStoreDirectory.repository
        val unpriced = directory.knownStores().single { it.id != store.id }
        val preferences = RetailerSelectionRepository()
        val week = MutableStateFlow(ShoppingWeek(10, listOf(ShoppingItemEntity(1, 12, 10, 300.0))))
        var loads = 0
        val repository = RegionalShoppingPriceRepository(
            load = { _, _ -> loads++; batch() },
            keys = { ids -> ids.associateWith { CommercialId("bedca.$it") } }
        )
        val model = ShoppingComparison(backgroundScope, SavedStateHandle(), week, repository,
            Clock.fixed(now, ZoneOffset.UTC), selections = preferences, directory = directory)
        model.changePostalCode("28012")
        model.refresh()
        runCurrent()
        assertEquals(1, model.state.value.coveredCount)
        assertEquals(setOf(store.id), model.state.value.pricedStores)
        assertEquals(setOf(store.id, unpriced.id), model.state.value.availableStores)
        model.restrictStores(store.retailerId)
        model.selectStore(store, false)
        runCurrent()
        val state = model.state.value
        val publication = (state.status as ShoppingComparisonStatus.Published).prices
        assertEquals(ShoppingLinePrice.NoPrice, publication.rows[first.id])
        assertEquals(0, state.coveredCount)
        assertEquals(emptySet<CommercialId>(), state.basketRequest()?.stores)
        assertEquals(setOf(store.id), state.pricedStores)
        model.resetPreferences()
        runCurrent()
        assertEquals(1, model.state.value.coveredCount)
        assertEquals(1, loads)
    }

    private fun batch(stale: Boolean = false): RetailBatchPublication {
        fun origin(key: String) = ProviderRecord(sourceId, key)
        val names = listOf("single", "family", "weighed", "bottle")
        val products = names.map { CommercialProduct(CommercialId(it), origin(it), it) }
        val packs = products.mapIndexed { index, product ->
            val weight = index == 2
            ProductPackage(CommercialId("pack-${product.id.value}"), origin(product.id.value), product.id,
                null, if (weight) WeightMode.VARIABLE else WeightMode.FIXED,
                if (weight) SaleUnit.KG else SaleUnit.PACKAGE,
                if (weight) null else NetContent(listOf("250", "550", "1000", "500")[index].toBigDecimal(),
                    if (index == 3) ContentUnit.ML else ContentUnit.G), if (weight) null else 1)
        }
        val matches = packs.mapIndexed { i, pack ->
            IngredientProductMatch(origin(pack.id.value), CommercialId(if (i == 3) "bedca.11" else "bedca.10"),
                1, pack.id, MatchKind.EXACT, Confidence.HIGH,
                if (i == 3) null else ReviewedEdibleMass(listOf("200", "500", "800")[i].toBigDecimal(), "review"))
        }
        val observed = if (stale) now.minusSeconds(31 * 86400) else now
        val prices = packs.mapIndexed { i, pack ->
            PriceObservation(ObservationRecord(origin(pack.id.value), 1, store.retailerId, pack.id,
                ObservationScope.AtStore(store.id), LocalDate.parse("2026-11-19"), observed, Confidence.HIGH),
                Money(listOf("2", "3", "4", "1")[i].toBigDecimal(), Currency.EUR),
                pack.saleUnit, PriceKind.REGULAR, null, null)
        }
        val snapshot = PriceSnapshot(1, directory.area.regionId, 1, now.minusSeconds(3600), now.plusSeconds(3600),
            listOf(PriceSource(sourceId, "Source", SourceKind.OPEN_DATA, "License", "Attribution")),
            listOf(Retailer(store.retailerId, origin("retailer"), "Retailer")), listOf(store), emptyList(),
            products, packs, matches, prices, emptyList())
        return RetailBatchPublication(snapshot, mapOf(store.id to directory), simulated = true)
    }
}
