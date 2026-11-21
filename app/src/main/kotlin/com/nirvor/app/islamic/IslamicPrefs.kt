package com.nirvor.app.islamic

import android.content.Context
import com.nirvor.app.data.Prefs
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

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

    /** Whether custom (manually typed) prayer times are active instead of auto calculation. */
    var customTimesEnabled: Boolean
        get() = sp.getBoolean("custom_times_enabled", false)
        set(v) = sp.edit().putBoolean("custom_times_enabled", v).apply()

    /** 6 times in "HH:mm" (24-hour) for Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha. */
    var customTimes: List<String>
        get() {
            val raw = sp.getString("custom_times", null) ?: return DEFAULT_CUSTOM_TIMES
            val list = raw.split(",")
            return if (list.size == 6) list else DEFAULT_CUSTOM_TIMES
        }
        set(v) = sp.edit().putString("custom_times", v.joinToString(",")).apply()

    fun hasCustomTimes(): Boolean = sp.contains("custom_times")

    fun setCustomTime(index: Int, timeStr: String) {
        val list = customTimes.toMutableList()
        if (index in 0..5) {
            list[index] = timeStr
            customTimes = list
        }
    }

    /** Copies currently calculated astronomical times into the custom times list. */
    fun copyCalculatedToCustom(date: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val pl = place() ?: PrayerPlace("ঢাকা", 23.8103, 90.4125)
        val ramadan = Hijri.of(date, hijriAdjust).month == 9
        val calc = PrayerTimes.day(date, pl.lat, pl.lon, zone, settings(), ramadan)
        val formatted = Prayer.entries.map { p ->
            val zdt = java.time.Instant.ofEpochMilli(calc[p]).atZone(zone)
            String.format(Locale.US, "%02d:%02d", zdt.hour, zdt.minute)
        }
        customTimes = formatted
        return true
    }

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
        p.lastCoords()?.let { (lat, lon) -> PrayerPlace("ফোনের লোকেশন", lat, lon) }?.let { return it }
        if (customTimesEnabled) return PrayerPlace("কাস্টম সময়", 23.8103, 90.4125)
        return null
    }

    /** Times for [date] at the saved place, or null when there is no place yet. */
    fun times(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): DayTimes? {
        if (customTimesEnabled) {
            val list = customTimes
            val millis = Prayer.entries.mapIndexed { i, _ ->
                val (h, m) = parseHourMinute(list.getOrElse(i) { DEFAULT_CUSTOM_TIMES[i] })
                val dt = date.atTime(h, m).atZone(zone)
                val offset = offsets.getOrElse(i) { 0 } * 60_000L
                dt.toInstant().toEpochMilli() + offset
            }
            return DayTimes(date, millis)
        }
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

    companion object {
        val DEFAULT_CUSTOM_TIMES = listOf("05:00", "06:15", "12:15", "16:15", "17:30", "19:45")

        fun parseHourMinute(s: String): Pair<Int, Int> {
            val parts = s.trim().split(":")
            val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 12
            val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
            return h to m
        }
    }
}
