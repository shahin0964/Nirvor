package com.nirvor.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nirvor.app.NirvorApplication
import com.nirvor.app.data.ReminderEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Shows the notification when a reminder rings, and books the next day for daily ones. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ReminderScheduler.EXTRA_ID, -1L)
        if (id < 0) return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Reminder"
        val minute = intent.getIntExtra(ReminderScheduler.EXTRA_MINUTE, 0)
        val repeat = intent.getBooleanExtra(ReminderScheduler.EXTRA_REPEAT, false)
        val icon = intent.getStringExtra(ReminderScheduler.EXTRA_ICON) ?: "bell"

        Notifications.show(context, id, title, minute)

        if (repeat) {
            ReminderScheduler.schedule(
                context,
                ReminderEntity(
                    id = id,
                    title = title,
                    epochDay = LocalDate.now().toEpochDay(),
                    minuteOfDay = minute,
                    repeatDaily = true,
                    icon = icon,
                ),
            )
        }
    }
}

/** Alarms are cleared on reboot, so book every reminder again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as NirvorApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.db.reminderDao().allOnce().forEach { ReminderScheduler.schedule(context, it) }
                DueAlarms.rescheduleAll(context, app.db)
                com.nirvor.app.islamic.PrayerAlarms.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
