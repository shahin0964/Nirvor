package com.nirvor.app.office

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A word on a page with its box (y grows downwards). From the PDF text layer or from OCR. */
class TWord(
    val text: String,
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val bold: Boolean = false,
) {
    val h: Float get() = max(1f, y1 - y0)
}

/** Words on one line, left to right. */
class TLine(val words: List<TWord>) {
    val x0 = words.minOf { it.x0 }
    val x1 = words.maxOf { it.x1 }
    val y0 = words.minOf { it.y0 }
    val y1 = words.maxOf { it.y1 }
    val size: Float = words.map { it.h }.sorted()[words.size / 2]
    val bold: Boolean = words.count { it.bold } * 2 > words.size

    /** Text with spaces between words, and a tab where there is a wide gap. */
    val text: String = buildString {
        words.forEachIndexed { i, w ->
            if (i > 0) append(if (w.x0 - words[i - 1].x1 > size * 2.2f) "\t" else " ")
            append(w.text)
        }
    }
}

/** Turns words with positions into lines, paragraphs (for Word) and tables (for Excel). */
object PageText {
    private val BULLET = Regex("^[•●▪■◦○◆▸►\\-–—*·]\\s+")

    private class C(val text: String, val x0: Float, val x1: Float)

    /** A line split into cells where the gap between words is wide (a column gap). */
    private fun cells(l: TLine): List<C> {
        val out = mutableListOf<C>()
        var start = 0
        for (i in 1..l.words.size) {
            val split = i == l.words.size || l.words[i].x0 - l.words[i - 1].x1 > l.size * 0.9f
            if (split) {
                val part = l.words.subList(start, i)
                out.add(C(part.joinToString(" ") { it.text }, part.first().x0, part.last().x1))
                start = i
            }
        }
        return out
    }

    fun lines(words: List<TWord>): List<TLine> {
        val sorted = words.filter { it.text.isNotBlank() }.sortedBy { (it.y0 + it.y1) / 2 }
        val groups = mutableListOf<MutableList<TWord>>()
        val bounds = mutableListOf<FloatArray>() // y0, y1 of each group
        for (w in sorted) {
            var best = -1
            var bestOverlap = 0f
            // only the last few lines can still take this word
            for (g in max(0, groups.size - 4) until groups.size) {
                val b = bounds[g]
                val overlap = min(b[1], w.y1) - max(b[0], w.y0)
                val need = 0.5f * min(b[1] - b[0], w.h)
                if (overlap >= need && overlap > bestOverlap) {
                    best = g
                    bestOverlap = overlap
                }
            }
            if (best < 0) {
                groups.add(mutableListOf(w))
                bounds.add(floatArrayOf(w.y0, w.y1))
            } else {
                groups[best].add(w)
                bounds[best][0] = min(bounds[best][0], w.y0)
                bounds[best][1] = max(bounds[best][1], w.y1)
            }
        }
        return groups.map { g -> TLine(g.sortedBy { it.x0 }) }.sortedBy { it.y0 }
    }

    /**
     * Lines of each page → paragraphs, headings, bullet lists and centred lines.
     * [pageWidths] in the same units as the words.
     */
    fun toBlocks(pages: List<List<TLine>>, pageWidths: List<Float>): List<Block> {
        val out = mutableListOf<Block>()
        pages.forEachIndexed { pi, lines ->
            if (pi > 0) out.add(PageBreak)
            if (lines.isEmpty()) return@forEachIndexed
            val body = lines.map { it.size }.sorted()[lines.size / 2]
            val left = lines.minOf { it.x0 }
            val right = lines.maxOf { it.x1 }
            val width = max(1f, right - left)
            val pageMid = (pageWidths.getOrElse(pi) { right + left }) / 2f

            var cur = mutableListOf<TLine>()
            fun flush() {
                if (cur.isEmpty()) return
                val first = cur.first()
                val size = first.size
                val kind = when {
                    size >= body * 1.6f -> ParaKind.H1
                    size >= body * 1.3f -> ParaKind.H2
                    size >= body * 1.12f && first.bold -> ParaKind.H3
                    else -> ParaKind.Normal
                }
                val sb = StringBuilder()
                cur.forEachIndexed { i, l ->
                    var t = l.text
                    if (i == 0) t = t.replace(BULLET, "")
                    if (i > 0) {
                        val prev = sb.toString()
                        if (prev.endsWith("-") && t.firstOrNull()?.isLowerCase() == true) sb.setLength(sb.length - 1) else sb.append(' ')
                    }
                    sb.append(t)
                }
                val bullet = BULLET.containsMatchIn(first.text)
                val centred = cur.all { l -> abs((l.x0 + l.x1) / 2 - pageMid) < width * 0.05f && (l.x1 - l.x0) < width * 0.75f }
                val bold = kind == ParaKind.Normal && cur.all { it.bold }
                out.add(
                    Para(
                        listOf(Run(sb.toString().trim(), bold = bold)),
                        kind,
                        if (centred) Align.Center else Align.Left,
                        if (bullet) ListKind.Bullet else ListKind.None,
                    )
                )
                cur = mutableListOf()
            }

            // runs of lines with several columns become real tables
            val multi = lines.map { cells(it).size >= 2 }
            var i = 0
            while (i < lines.size) {
                if (multi[i]) {
                    var j = i
                    while (j < lines.size && multi[j]) j++
                    if (j - i >= 2) {
                        flush()
                        val grid = table(lines.subList(i, j).flatMap { it.words })
                        val header = grid.firstOrNull()?.none { c -> c.any { ch -> ch.isDigit() } } == true
                        out.add(Table(grid.map { r -> r.map { c -> Cell(listOf(Para(listOf(Run(c))))) } }, header))
                        i = j
                        continue
                    }
                }
                val l = lines[i]
                i++
                val prev = cur.lastOrNull()
                if (prev != null) {
                    val gap = l.y0 - prev.y1
                    val newPara = gap > body * 0.9f ||
                        abs(l.size - prev.size) > prev.size * 0.15f ||
                        BULLET.containsMatchIn(l.text) ||
                        Regex("^(\\d+|[০-৯]+|[a-zA-Z])[.)]\\s").containsMatchIn(l.text) ||
                        prev.x1 < right - width * 0.18f ||
                        l.bold != prev.bold
                    if (newPara) flush()
                }
                cur.add(l)
            }
            flush()
        }
        return out
    }

    /** Words → a grid of cells, columns found from the gaps that run down the page. */
    fun table(words: List<TWord>): List<List<String>> {
        val lines = lines(words)
        if (lines.isEmpty()) return emptyList()

        val cellLines = lines.map { cells(it) }

        val multi = cellLines.filter { it.size >= 2 }
        if (multi.isEmpty()) return cellLines.map { cs -> listOf(cs.joinToString(" ") { it.text }) }

        val minX = words.minOf { it.x0 }
        val maxX = words.maxOf { it.x1 }
        val span = max(1f, maxX - minX)
        val bins = 600
        val scale = bins / span
        val cover = IntArray(bins + 1)
        for (cs in multi) {
            val mark = BooleanArray(bins + 1)
            for (c in cs) {
                val a = ((c.x0 - minX) * scale).toInt().coerceIn(0, bins)
                val b = ((c.x1 - minX) * scale).toInt().coerceIn(0, bins)
                for (k in a..b) mark[k] = true
            }
            for (k in 0..bins) if (mark[k]) cover[k]++
        }
        val threshold = max(1, (multi.size * 0.12f).toInt())
        val cols = mutableListOf<FloatArray>()
        var k = 0
        while (k <= bins) {
            if (cover[k] >= threshold) {
                val s = k
                while (k <= bins && cover[k] >= threshold) k++
                cols.add(floatArrayOf(minX + s / scale, minX + (k - 1) / scale))
            } else {
                k++
            }
        }
        // join columns split by a hair-thin gap
        val median = lines.map { it.size }.sorted()[lines.size / 2]
        val merged = mutableListOf<FloatArray>()
        for (c in cols) {
            val last = merged.lastOrNull()
            if (last != null && c[0] - last[1] < median * 0.4f) last[1] = c[1] else merged.add(c)
        }
        if (merged.isEmpty()) return cellLines.map { cs -> listOf(cs.joinToString(" ") { it.text }) }

        return cellLines.map { cs ->
            val row = Array(merged.size) { StringBuilder() }
            if (cs.size == 1 && merged.count { col -> min(col[1], cs[0].x1) - max(col[0], cs[0].x0) > 0 } >= 2) {
                // a title or sentence across the table: keep it in the first column
                row[0].append(cs[0].text)
                return@map row.map { it.toString() }
            }
            for (c in cs) {
                var best = 0
                var bestScore = Float.NEGATIVE_INFINITY
                merged.forEachIndexed { i, col ->
                    val overlap = min(col[1], c.x1) - max(col[0], c.x0)
                    val score = if (overlap > 0) overlap else -abs((col[0] + col[1]) / 2 - (c.x0 + c.x1) / 2) - 1e4f
                    if (score > bestScore) {
                        bestScore = score
                        best = i
                    }
                }
                if (row[best].isNotEmpty()) row[best].append(' ')
                row[best].append(c.text)
            }
            row.map { it.toString() }
        }
    }
}
