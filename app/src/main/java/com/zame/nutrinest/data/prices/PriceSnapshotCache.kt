package com.zame.nutrinest.data.prices

import androidx.room.Entity
import androidx.room.Index
import androidx.room.withTransaction
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.PriceSnapshot
import com.zame.nutrinest.domain.prices.ObservationScope
import com.zame.nutrinest.domain.stores.validSpanishPostalCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant

@Entity(tableName = "price_snapshot_cache", primaryKeys = ["source", "regionId"])
data class CachedPriceSnapshot(
    val source: String,
    val regionId: String,
    val revision: Long,
    val expiresAtMillis: Long,
    val sha256: String,
    val payload: ByteArray?
)

@Entity(tableName = "price_snapshot_postal", primaryKeys = ["source", "regionId", "postalCode"],
    indices = [Index(value = ["source", "postalCode"])])
data class CachedPricePostal(val source: String, val regionId: String, val postalCode: String)

@Entity(tableName = "price_snapshot_sync", primaryKeys = ["source", "regionId"])
data class PriceSnapshotSync(
    val source: String,
    val regionId: String,
    val lastAttemptMillis: Long,
    val lastSuccessMillis: Long?,
    val error: String?
)

interface PriceSnapshotReader {
    suspend fun read(regionId: CommercialId, now: Instant): PriceSnapshot?
}

internal class RoomPriceSnapshotCache(
    private val database: NutriNestDatabase,
    val source: String = "legacy"
) : PriceSnapshotReader {
    private val dao = database.priceSnapshotDao()

    override suspend fun read(regionId: CommercialId, now: Instant): PriceSnapshot? {
        return readRow(dao.find(regionId.value, source), now)
    }

    suspend fun readPostal(postalCode: String, now: Instant): PriceSnapshot? =
        readRow(dao.findPostal(source, postalCode, now.toEpochMilli()).singleOrNull(), now)

    private suspend fun readRow(cached: CachedPriceSnapshot?, now: Instant): PriceSnapshot? {
        val regionId = cached?.regionId
        val payload = cached?.payload
        if (cached == null || payload == null || cached.expiresAtMillis <= now.toEpochMilli()) {
            PriceDiagnostics.log {
                val reason = when {
                    cached == null -> "miss"
                    payload == null -> "withdrawn_or_pruned"
                    else -> "expired"
                }
                "stage=room.read region=$regionId reason=$reason observations=0"
            }
            return null
        }
        return withContext(Dispatchers.Default) {
            SnapshotJson.snapshot(payload).takeIf {
                it.regionId.value == cached.regionId && it.revision == cached.revision &&
                    it.expiresAt.toEpochMilli() == cached.expiresAtMillis && now >= it.generatedAt
            }
        }?.also {
            PriceDiagnostics.log { "stage=room.read region=$regionId revision=${it.revision} observations=${it.prices.size}" }
        }
    }

    /** Installs a complete, authenticated-transport snapshot or leaves the current cache intact. */
    suspend fun install(manifestJson: ByteArray, snapshotJson: ByteArray, now: Instant): Boolean {
        val manifest = SnapshotJson.manifest(manifestJson)
        require(snapshotJson.size == manifest.byteLength)
        val bytes = snapshotJson.copyOf()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        verifySnapshotDigest(hash, manifest.sha256)
        val snapshot = SnapshotJson.snapshot(bytes)
        require(snapshot.regionId == manifest.regionId && snapshot.revision == manifest.revision &&
            snapshot.contractVersion == manifest.contractVersion &&
            snapshot.generatedAt == manifest.generatedAt && snapshot.expiresAt == manifest.expiresAt)
        return installVerified(snapshot, bytes, hash, now)
    }

    /** Caller has validated these exact bytes against the manifest over HTTPS. */
    internal suspend fun installVerified(snapshot: PriceSnapshot, bytes: ByteArray, hash: String, now: Instant): Boolean {
        val expiresAtMillis = snapshot.expiresAt.toEpochMilli()
        if (expiresAtMillis <= now.toEpochMilli() || snapshot.generatedAt > now) {
            PriceDiagnostics.log { "stage=room.install reason=expired saved=0" }
            return false
        }
        require(snapshot.prices.isNotEmpty()) { "Empty price publication" }
        val observedStores = snapshot.prices.mapNotNull {
            (it.record.scope as? ObservationScope.AtStore)?.storeId
        }.toSet()
        val postalCodes = snapshot.stores.filter { it.id in observedStores }
            .mapNotNull { it.postalCode }.filter(::validSpanishPostalCode).distinct()
        return database.withTransaction {
            val cached = dao.find(snapshot.regionId.value, source)
            if (cached != null && cached.revision >= snapshot.revision) {
                require(cached.revision != snapshot.revision || cached.sha256 == hash) {
                    "A price snapshot revision cannot change its contents"
                }
                return@withTransaction false
            }
            dao.put(CachedPriceSnapshot(source, snapshot.regionId.value, snapshot.revision, expiresAtMillis, hash, bytes))
            dao.clearPostal(source, snapshot.regionId.value)
            dao.putPostal(postalCodes.map { CachedPricePostal(source, snapshot.regionId.value, it) })
            true
        }.also { installed ->
            PriceDiagnostics.log { "stage=room.install region=${snapshot.regionId.value} revision=${snapshot.revision} saved=${if (installed) snapshot.prices.size else 0} installed=$installed" }
        }
    }

    suspend fun pruneExpired(now: Instant): Int = dao.pruneExpired(now.toEpochMilli())

    suspend fun withdraw(regionId: CommercialId, revision: Long) = dao.withdraw(source, regionId.value, revision)

    suspend fun recordSync(region: CommercialId?, started: Instant, completed: Instant, error: String?) {
        val key = region?.value.orEmpty()
        database.withTransaction {
            val old = dao.sync(source, key)
            val attempt = started.toEpochMilli()
            val success = if (error == null) maxOf(old?.lastSuccessMillis ?: 0, completed.toEpochMilli())
                else old?.lastSuccessMillis
            if (old == null || attempt >= old.lastAttemptMillis) {
                dao.putSync(PriceSnapshotSync(source, key, attempt, success, error))
            } else if (success != old.lastSuccessMillis) {
                dao.putSync(old.copy(lastSuccessMillis = success))
            }
        }
    }
}
