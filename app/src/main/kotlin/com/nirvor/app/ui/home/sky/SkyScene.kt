package com.nirvor.app.ui.home.sky

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Width and visible height of the scene in reference px. It is drawn a little taller (to 290). */
const val SCENE_W = 766f
const val SCENE_H = 290f

private const val TIMELAPSE_MS = 18_000L

/** Frame clock for the header: real time, or a fast-forwarded day while a time-lapse runs. */
@Stable
class SkyClock {
    /** Seconds since the header appeared; drives clouds, birds, rain and so on. */
    var animSec by mutableFloatStateOf(0f)
        private set

    /** The moment of the day being shown (epoch millis). */
    var sim by mutableLongStateOf(System.currentTimeMillis())
        private set

    var timelapseStartUptime by mutableStateOf<Long?>(null)
    private var timelapseBase = 0L

    /** The sunrise intro plays once per app start, not every time Home is shown. */
    var introPlayed = false

    fun startTimelapse() {
        timelapseBase = System.currentTimeMillis()
        timelapseStartUptime = SystemClock.uptimeMillis()
    }

    fun stopTimelapse() {
        timelapseStartUptime = null
    }

    internal fun tick(seconds: Float) {
        animSec = seconds
        val start = timelapseStartUptime
        sim = if (start != null) {
            val elapsed = SystemClock.uptimeMillis() - start
            if (elapsed >= TIMELAPSE_MS) {
                timelapseStartUptime = null
                System.currentTimeMillis()
            } else {
                timelapseBase + elapsed * (86_400_000L / TIMELAPSE_MS)
            }
        } else {
            System.currentTimeMillis()
        }
    }
}

/** Holds the live sky state used by the header (both the drawing and the text colours). */
@Stable
class SkyController(
    val clock: SkyClock,
    private val inputs: State<SkyInputs>,
) {
    private val calc = SkyCalculator()

    val frame: State<SkyFrame> = derivedStateOf {
        val i = inputs.value
        calc.frame(clock.sim, i.lat, i.lon, i.weather)
    }

    /** Text darkness, rounded so the header text only recomposes when it visibly changes. */
    val darkText: State<Float> = derivedStateOf { Math.round(frame.value.darkText * 20f) / 20f }
    val hour: State<Int> = derivedStateOf { frame.value.hour }
}

/** Where the sun/moon should be drawn and what weather to show. */
data class SkyInputs(val lat: Double?, val lon: Double?, val weather: SceneWeather)


// --------------------------------------------------------------------------- particles

private class CloudSpec(val x0: Float, val y: Float, val w: Float, val speed: Float, val alpha: Float)
private class StarSpec(val x: Float, val y: Float, val r: Float, val freq: Float, val phase: Float)
private class BirdSpec(val x0: Float, val y: Float, val speed: Float, val flap: Float, val phase: Float, val size: Float)
private class DropSpec(val x0: Float, val phase: Float, val speed: Float, val len: Float)

private class SceneAssets {
    val pale = layerPath(SceneData.PALE)
    val blue = layerPath(SceneData.BLUE)
    val green = layerPath(SceneData.GREEN)

    private val rnd = Random(20261007)

    val clouds: List<CloudSpec> = List(12) { i ->
        CloudSpec(
            x0 = if (i < 2) 90f + i * 70f else rnd.nextFloat() * 900f,
            y = if (i < 2) 30f + i * 4f else 34f + rnd.nextFloat() * 120f,
            w = if (i < 2) 110f else 80f + rnd.nextFloat() * 110f,
            speed = 0.6f + rnd.nextFloat() * 0.8f,
            alpha = 0.75f + rnd.nextFloat() * 0.25f,
        )
    }
    val deck: List<CloudSpec> = List(7) { i ->
        CloudSpec(
            x0 = i * 150f + rnd.nextFloat() * 60f,
            y = 18f + rnd.nextFloat() * 50f,
            w = 210f + rnd.nextFloat() * 120f,
            speed = 0.8f + rnd.nextFloat() * 0.4f,
            alpha = 0.85f + rnd.nextFloat() * 0.15f,
        )
    }
    val stars: List<StarSpec> = List(55) {
        StarSpec(
            x = rnd.nextFloat() * SCENE_W,
            y = 4f + rnd.nextFloat() * 196f,
            r = 0.6f + rnd.nextFloat() * 1.1f,
            freq = 0.3f + rnd.nextFloat() * 1.1f,
            phase = rnd.nextFloat() * 6.283f,
        )
    }
    val birds: List<BirdSpec> = listOf(
        BirdSpec(470f, 118f, 13f, 1.9f, 0.0f, 1.0f),
        BirdSpec(420f, 147f, 11f, 2.2f, 1.7f, 1.15f),
        BirdSpec(80f, 104f, 15f, 1.7f, 3.1f, 0.85f),
    )
    val drops: List<DropSpec> = List(180) {
        DropSpec(
            x0 = rnd.nextFloat() * 820f,
            phase = rnd.nextFloat(),
            speed = 0.85f + rnd.nextFloat() * 0.3f,
            len = 0.8f + rnd.nextFloat() * 0.4f,
        )
    }

    private fun layerPath(pts: FloatArray): Path = Path().apply {
        moveTo(pts[0], pts[1])
        var i = 2
        while (i < pts.size) {
            lineTo(pts[i], pts[i + 1])
            i += 2
        }
        lineTo(SCENE_W + 2f, SCENE_H + 20f)
        lineTo(-2f, SCENE_H + 20f)
        close()
    }
}

/** Small integer hash -> 0..1, used for lightning and shooting stars. */
private fun hash01(i: Int): Float {
    var x = i * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    x = x xor (x ushr 16)
    return (x and 0xFFFFFF) / 16777215f
}

private fun wrap(v: Float, m: Float): Float {
    val r = v % m
    return if (r < 0) r + m else r
}

/**
 * Position on the sun/moon arc: 0 rises from behind the blue mountain on the right, so the
 * morning sky looks like the reference picture; 1 sets behind the hills on the left.
 */
private fun arc(t: Float): Offset {
    val x = (640f - 580f * t).coerceIn(-80f, SCENE_W + 80f)
    val tc = t.coerceIn(0f, 1f)
    var y = 238f - 172f * sin(PI * tc).toFloat()
    if (t < 0f) y += -t * 500f
    if (t > 1f) y += (t - 1f) * 500f
    return Offset(x, y)
}

// --------------------------------------------------------------------------- the scene

@Composable
fun SkyScene(controller: SkyController, modifier: Modifier = Modifier) {
    val assets = remember { SceneAssets() }
    val clock = controller.clock

    // Frame loop: runs only while the header is on screen.
    LaunchedEffect(clock) {
        val base = clock.animSec
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { t -> clock.tick(base + (t - start) / 1_000_000_000f) }
        }
    }

    // The sun (or moon) rises into place when the app opens.
    val intro = remember { Animatable(if (clock.introPlayed) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!clock.introPlayed) {
            clock.introPlayed = true
            intro.animateTo(1f, tween(1800, easing = FastOutSlowInEasing))
        }
    }

    Canvas(modifier) {
        val f = controller.frame.value
        val t = controller.clock.animSec
        val k = size.width / SCENE_W
        scale(k, k, pivot = Offset.Zero) {
            drawSky(f, assets, t, intro.value)
        }
    }
}

private fun DrawScope.drawSky(f: SkyFrame, a: SceneAssets, t: Float, intro: Float) {
    val p = f.p
    val w = f.w

    // 1. sky
    drawRect(
        brush = Brush.verticalGradient(
            0f to p.skyTop, 0.45f to p.skyMid, 0.9f to p.skyLow,
            startY = 0f, endY = 268f,
        ),
        topLeft = Offset.Zero,
        size = Size(SCENE_W, SCENE_H),
    )

    // 2. warm glow on the horizon around sunrise / sunset
    val sunPos = arc(f.sunT)
    val horizonGlow = p.glowAlpha * (1f - f.dayness * 0.7f)
    if (horizonGlow > 0.01f) {
        val cx = sunPos.x.coerceIn(0f, SCENE_W)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(p.glow.copy(alpha = horizonGlow * 0.75f), p.glow.copy(alpha = 0f)),
                center = Offset(cx, 250f),
                radius = 360f,
            ),
            radius = 360f,
            center = Offset(cx, 250f),
        )
    }

    // 3. stars
    if (p.stars > 0.01f) {
        val starColor = Color(0xFFFFF8E7)
        for (s in a.stars) {
            val tw = 0.55f + 0.45f * sin(t * s.freq * 6.283f + s.phase)
            drawCircle(starColor.copy(alpha = (p.stars * tw).coerceIn(0f, 1f)), s.r, Offset(s.x, s.y))
        }
        drawShootingStar(p.stars * (1f - w.cover * 1.5f).coerceIn(0f, 1f), t)
    }

    // 4. moon (rises with the intro too)
    if (f.moonLit > 0.03f && f.moonT > -0.08f && f.moonT < 1.08f) {
        var mp = arc(f.moonT)
        if (intro < 1f) mp = Offset(mp.x, lerpF(300f, mp.y, intro))
        val night = 1f - f.dayness
        val alpha = (0.35f + 0.65f * night) * (1f - 0.9f * f.overcast) * (1f - w.fog * 0.6f)
        drawMoon(mp, 22f, f.moonPhase, alpha, night, p.skyTop)
    }

    // 5. sun
    val sunAlpha = (1f - f.overcast * 1.05f).coerceIn(0f, 1f) * (1f - w.fog * 0.5f)
    if (f.sunT > -0.1f && f.sunT < 1.1f && sunAlpha > 0.01f) {
        val r = 40.5f
        var c = sunPos
        if (intro < 1f) c = Offset(c.x, lerpF(320f, c.y, intro))
        val pulse = 0.9f + 0.1f * sin(t * 1.25f)
        val glowR = r * (2.6f + 1.6f * (1f - f.dayness))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    p.glow.copy(alpha = (p.glowAlpha + 0.15f) * pulse * sunAlpha),
                    p.glow.copy(alpha = 0f),
                ),
                center = c,
                radius = glowR,
            ),
            radius = glowR,
            center = c,
        )
        drawCircle(
            brush = Brush.verticalGradient(listOf(p.sunTop, p.sunBottom), startY = c.y - r, endY = c.y + r),
            radius = r,
            center = c,
            alpha = sunAlpha,
        )
    }

    // 6. drifting clouds (in front of sun and moon, behind the hills)
    val windSpeed = 3f + w.wind * 0.35f
    val count = (2f + w.cover * 10f).coerceAtMost(a.clouds.size.toFloat())
    for ((i, c) in a.clouds.withIndex()) {
        val vis = (count - i).coerceIn(0f, 1f)
        if (vis <= 0f) break
        val span = SCENE_W + c.w + 60f
        val x = wrap(c.x0 + t * windSpeed * c.speed, span) - c.w - 30f
        drawCloud(x, c.y, c.w, p.cloud, (p.cloudAlpha * c.alpha * vis * fadeInFor(intro)), f.overcast)
    }

    // 7. far mountains
    drawPath(
        a.pale,
        Brush.verticalGradient(listOf(p.pale, lerp(p.pale, p.blue, 0.18f)), startY = 145f, endY = 250f),
    )
    drawPath(
        a.blue,
        Brush.verticalGradient(listOf(lerp(p.blue, Color.White, 0.06f), p.blue), startY = 185f, endY = 250f),
    )

    // 8. fog behind the hills
    if (w.fog > 0.01f) {
        drawFogBand(150f, 46f, w.fog, p.skyLow, t, 0)
        drawFogBand(196f, 52f, w.fog, p.skyLow, t, 1)
    }

    // 9. green hills
    drawPath(
        a.green,
        Brush.horizontalGradient(
            0f to p.greenL, 0.18f to p.greenL, 0.64f to p.greenM, 0.86f to p.greenR, 1f to p.greenR,
            startX = 0f, endX = SCENE_W,
        ),
    )
    drawPath(
        a.green,
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0f)),
            startY = 175f, endY = 245f,
        ),
    )
    if (w.fog > 0.01f) drawFogBand(234f, 56f, w.fog, p.skyLow, t, 2)

    // 10. birds (daytime, no rain)
    val birdAlpha = smooth(0.25f, 0.6f, f.dayness) * (1f - smooth(0.12f, 0.35f, w.precip)) *
        (1f - smooth(0.6f, 0.9f, f.overcast)) * (1f - w.fog * 0.7f)
    if (birdAlpha > 0.01f) {
        for (b in a.birds) drawBird(b, t, p.bird.copy(alpha = birdAlpha))
    }

    // 11. heavy cloud deck for overcast / rain / storms
    if (f.overcast > 0.3f) {
        val deckAlpha = ((f.overcast - 0.3f) / 0.6f).coerceIn(0f, 1f)
        val deckColor = lerp(p.cloud, Color.Black, 0.12f + 0.18f * w.precip)
        for (c in a.deck) {
            val span = SCENE_W + c.w + 60f
            val x = wrap(c.x0 + t * windSpeed * 0.7f * c.speed, span) - c.w - 30f
            drawCloud(x, c.y, c.w, deckColor, deckAlpha * c.alpha * 0.9f * fadeInFor(intro), 1f)
        }
    }

    // 12. rain or snow
    if (w.precip > 0.01f) {
        if (w.snow) drawSnow(a, t, w) else drawRain(a, t, w, f.dayness)
    }

    // 13. lightning
    if (w.thunder) drawLightning(t)
}

private fun fadeInFor(intro: Float) = (0.35f + intro * 0.65f).coerceIn(0f, 1f)

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun smooth(a: Float, b: Float, v: Float) = SkyCalculator.smoothRange(a, b, v)

/** Flat-bottomed cartoon cloud like the faint ones in the reference. (x, baseY) is its bottom-left. */
private fun DrawScope.drawCloud(x: Float, baseY: Float, w: Float, color: Color, alpha: Float, shade: Float) {
    if (alpha <= 0.005f) return
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                Rect(x, baseY - 0.13f * w, x + w, baseY),
                CornerRadius(0.065f * w),
            )
        )
        addOval(Rect(center = Offset(x + 0.36f * w, baseY - 0.13f * w), radius = 0.17f * w))
        addOval(Rect(center = Offset(x + 0.62f * w, baseY - 0.1f * w), radius = 0.12f * w))
        addOval(Rect(center = Offset(x + 0.18f * w, baseY - 0.07f * w), radius = 0.09f * w))
    }
    val bottom = lerp(color, Color.Black, 0.04f + 0.22f * shade)
    drawPath(
        path,
        Brush.verticalGradient(listOf(color, bottom), startY = baseY - 0.3f * w, endY = baseY),
        alpha = alpha.coerceIn(0f, 1f),
    )
}

private fun DrawScope.drawMoon(c: Offset, r: Float, phase: Float, alpha: Float, night: Float, sky: Color) {
    if (alpha <= 0.01f) return
    val moon = Color(0xFFF6F2E4)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(moon.copy(alpha = 0.22f * night * alpha), moon.copy(alpha = 0f)),
            center = c,
            radius = r * 3.2f,
        ),
        radius = r * 3.2f,
        center = c,
    )
    // the dark part of the disc, faintly visible at night
    drawCircle(lerp(sky, moon, 0.18f).copy(alpha = 0.55f * night * alpha), r, c)
    // lit part: a limb half-circle plus the terminator half-ellipse
    val k = cos(2.0 * PI * phase).toFloat()
    val waxing = phase <= 0.5f
    val lit = Path()
    val steps = 32
    for (i in 0..steps) {
        val th = -PI / 2 + PI * i / steps
        val limbX = (r * cos(th)).toFloat() * (if (waxing) 1f else -1f)
        val y = (r * sin(th)).toFloat()
        if (i == 0) lit.moveTo(c.x + limbX, c.y + y) else lit.lineTo(c.x + limbX, c.y + y)
    }
    for (i in steps downTo 0) {
        val th = -PI / 2 + PI * i / steps
        val tx = (r * cos(th)).toFloat() * k * (if (waxing) 1f else -1f)
        val y = (r * sin(th)).toFloat()
        lit.lineTo(c.x + tx, c.y + y)
    }
    lit.close()
    drawPath(lit, moon, alpha = alpha.coerceIn(0f, 1f))
}

private fun DrawScope.drawShootingStar(alpha: Float, t: Float) {
    if (alpha < 0.3f) return
    val period = 11f
    val n = floor(t / period).toInt()
    if (hash01(n * 3 + 1) > 0.55f) return
    val start = n * period + 2f + hash01(n * 3 + 2) * 6f
    val dt = t - start
    if (dt < 0f || dt > 0.9f) return
    val sx = 260f + hash01(n * 3 + 3) * 440f
    val sy = 18f + hash01(n * 3 + 4) * 60f
    val head = Offset(sx - dt * 230f, sy + dt * 100f)
    val tail = Offset(head.x + 70f, head.y - 30f)
    val fade = (if (dt < 0.15f) dt / 0.15f else 1f - (dt - 0.15f) / 0.75f) * alpha
    drawLine(
        brush = Brush.linearGradient(listOf(Color.White.copy(alpha = fade), Color.White.copy(alpha = 0f)), head, tail),
        start = head,
        end = tail,
        strokeWidth = 1.8f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawBird(b: BirdSpec, t: Float, color: Color) {
    val x = wrap(b.x0 + t * b.speed, SCENE_W + 120f) - 60f
    val y = b.y + 4f * sin(t * 0.6f + b.phase)
    // flaps, with a short glide every few seconds
    val glide = smooth(0.55f, 0.9f, (sin(t * 0.45f + b.phase) + 1f) / 2f)
    val fl = sin(t * b.flap * 6.283f + b.phase) * (1f - glide * 0.85f)
    val s = b.size
    val path = Path().apply {
        moveTo(x - 11f * s, y + (-2f - 5f * fl) * s)
        cubicTo(x - 7f * s, y + (-6f - 3f * fl) * s, x - 3f * s, y - 5f * s, x, y)
        cubicTo(x + 3f * s, y - 5f * s, x + 7f * s, y + (-6f - 3f * fl) * s, x + 11f * s, y + (-2f - 5f * fl) * s)
    }
    drawPath(path, color, style = Stroke(width = 2.3f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawFogBand(cy: Float, h: Float, fog: Float, sky: Color, t: Float, seed: Int) {
    val fogColor = lerp(sky, Color.White, 0.45f)
    drawRect(
        brush = Brush.verticalGradient(
            listOf(fogColor.copy(alpha = 0f), fogColor.copy(alpha = 0.5f * fog), fogColor.copy(alpha = 0f)),
            startY = cy - h / 2f,
            endY = cy + h / 2f,
        ),
        topLeft = Offset(0f, cy - h / 2f),
        size = Size(SCENE_W, h),
    )
    for (i in 0 until 5) {
        val ow = 180f + hash01(seed * 31 + i) * 140f
        val speed = 3f + hash01(seed * 17 + i) * 4f
        val x = wrap(hash01(seed * 7 + i) * 900f + t * speed, SCENE_W + ow) - ow
        drawOval(
            fogColor.copy(alpha = 0.2f * fog),
            topLeft = Offset(x, cy - h * 0.32f),
            size = Size(ow, h * 0.64f),
        )
    }
}

private fun DrawScope.drawRain(a: SceneAssets, t: Float, w: SceneWeather, dayness: Float) {
    val drizzle = w.cond == SkyCond.Drizzle
    val n = (w.precip * a.drops.size).toInt().coerceIn(0, a.drops.size)
    val baseSpeed = if (drizzle) 320f else 580f
    val baseLen = if (drizzle) 8f else 15f
    val slant = 0.1f + w.wind * 0.006f
    val color = Color.White.copy(alpha = 0.3f + 0.22f * dayness)
    val fall = 330f
    for (i in 0 until n) {
        val d = a.drops[i]
        val y = wrap(d.phase * fall + t * baseSpeed * d.speed, fall) - 30f
        val x = wrap(d.x0 + y * slant, 820f) - 27f
        val len = baseLen * d.len
        drawLine(
            color,
            start = Offset(x - len * slant, y - len),
            end = Offset(x, y),
            strokeWidth = if (drizzle) 1.1f else 1.5f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawSnow(a: SceneAssets, t: Float, w: SceneWeather) {
    val n = (w.precip * 90).toInt().coerceAtMost(a.drops.size)
    val fall = 320f
    for (i in 0 until n) {
        val d = a.drops[i]
        val speed = 22f + 18f * (d.speed - 0.85f) / 0.3f
        val y = wrap(d.phase * fall + t * speed, fall) - 20f
        val sway = (6f + 8f * d.len) * sin(t * (0.25f + 0.25f * d.phase) * 6.283f + d.x0)
        val x = wrap(d.x0 + sway + t * w.wind * 0.3f, 800f) - 17f
        drawCircle(Color.White.copy(alpha = 0.9f), 1.1f + 1.3f * (d.len - 0.8f) / 0.4f, Offset(x, y))
    }
}

private fun DrawScope.drawLightning(t: Float) {
    val period = 6.5f
    val n = floor(t / period).toInt()
    val dt = t - (n * period + 0.5f + hash01(n) * 4.5f)
    val flash = when {
        dt in 0f..0.12f -> 1f - dt / 0.12f
        dt in 0.2f..0.34f -> 0.6f * (1f - (dt - 0.2f) / 0.14f)
        else -> 0f
    }
    if (flash <= 0f) return
    drawRect(Color.White.copy(alpha = 0.5f * flash), Offset.Zero, Size(SCENE_W, SCENE_H))
    if (dt <= 0.3f) {
        var x = 120f + hash01(n * 13 + 5) * 560f
        var y = 20f
        val bolt = Path().apply { moveTo(x, y) }
        for (i in 1..7) {
            x += (hash01(n * 29 + i) - 0.5f) * 36f
            y += 28f
            bolt.lineTo(x, y)
        }
        drawPath(bolt, Color.White.copy(alpha = 0.35f * flash), style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(bolt, Color.White.copy(alpha = min(1f, flash * 1.4f)), style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
