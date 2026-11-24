package com.zame.nutrinest.data.stores

import android.content.Context
import com.zame.nutrinest.domain.stores.validSpanishPostalCode

internal class AreaPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("price_areas", Context.MODE_PRIVATE)
    val recent: List<String> get() = prefs.getString("postal", "").orEmpty().split(",").filter(::validSpanishPostalCode)
    @Synchronized fun select(cp: String) {
        if (validSpanishPostalCode(cp)) {
            prefs.edit().putString("postal", (listOf(cp) + recent).distinct().take(5).joinToString(",")).apply()
        }
    }
}
