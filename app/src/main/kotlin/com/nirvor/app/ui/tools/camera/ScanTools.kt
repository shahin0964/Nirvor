package com.nirvor.app.ui.tools.camera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.downloader.Storage
import com.nirvor.app.money.MoneyCategories
import com.nirvor.app.money.MoneyType
import com.nirvor.app.office.PaperSize
import com.nirvor.app.office.PdfTools
import com.nirvor.app.tools.DocScan
import com.nirvor.app.tools.Ocr
import com.nirvor.app.tools.ReceiptParser
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.money.CategoryChip
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.sheets.DayPicker
import com.nirvor.app.ui.sheets.NField
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TBlue
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.ToolNumberField
import com.nirvor.app.ui.tools.ToolScaffold
import com.nirvor.app.ui.tools.fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private fun stamp(): String = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

private fun startSafe(context: android.content.Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "খোলার মতো অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
    }
}

/** A picture with [boxes] (picture pixels) drawn on top; [highlight] boxes in green. */
@Composable
private fun PictureWithBoxes(bmp: Bitmap, boxes: List<android.graphics.Rect>, highlight: List<android.graphics.Rect>, modifier: Modifier) {
    val image = remember(bmp) { bmp.asImageBitmap() }
    Box(modifier) {
        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            val s = minOf(size.width / bmp.width, size.height / bmp.height)
            val dx = (size.width - bmp.width * s) / 2f
            val dy = (size.height - bmp.height * s) / 2f
            fun draw(r: android.graphics.Rect, color: Color, w: Float) {
                drawRect(
                    color,
                    Offset(r.left * s + dx, r.top * s + dy),
                    Size(r.width() * s, r.height() * s),
                    style = Stroke(w),
                )
            }
            boxes.forEach { draw(it, Color(0x99168FFB), 2f) }
            highlight.forEach {
                drawRect(Color(0x3334D399), Offset(it.left * s + dx, it.top * s + dy), Size(it.width() * s, it.height() * s))
                draw(it, Color(0xFF0BA765), 4f)
            }
        }
    }
}

// ================================================================ Document scanner

private class ScanPage(val raw: Bitmap, val mode: Int, val shown: Bitmap)

private val FILTERS = listOf("আসল", "সাদা-কালো", "ঝকঝকে", "ধূসর")

/** Photo → corners found by itself → straight, clean page. Many pages → one PDF. */
@Composable
fun DocScannerTool(onBack: () -> Unit) {
    CameraGate("Document Scanner", "ডকুমেন্ট স্ক্যান", onBack) {
        val scope = rememberCoroutineScope()
        val pages = remember { mutableStateListOf<ScanPage>() }
        var capturing by remember { mutableStateOf(true) }
        var reading by remember { mutableStateOf<Bitmap?>(null) }
        val r = reading
        when {
            r != null -> OcrResultView(r, "Document Scanner") { reading = null }
            capturing -> CaptureAndCrop(
                "Document Scanner",
                "কাগজটা দাগের ভেতরে এনে ছবি তুলুন — কোণগুলো নিজে খুঁজে নেবে",
                findPaper = true,
                onBack = { if (pages.isEmpty()) onBack() else capturing = false },
                footer = {
                    if (pages.isNotEmpty()) PanelButton("📄 ${pages.size} পাতা") { capturing = false }
                },
            ) { bmp ->
                scope.launch {
                    val shown = withContext(Dispatchers.Default) { DocScan.filter(bmp, 1) }
                    pages.add(ScanPage(bmp, 1, shown))
                    capturing = false
                }
            }
            else -> DocReview(pages, onAdd = { capturing = true }, onRead = { reading = it }, onBack = onBack)
        }
    }
}

@Composable
private fun DocReview(
    pages: MutableList<ScanPage>,
    onAdd: () -> Unit,
    onRead: (Bitmap) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sel by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Storage.Published?>(null) }
    if (pages.isEmpty()) {
        LaunchedEffect(Unit) { onAdd() }
        return
    }
    val index = sel.coerceIn(0, pages.size - 1)
    val page = pages[index]

    ToolScaffold("Document Scanner", "${pages.size} পাতা", onBack) {
        ListCard {
            val ratio = page.shown.width.toFloat() / page.shown.height
            Image(
                page.shown.asImageBitmap(),
                "page",
                Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.4f, 2f)).clip(RoundedCornerShape(10.r)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(12.r))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.r)) {
                FILTERS.forEachIndexed { i, label ->
                    ChoiceChip(label, page.mode == i) {
                        if (page.mode != i) {
                            scope.launch {
                                val shown = withContext(Dispatchers.Default) { DocScan.filter(page.raw, i) }
                                val at = pages.indexOf(page)
                                if (at >= 0) pages[at] = ScanPage(page.raw, i, shown)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.r)) {
            pages.forEachIndexed { i, p ->
                Box(
                    Modifier
                        .size(86.r, 116.r)
                        .clip(RoundedCornerShape(10.r))
                        .border(if (i == index) 3.r else 1.r, if (i == index) TGreen else C.FieldLine, RoundedCornerShape(10.r))
                        .clickable { sel = i },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Image(p.shown.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    RText(
                        "${i + 1}",
                        14,
                        FontWeight.Bold,
                        Color.White,
                        Modifier.background(Color(0x99000000), RoundedCornerShape(50)).padding(horizontal = 8.r),
                    )
                }
            }
            Box(
                Modifier
                    .size(86.r, 116.r)
                    .clip(RoundedCornerShape(10.r))
                    .background(Color(0xFFE3F6EC))
                    .clickable(onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                RText("+", 34, FontWeight.Bold, TGreen)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            RoundButton(if (busy) "…" else "📄 PDF সেভ", TGreen, Modifier.weight(1f)) {
                if (busy) return@RoundButton
                busy = true
                val shots = pages.map { it.shown }
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val file = File(context.cacheDir, "Scan_${stamp()}.pdf")
                            PdfTools.images(context, shots.size, { shots[it] }, PaperSize.A4, 0f, file, recycle = false)
                            Storage.publish(context, file, "application/pdf")
                        } catch (e: Exception) {
                            null
                        }
                    }
                    busy = false
                    saved = result
                    Toast.makeText(context, if (result != null) "PDF সেভ হয়েছে (Documents)" else "সেভ করা গেল না", Toast.LENGTH_SHORT).show()
                }
            }
            RoundButton("➕ আরেক পাতা", TBlue, Modifier.weight(1f), filled = false, onClick = onAdd)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            RoundButton("🔤 লেখা বের করুন", C.StatLabel, Modifier.weight(1f), filled = false) { onRead(page.shown) }
            RoundButton("🖼️ ছবি সেভ", C.StatLabel, Modifier.weight(1f), filled = false) {
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { saveBitmap(context, page.shown, "Scan") }
                    Toast.makeText(context, if (ok) "ছবি সেভ হয়েছে (Photos)" else "সেভ করা গেল না", Toast.LENGTH_SHORT).show()
                }
            }
        }
        RoundButton("🗑 এই পাতা বাদ দিন", C.Danger, Modifier.fillMaxWidth(), filled = false) {
            pages.removeAt(index)
            sel = (index - 1).coerceAtLeast(0)
        }
        saved?.let { p ->
            ListCard {
                RText("✅ ${p.name}", 19, FontWeight.SemiBold, C.Ink, maxLines = 2)
                Spacer(Modifier.height(10.r))
                Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
                    RoundButton("খুলুন", TGreen, Modifier.weight(1f)) { startSafe(context, Storage.openIntent(p.uri, p.mime)) }
                    RoundButton("শেয়ার", TBlue, Modifier.weight(1f), filled = false) {
                        startSafe(context, Storage.shareIntent(p.uri, p.mime))
                    }
                }
            }
        }
    }
}

// ================================================================ OCR

private val OCR_LANGS = listOf("ben+eng" to "বাংলা + English", "eng" to "শুধু English", "ben" to "শুধু বাংলা")

@Composable
fun OcrTool(onBack: () -> Unit) {
    CameraGate("OCR", "ছবি থেকে লেখা", onBack) {
        var img by remember { mutableStateOf<Bitmap?>(null) }
        val i = img
        if (i == null) {
            CaptureAndCrop("OCR", "যে লেখাটা পড়তে চান সেটার ছবি তুলুন, তারপর কোণ টেনে শুধু লেখাটুকু রাখুন", findPaper = false, onBack = onBack) { img = it }
        } else {
            OcrResultView(i, "OCR") { img = null }
        }
    }
}

/** Reads the text in [bmp]: words boxed on the picture, the text editable, copy / share / save. */
@Composable
internal fun OcrResultView(bmp: Bitmap, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lang by remember { mutableStateOf("ben+eng") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Ocr.Result?>(null) }
    var text by remember { mutableStateOf("") }
    var showBoxes by remember { mutableStateOf(true) }

    ToolScaffold(title, "ছবি থেকে লেখা", onBack) {
        OcrDataGate {
            LaunchedEffect(bmp, lang) {
                busy = true
                val r = Ocr.read(context, bmp, lang)
                result = r
                text = r?.text ?: ""
                busy = false
                if (r == null) Toast.makeText(context, "লেখা পড়া গেল না", Toast.LENGTH_SHORT).show()
            }
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(16.r)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.r)) {
                    OCR_LANGS.forEach { (code, label) -> ChoiceChip(label, lang == code) { lang = code } }
                }
                ListCard {
                    val ratio = bmp.width.toFloat() / bmp.height
                    PictureWithBoxes(
                        bmp,
                        if (showBoxes) result?.words?.map { it.rect } ?: emptyList() else emptyList(),
                        emptyList(),
                        Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.4f, 2f)),
                    )
                    Spacer(Modifier.height(10.r))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RText(
                            if (busy) "পড়ছি…" else "${result?.words?.size ?: 0} টা শব্দ পাওয়া গেছে",
                            17,
                            FontWeight.Medium,
                            C.StatLabel,
                            Modifier.weight(1f),
                        )
                        if (busy) CircularProgressIndicator(Modifier.size(28.r), color = TGreen, strokeWidth = 3.r)
                        ChoiceChip("বাক্স", showBoxes) { showBoxes = !showBoxes }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.r),
                    textStyle = TextStyle(fontFamily = Inter, fontSize = 20.rs, color = C.Ink),
                    placeholder = { RText(if (busy) "পড়ছি…" else "কোনো লেখা পাওয়া যায়নি", 19, FontWeight.Normal, C.Hint) },
                    shape = RoundedCornerShape(18.r),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TGreen,
                        unfocusedBorderColor = C.FieldLine,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                    RoundButton("📋 কপি", TGreen, Modifier.weight(1f)) {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("text", text))
                        Toast.makeText(context, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                    RoundButton("📤 শেয়ার", TBlue, Modifier.weight(1f), filled = false) {
                        startSafe(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "শেয়ার"))
                    }
                    RoundButton("💾 সেভ", C.StatLabel, Modifier.weight(1f), filled = false) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                try {
                                    val f = File(context.cacheDir, "Text_${stamp()}.txt")
                                    f.writeText(text)
                                    Storage.publish(context, f, "text/plain")
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            Toast.makeText(context, if (ok) "লেখা সেভ হয়েছে (Documents)" else "সেভ করা গেল না", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

// ================================================================ Receipt → expense

@Composable
fun ReceiptTool(vm: MainViewModel, onBack: () -> Unit) {
    CameraGate("Receipt Scanner", "রসিদ থেকে খরচ", onBack) {
        var img by remember { mutableStateOf<Bitmap?>(null) }
        val i = img
        if (i == null) {
            CaptureAndCrop("Receipt Scanner", "দোকানের রসিদটা দাগের ভেতরে এনে ছবি তুলুন", findPaper = true, onBack = onBack) { img = it }
        } else {
            ReceiptForm(vm, i, onRetake = { img = null }, onDone = onBack)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReceiptForm(vm: MainViewModel, bmp: Bitmap, onRetake: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val custom by vm.customCategories.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<Ocr.Result?>(null) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var category by remember { mutableStateOf("bazar") }
    var totalLine by remember { mutableStateOf<String?>(null) }
    var showText by remember { mutableStateOf(false) }

    ToolScaffold("Receipt Scanner", "রসিদ থেকে খরচ", onRetake) {
        OcrDataGate {
            LaunchedEffect(bmp) {
                busy = true
                val r = Ocr.read(context, bmp, "ben+eng")
                result = r
                if (r != null) {
                    val p = ReceiptParser.parse(r.text)
                    p.total?.let { amount = fmt(it, 2).replace(",", "") }
                    p.shop?.let { note = it }
                    p.date?.let { if (!it.isAfter(LocalDate.now())) day = it }
                    totalLine = p.totalLine
                }
                busy = false
            }
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(16.r)) {
                ListCard {
                    val ratio = bmp.width.toFloat() / bmp.height
                    val tl = totalLine?.let { ReceiptParser.normalize(it).filter { c -> !c.isWhitespace() } }
                    val hl = result?.lines?.filter { b ->
                        tl != null && tl.isNotEmpty() && ReceiptParser.normalize(b.text).filter { !it.isWhitespace() }.let { it.contains(tl) || tl.contains(it) && it.length > 3 }
                    }?.map { it.rect } ?: emptyList()
                    PictureWithBoxes(bmp, emptyList(), hl, Modifier.fillMaxWidth().aspectRatio(ratio.coerceIn(0.3f, 2f)))
                    Spacer(Modifier.height(10.r))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RText(
                            when {
                                busy -> "রসিদ পড়ছি…"
                                hl.isNotEmpty() -> "✅ সবুজ দাগ দেওয়া লাইন থেকে মোট টাকা নেওয়া হয়েছে"
                                result != null -> "মোট টাকা খুঁজে না পেলে নিচে লিখে দিন"
                                else -> "রসিদ পড়া গেল না, নিচে নিজে লিখুন"
                            },
                            16,
                            FontWeight.Medium,
                            C.StatLabel,
                            Modifier.weight(1f),
                            maxLines = 2,
                        )
                        if (busy) CircularProgressIndicator(Modifier.size(28.r), color = TGreen, strokeWidth = 3.r)
                    }
                }
                ListCard {
                    ToolNumberField("মোট টাকা", amount, { amount = it }, "৳", keyboard = KeyboardType.Decimal)
                    Spacer(Modifier.height(14.r))
                    RText("ক্যাটাগরি", 17, FontWeight.Medium, C.StatLabel)
                    Spacer(Modifier.height(8.r))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.r), verticalArrangement = Arrangement.spacedBy(8.r)) {
                        MoneyCategories.list(MoneyType.Expense, custom).forEach { c ->
                            CategoryChip(c, category == c.key) { category = c.key }
                        }
                    }
                    Spacer(Modifier.height(14.r))
                    RText("দোকান / বিবরণ", 17, FontWeight.Medium, C.StatLabel)
                    Spacer(Modifier.height(8.r))
                    NField(note, { note = it }, "যেমন: স্বপ্ন, গুলশান")
                    Spacer(Modifier.height(14.r))
                    RText("তারিখ", 17, FontWeight.Medium, C.StatLabel)
                    Spacer(Modifier.height(8.r))
                    DayPicker(day) { day = it }
                }
                val value = amount.replace(",", "").toDoubleOrNull()
                RoundButton("💸 Money-তে খরচ হিসেবে যোগ করুন", TGreen, Modifier.fillMaxWidth()) {
                    if (value == null || value <= 0.0) {
                        Toast.makeText(context, "মোট টাকা লিখুন", Toast.LENGTH_SHORT).show()
                    } else {
                        vm.addExpense(value, category, note, day.toEpochDay())
                        Toast.makeText(context, "খরচ যোগ হয়েছে", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                }
                val r = result
                if (r != null && r.text.isNotBlank()) {
                    RText(
                        if (showText) "▲ পড়া লেখা লুকান" else "▼ রসিদ থেকে পড়া লেখা দেখুন",
                        17,
                        FontWeight.SemiBold,
                        TBlue,
                        Modifier.clickable { showText = !showText }.padding(vertical = 6.r),
                    )
                    if (showText) {
                        ListCard { RText(r.text, 16, FontWeight.Normal, C.Ink, maxLines = 80, lineHeight = 24) }
                    }
                }
            }
        }
    }
}
