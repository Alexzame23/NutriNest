package com.zame.nutrinest.ui.ingredients.retail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.zame.nutrinest.data.prices.*
import com.zame.nutrinest.data.stores.PilotStoreDirectory
import com.zame.nutrinest.domain.stores.RetailerSelectionPreferences
import com.zame.nutrinest.domain.prices.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class RetailPriceViewModelTest {
    private val now = Instant.parse("2026-11-19T12:00:00Z")
    private val region = CommercialId("es-madrid-28012")
    private val key = CommercialId("bedca.904")
    private val sourceId = CommercialId("example-source")
    private val retailerId = CommercialId("example-retailer")
    private val storeId = CommercialId("example-store")

    @Test fun sortsExactNormalizedRatesWithinEachUnitAndOnlyShowsRequestedIngredientAndStore() {
        scenario(RetailPriceProvider { requested ->
            assertEquals(region, requested)
            RetailPublication(snapshot(), false)
        }) { model ->
            val result = (model.uiState.value as RetailUiState.Content).result
            assertFalse(result.simulated)
            assertEquals(listOf("large", "small", "liquid"), result.rows.map { it.product.name })
            assertEquals(listOf("4.0000", "6.0000", "1.0000"),
                result.rows.map { it.normalized.decimal(4).toPlainString() })
            assertEquals("2", result.rows.first().price.money.amount.toPlainString())
            assertEquals("Carrefour Market Lavapiés", result.rows.first().store.store.name)
        }
    }

    @Test fun staleObservationsAndExpiredSnapshotAreHidden() {
        scenario(RetailPriceProvider { RetailPublication(snapshot(observed = now.minusSeconds(31 * 86400)), false) }) {
            assertEquals(RetailUiState.Stale, it.uiState.value)
        }
        scenario(RetailPriceProvider { RetailPublication(snapshot().copy(expiresAt = now), false) }) {
            assertEquals(RetailUiState.Stale, it.uiState.value)
        }
    }

    @Test fun visibleOffersExpireAtThePublicationDeadlineWithoutAnotherRequest() {
        var requests = 0
        scenario(RetailPriceProvider {
            requests++
            RetailPublication(snapshot().copy(expiresAt = now.plusSeconds(2)), false)
        }) { model ->
            assertTrue(model.uiState.value is RetailUiState.Content)
            advanceTimeBy(1_999)
            runCurrent()
            assertTrue(model.uiState.value is RetailUiState.Content)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(RetailUiState.Stale, model.uiState.value)
            assertEquals(1, requests)
        }
    }

    @Test fun absentMatchesAndCoverageHaveSeparateStates() {
        scenario(RetailPriceProvider { RetailPublication(snapshot().copy(matches = emptyList()), false) }) {
            assertEquals(RetailUiState.NoMatches, it.uiState.value)
        }
        scenario(RetailPriceProvider { null }) {
            assertEquals(RetailUiState.NoCoverage, it.uiState.value)
        }
        scenario(RetailPriceProvider { error("Unexpected request") }, postalCode = "08001") {
            assertEquals(RetailUiState.NoCoverage, it.uiState.value)
        }
    }

    @Test fun httpErrorAndDisconnectedNetworkHaveSeparateStates() {
        scenario(RetailPriceProvider { throw RetailHttpError(503) }) {
            assertEquals(RetailUiState.Error(503), it.uiState.value)
        }
        scenario(RetailPriceProvider { error("Unexpected request") }, online = false) {
            assertEquals(RetailUiState.Offline, it.uiState.value)
        }
        scenario(RetailPriceProvider { throw IllegalArgumentException("Malformed publisher response") }) {
            assertEquals(RetailUiState.Error(), it.uiState.value)
        }
    }

    @Test fun fixturesAreHiddenByDefaultAndMarkedWhenExplicitlyAllowedInTests() {
        val fixture = RetailPriceProvider { RetailPublication(snapshot(), true) }
        scenario(fixture) { assertEquals(RetailUiState.NoCoverage, it.uiState.value) }
        scenario(fixture, allowSimulated = true) {
            assertTrue((it.uiState.value as RetailUiState.Content).result.simulated)
        }
    }

    @Test fun ingredientOffersUseDirectoryIdentitiesForPublisherAliases() = runTest {
        val directory = PilotStoreDirectory.repository
        val branch = directory.findByArea("28012").first().entry.store
        var preferences = RetailerSelectionPreferences(setOf(branch.retailerId),
            mapOf(branch.retailerId to setOf(branch.id)))
        val repository = RegionalRetailPriceRepository(
            ingredientKey = { key },
            stores = directory,
            provider = RetailPriceProvider { RetailPublication(snapshot(), false) },
            selection = { preferences }
        )
        val offers = repository.compare(42, "28012", now) as RetailResult.Offers
        assertEquals(setOf(branch.id), offers.rows.map { it.store.store.id }.toSet())
        preferences = preferences.copy(selectedRetailers = emptySet())
        assertEquals(RetailResult.NoCoverage, repository.compare(42, "28012", now))
        preferences = RetailerSelectionPreferences(setOf(branch.retailerId),
            mapOf(branch.retailerId to setOf(CommercialId("missing-store"))))
        assertEquals(RetailResult.NoCoverage, repository.compare(42, "28012", now))
    }

    private fun scenario(
        provider: RetailPriceProvider,
        online: Boolean = true,
        postalCode: String = "28012",
        allowSimulated: Boolean = false,
        verify: TestScope.(RetailPriceViewModel) -> Unit
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = RegionalRetailPriceRepository(
                ingredientKey = { id -> assertEquals(42L, id); key },
                stores = PilotStoreDirectory.repository,
                provider = provider,
                allowSimulated = allowSimulated,
                hasNetwork = { online }
            )
            val model = RetailPriceViewModel(
                SavedStateHandle(mapOf("ingredientId" to 42L)), repository, Clock.fixed(now, ZoneOffset.UTC)
            )
            try {
                model.search(postalCode)
                assertEquals(RetailUiState.Loading, model.uiState.value)
                runCurrent()
                verify(model)
            } finally {
                model.viewModelScope.cancel()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun snapshot(observed: Instant = now): PriceSnapshot {
        val otherStore = CommercialId("unverified-store")
        val names = listOf("small", "large", "liquid", "other-ingredient", "other-store")
        fun origin(key: String) = ProviderRecord(sourceId, key)
        val products = names.map { CommercialProduct(CommercialId(it), origin(it), it) }
        val packs = products.mapIndexed { index, product ->
            ProductPackage(CommercialId("pack-${product.id.value}"), origin(product.id.value), product.id,
                null, WeightMode.FIXED, SaleUnit.PACKAGE,
                NetContent(listOf("100", "500", "1000", "1000", "1000")[index].toBigDecimal(),
                    if (index == 2) ContentUnit.ML else ContentUnit.G), 1)
        }
        val retail = Retailer(retailerId, origin("retailer"), "Example retailer")
        val matches = packs.mapIndexed { index, pack ->
            IngredientProductMatch(origin(pack.id.value), if (index == 3) CommercialId("bedca.1009") else key,
                1, pack.id, MatchKind.EXACT, Confidence.HIGH)
        }
        val quotes = packs.mapIndexed { index, pack ->
            PriceObservation(
                ObservationRecord(origin(pack.id.value), 1, retailerId, pack.id,
                    ObservationScope.AtStore(if (index == 4) otherStore else storeId),
                    LocalDate.ofInstant(observed, ZoneOffset.UTC), observed, Confidence.HIGH),
                Money(listOf("0.6", "2", "1", "0.1", "0.1")[index].toBigDecimal(), Currency.EUR),
                SaleUnit.PACKAGE, PriceKind.REGULAR, null, null
            )
        }
        return PriceSnapshot(1, region, 1, now.minusSeconds(3600), now.plusSeconds(3600),
            listOf(PriceSource(sourceId, "Example publisher", SourceKind.OPEN_DATA, "Example license", "Example credit")),
            listOf(retail), listOf(
                Store(storeId, origin("store"), retailerId, "Published store", "28012", "node/622204638"),
                Store(otherStore, origin("other-store"), retailerId, "Other store", "28012")
            ), emptyList(), products, packs, matches, quotes, emptyList())
    }
}
