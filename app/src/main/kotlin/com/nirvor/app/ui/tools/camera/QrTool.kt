package com.nirvor.app.ui.tools.camera

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.sheets.NField
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TBlue
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Live barcode analyzer: hands the codes found and the upright frame size to [onResult]. */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
internal fun barcodeAnalyzer(scanner: BarcodeScanner, onResult: (List<Barcode>, Int, Int) -> Unit) =
    ImageAnalysis.Analyzer { proxy ->
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return@Analyzer
        }
        val (w, h) = uprightSize(proxy)
        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { onResult(it, w, h) }
            .addOnCompleteListener { proxy.close() }
    }

private fun typeName(b: Barcode): String = when (b.valueType) {
    Barcode.TYPE_URL -> "🔗 লিংক"
    Barcode.TYPE_WIFI -> "📶 Wi-Fi"
    Barcode.TYPE_PHONE -> "📞 ফোন নম্বর"
    Barcode.TYPE_EMAIL -> "✉️ ইমেইল"
    Barcode.TYPE_SMS -> "💬 SMS"
    Barcode.TYPE_GEO -> "📍 লোকেশন"
    Barcode.TYPE_CONTACT_INFO -> "👤 কন্টাক্ট"
    Barcode.TYPE_PRODUCT -> "🛒 পণ্যের বারকোড"
    Barcode.TYPE_ISBN -> "📚 বইয়ের ISBN"
    Barcode.TYPE_CALENDAR_EVENT -> "📅 ইভেন্ট"
    else -> if (b.format == Barcode.FORMAT_QR_CODE) "🔳 লেখা (QR)" else "▮▯ বারকোড"
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "এটা খোলার মতো অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("code", text))
    Toast.makeText(context, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
}

/** QR / barcode: scan live (or from a photo) with actions, or make your own QR code. */
@Composable
fun QrTool(onBack: () -> Unit) {
    var making by rememberSaveable { mutableStateOf(false) }
    if (making) {
        QrMaker(onBack = onBack, onScan = { making = false })
    } else {
        CameraGate("QR / Barcode", "স্ক্যান ও তৈরি", onBack) {
            QrScanner(onBack, onMake = { making = true })
        }
    }
}

@Composable
private fun QrScanner(onBack: () -> Unit, onMake: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build())
    }
    val handle = remember { CameraHandle() }
    var torch by remember { mutableStateOf(false) }
    var boxes by remember { mutableStateOf<List<Rect>>(emptyList()) }
    var found by remember { mutableStateOf<Barcode?>(null) }
    var viewSize by remember { mutableStateOf(Size.Zero) }
    val analyzer = remember {
        barcodeAnalyzer(scanner) { list, w, h ->
            if (found != null) return@barcodeAnalyzer
            val vs = viewSize
            boxes = if (vs.width > 0) list.mapNotNull { b -> b.boundingBox?.let { mapToView(it, w, h, vs.width, vs.height) } } else emptyList()
            val first = list.firstOrNull { !it.rawValue.isNullOrEmpty() }
            if (first != null) found = first
        }
    }
    LaunchedEffect(found) {
        if (found != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LaunchedEffect(handle.camera, torch) { handle.torch(torch) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { loadPicked(context, uri, 1600) }
                if (bmp == null) {
                    Toast.makeText(context, "ছবিটা খোলা গেল না", Toast.LENGTH_SHORT).show()
                } else {
                    scanner.process(InputImage.fromBitmap(bmp, 0))
                        .addOnSuccessListener { list ->
                            val first = list.firstOrNull { !it.rawValue.isNullOrEmpty() }
                            if (first != null) found = first else Toast.makeText(context, "ছবিতে কোনো কোড পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }
    }

    CameraFrame("QR / Barcode", onBack, topRight = {
        CircleIcon({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            RText("🖼️", 22, FontWeight.Normal, Color.White)
        }
        Spacer(Modifier.width(10.r))
        if (handle.hasTorch) {
            CircleIcon({ torch = !torch }, active = torch) { RText("🔦", 22, FontWeight.Normal, Color.White) }
        }
    }) {
        CameraView(Modifier.fillMaxSize().onSizeChanged { viewSize = Size(it.width.toFloat(), it.height.toFloat()) }, handle, analyzer)
        Canvas(Modifier.fillMaxSize()) {
            // viewfinder corners
            val side = size.width * 0.66f
            val l = (size.width - side) / 2f
            val t = size.height * 0.36f - side / 2f
            val arm = side * 0.16f
            val col = if (found != null) Color(0xFF34D399) else Color.White
            listOf(
                Offset(l, t) to Pair(1f, 1f), Offset(l + side, t) to Pair(-1f, 1f),
                Offset(l, t + side) to Pair(1f, -1f), Offset(l + side, t + side) to Pair(-1f, -1f),
            ).forEach { (p, d) ->
                drawLine(col, p, Offset(p.x + arm * d.first, p.y), 8f)
                drawLine(col, p, Offset(p.x, p.y + arm * d.second), 8f)
            }
            boxes.forEach { r ->
                drawRoundRect(Color(0xFF34D399), r.topLeft, r.size, CornerRadius(12f, 12f), style = Stroke(6f))
            }
        }
        CameraPanel(Modifier.align(Alignment.BottomCenter)) {
            val b = found
            if (b == null) {
                RText("QR কোড বা বারকোড ফ্রেমের মধ্যে আনুন", 19, FontWeight.SemiBold, Color.White)
                Spacer(Modifier.height(4.r))
                RText("ধরা পড়লেই নিচে দেখাবে। ছবি থেকে পড়তে ওপরের 🖼️ চাপুন।", 15, FontWeight.Normal, Color(0xFFB8C2D0), maxLines = 2)
                Spacer(Modifier.height(14.r))
                PanelButton("🔳 নিজের QR কোড বানান", onMake)
            } else {
                ScanResult(context, b) {
                    found = null
                    boxes = emptyList()
                }
            }
        }
    }
}

@Composable
private fun ScanResult(context: Context, b: Barcode, onAgain: () -> Unit) {
    val raw = b.rawValue ?: ""
    RText(typeName(b), 17, FontWeight.SemiBold, Color(0xFF34D399))
    Spacer(Modifier.height(6.r))
    val shown = when (b.valueType) {
        Barcode.TYPE_WIFI -> "নেটওয়ার্ক: ${b.wifi?.ssid ?: ""}\nপাসওয়ার্ড: ${b.wifi?.password ?: "(নেই)"}"
        Barcode.TYPE_URL -> b.url?.url ?: raw
        Barcode.TYPE_PHONE -> b.phone?.number ?: raw
        Barcode.TYPE_EMAIL -> b.email?.address ?: raw
        else -> b.displayValue ?: raw
    }
    RText(shown, 20, FontWeight.Medium, Color.White, maxLines = 6, lineHeight = 28)
    Spacer(Modifier.height(16.r))
    Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
        when (b.valueType) {
            Barcode.TYPE_URL -> PanelButton("🌐 খুলুন") { open(context, Intent(Intent.ACTION_VIEW, Uri.parse(b.url?.url ?: raw))) }
            Barcode.TYPE_PHONE -> PanelButton("📞 কল") { open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + (b.phone?.number ?: raw)))) }
            Barcode.TYPE_EMAIL -> PanelButton("✉️ মেইল") { open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + (b.email?.address ?: "")))) }
            Barcode.TYPE_WIFI -> PanelButton("📶 Wi-Fi সেটিংস") {
                b.wifi?.password?.let { copy(context, it) }
                open(context, Intent(Settings.ACTION_WIFI_SETTINGS))
            }
            Barcode.TYPE_GEO -> PanelButton("🗺️ ম্যাপ") {
                open(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:${b.geoPoint?.lat},${b.geoPoint?.lng}")))
            }
            Barcode.TYPE_PRODUCT, Barcode.TYPE_ISBN -> PanelButton("🔎 খুঁজুন") {
                open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(raw))))
            }
            else -> Unit
        }
        PanelButton("📋 কপি") { copy(context, if (b.valueType == Barcode.TYPE_WIFI) b.wifi?.password ?: raw else raw) }
        PanelButton("📤 শেয়ার") {
            open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, raw), "শেয়ার"))
        }
    }
    Spacer(Modifier.height(12.r))
    PanelButton("🔄 আবার স্ক্যান", onAgain)
}

// ---------------------------------------------------------------- make a QR code

/** Makes a QR bitmap (black on white) of [size] px, or null when the text is too long. */
internal fun makeQr(text: String, size: Int = 800): Bitmap? = try {
    val m = MultiFormatWriter().encode(
        text,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )
    val px = IntArray(m.width * m.height)
    for (y in 0 until m.height) {
        for (x in 0 until m.width) px[y * m.width + x] = if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
} catch (e: Exception) {
    null
}

private fun wifiEscape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

@Composable
private fun QrMaker(onBack: () -> Unit, onScan: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableIntStateOf(0) } // 0 text/link, 1 wifi, 2 phone
    var text by rememberSaveable { mutableStateOf("") }
    var ssid by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    val content = when (kind) {
        1 -> if (ssid.isBlank()) "" else "WIFI:T:${if (pass.isEmpty()) "nopass" else "WPA"};S:${wifiEscape(ssid)};P:${wifiEscape(pass)};;"
        2 -> if (phone.isBlank()) "" else "tel:" + phone.trim()
        else -> text.trim()
    }
    val qr = remember(content) { if (content.isEmpty()) null else makeQr(content) }

    ToolScaffold("QR / Barcode", "নিজের QR কোড বানান", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
            ChoiceChip("লেখা / লিংক", kind == 0) { kind = 0 }
            ChoiceChip("Wi-Fi", kind == 1) { kind = 1 }
            ChoiceChip("ফোন নম্বর", kind == 2) { kind = 2 }
        }
        ListCard {
            when (kind) {
                1 -> {
                    NField(ssid, { ssid = it }, "Wi-Fi-এর নাম")
                    Spacer(Modifier.height(12.r))
                    NField(pass, { pass = it }, "পাসওয়ার্ড")
                    Spacer(Modifier.height(8.r))
                    RText("এই QR স্ক্যান করলে অন্যরা পাসওয়ার্ড না লিখেই আপনার Wi-Fi-তে ঢুকতে পারবে।", 16, FontWeight.Normal, C.Hint, maxLines = 2)
                }
                2 -> NField(phone, { phone = it }, "যেমন: 01XXXXXXXXX", keyboardType = KeyboardType.Phone)
                else -> NField(text, { text = it }, "যেকোনো লেখা বা লিংক", singleLine = false, minHeight = 140)
            }
        }
        val bmp = qr
        if (bmp != null) {
            ListCard {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        bmp.asImageBitmap(),
                        "QR",
                        Modifier.fillMaxWidth(0.8f).aspectRatio(1f).background(Color.White, RoundedCornerShape(12.r)),
                        filterQuality = FilterQuality.None,
                    )
                }
                Spacer(Modifier.height(16.r))
                Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
                    RoundButton("💾 সেভ", TGreen, Modifier.weight(1f)) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { saveBitmap(context, bmp, "QR") }
                            Toast.makeText(context, if (ok) "QR ছবি সেভ হয়েছে (Photos)" else "সেভ করা গেল না", Toast.LENGTH_SHORT).show()
                        }
                    }
                    RoundButton("📤 শেয়ার", TBlue, Modifier.weight(1f), filled = false) {
                        scope.launch {
                            val uri = withContext(Dispatchers.IO) { shareableImage(context, bmp) }
                            if (uri != null) {
                                open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "শেয়ার"))
                            }
                        }
                    }
                }
            }
        } else if (content.isNotEmpty()) {
            RText("লেখাটা অনেক বড়, QR-এ আঁটছে না।", 18, FontWeight.Medium, C.Danger)
        }
        RoundButton("📷 স্ক্যান করুন", C.StatLabel, Modifier.fillMaxWidth(), filled = false, onClick = onScan)
    }
}

/** Writes a picture to the cache and returns a content:// link other apps can read. */
internal fun shareableImage(context: Context, bmp: Bitmap): Uri? = try {
    val dir = java.io.File(context.cacheDir, "share").apply { mkdirs() }
    val file = java.io.File(dir, "nirvor_${System.currentTimeMillis()}.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
} catch (e: Exception) {
    null
}
