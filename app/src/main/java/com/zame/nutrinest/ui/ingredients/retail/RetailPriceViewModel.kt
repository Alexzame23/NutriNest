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
import com.zame.nutrinest.data.prices.PriceDiagnostics
import com.zame.nutrinest.data.prices.RetailPriceRepository
import com.zame.nutrinest.data.prices.RetailResult
import com.zame.nutrinest.data.prices.isRetailNetworkFailure
import com.zame.nutrinest.domain.stores.RetailerSelectionPreferences
import com.zame.nutrinest.domain.stores.validSpanishPostalCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    data object NoPricesInArea : RetailUiState
    data object NoBaseUrl : RetailUiState
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
    private var expiry: Job? = null
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
        if (!validSpanishPostalCode(postalCode)) {
            request?.cancel()
            expiry?.cancel()
            state.value = RetailUiState.EnterArea
            return
        }
        if (request?.isActive == true && postalCode == lastPostalCode) return
        val previousPostal = lastPostalCode
        savedState["retailPostalCode"] = postalCode
        request?.cancel()
        if (postalCode != previousPostal || state.value !is RetailUiState.Content) {
            expiry?.cancel()
            state.value = RetailUiState.Loading
        }
        request = viewModelScope.launch {
            val result = try {
                val cached = repository.cached(ingredientId, postalCode, clock.instant())
                ensureActive()
                if (cached is RetailResult.Offers) show(cached)
                repository.compare(ingredientId, postalCode, clock.instant())
            } catch (error: Exception) {
                ensureActive()
                expiry?.cancel()
                state.value = when (error) {
                    is CancellationException -> throw error
                    is RetailHttpError -> RetailUiState.Error(error.status)
                    else -> if (error.isRetailNetworkFailure()) RetailUiState.Offline else RetailUiState.Error()
                }
                PriceDiagnostics.log { "stage=ingredient.ui state=Error offers=0 error=${error.javaClass.simpleName}" }
                return@launch
            }
            ensureActive()
            expiry?.cancel()
            state.value = when (result) {
                RetailResult.NoCoverage -> RetailUiState.NoCoverage
                RetailResult.NoPricesInArea -> RetailUiState.NoPricesInArea
                RetailResult.ProviderError -> RetailUiState.Error()
                RetailResult.NoBaseUrl -> RetailUiState.NoBaseUrl
                RetailResult.NoMatches -> RetailUiState.NoMatches
                RetailResult.Stale -> RetailUiState.Stale
                RetailResult.Offline -> RetailUiState.Offline
                is RetailResult.Offers -> {
                    if (clock.instant() >= result.validUntil) RetailUiState.Stale
                    else RetailUiState.Content(result)
                }
            }
            PriceDiagnostics.log {
                val visible = state.value
                "stage=ingredient.ui state=${visible.javaClass.simpleName} offers=${(visible as? RetailUiState.Content)?.result?.rows?.size ?: 0}"
            }
            if (result is RetailResult.Offers) show(result)
        }
    }

    private fun show(result: RetailResult.Offers) {
        expiry?.cancel()
        if (clock.instant() >= result.validUntil) {
            state.value = RetailUiState.Stale
            return
        }
        state.value = RetailUiState.Content(result)
        expiry = viewModelScope.launch {
            delay(Duration.between(clock.instant(), result.validUntil).toMillis().coerceAtLeast(0))
            state.value = RetailUiState.Stale
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                val state = createSavedStateHandle()
                if (state.get<String>("postalCode").isNullOrEmpty()) state["postalCode"] = app.container.areaPreferences.recent.firstOrNull()
                RetailPriceViewModel(state, app.container.retailPrices,
                    preferences = app.container.retailerSelectionRepository.preferences)
            }
        }
    }
}
