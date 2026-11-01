package com.nirvor.app.office

/**
 * The app's own document model. Every Office tool goes through it:
 * Word editor ↔ model ↔ DOCX / PDF, and PDF text → model → DOCX.
 */
class Run(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    /** 0xRRGGBB */
    val color: Int? = null,
    /** 0xRRGGBB behind the text */
    val highlight: Int? = null,
    /** points */
    val size: Float? = null,
    val link: String? = null,
) {
    fun sameStyle(o: Run) = bold == o.bold && italic == o.italic && underline == o.underline && strike == o.strike &&
        color == o.color && highlight == o.highlight && size == o.size && link == o.link

    fun withText(t: String) = Run(t, bold, italic, underline, strike, color, highlight, size, link)
}

enum class Align { Left, Center, Right, Justify }

enum class ParaKind { Normal, Title, H1, H2, H3, Quote }

enum class ListKind { None, Bullet, Number }

sealed class Block

class Para(
    val runs: List<Run>,
    val kind: ParaKind = ParaKind.Normal,
    val align: Align = Align.Left,
    val list: ListKind = ListKind.None,
    val level: Int = 0,
) : Block() {
    val text: String get() = runs.joinToString("") { it.text }

    /** Joins neighbouring runs that look the same. */
    fun compact(): Para {
        val out = mutableListOf<Run>()
        for (r in runs) {
            if (r.text.isEmpty()) continue
            val last = out.lastOrNull()
            if (last != null && last.sameStyle(r)) out[out.size - 1] = last.withText(last.text + r.text) else out.add(r)
        }
        return Para(out, kind, align, list, level)
    }
}

class Cell(val paras: List<Para>, val bg: Int? = null)

class Table(val rows: List<List<Cell>>, val header: Boolean = false) : Block() {
    val columns: Int get() = rows.maxOfOrNull { it.size } ?: 0
}

/** A picture: encoded bytes (JPEG / PNG) and its size on the page in points. */
class Picture(val bytes: ByteArray, val mime: String, val widthPt: Float, val heightPt: Float) : Block()

object PageBreak : Block()

/** A horizontal line. */
object Rule : Block()

class Doc(val blocks: List<Block>, val title: String = "") {
    val plainText: String
        get() = buildString {
            for (b in blocks) when (b) {
                is Para -> appendLine(b.text)
                is Table -> b.rows.forEach { r -> appendLine(r.joinToString("\t") { c -> c.paras.joinToString(" ") { it.text } }) }
                else -> Unit
            }
        }.trimEnd()

    companion object {
        /** Plain text → paragraphs (one per line). */
        fun fromText(text: String, title: String = ""): Doc =
            Doc(text.replace("\r\n", "\n").split('\n').map { Para(listOf(Run(it))) }, title)
    }
}

/** A4 in points, the page size the Office tools use. */
object PageSize {
    const val A4_W = 595f
    const val A4_H = 842f
    const val LETTER_W = 612f
    const val LETTER_H = 792f
}
