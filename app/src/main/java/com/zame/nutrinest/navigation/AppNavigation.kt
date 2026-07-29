package com.zame.nutrinest.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zame.nutrinest.ui.home.HomeScreen
import com.zame.nutrinest.ui.ingredients.CreateIngredientScreen
import com.zame.nutrinest.ui.menu.WeeklyMenuScreen
import com.zame.nutrinest.ui.menu.config.MenuConfigScreen
import com.zame.nutrinest.ui.menu.detail.MealDetailScreen
import com.zame.nutrinest.ui.menu.shopping.ShoppingListScreen
import com.zame.nutrinest.ui.profiles.ProfilesScreen
import com.zame.nutrinest.ui.profiles.edit.ProfileEditorScreen
import com.zame.nutrinest.ui.recipes.RecipesScreen
import com.zame.nutrinest.ui.recipes.create.CreateRecipeScreen
import com.zame.nutrinest.util.WeekUtils

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Recipes : Screen("recipes")
    data object CreateRecipe : Screen("recipes/create")
    data object CreateIngredient : Screen("ingredients/create")
    data object Profiles : Screen("profiles")

    data object ProfileEditor : Screen("profiles/editor?profileId={profileId}") {
        fun create() = "profiles/editor?profileId=-1"
        fun edit(id: Long) = "profiles/editor?profileId=$id"
    }

    data object WeeklyMenu : Screen("weekly_menu")

    data object MenuConfig : Screen("weekly_menu/config?weekStart={weekStart}") {
        fun forWeek(weekStart: Long) = "weekly_menu/config?weekStart=$weekStart"
    }

    data object MealDetail : Screen("weekly_menu/meal/{mealId}") {
        fun forMeal(mealId: Long) = "weekly_menu/meal/$mealId"
    }

    data object ShoppingList : Screen("weekly_menu/shopping")
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onRecipesClick = { navController.navigate(Screen.Recipes.route) },
                onWeeklyMenuClick = { navController.navigate(Screen.WeeklyMenu.route) },
                onProfilesClick = { navController.navigate(Screen.Profiles.route) }
            )
        }

        composable(Screen.Recipes.route) {
            RecipesScreen(
                onBack = { navController.popBackStack() },
                onAddRecipe = { navController.navigate(Screen.CreateRecipe.route) },
                onAddIngredient = { navController.navigate(Screen.CreateIngredient.route) },
                onOpenRecipe = { /* TODO: RecipeDetail screen in a future step */ }
            )
        }

        composable(Screen.CreateRecipe.route) {
            CreateRecipeScreen(
                onBack = { navController.popBackStack() },
                onCreateIngredient = { navController.navigate(Screen.CreateIngredient.route) }
            )
        }

        composable(Screen.CreateIngredient.route) {
            CreateIngredientScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profiles.route) {
            ProfilesScreen(
                onBack = { navController.popBackStack() },
                onAddProfile = { navController.navigate(Screen.ProfileEditor.create()) },
                onEditProfile = { id -> navController.navigate(Screen.ProfileEditor.edit(id)) }
            )
        }

        composable(
            route = Screen.ProfileEditor.route,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            ProfileEditorScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.WeeklyMenu.route) {
            WeeklyMenuScreen(
                onBack = { navController.popBackStack() },
                onConfigure = { weekStart ->
                    navController.navigate(Screen.MenuConfig.forWeek(weekStart))
                },
                onOpenMeal = { mealId ->
                    navController.navigate(Screen.MealDetail.forMeal(mealId))
                },
                onOpenShoppingList = {
                    navController.navigate(Screen.ShoppingList.route)
                }
            )
        }

        composable(
            route = Screen.MenuConfig.route,
            arguments = listOf(
                navArgument("weekStart") {
                    type = NavType.LongType
                    defaultValue = WeekUtils.weekStartOf()
                }
            )
        ) {
            MenuConfigScreen(
                onBack = { navController.popBackStack() },
                onGenerated = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.MealDetail.route,
            arguments = listOf(
                navArgument("mealId") { type = NavType.LongType }
            )
        ) {
            MealDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.ShoppingList.route) {
            ShoppingListScreen(onBack = { navController.popBackStack() })
        }
    }
}
