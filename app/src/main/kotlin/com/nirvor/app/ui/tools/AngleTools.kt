package com.nirvor.app.ui.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.tools.KeepScreenOn
import com.nirvor.app.tools.rememberGravity
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

private fun toDeg(rad: Double): Float = Math.toDegrees(rad).toFloat()

// ================================================================ Digital Angle

/**
 * Inclinometer / protractor. On an edge: the angle between the phone's edge and the true
 * horizontal, drawn as two crossing lines. Lying on a slope: the slope's angle and %.
 * "এখান থেকে মাপুন" sets a zero so the angle between two surfaces can be read.
 */
@Composable
fun AngleTool(onBack: () -> Unit) {
    KeepScreenOn()
    val g = rememberGravity(0.12f)
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 auto, 1 edge, 2 surface
    var zero by rememberSaveable { mutableStateOf<Float?>(null) }
    var held by remember { mutableStateOf<Float?>(null) }

    val v = g.values
    val gx = v?.getOrNull(0) ?: 0f
    val gy = v?.getOrNull(1) ?: 9.8f
    val gz = v?.getOrNull(2) ?: 0f
    val norm = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(0.1f)
    val onSurface = when (mode) {
        1 -> false
        2 -> true
        else -> abs(gz) / norm > 0.7f
    }
    val live = if (onSurface) {
        toDeg(acos((abs(gz) / norm).coerceIn(0f, 1f).toDouble()))
    } else {
        toDeg(atan2(gx.toDouble(), gy.toDouble()))
    }
    val raw = held ?: live
    val z = zero
    val shown = when {
        z != null -> if (onSurface) raw - z else turn(z, raw)
        onSurface -> raw
        else -> raw - (raw / 90f).roundToInt() * 90f
    }

    ToolScaffold("Digital Angle", "কোণ মাপা", onBack) {
        if (!g.available) {
            EmptyText("এই ফোনে কোণ মাপার সেন্সর পাওয়া যায়নি।")
            return@ToolScaffold
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
            ChoiceChip("অটো", mode == 0) { mode = 0 }
            ChoiceChip("কিনারা দিয়ে", mode == 1) { mode = 1 }
            ChoiceChip("উপরে শুইয়ে", mode == 2) { mode = 2 }
        }
        ListCard {
            Box(Modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.TopCenter) {
                if (onSurface) {
                    SlopeView(shown, Modifier.fillMaxWidth().aspectRatio(1.15f))
                } else {
                    EdgeView(raw, z ?: (raw / 90f).roundToInt() * 90f, Modifier.fillMaxWidth().aspectRatio(1.15f))
                }
                Column(Modifier.padding(top = 10.r), horizontalAlignment = Alignment.CenterHorizontally) {
                    RText(String.format(Locale.US, "%.1f°", shown), 66, FontWeight.Bold, C.Ink, align = TextAlign.Center)
                    if (held != null) RText("⏸ ধরে রাখা", 17, FontWeight.SemiBold, TAmber)
                }
            }
        }
        ListCard {
            if (onSurface) {
                val slope = abs(shown)
                StatLine("ঢাল (ডিগ্রি)", String.format(Locale.US, "%.1f°", slope), bold = true)
                StatLine("ঢাল (%)", if (slope < 89.5f) fmt(tan(Math.toRadians(slope.toDouble())) * 100.0, 1) + "%" else "—")
                StatLine(
                    "অনুপাত",
                    if (slope > 0.05f && slope < 89.5f) "1 : " + fmt(1.0 / tan(Math.toRadians(slope.toDouble())), 1) else "—",
                )
            } else {
                StatLine("কিনারার কোণ (সমান রেখা থেকে)", String.format(Locale.US, "%.1f°", abs(shown)), bold = true)
                StatLine("খাড়া রেখা থেকে", String.format(Locale.US, "%.1f°", 90f - abs(shown).coerceAtMost(90f)))
                StatLine("পুরো কোণ (০–৩৬০)", String.format(Locale.US, "%.1f°", (raw + 360f) % 360f))
            }
            if (z != null) StatLine("শূন্য ধরা হয়েছে", String.format(Locale.US, "%.1f°", z), TBlue)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            RoundButton(if (held == null) "⏸ ধরে রাখুন" else "▶ চালু", TGreen, Modifier.weight(1f), filled = held != null) {
                held = if (held == null) live else null
            }
            RoundButton(if (zero == null) "📍 এখান থেকে মাপুন" else "✕ শূন্য মুছুন", TBlue, Modifier.weight(1f), filled = false) {
                zero = if (zero == null) live else null
            }
        }
        RText(
            "দুই দেয়ালের মাঝের কোণ মাপতে: ফোনের কিনারা প্রথম দেয়ালে রেখে \"এখান থেকে মাপুন\" চাপুন, তারপর দ্বিতীয় দেয়ালে রাখুন।",
            16,
            FontWeight.Normal,
            C.Hint,
            maxLines = 3,
            lineHeight = 23,
        )
    }
}

/**
 * The phone edge that is closest to level (or the saved zero) as a solid line, the real
 * horizontal as a dashed green line, and the angle between them. Angles are screen angles
 * (clockwise from the right), the same way the water in the level tool turns.
 */
@Composable
private fun EdgeView(raw: Float, ref: Float, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height * 0.62f)
        val len = size.width * 0.46f
        fun line(angle: Float, color: Color, width: Float, dashed: Boolean) {
            val a = Math.toRadians(angle.toDouble())
            val dx = (cos(a) * len).toFloat()
            val dy = (sin(a) * len).toFloat()
            drawLine(
                color,
                Offset(c.x - dx, c.y - dy),
                Offset(c.x + dx, c.y + dy),
                width,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(22f, 14f)) else null,
            )
        }
        line(ref, Color(0xFF0B0E22), 8f, false)
        line(raw, Color(0xFF0BA765), 6f, true)
        val ar = len * 0.55f
        val sweep = turn(ref, raw)
        drawArc(Color(0x330BA765), ref, sweep, true, Offset(c.x - ar, c.y - ar), Size(ar * 2, ar * 2))
        drawArc(Color(0xFF0BA765), ref, sweep, false, Offset(c.x - ar, c.y - ar), Size(ar * 2, ar * 2), style = Stroke(4f))
        drawCircle(Color(0xFF0B0E22), 10f, c)
    }
}

/** Side view of a slope with the phone on it. */
@Composable
private fun SlopeView(angle: Float, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val style = TextStyle(fontFamily = Inter, fontSize = 16.rs, color = Color(0xFF63748A))
    Canvas(modifier) {
        val a = Math.toRadians(abs(angle).coerceIn(0f, 89f).toDouble())
        val base = size.width * 0.8f
        val left = Offset(size.width * 0.1f, size.height * 0.9f)
        val run = base
        val rise = (tan(a) * run).toFloat().coerceAtMost(size.height * 0.55f)
        val right = Offset(left.x + run, left.y)
        val top = Offset(right.x, right.y - rise)
        val tri = Path().apply {
            moveTo(left.x, left.y)
            lineTo(right.x, right.y)
            lineTo(top.x, top.y)
            close()
        }
        drawPath(tri, Brush.verticalGradient(listOf(Color(0xFFBDE8D0), Color(0xFFE3F6EC)), top.y, left.y))
        drawPath(tri, Color(0xFF0A8F56), style = Stroke(4f))
        drawLine(Color(0xFF41597A), Offset(0f, left.y), Offset(size.width, left.y), 3f)
        // phone resting on the slope
        val ang = -Math.toDegrees(a).toFloat()
        val mid = Offset((left.x + top.x) / 2f, (left.y + top.y) / 2f)
        val pw = size.width * 0.32f
        val ph = size.width * 0.035f
        val ca = cos(Math.toRadians(ang.toDouble())).toFloat()
        val sa = sin(Math.toRadians(ang.toDouble())).toFloat()
        val nx = sa
        val ny = -ca
        val p1 = Offset(mid.x - ca * pw / 2f, mid.y - sa * pw / 2f)
        val p2 = Offset(mid.x + ca * pw / 2f, mid.y + sa * pw / 2f)
        val phone = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p2.x + nx * ph, p2.y + ny * ph)
            lineTo(p1.x + nx * ph, p1.y + ny * ph)
            close()
        }
        drawPath(phone, Color(0xFF1F2937))
        val ar = size.width * 0.16f
        drawArc(Color(0xFF0BA765), 0f, -Math.toDegrees(a).toFloat(), false, Offset(left.x - ar, left.y - ar), Size(ar * 2, ar * 2), style = Stroke(4f))
        val layout = tm.measure("সমান মেঝে", style)
        drawText(layout, topLeft = Offset(size.width - layout.size.width - 8f, left.y + 6f))
    }
}

// ================================================================ Tilt Meter

/**
 * Tilt meter: a 3D phone that tilts exactly like the real one, forward/back and left/right
 * angles, the biggest tilt seen, and a zero button.
 */
@Composable
fun TiltTool(onBack: () -> Unit) {
    KeepScreenOn()
    val g = rememberGravity(0.15f)
    var zp by rememberSaveable { mutableFloatStateOf(0f) }
    var zr by rememberSaveable { mutableFloatStateOf(0f) }
    var maxTilt by remember { mutableFloatStateOf(0f) }

    val v = g.values
    val gx = v?.getOrNull(0) ?: 0f
    val gy = v?.getOrNull(1) ?: 0f
    val gz = v?.getOrNull(2) ?: 9.8f
    val norm = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(0.1f)
    val pitchRaw = toDeg(asin((gy / norm).coerceIn(-1f, 1f).toDouble()))
    val rollRaw = toDeg(asin((gx / norm).coerceIn(-1f, 1f).toDouble()))
    val pitch = pitchRaw - zp
    val roll = rollRaw - zr
    val total = sqrt(pitch * pitch + roll * roll)
    SideEffect {
        if (v != null && total > maxTilt) maxTilt = total
    }

    ToolScaffold("Tilt Meter", "কতটা হেলে আছে", onBack) {
        if (!g.available) {
            EmptyText("এই ফোনে সেন্সর পাওয়া যায়নি।")
            return@ToolScaffold
        }
        ListCard {
            Phone3D(pitch, roll, Modifier.fillMaxWidth().aspectRatio(1.1f))
        }
        ListCard {
            TriStat(
                listOf(
                    "সামনে-পেছনে" to String.format(Locale.US, "%.1f°", pitch),
                    "বাম-ডানে" to String.format(Locale.US, "%.1f°", roll),
                    "মোট" to String.format(Locale.US, "%.1f°", total),
                )
            )
            Spacer(Modifier.height(14.r))
            val words = buildList {
                if (abs(pitch) >= 0.5f) add(if (pitch > 0) "সামনের দিক উঁচু" else "পেছনের দিক উঁচু")
                if (abs(roll) >= 0.5f) add(if (roll > 0) "ডান দিক উঁচু" else "বাম দিক উঁচু")
            }
            RText(
                if (words.isEmpty()) "✅ সমান আছে" else words.joinToString(" · "),
                20,
                FontWeight.SemiBold,
                if (words.isEmpty()) TGreenDark else C.StatLabel,
            )
            Spacer(Modifier.height(4.r))
            RText("সবচেয়ে বেশি হেলেছিল: ${String.format(Locale.US, "%.1f°", maxTilt)}", 17, FontWeight.Normal, C.Hint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            RoundButton("📍 এখন শূন্য ধরুন", TBlue, Modifier.weight(1f), filled = false) {
                zp = pitchRaw
                zr = rollRaw
                maxTilt = 0f
            }
            RoundButton("↺ রিসেট", TGreen, Modifier.weight(1f), filled = false) {
                zp = 0f
                zr = 0f
                maxTilt = 0f
            }
        }
    }
}

private class P3(val x: Float, val y: Float, val z: Float)

/** A phone-shaped slab drawn in perspective, rotated by pitch and roll. */
@Composable
private fun Phone3D(pitch: Float, roll: Float, modifier: Modifier) {
    Canvas(modifier) {
        val pr = Math.toRadians(pitch.toDouble())
        val rr = Math.toRadians(-roll.toDouble())
        val cp = cos(pr).toFloat()
        val sp = sin(pr).toFloat()
        val cr = cos(rr).toFloat()
        val sr = sin(rr).toFloat()
        fun rot(p: P3): P3 {
            // around X by pitch (top edge up), then around Y by roll (right edge up)
            val y1 = p.y * cp - p.z * sp
            val z1 = p.y * sp + p.z * cp
            val x2 = p.x * cr + z1 * sr
            val z2 = -p.x * sr + z1 * cr
            return P3(x2, y1, z2)
        }
        val elev = Math.toRadians(52.0)
        val ce = cos(elev).toFloat()
        val se = sin(elev).toFloat()
        val dist = 7f
        val scale = size.minDimension * 0.62f
        val cx = size.width / 2f
        val cy = size.height * 0.5f
        fun project(p: P3): Offset {
            // camera in front of and above the phone, looking at it
            val sx = p.x
            val sy = p.y * se + p.z * ce
            val depth = dist + p.y * ce - p.z * se
            val f = dist / max(depth, 0.5f)
            return Offset(cx + sx * f * scale / 2.2f, cy - sy * f * scale / 2.2f)
        }
        // shadow / floor ring
        drawOval(
            Color(0x22000000),
            Offset(cx - scale * 0.42f, cy + scale * 0.36f),
            Size(scale * 0.84f, scale * 0.16f),
        )
        val a = 0.62f
        val b = 1.15f
        val t = 0.09f
        val corners = listOf(P3(-a, -b, 0f), P3(a, -b, 0f), P3(a, b, 0f), P3(-a, b, 0f))
        val top = corners.map { rot(P3(it.x, it.y, t)) }
        val bottom = corners.map { rot(P3(it.x, it.y, -t)) }
        fun face(pts: List<P3>): Pair<Float, Path> {
            val path = Path()
            pts.forEachIndexed { i, p ->
                val o = project(p)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            path.close()
            val depth = pts.map { dist + it.y * ce - it.z * se }.average().toFloat()
            return depth to path
        }
        val faces = mutableListOf<Triple<Float, Path, Color>>()
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val (d, p) = face(listOf(top[i], top[j], bottom[j], bottom[i]))
            faces.add(Triple(d, p, Color(0xFF374151)))
        }
        val (db, pb) = face(bottom)
        faces.add(Triple(db, pb, Color(0xFF111827)))
        val (dt, pt) = face(top)
        faces.add(Triple(dt, pt, Color(0xFF1F2937)))
        faces.sortedByDescending { it.first }.forEach { (_, p, col) -> drawPath(p, col) }
        // screen on the top face (only when it faces the camera)
        val screenFacing = dt <= db
        if (screenFacing) {
            val inset = listOf(P3(-a + 0.06f, -b + 0.12f, t), P3(a - 0.06f, -b + 0.12f, t), P3(a - 0.06f, b - 0.12f, t), P3(-a + 0.06f, b - 0.12f, t)).map { rot(it) }
            val (_, sp2) = face(inset)
            drawPath(sp2, Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF0BA765), Color(0xFF168FFB))))
            // grid lines on the screen
            for (k in 1..3) {
                val yy = -b + 0.12f + k * (2 * b - 0.24f) / 4f
                val l = project(rot(P3(-a + 0.06f, yy, t)))
                val r = project(rot(P3(a - 0.06f, yy, t)))
                drawLine(Color(0x55FFFFFF), l, r, 2f)
            }
            val l2 = project(rot(P3(0f, -b + 0.12f, t)))
            val r2 = project(rot(P3(0f, b - 0.12f, t)))
            drawLine(Color(0x55FFFFFF), l2, r2, 2f)
            // arrow pointing to the top of the phone
            val tip = project(rot(P3(0f, b - 0.3f, t)))
            val wl = project(rot(P3(-0.18f, b - 0.6f, t)))
            val wr = project(rot(P3(0.18f, b - 0.6f, t)))
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(wl.x, wl.y)
                lineTo(wr.x, wr.y)
                close()
            }
            drawPath(arrow, Color.White)
        }
        // world-level reference line through the centre
        drawLine(
            Color(0x660BA765),
            Offset(cx - scale * 0.6f, cy),
            Offset(cx + scale * 0.6f, cy),
            3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)),
        )
    }
}
