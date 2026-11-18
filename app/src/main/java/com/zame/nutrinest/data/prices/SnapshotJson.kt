package com.zame.nutrinest.data.prices

import android.util.JsonReader
import android.util.JsonToken
import com.zame.nutrinest.domain.prices.*
import java.io.InputStreamReader
import java.math.BigDecimal
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.LocalDate

/** NutriNest's public wire format; provider DTOs belong in their backend adapters. */
internal object SnapshotJson {
    fun manifest(bytes: ByteArray): SnapshotManifest = parse(bytes).run {
        SnapshotManifest(
            int("contractVersion"), id("regionId"), long("revision"), instant("generatedAt"),
            instant("expiresAt"), string("sha256"), int("byteLength")
        )
    }

    fun snapshot(bytes: ByteArray): PriceSnapshot = parse(bytes).run {
        PriceSnapshot(
            int("contractVersion"), id("regionId"), long("revision"), instant("generatedAt"),
            instant("expiresAt"),
            objects("sources") {
                PriceSource(id("id"), string("name"), enum("kind"), string("licenseRef"), string("attribution"))
            },
            objects("retailers") { Retailer(id("id"), origin(), string("name")) },
            objects("stores") {
                Store(id("id"), origin(), id("retailerId"), string("name"), optionalString("postalCode"))
            },
            objects("zones") { PriceZone(id("id"), origin(), id("retailerId"), string("name")) },
            objects("products") {
                CommercialProduct(
                    id("id"), origin(), string("name"), optionalString("brand"),
                    optionalString("variant"), obj("attributes").strings()
                )
            },
            objects("packages") {
                ProductPackage(
                    id("id"), origin(), id("productId"), optionalString("gtin")?.let(::Gtin),
                    enum("weightMode"), enum("saleUnit"), optionalObject("netContent")?.run {
                        NetContent(decimal("amount"), enum("unit"))
                    }, optionalInt("unitsPerPackage")
                )
            },
            objects("matches") {
                IngredientProductMatch(
                    origin(), id("ingredientKey"), int("mappingVersion"), id("packageId"),
                    enum("kind"), enum("confidence")
                )
            },
            objects("prices") {
                PriceObservation(
                    record(), Money(decimal("amount"), enum("currency")), enum("per"),
                    enum("kind"), optionalString("eligibility")?.let(PromotionEligibility::valueOf),
                    optionalString("validUntil")?.let(LocalDate::parse)
                )
            },
            objects("availability") { AvailabilityObservation(record(), enum("state")) }
        )
    }

    private fun WireObject.origin() = obj("origin").run { ProviderRecord(id("sourceId"), string("recordKey")) }

    private fun WireObject.record() = obj("record").run {
        ObservationRecord(
            origin(), int("revision"), id("retailerId"), id("packageId"), obj("scope").scope(),
            LocalDate.parse(string("observedOn")), optionalString("observedAt")?.let(Instant::parse),
            enum("confidence")
        )
    }

    private fun WireObject.scope(): ObservationScope {
        val storeId = optionalString("storeId")?.let(::CommercialId)
        val zoneId = optionalString("zoneId")?.let(::CommercialId)
        return when (val type = string("type")) {
            "STORE" -> {
                require(storeId != null && zoneId == null)
                ObservationScope.AtStore(storeId)
            }
            "ZONE" -> {
                require(storeId == null && zoneId != null)
                ObservationScope.InZone(zoneId)
            }
            "ONLINE" -> {
                require(storeId == null)
                ObservationScope.Online(zoneId)
            }
            "UNKNOWN" -> {
                require(storeId == null && zoneId == null)
                ObservationScope.Unknown
            }
            else -> throw IllegalArgumentException("Unsupported observation scope: $type")
        }
    }

    private fun parse(bytes: ByteArray): WireObject {
        require(bytes.size in 1..MAX_PRICE_SNAPSHOT_BYTES)
        val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        return JsonReader(InputStreamReader(bytes.inputStream(), utf8)).use { reader ->
            val result = reader.value(0) as? WireObject ?: throw IllegalArgumentException("Expected a JSON object")
            require(reader.peek() == JsonToken.END_DOCUMENT)
            result
        }
    }

    private fun JsonReader.value(depth: Int): Any? {
        require(depth <= 32)
        return when (peek()) {
            JsonToken.BEGIN_OBJECT -> {
                beginObject()
                val fields = mutableMapOf<String, Any?>()
                while (hasNext()) {
                    val name = nextName()
                    require(name !in fields) { "Duplicate JSON key: $name" }
                    fields[name] = value(depth + 1)
                }
                endObject()
                WireObject(fields)
            }
            JsonToken.BEGIN_ARRAY -> {
                beginArray()
                val items = buildList { while (hasNext()) add(value(depth + 1)) }
                endArray()
                items
            }
            JsonToken.STRING -> nextString()
            JsonToken.NUMBER -> WireNumber(nextString())
            JsonToken.BOOLEAN -> nextBoolean()
            JsonToken.NULL -> null.also { nextNull() }
            else -> throw IllegalArgumentException("Unexpected JSON token: ${peek()}")
        }
    }
}

private data class WireNumber(val text: String)

private class WireObject(private val fields: Map<String, Any?>) {
    fun string(key: String): String = optionalString(key) ?: missing(key)
    fun optionalString(key: String): String? = optional(key)
    fun id(key: String) = CommercialId(string(key))
    fun instant(key: String): Instant = Instant.parse(string(key))
    fun int(key: String): Int = optionalInt(key) ?: missing(key)
    fun optionalInt(key: String): Int? = optional<WireNumber>(key)?.text?.toInt()
    fun long(key: String): Long = (optional<WireNumber>(key) ?: missing(key)).text.toLong()
    fun decimal(key: String): BigDecimal {
        val text = string(key)
        require(text.matches(Regex("(0|[1-9][0-9]{0,8})(\\.[0-9]{1,4})?")))
        return text.toBigDecimal()
    }
    inline fun <reified T : Enum<T>> enum(key: String): T = enumValueOf(string(key))
    fun obj(key: String): WireObject = optionalObject(key) ?: missing(key)
    fun optionalObject(key: String): WireObject? = optional(key)
    fun strings(): Map<String, String> = fields.mapValues { (key, _) -> string(key) }
    fun <T> objects(key: String, read: WireObject.() -> T): List<T> =
        (optional<List<*>>(key) ?: missing(key)).map {
            (it as? WireObject ?: throw IllegalArgumentException("Expected objects in $key")).read()
        }

    private inline fun <reified T> optional(key: String): T? =
        fields[key]?.let { it as? T ?: throw IllegalArgumentException("Invalid JSON field: $key") }

    private fun missing(key: String): Nothing = throw IllegalArgumentException("Missing JSON field: $key")
}
