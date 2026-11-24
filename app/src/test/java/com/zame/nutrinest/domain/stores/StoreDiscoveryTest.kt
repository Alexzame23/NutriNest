package com.zame.nutrinest.domain.stores

import com.zame.nutrinest.data.stores.PilotStoreDirectory
import com.zame.nutrinest.domain.prices.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class StoreDiscoveryTest {
    private val directory = PilotStoreDirectory.repository
    private val lavapies = directory.findByArea("28012").first().entry

    @Test fun postcodeAndMunicipalityFindPilotStoresWithoutCoordinatesOrNetwork() {
        val postalResults = directory.findByArea(" 28012 ")
        assertEquals(2, postalResults.size)
        assertEquals(postalResults, directory.findByArea(" MáDRID "))
        assertTrue(postalResults.all { it.distanceKm == null && it.entry.area.postalCode == "28012" })
        assertTrue(directory.findByArea("08001").isEmpty())
        assertTrue(directory.findByArea("Barcelona").isEmpty())
    }

    @Test fun proximitySortsTwoStoresAndRespectsRadius() {
        val origin = GeoPoint.approximate(40.40828, -3.70102)
        assertEquals(GeoPoint(40.4, -3.7), origin)
        val results = directory.findNearby(origin, 5.0)
        assertEquals(lavapies, results.first().entry)
        assertEquals(2, results.size)
        assertTrue(results[0].distanceKm!! < results[1].distanceKm!!)
        assertEquals(1, directory.findNearby(origin, 1.1).size)
        assertTrue(directory.findNearby(GeoPoint(41.4, 2.17), 5.0).isEmpty())
    }

    @Test fun onlyExactPhysicalIdentityIsLocalAndAreaScopesStayDeclared() {
        val now = Instant.parse("2026-11-18T12:00:00Z")
        val sourceId = CommercialId("prices")
        fun origin(key: String) = ProviderRecord(sourceId, key)
        val retailer = Retailer(CommercialId("retailer"), origin("retailer"), "Retailer")
        val local = lavapies.store.copy(id = CommercialId("provider-lavapies"), origin = origin("local"), retailerId = retailer.id)
        val neighboring = local.copy(id = CommercialId("provider-other"), origin = origin("other"), osmRef = "way/622204638")
        val legacy = local.copy(id = CommercialId("legacy"), origin = origin("legacy"), osmRef = null)
        val zone = PriceZone(CommercialId("public-regional"), origin("zone"), retailer.id, "Área declarada")
        val product = CommercialProduct(CommercialId("milk"), origin("product"), "Milk")
        val pack = ProductPackage(
            CommercialId("pack"), origin("pack"), product.id, null, WeightMode.FIXED, SaleUnit.PACKAGE,
            NetContent(BigDecimal(1000), ContentUnit.ML), 1
        )
        fun quote(key: String, scope: ObservationScope, date: String = "2026-11-17") = PriceObservation(
            ObservationRecord(origin(key), 1, retailer.id, pack.id, scope, LocalDate.parse(date), null, Confidence.HIGH),
            Money(BigDecimal.ONE, Currency.EUR), SaleUnit.PACKAGE, PriceKind.REGULAR, null, null
        )
        val storePrice = quote("store", ObservationScope.AtStore(local.id))
        val zonePrice = quote("zone-price", ObservationScope.InZone(zone.id))
        val onlinePrice = quote("online", ObservationScope.Online(zone.id))
        val onlineWithoutArea = quote("online-no-area", ObservationScope.Online(null))
        val snapshot = PriceSnapshot(
            1, lavapies.area.regionId, 1, now.minusSeconds(60), now.plusSeconds(60),
            listOf(PriceSource(sourceId, "Provider", SourceKind.OPEN_DATA, "license", "Attribution")),
            listOf(retailer), listOf(local, neighboring, legacy), listOf(zone), listOf(product), listOf(pack), emptyList(),
            listOf(storePrice, zonePrice, onlinePrice, quote("other-store", ObservationScope.AtStore(neighboring.id)),
                quote("legacy-store", ObservationScope.AtStore(legacy.id)), quote("unknown", ObservationScope.Unknown),
                onlineWithoutArea,
                quote("future", ObservationScope.AtStore(local.id), "2026-11-19")), emptyList()
        )

        val coverage = snapshot.coverageFor(lavapies, now)
        assertEquals(listOf(storePrice), coverage.atStore.single().observations)
        assertEquals(listOf(zonePrice, onlinePrice),
            coverage.declaredAreas.filter { it.zone == zone }.flatMap { it.observations })
        assertEquals(listOf(onlineWithoutArea), coverage.declaredAreas.single { it.zone == null }.observations)
        val expired = snapshot.copy(prices = listOf(quote("old", ObservationScope.AtStore(local.id), "2026-10-01")))
            .coverageFor(lavapies, now)
        assertTrue(expired.hasStaleObservations)
        assertTrue(expired.atStore.isEmpty())
        val observationDeadline = Instant.parse("2026-11-18T00:00:00Z")
        val endingSoon = snapshot.copy(generatedAt = observationDeadline.minusSeconds(60),
            prices = listOf(quote("soon", ObservationScope.AtStore(local.id), "2026-10-19")))
            .coverageFor(lavapies, observationDeadline.minusSeconds(1))
        assertEquals(observationDeadline, endingSoon.nextChange)
        assertTrue(snapshot.coverageFor(directory.findByArea("28012")[1].entry, now).atStore.isEmpty())
        assertNull(snapshot.coverageFor(lavapies, snapshot.expiresAt).publishedAt)
        assertNull(snapshot.copy(regionId = CommercialId("other-region")).coverageFor(lavapies, now).publishedAt)
        assertTrue((null as PriceSnapshot?).coverageFor(lavapies, now).atStore.isEmpty())
    }
}
