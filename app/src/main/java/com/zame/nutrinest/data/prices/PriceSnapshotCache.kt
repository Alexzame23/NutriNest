package com.zame.nutrinest.data.prices

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.withTransaction
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.domain.prices.CommercialId
import com.zame.nutrinest.domain.prices.PriceSnapshot
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
        val cached = dao.find(regionId.value) ?: return null
        val payload = cached.payload ?: return null
        if (cached.expiresAtMillis <= now.toEpochMilli()) return null
        return SnapshotJson.snapshot(payload)
    }

    /** Installs a complete, authenticated-transport snapshot or leaves the current cache intact. */
    suspend fun install(manifestJson: ByteArray, snapshotJson: ByteArray, now: Instant): Boolean {
        val manifest = SnapshotJson.manifest(manifestJson)
        require(snapshotJson.size == manifest.byteLength)
        val bytes = snapshotJson.copyOf()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(hash == manifest.sha256) { "Price snapshot digest mismatch" }
        val snapshot = SnapshotJson.snapshot(bytes)
        require(snapshot.regionId == manifest.regionId && snapshot.revision == manifest.revision &&
            snapshot.contractVersion == manifest.contractVersion &&
            snapshot.generatedAt == manifest.generatedAt && snapshot.expiresAt == manifest.expiresAt)
        val expiresAtMillis = manifest.expiresAt.toEpochMilli()
        if (expiresAtMillis <= now.toEpochMilli()) return false
        return database.withTransaction {
            val cached = dao.find(manifest.regionId.value)
            if (cached != null && cached.revision >= manifest.revision) {
                require(cached.revision != manifest.revision || cached.sha256 == hash) {
                    "A price snapshot revision cannot change its contents"
                }
                return@withTransaction false
            }
            dao.put(CachedPriceSnapshot(manifest.regionId.value, manifest.revision, expiresAtMillis, hash, bytes))
            true
        }
    }

    suspend fun pruneExpired(now: Instant): Int = dao.pruneExpired(now.toEpochMilli())

    suspend fun withdraw(regionId: CommercialId, revision: Long) = dao.withdraw(regionId.value, revision)
}
