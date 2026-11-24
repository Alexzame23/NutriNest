package com.zame.nutrinest.data.prices.superfudis

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

class SuperfudisPriceSourceTest {
    @Test
    fun sendsOnlyPublicBasketFieldsAndParsesRetailerOffer() {
        lateinit var connection: TestConnection
        val source = SuperfudisPriceSource(SuperfudisRateLimiter()) { uri ->
            TestConnection(uri).also { connection = it }
        }
        val sample = runBlocking { source.compare(listOf("leche semidesnatada 1 l"), listOf("Mercadona", "Dia")) }
        assertEquals(SuperfudisPriceSource.ENDPOINT, connection.url.toString())
        assertEquals("POST", connection.requestMethod)
        assertEquals("application/json; charset=utf-8", connection.getRequestProperty("Content-Type"))
        assertNull(connection.getRequestProperty("Authorization"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.disconnected)
        val body = Json.parseToJsonElement(connection.posted.toString(Charsets.UTF_8.name())).jsonObject
        assertEquals(setOf("items", "retailers", "include_delivery"), body.keys)
        val requestItem = body.getValue("items").jsonArray.single().jsonObject
        assertEquals("leche semidesnatada 1 l", requestItem.getValue("query").jsonPrimitive.content)
        assertEquals("1", requestItem.getValue("quantity").jsonPrimitive.content)
        assertEquals("false", body.getValue("include_delivery").jsonPrimitive.content)

        val item = sample.items.single()
        val offer = item.offers.single()
        assertEquals(200, sample.status)
        assertEquals("ok", sample.providerStatus)
        assertEquals(listOf("Dia"), item.unavailableRetailers)
        assertEquals("Producto base sintético", item.product?.name)
        assertEquals("Mercadona", offer.retailer)
        assertEquals("Envase de prueba", offer.product.name)
        assertEquals("brik", offer.product.format)
        assertEquals("0001234567890", offer.product.ean)
        assertEquals(1, offer.product.units)
        assertEquals(BigDecimal("1.0"), offer.product.unitSize)
        assertEquals("l", offer.product.unitSizeUnit)
        assertEquals(BigDecimal("2.34"), offer.priceEur)
        assertEquals("exact_ean", offer.matchType)
        assertNull(offer.priceDate)
        assertTrue(sample.locationWarning.contains("tienda ni código postal"))
    }

    @Test
    fun resolvedInputInPartialResponseDoesNotBecomeAnOffer() {
        val sample = parseSuperfudisSample(
            200, """{"status":"needs_clarification","resolved_items":[{"query":"leche"}],
                "unresolved_items":[{"query":"arroz","reason":"ambiguous"}]}"""
        )
        assertTrue(sample.items.isEmpty())
        assertEquals(listOf("leche"), sample.resolvedQueries)
        assertEquals(listOf(SuperfudisUnresolvedItem("arroz", "ambiguous")), sample.unresolvedItems)
    }

    @Test
    fun missingPriceRemainsNullAndExplicitPriceDateIsKept() {
        val sample = parseSuperfudisSample(
            200, SUCCESS.replace("\"price_eur\":2.34", "\"price_eur\":null,\"price_date\":\"2026-01-02\"")
        )
        val offer = sample.items.single().offers.single()
        assertNull(offer.priceEur)
        assertEquals("2026-01-02", offer.priceDate)
    }

    @Test
    fun invalidBasketCountsAndUnknownRetailerAreRejectedLocally() {
        val source = SuperfudisPriceSource(SuperfudisRateLimiter()) { error("Unexpected HTTP request") }
        runBlocking {
            assertTrue(runCatching { source.compare(emptyList(), listOf("Dia")) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { source.compare(List(11) { "arroz" }, listOf("Dia")) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { source.compare(listOf("arroz"), listOf("otra")) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun slidingQuotaCountsTimeoutsAndIsSharedAcrossClients() {
        var now = TimeUnit.SECONDS.toNanos(5)
        var attempts = 0
        val limiter = SuperfudisRateLimiter { now }
        val broken = SuperfudisPriceSource(limiter) { uri ->
            attempts++
            TestConnection(uri, timeout = true)
        }
        val other = SuperfudisPriceSource(limiter) { uri ->
            attempts++
            TestConnection(uri)
        }
        runBlocking {
            val items = listOf("arroz")
            val retailers = listOf("Dia")
            assertTrue(runCatching { broken.compare(items, retailers) }.exceptionOrNull() is SocketTimeoutException)
            repeat(2) { other.compare(items, retailers) }
            now += TimeUnit.SECONDS.toNanos(59)
            assertTrue(runCatching { other.compare(items, retailers) }.exceptionOrNull() is SuperfudisRateLimitException)
            assertEquals(3, attempts)
            now += TimeUnit.SECONDS.toNanos(1)
            other.compare(items, retailers)
            assertEquals(4, attempts)
        }
    }

    @Test
    fun server429StopsCallsForRetryAfterAndDoesNotRetry() {
        var now = 0L
        var attempts = 0
        val limiter = SuperfudisRateLimiter { now }
        val source = SuperfudisPriceSource(limiter) { uri ->
            attempts++
            TestConnection(uri, status = 429, retryAfter = "75")
        }
        runBlocking {
            val request = suspend { source.compare(listOf("arroz"), listOf("Dia")) }
            val error = runCatching { request() }.exceptionOrNull() as SuperfudisHttpException
            assertEquals(429, error.status)
            assertEquals("75", error.retryAfter)
            now = TimeUnit.SECONDS.toNanos(60)
            assertTrue(runCatching { request() }.exceptionOrNull() is SuperfudisRateLimitException)
            assertEquals(1, attempts)
            now = TimeUnit.SECONDS.toNanos(75)
            assertTrue(runCatching { request() }.exceptionOrNull() is SuperfudisHttpException)
            assertEquals(2, attempts)
        }
    }

    @Test
    fun serviceErrorAndUnexpectedSuccessSchemaAreNotEmptyBaskets() {
        val source = SuperfudisPriceSource(SuperfudisRateLimiter()) { uri -> TestConnection(uri, status = 503) }
        val error = runBlocking {
            runCatching { source.compare(listOf("arroz"), listOf("Dia")) }.exceptionOrNull()
        } as SuperfudisHttpException
        assertEquals(503, error.status)
        assertThrows(IOException::class.java) { parseSuperfudisSample(200, """{"status":"ok"}""") }
    }

    private class TestConnection(
        uri: URI,
        private val status: Int = 200,
        private val retryAfter: String? = null,
        private val timeout: Boolean = false
    ) : HttpsURLConnection(uri.toURL()) {
        val posted = ByteArrayOutputStream()
        var disconnected = false

        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = ""
        override fun getLocalCertificates() = null
        override fun getServerCertificates() = null
        override fun getOutputStream() = posted
        override fun getResponseCode(): Int {
            if (timeout) throw SocketTimeoutException()
            return status
        }
        override fun getHeaderField(name: String): String? = retryAfter.takeIf { name == "Retry-After" }
        override fun getInputStream() = SUCCESS.byteInputStream()
    }

    private companion object {
        val SUCCESS = """{"status":"ok","items":[{
            "query":"leche","quantity":1,"product":{"name":"Producto base sintético"},
            "offers":[{"retailer":"Mercadona","name":"Envase de prueba","ean":"0001234567890",
                "format":"brik","n_units":1.0,"unit_size_value":1.0,"unit_size_unit":"l",
                "price_eur":2.34,"match_type":"exact_ean"}],"unavailable_retailers":["Dia"]}]}"""
    }
}
