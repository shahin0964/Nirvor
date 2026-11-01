package com.nirvor.app.ui.tools.ar

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ar.ArMath
import com.nirvor.app.ar.TiltMath
import com.nirvor.app.tools.KeepScreenOn
import com.nirvor.app.tools.rememberGravity
import com.nirvor.app.tools.toolPrefs
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.Inter
import com.nirvor.app.ui.tools.camera.CameraFrame
import com.nirvor.app.ui.tools.camera.CameraGate
import com.nirvor.app.ui.tools.camera.CameraPanel
import com.nirvor.app.ui.tools.camera.CameraView
import com.nirvor.app.ui.tools.camera.PanelButton
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sqrt
import kotlin.math.tan

/** What the camera + tilt screen starts with. */
enum class TiltGoal(val title: String, val bn: String) {
    Distance("Distance Measure", "ক্যামেরা আর ফোনের কোণ দিয়ে দূরত্ব"),
    Height("Height Measure", "ক্যামেরা আর ফোনের কোণ দিয়ে উচ্চতা"),
}

private const val PREF_PHONE_H = "tilt_phone_height"
private val Green = Color(0xFF0BA765)
private val Mint = Color(0xFF34D399)
private val Soft = Color(0xFFB8C2D0)

/**
 * Measuring with the camera and the phone's tilt, on every phone. Stand on level ground, put the
 * cross on the object's foot → distance; tilt up to its top → height. The foot stays marked on
 * the picture while you tilt, with a live line up to the cross.
 */
@Composable
fun TiltMeasureTool(goal: TiltGoal, onBack: () -> Unit) {
    BackHandler { onBack() }
    CameraGate(goal.title, goal.bn, onBack) { TiltScreen(goal, onBack) }
}

@Composable
private fun TiltScreen(goal: TiltGoal, onBack: () -> Unit) {
    KeepScreenOn()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { toolPrefs(context) }
    val vFov = remember { backCameraVerticalFov(context) }
    var phoneH by remember { mutableFloatStateOf(prefs.getFloat(PREF_PHONE_H, 1.5f)) }
    var wantHeight by remember { mutableStateOf(goal == TiltGoal.Height) }
    var baseAngle by remember { mutableStateOf<Float?>(null) }
    var topAngle by remember { mutableStateOf<Float?>(null) }
    val gravity = rememberGravity(0.12f)

    val g = gravity.values
    val angle = g?.let { TiltMath.angleFromDown(it[0], it[1], it[2]) }
    val base = baseAngle
    val top = topAngle
    val distance = base?.let { TiltMath.groundDistance(phoneH, it) }
    val liveDistance = angle?.let { TiltMath.groundDistance(phoneH, it) }
    val aimHeight = top ?: angle
    val height = if (distance != null && aimHeight != null) TiltMath.heightAt(phoneH, distance, aimHeight) else null

    // 0 = aim at the foot, 1 = aim at the top, 2 = done
    val step = when {
        base == null -> 0
        wantHeight && top == null -> 1
        else -> 2
    }
    val canPress = when (step) {
        0 -> liveDistance != null
        1 -> height != null
        else -> true
    }

    fun setPhoneH(x: Float) {
        phoneH = (Math.round(x.coerceIn(0.3f, 3f) * 100f) / 100f)
        prefs.edit().putFloat(PREF_PHONE_H, phoneH).apply()
    }

    fun press() {
        when (step) {
            0 -> baseAngle = angle
            1 -> topAngle = angle
            else -> {
                baseAngle = null
                topAngle = null
                wantHeight = goal == TiltGoal.Height
            }
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun undo() {
        when {
            topAngle != null -> topAngle = null
            wantHeight && goal == TiltGoal.Distance -> wantHeight = false
            else -> baseAngle = null
        }
    }

    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Inter, fontSize = 16.rs, color = Color.White, fontWeight = FontWeight.Bold)

    CameraFrame(goal.title, onBack) {
        CameraView(Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val gv = g ?: return@Canvas
            val a = angle ?: return@Canvas
            val c = center
            val fpx = (size.height / 2f) / tan(Math.toRadians(vFov / 2.0)).toFloat()
            val gxy = sqrt(gv[0] * gv[0] + gv[1] * gv[1])
            // the direction on screen that points "up" in the world, from the phone's roll
            val up = if (gxy > 1.5f) Offset(gv[0] / gxy, -gv[1] / gxy) else Offset(0f, -1f)
            val across = Offset(-up.y, up.x)

            fun at(p: Float): Offset? {
                val d = p - a
                if (abs(d) > 70f) return null
                return c + up * (fpx * tan(Math.toRadians(d.toDouble())).toFloat())
            }

            // eye level (horizon)
            at(90f)?.let { h ->
                val far = size.width + size.height
                drawLine(
                    Color(0xCCFFFFFF),
                    h - across * far,
                    h + across * far,
                    3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 14f)),
                )
                val t = tm.measure("চোখের লেভেল", labelStyle.copy(fontSize = labelStyle.fontSize * 0.8f, color = Color(0xCCFFFFFF)))
                drawText(t, topLeft = Offset(28f, h.y - t.size.height - 8f))
            }

            val footAt = base?.let { at(it) }
            val topAt = if (step == 2 && top != null) at(top) else if (step == 1) c else null
            if (footAt != null && topAt != null) {
                tiltSegment(footAt, topAt, dashed = step == 1)
                height?.let { tiltLabel(tm, labelStyle, ArMath.len(it), Offset((footAt.x + topAt.x) / 2f + 110f, (footAt.y + topAt.y) / 2f)) }
            }
            if (footAt != null) {
                tiltDot(footAt)
                distance?.let { tiltLabel(tm, labelStyle, "গোড়া · " + ArMath.len(it), footAt + Offset(0f, 56f)) }
            }
            if (step == 2 && top != null) at(top)?.let { tiltDot(it) }

            // the aiming cross
            val col = if (step == 2) Color.White else if (canPress) Mint else Color(0xFFFBBF24)
            drawCircle(Color(0x66000000), 46f, c, style = Stroke(10f))
            drawCircle(col, 46f, c, style = Stroke(5f))
            drawCircle(col, 6f, c)
            for (dir in listOf(up, up * -1f, across, across * -1f)) {
                drawLine(Color(0x66000000), c + dir * 62f, c + dir * 118f, 9f, cap = StrokeCap.Round)
                drawLine(col, c + dir * 62f, c + dir * 118f, 4f, cap = StrokeCap.Round)
            }
            if (step == 0 && liveDistance != null) {
                tiltLabel(tm, labelStyle, ArMath.len(liveDistance), c + Offset(0f, -96f))
            }
        }

        val hint = when {
            g == null -> "ফোনের সেন্সর পড়া যাচ্ছে না"
            step == 0 && liveDistance == null -> "ফোনটা একটু নিচে ঘোরান — গোড়াটা চোখের লেভেলের নিচে থাকে"
            step == 0 -> "সমান জায়গায় দাঁড়ান · ক্রসটা জিনিসের গোড়ায় রাখুন"
            step == 1 -> "এবার ফোনটা তুলে ক্রসটা জিনিসের মাথায় রাখুন"
            else -> ""
        }
        if (hint.isNotEmpty()) {
            RText(
                hint,
                17,
                FontWeight.SemiBold,
                Color.White,
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 96.r, start = 30.r, end = 30.r)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 18.r, vertical = 10.r),
                maxLines = 2,
                align = TextAlign.Center,
            )
        }

        CameraPanel(Modifier.align(Alignment.BottomCenter)) {
            val (big, small) = when (step) {
                0 -> (liveDistance?.let { ArMath.len(it) } ?: "—") to
                    (liveDistance?.let { ArMath.feet(it) + " · গোড়ায় ক্রস রেখে ✓ চাপুন" } ?: "গোড়ায় ক্রস রেখে ✓ চাপুন")
                1 -> (height?.let { ArMath.len(it) } ?: "—") to
                    "দূরত্ব ${distance?.let { ArMath.len(it) } ?: "—"} · মাথায় ক্রস রেখে ✓ চাপুন"
                else -> if (wantHeight) {
                    (height?.let { ArMath.len(it) } ?: "—") to
                        "উচ্চতা ${height?.let { ArMath.feet(it) } ?: ""} · দূরত্ব ${distance?.let { ArMath.len(it) } ?: "—"}"
                } else {
                    (distance?.let { ArMath.len(it) } ?: "—") to "দূরত্ব ${distance?.let { ArMath.feet(it) } ?: ""}"
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    RText(big, 34, FontWeight.Bold, Color.White)
                    Spacer(Modifier.height(2.r))
                    RText(small, 16, FontWeight.Medium, Soft, maxLines = 2, lineHeight = 22)
                }
                if (step == 2 && !wantHeight) {
                    Spacer(Modifier.width(10.r))
                    PanelButton("📏 উচ্চতাও") { wantHeight = true }
                }
            }
            Spacer(Modifier.height(14.r))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.r))
                    .background(Color(0x1FFFFFFF))
                    .padding(horizontal = 16.r, vertical = 8.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    RText("📱 ফোন মাটি থেকে কত উঁচুতে", 16, FontWeight.SemiBold, Color.White)
                    RText("চোখের সামনে ধরলে ≈ আপনার উচ্চতা − ১২ সেমি", 14, FontWeight.Normal, Soft)
                }
                StepButton("−") { setPhoneH(phoneH - 0.05f) }
                Column(Modifier.width(118.r), horizontalAlignment = Alignment.CenterHorizontally) {
                    RText(String.format(Locale.US, "%.2f মি", phoneH), 20, FontWeight.Bold, Color.White)
                    RText(ArMath.feet(phoneH), 13, FontWeight.Normal, Soft)
                }
                StepButton("+") { setPhoneH(phoneH + 0.05f) }
            }
            Spacer(Modifier.height(16.r))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                PanelButton("↩️ ফেরত") { undo() }
                Box(
                    Modifier
                        .size(92.r)
                        .clip(CircleShape)
                        .background(if (canPress) Green else Color(0x55FFFFFF))
                        .clickable(enabled = canPress) { press() },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(if (step == 2) Glyph.Refresh else Glyph.Check, Color.White, Modifier.size(46.r), strokeWidth = 3f)
                }
                PanelButton("🗑 নতুন") {
                    baseAngle = null
                    topAngle = null
                    wantHeight = goal == TiltGoal.Height
                }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(54.r)
            .clip(CircleShape)
            .background(Color(0x33FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        RText(label, 26, FontWeight.Bold, Color.White)
    }
}

/** Vertical view angle of the back camera in portrait (the sensor's long side), in degrees. */
internal fun backCameraVerticalFov(context: Context): Float = try {
    val cm = context.getSystemService(CameraManager::class.java)
    var fov = 65f
    for (id in cm.cameraIdList) {
        val ch = cm.getCameraCharacteristics(id)
        if (ch.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
        val sensor = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: continue
        val focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: continue
        if (focal <= 0f) continue
        val long = maxOf(sensor.width, sensor.height)
        fov = Math.toDegrees(2.0 * atan(long / (2.0 * focal))).toFloat()
        break
    }
    fov.coerceIn(35f, 110f)
} catch (e: Exception) {
    65f
}

private fun DrawScope.tiltLabel(tm: TextMeasurer, style: TextStyle, text: String, at: Offset) {
    val layout = tm.measure(text, style)
    val w = layout.size.width + 24f
    val h = layout.size.height + 10f
    drawRoundRect(Color(0xE60BA765), Offset(at.x - w / 2f, at.y - h / 2f), Size(w, h), CornerRadius(h / 2f, h / 2f))
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

private fun DrawScope.tiltSegment(a: Offset, b: Offset, dashed: Boolean) {
    drawLine(Color(0x66000000), a, b, 10f, cap = StrokeCap.Round)
    drawLine(
        Color.White,
        a,
        b,
        6f,
        cap = StrokeCap.Round,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(18f, 12f)) else null,
    )
}

private fun DrawScope.tiltDot(p: Offset) {
    drawCircle(Color(0x66000000), 20f, p)
    drawCircle(Color.White, 15f, p)
    drawCircle(Green, 9f, p)
}
