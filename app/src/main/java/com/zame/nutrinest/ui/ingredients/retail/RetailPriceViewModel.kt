package com.zame.nutrinest.ui.ingredients.retail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.prices.RetailHttpError
import com.zame.nutrinest.data.prices.RetailPriceRepository
import com.zame.nutrinest.data.prices.RetailResult
import com.zame.nutrinest.domain.stores.RetailerSelectionPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration

internal sealed interface RetailUiState {
    data object EnterArea : RetailUiState
    data object Loading : RetailUiState
    data object NoMatches : RetailUiState
    data object NoCoverage : RetailUiState
    data object Stale : RetailUiState
    data object Offline : RetailUiState
    data class Error(val httpStatus: Int? = null) : RetailUiState
    data class Content(val result: RetailResult.Offers) : RetailUiState
}

internal class RetailPriceViewModel(
    private val savedState: SavedStateHandle,
    private val repository: RetailPriceRepository,
    private val clock: Clock = Clock.systemUTC(),
    preferences: StateFlow<RetailerSelectionPreferences>? = null
) : ViewModel() {
    private val ingredientId = checkNotNull(savedState.get<Long>("ingredientId"))
    private val state = MutableStateFlow<RetailUiState>(RetailUiState.EnterArea)
    val uiState = state.asStateFlow()
    private var request: Job? = null
    private val initialArea = savedState.get<String>("postalCode").orEmpty()

    val lastPostalCode: String get() = savedState.get<String>("retailPostalCode") ?: initialArea

    init {
        if (lastPostalCode.isNotEmpty()) search(lastPostalCode)
        if (preferences != null) {
            viewModelScope.launch {
                preferences.drop(1).collect {
                    if (lastPostalCode.isNotEmpty()) search(lastPostalCode)
                }
            }
        }
    }

    fun search(input: String) {
        val postalCode = input.trim()
        if (request?.isActive == true && state.value == RetailUiState.Loading && postalCode == lastPostalCode) return
        savedState["retailPostalCode"] = postalCode
        request?.cancel()
        state.value = RetailUiState.Loading
        request = viewModelScope.launch {
            val result = try {
                repository.compare(ingredientId, postalCode, clock.instant())
            } catch (error: Exception) {
                state.value = when (error) {
                    is CancellationException -> throw error
                    is RetailHttpError -> RetailUiState.Error(error.status)
                    else -> RetailUiState.Error()
                }
                return@launch
            }
            state.value = when (result) {
                RetailResult.NoCoverage -> RetailUiState.NoCoverage
                RetailResult.NoMatches -> RetailUiState.NoMatches
                RetailResult.Stale -> RetailUiState.Stale
                RetailResult.Offline -> RetailUiState.Offline
                is RetailResult.Offers -> {
                    if (clock.instant() >= result.validUntil) RetailUiState.Stale
                    else RetailUiState.Content(result)
                }
            }
            if (state.value is RetailUiState.Content && result is RetailResult.Offers) {
                delay(Duration.between(clock.instant(), result.validUntil).toMillis().coerceAtLeast(0))
                state.value = RetailUiState.Stale
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                RetailPriceViewModel(createSavedStateHandle(), app.container.retailPrices,
                    preferences = app.container.retailerSelectionRepository.preferences)
            }
        }
    }
}
