package com.zame.nutrinest.data.prices.openprices

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import javax.net.ssl.HttpsURLConnection

internal fun interface OpenPriceObservationSource {
    suspend fun sample(area: OpenPricesArea): OpenPricesSample
}

internal sealed interface OpenPricesArea {
    data class Nearby(val latitude: Double, val longitude: Double, val radiusKm: Double) : OpenPricesArea {
        init {
            require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
            require(radiusKm > 0 && radiusKm.isFinite())
        }
    }

    data class Location(val id: Long) : OpenPricesArea {
        init { require(id > 0) }
    }
}

internal data class OpenPricesCall(val path: String, val status: Int, val received: Int, val total: Long?)

internal data class OpenPricesSample(
    val locations: List<OpenPricesLocation>,
    val observations: List<OpenPricesObservation>,
    val calls: List<OpenPricesCall>
)

internal class OpenPricesHttpException(
    val path: String,
    val status: Int,
    val retryAfter: String?
) : IOException("Open Prices GET $path returned HTTP $status")

/** Bounded, opt-in read of the public API. No dependency on the publication pipeline. */
internal class OpenPricesSource(
    private val openConnection: (URI) -> HttpsURLConnection = {
        it.toURL().openConnection() as HttpsURLConnection
    }
) : OpenPriceObservationSource {
    override suspend fun sample(area: OpenPricesArea): OpenPricesSample = withContext(Dispatchers.IO) {
        val calls = mutableListOf<OpenPricesCall>()
        val nearby = when (area) {
            is OpenPricesArea.Nearby -> page(
                "/api/v1/locations/nearby",
                "lat=${area.latitude}&lon=${area.longitude}&radius_km=${area.radiusKm}",
                calls
            ).mapNotNull { (it as? JsonObject)?.let(OpenPricesLocation::fromJson) }
            is OpenPricesArea.Location -> emptyList()
        }.associateBy { it.id }

        val filter = when (area) {
            is OpenPricesArea.Nearby -> {
                if (nearby.isEmpty()) return@withContext OpenPricesSample(emptyList(), emptyList(), calls)
                "location_id__in=${nearby.keys.joinToString(",")}"
            }
            is OpenPricesArea.Location -> "location_id=${area.id}"
        }
        val observations = page("/api/v1/prices", "$filter&currency=EUR&order_by=-date,-id", calls)
            .mapNotNull { (it as? JsonObject)?.let { row -> OpenPricesObservation.fromJson(row, nearby) } }
        val locations = (observations.mapNotNull { it.location } + nearby.values).distinctBy { it.id }
        OpenPricesSample(locations, observations, calls)
    }

    private fun page(path: String, query: String, calls: MutableList<OpenPricesCall>): JsonArray {
        val connection = openConnection(URI("$BASE_URL$path?$query&size=$PAGE_SIZE"))
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val status = connection.responseCode
            if (status != 200) {
                throw OpenPricesHttpException(path, status, connection.getHeaderField("Retry-After"))
            }

            val page = try {
                Json.parseToJsonElement(readBody(connection)) as? JsonObject
            } catch (_: SerializationException) {
                throw IOException("Invalid Open Prices JSON from $path")
            } ?: throw IOException("Invalid Open Prices page from $path")
            val items = page["items"] as? JsonArray
                ?: throw IOException("Missing Open Prices items array from $path")
            calls += OpenPricesCall(path, status, items.size, page.text("total")?.toLongOrNull()?.takeIf { it >= 0 })
            return items
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpsURLConnection): String = connection.inputStream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("Open Prices response too large")
            output.write(buffer, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }

    companion object {
        const val BASE_URL = "https://prices.openfoodfacts.org"
        const val USER_AGENT = "NutriNest/0.1 (https://github.com/Alexzame23/NutriNest)"
        private const val PAGE_SIZE = 10
        private const val MAX_RESPONSE_BYTES = 512 * 1024
    }
}
