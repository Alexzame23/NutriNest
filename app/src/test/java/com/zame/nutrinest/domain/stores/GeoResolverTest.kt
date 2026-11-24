package com.zame.nutrinest.domain.stores

import com.zame.nutrinest.domain.prices.*
import java.time.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GeoResolverTest {
    private val now = Instant.parse("2026-11-24T10:00:00Z")
    private val open = CommercialId("open-prices")
    private val cp = listOf("01001", "15001", "41001")
    private fun shop(id: String, postal: String, latitude: Double?): DirectoryStore {
        val store = Store(CommercialId(id), ProviderRecord(open, id), CommercialId("fixture"), id, postal)
        return DirectoryStore(store, DiscoveryArea(CommercialId("published"), "Localidad", postal,
            LocalDate.parse("2026-11-24"), "Provincia"), "", latitude?.let { GeoPoint(it, 0.0) }, null)
    }

    @Test fun resolvesOnlyPublishedPostalCodesWithNoUserLocationAndCachesAreaChanges() = runTest {
        var requests = 0
        val entries = listOf(shop("one", cp[0], null), shop("two", cp[1], null))
        val resolver = GeoResolver({
            requests++
            GeoDirectory(entries, now.plusSeconds(60), open)
        }, Clock.fixed(now, ZoneOffset.UTC))
        for ((i, postal) in cp.withIndex()) {
            resolver.refresh()
            val rows = resolver.findByArea(postal)
            assertEquals(if (i < 2) GeoCoverage.COBERTURA else GeoCoverage.SIN_COBERTURA,
                resolver.coverage(rows)[open.value])
            assertEquals(GeoCoverage.FUENTE_NO_LOCALIZABLE, resolver.coverage(rows)["Superfudis"])
            assertTrue(rows.all { it.entry.area.postalCode == postal && it.distanceKm == null })
        }
        assertEquals(1, requests)
        assertEquals(2, resolver.findByArea(" província ").size)
        assertEquals(2, resolver.findByArea("Localidad").size)
        assertEquals(cp[0], resolver.findByArea("alava").single().entry.area.postalCode)
        assertEquals(cp[1], resolver.findByArea("A Coruña").single().entry.area.postalCode)
        assertTrue(resolver.findByArea("Sevilla").isEmpty())
    }

    @Test fun optionalCoarseFixFiltersPhysicalStoresAtFiveTenAndTwentyFiveKm() = runTest {
        val entries = listOf(0.02, 0.07, 0.18, null).mapIndexed { i, lat -> shop("shop$i", cp[0], lat) }
        val resolver = GeoResolver({ GeoDirectory(entries, now.plusSeconds(60), open) }, Clock.fixed(now, ZoneOffset.UTC))
        resolver.refresh()
        assertEquals(4, resolver.findByArea(cp[0]).size)
        val point = GeoPoint.approximate(0.001, 0.002)
        listOf(5, 10, 25).forEachIndexed { i, radius ->
            val nearby = resolver.findNearby(point, radius.toDouble())
            assertEquals(i + 1, nearby.size)
            assertTrue(nearby.all { it.distanceKm!! <= radius })
        }
    }

    @Test fun expiredDirectoryDropsCoverageAndRefreshes() = runTest {
        var instant = now
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = instant
        }
        var calls = 0
        val resolver = GeoResolver({
            if (++calls == 1) GeoDirectory(listOf(shop("one", cp[0], null)), now.plusSeconds(5), open) else null
        }, clock)
        resolver.refresh()
        assertEquals(1, resolver.findByArea(cp[0]).size)
        instant = now.plusSeconds(5)
        assertTrue(resolver.knownStores().isEmpty())
        resolver.refresh()
        resolver.refresh()
        assertEquals(2, calls)
        assertTrue(resolver.findByArea(cp[0]).isEmpty())
    }

    @Test fun validatesSpanishInputWithoutPromisingCoverage() {
        (cp + listOf("51001", "52999")).forEach { assertTrue(validSpanishPostalCode(it)) }
        listOf("00001", "01000", "53001", "99999", "1001", "01A01").forEach { assertFalse(validSpanishPostalCode(it)) }
    }
}
