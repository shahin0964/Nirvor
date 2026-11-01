package com.nirvor.app.office

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.Closeable
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Page pictures of a PDF (Android's own renderer). One page at a time, so calls are serialised. */
class PdfPages(file: File) : Closeable {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(fd)
    } catch (e: Exception) {
        fd.close()
        throw e
    }
    val count: Int = renderer.pageCount

    /** Page size in points. */
    @Synchronized
    fun size(index: Int): Pair<Int, Int> = renderer.openPage(index).use { it.width to it.height }

    /** The page drawn [width] pixels wide on white. */
    @Synchronized
    fun render(index: Int, width: Int): Bitmap? = try {
        renderer.openPage(index).use { page ->
            val w = width.coerceIn(16, 3000)
            val h = (w * page.height / page.width.toFloat()).roundToInt().coerceIn(16, 4200)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        }
    } catch (e: Exception) {
        null
    }

    @Synchronized
    override fun close() {
        try {
            renderer.close()
        } catch (e: Exception) {
            // already closed
        }
        fd.close()
    }
}

/** Page size for pictures turned into a PDF. */
enum class PaperSize(val label: String) { A4("A4"), Letter("Letter"), Fit("ছবির মাপে") }

/** PDF jobs that need the real file structure (PdfBox): join, take pages out, read the text layer. */
object PdfTools {
    @Volatile
    private var ready = false

    fun init(context: Context) {
        if (!ready) {
            PDFBoxResourceLoader.init(context.applicationContext)
            ready = true
        }
    }

    private fun memory(context: Context) = MemoryUsageSetting.setupMixed(48L * 1024 * 1024).setTempDir(context.cacheDir)

    private fun load(context: Context, file: File): PDDocument =
        PDDocument.load(file, memory(context)).also { it.setAllSecurityToBeRemoved(true) }

    /** Joins [files] in order into [out]. */
    fun merge(context: Context, files: List<File>, out: File) {
        init(context)
        val m = PDFMergerUtility()
        files.forEach { m.addSource(it) }
        m.destinationFileName = out.absolutePath
        m.mergeDocuments(memory(context))
    }

    /** A new PDF with the chosen pages (0-based, in this order), each turned by its extra rotation. */
    fun extract(context: Context, src: File, pages: List<Int>, rotations: Map<Int, Int>, out: File) {
        init(context)
        load(context, src).use { doc ->
            PDDocument(memory(context)).use { dest ->
                for (i in pages) {
                    if (i !in 0 until doc.numberOfPages) continue
                    val page: PDPage = doc.getPage(i)
                    val copy = dest.importPage(page)
                    val extra = rotations[i] ?: 0
                    if (extra != 0) copy.rotation = ((page.rotation + extra) % 360 + 360) % 360
                }
                dest.save(out)
            }
        }
    }

    /** One PDF per page: name_1.pdf, name_2.pdf … in [dir]. */
    fun splitEach(context: Context, src: File, dir: File, baseName: String): List<File> {
        init(context)
        val files = mutableListOf<File>()
        load(context, src).use { doc ->
            for (i in 0 until doc.numberOfPages) {
                val f = File(dir, "${baseName}_${i + 1}.pdf")
                PDDocument(memory(context)).use { dest ->
                    dest.importPage(doc.getPage(i))
                    dest.save(f)
                }
                files.add(f)
            }
        }
        return files
    }

    /**
     * Pictures → one PDF, a picture per page, JPEG-compressed so photos stay small.
     * [load] gives picture i (upright); with [recycle] the pictures are freed after use.
     */
    fun images(
        context: Context,
        count: Int,
        load: (Int) -> Bitmap?,
        paper: PaperSize,
        marginPt: Float,
        out: File,
        recycle: Boolean,
        onProgress: (Int) -> Unit = {},
    ): Int {
        init(context)
        var pages = 0
        PDDocument(memory(context)).use { doc ->
            for (i in 0 until count) {
                val src = load(i) ?: continue
                // at most ~2400 px on the long side: sharp on paper, small file
                val k = min(1f, 2400f / max(src.width, src.height))
                var bmp = if (k < 1f) Bitmap.createScaledBitmap(src, (src.width * k).roundToInt(), (src.height * k).roundToInt(), true) else src
                if (bmp.hasAlpha()) {
                    val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(flat).apply {
                        drawColor(Color.WHITE)
                        drawBitmap(bmp, 0f, 0f, null)
                    }
                    if (bmp !== src) bmp.recycle()
                    bmp = flat
                }
                val landscape = bmp.width > bmp.height
                val (pw, ph) = when (paper) {
                    PaperSize.A4 -> if (landscape) PageSize.A4_H to PageSize.A4_W else PageSize.A4_W to PageSize.A4_H
                    PaperSize.Letter -> if (landscape) PageSize.LETTER_H to PageSize.LETTER_W else PageSize.LETTER_W to PageSize.LETTER_H
                    PaperSize.Fit -> {
                        val long = PageSize.A4_H
                        if (landscape) long to long * bmp.height / bmp.width else long * bmp.width / bmp.height to long
                    }
                }
                val m = if (paper == PaperSize.Fit) 0f else marginPt
                val s = min((pw - 2 * m) / bmp.width, (ph - 2 * m) / bmp.height)
                val dw = bmp.width * s
                val dh = bmp.height * s
                val page = PDPage(PDRectangle(pw, ph))
                doc.addPage(page)
                val img = JPEGFactory.createFromImage(doc, bmp, 0.85f)
                PDPageContentStream(doc, page).use { it.drawImage(img, (pw - dw) / 2, (ph - dh) / 2, dw, dh) }
                if (bmp !== src) bmp.recycle()
                if (recycle) src.recycle()
                pages++
                onProgress(i + 1)
            }
            doc.save(out)
        }
        return pages
    }

    class PageWords(val words: List<TWord>, val width: Float, val height: Float)

    /** Words with their positions on each page (points, y from the top), from the PDF's text layer. */
    fun words(context: Context, file: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<PageWords> {
        init(context)
        load(context, file).use { doc ->
            val total = doc.numberOfPages
            val pages = mutableListOf<PageWords>()
            val stripper = object : PDFTextStripper() {
                var current = mutableListOf<TWord>()

                override fun startPage(page: PDPage) {
                    current = mutableListOf()
                    super.startPage(page)
                }

                override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                    if (text.isBlank() || textPositions.isEmpty()) return
                    var x0 = Float.MAX_VALUE
                    var x1 = -Float.MAX_VALUE
                    var y0 = Float.MAX_VALUE
                    var y1 = -Float.MAX_VALUE
                    var bold = 0
                    for (p in textPositions) {
                        val h = max(p.heightDir, p.fontSizeInPt * 0.6f)
                        x0 = min(x0, p.xDirAdj)
                        x1 = max(x1, p.xDirAdj + p.widthDirAdj)
                        y0 = min(y0, p.yDirAdj - h)
                        y1 = max(y1, p.yDirAdj)
                        val name = p.font?.name.orEmpty()
                        if (name.contains("Bold", true) || name.contains("Black", true) || name.contains("Heavy", true)) bold++
                    }
                    current.add(TWord(text.trim(), x0, y0, x1, y1, bold * 2 > textPositions.size))
                }

                override fun endPage(page: PDPage) {
                    val box = page.mediaBox
                    val turned = page.rotation % 180 != 0
                    pages.add(PageWords(current, if (turned) box.height else box.width, if (turned) box.width else box.height))
                    onProgress(pages.size, total)
                    super.endPage(page)
                }
            }
            stripper.setSortByPosition(true)
            stripper.getText(doc)
            return pages
        }
    }
}
