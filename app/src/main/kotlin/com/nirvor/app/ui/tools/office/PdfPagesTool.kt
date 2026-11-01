package com.nirvor.app.ui.tools.office

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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
import com.nirvor.app.downloader.Storage
import com.nirvor.app.office.PdfTools
import com.nirvor.app.office.WordStore
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.TRed
import com.nirvor.app.ui.tools.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** PDF Merge / Split: join several PDFs in the order you set, or pick pages to keep, drop or turn. */
@Composable
fun PdfMergeSplitTool(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    ToolScaffold("PDF Merge / Split", "PDF জোড়া দিন বা ভাগ করুন", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
            ChoiceChip("🔗 জোড়া দিন", tab == 0, OfficePurple) { tab = 0 }
            ChoiceChip("✂️ ভাগ / পাতা বাছুন", tab == 1, OfficePurple) { tab = 1 }
        }
        if (tab == 0) MergePart() else SplitPart()
    }
}

@Composable
private fun MergePart() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember { mutableStateListOf<OpenPdf>() }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Storage.Published?>(null) }
    DisposableEffect(Unit) {
        onDispose { files.forEach { it.pages.close() } }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            scope.launch {
                val opened = withContext(Dispatchers.IO) { uris.map { openPdf(context, it) } }
                busy = false
                val bad = opened.count { it == null }
                files.addAll(opened.filterNotNull())
                saved = null
                if (bad > 0) Toast.makeText(context, "$bad টা PDF খোলা গেল না", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (files.isEmpty()) {
        PickCard("🔗", "PDF ফাইলগুলো বাছুন", "একসাথে কয়েকটা বাছতে পারেন, পরে সাজানো যাবে", "PDF বাছুন") { picker.launch(arrayOf(MIME_PDF)) }
        if (busy) BusyCard("খুলছি…")
        return
    }
    RText("${files.size} টা ফাইল · মোট ${files.sumOf { it.pages.count }} পাতা · ওপর থেকে নিচে এই ক্রমে জুড়বে", 16, FontWeight.Normal, C.StatLabel)
    files.forEachIndexed { i, f ->
        ListCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    PageThumb(
                        f.thumbs,
                        0,
                        Modifier
                            .width(70.r)
                            .paper()
                            .clip(RoundedCornerShape(6.r))
                            .border(1.r, C.FieldLine, RoundedCornerShape(6.r)),
                        widthPx = 220,
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(4.r)
                            .size(24.r)
                            .clip(CircleShape)
                            .background(OfficePurple),
                        contentAlignment = Alignment.Center,
                    ) { RText("${i + 1}", 13, FontWeight.Bold, Color.White) }
                }
                Spacer(Modifier.width(14.r))
                Column(Modifier.weight(1f)) {
                    RText(f.name, 17, FontWeight.SemiBold, C.Ink, maxLines = 2)
                    RText("${f.pages.count} পাতা", 15, FontWeight.Normal, C.StatLabel)
                }
                Column {
                    RowIcon("⬆️", i > 0) {
                        files.add(i - 1, files.removeAt(i))
                        saved = null
                    }
                    RowIcon("⬇️", i < files.size - 1) {
                        files.add(i + 1, files.removeAt(i))
                        saved = null
                    }
                }
                RowIcon("🗑", true) {
                    files.removeAt(i).pages.close()
                    saved = null
                }
            }
        }
    }
    RoundButton("➕ আরও PDF যোগ করুন", OfficePurple, Modifier.fillMaxWidth(), filled = false) { picker.launch(arrayOf(MIME_PDF)) }
    if (busy) {
        BusyCard("জোড়া দিচ্ছি…")
    } else if (files.size >= 2) {
        RoundButton("🔗 জোড়া দিয়ে একটা PDF বানান", TGreen, Modifier.fillMaxWidth()) {
            busy = true
            val list = files.map { it.file }
            val first = files.first().title
            scope.launch {
                val p = withContext(Dispatchers.IO) {
                    try {
                        val out = File(File(context.cacheDir, "office").apply { mkdirs() }, "${WordStore.fileName(first)}_merged.pdf")
                        PdfTools.merge(context, list, out)
                        publish(context, out, MIME_PDF)
                    } catch (e: Exception) {
                        null
                    }
                }
                busy = false
                saved = p
                if (p == null) Toast.makeText(context, "জোড়া দেওয়া গেল না", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        RText("জোড়া দিতে অন্তত দুইটা PDF লাগবে", 16, FontWeight.Normal, C.Hint)
    }
    saved?.let { SavedCard(it) }
}

@Composable
private fun RowIcon(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.r)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { RText(label, 20, FontWeight.Normal, if (enabled) C.Ink else C.Hint.copy(alpha = 0.4f)) }
}

@Composable
private fun SplitPart() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pdf by remember { mutableStateOf<OpenPdf?>(null) }
    val chosen = remember { mutableStateListOf<Boolean>() }
    val turns = remember { mutableStateMapOf<Int, Int>() }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<Storage.Published?>(null) }
    var many by remember { mutableIntStateOf(0) }

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
                    repeat(p.pages.count) { chosen.add(false) }
                    turns.clear()
                    saved = null
                    many = 0
                }
            }
        }
    }

    val p = pdf
    if (p == null) {
        PickCard("✂️", "PDF ফাইল বাছুন", "পাতাগুলো ছবির মতো দেখাবে — যেগুলো লাগবে সেগুলোতে চাপ দিন", "PDF বাছুন") { picker.launch(arrayOf(MIME_PDF)) }
        return
    }
    val count = chosen.count { it }
    FileRow("📕", p.name, "${p.pages.count} পাতা · $count টা বাছাই", onChange = { picker.launch(arrayOf(MIME_PDF)) })
    Row(horizontalArrangement = Arrangement.spacedBy(8.r)) {
        ChoiceChip("সব", count == p.pages.count, OfficePurple) { for (i in chosen.indices) chosen[i] = true }
        ChoiceChip("কোনোটা না", count == 0, OfficePurple) { for (i in chosen.indices) chosen[i] = false }
        ChoiceChip("বিজোড়", false, OfficePurple) { for (i in chosen.indices) chosen[i] = i % 2 == 0 }
        ChoiceChip("জোড়", false, OfficePurple) { for (i in chosen.indices) chosen[i] = i % 2 == 1 }
    }
    // the pages, three to a row
    (0 until p.pages.count).chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            row.forEach { i ->
                val on = chosen.getOrNull(i) == true
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        PageThumb(
                            p.thumbs,
                            i,
                            Modifier
                                .fillMaxWidth()
                                .paper()
                                .clip(RoundedCornerShape(6.r))
                                .border(if (on) 3.r else 1.r, if (on) OfficePurple else C.FieldLine, RoundedCornerShape(6.r))
                                .clickable {
                                    chosen[i] = !on
                                    saved = null
                                },
                            rotation = turns[i] ?: 0,
                        )
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.r)
                                .size(26.r)
                                .clip(CircleShape)
                                .background(if (on) OfficePurple else Color(0xCCFFFFFF))
                                .border(1.5.r, if (on) OfficePurple else C.Hint, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) { if (on) RText("✓", 14, FontWeight.Bold, Color.White) }
                    }
                    Spacer(Modifier.height(4.r))
                    RText("${i + 1}", 14, FontWeight.Medium, C.StatLabel)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    fun save(pages: List<Int>, suffix: String) {
        busy = true
        val rot = turns.toMap()
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    val out = File(File(context.cacheDir, "office").apply { mkdirs() }, "${WordStore.fileName(p.title)}_$suffix.pdf")
                    PdfTools.extract(context, p.file, pages, rot, out)
                    publish(context, out, MIME_PDF)
                } catch (e: Exception) {
                    null
                }
            }
            busy = false
            saved = r
            many = 0
            if (r == null) Toast.makeText(context, "PDF বানানো গেল না", Toast.LENGTH_SHORT).show()
        }
    }

    if (busy) {
        BusyCard("PDF বানাচ্ছি…")
    } else {
        if (count > 0) {
            RoundButton("🔄 বাছাই করা পাতা ঘোরান", OfficePurple, Modifier.fillMaxWidth(), filled = false) {
                chosen.indices.filter { chosen[it] }.forEach { i -> turns[i] = ((turns[i] ?: 0) + 90) % 360 }
                saved = null
            }
            RoundButton("📄 বাছাই করা $count পাতা দিয়ে নতুন PDF", TGreen, Modifier.fillMaxWidth()) {
                save(chosen.indices.filter { chosen[it] }, "pages")
            }
            if (count < p.pages.count) {
                RoundButton("🗑 বাছাই করা পাতা বাদ দিয়ে বাকিগুলো রাখুন", TRed, Modifier.fillMaxWidth(), filled = false) {
                    save(chosen.indices.filter { !chosen[it] }, "trimmed")
                }
            }
        } else if (turns.values.any { it != 0 }) {
            RoundButton("💾 ঘোরানো পাতাসহ PDF সেভ", TGreen, Modifier.fillMaxWidth()) { save(chosen.indices.toList(), "rotated") }
        }
        if (p.pages.count > 1) {
            RoundButton("✂️ প্রতিটা পাতা আলাদা PDF (${p.pages.count} টা ফাইল)", OfficePurple, Modifier.fillMaxWidth(), filled = false) {
                busy = true
                scope.launch {
                    val n = withContext(Dispatchers.IO) {
                        try {
                            val dir = File(context.cacheDir, "office/split_${System.currentTimeMillis()}").apply { mkdirs() }
                            PdfTools.splitEach(context, p.file, dir, WordStore.fileName(p.title)).count { f -> publish(context, f, MIME_PDF) != null }
                        } catch (e: Exception) {
                            0
                        }
                    }
                    busy = false
                    saved = null
                    many = n
                    if (n == 0) Toast.makeText(context, "ভাগ করা গেল না", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    if (many > 0) {
        ListCard {
            RText("✅ $many টা PDF সেভ হয়েছে", 20, FontWeight.Bold, TGreen)
            RText("Nirvor ফোল্ডার → Documents", 15, FontWeight.Normal, C.Hint)
        }
    }
    saved?.let { SavedCard(it) }
}
