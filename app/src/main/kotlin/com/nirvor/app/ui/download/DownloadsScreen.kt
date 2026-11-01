package com.nirvor.app.ui.download

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nirvor.app.downloader.Analyzer
import com.nirvor.app.downloader.DownloadEntity
import com.nirvor.app.downloader.DownloadLive
import com.nirvor.app.downloader.Downloads
import com.nirvor.app.downloader.Kind
import com.nirvor.app.downloader.LiveStat
import com.nirvor.app.downloader.NirvorFolder
import com.nirvor.app.downloader.Status
import com.nirvor.app.downloader.Storage
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.Pill
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import com.nirvor.app.util.Fmt
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private enum class Filter(val bn: String) { All("সব"), Active("চলছে"), Done("সম্পন্ন"), Failed("ব্যর্থ") }

/** The download manager: every download with live progress, plus ways to add new ones. */
@Composable
fun DownloadsScreen(
    bottomPadding: Dp,
    onBack: () -> Unit,
    onBrowser: () -> Unit,
    onSettings: () -> Unit,
    onAnalyze: (String) -> Unit,
    onSetupFolder: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val list by remember { Downloads.dao(context).all() }.collectAsStateWithLifecycle(emptyList())
    val live by DownloadLive.stats.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(Filter.All) }
    var showAdd by remember { mutableStateOf(false) }

    val active = list.count { it.status == Status.RUNNING || it.status == Status.QUEUED }
    val done = list.count { it.status == Status.DONE }
    val speed = live.values.sumOf { it.speedBps }
    var free by remember { mutableStateOf(Storage.freeBytes()) }
    LaunchedEffect(done) { free = Storage.freeBytes() }

    // Re-checked whenever the app comes back (the user may have changed the folder).
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val folderReady = remember(resumeTick) { NirvorFolder.isReady(context) }
    val folderPath = remember(resumeTick) { NirvorFolder.displayPath(context) }

    val shown = list.filter {
        when (filter) {
            Filter.All -> true
            Filter.Active -> it.status in listOf(Status.RUNNING, Status.QUEUED, Status.PAUSED)
            Filter.Done -> it.status == Status.DONE
            Filter.Failed -> it.status == Status.FAILED
        }
    }

    PageScaffold(
        "Download Manager",
        "ডাউনলোড ম্যানেজার",
        onBack = onBack,
        bottomPadding = bottomPadding,
        actions = { RoundButton(Glyph.Gear, onSettings) },
    ) {
        item { StatsCard(active, done, speed, free) }
        if (!folderReady) {
            item { FolderBanner(onSetupFolder) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.r)) {
                ActionTile("লিংক যোগ", Glyph.Link, Dl.sky, Modifier.weight(1f)) { showAdd = true }
                ActionTile("ব্রাউজার", Glyph.Globe, Dl.purple, Modifier.weight(1f), onBrowser)
                ActionTile("ক্লিপবোর্ড", Glyph.Clipboard, Dl.amber, Modifier.weight(1f)) {
                    val url = clipboardUrl(context)
                    if (url != null) onAnalyze(url) else Toast.makeText(context, "ক্লিপবোর্ডে কোনো লিংক নেই", Toast.LENGTH_SHORT).show()
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.r), horizontalArrangement = Arrangement.spacedBy(10.r)) {
                Filter.entries.forEach { f ->
                    val count = when (f) {
                        Filter.All -> list.size
                        Filter.Active -> list.count { it.status in listOf(Status.RUNNING, Status.QUEUED, Status.PAUSED) }
                        Filter.Done -> done
                        Filter.Failed -> list.count { it.status == Status.FAILED }
                    }
                    Pill("${f.bn} $count", f == filter) { filter = f }
                }
            }
        }
        if (list.isEmpty()) {
            item { EmptyHub(if (folderReady) folderPath else null) }
        } else if (shown.isEmpty()) {
            item { EmptyText("এখানে কিছু নেই") }
        }
        items(shown, key = { it.id }) { d ->
            DownloadCard(
                d = d,
                stat = live[d.id],
                onPause = { Downloads.pause(context, d.id) },
                onResume = { scope.launch { Downloads.resume(context, d.id) } },
                onRetry = { scope.launch { Downloads.retry(context, d.id) } },
                onOpen = {
                    if (!Downloads.open(context, d)) Toast.makeText(context, "খোলার মতো অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                },
                onShare = { Downloads.share(context, d) },
                onRemove = { withFile -> scope.launch { Downloads.remove(context, d, withFile) } },
            )
        }
    }

    if (showAdd) {
        AddLinkDialog(
            initial = clipboardUrl(context) ?: "",
            onDismiss = { showAdd = false },
            onGo = {
                showAdd = false
                onAnalyze(it)
            },
        )
    }
}

fun clipboardUrl(context: Context): String? = try {
    val cm = context.getSystemService(ClipboardManager::class.java)
    val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
    Analyzer.extractUrl(text)
} catch (e: Exception) {
    null
}

// ---------------------------------------------------------------- pieces

@Composable
private fun StatsCard(active: Int, done: Int, speed: Long, free: Long) {
    ListCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradIcon(Glyph.Bolt, Dl.brand, size = 66)
            Spacer(Modifier.width(20.r))
            Column(Modifier.weight(1f)) {
                RText("এখনকার গতি", 18, FontWeight.Normal, Dl.Sub)
                Spacer(Modifier.height(2.r))
                RText(if (speed > 0) DownloadLive.speedText(speed) else "—", 34, FontWeight.Bold, Dl.Ink)
            }
        }
        Spacer(Modifier.height(18.r))
        Box(Modifier.fillMaxWidth().height(1.r).background(C.Divider))
        Spacer(Modifier.height(16.r))
        Row(Modifier.fillMaxWidth()) {
            Stat("চলছে", active.toString(), Dl.Running, Modifier.weight(1f))
            Stat("সম্পন্ন", done.toString(), Dl.Done, Modifier.weight(1f))
            Stat("ফাঁকা জায়গা", Analyzer.sizeText(free).ifEmpty { "—" }, C.PurpleB, Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Box(Modifier.size(28.r, 5.r).clip(RoundedCornerShape(50)).background(color))
        Spacer(Modifier.height(10.r))
        RText(value, 25, FontWeight.Bold, Dl.Ink)
        RText(label, 17, FontWeight.Normal, Dl.Sub)
    }
}

/** Shown while the Nirvor folder is not set up. */
@Composable
private fun FolderBanner(onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.r)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFFFFF7E6))
            .border(1.r, Color(0xFFF6E2B6), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 22.r, vertical = 18.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradIcon(Glyph.Folder, Dl.amber, size = 54)
        Spacer(Modifier.width(18.r))
        Column(Modifier.weight(1f)) {
            RText("Nirvor ফোল্ডার ঠিক করা হয়নি", 20, FontWeight.SemiBold, Dl.Ink)
            Spacer(Modifier.height(2.r))
            RText("এখন ফাইল যাচ্ছে Download/Nirvor-এ। চাপ দিয়ে ঠিক করুন", 16, FontWeight.Normal, Dl.Sub, maxLines = 2)
        }
        GlyphIcon(Glyph.ChevronRight, C.RemChevron, Modifier.size(26.r), strokeWidth = 2f)
    }
}

@Composable
private fun ActionTile(label: String, glyph: Glyph, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    ListCard(modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            GradIcon(glyph, colors, size = 58)
            Spacer(Modifier.height(10.r))
            RText(label, 18, FontWeight.Medium, Dl.Ink)
        }
    }
}

@Composable
private fun EmptyHub(folderPath: String?) {
    ListCard {
        RText("কীভাবে ডাউনলোড করবেন", 22, FontWeight.SemiBold, Dl.Ink)
        Spacer(Modifier.height(12.r))
        Tip("১", "YouTube, Facebook, Instagram বা TikTok-এ ভিডিওর Share চাপুন, তারপর \"Nirvor দিয়ে ডাউনলোড\" বাছুন")
        Tip("২", "অথবা লিংক কপি করে এখানে \"ক্লিপবোর্ড\" চাপুন")
        Tip("৩", "অথবা \"ব্রাউজার\"-এ সাইট খুলে নিচের সবুজ ডাউনলোড বাটন চাপুন")
        Spacer(Modifier.height(8.r))
        RText(
            "ফাইল সেভ হয়: " + (folderPath ?: "Download/Nirvor") + " (ধরন অনুযায়ী আলাদা ফোল্ডারে)",
            16,
            FontWeight.Normal,
            Dl.Hint,
            maxLines = 2,
            lineHeight = 23,
        )
    }
}

@Composable
private fun Tip(n: String, text: String) {
    Row(Modifier.padding(vertical = 7.r), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(36.r).clip(CircleShape).background(Brush.linearGradient(Dl.brand)),
            contentAlignment = Alignment.Center,
        ) { RText(n, 17, FontWeight.Bold, Color.White) }
        Spacer(Modifier.width(14.r))
        RText(text, 18, FontWeight.Normal, Dl.Sub, Modifier.weight(1f), maxLines = 3, lineHeight = 26)
    }
}

@Composable
private fun DownloadCard(
    d: DownloadEntity,
    stat: LiveStat?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRemove: (Boolean) -> Unit,
) {
    val kind = Kind.of(d.kind)
    val colors = Dl.kindColors(kind)
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }

    DlCard(
        accent = if (d.status == Status.RUNNING) colors else null,
        onClick = if (d.status == Status.DONE) onOpen else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(104.r)
                    .clip(RoundedCornerShape(18.r))
                    .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.18f) })),
                contentAlignment = Alignment.Center,
            ) {
                if (d.thumbnail != null) {
                    AsyncImage(
                        model = d.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    GlyphIcon(Dl.kindGlyph(kind), colors.last(), Modifier.size(46.r), strokeWidth = 2f)
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(6.r)) {
                    GradIcon(Dl.kindGlyph(kind), colors, size = 32, iconScale = 0.58f)
                }
            }
            Spacer(Modifier.width(18.r))
            Column(Modifier.weight(1f)) {
                RText(d.title, 20, FontWeight.SemiBold, Dl.Ink, maxLines = 2, lineHeight = 27)
                Spacer(Modifier.height(8.r))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(d.label, colors)
                    Spacer(Modifier.width(10.r))
                    RText(statusText(d, stat), 16, FontWeight.Medium, statusColor(d.status), maxLines = 1)
                }
            }
            Box {
                GlyphIcon(
                    Glyph.Dots,
                    Dl.Label,
                    Modifier.size(48.r).clip(CircleShape).clickable { menu = true }.padding(10.r),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (d.status == Status.DONE) {
                        DropdownMenuItem(text = { RText("খুলুন", 19, FontWeight.Medium, Dl.Ink) }, onClick = { menu = false; onOpen() })
                        DropdownMenuItem(text = { RText("শেয়ার করুন", 19, FontWeight.Medium, Dl.Ink) }, onClick = { menu = false; onShare() })
                    }
                    if (d.status == Status.FAILED || d.status == Status.PAUSED) {
                        DropdownMenuItem(text = { RText("আবার শুরু থেকে", 19, FontWeight.Medium, Dl.Ink) }, onClick = { menu = false; onRetry() })
                    }
                    DropdownMenuItem(text = { RText("তালিকা থেকে সরান", 19, FontWeight.Medium, Dl.Ink) }, onClick = { menu = false; onRemove(false) })
                    if (d.status == Status.DONE) {
                        DropdownMenuItem(text = { RText("ফাইলসহ মুছে ফেলুন", 19, FontWeight.Medium, C.Danger) }, onClick = { menu = false; confirm = true })
                    }
                }
            }
        }

        if (d.status != Status.DONE) {
            Spacer(Modifier.height(16.r))
            val p = progressOf(d, stat)
            DlProgress(p, colors, running = d.status == Status.RUNNING)
            Spacer(Modifier.height(12.r))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText(progressLine(d, stat, p), 16, FontWeight.Normal, Dl.Sub, Modifier.weight(1f), maxLines = 1)
                when (d.status) {
                    Status.RUNNING, Status.QUEUED -> SmallAction(Glyph.Pause, "থামান", onPause)
                    Status.PAUSED -> SmallAction(Glyph.Play, "চালু", onResume)
                    Status.FAILED -> SmallAction(Glyph.Refresh, "আবার", onRetry)
                }
            }
            if (d.status == Status.FAILED && !d.error.isNullOrBlank()) {
                Spacer(Modifier.height(8.r))
                RText(d.error, 16, FontWeight.Normal, Dl.Failed, maxLines = 3, lineHeight = 23)
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor = Color.White,
            title = { RText("ফাইল মুছে ফেলবেন?", 24, FontWeight.Bold, Dl.Ink) },
            text = { RText(d.fileName ?: d.title, 18, FontWeight.Normal, Dl.Sub, maxLines = 3) },
            confirmButton = {
                TextButton(onClick = { confirm = false; onRemove(true) }) {
                    RText("মুছুন", 19, FontWeight.SemiBold, C.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { RText("না", 19, FontWeight.Medium, Dl.Sub) }
            },
        )
    }
}

@Composable
private fun SmallAction(glyph: Glyph, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(Dl.Soft)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.r, vertical = 9.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, Dl.Ink, Modifier.size(20.r), strokeWidth = 2.2f)
        Spacer(Modifier.width(8.r))
        RText(label, 16, FontWeight.SemiBold, Dl.Ink)
    }
}

private fun progressOf(d: DownloadEntity, s: LiveStat?): Float =
    if (s != null && s.totalBytes > 0 && s.doneBytes > 0) (s.doneBytes.toFloat() / s.totalBytes).coerceIn(0f, 1f) else d.progress

private fun statusText(d: DownloadEntity, s: LiveStat?): String = when (d.status) {
    Status.DONE -> listOf(
        Analyzer.sizeText(d.totalBytes),
        Fmt.dayMonth(Instant.ofEpochMilli(d.finishedAt).atZone(ZoneId.systemDefault()).toLocalDate()),
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    Status.RUNNING -> s?.stage ?: "নামছে"
    Status.QUEUED -> "লাইনে আছে"
    Status.PAUSED -> "থামানো"
    Status.FAILED -> "হয়নি"
    else -> ""
}

private fun statusColor(status: String): Color = when (status) {
    Status.DONE -> Dl.Done
    Status.RUNNING -> Dl.Running
    Status.FAILED -> Dl.Failed
    Status.PAUSED -> Dl.Paused
    else -> Dl.Sub
}

private fun progressLine(d: DownloadEntity, s: LiveStat?, p: Float): String {
    val parts = mutableListOf("${(p * 100).toInt()}%")
    if (s != null && d.status == Status.RUNNING) {
        if (s.totalBytes > 0) parts += Analyzer.sizeText(s.doneBytes).ifEmpty { "0" } + " / " + Analyzer.sizeText(s.totalBytes)
        DownloadLive.speedText(s.speedBps).takeIf { it.isNotEmpty() }?.let { parts += it }
        DownloadLive.etaText(s.etaSec).takeIf { it.isNotEmpty() && s.etaSec > 0 }?.let { parts += "$it বাকি" }
    } else if (d.totalBytes > 0) {
        parts += Analyzer.sizeText(d.totalBytes)
    }
    return parts.joinToString(" · ")
}

// ---------------------------------------------------------------- add link

@Composable
private fun AddLinkDialog(initial: String, onDismiss: () -> Unit, onGo: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { RText("লিংক যোগ করুন", 26, FontWeight.Bold, Dl.Ink) },
        text = {
            Column {
                RText("YouTube, Facebook, Instagram, TikTok বা যেকোনো ফাইলের লিংক", 18, FontWeight.Normal, Dl.Sub, maxLines = 2)
                Spacer(Modifier.height(16.r))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.r))
                        .background(Color.White)
                        .border(1.2.r, C.FieldLine, RoundedCornerShape(16.r))
                        .padding(horizontal = 18.r, vertical = 18.r)
                ) {
                    if (text.isEmpty()) RText("https://…", 20, FontWeight.Normal, Dl.Hint)
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = TextStyle(fontFamily = Inter, fontSize = 20.rs, color = Dl.Ink),
                        cursorBrush = SolidColor(Dl.Green),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (text.isNotBlank()) onGo(Analyzer.extractUrl(text) ?: text.trim()) }),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onGo(Analyzer.extractUrl(text) ?: text.trim()) }) {
                RText("দেখুন", 20, FontWeight.Bold, Dl.GreenText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { RText("বাতিল", 19, FontWeight.Medium, Dl.Sub) }
        },
    )
}
