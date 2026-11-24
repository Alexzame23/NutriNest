package com.zame.nutrinest.data.prices

import com.zame.nutrinest.BuildConfig

internal enum class PriceDiagnosis {
    DISABLED, NO_BASE_URL, NO_NETWORK, MANIFEST_404, SNAPSHOT_UNAVAILABLE, NOT_MARKED_LIVE,
    EMPTY_SNAPSHOT, HASH_MISMATCH, NO_PRODUCT_MATCH, NO_REGION, FILTERED_OUT, STALE_DATA, SUCCESS
}

internal fun configurationDiagnosis(enabled: Boolean, hasBaseUrl: Boolean): PriceDiagnosis? = when {
    !enabled -> PriceDiagnosis.DISABLED
    !hasBaseUrl -> PriceDiagnosis.NO_BASE_URL
    else -> null
}

// The app installs a Logcat sink only in debug. JVM tests can observe the same decisions.
internal object PriceDiagnostics {
    var sink: ((String) -> Unit)? = null

    inline fun log(message: () -> String) {
        if (BuildConfig.DEBUG) sink?.invoke(message())
    }

    fun state(stage: String, reason: PriceDiagnosis) = log { "stage=$stage state=$reason" }
}

internal fun responseDiagnosis(manifest: Boolean, status: Int, liveHeader: String?): PriceDiagnosis? = when {
    manifest && status == 404 -> PriceDiagnosis.MANIFEST_404
    !manifest && (status == 404 || status == 410) -> PriceDiagnosis.SNAPSHOT_UNAVAILABLE
    status == 200 && liveHeader != "live" -> PriceDiagnosis.NOT_MARKED_LIVE
    else -> null
}

internal fun verifySnapshotDigest(actual: String, expected: String) {
    val valid = actual == expected
    PriceDiagnostics.log { if (valid) "stage=hash validated=true sha256=$actual" else "stage=hash validated=false" }
    if (!valid) PriceDiagnostics.state("hash", PriceDiagnosis.HASH_MISMATCH)
    require(valid) { "Price snapshot digest mismatch" }
}
