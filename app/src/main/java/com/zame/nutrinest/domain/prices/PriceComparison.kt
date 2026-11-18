package com.zame.nutrinest.domain.prices

import com.zame.nutrinest.domain.nutrition.ConversionResult
import com.zame.nutrinest.domain.nutrition.FoodConversionFactor
import com.zame.nutrinest.domain.nutrition.FoodQuantityConversions
import com.zame.nutrinest.domain.nutrition.IngredientConversionContext
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import com.zame.nutrinest.domain.nutrition.MassBasis
import com.zame.nutrinest.domain.nutrition.Quantity
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A ratio of decimal quantities. Repeating rates remain exact through comparison and allocation. */
data class EuroRatio(val numerator: BigDecimal, val denominator: BigDecimal) : Comparable<EuroRatio> {
    init { require(numerator.signum() >= 0 && denominator.signum() > 0) }
    override fun compareTo(other: EuroRatio) =
        (numerator * other.denominator).compareTo(other.numerator * denominator)
    fun decimal(scale: Int): BigDecimal = numerator.divide(denominator, scale, RoundingMode.HALF_UP)
}

enum class PriceDenominator { KG, L, UNIT }

data class PriceDemand(val ingredientKey: CommercialId, val quantity: Quantity)

/** Reviewed amount on the requested basis per entire package or per 1 kg billed. */
data class VerifiedMassContent(
    val pack: ProductPackage, val match: IngredientProductMatch, val basis: MassBasis,
    val grams: BigDecimal, val sourceRef: String, val yieldFactor: FoodConversionFactor.EdibleYield? = null
) {
    init { require(grams.signum() > 0 && sourceRef.isNotBlank() && basis != MassBasis.UNKNOWN) }

    companion object {
        /** Caller resolves the exact match to its local ingredient and selects package-applicable evidence. */
        fun fromNetYield(pack: ProductPackage, match: IngredientProductMatch, context: IngredientConversionContext): VerifiedMassContent? {
            val net = pack.netContent ?: return null
            if (pack.id != match.packageId || match.kind != MatchKind.EXACT || net.unit != ContentUnit.G) return null
            val unitNet = Quantity(1.0, UnitDefinition.GRAM, MassBasis.NET)
            val validated = FoodQuantityConversions.toGrams(unitNet, context) as? ConversionResult.Success ?: return null
            val factor = validated.appliedFactors.singleOrNull() as? FoodConversionFactor.EdibleYield ?: return null
            return VerifiedMassContent(pack, match, MassBasis.EDIBLE, net.amount * BigDecimal.valueOf(factor.edibleFraction),
                factor.evidence.source, factor)
        }
    }
}

/** The observation amount is the price of ONE paid sale unit. Groups repeat on this SKU only. */
data class VerifiedPromotion(
    val observation: PriceObservation, val sourceRef: String, val take: Int = 1, val pay: Int = 1,
    val conditionsSatisfied: Boolean = false
) {
    init { require(sourceRef.isNotBlank() && take > 0 && pay in 1..take) }
}

data class ComparisonPolicy(
    val now: Instant, val saleZone: ZoneId, val freshness: Duration = Duration.ofDays(30),
    val loyaltyRetailers: Set<CommercialId> = emptySet()
) {
    init { require(!freshness.isNegative && !freshness.isZero && freshness <= Duration.ofDays(30)) }
}

enum class ComparisonError {
    INVALID_QUANTITY, NO_SNAPSHOT, SNAPSHOT_NOT_CURRENT, NO_MATCH, AMBIGUOUS_MATCH, SUBSTITUTE,
    NO_PRICE, UNKNOWN_SCOPE, FUTURE_PRICE, STALE_PRICE, EXPIRED_PROMOTION, UNVERIFIED_PROMOTION,
    INELIGIBLE_PROMOTION, INCOMPATIBLE_UNIT, UNVERIFIED_BASIS, INVALID_WEIGHING
}

data class ExcludedPrice(
    val error: ComparisonError, val packageId: CommercialId? = null, val observation: ObservationRecord? = null
)

data class ComparedPrice(
    val match: IngredientProductMatch, val pack: ProductPackage, val observation: PriceObservation,
    val denominator: PriceDenominator, val massBasis: MassBasis?, val normalized: EuroRatio,
    val proportional: EuroRatio, val packageCount: BigInteger?, val packagePrice: Money?,
    val checkoutEur: BigDecimal?, val massEvidence: VerifiedMassContent?, val promotion: VerifiedPromotion?,
    val weighedGrams: BigDecimal?
)

data class ComparisonResult(val options: List<ComparedPrice>, val excluded: List<ExcludedPrice>) {
    val comparablePackages get() = options.map { it.pack.id }.distinct().size
    val evaluatedPackages get() = (options.map { it.pack.id } + excluded.mapNotNull { it.packageId }).distinct().size
}
