package com.nirvor.app.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.google.ar.core.Anchor
import com.google.ar.core.DepthPoint
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** A point on screen (pixels of the AR view). */
class Px(val x: Float, val y: Float)

/** What the overlay draws for one frame. Positions are screen pixels; world values are metres. */
class ArFrame(
    val tracking: Boolean = false,
    val hint: String = "ফোনটা ধীরে ধীরে এদিক-ওদিক নাড়ান, মেঝে বা দেয়াল চিনে নিচ্ছে…",
    val aimOk: Boolean = false,
    val surface: String = "",
    val points: List<Px?> = emptyList(),
    val world: List<FloatArray> = emptyList(),
    val aim: Px? = null,
    val aimWorld: FloatArray? = null,
    /** Height mode: base on screen, top of the measuring line, height in metres. */
    val heightBase: Px? = null,
    val heightTop: Px? = null,
    val height: Float? = null,
    val heightFixed: Boolean = false,
    val error: String? = null,
)

/**
 * Renders the camera and works out, every frame, where the placed points and the aim point
 * are on screen. Buttons on the UI thread only set flags; the GL thread does the ARCore work.
 */
class MeasureRenderer(
    private val session: Session,
    private val mode: ArMode,
    private val rotation: () -> Int,
    private val publish: (ArFrame) -> Unit,
) : GLSurfaceView.Renderer {
    private val background = BackgroundRenderer()
    private val anchors = mutableListOf<Anchor>()
    private var width = 1
    private var height = 1
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProj = FloatArray(16)
    private var fixedHeight: Float? = null

    @Volatile var wantAdd = false
    @Volatile var wantUndo = false
    @Volatile var wantClear = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
        session.setCameraTextureName(background.textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
        session.setDisplayGeometry(rotation(), w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val frame = try {
            session.update()
        } catch (e: Exception) {
            publish(ArFrame(error = "ক্যামেরা চালু করা গেল না। অন্য অ্যাপ বন্ধ করে আবার চেষ্টা করুন।"))
            return
        }
        background.draw(frame)

        if (wantClear) {
            wantClear = false
            anchors.forEach { it.detach() }
            anchors.clear()
            fixedHeight = null
        }
        if (wantUndo) {
            wantUndo = false
            if (mode == ArMode.Height && fixedHeight != null) {
                fixedHeight = null
            } else if (anchors.isNotEmpty()) {
                anchors.removeAt(anchors.size - 1).detach()
                fixedHeight = null
            }
        }

        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            wantAdd = false
            publish(ArFrame(tracking = false, hint = reason(camera.trackingFailureReason)))
            return
        }
        camera.getProjectionMatrix(proj, 0, 0.05f, 100f)
        camera.getViewMatrix(view, 0)
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)

        val hit = bestHit(frame.hitTest(width / 2f, height / 2f))

        if (wantAdd) {
            wantAdd = false
            if (mode == ArMode.Height) {
                when {
                    anchors.isEmpty() -> hit?.let { anchors.add(it.createAnchor()) }
                    fixedHeight == null -> fixedHeight = currentHeight(camera)
                    else -> {
                        // start a new measurement
                        anchors.forEach { it.detach() }
                        anchors.clear()
                        fixedHeight = null
                        hit?.let { anchors.add(it.createAnchor()) }
                    }
                }
            } else if (hit != null && anchors.size < mode.maxPoints) {
                anchors.add(hit.createAnchor())
            }
        }

        val world = anchors.filter { it.trackingState != TrackingState.STOPPED }.map {
            val p = it.pose
            floatArrayOf(p.tx(), p.ty(), p.tz())
        }
        val aimWorld = hit?.hitPose?.let { floatArrayOf(it.tx(), it.ty(), it.tz()) }

        var base: Px? = null
        var top: Px? = null
        var h: Float? = null
        if (mode == ArMode.Height && world.isNotEmpty()) {
            val b = world[0]
            h = fixedHeight ?: currentHeight(camera)
            base = project(b)
            val hh = h
            if (hh != null) top = project(floatArrayOf(b[0], b[1] + hh, b[2]))
        }

        publish(
            ArFrame(
                tracking = true,
                hint = when {
                    hit == null -> "মাঝের গোলটা মেঝে, টেবিল বা দেয়ালের দিকে ধরুন"
                    else -> ""
                },
                aimOk = hit != null,
                surface = surfaceName(hit),
                points = world.map { project(it) },
                world = world,
                aim = if (hit != null) Px(width / 2f, height / 2f) else null,
                aimWorld = aimWorld,
                heightBase = base,
                heightTop = top,
                height = h,
                heightFixed = fixedHeight != null,
            )
        )
    }

    /** Height above the base where the centre of the screen points. */
    private fun currentHeight(camera: com.google.ar.core.Camera): Float? {
        val base = anchors.firstOrNull()?.pose ?: return null
        val pose = camera.displayOrientedPose
        val z = pose.zAxis
        val t = ArMath.heightOnRay(
            floatArrayOf(base.tx(), base.ty(), base.tz()),
            floatArrayOf(pose.tx(), pose.ty(), pose.tz()),
            floatArrayOf(-z[0], -z[1], -z[2]),
        ) ?: return null
        return t.coerceIn(0f, 50f)
    }

    private fun bestHit(hits: List<HitResult>): HitResult? = hits.firstOrNull { h ->
        when (val t = h.trackable) {
            is Plane -> t.trackingState == TrackingState.TRACKING && t.isPoseInPolygon(h.hitPose)
            is DepthPoint -> true
            is Point -> t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
            else -> false
        }
    }

    private fun surfaceName(hit: HitResult?): String {
        val t = hit?.trackable ?: return ""
        return if (t is Plane) {
            when (t.type) {
                Plane.Type.HORIZONTAL_UPWARD_FACING -> "মেঝে / টেবিল"
                Plane.Type.HORIZONTAL_DOWNWARD_FACING -> "ছাদ"
                Plane.Type.VERTICAL -> "দেয়াল"
                else -> "তল"
            }
        } else {
            "তল"
        }
    }

    private fun reason(r: TrackingFailureReason): String = when (r) {
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "আলো কম — আরেকটু আলোতে যান"
        TrackingFailureReason.EXCESSIVE_MOTION -> "ফোন খুব দ্রুত নড়ছে — আস্তে নাড়ান"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "সাদামাটা জায়গা — নকশা বা জিনিস আছে এমন দিকে ধরুন"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "ক্যামেরা পাওয়া যাচ্ছে না"
        else -> "ফোনটা ধীরে ধীরে এদিক-ওদিক নাড়ান, জায়গাটা চিনে নিচ্ছে…"
    }

    private fun project(p: FloatArray): Px? {
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, viewProj, 0, floatArrayOf(p[0], p[1], p[2], 1f), 0)
        if (out[3] <= 1e-4f) return null
        val x = out[0] / out[3]
        val y = out[1] / out[3]
        return Px((x + 1f) / 2f * width, (1f - y) / 2f * height)
    }
}
