package com.zame.nutrinest.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class LocalizedNumbersTest {

    @Test
    fun `parses dot and comma decimals`() {
        assertEquals(65.5, LocalizedNumbers.parse("65.5")!!, 0.0)
        assertEquals(65.5, LocalizedNumbers.parse("65,5")!!, 0.0)
        assertEquals(1234.5, LocalizedNumbers.parse("1.234,5")!!, 0.0)
        assertEquals(1234.5, LocalizedNumbers.parse("1,234.5")!!, 0.0)
    }

    @Test
    fun `rejects non finite and malformed values`() {
        assertNull(LocalizedNumbers.parse("NaN"))
        assertNull(LocalizedNumbers.parse("Infinity"))
        assertNull(LocalizedNumbers.parse("12 apples"))
    }

    @Test
    fun `formats editable values for the locale`() {
        assertEquals("65,5", LocalizedNumbers.format(65.5, Locale.forLanguageTag("es-ES")))
        assertEquals("65.5", LocalizedNumbers.format(65.5, Locale.UK))
    }
}
