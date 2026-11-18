package com.zame.nutrinest.data.prices

import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.MAX_PRICE_SNAPSHOT_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.URI
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

internal class RetailHttpError(val status: Int) : Exception("HTTP $status")

/** Only the configured NutriNest publisher is contacted. Redirects are rejected. */
internal class HttpsRetailPriceProvider(baseUrl: String) : RetailPriceProvider {
    private val base = URI(baseUrl.trimEnd('/')).apply {
        require(scheme == "https" && host != null && rawUserInfo == null &&
            rawQuery == null && rawFragment == null)
    }

    override suspend fun publication(region: CommercialId): RetailPublication? = withContext(Dispatchers.IO) {
        val path = "/v1/price-regions/${region.value}"
        val manifestBytes = get("$path/manifest", setOf(404)) ?: return@withContext null
        val manifest = SnapshotJson.manifest(manifestBytes)
        require(manifest.regionId == region)
        val bytes = get("$path/snapshots/${manifest.revision}", setOf(404, 410)) ?: return@withContext null
        require(bytes.size == manifest.byteLength)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(digest == manifest.sha256)
        val snapshot = SnapshotJson.snapshot(bytes)
        require(snapshot.regionId == region && snapshot.revision == manifest.revision &&
            snapshot.contractVersion == manifest.contractVersion &&
            snapshot.generatedAt == manifest.generatedAt && snapshot.expiresAt == manifest.expiresAt)
        RetailPublication(snapshot, simulated = snapshot.sources.any { it.id.value == "fake-prices" })
    }

    private fun get(path: String, unavailable: Set<Int>): ByteArray? {
        val connection = URI(base.toString() + path).toURL().openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
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
