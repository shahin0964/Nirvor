package com.nirvor.app.islamic

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** The five prayers plus sunrise, in the day's order. */
enum class Prayer(val bn: String, val bnOf: String, val en: String, val obligatory: Boolean) {
    Fajr("ফজর", "ফজরের", "Fajr", true),
    Sunrise("সূর্যোদয়", "সূর্যোদয়ের", "Sunrise", false),
    Dhuhr("যোহর", "যোহরের", "Dhuhr", true),
    Asr("আসর", "আসরের", "Asr", true),
    Maghrib("মাগরিব", "মাগরিবের", "Maghrib", true),
    Isha("এশা", "এশার", "Isha", true);

    companion object {
        val five = listOf(Fajr, Dhuhr, Asr, Maghrib, Isha)
    }
}

/**
 * Calculation conventions. [isha] is an angle, or minutes after Maghrib when [ishaMinutes] > 0.
 * Values are the published parameters of each organisation.
 */
enum class CalcMethod(val bn: String, val fajr: Double, val isha: Double, val ishaMinutes: Int = 0, val ishaRamadan: Int = 0) {
    Karachi("ইউনিভার্সিটি অব ইসলামিক সায়েন্সেস, করাচি (বাংলাদেশে প্রচলিত)", 18.0, 18.0),
    MWL("মুসলিম ওয়ার্ল্ড লীগ", 18.0, 17.0),
    ISNA("ISNA (উত্তর আমেরিকা)", 15.0, 15.0),
    Egypt("মিশর জেনারেল অথরিটি", 19.5, 17.5),
    UmmAlQura("উম্মুল কুরা, মক্কা", 18.5, 0.0, 90, 120),
    Gulf("উপসাগরীয় অঞ্চল (দুবাই)", 18.2, 18.2),
    Kuwait("কুয়েত", 18.0, 17.5),
    Qatar("কাতার", 18.0, 0.0, 90),
    Singapore("সিঙ্গাপুর / মালয়েশিয়া", 20.0, 18.0),
}

/** Settings that change the times. [offsets] are extra minutes for Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha. */
data class PrayerSettings(
    val method: CalcMethod = CalcMethod.Karachi,
    val hanafiAsr: Boolean = true,
    val offsets: List<Int> = List(6) { 0 },
)

/** One day's times as epoch milliseconds, in [Prayer] order. */
class DayTimes(val date: LocalDate, val millis: List<Long>) {
    operator fun get(p: Prayer): Long = millis[p.ordinal]
}

/**
 * Prayer times from the sun's position (the widely used "PrayTimes" method): Fajr and Isha by the
 * sun's angle below the horizon, sunrise/Maghrib at 0.833°, Asr by shadow length (Hanafi = 2×).
 */
object PrayerTimes {
    private const val ITERATIONS = 2
    private fun rad(d: Double) = d * Math.PI / 180.0
    private fun deg(r: Double) = r * 180.0 / Math.PI
    private fun fix(a: Double, b: Double) = (a - b * floor(a / b)).let { if (it < 0) it + b else it }
    private fun dsin(d: Double) = sin(rad(d))
    private fun dcos(d: Double) = cos(rad(d))
    private fun dtan(d: Double) = tan(rad(d))
    private fun darcsin(x: Double) = deg(asin(x))
    private fun darccos(x: Double) = deg(acos(x))
    private fun darctan2(y: Double, x: Double) = deg(atan2(y, x))
    private fun darccot(x: Double) = deg(atan(1 / x))

    private fun julian(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        val d = date.dayOfMonth
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    /** Declination (degrees) and equation of time (hours) for Julian day [jd]. */
    private fun sun(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fix(357.529 + 0.98560028 * d, 360.0)
        val q = fix(280.459 + 0.98564736 * d, 360.0)
        val l = fix(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fix(ra, 24.0)
        val decl = darcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    /**
     * Times for [date] at ([lat], [lon]) in [zone]. Far north / south, Fajr and Isha are kept within
     * the night by the "angle based" rule. [ramadan]: Umm al-Qura puts Isha later in Ramadan.
     */
    fun day(
        date: LocalDate,
        lat: Double,
        lon: Double,
        zone: ZoneId,
        s: PrayerSettings = PrayerSettings(),
        ramadan: Boolean = false,
        elevation: Double = 0.0,
    ): DayTimes {
        val jDate = julian(date) - lon / (15.0 * 24.0)
        val noonLocal = ZonedDateTime.of(date.atTime(12, 0), zone)
        val tz = noonLocal.offset.totalSeconds / 3600.0

        fun midDay(t: Double): Double = fix(12 - sun(jDate + t).second, 24.0)
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sun(jDate + t).first
            val noon = midDay(t)
            val v = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
            if (v < -1 || v > 1) return Double.NaN
            val h = darccos(v) / 15.0
            return noon + if (ccw) -h else h
        }
        fun asrTime(factor: Double, t: Double): Double {
            val decl = sun(jDate + t).first
            val angle = -darccot(factor + dtan(abs(lat - decl)))
            return angleTime(angle, t, false)
        }

        val rise = 0.833 + 0.0347 * sqrt(elevation.coerceAtLeast(0.0))
        // rough starting hours, refined by computing each time at the previous estimate
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(ITERATIONS) {
            val p = t.map { it / 24.0 }
            t = doubleArrayOf(
                angleTime(s.method.fajr, p[0], true),
                angleTime(rise, p[1], true),
                midDay(p[2]),
                asrTime(if (s.hanafiAsr) 2.0 else 1.0, p[3]),
                angleTime(rise, p[4], false),
                if (s.method.ishaMinutes > 0) Double.NaN else angleTime(s.method.isha, p[5], false),
            ).also { n -> for (i in n.indices) if (n[i].isNaN()) n[i] = t[i] }
        }
        val sunrise = t[1]
        val sunset = t[4]
        var fajrT = angleTime(s.method.fajr, t[0] / 24.0, true)
        val ishaMin = if (ramadan && s.method.ishaRamadan > 0) s.method.ishaRamadan else s.method.ishaMinutes
        var isha = if (ishaMin > 0) sunset + ishaMin / 60.0 else angleTime(s.method.isha, t[5] / 24.0, false)
        val dhuhr = t[2]
        val asr = t[3]

        // high latitudes ("angle based"): Fajr and Isha at most angle/60 of the night away from sunrise / sunset
        val night = 24 - (sunset - sunrise)
        val fajrPart = s.method.fajr / 60.0 * night
        if (fajrT.isNaN() || sunrise - fajrT > fajrPart) fajrT = sunrise - fajrPart
        if (s.method.ishaMinutes == 0) {
            val ishaPart = s.method.isha / 60.0 * night
            if (isha.isNaN() || isha - sunset > ishaPart) isha = sunset + ishaPart
        }

        val hours = listOf(fajrT, sunrise, dhuhr, asr, sunset, isha).map { it + tz - lon / 15.0 }
        val base = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val millis = hours.mapIndexed { i, h ->
            val minutes = Math.round(h * 60.0) + s.offsets.getOrElse(i) { 0 }
            base + minutes * 60_000L
        }
        return DayTimes(date, millis)
    }

    /** Bearing from a place to the Kaaba, degrees from true north. */
    fun qibla(lat: Double, lon: Double): Double {
        val k1 = rad(21.422487)
        val k2 = rad(39.826206)
        val p1 = rad(lat)
        val dl = k2 - rad(lon)
        val y = sin(dl) * cos(k1)
        val x = cos(p1) * sin(k1) - sin(p1) * cos(k1) * cos(dl)
        return (deg(atan2(y, x)) + 360.0) % 360.0
    }

    /** Distance to the Kaaba in km (great circle). */
    fun distanceToKaaba(lat: Double, lon: Double): Double {
        val r = 6371.0
        val dLat = rad(21.422487 - lat)
        val dLon = rad(39.826206 - lon)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(rad(lat)) * cos(rad(21.422487)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
