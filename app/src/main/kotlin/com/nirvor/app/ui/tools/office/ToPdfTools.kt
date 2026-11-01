package com.nirvor.app.ui.tools.office

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import com.nirvor.app.downloader.Storage
import com.nirvor.app.office.Doc
import com.nirvor.app.office.DocPdf
import com.nirvor.app.office.DocxReader
import com.nirvor.app.office.PageSize
import com.nirvor.app.office.PdfPages
import com.nirvor.app.office.SheetData
import com.nirvor.app.office.WordDoc
import com.nirvor.app.office.WordStore
import com.nirvor.app.office.Csv
import com.nirvor.app.office.XlsxReader
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** The finished PDF's pages, shown before saving. */
@Composable
internal fun PdfResultPages(file: File, version: Int) {
    var pages by remember { mutableStateOf<ThumbCache?>(null) }
    DisposableEffect(file, version) {
        val p = try {
            ThumbCache(PdfPages(file))
        } catch (e: Exception) {
            null
        }
        pages = p
        onDispose { p?.pdf?.close() }
    }
    val p = pages ?: return
    RText("👀 PDF দেখতে যেমন হবে · ${p.pdf.count} পাতা", 17, FontWeight.SemiBold, C.Ink)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.r),
    ) {
        for (i in 0 until p.pdf.count) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PageThumb(
                    p,
                    i,
                    Modifier
                        .width(230.r)
                        .paper()
                        .clip(RoundedCornerShape(6.r))
                        .border(1.r, C.FieldLine, RoundedCornerShape(6.r)),
                    widthPx = 700,
                )
                Spacer(Modifier.height(4.r))
                RText("${i + 1}", 14, FontWeight.Medium, C.StatLabel)
            }
        }
    }
}

private class Margins(val label: String, val pt: Float)

private val MARGINS = listOf(Margins("সরু", 28f), Margins("স্বাভাবিক", 56.7f), Margins("চওড়া", 85f))

// ================================================================ Word → PDF

/** Word → PDF: a .docx / .txt from the phone or a document made in Office Word, previewed page by page. */
@Composable
fun WordToPdfTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var doc by remember { mutableStateOf<Doc?>(null) }
    var name by remember { mutableStateOf("") }
    var letter by remember { mutableStateOf(false) }
    var margin by remember { mutableIntStateOf(1) }
    var numbers by remember { mutableStateOf(true) }
    var out by remember { mutableStateOf<File?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Storage.Published?>(null) }
    var mine by remember { mutableStateOf<List<WordDoc>>(emptyList()) }
    LaunchedEffect(Unit) { mine = withContext(Dispatchers.IO) { WordStore.list(context) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val fileName = displayName(context, uri)
                val d = withContext(Dispatchers.IO) {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                        val title = fileName.substringBeforeLast('.')
                        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
                        if (ext == "docx" || (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte())) {
                            DocxReader.read(bytes, title)
                        } else if (ext == "txt" || ext == "md" || ext.isEmpty()) {
                            Doc.fromText(String(bytes, Charsets.UTF_8).removePrefix("﻿"), title)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                busy = false
                if (d == null) {
                    Toast.makeText(context, "এই ফাইলটা পড়া গেল না (.docx বা .txt দিন)", Toast.LENGTH_LONG).show()
                } else {
                    doc = d
                    name = fileName.substringBeforeLast('.')
                    saved = null
                }
            }
        }
    }

    // make the PDF again whenever the document or a setting changes
    LaunchedEffect(doc, letter, margin, numbers) {
        val d = doc ?: return@LaunchedEffect
        delay(150)
        busy = true
        val f = withContext(Dispatchers.IO) {
            try {
                val file = File(File(context.cacheDir, "office/preview").apply { mkdirs() }, "${WordStore.fileName(name)}.pdf")
                val opt = DocPdf.Options(
                    pageW = if (letter) PageSize.LETTER_W else PageSize.A4_W,
                    pageH = if (letter) PageSize.LETTER_H else PageSize.A4_H,
                    margin = MARGINS[margin].pt,
                    pageNumbers = numbers,
                )
                file.outputStream().use { DocPdf.write(d, it, opt) }
                file
            } catch (e: Exception) {
                null
            }
        }
        busy = false
        out = f
        version++
        saved = null
    }

    ToolScaffold("Word → PDF", "Word ফাইল থেকে PDF", onBack) {
        val d = doc
        if (d == null) {
            PickCard("📃", "Word ফাইল বাছুন", "ফোনের .docx বা .txt ফাইল", "ফাইল বাছুন") {
                picker.launch(arrayOf(MIME_DOCX, "text/plain", "application/octet-stream"))
            }
            if (mine.isNotEmpty()) {
                RText("অথবা Office Word-এ বানানো ডকুমেন্ট", 18, FontWeight.SemiBold, C.Ink)
                mine.take(8).forEach { w ->
                    ListCard(onClick = {
                        busy = true
                        scope.launch {
                            val html = withContext(Dispatchers.IO) { WordStore.load(context, w.id) }
                            val m = html?.let { editorModel(context, it, w.title) }
                            busy = false
                            if (m == null) {
                                Toast.makeText(context, "ডকুমেন্টটা পড়া গেল না", Toast.LENGTH_SHORT).show()
                            } else {
                                name = w.title
                                doc = m
                            }
                        }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RText("📝", 26, FontWeight.Normal, C.Ink)
                            Spacer(Modifier.width(12.r))
                            RText(w.title, 18, FontWeight.Medium, C.Ink, Modifier.weight(1f), maxLines = 2)
                        }
                    }
                }
            }
            if (busy) BusyCard("খুলছি…")
            return@ToolScaffold
        }
        FileRow("📃", name, "${d.blocks.size} অংশ", onChange = {
            doc = null
            out = null
        })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.r)) {
            ChoiceChip("A4", !letter, OfficePurple) { letter = false }
            ChoiceChip("Letter", letter, OfficePurple) { letter = true }
            MARGINS.forEachIndexed { i, m -> ChoiceChip("মার্জিন: ${m.label}", margin == i, OfficePurple) { margin = i } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText("নিচে পাতার নম্বর", 17, FontWeight.Medium, C.Ink, Modifier.weight(1f))
            Switch(numbers, { numbers = it }, colors = SwitchDefaults.colors(checkedTrackColor = OfficePurple))
        }
        val f = out
        if (busy || f == null) {
            BusyCard("PDF বানাচ্ছি…")
        } else {
            PdfResultPages(f, version)
            RoundButton("💾 PDF সেভ", TGreen, Modifier.fillMaxWidth()) {
                scope.launch {
                    saved = withContext(Dispatchers.IO) {
                        // the preview stays; a copy with the same name is saved
                        val copy = File(File(context.cacheDir, "office").apply { mkdirs() }, f.name)
                        f.copyTo(copy, overwrite = true)
                        publish(context, copy, MIME_PDF)
                    }
                    if (saved == null) Toast.makeText(context, "সেভ করা গেল না", Toast.LENGTH_SHORT).show()
                }
            }
        }
        saved?.let { SavedCard(it) }
    }
}

// ================================================================ Excel → PDF

/** Excel / CSV → PDF: sheet tabs, the table as it is, then pages with the header repeated. */
@Composable
fun ExcelToPdfTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheets by remember { mutableStateOf<List<SheetData>?>(null) }
    var name by remember { mutableStateOf("") }
    var shown by remember { mutableIntStateOf(0) }
    var allSheets by remember { mutableStateOf(true) }
    var orientation by remember { mutableIntStateOf(0) } // 0 auto, 1 portrait, 2 landscape
    var header by remember { mutableStateOf(true) }
    var grid by remember { mutableStateOf(true) }
    var out by remember { mutableStateOf<File?>(null) }
    var version by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Storage.Published?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val fileName = displayName(context, uri)
                val s = withContext(Dispatchers.IO) {
                    try {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
                        when {
                            ext == "xlsx" || (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) -> XlsxReader.read(bytes)
                            ext == "csv" || ext == "txt" || ext == "tsv" -> listOf(SheetData(fileName.substringBeforeLast('.'), Csv.read(String(bytes, Charsets.UTF_8))))
                            else -> null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                busy = false
                if (s == null || s.all { it.rows.isEmpty() }) {
                    Toast.makeText(context, "এই ফাইলটা পড়া গেল না (.xlsx বা .csv দিন)", Toast.LENGTH_LONG).show()
                } else {
                    sheets = s.filter { it.rows.isNotEmpty() }
                    name = fileName.substringBeforeLast('.')
                    shown = 0
                    saved = null
                }
            }
        }
    }

    LaunchedEffect(sheets, allSheets, shown, orientation, header, grid) {
        val list = sheets ?: return@LaunchedEffect
        delay(150)
        val use = if (allSheets) list else listOfNotNull(list.getOrNull(shown))
        val cols = use.maxOfOrNull { it.columns } ?: 0
        val landscape = when (orientation) {
            1 -> false
            2 -> true
            else -> cols > 6
        }
        busy = true
        val f = withContext(Dispatchers.IO) {
            try {
                val file = File(File(context.cacheDir, "office/preview").apply { mkdirs() }, "${WordStore.fileName(name)}.pdf")
                file.outputStream().use { DocPdf.sheets(use, it, landscape, header, grid) }
                file
            } catch (e: Exception) {
                null
            }
        }
        busy = false
        out = f
        version++
        saved = null
    }

    ToolScaffold("Excel → PDF", "Excel বা CSV থেকে PDF", onBack) {
        val list = sheets
        if (list == null) {
            PickCard("📑", "Excel ফাইল বাছুন", ".xlsx বা .csv ফাইল", "ফাইল বাছুন") {
                picker.launch(arrayOf(MIME_XLSX, "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"))
            }
            if (busy) BusyCard("খুলছি…")
            return@ToolScaffold
        }
        FileRow("📑", name, "${list.size} শিট", onChange = { picker.launch(arrayOf(MIME_XLSX, "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) })
        if (list.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.r)) {
                list.forEachIndexed { i, s -> ChoiceChip(s.name, shown == i, OfficePurple) { shown = i } }
            }
        }
        list.getOrNull(shown)?.let { s ->
            ListCard {
                RText("${s.name} · ${s.rows.size} সারি × ${s.columns} কলাম", 16, FontWeight.Medium, C.StatLabel)
                Spacer(Modifier.height(8.r))
                TablePreview(s.rows, header, maxRows = 12)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.r)) {
            if (list.size > 1) {
                ChoiceChip("সব শিট", allSheets, OfficePurple) { allSheets = true }
                ChoiceChip("শুধু এই শিট", !allSheets, OfficePurple) { allSheets = false }
            }
            ChoiceChip("নিজে বুঝে", orientation == 0, OfficePurple) { orientation = 0 }
            ChoiceChip("খাড়া পাতা", orientation == 1, OfficePurple) { orientation = 1 }
            ChoiceChip("আড়াআড়ি পাতা", orientation == 2, OfficePurple) { orientation = 2 }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText("প্রথম সারি শিরোনাম (প্রতি পাতায়)", 17, FontWeight.Medium, C.Ink, Modifier.weight(1f))
            Switch(header, { header = it }, colors = SwitchDefaults.colors(checkedTrackColor = OfficePurple))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText("ঘরের দাগ (গ্রিড)", 17, FontWeight.Medium, C.Ink, Modifier.weight(1f))
            Switch(grid, { grid = it }, colors = SwitchDefaults.colors(checkedTrackColor = OfficePurple))
        }
        val f = out
        if (busy || f == null) {
            BusyCard("PDF বানাচ্ছি…")
        } else {
            PdfResultPages(f, version)
            RoundButton("💾 PDF সেভ", TGreen, Modifier.fillMaxWidth()) {
                scope.launch {
                    saved = withContext(Dispatchers.IO) {
                        // the preview stays; a copy with the same name is saved
                        val copy = File(File(context.cacheDir, "office").apply { mkdirs() }, f.name)
                        f.copyTo(copy, overwrite = true)
                        publish(context, copy, MIME_PDF)
                    }
                    if (saved == null) Toast.makeText(context, "সেভ করা গেল না", Toast.LENGTH_SHORT).show()
                }
            }
        }
        saved?.let { SavedCard(it) }
    }
}
