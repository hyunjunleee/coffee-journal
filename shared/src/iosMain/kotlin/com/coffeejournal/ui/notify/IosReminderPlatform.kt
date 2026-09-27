package com.coffeejournal.ui.notify

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDateComponents
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.NSObjectProtocol
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/**
 * Reminders on iOS. The app gets no reliable daily run in the background, so no daily check runs:
 * [ReminderScheduleAhead] schedules what the check would send on each of the coming days as local notifications, and
 * plans again at start, back in the foreground and after every change to the records, the pantry or the settings
 * ([start]), and when the settings screen turns reminders on or moves their time ([schedule]). Tapping a notification
 * opens the app where it was.
 */
class IosReminderPlatform(
    private val notifications: IosNotifications,
    private val ahead: ReminderScheduleAhead,
) : ReminderPlatform {
    private val foreground = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var foregroundObserver: NSObjectProtocol? = null

    /** Plans from the saved settings, whose time is [time]. */
    override suspend fun schedule(time: ReminderTime) {
        ahead.sync()
    }

    override suspend fun cancel() = ahead.clear()

    override fun canNotify(): Boolean = notifications.authorized.value

    override fun canNotifyChanges(): Flow<Boolean> = notifications.authorized

    /** This app's page under Settings › Notifications (iOS 16+), or its Settings page if that does not open. */
    override fun openNotificationSettings() {
        val app = UIApplication.sharedApplication
        val appSettings = NSURL.URLWithString(UIApplicationOpenSettingsURLString)
        val notificationSettings = NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString) ?: appSettings ?: return
        // openURL(_:) without options is refused ("Force returning false") since iOS 18
        app.openURL(notificationSettings, options = emptyMap<Any?, Any?>()) { opened ->
            if (!opened && appSettings != null && notificationSettings != appSettings) {
                app.openURL(appSettings, options = emptyMap<Any?, Any?>(), completionHandler = null)
            }
        }
    }

    /**
     * From the app start on (once): banners while the app is open, the authorization read now and back in the
     * foreground, and a sync at start, after each of [changes] and back in the foreground ([ReminderScheduleAhead.follow]).
     */
    fun start(scope: CoroutineScope, changes: Flow<Unit>) {
        if (foregroundObserver != null) return
        notifications.showWhileOpen()
        notifications.refreshAuthorization()
        foregroundObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            // back from Settings, the user may have allowed or blocked the notifications
            notifications.refreshAuthorization()
            foreground.tryEmit(Unit)
        }
        scope.launch { ahead.follow(changes, foreground) }
    }
}

/**
 * UNUserNotificationCenter as schedule-ahead uses it ([ReminderScheduler]), with the authorization kept at hand
 * ([authorized]) for the synchronous [ReminderPlatform.canNotify]: read at each sync, back in the foreground and after
 * the permission prompt. Allowed means authorized or provisional (delivered quietly).
 */
class IosNotifications : ReminderScheduler {
    private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()
    private val allowed = MutableStateFlow(false)

    /** The notification center holds its delegate weakly: this reference keeps it alive with the app. */
    private val banners = BannersWhileOpen()

    val authorized: StateFlow<Boolean> = allowed.asStateFlow()

    /** iOS shows nothing for a notification that arrives while its app is open, unless the delegate asks for it. */
    fun showWhileOpen() {
        center.delegate = banners
    }

    fun refreshAuthorization() = readAuthorization { }

    override suspend fun canNotify(): Boolean = suspendCancellableCoroutine { cont -> readAuthorization { cont.resume(it) } }

    /**
     * Asks for alerts, sounds and badges (the system prompt appears once; later calls answer at once) and reports on the
     * main thread whether notifications can be shown.
     */
    fun requestAuthorization(onResult: (Boolean) -> Unit) {
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            readAuthorization { can -> dispatch_async(dispatch_get_main_queue()) { onResult(granted && can) } }
        }
    }

    override suspend fun pending(): List<String> = suspendCancellableCoroutine { cont ->
        center.getPendingNotificationRequestsWithCompletionHandler { requests ->
            cont.resume(requests.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier })
        }
    }

    override suspend fun remove(ids: List<String>) {
        center.removePendingNotificationRequestsWithIdentifiers(ids)
    }

    /** The reminder's own title and body (the Korean copy Android posts), at its local date and time, once. */
    override suspend fun add(planned: PlannedReminder): Boolean {
        val content = UNMutableNotificationContent()
        content.setTitle(planned.reminder.title)
        content.setBody(planned.reminder.body)
        content.setSound(UNNotificationSound.defaultSound())
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(triggerComponents(planned.at), repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(planned.id, content, trigger)
        return suspendCancellableCoroutine { cont -> center.addNotificationRequest(request) { error -> cont.resume(error == null) } }
    }

    private fun readAuthorization(done: (Boolean) -> Unit) {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            val ok = status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional
            allowed.value = ok
            done(ok)
        }
    }
}

/**
 * When a reminder planned for [at] goes off: that Gregorian date and wall-clock time, without a time zone, so it
 * goes off at that local time wherever the phone is, like the daily check (a user's other calendar setting does not
 * change the date).
 */
internal fun triggerComponents(at: LocalDateTime): NSDateComponents = NSDateComponents().apply {
    calendar = NSCalendar.calendarWithIdentifier(NSCalendarIdentifierGregorian)
    year = at.year.toLong()
    month = at.month.number.toLong()
    day = at.day.toLong()
    hour = at.hour.toLong()
    minute = at.minute.toLong()
}

/** Shows a reminder that arrives while the app is open as a banner with its sound. */
private class BannersWhileOpen : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
    }
}
