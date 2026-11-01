package com.nirvor.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nirvor.app.data.ReminderEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object ReminderScheduler {
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TITLE = "reminder_title"
    const val EXTRA_MINUTE = "reminder_minute"
    const val EXTRA_REPEAT = "reminder_repeat"
    const val EXTRA_ICON = "reminder_icon"

    /** Next time this reminder should ring, or null when a one-time reminder is already past. */
    fun nextTrigger(r: ReminderEntity, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val time = LocalTime.of(r.minuteOfDay / 60, r.minuteOfDay % 60)
        return if (r.repeatDaily) {
            val todayAt = now.toLocalDate().atTime(time)
            if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
        } else {
            val at = LocalDate.ofEpochDay(r.epochDay).atTime(time)
            if (at.isAfter(now)) at else null
        }
    }

    private fun pendingIntent(context: Context, r: ReminderEntity): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_ID, r.id)
            putExtra(EXTRA_TITLE, r.title)
            putExtra(EXTRA_MINUTE, r.minuteOfDay)
            putExtra(EXTRA_REPEAT, r.repeatDaily)
            putExtra(EXTRA_ICON, r.icon)
        }
        return PendingIntent.getBroadcast(
            context,
            r.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    fun schedule(context: Context, r: ReminderEntity) {
        val at = nextTrigger(r) ?: return
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, r)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    fun cancel(context: Context, id: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pi)
            pi.cancel()
        }
    }
}
