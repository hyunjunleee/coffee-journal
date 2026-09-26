package com.coffeejournal.android

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.coffeejournal.data.repo.EntryRepository
import com.coffeejournal.data.repo.PantryRepository
import com.coffeejournal.data.repo.SettingsRepository
import com.coffeejournal.domain.model.Category
import com.coffeejournal.domain.model.Entry
import com.coffeejournal.domain.model.PantryItem
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.nav.LaunchTarget
import com.coffeejournal.ui.notify.AndroidReminderPlatform
import com.coffeejournal.ui.notify.LaunchIntents
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.koin.core.context.GlobalContext
import org.robolectric.Shadows.shadowOf

/** Shared by the reminder tests: a journal where all three reminder kinds are due today, and WorkManager / notification probes. */
object ReminderFixtures {
    const val PEAK_BAG = "구지 함벨라"
    const val LOW_BAG = "게이샤 빌리지"

    fun initWorkManager(context: Context) {
        val config = Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    fun grantNotifications(context: Application) = shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    fun denyNotifications(context: Application) = shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

    /**
     * Today (the real date, which the worker reads): a light roast whose peak starts today, an opened 200 g bag with
     * 24 g left (a cup is 16 g, so 1.5 cups), and a D-day start that makes today D-30.
     */
    fun seedDueToday(today: LocalDate = Dates.today()) = runBlocking {
        val koin = GlobalContext.get()
        koin.get<PantryRepository>().upsertAll(listOf(
            PantryItem(id = "peak", name = PEAK_BAG, roastLevel = "라이트", roastDate = Dates.isoDate(Dates.plusDays(today, -14)), weight = "200", createdAt = 1L),
            PantryItem(id = "low", name = LOW_BAG, weight = "200", status = PantryItem.STATUS_OPENED, openedAt = 2L, createdAt = 2L),
        ))
        val entries = koin.get<EntryRepository>()
        (1..11).forEach { i ->
            entries.upsert(Entry(id = "b$i", createdAt = Dates.startOfDayMillis(Dates.plusDays(today, -i)) + 9 * 3_600_000L, category = Category.BEAN, name = LOW_BAG, dose = "16"))
        }
        koin.get<SettingsRepository>().setDdayStart(Dates.plusDays(today, -29))
    }

    fun notifications(context: Context): List<Notification> =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    fun titles(context: Context): List<String> =
        notifications(context).map { it.extras.getString(Notification.EXTRA_TITLE).orEmpty() }.sorted()

    fun target(notification: Notification): LaunchTarget? = LaunchIntents.targetOf(shadowOf(notification.contentIntent).savedIntent)

    fun clearNotifications(context: Context) = context.getSystemService(NotificationManager::class.java).cancelAll()

    fun reminderWork(context: Context): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(AndroidReminderPlatform.WORK_NAME).get()

    fun activeReminderWork(context: Context): List<WorkInfo> = reminderWork(context).filter { !it.state.isFinished }

    /** The time tag of the one active daily check ("09:00"), or null when none is scheduled. */
    fun scheduledTime(context: Context): String? = activeReminderWork(context).singleOrNull()
        ?.tags?.firstOrNull { it.startsWith(AndroidReminderPlatform.TIME_TAG_PREFIX) }
        ?.removePrefix(AndroidReminderPlatform.TIME_TAG_PREFIX)
}
