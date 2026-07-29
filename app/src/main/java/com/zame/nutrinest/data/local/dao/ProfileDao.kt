package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY createdAt")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeById(id: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): ProfileEntity?

    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)

    @Insert
    suspend fun insertWeightEntry(entry: WeightEntryEntity)

    @Query("SELECT * FROM weight_entries WHERE profileId = :profileId ORDER BY recordedAt")
    fun observeWeightHistory(profileId: Long): Flow<List<WeightEntryEntity>>

    /** Insert a profile and seed its first weight-history point atomically. */
    @Transaction
    suspend fun insertWithInitialWeight(profile: ProfileEntity): Long {
        val id = insert(profile)
        insertWeightEntry(WeightEntryEntity(profileId = id, weightKg = profile.weightKg))
        return id
    }

    /** Update a profile; if the weight changed, append a history point. */
    @Transaction
    suspend fun updateTrackingWeight(profile: ProfileEntity) {
        val previous = getById(profile.id)
        update(profile)
        if (previous != null && previous.weightKg != profile.weightKg) {
            insertWeightEntry(
                WeightEntryEntity(profileId = profile.id, weightKg = profile.weightKg)
            )
        }
    }
}
