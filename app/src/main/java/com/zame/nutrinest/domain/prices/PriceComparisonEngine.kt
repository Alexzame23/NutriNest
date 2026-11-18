package com.zame.nutrinest.domain.prices

import com.zame.nutrinest.domain.nutrition.MassBasis
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZoneOffset

object PriceComparisonEngine {
    private val thousand = BigDecimal("1000")

    fun compare(
        demand: PriceDemand, snapshot: PriceSnapshot?, policy: ComparisonPolicy,
        massContents: List<VerifiedMassContent> = emptyList(),
        promotions: List<VerifiedPromotion> = emptyList(),
        weighedGrams: Map<PriceObservation, BigDecimal> = emptyMap()
    ): ComparisonResult {
        fun unavailable(error: ComparisonError) = ComparisonResult(emptyList(), listOf(ExcludedPrice(error)))
        val quantity = demand.quantity
        if (!quantity.amount.isFinite() || quantity.amount <= 0) return unavailable(ComparisonError.INVALID_QUANTITY)
        if (snapshot == null) return unavailable(ComparisonError.NO_SNAPSHOT)
        if (policy.now < snapshot.generatedAt || policy.now >= snapshot.expiresAt)
            return unavailable(ComparisonError.SNAPSHOT_NOT_CURRENT)
        val matches = snapshot.matches.filter { it.ingredientKey == demand.ingredientKey }.groupBy { it.packageId }
        if (matches.isEmpty()) return unavailable(ComparisonError.NO_MATCH)
        val packs = snapshot.packages.associateBy { it.id }
        val prices = snapshot.prices.groupBy { it.record.packageId }
        val options = mutableListOf<ComparedPrice>()
        val excluded = mutableListOf<ExcludedPrice>()
        for ((id, mappings) in matches.entries.sortedBy { it.key.value }) {
            val match = mappings.singleOrNull()
            val observations = prices[id].orEmpty()
            val error = when {
                match == null -> ComparisonError.AMBIGUOUS_MATCH
                match.kind != MatchKind.EXACT -> ComparisonError.SUBSTITUTE
                observations.isEmpty() -> ComparisonError.NO_PRICE
                else -> null
            }
            if (error != null || match == null) {
                excluded += ExcludedPrice(requireNotNull(error), id)
                continue
            }
            val pack = packs.getValue(id)
            for (price in observations.sortedWith(compareBy({ it.record.origin.sourceId.value }, { it.record.origin.recordKey }))) {
                val terms = promotions.filter { it.observation == price }
                val promo = terms.singleOrNull()
                val ineligible = eligibility(price, policy, promo)
                if (ineligible != null) {
                    excluded += ExcludedPrice(ineligible, id, price.record)
                    continue
                }
                val unit = pack.netContent?.unit ?: ContentUnit.G
                val required = baseAmount(demand, unit)
                if (required == null) {
                    excluded += ExcludedPrice(ComparisonError.INCOMPATIBLE_UNIT, id, price.record)
                    continue
                }
                val variable = pack.weightMode == WeightMode.VARIABLE
                val declared = pack.netContent?.amount ?: thousand
                var capacity = declared
                var evidence: VerifiedMassContent? = null
                if (unit == ContentUnit.G && (quantity.massBasis != MassBasis.NET || variable)) {
                    evidence = massContents.singleOrNull { it.pack == pack && it.match == match && it.basis == quantity.massBasis }
                    if (evidence == null || (variable && evidence.basis == MassBasis.NET && evidence.grams > declared) ||
                        (!variable && evidence.basis == MassBasis.GROSS && evidence.grams < declared) ||
                        (evidence.basis in listOf(MassBasis.EDIBLE, MassBasis.DRAINED) && evidence.grams > declared)) {
                        excluded += ExcludedPrice(ComparisonError.UNVERIFIED_BASIS, id, price.record)
                        continue
                    }
                    capacity = evidence.grams
                }
                val basis = quantity.massBasis.takeIf { unit == ContentUnit.G }
                val denominator = when (unit) {
                    ContentUnit.G -> PriceDenominator.KG
                    ContentUnit.ML -> PriceDenominator.L
                    ContentUnit.UNIT -> PriceDenominator.UNIT
                }
                val scale = if (unit == ContentUnit.UNIT) BigDecimal.ONE else thousand
                val amount = price.money.amount
                if (variable) {
                    val weight = weighedGrams[price]
                    if (weight != null && (weight.signum() <= 0 || weight * capacity < required * thousand)) {
                        excluded += ExcludedPrice(ComparisonError.INVALID_WEIGHING, id, price.record)
                        continue
                    }
                    options += ComparedPrice(match, pack, price, denominator, basis, EuroRatio(amount * scale, capacity),
                        EuroRatio(amount * required, capacity), null, null,
                        weight?.let { EuroRatio(amount * it, thousand).decimal(2) }, evidence, promo, weight)
                } else {
                    val count = required.divide(capacity, 0, RoundingMode.CEILING)
                    val take = (promo?.take ?: 1).toBigDecimal()
                    val paid = count.divideToIntegralValue(take) * (promo?.pay ?: 1).toBigDecimal() + count.remainder(take)
                    val line = amount * paid
                    val contents = count * capacity
                    options += ComparedPrice(match, pack, price, denominator, basis, EuroRatio(line * scale, contents),
                        EuroRatio(line * required, contents), count.toBigIntegerExact(), price.money,
                        line.setScale(2, RoundingMode.HALF_UP), evidence, promo, null)
                }
            }
        }
        val order = compareBy<ComparedPrice> { it.checkoutEur == null }.thenBy { it.checkoutEur }
            .thenBy { it.proportional }.thenBy { it.pack.id.value }
            .thenBy { it.observation.record.origin.sourceId.value }.thenBy { it.observation.record.origin.recordKey }
        return ComparisonResult(options.sortedWith(order), excluded)
    }

    private fun baseAmount(demand: PriceDemand, unit: ContentUnit): BigDecimal? {
        val q = demand.quantity
        val allowed = when (unit) {
            ContentUnit.G -> q.unit in listOf(UnitDefinition.GRAM, UnitDefinition.KILOGRAM, UnitDefinition.OUNCE, UnitDefinition.POUND)
            ContentUnit.ML -> q.unit in listOf(UnitDefinition.MILLILITRE, UnitDefinition.LITRE)
            ContentUnit.UNIT -> q.unit == UnitDefinition.count(demand.ingredientKey.value)
        }
        return if (allowed) BigDecimal.valueOf(q.amount) * BigDecimal.valueOf(q.unit.baseMultiplier) else null
    }

    private fun eligibility(price: PriceObservation, policy: ComparisonPolicy, terms: VerifiedPromotion?): ComparisonError? {
        val record = price.record
        val observed = record.observedAt ?: record.observedOn.atStartOfDay(ZoneOffset.UTC).toInstant()
        if (record.scope == ObservationScope.Unknown) return ComparisonError.UNKNOWN_SCOPE
        if (observed > policy.now) return ComparisonError.FUTURE_PRICE
        if (observed <= policy.now.minus(policy.freshness)) return ComparisonError.STALE_PRICE
        if (price.kind == PriceKind.REGULAR)
            return if (terms == null) null else ComparisonError.UNVERIFIED_PROMOTION
        val until = price.validUntil ?: return ComparisonError.UNVERIFIED_PROMOTION
        if (policy.now.atZone(policy.saleZone).toLocalDate() > until) return ComparisonError.EXPIRED_PROMOTION
        if (terms == null || (price.per == SaleUnit.KG && terms.take != 1))
            return ComparisonError.UNVERIFIED_PROMOTION
        return when (price.eligibility) {
            PromotionEligibility.PUBLIC -> null
            PromotionEligibility.LOYALTY -> if (record.retailerId in policy.loyaltyRetailers) null else ComparisonError.INELIGIBLE_PROMOTION
            PromotionEligibility.CONDITIONAL -> if (terms.conditionsSatisfied) null else ComparisonError.INELIGIBLE_PROMOTION
            else -> ComparisonError.UNVERIFIED_PROMOTION
        }
    }
}
