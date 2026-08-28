package com.zame.nutrinest.ui.menu.intake

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.IntakeEntryEntity
import com.zame.nutrinest.data.local.entity.NutritionValues
import com.zame.nutrinest.data.local.entity.PlannedMealWithPortions
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.repository.MenuRepository
import com.zame.nutrinest.data.repository.RecipeRepository
import com.zame.nutrinest.domain.nutrition.NutritionCalculator
import com.zame.nutrinest.domain.planner.MenuResolver
import com.zame.nutrinest.util.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A planned dish today, with what this person is actually being served. */
data class PlannedToday(
    val row: PlannedMealWithPortions,
    val profileId: Long,
    val recipeKcal: Double
) {
    private val portion get() = row.portions.firstOrNull { it.profileId == profileId }

    val factor: Double get() = portion?.factor ?: 0.0
    val baseFactor: Double get() = portion?.baseFactor ?: 0.0
    val kcal: Double get() = recipeKcal * factor
    val plannedKcal: Double get() = recipeKcal * baseFactor
    val isFree: Boolean get() = row.meal.isFree || row.meal.recipeId == null
    val isAdjusted: Boolean get() = !isFree && kotlin.math.abs(factor - baseFactor) > 0.01
}

data class IntakeUiState(
    val profiles: List<ProfileEntity> = emptyList(),
    val selectedProfileId: Long? = null,
    val entries: List<IntakeEntryEntity> = emptyList(),
    val meals: List<PlannedToday> = emptyList(),
    val dailyTarget: Int = 0
) {
    val selectedProfile: ProfileEntity?
        get() = profiles.find { it.id == selectedProfileId }

    /** What the plan puts on a plate today; free slots contribute nothing. */
    val plannedKcal: Double get() = meals.filterNot { it.isFree }.sumOf { it.plannedKcal }

    val loggedKcal: Double get() = entries.sumOf { it.nutrition.kcal }

    /** After redistribution: what today's meals now add up to, plus the log. */
    val servedKcal: Double get() = meals.filterNot { it.isFree }.sumOf { it.kcal }

    val remainingKcal: Double get() = plannedKcal - loggedKcal - servedKcal

    /** True once the log has eaten the whole remaining budget and then some. */
    val isOverBudget: Boolean get() = loggedKcal > plannedKcal
}

/** Guards the database and rebalancer against negative, non-finite, or absurd input. */
internal fun isValidIntake(label: String, nutrition: NutritionValues): Boolean =
    label.isNotBlank() &&
        nutrition.kcal.isFinite() && nutrition.kcal > 0.0 && nutrition.kcal <= 100_000.0 &&
        listOf(nutrition.carbs, nutrition.fats, nutrition.protein).all {
            it.isFinite() && it in 0.0..10_000.0
        }

@OptIn(ExperimentalCoroutinesApi::class)
class IntakeViewModel(
    private val repository: MenuRepository,
    recipeRepository: RecipeRepository
) : ViewModel() {

    /** Always today: this is the screen for what is happening right now. */
    val dayStart: Long = WeekUtils.dayStartOf()
    private val dayIndex = WeekUtils.dayIndexOf(dayStart)
    private val weekStart = WeekUtils.weekStartOf(dayStart)

    private val selectedProfileId = MutableStateFlow<Long?>(null)

    private val nutritionByRecipe = recipeRepository.observeNutritionTotals()

    val uiState = combine(
        repository.observeProfiles(),
        selectedProfileId
    ) { profiles, selected ->
        // Defaults to the first profile, and recovers if that one is deleted.
        profiles to (selected?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.firstOrNull()?.id)
    }.flatMapLatest { (profiles, profileId) ->
        if (profileId == null) {
            flowOf(IntakeUiState(profiles = profiles))
        } else {
            combine(
                repository.observeIntake(profileId, dayStart),
                repository.observeDayMeals(weekStart, dayIndex),
                nutritionByRecipe
            ) { entries, dayMeals, nutrition ->
                val mine = MenuResolver.forProfile(dayMeals, profileId) { it.meal }
                IntakeUiState(
                    profiles = profiles,
                    selectedProfileId = profileId,
                    entries = entries,
                    meals = mine
                        .sortedBy { it.meal.slot.ordinal }
                        .map { row ->
                            PlannedToday(
                                row = row,
                                profileId = profileId,
                                recipeKcal = row.meal.recipeId
                                    ?.let { nutrition[it]?.kcal } ?: 0.0
                            )
                        },
                    dailyTarget = profiles.find { it.id == profileId }
                        ?.let { NutritionCalculator.targetsFor(it).dailyCalories } ?: 0
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IntakeUiState())

    fun selectProfile(profileId: Long) { selectedProfileId.value = profileId }

    /** Logging is per profile: nobody else's portions move. */
    fun log(label: String, kcal: Double, carbs: Double, fats: Double, protein: Double) {
        val profileId = uiState.value.selectedProfileId ?: return
        val nutrition = NutritionValues(kcal, carbs, fats, protein)
        if (!isValidIntake(label, nutrition)) return
        viewModelScope.launch {
            repository.logIntake(
                profileId = profileId,
                dayStart = dayStart,
                label = label.trim(),
                nutrition = nutrition
            )
        }
    }

    fun delete(entryId: Long) {
        val profileId = uiState.value.selectedProfileId ?: return
        viewModelScope.launch { repository.deleteIntake(entryId, profileId, dayStart) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                IntakeViewModel(app.container.menuRepository, app.container.recipeRepository)
            }
        }
    }
}
