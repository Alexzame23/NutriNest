package com.zame.nutrinest.domain.prices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class CommercialCatalogTest {
    private val id = CommercialId("fixture")
    private val origin = ProviderRecord(id, "vendor:004")
    private val record = ObservationRecord(
        origin, 1, id, id, ObservationScope.Unknown, LocalDate.parse("2026-11-18"), null, Confidence.MEDIUM
    )

    @Test
    fun identifiersUseAnIndependentNamespaceAndPreserveGtinZeros() {
        assertEquals("vendor:004", origin.recordKey)
        assertEquals("00036000291452", Gtin("036000291452").normalized)
        assertEquals(Gtin("036000291452").normalized, Gtin("0036000291452").normalized)
        for (invalid in listOf("00000000", "036000291453", "123456789", "٣٦٠٠٠٢٩١٤٥٢")) {
            assertThrows(IllegalArgumentException::class.java) { Gtin(invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { CommercialId("retailer/other") }
    }

    @Test
    fun packContentAndVariableWeightHaveDifferentSaleUnits() {
        val content = NetContent(BigDecimal("500"), ContentUnit.G)
        val fixed = ProductPackage(id, origin, id, null, WeightMode.FIXED, SaleUnit.PACKAGE, content, 2)
        val variable = ProductPackage(id, origin, id, null, WeightMode.VARIABLE, SaleUnit.KG, null, null)
        assertEquals(content, fixed.netContent)
        assertEquals(SaleUnit.KG, variable.saleUnit)
        assertThrows(IllegalArgumentException::class.java) { fixed.copy(saleUnit = SaleUnit.KG) }
        assertThrows(IllegalArgumentException::class.java) { variable.copy(netContent = content) }
        assertThrows(IllegalArgumentException::class.java) { NetContent(BigDecimal("1.4"), ContentUnit.UNIT) }
    }

    @Test
    fun priceIsExactAndPromotionKeepsEligibility() {
        val money = Money(BigDecimal("1.2345"), Currency.EUR)
        val regular = PriceObservation(record, money, SaleUnit.KG, PriceKind.REGULAR, null, null)
        assertEquals("1.2345", regular.money.amount.toPlainString())
        val promo = regular.copy(kind = PriceKind.PROMOTIONAL, eligibility = PromotionEligibility.LOYALTY)
        assertEquals(PromotionEligibility.LOYALTY, promo.eligibility)
        assertThrows(IllegalArgumentException::class.java) { promo.copy(eligibility = null) }
        assertThrows(IllegalArgumentException::class.java) { promo.copy(validUntil = record.observedOn.minusDays(1)) }
        assertThrows(IllegalArgumentException::class.java) { Money(BigDecimal("0.00001"), Currency.EUR) }
    }

    @Test
    fun syncSuccessRequiresAnAttemptAndMatchingSuccessTime() {
        val attempt = Instant.parse("2026-11-18T12:00:00Z")
        assertEquals(SyncState.SUCCEEDED, PriceSyncStatus(id, id, SyncState.SUCCEEDED, attempt, attempt).state)
        assertThrows(IllegalArgumentException::class.java) {
            PriceSyncStatus(id, id, SyncState.SUCCEEDED, attempt, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PriceSyncStatus(id, id, SyncState.FAILED, attempt, attempt.plusSeconds(1))
        }
    }
}
