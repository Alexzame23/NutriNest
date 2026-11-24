package com.zame.nutrinest.data.prices.openprices

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class OpenPricesLiveTest {
    @Test
    fun mapsAPublicObservationOverHttps() {
        assumeTrue(System.getenv("NUTRINEST_OPEN_PRICES_LIVE") == "1")
        val locationId = System.getenv("NUTRINEST_OPEN_PRICES_LOCATION_ID")
        val latitude = System.getenv("NUTRINEST_OPEN_PRICES_LAT")
        val longitude = System.getenv("NUTRINEST_OPEN_PRICES_LON")
        require((latitude == null) == (longitude == null))
        require(locationId == null || latitude == null)
        val area = when {
            locationId != null -> OpenPricesArea.Location(locationId.toLong())
            latitude != null -> OpenPricesArea.Nearby(
                latitude.toDouble(), requireNotNull(longitude).toDouble(),
                System.getenv("NUTRINEST_OPEN_PRICES_RADIUS_KM")?.toDouble() ?: 5.0
            )
            else -> OpenPricesArea.Nearby(40.4168, -3.7038, 5.0) // Puerta del Sol, Madrid
        }

        val source: OpenPriceObservationSource = OpenPricesSource()
        val sample = runBlocking { source.sample(area) }
        sample.calls.forEach { call ->
            println("GET ${OpenPricesSource.BASE_URL}${call.path} status=${call.status} items=${call.received} total=${call.total}")
        }
        val dates = sample.observations.mapNotNull { it.date }
        val observedLocations = sample.observations.mapNotNull { it.location }.distinctBy { it.id }
        val cities = observedLocations.mapNotNull { location ->
            location.city?.let { city -> "$city (${location.countryCode ?: "?"})" }
        }.distinct()
        println("locations=${sample.locations.size} observedLocations=${observedLocations.size} cities=$cities")
        println("observations=${sample.observations.size} projected=${sample.observations.count { it.observation != null }} " +
            "firstDate=${dates.minOrNull()} lastDate=${dates.maxOrNull()}")

        val evidence = sample.observations.firstOrNull {
            it.observation != null && it.gtin != null && it.proofId != null
        }
        assertNotNull("Expected a live domain observation with GTIN, store and proof reference", evidence)
        requireNotNull(evidence)
        println("evidence=${evidence.origin.recordKey} date=${evidence.date} " +
            "code=${evidence.productCode} amount=${evidence.amount} ${evidence.currency} " +
            "locationId=${evidence.locationId} proofId=${evidence.proofId} proofType=${evidence.proofType}")
    }
}
