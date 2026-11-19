package com.zame.nutrinest.domain.stores

import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.Store
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
    }

    fun distanceKm(other: GeoPoint): Double {
        val lat = Math.toRadians(other.latitude - latitude)
        val lon = Math.toRadians(other.longitude - longitude)
        val a = sin(lat / 2) * sin(lat / 2) + cos(Math.toRadians(latitude)) *
            cos(Math.toRadians(other.latitude)) * sin(lon / 2) * sin(lon / 2)
        return 2 * 6371.0088 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    companion object {
        // Reduce Android's coarse fix to a grid of roughly 2 km before discovery.
        fun approximate(latitude: Double, longitude: Double) =
            GeoPoint(round(latitude * 50) / 50, round(longitude * 50) / 50)
    }
}

/** Physical coverage and the key of the corresponding publication, independent of PriceZone. */
data class DiscoveryArea(
    val regionId: CommercialId,
    val municipality: String,
    val postalCode: String,
    val verifiedOn: LocalDate
)

data class DirectoryStore(
    val store: Store,
    val area: DiscoveryArea,
    val address: String,
    val point: GeoPoint,
    val directoryUrl: String
)

data class NearbyStore(val entry: DirectoryStore, val distanceKm: Double?)
interface StoreRepository {
    fun knownStores(): List<Store> = emptyList()
    fun findByArea(postalCodeOrMunicipality: String): List<NearbyStore>
    fun findNearby(origin: GeoPoint, radiusKm: Double): List<NearbyStore>
}

internal class LocalStoreRepository(private val entries: List<DirectoryStore>) : StoreRepository {
    override fun knownStores(): List<Store> = entries.map { it.store }

    override fun findByArea(postalCodeOrMunicipality: String): List<NearbyStore> {
        val query = normalize(postalCodeOrMunicipality)
        val matches = entries.filter {
            it.area.postalCode == query || normalize(it.area.municipality) == query
        }
        return matches.sortedBy { it.store.name }.map { NearbyStore(it, null) }
    }

    override fun findNearby(origin: GeoPoint, radiusKm: Double): List<NearbyStore> {
        require(radiusKm.isFinite() && radiusKm > 0)
        return entries.map { NearbyStore(it, origin.distanceKm(it.point)) }
            .filter { requireNotNull(it.distanceKm) <= radiusKm }
            .sortedWith(compareBy<NearbyStore> { it.distanceKm }.thenBy { it.entry.store.id.value })
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
}
