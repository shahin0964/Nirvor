package com.nirvor.app.ui.tools.office

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.downloader.Storage
import com.nirvor.app.office.PdfPages
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.sheets.NField
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TBlue
import com.nirvor.app.ui.tools.TGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val MIME_PDF = "application/pdf"
internal const val MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
internal const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

internal val OfficePurple = Color(0xFF6E56CF)

internal fun stamp(): String = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())

/** The name a picked file has on the phone ("bill.pdf"). */
internal fun displayName(context: Context, uri: Uri): String = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()
} catch (e: Exception) {
    uri.lastPathSegment.orEmpty()
}

/** Copies a picked file into the app's cache so the tools can read it as a file. */
internal fun copyIn(context: Context, uri: Uri, name: String = displayName(context, uri)): File? {
    return try {
        val dir = File(context.cacheDir, "office").apply { mkdirs() }
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
        val f = File(dir, "${System.nanoTime()}_$safe")
        val copied = context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
        if (copied == null) null else f
    } catch (e: Exception) {
        null
    }
}

internal fun startSafe(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "খোলার মতো অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
    }
}

/** Saves a finished file to Nirvor / Documents; null when it could not be saved. */
internal fun publish(context: Context, file: File, mime: String): Storage.Published? = try {
    Storage.publish(context, file, mime)
} catch (e: Exception) {
    null
}

/** Big dashed card asking for a file. */
@Composable
internal fun PickCard(emoji: String, title: String, hint: String, button: String, color: Color = OfficePurple, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.r)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.06f))
            .border(1.6.r, color.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 34.r, horizontal = 24.r),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RText(emoji, 52, FontWeight.Normal, C.Ink)
        Spacer(Modifier.height(10.r))
        RText(title, 22, FontWeight.SemiBold, C.Ink, maxLines = 2, align = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(6.r))
        RText(hint, 17, FontWeight.Normal, C.StatLabel, maxLines = 3, lineHeight = 24, align = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(18.r))
        RText(
            button,
            19,
            FontWeight.SemiBold,
            Color.White,
            Modifier
                .clip(RoundedCornerShape(50))
                .background(color)
                .padding(horizontal = 26.r, vertical = 13.r),
        )
    }
}

/** The file that is being worked on: icon, name, details and "change". */
@Composable
internal fun FileRow(emoji: String, name: String, detail: String, onChange: (() -> Unit)?) {
    ListCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText(emoji, 34, FontWeight.Normal, C.Ink)
            Spacer(Modifier.width(14.r))
            Column(Modifier.weight(1f)) {
                RText(name, 19, FontWeight.SemiBold, C.Ink, maxLines = 2)
                if (detail.isNotEmpty()) RText(detail, 16, FontWeight.Normal, C.StatLabel, maxLines = 2)
            }
            if (onChange != null) {
                RText(
                    "বদলান",
                    17,
                    FontWeight.SemiBold,
                    OfficePurple,
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onChange)
                        .padding(horizontal = 12.r, vertical = 8.r),
                )
            }
        }
    }
}

/** "Working…" with an optional progress bar. */
@Composable
internal fun BusyCard(text: String, progress: Float? = null) {
    ListCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(30.r), color = OfficePurple, strokeWidth = 3.r)
            Spacer(Modifier.width(16.r))
            RText(text, 19, FontWeight.Medium, C.Ink, Modifier.weight(1f), maxLines = 2)
        }
        if (progress != null) {
            Spacer(Modifier.height(14.r))
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = OfficePurple)
        }
    }
}

/** A saved file with open / share buttons. */
@Composable
internal fun SavedCard(p: Storage.Published, extra: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    ListCard {
        RText("✅ সেভ হয়েছে", 21, FontWeight.Bold, TGreen)
        Spacer(Modifier.height(4.r))
        RText(p.name, 18, FontWeight.Medium, C.Ink, maxLines = 2)
        RText("Nirvor ফোল্ডার → Documents", 15, FontWeight.Normal, C.Hint)
        Spacer(Modifier.height(14.r))
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            RoundButton("খুলুন", TGreen, Modifier.weight(1f)) { startSafe(context, Storage.openIntent(p.uri, p.mime)) }
            RoundButton("📤 শেয়ার", TBlue, Modifier.weight(1f), filled = false) { startSafe(context, Storage.shareIntent(p.uri, p.mime)) }
        }
        if (extra != null) {
            Spacer(Modifier.height(10.r))
            extra()
        }
    }
}

/** Page pictures are kept for a while so scrolling back is instant. */
internal class ThumbCache(val pdf: PdfPages) {
    val cache = object : LruCache<Int, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
}

/** One PDF page as a picture, drawn [widthPx] wide and turned by [rotation] degrees. */
@Composable
internal fun PageThumb(thumbs: ThumbCache, index: Int, modifier: Modifier, widthPx: Int = 360, rotation: Int = 0) {
    val bmp by produceState<Bitmap?>(thumbs.cache.get(index), thumbs, index) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { thumbs.pdf.render(index, widthPx)?.also { thumbs.cache.put(index, it) } }
        }
    }
    Box(modifier.background(Color.White), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) {
            CircularProgressIndicator(Modifier.size(22.r), color = OfficePurple.copy(alpha = 0.5f), strokeWidth = 2.r)
        } else {
            Image(
                b.asImageBitmap(),
                null,
                Modifier
                    .fillMaxSize()
                    .rotate(rotation.toFloat()),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** A sheet of paper shaped box for page thumbnails (A4 ratio). */
internal fun Modifier.paper(): Modifier = this.aspectRatio(0.707f)

/** Small text dialog (rename, link). */
@Composable
internal fun TextDialog(title: String, initial: String, hint: String, ok: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { RText(title, 23, FontWeight.Bold, C.Ink) },
        text = { NField(value, { value = it }, hint) },
        confirmButton = {
            TextButton(onClick = {
                onOk(value)
                onDismiss()
            }) { RText(ok, 20, FontWeight.SemiBold, OfficePurple) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { RText("বাতিল", 20, FontWeight.Medium, C.Hint) } },
    )
}
