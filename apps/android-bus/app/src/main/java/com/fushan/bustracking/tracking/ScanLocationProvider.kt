package com.fushan.bustracking.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class ScanLocationProvider(context: Context) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val appContext = context.applicationContext

    suspend fun capture(): Capture {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return Capture(null, "UNAVAILABLE")

        val current = withTimeoutOrNull(CURRENT_TIMEOUT_MS) { requestCurrent() }
        if (current != null) return Capture(current, "CURRENT")
        val snapshot = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider -> runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull(Location::getTime)
        return Capture(snapshot, if (snapshot == null) "UNAVAILABLE" else "SNAPSHOT")
    }

    @Suppress("MissingPermission")
    private suspend fun requestCurrent(): Location? = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT >= 30) {
            val cancellation = CancellationSignal()
            continuation.invokeOnCancellation { cancellation.cancel() }
            locationManager.getCurrentLocation(
                LocationManager.GPS_PROVIDER,
                cancellation,
                appContext.mainExecutor,
            ) { location -> if (continuation.isActive) continuation.resume(location) }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) continuation.resume(location)
                }
                @Deprecated("Deprecated by Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null)
        }
    }

    data class Capture(val location: Location?, val source: String)

    companion object {
        private const val CURRENT_TIMEOUT_MS = 500L
    }
}
