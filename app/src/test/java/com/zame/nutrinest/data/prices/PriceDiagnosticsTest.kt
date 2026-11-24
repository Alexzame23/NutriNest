package com.zame.nutrinest.data.prices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PriceDiagnosticsTest {
    @Test fun disabledFlagTakesPrecedenceOverMissingPublisher() {
        assertEquals(PriceDiagnosis.DISABLED, configurationDiagnosis(false, false))
        assertEquals(PriceDiagnosis.DISABLED, configurationDiagnosis(false, true))
        assertEquals(PriceDiagnosis.NO_BASE_URL, configurationDiagnosis(true, false))
        assertNull(configurationDiagnosis(true, true))
    }

    @Test fun missingManifestAndWithdrawnRevisionAreDistinct() {
        assertEquals(PriceDiagnosis.MANIFEST_404, responseDiagnosis(true, 404, null))
        for (code in listOf(404, 410)) {
            assertEquals(PriceDiagnosis.SNAPSHOT_UNAVAILABLE, responseDiagnosis(false, code, null))
        }
        // Other non-200 statuses continue to RetailHttpError.
        assertNull(responseDiagnosis(true, 503, null))
    }

    @Test fun bothResourcesRequireTheExactLiveMarker() {
        for (manifest in listOf(true, false)) {
            for (marker in listOf(null, "", "LIVE", "simulated")) {
                assertEquals(PriceDiagnosis.NOT_MARKED_LIVE, responseDiagnosis(manifest, 200, marker))
            }
            assertNull(responseDiagnosis(manifest, 200, "live"))
        }
    }

    @Test fun digestMismatchIsReportedAndStillRejected() {
        org.junit.Assume.assumeTrue(com.zame.nutrinest.BuildConfig.DEBUG)
        val messages = mutableListOf<String>()
        val previous = PriceDiagnostics.sink
        try {
            PriceDiagnostics.sink = { messages.add(it) }
            verifySnapshotDigest("a".repeat(64), "a".repeat(64))
            assertThrows(IllegalArgumentException::class.java) {
                verifySnapshotDigest("a".repeat(64), "b".repeat(64))
            }
            assertEquals(listOf("stage=hash validated=true sha256=${"a".repeat(64)}", "stage=hash validated=false",
                "stage=hash state=HASH_MISMATCH"), messages)
        } finally {
            PriceDiagnostics.sink = previous
        }
    }
}
