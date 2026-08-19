package com.zame.nutrinest.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.repository.ProfileRepository
import com.zame.nutrinest.domain.nutrition.NutritionCalculator
import com.zame.nutrinest.domain.nutrition.NutritionTargets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileListItem(
    val profile: ProfileEntity,
    val targets: NutritionTargets
)

class ProfilesViewModel(
    private val repository: ProfileRepository
) : ViewModel() {

    private val _operationFailed = MutableStateFlow(false)
    val operationFailed = _operationFailed.asStateFlow()

    val profiles = repository.observeAll()
        .map { list ->
            list.map { ProfileListItem(it, NutritionCalculator.targetsFor(it)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(profile: ProfileEntity) {
        viewModelScope.launch {
            runCatching { repository.delete(profile) }
                .onFailure { _operationFailed.value = true }
        }
    }

    fun dismissOperationError() {
        _operationFailed.value = false
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                ProfilesViewModel(app.container.profileRepository)
            }
        }
    }
}
