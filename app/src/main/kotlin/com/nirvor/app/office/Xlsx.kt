package com.nirvor.app.office

import java.io.OutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One sheet of a table: a name and rows of cell texts. */
class SheetData(val name: String, val rows: List<List<String>>) {
    val columns: Int get() = rows.maxOfOrNull { it.size } ?: 0
}

/** Writes Excel files (.xlsx). Numbers become real numbers, the first row can be a bold header. */
object XlsxWriter {
    private val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

    /** "১,২৫০.৫০" / "1,250.50" / "-42" → number; texts with leading zeros (phone numbers, IDs) stay text. */
    fun asNumber(raw: String): Double? {
        var s = raw.trim()
        if (s.isEmpty() || s.length > 22) return null
        s = buildString(s.length) { s.forEach { c -> val i = BN_DIGITS.indexOf(c); append(if (i >= 0) ('0' + i) else c) } }
        val neg = s.startsWith("(") && s.endsWith(")")
        if (neg) s = s.substring(1, s.length - 1)
        if (!Regex("^-?(\\d{1,3}(,\\d{3})+|\\d+)(\\.\\d+)?$").matches(s)) return null
        val digits = s.removePrefix("-")
        if (digits.length > 1 && digits[0] == '0' && digits[1] != '.') return null
        val v = s.replace(",", "").toDoubleOrNull() ?: return null
        return if (neg) -v else v
    }

    private fun colName(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun sheetName(raw: String, used: MutableSet<String>): String {
        val base = raw.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().ifEmpty { "Sheet" }.take(26)
        var name = base
        var k = 2
        while (!used.add(name.lowercase())) {
            name = "$base ($k)"
            k++
        }
        return name
    }

    private fun num(v: Double): String =
        if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else BigDecimal(v).round(java.math.MathContext(15)).stripTrailingZeros().toPlainString()

    fun write(sheets: List<SheetData>, out: OutputStream, header: Boolean = true) {
        val used = HashSet<String>()
        val names = sheets.map { sheetName(it.name, used) }
        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val ct = StringBuilder()
            ct.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            ct.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            ct.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            ct.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            ct.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
            ct.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>")
            sheets.indices.forEach {
                ct.append("<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
            }
            ct.append("</Types>")
            put("[Content_Types].xml", ct.toString())
            put(
                "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>",
            )
            val wb = StringBuilder()
            wb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            wb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>")
            names.forEachIndexed { i, n -> wb.append("<sheet name=\"${MiniXml.esc(n)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>") }
            wb.append("</sheets></workbook>")
            put("xl/workbook.xml", wb.toString())
            val rels = StringBuilder()
            rels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            rels.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            sheets.indices.forEach {
                rels.append("<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>")
            }
            rels.append("<Relationship Id=\"rId${sheets.size + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>")
            rels.append("</Relationships>")
            put("xl/_rels/workbook.xml.rels", rels.toString())
            put("xl/styles.xml", STYLES)
            sheets.forEachIndexed { si, sheet -> put("xl/worksheets/sheet${si + 1}.xml", sheetXml(sheet, header)) }
        }
    }

    private fun sheetXml(sheet: SheetData, header: Boolean): String {
        val cols = sheet.columns
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
        if (sheet.rows.isNotEmpty() && cols > 0) sb.append("<dimension ref=\"A1:${colName(cols - 1)}${sheet.rows.size}\"/>")
        if (header && sheet.rows.size > 1) {
            sb.append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        }
        if (cols > 0) {
            sb.append("<cols>")
            for (c in 0 until cols) {
                val longest = sheet.rows.maxOfOrNull { r -> r.getOrNull(c)?.lines()?.maxOfOrNull { it.length } ?: 0 } ?: 0
                val w = (longest * 1.15 + 3).coerceIn(6.0, 60.0)
                sb.append("<col min=\"${c + 1}\" max=\"${c + 1}\" width=\"${String.format(java.util.Locale.US, "%.1f", w)}\" customWidth=\"1\"/>")
            }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        sheet.rows.forEachIndexed { ri, row ->
            sb.append("<row r=\"${ri + 1}\">")
            val style = if (header && ri == 0) 1 else 2
            row.forEachIndexed { ci, raw ->
                val ref = "${colName(ci)}${ri + 1}"
                val n = if (header && ri == 0) null else asNumber(raw)
                when {
                    n != null -> sb.append("<c r=\"$ref\" s=\"$style\"><v>${num(n)}</v></c>")
                    raw.isEmpty() -> sb.append("<c r=\"$ref\" s=\"$style\"/>")
                    else -> sb.append("<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${MiniXml.esc(raw)}</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        sb.append("<pageMargins left=\"0.5\" right=\"0.5\" top=\"0.6\" bottom=\"0.6\" header=\"0.3\" footer=\"0.3\"/>")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private const val STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
        "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
        "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/><family val=\"2\"/></font>" +
        "<font><b/><sz val=\"11\"/><color rgb=\"FF0B3D27\"/><name val=\"Calibri\"/><family val=\"2\"/></font></fonts>" +
        "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>" +
        "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFE3F6EC\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>" +
        "<borders count=\"2\"><border><left/><right/><top/><bottom/><diagonal/></border>" +
        "<border><left style=\"thin\"><color rgb=\"FFC9D2DC\"/></left><right style=\"thin\"><color rgb=\"FFC9D2DC\"/></right>" +
        "<top style=\"thin\"><color rgb=\"FFC9D2DC\"/></top><bottom style=\"thin\"><color rgb=\"FFC9D2DC\"/></bottom><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"1\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyBorder=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"1\" xfId=\"0\" applyBorder=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf></cellXfs>" +
        "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>" +
        "</styleSheet>"
}

/** Reads Excel (.xlsx) and CSV files into [SheetData]. */
object XlsxReader {
    fun read(bytes: ByteArray): List<SheetData> {
        val files = DocxReader.unzip(bytes) { it.startsWith("xl/") }
        val wbXml = files["xl/workbook.xml"] ?: throw IllegalArgumentException("Not an Excel file")
        val rels = HashMap<String, String>()
        files["xl/_rels/workbook.xml.rels"]?.let { r ->
            for (n in MiniXml.parse(String(r, Charsets.UTF_8)).find("Relationship")) {
                val id = n.attr("Id") ?: continue
                val t = n.attr("Target") ?: continue
                rels[id] = if (t.startsWith("/")) t.trimStart('/') else "xl/" + t.removePrefix("./")
            }
        }
        val shared = files["xl/sharedStrings.xml"]?.let { s ->
            MiniXml.parse(String(s, Charsets.UTF_8)).find("si").map { si ->
                // plain <t>, or rich text runs <r><t>; phonetic hints (<rPh>) are skipped
                si.elements().joinToString("") { e ->
                    when (e.local) {
                        "t" -> e.text()
                        "r" -> e.child("t")?.text() ?: ""
                        else -> ""
                    }
                }
            }
        } ?: emptyList()

        // which cell styles are dates
        val dateStyles = HashSet<Int>()
        files["xl/styles.xml"]?.let { s ->
            val root = MiniXml.parse(String(s, Charsets.UTF_8))
            val custom = root.find("numFmt").associate { (it.attr("numFmtId")?.toIntOrNull() ?: -1) to (it.attr("formatCode") ?: "") }
            root.find("cellXfs").firstOrNull()?.all("xf")?.forEachIndexed { i, xf ->
                val id = xf.attr("numFmtId")?.toIntOrNull() ?: 0
                val code = custom[id]
                val isDate = id in 14..22 || id in 45..47 ||
                    (code != null && code.replace(Regex("\"[^\"]*\"|\\[[^]]*]"), "").let { c -> c.contains('y', true) || c.contains('d', true) || (c.contains('m', true) && c.contains('h', true)) })
                if (isDate) dateStyles.add(i)
            }
        }

        val sheets = mutableListOf<SheetData>()
        for (sh in MiniXml.parse(String(wbXml, Charsets.UTF_8)).find("sheet")) {
            val name = sh.attr("name") ?: "Sheet"
            val rid = sh.attr("r:id") ?: sh.attr("id") ?: continue
            val path = rels[rid] ?: continue
            val xml = files[path] ?: continue
            sheets.add(SheetData(name, sheetRows(String(xml, Charsets.UTF_8), shared, dateStyles)))
        }
        return sheets
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (c in ref) {
            if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1) else if (c in 'a'..'z') n = n * 26 + (c - 'a' + 1) else break
        }
        return n - 1
    }

    private val dateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    private fun number(v: String, style: Int, dates: Set<Int>): String {
        val d = v.toDoubleOrNull() ?: return v
        if (style in dates && d > 0 && d < 2958466) {
            val days = Math.floor(d).toLong()
            val frac = d - days
            val base = LocalDate.of(1899, 12, 30).plusDays(days)
            return if (frac > 1e-6) LocalDateTime.of(base, java.time.LocalTime.ofSecondOfDay(Math.round(frac * 86400).coerceAtMost(86399))).format(dateTimeFmt) else base.format(dateFmt)
        }
        if (d == Math.floor(d) && Math.abs(d) < 1e15) return d.toLong().toString()
        return BigDecimal(d).round(java.math.MathContext(12)).stripTrailingZeros().toPlainString()
    }

    private fun sheetRows(xml: String, shared: List<String>, dates: Set<Int>): List<List<String>> {
        val root = MiniXml.parse(xml)
        val data = root.find("sheetData").firstOrNull() ?: return emptyList()
        val grid = sortedMapOf<Int, MutableMap<Int, String>>()
        var nextRow = 0
        for (row in data.all("row")) {
            val r = (row.attr("r")?.toIntOrNull()?.minus(1)) ?: nextRow
            nextRow = r + 1
            var nextCol = 0
            val cells = grid.getOrPut(r) { HashMap() }
            for (c in row.all("c")) {
                val col = c.attr("r")?.let { colIndex(it) }?.takeIf { it >= 0 } ?: nextCol
                nextCol = col + 1
                val t = c.attr("t")
                val v = c.child("v")?.text()
                val style = c.attr("s")?.toIntOrNull() ?: 0
                val text = when (t) {
                    "s" -> v?.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                    "inlineStr" -> c.child("is")?.find("t")?.joinToString("") { it.text() } ?: ""
                    "b" -> if (v == "1") "TRUE" else "FALSE"
                    "str", "e" -> v ?: ""
                    else -> v?.let { number(it, style, dates) } ?: ""
                }
                if (text.isNotEmpty()) cells[col] = text
            }
        }
        val maxCol = grid.values.maxOfOrNull { m -> m.keys.maxOrNull() ?: -1 } ?: -1
        if (maxCol < 0) return emptyList()
        val lastRow = grid.entries.lastOrNull { it.value.isNotEmpty() }?.key ?: return emptyList()
        return (0..lastRow).map { r -> val m = grid[r]; (0..maxCol).map { c -> m?.get(c) ?: "" } }
    }
}

/** Comma / semicolon / tab separated text files. */
object Csv {
    fun read(text: String): List<List<String>> {
        val t = text.removePrefix("﻿")
        val first = t.lineSequence().firstOrNull().orEmpty()
        val sep = listOf(',', ';', '\t').maxByOrNull { c -> first.count { it == c } } ?: ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < t.length && t[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    cell.append(c)
                }
            } else {
                when (c) {
                    '"' -> quoted = true
                    sep -> {
                        row.add(cell.toString())
                        cell.setLength(0)
                    }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(cell.toString())
                        cell.setLength(0)
                        rows.add(row)
                        row = mutableListOf()
                    }
                    else -> cell.append(c)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }

    fun write(rows: List<List<String>>): String = buildString {
        append('﻿')
        for (r in rows) {
            appendLine(r.joinToString(",") { c -> if (c.any { it == ',' || it == '"' || it == '\n' }) "\"" + c.replace("\"", "\"\"") + "\"" else c })
        }
    }
}
