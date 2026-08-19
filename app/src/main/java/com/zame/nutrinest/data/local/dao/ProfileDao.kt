package com.zame.nutrinest.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.ProfileMealPlanEntity
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

    // ── How this person splits their day ────────────────────────────────

    @Query("SELECT * FROM profile_meal_plans WHERE profileId = :profileId")
    fun observeMealPlan(profileId: Long): Flow<List<ProfileMealPlanEntity>>

    @Query("SELECT * FROM profile_meal_plans WHERE profileId = :profileId")
    suspend fun getMealPlan(profileId: Long): List<ProfileMealPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMealPlan(rows: List<ProfileMealPlanEntity>)

    @Query("DELETE FROM profile_meal_plans WHERE profileId = :profileId")
    suspend fun clearMealPlan(profileId: Long)

    /** Swaps a profile's whole distribution, so dropping a slot really drops it. */
    @Transaction
    suspend fun replaceMealPlan(profileId: Long, rows: List<ProfileMealPlanEntity>) {
        clearMealPlan(profileId)
        insertMealPlan(rows.map { it.copy(profileId = profileId) })
    }

    /**
     * Insert a profile, seed its first weight-history point and store how it
     * splits the day — atomically, so a profile is never half set up.
     */
    @Transaction
    suspend fun insertWithInitialWeight(
        profile: ProfileEntity,
        mealPlan: List<ProfileMealPlanEntity>
    ): Long {
        val id = insert(profile)
        insertWeightEntry(WeightEntryEntity(profileId = id, weightKg = profile.weightKg))
        if (mealPlan.isNotEmpty()) insertMealPlan(mealPlan.map { it.copy(profileId = id) })
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
