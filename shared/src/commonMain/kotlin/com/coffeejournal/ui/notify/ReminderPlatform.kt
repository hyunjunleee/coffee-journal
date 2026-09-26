package com.coffeejournal.ui.notify

/**
 * The platform side of reminders (a Koin single from each platform's `platformModule`, like PhotoStore). Android:
 * a daily WorkManager job that runs [ReminderCheck] and posts notifications on per-kind channels. iOS: not delivered
 * yet (see PlatformModule.ios.kt).
 */
interface ReminderPlatform {
    /**
     * Runs the daily check at [time] every day from now on. Calling it again with the same time keeps the existing
     * schedule; a different time replaces it.
     */
    suspend fun schedule(time: ReminderTime)

    /** Stops the daily check. */
    suspend fun cancel()

    /** Notifications can be shown now (Android 13+: the permission is granted; and the app's notifications are on). */
    fun canNotify(): Boolean

    /** Opens the phone's notification settings for this app, where a refused permission can be given later. */
    fun openNotificationSettings()
}

/** Keeps the platform's schedule in step with the saved settings: at app start, after a phone transfer, an update. */
object ReminderSchedule {
    suspend fun sync(prefs: ReminderPrefs, platform: ReminderPlatform) {
        val settings = prefs.load()
        if (settings.enabled) platform.schedule(settings.time) else platform.cancel()
    }
}
