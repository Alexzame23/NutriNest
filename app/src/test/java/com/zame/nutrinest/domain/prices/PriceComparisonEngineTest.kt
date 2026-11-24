package com.zame.nutrinest.domain.prices

import com.zame.nutrinest.domain.nutrition.ConversionConfidence
import com.zame.nutrinest.domain.nutrition.ConversionEvidence
import com.zame.nutrinest.domain.nutrition.EvidenceStatus
import com.zame.nutrinest.domain.nutrition.FoodConversionFactor
import com.zame.nutrinest.domain.nutrition.IngredientConversionContext
import com.zame.nutrinest.domain.nutrition.MassBasis
import com.zame.nutrinest.domain.nutrition.Quantity
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val key = CommercialId("fixture")
private val origin = ProviderRecord(key, "fixture")
private val now = Instant.parse("2026-11-18T12:00:00Z")
private val today = LocalDate.parse("2026-11-18")
private val policy = ComparisonPolicy(now, ZoneId.of("Europe/Madrid"))
private val match = IngredientProductMatch(origin, key, 1, key, MatchKind.EXACT, Confidence.HIGH)

private fun sample(unit: ContentUnit = ContentUnit.G, size: String = "500", amount: String = "1.00", inner: Int = 1): PriceSnapshot {
    val pack = ProductPackage(key, origin, key, null, WeightMode.FIXED, SaleUnit.PACKAGE, NetContent(BigDecimal(size), unit), inner)
    val record = ObservationRecord(origin, 1, key, key, ObservationScope.AtStore(key), today, null, Confidence.HIGH)
    val price = PriceObservation(record, Money(BigDecimal(amount), Currency.EUR), SaleUnit.PACKAGE, PriceKind.REGULAR, null, null)
    return PriceSnapshot(1, key, 1, now.minusSeconds(1), now.plusSeconds(3600),
        listOf(PriceSource(key, "Simulado", SourceKind.AUTHORIZED_IMPORT, "fixture", "fixture")),
        listOf(Retailer(key, origin, "Simulado")), listOf(Store(key, origin, key, "Simulada")), emptyList(),
        listOf(CommercialProduct(key, origin, "Simulado")), listOf(pack), listOf(match), listOf(price), emptyList())
}

@RunWith(Parameterized::class)
class FixedPriceComparisonTest(
    private val name: String, private val unit: ContentUnit, private val size: String, private val amount: String,
    private val need: Double, private val inner: Int, private val packs: Int, private val rate: String,
    private val proportional: String, private val checkout: String
) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun examples(): List<Array<Any>> = listOf(
            arrayOf("Arroz 300 g", ContentUnit.G, "1000", "2.40", 300.0, 1, 1, "2.4000", "0.72", "2.40"),
            arrayOf("Dos bolsas de arroz", ContentUnit.G, "500", "1.30", 800.0, 1, 2, "2.6000", "2.08", "2.60"),
            arrayOf("Seis huevos", ContentUnit.UNIT, "12", "3.00", 6.0, 12, 1, "0.2500", "1.50", "3.00"),
            arrayOf("Leche 1 L", ContentUnit.ML, "1000", "1.10", 1500.0, 1, 2, "1.1000", "1.65", "2.20"),
            arrayOf("Leche 1,5 L", ContentUnit.ML, "1500", "1.50", 1500.0, 1, 1, "1.0000", "1.50", "1.50"),
            arrayOf("Aceite", ContentUnit.ML, "750", "5.00", 75.0, 1, 1, "6.6667", "0.50", "5.00"),
            arrayOf("Pack 2 x 500", ContentUnit.G, "1000", "1.9950", 1001.0, 2, 2, "1.9950", "2.00", "3.99"),
            arrayOf("Medio céntimo", ContentUnit.G, "100", "1.0050", 100.0, 1, 1, "10.0500", "1.01", "1.01"),
            arrayOf("Precio cero observado", ContentUnit.G, "100", "0", 100.0, 1, 1, "0.0000", "0.00", "0.00")
        )
    }
    @Test fun compare() {
        val measure = when (unit) {
            ContentUnit.G -> UnitDefinition.GRAM
            ContentUnit.ML -> UnitDefinition.MILLILITRE
            ContentUnit.UNIT -> UnitDefinition.count(key.value)
        }
        val result = PriceComparisonEngine.compare(PriceDemand(key, Quantity(need, measure, MassBasis.NET)),
            sample(unit, size, amount, inner), policy)
        val option = result.options.single()
        assertEquals(BigDecimal(amount), option.packagePrice!!.amount)
        assertEquals(packs.toBigInteger(), option.packageCount)
        assertEquals(rate, option.normalized.decimal(4).toPlainString())
        assertEquals(proportional, option.proportional.decimal(2).toPlainString())
        assertEquals(checkout, option.checkoutEur!!.toPlainString())
        assertEquals(1, result.comparablePackages)
    }
}

class PriceComparisonEngineTest {
    private val snap = sample()
    private val pack = snap.packages.single()
    private val price = snap.prices.single()
    private fun demand(n: Double = 300.0, basis: MassBasis = MassBasis.NET) = PriceDemand(key, Quantity(n, UnitDefinition.GRAM, basis))

    @Test fun promotionGroupsRemaindersAndLoyalty() {
        val promo = price.copy(kind = PriceKind.PROMOTIONAL, eligibility = PromotionEligibility.LOYALTY, validUntil = today)
        val snapshot = snap.copy(prices = listOf(promo))
        val terms = VerifiedPromotion(promo, "simulated-terms", 3, 2)
        val member = policy.copy(loyaltyRetailers = setOf(key))
        fun run(n: Int, p: ComparisonPolicy) = PriceComparisonEngine.compare(demand(500.0 * n), snapshot, p, promotions = listOf(terms))
        assertEquals(ComparisonError.INELIGIBLE_PROMOTION, run(3, policy).excluded.single().error)
        assertEquals(ComparisonError.UNVERIFIED_PROMOTION, PriceComparisonEngine.compare(demand(), snapshot, member).excluded.single().error)
        for ((count, paid) in listOf(2 to 2, 3 to 2, 4 to 3, 6 to 4)) {
            assertEquals(paid.toBigDecimal().setScale(2), run(count, member).options.single().checkoutEur)
        }
        assertEquals("1.3333", run(3, member).options.single().normalized.decimal(4).toPlainString())
        val partial = PriceComparisonEngine.compare(demand(1250.0), snapshot, member, promotions = listOf(terms)).options.single()
        assertEquals("1.67", partial.proportional.decimal(2).toPlainString())
        assertEquals("2.00", partial.checkoutEur.toString())
    }

    @Test fun freshnessCompatibilityAndCoverage() {
        fun error(snapshot: PriceSnapshot?, d: PriceDemand = demand()) =
            PriceComparisonEngine.compare(d, snapshot, policy).also { assertTrue(it.options.isEmpty()) }.excluded.single().error
        assertEquals(ComparisonError.NO_SNAPSHOT, error(null))
        assertEquals(ComparisonError.NO_MATCH, error(snap.copy(matches = emptyList())))
        assertEquals(ComparisonError.NO_PRICE, error(snap.copy(prices = emptyList())))
        assertEquals(ComparisonError.SNAPSHOT_NOT_CURRENT, error(snap.copy(expiresAt = now)))
        assertEquals(ComparisonError.SUBSTITUTE, error(snap.copy(matches = listOf(match.copy(kind = MatchKind.ALTERNATIVE)))))
        assertEquals(ComparisonError.STALE_PRICE, error(snap.copy(prices = listOf(price.copy(record =
            price.record.copy(observedAt = now.minusSeconds(30L * 86400), observedOn = today.minusDays(30)))))))
        assertEquals(ComparisonError.INCOMPATIBLE_UNIT, error(snap, PriceDemand(key, Quantity(1.0, UnitDefinition.LITRE))))
        assertEquals(ComparisonError.UNVERIFIED_BASIS, error(snap, demand(basis = MassBasis.EDIBLE)))
        val expired = price.copy(record = price.record.copy(observedOn = today.minusDays(1)), kind = PriceKind.PROMOTIONAL,
            validUntil = today.minusDays(1), eligibility = PromotionEligibility.PUBLIC)
        assertEquals(ComparisonError.EXPIRED_PROMOTION, error(snap.copy(prices = listOf(expired))))
    }

    @Test fun sourceTtlCanExpireOneSourceBeforeThePublication() {
        val old = price.copy(record = price.record.copy(
            observedAt = now.minusSeconds(2 * 86400), observedOn = today.minusDays(2)
        ))
        val snapshot = snap.copy(prices = listOf(old))
        assertTrue(PriceComparisonEngine.compare(demand(), snapshot, policy).options.isNotEmpty())
        val shorter = policy.copy(freshnessBySource = mapOf(old.record.origin.sourceId to java.time.Duration.ofDays(1)))
        val result = PriceComparisonEngine.compare(demand(), snapshot, shorter)
        assertTrue(result.options.isEmpty())
        assertEquals(ComparisonError.STALE_PRICE, result.excluded.single().error)
    }

    @Test fun ambiguousMappingsCannotPriceAnIngredientRegardlessOfMappingOrder() {
        val competing = match.copy(origin = ProviderRecord(key, "competing"), mappingVersion = 2)
        val unrelated = match.copy(origin = ProviderRecord(key, "unrelated"), ingredientKey = CommercialId("other"))
        for (mappings in listOf(listOf(match, competing), listOf(competing, match))) {
            val result = PriceComparisonEngine.compare(demand(), snap.copy(matches = mappings), policy)
            assertTrue(result.options.isEmpty())
            assertEquals(listOf(ExcludedPrice(ComparisonError.AMBIGUOUS_MATCH, pack.id)), result.excluded)
        }
        val result = PriceComparisonEngine.compare(demand(), snap.copy(matches = listOf(unrelated, match)), policy)
        assertEquals(match, result.options.single().match)
    }

    @Test fun checkoutOnlyAddsAPackageAboveTheBoundaryAcrossSupportedSalesUnits() {
        data class Case(val contentUnit: ContentUnit, val unit: UnitDefinition, val size: String, val boundary: Double)
        val cases = listOf(
            Case(ContentUnit.G, UnitDefinition.KILOGRAM, "500", 1.0),
            Case(ContentUnit.ML, UnitDefinition.LITRE, "750", 1.5),
            Case(ContentUnit.UNIT, UnitDefinition.count(key.value), "6", 12.0)
        )
        for ((contentUnit, unit, size, boundary) in cases) {
            val snapshot = sample(contentUnit, size, "1.0050")
            for ((amount, count, checkout) in listOf(
                Triple(Math.nextDown(boundary), 2, "2.01"),
                Triple(boundary, 2, "2.01"),
                Triple(Math.nextUp(boundary), 3, "3.02")
            )) {
                val request = PriceDemand(key, Quantity(amount, unit, MassBasis.NET))
                val option = PriceComparisonEngine.compare(request, snapshot, policy).options.single()
                assertEquals(unit.key, count.toBigInteger(), option.packageCount)
                assertEquals(unit.key, checkout, option.checkoutEur.toString())
            }
        }
    }

    @Test fun drainedAndEdibleRequireTheirOwnEvidence() {
        val drained = VerifiedMassContent(pack, match, MassBasis.DRAINED, BigDecimal("200"), "simulated-label")
        fun compare(basis: MassBasis) = PriceComparisonEngine.compare(demand(basis = basis), snap, policy, listOf(drained))
        assertEquals("2.00", compare(MassBasis.DRAINED).options.single().checkoutEur.toString())
        assertEquals("1.50", compare(MassBasis.DRAINED).options.single().proportional.decimal(2).toString())
        assertEquals(ComparisonError.UNVERIFIED_BASIS, compare(MassBasis.EDIBLE).excluded.single().error)
    }

    @Test fun verifiedYieldUsesPhaseFourAndKeepsExactPackBoundary() {
        val evidence = ConversionEvidence("yield", "fixture", "1", ConversionConfidence.HIGH, EvidenceStatus.ACCEPTED, "net pack")
        val factor = FoodConversionFactor.EdibleYield(17, MassBasis.NET, 0.8, evidence)
        val context = IngredientConversionContext(17, listOf(factor))
        val edible = requireNotNull(VerifiedMassContent.fromNetYield(pack, match, context))
        assertEquals(factor, edible.yieldFactor)
        assertNull(VerifiedMassContent.fromNetYield(pack, match, context.copy(ingredientId = 18)))
        assertNull(VerifiedMassContent.fromNetYield(pack, match, context.copy(factors = listOf(factor, factor))))
        val result = PriceComparisonEngine.compare(demand(400.0, MassBasis.EDIBLE), snap, policy, listOf(edible))
        assertEquals("1.00", result.options.single().checkoutEur.toString())
    }

    @Test fun variableWeightNeedsActualWeighingForCheckout() {
        val variable = pack.copy(weightMode = WeightMode.VARIABLE, saleUnit = SaleUnit.KG, netContent = null, unitsPerPackage = null)
        val kgPrice = price.copy(per = SaleUnit.KG, money = Money(BigDecimal("7.90"), Currency.EUR))
        val snapshot = snap.copy(packages = listOf(variable), prices = listOf(kgPrice))
        val edible = VerifiedMassContent(variable, match, MassBasis.EDIBLE, BigDecimal("800"), "simulated-yield")
        fun run(weight: Map<PriceObservation, BigDecimal>) = PriceComparisonEngine.compare(demand(basis = MassBasis.EDIBLE),
            snapshot, policy, listOf(edible), weighedGrams = weight)
        val estimate = run(emptyMap()).options.single()
        assertNull(estimate.checkoutEur)
        assertNull(estimate.packageCount)
        assertEquals("2.96", estimate.proportional.decimal(2).toString())
        assertEquals("3.16", run(mapOf(kgPrice to BigDecimal("400"))).options.single().checkoutEur.toString())
        assertEquals(ComparisonError.INVALID_WEIGHING, run(mapOf(kgPrice to BigDecimal("374"))).excluded.single().error)
    }

    @Test fun compareDifferentFormatsAndReportUnpricedPackage() {
        val milk = sample(ContentUnit.ML, "1000", "1.10")
        val small = milk.packages.single()
        val one = milk.prices.single()
        fun format(id: String, size: String) = small.copy(id = CommercialId(id), origin = ProviderRecord(key, id),
            netContent = NetContent(BigDecimal(size), ContentUnit.ML))
        val large = format("large", "1500")
        val absent = format("unpriced", "2000")
        val formats = listOf(small, large, absent)
        val snapshot = milk.copy(packages = formats, matches = formats.map { match.copy(packageId = it.id, origin = it.origin) },
            prices = listOf(one, one.copy(record = one.record.copy(packageId = large.id, origin = large.origin),
                money = Money(BigDecimal("1.50"), Currency.EUR))))
        val result = PriceComparisonEngine.compare(PriceDemand(key, Quantity(1.5, UnitDefinition.LITRE)), snapshot, policy)
        assertEquals(listOf(large.id, small.id), result.options.map { it.pack.id })
        assertEquals(2, result.comparablePackages)
        assertEquals(3, result.evaluatedPackages)
        assertEquals(ExcludedPrice(ComparisonError.NO_PRICE, absent.id), result.excluded.single())
    }

    @Test fun conditionalDiscountNeedsConfirmationAndRegularPriceRemainsAvailable() {
        val promo = price.copy(record = price.record.copy(origin = ProviderRecord(key, "promo")),
            money = Money(BigDecimal("0.80"), Currency.EUR), kind = PriceKind.PROMOTIONAL,
            validUntil = today, eligibility = PromotionEligibility.CONDITIONAL)
        val snapshot = snap.copy(prices = listOf(promo, price))
        val terms = VerifiedPromotion(promo, "simulated-check")
        fun run(t: VerifiedPromotion) = PriceComparisonEngine.compare(demand(), snapshot, policy, promotions = listOf(t))
        assertEquals(price, run(terms).options.single().observation)
        assertEquals(ComparisonError.INELIGIBLE_PROMOTION, run(terms).excluded.single().error)
        assertEquals(promo, run(terms.copy(conditionsSatisfied = true)).options.first().observation)
    }

    @Test fun orderingIsIndependentOfInputAndUsesUnroundedAllocationForTies() {
        val another = price.copy(record = price.record.copy(origin = ProviderRecord(key, "a")))
        val lower = another.copy(money = Money(BigDecimal("0.9999"), Currency.EUR))
        for (other in listOf(another, lower)) {
            val rows = listOf(price, other)
            for (prices in listOf(rows, rows.reversed())) {
                val result = PriceComparisonEngine.compare(demand(), snap.copy(prices = prices), policy)
                assertEquals(other, result.options.first().observation)
                assertEquals(1, result.evaluatedPackages)
            }
        }
    }
}
