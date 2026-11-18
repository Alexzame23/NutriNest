package com.zame.nutrinest.data.repository

import com.zame.nutrinest.data.local.dao.CatalogRecipeMeasure
import com.zame.nutrinest.domain.nutrition.ConversionConfidence
import com.zame.nutrinest.domain.nutrition.ConversionEvidence
import com.zame.nutrinest.domain.nutrition.EvidenceStatus
import com.zame.nutrinest.domain.nutrition.FoodConversionFactor
import com.zame.nutrinest.domain.nutrition.MassBasis
import com.zame.nutrinest.domain.nutrition.QuantityDimension
import com.zame.nutrinest.domain.nutrition.RecipeMeasure
import com.zame.nutrinest.domain.nutrition.UnitDefinition

internal fun CatalogRecipeMeasure.toRecipeMeasure(): RecipeMeasure? {
    if (evidence.status != "ACCEPTED" || evidence.massBasis != "EDIBLE") return null
    val key = evidence.unitKey
    val unit = when (kind) {
        "COUNT" -> key.takeIf { it.startsWith("UNIT:") }?.let { UnitDefinition.count(it.removePrefix("UNIT:")) }
        "PORTION" -> key.takeIf { it.startsWith("PORTION:") }?.let { UnitDefinition.portion(it.removePrefix("PORTION:")) }
        "VOLUME" -> UnitDefinition.fromLegacyKey(key)?.takeIf { it.dimension == QuantityDimension.VOLUME }
        else -> null
    } ?: return null
    val grams = evidence.gramsPerUnit ?: return null
    val confidence = ConversionConfidence.entries.firstOrNull { it.name == evidence.confidence } ?: return null
    val source = ConversionEvidence(
        evidence.evidenceId, evidence.sourceLocator, evidence.evidenceVersion,
        confidence, EvidenceStatus.ACCEPTED, evidence.conditions
    )
    val factor = if (unit.dimension == QuantityDimension.VOLUME) {
        FoodConversionFactor.Density(evidence.ingredientId, grams / unit.baseMultiplier, MassBasis.EDIBLE, source)
    } else {
        FoodConversionFactor.UnitWeight(evidence.ingredientId, unit, grams, MassBasis.EDIBLE, source)
    }
    val measure = RecipeMeasure(
        unit, labelEn ?: labelEs ?: key, labelEs ?: labelEn ?: key, defaultSlot == 1L, factor
    )
    return measure.takeIf { it.enter(1.0) != null }
}
