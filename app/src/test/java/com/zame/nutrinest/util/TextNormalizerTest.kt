package com.zame.nutrinest.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The normaliser has to reproduce exactly the rule the shipped food dataset
 * used for its own `name_norm` and `alias_norm` columns — otherwise a search
 * typed by the user would not match the values stored at import time.
 *
 * The pairs below are taken verbatim from the dataset.
 */
class TextNormalizerTest {

    @Test
    fun `matches the dataset's own normalised names`() {
        val fromDataset = mapOf(
            "Corazón de ternera, crudo" to "corazon de ternera crudo",
            "Barra chocolate, tipo \"mars\"" to "barra chocolate tipo mars",
            "Yogur, desnatado, sabor, s/e" to "yogur desnatado sabor s e",
            "Riñón, de cordero, crudo" to "rinon de cordero crudo",
            "Castaña,  asada" to "castana asada",
            "Yogur, desnatado, con piña y pomelo" to "yogur desnatado con pina y pomelo",
            "Zumo de pomelo" to "zumo de pomelo"
        )
        fromDataset.forEach { (input, expected) ->
            assertEquals(input, expected, TextNormalizer.normalize(input))
        }
    }

    @Test
    fun `strips accents so a plain keyboard finds accented names`() {
        assertEquals("platano", TextNormalizer.normalize("Plátano"))
        assertEquals(TextNormalizer.normalize("plátano"), TextNormalizer.normalize("platano"))
    }

    @Test
    fun `collapses punctuation and whitespace`() {
        assertEquals("olive oil extra virgin", TextNormalizer.normalize("  Olive oil, extra-virgin! "))
        assertEquals("", TextNormalizer.normalize("   "))
        assertEquals("", TextNormalizer.normalize("---"))
    }

    @Test
    fun `keeps digits, which appear in fatty acid names`() {
        assertEquals("acido graso 18 2", TextNormalizer.normalize("ácido graso 18:2"))
    }

    @Test
    fun `normalizeOrNull returns null for nothing usable`() {
        assertEquals(null, TextNormalizer.normalizeOrNull(null))
        assertEquals(null, TextNormalizer.normalizeOrNull("   "))
        assertEquals(null, TextNormalizer.normalizeOrNull("!!!"))
        assertEquals("tomato", TextNormalizer.normalizeOrNull("Tomato"))
    }

    @Test
    fun `similarity scores identical names highest`() {
        assertEquals(1.0, TextNormalizer.similarity("olive oil", "olive oil"), 0.0)
    }

    @Test
    fun `similarity treats a longer qualified name as close`() {
        val score = TextNormalizer.similarity("olive oil", "olive oil extra virgin")
        assertTrue("expected a close match, got $score", score > 0.4)
    }

    @Test
    fun `similarity tolerates a typo`() {
        val score = TextNormalizer.similarity("broccoli", "brocoli")
        assertTrue("expected a near match, got $score", score > 0.8)
    }

    @Test
    fun `similarity separates unrelated names`() {
        val score = TextNormalizer.similarity("olive oil", "chicken breast")
        assertTrue("expected a poor match, got $score", score < 0.3)
    }

    @Test
    fun `similarity handles empty input`() {
        assertEquals(0.0, TextNormalizer.similarity("", "tomato"), 0.0)
        assertEquals(0.0, TextNormalizer.similarity("tomato", ""), 0.0)
    }
}
