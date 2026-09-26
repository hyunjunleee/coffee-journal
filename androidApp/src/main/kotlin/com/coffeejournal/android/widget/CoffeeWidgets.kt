package com.coffeejournal.android.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.coffeejournal.di.AppScope
import com.coffeejournal.domain.rules.Dates
import com.coffeejournal.ui.notify.HomeWidgets
import com.coffeejournal.ui.notify.ReminderTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/** The widget's provider, declared in the manifest with res/xml/coffee_widget_info.xml. */
class CoffeeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = CoffeeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        CoffeeWidgets.scheduleMidnightRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        CoffeeWidgets.cancelMidnightRefresh(context)
    }
}

/**
 * When the widget is redrawn: after the app's data changes (HomeWidgetSync, while the app process lives), when the app
 * goes to the background (MainActivity.onStop), from the daily reminder check, and shortly after midnight every day
 * while a widget is on the home screen ([WidgetRefreshWorker]), so the D-day moves on without the app being opened.
 */
object CoffeeWidgets {
    const val MIDNIGHT_WORK = "coffee-journal.widget-midnight"

    /** A minute past midnight: the new date is safely there. */
    val MIDNIGHT = ReminderTime(0, 1)

    suspend fun refresh(context: Context) = CoffeeWidget().updateAll(context.applicationContext)

    /** [refresh] without waiting (from a lifecycle callback); failures are dropped, the next refresh redraws. */
    fun refreshSoon(context: Context) {
        val scope = GlobalContext.getOrNull()?.getOrNull<AppScope>() ?: return
        scope.launch {
            try {
                refresh(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // no widget host (yet), or it is busy: nothing to redraw now
            }
        }
    }

    suspend fun hasWidgets(context: Context): Boolean =
        GlanceAppWidgetManager(context.applicationContext).getGlanceIds(CoffeeWidget::class.java).isNotEmpty()

    fun scheduleMidnightRefresh(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(MIDNIGHT.millisUntilNext(Dates.nowMillis()), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(MIDNIGHT_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelMidnightRefresh(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(MIDNIGHT_WORK)
    }

    /** At app start: the midnight refresh runs exactly while a widget exists. */
    suspend fun syncMidnightRefresh(context: Context) {
        if (hasWidgets(context)) scheduleMidnightRefresh(context) else cancelMidnightRefresh(context)
    }
}

/** [HomeWidgets] for the shared code (the reminder worker, the app-level observer). */
class GlanceHomeWidgets(private val context: Context) : HomeWidgets {
    override suspend fun refresh() = CoffeeWidgets.refresh(context)
}

/** Redraws the widget shortly after midnight (the D-day and "N일째" follow the date). */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        CoffeeWidgets.refresh(applicationContext)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.retry()
    }
}
