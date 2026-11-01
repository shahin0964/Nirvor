package com.nirvor.app.office

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a [Doc] onto PDF pages with Android's text engine, so Bangla is shaped correctly:
 * headings, styled text, lists, quotes, tables (header repeats on each page), pictures, page breaks.
 */
object DocPdf {
    class Options(
        val pageW: Float = PageSize.A4_W,
        val pageH: Float = PageSize.A4_H,
        val margin: Float = 56.7f,
        val baseSize: Float = 12f,
        val pageNumbers: Boolean = true,
        val gridLines: Boolean = true,
    )

    private const val INK = 0xFF1B1F2A.toInt()
    private const val GRID = 0xFFAAB4C0.toInt()
    private const val HEADER_BG = 0xFFE3F6EC.toInt()
    private const val LINK = 0xFF168FFB.toInt()

    /** Writes the PDF and returns the number of pages. */
    fun write(doc: Doc, out: OutputStream, opt: Options = Options()): Int {
        val pdf = PdfDocument()
        try {
            val w = Writer(pdf, opt)
            w.blocks(doc.blocks)
            val pages = w.finish()
            pdf.writeTo(out)
            return pages
        } finally {
            pdf.close()
        }
    }

    private class Writer(val pdf: PdfDocument, val opt: Options) {
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var number = 0
        var y = 0f
        val left = opt.margin
        val contentW = opt.pageW - 2 * opt.margin
        val bottom = opt.pageH - opt.margin
        var atTop = true

        fun newPage() {
            endPage()
            number++
            val info = PdfDocument.PageInfo.Builder(opt.pageW.roundToInt(), opt.pageH.roundToInt(), number).create()
            val p = pdf.startPage(info)
            page = p
            canvas = p.canvas
            y = opt.margin
            atTop = true
        }

        fun endPage() {
            val p = page ?: return
            if (opt.pageNumbers) {
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 9f
                    color = 0xFF8A97A8.toInt()
                    textAlign = Paint.Align.CENTER
                }
                p.canvas.drawText("$number", opt.pageW / 2, opt.pageH - opt.margin / 2, paint)
            }
            pdf.finishPage(p)
            page = null
            canvas = null
        }

        fun finish(): Int {
            if (page == null && number == 0) newPage()
            endPage()
            return number
        }

        fun c(): Canvas {
            if (canvas == null) newPage()
            return canvas!!
        }

        fun room() = bottom - y

        // ---------------------------------------------------------------- text

        fun spannable(runs: List<Run>, scale: Float = 1f): SpannableStringBuilder {
            val sb = SpannableStringBuilder()
            for (r in runs) {
                if (r.text.isEmpty()) continue
                val s = sb.length
                sb.append(r.text)
                val e = sb.length
                val flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                when {
                    r.bold && r.italic -> sb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), s, e, flags)
                    r.bold -> sb.setSpan(StyleSpan(Typeface.BOLD), s, e, flags)
                    r.italic -> sb.setSpan(StyleSpan(Typeface.ITALIC), s, e, flags)
                }
                if (r.underline || r.link != null) sb.setSpan(UnderlineSpan(), s, e, flags)
                if (r.strike) sb.setSpan(StrikethroughSpan(), s, e, flags)
                val col = r.color ?: if (r.link != null) LINK else null
                col?.let { sb.setSpan(ForegroundColorSpan(it or 0xFF000000.toInt()), s, e, flags) }
                r.highlight?.let { sb.setSpan(BackgroundColorSpan(it or 0xFF000000.toInt()), s, e, flags) }
                r.size?.let { sb.setSpan(AbsoluteSizeSpan((it * scale).roundToInt().coerceIn(4, 120)), s, e, flags) }
            }
            return sb
        }

        fun layout(text: CharSequence, paint: TextPaint, width: Float, align: Align): StaticLayout {
            val a = when (align) {
                Align.Center -> Layout.Alignment.ALIGN_CENTER
                Align.Right -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            val b = StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width.toInt()))
                .setAlignment(a)
                .setLineSpacing(0f, 1.18f)
                .setIncludePad(false)
            if (align == Align.Justify) b.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
            return b.build()
        }

        /** Draws [l] at x, continuing on new pages when it runs out of room; [onLine0] gets the first baseline. */
        fun drawFlowing(l: StaticLayout, x: Float, onLine0: ((Canvas, Float) -> Unit)? = null) {
            var line = 0
            var first = true
            while (line < l.lineCount) {
                var end = line
                val top = l.getLineTop(line)
                while (end < l.lineCount && l.getLineBottom(end) - top <= room()) end++
                if (end == line) {
                    if (atTop) {
                        end = line + 1 // a line taller than a page: draw it anyway
                    } else {
                        newPage()
                        continue
                    }
                }
                val cv = c()
                val bottomY = l.getLineBottom(end - 1)
                cv.save()
                cv.translate(x, y - top)
                cv.clipRect(-200f, top.toFloat(), l.width + 200f, bottomY.toFloat())
                l.draw(cv)
                cv.restore()
                if (first) {
                    onLine0?.invoke(cv, y + l.getLineBaseline(0) - top)
                    first = false
                }
                y += bottomY - top
                atTop = false
                line = end
            }
        }

        fun paintFor(kind: ParaKind): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            linkColor = LINK
            color = when (kind) {
                ParaKind.H1 -> 0xFF0A8F56.toInt()
                ParaKind.H2 -> 0xFF1F2A3A.toInt()
                ParaKind.H3, ParaKind.Quote -> 0xFF41597A.toInt()
                else -> INK
            }
            textSize = opt.baseSize * when (kind) {
                ParaKind.Title -> 2.15f
                ParaKind.H1 -> 1.5f
                ParaKind.H2 -> 1.25f
                ParaKind.H3 -> 1.1f
                else -> 1f
            }
            typeface = when (kind) {
                ParaKind.Title, ParaKind.H1, ParaKind.H2, ParaKind.H3 -> Typeface.DEFAULT_BOLD
                ParaKind.Quote -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }
        }

        val counters = IntArray(9)

        fun para(p: Para) {
            val paint = paintFor(p.kind)
            val before = when (p.kind) {
                ParaKind.Title -> 4f
                ParaKind.H1 -> 14f
                ParaKind.H2 -> 11f
                ParaKind.H3 -> 9f
                else -> 0f
            }
            val after = if (p.list != ListKind.None) 3f else 7f
            if (!atTop) y += before
            var indent = 0f
            var marker: String? = null
            if (p.list != ListKind.None) {
                val lv = p.level.coerceIn(0, 8)
                indent = 20f * (lv + 1)
                marker = if (p.list == ListKind.Bullet) {
                    listOf("•", "◦", "▪")[lv % 3]
                } else {
                    counters[lv]++
                    for (k in lv + 1 until counters.size) counters[k] = 0
                    "${counters[lv]}."
                }
            } else {
                counters.fill(0)
            }
            if (p.kind == ParaKind.Quote) indent = 16f
            val text: CharSequence = if (p.text.isEmpty()) " " else spannable(p.runs)
            val l = layout(text, paint, contentW - indent, p.align)
            // keep a heading together with the first line of what follows
            if (p.kind in listOf(ParaKind.H1, ParaKind.H2, ParaKind.H3) && room() < l.height + paint.textSize * 2.5f && !atTop) newPage()
            val startY = y
            val startPage = number
            drawFlowing(l, left + indent) { cv, baseline ->
                if (marker != null) {
                    val mp = TextPaint(paint).apply { textAlign = Paint.Align.RIGHT }
                    cv.drawText(marker, left + indent - 5f, baseline, mp)
                }
            }
            if (p.kind == ParaKind.Quote && number == startPage) {
                val bar = Paint().apply { color = 0xFF0BA765.toInt() }
                c().drawRect(left, startY, left + 3f, y, bar)
            }
            y += after
        }

        // ---------------------------------------------------------------- table

        fun table(t: Table) {
            val cols = t.columns
            if (cols == 0) return
            counters.fill(0)
            val pad = 4f
            val size = opt.baseSize * 0.92f
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                color = INK
                linkColor = LINK
            }
            val bold = TextPaint(paint).apply { typeface = Typeface.DEFAULT_BOLD }
            // natural column widths from the longest line in each column
            val natural = FloatArray(cols) { ci ->
                val longest = t.rows.maxOfOrNull { r ->
                    r.getOrNull(ci)?.paras?.maxOfOrNull { p -> p.text.split('\n').maxOfOrNull { paint.measureText(it) } ?: 0f } ?: 0f
                } ?: 0f
                min(longest, contentW * 0.6f) + 2 * pad + 2f
            }
            val total = natural.sum()
            val widths = if (total <= contentW) {
                natural.map { it * contentW / total }.toFloatArray()
            } else {
                // too wide: shrink the wide columns first, never below a readable minimum
                val minW = max(28f, contentW / cols * 0.5f)
                val flex = natural.sumOf { max(0f, it - minW).toDouble() }.toFloat()
                val over = total - contentW
                natural.map { w -> if (flex <= 0f) contentW / cols else max(minW, w - over * max(0f, w - minW) / flex) }.toFloatArray()
            }

            fun rowLayouts(r: List<Cell>, header: Boolean): List<StaticLayout> = (0 until cols).map { ci ->
                val cell = r.getOrNull(ci)
                val sb = SpannableStringBuilder()
                cell?.paras?.forEachIndexed { i, p ->
                    if (i > 0) sb.append('\n')
                    sb.append(spannable(p.runs))
                }
                val align = cell?.paras?.firstOrNull()?.align ?: Align.Left
                layout(if (sb.isEmpty()) " " else sb, if (header) bold else paint, widths[ci] - 2 * pad, align)
            }

            fun drawRow(r: List<Cell>, ls: List<StaticLayout>, header: Boolean) {
                val h = ls.maxOf { it.height } + 2 * pad
                if (h > room() && !atTop) newPage()
                val cv = c()
                var x = left
                val gridPaint = Paint().apply {
                    color = GRID
                    strokeWidth = 0.6f
                    style = Paint.Style.STROKE
                }
                for (ci in 0 until cols) {
                    val bg = r.getOrNull(ci)?.bg?.let { it or 0xFF000000.toInt() } ?: if (header) HEADER_BG else null
                    if (bg != null) cv.drawRect(x, y, x + widths[ci], y + h, Paint().apply { color = bg })
                    cv.save()
                    cv.translate(x + pad, y + pad)
                    ls[ci].draw(cv)
                    cv.restore()
                    if (opt.gridLines) cv.drawRect(x, y, x + widths[ci], y + h, gridPaint)
                    x += widths[ci]
                }
                y += h
                atTop = false
            }

            if (!atTop) y += 4f
            val headerRow = if (t.header) t.rows.firstOrNull() else null
            val headerLayouts = headerRow?.let { rowLayouts(it, true) }
            t.rows.forEachIndexed { ri, r ->
                val isHeader = ri == 0 && t.header
                val ls = if (isHeader) headerLayouts!! else rowLayouts(r, false)
                val h = ls.maxOf { it.height } + 2 * pad
                if (!isHeader && h > room() && !atTop) {
                    newPage()
                    // repeat the header on the new page
                    if (headerRow != null && headerLayouts != null) drawRow(headerRow, headerLayouts, true)
                }
                drawRow(r, ls, isHeader)
            }
            y += 8f
        }

        // ---------------------------------------------------------------- picture

        fun picture(pic: Picture) {
            counters.fill(0)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(pic.bytes, 0, pic.bytes.size, opts)
            if (opts.outWidth <= 0) return
            var sample = 1
            while (max(opts.outWidth, opts.outHeight) / (sample * 2) >= 2000) sample *= 2
            val bmp: Bitmap = BitmapFactory.decodeByteArray(pic.bytes, 0, pic.bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return
            var w = if (pic.widthPt > 0) pic.widthPt else bmp.width * 0.75f
            var h = if (pic.heightPt > 0) pic.heightPt else bmp.height * 0.75f
            if (w > contentW) {
                h *= contentW / w
                w = contentW
            }
            val maxH = bottom - opt.margin
            if (h > maxH) {
                w *= maxH / h
                h = maxH
            }
            if (h > room() && !atTop) newPage()
            val x = left + (contentW - w) / 2
            c().drawBitmap(bmp, null, RectF(x, y, x + w, y + h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            bmp.recycle()
            y += h + 8f
            atTop = false
        }

        fun blocks(list: List<Block>) {
            if (page == null) newPage()
            for (b in list) {
                when (b) {
                    is Para -> para(b)
                    is Table -> table(b)
                    is Picture -> picture(b)
                    PageBreak -> {
                        counters.fill(0)
                        newPage()
                    }
                    Rule -> {
                        counters.fill(0)
                        if (room() < 14f) newPage()
                        c().drawLine(left, y + 6f, left + contentW, y + 6f, Paint().apply {
                            color = 0xFFBFC7D1.toInt()
                            strokeWidth = 0.8f
                        })
                        y += 14f
                        atTop = false
                    }
                }
            }
        }
    }

    /**
     * Sheets as PDF tables (each sheet starts on a new page with its name), header row repeated
     * on every page, numbers right-aligned.
     */
    fun sheets(list: List<SheetData>, out: OutputStream, landscape: Boolean, header: Boolean, grid: Boolean): Int {
        val blocks = mutableListOf<Block>()
        list.forEachIndexed { i, sheet ->
            if (i > 0) blocks.add(PageBreak)
            if (list.size > 1) blocks.add(Para(listOf(Run(sheet.name, bold = true)), ParaKind.H3))
            val rows = sheet.rows.map { r ->
                r.map { c -> Cell(listOf(Para(listOf(Run(c)), align = if (XlsxWriter.asNumber(c) != null) Align.Right else Align.Left))) }
            }
            if (rows.isNotEmpty()) blocks.add(Table(rows, header))
        }
        val cols = list.maxOfOrNull { it.columns } ?: 0
        val opt = Options(
            pageW = if (landscape) PageSize.A4_H else PageSize.A4_W,
            pageH = if (landscape) PageSize.A4_W else PageSize.A4_H,
            margin = 36f,
            baseSize = when {
                cols > 12 -> 7f
                cols > 8 -> 8f
                else -> 9.5f
            },
            gridLines = grid,
        )
        return write(Doc(blocks), out, opt)
    }
}
