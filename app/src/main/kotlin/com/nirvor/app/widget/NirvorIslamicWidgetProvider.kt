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
import com.nirvor.app.islamic.bnDigits
import com.nirvor.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class NirvorIslamicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            val today = LocalDate.now()
            val hijri = Hijri.of(today)
            val islamicPrefs = IslamicPrefs(context)
            val dayTimes = islamicPrefs.times(today) ?: PrayerTimes.day(today, 23.8103, 90.4125, ZoneId.systemDefault())

            val fajrTime = Fmt.timeAt(dayTimes[Prayer.Fajr])
            val sunriseTime = Fmt.timeAt(dayTimes[Prayer.Sunrise])
            val dhuhrTime = Fmt.timeAt(dayTimes[Prayer.Dhuhr])
            val asrTime = Fmt.timeAt(dayTimes[Prayer.Asr])
            val maghribTime = Fmt.timeAt(dayTimes[Prayer.Maghrib])
            val ishaTime = Fmt.timeAt(dayTimes[Prayer.Isha])

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

            // Determine active prayer for card highlight
            val activePrayer: Prayer = when {
                nowMillis >= dayTimes[Prayer.Fajr] && nowMillis < dayTimes[Prayer.Sunrise] -> Prayer.Fajr
                nowMillis >= dayTimes[Prayer.Dhuhr] && nowMillis < dayTimes[Prayer.Asr] -> Prayer.Dhuhr
                nowMillis >= dayTimes[Prayer.Asr] && nowMillis < dayTimes[Prayer.Maghrib] -> Prayer.Asr
                nowMillis >= dayTimes[Prayer.Maghrib] && nowMillis < dayTimes[Prayer.Isha] -> Prayer.Maghrib
                nowMillis >= dayTimes[Prayer.Isha] || nowMillis < dayTimes[Prayer.Fajr] -> Prayer.Isha
                else -> Prayer.Dhuhr // Between sunrise and dhuhr: upcoming is dhuhr
            }

            // Bottom status text
            val statusText = when {
                // Night time: counting down to upcoming Sehri
                nowMillis >= todayMaghrib + 30 * 60_000L || nowMillis < todayFajr -> {
                    val targetFajr = if (nowMillis < todayFajr) todayFajr else tomorrowFajr
                    val sLeft = cnt(targetFajr - nowMillis)
                    "🌙 সেহরি শেষ হতে বাকি $sLeft (${if (nowMillis < todayFajr) fajrTime else tomorrowFajrTime})"
                }
                // Iftar window
                nowMillis >= todayMaghrib && nowMillis < todayMaghrib + 30 * 60_000L -> {
                    "☀️ ইফতারের সময় চলছে! ($maghribTime)"
                }
                // Daytime: counting down to Iftar
                else -> {
                    val iLeft = cnt(todayMaghrib - nowMillis)
                    "☀️ ইফতার হতে বাকি $iLeft ($maghribTime)"
                }
            }

            // Bengali Gregorian formatted date: e.g. শুক্রবার, ২০ নভেম্বর ২০২৬
            val bngDays = arrayOf("সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার", "রবিবার")
            val bngMonths = arrayOf("জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর")
            val dayName = bngDays[today.dayOfWeek.value - 1]
            val monthName = bngMonths[today.monthValue - 1]
            val dateFormatted = "$dayName, ${bnDigits(today.dayOfMonth.toString())} $monthName ${bnDigits(today.year.toString())}"

            val activeBg = R.drawable.bg_widget_waqt_active
            val normalBg = R.drawable.bg_widget_waqt_normal
            val activeNameColor = 0xFF064E3B.toInt()
            val activeTimeColor = 0xFF047857.toInt()
            val normalNameColor = 0xFFF3F4F6.toInt()
            val normalTimeColor = 0xFFCBD5E1.toInt()

            for (id in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_islamic_transparent)

                // Top Header
                views.setTextViewText(R.id.widget_islamic_hijri, hijri.text)
                views.setTextViewText(R.id.widget_islamic_date, dateFormatted)
                views.setTextViewText(R.id.widget_islamic_sunrise, "🌅 সূর্যোদয় $sunriseTime")
                views.setTextViewText(R.id.widget_islamic_sunset, "🌇 সূর্যাস্ত $maghribTime")
                views.setTextViewText(
                    R.id.widget_islamic_sehri_tag,
                    if (nowMillis >= todayMaghrib) "🌙 পরবর্তী সেহরি $tomorrowFajrTime" else "🌙 সেহরি শেষ $fajrTime"
                )

                // 1. Fajr
                views.setTextViewText(R.id.waqt_fajr_name, "ফজর")
                views.setTextViewText(R.id.waqt_fajr_time, fajrTime)
                views.setInt(R.id.waqt_fajr_layout, "setBackgroundResource", if (activePrayer == Prayer.Fajr) activeBg else normalBg)
                views.setTextColor(R.id.waqt_fajr_name, if (activePrayer == Prayer.Fajr) activeNameColor else normalNameColor)
                views.setTextColor(R.id.waqt_fajr_time, if (activePrayer == Prayer.Fajr) activeTimeColor else normalTimeColor)

                // 2. Dhuhr
                val dhuhrLabel = if (today.dayOfWeek == java.time.DayOfWeek.FRIDAY) "জুম'আ" else "যোহর"
                views.setTextViewText(R.id.waqt_dhuhr_name, dhuhrLabel)
                views.setTextViewText(R.id.waqt_dhuhr_time, dhuhrTime)
                views.setInt(R.id.waqt_dhuhr_layout, "setBackgroundResource", if (activePrayer == Prayer.Dhuhr) activeBg else normalBg)
                views.setTextColor(R.id.waqt_dhuhr_name, if (activePrayer == Prayer.Dhuhr) activeNameColor else normalNameColor)
                views.setTextColor(R.id.waqt_dhuhr_time, if (activePrayer == Prayer.Dhuhr) activeTimeColor else normalTimeColor)

                // 3. Asr
                views.setTextViewText(R.id.waqt_asr_name, "আসর")
                views.setTextViewText(R.id.waqt_asr_time, asrTime)
                views.setInt(R.id.waqt_asr_layout, "setBackgroundResource", if (activePrayer == Prayer.Asr) activeBg else normalBg)
                views.setTextColor(R.id.waqt_asr_name, if (activePrayer == Prayer.Asr) activeNameColor else normalNameColor)
                views.setTextColor(R.id.waqt_asr_time, if (activePrayer == Prayer.Asr) activeTimeColor else normalTimeColor)

                // 4. Maghrib
                views.setTextViewText(R.id.waqt_maghrib_name, "মাগরিব")
                views.setTextViewText(R.id.waqt_maghrib_time, maghribTime)
                views.setInt(R.id.waqt_maghrib_layout, "setBackgroundResource", if (activePrayer == Prayer.Maghrib) activeBg else normalBg)
                views.setTextColor(R.id.waqt_maghrib_name, if (activePrayer == Prayer.Maghrib) activeNameColor else normalNameColor)
                views.setTextColor(R.id.waqt_maghrib_time, if (activePrayer == Prayer.Maghrib) activeTimeColor else normalTimeColor)

                // 5. Isha
                views.setTextViewText(R.id.waqt_isha_name, "এশা")
                views.setTextViewText(R.id.waqt_isha_time, ishaTime)
                views.setInt(R.id.waqt_isha_layout, "setBackgroundResource", if (activePrayer == Prayer.Isha) activeBg else normalBg)
                views.setTextColor(R.id.waqt_isha_name, if (activePrayer == Prayer.Isha) activeNameColor else normalNameColor)
                views.setTextColor(R.id.waqt_isha_time, if (activePrayer == Prayer.Isha) activeTimeColor else normalTimeColor)

                // Bottom Status Banner
                views.setTextViewText(R.id.widget_islamic_status, statusText)

                // On click opens Islamic Screen
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
