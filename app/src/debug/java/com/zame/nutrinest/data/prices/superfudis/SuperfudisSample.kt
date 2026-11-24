package com.zame.nutrinest.data.prices.superfudis

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.math.BigDecimal

internal data class SuperfudisProduct(
    val name: String,
    val brand: String?,
    val ean: String?,
    val format: String?,
    val units: Int?,
    val totalVolume: BigDecimal?,
    val unitSize: BigDecimal?,
    val unitSizeUnit: String?
)

internal data class SuperfudisOffer(
    val retailer: String,
    val product: SuperfudisProduct,
    val priceEur: BigDecimal?,
    val eurPerUnit: BigDecimal?,
    val comparisonUnit: String?,
    val matchType: String?,
    val priceDate: String?
)

internal data class SuperfudisBasketItem(
    val query: String,
    val product: SuperfudisProduct?,
    val offers: List<SuperfudisOffer>,
    val unavailableRetailers: List<String>
)

internal data class SuperfudisUnresolvedItem(val query: String, val reason: String?)

internal data class SuperfudisSample(
    val status: Int,
    val providerStatus: String,
    val items: List<SuperfudisBasketItem>,
    val resolvedQueries: List<String>,
    val unresolvedItems: List<SuperfudisUnresolvedItem>
) {
    val locationWarning: String = "Precio orientativo de la cadena. Superfudis no identifica tienda ni código postal."
}

internal fun parseSuperfudisSample(status: Int, body: String): SuperfudisSample {
    val payload = try {
        Json.parseToJsonElement(body) as? JsonObject
    } catch (_: SerializationException) {
        null
    } ?: throw IOException("Invalid Superfudis JSON")
    val providerStatus = payload.text("status") ?: throw IOException("Missing Superfudis comparison status")
    val items = payload["items"] as? JsonArray
    val unresolved = payload["unresolved_items"] as? JsonArray
    if ((providerStatus == "ok" && items == null) || (items == null && unresolved == null)) {
        throw IOException("Missing Superfudis comparison items")
    }
    return SuperfudisSample(
        status = status,
        providerStatus = providerStatus,
        items = items.objects().mapNotNull { item ->
            val query = item.text("query") ?: return@mapNotNull null
            SuperfudisBasketItem(
                query,
                (item["product"] as? JsonObject)?.let(::product),
                (item["offers"] as? JsonArray).objects().mapNotNull(::offer),
                (item["unavailable_retailers"] as? JsonArray).orEmpty().mapNotNull { it.asText() }
            )
        },
        resolvedQueries = (payload["resolved_items"] as? JsonArray).objects().mapNotNull { it.text("query") },
        unresolvedItems = unresolved.objects().mapNotNull { item ->
            item.text("query")?.let { SuperfudisUnresolvedItem(it, item.text("reason")) }
        }
    )
}

private fun offer(row: JsonObject): SuperfudisOffer? {
    val retailer = row.text("retailer") ?: return null
    val product = product(row) ?: return null
    return SuperfudisOffer(
        retailer, product, row.decimal("price_eur")?.takeIf { it.signum() >= 0 },
        row.decimal("eur_per_unit"), row.text("comparison_unit"), row.text("match_type"), row.text("price_date")
    )
}

private fun product(row: JsonObject): SuperfudisProduct? = row.text("name")?.let { name ->
    val ean = (row["ean"] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
    val units = runCatching { row.decimal("n_units")?.intValueExact() }.getOrNull()
    SuperfudisProduct(
        name, row.text("brand"), ean, row.text("format"), units, row.decimal("volume_total"),
        row.decimal("unit_size_value"), row.text("unit_size_unit")
    )
}

private fun JsonArray?.objects(): List<JsonObject> = orEmpty().filterIsInstance<JsonObject>()

private fun JsonObject.text(key: String): String? = this[key].asText()

private fun JsonElement?.asText(): String? = (this as? JsonPrimitive)
    ?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank)

private fun JsonObject.decimal(key: String): BigDecimal? =
    (this[key] as? JsonPrimitive)?.content?.toBigDecimalOrNull()
