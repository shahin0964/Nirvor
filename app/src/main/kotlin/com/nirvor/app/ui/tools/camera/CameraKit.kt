package com.nirvor.app.ui.tools.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.ToolScaffold
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun hasCamera(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Asks for the camera permission, then shows [content]. */
@Composable
fun CameraGate(title: String, subtitle: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCamera(context)) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    if (granted) {
        content()
        return
    }
    ToolScaffold(title, subtitle, onBack) {
        ListCard {
            RText("📷 ক্যামেরার অনুমতি লাগবে", 21, FontWeight.SemiBold, C.Ink)
            Spacer(Modifier.height(6.r))
            RText(
                "এই টুলটা ক্যামেরায় দেখে কাজ করে। ছবি বা ভিডিও কোথাও পাঠানো হয় না, সব ফোনের ভেতরেই হয়।",
                17,
                FontWeight.Normal,
                C.StatLabel,
                maxLines = 3,
                lineHeight = 25,
            )
            Spacer(Modifier.height(16.r))
            RoundButton("অনুমতি দিন", TGreen, Modifier.fillMaxWidth()) { launcher.launch(Manifest.permission.CAMERA) }
            if (asked) {
                Spacer(Modifier.height(10.r))
                RoundButton("সেটিংস থেকে দিন", C.StatLabel, Modifier.fillMaxWidth(), filled = false) {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (e: Exception) {
                        // no settings screen
                    }
                }
            }
        }
    }
}

/** The bound camera, for zoom, torch and focus. */
class CameraHandle {
    var camera by mutableStateOf<Camera?>(null)
    val hasTorch: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    fun zoom(linear: Float) {
        camera?.cameraControl?.setLinearZoom(linear.coerceIn(0f, 1f))
    }

    fun torch(on: Boolean) {
        if (hasTorch) camera?.cameraControl?.enableTorch(on)
    }
}

/**
 * Live back-camera preview. [analyzer] gets frames (YUV, or RGBA when [rgba]); [capture] takes
 * photos. Everything is unbound when the screen closes.
 */
@Composable
fun CameraView(
    modifier: Modifier,
    handle: CameraHandle? = null,
    analyzer: ImageAnalysis.Analyzer? = null,
    rgba: Boolean = false,
    capture: ImageCapture? = null,
    onPreviewView: (PreviewView) -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    AndroidView({ previewView }, modifier)
    DisposableEffect(owner, analyzer, capture) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        future.addListener({
            if (disposed) return@addListener
            val p = try {
                future.get()
            } catch (e: Exception) {
                return@addListener
            }
            provider = p
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val cases = mutableListOf<UseCase>(preview)
            if (analyzer != null) {
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .apply { if (rgba) setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888) }
                    .build()
                analysis.setAnalyzer(executor, analyzer)
                cases.add(analysis)
            }
            if (capture != null) cases.add(capture)
            try {
                p.unbindAll()
                val cam = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *cases.toTypedArray())
                handle?.camera = cam
                onPreviewView(previewView)
            } catch (e: Exception) {
                handle?.camera = null
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            handle?.camera = null
            try {
                provider?.unbindAll()
            } catch (e: Exception) {
                // already gone
            }
            executor.shutdown()
        }
    }
}

/** Takes one photo into the cache folder and returns it upright (or null on failure). */
suspend fun takePhoto(context: Context, capture: ImageCapture): Bitmap? = suspendCoroutine { cont ->
    val file = File(context.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    capture.takePicture(
        options,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val bmp = decodeUpright(file, 2400)
                file.delete()
                cont.resume(bmp)
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                cont.resume(null)
            }
        },
    )
}

/** Decodes a photo, scaled so its long side is at most [maxSide], turned the right way up. */
fun decodeUpright(file: File, maxSide: Int): Bitmap? {
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bmp = android.graphics.BitmapFactory.decodeFile(
        file.absolutePath,
        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    val rotation = try {
        when (android.media.ExifInterface(file.absolutePath).getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL,
        )) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }
    return rotate(bmp, rotation)
}

fun rotate(bmp: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bmp
    val m = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}

/** Loads a picked image (gallery / files), upright and at most [maxSide] px. */
fun loadPicked(context: Context, uri: Uri, maxSide: Int = 2400): Bitmap? = try {
    val tmp = File(context.cacheDir, "picked_${System.currentTimeMillis()}")
    context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
    val bmp = decodeUpright(tmp, maxSide)
    tmp.delete()
    bmp
} catch (e: Exception) {
    null
}

/**
 * Maps a rectangle from an analysed frame (already turned upright, size [imageW]×[imageH]) to the
 * preview on screen ([viewW]×[viewH]), which shows the frame scaled to fill and centred.
 */
fun mapToView(r: android.graphics.Rect, imageW: Int, imageH: Int, viewW: Float, viewH: Float): Rect {
    val s = maxOf(viewW / imageW, viewH / imageH)
    val dx = (viewW - imageW * s) / 2f
    val dy = (viewH - imageH * s) / 2f
    return Rect(r.left * s + dx, r.top * s + dy, r.right * s + dx, r.bottom * s + dy)
}

fun mapPoint(x: Float, y: Float, imageW: Int, imageH: Int, viewW: Float, viewH: Float): Offset {
    val s = maxOf(viewW / imageW, viewH / imageH)
    return Offset(x * s + (viewW - imageW * s) / 2f, y * s + (viewH - imageH * s) / 2f)
}

/** Upright size of an analysed frame. */
fun uprightSize(proxy: ImageProxy): Pair<Int, Int> {
    val rot = proxy.imageInfo.rotationDegrees
    return if (rot % 180 == 0) proxy.width to proxy.height else proxy.height to proxy.width
}

/** Full-screen black camera page with a back button and title floating on top. */
@Composable
fun CameraFrame(
    title: String,
    onBack: () -> Unit,
    topRight: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        content()
        Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 20.r, vertical = 16.r),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIcon(onBack) {
                GlyphIcon(Glyph.ChevronLeft, Color.White, Modifier.size(30.r), strokeWidth = 2.4f)
            }
            Spacer(Modifier.width(14.r))
            RText(title, 24, FontWeight.Bold, Color.White, Modifier.weight(1f))
            topRight()
        }
    }
}

/** Round translucent button used on camera screens. */
@Composable
fun CircleIcon(onClick: () -> Unit, active: Boolean = false, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(58.r)
            .clip(CircleShape)
            .background(if (active) Color(0xFFFBBF24) else Color(0x55000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Dark rounded panel at the bottom of a camera screen. */
@Composable
fun CameraPanel(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xE6101420), androidx.compose.foundation.shape.RoundedCornerShape(topStart = 30.r, topEnd = 30.r))
            .navigationBarsPadding()
            .padding(horizontal = 24.r, vertical = 20.r),
        content = content,
    )
}

/** Big round white shutter. */
@Composable
fun Shutter(onClick: () -> Unit, enabled: Boolean = true) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(92.r)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.r)
            .border(4.r, Color(0xFF101420), CircleShape),
    )
}
