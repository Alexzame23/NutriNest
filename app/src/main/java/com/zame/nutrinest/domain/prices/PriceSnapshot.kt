package com.zame.nutrinest.domain.prices

import java.time.Instant

const val PRICE_SNAPSHOT_VERSION = 1
const val MAX_PRICE_SNAPSHOT_BYTES = 1024 * 1024
private const val MAX_PUBLICATION_REVISION = 9_007_199_254_740_991L

data class SnapshotManifest(
    val contractVersion: Int,
    val regionId: CommercialId,
    val revision: Long,
    val generatedAt: Instant,
    val expiresAt: Instant,
    val sha256: String,
    val byteLength: Int
) {
    init {
        checkHeader(contractVersion, revision, generatedAt, expiresAt)
        require(sha256.matches(Regex("[0-9a-f]{64}")))
        require(byteLength in 1..MAX_PRICE_SNAPSHOT_BYTES)
    }
}

data class PriceSnapshot(
    val contractVersion: Int,
    val regionId: CommercialId,
    val revision: Long,
    val generatedAt: Instant,
    val expiresAt: Instant,
    val sources: List<PriceSource>,
    val retailers: List<Retailer>,
    val stores: List<Store>,
    val zones: List<PriceZone>,
    val products: List<CommercialProduct>,
    val packages: List<ProductPackage>,
    val matches: List<IngredientProductMatch>,
    val prices: List<PriceObservation>,
    val availability: List<AvailabilityObservation>
) {
    init {
        checkHeader(contractVersion, revision, generatedAt, expiresAt)
        val sourceIds = sources.uniqueBy { it.id }
        val retailerIds = retailers.uniqueBy { it.id }
        val storesById = stores.uniqueBy { it.id }
        val zonesById = zones.uniqueBy { it.id }
        val productIds = products.uniqueBy { it.id }
        val packagesById = packages.uniqueBy { it.id }

        fun <T> checkOrigins(items: List<T>, origin: (T) -> ProviderRecord) {
            require(items.uniqueBy(origin).keys.all { it.sourceId in sourceIds })
        }

        checkOrigins(retailers) { it.origin }
        checkOrigins(stores) { it.origin }
        checkOrigins(zones) { it.origin }
        checkOrigins(products) { it.origin }
        checkOrigins(packages) { it.origin }
        checkOrigins(matches) { it.origin }
        checkOrigins(prices) { it.record.origin }
        checkOrigins(availability) { it.record.origin }
        require(stores.all { it.retailerId in retailerIds })
        require(zones.all { it.retailerId in retailerIds })
        require(packages.all { it.productId in productIds })
        require(packages.all { it.retailerSku == null || it.retailerSku.retailerId in retailerIds })
        require(matches.all { it.packageId in packagesById &&
            (it.commercialProductId == null || it.commercialProductId == packagesById[it.packageId]?.productId) })
        matches.filter { it.classification != null }.uniqueBy { it.packageId }

        fun checkObservation(record: ObservationRecord) {
            require(record.retailerId in retailerIds && record.packageId in packagesById)
            packagesById.getValue(record.packageId).retailerSku?.let { require(it.retailerId == record.retailerId) }
            val retailer = record.retailerId
            when (val scope = record.scope) {
                is ObservationScope.AtStore -> require(storesById[scope.storeId]?.retailerId == retailer)
                is ObservationScope.InZone -> require(zonesById[scope.zoneId]?.retailerId == retailer)
                is ObservationScope.Online -> require(scope.zoneId == null ||
                    zonesById[scope.zoneId]?.retailerId == retailer)
                ObservationScope.Unknown -> Unit
            }
        }

        prices.forEach {
            checkObservation(it.record)
            require(packagesById.getValue(it.record.packageId).saleUnit == it.per)
        }
        availability.forEach { checkObservation(it.record) }
    }
}

private fun <T, K> List<T>.uniqueBy(key: (T) -> K): Map<K, T> =
    associateBy(key).also { require(it.size == size) { "Duplicate commercial identity" } }

private fun checkHeader(version: Int, revision: Long, generatedAt: Instant, expiresAt: Instant) {
    require(version == PRICE_SNAPSHOT_VERSION && revision in 1..MAX_PUBLICATION_REVISION)
    require(expiresAt > generatedAt && expiresAt.nano % 1_000_000 == 0)
}
