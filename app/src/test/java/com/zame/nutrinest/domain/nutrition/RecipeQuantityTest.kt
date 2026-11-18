package com.zame.nutrinest.domain.nutrition

import com.zame.nutrinest.data.local.dao.CatalogRecipeMeasure
import com.zame.nutrinest.data.local.entity.ConversionEvidenceEntity
import com.zame.nutrinest.data.repository.toRecipeMeasure
import org.junit.Assert.*
import org.junit.Test

class RecipeQuantityTest {
    private val grams = RecipeMeasure.GRAMS
    private val milk = catalogOption("MILLILITRE", "VOLUME", 1.03).toRecipeMeasure()!!
    private val egg = catalogOption("UNIT:large", "COUNT", 50.0).toRecipeMeasure()!!

    @Test fun catalogOptionsRequireConvertibleAcceptedEvidence() {
        val portion = catalogOption("PORTION:bowl", "PORTION", 115.0)
        assertEquals(230.0, portion.toRecipeMeasure()!!.enter(2.0)!!.grams, 0.0)
        assertNull(catalogOption("GRAM", "VOLUME", 1.0).toRecipeMeasure())
        assertNull(catalogOption("UNIT:", "COUNT", 50.0).toRecipeMeasure())
        assertNull(catalogOption("MILLILITRE", "VOLUME", 0.0).toRecipeMeasure())
        assertNull(portion.copy(evidence = portion.evidence.copy(status = "UNVERIFIED")).toRecipeMeasure())
        assertNull(portion.copy(evidence = portion.evidence.copy(massBasis = "GROSS")).toRecipeMeasure())
        assertEquals(grams, RecipeMeasure.default(emptyList()))
        assertEquals(egg, RecipeMeasure.default(listOf(grams, egg)))
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 3_000.0).forEach {
            assertNull(egg.enter(it))
        }
        assertFalse(egg.supports(RecipeQuantity(200.0, egg.unit.key, 2.0, egg.factor!!.evidence.id)))
    }

    @Test fun switchesPreserveTheExactEdibleMassAndOriginalAmount() {
        val original = milk.enter(213.9876543210987)!!
        val asMass = grams.forMass(original)!!
        val other = milk.forMass(asMass)!!
        assertEquals(original.grams.toRawBits(), other.grams.toRawBits())
        assertEquals(original, milk.forMass(original))
        assertTrue(milk.supports(other))
        assertFalse(milk.supports(original.copy(evidenceId = "superseded")))
    }

    @Test fun duplicateIngredientsKeepTheirMassAndCompatibleOriginalAmount() {
        val one = egg.enter(1.0)!!
        val two = egg.enter(2.0)!!
        assertEquals(egg.enter(3.0), one + two)
        assertEquals(RecipeQuantity(50.125), one + grams.enter(0.125)!!)
    }

    private fun catalogOption(key: String, kind: String, gramsPerUnit: Double) = CatalogRecipeMeasure(
        evidence = ConversionEvidenceEntity(
            evidenceId = "test:$key", ingredientId = 12, unitKey = key, gramsPerUnit = gramsPerUnit,
            massBasis = "EDIBLE", method = "MEASURED", status = "ACCEPTED", evidenceVersion = "test-v1",
            confidence = "HIGH", sourceLocator = "fixture://recipe-measures", sourceExcerpt = "Synthetic fixture",
            conditions = "Edible mass, synthetic fixture", revisionId = "test"
        ),
        kind = kind, labelEn = "test measure", labelEs = "medida de prueba", defaultSlot = 1
    )
}
