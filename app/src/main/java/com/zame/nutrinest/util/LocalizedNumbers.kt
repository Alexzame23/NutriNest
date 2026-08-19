package com.zame.nutrinest.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Number parsing shared by editable fields in both supported locales. */
object LocalizedNumbers {

    fun parse(text: String): Double? {
        val compact = text.trim().replace("\u00A0", "").replace(" ", "")
        if (compact.isEmpty()) return null

        val decimalSeparator = when {
            compact.contains('.') && compact.contains(',') ->
                if (compact.lastIndexOf('.') > compact.lastIndexOf(',')) '.' else ','
            compact.contains(',') -> ','
            else -> '.'
        }
        val groupingSeparator = if (decimalSeparator == '.') ',' else '.'
        val normalized = compact.replace(groupingSeparator.toString(), "")
            .replace(decimalSeparator, '.')
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun format(value: Double, locale: Locale = Locale.getDefault()): String {
        if (!value.isFinite()) return ""
        val format = DecimalFormat("0.######", DecimalFormatSymbols.getInstance(locale)).apply {
            isGroupingUsed = false
        }
        return format.format(value)
    }
}

fun String.toLocalizedDoubleOrNull(): Double? = LocalizedNumbers.parse(this)
