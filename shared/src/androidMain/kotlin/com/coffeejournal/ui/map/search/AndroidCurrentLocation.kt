package com.coffeejournal.ui.map.search

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.coffeejournal.domain.model.GeoPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * [CurrentLocation] on the framework's LocationManager (no Google Play services): a recent fix (under two minutes,
 * within 100 m) if one is at hand, else one fresh fix from each usable provider at once — fused (Android 12+), network,
 * and GPS when precise location is allowed — and the first to answer wins. Android 11+ asks each provider with
 * getCurrentLocation; older versions register for a single update and unregister. Cancelling (the picker's timeout,
 * leaving the screen) stops every request.
 */
class AndroidCurrentLocation(private val context: Context) : CurrentLocation {
    private val manager: LocationManager? get() = context.getSystemService(LocationManager::class.java)

    override val supported: Boolean
        get() = manager?.allProviders?.any { it in USABLE } == true

    override suspend fun servicesOn(): Boolean = manager?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

    override suspend fun locate(): LocateResult {
        val lm = manager ?: return LocateResult.NoFix
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !granted(Manifest.permission.ACCESS_COARSE_LOCATION)) return LocateResult.Denied
        if (!LocationManagerCompat.isLocationEnabled(lm)) return LocateResult.ServicesOff
        val providers = providers(lm, fine)
        if (providers.isEmpty()) return LocateResult.ServicesOff
        val fix = try {
            recent(lm, providers) ?: firstFix(lm, providers)
        } catch (e: SecurityException) {
            // the permission was taken away in the meantime
            return LocateResult.Denied
        }
        return fix?.let { found(it) } ?: LocateResult.NoFix
    }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun providers(lm: LocationManager, fine: Boolean): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) add(LocationManager.FUSED_PROVIDER)
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        // GPS needs precise location; with approximate only the other providers answer (roughly)
        if (fine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
    }

    @SuppressLint("MissingPermission") // checked by locate() before
    private fun recent(lm: LocationManager, providers: List<String>): Location? {
        val now = SystemClock.elapsedRealtimeNanos()
        return providers.mapNotNull { lm.getLastKnownLocation(it) }
            .filter { now - it.elapsedRealtimeNanos in 0..RECENT_NANOS && (!it.hasAccuracy() || it.accuracy <= RECENT_ACCURACY_M) }
            .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
    }

    /** Every provider at once; the first fix wins and the others are cancelled. Null when none of them found one. */
    private suspend fun firstFix(lm: LocationManager, providers: List<String>): Location? = coroutineScope {
        val answers = Channel<Location?>(Channel.UNLIMITED)
        val requests = providers.map { p -> launch { answers.send(current(lm, p)) } }
        var fix: Location? = null
        var waiting = providers.size
        while (fix == null && waiting > 0) {
            fix = answers.receive()
            waiting--
        }
        requests.forEach { it.cancel() }
        fix
    }

    @SuppressLint("MissingPermission") // checked by locate() before
    private suspend fun current(lm: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                if (cont.isActive) cont.resume(location)
            }
        } else {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(location)
                }

                override fun onProviderDisabled(provider: String) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resume(null)
                }

                override fun onProviderEnabled(provider: String) = Unit

                // abstract before Android 11: without it an old phone's framework would call a missing method
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            }
            cont.invokeOnCancellation { lm.removeUpdates(listener) }
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        }
    }

    private fun found(location: Location): LocateResult =
        GeoPoint.of(location.latitude, location.longitude)?.let { LocateResult.Found(it, if (location.hasAccuracy()) location.accuracy.toDouble() else null) }
            ?: LocateResult.NoFix

    private companion object {
        val USABLE = setOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        const val RECENT_NANOS = 120_000_000_000L
        const val RECENT_ACCURACY_M = 100f
    }
}
