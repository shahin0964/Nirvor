package com.nirvor.app.ui.home.sky

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Sunrise, solar noon and sunset for one day, as epoch milliseconds. */
data class SunDay(val rise: Long, val noon: Long, val set: Long)

/**
 * Astronomy used by the header: real sunrise/sunset for the user's location
 * (NOAA sunrise equation, accurate to about a minute) and the real phase of the moon.
 */
object SunCalc {
    private const val DAY_MS = 86_400_000L
    private const val J2000_EPOCH_DAY = 10_957L // 2000-01-01
    private const val RAD = PI / 180.0

    fun sunDay(date: LocalDate, lat: Double?, lon: Double?, zone: ZoneId): SunDay {
        if (lat == null || lon == null) return fallback(date, zone)
        val n = (date.toEpochDay() - J2000_EPOCH_DAY).toDouble() + 0.0008
        val jStar = n - lon / 360.0
        val m = ((357.5291 + 0.98560028 * jStar) % 360.0 + 360.0) % 360.0
        val c = 1.9148 * sin(m * RAD) + 0.02 * sin(2 * m * RAD) + 0.0003 * sin(3 * m * RAD)
        val lambda = ((m + c + 180.0 + 102.9372) % 360.0 + 360.0) % 360.0
        val transit = 2451545.0 + jStar + 0.0053 * sin(m * RAD) - 0.0069 * sin(2 * lambda * RAD)
        val decl = asin(sin(lambda * RAD) * sin(23.4397 * RAD))
        val cosW = (sin(-0.833 * RAD) - sin(lat * RAD) * sin(decl)) / (cos(lat * RAD) * cos(decl))
        if (abs(cosW) > 1.0) return fallback(date, zone) // polar day or night
        val w = acos(cosW) / RAD
        return SunDay(
            rise = julianToMillis(transit - w / 360.0),
            noon = julianToMillis(transit),
            set = julianToMillis(transit + w / 360.0),
        )
    }

    /** Without a location: 06:00 / 12:00 / 18:00 local time. */
    private fun fallback(date: LocalDate, zone: ZoneId): SunDay {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return SunDay(start + 6 * 3_600_000L, start + 12 * 3_600_000L, start + 18 * 3_600_000L)
    }

    private fun julianToMillis(j: Double): Long = ((j - 2440587.5) * DAY_MS).toLong()

    // ---------------------------------------------------------------- Moon

    private const val SYNODIC_DAYS = 29.530588853
    private const val KNOWN_NEW_MOON = 947_182_440_000L // 2000-01-06 18:14 UTC

    /** 0 = new moon, 0.25 = first quarter, 0.5 = full, 0.75 = last quarter. */
    fun moonPhase(nowMillis: Long): Double {
        val days = (nowMillis - KNOWN_NEW_MOON) / DAY_MS.toDouble()
        val p = (days / SYNODIC_DAYS) % 1.0
        return if (p < 0) p + 1.0 else p
    }

    /** Fraction of the moon's disc that is lit (0..1). */
    fun moonIllumination(phase: Double): Double = (1 - cos(2 * PI * phase)) / 2

    /**
     * Where the moon is on its arc across the sky, 0 = rising, 1 = setting, outside 0..1 = below
     * the horizon. The moon crosses the sky about phase x 24.8 hours after the sun does.
     */
    fun moonProgress(nowMillis: Long, solarNoon: Long, phase: Double): Double {
        val lunarDay = 24.84 * 3_600_000.0
        var transit = solarNoon + phase * lunarDay
        val k = floor((nowMillis - transit) / lunarDay + 0.5)
        transit += k * lunarDay
        return (nowMillis - transit) / (lunarDay / 2) + 0.5
    }
}
