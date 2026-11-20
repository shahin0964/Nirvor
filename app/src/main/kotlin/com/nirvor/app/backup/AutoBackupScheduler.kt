package com.nirvor.app.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nirvor.app.NirvorApplication
import com.nirvor.app.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

object AutoBackupScheduler {

    private const val REQUEST_CODE = 8823
    private val isBackingUp = AtomicBoolean(false)

    /**
     * Checks if today's scheduled 11:30 PM backup was missed (e.g., app was closed,
     * device powered off, or first run). If missed or never run, triggers auto-backup
     * immediately in the background and reschedules the upcoming 11:30 PM alarm.
     * If already completed for this cycle, does nothing and waits for the next 11:30 PM.
     */
    fun checkAndTriggerOnAppOpen(context: Context) {
        val prefs = (context.applicationContext as? NirvorApplication)?.prefs ?: Prefs(context)
        if (!prefs.autoBackupEnabled) return

        val now = LocalDateTime.now()
        val today1130PM = now.withHour(23).withMinute(30).withSecond(0).withNano(0)
        val lastExpected1130PM = if (now.isAfter(today1130PM)) today1130PM else today1130PM.minusDays(1)
        val lastExpectedMillis = lastExpected1130PM.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val singleFile = BackupManager.getSingleAutoBackupFile(context)
        val lastBackup = prefs.lastBackupTime
        val needsBackup = !singleFile.exists() || lastBackup < lastExpectedMillis

        if (needsBackup && isBackingUp.compareAndSet(false, true)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val file = BackupManager.saveOfflineBackup(context, isAuto = true)
                    prefs.lastBackupTime = System.currentTimeMillis()
                    BackupNotifications.show(
                        context,
                        title = "দৈনিক অটো-ব্যাকআপ সম্পন্ন হয়েছে",
                        body = "সব ডেটা সফলভাবে সংরক্ষিত: ${file.name}"
                    )
                } catch (_: Exception) {
                } finally {
                    isBackingUp.set(false)
                }
            }
        }
        schedule(context)
    }

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
