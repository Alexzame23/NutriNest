package com.zame.nutrinest.util

import java.text.Normalizer
import java.util.Locale

/**
 * Reduces a name to its searchable form: lowercase, accents stripped,
 * punctuation flattened to single spaces.
 *
 *     "Riñón, de cordero, crudo"  ->  "rinon de cordero crudo"
 *     "Barra chocolate, tipo \"mars\""  ->  "barra chocolate tipo mars"
 *
 * This reproduces exactly the rule the shipped food dataset used to build its
 * own `name_norm` and `alias_norm` columns (verified against all 936 foods and
 * 1164 aliases). Import and runtime search therefore agree, and a Spanish user
 * typing "platano" finds "plátano".
 */
object TextNormalizer {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return COMBINING_MARKS.replace(decomposed, "")
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
    }

    fun normalizeOrNull(text: String?): String? =
        text?.takeIf { it.isNotBlank() }?.let(::normalize)?.takeIf { it.isNotEmpty() }

    /**
     * Similarity in 0..1 for duplicate detection, on already-normalised input.
     * Uses Levenshtein distance over the longer length, with a shortcut for
     * one name containing the other ("olive oil" vs "olive oil, extra virgin").
     */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) {
            return minOf(a.length, b.length).toDouble() / maxOf(a.length, b.length)
        }
        val distance = levenshtein(a, b)
        return 1.0 - distance.toDouble() / maxOf(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        // Two-row variant: the full matrix is never needed and names are short.
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
