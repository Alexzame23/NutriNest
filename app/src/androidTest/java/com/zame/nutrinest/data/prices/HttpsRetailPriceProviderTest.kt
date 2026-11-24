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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import androidx.room.Room
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.domain.stores.GeoResolver
import org.json.JSONObject
import org.json.JSONArray
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
            manifest(wrongRevision, revision = 1) to wrongRevision
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

    @Test fun revalidatesWithoutLosingVerifiedPricesAndReopensOffline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "price-revalidation-${UUID.randomUUID()}.db"
        val now = Instant.parse("2026-11-18T13:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val secondRegion = CommercialId("other-region")
        val key = CommercialId("test-pasta-dry")
        var database = Room.databaseBuilder(context, NutriNestDatabase::class.java, name).build()

        fun directory(id: CommercialId, bytes: ByteArray): Reply {
            val stores = JSONObject(bytes.decodeToString()).getJSONArray("stores")
            return Reply(bytes = JSONObject().put("provider", "test-source")
                .put("regionId", id.value).put("generatedAt", "2026-11-18T12:00:00Z")
                .put("expiresAt", "2026-11-20T12:00:00Z").put("stores", stores).toString().encodeToByteArray())
        }

        fun repository(publisher: Publisher, online: Boolean = true): RegionalRetailPriceRepository {
            val geo = GeoResolver({ publisher.provider.directory() }, clock)
            return RegionalRetailPriceRepository(
                { RetailIngredientKeys(key) }, geo, publisher.provider, hasNetwork = { online },
                cache = RoomPriceSnapshotCache(database, "publisher-a"), clock = clock
            )
        }

        fun publisher(vararg replies: Reply) =
            Publisher(*replies, cache = RoomPriceSnapshotCache(database, "publisher-a"), clock = clock)

        fun withRevision(revision: Int): ByteArray = JSONObject(snapshot.decodeToString())
            .put("revision", revision).toString().encodeToByteArray()

        suspend fun count(id: CommercialId = region): Int =
            RoomPriceSnapshotCache(database, "publisher-a").read(id, now)?.prices?.size ?: 0

        suspend fun refresh(postal: String, id: CommercialId, bytes: ByteArray, reply: Reply = Reply(bytes)): RetailResult {
            val source = publisher(directory(id, bytes), manifest(bytes), reply)
            return repository(source).compare(1, postal, now)
        }

        try {
            assertEquals(0, count())
            assertEquals(1, (refresh("08001", region, snapshot) as RetailResult.Offers).rows.size)
            assertEquals(2, count())
            assertEquals(1, (refresh("08001", region, snapshot) as RetailResult.Offers).rows.size)
            val second = withRevision(2)
            assertEquals(1, (refresh("08001", region, second) as RetailResult.Offers).rows.size)
            assertEquals(2L, database.priceSnapshotDao().find(region.value, "publisher-a")?.revision)
            assertEquals(2, count())
            val originalSuccess = database.priceSnapshotDao().sync("publisher-a", region.value)?.lastSuccessMillis
            val bad = withRevision(3)
            val empty = JSONObject(bad.decodeToString()).put("prices", JSONArray()).toString().encodeToByteArray()
            for ((bytes, response, error) in listOf(
                Triple(bad, Reply(bad.copyOf().apply { this[lastIndex] = 0 }), "HASH_MISMATCH"),
                Triple(bad, Reply(timeout = true), "TIMEOUT"),
                Triple(empty, Reply(empty), "EMPTY_SNAPSHOT"),
                Triple(bad, Reply(status = 404), "PUBLICATION_UNAVAILABLE")
            )) {
                val cached = refresh("08001", region, bytes, response) as RetailResult.Offers
                assertTrue(error, cached.fromCache)
                assertEquals(error, 1, cached.rows.size)
                assertEquals(error, 2, count())
                assertEquals(2L, database.priceSnapshotDao().find(region.value, "publisher-a")?.revision)
                val sync = database.priceSnapshotDao().sync("publisher-a", region.value)
                assertEquals(error, sync?.error)
                assertEquals(originalSuccess, sync?.lastSuccessMillis)
            }
            val other = snapshot.decodeToString().replace("test-region", secondRegion.value)
                .replace("08001", "15001").encodeToByteArray()
            val otherOffer = refresh("15001", secondRegion, other) as RetailResult.Offers
            assertEquals("15001", otherOffer.rows.single().store.area.postalCode)
            assertEquals(2, count(secondRegion))
            assertEquals(2, count())
            assertNull(RoomPriceSnapshotCache(database, "publisher-b").readPostal("08001", now))
            val unreachable = publisher()
            val offline = repository(unreachable, online = false)
            val beforeRestart = offline.compare(1, "08001", now) as RetailResult.Offers
            assertTrue(beforeRestart.fromCache)
            assertEquals(1, beforeRestart.rows.size)
            assertEquals(RetailResult.Offline, offline.compare(1, "41001", now))
            assertTrue(unreachable.paths.isEmpty())

            database.close()
            database = Room.databaseBuilder(context, NutriNestDatabase::class.java, name).build()
            val restarted = repository(publisher(), online = false)
            val afterRestart = restarted.compare(1, "08001", now) as RetailResult.Offers
            assertTrue(afterRestart.fromCache)
            assertEquals(beforeRestart.rows, afterRestart.rows)
            assertEquals("15001", (restarted.compare(1, "15001", now) as RetailResult.Offers)
                .rows.single().store.area.postalCode)
            assertEquals("PUBLICATION_UNAVAILABLE", database.priceSnapshotDao().sync("publisher-a", region.value)?.error)
            assertEquals(RetailResult.Offline, restarted.compare(1, "08001", Instant.parse("2026-11-20T12:00:00Z")))
            val withdrawal = publisher(directory(region, second), manifest(second), Reply(status = 410))
            assertEquals(RetailResult.NoCoverage, repository(withdrawal).compare(1, "08001", now))
            assertEquals(0, count())
            assertEquals(2, count(secondRegion))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
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

    private fun manifest(bytes: ByteArray = snapshot, revision: Int? = null): Reply {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val header = JSONObject(bytes.decodeToString())
        return Reply(bytes = """
            {"contractVersion":1,"regionId":"${header.getString("regionId")}","revision":${revision ?: header.getInt("revision")},
            "generatedAt":"2026-11-18T12:00:00Z","expiresAt":"2026-11-20T12:00:00Z",
            "sha256":"$hash","byteLength":${bytes.size}}
        """.trimIndent().encodeToByteArray())
    }

    private class Publisher(vararg replies: Reply, cache: RoomPriceSnapshotCache? = null, clock: Clock = Clock.systemUTC()) {
        val paths = mutableListOf<String>()
        val connections = mutableListOf<Connection>()
        val provider = HttpsRetailPriceProvider("https://publisher.invalid/publisher/", cache, clock) { uri ->
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
