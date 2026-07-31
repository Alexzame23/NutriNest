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
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.UnitSystem
import com.zame.nutrinest.data.local.entity.WeightLossRate
import com.zame.nutrinest.data.repository.ProfileRepository
import com.zame.nutrinest.domain.nutrition.NutritionCalculator
import com.zame.nutrinest.domain.nutrition.NutritionTargets
import com.zame.nutrinest.domain.nutrition.UnitConversions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What is wrong with the form, as a code the UI layer translates. */
enum class ProfileFormError(@StringRes val messageRes: Int) {
    NAME_REQUIRED(R.string.profile_error_name),
    DOB_REQUIRED(R.string.profile_error_dob),
    DOB_IN_FUTURE(R.string.profile_error_dob_future),
    HEIGHT_INVALID(R.string.profile_error_height),
    WEIGHT_INVALID(R.string.profile_error_weight),
    BODY_FAT_INVALID(R.string.profile_error_body_fat)
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
    val error: ProfileFormError? = null,
    val saved: Boolean = false,
    val isLoading: Boolean = false
) {
    /** Height in cm parsed from the current inputs, or null if invalid. */
    val heightCm: Double?
        get() = when (unitSystem) {
            UnitSystem.METRIC -> heightText.toDoubleOrNull()?.takeIf { it in 50.0..280.0 }
            UnitSystem.IMPERIAL -> {
                val feet = heightText.toIntOrNull() ?: return null
                val inches = heightInchesText.toDoubleOrNull() ?: 0.0
                UnitConversions.feetInchesToCm(feet, inches).takeIf { it in 50.0..280.0 }
            }
        }

    /** Weight in kg parsed from current inputs, or null if invalid. */
    val weightKg: Double?
        get() {
            val raw = weightText.toDoubleOrNull() ?: return null
            val kg = when (unitSystem) {
                UnitSystem.METRIC -> raw
                UnitSystem.IMPERIAL -> UnitConversions.lbToKg(raw)
            }
            return kg.takeIf { it in 20.0..400.0 }
        }

    val bodyFatPercent: Double?
        get() = bodyFatText.toDoubleOrNull()?.takeIf { it in 3.0..70.0 }

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

    init {
        val profileId: Long = savedStateHandle["profileId"] ?: -1L
        if (profileId > 0) loadProfile(profileId)
    }

    private fun loadProfile(id: Long) {
        update { copy(isLoading = true) }
        viewModelScope.launch {
            repository.observeById(id).collect { profile ->
                if (profile != null && _state.value.editingId == null) {
                    update {
                        val (feet, inches) =
                            UnitConversions.cmToFeetInches(profile.heightCm)
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
                                UnitSystem.IMPERIAL ->
                                    trimNumber(UnitConversions.kgToLb(profile.weightKg))
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
                colorIndex = (s.editingId ?: System.currentTimeMillis()).toInt().let {
                    kotlin.math.abs(it) % 4
                }
            )
            if (s.editingId == null) repository.create(entity)
            else repository.update(entity)
            update { copy(saved = true) }
        }
    }

    private fun trimNumber(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

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
