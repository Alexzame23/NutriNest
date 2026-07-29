package com.zame.nutrinest.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.PlannedMealWithRecipe
import com.zame.nutrinest.data.local.entity.WeeklyMenuEntity
import com.zame.nutrinest.data.repository.MenuRepository
import com.zame.nutrinest.util.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class WeeklyMenuUiState(
    val weekStart: Long = WeekUtils.weekStartOf(),
    val isCurrentWeek: Boolean = true,
    val menu: WeeklyMenuEntity? = null,
    /** dayIndex (0=Mon) → meals sorted by slot. */
    val mealsByDay: Map<Int, List<PlannedMealWithRecipe>> = emptyMap(),
    val todayIndex: Int = WeekUtils.todayIndex()
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyMenuViewModel(
    private val repository: MenuRepository
) : ViewModel() {

    /** Which week is being viewed: current or next. */
    private val viewingWeekStart = MutableStateFlow(WeekUtils.weekStartOf())
    val weekStart = viewingWeekStart.asStateFlow()

    val uiState = viewingWeekStart
        .flatMapLatest { weekStart ->
            repository.observeMenu(weekStart).flatMapLatest { menu ->
                if (menu == null) {
                    flowOf(
                        WeeklyMenuUiState(
                            weekStart = weekStart,
                            isCurrentWeek = weekStart == WeekUtils.weekStartOf()
                        )
                    )
                } else {
                    repository.observeMeals(menu.id).combine(flowOf(menu)) { meals, m ->
                        WeeklyMenuUiState(
                            weekStart = weekStart,
                            isCurrentWeek = weekStart == WeekUtils.weekStartOf(),
                            menu = m,
                            mealsByDay = meals
                                .groupBy { it.meal.dayOfWeek }
                                .mapValues { (_, dayMeals) ->
                                    dayMeals.sortedBy { it.meal.slot.ordinal }
                                }
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyMenuUiState())

    fun showCurrentWeek() { viewingWeekStart.value = WeekUtils.weekStartOf() }
    fun showNextWeek() { viewingWeekStart.value = WeekUtils.nextWeekStart() }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                WeeklyMenuViewModel(app.container.menuRepository)
            }
        }
    }
}
