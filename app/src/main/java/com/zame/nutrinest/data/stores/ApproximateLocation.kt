package com.zame.nutrinest.data.stores

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.zame.nutrinest.domain.stores.GeoPoint
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal class ApproximateLocation(private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun locate(): GeoPoint? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(
                        manager, LocationManager.NETWORK_PROVIDER, signal,
                        ContextCompat.getMainExecutor(context)
                    ) { location ->
                        continuation.resume(location?.takeIf { hasPermission() }?.let {
                            GeoPoint.approximate(it.latitude, it.longitude)
                        })
                    }
                } catch (_: SecurityException) {
                    continuation.resume(null)
                } catch (_: IllegalArgumentException) {
                    // Some devices do not expose a network provider.
                    continuation.resume(null)
                }
            }
        }
    }
}
