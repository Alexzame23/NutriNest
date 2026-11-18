package com.zame.nutrinest.domain.prices

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@JvmInline
value class CommercialId(val value: String) {
    init { require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._~-]{0,127}"))) }
}

data class ProviderRecord(val sourceId: CommercialId, val recordKey: String) {
    init { require(recordKey.isNotBlank() && recordKey.length <= 256) }
}

data class Retailer(val id: CommercialId, val origin: ProviderRecord, val name: String) {
    init { require(name.isNotBlank()) }
}

data class Store(
    val id: CommercialId,
    val origin: ProviderRecord,
    val retailerId: CommercialId,
    val name: String,
    val postalCode: String? = null,
    val osmRef: String? = null
) {
    init {
        require(name.isNotBlank())
        require(postalCode == null || postalCode.matches(Regex("[0-9]{5}")))
        require(osmRef == null || osmRef.matches(Regex("(node|way|relation)/[1-9][0-9]{0,15}")))
    }
}

data class PriceZone(
    val id: CommercialId,
    val origin: ProviderRecord,
    val retailerId: CommercialId,
    val name: String
) {
    init { require(name.isNotBlank()) }
}

data class CommercialProduct(
    val id: CommercialId,
    val origin: ProviderRecord,
    val name: String,
    val brand: String? = null,
    val variant: String? = null,
    val attributes: Map<String, String> = emptyMap()
) {
    init {
        require(name.isNotBlank())
        require(attributes.keys.none(String::isBlank))
    }
}

@JvmInline
value class Gtin(val original: String) {
    init {
        require(original.length in listOf(8, 12, 13, 14) && original.all { it in '0'..'9' })
        require(original.any { it != '0' })
        val sum = original.reversed().mapIndexed { index, digit ->
            digit.digitToInt() * if (index % 2 == 0) 1 else 3
        }.sum()
        require(sum % 10 == 0)
    }

    val normalized: String get() = original.padStart(14, '0')
}

enum class ContentUnit { G, ML, UNIT }
enum class SaleUnit { PACKAGE, KG }
enum class WeightMode { FIXED, VARIABLE }

/** Declared total net content of the sellable pack, including all its inner units. */
data class NetContent(val amount: BigDecimal, val unit: ContentUnit) {
    init {
        require(amount.signum() > 0 && amount < BigDecimal("1000000000") && amount.scale() in 0..3)
        require(unit != ContentUnit.UNIT || amount.stripTrailingZeros().scale() <= 0)
    }
}

data class ProductPackage(
    val id: CommercialId,
    val origin: ProviderRecord,
    val productId: CommercialId,
    val gtin: Gtin?,
    val weightMode: WeightMode,
    val saleUnit: SaleUnit,
    val netContent: NetContent?,
    val unitsPerPackage: Int?
) {
    init {
        when (weightMode) {
            WeightMode.FIXED -> require(saleUnit == SaleUnit.PACKAGE && netContent != null &&
                unitsPerPackage != null && unitsPerPackage > 0)
            WeightMode.VARIABLE -> require(saleUnit == SaleUnit.KG &&
                netContent == null && unitsPerPackage == null)
        }
    }
}

enum class SourceKind { OPEN_DATA, LICENSED_FEED, AUTHORIZED_IMPORT }

data class PriceSource(
    val id: CommercialId,
    val name: String,
    val kind: SourceKind,
    val licenseRef: String,
    val attribution: String
) {
    init { require(listOf(name, licenseRef, attribution).none(String::isBlank)) }
}

/** Confidence is an evidence classification; source authorization is a separate decision. */
enum class Confidence { LOW, MEDIUM, HIGH }
enum class MatchKind { EXACT, ALTERNATIVE }

data class IngredientProductMatch(
    val origin: ProviderRecord,
    val ingredientKey: CommercialId,
    val mappingVersion: Int,
    val packageId: CommercialId,
    val kind: MatchKind,
    val confidence: Confidence,
    val edibleMass: ReviewedEdibleMass? = null
) {
    init {
        require(mappingVersion > 0)
        require(edibleMass == null || kind == MatchKind.EXACT)
    }
}

/** Reviewed edible grams per complete pack or per kilogram billed for variable weight. */
data class ReviewedEdibleMass(val grams: BigDecimal, val sourceRef: String) {
    init {
        require(grams.signum() > 0 && grams < BigDecimal("1000000000") && grams.scale() in 0..3)
        require(sourceRef.isNotBlank())
    }
}

sealed interface ObservationScope {
    data class AtStore(val storeId: CommercialId) : ObservationScope
    data class InZone(val zoneId: CommercialId) : ObservationScope
    data class Online(val zoneId: CommercialId?) : ObservationScope
    data object Unknown : ObservationScope
}

data class ObservationRecord(
    val origin: ProviderRecord,
    val revision: Int,
    val retailerId: CommercialId,
    val packageId: CommercialId,
    val scope: ObservationScope,
    val observedOn: LocalDate,
    val observedAt: Instant?,
    val confidence: Confidence
) {
    init { require(revision > 0) }
}

enum class Currency { EUR }

data class Money(val amount: BigDecimal, val currency: Currency) {
    init {
        require(amount.signum() >= 0 && amount < BigDecimal("1000000000") && amount.scale() in 0..4)
    }
}

enum class PriceKind { REGULAR, PROMOTIONAL }
enum class PromotionEligibility { PUBLIC, LOYALTY, CONDITIONAL, UNKNOWN }

data class PriceObservation(
    val record: ObservationRecord,
    val money: Money,
    val per: SaleUnit,
    val kind: PriceKind,
    val eligibility: PromotionEligibility?,
    val validUntil: LocalDate?
) {
    init {
        require((kind == PriceKind.PROMOTIONAL) == (eligibility != null))
        require(validUntil == null ||
            (kind == PriceKind.PROMOTIONAL && !validUntil.isBefore(record.observedOn)))
    }
}

enum class StockState { IN_STOCK, OUT_OF_STOCK, LIMITED, UNKNOWN }

data class AvailabilityObservation(val record: ObservationRecord, val state: StockState)

enum class SyncState { NEVER_SYNCED, RUNNING, SUCCEEDED, FAILED }

data class PriceSyncStatus(
    val sourceId: CommercialId,
    val regionId: CommercialId,
    val state: SyncState,
    val lastAttemptAt: Instant?,
    val lastSuccessAt: Instant?
) {
    init {
        require((state == SyncState.NEVER_SYNCED) == (lastAttemptAt == null))
        require(lastSuccessAt == null || (lastAttemptAt != null && lastSuccessAt <= lastAttemptAt))
        require(state != SyncState.SUCCEEDED || lastSuccessAt == lastAttemptAt)
    }
}
