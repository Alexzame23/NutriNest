package com.zame.nutrinest.data.prices

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zame.nutrinest.domain.prices.CommercialId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import java.net.URI
import java.security.MessageDigest
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection

@RunWith(AndroidJUnit4::class)
class HttpsRetailPriceProviderTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val region = CommercialId("test-region")
    private val snapshot = assets.open("price-snapshot-v1.json").use { it.readBytes() }
    private val prefix = "/publisher/v1/price-regions/test-region"

    @Test fun servesExactlyTheManifestRevisionAndPreservesDecimalPrices() = runBlocking {
        val publisher = Publisher(manifest(), Reply(bytes = snapshot))
        val publication = requireNotNull(publisher.provider.publication(region))
        assertEquals(region, publication.snapshot.regionId)
        assertEquals(1L, publication.snapshot.revision)
        assertEquals("1.2345", publication.snapshot.prices.first().money.amount.toPlainString())
        assertEquals(listOf("$prefix/manifest", "$prefix/snapshots/1"), publisher.paths)
        for (connection in publisher.connections) {
            assertEquals("GET", connection.requestMethod)
            assertEquals("application/json", connection.getRequestProperty("Accept"))
            assertFalse(connection.instanceFollowRedirects)
            assertFalse(connection.useCaches)
            assertTrue(connection.connectTimeout > 0 && connection.readTimeout > 0)
            assertTrue(connection.disconnected)
        }
    }

    @Test fun missingWithdrawnAndUnattestedResponsesDoNotExposePrices() = runBlocking {
        for (reply in listOf(Reply(status = 404), Reply(dataHeader = null), Reply(dataHeader = "simulated"))) {
            val publisher = Publisher(reply)
            assertNull(publisher.provider.publication(region))
            assertEquals(listOf("$prefix/manifest"), publisher.paths)
            assertFalse(publisher.connections.single().bodyRead)
        }
        for (reply in listOf(Reply(status = 404), Reply(status = 410), Reply(dataHeader = null))) {
            val publisher = Publisher(manifest(), reply)
            assertNull(publisher.provider.publication(region))
            assertEquals(2, publisher.paths.size)
            assertFalse(publisher.connections.last().bodyRead)
        }
    }

    @Test fun malformedManifestAndInconsistentPayloadsAreRejected() = runBlocking {
        val duplicate = assets.open("price-manifest-v1-duplicate-revision.invalid.json").use { it.readBytes() }
        val badManifest = Publisher(Reply(bytes = duplicate))
        assertTrue(rejection(badManifest) is IllegalArgumentException)
        assertEquals(listOf("$prefix/manifest"), badManifest.paths)

        val wrongRevision = snapshot.decodeToString().replace("\"revision\": 1", "\"revision\": 2").encodeToByteArray()
        for ((header, payload) in listOf(
            manifest() to snapshot.copyOf(snapshot.size - 1),
            manifest() to snapshot.copyOf().apply { this[lastIndex] = 0 },
            manifest(wrongRevision) to wrongRevision
        )) {
            val publisher = Publisher(header, Reply(bytes = payload))
            assertTrue(rejection(publisher) is IllegalArgumentException)
            assertEquals(2, publisher.paths.size)
        }
    }

    @Test fun redirectsErrorsAndTimeoutsStopWithoutRetry() = runBlocking {
        for (status in listOf(302, 429, 503)) {
            val publisher = Publisher(Reply(status = status))
            assertEquals(status, (rejection(publisher) as RetailHttpError).status)
            assertFalse(publisher.connections.single().bodyRead)
        }
        val timeout = Publisher(manifest(), Reply(timeout = true))
        assertTrue(rejection(timeout) is SocketTimeoutException)
        assertEquals(2, timeout.paths.size)
    }

    private suspend fun rejection(publisher: Publisher): Exception {
        val error = try {
            publisher.provider.publication(region)
            throw AssertionError("Expected provider rejection")
        } catch (failure: Exception) {
            failure
        }
        assertTrue(publisher.connections.all { it.disconnected })
        return error
    }

    private fun manifest(bytes: ByteArray = snapshot): Reply {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return Reply(bytes = """
            {"contractVersion":1,"regionId":"test-region","revision":1,
            "generatedAt":"2026-11-18T12:00:00Z","expiresAt":"2026-11-20T12:00:00Z",
            "sha256":"$hash","byteLength":${bytes.size}}
        """.trimIndent().encodeToByteArray())
    }

    private class Publisher(vararg replies: Reply) {
        val paths = mutableListOf<String>()
        val connections = mutableListOf<Connection>()
        val provider = HttpsRetailPriceProvider("https://publisher.invalid/publisher/") { uri ->
            val reply = replies.getOrNull(paths.size) ?: throw AssertionError("Unexpected request to $uri")
            assertEquals("publisher.invalid", uri.host)
            paths += uri.path
            Connection(uri, reply).also { connections += it }
        }
    }

    private data class Reply(
        val bytes: ByteArray = byteArrayOf(),
        val status: Int = 200,
        val dataHeader: String? = "live",
        val timeout: Boolean = false
    )

    private class Connection(uri: URI, private val reply: Reply) : HttpsURLConnection(uri.toURL()) {
        var disconnected = false
            private set
        var bodyRead = false
            private set

        override fun getResponseCode(): Int {
            if (reply.timeout) throw SocketTimeoutException("Synthetic timeout")
            return reply.status
        }
        override fun getHeaderField(name: String): String? =
            if (name.equals("X-NutriNest-Price-Data", ignoreCase = true)) reply.dataHeader else null
        override fun getContentLength() = reply.bytes.size
        override fun getInputStream(): ByteArrayInputStream {
            bodyRead = true
            return ByteArrayInputStream(reply.bytes)
        }
        override fun disconnect() { disconnected = true }
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun getCipherSuite() = "unused"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate>? = null
    }
}
