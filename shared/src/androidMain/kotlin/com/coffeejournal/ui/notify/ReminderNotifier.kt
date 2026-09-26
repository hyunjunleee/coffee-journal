package com.coffeejournal.ui.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.coffeejournal.domain.rules.Reminder
import com.coffeejournal.domain.rules.ReminderKind
import com.coffeejournal.shared.R
import com.coffeejournal.ui.nav.LaunchTarget

/**
 * Intents that open the app on a screen ([LaunchTarget]): the reminder notifications and the home-screen widget use
 * them, MainActivity reads them back ([targetOf]) and hands the target to the nav host.
 */
object LaunchIntents {
    const val EXTRA_OPEN = "com.coffeejournal.extra.OPEN"

    /** The app's launcher activity, asked to open [target]; brings a running app to the front (onNewIntent). */
    fun open(context: Context, target: LaunchTarget): Intent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
        return launch
            .putExtra(EXTRA_OPEN, target.id)
            // one PendingIntent per target: intents that differ only in extras would share one
            .setData(Uri.parse("coffeejournal://open/${target.id}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun targetOf(intent: Intent?): LaunchTarget? = LaunchTarget.of(intent?.getStringExtra(EXTRA_OPEN))
}

/** Posts reminders as notifications, one channel per kind so each can be silenced in the phone's settings too. */
class ReminderNotifier(context: Context) {
    private val app = context.applicationContext
    private val manager = NotificationManagerCompat.from(app)

    /** Android 13+: the POST_NOTIFICATIONS permission; every version: the app's notifications are not switched off. */
    fun canNotify(): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && manager.areNotificationsEnabled()
    }

    /** Creates (or renames) the three channels; they show under the app's notification settings from then on. */
    fun ensureChannels() {
        val system = app.getSystemService(NotificationManager::class.java) ?: return
        Channel.entries.forEach { c ->
            system.createNotificationChannel(
                NotificationChannel(c.id, c.title, NotificationManager.IMPORTANCE_DEFAULT).apply { description = c.description }
            )
        }
    }

    /** Shows [reminder]; false when it could not be shown (no permission, its channel silenced by the user). */
    fun post(reminder: Reminder): Boolean {
        if (!canNotify()) return false
        ensureChannels()
        val channel = Channel.of(reminder.kind)
        if (manager.getNotificationChannel(channel.id)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val target = reminder.kind.launchTarget
        val tap = PendingIntent.getActivity(
            app, target.ordinal, LaunchIntents.open(app, target),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(app, channel.id)
            .setSmallIcon(R.drawable.ic_stat_coffee)
            .setColor(INK)
            .setContentTitle(reminder.title)
            .setContentText(reminder.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(TAG, notificationId(reminder), notification)
            true
        } catch (e: SecurityException) {
            // the permission was taken away between the check and the post
            false
        }
    }

    enum class Channel(val id: String, val title: String, val description: String) {
        PEAK("reminders.peak", "피크 시작", ReminderTexts.PEAK),
        LOW_STOCK("reminders.lowStock", "원두 소진 임박", ReminderTexts.LOW_STOCK),
        DDAY("reminders.dday", "D-day 마일스톤", ReminderTexts.DDAY);

        companion object {
            fun of(kind: ReminderKind): Channel = when (kind) {
                ReminderKind.PEAK -> PEAK
                ReminderKind.LOW_STOCK -> LOW_STOCK
                ReminderKind.DDAY -> DDAY
            }
        }
    }

    companion object {
        const val TAG = "coffee-journal.reminder"

        /** The app's ink (#20201D) as the notification accent. */
        private const val INK = 0xFF20201D.toInt()

        /** Stable per reminder, so the same event posted again replaces its notification. */
        fun notificationId(reminder: Reminder): Int = reminder.key.hashCode()
    }
}
