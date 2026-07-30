package com.zame.nutrinest

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zame.nutrinest.navigation.AppNavigation
import com.zame.nutrinest.ui.theme.NutritionAppTheme
import com.zame.nutrinest.util.AppLocale

class MainActivity : ComponentActivity() {

    /**
     * The in-app language has to be in place before a single resource is read,
     * which is what this hook is for. On Android 13+ the platform has already
     * applied it and [AppLocale.wrap] steps aside.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

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
