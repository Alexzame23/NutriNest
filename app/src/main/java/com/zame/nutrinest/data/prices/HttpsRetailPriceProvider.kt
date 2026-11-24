package com.zame.nutrinest.data.prices

import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.MAX_PRICE_SNAPSHOT_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

internal class RetailHttpError(val status: Int) : Exception("HTTP $status")

/** Only the configured NutriNest publisher is contacted. Redirects are rejected. */
internal class HttpsRetailPriceProvider(
    baseUrl: String,
    private val cache: RoomPriceSnapshotCache? = null,
    private val openConnection: (URI) -> HttpsURLConnection = { it.toURL().openConnection() as HttpsURLConnection }
) : RetailPriceProvider {
    private val base = URI(baseUrl.trimEnd('/')).apply {
        require(scheme == "https" && host != null && rawUserInfo == null &&
            rawQuery == null && rawFragment == null)
    }

    suspend fun directory() = withContext(Dispatchers.IO) {
        get("/v1/geo/directory", setOf(404))?.let(SnapshotJson::directory)
    }

    override suspend fun publication(region: CommercialId): RetailPublication? = withContext(Dispatchers.IO) {
        val path = "/v1/price-regions/${region.value}"
        val manifestBytes = get("$path/manifest", setOf(404)) ?: return@withContext null
        val manifest = SnapshotJson.manifest(manifestBytes)
        require(manifest.regionId == region)
        val bytes = get("$path/snapshots/${manifest.revision}", setOf(404, 410)) {
            cache?.withdraw(region, manifest.revision)
        } ?: return@withContext null
        require(bytes.size == manifest.byteLength)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(digest == manifest.sha256)
        val snapshot = SnapshotJson.snapshot(bytes)
        require(snapshot.regionId == region && snapshot.revision == manifest.revision &&
            snapshot.contractVersion == manifest.contractVersion &&
            snapshot.generatedAt == manifest.generatedAt && snapshot.expiresAt == manifest.expiresAt)
        val simulated = snapshot.sources.any { it.id.value == "fake-prices" }
        val now = Instant.now()
        if (cache != null && !simulated && now >= snapshot.generatedAt && now < snapshot.expiresAt &&
            !cache.installVerified(snapshot, bytes, digest, now)) {
            // A concurrent install or withdrawal may already have advanced the region's revision.
            val current = cache.read(region, now) ?: return@withContext null
            return@withContext RetailPublication(current, simulated = current.sources.any { it.id.value == "fake-prices" })
        }
        RetailPublication(snapshot, simulated = simulated)
    }

    private suspend fun get(path: String, unavailable: Set<Int>, onGone: suspend () -> Unit = {}): ByteArray? {
        val connection = openConnection(URI(base.toString() + path))
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status == 410) onGone()
            if (status in unavailable) return null
            if (status != 200) throw RetailHttpError(status)
            if (connection.getHeaderField("X-NutriNest-Price-Data") != "live") return null
            require(connection.contentLength <= MAX_PRICE_SNAPSHOT_BYTES)
            return connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count == -1) break
                    require(output.size() + count <= MAX_PRICE_SNAPSHOT_BYTES)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}
