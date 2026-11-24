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
    val verifiedOn: LocalDate,
    val province: String = ""
)

data class DirectoryStore(
    val store: Store,
    val area: DiscoveryArea,
    val address: String,
    val point: GeoPoint?,
    val directoryUrl: String?
)

data class NearbyStore(val entry: DirectoryStore, val distanceKm: Double?)
interface StoreRepository {
    suspend fun refresh() {}
    fun knownStores(): List<Store> = emptyList()
    fun findByArea(postalCodeOrMunicipality: String): List<NearbyStore>
    fun findNearby(origin: GeoPoint, radiusKm: Double): List<NearbyStore>
}

internal class LocalStoreRepository(private val entries: List<DirectoryStore>) : StoreRepository {
    override fun knownStores(): List<Store> = entries.map { it.store }

    override fun findByArea(postalCodeOrMunicipality: String): List<NearbyStore> {
        val query = normalize(postalCodeOrMunicipality)
        if (query.isBlank()) return emptyList()
        val provinceCode = provinces.indexOfFirst { names -> names.split('/').any { normalize(it) == query } }
            .takeIf { it >= 0 }?.let { (it + 1).toString().padStart(2, '0') }
        val matches = entries.filter {
            it.area.postalCode == query || normalize(it.area.municipality) == query || normalize(it.area.province) == query ||
                (provinceCode != null && it.area.postalCode.startsWith(provinceCode))
        }
        return matches.sortedBy { it.store.name }.map { NearbyStore(it, null) }
    }

    override fun findNearby(origin: GeoPoint, radiusKm: Double): List<NearbyStore> {
        require(radiusKm.isFinite() && radiusKm > 0)
        return entries.mapNotNull { entry -> entry.point?.let { NearbyStore(entry, origin.distanceKm(it)) } }
            .filter { requireNotNull(it.distanceKm) <= radiusKm }
            .sortedWith(compareBy<NearbyStore> { it.distanceKm }.thenBy { it.entry.store.id.value })
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
}

// INE province codes 01–52, used only to search published physical addresses.
private val provinces = listOf(
    "Araba/Álava", "Albacete", "Alicante/Alacant", "Almería", "Ávila", "Badajoz", "Illes Balears/Baleares",
    "Barcelona", "Burgos", "Cáceres", "Cádiz", "Castellón/Castelló", "Ciudad Real", "Córdoba", "A Coruña",
    "Cuenca", "Girona", "Granada", "Guadalajara", "Gipuzkoa", "Huelva", "Huesca", "Jaén", "León", "Lleida",
    "La Rioja", "Lugo", "Madrid", "Málaga", "Murcia", "Navarra", "Ourense", "Asturias", "Palencia",
    "Las Palmas", "Pontevedra", "Salamanca", "Santa Cruz de Tenerife", "Cantabria", "Segovia", "Sevilla",
    "Soria", "Tarragona", "Teruel", "Toledo", "Valencia/València", "Valladolid", "Bizkaia", "Zamora",
    "Zaragoza", "Ceuta", "Melilla"
)
