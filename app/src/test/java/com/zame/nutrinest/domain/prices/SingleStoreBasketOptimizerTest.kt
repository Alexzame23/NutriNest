package com.zame.nutrinest.domain.prices

import com.zame.nutrinest.domain.nutrition.MassBasis
import com.zame.nutrinest.domain.nutrition.Quantity
import com.zame.nutrinest.domain.nutrition.UnitDefinition
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class SingleStoreBasketOptimizerTest {
    private val now = Instant.parse("2026-11-19T12:00:00Z")
    private val today = LocalDate.parse("2026-11-19")
    private val policy = ComparisonPolicy(now, ZoneId.of("Europe/Madrid"))
    private val rice = id("rice")
    private val beans = id("beans")

    @Test fun picksTheCheapestCompleteStoreWithMixedPacksAndKeepsPartialCostsSeparate() {
        val fixture = Fixture()
        val first = fixture.store("first", "a")
        val second = fixture.store("second", "b")
        val partial = fixture.store("partial", "c")
        val cheaperElsewhere = fixture.store("excluded", "d")
        val medium = fixture.pack("medium", rice, 600)
        val small = fixture.pack("small", rice, 500)
        val tin = fixture.pack("tin", beans, 400)
        fixture.price(first, medium, "1.10")
        fixture.price(first, small, "0.98")
        fixture.price(first, tin, "1.60")
        fixture.price(second, medium, "1.11")
        fixture.price(second, tin, "1.59")
        fixture.price(partial, medium, "0.01")
        fixture.price(cheaperElsewhere, medium, "0.01")
        fixture.price(cheaperElsewhere, tin, "0.01")

        val demands = mapOf(11L to demand(rice, 1100), 12L to demand(beans, 200))
        val result = fixture.optimize(demands, selected = setOf(id("a"), id("b"), id("c")))
        assertEquals(listOf(first, second, partial), result.alternatives.map { it.store })
        val winner = requireNotNull(result.winner)
        assertEquals(first, winner.store)
        assertEquals("3.68", winner.checkoutEur.toString())
        assertEquals("2.88", winner.proportionalEur.toString())
        assertEquals(listOf(medium, small), winner.lines.first().purchases.map { it.pack })
        assertEquals(listOf("1", "1"), winner.lines.first().purchases.map { it.count.toString() })
        assertEquals("3.81", result.alternatives[1].checkoutEur.toString())
        assertEquals("0.02", result.alternatives.last().checkoutEur.toString())
        assertEquals(listOf(BasketMissingItem(12, setOf(BasketMissingReason.NO_PRICE))), result.alternatives.last().missing)
        assertEquals(2, winner.unconfirmedStockCount)
    }

    @Test fun neverCombinesBranchesZoneOnlyPricesOrOutOfAreaPricesAndDoesNotChoosePartialWinner() {
        val fixture = Fixture()
        val alpha = fixture.store("a", "chain")
        val beta = fixture.store("b", "chain")
        val remote = fixture.store("remote", "chain")
        val ricePack = fixture.pack("rice-pack", rice, 500)
        val beanPack = fixture.pack("bean-pack", beans, 500)
        fixture.price(alpha, ricePack, "1")
        fixture.price(beta, beanPack, "1")
        fixture.price(remote, ricePack, "1")
        fixture.price(remote, beanPack, "1")
        val zone = fixture.zone(alpha)
        fixture.price(alpha, beanPack, "0.10", scope = ObservationScope.InZone(zone.id))
        val demands = mapOf(1L to demand(rice, 500), 2L to demand(beans, 500))

        val result = fixture.optimize(demands, localStores = setOf(alpha.id, beta.id))
        assertNull(result.winner)
        assertEquals(alpha, result.alternatives.single().store)
        assertEquals(1, result.alternatives.single().coveredCount)
        val wrongZone = fixture.optimize(demands, region = id("other"))
        assertNull(wrongZone.alternatives.single().checkoutEur)
        assertEquals(setOf(BasketMissingReason.NO_LOCAL_STORE), wrongZone.alternatives.single().missing.first().reasons)
        assertTrue(fixture.optimize(demands, selected = emptySet()).alternatives.isEmpty())
        assertNull(fixture.optimize(emptyMap()).winner)
    }

    @Test fun aggregatesCompatibleCanonicalRowsAndUsesOneVerifiedPromotionSchedulePerSku() {
        val fixture = Fixture()
        val store = fixture.store("a")
        val pack = fixture.pack("beans", beans, 500)
        fixture.price(store, pack, "1.00")
        val conditional = fixture.price(store, pack, "1.10", eligibility = PromotionEligibility.CONDITIONAL)
        val terms = VerifiedPromotion(conditional, "verified rule", take = 3, pay = 2)
        val demands = mapOf(1L to demand(beans, 650), 2L to
            PriceDemand(beans, Quantity(0.6, UnitDefinition.KILOGRAM, MassBasis.NET)))

        assertEquals("3.00", fixture.optimize(demands, promotions = listOf(terms)).winner?.checkoutEur.toString())
        val allowed = fixture.optimize(demands, promotions = listOf(terms.copy(conditionsSatisfied = true)))
        val winner = requireNotNull(allowed.winner)
        assertEquals("2.20", winner.checkoutEur.toString())
        assertEquals("1.83", winner.proportionalEur.toString())
        assertEquals(listOf(1L, 2L), winner.lines.single().itemIds)
        assertEquals(conditional, winner.lines.single().purchases.single().price)
        assertEquals("3", winner.lines.single().purchases.single().count.toString())
        val smaller = fixture.optimize(mapOf(1L to demand(beans, 800)),
            promotions = listOf(terms.copy(conditionsSatisfied = true)))
        assertEquals("2.00", smaller.winner?.checkoutEur.toString())
    }

    @Test fun requiresFreshPricesEligibleOffersAndReviewedEquivalences() {
        val fixture = Fixture()
        val store = fixture.store("a")
        val stale = fixture.pack("stale", rice, 500)
        val expired = fixture.pack("expired", beans, 500)
        val substitute = fixture.pack("substitute", beans, 500, MatchKind.ALTERNATIVE)
        val loose = fixture.pack("loose", id("loose"), null)
        val zero = fixture.pack("free", id("free"), 500)
        fixture.price(store, stale, "0.10", observed = now.minus(policy.freshness))
        val promo = fixture.price(store, expired, "0.20", observed = now.minusSeconds(86_400),
            eligibility = PromotionEligibility.PUBLIC, validUntil = today.minusDays(1))
        fixture.price(store, substitute, "1.00")
        fixture.price(store, loose, "1.00")
        fixture.price(store, zero, "0.00")
        val demands = mapOf(1L to demand(rice, 250), 2L to demand(beans, 250), 3L to null,
            4L to PriceDemand(id("loose"), Quantity(250.0, UnitDefinition.GRAM)), 5L to demand(id("free"), 500))
        val denied = fixture.optimize(demands, promotions = listOf(VerifiedPromotion(promo, "terms")))
        val basket = denied.alternatives.single()
        assertNull(denied.winner)
        assertEquals("0.00", basket.checkoutEur.toString())
        assertEquals(1, basket.coveredCount)
        assertEquals(setOf(BasketMissingReason.STALE_PRICE), basket.missing[0].reasons)
        assertEquals(setOf(BasketMissingReason.PROMOTION_UNAVAILABLE, BasketMissingReason.NO_EQUIVALENT),
            basket.missing[1].reasons)
        assertEquals(setOf(BasketMissingReason.NO_EQUIVALENT), basket.missing[2].reasons)
        assertEquals(setOf(BasketMissingReason.WEIGHT_REQUIRED), basket.missing[3].reasons)
        val authorized = fixture.optimize(mapOf(2L to demand(beans, 250)),
            substitutions = fixture.matches.filter { it.packageId == substitute.id }.toSet())
        assertEquals(MatchKind.ALTERNATIVE, authorized.winner?.lines?.single()?.purchases?.single()?.match?.kind)
        assertNull(fixture.optimize(mapOf(1L to demand(rice, 250))).alternatives.single().checkoutEur)
    }

    @Test fun resolvesStockAndLatestRegularPriceCautiouslyAndBreaksTiesDeterministically() {
        val fixture = Fixture()
        val alpha = fixture.store("a")
        val zeta = fixture.store("z", "zeta")
        val a = fixture.pack("a", rice, 500)
        val b = fixture.pack("b", rice, 500)
        val conflict = fixture.pack("conflict", rice, 500)
        fixture.price(alpha, a, "0.01", observed = now.minusSeconds(60))
        fixture.price(alpha, a, "1")
        fixture.price(alpha, b, "0.01", observed = now.minusSeconds(60))
        fixture.price(alpha, b, "1")
        fixture.price(zeta, b, "1")
        fixture.price(alpha, conflict, "0.05")
        fixture.price(alpha, conflict, "0.07")
        fixture.stock(alpha, a, StockState.IN_STOCK)
        fixture.stock(alpha, a, StockState.OUT_OF_STOCK)
        fixture.stock(alpha, b, StockState.IN_STOCK, observed = now.minus(policy.freshness))
        fixture.stock(zeta, b, StockState.LIMITED)
        val demands = mapOf(1L to demand(rice, 500))
        val forward = fixture.optimize(demands)
        assertEquals(forward, fixture.optimize(demands, reverse = true))
        assertEquals(alpha, forward.winner?.store)
        assertEquals("1.00", forward.winner?.checkoutEur.toString())
        assertEquals(StockState.UNKNOWN, forward.winner?.lines?.single()?.purchases?.single()?.stock)
        fixture.stock(alpha, b, StockState.OUT_OF_STOCK)
        val exhausted = fixture.optimize(demands, selected = setOf(alpha.retailerId))
        assertNull(exhausted.winner)
        assertEquals(setOf(BasketMissingReason.OUT_OF_STOCK, BasketMissingReason.AMBIGUOUS_PRICE),
            exhausted.alternatives.single().missing.single().reasons)
    }

    @Test fun searchLimitCannotPromoteAnotherStoreToWinner() {
        val fixture = Fixture()
        val alpha = fixture.store("a", "alpha")
        val beta = fixture.store("b", "beta")
        fixture.price(alpha, fixture.pack("tiny-a", rice, 1), "0")
        fixture.price(alpha, fixture.pack("tiny-b", rice, 1), "0")
        fixture.price(beta, fixture.pack("bulk", rice, 500_000), "10")
        val result = fixture.optimize(mapOf(1L to demand(rice, 200_000)))
        assertTrue(result.rankingPending)
        assertNull(result.winner)
        assertTrue(result.alternatives.first().isComplete)
        assertEquals(setOf(BasketMissingReason.SEARCH_LIMIT), result.alternatives.last().missing.single().reasons)
    }

    @Test fun buysExtraPacksOrKeepsSearchingAfterCostRisesWhenAPromotionMakesItCheaper() {
        val fixture = Fixture()
        val store = fixture.store("a")
        val large = fixture.pack("large", rice, 500)
        val small = fixture.pack("small", rice, 400)
        fixture.price(store, small, "0.45")
        val deal = fixture.price(store, large, "0.60", eligibility = PromotionEligibility.PUBLIC)
        val terms = listOf(VerifiedPromotion(deal, "terms", take = 4, pay = 1))
        val extra = fixture.optimize(mapOf(1L to demand(rice, 600)), promotions = terms).winner
        assertEquals("0.60", extra?.checkoutEur.toString())
        assertEquals("4", extra?.lines?.single()?.purchases?.single()?.count.toString())
        val mixed = fixture.optimize(mapOf(1L to demand(rice, 2400)), promotions = terms).winner
        assertEquals("1.05", mixed?.checkoutEur.toString())
        assertEquals(listOf("4", "1"), mixed?.lines?.single()?.purchases?.map { it.count.toString() })
    }

    @Test fun agreesWithAnIndependentExhaustiveEnumeratorAcrossSeededFixtures() {
        val random = Random(19)
        repeat(240) { sample ->
            val fixture = Fixture()
            val store = fixture.store("a")
            val target = random.nextInt(1, 21)
            val formats = List(random.nextInt(1, 5)) { i ->
                val size = random.nextInt(1, 11)
                val base = random.nextInt(0, 241).toBigDecimal().movePointLeft(2) + BigDecimal("0.005")
                val pack = fixture.pack("p$i", rice, size)
                fixture.price(store, pack, base.toPlainString())
                val deal = if (random.nextBoolean()) {
                    val rate = random.nextInt(0, 241).toBigDecimal().movePointLeft(2)
                    val promo = fixture.price(store, pack, rate.toPlainString(), eligibility = PromotionEligibility.PUBLIC)
                    VerifiedPromotion(promo, "terms", take = random.nextInt(2, 5), pay = 1)
                } else null
                Format(size, base, deal)
            }
            val result = fixture.optimize(mapOf(1L to demand(rice, target)), promotions = formats.mapNotNull { it.deal })
            val expected = exhaustive(formats, target)
            assertEquals("Fixture $sample, target $target", expected, result.winner?.checkoutEur)
        }
    }

    private data class Format(val size: Int, val base: BigDecimal, val deal: VerifiedPromotion?)

    private fun exhaustive(formats: List<Format>, target: Int): BigDecimal? {
        var best: BigDecimal? = null
        fun enumerate(index: Int, amount: Int, cost: BigDecimal) {
            if (index == formats.size) {
                if (amount >= target && (best == null || cost < best!!)) best = cost
                return
            }
            val format = formats[index]
            val extra = (format.deal?.take ?: 1) - 1
            for (count in 0..((target + format.size - 1) / format.size + extra)) {
                val regular = format.base * count.toBigDecimal()
                val deal = format.deal?.let {
                    val groups = count / it.take
                    val free = groups * (it.take - it.pay)
                    it.observation.money.amount * (count - free).toBigDecimal()
                }
                val line = minOf(regular, deal ?: regular).setScale(2, RoundingMode.HALF_UP)
                enumerate(index + 1, amount + count * format.size, cost + line)
            }
        }
        enumerate(0, 0, BigDecimal.ZERO)
        return best
    }

    private fun id(value: String) = CommercialId(value)
    private fun demand(key: CommercialId, grams: Int) = PriceDemand(key,
        Quantity(grams.toDouble(), UnitDefinition.GRAM, MassBasis.NET))

    private inner class Fixture {
        private val regionId = id("region")
        private val source = PriceSource(id("source"), "Source", SourceKind.OPEN_DATA, "License", "Credit")
        private val retailers = mutableMapOf<CommercialId, Retailer>()
        private val stores = mutableListOf<Store>()
        private val zones = mutableListOf<PriceZone>()
        private val products = mutableListOf<CommercialProduct>()
        private val packs = mutableListOf<ProductPackage>()
        val matches = mutableListOf<IngredientProductMatch>()
        private val prices = mutableListOf<PriceObservation>()
        private val stock = mutableListOf<AvailabilityObservation>()

        private fun origin(key: String) = ProviderRecord(source.id, key)
        fun store(key: String, chain: String = "chain"): Store {
            val retailer = retailers.getOrPut(id(chain)) { Retailer(id(chain), origin(chain), chain) }
            return Store(id(key), origin(key), retailer.id, key, "28012").also { stores += it }
        }

        fun zone(store: Store) = PriceZone(id("commercial-zone"), origin("zone"), store.retailerId, "Zone")
            .also { zones += it }

        fun pack(name: String, ingredient: CommercialId, size: Int?, match: MatchKind = MatchKind.EXACT): ProductPackage {
            val product = CommercialProduct(id("product.$name"), origin(name), name)
            products += product
            val pack = if (size != null) ProductPackage(id("pack.$name"), origin(name), product.id, null, WeightMode.FIXED,
                SaleUnit.PACKAGE, NetContent(size.toBigDecimal(), ContentUnit.G), 1)
            else ProductPackage(id("pack.$name"), origin(name), product.id, null, WeightMode.VARIABLE, SaleUnit.KG, null, null)
            packs += pack
            matches += IngredientProductMatch(origin(name), ingredient, 1, pack.id, match, Confidence.HIGH,
                if (size == null) ReviewedEdibleMass(800.toBigDecimal(), "verified weight yield") else null)
            return pack
        }

        fun price(store: Store, pack: ProductPackage, eur: String, observed: Instant = now,
                  eligibility: PromotionEligibility? = null, validUntil: LocalDate? = eligibility?.let { today },
                  scope: ObservationScope = ObservationScope.AtStore(store.id)): PriceObservation =
            PriceObservation(ObservationRecord(origin("price.${prices.size}"), 1, store.retailerId, pack.id, scope,
                observed.atZone(policy.saleZone).toLocalDate(), observed, Confidence.HIGH),
                Money(eur.toBigDecimal(), Currency.EUR), pack.saleUnit,
                if (eligibility == null) PriceKind.REGULAR else PriceKind.PROMOTIONAL, eligibility, validUntil)
                .also { prices += it }

        fun stock(store: Store, pack: ProductPackage, state: StockState, observed: Instant = now) {
            stock += AvailabilityObservation(ObservationRecord(origin("stock.${stock.size}"), 1, store.retailerId, pack.id,
                ObservationScope.AtStore(store.id), observed.atZone(policy.saleZone).toLocalDate(), observed,
                Confidence.HIGH), state)
        }

        fun optimize(
            demands: Map<Long, PriceDemand?>, selected: Set<CommercialId> = retailers.keys,
            localStores: Set<CommercialId> = stores.map { it.id }.toSet(), region: CommercialId = regionId,
            promotions: List<VerifiedPromotion> = emptyList(), substitutions: Set<IngredientProductMatch> = emptySet(),
            reverse: Boolean = false
        ): SingleStoreBasketResult {
            fun <T> List<T>.ordered() = if (reverse) reversed() else toList()
            val snapshot = PriceSnapshot(1, regionId, 1, now.minusSeconds(60), now.plusSeconds(3600),
                listOf(source), retailers.values.toList().ordered(), stores.ordered(), zones.ordered(), products.ordered(),
                packs.ordered(), matches.ordered(), prices.ordered(), stock.ordered())
            return SingleStoreBasketOptimizer.optimize(demands, selected, region, localStores, snapshot, policy,
                promotions, substitutions)
        }
    }
}
