package com.nirvor.app.ui.tools.gametools.engine

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

open class Renderer3D : GLSurfaceView.Renderer {

    protected val mvpMatrix = FloatArray(16)
    protected val projMatrix = FloatArray(16)
    protected val viewMatrix = FloatArray(16)
    protected val modelMatrix = FloatArray(16)
    protected val mvMatrix = FloatArray(16)
    protected val invMvpMatrix = FloatArray(16)

    var width: Int = 1
        private set
    var height: Int = 1
        private set

    // Camera Orbit Controls
    var cameraYaw: Float = 0f
    var cameraPitch: Float = 55f // Angled top-down perspective
    var cameraDistance: Float = 14f
    var cameraTargetX: Float = 0f
    var cameraTargetY: Float = 0f
    var cameraTargetZ: Float = 0f

    // Light Source in 3D Space
    val lightPos = floatArrayOf(5f, 15f, 8f)

    private var program: Int = 0
    private var uMVPMatrixHandle: Int = 0
    private var uMVMatrixHandle: Int = 0
    private var uLightPosHandle: Int = 0
    private var aPositionHandle: Int = 0
    private var aNormalHandle: Int = 0
    private var aColorHandle: Int = 0

    var clearColor = floatArrayOf(0.965f, 0.980f, 0.988f, 1.0f) // Matches C.Sheet (0xFFF6FAFC)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3])
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        program = ShaderUtils.createProgram(
            ShaderUtils.VERTEX_SHADER_LIGHTING,
            ShaderUtils.FRAGMENT_SHADER_LIGHTING
        )

        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        uMVMatrixHandle = GLES20.glGetUniformLocation(program, "uMVMatrix")
        uLightPosHandle = GLES20.glGetUniformLocation(program, "uLightPos")
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aNormalHandle = GLES20.glGetAttribLocation(program, "aNormal")
        aColorHandle = GLES20.glGetAttribLocation(program, "aColor")

        onSceneInit()
    }

    open fun onSceneInit() {}

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 45f, ratio, 0.5f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        // Compute Camera Position from Orbit Parameters
        val pitchRad = Math.toRadians(cameraPitch.toDouble()).toFloat()
        val yawRad = Math.toRadians(cameraYaw.toDouble()).toFloat()

        val eyeX = cameraTargetX + cameraDistance * cos(pitchRad) * sin(yawRad)
        val eyeY = cameraTargetY + cameraDistance * sin(pitchRad)
        val eyeZ = cameraTargetZ + cameraDistance * cos(pitchRad) * cos(yawRad)

        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,
            cameraTargetX, cameraTargetY, cameraTargetZ,
            0f, 1f, 0f
        )

        GLES20.glUniform3fv(uLightPosHandle, 1, lightPos, 0)

        onRenderScene()
    }

    open fun onRenderScene() {}

    /**
     * Renders a 3D mesh with the given model transform matrix.
     */
    fun drawMesh(mesh: Mesh, model: FloatArray) {
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, model, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvMatrix, 0)

        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uMVMatrixHandle, 1, false, mvMatrix, 0)

        val stride = 10 * 4 // 10 floats per vertex (pos: 3, norm: 3, col: 4)

        mesh.vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, stride, mesh.vertexBuffer)

        mesh.vertexBuffer.position(3)
        GLES20.glEnableVertexAttribArray(aNormalHandle)
        GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, stride, mesh.vertexBuffer)

        mesh.vertexBuffer.position(6)
        GLES20.glEnableVertexAttribArray(aColorHandle)
        GLES20.glVertexAttribPointer(aColorHandle, 4, GLES20.GL_FLOAT, false, stride, mesh.vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aNormalHandle)
        GLES20.glDisableVertexAttribArray(aColorHandle)
    }

    /**
     * Raycasts from screen coordinates (touchX, touchY) into world 3D space,
     * intersecting with the horizontal plane Y = planeY.
     * Returns floatArrayOf(hitX, hitY, hitZ) or null if ray is parallel.
     */
    fun raycastPlaneY(touchX: Float, touchY: Float, planeY: Float = 0f): FloatArray? {
        if (width <= 0 || height <= 0) return null

        val ndcX = (2f * touchX / width) - 1f
        val ndcY = 1f - (2f * touchY / height)

        val pvMatrix = FloatArray(16)
        Matrix.multiplyMM(pvMatrix, 0, projMatrix, 0, viewMatrix, 0)
        if (!Matrix.invertM(invMvpMatrix, 0, pvMatrix, 0)) return null

        val nearVec = floatArrayOf(ndcX, ndcY, -1f, 1f)
        val farVec = floatArrayOf(ndcX, ndcY, 1f, 1f)

        val worldNear = FloatArray(4)
        val worldFar = FloatArray(4)

        Matrix.multiplyMV(worldNear, 0, invMvpMatrix, 0, nearVec, 0)
        Matrix.multiplyMV(worldFar, 0, invMvpMatrix, 0, farVec, 0)

        if (worldNear[3] == 0f || worldFar[3] == 0f) return null

        val nx = worldNear[0] / worldNear[3]
        val ny = worldNear[1] / worldNear[3]
        val nz = worldNear[2] / worldNear[3]

        val fx = worldFar[0] / worldFar[3]
        val fy = worldFar[1] / worldFar[3]
        val fz = worldFar[2] / worldFar[3]

        val dx = fx - nx
        val dy = fy - ny
        val dz = fz - nz

        if (Math.abs(dy) < 0.0001f) return null // Ray parallel to plane

        val t = (planeY - ny) / dy
        if (t < 0) return null

        return floatArrayOf(nx + t * dx, planeY, nz + t * dz)
    }
}
