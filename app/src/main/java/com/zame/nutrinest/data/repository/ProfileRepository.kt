package com.zame.nutrinest.data.repository

import com.zame.nutrinest.data.local.dao.ProfileDao
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.ProfileMealPlanEntity
import com.zame.nutrinest.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Same seam pattern as recipes/ingredients: when cloud sync arrives,
 * a synced implementation replaces the local one behind this interface.
 */
interface ProfileRepository {
    fun observeAll(): Flow<List<ProfileEntity>>
    fun observeById(id: Long): Flow<ProfileEntity?>
    fun observeWeightHistory(profileId: Long): Flow<List<WeightEntryEntity>>

    /** How this person splits their daily calories, and which slots are free. */
    fun observeMealPlan(profileId: Long): Flow<List<ProfileMealPlanEntity>>

    suspend fun create(
        profile: ProfileEntity,
        mealPlan: List<ProfileMealPlanEntity> = emptyList()
    ): Long

    suspend fun update(profile: ProfileEntity, mealPlan: List<ProfileMealPlanEntity>? = null)
    suspend fun delete(profile: ProfileEntity)
}

class LocalProfileRepository(private val dao: ProfileDao) : ProfileRepository {
    override fun observeAll() = dao.observeAll()
    override fun observeById(id: Long) = dao.observeById(id)
    override fun observeWeightHistory(profileId: Long) = dao.observeWeightHistory(profileId)
    override fun observeMealPlan(profileId: Long) = dao.observeMealPlan(profileId)

    override suspend fun create(profile: ProfileEntity, mealPlan: List<ProfileMealPlanEntity>) =
        dao.insertWithInitialWeight(profile, mealPlan)

    override suspend fun update(profile: ProfileEntity, mealPlan: List<ProfileMealPlanEntity>?) {
        dao.updateTrackingWeight(profile)
        if (mealPlan != null) dao.replaceMealPlan(profile.id, mealPlan)
    }

    override suspend fun delete(profile: ProfileEntity) = dao.delete(profile)
}
