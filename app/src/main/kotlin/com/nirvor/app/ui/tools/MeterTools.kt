package com.nirvor.app.ui.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nirvor.app.tools.KeepScreenOn
import com.nirvor.app.tools.rememberSensor
import com.nirvor.app.tools.toolPrefs
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

// ================================================================ shared drawing

/** Half-circle gauge from [min] to [max] with coloured bands and a needle. */
@Composable
private fun HalfGauge(
    fraction: Float,
    bands: List<Pair<Float, Color>>,
    labels: List<Pair<Float, String>>,
    modifier: Modifier,
) {
    val tm = rememberTextMeasurer()
    val style = TextStyle(fontFamily = Inter, fontSize = 15.rs, color = Color(0xFF63748A))
    Canvas(modifier) {
        val w = size.width
        val r = w * 0.42f
        val c = Offset(w / 2f, size.height * 0.92f)
        val sw = r * 0.16f
        var start = 0f
        bands.forEach { (end, color) ->
            drawArc(
                color,
                180f + start * 180f,
                (end - start) * 180f,
                false,
                Offset(c.x - r, c.y - r),
                Size(r * 2, r * 2),
                style = Stroke(sw, cap = StrokeCap.Butt),
            )
            start = end
        }
        labels.forEach { (f, text) ->
            val a = Math.toRadians(180.0 + f * 180.0)
            val rr = r - sw * 1.6f
            val layout = tm.measure(text, style)
            drawText(
                layout,
                topLeft = Offset(
                    c.x + rr * cos(a).toFloat() - layout.size.width / 2f,
                    c.y + rr * sin(a).toFloat() - layout.size.height / 2f,
                ),
            )
        }
        val a = Math.toRadians(180.0 + fraction.coerceIn(0f, 1f) * 180.0)
        val tip = Offset(c.x + (r + sw * 0.3f) * cos(a).toFloat(), c.y + (r + sw * 0.3f) * sin(a).toFloat())
        drawLine(Color(0xFF0B0E22), c, tip, 7f, cap = StrokeCap.Round)
        drawCircle(Color(0xFF0B0E22), 14f, c)
        drawCircle(Color.White, 5f, c)
    }
}

/** Last readings as a filled line chart. */
@Composable
private fun HistoryChart(points: List<Float>, max: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        for (i in 1..3) {
            val y = h * i / 4f
            drawLine(Color(0xFFEFF2F6), Offset(0f, y), Offset(w, y), 2f)
        }
        if (points.size < 2) return@Canvas
        val step = w / (points.size - 1).coerceAtLeast(1)
        val line = Path()
        points.forEachIndexed { i, v ->
            val x = i * step
            val y = h - (v / max).coerceIn(0f, 1f) * h
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo((points.size - 1) * step, h)
            lineTo(0f, h)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.02f))))
        drawPath(line, color, style = Stroke(4f))
    }
}

// ================================================================ Sound meter

private fun soundWords(db: Float): Pair<String, Color> = when {
    db < 30 -> "খুব শান্ত (ফিসফিস)" to Color(0xFF0BA765)
    db < 50 -> "শান্ত (লাইব্রেরির মতো)" to Color(0xFF0BA765)
    db < 70 -> "স্বাভাবিক কথাবার্তা" to Color(0xFF168FFB)
    db < 85 -> "জোরে (রাস্তার ট্রাফিক)" to Color(0xFFE89B0C)
    db < 100 -> "খুব জোরে — বেশিক্ষণ শুনলে কানের ক্ষতি" to Color(0xFFF76B15)
    else -> "বিপজ্জনক জোরে" to Color(0xFFE5484D)
}

/** Sound level in dB from the microphone, with a gauge and the last 12 seconds as a chart. */
@SuppressLint("MissingPermission")
@Composable
fun SoundMeterTool(onBack: () -> Unit) {
    KeepScreenOn()
    val context = LocalContext.current
    val prefs = remember { toolPrefs(context) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO) }
    var offset by remember { mutableFloatStateOf(prefs.getFloat("sound_offset", 0f)) }
    var db by remember { mutableFloatStateOf(0f) }
    var minDb by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var maxDb by remember { mutableFloatStateOf(0f) }
    var sum by remember { mutableFloatStateOf(0f) }
    var count by remember { mutableFloatStateOf(0f) }
    val history = remember { mutableStateListOf<Float>() }
    var paused by remember { mutableStateOf(false) }
    var resumed by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }

    LaunchedEffect(granted, resumed, paused) {
        if (!granted || !resumed || paused) return@LaunchedEffect
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return@LaunchedEffect
        val rate = 44100
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            failed = true
            return@LaunchedEffect
        }
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, rate / 5 * 2),
            )
        } catch (e: Exception) {
            null
        }
        if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
            rec?.release()
            failed = true
            return@LaunchedEffect
        }
        failed = false
        try {
            withContext(Dispatchers.IO) {
                rec.startRecording()
                val buf = ShortArray(rate / 10)
                while (isActive) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var acc = 0.0
                    for (i in 0 until n) {
                        val s = buf[i] / 32768.0
                        acc += s * s
                    }
                    val rms = sqrt(acc / n)
                    val dbfs = if (rms > 1e-9) 20.0 * log10(rms) else -100.0
                    val spl = (dbfs + 90.0 + offset).coerceIn(0.0, 130.0).toFloat()
                    withContext(Dispatchers.Main) {
                        db = if (count == 0f) spl else db + 0.5f * (spl - db)
                        if (db < minDb) minDb = db
                        if (db > maxDb) maxDb = db
                        sum += db
                        count += 1f
                        history.add(db)
                        if (history.size > 120) history.removeAt(0)
                    }
                }
            }
        } finally {
            try {
                rec.stop()
            } catch (e: Exception) {
                // already stopped
            }
            rec.release()
        }
    }

    ToolScaffold("Sound Meter", "শব্দ মাপা (dB)", onBack) {
        if (!granted) {
            ListCard {
                RText("🎤 মাইক্রোফোনের অনুমতি লাগবে", 21, FontWeight.SemiBold, C.Ink)
                Spacer(Modifier.height(6.r))
                RText("শব্দ কত জোরে তা মাপতে মাইক্রোফোন ব্যবহার হয়। কিছু রেকর্ড বা সেভ হয় না।", 17, FontWeight.Normal, C.StatLabel, maxLines = 3)
                Spacer(Modifier.height(14.r))
                RoundButton("অনুমতি দিন", TGreen, Modifier.fillMaxWidth()) { launcher.launch(Manifest.permission.RECORD_AUDIO) }
            }
            return@ToolScaffold
        }
        if (failed) {
            EmptyText("মাইক্রোফোন চালু করা গেল না। অন্য কোনো অ্যাপ হয়তো মাইক ব্যবহার করছে।")
        }
        val (words, color) = soundWords(db)
        ListCard {
            Box(Modifier.fillMaxWidth().aspectRatio(1.7f), contentAlignment = Alignment.BottomCenter) {
                HalfGauge(
                    db / 120f,
                    listOf(
                        0.42f to Color(0xFF34D399),
                        0.58f to Color(0xFF60A5FA),
                        0.71f to Color(0xFFFBBF24),
                        0.83f to Color(0xFFFB923C),
                        1f to Color(0xFFF87171),
                    ),
                    listOf(0f to "0", 0.25f to "30", 0.5f to "60", 0.75f to "90", 1f to "120"),
                    Modifier.fillMaxWidth().aspectRatio(1.7f),
                )
            }
            Spacer(Modifier.height(10.r))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                RText(String.format(Locale.US, "%.0f", db), 60, FontWeight.Bold, C.Ink)
                Spacer(Modifier.width(8.r))
                RText("dB", 22, FontWeight.Medium, C.Hint, Modifier.padding(bottom = 12.r))
            }
            RText(words, 21, FontWeight.SemiBold, color, Modifier.fillMaxWidth(), align = TextAlign.Center, maxLines = 2)
        }
        ListCard {
            HistoryChart(history.toList(), 120f, color, Modifier.fillMaxWidth().height(170.r))
            Spacer(Modifier.height(12.r))
            TriStat(
                listOf(
                    "কম" to if (count > 0) String.format(Locale.US, "%.0f", minDb) else "—",
                    "গড়" to if (count > 0) String.format(Locale.US, "%.0f", sum / count) else "—",
                    "বেশি" to if (count > 0) String.format(Locale.US, "%.0f", maxDb) else "—",
                )
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            RoundButton(if (paused) "▶ চালু" else "⏸ থামান", TGreen, Modifier.weight(1f), filled = paused) { paused = !paused }
            RoundButton("↺ রিসেট", TBlue, Modifier.weight(1f), filled = false) {
                minDb = Float.MAX_VALUE
                maxDb = 0f
                sum = 0f
                count = 0f
                history.clear()
            }
        }
        ListCard {
            RText("ক্যালিব্রেশন: ${if (offset >= 0) "+" else ""}${offset.toInt()} dB", 19, FontWeight.SemiBold, C.Ink)
            Slider(
                value = offset,
                onValueChange = { offset = it },
                onValueChangeFinished = { prefs.edit().putFloat("sound_offset", offset).apply() },
                valueRange = -20f..20f,
                colors = SliderDefaults.colors(thumbColor = TGreen, activeTrackColor = TGreen),
            )
            RText(
                "ফোনের মাইক দিয়ে মাপা, তাই মান আনুমানিক (±৫ dB)। আসল সাউন্ড মিটারের সাথে মিলিয়ে নিতে চাইলে এখানে ঠিক করুন।",
                16,
                FontWeight.Normal,
                C.Hint,
                maxLines = 3,
                lineHeight = 23,
            )
        }
        ListCard {
            RText("উদাহরণ", 19, FontWeight.SemiBold, C.Ink)
            Spacer(Modifier.height(4.r))
            listOf(
                "🍃 পাতার শব্দ" to "20 dB", "📚 লাইব্রেরি" to "40 dB", "🗣️ কথাবার্তা" to "60 dB",
                "🚗 রাস্তার ট্রাফিক" to "80 dB", "🏍️ মোটরসাইকেল" to "95 dB", "🎆 আতশবাজি" to "120+ dB",
            ).forEach { (k, v) -> StatLine(k, v) }
        }
    }
}

// ================================================================ Light meter

private fun lightWords(lux: Float): String = when {
    lux < 10 -> "অন্ধকার"
    lux < 50 -> "খুব কম আলো"
    lux < 200 -> "ঘরের সাধারণ আলো"
    lux < 500 -> "পড়া-লেখার জন্য ভালো"
    lux < 1000 -> "অফিসের মতো উজ্জ্বল"
    lux < 10_000 -> "দিনের বাইরের আলো (ছায়া / মেঘলা)"
    else -> "সরাসরি রোদ"
}

/** Light level from the phone's light sensor (lux), log gauge and the last readings. */
@Composable
fun LightMeterTool(onBack: () -> Unit) {
    KeepScreenOn()
    val s = rememberSensor(Sensor.TYPE_LIGHT, 0.35f, SensorManager.SENSOR_DELAY_UI)
    val lux = s.values?.getOrNull(0) ?: 0f
    val history = remember { mutableStateListOf<Float>() }
    var minL by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var maxL by remember { mutableFloatStateOf(0f) }
    var sum by remember { mutableFloatStateOf(0f) }
    var count by remember { mutableFloatStateOf(0f) }
    val latest by androidx.compose.runtime.rememberUpdatedState(lux)
    val hasValue = s.values != null
    LaunchedEffect(hasValue) {
        while (hasValue) {
            val v = latest
            history.add(v)
            if (history.size > 100) history.removeAt(0)
            if (v < minL) minL = v
            if (v > maxL) maxL = v
            sum += v
            count += 1f
            delay(250)
        }
    }
    val logMax = log10(100_001f)
    val f = log10(lux + 1f) / logMax

    ToolScaffold("Light Meter", "আলো মাপা (lux)", onBack) {
        if (!s.available) {
            EmptyText("এই ফোনে আলো মাপার সেন্সর নেই।")
            return@ToolScaffold
        }
        ListCard {
            Box(Modifier.fillMaxWidth().aspectRatio(1.7f), contentAlignment = Alignment.BottomCenter) {
                SunGlow(f, Modifier.fillMaxWidth().aspectRatio(1.7f))
                HalfGauge(
                    f,
                    listOf(
                        0.2f to Color(0xFF475569),
                        0.4f to Color(0xFF94A3B8),
                        0.6f to Color(0xFFFDE68A),
                        0.8f to Color(0xFFFBBF24),
                        1f to Color(0xFFF59E0B),
                    ),
                    listOf(0f to "0", 0.2f to "10", 0.4f to "100", 0.6f to "1k", 0.8f to "10k", 1f to "100k"),
                    Modifier.fillMaxWidth().aspectRatio(1.7f),
                )
            }
            Spacer(Modifier.height(10.r))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                RText(fmt(lux.toDouble(), 0), 56, FontWeight.Bold, C.Ink)
                Spacer(Modifier.width(8.r))
                RText("lux", 22, FontWeight.Medium, C.Hint, Modifier.padding(bottom = 12.r))
            }
            RText(lightWords(lux), 21, FontWeight.SemiBold, Color(0xFFB45309), Modifier.fillMaxWidth(), align = TextAlign.Center)
        }
        ListCard {
            HistoryChart(history.map { log10(it + 1f) }, logMax, Color(0xFFF59E0B), Modifier.fillMaxWidth().height(160.r))
            Spacer(Modifier.height(12.r))
            TriStat(
                listOf(
                    "কম" to if (count > 0) fmt(minL.toDouble(), 0) else "—",
                    "গড়" to if (count > 0) fmt((sum / count).toDouble(), 0) else "—",
                    "বেশি" to if (count > 0) fmt(maxL.toDouble(), 0) else "—",
                )
            )
        }
        RoundButton("↺ রিসেট", TBlue, Modifier.fillMaxWidth(), filled = false) {
            history.clear()
            minL = Float.MAX_VALUE
            maxL = 0f
            sum = 0f
            count = 0f
        }
        ListCard {
            RText("কোথায় কত আলো দরকার", 19, FontWeight.SemiBold, C.Ink)
            Spacer(Modifier.height(4.r))
            listOf(
                "🛏️ শোবার ঘর" to "100–300 lux",
                "📖 পড়া-লেখা" to "300–500 lux",
                "🍳 রান্নাঘর" to "300–750 lux",
                "🧵 সূক্ষ্ম কাজ / সেলাই" to "750–1000 lux",
                "🪴 ঘরের গাছ" to "1000+ lux",
            ).forEach { (k, v) -> StatLine(k, v) }
            Spacer(Modifier.height(6.r))
            RText("সেন্সরটা সাধারণত ফোনের সামনের উপরের দিকে, ক্যামেরার পাশে থাকে।", 16, FontWeight.Normal, C.Hint, maxLines = 2)
        }
    }
}

@Composable
private fun SunGlow(fraction: Float, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.92f)
        val r = size.width * 0.5f * (0.4f + 0.6f * fraction.coerceIn(0f, 1f))
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFDE68A).copy(alpha = 0.15f + 0.6f * fraction.coerceIn(0f, 1f)), Color(0x00FDE68A)),
                c,
                r,
            ),
            r,
            c,
        )
    }
}

// ================================================================ Screen ruler

/**
 * A real-size ruler on the screen (cm on the left, inch on the right). Two lines can be
 * dragged to measure something laid on the screen. Calibrate with any object of known size.
 */
@Composable
fun RulerTool(onBack: () -> Unit) {
    KeepScreenOn()
    val context = LocalContext.current
    val prefs = remember { toolPrefs(context) }
    val dm = context.resources.displayMetrics
    var calib by remember { mutableFloatStateOf(prefs.getFloat("ruler_cal", 1f)) }
    val pxPerMm = dm.ydpi / 25.4f * calib
    var a by remember { mutableFloatStateOf(0f) }
    var b by remember { mutableFloatStateOf(dm.ydpi / 25.4f * 50f) }
    var calibrating by remember { mutableStateOf(false) }
    val tm = rememberTextMeasurer()
    val cmStyle = TextStyle(fontFamily = Inter, fontSize = 16.rs, color = Color(0xFF0B0E22), fontWeight = FontWeight.SemiBold)
    val inStyle = TextStyle(fontFamily = Inter, fontSize = 15.rs, color = Color(0xFF1764B5), fontWeight = FontWeight.SemiBold)
    var active by remember { mutableFloatStateOf(0f) } // 0 = line A, 1 = line B

    val mm = abs(b - a) / pxPerMm

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFFFBEB))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { pos -> active = if (abs(pos.y - a) <= abs(pos.y - b)) 0f else 1f },
                ) { change, drag ->
                    change.consume()
                    val h = size.height.toFloat()
                    if (active == 0f) a = (a + drag.y).coerceIn(0f, h) else b = (b + drag.y).coerceIn(0f, h)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // cm / mm scale on the left
            var m = 0
            while (m * pxPerMm <= h) {
                val y = m * pxPerMm
                val len = when {
                    m % 10 == 0 -> w * 0.16f
                    m % 5 == 0 -> w * 0.1f
                    else -> w * 0.06f
                }
                drawLine(Color(0xFF0B0E22), Offset(0f, y), Offset(len, y), if (m % 10 == 0) 3f else 2f)
                if (m % 10 == 0 && m > 0) {
                    val layout = tm.measure((m / 10).toString(), cmStyle)
                    drawText(layout, topLeft = Offset(len + 8f, y - layout.size.height / 2f))
                }
                m++
            }
            // inch scale on the right (1/16")
            val pxPerInch = pxPerMm * 25.4f
            var k = 0
            while (k * pxPerInch / 16f <= h) {
                val y = k * pxPerInch / 16f
                val len = when {
                    k % 16 == 0 -> w * 0.16f
                    k % 8 == 0 -> w * 0.11f
                    k % 4 == 0 -> w * 0.08f
                    else -> w * 0.045f
                }
                drawLine(Color(0xFF1764B5), Offset(w - len, y), Offset(w, y), if (k % 16 == 0) 3f else 2f)
                if (k % 16 == 0 && k > 0) {
                    val layout = tm.measure((k / 16).toString(), inStyle)
                    drawText(layout, topLeft = Offset(w - len - 8f - layout.size.width, y - layout.size.height / 2f))
                }
                k++
            }
            // measuring lines
            val top = minOf(a, b)
            val bottom = maxOf(a, b)
            drawRect(Color(0x220BA765), Offset(0f, top), Size(w, bottom - top))
            listOf(a, b).forEach { y ->
                drawLine(Color(0xFF0BA765), Offset(0f, y), Offset(w, y), 4f)
                drawCircle(Color(0xFF0BA765), 22f, Offset(w / 2f, y))
                drawCircle(Color.White, 8f, Offset(w / 2f, y))
            }
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 150.r)
                .clip(RoundedCornerShape(22.r))
                .background(Color.White.copy(alpha = 0.94f))
                .padding(22.r),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RText(String.format(Locale.US, "%.1f সেমি", mm / 10f), 40, FontWeight.Bold, C.Ink, align = TextAlign.Center)
            RText(
                String.format(Locale.US, "%.0f মিমি · %.2f ইঞ্চি", mm, mm / 25.4f),
                18,
                FontWeight.Medium,
                C.StatLabel,
                align = TextAlign.Center,
            )
            Spacer(Modifier.height(12.r))
            RText("সবুজ লাইন দুটো টেনে মাপুন", 15, FontWeight.Normal, C.Hint, align = TextAlign.Center)
            Spacer(Modifier.height(14.r))
            Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                RoundButton("← ফিরুন", C.StatLabel, Modifier.weight(1f), filled = false, onClick = onBack)
                RoundButton("ক্যালিব্রেট", TBlue, Modifier.weight(1f), filled = false) { calibrating = true }
            }
        }
    }

    if (calibrating) {
        var known by remember { mutableStateOf("85.6") }
        AlertDialog(
            onDismissRequest = { calibrating = false },
            containerColor = Color.White,
            title = { RText("ক্যালিব্রেট", 25, FontWeight.Bold, C.Ink) },
            text = {
                Column {
                    RText(
                        "জানা মাপের কোনো জিনিস (যেমন ATM / ব্যাংক কার্ডের লম্বা দিক = 85.6 মিমি) স্ক্রিনে রেখে লাইন দুটো তার দুই মাথায় বসান। তারপর আসল মাপ লিখুন।",
                        18,
                        FontWeight.Normal,
                        C.StatLabelBn,
                        maxLines = 6,
                        lineHeight = 26,
                    )
                    Spacer(Modifier.height(14.r))
                    ToolNumberField("আসল মাপ", known, { known = it }, suffix = "মিমি")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val k = parseNum(known)
                    if (k != null && k > 1 && mm > 1) {
                        calib = (calib * (mm / k.toFloat())).coerceIn(0.5f, 2f)
                        prefs.edit().putFloat("ruler_cal", calib).apply()
                    }
                    calibrating = false
                }) { RText("সেভ করুন", 20, FontWeight.SemiBold, TGreen) }
            },
            dismissButton = {
                TextButton(onClick = {
                    calib = 1f
                    prefs.edit().remove("ruler_cal").apply()
                    calibrating = false
                }) { RText("রিসেট", 20, FontWeight.Medium, C.Hint) }
            },
        )
    }
}
