package com.nirvor.app.ui.tools.office

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.downloader.Storage
import com.nirvor.app.office.PageSize
import com.nirvor.app.office.PaperSize
import com.nirvor.app.office.PdfTools
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.ToolScaffold
import com.nirvor.app.ui.tools.camera.CameraGate
import com.nirvor.app.ui.tools.camera.CaptureAndCrop
import com.nirvor.app.ui.tools.camera.loadPicked
import com.nirvor.app.ui.tools.camera.rotate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One picture of the PDF: from the gallery ([uri]) or the camera ([bmp]), turned by [rotation]. */
private class Pic(val key: Long, val uri: Uri?, val bmp: Bitmap?, val rotation: Int) {
    fun turned() = Pic(key, uri, bmp, (rotation + 90) % 360)
}

private val PIC_MARGINS = listOf("নেই" to 0f, "অল্প" to 18f, "বেশি" to 40f)

/** Image → PDF: pictures laid out on pages you can see, in the order you set. */
@Composable
fun ImageToPdfTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pics = remember { mutableStateListOf<Pic>() }
    var nextKey by remember { mutableLongStateOf(1L) }
    var selected by remember { mutableStateOf<Long?>(null) }
    var paper by remember { mutableStateOf(PaperSize.A4) }
    var margin by remember { mutableIntStateOf(1) }
    var camera by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var saved by remember { mutableStateOf<Storage.Published?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(60)) { uris ->
        uris.forEach { u ->
            pics.add(Pic(nextKey, u, null, 0))
            nextKey++
        }
        if (uris.isNotEmpty()) saved = null
    }

    if (camera) {
        CameraGate("Image → PDF", "ছবি তুলুন", { camera = false }) {
            CaptureAndCrop("Image → PDF", "যেটার ছবি PDF-এ রাখবেন সেটা দাগের ভেতরে আনুন", findPaper = true, onBack = { camera = false }) { bmp ->
                pics.add(Pic(nextKey, null, bmp, 0))
                nextKey++
                camera = false
                saved = null
            }
        }
        return
    }

    ToolScaffold("Image → PDF", "ছবি থেকে PDF", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            RoundButton("🖼️ গ্যালারি", OfficePurple, Modifier.weight(1f)) {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            RoundButton("📷 ক্যামেরা", OfficePurple, Modifier.weight(1f), filled = false) { camera = true }
        }
        if (pics.isEmpty()) {
            PickCard("🖼️", "ছবি যোগ করুন", "গ্যালারি থেকে একসাথে অনেকগুলো, বা ক্যামেরায় তুলে", "গ্যালারি খুলুন") {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            return@ToolScaffold
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
            PaperSize.entries.forEach { p -> ChoiceChip(p.label, paper == p, OfficePurple) { paper = p } }
        }
        if (paper != PaperSize.Fit) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.r), verticalAlignment = Alignment.CenterVertically) {
                RText("মার্জিন:", 17, FontWeight.Medium, C.StatLabel)
                PIC_MARGINS.forEachIndexed { i, (label, _) -> ChoiceChip(label, margin == i, OfficePurple) { margin = i } }
            }
        }
        RText("${pics.size} পাতা · ছবিতে চাপ দিয়ে সরান, ঘোরান বা বাদ দিন", 16, FontWeight.Normal, C.StatLabel)
        pics.chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.r), verticalAlignment = Alignment.Bottom) {
                row.forEachIndexed { k, pic ->
                    val index = rowIndex * 3 + k
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        PicPage(
                            pic,
                            paper,
                            if (paper == PaperSize.Fit) 0f else PIC_MARGINS[margin].second,
                            selected == pic.key,
                        ) { selected = if (selected == pic.key) null else pic.key }
                        Spacer(Modifier.height(4.r))
                        RText("${index + 1}", 14, FontWeight.Medium, C.StatLabel)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        val sel = pics.indexOfFirst { it.key == selected }
        if (sel >= 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.r)) {
                SmallAction("⬅️ আগে", Modifier.weight(1f), sel > 0) {
                    val p = pics.removeAt(sel)
                    pics.add(sel - 1, p)
                }
                SmallAction("➡️ পরে", Modifier.weight(1f), sel < pics.size - 1) {
                    val p = pics.removeAt(sel)
                    pics.add(sel + 1, p)
                }
                SmallAction("🔄 ঘোরান", Modifier.weight(1f), true) { pics[sel] = pics[sel].turned() }
                SmallAction("🗑 বাদ", Modifier.weight(1f), true) {
                    pics.removeAt(sel)
                    selected = null
                }
            }
        }
        if (busy) {
            BusyCard("PDF বানাচ্ছি… ${(progress * 100).toInt()}%", progress)
        } else {
            RoundButton("📕 PDF বানান (${pics.size} পাতা)", TGreen, Modifier.fillMaxWidth()) {
                busy = true
                progress = 0f
                val list = pics.toList()
                scope.launch {
                    val p = withContext(Dispatchers.IO) {
                        try {
                            val f = File(File(context.cacheDir, "office").apply { mkdirs() }, "Images_${stamp()}.pdf")
                            val n = PdfTools.images(
                                context,
                                list.size,
                                load = { i ->
                                    val pic = list[i]
                                    val raw = pic.uri?.let { loadPicked(context, it, 2400) } ?: pic.bmp?.copy(Bitmap.Config.ARGB_8888, false)
                                    raw?.let { b -> if (pic.rotation != 0) rotate(b, pic.rotation.toFloat()).also { r -> if (r !== b) b.recycle() } else b }
                                },
                                paper = paper,
                                marginPt = if (paper == PaperSize.Fit) 0f else PIC_MARGINS[margin].second,
                                out = f,
                                recycle = true,
                            ) { done -> progress = done.toFloat() / list.size }
                            if (n > 0) publish(context, f, MIME_PDF) else null
                        } catch (e: Exception) {
                            null
                        }
                    }
                    busy = false
                    saved = p
                    if (p == null) Toast.makeText(context, "PDF বানানো গেল না", Toast.LENGTH_SHORT).show()
                }
            }
        }
        saved?.let { SavedCard(it) }
    }
}

@Composable
private fun SmallAction(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    RText(
        label,
        15,
        FontWeight.SemiBold,
        if (enabled) C.Ink else C.Hint,
        modifier
            .clip(RoundedCornerShape(12.r))
            .background(Color(0xFFF1F4F7))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.r),
        align = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

/** The page as it will come out: paper shape, margins and the picture fitted inside. */
@Composable
private fun PicPage(pic: Pic, paper: PaperSize, marginPt: Float, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    // a small copy of the picture, already turned
    val bmp by produceState<Bitmap?>(null, pic.key, pic.rotation) {
        value = withContext(Dispatchers.IO) {
            val small = pic.uri?.let { loadPicked(context, it, 420) } ?: pic.bmp?.let { b ->
                val k = 420f / maxOf(b.width, b.height)
                if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt().coerceAtLeast(1), (b.height * k).toInt().coerceAtLeast(1), true) else b
            }
            small?.let { if (pic.rotation != 0) rotate(it, pic.rotation.toFloat()) else it }
        }
    }
    val b = bmp
    val iw = b?.width ?: 3
    val ih = b?.height ?: 4
    val landscape = iw > ih
    val (pw, ph) = when (paper) {
        PaperSize.A4 -> if (landscape) PageSize.A4_H to PageSize.A4_W else PageSize.A4_W to PageSize.A4_H
        PaperSize.Letter -> if (landscape) PageSize.LETTER_H to PageSize.LETTER_W else PageSize.LETTER_W to PageSize.LETTER_H
        PaperSize.Fit -> iw.toFloat() to ih.toFloat()
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(pw / ph)
            .clip(RoundedCornerShape(6.r))
            .background(Color.White)
            .border(if (selected) 3.r else 1.r, if (selected) OfficePurple else C.FieldLine, RoundedCornerShape(6.r))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val pad = maxWidth * (marginPt / pw)
        if (b == null) {
            CircularProgressIndicator(Modifier.size(22.r), color = OfficePurple.copy(alpha = 0.5f), strokeWidth = 2.r)
        } else {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize().padding(pad), contentScale = ContentScale.Fit)
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.r)
                    .size(24.r)
                    .clip(CircleShape)
                    .background(OfficePurple),
                contentAlignment = Alignment.Center,
            ) { RText("✓", 14, FontWeight.Bold, Color.White) }
        }
    }
}
