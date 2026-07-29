package com.zame.nutrinest

import android.app.Application
import com.zame.nutrinest.data.local.NutriNestDatabase
import com.zame.nutrinest.data.repository.IngredientRepository
import com.zame.nutrinest.data.repository.LocalIngredientRepository
import com.zame.nutrinest.data.repository.LocalMenuRepository
import com.zame.nutrinest.data.repository.LocalProfileRepository
import com.zame.nutrinest.data.repository.LocalRecipeRepository
import com.zame.nutrinest.data.repository.MenuRepository
import com.zame.nutrinest.data.repository.ProfileRepository
import com.zame.nutrinest.data.repository.RecipeRepository
import com.zame.nutrinest.notifications.MenuReminderWorker

class AppContainer(app: Application) {
    private val database = NutriNestDatabase.get(app)

    val ingredientRepository: IngredientRepository =
        LocalIngredientRepository(database.ingredientDao())

    val recipeRepository: RecipeRepository =
        LocalRecipeRepository(database.recipeDao())

    val profileRepository: ProfileRepository =
        LocalProfileRepository(database.profileDao())

    val menuRepository: MenuRepository =
        LocalMenuRepository(database.menuDao())
}

class NutriNestApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        MenuReminderWorker.schedule(this)
    }
}
