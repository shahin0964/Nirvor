package com.nirvor.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nirvor.app.MainActivity
import com.nirvor.app.R
import com.nirvor.app.islamic.Hijri
import com.nirvor.app.islamic.IslamicPrefs
import com.nirvor.app.islamic.Prayer
import com.nirvor.app.islamic.PrayerTimes
import com.nirvor.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class NirvorIslamicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val today = LocalDate.now()
            val hijri = Hijri.of(today)
            val islamicPrefs = IslamicPrefs(context)
            val dayTimes = islamicPrefs.times(today) ?: PrayerTimes.day(today, 23.8103, 90.4125, ZoneId.systemDefault())

            val fajr = Fmt.timeAt(dayTimes[Prayer.Fajr])
            val dhuhr = Fmt.timeAt(dayTimes[Prayer.Dhuhr])
            val asr = Fmt.timeAt(dayTimes[Prayer.Asr])
            val maghrib = Fmt.timeAt(dayTimes[Prayer.Maghrib])
            val isha = Fmt.timeAt(dayTimes[Prayer.Isha])

            val nowMillis = System.currentTimeMillis()
            val todayFajr = dayTimes[Prayer.Fajr]
            val todayMaghrib = dayTimes[Prayer.Maghrib]
            val tomorrowTimes = islamicPrefs.times(today.plusDays(1)) ?: PrayerTimes.day(today.plusDays(1), 23.8103, 90.4125, ZoneId.systemDefault())
            val tomorrowFajr = tomorrowTimes[Prayer.Fajr]
            val tomorrowMaghrib = tomorrowTimes[Prayer.Maghrib]

            fun cnt(diff: Long): String {
                val mTotal = (diff / 60_000L).coerceAtLeast(1L)
                val h = mTotal / 60L
                val m = mTotal % 60L
                return if (h > 0L) "${com.nirvor.app.islamic.bnDigits(h.toString())}ঘ ${com.nirvor.app.islamic.bnDigits(m.toString())}মি" else "${com.nirvor.app.islamic.bnDigits(m.toString())}মি"
            }

            val sehriLeft = if (nowMillis < todayFajr) cnt(todayFajr - nowMillis) else cnt(tomorrowFajr - nowMillis)
            val iftarLeft = if (nowMillis < todayMaghrib) cnt(todayMaghrib - nowMillis) else cnt(tomorrowMaghrib - nowMillis)

            for (id in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_islamic_transparent)

                views.setTextViewText(R.id.widget_islamic_hijri, hijri.text)
                views.setTextViewText(R.id.widget_islamic_sehri, "সেহরি $fajr (বাকি $sehriLeft)")
                views.setTextViewText(R.id.widget_islamic_iftar, "ইফতার $maghrib (বাকি $iftarLeft)")

                views.setTextViewText(R.id.prayer_fajr, "ফজর\n$fajr")
                views.setTextViewText(R.id.prayer_dhuhr, "যোহর\n$dhuhr")
                views.setTextViewText(R.id.prayer_asr, "আসর\n$asr")
                views.setTextViewText(R.id.prayer_maghrib, "মাগরিব\n$maghrib")
                views.setTextViewText(R.id.prayer_isha, "এশা\n$isha")

                val openIslamic = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN, "islamic")
                }
                val pi = PendingIntent.getActivity(
                    context, 201, openIslamic,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_islamic_root, pi)

                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }
}
