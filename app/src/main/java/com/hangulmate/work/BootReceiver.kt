package com.hangulmate.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.hangulmate.data.preferences.SettingsRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-asserts the saved study-reminder schedule after a device reboot.
 *
 * WorkManager persists the periodic work itself, but the schedule is
 * re-applied here from the saved settings so an enabled reminder can
 * never silently die across a reboot. When reminders are off, the call
 * is a no-op (any stale work is cancelled instead).
 */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        scope.launch {
            try {
                val settings = SettingsRepositoryImpl(context.applicationContext)
                val enabled = settings.remindersEnabled.first()
                val hour = settings.reminderHour.first()
                ReminderScheduler.schedule(context.applicationContext, enabled, hour)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
