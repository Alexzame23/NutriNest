package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zame.nutrinest.data.prices.CachedPriceSnapshot

@Dao
interface PriceSnapshotDao {
    @Query("SELECT * FROM price_snapshot_cache WHERE regionId = :regionId")
    suspend fun find(regionId: String): CachedPriceSnapshot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(snapshot: CachedPriceSnapshot)

    @Query("UPDATE price_snapshot_cache SET payload = NULL WHERE expiresAtMillis <= :now AND payload IS NOT NULL")
    suspend fun pruneExpired(now: Long): Int

    @Query("UPDATE price_snapshot_cache SET payload = NULL WHERE regionId = :regionId AND revision = :revision")
    suspend fun withdraw(regionId: String, revision: Long)
}
