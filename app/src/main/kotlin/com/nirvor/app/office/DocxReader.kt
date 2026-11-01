package com.nirvor.app.office

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** Reads a Word file (.docx) into the app's [Doc] model: text styles, headings, lists, tables, pictures. */
object DocxReader {
    /** All files inside a zip (DOCX / XLSX are zips), by path. */
    fun unzip(bytes: ByteArray, wanted: (String) -> Boolean = { true }): Map<String, ByteArray> {
        val out = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.trimStart('/')
                if (!e.isDirectory && wanted(name)) out[name] = zip.readBytes()
            }
        }
        return out
    }

    private val HIGHLIGHTS = mapOf(
        "yellow" to 0xFFFF00, "green" to 0x00FF00, "cyan" to 0x00FFFF, "magenta" to 0xFF00FF, "blue" to 0x0000FF,
        "red" to 0xFF0000, "darkBlue" to 0x000080, "darkCyan" to 0x008080, "darkGreen" to 0x008000,
        "darkMagenta" to 0x800080, "darkRed" to 0x800000, "darkYellow" to 0x808000, "darkGray" to 0x808080,
        "lightGray" to 0xC0C0C0, "black" to 0x000000, "white" to 0xFFFFFF,
    )

    private fun on(n: XNode?): Boolean {
        if (n == null) return false
        val v = n.attr("val") ?: return true
        return v != "0" && v != "false" && v != "none"
    }

    private fun color(v: String?): Int? =
        if (v == null || v.length != 6 || v.equals("auto", true)) null else v.toIntOrNull(16)

    private class Ctx(
        val files: Map<String, ByteArray>,
        val rels: Map<String, String>,
        val headingOf: Map<String, ParaKind>,
        val styleNum: Map<String, Pair<String, Int>>,
        val styleList: Map<String, ListKind>,
        val numFormat: (String, Int) -> ListKind,
    )

    fun read(bytes: ByteArray, title: String = ""): Doc {
        val files = unzip(bytes) { it.startsWith("word/") }
        val main = files["word/document.xml"] ?: throw IllegalArgumentException("Not a Word file")

        val rels = HashMap<String, String>()
        files["word/_rels/document.xml.rels"]?.let { r ->
            for (n in MiniXml.parse(String(r, Charsets.UTF_8)).find("Relationship")) {
                val id = n.attr("Id") ?: continue
                val target = n.attr("Target") ?: continue
                rels[id] = if (n.attr("TargetMode") == "External") target else normalize("word/", target)
            }
        }

        // heading styles by id → kind (style names are the same in every language Word uses),
        // and list styles ("List Bullet", "List Number" or a style with its own numbering)
        val headingOf = HashMap<String, ParaKind>()
        val styleNum = HashMap<String, Pair<String, Int>>() // style id → numId, level
        val styleList = HashMap<String, ListKind>()
        files["word/styles.xml"]?.let { s ->
            for (st in MiniXml.parse(String(s, Charsets.UTF_8)).find("style")) {
                val id = st.attr("styleId") ?: continue
                val name = (st.child("name")?.attr("val") ?: id).lowercase()
                kindFor(name)?.let { headingOf[id] = it } ?: kindFor(id.lowercase())?.let { headingOf[id] = it }
                st.child("pPr")?.child("numPr")?.let { np ->
                    val numId = np.child("numId")?.attr("val")
                    if (numId != null) styleNum[id] = numId to (np.child("ilvl")?.attr("val")?.toIntOrNull() ?: 0)
                }
                when {
                    name.startsWith("list bullet") -> styleList[id] = ListKind.Bullet
                    name.startsWith("list number") -> styleList[id] = ListKind.Number
                }
            }
        }

        // numbering: numId → abstractNum → level formats
        val numToAbs = HashMap<String, String>()
        val absLevels = HashMap<String, Map<Int, String>>()
        files["word/numbering.xml"]?.let { s ->
            val root = MiniXml.parse(String(s, Charsets.UTF_8))
            for (a in root.find("abstractNum")) {
                val id = a.attr("abstractNumId") ?: continue
                absLevels[id] = a.all("lvl").associate { l -> (l.attr("ilvl")?.toIntOrNull() ?: 0) to (l.child("numFmt")?.attr("val") ?: "decimal") }
            }
            for (n in root.find("num")) {
                val id = n.attr("numId") ?: continue
                n.child("abstractNumId")?.attr("val")?.let { numToAbs[id] = it }
            }
        }
        val numFormat = { numId: String, level: Int ->
            val fmt = numToAbs[numId]?.let { absLevels[it]?.get(level) } ?: "decimal"
            when (fmt) {
                "bullet" -> ListKind.Bullet
                "none" -> ListKind.None
                else -> ListKind.Number
            }
        }

        val ctx = Ctx(files, rels, headingOf, styleNum, styleList, numFormat)
        val body = MiniXml.parse(String(main, Charsets.UTF_8)).find("body").firstOrNull()
            ?: throw IllegalArgumentException("Empty Word file")
        val blocks = mutableListOf<Block>()
        bodyBlocks(body, ctx, blocks)
        return Doc(blocks, title)
    }

    private fun kindFor(name: String): ParaKind? = when (name.replace(" ", "")) {
        "title" -> ParaKind.Title
        "heading1" -> ParaKind.H1
        "heading2" -> ParaKind.H2
        "heading3", "heading4", "heading5", "heading6" -> ParaKind.H3
        "quote", "intensequote" -> ParaKind.Quote
        else -> null
    }

    private fun normalize(base: String, target: String): String {
        if (target.startsWith("/")) return target.trimStart('/')
        val parts = (base + target).split('/').toMutableList()
        val out = mutableListOf<String>()
        for (p in parts) when (p) {
            ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1)
            ".", "" -> Unit
            else -> out.add(p)
        }
        return out.joinToString("/")
    }

    private fun bodyBlocks(parent: XNode, ctx: Ctx, out: MutableList<Block>) {
        for (n in parent.elements()) {
            when (n.local) {
                "p" -> paragraph(n, ctx, out)
                "tbl" -> out.add(table(n, ctx))
                "sdt" -> n.child("sdtContent")?.let { bodyBlocks(it, ctx, out) }
                "customXml", "ins", "smartTag" -> bodyBlocks(n, ctx, out)
            }
        }
    }

    private fun table(tbl: XNode, ctx: Ctx): Table {
        val rows = mutableListOf<List<Cell>>()
        var header = false
        tbl.all("tr").forEachIndexed { i, tr ->
            if (i == 0 && tr.child("trPr")?.child("tblHeader") != null) header = true
            val cells = tr.all("tc").map { tc ->
                val blocks = mutableListOf<Block>()
                bodyBlocks(tc, ctx, blocks)
                val paras = blocks.flatMap { b ->
                    when (b) {
                        is Para -> listOf(b)
                        is Table -> b.rows.map { r -> Para(listOf(Run(r.joinToString("  ") { c -> c.paras.joinToString(" ") { it.text } }))) }
                        else -> emptyList()
                    }
                }
                val bg = color(tc.child("tcPr")?.child("shd")?.attr("fill"))
                Cell(paras, bg)
            }
            rows.add(cells)
        }
        return Table(rows, header)
    }

    private fun paragraph(p: XNode, ctx: Ctx, out: MutableList<Block>) {
        val ppr = p.child("pPr")
        val styleId = ppr?.child("pStyle")?.attr("val")
        val kind = styleId?.let { ctx.headingOf[it] ?: kindFor(it.lowercase()) } ?: ParaKind.Normal
        val align = when (ppr?.child("jc")?.attr("val")) {
            "center" -> Align.Center
            "right", "end" -> Align.Right
            "both", "distribute", "thaiDistribute" -> Align.Justify
            else -> Align.Left
        }
        var list = ListKind.None
        var level = 0
        val numPr = ppr?.child("numPr")
        if (numPr != null) {
            level = numPr.child("ilvl")?.attr("val")?.toIntOrNull() ?: styleId?.let { ctx.styleNum[it]?.second } ?: 0
            val numId = numPr.child("numId")?.attr("val") ?: styleId?.let { ctx.styleNum[it]?.first }
            if (numId != null && numId != "0") list = ctx.numFormat(numId, level)
        } else if (styleId != null) {
            val sn = ctx.styleNum[styleId]
            if (sn != null && sn.first != "0") {
                level = sn.second
                list = ctx.numFormat(sn.first, level)
            } else {
                ctx.styleList[styleId]?.let { list = it }
            }
        }
        val runs = mutableListOf<Run>()
        var pageBreakAfter = false
        var hadPicture = false

        fun flushText() {
            if (runs.isNotEmpty()) {
                out.add(Para(runs.toList(), kind, align, list, level).compact())
                runs.clear()
            }
        }

        fun run(r: XNode, link: String?) {
            val rpr = r.child("rPr")
            val bold = on(rpr?.child("b"))
            val italic = on(rpr?.child("i"))
            val underline = on(rpr?.child("u"))
            val strike = on(rpr?.child("strike")) || on(rpr?.child("dstrike"))
            val col = color(rpr?.child("color")?.attr("val"))
            val size = rpr?.child("sz")?.attr("val")?.toFloatOrNull()?.div(2f)
            val hl = rpr?.child("highlight")?.attr("val")?.let { HIGHLIGHTS[it] } ?: color(rpr?.child("shd")?.attr("fill"))
            val sb = StringBuilder()
            fun emit() {
                if (sb.isNotEmpty()) {
                    runs.add(Run(sb.toString(), bold, italic, underline, strike, col, hl, size, link))
                    sb.setLength(0)
                }
            }
            for (c in r.elements()) {
                when (c.local) {
                    "t" -> sb.append(c.text())
                    "tab" -> sb.append('\t')
                    "br", "cr" -> if (c.attr("type") == "page") {
                        emit()
                        pageBreakAfter = true
                    } else {
                        sb.append('\n')
                    }
                    "noBreakHyphen" -> sb.append('-')
                    "sym" -> c.attr("char")?.toIntOrNull(16)?.let { code -> if (code in 0x20..0xEFFF && code !in 0xD800..0xDFFF) sb.append(code.toChar()) }
                    "drawing", "pict" -> {
                        emit()
                        picture(c, ctx)?.let { pic ->
                            flushText()
                            out.add(pic)
                            hadPicture = true
                        }
                    }
                }
            }
            emit()
        }

        fun walk(n: XNode, link: String?) {
            for (c in n.elements()) {
                when (c.local) {
                    "r" -> run(c, link)
                    "hyperlink" -> walk(c, c.attr("id")?.let { ctx.rels[it] })
                    "ins", "smartTag", "customXml", "fldSimple", "sdt", "sdtContent", "bdo", "dir" -> walk(c, link)
                }
            }
        }
        walk(p, null)
        val empty = runs.all { it.text.isEmpty() }
        when {
            !empty -> out.add(Para(runs.toList(), kind, align, list, level).compact())
            hadPicture || pageBreakAfter -> Unit
            ppr?.child("pBdr")?.child("bottom") != null -> out.add(Rule)
            else -> out.add(Para(emptyList(), kind, align, list, level))
        }
        if (pageBreakAfter) out.add(PageBreak)
    }

    private fun picture(n: XNode, ctx: Ctx): Picture? {
        val blip = n.find("blip").firstOrNull() ?: n.find("imagedata").firstOrNull() ?: return null
        val id = blip.attr("r:embed") ?: blip.attr("embed") ?: blip.attr("r:id") ?: return null
        val path = ctx.rels[id] ?: return null
        val bytes = ctx.files[path] ?: return null
        val lower = path.lowercase()
        val mime = when {
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".bmp") -> "image/bmp"
            else -> return null // emf / wmf drawings cannot be shown
        }
        val ext = n.find("extent").firstOrNull()
        val w = ext?.attr("cx")?.toLongOrNull()?.let { it / 12700f } ?: 300f
        val h = ext?.attr("cy")?.toLongOrNull()?.let { it / 12700f } ?: 200f
        return Picture(bytes, mime, w, h)
    }
}
