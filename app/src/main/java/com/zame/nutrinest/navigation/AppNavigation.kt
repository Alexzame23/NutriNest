package com.zame.nutrinest.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zame.nutrinest.ui.home.HomeScreen
import com.zame.nutrinest.ui.help.HelpScreen
import com.zame.nutrinest.ui.ingredients.compare.CompareIngredientsScreen
import com.zame.nutrinest.ui.ingredients.detail.IngredientDetailScreen
import com.zame.nutrinest.ui.ingredients.editor.IngredientEditorScreen
import com.zame.nutrinest.ui.menu.WeeklyMenuScreen
import com.zame.nutrinest.ui.menu.config.MenuConfigScreen
import com.zame.nutrinest.ui.menu.detail.MealDetailScreen
import com.zame.nutrinest.ui.menu.intake.IntakeScreen
import com.zame.nutrinest.ui.menu.shopping.ShoppingListScreen
import com.zame.nutrinest.ui.profiles.ProfilesScreen
import com.zame.nutrinest.ui.profiles.edit.ProfileEditorScreen
import com.zame.nutrinest.ui.recipes.RecipesAndIngredientsScreen
import com.zame.nutrinest.ui.recipes.create.CreateRecipeScreen
import com.zame.nutrinest.ui.recipes.detail.RecipeDetailScreen
import com.zame.nutrinest.util.WeekUtils

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Help : Screen("help")

    /** Recipes and Ingredients, as two tabs of a single destination. */
    data object RecipesAndIngredients : Screen("food?tab={tab}") {
        fun recipes() = "food?tab=0"
        fun ingredients() = "food?tab=1"
    }

    data object CreateRecipe : Screen("recipes/create")

    /** Kept off `recipes/{id}` so it can never race `recipes/create` for a match. */
    data object RecipeDetail : Screen("recipes/detail/{recipeId}") {
        fun of(id: Long) = "recipes/detail/$id"
    }

    data object IngredientDetail : Screen("ingredients/{ingredientId}") {
        fun of(id: Long) = "ingredients/$id"
    }

    data object IngredientEditor : Screen("ingredients/editor?ingredientId={ingredientId}") {
        fun create() = "ingredients/editor?ingredientId=-1"
        fun edit(id: Long) = "ingredients/editor?ingredientId=$id"
    }

    data object CompareIngredients : Screen("ingredients/compare?ids={ids}") {
        fun of(ids: List<Long>) = "ingredients/compare?ids=${ids.joinToString(",")}"
    }

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

    /** Ad-hoc food logging, which re-portions the rest of that person's day. */
    data object Intake : Screen("weekly_menu/intake")
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onRecipesClick = {
                    navController.navigate(Screen.RecipesAndIngredients.recipes())
                },
                onWeeklyMenuClick = { navController.navigate(Screen.WeeklyMenu.route) },
                onProfilesClick = { navController.navigate(Screen.Profiles.route) },
                onHelpClick = { navController.navigate(Screen.Help.route) }
            )
        }

        composable(Screen.Help.route) {
            HelpScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.RecipesAndIngredients.route,
            arguments = listOf(
                navArgument("tab") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { entry ->
            RecipesAndIngredientsScreen(
                onBack = { navController.popBackStack() },
                onAddRecipe = { navController.navigate(Screen.CreateRecipe.route) },
                onAddIngredient = { navController.navigate(Screen.IngredientEditor.create()) },
                onOpenRecipe = { id -> navController.navigate(Screen.RecipeDetail.of(id)) },
                onOpenIngredient = { id -> navController.navigate(Screen.IngredientDetail.of(id)) },
                onCompareIngredients = { ids ->
                    navController.navigate(Screen.CompareIngredients.of(ids))
                },
                startTab = entry.arguments?.getInt("tab") ?: 0
            )
        }

        composable(Screen.CreateRecipe.route) {
            CreateRecipeScreen(
                onBack = { navController.popBackStack() },
                onCreateIngredient = { navController.navigate(Screen.IngredientEditor.create()) }
            )
        }

        composable(
            route = Screen.RecipeDetail.route,
            arguments = listOf(navArgument("recipeId") { type = NavType.LongType })
        ) {
            RecipeDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.IngredientDetail.route,
            arguments = listOf(navArgument("ingredientId") { type = NavType.LongType })
        ) {
            IngredientDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Screen.IngredientEditor.edit(id)) },
                onCompare = { id ->
                    navController.navigate(Screen.CompareIngredients.of(listOf(id)))
                }
            )
        }

        composable(
            route = Screen.IngredientEditor.route,
            arguments = listOf(
                navArgument("ingredientId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            IngredientEditorScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.CompareIngredients.route,
            arguments = listOf(
                navArgument("ids") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            CompareIngredientsScreen(onBack = { navController.popBackStack() })
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
                },
                onOpenIntake = { navController.navigate(Screen.Intake.route) }
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

        composable(Screen.Intake.route) {
            IntakeScreen(onBack = { navController.popBackStack() })
        }
    }
}
