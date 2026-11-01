package com.nirvor.app.ui.tools.camera

import android.graphics.Bitmap
import android.graphics.PointF
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.tools.DocScan
import com.nirvor.app.tools.Ocr
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min

/**
 * Camera → photo → drag the four corners → straightened picture. [findPaper]: guess the
 * paper's corners automatically (documents, receipts); otherwise start with a rectangle.
 */
@Composable
fun CaptureAndCrop(
    title: String,
    hint: String,
    findPaper: Boolean,
    onBack: () -> Unit,
    footer: @Composable () -> Unit = {},
    onDone: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    var shot by remember { mutableStateOf<Bitmap?>(null) }
    var corners by remember { mutableStateOf<List<PointF>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    fun accept(bmp: Bitmap) {
        busy = true
        scope.launch {
            val found = if (findPaper) withContext(Dispatchers.Default) { DocScan.detect(bmp) } else null
            corners = found ?: DocScan.inset(bmp.width, bmp.height, if (findPaper) 0.06f else 0.1f)
            shot = bmp
            busy = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { loadPicked(context, uri) }
                if (bmp == null) {
                    busy = false
                    Toast.makeText(context, "ছবিটা খোলা গেল না", Toast.LENGTH_SHORT).show()
                } else {
                    accept(bmp)
                }
            }
        }
    }

    val photo = shot
    if (photo == null) {
        CameraFrame(title, onBack, topRight = {
            CircleIcon({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                RText("🖼️", 22, FontWeight.Normal, Color.White)
            }
        }) {
            CameraView(Modifier.fillMaxSize(), capture = capture)
            Canvas(Modifier.fillMaxSize()) {
                val l = size.width * 0.08f
                val t = size.height * 0.14f
                drawRoundRect(
                    Color.White.copy(alpha = 0.8f),
                    Offset(l, t),
                    Size(size.width - 2 * l, size.height * 0.56f),
                    androidx.compose.ui.geometry.CornerRadius(24f, 24f),
                    style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 18f))),
                )
            }
            CameraPanel(Modifier.align(Alignment.BottomCenter)) {
                RText(hint, 17, FontWeight.Medium, Color.White, maxLines = 2)
                Spacer(Modifier.height(16.r))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PanelButton("🖼️ গ্যালারি") { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    if (busy) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Shutter({
                            busy = true
                            scope.launch {
                                val bmp = takePhoto(context, capture)
                                if (bmp == null) {
                                    busy = false
                                    Toast.makeText(context, "ছবি তোলা গেল না, আবার চেষ্টা করুন", Toast.LENGTH_SHORT).show()
                                } else {
                                    accept(bmp)
                                }
                            }
                        })
                    }
                    footer()
                }
            }
        }
    } else {
        CropView(photo, corners, onCorners = { corners = it }, busy = busy, title = title, onRetake = { shot = null }) {
            busy = true
            scope.launch {
                val out = withContext(Dispatchers.Default) { DocScan.warp(photo, corners) }
                busy = false
                shot = null
                onDone(out)
            }
        }
    }
}

/** The photo with four draggable corners. */
@Composable
private fun CropView(
    photo: Bitmap,
    corners: List<PointF>,
    onCorners: (List<PointF>) -> Unit,
    busy: Boolean,
    title: String,
    onRetake: () -> Unit,
    onOk: () -> Unit,
) {
    var box by remember { mutableStateOf(Size.Zero) }
    var dragging by remember { mutableIntStateOf(-1) }
    var zoomAt by remember { mutableStateOf<Offset?>(null) }
    val image = remember(photo) { photo.asImageBitmap() }
    val scale = if (box.width > 0) min(box.width / photo.width, box.height / photo.height) else 1f
    val dx = (box.width - photo.width * scale) / 2f
    val dy = (box.height - photo.height * scale) / 2f
    fun toView(p: PointF) = Offset(p.x * scale + dx, p.y * scale + dy)
    val latest by androidx.compose.runtime.rememberUpdatedState(corners)

    CameraFrame("$title · কোণ ঠিক করুন", onRetake) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 90.r, bottom = 230.r, start = 20.r, end = 20.r)
                .onSizeChanged { box = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(photo, box) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val pts = latest.map { toView(it) }
                            val i = pts.indices.minByOrNull { hypot(pts[it].x - pos.x, pts[it].y - pos.y) } ?: -1
                            dragging = if (i >= 0 && hypot(pts[i].x - pos.x, pts[i].y - pos.y) < 140f) i else -1
                            zoomAt = if (dragging >= 0) pts[dragging] else null
                        },
                        onDragEnd = {
                            dragging = -1
                            zoomAt = null
                        },
                        onDragCancel = {
                            dragging = -1
                            zoomAt = null
                        },
                    ) { change, d ->
                        val i = dragging
                        if (i < 0 || scale <= 0f) return@detectDragGestures
                        change.consume()
                        val list = latest.toMutableList()
                        val p = list[i]
                        val nx = (p.x + d.x / scale).coerceIn(0f, photo.width.toFloat())
                        val ny = (p.y + d.y / scale).coerceIn(0f, photo.height.toFloat())
                        list[i] = PointF(nx, ny)
                        onCorners(list)
                        zoomAt = toView(list[i])
                    }
                },
        ) {
            Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Canvas(Modifier.fillMaxSize()) {
                if (corners.size == 4) {
                    val pts = corners.map { toView(it) }
                    val path = Path().apply {
                        moveTo(pts[0].x, pts[0].y)
                        for (k in 1..3) lineTo(pts[k].x, pts[k].y)
                        close()
                    }
                    drawPath(path, Color(0x330BA765))
                    drawPath(path, Color(0xFF34D399), style = Stroke(5f))
                    pts.forEachIndexed { k, p ->
                        drawCircle(Color(0x6634D399), if (k == dragging) 46f else 34f, p)
                        drawCircle(Color.White, 16f, p)
                        drawCircle(Color(0xFF0BA765), 16f, p, style = Stroke(5f))
                    }
                }
                // loupe while dragging: a bigger view of the picture around the corner
                val z = zoomAt
                if (z != null) {
                    val r = 110f
                    val center = Offset(if (z.x < size.width / 2) size.width - r - 20f else r + 20f, r + 20f)
                    drawCircle(Color.Black, r + 4f, center)
                    val lens = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, r)) }
                    val srcX = ((z.x - dx) / scale).toInt()
                    val srcY = ((z.y - dy) / scale).toInt()
                    val half = (r / scale / 2.5f).toInt().coerceAtLeast(8)
                    clipPath(lens) {
                    drawImage(
                        image,
                        srcOffset = androidx.compose.ui.unit.IntOffset(
                            (srcX - half).coerceIn(0, (photo.width - 2 * half).coerceAtLeast(0)),
                            (srcY - half).coerceIn(0, (photo.height - 2 * half).coerceAtLeast(0)),
                        ),
                        srcSize = androidx.compose.ui.unit.IntSize(2 * half, 2 * half).let {
                            androidx.compose.ui.unit.IntSize(min(it.width, photo.width), min(it.height, photo.height))
                        },
                        dstOffset = androidx.compose.ui.unit.IntOffset((center.x - r).toInt(), (center.y - r).toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize((2 * r).toInt(), (2 * r).toInt()),
                    )
                    }
                    drawCircle(Color(0xFF34D399), r, center, style = Stroke(4f))
                    drawLine(Color(0xFF34D399), Offset(center.x - 18f, center.y), Offset(center.x + 18f, center.y), 3f)
                    drawLine(Color(0xFF34D399), Offset(center.x, center.y - 18f), Offset(center.x, center.y + 18f), 3f)
                }
            }
        }
        CameraPanel(Modifier.align(Alignment.BottomCenter)) {
            RText("সবুজ গোলগুলো টেনে কাগজ / লেখার চার কোণে বসান", 17, FontWeight.Medium, Color.White, maxLines = 2)
            Spacer(Modifier.height(16.r))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                PanelButton("🔄 আবার তুলুন", onRetake)
                PanelButton("⬜ পুরোটা") { onCorners(DocScan.inset(photo.width, photo.height, 0f)) }
                if (busy) CircularProgressIndicator(color = Color.White) else PanelButton("✓ ঠিক আছে", onOk)
            }
        }
    }
}

/** Makes sure the OCR language data is on the phone (asks once, downloads ~5 MB), then shows [content]. */
@Composable
fun OcrDataGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(Ocr.ready(context)) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var failed by remember { mutableStateOf(false) }
    if (ready) {
        content()
        return
    }
    ListCard {
        RText("🔤 লেখা পড়ার ডেটা নামাতে হবে", 21, FontWeight.SemiBold, C.Ink)
        Spacer(Modifier.height(6.r))
        RText(
            "বাংলা আর ইংরেজি লেখা চিনতে একবার প্রায় ৫ MB ডেটা নামবে (ইন্টারনেট লাগবে)। এরপর সব কাজ ইন্টারনেট ছাড়াই ফোনের ভেতরে হবে।",
            17,
            FontWeight.Normal,
            C.StatLabel,
            maxLines = 4,
            lineHeight = 25,
        )
        Spacer(Modifier.height(16.r))
        if (progress >= 0f) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = TGreen)
            Spacer(Modifier.height(8.r))
            RText("নামছে… ${(progress * 100).toInt()}%", 16, FontWeight.Medium, C.StatLabel)
        } else {
            RoundButton(if (failed) "আবার চেষ্টা করুন" else "⬇️ নামিয়ে নিন", TGreen, Modifier.fillMaxWidth()) {
                failed = false
                progress = 0f
                scope.launch {
                    val ok = Ocr.download(context) { progress = it }
                    progress = -1f
                    if (ok) ready = true else failed = true
                }
            }
            if (failed) {
                Spacer(Modifier.height(8.r))
                RText("নামানো গেল না। ইন্টারনেট চালু আছে কিনা দেখুন।", 16, FontWeight.Medium, C.Danger)
            }
        }
    }
}
