package com.nirvor.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
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
import com.nirvor.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class NirvorWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as? NirvorApplication ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            val hijri = Hijri.of(today)
            val ym = YearMonth.from(today)
            val startDay = ym.atDay(1).toEpochDay()
            val endDay = ym.atEndOfMonth().toEpochDay()

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
                    "সেহরি শেষ: $fajrTime\nইফতার: $maghribTime"
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

                // Click on Expense opens Money screen
                val openMoney = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_OPEN, "money")
                }
                val piMoney = PendingIntent.getActivity(
                    context, 102, openMoney,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_expense_layout, piMoney)

                // Click on Ramadan/Prayer opens Islamic Tools
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
