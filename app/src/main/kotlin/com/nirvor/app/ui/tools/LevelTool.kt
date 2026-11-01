package com.nirvor.app.ui.tools

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.tools.KeepScreenOn
import com.nirvor.app.tools.rememberGravity
import com.nirvor.app.tools.toolPrefs
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private fun deg(rad: Float): Float = (rad * 180.0 / PI).toFloat()

internal fun one(v: Float): String = String.format(Locale.US, "%.1f°", v)

/**
 * 3D spirit level. Lying on a table: a bull's-eye bubble plus two tube vials. Standing on an
 * edge: a glass of water whose surface always stays level, like real water.
 */
@Composable
fun LevelTool(onBack: () -> Unit) {
    KeepScreenOn()
    val context = LocalContext.current
    val prefs = remember { toolPrefs(context) }
    val haptic = LocalHapticFeedback.current
    val g = rememberGravity(0.15f)
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = auto, 1 = flat, 2 = edge
    var held by remember { mutableStateOf<FloatArray?>(null) }
    var offX by remember { mutableFloatStateOf(prefs.getFloat("level_off_x", 0f)) }
    var offY by remember { mutableFloatStateOf(prefs.getFloat("level_off_y", 0f)) }
    var offE by remember { mutableFloatStateOf(prefs.getFloat("level_off_e", 0f)) }
    var asking by remember { mutableStateOf(false) }

    val v = held ?: g.values
    val gx = v?.getOrNull(0) ?: 0f
    val gy = v?.getOrNull(1) ?: 0f
    val gz = v?.getOrNull(2) ?: 9.8f
    val norm = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(0.1f)
    val flat = when (mode) {
        1 -> true
        2 -> false
        else -> abs(gz) / norm > 0.75f
    }
    val rollRaw = deg(atan2(gx, abs(gz)))
    val pitchRaw = deg(atan2(gy, abs(gz)))
    val roll = rollRaw - offX
    val pitch = pitchRaw - offY
    val inPlane = deg(atan2(gx, gy))
    val nearest = (inPlane / 90f).roundToInt() * 90f
    val edgeRaw = inPlane - nearest
    val edge = edgeRaw - offE
    val level = if (flat) abs(roll) < 0.3f && abs(pitch) < 0.3f else abs(edge) < 0.3f

    LaunchedEffect(level) {
        if (level && v != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    ToolScaffold("3D Spirit Level", "লেভেল ও পানির লেভেল", onBack) {
        if (!g.available) {
            EmptyText("এই ফোনে লেভেল মাপার সেন্সর পাওয়া যায়নি।")
            return@ToolScaffold
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
            ChoiceChip("অটো", mode == 0) { mode = 0 }
            ChoiceChip("টেবিলে রেখে", mode == 1) { mode = 1 }
            ChoiceChip("খাড়া করে (পানি)", mode == 2) { mode = 2 }
        }

        ListCard {
            if (flat) {
                BullsEye(roll, pitch, level, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(18.r))
                RText("X · বাম ↔ ডান", 16, FontWeight.Medium, C.Hint)
                Spacer(Modifier.height(6.r))
                Vial(roll, level, Modifier.fillMaxWidth().height(70.r))
                Spacer(Modifier.height(14.r))
                RText("Y · পেছন ↔ সামনে", 16, FontWeight.Medium, C.Hint)
                Spacer(Modifier.height(6.r))
                Vial(pitch, level, Modifier.fillMaxWidth().height(70.r))
            } else {
                Box {
                    WaterGlass(deg(atan2(gx, gy)), level, Modifier.fillMaxWidth().height(640.r))
                    Column(
                        Modifier.fillMaxWidth().padding(top = 40.r),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RText(one(abs(edge)), 64, FontWeight.Bold, if (level) TGreenDark else C.Ink, align = TextAlign.Center)
                        RText(
                            if (level) "একদম সমান" else if (edge > 0) "ডান দিক উঁচু" else "বাম দিক উঁচু",
                            20,
                            FontWeight.SemiBold,
                            if (level) TGreenDark else C.StatLabel,
                            align = TextAlign.Center,
                        )
                    }
                }
            }
        }

        ListCard {
            val tilt = hypot(roll, pitch)
            RText(
                when {
                    level -> "✅ একদম সমান (LEVEL)"
                    flat -> "${one(tilt)} হেলে আছে"
                    else -> "${one(abs(edge))} হেলে আছে"
                },
                26,
                FontWeight.Bold,
                if (level) TGreenDark else C.Ink,
            )
            Spacer(Modifier.height(6.r))
            if (flat) {
                val side = when {
                    abs(roll) < 0.3f -> "বাম-ডান সমান"
                    roll > 0 -> "ডান দিক ${one(abs(roll))} উঁচু"
                    else -> "বাম দিক ${one(abs(roll))} উঁচু"
                }
                val front = when {
                    abs(pitch) < 0.3f -> "সামনে-পেছনে সমান"
                    pitch > 0 -> "সামনের দিক ${one(abs(pitch))} উঁচু"
                    else -> "পেছনের দিক ${one(abs(pitch))} উঁচু"
                }
                RText("$side · $front", 18, FontWeight.Normal, C.StatLabel, maxLines = 2)
                Spacer(Modifier.height(12.r))
                TriStat(listOf("X" to one(roll), "Y" to one(pitch), "মোট" to one(tilt)))
            } else {
                RText("ফোন যে কিনারায় দাঁড়িয়ে আছে সেটা মাটির সাথে কতটা সমান", 18, FontWeight.Normal, C.StatLabel, maxLines = 2)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            RoundButton(if (held != null) "▶ চালু করুন" else "⏸ ধরে রাখুন", TGreen, Modifier.weight(1f), filled = held != null) {
                held = if (held != null) null else g.values?.clone()
            }
            RoundButton("🎯 ক্যালিব্রেট", TBlue, Modifier.weight(1f), filled = false) { asking = true }
        }
        RText(
            "টিপস: টেবিলে রাখলে বাবল মাঝখানের গোলে এলে জায়গাটা সমান। দেয়ালে বা তাকে মাপতে ফোনটা কিনারায় খাড়া করে রাখুন।",
            16,
            FontWeight.Normal,
            C.Hint,
            maxLines = 3,
            lineHeight = 23,
        )
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            containerColor = Color.White,
            title = { RText("ক্যালিব্রেট", 25, FontWeight.Bold, C.Ink) },
            text = {
                RText(
                    "ফোনটা একদম সমান জায়গায় (যা আপনি জানেন সমান) রেখে \"এখন সমান ধরুন\" চাপুন। " +
                        "কোনো ভুল হলে \"রিসেট\" চাপুন।",
                    19,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    maxLines = 5,
                    lineHeight = 28,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (flat) {
                        offX = rollRaw
                        offY = pitchRaw
                    } else {
                        offE = edgeRaw
                    }
                    prefs.edit().putFloat("level_off_x", offX).putFloat("level_off_y", offY).putFloat("level_off_e", offE).apply()
                    asking = false
                }) { RText("এখন সমান ধরুন", 20, FontWeight.SemiBold, TGreen) }
            },
            dismissButton = {
                TextButton(onClick = {
                    offX = 0f
                    offY = 0f
                    offE = 0f
                    prefs.edit().remove("level_off_x").remove("level_off_y").remove("level_off_e").apply()
                    asking = false
                }) { RText("রিসেট", 20, FontWeight.Medium, C.Hint) }
            },
        )
    }
}

private val Liquid = listOf(Color(0xFFF4FBC8), Color(0xFFCDE97A), Color(0xFF8DBE2E))
private val LiquidLevel = listOf(Color(0xFFE6FBE9), Color(0xFF9BE3B1), Color(0xFF2FB36A))

/** Round bull's-eye level drawn as a glass dome with a moving air bubble. */
@Composable
private fun BullsEye(roll: Float, pitch: Float, level: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val c = center
        val outer = size.minDimension / 2f
        val r = outer * 0.88f
        // metal ring
        drawCircle(
            Brush.linearGradient(listOf(Color(0xFFE9EEF3), Color(0xFF9AA8B6), Color(0xFFEFF3F7)), Offset(0f, 0f), Offset(size.width, size.height)),
            outer,
            c,
        )
        drawCircle(Color(0xFF6B7A8C), outer, c, style = Stroke(outer * 0.012f))
        // liquid
        val liquid = if (level) LiquidLevel else Liquid
        drawCircle(Brush.radialGradient(liquid, c, r), r, c)
        // rings: 1°, 3°, 6° (10° = edge)
        listOf(1f, 3f, 6f).forEach { d ->
            drawCircle(Color(0x55223322), r * d / 10f, c, style = Stroke(if (d == 1f) 3f else 2f))
        }
        drawLine(Color(0x44223322), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 2f)
        drawLine(Color(0x44223322), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 2f)
        // bubble
        val br = r * 0.11f
        var bx = roll.coerceIn(-10f, 10f) / 10f * r
        var by = -pitch.coerceIn(-10f, 10f) / 10f * r
        val dist = hypot(bx, by)
        val maxD = r - br - 4f
        if (dist > maxD) {
            bx = bx / dist * maxD
            by = by / dist * maxD
        }
        val p = Offset(c.x + bx, c.y + by)
        drawCircle(Color(0x33000000), br * 1.05f, p + Offset(br * 0.18f, br * 0.24f))
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, Color(0xF2FFFFFF), Color(0x99E8F5D0)),
                p - Offset(br * 0.3f, br * 0.3f),
                br * 1.3f,
            ),
            br,
            p,
        )
        drawCircle(Color(0x66FFFFFF), br * 0.32f, p - Offset(br * 0.38f, br * 0.4f))
        // glass shine
        drawArc(
            Brush.verticalGradient(listOf(Color(0x88FFFFFF), Color(0x00FFFFFF)), c.y - r, c.y),
            200f,
            140f,
            false,
            Offset(c.x - r * 0.86f, c.y - r * 0.86f),
            Size(r * 1.72f, r * 1.72f),
            style = Stroke(r * 0.06f),
        )
        drawCircle(Color(0x332A3B22), r, c, style = Stroke(4f))
    }
}

/** Tube vial: bubble slides left/right with the tilt. */
@Composable
private fun Vial(angle: Float, level: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val h = size.height
        val w = size.width
        val rad = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFE9EEF3), Color(0xFF9AA8B6))), cornerRadius = rad)
        val inset = h * 0.12f
        val inner = Size(w - 2 * inset, h - 2 * inset)
        val liquid = if (level) LiquidLevel else Liquid
        drawRoundRect(
            Brush.verticalGradient(listOf(liquid[2], liquid[0], liquid[1], liquid[2]), inset, h - inset),
            Offset(inset, inset),
            inner,
            CornerRadius(inner.height / 2f, inner.height / 2f),
        )
        val bw = inner.width * 0.16f
        val cx = w / 2f
        // centre marks
        drawLine(Color(0x99223322), Offset(cx - bw / 2f - 4f, inset), Offset(cx - bw / 2f - 4f, h - inset), 3f)
        drawLine(Color(0x99223322), Offset(cx + bw / 2f + 4f, inset), Offset(cx + bw / 2f + 4f, h - inset), 3f)
        val travel = inner.width / 2f - bw / 2f - 6f
        val bx = cx + angle.coerceIn(-10f, 10f) / 10f * travel
        val bh = inner.height * 0.72f
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White, Color(0xCCFFFFFF), Color(0x88E8F5D0)), h / 2f - bh / 2f, h / 2f + bh / 2f),
            Offset(bx - bw / 2f, h / 2f - bh / 2f),
            Size(bw, bh),
            CornerRadius(bh / 2f, bh / 2f),
        )
        drawRoundRect(
            Color(0x55FFFFFF),
            Offset(inset + 8f, inset + 4f),
            Size(inner.width - 16f, inner.height * 0.25f),
            CornerRadius(inner.height, inner.height),
        )
    }
}

/**
 * A tall glass of water. [tiltDeg] is how far the phone is turned from upright; the water is
 * turned the other way on screen, so it always looks level like real water.
 */
@Composable
private fun WaterGlass(tiltDeg: Float, level: Boolean, modifier: Modifier) {
    val wave by rememberInfiniteTransition(label = "wave").animateFloat(
        0f,
        (2 * PI).toFloat(),
        infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val glass = Path().apply {
            addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(w * 0.08f, w * 0.08f)))
        }
        drawPath(glass, Brush.verticalGradient(listOf(Color(0xFFF3F8FC), Color(0xFFE6EEF5))))
        clipPath(glass) {
            rotate(tiltDeg, Offset(w / 2f, h / 2f)) {
                val big = hypot(w, h) * 1.2f
                val left = w / 2f - big
                val right = w / 2f + big
                val surfaceY = h / 2f
                val water = Path().apply {
                    moveTo(left, surfaceY)
                    var x = left
                    while (x <= right) {
                        val y = surfaceY + sin(x / 70f + wave) * 7f + sin(x / 23f - wave * 2f) * 2f
                        lineTo(x, y)
                        x += 12f
                    }
                    lineTo(right, surfaceY + big)
                    lineTo(left, surfaceY + big)
                    close()
                }
                val colors = if (level) {
                    listOf(Color(0xCC5EE6A5), Color(0xFF16B47A), Color(0xFF07885A))
                } else {
                    listOf(Color(0xCC7FD6FB), Color(0xFF2B9BE0), Color(0xFF1764B5))
                }
                drawPath(water, Brush.verticalGradient(colors, surfaceY - 10f, surfaceY + h))
                drawLine(Color(0xAAFFFFFF), Offset(left, surfaceY + 2f), Offset(right, surfaceY + 2f), 3f)
                // a few rising bubbles
                for (i in 0 until 7) {
                    val bx = w / 2f + (i - 3) * w * 0.12f
                    val phase = ((wave / (2 * PI).toFloat()) + i * 0.137f) % 1f
                    val byy = surfaceY + h * 0.55f * (1f - phase) + 20f
                    drawCircle(Color(0x66FFFFFF), 4f + (i % 3) * 2f, Offset(bx, byy))
                }
            }
        }
        // phone's own level line (dashed) and side scale
        val mid = h / 2f
        var x = 0f
        while (x < w) {
            drawLine(if (level) Color(0xFF0A8F56) else Color(0xFF41597A), Offset(x, mid), Offset(x + 18f, mid), 3f)
            x += 30f
        }
        for (i in -6..6) {
            val y = mid + i * h * 0.06f
            val len = if (i % 3 == 0) w * 0.06f else w * 0.035f
            drawLine(Color(0x8841597A), Offset(0f, y), Offset(len, y), 2f)
            drawLine(Color(0x8841597A), Offset(w - len, y), Offset(w, y), 2f)
        }
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0x55FFFFFF), Color(0x00FFFFFF), Color(0x22FFFFFF))),
            Offset(w * 0.04f, h * 0.03f),
            Size(w * 0.1f, h * 0.94f),
            CornerRadius(w * 0.05f, w * 0.05f),
        )
        drawPath(glass, Color(0xFF9AA8B6), style = Stroke(4f))
    }
}
