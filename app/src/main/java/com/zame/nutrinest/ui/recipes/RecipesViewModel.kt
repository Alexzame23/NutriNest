package com.zame.nutrinest.ui.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zame.nutrinest.NutriNestApp
import com.zame.nutrinest.data.local.entity.RecipeEntity
import com.zame.nutrinest.data.repository.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RecipesUiState(
    val recipes: List<RecipeEntity> = emptyList(),
    val folders: List<String> = emptyList(),
    val selectedFolder: String? = null,
    val selectedCategory: String? = null,
    val isEmpty: Boolean = false
)

class RecipesViewModel(
    repository: RecipeRepository
) : ViewModel() {

    private val selectedFolder = MutableStateFlow<String?>(null)
    private val selectedCategory = MutableStateFlow<String?>(null)

    val uiState = combine(
        repository.observeAll(),
        repository.observeFolders(),
        selectedFolder,
        selectedCategory
    ) { recipes, folders, folder, category ->
        val filtered = recipes.filter { recipe ->
            (folder == null || recipe.folder == folder) &&
                (category == null || recipe.category == category)
        }
        RecipesUiState(
            recipes = filtered,
            folders = folders,
            selectedFolder = folder,
            selectedCategory = category,
            isEmpty = recipes.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipesUiState())

    fun onFolderSelected(folder: String?) {
        selectedFolder.value = if (selectedFolder.value == folder) null else folder
    }

    fun onCategorySelected(category: String?) {
        selectedCategory.value = if (selectedCategory.value == category) null else category
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriNestApp
                RecipesViewModel(app.container.recipeRepository)
            }
        }
    }
}
