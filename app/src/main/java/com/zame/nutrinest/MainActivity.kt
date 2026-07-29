package com.zame.nutrinest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zame.nutrinest.navigation.AppNavigation
import com.zame.nutrinest.ui.theme.NutritionAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NutritionAppTheme {
                AppNavigation()
            }
        }
    }
}
