package com.hangulmate.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules (or cancels) the daily study reminder.
 *
 * When enabled, a [PeriodicWorkRequest] runs once a day with its first run
 * at the next occurrence of [hour]:00. The [Constraints] keep the run off
 * a low battery. [ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE] makes
 * every call authoritative: changing the hour re-times the next run.
 * When disabled, the unique work is cancelled outright.
 *
 * Called from [com.hangulmate.ui.profile.ProfileViewModel] whenever the
 * reminder settings change, and once at startup to re-assert the saved
 * settings. WorkManager itself persists the work across reboots.
 */
object ReminderScheduler {

    fun schedule(context: Context, enabled: Boolean, hour: Int) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(ReminderWorker.UNIQUE_WORK_NAME)
            return
        }
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelayMillis = next.timeInMillis - now.timeInMillis
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag("hangulmate_reminder")
            .build()
        workManager.enqueueUniquePeriodicWork(
            ReminderWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(ReminderWorker.UNIQUE_WORK_NAME)
    }
}
