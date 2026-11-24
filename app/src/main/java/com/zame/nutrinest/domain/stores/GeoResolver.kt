package com.zame.nutrinest.domain.stores

import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.Store
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun validSpanishPostalCode(input: String): Boolean =
    input.matches(Regex("[0-9]{5}")) && input in "01001".."52999" && !input.endsWith("000")

enum class GeoCoverage { COBERTURA, SIN_COBERTURA, FUENTE_NO_LOCALIZABLE }

internal data class GeoDirectory(
    val entries: List<DirectoryStore>,
    val expires: Instant,
    val provider: CommercialId
)

internal class GeoResolver(
    private val fetch: suspend () -> GeoDirectory?,
    private val clock: Clock = Clock.systemUTC()
) : StoreRepository {
    private val lock = Mutex()
    @Volatile private var cached: GeoDirectory? = null
    private var refreshAt = Instant.MIN

    override suspend fun refresh() {
        lock.withLock {
            val now = clock.instant()
            if (now < refreshAt) return
            val directory = fetch()?.takeIf { it.expires > clock.instant() }
            cached = directory
            refreshAt = minOf(directory?.expires ?: Instant.MAX, now.plusSeconds(if (directory == null) 30 else 300))
        }
    }

    private fun current() = cached?.takeIf { clock.instant() < it.expires }
    private fun directory() = LocalStoreRepository(current()?.entries.orEmpty())
    override fun knownStores(): List<Store> = directory().knownStores()
    override fun findByArea(postalCodeOrMunicipality: String) = directory().findByArea(postalCodeOrMunicipality)
    override fun findNearby(origin: GeoPoint, radiusKm: Double) = directory().findNearby(origin, radiusKm)
    fun hasGeolocatedStores() = current()?.entries.orEmpty().any { it.point != null }

    fun coverage(results: List<NearbyStore>): Map<String, GeoCoverage> {
        val publication = current()
        val provider = publication?.provider?.value ?: "open-prices"
        val covered = publication != null && results.any { it.entry.store.origin.sourceId == publication.provider }
        return mapOf(
            provider to if (covered) GeoCoverage.COBERTURA else GeoCoverage.SIN_COBERTURA,
            "Superfudis" to GeoCoverage.FUENTE_NO_LOCALIZABLE
        )
    }
}
