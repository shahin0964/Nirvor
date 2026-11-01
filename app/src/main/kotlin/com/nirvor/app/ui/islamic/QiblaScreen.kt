package com.nirvor.app.ui.islamic

import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.islamic.IslamicPrefs
import com.nirvor.app.islamic.PrayerTimes
import com.nirvor.app.islamic.bnDigits
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.tools.KeepScreenOn
import com.nirvor.app.tools.hasSensor
import com.nirvor.app.tools.rememberSensor
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.ToolScaffold
import com.nirvor.app.ui.tools.ar.backCameraVerticalFov
import com.nirvor.app.ui.tools.camera.CameraFrame
import com.nirvor.app.ui.tools.camera.CameraGate
import com.nirvor.app.ui.tools.camera.CameraPanel
import com.nirvor.app.ui.tools.camera.CameraView
import com.nirvor.app.ui.tools.camera.PanelButton
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/** -180..180: how far [to] is from [from], turning the short way. */
private fun turnTo(from: Float, to: Float): Float = ((to - from) % 360f + 540f) % 360f - 180f

/** Phone heading from the sensors (degrees from true north), plus how sure the compass is. */
private class Heading(val top: Float, val camera: Float, val flat: Boolean, val ok: Boolean, val accuracy: Int)

@Composable
private fun rememberHeading(declination: Float): Heading {
    val context = LocalContext.current
    val vector = remember {
        when {
            hasSensor(context, Sensor.TYPE_ROTATION_VECTOR) -> Sensor.TYPE_ROTATION_VECTOR
            hasSensor(context, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) -> Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
            else -> -1
        }
    }
    val rv = rememberSensor(if (vector > 0) vector else Sensor.TYPE_ACCELEROMETER, if (vector > 0) 0.25f else 0.15f)
    val mag = rememberSensor(Sensor.TYPE_MAGNETIC_FIELD, 0.15f)
    val r = remember { FloatArray(9) }
    val ok = if (vector > 0) {
        rv.values?.let { v ->
            SensorManager.getRotationMatrixFromVector(r, if (v.size > 4) v.copyOf(4) else v)
            true
        } ?: false
    } else {
        val a = rv.values
        val m = mag.values
        a != null && m != null && SensorManager.getRotationMatrix(r, null, a, m)
    }
    if (!ok) return Heading(0f, 0f, true, false, mag.accuracy)
    // world: x east, y north, z up. Top of the phone = device +Y, back camera = device −Z.
    val top = Math.toDegrees(atan2(r[1].toDouble(), r[4].toDouble())).toFloat()
    val cam = Math.toDegrees(atan2(-r[2].toDouble(), -r[5].toDouble())).toFloat()
    fun norm(d: Float) = ((d + declination) % 360f + 360f) % 360f
    return Heading(norm(top), norm(cam), abs(r[8]) > 0.6f, true, mag.accuracy)
}

/** Qibla finder: a compass dial with the Kaaba marked, or the camera with the Kaaba on the horizon. */
@Composable
fun QiblaScreen(onBack: () -> Unit, onLocation: () -> Unit) {
    KeepScreenOn()
    val context = LocalContext.current
    val prefs = remember { IslamicPrefs(context) }
    val place = remember { prefs.place() }
    var camera by rememberSaveable { mutableStateOf(false) }
    if (place == null) {
        ToolScaffold("কিবলা", "কাবার দিক", onBack) {
            ListCard {
                RText("📍 লোকেশন পাওয়া যায়নি", 21, FontWeight.SemiBold, C.Ink)
                RText("কিবলার দিক বের করতে আপনার জায়গা জানা দরকার।", 17, FontWeight.Normal, C.StatLabel, maxLines = 2)
                Spacer(Modifier.height(12.r))
                RoundButton("লোকেশন ঠিক করুন", IslamicA, Modifier.fillMaxWidth(), onClick = onLocation)
            }
        }
        return
    }
    val qibla = remember(place) { PrayerTimes.qibla(place.lat, place.lon).toFloat() }
    val km = remember(place) { PrayerTimes.distanceToKaaba(place.lat, place.lon) }
    val declination = remember(place) {
        GeomagneticField(place.lat.toFloat(), place.lon.toFloat(), 0f, System.currentTimeMillis()).declination
    }
    if (!hasSensor(context, Sensor.TYPE_MAGNETIC_FIELD)) {
        ToolScaffold("কিবলা", "কাবার দিক", onBack) {
            ListCard {
                RText("🕋 কিবলা: ${bnNum(qibla.roundToInt())}° (উত্তর থেকে ঘড়ির কাঁটার দিকে)", 20, FontWeight.SemiBold, C.Ink, maxLines = 2)
                RText("এই ফোনে কম্পাস সেন্সর নেই, তাই দিকটা দেখানো যাচ্ছে না।", 17, FontWeight.Normal, C.StatLabel, maxLines = 2)
            }
        }
        return
    }
    if (camera) {
        CameraGate("কিবলা", "ক্যামেরায় কাবার দিক", { camera = false }) {
            QiblaCamera(qibla, declination, onBack = { camera = false })
        }
    } else {
        QiblaCompass(qibla, km, place.name, declination, onBack) { camera = true }
    }
}

@Composable
private fun QiblaCompass(qibla: Float, km: Double, placeName: String, declination: Float, onBack: () -> Unit, onCamera: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val h = rememberHeading(declination)
    // the sensor readings are already smoothed, so the dial follows them directly
    val shown = h.top
    val off = turnTo(shown, qibla)
    val facing = h.ok && abs(off) < 3f
    LaunchedEffect(facing) { if (facing) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    val tm = rememberTextMeasurer()
    val dirStyle = TextStyle(fontFamily = Inter, fontSize = 20.rs, fontWeight = FontWeight.Bold)
    val kaabaStyle = TextStyle(fontSize = 34.rs)

    ToolScaffold("কিবলা", "📍 $placeName", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
            ChoiceChip("🧭 কম্পাস", true, IslamicA) {}
            ChoiceChip("📷 ক্যামেরা", false, IslamicA, onClick = onCamera)
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                val rad = size.minDimension / 2f * 0.92f
                // glow when facing the Kaaba
                if (facing) drawCircle(Color(0x332BD49A), rad * 1.06f, c)
                drawCircle(Color.White, rad, c)
                drawCircle(if (facing) IslamicA else Color(0xFFDCE4EA), rad, c, style = Stroke(rad * 0.025f))
                rotate(-shown, c) {
                    for (d in 0 until 360 step 5) {
                        val long = d % 30 == 0
                        val a = Math.toRadians(d.toDouble())
                        val r1 = rad * if (long) 0.84f else 0.89f
                        val r2 = rad * 0.94f
                        drawLine(
                            if (long) Color(0xFF41597A) else Color(0xFFB8C4D0),
                            Offset(c.x + r1 * sin(a).toFloat(), c.y - r1 * cos(a).toFloat()),
                            Offset(c.x + r2 * sin(a).toFloat(), c.y - r2 * cos(a).toFloat()),
                            if (long) rad * 0.014f else rad * 0.008f,
                            cap = StrokeCap.Round,
                        )
                    }
                    listOf("N" to 0, "E" to 90, "S" to 180, "W" to 270).forEach { (l, d) ->
                        val a = Math.toRadians(d.toDouble())
                        val rr = rad * 0.74f
                        val layout = tm.measure(l, dirStyle.copy(color = if (l == "N") Color(0xFFE5484D) else Color(0xFF41597A)))
                        val p = Offset(c.x + rr * sin(a).toFloat(), c.y - rr * cos(a).toFloat())
                        rotate(shown, p) { drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f)) }
                    }
                    // the Qibla line and the Kaaba on the rim
                    val qa = Math.toRadians(qibla.toDouble())
                    val tip = Offset(c.x + rad * 0.62f * sin(qa).toFloat(), c.y - rad * 0.62f * cos(qa).toFloat())
                    drawLine(Gold, c, tip, rad * 0.035f, cap = StrokeCap.Round)
                    val kp = Offset(c.x + rad * 0.62f * sin(qa).toFloat(), c.y - rad * 0.62f * cos(qa).toFloat())
                    val kaaba = tm.measure("🕋", kaabaStyle)
                    rotate(shown, kp) { drawText(kaaba, topLeft = Offset(kp.x - kaaba.size.width / 2f, kp.y - kaaba.size.height / 2f)) }
                }
                // fixed pointer: where the top of the phone points
                val p = Path().apply {
                    moveTo(c.x, c.y - rad * 1.0f)
                    lineTo(c.x - rad * 0.06f, c.y - rad * 0.86f)
                    lineTo(c.x + rad * 0.06f, c.y - rad * 0.86f)
                    close()
                }
                drawPath(p, if (facing) IslamicA else Color(0xFF0B0E22))
                drawCircle(if (facing) IslamicA else Color(0xFF0B0E22), rad * 0.035f, c)
            }
        }
        ListCard {
            RText("🕋 ${bnNum(qibla.roundToInt())}°", 40, FontWeight.Bold, IslamicB, Modifier.fillMaxWidth(), align = TextAlign.Center)
            Spacer(Modifier.height(4.r))
            RText(
                if (!h.ok) "সেন্সর চালু হচ্ছে…" else if (facing) "✅ আপনি কিবলার দিকে আছেন" else if (off > 0) "ডান দিকে ${bnNum(abs(off).roundToInt())}° ঘোরান →" else "← বাম দিকে ${bnNum(abs(off).roundToInt())}° ঘোরান",
                24,
                FontWeight.Bold,
                if (facing) IslamicA else C.Ink,
                Modifier.fillMaxWidth(),
                align = TextAlign.Center,
            )
            Spacer(Modifier.height(6.r))
            RText(
                "উত্তর থেকে ঘড়ির কাঁটার দিকে · মক্কা ${bnDigits(String.format(Locale.US, "%,d", km.roundToInt()))} কিমি দূরে",
                16,
                FontWeight.Medium,
                C.StatLabel,
                Modifier.fillMaxWidth(),
                align = TextAlign.Center,
            )
            if (!h.flat) {
                Spacer(Modifier.height(4.r))
                RText("ফোনটা টেবিলের মতো সমান করে ধরুন", 15, FontWeight.Medium, C.Hint, Modifier.fillMaxWidth(), align = TextAlign.Center)
            }
        }
        if (h.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.r))
                    .background(Color(0xFFFFF4E0))
                    .padding(18.r),
            ) {
                RText("⚠️ কম্পাস ঠিক করুন", 19, FontWeight.SemiBold, Color(0xFFB45309))
                RText("ফোনটা বাতাসে ৮ আকারে কয়েকবার ঘোরান। চুম্বক, স্পিকার আর লোহার জিনিস থেকে দূরে রাখুন।", 16, FontWeight.Normal, Color(0xFF92400E), maxLines = 3, lineHeight = 23)
            }
        }
    }
}

/** Camera: a heading tape on top and the Kaaba marked where it is, with an arrow when it is off screen. */
@Composable
private fun QiblaCamera(qibla: Float, declination: Float, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val h = rememberHeading(declination)
    val shown = h.camera
    val off = turnTo(shown, qibla)
    val facing = h.ok && abs(off) < 4f
    LaunchedEffect(facing) { if (facing) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    val vFov = remember { backCameraVerticalFov(context) }
    val tm = rememberTextMeasurer()
    val tapeStyle = TextStyle(fontFamily = Inter, fontSize = 15.rs, fontWeight = FontWeight.Bold, color = Color.White)
    val tapeKaaba = TextStyle(fontSize = 22.rs)
    val bigKaaba = TextStyle(fontSize = 64.rs)
    val unit = 1.r

    CameraFrame("কিবলা", onBack) {
        CameraView(Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val u = unit.toPx()
            val fpx = (size.height / 2f) / tan(Math.toRadians(vFov / 2.0)).toFloat()
            val cx = size.width / 2f
            val tapeY = size.height * 0.2f
            // heading tape
            drawRect(Color(0x66000000), Offset(0f, tapeY - 46f * u), androidx.compose.ui.geometry.Size(size.width, 92f * u))
            for (d in -60..60) {
                val deg = Math.round(shown) + d
                if (deg % 5 != 0) continue
                val x = cx + d * size.width / 90f
                val long = deg % 30 == 0
                drawLine(Color.White, Offset(x, tapeY + 40f * u), Offset(x, tapeY + (if (long) 6f else 22f) * u), (if (long) 4f else 2f) * u)
                if (long) {
                    val n = ((deg % 360) + 360) % 360
                    val label = when (n) {
                        0 -> "N"
                        90 -> "E"
                        180 -> "S"
                        270 -> "W"
                        else -> "$n"
                    }
                    val l = tm.measure(label, tapeStyle)
                    drawText(l, topLeft = Offset(x - l.size.width / 2f, tapeY - 40f * u))
                }
            }
            val qx = cx + off * size.width / 90f
            if (abs(off) < 45f) {
                val k = tm.measure("🕋", tapeKaaba)
                drawText(k, topLeft = Offset(qx - k.size.width / 2f, tapeY - 32f * u))
            }
            drawLine(Gold, Offset(cx, tapeY - 46f * u), Offset(cx, tapeY + 46f * u), 4f * u)

            // the Kaaba on the picture (at eye level, where the Qibla direction is)
            val x = cx + fpx * tan(Math.toRadians(off.toDouble().coerceIn(-80.0, 80.0))).toFloat()
            val y = size.height * 0.48f
            if (x in 0f..size.width && abs(off) < 80f) {
                if (facing) drawCircle(Color(0x552BD49A), 150f * u, Offset(x, y))
                drawCircle(if (facing) IslamicA else Gold, 110f * u, Offset(x, y), style = Stroke(8f * u))
                val k = tm.measure("🕋", bigKaaba)
                drawText(k, topLeft = Offset(x - k.size.width / 2f, y - k.size.height / 2f))
                drawLine(Gold, Offset(x, y + 115f * u), Offset(x, size.height * 0.72f), 6f * u, cap = StrokeCap.Round)
            } else {
                // off screen: a big arrow at the edge
                val right = off > 0
                val ax = if (right) size.width - 90f * u else 90f * u
                val dir = if (right) 1f else -1f
                val p = Path().apply {
                    moveTo(ax + dir * 60f * u, y)
                    lineTo(ax - dir * 30f * u, y - 70f * u)
                    lineTo(ax - dir * 30f * u, y + 70f * u)
                    close()
                }
                drawPath(p, Gold)
            }
        }
        RText(
            if (facing) "✅ কিবলা এদিকে" else if (off > 0) "ডান দিকে ${bnNum(abs(off).roundToInt())}° ঘুরুন →" else "← বাম দিকে ${bnNum(abs(off).roundToInt())}° ঘুরুন",
            22,
            FontWeight.Bold,
            Color.White,
            Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 300.r)
                .clip(RoundedCornerShape(50))
                .background(if (facing) IslamicA.copy(alpha = 0.9f) else Color(0xAA000000))
                .padding(horizontal = 22.r, vertical = 12.r),
        )
        CameraPanel(Modifier.align(Alignment.BottomCenter)) {
            RText("ফোন খাড়া করে ধরুন, ক্যামেরা যেদিকে তাকিয়ে আছে সেটাই দিক।", 16, FontWeight.Medium, Color.White, maxLines = 2, lineHeight = 22)
            Spacer(Modifier.height(12.r))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                RText("🕋 ${bnNum(qibla.roundToInt())}° · এখন ${bnNum(((shown % 360 + 360) % 360).roundToInt())}°", 18, FontWeight.SemiBold, Gold)
                PanelButton("🧭 কম্পাস", onBack)
            }
            if (h.accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                Spacer(Modifier.height(8.r))
                RText("⚠️ কম্পাস ঠিক নেই — ফোনটা বাতাসে ৮ আকারে ঘোরান", 15, FontWeight.Medium, Color(0xFFFBBF24), maxLines = 2)
            }
        }
    }
}
