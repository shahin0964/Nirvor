package com.nirvor.app.ui.tools.office

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.downloader.Storage
import com.nirvor.app.office.Block
import com.nirvor.app.office.Doc
import com.nirvor.app.office.DocxWriter
import com.nirvor.app.office.HtmlDoc
import com.nirvor.app.office.PageBreak
import com.nirvor.app.office.PageText
import com.nirvor.app.office.Para
import com.nirvor.app.office.ParaKind
import com.nirvor.app.office.PdfPages
import com.nirvor.app.office.PdfTools
import com.nirvor.app.office.Picture
import com.nirvor.app.office.SheetData
import com.nirvor.app.office.TWord
import com.nirvor.app.office.Table
import com.nirvor.app.office.WordStore
import com.nirvor.app.office.XlsxWriter
import com.nirvor.app.tools.Ocr
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.ToolScaffold
import com.nirvor.app.ui.tools.camera.OcrDataGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

// ================================================================ shared: an opened PDF

/** A PDF picked by the user, copied into the app, with its page pictures. */
internal class OpenPdf(val file: File, val name: String, val pages: PdfPages) {
    val thumbs = ThumbCache(pages)
    val title: String get() = name.substringBeforeLast('.').ifBlank { name }
}

/** Copies and opens a picked PDF; null (with a message) when it cannot be read. */
internal fun openPdf(context: Context, uri: android.net.Uri): OpenPdf? {
    val name = displayName(context, uri)
    val f = copyIn(context, uri, name) ?: return null
    return try {
        OpenPdf(f, name, PdfPages(f))
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }
}

/** How scanned pages (pictures of text, no text layer) are read. */
internal enum class OcrMode { Off, Auto, Always }

/** Words of each chosen page: from the PDF's text, or read from the page picture (OCR). */
internal suspend fun readPages(
    context: Context,
    pdf: OpenPdf,
    pages: List<Int>,
    ocr: OcrMode,
    onProgress: (Float) -> Unit,
): List<Pair<List<TWord>, Float>> = withContext(Dispatchers.Default) {
    val layer = if (ocr == OcrMode.Always) {
        null
    } else {
        try {
            PdfTools.words(context, pdf.file) { done, total -> onProgress(0.5f * done / total.coerceAtLeast(1)) }
        } catch (e: Exception) {
            null
        }
    }
    val canOcr = ocr != OcrMode.Off && Ocr.ready(context)
    pages.mapIndexed { k, i ->
        val fromLayer = layer?.getOrNull(i)
        val result = if (fromLayer != null && (fromLayer.words.size >= 3 || !canOcr)) {
            fromLayer.words to fromLayer.width
        } else if (canOcr) {
            val bmp = pdf.pages.render(i, 1800)
            val read = bmp?.let { Ocr.read(context, it, "ben+eng") }
            val words = read?.words?.map { b -> TWord(b.text, b.rect.left.toFloat(), b.rect.top.toFloat(), b.rect.right.toFloat(), b.rect.bottom.toFloat()) }.orEmpty()
            val w = bmp?.width?.toFloat() ?: 1f
            bmp?.recycle()
            words to w
        } else {
            emptyList<TWord>() to 595f
        }
        onProgress(0.5f + 0.5f * (k + 1) / pages.size)
        result
    }
}

/** Page pictures in a row; tap to choose pages when [selected] is given. */
@Composable
internal fun PageStrip(pdf: OpenPdf, selected: List<Boolean>? = null, onToggle: (Int) -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.r),
    ) {
        for (i in 0 until pdf.pages.count) {
            val on = selected?.getOrNull(i) ?: true
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    PageThumb(
                        pdf.thumbs,
                        i,
                        Modifier
                            .width(104.r)
                            .paper()
                            .clip(RoundedCornerShape(8.r))
                            .border(if (on && selected != null) 3.r else 1.r, if (on && selected != null) OfficePurple else C.FieldLine, RoundedCornerShape(8.r))
                            .clickable(enabled = selected != null) { onToggle(i) },
                    )
                    if (selected != null) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.r)
                                .size(26.r)
                                .clip(CircleShape)
                                .background(if (on) OfficePurple else Color(0xCCFFFFFF))
                                .border(1.5.r, if (on) OfficePurple else C.Hint, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) RText("✓", 15, FontWeight.Bold, Color.White) }
                    }
                }
                Spacer(Modifier.height(4.r))
                RText("${i + 1}", 14, FontWeight.Medium, C.StatLabel)
            }
        }
    }
}

/** Two big choices side by side. */
@Composable
internal fun BigChoice(emoji: String, title: String, sub: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.r)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) OfficePurple.copy(alpha = 0.08f) else Color.White)
            .border(if (selected) 2.r else 1.r, if (selected) OfficePurple else C.FieldLine, shape)
            .clickable(onClick = onClick)
            .padding(14.r),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RText(emoji, 30, FontWeight.Normal, C.Ink)
        Spacer(Modifier.height(6.r))
        RText(title, 17, FontWeight.SemiBold, if (selected) OfficePurple else C.Ink, maxLines = 2, align = TextAlign.Center)
        Spacer(Modifier.height(2.r))
        RText(sub, 14, FontWeight.Normal, C.StatLabel, maxLines = 3, align = TextAlign.Center, lineHeight = 19)
    }
}

/** "Scanned PDF? read it with OCR" switch, with the one-time language download when needed. */
@Composable
internal fun OcrSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    ListCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                RText("🔤 স্ক্যান করা PDF (ছবির লেখা)", 18, FontWeight.SemiBold, C.Ink)
                RText("লেখা না পেলে ছবি থেকে পড়বে (বাংলা + English)", 15, FontWeight.Normal, C.StatLabel, maxLines = 2)
            }
            Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = OfficePurple))
        }
    }
    if (on) OcrDataGate { }
}

// ================================================================ PDF → Word

/** PDF → Word: editable text (paragraphs, headings, tables) or each page as an exact picture. */
@Composable
fun PdfToWordTool(onBack: () -> Unit, onOpenWord: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdf by remember { mutableStateOf<OpenPdf?>(null) }
    var asPictures by remember { mutableStateOf(false) }
    var ocr by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var doc by remember { mutableStateOf<Doc?>(null) }
    var saved by remember { mutableStateOf<Storage.Published?>(null) }

    DisposableEffect(pdf) {
        val p = pdf
        onDispose { p?.pages?.close() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val p = withContext(Dispatchers.IO) { openPdf(context, uri) }
                if (p == null) Toast.makeText(context, "PDF-টা খোলা গেল না (পাসওয়ার্ড দেওয়া থাকলে খোলা যায় না)", Toast.LENGTH_LONG).show()
                pdf = p ?: pdf
                doc = null
                saved = null
            }
        }
    }

    ToolScaffold("PDF → Word", "PDF থেকে এডিট করা যায় এমন Word", onBack) {
        val p = pdf
        if (p == null) {
            PickCard("📕", "PDF ফাইল বাছুন", "যে PDF-টা Word বানাতে চান", "PDF বাছুন") { picker.launch(arrayOf(MIME_PDF)) }
            return@ToolScaffold
        }
        FileRow("📕", p.name, "${p.pages.count} পাতা", onChange = { picker.launch(arrayOf(MIME_PDF)) })
        PageStrip(p)
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            BigChoice("✍️", "এডিট করা যায়", "লেখা, হেডিং, তালিকা আর টেবিল আলাদা হয়ে আসবে", !asPictures, Modifier.weight(1f)) {
                asPictures = false
                doc = null
            }
            BigChoice("🖼️", "দেখতে হুবহু", "প্রতিটা পাতা ছবি হয়ে Word-এ বসবে", asPictures, Modifier.weight(1f)) {
                asPictures = true
                doc = null
            }
        }
        if (!asPictures) OcrSwitch(ocr) {
            ocr = it
            doc = null
        }
        if (busy) {
            BusyCard(if (asPictures) "পাতাগুলো ছবি করছি…" else "লেখা পড়ছি… ${(progress * 100).toInt()}%", progress)
            return@ToolScaffold
        }
        val d = doc
        if (d == null) {
            RoundButton("📝 Word বানান", OfficePurple, Modifier.fillMaxWidth()) {
                busy = true
                progress = 0f
                scope.launch {
                    val made = try {
                        if (asPictures) {
                            withContext(Dispatchers.Default) {
                                val blocks = mutableListOf<Block>()
                                for (i in 0 until p.pages.count) {
                                    if (i > 0) blocks.add(PageBreak)
                                    val bmp = p.pages.render(i, 1240) ?: continue
                                    val out = ByteArrayOutputStream()
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                    blocks.add(Picture(out.toByteArray(), "image/jpeg", DocxWriter.CONTENT_W_PT, DocxWriter.CONTENT_W_PT * bmp.height / bmp.width))
                                    bmp.recycle()
                                    progress = (i + 1f) / p.pages.count
                                }
                                Doc(blocks, p.title)
                            }
                        } else {
                            val pages = readPages(context, p, (0 until p.pages.count).toList(), if (ocr) OcrMode.Auto else OcrMode.Off) { progress = it }
                            val blocks = PageText.toBlocks(pages.map { PageText.lines(it.first) }, pages.map { it.second })
                            Doc(blocks, p.title)
                        }
                    } catch (e: Exception) {
                        null
                    }
                    busy = false
                    if (made == null) {
                        Toast.makeText(context, "PDF পড়া গেল না", Toast.LENGTH_SHORT).show()
                    } else {
                        doc = made
                        if (!asPictures && made.blocks.none { it is Para && it.text.isNotBlank() || it is Table }) {
                            Toast.makeText(context, "এই PDF-এ লেখা পাওয়া যায়নি — স্ক্যান করা হলে ওপরের OCR চালু করুন", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            return@ToolScaffold
        }

        // preview of what came out
        ListCard {
            RText("👀 Word-এ যা যাবে", 19, FontWeight.SemiBold, C.Ink)
            Spacer(Modifier.height(10.r))
            DocPreview(d)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            RoundButton("💾 Word সেভ", TGreen, Modifier.weight(1f)) {
                scope.launch {
                    saved = withContext(Dispatchers.IO) {
                        try {
                            val f = File(File(context.cacheDir, "office").apply { mkdirs() }, "${WordStore.fileName(p.title)}.docx")
                            f.outputStream().use { DocxWriter.write(d, it) }
                            publish(context, f, MIME_DOCX)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (saved == null) Toast.makeText(context, "সেভ করা গেল না", Toast.LENGTH_SHORT).show()
                }
            }
            RoundButton("✏️ এডিট করুন", OfficePurple, Modifier.weight(1f), filled = false) {
                scope.launch {
                    val id = withContext(Dispatchers.IO) { WordStore.create(context, p.title, HtmlDoc.toHtml(d)) }
                    WordStore.pendingOpen = id
                    onOpenWord()
                }
            }
        }
        saved?.let { SavedCard(it) }
    }
}

/** A light, read-only look at a document: headings, paragraphs, tables, pictures. */
@Composable
internal fun DocPreview(d: Doc, maxBlocks: Int = 40) {
    Column(verticalArrangement = Arrangement.spacedBy(8.r)) {
        var page = 1
        d.blocks.take(maxBlocks).forEach { b ->
            when (b) {
                is Para -> if (b.text.isNotBlank()) {
                    val (size, weight, color) = when (b.kind) {
                        ParaKind.Title, ParaKind.H1 -> Triple(22, FontWeight.Bold, Color(0xFF0A8F56))
                        ParaKind.H2 -> Triple(19, FontWeight.Bold, C.Ink)
                        ParaKind.H3 -> Triple(17, FontWeight.SemiBold, C.StatLabel)
                        else -> Triple(16, if (b.runs.all { it.bold }) FontWeight.SemiBold else FontWeight.Normal, C.Ink)
                    }
                    RText(
                        (if (b.list != com.nirvor.app.office.ListKind.None) "•  " else "") + b.text,
                        size,
                        weight,
                        color,
                        maxLines = 8,
                        lineHeight = size * 1.45f,
                        align = when (b.align) {
                            com.nirvor.app.office.Align.Center -> TextAlign.Center
                            com.nirvor.app.office.Align.Right -> TextAlign.End
                            else -> TextAlign.Start
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is Table -> TablePreview(b.rows.map { r -> r.map { c -> c.paras.joinToString(" ") { it.text } } }, b.header, maxRows = 12)
                is Picture -> RText("🖼️ পাতা $page (ছবি)", 15, FontWeight.Medium, C.Hint)
                PageBreak -> {
                    page++
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).height(1.r).background(C.FieldLine))
                        RText("  পাতা $page  ", 13, FontWeight.Medium, C.Hint)
                        Box(Modifier.weight(1f).height(1.r).background(C.FieldLine))
                    }
                }
                else -> Box(Modifier.fillMaxWidth().height(1.r).background(C.FieldLine))
            }
        }
        if (d.blocks.size > maxBlocks) RText("… আরও ${d.blocks.size - maxBlocks} অংশ", 15, FontWeight.Normal, C.Hint)
    }
}

/** A small grid of cells (scrolls sideways when wide). */
@Composable
internal fun TablePreview(rows: List<List<String>>, header: Boolean, maxRows: Int = 30) {
    val cols = rows.maxOfOrNull { it.size } ?: 0
    if (cols == 0) return
    val widths = (0 until cols).map { c ->
        (rows.take(maxRows).maxOfOrNull { r -> r.getOrNull(c)?.length ?: 0 } ?: 0).coerceIn(3, 22) * 9 + 20
    }
    Column(
        Modifier
            .horizontalScroll(rememberScrollState())
            .border(1.r, Color(0xFFC9D2DC), RoundedCornerShape(6.r)),
    ) {
        rows.take(maxRows).forEachIndexed { ri, r ->
            Row(Modifier.background(if (ri == 0 && header) Color(0xFFE3F6EC) else if (ri % 2 == 0) Color.White else Color(0xFFF8FAFB))) {
                for (c in 0 until cols) {
                    RText(
                        r.getOrNull(c).orEmpty(),
                        14,
                        if (ri == 0 && header) FontWeight.SemiBold else FontWeight.Normal,
                        C.Ink,
                        Modifier
                            .width(widths[c].r)
                            .border(0.5.r, Color(0xFFDCE4EA))
                            .padding(horizontal = 6.r, vertical = 5.r),
                        maxLines = 2,
                    )
                }
            }
        }
    }
    if (rows.size > maxRows) RText("… মোট ${rows.size} সারি", 14, FontWeight.Normal, C.Hint)
}

// ================================================================ PDF → Excel

/** PDF → Excel: finds the columns on each chosen page and shows the table before saving. */
@Composable
fun PdfToExcelTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdf by remember { mutableStateOf<OpenPdf?>(null) }
    val chosen = remember { mutableStateListOf<Boolean>() }
    var onePerPage by remember { mutableStateOf(false) }
    var header by remember { mutableStateOf(true) }
    var ocr by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var sheets by remember { mutableStateOf<List<SheetData>?>(null) }
    var shown by remember { mutableIntStateOf(0) }
    var saved by remember { mutableStateOf<Storage.Published?>(null) }

    DisposableEffect(pdf) {
        val p = pdf
        onDispose { p?.pages?.close() }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val p = withContext(Dispatchers.IO) { openPdf(context, uri) }
                if (p == null) {
                    Toast.makeText(context, "PDF-টা খোলা গেল না (পাসওয়ার্ড দেওয়া থাকলে খোলা যায় না)", Toast.LENGTH_LONG).show()
                } else {
                    pdf = p
                    chosen.clear()
                    repeat(p.pages.count) { chosen.add(true) }
                    sheets = null
                    saved = null
                }
            }
        }
    }

    ToolScaffold("PDF → Excel", "PDF-এর টেবিল থেকে Excel", onBack) {
        val p = pdf
        if (p == null) {
            PickCard("📊", "PDF ফাইল বাছুন", "ব্যাংক স্টেটমেন্ট, বিল, রিপোর্ট — যে PDF-এ টেবিল আছে", "PDF বাছুন") { picker.launch(arrayOf(MIME_PDF)) }
            return@ToolScaffold
        }
        FileRow("📕", p.name, "${chosen.count { it }} / ${p.pages.count} পাতা বাছাই", onChange = { picker.launch(arrayOf(MIME_PDF)) })
        RText("যে পাতাগুলো নেবেন সেগুলোতে টিক দিন", 16, FontWeight.Normal, C.StatLabel)
        PageStrip(p, chosen) { i ->
            chosen[i] = !chosen[i]
            sheets = null
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
            ChoiceChip("সব এক শিটে", !onePerPage, OfficePurple) {
                onePerPage = false
                sheets = null
            }
            ChoiceChip("প্রতি পাতা আলাদা শিট", onePerPage, OfficePurple) {
                onePerPage = true
                sheets = null
            }
        }
        OcrSwitch(ocr) {
            ocr = it
            sheets = null
        }
        if (busy) {
            BusyCard("টেবিল খুঁজছি… ${(progress * 100).toInt()}%", progress)
            return@ToolScaffold
        }
        val s = sheets
        if (s == null) {
            RoundButton("🔍 টেবিল খুঁজুন", OfficePurple, Modifier.fillMaxWidth()) {
                val pages = chosen.indices.filter { chosen[it] }
                if (pages.isEmpty()) {
                    Toast.makeText(context, "অন্তত একটা পাতা বাছুন", Toast.LENGTH_SHORT).show()
                    return@RoundButton
                }
                busy = true
                progress = 0f
                scope.launch {
                    val result = try {
                        val read = readPages(context, p, pages, if (ocr) OcrMode.Auto else OcrMode.Off) { progress = it }
                        withContext(Dispatchers.Default) {
                            val grids = read.map { PageText.table(it.first) }
                            if (onePerPage) {
                                grids.mapIndexed { k, g -> SheetData("পাতা ${pages[k] + 1}", g) }
                            } else {
                                listOf(SheetData(p.title, grids.flatten()))
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                    busy = false
                    sheets = result
                    shown = 0
                    if (result == null || result.all { it.rows.isEmpty() }) {
                        Toast.makeText(context, "কোনো লেখা পাওয়া যায়নি — স্ক্যান করা PDF হলে OCR চালু করুন", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return@ToolScaffold
        }

        ListCard {
            RText("👀 যে টেবিল পাওয়া গেছে", 19, FontWeight.SemiBold, C.Ink)
            if (s.size > 1) {
                Spacer(Modifier.height(10.r))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.r)) {
                    s.forEachIndexed { i, sh -> ChoiceChip(sh.name, shown == i, OfficePurple) { shown = i } }
                }
            }
            Spacer(Modifier.height(10.r))
            val sh = s.getOrNull(shown)
            if (sh == null || sh.rows.isEmpty()) {
                RText("এই পাতায় কিছু পাওয়া যায়নি", 16, FontWeight.Normal, C.Hint)
            } else {
                RText("${sh.rows.size} সারি × ${sh.columns} কলাম", 15, FontWeight.Medium, C.StatLabel)
                Spacer(Modifier.height(8.r))
                TablePreview(sh.rows, header)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText("প্রথম সারি শিরোনাম (মোটা অক্ষরে)", 17, FontWeight.Medium, C.Ink, Modifier.weight(1f))
            Switch(header, { header = it }, colors = SwitchDefaults.colors(checkedTrackColor = OfficePurple))
        }
        RoundButton("💾 Excel (.xlsx) সেভ", TGreen, Modifier.fillMaxWidth()) {
            scope.launch {
                saved = withContext(Dispatchers.IO) {
                    try {
                        val f = File(File(context.cacheDir, "office").apply { mkdirs() }, "${WordStore.fileName(p.title)}.xlsx")
                        f.outputStream().use { XlsxWriter.write(s, it, header) }
                        publish(context, f, MIME_XLSX)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (saved == null) Toast.makeText(context, "সেভ করা গেল না", Toast.LENGTH_SHORT).show()
            }
        }
        saved?.let { SavedCard(it) }
    }
}
