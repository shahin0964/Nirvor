package com.nirvor.app.office

import java.util.Base64
import kotlin.math.roundToInt

/**
 * Bridges the Word editor (an HTML page) and the [Doc] model:
 * [toHtml] fills the editor, [fromJson] reads what the editor hands back.
 */
object HtmlDoc {
    /** Editor px ↔ document pt (the editor's 16 px body text is a 12 pt line in the file). */
    const val PX_PER_PT = 1f / 0.75f

    /** Text width of an A4 page with 2 cm margins, in points (pictures are sized against it). */
    const val CONTENT_PT = 481.6f

    private fun hex(c: Int) = String.format("#%06x", c and 0xFFFFFF)

    private fun color(s: String?): Int? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        if (t.startsWith("#") && t.length == 7) return t.substring(1).toIntOrNull(16)
        val m = Regex("rgba?\\((\\d+),\\s*(\\d+),\\s*(\\d+)(?:,\\s*([\\d.]+))?\\)").find(t) ?: return null
        val a = m.groupValues[4].toFloatOrNull()
        if (a != null && a < 0.05f) return null
        return (m.groupValues[1].toInt() shl 16) or (m.groupValues[2].toInt() shl 8) or m.groupValues[3].toInt()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun runHtml(r: Run): String {
        val style = StringBuilder()
        if (r.bold) style.append("font-weight:bold;")
        if (r.italic) style.append("font-style:italic;")
        val deco = listOfNotNull(if (r.underline) "underline" else null, if (r.strike) "line-through" else null)
        if (deco.isNotEmpty()) style.append("text-decoration:${deco.joinToString(" ")};")
        r.color?.let { style.append("color:${hex(it)};") }
        r.highlight?.let { style.append("background-color:${hex(it)};") }
        r.size?.let { style.append("font-size:${String.format(java.util.Locale.US, "%.1f", it * PX_PER_PT)}px;") }
        val text = esc(r.text).replace("\n", "<br>")
        val inner = if (style.isEmpty()) text else "<span style=\"$style\">$text</span>"
        return if (r.link != null) "<a href=\"${esc(r.link)}\">$inner</a>" else inner
    }

    private fun alignStyle(a: Align) = when (a) {
        Align.Center -> " style=\"text-align:center\""
        Align.Right -> " style=\"text-align:right\""
        Align.Justify -> " style=\"text-align:justify\""
        Align.Left -> ""
    }

    private fun inner(p: Para): String = p.runs.joinToString("") { runHtml(it) }.ifEmpty { "<br>" }

    private fun paraHtml(p: Para): String {
        val a = alignStyle(p.align)
        val body = inner(p)
        return when (p.kind) {
            ParaKind.Title -> "<h1 class=\"title\"$a>$body</h1>"
            ParaKind.H1 -> "<h1$a>$body</h1>"
            ParaKind.H2 -> "<h2$a>$body</h2>"
            ParaKind.H3 -> "<h3$a>$body</h3>"
            ParaKind.Quote -> "<blockquote$a>$body</blockquote>"
            ParaKind.Normal -> "<p$a>$body</p>"
        }
    }

    fun toHtml(doc: Doc): String {
        val sb = StringBuilder()
        val blocks = doc.blocks
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            if (b is Para && b.list != ListKind.None) {
                // a run of list items, nested by level
                val open = ArrayDeque<String>()
                var level = -1
                while (i < blocks.size) {
                    val p = blocks[i] as? Para ?: break
                    if (p.list == ListKind.None) break
                    val tag = if (p.list == ListKind.Bullet) "ul" else "ol"
                    val lv = p.level.coerceIn(0, 6)
                    while (level < lv) {
                        sb.append("<$tag>")
                        open.addLast(tag)
                        level++
                    }
                    while (level > lv) {
                        sb.append("</${open.removeLast()}>")
                        level--
                    }
                    if (open.last() != tag) {
                        sb.append("</${open.removeLast()}><$tag>")
                        open.addLast(tag)
                    }
                    sb.append("<li${alignStyle(p.align)}>").append(inner(p)).append("</li>")
                    i++
                }
                while (open.isNotEmpty()) sb.append("</${open.removeLast()}>")
                continue
            }
            when (b) {
                is Para -> sb.append(paraHtml(b))
                is Table -> {
                    sb.append("<table>")
                    b.rows.forEachIndexed { ri, row ->
                        sb.append("<tr>")
                        for (ci in 0 until b.columns) {
                            val cell = row.getOrNull(ci)
                            val tag = if (ri == 0 && b.header) "th" else "td"
                            val bg = cell?.bg?.let { " style=\"background-color:${hex(it)}\"" } ?: ""
                            val content = cell?.paras?.joinToString("") { "<p${alignStyle(it.align)}>${inner(it)}</p>" } ?: ""
                            sb.append("<$tag$bg>").append(content.ifEmpty { "<p><br></p>" }).append("</$tag>")
                        }
                        sb.append("</tr>")
                    }
                    sb.append("</table>")
                    // room to type after a table that ends the document
                    if (i == blocks.size - 1) sb.append("<p><br></p>")
                }
                is Picture -> {
                    val data = Base64.getEncoder().encodeToString(b.bytes)
                    val pct = (b.widthPt / CONTENT_PT * 100f).coerceIn(5f, 100f).roundToInt()
                    sb.append("<p style=\"text-align:center\"><img src=\"data:${b.mime};base64,$data\" style=\"width:$pct%\"></p>")
                }
                PageBreak -> sb.append("<div class=\"pb\" contenteditable=\"false\"></div>")
                Rule -> sb.append("<hr>")
            }
            i++
        }
        return sb.toString().ifEmpty { "<p><br></p>" }
    }

    private fun para(o: Map<String, Any?>): Para {
        val runs = o["runs"].arr().map { r ->
            val m = r.obj()
            Run(
                m.str("x"),
                bold = m.flag("b"),
                italic = m.flag("i"),
                underline = m.flag("u"),
                strike = m.flag("s"),
                color = color(m["c"] as? String),
                highlight = color(m["h"] as? String),
                size = m.num("z")?.toFloat(),
                link = (m["a"] as? String)?.takeIf { it.isNotBlank() },
            )
        }
        val kind = when (o.str("kind")) {
            "title" -> ParaKind.Title
            "h1" -> ParaKind.H1
            "h2" -> ParaKind.H2
            "h3" -> ParaKind.H3
            "quote" -> ParaKind.Quote
            else -> ParaKind.Normal
        }
        val align = when (o.str("align")) {
            "center" -> Align.Center
            "right", "end" -> Align.Right
            "justify" -> Align.Justify
            else -> Align.Left
        }
        val list = when (o.str("list")) {
            "bullet" -> ListKind.Bullet
            "number" -> ListKind.Number
            else -> ListKind.None
        }
        return Para(runs, kind, align, list, (o.num("level") ?: 0.0).toInt()).compact()
    }

    /** The editor's export (see the editor page's toModel()) → [Doc]. */
    fun fromJson(json: String): Doc {
        val root = MiniJson.parse(json).obj()
        val blocks = mutableListOf<Block>()
        for (b in root["blocks"].arr()) {
            val o = b.obj()
            when (o.str("t")) {
                "p" -> blocks.add(para(o))
                "table" -> {
                    val rows = o["rows"].arr().map { r ->
                        r.arr().map { c ->
                            val cm = c.obj()
                            Cell(cm["paras"].arr().map { para(it.obj()) }, color(cm["bg"] as? String))
                        }
                    }
                    if (rows.isNotEmpty()) blocks.add(Table(rows, o.flag("header")))
                }
                "img" -> {
                    val src = o.str("src")
                    val comma = src.indexOf(',')
                    if (src.startsWith("data:") && comma > 0) {
                        val mime = src.substring(5, src.indexOf(';').takeIf { it in 5 until comma } ?: comma)
                        val bytes = try {
                            Base64.getDecoder().decode(src.substring(comma + 1))
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                        if (bytes != null) {
                            // the editor sends the picture's size on the page in points
                            val w = (o.num("w") ?: 300.0).toFloat()
                            val h = (o.num("h") ?: 200.0).toFloat()
                            blocks.add(Picture(bytes, mime, w, h))
                        }
                    }
                }
                "hr" -> blocks.add(Rule)
                "pb" -> blocks.add(PageBreak)
            }
        }
        return Doc(blocks, root.str("title"))
    }
}
