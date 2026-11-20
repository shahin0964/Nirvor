package com.nirvor.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nirvor.app.MainActivity
import com.nirvor.app.NirvorApplication
import com.nirvor.app.R
import com.nirvor.app.islamic.Hijri
import com.nirvor.app.islamic.IslamicPrefs
import com.nirvor.app.islamic.Prayer
import com.nirvor.app.islamic.PrayerTimes
import com.nirvor.app.islamic.bnDigits
import com.nirvor.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class NirvorWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as NirvorApplication
            val today = LocalDate.now()
            val hijri = Hijri.of(today)

            // Current Month Total Expense
            val startDay = today.withDayOfMonth(1).toEpochDay()
            val endDay = today.withDayOfMonth(today.lengthOfMonth()).toEpochDay()
            val allExpenses = try {
                app.db.expenseDao().all().first()
            } catch (_: Exception) {
                emptyList()
            }
            val monthTotal = allExpenses.filter { it.epochDay in startDay..endDay }.sumOf { it.amount }

            val islamicPrefs = IslamicPrefs(context)
            val dayTimes = islamicPrefs.times(today) ?: PrayerTimes.day(today, 23.8103, 90.4125, ZoneId.systemDefault())

            val fajrTime = Fmt.timeAt(dayTimes[Prayer.Fajr])
            val maghribTime = Fmt.timeAt(dayTimes[Prayer.Maghrib])

            val nowMillis = System.currentTimeMillis()
            val todayFajr = dayTimes[Prayer.Fajr]
            val todayMaghrib = dayTimes[Prayer.Maghrib]
            val tomorrowDate = today.plusDays(1)
            val tomorrowTimes = islamicPrefs.times(tomorrowDate) ?: PrayerTimes.day(tomorrowDate, 23.8103, 90.4125, ZoneId.systemDefault())
            val tomorrowFajr = tomorrowTimes[Prayer.Fajr]
            val tomorrowFajrTime = Fmt.timeAt(tomorrowFajr)

            fun cnt(diff: Long): String {
                val mTotal = (diff / 60_000L).coerceAtLeast(1L)
                val h = mTotal / 60L
                val m = mTotal % 60L
                return if (h > 0L) "${bnDigits(h.toString())}ঘ ${bnDigits(m.toString())}মি" else "${bnDigits(m.toString())}মি"
            }

            val (sehriStr, iftarStr) = when {
                // 1. Before today's Fajr (Early morning)
                nowMillis < todayFajr -> {
                    val sLeft = cnt(todayFajr - nowMillis)
                    "সেহরি $fajrTime (বাকি $sLeft)" to "ইফতার $maghribTime"
                }
                // 2. Between today's Fajr and Maghrib (Daytime)
                nowMillis < todayMaghrib -> {
                    val iLeft = cnt(todayMaghrib - nowMillis)
                    "সেহরি শেষ ($fajrTime)" to "ইফতার $maghribTime (বাকি $iLeft)"
                }
                // 3. Iftar window (Maghrib to Maghrib + 30 mins)
                nowMillis < todayMaghrib + 30 * 60_000L -> {
                    val sLeft = cnt(tomorrowFajr - nowMillis)
                    "সেহরি $tomorrowFajrTime (বাকি $sLeft)" to "ইফতারের সময় চলছে!"
                }
                // 4. After Maghrib + 30 mins (Night time)
                else -> {
                    val sLeft = cnt(tomorrowFajr - nowMillis)
                    "সেহরি $tomorrowFajrTime (বাকি $sLeft)" to "ইফতার সম্পন্ন ($maghribTime)"
                }
            }

            val nextPrayerText = when {
                nowMillis < dayTimes[Prayer.Fajr] -> "ফজর: $fajrTime"
                nowMillis < dayTimes[Prayer.Sunrise] -> "সূর্যোদয়: ${Fmt.timeAt(dayTimes[Prayer.Sunrise])}"
                nowMillis < dayTimes[Prayer.Dhuhr] -> "যোহর: ${Fmt.timeAt(dayTimes[Prayer.Dhuhr])}"
                nowMillis < dayTimes[Prayer.Asr] -> "আসর: ${Fmt.timeAt(dayTimes[Prayer.Asr])}"
                nowMillis < dayTimes[Prayer.Maghrib] -> "মাগরিব (ইফতার): $maghribTime"
                nowMillis < dayTimes[Prayer.Isha] -> "এশা: ${Fmt.timeAt(dayTimes[Prayer.Isha])}"
                else -> "ফজর: $fajrTime"
            }

            for (id in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_nirvor_transparent)

                views.setTextViewText(R.id.widget_hijri_date, hijri.text)
                views.setTextViewText(R.id.widget_next_prayer_badge, nextPrayerText)
                views.setTextViewText(R.id.widget_expense_amount, "৳ " + Fmt.money(monthTotal))
                views.setTextViewText(
                    R.id.widget_sehri_iftar_times,
                    "$sehriStr\n$iftarStr"
                )

                // Click on root opens Nirvor Home
                val openMain = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val piMain = PendingIntent.getActivity(
                    context, 101, openMain,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, piMain)

                // Click on Expense card opens Money Screen
                val openMoney = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN, "money")
                }
                val piMoney = PendingIntent.getActivity(
                    context, 102, openMoney,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_expense_layout, piMoney)

                // Click on Islamic card opens Islamic Tools
                val openIslamic = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN, "islamic")
                }
                val piIslamic = PendingIntent.getActivity(
                    context, 103, openIslamic,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_islamic_layout, piIslamic)

                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }
}
