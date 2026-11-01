package com.nirvor.app.ui.tools.camera

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.Inter

/** Bangla names for the most common things the on-device model knows. */
private val BN = mapOf(
    "Person" to "মানুষ", "Hand" to "হাত", "Smile" to "হাসি", "Hair" to "চুল", "Beard" to "দাড়ি", "Eyelash" to "চোখের পাপড়ি",
    "Cat" to "বিড়াল", "Dog" to "কুকুর", "Bird" to "পাখি", "Fish" to "মাছ", "Insect" to "পোকা", "Butterfly" to "প্রজাপতি",
    "Horse" to "ঘোড়া", "Cattle" to "গরু", "Duck" to "হাঁস", "Plant" to "গাছ", "Tree" to "গাছ", "Flower" to "ফুল",
    "Leaf" to "পাতা", "Grass" to "ঘাস", "Food" to "খাবার", "Fruit" to "ফল", "Vegetable" to "সবজি", "Bread" to "রুটি",
    "Cake" to "কেক", "Cookie" to "বিস্কুট", "Egg" to "ডিম", "Juice" to "জুস", "Tea" to "চা", "Coffee" to "কফি",
    "Car" to "গাড়ি", "Bus" to "বাস", "Bicycle" to "সাইকেল", "Motorcycle" to "মোটরসাইকেল", "Boat" to "নৌকা",
    "Train" to "ট্রেন", "Airplane" to "বিমান", "Wheel" to "চাকা", "Building" to "দালান", "House" to "বাড়ি",
    "Bridge" to "সেতু", "Road" to "রাস্তা", "Sky" to "আকাশ", "Cloud" to "মেঘ", "Sunset" to "সূর্যাস্ত", "Water" to "পানি",
    "River" to "নদী", "Lake" to "লেক", "Beach" to "সৈকত", "Mountain" to "পাহাড়", "Chair" to "চেয়ার", "Table" to "টেবিল",
    "Desk" to "ডেস্ক", "Bed" to "বিছানা", "Couch" to "সোফা", "Door" to "দরজা", "Window" to "জানালা", "Curtain" to "পর্দা",
    "Lamp" to "বাতি", "Clock" to "ঘড়ি", "Mobile phone" to "মোবাইল ফোন", "Computer" to "কম্পিউটার", "Laptop" to "ল্যাপটপ",
    "Television" to "টিভি", "Monitor" to "মনিটর", "Keyboard" to "কিবোর্ড", "Book" to "বই", "Paper" to "কাগজ",
    "Pen" to "কলম", "Bag" to "ব্যাগ", "Shoe" to "জুতা", "Sunglasses" to "সানগ্লাস", "Glasses" to "চশমা", "Hat" to "টুপি",
    "Jacket" to "জ্যাকেট", "Jeans" to "জিন্স", "Shirt" to "শার্ট", "Cup" to "কাপ", "Bottle" to "বোতল", "Plate" to "প্লেট",
    "Toy" to "খেলনা", "Umbrella" to "ছাতা", "Flag" to "পতাকা", "Money" to "টাকা", "Musical instrument" to "বাদ্যযন্ত্র",
    "Guitar" to "গিটার", "Ball" to "বল", "Cushion" to "কুশন", "Bathroom" to "বাথরুম", "Kitchen" to "রান্নাঘর",
    "Room" to "ঘর", "Shelf" to "তাক", "Fashion good" to "পোশাক / ফ্যাশন", "Home good" to "ঘরের জিনিস", "Place" to "জায়গা",
)

private fun bn(label: String): String = BN[label]?.let { "$it ($label)" } ?: label

private class Found(val box: Rect, val label: String)

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun objectAnalyzer(
    detector: ObjectDetector,
    labeler: ImageLabeler,
    paused: () -> Boolean,
    onObjects: (List<DetectedObject>, Int, Int) -> Unit,
    onLabels: (List<ImageLabel>) -> Unit,
): ImageAnalysis.Analyzer {
    var lastLabels = 0L
    return ImageAnalysis.Analyzer { proxy ->
        val media = proxy.image
        if (media == null || paused()) {
            proxy.close()
            return@Analyzer
        }
        val (w, h) = uprightSize(proxy)
        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        detector.process(input)
            .addOnSuccessListener { onObjects(it, w, h) }
            .addOnCompleteListener {
                val now = SystemClock.elapsedRealtime()
                if (now - lastLabels > 700) {
                    lastLabels = now
                    labeler.process(input)
                        .addOnSuccessListener { onLabels(it) }
                        .addOnCompleteListener { proxy.close() }
                } else {
                    proxy.close()
                }
            }
    }
}

/** Live object detection: boxes around things in view plus what the picture shows, in Bangla. */
@Composable
fun ObjectsTool(onBack: () -> Unit) {
    CameraGate("Object Detection", "জিনিস চেনা", onBack) {
        var paused by remember { mutableStateOf(false) }
        var found by remember { mutableStateOf<List<Found>>(emptyList()) }
        var labels by remember { mutableStateOf<List<ImageLabel>>(emptyList()) }
        var viewSize by remember { mutableStateOf(Size.Zero) }
        val detector = remember {
            ObjectDetection.getClient(
                ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .build()
            )
        }
        val labeler = remember {
            ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build())
        }
        DisposableEffect(Unit) {
            onDispose {
                detector.close()
                labeler.close()
            }
        }
        val analyzer = remember {
            objectAnalyzer(
                detector,
                labeler,
                paused = { paused },
                onObjects = { list, w, h ->
                    val vs = viewSize
                    if (vs.width > 0) {
                        found = list.map { o ->
                            Found(
                                mapToView(o.boundingBox, w, h, vs.width, vs.height),
                                o.labels.maxByOrNull { it.confidence }?.text?.let { bn(it) } ?: "জিনিস",
                            )
                        }
                    }
                },
                onLabels = { labels = it.sortedByDescending { l -> l.confidence }.take(6) },
            )
        }
        val tm = rememberTextMeasurer()
        val chipStyle = TextStyle(fontFamily = Inter, fontSize = 15.rs, color = Color.White, fontWeight = FontWeight.SemiBold)

        CameraFrame("Object Detection", onBack) {
            CameraView(
                Modifier.fillMaxSize().onSizeChanged { viewSize = Size(it.width.toFloat(), it.height.toFloat()) },
                analyzer = analyzer,
            )
            Canvas(Modifier.fillMaxSize()) {
                found.forEach { f ->
                    drawRoundRect(Color(0xFF34D399), f.box.topLeft, f.box.size, CornerRadius(18f, 18f), style = Stroke(6f))
                    val layout = tm.measure(f.label, chipStyle)
                    val top = (f.box.top - layout.size.height - 14f).coerceAtLeast(0f)
                    drawRoundRect(
                        Color(0xE60BA765),
                        Offset(f.box.left, top),
                        Size(layout.size.width + 24f, layout.size.height + 10f),
                        CornerRadius(14f, 14f),
                    )
                    drawText(layout, topLeft = Offset(f.box.left + 12f, top + 5f))
                }
            }
            CameraPanel(Modifier.align(Alignment.BottomCenter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RText("👀 যা দেখছি", 20, FontWeight.SemiBold, Color.White, Modifier.weight(1f))
                    PanelButton(if (paused) "▶ চালু" else "⏸ থামান") { paused = !paused }
                }
                Spacer(Modifier.height(10.r))
                if (labels.isEmpty()) {
                    RText("ক্যামেরা কোনো জিনিসের দিকে ধরুন…", 16, FontWeight.Normal, Color(0xFFB8C2D0))
                }
                labels.forEach { l ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.r), verticalAlignment = Alignment.CenterVertically) {
                        RText(bn(l.text), 18, FontWeight.Medium, Color.White, Modifier.weight(1f))
                        Box(
                            Modifier
                                .width(120.r)
                                .height(10.r)
                                .background(Color(0x33FFFFFF), RoundedCornerShape(50)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(l.confidence.coerceIn(0f, 1f))
                                    .height(10.r)
                                    .background(Color(0xFF34D399), RoundedCornerShape(50)),
                            )
                        }
                        Spacer(Modifier.width(10.r))
                        RText("${(l.confidence * 100).toInt()}%", 15, FontWeight.Medium, Color(0xFFB8C2D0))
                    }
                }
            }
        }
    }
}
