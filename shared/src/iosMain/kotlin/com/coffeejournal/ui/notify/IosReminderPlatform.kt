package com.coffeejournal.ui.notify

/*
 * Reminders are not delivered on iOS yet. The rules and the daily check are shared (Reminders, ReminderCheck,
 * ReminderPrefs), so wiring them here means: schedule a daily UNCalendarNotificationTrigger or a BGAppRefreshTask
 * that runs ReminderCheck, post through UNUserNotificationCenter, and ask for authorization in
 * rememberNotificationPermissionRequest. Until then nothing is scheduled, and 알림 설정 keeps its switch off with
 * the "알림 권한" hint because canNotify() is false. Not compiled in this repository's Linux CI.
 */
class IosReminderPlatform : ReminderPlatform {
    override suspend fun schedule(time: ReminderTime) {}

    override suspend fun cancel() {}

    override fun canNotify(): Boolean = false

    override fun openNotificationSettings() {}
}
