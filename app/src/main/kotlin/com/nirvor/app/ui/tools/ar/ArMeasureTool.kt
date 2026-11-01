package com.nirvor.app.ui.tools.ar

import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.nirvor.app.ar.ArFrame
import com.nirvor.app.ar.ArMath
import com.nirvor.app.ar.ArMode
import com.nirvor.app.ar.MeasureRenderer
import com.nirvor.app.ar.Px
import com.nirvor.app.tools.KeepScreenOn
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.browser.findActivity
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.ToolScaffold
import com.nirvor.app.ui.tools.camera.CameraFrame
import com.nirvor.app.ui.tools.camera.CameraGate
import com.nirvor.app.ui.tools.camera.CameraPanel
import com.nirvor.app.ui.tools.camera.PanelButton
import kotlinx.coroutines.delay
import kotlin.math.hypot

private enum class ArStatus { Checking, Ready, NeedInstall, Installing, Unsupported }

/**
 * AR measuring: aim the centre circle at a floor / table / wall, press + for each point and
 * the lines, lengths, angles and areas appear live on the camera picture. Phones without
 * ARCore get the camera + tilt method for distance and height.
 */
@Composable
fun ArMeasureTool(mode: ArMode, onBack: () -> Unit) {
    CameraGate(mode.title, mode.bn, onBack) {
        val context = LocalContext.current
        var status by remember { mutableStateOf(ArStatus.Checking) }
        var error by remember { mutableStateOf<String?>(null) }
        var tilt by remember { mutableStateOf(false) }

        fun check() {
            val a = ArCoreApk.getInstance().checkAvailability(context)
            status = when {
                a == ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArStatus.Ready
                a == ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD || a == ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED ->
                    if (status == ArStatus.Installing) ArStatus.Installing else ArStatus.NeedInstall
                a.isTransient -> ArStatus.Checking
                else -> ArStatus.Unsupported
            }
        }

        LaunchedEffect(Unit) {
            repeat(25) {
                check()
                if (status != ArStatus.Checking) return@LaunchedEffect
                delay(300)
            }
            if (status == ArStatus.Checking) status = ArStatus.Unsupported
        }
        // Back from the Play Store after installing "Google Play Services for AR".
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
            if (status == ArStatus.Installing || status == ArStatus.NeedInstall) {
                val activity = context.findActivity()
                if (activity != null) {
                    try {
                        if (ArCoreApk.getInstance().requestInstall(activity, false) == ArCoreApk.InstallStatus.INSTALLED) {
                            status = ArStatus.Ready
                        }
                    } catch (e: Exception) {
                        // still not installed
                    }
                }
            }
        }

        if (tilt) {
            TiltMeasureTool(if (mode == ArMode.Height) TiltGoal.Height else TiltGoal.Distance) { tilt = false }
        } else when (status) {
            ArStatus.Ready -> if (error == null) {
                ArScreen(mode, onBack, onTilt = if (mode == ArMode.Distance || mode == ArMode.Height) ({ tilt = true }) else null) { error = it }
            } else {
                Message(mode, onBack, "AR চালু করা গেল না", error ?: "", fallback = true) { tilt = true }
            }
            ArStatus.Checking -> Message(mode, onBack, "দেখছি এই ফোনে AR চলে কিনা…", "", fallback = false) { tilt = true }
            ArStatus.NeedInstall, ArStatus.Installing -> ToolScaffold(mode.title, mode.bn, onBack) {
                ListCard {
                    RText("📦 Google Play Services for AR লাগবে", 21, FontWeight.SemiBold, C.Ink, maxLines = 2)
                    Spacer(Modifier.height(6.r))
                    RText(
                        "ক্যামেরা দিয়ে আসল মাপ নিতে Google-এর ফ্রি AR অ্যাপটা (ARCore) একবার ইনস্টল করতে হবে। ইনস্টল হলে এখানে ফিরে আসুন।",
                        17,
                        FontWeight.Normal,
                        C.StatLabel,
                        maxLines = 4,
                        lineHeight = 25,
                    )
                    Spacer(Modifier.height(16.r))
                    RoundButton("ইনস্টল করুন", TGreen, Modifier.fillMaxWidth()) {
                        val activity = context.findActivity() ?: return@RoundButton
                        try {
                            when (ArCoreApk.getInstance().requestInstall(activity, true)) {
                                ArCoreApk.InstallStatus.INSTALLED -> status = ArStatus.Ready
                                else -> status = ArStatus.Installing
                            }
                        } catch (e: Exception) {
                            status = ArStatus.Unsupported
                        }
                    }
                }
                if (mode.tiltable()) TiltCard { tilt = true } else FallbackCard(mode)
            }
            ArStatus.Unsupported -> Message(
                mode,
                onBack,
                "এই ফোনে AR (ARCore) চলে না",
                "ক্যামেরায় পয়েন্ট বসিয়ে মাপতে Google-এর AR সাপোর্ট লাগে।",
                fallback = true,
            ) { tilt = true }
        }
    }
}

@Composable
private fun Message(mode: ArMode, onBack: () -> Unit, title: String, text: String, fallback: Boolean, onTilt: () -> Unit) {
    ToolScaffold(mode.title, mode.bn, onBack) {
        ListCard {
            RText(title, 21, FontWeight.SemiBold, C.Ink, maxLines = 2)
            if (text.isNotEmpty()) {
                Spacer(Modifier.height(6.r))
                RText(text, 17, FontWeight.Normal, C.StatLabel, maxLines = 4, lineHeight = 25)
            }
        }
        if (fallback) {
            if (mode.tiltable()) TiltCard(onTilt) else FallbackCard(mode)
        }
    }
}

private fun ArMode.tiltable() = this == ArMode.Distance || this == ArMode.Height || this == ArMode.Points

/** Offers the camera + tilt method, which works without AR. */
@Composable
private fun TiltCard(onTilt: () -> Unit) {
    ListCard {
        RText("📐 ক্যামেরা + ফোনের কোণ দিয়ে মাপুন", 19, FontWeight.SemiBold, C.Ink)
        Spacer(Modifier.height(6.r))
        RText(
            "ফোন কত উঁচুতে ধরে আছেন সেটা দিয়ে, জিনিসের গোড়া (আর উচ্চতার জন্য মাথা) ক্যামেরায় তাক করে দূরত্ব ও উচ্চতা বের করা যায়। AR ছাড়াই সব ফোনে চলে।",
            17,
            FontWeight.Normal,
            C.StatLabel,
            maxLines = 5,
            lineHeight = 25,
        )
        Spacer(Modifier.height(14.r))
        RoundButton("চালু করুন", TGreen, Modifier.fillMaxWidth(), onClick = onTilt)
    }
}

@Composable
private fun FallbackCard(mode: ArMode) {
    if (mode == ArMode.Distance || mode == ArMode.Height) return
    ListCard {
        RText(
            "এই মাপের জন্য AR লাগে। AR না থাকলে Tool Box-এর Distance বা Height Measure (ক্যামেরা + কোণ) দিয়ে দূরত্ব ও উচ্চতা মাপতে পারবেন, আর Screen Ruler দিয়ে ছোট জিনিস।",
            16,
            FontWeight.Normal,
            C.Hint,
            maxLines = 5,
            lineHeight = 23,
        )
    }
}

/** The live AR camera with the measuring overlay. */
@Composable
private fun ArScreen(mode: ArMode, onBack: () -> Unit, onTilt: (() -> Unit)?, onError: (String) -> Unit) {
    KeepScreenOn()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    var frame by remember { mutableStateOf(ArFrame()) }
    var renderer by remember { mutableStateOf<MeasureRenderer?>(null) }
    val glView = remember { GLSurfaceView(context) }
    val main = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(Unit) {
        val session = try {
            Session(context).also { s ->
                val config = Config(s)
                config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL)
                config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE)
                config.setFocusMode(Config.FocusMode.AUTO)
                config.setLightEstimationMode(Config.LightEstimationMode.DISABLED)
                if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) config.setDepthMode(Config.DepthMode.AUTOMATIC)
                s.configure(config)
            }
        } catch (e: Exception) {
            onError("এই ফোনে AR সেশন শুরু করা গেল না।")
            null
        }
        if (session == null) {
            onDispose { }
        } else {
            @Suppress("DEPRECATION")
            val rotation = { (context.getSystemService(WindowManager::class.java))?.defaultDisplay?.rotation ?: 0 }
            val r = MeasureRenderer(session, mode, rotation) { f -> main.post { frame = f } }
            renderer = r
            glView.preserveEGLContextOnPause = true
            glView.setEGLContextClientVersion(2)
            glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            glView.setRenderer(r)
            glView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> try {
                        session.resume()
                        glView.onResume()
                    } catch (e: Exception) {
                        onError("ক্যামেরা চালু করা গেল না। অন্য কোনো অ্যাপ ক্যামেরা ব্যবহার করছে কিনা দেখুন।")
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        glView.onPause()
                        session.pause()
                    }
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose {
                owner.lifecycle.removeObserver(observer)
                glView.onPause()
                session.pause()
                session.close()
                main.removeCallbacksAndMessages(null)
            }
        }
    }

    val tm = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Inter, fontSize = 16.rs, color = Color.White, fontWeight = FontWeight.Bold)
    // Area / Point-to-Point: "✓ শেষ" (or aiming back at the first corner) ends the shape, so the
    // live aim point stops being part of the result.
    var done by remember { mutableStateOf(false) }
    val raw = frame
    val canFinish = !done && raw.world.size >= when (mode) {
        ArMode.Area -> 3
        ArMode.Points -> 2
        else -> Int.MAX_VALUE
    }
    val nearFirst = mode == ArMode.Area && canFinish && run {
        val a = raw.aim
        val p0 = raw.points.firstOrNull()
        a != null && p0 != null && hypot(a.x - p0.x, a.y - p0.y) < 70f
    }
    val f = if (done || nearFirst) raw.withoutAim() else raw

    CameraFrame(
        mode.title,
        onBack,
        topRight = {
            if (onTilt != null) PanelButton("📐 কোণ দিয়ে", onTilt)
        },
    ) {
        // the GL view only joins the screen once its renderer is set
        if (renderer != null) AndroidView({ glView }, Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) { drawOverlay(f, mode, tm, labelStyle) }
        if (f.hint.isNotEmpty() || f.error != null) {
            RText(
                f.error ?: f.hint,
                17,
                FontWeight.SemiBold,
                Color.White,
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 96.r, start = 30.r, end = 30.r)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xAA000000))
                    .padding(horizontal = 18.r, vertical = 10.r),
                maxLines = 2,
                align = TextAlign.Center,
            )
        }
        CameraPanel(Modifier.align(Alignment.BottomCenter)) {
            val (big, small) = when {
                nearFirst -> readout(f, mode).first to "প্রথম কোণায় ফিরে এসেছেন — + চাপলে জায়গাটা বন্ধ হবে"
                done -> readout(f, mode).let { (b, sm) -> b to sm + " · নতুন মাপের জন্য +" }
                else -> readout(f, mode)
            }
            RText(big, 30, FontWeight.Bold, Color.White, maxLines = 2)
            if (small.isNotEmpty()) {
                Spacer(Modifier.height(4.r))
                RText(small, 16, FontWeight.Medium, Color(0xFFB8C2D0), maxLines = 3, lineHeight = 22)
            }
            Spacer(Modifier.height(16.r))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                PanelButton("↩️ ফেরত") {
                    if (done) done = false else renderer?.wantUndo = true
                }
                Box(
                    Modifier
                        .size(92.r)
                        .clip(CircleShape)
                        .background(
                            if (raw.aimOk || done || nearFirst || (mode == ArMode.Height && raw.world.isNotEmpty())) Color(0xFF0BA765) else Color(0x55FFFFFF)
                        )
                        .clickable {
                            val r = renderer
                            when {
                                r == null -> Unit
                                done -> {
                                    // start a new shape from the point in the circle
                                    r.wantClear = true
                                    r.wantAdd = true
                                    done = false
                                }
                                nearFirst -> done = true
                                else -> r.wantAdd = true
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        if (mode == ArMode.Height && raw.world.isNotEmpty() && !raw.heightFixed) Glyph.Check else Glyph.Plus,
                        Color.White,
                        Modifier.size(46.r),
                        strokeWidth = 3f,
                    )
                }
                if (canFinish) PanelButton("✓ শেষ") { done = true }
                PanelButton("🗑 মুছুন") {
                    done = false
                    renderer?.wantClear = true
                }
            }
        }
    }
}

/** The same frame without the live aim point (the shape is finished). */
private fun ArFrame.withoutAim() = ArFrame(
    tracking = tracking,
    hint = hint,
    aimOk = aimOk,
    surface = surface,
    points = points,
    world = world,
    aim = null,
    aimWorld = null,
    heightBase = heightBase,
    heightTop = heightTop,
    height = height,
    heightFixed = heightFixed,
    error = error,
)

/** Main number + explanation for the bottom panel. */
private fun readout(f: ArFrame, mode: ArMode): Pair<String, String> {
    val w = f.world
    val aim = f.aimWorld
    return when (mode) {
        ArMode.Distance -> {
            val open = w.size % 2 == 1
            val segs = w.chunked(2).filter { it.size == 2 }.map { ArMath.dist(it[0], it[1]) }
            when {
                open && aim != null -> ArMath.dist(w.last(), aim).let { ArMath.len(it) to "${ArMath.feet(it)} · দ্বিতীয় পয়েন্টে + চাপুন" }
                segs.isNotEmpty() -> ArMath.len(segs.last()) to "${ArMath.feet(segs.last())} · নতুন মাপের জন্য আবার + চাপুন"
                else -> "শুরু করুন" to "মাঝের গোলটা প্রথম জায়গায় রেখে + চাপুন, তারপর দ্বিতীয় জায়গায়"
            }
        }
        ArMode.Points -> {
            val pts = if (aim != null && w.isNotEmpty()) w + listOf(aim) else w
            val total = ArMath.pathLength(pts)
            if (w.isEmpty()) "শুরু করুন" to "প্রতিটা জায়গায় + চাপুন — সব লাইনের মাপ আর মোট দূরত্ব দেখাবে"
            else "মোট " + ArMath.len(total) to "${w.size} টা পয়েন্ট · ${ArMath.feet(total)}"
        }
        ArMode.Angle -> {
            val pts = if (w.size < 3 && aim != null) w + listOf(aim) else w
            if (pts.size >= 3) {
                String.format(java.util.Locale.US, "%.1f°", ArMath.angle(pts[0], pts[1], pts[2])) to
                    if (w.size >= 3) "কোণ মাপা হয়েছে · আবার মাপতে 🗑 চাপুন" else "তৃতীয় পয়েন্টে + চাপুন"
            } else {
                "কোণ" to when (w.size) {
                    0 -> "প্রথম বাহুর মাথায় + চাপুন"
                    1 -> "এবার কোণের কোণাটায় (মাঝের বিন্দু) + চাপুন"
                    else -> "দ্বিতীয় বাহুর মাথায় + চাপুন"
                }
            }
        }
        ArMode.Area, ArMode.Wall -> {
            val pts = if (aim != null && w.size < mode.maxPoints) w + listOf(aim) else w
            val a = ArMath.area(pts)
            if (pts.size >= 3) {
                val sides = if (mode == ArMode.Wall && pts.size == 4) {
                    "চওড়া ${ArMath.len(ArMath.dist(pts[0], pts[1]))} · উঁচু ${ArMath.len(ArMath.dist(pts[1], pts[2]))}\n"
                } else {
                    ""
                }
                ArMath.area(a) to sides + "চারপাশ ${ArMath.len(ArMath.pathLength(pts + listOf(pts[0])))} · ${w.size} টা কোণা"
            } else {
                (if (mode == ArMode.Wall) "দেয়াল / মেঝে" else "এরিয়া") to
                    (if (mode == ArMode.Wall) "চার কোণায় পর পর + চাপুন (উপর-বাম থেকে ঘড়ির কাঁটার দিকে)" else "জায়গাটার প্রতিটা কোণায় পর পর + চাপুন")
            }
        }
        ArMode.Height -> {
            val h = f.height
            when {
                w.isEmpty() -> "উচ্চতা" to "জিনিসটার গোড়ায় (মেঝেতে) মাঝের গোল রেখে + চাপুন"
                h == null -> "উচ্চতা" to "ফোনটা জিনিসটার মাথার দিকে তুলুন"
                f.heightFixed -> ArMath.len(h) to "${ArMath.feet(h)} · নতুন মাপের জন্য + চাপুন"
                else -> ArMath.len(h) to "${ArMath.feet(h)} · মাথা পর্যন্ত তুলে ✓ চাপুন"
            }
        }
    }
}

private fun Px.o() = Offset(x, y)

private fun DrawScope.label(tm: TextMeasurer, style: TextStyle, text: String, at: Offset) {
    val layout = tm.measure(text, style)
    val w = layout.size.width + 24f
    val h = layout.size.height + 10f
    drawRoundRect(
        Color(0xE60BA765),
        Offset(at.x - w / 2f, at.y - h / 2f),
        Size(w, h),
        androidx.compose.ui.geometry.CornerRadius(h / 2f, h / 2f),
    )
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}

private fun DrawScope.segment(a: Offset, b: Offset, dashed: Boolean = false) {
    drawLine(Color(0x66000000), a, b, 10f, cap = StrokeCap.Round)
    drawLine(
        Color.White,
        a,
        b,
        6f,
        cap = StrokeCap.Round,
        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(18f, 12f)) else null,
    )
}

private fun DrawScope.dot(p: Offset) {
    drawCircle(Color(0x66000000), 20f, p)
    drawCircle(Color.White, 15f, p)
    drawCircle(Color(0xFF0BA765), 9f, p)
}

private fun DrawScope.drawOverlay(f: ArFrame, mode: ArMode, tm: TextMeasurer, style: TextStyle) {
    val pts = f.points
    val w = f.world
    val aim = f.aim?.o()
    when (mode) {
        ArMode.Distance -> {
            for (i in 0 until pts.size - 1 step 2) {
                val a = pts[i]?.o()
                val b = pts[i + 1]?.o()
                if (a != null && b != null) {
                    segment(a, b)
                    label(tm, style, ArMath.len(ArMath.dist(w[i], w[i + 1])), Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f))
                }
            }
            if (pts.size % 2 == 1 && aim != null && f.aimWorld != null) {
                pts.last()?.o()?.let { a ->
                    segment(a, aim, dashed = true)
                    label(tm, style, ArMath.len(ArMath.dist(w.last(), f.aimWorld)), Offset((a.x + aim.x) / 2f, (a.y + aim.y) / 2f))
                }
            }
        }
        ArMode.Points, ArMode.Angle, ArMode.Area, ArMode.Wall -> {
            val closed = (mode == ArMode.Area || mode == ArMode.Wall)
            if (closed && pts.size >= 3 && pts.all { it != null }) {
                val path = Path().apply {
                    moveTo(pts[0]!!.x, pts[0]!!.y)
                    pts.drop(1).forEach { lineTo(it!!.x, it.y) }
                    if (aim != null && w.size < mode.maxPoints) lineTo(aim.x, aim.y)
                    close()
                }
                drawPath(path, Color(0x330BA765))
            }
            for (i in 0 until pts.size - 1) {
                val a = pts[i]?.o()
                val b = pts[i + 1]?.o()
                if (a != null && b != null) {
                    segment(a, b)
                    if (mode != ArMode.Angle) {
                        label(tm, style, ArMath.len(ArMath.dist(w[i], w[i + 1])), Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f))
                    }
                }
            }
            val last = pts.lastOrNull()?.o()
            if (last != null && aim != null && f.aimWorld != null && w.size < mode.maxPoints) {
                segment(last, aim, dashed = true)
                if (mode != ArMode.Angle) {
                    label(tm, style, ArMath.len(ArMath.dist(w.last(), f.aimWorld)), Offset((last.x + aim.x) / 2f, (last.y + aim.y) / 2f))
                }
                if (closed && pts.size >= 2) pts.first()?.o()?.let { segment(aim, it, dashed = true) }
            }
            if (closed && w.size == mode.maxPoints || (closed && w.size >= 3 && aim == null)) {
                val a = pts.lastOrNull()?.o()
                val b = pts.firstOrNull()?.o()
                if (a != null && b != null) {
                    segment(a, b)
                    label(tm, style, ArMath.len(ArMath.dist(w.last(), w.first())), Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f))
                }
            }
            if (mode == ArMode.Angle && pts.size >= 2) {
                val all = if (w.size < 3 && f.aimWorld != null) w + listOf(f.aimWorld) else w
                val vertex = pts[1]?.o()
                if (all.size >= 3 && vertex != null) {
                    label(tm, style, String.format(java.util.Locale.US, "%.1f°", ArMath.angle(all[0], all[1], all[2])), Offset(vertex.x, vertex.y - 60f))
                }
            }
        }
        ArMode.Height -> {
            val base = f.heightBase?.o()
            val top = f.heightTop?.o()
            if (base != null && top != null) {
                segment(base, top)
                dot(top)
                f.height?.let { label(tm, style, ArMath.len(it), Offset(top.x, top.y - 50f)) }
            }
        }
    }
    pts.forEach { p -> p?.let { dot(it.o()) } }
    // aim circle
    val c = center
    val col = if (f.aimOk) Color(0xFF34D399) else Color(0xFFFFFFFF)
    drawCircle(Color(0x66000000), 44f, c, style = Stroke(10f))
    drawCircle(col, 44f, c, style = Stroke(5f))
    drawCircle(col, 6f, c)
    if (f.surface.isNotEmpty()) {
        val layout = tm.measure(f.surface, style.copy(fontSize = style.fontSize * 0.85f))
        drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y + 56f))
    }
}
