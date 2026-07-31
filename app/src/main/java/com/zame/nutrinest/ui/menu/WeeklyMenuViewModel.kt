package com.zame.nutrinest.ui.menu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.PlannedMealWithRecipe
import com.zame.nutrinest.data.local.entity.ProfileEntity
import com.zame.nutrinest.data.local.entity.WeeklyMenuEntity
import com.zame.nutrinest.data.repository.MenuRepository
import com.zame.nutrinest.domain.planner.MenuResolver
import com.zame.nutrinest.util.WeekUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeeklyMenuUiState(
    val weekStart: Long = WeekUtils.weekStartOf(),
    val isCurrentWeek: Boolean = true,
    val menu: WeeklyMenuEntity? = null,
    /** dayIndex (0=Mon) → meals sorted by slot, then by course within it. */
    val mealsByDay: Map<Int, List<PlannedMealWithRecipe>> = emptyMap(),
    val todayIndex: Int = WeekUtils.todayIndex(),
    val profiles: List<ProfileEntity> = emptyList(),
    /** null = the household view, showing every version of every meal. */
    val selectedProfileId: Long? = null
) {
    fun nameOf(profileId: Long): String =
        profiles.find { it.id == profileId }?.name.orEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class WeeklyMenuViewModel(
    private val repository: MenuRepository
) : ViewModel() {

    /** Which week is being viewed: current or next. */
    private val viewingWeekStart = MutableStateFlow(WeekUtils.weekStartOf())
    val weekStart = viewingWeekStart.asStateFlow()

    /** Whose menu is being viewed; null shows the whole household's. */
    private val selectedProfileId = MutableStateFlow<Long?>(null)

    private val inputs = combine(
        viewingWeekStart,
        selectedProfileId,
        repository.observeProfiles()
    ) { week, profileId, profiles ->
        // A filter pointing at a deleted profile silently falls back to all.
        Triple(week, profileId?.takeIf { id -> profiles.any { it.id == id } }, profiles)
    }

    val uiState = inputs
        .flatMapLatest { (weekStart, profileId, profiles) ->
            val empty = WeeklyMenuUiState(
                weekStart = weekStart,
                isCurrentWeek = weekStart == WeekUtils.weekStartOf(),
                profiles = profiles,
                selectedProfileId = profileId
            )
            repository.observeMenu(weekStart).flatMapLatest { menu ->
                if (menu == null) {
                    flowOf(empty)
                } else {
                    repository.observeMeals(menu.id).map { meals ->
                        val visible = if (profileId == null) meals
                        else MenuResolver.forProfile(meals, profileId) { it.meal }
                        empty.copy(
                            menu = menu,
                            mealsByDay = visible
                                .groupBy { it.meal.dayOfWeek }
                                .mapValues { (_, dayMeals) ->
                                    dayMeals.sortedWith(
                                        compareBy<PlannedMealWithRecipe>(
                                            { it.meal.slot.ordinal },
                                            { it.meal.course.ordinal },
                                            { it.meal.profileId ?: -1L }
                                        )
                                    )
                                }
                        )
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyMenuUiState())

    fun showCurrentWeek() { viewingWeekStart.value = WeekUtils.weekStartOf() }
    fun showNextWeek() { viewingWeekStart.value = WeekUtils.nextWeekStart() }
    fun selectProfile(profileId: Long?) { selectedProfileId.value = profileId }

    /** Wipes whichever weeks the user picked, meals and shopping list alike. */
    fun eraseWeeks(currentWeek: Boolean, nextWeek: Boolean) {
        val weeks = buildList {
            if (currentWeek) add(WeekUtils.weekStartOf())
            if (nextWeek) add(WeekUtils.nextWeekStart())
        }
        if (weeks.isEmpty()) return
        viewModelScope.launch { repository.eraseWeeks(weeks) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                WeeklyMenuViewModel(app.container.menuRepository)
            }
        }
    }
}
