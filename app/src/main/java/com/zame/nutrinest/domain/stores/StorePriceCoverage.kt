package com.zame.nutrinest.domain.stores

import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.ObservationScope
import com.zame.nutrinest.domain.prices.PriceObservation
import com.zame.nutrinest.domain.prices.PriceSnapshot
import com.zame.nutrinest.domain.prices.PriceSource
import com.zame.nutrinest.domain.prices.PriceZone
import java.time.Instant
import java.time.ZoneOffset

data class ScopedPriceCoverage(
    val scope: ObservationScope,
    val zone: PriceZone?,
    val source: PriceSource,
    val observations: List<PriceObservation>
)

data class StorePriceCoverage(
    val publishedAt: Instant?,
    val atStore: List<ScopedPriceCoverage>,
    val declaredAreas: List<ScopedPriceCoverage>
)

/** Preserve the publisher's scope. Directory geography is never evidence of a commercial scope. */
fun PriceSnapshot?.coverageFor(entry: DirectoryStore, now: Instant): StorePriceCoverage {
    if (this == null || regionId != entry.area.regionId || now < generatedAt || now >= expiresAt) {
        return StorePriceCoverage(null, emptyList(), emptyList())
    }

    val storeIds = stores.filter { store ->
        store.id == entry.store.id || (entry.store.osmRef != null && store.osmRef == entry.store.osmRef)
    }.map { it.id }.toSet()
    val declaredZones = zones.associateBy { it.id }
    val declaredSources = sources.associateBy { it.id }
    val observed = prices.filter { price ->
        val record = price.record
        val observedAt = record.observedAt ?: record.observedOn.atStartOfDay(ZoneOffset.UTC).toInstant()
        observedAt <= now
    }
    val groups = observed.groupBy { it.record.scope to it.record.origin.sourceId }

    fun coverage(scope: ObservationScope, sourceId: CommercialId, prices: List<PriceObservation>): ScopedPriceCoverage? {
        val zone = when (scope) {
            is ObservationScope.AtStore -> {
                if (scope.storeId !in storeIds) return null
                null
            }
            is ObservationScope.InZone -> declaredZones.getValue(scope.zoneId)
            is ObservationScope.Online -> declaredZones.getValue(scope.zoneId ?: return null)
            ObservationScope.Unknown -> return null
        }
        return ScopedPriceCoverage(scope, zone, declaredSources.getValue(sourceId), prices)
    }

    val (local, areas) = groups.mapNotNull { (key, values) -> coverage(key.first, key.second, values) }
        .partition { it.scope is ObservationScope.AtStore }
    return StorePriceCoverage(generatedAt, local, areas)
}
