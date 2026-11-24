package com.zame.nutrinest.data.prices.openprices

import com.zame.nutrinest.domain.prices.Currency
import com.zame.nutrinest.domain.prices.ObservationScope
import com.zame.nutrinest.domain.prices.SaleUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.net.URI
import java.time.LocalDate
import javax.net.ssl.HttpsURLConnection

class OpenPricesSourceTest {
    @Test
    fun nearbyUsesBoundedPagesAndMapsTheCapturedRow() = runBlocking {
        val nearby = """{"items":[{"id":31,"type":"OSM"}],"total":1}"""
        val requests = mutableListOf<TestConnection>()
        val source: OpenPriceObservationSource = OpenPricesSource { uri ->
            TestConnection(uri, body = if (uri.path.endsWith("/nearby")) nearby else fixture()).also(requests::add)
        }
        val result = source.sample(OpenPricesArea.Nearby(40.4168, -3.7038, 5.0))

        assertEquals(
            listOf("/api/v1/locations/nearby", "/api/v1/prices"),
            result.calls.map { it.path }
        )
        assertEquals(listOf(200, 200), result.calls.map { it.status })
        assertEquals(listOf(1, 1), result.calls.map { it.received })
        assertEquals("lat=40.4168&lon=-3.7038&radius_km=5.0&size=10", requests[0].url.query)
        assertEquals("location_id__in=31&currency=EUR&order_by=-date,-id&size=10", requests[1].url.query)
        requests.forEach {
            assertEquals("prices.openfoodfacts.org", it.url.host)
            assertEquals(OpenPricesSource.USER_AGENT, it.userAgent)
            assertEquals("application/json", it.accept)
            assertFalse(it.instanceFollowRedirects)
            assertEquals(5_000, it.connectTimeout)
            assertEquals(5_000, it.readTimeout)
            assertTrue(it.disconnected)
        }

        val row = result.observations.single()
        val price = requireNotNull(row.observation)
        val location = requireNotNull(row.location)
        val store = requireNotNull(location.store)
        assertEquals(location, result.locations.single())
        assertEquals("Madrid", location.city)
        assertEquals("node/41", store.osmRef)
        assertEquals(ObservationScope.AtStore(store.id), price.record.scope)
        assertEquals("4006381333931", row.gtin?.original)
        assertEquals("Producto de prueba", row.product?.name)
        assertEquals("prices/11", price.record.origin.recordKey)
        assertEquals("open-prices", row.source.id.value)
        assertEquals("ODbL-1.0", row.source.licenseRef)
        assertEquals(51L, row.proofId)
        assertEquals(LocalDate.of(2026, 11, 2), price.record.observedOn)
        assertNull(price.record.observedAt)
        assertEquals(BigDecimal("1.69"), price.money.amount)
        assertEquals(Currency.EUR, price.money.currency)
        assertEquals(SaleUnit.PACKAGE, price.per)
    }

    @Test
    fun emptyPricesAndEmptyNearbyHaveDifferentCallPaths() = runBlocking {
        val source = source("""{"items":[],"total":0}""")
        val prices = source.sample(OpenPricesArea.Location(31))
        assertTrue(prices.observations.isEmpty())
        assertEquals(0L, prices.calls.single().total)
        assertEquals("/api/v1/prices", prices.calls.single().path)

        val nearby = source.sample(OpenPricesArea.Nearby(40.4168, -3.7038, 5.0))
        assertTrue(nearby.observations.isEmpty())
        assertEquals("/api/v1/locations/nearby", nearby.calls.single().path)
    }

    @Test
    fun upstreamCodeWithoutGtinKeepsItsProductIdentity() = runBlocking {
        val row = observation(fixture().replace("4006381333931", "SHOPCODE"))
        assertEquals("SHOPCODE", row.productCode)
        assertEquals("open-prices-product.SHOPCODE", row.product?.id?.value)
        assertNull(row.gtin)
    }

    @Test
    fun observationsOfTheSameProductStayIndependent() = runBlocking {
        val first = Json.parseToJsonElement(fixture()).jsonObject.getValue("items").jsonArray.single().toString()
        val second = first.replace("\"id\":11", "\"id\":12").replace("2026-11-02", "2026-11-01")
        val rows = source("""{"items":[$first,$second]}""").sample(OpenPricesArea.Location(31)).observations
        assertEquals(2, rows.size)
        assertEquals(rows.first().product, rows.last().product)
        assertNotEquals(rows.first().origin, rows.last().origin)
        assertNotEquals(rows.first().observation?.record?.observedOn, rows.last().observation?.record?.observedOn)
    }

    @Test
    fun missingLocationKeepsThePriceWithoutClaimingAStore() = runBlocking {
        val row = observation(
            """{"items":[{"id":11,"type":"PRODUCT","product_code":"SHOPCODE","price":1.69,"currency":"EUR"}]}"""
        )
        assertNull(row.locationId)
        assertNull(row.location)
        assertEquals(BigDecimal("1.69"), row.amount)
        assertNull(row.observation)
    }

    @Test
    fun invalidDateOrMissingAmountDoesNotInventValues() = runBlocking {
        val invalidDate = observation(fixture().replace("2026-11-02", "2026-02-30"))
        assertNull(invalidDate.date)
        assertNull(invalidDate.observation)

        val missingAmount = observation(fixture().replace("1.69", "null"))
        assertNull(missingAmount.amount)
        assertNull(missingAmount.observation)

        val explicitZero = observation(fixture().replace("1.69", "0"))
        assertEquals(BigDecimal.ZERO, explicitZero.observation?.money?.amount)
    }

    @Test
    fun categoryRateAndUnsupportedCurrencyRemainSourceObservations() = runBlocking {
        val category = observation(
            """{"items":[{"id":11,"type":"CATEGORY","category_tag":"en:apples","price_per":"KILOGRAM","price":1.69}]}"""
        )
        assertEquals("en:apples", category.categoryTag)
        assertEquals("KILOGRAM", category.pricePer)
        assertNull(category.product)
        assertNull(category.observation)

        val usd = observation(fixture().replace("EUR", "USD"))
        assertEquals("USD", usd.currency)
        assertNull(usd.observation)
    }

    @Test
    fun timeoutIsPropagatedAndConnectionIsClosed() = runBlocking {
        val source = OpenPricesSource { uri ->
            TestConnection(uri, timeout = true).also { connection = it }
        }
        val error = runCatching { source.sample(OpenPricesArea.Location(31)) }.exceptionOrNull()
        assertTrue(error is SocketTimeoutException)
        assertTrue(connection.disconnected)
    }

    @Test
    fun rateLimitAndServerErrorsPreserveStatusWithoutRetries() = runBlocking {
        for (status in listOf(429, 500, 503)) {
            var requests = 0
            val source = OpenPricesSource { uri ->
                requests++
                TestConnection(uri, status = status, retryAfter = "30")
            }
            val error = runCatching { source.sample(OpenPricesArea.Location(31)) }.exceptionOrNull()
            assertTrue(error is OpenPricesHttpException)
            error as OpenPricesHttpException
            assertEquals(status, error.status)
            assertEquals("30", error.retryAfter)
            assertEquals("/api/v1/prices", error.path)
            assertEquals(1, requests)
        }
    }

    @Test
    fun malformedPageIsDistinguishedFromAnEmptyResponse() = runBlocking {
        val error = runCatching { source("{}").sample(OpenPricesArea.Location(31)) }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    private lateinit var connection: TestConnection

    private suspend fun observation(body: String) =
        source(body).sample(OpenPricesArea.Location(31)).observations.single()

    private fun source(body: String) = OpenPricesSource { uri -> TestConnection(uri, body) }

    private fun fixture(): String =
        requireNotNull(javaClass.getResource("/open-prices/madrid-anonymized.json")).readText()

    private class TestConnection(
        uri: URI,
        private val body: String = "",
        private val status: Int = 200,
        private val retryAfter: String? = null,
        private val timeout: Boolean = false
    ) : HttpsURLConnection(uri.toURL()) {
        var disconnected = false
        val userAgent: String? get() = getRequestProperty("User-Agent")
        val accept: String? get() = getRequestProperty("Accept")

        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = ""
        override fun getLocalCertificates() = null
        override fun getServerCertificates() = null
        override fun getResponseCode(): Int {
            if (timeout) throw SocketTimeoutException()
            return status
        }
        override fun getHeaderField(name: String): String? = retryAfter.takeIf { name == "Retry-After" }
        override fun getInputStream() = body.byteInputStream()
    }
}
