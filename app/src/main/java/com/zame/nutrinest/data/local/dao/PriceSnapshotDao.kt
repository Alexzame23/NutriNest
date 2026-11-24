package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zame.nutrinest.data.prices.CachedPriceSnapshot
import com.zame.nutrinest.data.prices.CachedPricePostal
import com.zame.nutrinest.data.prices.PriceSnapshotSync

@Dao
interface PriceSnapshotDao {
    @Query("SELECT * FROM price_snapshot_cache WHERE regionId = :regionId AND source = :source")
    suspend fun find(regionId: String, source: String = "legacy"): CachedPriceSnapshot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(snapshot: CachedPriceSnapshot)

    @Query("""
        SELECT c.* FROM price_snapshot_cache c JOIN price_snapshot_postal p
            ON c.source = p.source AND c.regionId = p.regionId
        WHERE p.source = :source AND p.postalCode = :postalCode
            AND c.expiresAtMillis > :now AND c.payload IS NOT NULL LIMIT 2
    """)
    suspend fun findPostal(source: String, postalCode: String, now: Long): List<CachedPriceSnapshot>

    @Query("DELETE FROM price_snapshot_postal WHERE source = :source AND regionId = :region")
    suspend fun clearPostal(source: String, region: String)

    @Insert
    suspend fun putPostal(rows: List<CachedPricePostal>)

    @Query("SELECT * FROM price_snapshot_sync WHERE source = :source AND regionId = :region")
    suspend fun sync(source: String, region: String): PriceSnapshotSync?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSync(row: PriceSnapshotSync)

    @Query("""
        UPDATE price_snapshot_cache SET payload = NULL WHERE rowid IN (
            SELECT rowid FROM price_snapshot_cache WHERE expiresAtMillis <= :now AND payload IS NOT NULL
            ORDER BY expiresAtMillis LIMIT 32
        )
    """)
    suspend fun pruneExpired(now: Long): Int

    @Query("UPDATE price_snapshot_cache SET payload = NULL WHERE source = :source AND regionId = :regionId AND revision = :revision")
    suspend fun withdraw(source: String, regionId: String, revision: Long)
}
