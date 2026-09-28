package com.coffeejournal.ui.map.search

import com.coffeejournal.domain.model.GeoPoint

/**
 * The phone's position, once, for the picker's "현재 위치", bound in Koin by the platform module: Android's
 * LocationManager (no Play Services), iOS's CLLocationManager. Asked only after the user tapped the button and the
 * permission was granted ([com.coffeejournal.ui.platform.rememberLocationPermissionRequest]); nothing runs in the
 * background and nothing is kept but the point the user then saves. Tests bind a fake, so no test reads a real GPS.
 */
interface CurrentLocation {
    /** False where the phone has no location provider at all. */
    val supported: Boolean

    /** Whether the phone's location services are on (a refused permission on iOS may be services switched off). */
    suspend fun servicesOn(): Boolean

    /** One fresh position (a few seconds at most, cancelled by the caller's timeout), or why there is none. */
    suspend fun locate(): LocateResult
}

sealed interface LocateResult {
    /** [accuracyM]: the radius the platform is 68 % sure of, when it says. */
    data class Found(val point: GeoPoint, val accuracyM: Double? = null) : LocateResult
    data object Denied : LocateResult
    data object ServicesOff : LocateResult
    data object NoFix : LocateResult
}

object LocateTexts {
    /** How long "현재 위치" waits for a position. */
    const val TIMEOUT_MS = 15_000L

    const val DENIED = "위치 권한이 없어 현재 위치를 알 수 없어요. 휴대폰 설정에서 이 앱의 위치 권한을 허용할 수 있어요."
    const val SERVICES_OFF = "휴대폰의 위치 서비스가 꺼져 있어요. 켠 뒤 다시 눌러 주세요."
    const val NO_FIX = "현재 위치를 찾지 못했어요. 창가나 밖에서 다시 해 보거나, 검색하거나 지도에서 찍어 주세요."
    const val UNSUPPORTED = "이 기기에서는 현재 위치를 쓸 수 없어요. 검색하거나 지도에서 찍어 주세요."

    /** Under the point once it came from the phone: how sure it is. */
    fun found(accuracyM: Double?): String =
        if (accuracyM == null || !accuracyM.isFinite()) "현재 위치로 정했어요."
        else "현재 위치로 정했어요 (오차 약 ${PlaceSearchService.distanceLabel(accuracyM.toInt().coerceAtLeast(1))})."
}
