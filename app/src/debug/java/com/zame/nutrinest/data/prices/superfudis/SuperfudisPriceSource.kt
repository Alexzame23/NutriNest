package com.zame.nutrinest.data.prices.superfudis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

internal fun interface SpanishBasketPriceSource {
    suspend fun compare(items: List<String>, retailers: List<String>): SuperfudisSample
}

internal class SuperfudisHttpException(val status: Int, val retryAfter: String?) :
    IOException("Superfudis compare-basket returned HTTP $status")

internal class SuperfudisRateLimitException : IOException("Superfudis local limit reached. Try again later.")

/** Shared for all default clients in this process. Failed and timed-out calls consume a slot. */
internal class SuperfudisRateLimiter(private val nanoTime: () -> Long = System::nanoTime) {
    private val calls = ArrayDeque<Long>()
    private val window = TimeUnit.MINUTES.toNanos(1)
    private var lastRejection: Long? = null
    private var rejectionCooldown = window

    @Synchronized
    fun reserve() {
        val now = nanoTime()
        if (lastRejection?.let { now - it < rejectionCooldown } == true) throw SuperfudisRateLimitException()
        while (calls.isNotEmpty() && now - calls.first() >= window) calls.removeFirst()
        if (calls.size >= 3) throw SuperfudisRateLimitException()
        calls.addLast(now)
    }

    @Synchronized
    fun record429(retryAfter: String?) {
        val now = nanoTime()
        val seconds = retryAfter?.trim()?.let { value ->
            value.toLongOrNull() ?: try {
                val until = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
                Duration.between(Instant.now(), until).seconds.coerceAtLeast(0) + 1
            } catch (_: DateTimeParseException) {
                null
            }
        } ?: 0
        val cooldown = maxOf(window, TimeUnit.SECONDS.toNanos(seconds))
        val remaining = lastRejection?.let { rejectionCooldown - (now - it) } ?: 0
        rejectionCooldown = maxOf(cooldown, remaining)
        lastRejection = now
    }
}

/** Interactive debug PoC for the documented public basket endpoint. */
internal class SuperfudisPriceSource(
    private val rateLimiter: SuperfudisRateLimiter = sharedLimiter,
    private val openConnection: (URI) -> HttpsURLConnection = {
        it.toURL().openConnection() as HttpsURLConnection
    }
) : SpanishBasketPriceSource {
    override suspend fun compare(items: List<String>, retailers: List<String>): SuperfudisSample =
        withContext(Dispatchers.IO) {
            require(items.size in 1..10 && items.all(String::isNotBlank))
            require(retailers.isNotEmpty() && retailers.all { it in RETAILERS })
            val body = buildJsonObject {
                put("items", JsonArray(items.map { query ->
                    buildJsonObject {
                        put("query", query)
                        put("quantity", 1)
                    }
                }))
                put("retailers", JsonArray(retailers.distinct().map(::JsonPrimitive)))
                put("include_delivery", false)
            }.toString().toByteArray(Charsets.UTF_8)

            rateLimiter.reserve()
            val connection = openConnection(URI(ENDPOINT))
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10_000
                connection.readTimeout = 60_000
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.outputStream.use { it.write(body) }
                val status = connection.responseCode
                if (status != 200) {
                    val retryAfter = connection.getHeaderField("Retry-After")
                    if (status == 429) rateLimiter.record429(retryAfter)
                    throw SuperfudisHttpException(status, retryAfter)
                }
                parseSuperfudisSample(status, readBody(connection))
            } finally {
                connection.disconnect()
            }
        }

    private fun readBody(connection: HttpsURLConnection): String = connection.inputStream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_RESPONSE_BYTES) throw IOException("Superfudis response too large")
            output.write(buffer, 0, read)
        }
        output.toString(Charsets.UTF_8.name())
    }

    companion object {
        const val ENDPOINT = "https://supercomparador-i9tw.onrender.com/api/v1/compare-basket"
        private const val USER_AGENT = "NutriNest/0.1 (https://github.com/Alexzame23/NutriNest)"
        private const val MAX_RESPONSE_BYTES = 1024 * 1024
        private val sharedLimiter = SuperfudisRateLimiter()
        val RETAILERS = listOf(
            "Mercadona", "Carrefour", "Dia", "Consum", "Eroski", "El Corte Inglés", "ahorramas", "BM", "Alcampo"
        )
    }
}
