package com.nirvor.app.ui.download

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.nirvor.app.downloader.AnalyzeException
import com.nirvor.app.downloader.Analyzer
import com.nirvor.app.downloader.DlOption
import com.nirvor.app.downloader.Downloads
import com.nirvor.app.downloader.Kind
import com.nirvor.app.downloader.MediaInfo
import com.nirvor.app.reminders.Notifications
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import kotlinx.coroutines.launch
import java.util.Locale

private sealed interface Probe {
    data object Loading : Probe
    data class Ready(val info: MediaInfo) : Probe
    data class Failed(val message: String) : Probe
}

/** The format picker as a bottom sheet (used inside the app). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSheet(url: String, onDismiss: () -> Unit, onOpenBrowser: (String) -> Unit, onStarted: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Color.White,
        dragHandle = { SheetHandle() },
    ) {
        FormatPicker(url = url, onClose = onDismiss, onOpenBrowser = onOpenBrowser, onStarted = onStarted)
    }
}

/**
 * Looks at a link and shows what can be downloaded from it: video qualities, audio formats and
 * photos, each with its size. Shared by the in-app sheet and the "Share → Nirvor" window.
 */
@Composable
fun FormatPicker(url: String, onClose: () -> Unit, onOpenBrowser: (String) -> Unit, onStarted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    var probe by remember { mutableStateOf<Probe>(Probe.Loading) }

    LaunchedEffect(url, attempt) {
        probe = Probe.Loading
        probe = try {
            Probe.Ready(Analyzer.analyze(context, url))
        } catch (e: AnalyzeException) {
            Probe.Failed(e.message ?: "")
        } catch (e: Exception) {
            Probe.Failed("কিছু একটা সমস্যা হয়েছে: " + (e.message ?: e.javaClass.simpleName).take(120))
        }
    }

    // Ask for storage (Android 9 and older) and notifications once, right before the first download.
    var pending by remember { mutableStateOf<Pair<MediaInfo, DlOption>?>(null) }
    fun startNow(info: MediaInfo, option: DlOption) {
        scope.launch {
            Downloads.enqueue(context, info, option)
            Toast.makeText(context, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
            onStarted()
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.let { (i, o) -> startNow(i, o) }
        pending = null
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val p = pending
        if (p != null) {
            if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context)) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startNow(p.first, p.second)
                pending = null
            }
        }
    }
    fun request(info: MediaInfo, option: DlOption) {
        pending = info to option
        when {
            Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED ->
                storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)

            Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(context) ->
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

            else -> {
                pending = null
                startNow(info, option)
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 28.r, end = 28.r, bottom = 28.r)
    ) {
        when (val p = probe) {
            Probe.Loading -> Scanning(url)
            is Probe.Failed -> Failed(
                message = p.message,
                onRetry = { attempt++ },
                onBrowser = { onOpenBrowser(url) },
                onClose = onClose,
            )
            is Probe.Ready -> Choices(p.info, onDownload = { o -> request(p.info, o) })
        }
    }
}

// ---------------------------------------------------------------- states

@Composable
private fun Scanning(url: String) {
    val t = rememberInfiniteTransition(label = "scan")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "a")
    val pulse by t.animateFloat(0.6f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.r),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(150.r), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(150.r)) {
                val sw = size.minDimension * 0.06f
                drawCircle(Dl.Track, style = Stroke(sw))
                rotate(angle) {
                    drawArc(
                        Brush.sweepGradient(listOf(Color.Transparent, C.FabA, C.FabB)),
                        startAngle = 0f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
                drawCircle(
                    Brush.radialGradient(listOf(C.FabA.copy(alpha = 0.22f * pulse), Color.Transparent)),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width / 2, size.height / 2),
                )
            }
            GlyphIcon(Glyph.Link, Dl.GreenText, Modifier.size(52.r), strokeWidth = 2.0f)
        }
        Spacer(Modifier.height(24.r))
        RText("লিংক দেখা হচ্ছে…", 25, FontWeight.Bold, Dl.Ink)
        Spacer(Modifier.height(8.r))
        RText(Analyzer.hostOf(url) ?: url, 18, FontWeight.Normal, Dl.Sub)
        Spacer(Modifier.height(16.r))
        RText(
            "প্রথমবার ইঞ্জিন চালু হতে কয়েক সেকেন্ড বেশি লাগতে পারে",
            16,
            FontWeight.Normal,
            Dl.Hint,
            align = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit, onBrowser: () -> Unit, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 26.r), horizontalAlignment = Alignment.CenterHorizontally) {
        GradIcon(Glyph.Close, Dl.red, size = 90, iconScale = 0.5f)
        Spacer(Modifier.height(20.r))
        RText("ডাউনলোড করার মতো কিছু পাওয়া যায়নি", 23, FontWeight.Bold, Dl.Ink, align = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.height(10.r))
        RText(message, 18, FontWeight.Normal, Dl.Sub, align = TextAlign.Center, maxLines = 4, lineHeight = 27)
        Spacer(Modifier.height(26.r))
        GreenButton("আবার চেষ্টা করুন", Modifier.fillMaxWidth(), glyph = Glyph.Refresh, onClick = onRetry)
        Spacer(Modifier.height(12.r))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.r)) {
            LightButton("ব্রাউজারে খুলুন", Glyph.Globe, Modifier.weight(1f), onBrowser)
            LightButton("বন্ধ করুন", Glyph.Close, Modifier.weight(1f), onClose)
        }
    }
}

@Composable
private fun Choices(info: MediaInfo, onDownload: (DlOption) -> Unit) {
    val kinds = Kind.entries.filter { k -> info.options.any { it.kind == k } }
    var tab by remember(info) { mutableStateOf(kinds.firstOrNull() ?: Kind.Video) }
    var selected by remember(info) { mutableStateOf(info.options.firstOrNull { it.kind == tab }) }

    // Preview
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(22.r))
            .background(Brush.linearGradient(listOf(Color(0xFFE7F2F8), Color(0xFFD2E3EE))))
    ) {
        if (info.thumbnail != null) {
            AsyncImage(
                model = info.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                GradIcon(Dl.kindGlyph(kinds.firstOrNull() ?: Kind.Other), Dl.kindColors(kinds.firstOrNull() ?: Kind.Other), size = 96)
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000))))
        )
        Row(Modifier.align(Alignment.BottomStart).padding(16.r), verticalAlignment = Alignment.CenterVertically) {
            info.source?.let { Tag(it, platformColors(info.pageUrl), filled = true) }
            if (info.durationSec > 0) {
                Spacer(Modifier.width(10.r))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xCC0B0E22))
                        .padding(horizontal = 13.r, vertical = 5.r)
                ) { RText(duration(info.durationSec), 15, FontWeight.Bold, Color.White) }
            }
        }
    }
    Spacer(Modifier.height(18.r))
    RText(info.title, 23, FontWeight.SemiBold, Dl.Ink, maxLines = 2, lineHeight = 31)
    Spacer(Modifier.height(18.r))

    // Tabs
    if (kinds.size > 1) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.r))
                .background(Dl.Soft)
                .padding(6.r),
        ) {
            kinds.forEach { k ->
                val on = k == tab
                Row(
                    Modifier
                        .weight(1f)
                        .height(62.r)
                        .clip(RoundedCornerShape(16.r))
                        .background(if (on) Brush.horizontalGradient(Dl.kindColors(k)) else SolidColor(Color.Transparent))
                        .clickable {
                            tab = k
                            selected = info.options.firstOrNull { it.kind == k }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    GlyphIcon(Dl.kindGlyph(k), if (on) Color.White else Dl.Label, Modifier.size(24.r), strokeWidth = 2f)
                    Spacer(Modifier.width(8.r))
                    RText(k.bn, 19, FontWeight.SemiBold, if (on) Color.White else Dl.Label)
                }
            }
        }
        Spacer(Modifier.height(16.r))
    }

    // Options
    Column(
        Modifier.heightIn(max = 620.r).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.r),
    ) {
        info.options.filter { it.kind == tab }.forEach { o ->
            OptionRow(o, o == selected) { selected = o }
        }
    }
    Spacer(Modifier.height(20.r))
    val sel = selected
    GreenButton(
        text = if (sel == null) "একটা অপশন বাছুন" else "ডাউনলোড" + if (sel.sizeBytes > 0) " · " + Analyzer.sizeText(sel.sizeBytes) else "",
        modifier = Modifier.fillMaxWidth(),
        glyph = Glyph.Download,
        enabled = sel != null,
    ) { sel?.let(onDownload) }
}

@Composable
private fun OptionRow(o: DlOption, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.r)
    val colors = Dl.kindColors(o.kind)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Color(0xFFEFFAF4) else Color.White)
            .border(if (selected) 2.r else 1.2.r, if (selected) Dl.Green else C.FieldLine, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.r, vertical = 16.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(30.r)
                .border(2.4.r, if (selected) Dl.Green else C.HabOpen, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(15.r).background(Dl.Green, CircleShape))
        }
        Spacer(Modifier.width(18.r))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText(o.label, 23, FontWeight.Bold, Dl.Ink)
                o.badge?.let {
                    Spacer(Modifier.width(10.r))
                    Tag(it, colors)
                }
            }
            Spacer(Modifier.height(4.r))
            RText(o.detail, 17, FontWeight.Normal, Dl.Sub, maxLines = 2)
        }
    }
}

// ---------------------------------------------------------------- helpers

fun platformColors(url: String): List<Color> {
    val h = (Analyzer.hostOf(url) ?: "").lowercase(Locale.US)
    return when {
        "youtu" in h -> listOf(Color(0xFFFF3B3B), Color(0xFFC81E1E))
        "facebook" in h || "fb." in h -> listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
        "instagram" in h -> listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6))
        "tiktok" in h -> listOf(Color(0xFF22D3EE), Color(0xFFF43F5E))
        "twitter" in h || "x.com" in h -> listOf(Color(0xFF64748B), Color(0xFF0F172A))
        else -> Dl.brand
    }
}

private fun duration(sec: Int): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
