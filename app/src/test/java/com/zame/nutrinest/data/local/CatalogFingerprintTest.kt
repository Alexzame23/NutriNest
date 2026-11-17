package com.zame.nutrinest.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogFingerprintTest {
    @Test fun `tokens use UTF8 byte lengths and a reproducible hash`() {
        assertEquals("6:á🍎", CatalogFingerprint.token("á🍎"))
        assertEquals("1de33eb99f1e97cbce58b69557f186ce57de1f2e86dca4411dc69bd3447693ba",
            CatalogFingerprint.sha256(CatalogFingerprint.token("á🍎")))
    }

    @Test fun `real values preserve IEEE bits without rounding`() {
        assertEquals("406123f35ba6e72c", CatalogFingerprint.real(137.123456789))
        assertEquals("8000000000000000", CatalogFingerprint.real(-0.0))
        assertEquals("0000000000000000", CatalogFingerprint.real(0.0))
    }
}
