package com.nirvor.app.ui.download

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.nirvor.app.downloader.Kind
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.cardSurface
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C

/**
 * Colours of the download pages: the app's own palette (sky header, white cards, the green of
 * the + button), so the download manager looks like the rest of Nirvor.
 */
object Dl {
    val Bg = C.Sheet
    val Card = Color.White
    val Line = C.TodayBorder
    val Soft = Color(0xFFF1F4F7)
    val Track = Color(0xFFE6EDF2)
    val Ink = C.Ink
    val Label = C.StatLabel
    val Sub = C.StatLabelBn
    val Hint = C.Hint

    /** The app's button green (same as "সেভ করুন"). */
    val Green = Color(0xFF0BA765)
    val GreenText = Color(0xFF0A955A)

    val brand = listOf(C.FabA, C.FabB)
    val purple = listOf(C.PurpleA, C.PurpleB)
    val sky = listOf(Color(0xFF5BBEF8), Color(0xFF2F8FDB))
    val orange = listOf(C.OrangeIconA, C.OrangeIconB)
    val amber = listOf(C.NotesIconA, C.NotesIconB)
    val red = listOf(C.RedA, C.RedB)
    val slate = listOf(Color(0xFF6E86A6), Color(0xFF4A6182))

    val Running = Color(0xFF2F8FDB)
    val Done = Color(0xFF0A955A)
    val Failed = C.Danger
    val Paused = Color(0xFFD0681A)

    fun kindColors(k: Kind): List<Color> = when (k) {
        Kind.Video -> purple
        Kind.Audio -> sky
        Kind.Photo -> orange
        Kind.Other -> slate
    }

    fun kindGlyph(k: Kind): Glyph = when (k) {
        Kind.Video -> Glyph.Film
        Kind.Audio -> Glyph.Music
        Kind.Photo -> Glyph.Image
        Kind.Other -> Glyph.FileDoc
    }
}

/** White card like the app's list cards; [accent] draws a coloured outline (e.g. while downloading). */
@Composable
fun DlCard(
    modifier: Modifier = Modifier,
    accent: List<Color>? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = 22.r,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.r)
    var m = modifier
        .fillMaxWidth()
        .cardSurface(shape, SolidColor(Color.White), Dl.Line, 1.r, 5.r)
        .clip(shape)
    if (accent != null) m = m.border(1.8.r, Brush.linearGradient(accent), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

/** Round icon with a gradient fill (same look as the home and settings icons). */
@Composable
fun GradIcon(glyph: Glyph, colors: List<Color>, size: Number = 58, iconScale: Float = 0.55f, shape: Shape = CircleShape) {
    val d = size.toFloat()
    Box(
        Modifier
            .size(d.r)
            .clip(shape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, Color.White, Modifier.size((d * iconScale).r), strokeWidth = 2f)
    }
}

/** The app's green main button, with an optional icon. */
@Composable
fun GreenButton(
    text: String,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    enabled: Boolean = true,
    colors: List<Color> = Dl.brand,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.r)
    Row(
        modifier
            .height(78.r)
            .clip(shape)
            .background(if (enabled) Brush.horizontalGradient(colors) else SolidColor(Dl.Track))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.r),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, if (enabled) Color.White else Dl.Hint, Modifier.size(30.r), strokeWidth = 2.3f)
            Spacer(Modifier.width(12.r))
        }
        RText(text, 22, FontWeight.SemiBold, if (enabled) Color.White else Dl.Hint)
    }
}

/** White button with a thin border (second choice next to a green button). */
@Composable
fun LightButton(text: String, glyph: Glyph, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.r)
    Row(
        modifier
            .height(72.r)
            .clip(shape)
            .background(Color.White)
            .border(1.2.r, C.FieldLine, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.r),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        GlyphIcon(glyph, Dl.Label, Modifier.size(26.r), strokeWidth = 2.1f)
        Spacer(Modifier.width(10.r))
        RText(text, 19, FontWeight.SemiBold, Dl.Label)
    }
}

/** Small rounded label: tinted, or filled with the gradient. */
@Composable
fun Tag(text: String, colors: List<Color>, filled: Boolean = false) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (filled) Brush.horizontalGradient(colors) else SolidColor(colors.last().copy(alpha = 0.12f)))
            .padding(horizontal = 13.r, vertical = 5.r)
    ) {
        RText(text, 15, FontWeight.Bold, if (filled) Color.White else colors.last())
    }
}

/** Progress bar; a soft light sweeps along it while downloading. */
@Composable
fun DlProgress(progress: Float, colors: List<Color>, running: Boolean, modifier: Modifier = Modifier) {
    val sweep by if (running) {
        rememberInfiniteTransition(label = "sweep").animateFloat(
            initialValue = -0.3f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "sweep",
        )
    } else {
        remember { mutableFloatStateOf(-1f) }
    }
    Canvas(modifier.fillMaxWidth().height(10.r)) {
        val h = size.height
        val r = CornerRadius(h / 2, h / 2)
        drawRoundRect(Dl.Track, cornerRadius = r)
        val w = size.width * progress.coerceIn(0f, 1f)
        if (w > 0f) {
            drawRoundRect(
                Brush.horizontalGradient(colors, endX = size.width),
                size = androidx.compose.ui.geometry.Size(w, h),
                cornerRadius = r,
            )
            if (running && sweep in 0f..1.2f) {
                val x = size.width * sweep
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.6f), Color.Transparent),
                        startX = x - 60f,
                        endX = x + 60f,
                    ),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    cornerRadius = r,
                )
            }
        }
    }
}

/** Round light button used in headers and the browser bar. */
@Composable
fun RoundButton(glyph: Glyph, onClick: () -> Unit, size: Number = 58, tint: Color = C.Ink, background: Color = Color.White.copy(alpha = 0.7f)) {
    val d = size.toFloat()
    GlyphIcon(
        glyph,
        tint,
        Modifier
            .size(d.r)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding((d * 0.25f).r),
        strokeWidth = 2.2f,
    )
}

/** Drag handle for the download sheets. */
@Composable
fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 14.r, bottom = 8.r)
            .size(76.r, 8.r)
            .clip(RoundedCornerShape(50))
            .background(Dl.Track)
    )
}
