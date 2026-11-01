package com.nirvor.app.ui.tools.camera

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.downloader.Storage
import com.nirvor.app.tools.toolPrefs
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.tools.ChoiceChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ================================================================ Magnifier

/** Live magnifier: zoom, torch, freeze the picture and pinch on it, contrast / invert, save. */
@Composable
fun MagnifierTool(onBack: () -> Unit) {
    CameraGate("Magnifier", "বড় করে দেখা", onBack) {
        val context = LocalContext.current
        val handle = remember { CameraHandle() }
        var zoom by remember { mutableFloatStateOf(0.35f) }
        var torch by remember { mutableStateOf(false) }
        var frozen by remember { mutableStateOf<Bitmap?>(null) }
        var preview by remember { mutableStateOf<PreviewView?>(null) }
        var filter by remember { mutableIntStateOf(0) }
        var scale by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        val scope = rememberCoroutineScope()
        val cam = handle.camera
        LaunchedEffect(cam) { handle.zoom(zoom) }
        LaunchedEffect(cam, torch) { handle.torch(torch) }

        CameraFrame("Magnifier", onBack, topRight = {
            if (handle.hasTorch) {
                CircleIcon({ torch = !torch }, active = torch) { RText("🔦", 24, FontWeight.Normal, Color.White) }
            }
        }) {
            CameraView(Modifier.fillMaxSize(), handle, onPreviewView = { preview = it })
            val f = frozen
            if (f != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, p, z, _ ->
                                scale = (scale * z).coerceIn(1f, 8f)
                                pan += p
                            }
                        },
                ) {
                    Image(
                        f.asImageBitmap(),
                        null,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = pan.x
                                translationY = pan.y
                            },
                        contentScale = ContentScale.Fit,
                        colorFilter = filterFor(filter),
                    )
                }
            }
            CameraPanel(Modifier.align(Alignment.BottomCenter)) {
                if (frozen == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RText("🔍", 22, FontWeight.Normal, Color.White)
                        Spacer(Modifier.width(10.r))
                        Slider(
                            value = zoom,
                            onValueChange = {
                                zoom = it
                                handle.zoom(it)
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFF34D399)),
                        )
                        Spacer(Modifier.width(10.r))
                        RText("${(1 + zoom * 9).toInt()}×", 20, FontWeight.Bold, Color.White)
                    }
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.r)) {
                        listOf("স্বাভাবিক", "উচ্চ কনট্রাস্ট", "উল্টো রং", "সাদা-কালো").forEachIndexed { i, label ->
                            ChoiceChip(label, filter == i) { filter = i }
                        }
                    }
                    Spacer(Modifier.height(6.r))
                    RText("দুই আঙুলে টেনে আরও বড় করুন", 15, FontWeight.Normal, Color(0xFFB8C2D0))
                }
                Spacer(Modifier.height(16.r))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    if (frozen != null) {
                        PanelButton("💾 সেভ") {
                            val bmp = frozen ?: return@PanelButton
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { saveBitmap(context, bmp, "Magnifier") }
                                Toast.makeText(context, if (ok) "ছবি সেভ হয়েছে (Photos)" else "সেভ করা গেল না", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Shutter({
                        if (frozen != null) {
                            frozen = null
                            scale = 1f
                            pan = Offset.Zero
                        } else {
                            frozen = preview?.bitmap
                        }
                    })
                    RText(if (frozen != null) "▶ লাইভ" else "⏸ থামান", 18, FontWeight.SemiBold, Color.White)
                }
            }
        }
    }
}

private fun filterFor(i: Int): ColorFilter? = when (i) {
    1 -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                1.8f, 0f, 0f, 0f, -100f,
                0f, 1.8f, 0f, 0f, -100f,
                0f, 0f, 1.8f, 0f, -100f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
    2 -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    )
    3 -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    else -> null
}

@Composable
internal fun PanelButton(label: String, onClick: () -> Unit) {
    RText(
        label,
        18,
        FontWeight.SemiBold,
        Color.White,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.r, vertical = 12.r),
    )
}

/** Saves a picture to Nirvor/Downloads/Photos (or the phone's Download/Nirvor). */
internal fun saveBitmap(context: android.content.Context, bmp: Bitmap, prefix: String): Boolean = try {
    val file = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    Storage.publish(context, file, "image/jpeg")
    true
} catch (e: Exception) {
    false
}

// ================================================================ Colour detector

private class NamedColor(val bn: String, val en: String, val rgb: Int)

private val NAMED = listOf(
    NamedColor("লাল", "Red", 0xE53935), NamedColor("গাঢ় লাল / মেরুন", "Maroon", 0x800000),
    NamedColor("গোলাপি", "Pink", 0xF48FB1), NamedColor("গাঢ় গোলাপি", "Hot pink", 0xE91E63),
    NamedColor("কমলা", "Orange", 0xFB8C00), NamedColor("হলুদ", "Yellow", 0xFDD835),
    NamedColor("সোনালি", "Gold", 0xD4A017), NamedColor("ক্রিম", "Cream", 0xFFF5D6),
    NamedColor("বাদামি", "Brown", 0x795548), NamedColor("হালকা বাদামি", "Tan", 0xC8A27A),
    NamedColor("সবুজ", "Green", 0x43A047), NamedColor("গাঢ় সবুজ", "Dark green", 0x1B5E20),
    NamedColor("কলাপাতা সবুজ", "Lime", 0xA5D63F), NamedColor("জলপাই", "Olive", 0x808000),
    NamedColor("ফিরোজা", "Teal", 0x009688), NamedColor("আকাশি", "Sky blue", 0x81D4FA),
    NamedColor("নীল", "Blue", 0x1E88E5), NamedColor("গাঢ় নীল / নেভি", "Navy", 0x1A237E),
    NamedColor("বেগুনি", "Purple", 0x8E24AA), NamedColor("হালকা বেগুনি", "Lavender", 0xC5A3E0),
    NamedColor("সাদা", "White", 0xFAFAFA), NamedColor("হালকা ধূসর", "Light grey", 0xC9CDD2),
    NamedColor("ধূসর", "Grey", 0x8A8F96), NamedColor("গাঢ় ধূসর", "Dark grey", 0x45494F),
    NamedColor("কালো", "Black", 0x111111), NamedColor("চামড়ার রং", "Skin / beige", 0xE8C4A0),
)

private fun nearestName(rgb: Int): NamedColor {
    val r = (rgb shr 16) and 255
    val g = (rgb shr 8) and 255
    val b = rgb and 255
    return NAMED.minBy { c ->
        val dr = r - ((c.rgb shr 16) and 255)
        val dg = g - ((c.rgb shr 8) and 255)
        val db = b - (c.rgb and 255)
        val rm = (r + ((c.rgb shr 16) and 255)) / 2
        // "redmean" distance: closer to how eyes see colour than plain RGB distance
        (((512 + rm) * dr * dr) shr 8) + 4 * dg * dg + (((767 - rm) * db * db) shr 8)
    }
}

private fun hex(rgb: Int) = String.format("#%06X", rgb and 0xFFFFFF)

/** Points the camera at something and names its colour (centre of the screen), with HEX/RGB. */
@Composable
fun ColorDetectorTool(onBack: () -> Unit) {
    CameraGate("Color Detector", "রং চেনা", onBack) {
        val context = LocalContext.current
        val prefs = remember { toolPrefs(context) }
        var live by remember { mutableIntStateOf(0x808080) }
        var held by remember { mutableStateOf<Int?>(null) }
        val saved = remember {
            mutableStateListOf<Int>().apply {
                prefs.getString("colors_saved", "")?.split(',')?.mapNotNull { it.toIntOrNull() }?.let { addAll(it) }
            }
        }
        val main = remember { Handler(Looper.getMainLooper()) }
        val analyzer = remember {
            ImageAnalysis.Analyzer { proxy ->
                try {
                    val plane = proxy.planes[0]
                    val buf = plane.buffer
                    val row = plane.rowStride
                    val px = plane.pixelStride
                    val cx = proxy.width / 2
                    val cy = proxy.height / 2
                    var r = 0L
                    var g = 0L
                    var b = 0L
                    var n = 0
                    for (dy in -6..6 step 2) {
                        for (dx in -6..6 step 2) {
                            val i = (cy + dy) * row + (cx + dx) * px
                            if (i >= 0 && i + 2 < buf.limit()) {
                                r += buf.get(i).toInt() and 255
                                g += buf.get(i + 1).toInt() and 255
                                b += buf.get(i + 2).toInt() and 255
                                n++
                            }
                        }
                    }
                    if (n > 0) {
                        val rgb = ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
                        main.post { live = rgb }
                    }
                } finally {
                    proxy.close()
                }
            }
        }
        val rgb = held ?: live
        val name = nearestName(rgb)
        val color = Color(0xFF000000.toInt() or rgb)

        CameraFrame("Color Detector", onBack) {
            CameraView(Modifier.fillMaxSize(), analyzer = analyzer, rgba = true)
            Canvas(Modifier.fillMaxSize()) {
                val c = center
                drawCircle(Color.Black.copy(alpha = 0.5f), 46f, c, style = Stroke(10f))
                drawCircle(Color.White, 46f, c, style = Stroke(4f))
                drawCircle(color, 30f, c)
                drawLine(Color.White, Offset(c.x - 90f, c.y), Offset(c.x - 54f, c.y), 4f)
                drawLine(Color.White, Offset(c.x + 54f, c.y), Offset(c.x + 90f, c.y), 4f)
                drawLine(Color.White, Offset(c.x, c.y - 90f), Offset(c.x, c.y - 54f), 4f)
                drawLine(Color.White, Offset(c.x, c.y + 54f), Offset(c.x, c.y + 90f), 4f)
            }
            CameraPanel(Modifier.align(Alignment.BottomCenter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(96.r)
                            .clip(RoundedCornerShape(24.r))
                            .background(color)
                            .border(2.r, Color(0x55FFFFFF), RoundedCornerShape(24.r)),
                    )
                    Spacer(Modifier.width(18.r))
                    Column(Modifier.weight(1f)) {
                        RText(name.bn, 26, FontWeight.Bold, Color.White)
                        RText(name.en, 17, FontWeight.Medium, Color(0xFFB8C2D0))
                        Spacer(Modifier.height(6.r))
                        RText(
                            "${hex(rgb)} · RGB ${(rgb shr 16) and 255}, ${(rgb shr 8) and 255}, ${rgb and 255}",
                            17,
                            FontWeight.SemiBold,
                            Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(16.r))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PanelButton("📋 কপি") {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("color", hex(rgb)))
                        Toast.makeText(context, hex(rgb) + " কপি হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                    Shutter({ held = if (held == null) live else null })
                    PanelButton("➕ রাখুন") {
                        saved.add(0, rgb)
                        while (saved.size > 12) saved.removeAt(saved.size - 1)
                        prefs.edit().putString("colors_saved", saved.joinToString(",")).apply()
                    }
                }
                RText(
                    if (held != null) "ধরে রাখা হয়েছে — আবার চাপলে লাইভ" else "মাঝের গোলে যা আছে তার রং · চাপলে ধরে রাখবে",
                    15,
                    FontWeight.Normal,
                    Color(0xFFB8C2D0),
                    Modifier.fillMaxWidth().padding(top = 8.r),
                    align = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (saved.isNotEmpty()) {
                    Spacer(Modifier.height(12.r))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.r)) {
                        saved.forEach { c ->
                            Box(
                                Modifier
                                    .size(48.r)
                                    .clip(CircleShape)
                                    .background(Color(0xFF000000.toInt() or c))
                                    .border(2.r, Color(0x55FFFFFF), CircleShape)
                                    .clickable { held = c },
                            )
                        }
                    }
                }
            }
        }
    }
}
