package com.nirvor.app.ui.browser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.download.Dl
import com.nirvor.app.ui.download.DlCard
import com.nirvor.app.ui.download.GradIcon
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter

// ==================================================================== start page

private class QuickSite(val name: String, val code: String, val url: String, val colors: List<Color>)

private val quickSites = listOf(
    QuickSite("YouTube", "YT", "https://m.youtube.com", listOf(Color(0xFFFF3B3B), Color(0xFFC81E1E))),
    QuickSite("Facebook", "FB", "https://m.facebook.com", listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))),
    QuickSite("Instagram", "IG", "https://www.instagram.com", listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6))),
    QuickSite("TikTok", "TK", "https://www.tiktok.com", listOf(Color(0xFF22D3EE), Color(0xFFF43F5E))),
    QuickSite("X", "X", "https://x.com", listOf(Color(0xFF64748B), Color(0xFF1E293B))),
    QuickSite("Pinterest", "PN", "https://www.pinterest.com", listOf(Color(0xFFFB7185), Color(0xFFBE123C))),
    QuickSite("Vimeo", "VM", "https://vimeo.com", listOf(Color(0xFF38BDF8), Color(0xFF0369A1))),
    QuickSite("Google", "GO", "https://www.google.com", listOf(Color(0xFF34D399), Color(0xFF2563EB))),
)

@Composable
fun StartPage(incognito: Boolean, onGo: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White, Dl.Bg)))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.r),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(34.r))
            HoloGlobe(incognito)
            Spacer(Modifier.height(16.r))
            RText(if (incognito) "ইনকগনিটো ট্যাব" else "Nirvor Browser", 38, FontWeight.Bold, Dl.Ink)
            Spacer(Modifier.height(6.r))
            RText(
                if (incognito) "হিস্ট্রি সেভ হয় না, ট্যাব বন্ধ করলে কুকিও মুছে যায়" else "দেখুন, চালান, এক চাপে ডাউনলোড",
                19,
                FontWeight.Normal,
                Dl.Sub,
            )
            Spacer(Modifier.height(30.r))
            SearchBox(q, { q = it }) { if (q.isNotBlank()) onGo(q) }
            Spacer(Modifier.height(34.r))
            RText("দ্রুত খুলুন", 20, FontWeight.SemiBold, Dl.Sub, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.r))
            quickSites.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { site -> QuickTile(site, Modifier.weight(1f)) { onGo(site.url) } }
                }
                Spacer(Modifier.height(18.r))
            }
            Spacer(Modifier.height(8.r))
            DlCard {
                HintLine(Glyph.Download, Dl.brand, "ভিডিও চালু করুন, তারপর নিচের ডাউনলোড বাটন চাপুন — সব কোয়ালিটি দেখাবে")
                Spacer(Modifier.height(16.r))
                HintLine(Glyph.Shield, Dl.sky, "প্রাইভেট পোস্ট? এখানে একবার লগইন করলে সেগুলোও নামানো যাবে")
            }
            Spacer(Modifier.height(34.r))
        }
    }
}

/** A slowly turning hologram ring around a globe. */
@Composable
private fun HoloGlobe(incognito: Boolean) {
    val t = rememberInfiniteTransition(label = "globe")
    val spin by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "spin",
    )
    Box(Modifier.size(200.r), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val c = center
            val rr = size.minDimension / 2
            drawCircle(
                Brush.radialGradient(listOf(C.FabA.copy(alpha = 0.16f), Color.Transparent), center = c, radius = rr),
                radius = rr,
                center = c,
            )
            rotate(spin, c) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Dl.sky.first(), C.FabA, Color.Transparent), c),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(c.x - rr * 0.88f, c.y - rr * 0.88f),
                    size = Size(rr * 1.76f, rr * 1.76f),
                    style = Stroke(width = rr * 0.03f, cap = StrokeCap.Round),
                )
            }
            rotate(-spin * 1.6f, c) {
                val box = Size(rr * 1.42f, rr * 1.42f)
                val tl = Offset(c.x - rr * 0.71f, c.y - rr * 0.71f)
                drawArc(C.FabB.copy(alpha = 0.7f), 30f, 110f, false, tl, box, style = Stroke(rr * 0.02f, cap = StrokeCap.Round))
                drawArc(Dl.sky.last().copy(alpha = 0.7f), 210f, 110f, false, tl, box, style = Stroke(rr * 0.02f, cap = StrokeCap.Round))
            }
        }
        GradIcon(if (incognito) Glyph.Incognito else Glyph.Globe, if (incognito) Dl.slate else Dl.sky, size = 104, iconScale = 0.52f)
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, onGo: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .fillMaxWidth()
            .height(80.r)
            .clip(shape)
            .background(Color.White)
            .border(1.6.r, Brush.horizontalGradient(Dl.brand), shape)
            .padding(start = 26.r, end = 9.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Search, Dl.Label, Modifier.size(28.r), strokeWidth = 2.2f)
        Spacer(Modifier.width(14.r))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) RText("সার্চ করুন বা লিংক লিখুন", 21, FontWeight.Normal, Dl.Hint)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Inter, fontSize = 21.rs, color = Dl.Ink),
                cursorBrush = SolidColor(Dl.Green),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier
                .size(62.r)
                .clip(CircleShape)
                .background(Brush.linearGradient(Dl.brand))
                .clickable(onClick = onGo),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.ArrowRight, Color.White, Modifier.size(28.r), strokeWidth = 2.4f)
        }
    }
}

@Composable
private fun QuickTile(site: QuickSite, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.r))
            .clickable(onClick = onClick)
            .padding(vertical = 8.r),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(74.r)
                .clip(RoundedCornerShape(24.r))
                .background(Brush.linearGradient(site.colors)),
            contentAlignment = Alignment.Center,
        ) {
            RText(site.code, 23, FontWeight.Bold, Color.White)
        }
        Spacer(Modifier.height(10.r))
        RText(site.name, 16, FontWeight.Medium, Dl.Label)
    }
}

@Composable
private fun HintLine(glyph: Glyph, colors: List<Color>, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GradIcon(glyph, colors, size = 48)
        Spacer(Modifier.width(16.r))
        RText(text, 17, FontWeight.Normal, Dl.Sub, Modifier.weight(1f), maxLines = 3, lineHeight = 25)
    }
}
