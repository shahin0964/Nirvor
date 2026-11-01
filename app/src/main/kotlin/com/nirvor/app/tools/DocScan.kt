package com.nirvor.app.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Paper finding, straightening and clean-up filters for the scanner tools. */
object DocScan {
    /** A rectangle a little inside the picture, as TL, TR, BR, BL. */
    fun inset(w: Int, h: Int, f: Float = 0.08f): List<PointF> = listOf(
        PointF(w * f, h * f), PointF(w * (1 - f), h * f), PointF(w * (1 - f), h * (1 - f)), PointF(w * f, h * (1 - f)),
    )

    /**
     * Finds a sheet of paper (a bright area on a darker background) and returns its corners
     * TL, TR, BR, BL in picture pixels, or null when nothing clear is found.
     */
    fun detect(bmp: Bitmap): List<PointF>? {
        val w = 240
        val h = (bmp.height * w / bmp.width.toFloat()).roundToInt().coerceAtLeast(10)
        val small = Bitmap.createScaledBitmap(bmp, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small != bmp) small.recycle()
        val lum = IntArray(w * h)
        val hist = IntArray(256)
        for (i in px.indices) {
            val c = px[i]
            val l = (((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000
            lum[i] = l
            hist[l]++
        }
        // Otsu threshold
        val total = w * h
        var sum = 0.0
        for (t in 0 until 256) sum += t * hist[t].toDouble()
        var sumB = 0.0
        var wB = 0
        var best = 0.0
        var threshold = 128
        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t * hist[t].toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between
                threshold = t
            }
        }
        // largest bright region (4-neighbour flood fill)
        val label = IntArray(w * h)
        val stack = IntArray(w * h)
        var bestLabel = 0
        var bestSize = 0
        var next = 1
        for (start in 0 until total) {
            if (lum[start] <= threshold || label[start] != 0) continue
            var sp = 0
            stack[sp++] = start
            label[start] = next
            var size = 0
            while (sp > 0) {
                val p = stack[--sp]
                size++
                val x = p % w
                val y = p / w
                if (x > 0) { val q = p - 1; if (label[q] == 0 && lum[q] > threshold) { label[q] = next; stack[sp++] = q } }
                if (x < w - 1) { val q = p + 1; if (label[q] == 0 && lum[q] > threshold) { label[q] = next; stack[sp++] = q } }
                if (y > 0) { val q = p - w; if (label[q] == 0 && lum[q] > threshold) { label[q] = next; stack[sp++] = q } }
                if (y < h - 1) { val q = p + w; if (label[q] == 0 && lum[q] > threshold) { label[q] = next; stack[sp++] = q } }
            }
            if (size > bestSize) {
                bestSize = size
                bestLabel = next
            }
            next++
        }
        if (bestSize < total * 0.12 || bestSize > total * 0.97) return null
        var tl = 0
        var tr = 0
        var br = 0
        var bl = 0
        var minSum = Int.MAX_VALUE
        var maxSum = Int.MIN_VALUE
        var minDiff = Int.MAX_VALUE
        var maxDiff = Int.MIN_VALUE
        for (p in 0 until total) {
            if (label[p] != bestLabel) continue
            val x = p % w
            val y = p / w
            if (x + y < minSum) { minSum = x + y; tl = p }
            if (x + y > maxSum) { maxSum = x + y; br = p }
            if (x - y > maxDiff) { maxDiff = x - y; tr = p }
            if (x - y < minDiff) { minDiff = x - y; bl = p }
        }
        val s = bmp.width / w.toFloat()
        return listOf(tl, tr, br, bl).map { PointF((it % w + 0.5f) * s, (it / w + 0.5f) * s) }
    }

    /** Straightens the area inside [c] (TL, TR, BR, BL) into a flat rectangle. */
    fun warp(bmp: Bitmap, c: List<PointF>, maxSide: Int = 2200): Bitmap {
        val wTop = hypot(c[1].x - c[0].x, c[1].y - c[0].y)
        val wBottom = hypot(c[2].x - c[3].x, c[2].y - c[3].y)
        val hLeft = hypot(c[3].x - c[0].x, c[3].y - c[0].y)
        val hRight = hypot(c[2].x - c[1].x, c[2].y - c[1].y)
        var outW = max(wTop, wBottom)
        var outH = max(hLeft, hRight)
        val k = min(1f, maxSide / max(outW, outH))
        outW = (outW * k).coerceAtLeast(16f)
        outH = (outH * k).coerceAtLeast(16f)
        val out = Bitmap.createBitmap(outW.roundToInt(), outH.roundToInt(), Bitmap.Config.ARGB_8888)
        val m = Matrix()
        m.setPolyToPoly(
            floatArrayOf(c[0].x, c[0].y, c[1].x, c[1].y, c[2].x, c[2].y, c[3].x, c[3].y),
            0,
            floatArrayOf(0f, 0f, outW, 0f, outW, outH, 0f, outH),
            0,
            4,
        )
        Canvas(out).drawBitmap(bmp, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    /** 0 = as taken, 1 = black & white document, 2 = brighter colours, 3 = grey. */
    fun filter(bmp: Bitmap, mode: Int): Bitmap {
        if (mode == 0) return bmp
        val cm = ColorMatrix()
        when (mode) {
            1 -> {
                cm.setSaturation(0f)
                cm.postConcat(contrast(1.9f, 30f))
            }
            2 -> {
                val sat = ColorMatrix().apply { setSaturation(1.3f) }
                cm.set(sat)
                cm.postConcat(contrast(1.3f, 12f))
            }
            else -> cm.setSaturation(0f)
        }
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bmp, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }

    private fun contrast(c: Float, brightness: Float): ColorMatrix {
        val t = (-0.5f * c + 0.5f) * 255f + brightness
        return ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }
}
