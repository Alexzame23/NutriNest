package com.zame.nutrinest.domain.stores

import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.ComparisonPolicy
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
    val declaredAreas: List<ScopedPriceCoverage>,
    val hasStaleObservations: Boolean = false,
    val nextChange: Instant? = null
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
    val observed = prices.filter { it.record.observedInstant <= now }
    val groups = observed.groupBy { it.record.scope to it.record.origin.sourceId }

    fun coverage(scope: ObservationScope, sourceId: CommercialId, prices: List<PriceObservation>): ScopedPriceCoverage? {
        val zone = when (scope) {
            is ObservationScope.AtStore -> {
                if (scope.storeId !in storeIds) return null
                null
            }
            is ObservationScope.InZone -> declaredZones.getValue(scope.zoneId)
            is ObservationScope.Online -> scope.zoneId?.let(declaredZones::getValue)
            ObservationScope.Unknown -> return null
        }
        return ScopedPriceCoverage(scope, zone, declaredSources.getValue(sourceId), prices)
    }

    val policy = ComparisonPolicy(now, ZoneOffset.UTC)
    var hasStaleObservations = false
    var nextChange = expiresAt
    val current = groups.mapNotNull { (key, values) -> coverage(key.first, key.second, values) }
        .mapNotNull { group ->
            val observations = group.observations.filter {
                val deadline = it.record.observedInstant.plus(policy.freshness)
                if (now < deadline) {
                    nextChange = minOf(nextChange, deadline)
                    true
                } else {
                    hasStaleObservations = true
                    false
                }
            }
            group.copy(observations = observations).takeIf { observations.isNotEmpty() }
        }
    val (local, areas) = current.partition { it.scope is ObservationScope.AtStore }
    return StorePriceCoverage(generatedAt, local, areas, hasStaleObservations, nextChange)
}
