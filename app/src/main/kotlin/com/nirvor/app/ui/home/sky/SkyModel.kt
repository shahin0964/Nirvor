package com.nirvor.app.ui.home.sky

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Colours of the scene at one moment of the day. */
data class SkyPalette(
    val skyTop: Color,
    val skyMid: Color,
    val skyLow: Color,
    val pale: Color,
    val blue: Color,
    val greenL: Color,
    val greenM: Color,
    val greenR: Color,
    val sunTop: Color,
    val sunBottom: Color,
    val glow: Color,
    val cloud: Color,
    val bird: Color,
    val glowAlpha: Float,
    val stars: Float,
    val cloudAlpha: Float,
) {
    fun lerpTo(o: SkyPalette, t: Float) = SkyPalette(
        lerp(skyTop, o.skyTop, t), lerp(skyMid, o.skyMid, t), lerp(skyLow, o.skyLow, t),
        lerp(pale, o.pale, t), lerp(blue, o.blue, t),
        lerp(greenL, o.greenL, t), lerp(greenM, o.greenM, t), lerp(greenR, o.greenR, t),
        lerp(sunTop, o.sunTop, t), lerp(sunBottom, o.sunBottom, t), lerp(glow, o.glow, t),
        lerp(cloud, o.cloud, t), lerp(bird, o.bird, t),
        glowAlpha + (o.glowAlpha - glowAlpha) * t,
        stars + (o.stars - stars) * t,
        cloudAlpha + (o.cloudAlpha - cloudAlpha) * t,
    )
}

/** Weather kinds the header can show. */
enum class SkyCond(val label: String) {
    Clear("পরিষ্কার"),
    PartlyCloudy("আংশিক মেঘ"),
    Overcast("মেঘলা"),
    Fog("কুয়াশা"),
    Drizzle("গুঁড়ি বৃষ্টি"),
    Rain("বৃষ্টি"),
    Thunder("ঝড়-বৃষ্টি"),
    Snow("তুষার"),
}

/** What the weather looks like, as numbers the renderer can use directly. */
data class SceneWeather(
    val cond: SkyCond,
    val cover: Float, // 0..1 how much of the sky is cloud
    val precip: Float, // 0..1 rain / snow intensity
    val fog: Float, // 0..1
    val wind: Float, // km/h
) {
    val thunder: Boolean get() = cond == SkyCond.Thunder
    val snow: Boolean get() = cond == SkyCond.Snow

    companion object {
        /** Used before any real weather has arrived: a clear sky with the reference's faint clouds. */
        val DEFAULT = SceneWeather(SkyCond.Clear, 0.12f, 0f, 0f, 8f)

        /** From Open-Meteo values: WMO weather code, cloud cover %, wind km/h, precipitation mm. */
        fun fromWeather(code: Int, cloudCover: Int?, wind: Double?, precipMm: Double?): SceneWeather {
            val cc = cloudCover?.let { it / 100f }
            val w = (wind ?: 8.0).toFloat()
            val rainBoost = ((precipMm ?: 0.0) / 6.0).toFloat().coerceIn(0f, 0.3f)
            fun cov(lo: Float, hi: Float) = (cc ?: (lo + hi) / 2f).coerceIn(lo, hi)
            return when (code) {
                0 -> SceneWeather(SkyCond.Clear, cov(0.05f, 0.15f), 0f, 0f, w)
                1 -> SceneWeather(SkyCond.Clear, cov(0.15f, 0.35f), 0f, 0f, w)
                2 -> SceneWeather(SkyCond.PartlyCloudy, cov(0.35f, 0.65f), 0f, 0f, w)
                3 -> SceneWeather(SkyCond.Overcast, cov(0.75f, 1f), 0f, 0f, w)
                45, 48 -> SceneWeather(SkyCond.Fog, cov(0.5f, 0.8f), 0f, 1f, w)
                51, 53, 55, 56, 57 -> SceneWeather(SkyCond.Drizzle, cov(0.8f, 1f), 0.3f + rainBoost, 0.15f, w)
                61, 80 -> SceneWeather(SkyCond.Rain, cov(0.8f, 1f), 0.5f + rainBoost, 0.1f, w)
                63, 81, 66 -> SceneWeather(SkyCond.Rain, cov(0.85f, 1f), 0.72f + rainBoost, 0.1f, w)
                65, 82, 67 -> SceneWeather(SkyCond.Rain, cov(0.9f, 1f), 1f, 0.15f, w)
                71, 77, 85 -> SceneWeather(SkyCond.Snow, cov(0.8f, 1f), 0.45f, 0.1f, w)
                73 -> SceneWeather(SkyCond.Snow, cov(0.85f, 1f), 0.7f, 0.1f, w)
                75, 86 -> SceneWeather(SkyCond.Snow, cov(0.9f, 1f), 1f, 0.15f, w)
                95, 96, 99 -> SceneWeather(SkyCond.Thunder, cov(0.92f, 1f), 0.9f, 0.1f, w)
                else -> DEFAULT.copy(wind = w)
            }
        }

        /** Representative look for each kind, used by the preview sheet. */
        fun preview(cond: SkyCond): SceneWeather = when (cond) {
            SkyCond.Clear -> SceneWeather(cond, 0.12f, 0f, 0f, 10f)
            SkyCond.PartlyCloudy -> SceneWeather(cond, 0.5f, 0f, 0f, 14f)
            SkyCond.Overcast -> SceneWeather(cond, 0.92f, 0f, 0f, 14f)
            SkyCond.Fog -> SceneWeather(cond, 0.65f, 0f, 1f, 4f)
            SkyCond.Drizzle -> SceneWeather(cond, 0.9f, 0.32f, 0.15f, 10f)
            SkyCond.Rain -> SceneWeather(cond, 0.95f, 0.78f, 0.1f, 16f)
            SkyCond.Thunder -> SceneWeather(cond, 1f, 0.95f, 0.1f, 24f)
            SkyCond.Snow -> SceneWeather(cond, 0.9f, 0.7f, 0.1f, 8f)
        }
    }
}

/** Everything the renderer needs for one moment. */
data class SkyFrame(
    val p: SkyPalette,
    /** Sun position on its arc: 0 = sunrise, 1 = sunset (outside = below horizon). */
    val sunT: Float,
    /** Moon position on its arc, same convention. */
    val moonT: Float,
    val moonPhase: Float,
    val moonLit: Float,
    /** 0 at night, 1 in full daylight. */
    val dayness: Float,
    val w: SceneWeather,
    /** How strongly the sky is greyed by cloud (0..1). */
    val overcast: Float,
    /** 0 = header text should be dark, 1 = header text should be white. */
    val darkText: Float,
    val hour: Int,
)

/** Caches sunrise/sunset per day so the per-frame work stays tiny. */
class SkyCalculator(private val zone: ZoneId = ZoneId.systemDefault()) {
    private var cacheKey: Triple<Long, Double?, Double?>? = null
    private var cacheDays: List<SunDay> = emptyList()

    private fun days(date: LocalDate, lat: Double?, lon: Double?): List<SunDay> {
        val key = Triple(date.toEpochDay(), lat, lon)
        if (key != cacheKey) {
            cacheDays = listOf(-1L, 0L, 1L).map { SunCalc.sunDay(date.plusDays(it), lat, lon, zone) }
            cacheKey = key
        }
        return cacheDays
    }

    fun frame(now: Long, lat: Double?, lon: Double?, weather: SceneWeather): SkyFrame {
        val zdt = Instant.ofEpochMilli(now).atZone(zone)
        val (yesterday, today, tomorrow) = days(zdt.toLocalDate(), lat, lon)

        // ---- palette from the day timeline
        val keys = ArrayList<Pair<Long, SkyPalette>>(30)
        for (d in listOf(yesterday, today, tomorrow)) {
            var last = Long.MIN_VALUE
            for ((pal, anchor, minutes) in Palettes.TIMELINE) {
                val base = when (anchor) {
                    Anchor.Rise -> d.rise
                    Anchor.Noon -> d.noon
                    Anchor.Set -> d.set
                }
                val t = base + minutes * 60_000L
                if (t > last) {
                    keys.add(t to pal)
                    last = t
                }
            }
        }
        keys.sortBy { it.first }
        var palette = keys.first().second
        for (i in 0 until keys.size - 1) {
            val (t0, p0) = keys[i]
            val (t1, p1) = keys[i + 1]
            if (now in t0..t1) {
                val f = ((now - t0).toFloat() / max(1L, t1 - t0).toFloat())
                palette = p0.lerpTo(p1, smooth(f))
                break
            }
        }

        // ---- sun and moon
        val sunT = (now - today.rise).toFloat() / (today.set - today.rise).toFloat()
        val dayness = if (sunT in 0f..1f) min(1f, sin(PI * sunT).toFloat() * 3f) else 0f
        val phase = SunCalc.moonPhase(now)
        val moonT = SunCalc.moonProgress(now, today.noon, phase).toFloat()
        val lit = SunCalc.moonIllumination(phase).toFloat()

        // ---- weather tint
        val overcast = (smoothRange(0.45f, 1f, weather.cover) * 0.75f + weather.precip * 0.2f +
            (if (weather.thunder) 0.1f else 0f)).coerceIn(0f, 0.92f)
        palette = applyWeather(palette, weather, overcast)

        // The greeting sits over the upper-middle of the sky, so judge contrast there.
        val textLight = 0.3f * lightness(palette.skyTop) + 0.7f * lightness(palette.skyMid)
        val darkText = 1f - smoothRange(0.42f, 0.56f, textLight)

        return SkyFrame(
            p = palette,
            sunT = sunT,
            moonT = moonT,
            moonPhase = phase.toFloat(),
            moonLit = lit,
            dayness = dayness,
            w = weather,
            overcast = overcast,
            darkText = darkText,
            hour = zdt.hour,
        )
    }

    private fun applyWeather(p: SkyPalette, w: SceneWeather, o: Float): SkyPalette {
        val rainDark = 1f - 0.2f * w.precip - (if (w.thunder) 0.12f else 0f)
        fun grey(c: Color, amount: Float): Color {
            val l = lightness(c) * rainDark
            val g = Color(
                (l * 0.96f).coerceIn(0f, 1f),
                (l * 0.99f).coerceIn(0f, 1f),
                (l * 1.05f).coerceIn(0f, 1f),
            )
            return lerp(c, g, amount)
        }
        val fogColor = lerp(p.skyLow, Color.White, 0.35f)
        fun fog(c: Color, amount: Float) = lerp(c, fogColor, amount * w.fog)
        return p.copy(
            skyTop = fog(grey(p.skyTop, o), 0.35f),
            skyMid = fog(grey(p.skyMid, o), 0.5f),
            skyLow = fog(grey(p.skyLow, o), 0.6f),
            pale = fog(grey(p.pale, o * 0.6f), 0.75f),
            blue = fog(grey(p.blue, o * 0.6f), 0.65f),
            greenL = fog(grey(p.greenL, o * 0.45f), 0.4f),
            greenM = fog(grey(p.greenM, o * 0.45f), 0.4f),
            greenR = fog(grey(p.greenR, o * 0.45f), 0.4f),
            cloud = grey(p.cloud, min(1f, o * 1.1f)),
            glowAlpha = p.glowAlpha * (1f - o),
            stars = p.stars * (1f - w.cover).coerceIn(0f, 1f) * (1f - w.fog * 0.8f),
            cloudAlpha = min(0.95f, p.cloudAlpha + 0.4f * o),
        )
    }

    companion object {
        fun lightness(c: Color) = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue

        fun smooth(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }

        fun smoothRange(a: Float, b: Float, v: Float) = smooth((v - a) / (b - a))
    }
}
