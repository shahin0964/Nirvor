package com.nirvor.app.ui.tools

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.tools.toolPrefs
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.browser.findActivity
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.cos
import kotlin.math.sin

// ================================================================ Stopwatch

/** Kept outside the screen so the stopwatch keeps running while you use other parts of the app. */
object StopwatchState {
    var running by mutableStateOf(false)
        private set
    private var startAt = 0L
    private var accumulated = 0L
    val laps = mutableStateListOf<Long>()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = toolPrefs(context)
        running = p.getBoolean("sw_running", false)
        startAt = p.getLong("sw_start", 0L)
        accumulated = p.getLong("sw_acc", 0L)
        laps.clear()
        p.getString("sw_laps", "")?.split(',')?.mapNotNull { it.toLongOrNull() }?.let { laps.addAll(it) }
        // The phone restarted since: keep the time it had, paused.
        if (running && startAt > SystemClock.elapsedRealtime()) running = false
    }

    private fun save(context: Context) {
        toolPrefs(context).edit()
            .putBoolean("sw_running", running)
            .putLong("sw_start", startAt)
            .putLong("sw_acc", accumulated)
            .putString("sw_laps", laps.joinToString(","))
            .apply()
    }

    fun elapsed(now: Long = SystemClock.elapsedRealtime()): Long = accumulated + if (running) now - startAt else 0L

    fun start(context: Context) {
        startAt = SystemClock.elapsedRealtime()
        running = true
        save(context)
    }

    fun pause(context: Context) {
        accumulated = elapsed()
        running = false
        save(context)
    }

    fun lap(context: Context) {
        laps.add(elapsed())
        save(context)
    }

    fun reset(context: Context) {
        running = false
        accumulated = 0L
        laps.clear()
        save(context)
    }
}

/** 83456 -> "01:23.45", 3_723_450 -> "1:02:03.45" */
internal fun swText(ms: Long): String {
    val cs = (ms / 10) % 100
    val s = (ms / 1000) % 60
    val m = (ms / 60_000) % 60
    val h = ms / 3_600_000
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs)
    else String.format(java.util.Locale.US, "%02d:%02d.%02d", m, s, cs)
}

@Composable
fun StopwatchTool(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { StopwatchState.load(context) }
    val running = StopwatchState.running
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        now = SystemClock.elapsedRealtime()
        while (running) {
            withFrameMillis { now = SystemClock.elapsedRealtime() }
        }
    }
    val elapsed = StopwatchState.elapsed(now)
    val laps = StopwatchState.laps

    ToolScaffold("Stopwatch", "স্টপওয়াচ", onBack) {
        ListCard {
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                StopwatchDial(elapsed, Modifier.fillMaxWidth().aspectRatio(1f))
                Column(Modifier.padding(top = 150.r), horizontalAlignment = Alignment.CenterHorizontally) {
                    RText(swText(elapsed), 50, FontWeight.Bold, C.Ink, align = TextAlign.Center)
                    if (laps.isNotEmpty()) {
                        RText("ল্যাপ ${laps.size + 1}: ${swText(elapsed - laps.last())}", 18, FontWeight.Medium, C.StatLabel)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.r)) {
            if (running) {
                RoundButton("🏁 ল্যাপ", TBlue, Modifier.weight(1f), filled = false) { StopwatchState.lap(context) }
                RoundButton("⏸ থামান", TAmber, Modifier.weight(1f)) { StopwatchState.pause(context) }
            } else {
                RoundButton("↺ রিসেট", TRed, Modifier.weight(1f), filled = false) { StopwatchState.reset(context) }
                RoundButton(if (elapsed > 0) "▶ আবার চালু" else "▶ শুরু", TGreen, Modifier.weight(1f)) { StopwatchState.start(context) }
            }
        }
        if (laps.isNotEmpty()) {
            val splits = laps.mapIndexed { i, t -> t - (if (i == 0) 0L else laps[i - 1]) }
            val best = if (splits.size >= 2) splits.min() else -1L
            val worst = if (splits.size >= 2) splits.max() else -1L
            ListCard {
                RText("ল্যাপ", 21, FontWeight.SemiBold, C.Ink)
                Spacer(Modifier.height(6.r))
                for (i in splits.indices.reversed()) {
                    val sp = splits[i]
                    val color = when (sp) {
                        best -> TGreenDark
                        worst -> TRed
                        else -> C.Ink
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.r), verticalAlignment = Alignment.CenterVertically) {
                        RText("ল্যাপ ${i + 1}", 18, FontWeight.Medium, C.StatLabel, Modifier.weight(1f))
                        RText(swText(sp), 19, FontWeight.SemiBold, color, Modifier.weight(1f), align = TextAlign.Center)
                        RText(swText(laps[i]), 17, FontWeight.Normal, C.Hint, Modifier.weight(1f), align = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun StopwatchDial(ms: Long, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val style = TextStyle(fontFamily = Inter, fontSize = 18.rs, color = Color(0xFF41597A), fontWeight = FontWeight.Medium)
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f * 0.96f
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFF1F5F9)), c, r), r, c)
        drawCircle(Color(0xFFE3E9EF), r, c, style = Stroke(6f))
        for (i in 0 until 300) {
            val a = Math.toRadians(i * 1.2 - 90.0)
            val long = i % 25 == 0
            val mid = i % 5 == 0
            val len = if (long) r * 0.09f else if (mid) r * 0.05f else r * 0.025f
            val ro = r * 0.94f
            drawLine(
                if (long) Color(0xFF0B0E22) else Color(0xFFAAB5C1),
                Offset(c.x + ro * cos(a).toFloat(), c.y + ro * sin(a).toFloat()),
                Offset(c.x + (ro - len) * cos(a).toFloat(), c.y + (ro - len) * sin(a).toFloat()),
                if (long) 4f else 2f,
            )
        }
        for (s in 5..60 step 5) {
            val a = Math.toRadians(s * 6.0 - 90.0)
            val layout = tm.measure(s.toString(), style)
            val rr = r * 0.74f
            drawText(
                layout,
                topLeft = Offset(
                    c.x + rr * cos(a).toFloat() - layout.size.width / 2f,
                    c.y + rr * sin(a).toFloat() - layout.size.height / 2f,
                ),
            )
        }
        // progress ring of the current minute
        val secAngle = (ms % 60_000L) / 60_000f * 360f
        drawArc(
            Brush.sweepGradient(listOf(Color(0x000BA765), Color(0xFF0BA765)), c),
            -90f,
            secAngle,
            false,
            Offset(c.x - r * 0.985f, c.y - r * 0.985f),
            androidx.compose.ui.geometry.Size(r * 1.97f, r * 1.97f),
            style = Stroke(r * 0.03f),
        )
        rotate(secAngle, c) {
            val hand = Path().apply {
                moveTo(c.x - 4f, c.y + r * 0.15f)
                lineTo(c.x, c.y - r * 0.9f)
                lineTo(c.x + 4f, c.y + r * 0.15f)
                close()
            }
            drawPath(hand, Color(0xFFE5484D))
        }
        drawCircle(Color(0xFFE5484D), r * 0.04f, c)
        drawCircle(Color.White, r * 0.015f, c)
    }
}

// ================================================================ Flashlight

private val SCREEN_COLORS = listOf(
    "সাদা" to Color.White,
    "হালকা হলুদ" to Color(0xFFFFE8B0),
    "লাল" to Color(0xFFFF2D2D),
    "সবুজ" to Color(0xFF22E07A),
    "নীল" to Color(0xFF3B82F6),
)

/** Torch with brightness (Android 13+), strobe, SOS, and a screen light. Turns off when you leave. */
@Composable
fun FlashlightTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val cm = remember { context.getSystemService(CameraManager::class.java) }
    val camId = remember {
        try {
            cm?.cameraIdList?.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }
    }
    val maxLevel = remember {
        if (Build.VERSION.SDK_INT >= 33 && cm != null && camId != null) {
            try {
                cm.getCameraCharacteristics(camId).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
            } catch (e: Exception) {
                1
            }
        } else {
            1
        }
    }
    var on by remember { mutableStateOf(false) }
    var mode by rememberSaveable { mutableIntStateOf(if (camId == null) 3 else 0) } // 0 steady, 1 strobe, 2 SOS, 3 screen
    var level by rememberSaveable { mutableIntStateOf(maxLevel) }
    var hz by rememberSaveable { mutableFloatStateOf(5f) }
    var screenColor by rememberSaveable { mutableIntStateOf(0) }
    var flash by remember { mutableStateOf(false) } // torch currently lit (for the drawing)

    fun torch(state: Boolean) {
        flash = state
        if (cm == null || camId == null) return
        try {
            if (state && maxLevel > 1 && Build.VERSION.SDK_INT >= 33) {
                cm.turnOnTorchWithStrengthLevel(camId, level.coerceIn(1, maxLevel))
            } else {
                cm.setTorchMode(camId, state)
            }
        } catch (e: Exception) {
            // Camera busy (another app); nothing to do.
        }
    }

    LaunchedEffect(on, mode, hz, level) {
        if (!on || mode == 3) {
            torch(false)
            return@LaunchedEffect
        }
        when (mode) {
            0 -> torch(true)
            1 -> {
                val half = (500f / hz).toLong().coerceAtLeast(20L)
                while (isActive) {
                    torch(true)
                    delay(half)
                    torch(false)
                    delay(half)
                }
            }
            else -> {
                // ... --- ...
                val unit = 180L
                val pattern = listOf(1, 1, 1, 3, 3, 3, 1, 1, 1)
                while (isActive) {
                    pattern.forEachIndexed { i, len ->
                        torch(true)
                        delay(unit * len)
                        torch(false)
                        delay(if (i == 2 || i == 5) unit * 3 else unit)
                    }
                    delay(unit * 7)
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { torch(false) }
    }
    // Screen light: full brightness while shown.
    val screenOn = on && mode == 3
    DisposableEffect(screenOn) {
        val window = view.context.findActivity()?.window
        val before = window?.attributes?.screenBrightness
        if (screenOn && window != null) {
            window.attributes = window.attributes.apply { screenBrightness = 1f }
        }
        onDispose {
            if (screenOn && window != null && before != null) {
                window.attributes = window.attributes.apply { screenBrightness = before }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ToolScaffold("Flashlight", "টর্চ ও SOS", onBack) {
            ListCard {
                Box(Modifier.fillMaxWidth().height(520.r), contentAlignment = Alignment.Center) {
                    TorchArt(flash || screenOn, Modifier.fillMaxWidth().height(520.r))
                    Box(
                        Modifier
                            .padding(top = 150.r)
                            .size(210.r)
                            .clip(CircleShape)
                            .background(
                                if (on) Brush.radialGradient(listOf(Color(0xFFFFF3C4), Color(0xFFFBBF24)))
                                else Brush.radialGradient(listOf(Color(0xFFF1F4F7), Color(0xFFD5DEE7)))
                            )
                            .border(6.r, if (on) Color(0xFFF59E0B) else Color(0xFFC3CDD8), CircleShape)
                            .clickable { on = !on },
                        contentAlignment = Alignment.Center,
                    ) {
                        RText(if (on) "বন্ধ" else "চালু", 34, FontWeight.Bold, if (on) Color(0xFF92400E) else C.StatLabel)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                if (camId != null) {
                    ChoiceChip("টর্চ", mode == 0, TAmber) { mode = 0 }
                    ChoiceChip("স্ট্রোব", mode == 1, TAmber) { mode = 1 }
                    ChoiceChip("SOS", mode == 2, TAmber) { mode = 2 }
                }
                ChoiceChip("স্ক্রিন লাইট", mode == 3, TAmber) { mode = 3 }
            }
            if (camId == null) {
                RText("এই ফোনে ফ্ল্যাশ নেই, তাই স্ক্রিন লাইট ব্যবহার হবে।", 17, FontWeight.Normal, C.Hint)
            }
            if (mode == 0 && maxLevel > 1) {
                ListCard {
                    RText("উজ্জ্বলতা: $level / $maxLevel", 19, FontWeight.SemiBold, C.Ink)
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { level = it.toInt().coerceIn(1, maxLevel) },
                        valueRange = 1f..maxLevel.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = TAmber, activeTrackColor = TAmber),
                    )
                }
            }
            if (mode == 1) {
                ListCard {
                    RText("গতি: ${hz.toInt()} বার / সেকেন্ড", 19, FontWeight.SemiBold, C.Ink)
                    Slider(
                        value = hz,
                        onValueChange = { hz = it },
                        valueRange = 1f..15f,
                        colors = SliderDefaults.colors(thumbColor = TAmber, activeTrackColor = TAmber),
                    )
                }
            }
            if (mode == 2) {
                RText("SOS: ··· ─── ··· বিপদে আলো দিয়ে সাহায্য চাওয়ার আন্তর্জাতিক সংকেত।", 17, FontWeight.Normal, C.Hint, maxLines = 2)
            }
            if (mode == 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                    SCREEN_COLORS.forEachIndexed { i, (_, col) ->
                        Box(
                            Modifier
                                .size(54.r)
                                .clip(CircleShape)
                                .background(col)
                                .border(if (i == screenColor) 3.r else 1.r, if (i == screenColor) TAmber else C.FieldLine, CircleShape)
                                .clickable { screenColor = i },
                        )
                    }
                }
                RText("স্ক্রিন পুরো উজ্জ্বল হয়ে আলো দেবে। স্ক্রিনে চাপ দিলে বন্ধ হবে।", 17, FontWeight.Normal, C.Hint, maxLines = 2)
            }
        }
        if (screenOn) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(SCREEN_COLORS[screenColor].second)
                    .clickable { on = false },
                contentAlignment = Alignment.BottomCenter,
            ) {
                RText("বন্ধ করতে চাপ দিন", 18, FontWeight.Medium, Color(0x66000000), Modifier.padding(bottom = 80.r))
            }
        }
    }
}

/** Torch head with a light beam when lit. */
@Composable
private fun TorchArt(lit: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val cx = size.width / 2f
        if (lit) {
            val beam = Path().apply {
                moveTo(cx - size.width * 0.12f, size.height * 0.3f)
                lineTo(cx - size.width * 0.48f, 0f)
                lineTo(cx + size.width * 0.48f, 0f)
                lineTo(cx + size.width * 0.12f, size.height * 0.3f)
                close()
            }
            drawPath(beam, Brush.verticalGradient(listOf(Color(0x00FDE68A), Color(0xAAFDE68A)), 0f, size.height * 0.3f))
            drawCircle(
                Brush.radialGradient(listOf(Color(0x99FDE68A), Color(0x00FDE68A)), Offset(cx, size.height * 0.62f), size.width * 0.45f),
                size.width * 0.45f,
                Offset(cx, size.height * 0.62f),
            )
        }
    }
}
