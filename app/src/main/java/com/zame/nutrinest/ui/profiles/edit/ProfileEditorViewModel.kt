package com.zame.nutrinest.ui.profiles.edit

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.R
import com.zame.nutrinest.data.local.entity.ActivityLevel
import com.zame.nutrinest.data.local.entity.BiologicalSex
import com.zame.nutrinest.data.local.entity.BmrFormula
import com.zame.nutrinest.data.local.entity.Goal
import com.zame.nutrinest.data.local.entity.MealSlot
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.ProfileMealPlanEntity
import com.zame.nutrinest.data.local.entity.UnitSystem
import com.zame.nutrinest.data.local.entity.WeightLossRate
import com.zame.nutrinest.data.repository.ProfileRepository
import com.zame.nutrinest.domain.nutrition.NutritionCalculator
import com.zame.nutrinest.domain.nutrition.NutritionTargets
import com.zame.nutrinest.domain.nutrition.UnitConversions
import com.zame.nutrinest.util.LocalizedNumbers
import com.zame.nutrinest.util.toLocalizedDoubleOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** What is wrong with the form, as a code the UI layer translates. */
enum class ProfileFormError(@param:StringRes val messageRes: Int) {
    NAME_REQUIRED(R.string.profile_error_name),
    DOB_REQUIRED(R.string.profile_error_dob),
    DOB_IN_FUTURE(R.string.profile_error_dob_future),
    HEIGHT_INVALID(R.string.profile_error_height),
    WEIGHT_INVALID(R.string.profile_error_weight),
    BODY_FAT_INVALID(R.string.profile_error_body_fat),
    MEAL_SPLIT_INVALID(R.string.profile_error_meal_split),
    SAVE_FAILED(R.string.error_save_failed)
}

data class ProfileEditorState(
    val editingId: Long? = null,
    val name: String = "",
    val dateOfBirth: Long? = null,
    val sex: BiologicalSex = BiologicalSex.MALE,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    // Raw text fields so partial input ("17", "17.") doesn't fight the user.
    val heightText: String = "",   // cm, or feet when imperial
    val heightInchesText: String = "", // inches part, imperial only
    val weightText: String = "",   // kg or lb depending on unit system
    val bodyFatText: String = "",
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    val goal: Goal = Goal.MAINTAIN,
    val weightLossRate: WeightLossRate = WeightLossRate.MODERATE,
    val formula: BmrFormula = BmrFormula.AUTO,
    val dietaryPreferences: String = "",
    val allergies: String = "",
    // ── How the day is split ────────────────────────────────────────────
    val mealsPerDay: Int = 3,
    /** Fraction of the daily target per slot; deliberately not normalised. */
    val slotShares: Map<MealSlot, Float> =
        MealSlot.defaultWeights(MealSlot.forMealsPerDay(3)),
    val freeSlots: Set<MealSlot> = emptySet(),
    val error: ProfileFormError? = null,
    val saved: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false
) {
    val slots: List<MealSlot> get() = MealSlot.forMealsPerDay(mealsPerDay)

    /** Share of the day the plan actually puts on a plate. */
    val plannedShare: Float
        get() = slots.filterNot { it in freeSlots }.sumOf {
            (slotShares[it] ?: 0f).toDouble()
        }.toFloat()

    /** Everything assigned, free slots included — should come to 100%. */
    val totalShare: Float
        get() = slots.sumOf { (slotShares[it] ?: 0f).toDouble() }.toFloat()

    /** Small rounding differences are harmless, but a day must still add up. */
    val isMealSplitValid: Boolean
        get() = totalShare.isFinite() && totalShare in 0.95f..1.05f

    /**
     * Absolute budget of a slot in kcal. Free slots keep theirs on paper —
     * that is what makes them untracked rather than redistributed.
     */
    fun slotKcal(slot: MealSlot): Int? =
        liveTargets?.let { ((slotShares[slot] ?: 0f) * it.dailyCalories).toInt() }

    fun mealPlanRows(profileId: Long = 0): List<ProfileMealPlanEntity> = slots.map { slot ->
        ProfileMealPlanEntity(
            profileId = profileId,
            slot = slot,
            share = slotShares[slot] ?: 0f,
            isFree = slot in freeSlots
        )
    }

    /** Height in cm parsed from the current inputs, or null if invalid. */
    val heightCm: Double?
        get() = when (unitSystem) {
            UnitSystem.METRIC -> heightText.toLocalizedDoubleOrNull()?.takeIf { it in 50.0..280.0 }
            UnitSystem.IMPERIAL -> {
                val feet = heightText.toIntOrNull() ?: return null
                val inches = heightInchesText.toLocalizedDoubleOrNull() ?: 0.0
                UnitConversions.feetInchesToCm(feet, inches).takeIf { it in 50.0..280.0 }
            }
        }

    /** Weight in kg parsed from current inputs, or null if invalid. */
    val weightKg: Double?
        get() {
            val raw = weightText.toLocalizedDoubleOrNull() ?: return null
            val kg = when (unitSystem) {
                UnitSystem.METRIC -> raw
                UnitSystem.IMPERIAL -> UnitConversions.lbToKg(raw)
            }
            return kg.takeIf { it in 20.0..400.0 }
        }

    val bodyFatPercent: Double?
        get() = bodyFatText.toLocalizedDoubleOrNull()?.takeIf { it in 3.0..70.0 }

    /** Live targets whenever there's enough valid data; null otherwise. */
    val liveTargets: NutritionTargets?
        get() {
            val h = heightCm ?: return null
            val w = weightKg ?: return null
            val dob = dateOfBirth ?: return null
            return NutritionCalculator.targetsFor(
                ProfileEntity(
                    name = name,
                    dateOfBirth = dob,
                    sex = sex,
                    heightCm = h,
                    weightKg = w,
                    bodyFatPercent = bodyFatPercent,
                    activityLevel = activityLevel,
                    goal = goal,
                    weightLossRate = weightLossRate,
                    formula = formula
                )
            )
        }
}

class ProfileEditorViewModel(
    private val repository: ProfileRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileEditorState())
    val state = _state.asStateFlow()
    private var original: ProfileEntity? = null

    init {
        val profileId: Long = savedStateHandle["profileId"] ?: -1L
        if (profileId > 0) {
            loadProfile(profileId)
            loadMealPlan(profileId)
        }
    }

    /**
     * Only the first emission is taken: after that the sliders belong to the
     * user, and echoing the database back would fight them mid-drag.
     */
    private fun loadMealPlan(id: Long) {
        viewModelScope.launch {
            val rows = runCatching { repository.observeMealPlan(id).first() }
                .getOrElse { return@launch }
            if (rows.isEmpty()) return@launch
            update {
                copy(
                    mealsPerDay = rows.size,
                    slotShares = rows.associate { it.slot to it.share },
                    freeSlots = rows.filter { it.isFree }.map { it.slot }.toSet()
                )
            }
        }
    }

    private fun loadProfile(id: Long) {
        update { copy(isLoading = true) }
        viewModelScope.launch {
            val profile = runCatching { repository.observeById(id).first() }.getOrNull()
            if (profile == null) {
                update { copy(isLoading = false, error = ProfileFormError.SAVE_FAILED) }
                return@launch
            }
            original = profile
            update {
                val (feet, inches) = UnitConversions.cmToFeetInches(profile.heightCm)
                copy(
                    editingId = profile.id,
                    name = profile.name,
                    dateOfBirth = profile.dateOfBirth,
                    sex = profile.sex,
                    unitSystem = profile.unitSystem,
                    heightText = when (profile.unitSystem) {
                        UnitSystem.METRIC -> trimNumber(profile.heightCm)
                        UnitSystem.IMPERIAL -> feet.toString()
                    },
                    heightInchesText = when (profile.unitSystem) {
                        UnitSystem.METRIC -> ""
                        UnitSystem.IMPERIAL -> trimNumber(inches)
                    },
                    weightText = when (profile.unitSystem) {
                        UnitSystem.METRIC -> trimNumber(profile.weightKg)
                        UnitSystem.IMPERIAL -> trimNumber(UnitConversions.kgToLb(profile.weightKg))
                    },
                    bodyFatText = profile.bodyFatPercent?.let(::trimNumber) ?: "",
                    activityLevel = profile.activityLevel,
                    goal = profile.goal,
                    weightLossRate = profile.weightLossRate,
                    formula = profile.formula,
                    dietaryPreferences = profile.dietaryPreferences ?: "",
                    allergies = profile.allergies ?: "",
                    isLoading = false
                )
            }
        }
    }

    fun onNameChange(v: String) = update { copy(name = v) }
    fun onDateOfBirthChange(v: Long) = update { copy(dateOfBirth = v) }
    fun onSexChange(v: BiologicalSex) = update { copy(sex = v) }
    fun onHeightChange(v: String) = update { copy(heightText = v) }
    fun onHeightInchesChange(v: String) = update { copy(heightInchesText = v) }
    fun onWeightChange(v: String) = update { copy(weightText = v) }
    fun onBodyFatChange(v: String) = update { copy(bodyFatText = v) }
    fun onActivityChange(v: ActivityLevel) = update { copy(activityLevel = v) }
    fun onGoalChange(v: Goal) = update { copy(goal = v) }
    fun onWeightLossRateChange(v: WeightLossRate) = update { copy(weightLossRate = v) }
    fun onFormulaChange(v: BmrFormula) = update { copy(formula = v) }
    fun onDietaryChange(v: String) = update { copy(dietaryPreferences = v) }
    fun onAllergiesChange(v: String) = update { copy(allergies = v) }
    fun dismissError() = update { copy(error = null) }

    fun onMealsPerDayChange(count: Int) = update {
        val slots = MealSlot.forMealsPerDay(count)
        copy(
            mealsPerDay = count,
            slotShares = MealSlot.defaultWeights(slots),
            freeSlots = freeSlots.filter { it in slots }.toSet()
        )
    }

    fun onSlotShareChange(slot: MealSlot, share: Float) = update {
        copy(slotShares = slotShares + (slot to share.coerceIn(0f, 1f)))
    }

    /**
     * Marking a slot free does not touch the other shares: the calories that
     * belonged to it stay unplanned rather than being pushed onto the meals
     * that are still tracked.
     */
    fun onSlotFreeToggle(slot: MealSlot) = update {
        copy(freeSlots = if (slot in freeSlots) freeSlots - slot else freeSlots + slot)
    }

    /** Switching units converts the current values instead of clearing them. */
    fun onUnitSystemChange(target: UnitSystem) {
        val s = _state.value
        if (target == s.unitSystem) return
        val heightCm = s.heightCm
        val weightKg = s.weightKg
        update {
            when (target) {
                UnitSystem.METRIC -> copy(
                    unitSystem = target,
                    heightText = heightCm?.let(::trimNumber) ?: "",
                    heightInchesText = "",
                    weightText = weightKg?.let(::trimNumber) ?: ""
                )
                UnitSystem.IMPERIAL -> {
                    val (feet, inches) = heightCm
                        ?.let { UnitConversions.cmToFeetInches(it) }
                        ?: (0 to 0.0)
                    copy(
                        unitSystem = target,
                        heightText = if (heightCm != null) feet.toString() else "",
                        heightInchesText = if (heightCm != null) trimNumber(inches) else "",
                        weightText = weightKg
                            ?.let { trimNumber(UnitConversions.kgToLb(it)) } ?: ""
                    )
                }
            }
        }
    }

    fun save() {
        if (_state.value.isSaving || _state.value.isLoading) return
        val s = _state.value
        viewModelScope.launch {
            val error = when {
                s.name.isBlank() -> ProfileFormError.NAME_REQUIRED
                s.dateOfBirth == null -> ProfileFormError.DOB_REQUIRED
                s.dateOfBirth > System.currentTimeMillis() -> ProfileFormError.DOB_IN_FUTURE
                s.heightCm == null -> ProfileFormError.HEIGHT_INVALID
                s.weightKg == null -> ProfileFormError.WEIGHT_INVALID
                s.bodyFatText.isNotBlank() && s.bodyFatPercent == null ->
                    ProfileFormError.BODY_FAT_INVALID
                !s.isMealSplitValid -> ProfileFormError.MEAL_SPLIT_INVALID
                else -> null
            }
            if (error != null) {
                update { copy(error = error) }
                return@launch
            }

            val entity = ProfileEntity(
                id = s.editingId ?: 0,
                name = s.name.trim(),
                dateOfBirth = s.dateOfBirth!!,
                sex = s.sex,
                heightCm = s.heightCm!!,
                weightKg = s.weightKg!!,
                bodyFatPercent = s.bodyFatPercent,
                activityLevel = s.activityLevel,
                goal = s.goal,
                weightLossRate = s.weightLossRate,
                formula = s.formula,
                unitSystem = s.unitSystem,
                dietaryPreferences = s.dietaryPreferences.trim().ifEmpty { null },
                allergies = s.allergies.trim().ifEmpty { null },
                colorIndex = original?.colorIndex ?: (System.currentTimeMillis().toInt() and Int.MAX_VALUE) % 4,
                createdAt = original?.createdAt ?: System.currentTimeMillis(),
                remoteId = original?.remoteId
            )
            update { copy(isSaving = true) }
            runCatching {
                val mealPlan = s.mealPlanRows(entity.id)
                if (s.editingId == null) repository.create(entity, mealPlan)
                else repository.update(entity, mealPlan)
            }.onSuccess {
                update { copy(isSaving = false, saved = true) }
            }.onFailure {
                update { copy(isSaving = false, error = ProfileFormError.SAVE_FAILED) }
            }
        }
    }

    private fun trimNumber(v: Double): String =
        LocalizedNumbers.format(v)

    private inline fun update(block: ProfileEditorState.() -> ProfileEditorState) {
        _state.value = _state.value.block()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                ProfileEditorViewModel(
                    app.container.profileRepository,
                    createSavedStateHandle()
                )
            }
        }
    }
}
