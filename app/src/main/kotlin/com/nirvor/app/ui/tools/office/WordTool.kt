package com.nirvor.app.ui.tools.office

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.viewinterop.AndroidView
import com.nirvor.app.downloader.Storage
import com.nirvor.app.office.Doc
import com.nirvor.app.office.DocPdf
import com.nirvor.app.office.DocxReader
import com.nirvor.app.office.DocxWriter
import com.nirvor.app.office.EditorPage
import com.nirvor.app.office.HtmlDoc
import com.nirvor.app.office.MiniJson
import com.nirvor.app.office.WordDoc
import com.nirvor.app.office.WordStore
import com.nirvor.app.office.WordTemplates
import com.nirvor.app.office.arr
import com.nirvor.app.office.flag
import com.nirvor.app.office.num
import com.nirvor.app.office.obj
import com.nirvor.app.office.str
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.ledger.ConfirmDialog
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.ToolScaffold
import com.nirvor.app.ui.tools.camera.loadPicked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/** Office Word: your documents, templates to start from, and the page editor. */
@Composable
fun WordTool(onBack: () -> Unit) {
    var openId by rememberSaveable { mutableStateOf(WordStore.pendingOpen.also { WordStore.pendingOpen = null }) }
    val id = openId
    if (id == null) {
        WordHome(onBack) { openId = it }
    } else {
        WordEditor(id) { openId = null }
    }
}

// ================================================================ documents

/** Opens a Word / text / web page file from the phone as a new document. Null: not readable. */
private fun importFile(context: Context, uri: Uri): String? {
    val name = displayName(context, uri)
    val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
    val title = name.substringBeforeLast('.').ifBlank { name }
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val html = when {
        ext == "docx" || (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) ->
            HtmlDoc.toHtml(DocxReader.read(bytes, title))
        ext == "html" || ext == "htm" -> {
            val s = String(bytes, Charsets.UTF_8)
            val body = Regex("(?is)<body[^>]*>(.*)</body>").find(s)?.groupValues?.get(1) ?: s
            body.replace(Regex("(?is)<script.*?</script>|<style.*?</style>"), "")
                .replace(Regex("(?i)\\son\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*')"), "")
        }
        ext == "txt" || ext == "md" || ext == "csv" || ext.isEmpty() -> HtmlDoc.toHtml(Doc.fromText(String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")))
        else -> return null
    }
    return WordStore.create(context, title, html)
}

/**
 * A saved editor document as the app's [Doc] model, read by an off-screen copy of the editor page
 * (used by Word → PDF for documents made in Office Word).
 */
@SuppressLint("SetJavaScriptEnabled")
internal suspend fun editorModel(context: Context, html: String, title: String): Doc? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { cont ->
        val wv = WebView(context)
        wv.settings.javaScriptEnabled = true
        wv.layout(0, 0, 1080, 2000)
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript("N.load(${MiniJson.quote(html)}); N.model()") { res ->
                    val doc = try {
                        (MiniJson.parse(res) as? String)?.let { HtmlDoc.fromJson(it) }?.let { Doc(it.blocks, title) }
                    } catch (e: Exception) {
                        null
                    }
                    view.destroy()
                    if (cont.isActive) cont.resume(doc)
                }
            }
        }
        wv.loadDataWithBaseURL("https://nirvor.app/", EditorPage.HTML, "text/html", "utf-8", null)
    }
}

private fun whenText(ms: Long): String = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.US).format(Date(ms))

private fun sizeText(b: Long): String = if (b < 1024 * 1024) "${(b / 1024).coerceAtLeast(1)} KB" else String.format(Locale.US, "%.1f MB", b / 1048576.0)

@Composable
private fun WordHome(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var docs by remember { mutableStateOf<List<WordDoc>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<WordDoc?>(null) }
    var deleting by remember { mutableStateOf<WordDoc?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { docs = withContext(Dispatchers.IO) { WordStore.list(context) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val id = withContext(Dispatchers.IO) {
                    try {
                        importFile(context, uri)
                    } catch (e: Exception) {
                        null
                    }
                }
                busy = false
                if (id != null) onOpen(id) else Toast.makeText(context, "এই ফাইলটা খোলা গেল না (.docx, .txt বা .html দিন)", Toast.LENGTH_LONG).show()
            }
        }
    }

    ToolScaffold("Office Word", "নতুন ডকুমেন্ট বানান ও এডিট করুন", onBack) {
        RText("নতুন ডকুমেন্ট", 21, FontWeight.SemiBold, C.Ink)
        WordTemplates.all.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.r)) {
                row.forEach { t ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable {
                                scope.launch {
                                    // a template keeps its name; a blank page is named after its first line
                                    val name = if (t === WordTemplates.all.first()) "" else t.name
                                    val id = withContext(Dispatchers.IO) { WordStore.create(context, name, HtmlDoc.toHtml(t.build())) }
                                    onOpen(id)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.78f)
                                .clip(RoundedCornerShape(14.r))
                                .background(Color.White)
                                .border(1.2.r, C.FieldLine, RoundedCornerShape(14.r)),
                            contentAlignment = Alignment.Center,
                        ) {
                            // a tiny page: a few grey lines with the emoji on top
                            Canvas(Modifier.fillMaxSize().padding(14.r)) {
                                val gap = size.height / 9
                                for (k in 1..8) {
                                    val w = if (k % 3 == 0) size.width * 0.6f else size.width
                                    drawLine(Color(0xFFE6EBF0), Offset(0f, gap * k), Offset(w, gap * k), 3f, cap = StrokeCap.Round)
                                }
                            }
                            RText(t.emoji, 40, FontWeight.Normal, C.Ink)
                        }
                        Spacer(Modifier.height(8.r))
                        RText(t.name, 16, FontWeight.Medium, C.Ink, maxLines = 2, align = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        RoundButton("📂 ফোন থেকে Word / লেখার ফাইল খুলুন", OfficePurple, Modifier.fillMaxWidth(), filled = false) {
            picker.launch(arrayOf(MIME_DOCX, "text/plain", "text/html", "text/markdown", "application/octet-stream"))
        }
        if (busy) BusyCard("ফাইল খুলছি…")
        RText("আমার ডকুমেন্ট (${docs.size})", 21, FontWeight.SemiBold, C.Ink)
        if (docs.isEmpty()) EmptyText("এখনো কোনো ডকুমেন্ট নেই। ওপরের যেকোনো একটা দিয়ে শুরু করুন।")
        docs.forEach { d ->
            ListCard(onClick = { onOpen(d.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.r, 64.r)
                            .clip(RoundedCornerShape(8.r))
                            .background(OfficePurple.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) { RText("📝", 26, FontWeight.Normal, C.Ink) }
                    Spacer(Modifier.width(14.r))
                    Column(Modifier.weight(1f)) {
                        RText(d.title, 19, FontWeight.SemiBold, C.Ink, maxLines = 2)
                        RText("${whenText(d.updated)} · ${sizeText(d.size)}", 15, FontWeight.Normal, C.Hint)
                    }
                    RText("✏️", 22, FontWeight.Normal, C.Ink, Modifier.clip(CircleShape).clickable { renaming = d }.padding(10.r))
                    RText("🗑", 22, FontWeight.Normal, C.Ink, Modifier.clip(CircleShape).clickable { deleting = d }.padding(10.r))
                }
            }
        }
    }
    renaming?.let { d ->
        TextDialog("নাম বদলান", d.title, "ডকুমেন্টের নাম", "রাখুন", onDismiss = { renaming = null }) { name ->
            scope.launch {
                withContext(Dispatchers.IO) { WordStore.rename(context, d.id, name) }
                refresh++
            }
        }
    }
    deleting?.let { d ->
        ConfirmDialog("ডকুমেন্ট মুছবেন?", "\"${d.title}\" পুরোপুরি মুছে যাবে।", "মুছুন", danger = true, onDismiss = { deleting = null }) {
            scope.launch {
                withContext(Dispatchers.IO) { WordStore.delete(context, d.id) }
                refresh++
            }
        }
    }
}

// ================================================================ editor

/** What the cursor is on, reported by the page. */
private class EdState(
    val b: Boolean = false,
    val i: Boolean = false,
    val u: Boolean = false,
    val s: Boolean = false,
    val kind: String = "p",
    val list: String = "",
    val align: String = "left",
    val table: Boolean = false,
    val img: Boolean = false,
    val words: Int = 0,
) {
    companion object {
        fun parse(json: String): EdState = try {
            val m = MiniJson.parse(json).obj()
            EdState(m.flag("b"), m.flag("i"), m.flag("u"), m.flag("s"), m.str("kind"), m.str("list"), m.str("align"), m.flag("table"), m.flag("img"), (m.num("words") ?: 0.0).toInt())
        } catch (e: Exception) {
            EdState()
        }
    }
}

private enum class Panel { None, Style, Color, Mark, Size, Align, Table, Insert, Export }

/** Messages from the page (they arrive on a background thread). */
private class EditorBridge(val onState: (String) -> Unit, val onChanged: (String, String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun state(json: String) {
        main.post { onState(json) }
    }

    @JavascriptInterface
    fun changed(html: String, first: String) {
        main.post { onChanged(html, first) }
    }
}

private val TEXT_COLORS = listOf(
    0x1B1F2A, 0xE5484D, 0xF76B15, 0xD97F06, 0x0A8F56, 0x0E9F8E, 0x168FFB, 0x4F5BD5, 0x8A43DB, 0xE0428E, 0x8A5A3C, 0x6B7785,
)
private val MARK_COLORS = listOf(0xFFF59D, 0xC8F7DC, 0xCDE9FF, 0xFFD6E7, 0xFFE0B2, 0xE9D7FE, 0xE5E7EB)
private val SIZES = listOf("ছোট" to 13, "স্বাভাবিক" to 16, "মাঝারি" to 20, "বড়" to 24, "আরো বড়" to 30, "বিশাল" to 40)

private fun hex(c: Int) = String.format("#%06x", c and 0xFFFFFF)

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WordEditor(id: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var st by remember { mutableStateOf(EdState()) }
    var saving by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf(Panel.None) }
    var pageReady by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var html by remember { mutableStateOf<String?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var linkOpen by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Storage.Published?>(null) }

    fun save(h: String, first: String) {
        saving = true
        scope.launch {
            val t = withContext(Dispatchers.IO) {
                WordStore.save(context, id, h, first)
                WordStore.title(context, id)
            }
            title = t
            saving = false
        }
    }

    val bridge = remember { EditorBridge({ st = EdState.parse(it) }, { h, f -> save(h, f) }) }
    val web = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.textZoom = 100
            settings.setSupportZoom(false)
            setBackgroundColor(0xFFE9EEF2.toInt())
            addJavascriptInterface(bridge, "Android")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

                override fun onPageFinished(view: WebView, url: String?) {
                    pageReady = true
                }
            }
            loadDataWithBaseURL("https://nirvor.app/", EditorPage.HTML, "text/html", "utf-8", null)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            web.removeJavascriptInterface("Android")
            web.destroy()
        }
    }
    LaunchedEffect(id) {
        val (h, t) = withContext(Dispatchers.IO) { (WordStore.load(context, id) ?: "") to WordStore.title(context, id) }
        html = h
        title = t
    }
    LaunchedEffect(pageReady, html) {
        val h = html
        if (pageReady && h != null && !loaded) {
            web.evaluateJavascript("N.load(${MiniJson.quote(h)})", null)
            loaded = true
        }
    }

    fun js(code: String) {
        if (loaded) web.evaluateJavascript(code, null)
    }

    fun close() {
        if (!loaded) {
            onClose()
            return
        }
        web.evaluateJavascript("JSON.stringify([N.html(), N.first()])") { res ->
            val pair = try {
                (MiniJson.parse(MiniJson.parse(res) as? String ?: "[]")).arr()
            } catch (e: Exception) {
                emptyList()
            }
            val h = pair.getOrNull(0) as? String
            val first = pair.getOrNull(1) as? String ?: ""
            if (h == null) {
                onClose()
            } else {
                scope.launch {
                    withContext(Dispatchers.IO) { WordStore.save(context, id, h, first) }
                    onClose()
                }
            }
        }
    }

    fun export(pdf: Boolean, share: Boolean) {
        if (!loaded || exporting) return
        exporting = true
        panel = Panel.None
        web.evaluateJavascript("N.model()") { res ->
            val json = try {
                MiniJson.parse(res) as? String
            } catch (e: Exception) {
                null
            }
            scope.launch {
                val p = if (json == null) {
                    null
                } else {
                    withContext(Dispatchers.IO) {
                        try {
                            val name = WordStore.fileName(title.ifBlank { "Document" })
                            val doc = HtmlDoc.fromJson(json).let { Doc(it.blocks, title) }
                            val dir = File(context.cacheDir, "office").apply { mkdirs() }
                            val f = File(dir, if (pdf) "$name.pdf" else "$name.docx")
                            f.outputStream().use { if (pdf) DocPdf.write(doc, it) else DocxWriter.write(doc, it) }
                            publish(context, f, if (pdf) MIME_PDF else MIME_DOCX)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                exporting = false
                when {
                    p == null -> Toast.makeText(context, "ফাইল বানানো গেল না", Toast.LENGTH_SHORT).show()
                    share -> startSafe(context, Storage.shareIntent(p.uri, p.mime))
                    else -> saved = p
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val data = withContext(Dispatchers.IO) {
                    loadPicked(context, uri, 1400)?.let { bmp ->
                        val out = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                        val pct = if (bmp.width >= bmp.height) 100 else 60
                        bmp.recycle()
                        "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray()) to pct
                    }
                }
                if (data == null) {
                    Toast.makeText(context, "ছবিটা খোলা গেল না", Toast.LENGTH_SHORT).show()
                } else {
                    js("N.image(${MiniJson.quote(data.first)}, ${data.second})")
                }
            }
        }
    }

    BackHandler {
        if (panel != Panel.None) panel = Panel.None else close()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars).only(WindowInsetsSides.Bottom)),
    ) {
        // top bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(start = 14.r, end = 10.r, top = 10.r, bottom = 10.r),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(
                Glyph.ChevronLeft,
                C.Ink,
                Modifier
                    .size(52.r)
                    .clip(CircleShape)
                    .clickable { close() }
                    .padding(12.r),
                strokeWidth = 2.2f,
            )
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.r))
                    .clickable { renameOpen = true }
                    .padding(horizontal = 8.r, vertical = 4.r),
            ) {
                RText(title.ifBlank { "নাম ছাড়া ডকুমেন্ট" }, 21, FontWeight.Bold, C.Ink)
                RText(
                    "${st.words} শব্দ · " + if (saving) "সেভ হচ্ছে…" else if (loaded) "সেভ হয়েছে ✓" else "খুলছি…",
                    14,
                    FontWeight.Normal,
                    C.Hint,
                )
            }
            TopIcon("↩️") { js("N.cmd('undo')") }
            TopIcon("↪️") { js("N.cmd('redo')") }
            RText(
                if (exporting) "…" else "সেভ / শেয়ার",
                16,
                FontWeight.SemiBold,
                Color.White,
                Modifier
                    .padding(start = 4.r)
                    .clip(RoundedCornerShape(50))
                    .background(OfficePurple)
                    .clickable { panel = if (panel == Panel.Export) Panel.None else Panel.Export }
                    .padding(horizontal = 14.r, vertical = 9.r),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.r).background(C.FieldLine))
        AndroidView({ web }, Modifier.fillMaxWidth().weight(1f))

        // what the cursor is on: table or picture tools
        if (st.table && panel == Panel.None) {
            ContextRow(
                listOf(
                    "⬆️ সারি" to "rowAbove", "⬇️ সারি" to "rowBelow", "⬅️ কলাম" to "colLeft", "➡️ কলাম" to "colRight",
                    "✖️ সারি" to "delRow", "✖️ কলাম" to "delCol", "হেডার" to "header", "🗑 টেবিল" to "delTable",
                ),
            ) { js("N.tableOp('$it')") }
        }
        if (st.img && panel == Panel.None) {
            ContextRow(listOf("২৫%" to "25", "৫০%" to "50", "৭৫%" to "75", "পুরো" to "100", "🗑 ছবি মুছুন" to "del")) {
                if (it == "del") js("N.imgDelete()") else js("N.imgSize($it)")
            }
        }
        when (panel) {
            Panel.None -> Unit
            Panel.Style -> PanelBox {
                StyleChoice("শিরোনাম", 26, FontWeight.Bold, C.Ink, st.kind == "title") { js("N.block('title')") }
                StyleChoice("হেডিং ১", 22, FontWeight.Bold, Color(0xFF0A8F56), st.kind == "h1") { js("N.block('h1')") }
                StyleChoice("হেডিং ২", 19, FontWeight.Bold, Color(0xFF1F2A3A), st.kind == "h2") { js("N.block('h2')") }
                StyleChoice("হেডিং ৩", 17, FontWeight.Bold, Color(0xFF41597A), st.kind == "h3") { js("N.block('h3')") }
                StyleChoice("সাধারণ লেখা", 16, FontWeight.Normal, C.Ink, st.kind == "p") { js("N.block('p')") }
                StyleChoice("“ উদ্ধৃতি ”", 16, FontWeight.Normal, Color(0xFF41597A), st.kind == "quote") { js("N.block('quote')") }
            }
            Panel.Color -> PanelBox {
                TEXT_COLORS.forEach { c ->
                    Swatch(Color(0xFF000000 or c.toLong())) { js("N.cmd('foreColor', '${hex(c)}')") }
                }
            }
            Panel.Mark -> PanelBox {
                MARK_COLORS.forEach { c ->
                    Swatch(Color(0xFF000000 or c.toLong())) { js("N.cmd('hiliteColor', '${hex(c)}')") }
                }
                Chip("মার্কার মুছুন") { js("N.cmd('hiliteColor', 'transparent')") }
            }
            Panel.Size -> PanelBox {
                SIZES.forEach { (label, px) ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.r))
                            .background(Color(0xFFF1F4F7))
                            .clickable { js("N.size($px)") }
                            .padding(horizontal = 14.r, vertical = 8.r),
                    ) {
                        Text(label, style = TextStyle(fontFamily = Inter, fontSize = (px * 0.85f).rs, color = C.Ink))
                    }
                }
            }
            Panel.Align -> PanelBox {
                listOf("left" to "justifyLeft", "center" to "justifyCenter", "right" to "justifyRight", "justify" to "justifyFull").forEach { (a, cmd) ->
                    Box(
                        Modifier
                            .size(58.r)
                            .clip(RoundedCornerShape(12.r))
                            .background(if (st.align == a) OfficePurple.copy(alpha = 0.14f) else Color(0xFFF1F4F7))
                            .clickable { js("N.cmd('$cmd')") }
                            .padding(14.r),
                    ) { AlignIcon(a, if (st.align == a) OfficePurple else C.StatLabel) }
                }
            }
            Panel.Table -> TablePicker { r, c ->
                js("N.table($r, $c)")
                panel = Panel.None
            }
            Panel.Insert -> PanelBox {
                Chip("— লাইন") { js("N.hr()") }
                Chip("📄 নতুন পাতা") { js("N.pageBreak()") }
                Chip("🔗 লিংক") { linkOpen = true }
                Chip("📅 আজকের তারিখ") {
                    js("N.cmd('insertText', ${MiniJson.quote(SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date()))})")
                }
                Chip("🧹 ফরম্যাট মুছুন") { js("N.cmd('removeFormat')") }
            }
            Panel.Export -> PanelBox {
                Chip("📄 Word (.docx) সেভ") { export(pdf = false, share = false) }
                Chip("📕 PDF সেভ") { export(pdf = true, share = false) }
                Chip("📤 Word শেয়ার") { export(pdf = false, share = true) }
                Chip("📤 PDF শেয়ার") { export(pdf = true, share = true) }
            }
        }

        // main toolbar
        Box(Modifier.fillMaxWidth().height(1.r).background(C.FieldLine))
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFB))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.r, vertical = 8.r),
            horizontalArrangement = Arrangement.spacedBy(6.r),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            fun toggle(p: Panel) {
                panel = if (panel == p) Panel.None else p
            }
            StyleKey(styleLabel(st.kind), panel == Panel.Style) { toggle(Panel.Style) }
            Key("B", st.b, weight = FontWeight.Black) { js("N.cmd('bold')") }
            Key("I", st.i, italic = true) { js("N.cmd('italic')") }
            Key("U", st.u, decoration = TextDecoration.Underline) { js("N.cmd('underline')") }
            Key("S", st.s, decoration = TextDecoration.LineThrough) { js("N.cmd('strikeThrough')") }
            Key("🎨", panel == Panel.Color) { toggle(Panel.Color) }
            Key("🖍️", panel == Panel.Mark) { toggle(Panel.Mark) }
            Key("Aa", panel == Panel.Size) { toggle(Panel.Size) }
            Box(
                Modifier
                    .size(52.r)
                    .clip(RoundedCornerShape(12.r))
                    .background(if (panel == Panel.Align) OfficePurple.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable { toggle(Panel.Align) }
                    .padding(14.r),
            ) { AlignIcon(st.align, C.Ink) }
            Key("• তালিকা", st.list == "ul", wide = true) { js("N.cmd('insertUnorderedList')") }
            Key("1. তালিকা", st.list == "ol", wide = true) { js("N.cmd('insertOrderedList')") }
            Key("➡️", false) { js("N.cmd('indent')") }
            Key("⬅️", false) { js("N.cmd('outdent')") }
            TableKey(panel == Panel.Table) { toggle(Panel.Table) }
            Key("🖼️ ছবি", false, wide = true) { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            Key("➕ আরো", panel == Panel.Insert, wide = true) { toggle(Panel.Insert) }
        }
    }

    if (renameOpen) {
        TextDialog("ডকুমেন্টের নাম", title, "যেমন: ছুটির দরখাস্ত", "রাখুন", onDismiss = { renameOpen = false }) { name ->
            scope.launch {
                val t = withContext(Dispatchers.IO) {
                    WordStore.rename(context, id, name)
                    WordStore.title(context, id)
                }
                title = t
            }
        }
    }
    if (linkOpen) {
        TextDialog("লিংক দিন", "https://", "https://…", "বসান", onDismiss = { linkOpen = false }) { url ->
            val u = url.trim()
            if (u.length > 8) js("N.link(${MiniJson.quote(if (u.contains("://") || u.startsWith("mailto:")) u else "https://$u")})")
        }
    }
    saved?.let { p ->
        AlertDialog(
            onDismissRequest = { saved = null },
            containerColor = Color.White,
            title = { RText("✅ সেভ হয়েছে", 23, FontWeight.Bold, TGreen) },
            text = {
                Column {
                    RText(p.name, 19, FontWeight.Medium, C.Ink, maxLines = 2)
                    RText("Nirvor ফোল্ডার → Documents", 16, FontWeight.Normal, C.Hint)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    startSafe(context, Storage.openIntent(p.uri, p.mime))
                    saved = null
                }) { RText("খুলুন", 20, FontWeight.SemiBold, OfficePurple) }
            },
            dismissButton = {
                TextButton(onClick = {
                    startSafe(context, Storage.shareIntent(p.uri, p.mime))
                    saved = null
                }) { RText("📤 শেয়ার", 20, FontWeight.Medium, C.StatLabel) }
            },
        )
    }
}

private fun styleLabel(kind: String) = when (kind) {
    "title" -> "শিরোনাম"
    "h1" -> "হেডিং ১"
    "h2" -> "হেডিং ২"
    "h3" -> "হেডিং ৩"
    "quote" -> "উদ্ধৃতি"
    else -> "সাধারণ"
}

/** The paragraph-style key: current style name and a little arrow. */
@Composable
private fun StyleKey(label: String, open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(52.r)
            .clip(RoundedCornerShape(12.r))
            .background(if (open) OfficePurple.copy(alpha = 0.14f) else Color(0xFFEEF1F5))
            .clickable(onClick = onClick)
            .padding(start = 14.r, end = 8.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RText(label, 18, FontWeight.SemiBold, if (open) OfficePurple else C.Ink)
        GlyphIcon(Glyph.ChevronDown, if (open) OfficePurple else C.StatLabel, Modifier.size(26.r).padding(4.r), strokeWidth = 2f)
    }
}

/** Table key with a small drawn grid. */
@Composable
private fun TableKey(open: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .height(52.r)
            .clip(RoundedCornerShape(12.r))
            .background(if (open) OfficePurple.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val col = if (open) OfficePurple else C.Ink
        Canvas(Modifier.size(22.r)) {
            val sw = size.width * 0.09f
            drawRect(col, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            for (k in 1..2) {
                drawLine(col, Offset(size.width * k / 3, 0f), Offset(size.width * k / 3, size.height), sw)
                drawLine(col, Offset(0f, size.height * k / 3), Offset(size.width, size.height * k / 3), sw)
            }
        }
        Spacer(Modifier.width(8.r))
        RText("টেবিল", 18, FontWeight.SemiBold, col)
    }
}

@Composable
private fun TopIcon(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.r)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { RText(label, 21, FontWeight.Normal, C.Ink) }
}

/** One toolbar key; green-tinted when that style is on. */
@Composable
private fun Key(
    label: String,
    active: Boolean,
    wide: Boolean = false,
    weight: FontWeight = FontWeight.SemiBold,
    italic: Boolean = false,
    decoration: TextDecoration? = null,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .height(52.r)
            .widthIn(min = 52.r)
            .clip(RoundedCornerShape(12.r))
            .background(if (active) OfficePurple.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (wide) 12.r else 8.r),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            maxLines = 1,
            style = TextStyle(
                fontFamily = Inter,
                fontSize = 19.rs,
                fontWeight = weight,
                fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = decoration,
                color = if (active) OfficePurple else C.Ink,
            ),
        )
    }
}

@Composable
private fun PanelBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(1.r).background(C.FieldLine))
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.r, vertical = 12.r),
        horizontalArrangement = Arrangement.spacedBy(10.r),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun ContextRow(items: List<Pair<String, String>>, onPick: (String) -> Unit) {
    PanelBox {
        items.forEach { (label, key) -> Chip(label) { onPick(key) } }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    RText(
        label,
        16,
        FontWeight.Medium,
        C.Ink,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFF1F4F7))
            .border(1.r, Color(0xFFE1E7ED), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.r, vertical = 10.r),
    )
}

@Composable
private fun Swatch(color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.r)
            .clip(CircleShape)
            .background(color)
            .border(1.5.r, Color(0x22000000), CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun StyleChoice(label: String, size: Int, weight: FontWeight, color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.r))
            .background(if (selected) OfficePurple.copy(alpha = 0.12f) else Color(0xFFF6F8FA))
            .border(if (selected) 1.6.r else 1.r, if (selected) OfficePurple else Color(0xFFE6EBF0), RoundedCornerShape(12.r))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.r, vertical = 8.r),
    ) { RText(label, size, weight, color) }
}

/** Four little lines showing left / centre / right / justified text. */
@Composable
private fun AlignIcon(align: String, color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val widths = listOf(1f, 0.62f, 0.86f, 0.5f)
        widths.forEachIndexed { i, f ->
            val w = if (align == "justify" && i < 3) size.width else size.width * f
            val y = size.height * (0.12f + i * 0.25f)
            val x0 = when (align) {
                "center" -> (size.width - w) / 2
                "right" -> size.width - w
                else -> 0f
            }
            drawLine(color, Offset(x0, y), Offset(x0 + w, y), size.height * 0.1f, cap = StrokeCap.Round)
        }
    }
}

/** Tap a square to choose rows × columns, then place the table. */
@Composable
private fun TablePicker(onPick: (Int, Int) -> Unit) {
    var rows by remember { mutableIntStateOf(3) }
    var cols by remember { mutableIntStateOf(3) }
    Box(Modifier.fillMaxWidth().height(1.r).background(C.FieldLine))
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.r, vertical = 12.r),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(5.r)) {
                for (r in 1..6) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.r)) {
                        for (c in 1..7) {
                            val on = r <= rows && c <= cols
                            Box(
                                Modifier
                                    .size(30.r, 24.r)
                                    .clip(RoundedCornerShape(4.r))
                                    .background(if (on) OfficePurple.copy(alpha = 0.28f) else Color(0xFFF1F4F7))
                                    .border(1.r, if (on) OfficePurple else Color(0xFFDCE4EA), RoundedCornerShape(4.r))
                                    .clickable {
                                        rows = r
                                        cols = c
                                    },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(18.r))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                RText("$rows × $cols", 30, FontWeight.Bold, OfficePurple)
                RText("সারি × কলাম", 15, FontWeight.Normal, C.Hint)
                Spacer(Modifier.height(12.r))
                RoundButton("টেবিল বসান", OfficePurple, Modifier.fillMaxWidth()) { onPick(rows, cols) }
            }
        }
    }
}
