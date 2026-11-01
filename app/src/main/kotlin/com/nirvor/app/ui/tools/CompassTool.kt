package com.nirvor.app.ui.tools

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import com.nirvor.app.NirvorApplication
import com.nirvor.app.tools.KeepScreenOn
import com.nirvor.app.tools.hasSensor
import com.nirvor.app.tools.rememberSensor
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DIRS = listOf(
    "উত্তর (N)", "উত্তর-পূর্ব (NE)", "পূর্ব (E)", "দক্ষিণ-পূর্ব (SE)",
    "দক্ষিণ (S)", "দক্ষিণ-পশ্চিম (SW)", "পশ্চিম (W)", "উত্তর-পশ্চিম (NW)",
)

internal fun dirName(deg: Float): String = DIRS[(((deg + 22.5f) % 360f + 360f) % 360f / 45f).toInt() % 8]

/** -180..180: how far [to] is from [from], turning the short way. */
internal fun turn(from: Float, to: Float): Float = ((to - from) % 360f + 540f) % 360f - 180f

/** Bearing from a place to the Kaaba (true north = 0°). */
internal fun qiblaBearing(lat: Double, lon: Double): Float {
    val k1 = Math.toRadians(21.422487)
    val k2 = Math.toRadians(39.826206)
    val p1 = Math.toRadians(lat)
    val dl = k2 - Math.toRadians(lon)
    val y = sin(dl) * cos(k1)
    val x = cos(p1) * kotlin.math.sin(k1) - kotlin.math.sin(p1) * cos(k1) * cos(dl)
    return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
}

/**
 * Compass: a dial that turns with the phone, true north from the saved location, the Qibla
 * direction, and a "remember this direction" marker.
 */
@Composable
fun CompassTool(onBack: () -> Unit) {
    KeepScreenOn()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val app = context.applicationContext as NirvorApplication
    val place = remember {
        app.prefs.city()?.let { it.lat to it.lon } ?: app.prefs.lastCoords()
    }
    val declination = remember(place) {
        place?.let { GeomagneticField(it.first.toFloat(), it.second.toFloat(), 0f, System.currentTimeMillis()).declination } ?: 0f
    }
    val qibla = remember(place) { place?.let { qiblaBearing(it.first, it.second) } }

    val source = remember {
        when {
            hasSensor(context, Sensor.TYPE_ROTATION_VECTOR) -> 0
            hasSensor(context, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) -> 1
            hasSensor(context, Sensor.TYPE_MAGNETIC_FIELD) -> 2
            else -> 3
        }
    }
    if (source == 3) {
        ToolScaffold("Compass", "কম্পাস ও কিবলা", onBack) {
            EmptyText("এই ফোনে কম্পাস সেন্সর (ম্যাগনেটোমিটার) নেই।")
        }
        return
    }
    val primary = rememberSensor(
        when (source) {
            0 -> Sensor.TYPE_ROTATION_VECTOR
            1 -> Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
            else -> Sensor.TYPE_ACCELEROMETER
        },
        if (source == 2) 0.15f else 0.3f,
    )
    val magnet = if (source == 2) rememberSensor(Sensor.TYPE_MAGNETIC_FIELD, 0.15f) else null

    var useTrue by rememberSaveable { mutableStateOf(true) }
    var target by rememberSaveable { mutableStateOf<Float?>(null) }

    val rot = remember { FloatArray(9) }
    val rot2 = remember { FloatArray(9) }
    val orient = remember { FloatArray(3) }
    val pv = primary.values
    val ok = when (source) {
        2 -> {
            val mv = magnet?.values
            pv != null && mv != null && SensorManager.getRotationMatrix(rot, null, pv, mv)
        }
        else -> pv != null && run {
            SensorManager.getRotationMatrixFromVector(rot, if (pv.size > 4) pv.copyOf(4) else pv)
            true
        }
    }
    var magnetic = 0f
    if (ok) {
        SensorManager.getOrientation(rot, orient)
        if (abs(orient[1]) > 0.8f) {
            // Held upright: the direction the back camera faces.
            SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, rot2)
            SensorManager.getOrientation(rot2, orient)
        }
        magnetic = ((Math.toDegrees(orient[0].toDouble()) + 360.0) % 360.0).toFloat()
    }
    val trueNorth = useTrue && place != null
    val heading = if (trueNorth) (magnetic + declination + 360f) % 360f else magnetic
    val accuracy = magnet?.accuracy ?: primary.accuracy
    val qiblaHere = qibla?.let { if (trueNorth) it else (it - declination + 360f) % 360f }
    val qiblaOff = qiblaHere?.let { turn(heading, it) }
    val facingQibla = qiblaOff != null && abs(qiblaOff) < 3f

    LaunchedEffect(facingQibla) {
        if (facingQibla) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    ToolScaffold("Compass", "কম্পাস ও কিবলা", onBack) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            Dial(heading, qiblaHere, target, Modifier.fillMaxWidth().aspectRatio(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RText("${heading.roundToInt() % 360}°", 58, FontWeight.Bold, C.Ink, align = TextAlign.Center)
                RText(dirName(heading), 19, FontWeight.SemiBold, C.StatLabel, align = TextAlign.Center)
            }
        }

        if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF4E0), RoundedCornerShape(18.r))
                    .padding(18.r),
            ) {
                RText("⚠️ কম্পাস ঠিক করুন", 20, FontWeight.SemiBold, Color(0xFFB45309))
                Spacer(Modifier.height(4.r))
                RText(
                    "ফোনটা হাতে নিয়ে বাতাসে ৮ (∞) আকারে কয়েকবার ঘোরান। চুম্বক, স্পিকার, লোহার জিনিস থেকে দূরে রাখুন।",
                    17,
                    FontWeight.Normal,
                    Color(0xFF92400E),
                    maxLines = 3,
                    lineHeight = 24,
                )
            }
        }

        ListCard {
            if (qiblaHere != null && qiblaOff != null) {
                RText("🕋 কিবলা: ${qiblaHere.roundToInt()}° · ${dirName(qiblaHere)}", 21, FontWeight.SemiBold, C.Ink)
                Spacer(Modifier.height(6.r))
                RText(
                    when {
                        facingQibla -> "✅ ফোনের মাথা এখন কিবলার দিকে"
                        qiblaOff > 0 -> "ডান দিকে ${abs(qiblaOff).roundToInt()}° ঘুরুন"
                        else -> "বাম দিকে ${abs(qiblaOff).roundToInt()}° ঘুরুন"
                    },
                    19,
                    FontWeight.Medium,
                    if (facingQibla) TGreenDark else C.StatLabel,
                )
            } else {
                RText("🕋 কিবলার দিক দেখাতে লোকেশন লাগবে", 19, FontWeight.SemiBold, C.Ink, maxLines = 2)
                Spacer(Modifier.height(4.r))
                RText(
                    "আবহাওয়ার জন্য একবার লোকেশন দিলে (বা সেটিংস → আবহাওয়ায় শহর বাছলে) এখানে কিবলা দেখাবে।",
                    17,
                    FontWeight.Normal,
                    C.Hint,
                    maxLines = 3,
                    lineHeight = 24,
                )
            }
            val t = target
            if (t != null) {
                Spacer(Modifier.height(14.r))
                val off = turn(heading, t)
                RText(
                    "🎯 মনে রাখা দিক ${t.roundToInt()}° · " +
                        if (abs(off) < 3f) "ঠিক এদিকেই" else if (off > 0) "ডানে ${abs(off).roundToInt()}°" else "বামে ${abs(off).roundToInt()}°",
                    19,
                    FontWeight.Medium,
                    C.StatLabel,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            RoundButton(if (target == null) "🎯 এই দিক মনে রাখুন" else "✕ মনে রাখা দিক মুছুন", TGreen, Modifier.weight(1f), filled = target == null) {
                target = if (target == null) heading else null
            }
        }
        if (place != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.r), verticalAlignment = Alignment.CenterVertically) {
                ChoiceChip("আসল উত্তর", useTrue) { useTrue = true }
                ChoiceChip("চুম্বকীয় উত্তর", !useTrue) { useTrue = false }
            }
            RText(
                "এখানে চুম্বকীয় আর আসল উত্তরের পার্থক্য ${String.format(java.util.Locale.US, "%.1f", declination)}°",
                16,
                FontWeight.Normal,
                C.Hint,
            )
        }
        RText(
            "ফোন টেবিলে শুয়ে রাখলে ফোনের মাথা যেদিকে, আর খাড়া করে ধরলে পেছনের ক্যামেরা যেদিকে, সেই দিক দেখায়।",
            16,
            FontWeight.Normal,
            C.Hint,
            maxLines = 3,
            lineHeight = 23,
        )
    }
}

@Composable
private fun Dial(heading: Float, qibla: Float?, target: Float?, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val numStyle = TextStyle(fontFamily = Inter, fontSize = 17.rs, color = Color(0xFF41597A), fontWeight = FontWeight.Medium)
    val cardStyle = TextStyle(fontFamily = Inter, fontSize = 30.rs, color = Color(0xFF0B0E22), fontWeight = FontWeight.Bold)
    val northStyle = cardStyle.copy(color = Color(0xFFE5484D))
    val kaabaStyle = TextStyle(fontSize = 30.rs)
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFF1F5F9)), c, r), r, c)
        drawCircle(Color(0xFF15124A), r - r * 0.025f, c, style = Stroke(r * 0.05f))
        rotate(-heading, c) {
            for (d in 0 until 360 step 2) {
                val long = d % 30 == 0
                val mid = d % 10 == 0
                val len = when {
                    long -> r * 0.11f
                    mid -> r * 0.075f
                    else -> r * 0.04f
                }
                val a = Math.toRadians(d.toDouble() - 90.0)
                val ro = r * 0.9f
                val ri = ro - len
                drawLine(
                    if (d == 0) Color(0xFFE5484D) else if (long) Color(0xFF0B0E22) else Color(0xFF8A97A8),
                    Offset(c.x + ro * cos(a).toFloat(), c.y + ro * sin(a).toFloat()),
                    Offset(c.x + ri * cos(a).toFloat(), c.y + ri * sin(a).toFloat()),
                    if (long) 4f else 2f,
                )
            }
            val labels = mapOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
            for (d in 0 until 360 step 30) {
                val text = labels[d] ?: d.toString()
                val style = when {
                    d == 0 -> northStyle
                    labels.containsKey(d) -> cardStyle
                    else -> numStyle
                }
                val layout = tm.measure(text, style)
                val a = Math.toRadians(d.toDouble() - 90.0)
                val rr = r * 0.66f
                rotate(d.toFloat(), Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat())) {
                    drawText(
                        layout,
                        topLeft = Offset(
                            c.x + rr * cos(a).toFloat() - layout.size.width / 2f,
                            c.y + rr * sin(a).toFloat() - layout.size.height / 2f,
                        ),
                    )
                }
            }
            if (target != null) {
                val a = Math.toRadians(target.toDouble() - 90.0)
                val tip = Offset(c.x + r * 0.9f * cos(a).toFloat(), c.y + r * 0.9f * sin(a).toFloat())
                drawCircle(Color(0xFF0BA765), r * 0.035f, tip)
            }
            if (qibla != null) {
                val a = Math.toRadians(qibla.toDouble() - 90.0)
                val rr = r * 0.47f
                val layout = tm.measure("🕋", kaabaStyle)
                drawLine(
                    Color(0x660BA765),
                    c,
                    Offset(c.x + rr * cos(a).toFloat(), c.y + rr * sin(a).toFloat()),
                    5f,
                )
                drawText(
                    layout,
                    topLeft = Offset(
                        c.x + rr * cos(a).toFloat() - layout.size.width / 2f,
                        c.y + rr * sin(a).toFloat() - layout.size.height / 2f,
                    ),
                )
            }
        }
        // Fixed pointer at the top (where the phone points).
        val tri = Path().apply {
            moveTo(c.x, c.y - r * 0.99f)
            lineTo(c.x - r * 0.06f, c.y - r * 0.84f)
            lineTo(c.x + r * 0.06f, c.y - r * 0.84f)
            close()
        }
        drawPath(tri, Color(0xFFE5484D))
        drawCircle(Color(0xFFE5E9EF), r * 0.3f, c, style = Stroke(2f))
        drawLine(Color(0x33000000), Offset(c.x, c.y - r * 0.82f), Offset(c.x, c.y - r * 0.32f), 2f)
    }
}
