package com.nirvor.app.islamic

import android.content.Context
import com.nirvor.app.data.Prefs
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Where the prayer times are for: the weather city, or the phone's last location. */
class PrayerPlace(val name: String, val lat: Double, val lon: Double)

/** How the adhan sounds. */
enum class AdhanSound(val bn: String) { Adhan("আজানের অডিও"), Notification("নোটিফিকেশনের শব্দ"), Silent("শুধু কম্পন") }

/** Islamic Tools settings (prayer calculation, adhan, jama'ah, Hijri date). */
class IslamicPrefs(private val context: Context) {
    private val sp = context.getSharedPreferences("nirvor_islamic", Context.MODE_PRIVATE)

    var method: CalcMethod
        get() = CalcMethod.entries.firstOrNull { it.name == sp.getString("method", null) } ?: CalcMethod.Karachi
        set(v) = sp.edit().putString("method", v.name).apply()

    var hanafiAsr: Boolean
        get() = sp.getBoolean("hanafi", true)
        set(v) = sp.edit().putBoolean("hanafi", v).apply()

    /** Extra minutes for Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha. */
    var offsets: List<Int>
        get() = (sp.getString("offsets", null) ?: "").split(',').mapNotNull { it.toIntOrNull() }.let { l -> List(6) { l.getOrElse(it) { 0 } } }
        set(v) = sp.edit().putString("offsets", v.joinToString(",")).apply()

    /** Days added to the Umm al-Qura Hijri date (local moon sighting). */
    var hijriAdjust: Int
        get() = sp.getInt("hijri_adjust", 0)
        set(v) = sp.edit().putInt("hijri_adjust", v.coerceIn(-2, 2)).apply()

    /** True once the prayer page has been opened: only then are adhan alarms booked. */
    var prayerSetUp: Boolean
        get() = sp.getBoolean("prayer_setup", false)
        set(v) = sp.edit().putBoolean("prayer_setup", v).apply()

    /** Adhan notification per obligatory prayer (bit i = Prayer.five[i]). */
    var adhanMask: Int
        get() = sp.getInt("adhan_mask", 0b11111)
        set(v) = sp.edit().putInt("adhan_mask", v).apply()

    var adhanSound: AdhanSound
        get() = AdhanSound.entries.firstOrNull { it.name == sp.getString("adhan_sound", null) } ?: AdhanSound.Notification
        set(v) = sp.edit().putString("adhan_sound", v.name).apply()

    /** The adhan audio the user picked, copied into the app. */
    val adhanFile: File get() = File(context.filesDir, "islamic/adhan_audio")

    var adhanFileName: String
        get() = sp.getString("adhan_name", "") ?: ""
        set(v) = sp.edit().putString("adhan_name", v).apply()

    /** Jama'ah reminder per obligatory prayer (bit i = Prayer.five[i]). */
    var jamaatMask: Int
        get() = sp.getInt("jamaat_mask", 0)
        set(v) = sp.edit().putInt("jamaat_mask", v).apply()

    /** Jama'ah time of the mosque, minute of the day, per obligatory prayer; -1 = [jamaatAfter] minutes after the adhan time. */
    var jamaatMinutes: List<Int>
        get() = (sp.getString("jamaat_minutes", null) ?: "").split(',').mapNotNull { it.toIntOrNull() }.let { l -> List(5) { l.getOrElse(it) { -1 } } }
        set(v) = sp.edit().putString("jamaat_minutes", v.joinToString(",")).apply()

    var jamaatAfter: Int
        get() = sp.getInt("jamaat_after", 15)
        set(v) = sp.edit().putInt("jamaat_after", v).apply()

    /** Minutes before the jama'ah to remind. */
    var jamaatLead: Int
        get() = sp.getInt("jamaat_lead", 10)
        set(v) = sp.edit().putInt("jamaat_lead", v).apply()

    fun settings() = PrayerSettings(method, hanafiAsr, offsets)

    fun place(): PrayerPlace? {
        val p = Prefs(context)
        p.city()?.let { return PrayerPlace(it.name, it.lat, it.lon) }
        return p.lastCoords()?.let { (lat, lon) -> PrayerPlace("ফোনের লোকেশন", lat, lon) }
    }

    /** Times for [date] at the saved place, or null when there is no place yet. */
    fun times(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DayTimes? {
        val pl = place() ?: return null
        val ramadan = Hijri.of(date, hijriAdjust).month == 9
        return PrayerTimes.day(date, pl.lat, pl.lon, zone, settings(), ramadan)
    }

    /** When the jama'ah of [prayer] (index in Prayer.five) is on [date]. */
    fun jamaatAt(prayer: Int, date: LocalDate, day: DayTimes, zone: ZoneId = ZoneId.systemDefault()): Long {
        val fixed = jamaatMinutes[prayer]
        return if (fixed >= 0) {
            date.atStartOfDay(zone).toInstant().toEpochMilli() + fixed * 60_000L
        } else {
            day[Prayer.five[prayer]] + jamaatAfter * 60_000L
        }
    }
}
