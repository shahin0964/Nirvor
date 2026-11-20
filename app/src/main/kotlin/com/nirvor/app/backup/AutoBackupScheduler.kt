package com.nirvor.app.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nirvor.app.NirvorApplication
import com.nirvor.app.data.Prefs
import java.time.LocalDateTime
import java.time.ZoneId

object AutoBackupScheduler {

    private const val REQUEST_CODE = 8823

    fun schedule(context: Context) {
        val prefs = (context.applicationContext as? NirvorApplication)?.prefs ?: Prefs(context)
        if (!prefs.autoBackupEnabled) return

        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AutoBackupReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val now = LocalDateTime.now()
        var target = now.withHour(23).withMinute(30).withSecond(0).withNano(0)
        if (now.isAfter(target)) {
            target = target.plusDays(1)
        }
        val triggerMillis = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
            }
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AutoBackupReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) am.cancel(pi)
    }
}
