@file:OptIn(ExperimentalForeignApi::class)

package com.coffeejournal.ui.map.search

import com.coffeejournal.domain.model.GeoPoint
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLErrorDenied
import platform.CoreLocation.kCLErrorDomain
import platform.CoreLocation.kCLLocationAccuracyNearestTenMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * [CurrentLocation] on CoreLocation: "while using the app" authorization (asked from the picker's button, see
 * rememberLocationPermissionRequest) and one requestLocation. The manager is made on the main thread, where its
 * delegate then hears back; nothing keeps running after the one answer (or the picker's timeout, which stops it).
 */
class IosCurrentLocation : CurrentLocation {
    private var manager: CLLocationManager? = null
    private val delegate = LocationDelegate()

    override val supported: Boolean get() = true

    // the class method may be slow and iOS warns when the main thread calls it
    override suspend fun servicesOn(): Boolean = withContext(Dispatchers.Default) { CLLocationManager.locationServicesEnabled() }

    /** Main thread only. */
    private fun manager(): CLLocationManager = manager ?: CLLocationManager().also {
        it.delegate = delegate
        it.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
        manager = it
    }

    /**
     * Asks for "while using the app" when it was never asked (the system prompt), else answers at once; [onResult]
     * runs on the main thread. Main thread only.
     */
    fun requestAuthorization(onResult: (Boolean) -> Unit) {
        val m = manager()
        if (m.authorizationStatus != kCLAuthorizationStatusNotDetermined) {
            onResult(allowed(m.authorizationStatus))
            return
        }
        delegate.onAuthorization = { status ->
            // the delegate also hears the unchanged status when it is set; only the user's answer ends the wait
            if (status != kCLAuthorizationStatusNotDetermined) {
                delegate.onAuthorization = null
                onResult(allowed(status))
            }
        }
        m.requestWhenInUseAuthorization()
    }

    override suspend fun locate(): LocateResult {
        val authorized = withContext(Dispatchers.Main) { allowed(manager().authorizationStatus) }
        if (!authorized) return LocateResult.Denied
        if (!servicesOn()) return LocateResult.ServicesOff
        return withContext(Dispatchers.Main) {
            val m = manager()
            suspendCancellableCoroutine { cont ->
                delegate.onFix = { location, error ->
                    delegate.onFix = null
                    if (cont.isActive) {
                        val point = location?.coordinate?.useContents { GeoPoint.of(latitude, longitude) }
                        cont.resume(
                            when {
                                location != null && point != null -> LocateResult.Found(point, location.horizontalAccuracy.takeIf { it >= 0.0 })
                                error != null && error.domain == kCLErrorDomain && error.code == kCLErrorDenied -> LocateResult.Denied
                                else -> LocateResult.NoFix
                            },
                        )
                    }
                }
                cont.invokeOnCancellation {
                    dispatch_async(dispatch_get_main_queue()) {
                        delegate.onFix = null
                        m.stopUpdatingLocation()
                    }
                }
                m.requestLocation()
            }
        }
    }

    private fun allowed(status: CLAuthorizationStatus): Boolean =
        status == kCLAuthorizationStatusAuthorizedWhenInUse || status == kCLAuthorizationStatusAuthorizedAlways
}

/** Hands CoreLocation's answers to whoever waits for them; the manager keeps a weak reference, IosCurrentLocation the strong one. */
private class LocationDelegate : NSObject(), CLLocationManagerDelegateProtocol {
    var onAuthorization: ((CLAuthorizationStatus) -> Unit)? = null
    var onFix: ((CLLocation?, NSError?) -> Unit)? = null

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        onAuthorization?.invoke(manager.authorizationStatus)
    }

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        onFix?.invoke(didUpdateLocations.lastOrNull() as? CLLocation, null)
    }

    // requestLocation needs this; its Kotlin signature matches didFinishDeferredUpdatesWithError's
    @ObjCSignatureOverride
    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        onFix?.invoke(null, didFailWithError)
    }
}
