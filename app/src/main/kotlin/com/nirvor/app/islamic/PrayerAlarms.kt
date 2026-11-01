package com.nirvor.app.islamic

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.nirvor.app.MainActivity
import com.nirvor.app.R
import com.nirvor.app.reminders.Notifications
import com.nirvor.app.reminders.ReminderScheduler
import com.nirvor.app.util.Fmt
import java.time.LocalDate
import java.time.ZoneId

/**
 * Adhan and jama'ah notifications. Only the next one of each (prayer × kind) is booked; when it
 * rings, everything is booked again from the new "now".
 */
object PrayerAlarms {
    const val KIND_ADHAN = 0
    const val KIND_JAMAAT = 1
    const val EXTRA_KIND = "prayer_kind"
    const val EXTRA_PRAYER = "prayer_index"
    const val EXTRA_AT = "prayer_at"
    private const val BASE = 3_000_000

    const val CH_ADHAN = "prayer_adhan"
    const val CH_SILENT = "prayer_silent"
    const val CH_AUDIO = "prayer_audio"
    const val CH_JAMAAT = "prayer_jamaat"

    private fun code(kind: Int, prayer: Int) = BASE + kind * 10 + prayer

    private fun intent(context: Context, kind: Int, prayer: Int) = Intent(context, PrayerReceiver::class.java).apply {
        data = android.net.Uri.parse("nirvor://prayer/$kind/$prayer")
        putExtra(EXTRA_KIND, kind)
        putExtra(EXTRA_PRAYER, prayer)
    }

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_ADHAN, "নামাজের সময় (শব্দসহ)", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SILENT, "নামাজের সময় (শুধু কম্পন)", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_AUDIO, "আজান বাজছে", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_JAMAAT, "জামাতের রিমাইন্ডার", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun cancelAll(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        for (kind in 0..1) for (p in 0..4) {
            PendingIntent.getBroadcast(context, code(kind, p), intent(context, kind, p), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let {
                    am.cancel(it)
                    it.cancel()
                }
        }
    }

    private fun set(context: Context, kind: Int, prayer: Int, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context,
            code(kind, prayer),
            intent(context, kind, prayer).putExtra(EXTRA_AT, at),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (ReminderScheduler.canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Books the next adhan and jama'ah alarms (call after any change and when one rings). */
    fun reschedule(context: Context) {
        cancelAll(context)
        val prefs = IslamicPrefs(context)
        if (!prefs.prayerSetUp) return
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = LocalDate.now(zone)
        val days = listOf(today, today.plusDays(1)).mapNotNull { d -> prefs.times(d, zone)?.let { d to it } }
        if (days.isEmpty()) return
        for (i in 0..4) {
            if (prefs.adhanMask and (1 shl i) != 0) {
                days.map { it.second[Prayer.five[i]] }.firstOrNull { it > now + 1000 }?.let { set(context, KIND_ADHAN, i, it) }
            }
            if (prefs.jamaatMask and (1 shl i) != 0) {
                days.map { (d, t) -> prefs.jamaatAt(i, d, t, zone) - prefs.jamaatLead * 60_000L }
                    .firstOrNull { it > now + 1000 }?.let { set(context, KIND_JAMAAT, i, it) }
            }
        }
    }

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        BASE,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** An adhan or jama'ah alarm went off. */
class PrayerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getIntExtra(PrayerAlarms.EXTRA_KIND, -1)
        val index = intent.getIntExtra(PrayerAlarms.EXTRA_PRAYER, -1)
        val prayer = Prayer.five.getOrNull(index)
        if (prayer != null && Notifications.canPost(context)) {
            PrayerAlarms.ensureChannels(context)
            val prefs = IslamicPrefs(context)
            if (kind == PrayerAlarms.KIND_ADHAN) {
                val at = intent.getLongExtra(PrayerAlarms.EXTRA_AT, System.currentTimeMillis())
                val audio = prefs.adhanSound == AdhanSound.Adhan && prefs.adhanFile.exists() &&
                    context.getSystemService(AudioManager::class.java)?.ringerMode == AudioManager.RINGER_MODE_NORMAL
                var started = false
                if (audio) {
                    started = try {
                        val s = Intent(context, AdhanService::class.java).putExtra(PrayerAlarms.EXTRA_PRAYER, index).putExtra(PrayerAlarms.EXTRA_AT, at)
                        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s) else context.startService(s)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (!started) {
                    val channel = if (prefs.adhanSound == AdhanSound.Silent) PrayerAlarms.CH_SILENT else PrayerAlarms.CH_ADHAN
                    notify(context, 3_100_000 + index, channel, "🕌 ${prayer.bnOf} ওয়াক্ত হয়েছে", "সময় ${Fmt.timeAt(at)} · নামাজ পড়তে ভুলবেন না", null)
                }
            } else if (kind == PrayerAlarms.KIND_JAMAAT) {
                val lead = prefs.jamaatLead
                notify(
                    context,
                    3_200_000 + index,
                    PrayerAlarms.CH_JAMAAT,
                    "🤝 ${prayer.bnOf} জামাত ${bnNum(lead)} মিনিট পরে",
                    "মসজিদে যাওয়ার প্রস্তুতি নিন",
                    null,
                )
            }
        }
        PrayerAlarms.reschedule(context)
    }

    companion object {
        fun notify(context: Context, id: Int, channel: String, title: String, body: String, action: NotificationCompat.Action?): Notification {
            val n = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(PrayerAlarms.openApp(context))
                .apply { if (action != null) addAction(action) }
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, n)
            } catch (e: SecurityException) {
                // notifications were turned off in between
            }
            return n
        }
    }
}

/** The clock or time zone changed: prayer alarms are booked again. */
class PrayerClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_TIME_SET || action == Intent.ACTION_TIMEZONE_CHANGED) {
            PrayerAlarms.reschedule(context)
        }
    }
}

/** Plays the adhan audio the user chose, with a "stop" button, then ends by itself. */
class AdhanService : Service() {
    private var player: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        PrayerAlarms.ensureChannels(this)
        val index = intent?.getIntExtra(PrayerAlarms.EXTRA_PRAYER, 0) ?: 0
        val at = intent?.getLongExtra(PrayerAlarms.EXTRA_AT, System.currentTimeMillis()) ?: System.currentTimeMillis()
        val prayer = Prayer.five.getOrElse(index) { Prayer.Fajr }
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, PrayerAlarms.CH_AUDIO)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("🕌 ${prayer.bnOf} আজান")
            .setContentText("সময় ${Fmt.timeAt(at)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(PrayerAlarms.openApp(this))
            .addAction(0, "থামান", stop)
            .setDeleteIntent(stop)
            .build()
        try {
            ServiceCompat.startForeground(this, 3_300_000, n, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        player?.release()
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(IslamicPrefs(this@AdhanService).adhanFile.absolutePath)
                setOnCompletionListener { stopSelf() }
                setOnErrorListener { _, _, _ ->
                    stopSelf()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            stopSelf()
            null
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.release()
        player = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.nirvor.app.STOP_ADHAN"
    }
}
