package com.nirvor.app.ar

import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.tan

/** Which measurement the AR screen makes. */
enum class ArMode(val title: String, val bn: String, val maxPoints: Int) {
    Distance("Distance Measure", "দূরত্ব মাপা", 40),
    Points("Point-to-Point", "পয়েন্ট থেকে পয়েন্ট", 30),
    Angle("Angle Measure", "ক্যামেরায় কোণ", 3),
    Area("Area Measure", "জায়গার মাপ", 30),
    Height("Height Measure", "উচ্চতা মাপা", 1),
    Wall("Surface / Wall", "দেয়াল ও মেঝে", 4),
}

/** Small vector maths on (x, y, z) float arrays, in metres. */
object ArMath {
    fun sub(a: FloatArray, b: FloatArray) = floatArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    fun dot(a: FloatArray, b: FloatArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    fun cross(a: FloatArray, b: FloatArray) = floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    fun length(a: FloatArray) = sqrt(dot(a, a))
    fun dist(a: FloatArray, b: FloatArray) = length(sub(a, b))

    /** Total length of the path through [pts]. */
    fun pathLength(pts: List<FloatArray>): Float = pts.zipWithNext { a, b -> dist(a, b) }.sum()

    /** Angle at [b] between b→a and b→c, in degrees. */
    fun angle(a: FloatArray, b: FloatArray, c: FloatArray): Float {
        val u = sub(a, b)
        val v = sub(c, b)
        val l = length(u) * length(v)
        if (l < 1e-6f) return 0f
        return Math.toDegrees(acos((dot(u, v) / l).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }

    /** Area of a flat polygon in 3D (m²). */
    fun area(pts: List<FloatArray>): Float {
        if (pts.size < 3) return 0f
        val o = pts[0]
        var sx = 0f
        var sy = 0f
        var sz = 0f
        for (i in 1 until pts.size - 1) {
            val c = cross(sub(pts[i], o), sub(pts[i + 1], o))
            sx += c[0]
            sy += c[1]
            sz += c[2]
        }
        return 0.5f * sqrt(sx * sx + sy * sy + sz * sz)
    }

    /**
     * Height mode: the point on the vertical line through [base] that is closest to the ray from
     * [camera] along [dir] (both world). Returns the height above the base in metres, or null when
     * the phone points straight up or down.
     */
    fun heightOnRay(base: FloatArray, camera: FloatArray, dir: FloatArray): Float? {
        val dl = length(dir)
        if (dl < 1e-6f) return null
        val d = floatArrayOf(dir[0] / dl, dir[1] / dl, dir[2] / dl)
        val w0 = sub(base, camera)
        val b = d[1]
        val denom = 1f - b * b
        if (abs(denom) < 1e-4f) return null
        val e = dot(d, w0)
        val t = (b * e - w0[1]) / denom
        return t
    }

    private fun f(v: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", v)

    /** "45.2 সেমি" / "1.85 মি" */
    fun len(m: Float): String = if (abs(m) < 1f) f(m * 100.0, 1) + " সেমি" else f(m.toDouble(), 2) + " মি"

    /** "6 ফুট 0.8 ইঞ্চি" */
    fun feet(m: Float): String {
        val inches = abs(m) / 0.0254
        val ft = (inches / 12).toInt()
        val inch = inches - ft * 12
        return if (ft == 0) f(inch, 1) + " ইঞ্চি" else "$ft ফুট " + f(inch, 1) + " ইঞ্চি"
    }

    fun area(m2: Float): String {
        val sqft = m2 * 10.7639104
        val base = f(m2.toDouble(), 2) + " বর্গমিটার · " + f(sqft, 1) + " বর্গফুট"
        return if (m2 >= 20f) base + " · " + f(sqft / 435.6, 2) + " শতাংশ" else base
    }
}

/**
 * Measuring without AR: the phone's height above the ground plus the angle the back camera
 * points at. Gravity is in phone coordinates (x right, y up the screen, z out of the screen);
 * the back camera looks along −z.
 */
object TiltMath {
    /** Angle between the camera's view and straight down, in degrees (0 = down, 90 = level, 180 = up). */
    fun angleFromDown(gx: Float, gy: Float, gz: Float): Float? {
        val n = sqrt(gx * gx + gy * gy + gz * gz)
        if (n < 1f) return null
        return Math.toDegrees(acos((gz / n).coerceIn(-1f, 1f)).toDouble()).toFloat()
    }

    /** Distance along the ground to the point the camera aims at, or null if it aims at or above the horizon. */
    fun groundDistance(phoneHeight: Float, angleDeg: Float): Float? {
        if (angleDeg <= 0.5f || angleDeg >= 88.5f) return if (angleDeg <= 0.5f) 0f else null
        val d = phoneHeight * tan(Math.toRadians(angleDeg.toDouble())).toFloat()
        return if (d > 300f) null else d
    }

    /** Height of the point the camera aims at, standing [distance] metres away. */
    fun heightAt(phoneHeight: Float, distance: Float, angleDeg: Float): Float? {
        val up = angleDeg - 90f
        if (up >= 85f) return null
        val h = phoneHeight + distance * tan(Math.toRadians(up.toDouble())).toFloat()
        return h.coerceAtLeast(0f)
    }
}
