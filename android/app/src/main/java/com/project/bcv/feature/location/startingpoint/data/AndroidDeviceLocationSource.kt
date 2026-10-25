package com.project.bcv.feature.location.startingpoint.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.project.bcv.feature.location.startingpoint.domain.Coordinates
import com.project.bcv.feature.location.startingpoint.domain.DeviceLocationSource

/**
 * Asks every enabled provider at once and takes the first fix. Some devices (e.g. Vivo without
 * GMS) never answer on the fused provider, so a recent or last known location is the fallback.
 */
class AndroidDeviceLocationSource(context: Context) : DeviceLocationSource {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val signals = mutableListOf<CancellationSignal>()
    private val listeners = mutableListOf<LocationListener>()
    private var timeout: Runnable? = null
    private var pending: ((Coordinates?) -> Unit)? = null
    private var settle: Runnable? = null
    private var fallback: Location? = null
    private var best: Location? = null
    private var waiting = 0

    override fun findCurrent(callback: (Coordinates?) -> Unit) {
        cancel()
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            callback(null)
            return
        }
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER).filter { name ->
            (fine || name != LocationManager.GPS_PROVIDER) &&
                runCatching { manager.isProviderEnabled(name) }.getOrDefault(false)
        }
        val lastKnown = lastKnown()
        if (lastKnown != null && ageMillis(lastKnown) <= FRESH_MILLIS && isPrecise(lastKnown)) {
            callback(lastKnown.toCoordinates())
            return
        }
        if (providers.isEmpty()) {
            callback(lastKnown?.toCoordinates())
            return
        }

        pending = callback
        fallback = lastKnown
        waiting = providers.size
        timeout = Runnable { finish(fallback) }.also { handler.postDelayed(it, TIMEOUT_MILLIS) }
        providers.forEach(::request)
    }

    override fun cancel() {
        pending = null
        fallback = null
        best = null
        waiting = 0
        timeout?.let(handler::removeCallbacks)
        timeout = null
        settle?.let(handler::removeCallbacks)
        settle = null
        signals.forEach { it.cancel() }
        signals.clear()
        listeners.forEach { runCatching { manager.removeUpdates(it) } }
        listeners.clear()
    }

    private fun request(provider: String) {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val signal = CancellationSignal().also(signals::add)
                val request = LocationRequest.Builder(0L)
                    .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
                    .setDurationMillis(TIMEOUT_MILLIS)
                    .build()
                manager.getCurrentLocation(provider, request, signal, appContext.mainExecutor, ::onResult)
            } else if (Build.VERSION.SDK_INT >= 30) {
                val signal = CancellationSignal().also(signals::add)
                manager.getCurrentLocation(provider, signal, appContext.mainExecutor, ::onResult)
            } else {
                val listener = LocationListener(::onResult).also(listeners::add)
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            }
        } catch (_: Exception) {
            onResult(null)
        }
    }

    private fun onResult(location: Location?) {
        if (pending == null) return
        waiting--
        if (location != null && (best?.let { location.accuracy < it.accuracy } != false)) best = location
        val current = best
        when {
            current != null && isPrecise(current) -> finish(current)
            waiting <= 0 -> finish(current ?: fallback)
            // A rough fix arrived first; give the other providers a moment to beat it.
            current != null && settle == null ->
                settle = Runnable { finish(best) }.also { handler.postDelayed(it, SETTLE_MILLIS) }
        }
    }

    private fun finish(location: Location?) {
        val callback = pending ?: return
        cancel()
        callback(location?.toCoordinates())
    }

    private fun lastKnown(): Location? = manager.allProviders.mapNotNull { name ->
        runCatching { manager.getLastKnownLocation(name) }.getOrNull()
    }.minByOrNull(::ageMillis)

    private fun isPrecise(location: Location) = location.hasAccuracy() && location.accuracy <= PRECISE_METERS

    private fun ageMillis(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    private fun Location.toCoordinates() = Coordinates(latitude, longitude)

    private companion object {
        const val FRESH_MILLIS = 2 * 60_000L
        const val TIMEOUT_MILLIS = 20_000L
        const val SETTLE_MILLIS = 6_000L
        const val PRECISE_METERS = 50f
    }
}
