package com.zame.nutrinest.data.prices

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.withTransaction
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.PriceSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant

@Entity(tableName = "price_snapshot_cache")
data class CachedPriceSnapshot(
    @PrimaryKey val regionId: String,
    val revision: Long,
    val expiresAtMillis: Long,
    val sha256: String,
    val payload: ByteArray?
)

interface PriceSnapshotReader {
    suspend fun read(regionId: CommercialId, now: Instant): PriceSnapshot?
}

internal class RoomPriceSnapshotCache(private val database: NutriNestDatabase) : PriceSnapshotReader {
    private val dao = database.priceSnapshotDao()

    override suspend fun read(regionId: CommercialId, now: Instant): PriceSnapshot? {
        val cached = dao.find(regionId.value)
        val payload = cached?.payload
        if (cached == null || payload == null || cached.expiresAtMillis <= now.toEpochMilli()) {
            PriceDiagnostics.log {
                val reason = when {
                    cached == null -> "miss"
                    payload == null -> "withdrawn_or_pruned"
                    else -> "expired"
                }
                "stage=room.read region=${regionId.value} reason=$reason observations=0"
            }
            return null
        }
        return withContext(Dispatchers.Default) { SnapshotJson.snapshot(payload) }.also {
            PriceDiagnostics.log { "stage=room.read region=${regionId.value} revision=${it.revision} observations=${it.prices.size}" }
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
        if (expiresAtMillis <= now.toEpochMilli()) {
            PriceDiagnostics.log { "stage=room.install reason=expired saved=0" }
            return false
        }
        return database.withTransaction {
            val cached = dao.find(snapshot.regionId.value)
            if (cached != null && cached.revision >= snapshot.revision) {
                require(cached.revision != snapshot.revision || cached.sha256 == hash) {
                    "A price snapshot revision cannot change its contents"
                }
                return@withTransaction false
            }
            dao.put(CachedPriceSnapshot(snapshot.regionId.value, snapshot.revision, expiresAtMillis, hash, bytes))
            true
        }.also { installed ->
            PriceDiagnostics.log { "stage=room.install region=${snapshot.regionId.value} revision=${snapshot.revision} saved=${if (installed) snapshot.prices.size else 0} installed=$installed" }
        }
    }

    suspend fun pruneExpired(now: Instant): Int = dao.pruneExpired(now.toEpochMilli())

    suspend fun withdraw(regionId: CommercialId, revision: Long) = dao.withdraw(regionId.value, revision)
}
