package com.zame.nutrinest.domain.prices

import com.zame.nutrinest.domain.nutrition.*
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class MultiStoreBasketOptimizerTest {
    private val now = Instant.parse("2026-11-19T12:00:00Z")
    private val region = CommercialId("local")
    private val source = PriceSource(CommercialId("s"), "Test", SourceKind.OPEN_DATA, "test", "test")
    private fun origin(key: String) = ProviderRecord(source.id, key)
    private fun id(key: String) = CommercialId(key)
    private fun demand(key: String, grams: Int) = PriceDemand(id(key), Quantity(grams.toDouble(), UnitDefinition.GRAM, MassBasis.NET))

    private data class Offer(val shop: Int, val key: String, val size: Int, val cents: Int)
    private fun snapshot(n: Int, offers: List<Offer>): PriceSnapshot {
        val retailers = (0 until n).map { Retailer(id("r$it"), origin("r$it"), "Chain $it") }
        val stores = retailers.mapIndexed { i, r -> Store(id("s$i"), origin("s$i"), r.id, "Store $i") }
        val formats = offers.distinctBy { it.key to it.size }
        fun sku(o: Offer) = id("${o.key}.${o.size}")
        val products = formats.map { CommercialProduct(sku(it), origin(sku(it).value), it.key) }
        val packs = formats.map { ProductPackage(sku(it), origin(sku(it).value), sku(it), null, WeightMode.FIXED,
            SaleUnit.PACKAGE, NetContent(it.size.toBigDecimal(), ContentUnit.G), 1) }
        val matches = formats.map { IngredientProductMatch(origin(sku(it).value), id(it.key), 1, sku(it),
            MatchKind.EXACT, Confidence.HIGH) }
        val prices = offers.map { o ->
            val store = stores[o.shop]
            PriceObservation(ObservationRecord(origin("${store.id.value}.${sku(o).value}"), 1, store.retailerId,
                sku(o), ObservationScope.AtStore(store.id), now.atZone(ZoneOffset.UTC).toLocalDate(), now, Confidence.HIGH),
                Money(o.cents.toBigDecimal().movePointLeft(2), Currency.EUR), SaleUnit.PACKAGE, PriceKind.REGULAR, null, null)
        }
        return PriceSnapshot(1, region, 1, now.minusSeconds(60), now.plusSeconds(3600),
            listOf(source), retailers, stores, emptyList(), products, packs, matches, prices, emptyList())
    }

    private fun run(data: PriceSnapshot, rows: Map<Long, PriceDemand?>, limit: Int,
                    fees: Map<CommercialId, VerifiedVisitCost>? = null,
                    shops: Set<CommercialId> = data.stores.map { it.id }.toSet(),
                    chains: Set<CommercialId> = data.retailers.map { it.id }.toSet()) =
        MultiStoreBasketOptimizer.optimize(rows, chains, region, shops, data,
            ComparisonPolicy(now, ZoneOffset.UTC), limit, fees)

    @Test fun visitCapsSavingsAndConfirmedCostCanReverseTheTripDecision() {
        val offers = listOf(Offer(0,"a",1,100), Offer(0,"b",1,400), Offer(0,"c",1,400),
            Offer(1,"b",1,100), Offer(2,"c",1,100))
        val data = snapshot(3, offers)
        val rows = mapOf(1L to demand("a",1), 2L to demand("b",1), 3L to demand("c",1))
        val three = run(data, rows, 3)
        assertEquals("9.00", run(data, rows, 1).plan.checkoutEur.toString())
        assertEquals("6.00", run(data, rows, 2).plan.checkoutEur.toString())
        assertEquals("3.00", three.plan.checkoutEur.toString())
        assertEquals("6.00", three.grossSavingsEur.toString())
        assertNull(three.netSavingsEur)
        val fees = listOf("0.00","1.00","5.00").mapIndexed { i, n ->
            id("s$i") to VerifiedVisitCost(n.toBigDecimal(), "confirmed fare $i")
        }.toMap()
        val paid = run(data, rows, 3, fees)
        assertEquals(listOf(id("s0"), id("s1")), paid.plan.visits.map { it.store.id })
        assertEquals("3.00", paid.grossSavingsEur.toString())
        assertEquals("2.00", paid.netSavingsEur.toString())
        assertTrue(paid.exact)
        val unknown = run(data, rows, 3, fees - id("s2"))
        assertEquals(TravelMode.MISSING_COSTS, unknown.plan.travelMode)
        assertNull(unknown.netSavingsEur)
        assertEquals("2.00", run(data, rows, 2, fees - id("s2")).netSavingsEur.toString())
        assertEquals(three.plan, run(snapshot(3, offers.reversed()), rows, 3).plan)
    }

    @Test fun mixesFormatsAcrossStoresAndSharesContentsAcrossRows() {
        val data = snapshot(2, listOf(Offer(0,"a",6,110), Offer(1,"a",5,98)))
        val rows = mapOf(1L to demand("a",4), 2L to demand("a",7))
        val result = run(data, rows, 2)
        assertEquals("2.08", result.plan.checkoutEur.toString())
        assertEquals(2, result.plan.visits.size)
        assertEquals(listOf(1L,2L), result.plan.lines.single().itemIds)
        val shared = run(data, mapOf(1L to demand("a",2), 2L to demand("a",3)), 2)
        assertEquals("0.98", shared.plan.checkoutEur.toString())
        assertEquals("1", shared.plan.visits.single().purchases.single().count.toString())
    }

    @Test fun reportsPartialAndHonorsBothSelections() {
        val data = snapshot(3, listOf(Offer(0,"a",1,10), Offer(1,"b",1,20), Offer(2,"c",1,30)))
        val rows = mapOf(1L to demand("a",1), 2L to demand("b",1), 3L to demand("c",1), 4L to null)
        val plan = run(data, rows, 3, shops = setOf(id("s0"),id("s1")), chains = setOf(id("r1"),id("r2")))
        assertEquals(listOf(1L,3L,4L), plan.plan.missing.map { it.itemId })
        assertEquals("0.20", plan.plan.checkoutEur.toString())
        assertNull(plan.grossSavingsEur)
        assertNull(run(data, rows, 1, shops = emptySet()).plan.checkoutEur)
        val dispersed = run(data, rows - 4L, 3)
        assertTrue(dispersed.plan.complete)
        assertNull(dispersed.singleStoreReference)
        assertNull(dispersed.netSavingsEur)
    }

    @Test fun largeStoreSetsAreMarkedHeuristic() {
        val data = snapshot(10, (0..9).map { Offer(it,"a",1,it+1) })
        val result = run(data, mapOf(1L to demand("a", 2)), 10)
        assertFalse(result.exact)
        assertEquals("0.02", result.plan.checkoutEur.toString())
    }

    @Test fun packSearchBudgetRetainsAFeasiblePlanWithoutAnOptimalityClaim() {
        val data = snapshot(2, listOf(Offer(0,"a",1,0), Offer(1,"a",1,0)))
        val result = run(data, mapOf(1L to demand("a",200_000)), 2)
        assertTrue(result.plan.complete)
        assertFalse(result.exact)
        assertEquals("0.00", result.plan.checkoutEur.toString())
        assertEquals(1, result.plan.visits.size)
    }

    @Test fun matchesIndependentEnumerationOfEveryPackCountAndVisitFee() {
        val random = Random(20)
        repeat(80) {
            val offers = (0..2).flatMap { shop -> listOf("a","b").map { key ->
                Offer(shop, key, random.nextInt(1,4), random.nextInt(1,31))
            } }
            val targets = listOf(random.nextInt(1,5), random.nextInt(1,5))
            val fares = List(3) { random.nextInt(0,20) }
            val cap = random.nextInt(1,4)
            var minimum = Int.MAX_VALUE
            fun visit(i: Int, a: Int, b: Int, mask: Int, cents: Int) {
                if (Integer.bitCount(mask) > cap || cents >= minimum) return
                if (i == offers.size) {
                    if (a >= targets[0] && b >= targets[1]) minimum = cents
                    return
                }
                val o = offers[i]
                val first = o.key == "a"
                val needed = if (first) targets[0] else targets[1]
                for (count in 0..((needed + o.size - 1) / o.size)) {
                    val bit = 1 shl o.shop
                    val fee = if (count > 0 && mask and bit == 0) fares[o.shop] else 0
                    visit(i+1, a + if (first) count*o.size else 0, b + if (first) 0 else count*o.size,
                        if (count > 0) mask or bit else mask, cents + count*o.cents + fee)
                }
            }
            visit(0,0,0,0,0)
            val fees = fares.mapIndexed { i, cents ->
                id("s$i") to VerifiedVisitCost(BigDecimal.valueOf(cents.toLong(),2), "fixture")
            }.toMap()
            val result = run(snapshot(3, offers), mapOf(1L to demand("a",targets[0]), 2L to demand("b",targets[1])), cap, fees)
            assertTrue(result.exact && result.plan.complete)
            assertEquals(minimum, result.plan.totalWithVisitsEur!!.movePointRight(2).intValueExact())
        }
    }
}
