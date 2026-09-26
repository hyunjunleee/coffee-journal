package com.coffeejournal.ui.notify

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coffeejournal.domain.rules.Dates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * Reminders on Android: one unique periodic WorkManager job ([WORK_NAME], every 24 hours, first run at the chosen
 * time) runs [ReminderWorker]. The job carries its time as a tag, so scheduling the same time again changes nothing
 * and a new time replaces the job.
 */
class AndroidReminderPlatform(context: Context) : ReminderPlatform {
    private val app = context.applicationContext

    override suspend fun schedule(time: ReminderTime) {
        ReminderNotifier(app).ensureChannels()
        val work = WorkManager.getInstance(app)
        val timeTag = TIME_TAG_PREFIX + time
        val current = work.getWorkInfosForUniqueWorkFlow(WORK_NAME).first()
        if (current.any { !it.state.isFinished && timeTag in it.tags }) return
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(time.millisUntilNext(Dates.nowMillis()), TimeUnit.MILLISECONDS)
            .addTag(WORK_TAG)
            .addTag(timeTag)
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    override suspend fun cancel() {
        WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
    }

    override fun canNotify(): Boolean = ReminderNotifier(app).canNotify()

    override fun openNotificationSettings() {
        val notifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(notifications)
        } catch (e: ActivityNotFoundException) {
            // a phone without the notification page: the app's details page has the same switch
            runCatching {
                app.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    companion object {
        const val WORK_NAME = "coffee-journal.daily-reminders"
        const val WORK_TAG = "coffee-journal.reminders"
        const val TIME_TAG_PREFIX = "coffee-journal.reminders-at:"
    }
}

/**
 * The daily check: posts what [ReminderCheck] finds due today and redraws the home-screen widget (the date moved on).
 * Koin is started by the application before any worker runs.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val koin = GlobalContext.getOrNull() ?: return Result.retry()
        return try {
            val notifier = ReminderNotifier(applicationContext)
            koin.get<ReminderCheck>().run(Dates.today(), notifier.canNotify(), notifier::post)
            koin.getOrNull<HomeWidgets>()?.refresh()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
