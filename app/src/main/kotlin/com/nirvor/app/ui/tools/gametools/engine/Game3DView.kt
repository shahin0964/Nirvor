package com.nirvor.app.ui.tools.gametools.engine

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

interface Touch3DListener {
    fun onTouchDown(worldX: Float, worldZ: Float, screenX: Float, screenY: Float): Boolean
    fun onTouchMove(worldX: Float, worldZ: Float, screenX: Float, screenY: Float)
    fun onTouchUp(worldX: Float, worldZ: Float, screenX: Float, screenY: Float)
}

class InteractiveGLSurfaceView(
    context: Context,
    val renderer: Renderer3D,
    var touchListener: Touch3DListener? = null,
    var allowCameraOrbit: Boolean = true,
) : GLSurfaceView(context) {

    private var previousX: Float = 0f
    private var previousY: Float = 0f
    private var isInteractingWithPiece: Boolean = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            renderer.cameraDistance = (renderer.cameraDistance / scaleFactor).coerceIn(6f, 32f)
            return true
        }
    })

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = renderer.raycastPlaneY(x, y, 0f)
                isInteractingWithPiece = if (hit != null) {
                    touchListener?.onTouchDown(hit[0], hit[2], x, y) ?: false
                } else false

                previousX = x
                previousY = y
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    if (isInteractingWithPiece) {
                        val hit = renderer.raycastPlaneY(x, y, 0f)
                        if (hit != null) {
                            touchListener?.onTouchMove(hit[0], hit[2], x, y)
                        }
                    } else if (allowCameraOrbit) {
                        val dx = x - previousX
                        val dy = y - previousY
                        renderer.cameraYaw = (renderer.cameraYaw + dx * 0.4f) % 360f
                        renderer.cameraPitch = (renderer.cameraPitch - dy * 0.3f).coerceIn(20f, 85f)
                    }
                }
                previousX = x
                previousY = y
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isInteractingWithPiece) {
                    val hit = renderer.raycastPlaneY(x, y, 0f)
                    val hx = hit?.get(0) ?: 0f
                    val hz = hit?.get(2) ?: 0f
                    touchListener?.onTouchUp(hx, hz, x, y)
                    isInteractingWithPiece = false
                }
            }
        }
        return true
    }
}

@Composable
fun Game3DCanvas(
    renderer: Renderer3D,
    modifier: Modifier = Modifier,
    touchListener: Touch3DListener? = null,
    allowCameraOrbit: Boolean = true,
) {
    val context = LocalContext.current
    val glView = remember(renderer) {
        InteractiveGLSurfaceView(context, renderer, touchListener, allowCameraOrbit)
    }

    // Keep listener updated
    glView.touchListener = touchListener
    glView.allowCameraOrbit = allowCameraOrbit

    DisposableEffect(glView) {
        glView.onResume()
        onDispose {
            glView.onPause()
        }
    }

    AndroidView(
        factory = { glView },
        modifier = modifier,
    )
}
