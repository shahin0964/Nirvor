package com.nirvor.app.ui.tools.gametools.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

class Mesh(
    val vertexBuffer: FloatBuffer,
    val vertexCount: Int,
)

object MeshBuilder {

    private fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(coords.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply {
                put(coords)
                position(0)
            }
        }
    }

    /**
     * Builds a 3D Box/Cube with centered origin.
     * Each vertex has 10 floats: [x, y, z, nx, ny, nz, r, g, b, a]
     */
    fun buildBox(
        width: Float,
        height: Float,
        depth: Float,
        color: FloatArray,
    ): Mesh {
        val w = width * 0.5f
        val h = height * 0.5f
        val d = depth * 0.5f
        val r = color[0]; val g = color[1]; val b = color[2]; val a = color[3]

        // 6 faces * 2 triangles * 3 vertices = 36 vertices
        val data = ArrayList<Float>(36 * 10)

        fun addQuad(
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            x4: Float, y4: Float, z4: Float,
            nx: Float, ny: Float, nz: Float,
        ) {
            // Triangle 1: 1, 2, 3
            data.addAll(listOf(x1, y1, z1, nx, ny, nz, r, g, b, a))
            data.addAll(listOf(x2, y2, z2, nx, ny, nz, r, g, b, a))
            data.addAll(listOf(x3, y3, z3, nx, ny, nz, r, g, b, a))
            // Triangle 2: 1, 3, 4
            data.addAll(listOf(x1, y1, z1, nx, ny, nz, r, g, b, a))
            data.addAll(listOf(x3, y3, z3, nx, ny, nz, r, g, b, a))
            data.addAll(listOf(x4, y4, z4, nx, ny, nz, r, g, b, a))
        }

        // Top (+Y)
        addQuad(-w, h, -d, -w, h, d, w, h, d, w, h, -d, 0f, 1f, 0f)
        // Bottom (-Y)
        addQuad(-w, -h, d, -w, -h, -d, w, -h, -d, w, -h, d, 0f, -1f, 0f)
        // Front (+Z)
        addQuad(-w, -h, d, w, -h, d, w, h, d, -w, h, d, 0f, 0f, 1f)
        // Back (-Z)
        addQuad(w, -h, -d, -w, -h, -d, -w, h, -d, w, h, -d, 0f, 0f, -1f)
        // Right (+X)
        addQuad(w, -h, d, w, -h, -d, w, h, -d, w, h, d, 1f, 0f, 0f)
        // Left (-X)
        addQuad(-w, -h, -d, -w, -h, d, -w, h, d, -w, h, -d, -1f, 0f, 0f)

        return Mesh(createFloatBuffer(data.toFloatArray()), 36)
    }

    /**
     * Builds a 3D Cylinder / Disc (for carrom coins, striker, sholo guti pieces, board pegs).
     * Top face (+Y), bottom face (-Y), and curved side walls.
     */
    fun buildCylinder(
        radius: Float,
        height: Float,
        segments: Int = 24,
        color: FloatArray,
    ): Mesh {
        val h = height * 0.5f
        val r = color[0]; val g = color[1]; val b = color[2]; val a = color[3]
        val data = ArrayList<Float>()

        val step = (2.0 * Math.PI / segments).toFloat()

        // Top & Bottom Caps + Side Quads
        for (i in 0 until segments) {
            val theta1 = i * step
            val theta2 = (i + 1) * step

            val x1 = radius * cos(theta1)
            val z1 = radius * sin(theta1)
            val x2 = radius * cos(theta2)
            val z2 = radius * sin(theta2)

            // Top Cap (y = h, normal = 0, 1, 0)
            data.addAll(listOf(0f, h, 0f, 0f, 1f, 0f, r, g, b, a))
            data.addAll(listOf(x1, h, z1, 0f, 1f, 0f, r, g, b, a))
            data.addAll(listOf(x2, h, z2, 0f, 1f, 0f, r, g, b, a))

            // Bottom Cap (y = -h, normal = 0, -1, 0)
            data.addAll(listOf(0f, -h, 0f, 0f, -1f, 0f, r, g, b, a))
            data.addAll(listOf(x2, -h, z2, 0f, -1f, 0f, r, g, b, a))
            data.addAll(listOf(x1, -h, z1, 0f, -1f, 0f, r, g, b, a))

            // Side Wall (normals along (cos, 0, sin))
            val nx1 = cos(theta1); val nz1 = sin(theta1)
            val nx2 = cos(theta2); val nz2 = sin(theta2)

            // Triangle 1
            data.addAll(listOf(x1, -h, z1, nx1, 0f, nz1, r, g, b, a))
            data.addAll(listOf(x2, -h, z2, nx2, 0f, nz2, r, g, b, a))
            data.addAll(listOf(x2, h, z2, nx2, 0f, nz2, r, g, b, a))

            // Triangle 2
            data.addAll(listOf(x1, -h, z1, nx1, 0f, nz1, r, g, b, a))
            data.addAll(listOf(x2, h, z2, nx2, 0f, nz2, r, g, b, a))
            data.addAll(listOf(x1, h, z1, nx1, 0f, nz1, r, g, b, a))
        }

        val totalVerts = segments * (3 + 3 + 6)
        return Mesh(createFloatBuffer(data.toFloatArray()), totalVerts)
    }

    /**
     * Builds a 3D Pen / Pencil model with body, cone tip, and lead.
     * Length along the Z-axis.
     */
    fun buildPen(
        radius: Float,
        bodyLength: Float,
        tipLength: Float,
        bodyColor: FloatArray,
        tipColor: FloatArray,
        leadColor: FloatArray,
    ): Mesh {
        val segments = 8 // octagonal pencil profile
        val step = (2.0 * Math.PI / segments).toFloat()
        val data = ArrayList<Float>()

        val bR = bodyColor[0]; val bG = bodyColor[1]; val bB = bodyColor[2]; val bA = bodyColor[3]
        val tR = tipColor[0]; val tG = tipColor[1]; val tB = tipColor[2]; val tA = tipColor[3]
        val lR = leadColor[0]; val lG = leadColor[1]; val lB = leadColor[2]; val lA = leadColor[3]

        val halfLen = (bodyLength + tipLength) * 0.5f
        val zBack = -halfLen
        val zConeStart = halfLen - tipLength
        val zTip = halfLen

        // 1. Back cap
        for (i in 0 until segments) {
            val t1 = i * step; val t2 = (i + 1) * step
            val x1 = radius * cos(t1); val y1 = radius * sin(t1)
            val x2 = radius * cos(t2); val y2 = radius * sin(t2)

            data.addAll(listOf(0f, 0f, zBack, 0f, 0f, -1f, bR, bG, bB, bA))
            data.addAll(listOf(x2, y2, zBack, 0f, 0f, -1f, bR, bG, bB, bA))
            data.addAll(listOf(x1, y1, zBack, 0f, 0f, -1f, bR, bG, bB, bA))
        }

        // 2. Body sides (zBack to zConeStart)
        for (i in 0 until segments) {
            val t1 = i * step; val t2 = (i + 1) * step
            val x1 = radius * cos(t1); val y1 = radius * sin(t1)
            val x2 = radius * cos(t2); val y2 = radius * sin(t2)
            val nx1 = cos(t1); val ny1 = sin(t1)
            val nx2 = cos(t2); val ny2 = sin(t2)

            data.addAll(listOf(x1, y1, zBack, nx1, ny1, 0f, bR, bG, bB, bA))
            data.addAll(listOf(x2, y2, zBack, nx2, ny2, 0f, bR, bG, bB, bA))
            data.addAll(listOf(x2, y2, zConeStart, nx2, ny2, 0f, bR, bG, bB, bA))

            data.addAll(listOf(x1, y1, zBack, nx1, ny1, 0f, bR, bG, bB, bA))
            data.addAll(listOf(x2, y2, zConeStart, nx2, ny2, 0f, bR, bG, bB, bA))
            data.addAll(listOf(x1, y1, zConeStart, nx1, ny1, 0f, bR, bG, bB, bA))
        }

        // 3. Sharpened Wooden Tip (Cone)
        for (i in 0 until segments) {
            val t1 = i * step; val t2 = (i + 1) * step
            val x1 = radius * cos(t1); val y1 = radius * sin(t1)
            val x2 = radius * cos(t2); val y2 = radius * sin(t2)

            // Approximate normal pointing outward and forward
            val nx = cos((t1 + t2) * 0.5f); val ny = sin((t1 + t2) * 0.5f); val nz = 0.5f

            data.addAll(listOf(x1, y1, zConeStart, nx, ny, nz, tR, tG, tB, tA))
            data.addAll(listOf(x2, y2, zConeStart, nx, ny, nz, tR, tG, tB, tA))
            data.addAll(listOf(0f, 0f, zTip, 0f, 0f, 1f, lR, lG, lB, lA))
        }

        val totalVerts = data.size / 10
        return Mesh(createFloatBuffer(data.toFloatArray()), totalVerts)
    }
}
