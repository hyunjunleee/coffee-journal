package com.coffeejournal.ui.notify

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The platform side of reminders (a Koin single from each platform's `platformModule`, like PhotoStore). Android:
 * a daily WorkManager job that runs [ReminderCheck] and posts notifications on per-kind channels. iOS: no daily
 * check runs in the background, so the reminders of the coming days are scheduled ahead as local notifications
 * ([ReminderScheduleAhead]).
 */
interface ReminderPlatform {
    /**
     * Runs the daily check at [time] every day from now on. Calling it again with the same time keeps the existing
     * schedule; a different time replaces it. (iOS: schedules the coming days' reminders from the saved settings,
     * whose time is [time].)
     */
    suspend fun schedule(time: ReminderTime)

    /** Stops the daily check. */
    suspend fun cancel()

    /**
     * Notifications can be shown now (Android 13+: the permission is granted; and the app's notifications are on.
     * iOS: the authorization last read, see [canNotifyChanges]).
     */
    fun canNotify(): Boolean

    /**
     * [canNotify]'s answer each time it changes on its own, for a platform that reads it asynchronously (iOS, at
     * each sync and back in the foreground). Android answers at once, so it never emits: the screen asks again when
     * it resumes.
     */
    fun canNotifyChanges(): Flow<Boolean> = emptyFlow()

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
