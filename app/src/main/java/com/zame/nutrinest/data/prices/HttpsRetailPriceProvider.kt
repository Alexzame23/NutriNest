package com.zame.nutrinest.data.prices

import com.zame.nutrinest.BuildConfig
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
        get("/v1/geo/directory", manifest = true)?.let(SnapshotJson::directory)
    }

    override suspend fun publication(region: CommercialId): RetailPublication? = withContext(Dispatchers.IO) {
        val path = "/v1/price-regions/${region.value}"
        val manifestBytes = get("$path/manifest", manifest = true) ?: return@withContext null
        val manifest = checked("manifest.parse") { SnapshotJson.manifest(manifestBytes) }
        PriceDiagnostics.log { "stage=manifest region=${region.value} revision=${manifest.revision}" }
        checked("manifest.region") { require(manifest.regionId == region) }
        val bytes = get("$path/snapshots/${manifest.revision}", manifest = false) {
            cache?.withdraw(region, manifest.revision)
        } ?: return@withContext null
        checked("snapshot.length") { require(bytes.size == manifest.byteLength) }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        verifySnapshotDigest(digest, manifest.sha256)
        val snapshot = checked("snapshot.parse") { SnapshotJson.snapshot(bytes) }
        checked("snapshot.header") {
            require(snapshot.regionId == region && snapshot.revision == manifest.revision &&
                snapshot.contractVersion == manifest.contractVersion &&
                snapshot.generatedAt == manifest.generatedAt && snapshot.expiresAt == manifest.expiresAt)
        }
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

    private inline fun <T> checked(stage: String, block: () -> T): T {
        if (!BuildConfig.DEBUG) return block()
        return try {
            block()
        } catch (error: Exception) {
            PriceDiagnostics.log { "stage=$stage rejected=${error.javaClass.simpleName}" }
            throw error
        }
    }

    private suspend fun get(path: String, manifest: Boolean, onGone: suspend () -> Unit = {}): ByteArray? {
        val started = if (BuildConfig.DEBUG) System.nanoTime() else 0L
        var status: Int? = null
        var marker = "unread"
        PriceDiagnostics.log { "stage=http.start path=$path" }
        val connection = openConnection(URI(base.toString() + path))
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            status = connection.responseCode
            if (status == 410 && !manifest) onGone()
            val header = connection.getHeaderField("X-NutriNest-Price-Data")
            if (BuildConfig.DEBUG) marker = when (header) {
                null -> "absent"
                "live", "simulated" -> header
                else -> "other"
            }
            responseDiagnosis(manifest, status, header)?.let {
                PriceDiagnostics.log { "stage=http path=$path state=$it" }
                return null
            }
            if (status != 200) throw RetailHttpError(status)
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
            PriceDiagnostics.log { "stage=http.end path=$path status=${status ?: "none"} marker=$marker elapsedMs=${(System.nanoTime() - started) / 1_000_000}" }
            connection.disconnect()
        }
    }
}
