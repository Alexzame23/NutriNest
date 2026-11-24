package com.zame.nutrinest.data.prices.openprices

import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.CommercialProduct
import com.zame.nutrinest.domain.prices.Confidence
import com.zame.nutrinest.domain.prices.Currency
import com.zame.nutrinest.domain.prices.Gtin
import com.zame.nutrinest.domain.prices.Money
import com.zame.nutrinest.domain.prices.ObservationRecord
import com.zame.nutrinest.domain.prices.ObservationScope
import com.zame.nutrinest.domain.prices.PriceKind
import com.zame.nutrinest.domain.prices.PriceObservation
import com.zame.nutrinest.domain.prices.PriceSource
import com.zame.nutrinest.domain.prices.PromotionEligibility
import com.zame.nutrinest.domain.prices.ProviderRecord
import com.zame.nutrinest.domain.prices.SaleUnit
import com.zame.nutrinest.domain.prices.SourceKind
import com.zame.nutrinest.domain.prices.Store
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val OPEN_PRICES = PriceSource(
    CommercialId("open-prices"),
    "Open Prices",
    SourceKind.OPEN_DATA,
    "ODbL-1.0",
    "Open Prices by Open Food Facts contributors (https://prices.openfoodfacts.org)"
)

internal data class OpenPricesLocation(
    val id: Long,
    val type: String?,
    val osmRef: String?,
    val city: String?,
    val countryCode: String?,
    val store: Store?
) {
    companion object {
        private val osmReference = Regex("(node|way|relation)/[1-9][0-9]{0,15}")
        private val spanishPostalCode = Regex("[0-9]{5}")

        fun fromJson(location: JsonObject): OpenPricesLocation? {
            val id = location.positiveId("id") ?: return null
            val type = location.text("type")
            val osmId = location.positiveId("osm_id")
            val osmType = location.text("osm_type")?.lowercase()
            val osmRef = "$osmType/$osmId".takeIf(osmReference::matches)
            val countryCode = location.text("osm_address_country_code")?.uppercase()
            val postalCode = location.text("osm_address_postcode")
                ?.takeIf { countryCode == "ES" && spanishPostalCode.matches(it) }
            val brand = location.text("osm_brand")
            val store = if (type == "OSM" && osmRef != null && location.text("osm_tag_key") == "shop" && brand != null) {
                Store(
                    CommercialId("open-prices-store.$id"),
                    ProviderRecord(OPEN_PRICES.id, "locations/$id"),
                    CommercialId("open-prices-retailer-at.$id"),
                    location.text("osm_name") ?: brand,
                    postalCode,
                    osmRef
                )
            } else null
            return OpenPricesLocation(id, type, osmRef, location.text("osm_address_city"), countryCode, store)
        }
    }
}

/** One upstream row. The domain projection is a candidate and has no published package or retailer catalog. */
internal data class OpenPricesObservation(
    val origin: ProviderRecord,
    val source: PriceSource,
    val proofId: Long?,
    val proofType: String?,
    val locationId: Long?,
    val location: OpenPricesLocation?,
    val productCode: String?,
    val gtin: Gtin?,
    val product: CommercialProduct?,
    val categoryTag: String?,
    val date: LocalDate?,
    val amount: BigDecimal?,
    val currency: String?,
    val pricePer: String?,
    val discounted: Boolean?,
    val priceWithoutDiscount: BigDecimal?,
    val observation: PriceObservation?
) {
    companion object {
        private val productCodePattern = Regex("[A-Za-z0-9]{1,100}")
        private val proofTypes = setOf("PRICE_TAG", "RECEIPT", "GDPR_REQUEST", "SHOP_IMPORT")

        fun fromJson(row: JsonObject, nearby: Map<Long, OpenPricesLocation>): OpenPricesObservation? {
            val id = row.positiveId("id") ?: return null
            val origin = ProviderRecord(OPEN_PRICES.id, "prices/$id")
            val embeddedLocation = row.obj("location")?.let(OpenPricesLocation::fromJson)
            val locationId = row.positiveId("location_id") ?: embeddedLocation?.id
            val location = embeddedLocation?.takeIf { it.id == locationId } ?: nearby[locationId]
            val proof = row.obj("proof")
            val proofId = row.positiveId("proof_id") ?: proof?.positiveId("id")
            val type = row.text("type")
            val embeddedProduct = row.obj("product")
            val code = (row.text("product_code") ?: embeddedProduct?.text("code"))
                ?.takeIf(productCodePattern::matches)
            val gtin = code?.let { runCatching { Gtin(it) }.getOrNull() }
            val productId = row.positiveId("product_id") ?: embeddedProduct?.positiveId("id")
            val product = if (type == "PRODUCT") product(code, productId, row.text("product_name"), embeddedProduct)
                else null
            val date = try {
                row.text("date")?.let(LocalDate::parse)
            } catch (_: DateTimeParseException) {
                null
            }
            val amount = row.decimal("price")
            val currency = row.text("currency")
            val pricePer = row.text("price_per")
            val discounted = (row["price_is_discounted"] as? JsonPrimitive)
                ?.takeUnless { it.isString }?.booleanOrNull
            val observation = project(
                origin, product, location?.store, date, amount, currency, pricePer, discounted, row.text("discount_type")
            )
            return OpenPricesObservation(
                origin, OPEN_PRICES, proofId, proof?.text("type")?.takeIf { it in proofTypes },
                locationId, location, code, gtin, product, row.text("category_tag"), date, amount, currency,
                pricePer, discounted, row.decimal("price_without_discount"), observation
            )
        }

        private fun product(code: String?, id: Long?, name: String?, details: JsonObject?): CommercialProduct? {
            val key = code ?: id?.let { "id.$it" } ?: return null
            return CommercialProduct(
                CommercialId("open-prices-product.$key"),
                ProviderRecord(OPEN_PRICES.id, "products/$key"),
                details?.text("product_name") ?: name ?: code ?: "Open Prices #$id",
                details?.text("brands")
            )
        }

        private fun project(
            origin: ProviderRecord,
            product: CommercialProduct?,
            store: Store?,
            date: LocalDate?,
            amount: BigDecimal?,
            currency: String?,
            pricePer: String?,
            discounted: Boolean?,
            discountType: String?
        ): PriceObservation? {
            // Open Prices defines an unset price_per for PRODUCT. CATEGORY rates stay in the sample.
            if (product == null || store == null || date == null || amount == null ||
                currency != Currency.EUR.name || pricePer != null || discounted == null
            ) return null
            val money = runCatching { Money(amount, Currency.EUR) }.getOrNull() ?: return null
            val eligibility = when {
                !discounted -> null
                discountType == "LOYALTY_PROGRAM" -> PromotionEligibility.LOYALTY
                discountType == "QUANTITY" -> PromotionEligibility.CONDITIONAL
                else -> PromotionEligibility.UNKNOWN
            }
            return PriceObservation(
                ObservationRecord(
                    origin, 1, store.retailerId, CommercialId("${product.id.value}.pack"),
                    ObservationScope.AtStore(store.id), date, null, Confidence.LOW
                ),
                money, SaleUnit.PACKAGE, if (discounted) PriceKind.PROMOTIONAL else PriceKind.REGULAR,
                eligibility, null
            )
        }
    }
}

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)
    ?.takeUnless { it is JsonNull }?.content?.takeIf(String::isNotBlank)

internal fun JsonObject.positiveId(key: String): Long? = text(key)?.toLongOrNull()?.takeIf { it > 0 }

private fun JsonObject.decimal(key: String): BigDecimal? = text(key)?.toBigDecimalOrNull()
