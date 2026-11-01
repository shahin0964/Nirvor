package com.nirvor.app.office

import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a [Doc] as a Word file (.docx, Office Open XML) that Word, Google Docs and WPS open. */
object DocxWriter {
    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val R_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val EMU_PER_PT = 12700L

    /** Content width of an A4 page with 2 cm margins, in points. */
    const val CONTENT_W_PT = 595f - 2 * 56.7f

    fun write(doc: Doc, out: OutputStream) {
        val media = mutableListOf<Pair<String, ByteArray>>() // file name, bytes
        val rels = StringBuilder()
        var relId = 10
        var pictureId = 1
        val numInstances = mutableListOf<Int>() // abstract id per w:num (1 = bullet, 2 = number)

        fun rel(type: String, target: String, external: Boolean = false): String {
            val id = "rId${relId++}"
            rels.append("<Relationship Id=\"$id\" Type=\"$R_NS/$type\" Target=\"${MiniXml.esc(target)}\"")
            if (external) rels.append(" TargetMode=\"External\"")
            rels.append("/>")
            return id
        }

        val body = StringBuilder()

        fun runXml(r: Run, sb: StringBuilder) {
            if (r.text.isEmpty()) return
            val rpr = StringBuilder()
            if (r.link != null) rpr.append("<w:rStyle w:val=\"Hyperlink\"/>")
            if (r.bold) rpr.append("<w:b/><w:bCs/>")
            if (r.italic) rpr.append("<w:i/><w:iCs/>")
            if (r.strike) rpr.append("<w:strike/>")
            r.color?.let { rpr.append("<w:color w:val=\"${hex(it)}\"/>") }
            r.size?.let {
                val hp = (it * 2).toInt().coerceIn(2, 3276)
                rpr.append("<w:sz w:val=\"$hp\"/><w:szCs w:val=\"$hp\"/>")
            }
            // schema order: … sz, szCs, u, …, shd
            if (r.underline) rpr.append("<w:u w:val=\"single\"/>")
            r.highlight?.let { rpr.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"${hex(it)}\"/>") }
            sb.append("<w:r>")
            if (rpr.isNotEmpty()) sb.append("<w:rPr>").append(rpr).append("</w:rPr>")
            val parts = r.text.split('\n')
            parts.forEachIndexed { i, line ->
                if (i > 0) sb.append("<w:br/>")
                val tabs = line.split('\t')
                tabs.forEachIndexed { k, t ->
                    if (k > 0) sb.append("<w:tab/>")
                    if (t.isNotEmpty()) sb.append("<w:t xml:space=\"preserve\">").append(MiniXml.esc(t)).append("</w:t>")
                }
            }
            sb.append("</w:r>")
        }

        // numbered lists restart after anything that is not part of the list
        var currentNumId = 0
        var lastWasNumbered = false

        fun paraXml(p: Para, sb: StringBuilder, inTable: Boolean = false) {
            sb.append("<w:p>")
            val ppr = StringBuilder()
            when (p.kind) {
                ParaKind.Title -> "Title"
                ParaKind.H1 -> "Heading1"
                ParaKind.H2 -> "Heading2"
                ParaKind.H3 -> "Heading3"
                ParaKind.Quote -> "Quote"
                ParaKind.Normal -> if (p.list != ListKind.None) "ListParagraph" else null
            }?.let { ppr.append("<w:pStyle w:val=\"$it\"/>") }
            if (p.list != ListKind.None) {
                val numId = if (p.list == ListKind.Bullet) {
                    1
                } else {
                    if (!lastWasNumbered || currentNumId == 0) {
                        numInstances.add(2)
                        currentNumId = numInstances.size + 1
                    }
                    currentNumId
                }
                ppr.append("<w:numPr><w:ilvl w:val=\"${p.level.coerceIn(0, 8)}\"/><w:numId w:val=\"$numId\"/></w:numPr>")
            }
            if (inTable) ppr.append("<w:spacing w:before=\"0\" w:after=\"0\"/>")
            when (p.align) {
                Align.Center -> "center"
                Align.Right -> "right"
                Align.Justify -> "both"
                Align.Left -> null
            }?.let { ppr.append("<w:jc w:val=\"$it\"/>") }
            if (ppr.isNotEmpty()) sb.append("<w:pPr>").append(ppr).append("</w:pPr>")
            var i = 0
            val runs = p.compact().runs
            while (i < runs.size) {
                val r = runs[i]
                if (r.link != null) {
                    val id = rel("hyperlink", r.link, external = true)
                    sb.append("<w:hyperlink r:id=\"$id\">")
                    while (i < runs.size && runs[i].link == r.link) {
                        runXml(runs[i], sb)
                        i++
                    }
                    sb.append("</w:hyperlink>")
                } else {
                    runXml(r, sb)
                    i++
                }
            }
            sb.append("</w:p>")
        }

        fun pictureXml(pic: Picture, sb: StringBuilder) {
            val ext = if (pic.mime.contains("png")) "png" else "jpeg"
            val name = "image${media.size + 1}.${if (ext == "png") "png" else "jpg"}"
            media.add(name to pic.bytes)
            val id = rel("image", "media/$name")
            var w = pic.widthPt
            var h = pic.heightPt
            if (w > CONTENT_W_PT) {
                h *= CONTENT_W_PT / w
                w = CONTENT_W_PT
            }
            val maxH = 842f - 2 * 56.7f
            if (h > maxH) {
                w *= maxH / h
                h = maxH
            }
            val cx = (w * EMU_PER_PT).toLong()
            val cy = (h * EMU_PER_PT).toLong()
            val pid = pictureId++
            sb.append("<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr><w:r><w:drawing>")
            sb.append("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\"><wp:extent cx=\"$cx\" cy=\"$cy\"/>")
            sb.append("<wp:docPr id=\"$pid\" name=\"Picture $pid\"/>")
            sb.append("<wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>")
            sb.append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic>")
            sb.append("<pic:nvPicPr><pic:cNvPr id=\"$pid\" name=\"$name\"/><pic:cNvPicPr/></pic:nvPicPr>")
            sb.append("<pic:blipFill><a:blip r:embed=\"$id\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>")
            sb.append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm>")
            sb.append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic>")
            sb.append("</wp:inline></w:drawing></w:r></w:p>")
        }

        fun tableXml(t: Table, sb: StringBuilder) {
            val cols = t.columns.coerceAtLeast(1)
            val total = 9638 // content width in twips (A4, 2 cm margins)
            val colW = total / cols
            sb.append("<w:tbl><w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"$total\" w:type=\"dxa\"/>")
            sb.append("<w:tblLook w:val=\"04A0\" w:firstRow=\"1\" w:lastRow=\"0\" w:firstColumn=\"0\" w:lastColumn=\"0\" w:noHBand=\"0\" w:noVBand=\"1\"/></w:tblPr>")
            sb.append("<w:tblGrid>")
            repeat(cols) { sb.append("<w:gridCol w:w=\"$colW\"/>") }
            sb.append("</w:tblGrid>")
            t.rows.forEachIndexed { ri, row ->
                sb.append("<w:tr>")
                if (ri == 0 && t.header) sb.append("<w:trPr><w:tblHeader/></w:trPr>")
                for (ci in 0 until cols) {
                    val cell = row.getOrNull(ci)
                    sb.append("<w:tc><w:tcPr><w:tcW w:w=\"$colW\" w:type=\"dxa\"/>")
                    val bg = cell?.bg ?: if (ri == 0 && t.header) 0xE3F6EC else null
                    bg?.let { sb.append("<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"${hex(it)}\"/>") }
                    sb.append("</w:tcPr>")
                    val paras = cell?.paras.orEmpty()
                    if (paras.isEmpty()) {
                        sb.append("<w:p><w:pPr><w:spacing w:before=\"0\" w:after=\"0\"/></w:pPr></w:p>")
                    } else {
                        paras.forEach { p ->
                            val hp = if (ri == 0 && t.header) Para(p.runs.map { r -> Run(r.text, true, r.italic, r.underline, r.strike, r.color, r.highlight, r.size, r.link) }, p.kind, p.align, p.list, p.level) else p
                            lastWasNumbered = false
                            paraXml(hp, sb, inTable = true)
                        }
                    }
                    sb.append("</w:tc>")
                }
                sb.append("</w:tr>")
            }
            sb.append("</w:tbl>")
        }

        for (b in doc.blocks) {
            val numbered = b is Para && b.list == ListKind.Number
            when (b) {
                is Para -> paraXml(b, body)
                is Table -> tableXml(b, body)
                is Picture -> pictureXml(b, body)
                PageBreak -> body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
                Rule -> body.append("<w:p><w:pPr><w:pBdr><w:bottom w:val=\"single\" w:sz=\"8\" w:space=\"1\" w:color=\"BFC7D1\"/></w:pBdr></w:pPr></w:p>")
            }
            lastWasNumbered = numbered || (b is Para && b.list == ListKind.Bullet && lastWasNumbered)
        }
        if (doc.blocks.lastOrNull() !is Para) body.append("<w:p/>")

        val document = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<w:document xmlns:w=\"$W_NS\" xmlns:r=\"$R_NS\" ")
            append("xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" ")
            append("xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" ")
            append("xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">")
            append("<w:body>").append(body)
            append("<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>")
            append("<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/>")
            append("</w:sectPr></w:body></w:document>")
        }

        val numbering = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:numbering xmlns:w=\"$W_NS\">")
            append("<w:abstractNum w:abstractNumId=\"1\"><w:multiLevelType w:val=\"hybridMultilevel\"/>")
            val bullets = listOf("•", "◦", "▪")
            for (l in 0..8) {
                val ind = 720 + 360 * l
                append("<w:lvl w:ilvl=\"$l\"><w:start w:val=\"1\"/><w:numFmt w:val=\"bullet\"/><w:lvlText w:val=\"${bullets[l % 3]}\"/><w:lvlJc w:val=\"left\"/>")
                append("<w:pPr><w:ind w:left=\"$ind\" w:hanging=\"360\"/></w:pPr></w:lvl>")
            }
            append("</w:abstractNum>")
            append("<w:abstractNum w:abstractNumId=\"2\"><w:multiLevelType w:val=\"hybridMultilevel\"/>")
            val fmts = listOf("decimal", "lowerLetter", "lowerRoman")
            for (l in 0..8) {
                val ind = 720 + 360 * l
                append("<w:lvl w:ilvl=\"$l\"><w:start w:val=\"1\"/><w:numFmt w:val=\"${fmts[l % 3]}\"/><w:lvlText w:val=\"%${l + 1}.\"/><w:lvlJc w:val=\"left\"/>")
                append("<w:pPr><w:ind w:left=\"$ind\" w:hanging=\"360\"/></w:pPr></w:lvl>")
            }
            append("</w:abstractNum>")
            append("<w:num w:numId=\"1\"><w:abstractNumId w:val=\"1\"/></w:num>")
            numInstances.forEachIndexed { i, abs ->
                append("<w:num w:numId=\"${i + 2}\"><w:abstractNumId w:val=\"$abs\"/>")
                append("<w:lvlOverride w:ilvl=\"0\"><w:startOverride w:val=\"1\"/></w:lvlOverride></w:num>")
            }
            append("</w:numbering>")
        }

        val docRels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"$R_NS/styles\" Target=\"styles.xml\"/>" +
            "<Relationship Id=\"rId2\" Type=\"$R_NS/numbering\" Target=\"numbering.xml\"/>" +
            "<Relationship Id=\"rId3\" Type=\"$R_NS/settings\" Target=\"settings.xml\"/>" +
            rels + "</Relationships>"

        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val core = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" " +
            "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">" +
            "<dc:title>${MiniXml.esc(doc.title)}</dc:title><dc:creator>Nirvor</dc:creator>" +
            "<dcterms:created xsi:type=\"dcterms:W3CDTF\">$now</dcterms:created>" +
            "<dcterms:modified xsi:type=\"dcterms:W3CDTF\">$now</dcterms:modified></cp:coreProperties>"

        ZipOutputStream(out).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put(
                "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Default Extension=\"png\" ContentType=\"image/png\"/>" +
                    "<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>" +
                    "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                    "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
                    "<Override PartName=\"/word/numbering.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.numbering+xml\"/>" +
                    "<Override PartName=\"/word/settings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.settings+xml\"/>" +
                    "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
                    "</Types>",
            )
            put(
                "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"$R_NS/officeDocument\" Target=\"word/document.xml\"/>" +
                    "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
                    "</Relationships>",
            )
            put("docProps/core.xml", core)
            put("word/document.xml", document)
            put("word/_rels/document.xml.rels", docRels)
            put("word/styles.xml", STYLES)
            put("word/numbering.xml", numbering)
            put(
                "word/settings.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:settings xmlns:w=\"$W_NS\">" +
                    "<w:defaultTabStop w:val=\"720\"/><w:characterSpacingControl w:val=\"doNotCompress\"/>" +
                    "<w:compat><w:compatSetting w:name=\"compatibilityMode\" w:uri=\"http://schemas.microsoft.com/office/word\" w:val=\"15\"/></w:compat>" +
                    "</w:settings>",
            )
            for ((name, bytes) in media) {
                zip.putNextEntry(ZipEntry("word/media/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun hex(c: Int) = String.format("%06X", c and 0xFFFFFF)

    // Bangla text uses the complex-script font (w:cs); Latin uses Calibri.
    private val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="$W_NS">
<w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Calibri" w:hAnsi="Calibri" w:eastAsia="Calibri" w:cs="Nirmala UI"/><w:sz w:val="24"/><w:szCs w:val="24"/><w:lang w:val="en-US" w:eastAsia="en-US" w:bidi="bn-BD"/></w:rPr></w:rPrDefault>
<w:pPrDefault><w:pPr><w:spacing w:after="120" w:line="276" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults>
<w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/><w:qFormat/></w:style>
<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:after="200"/></w:pPr><w:rPr><w:b/><w:bCs/><w:color w:val="0B0E22"/><w:sz w:val="52"/><w:szCs w:val="52"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="320" w:after="120"/><w:outlineLvl w:val="0"/></w:pPr><w:rPr><w:b/><w:bCs/><w:color w:val="0A8F56"/><w:sz w:val="36"/><w:szCs w:val="36"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="240" w:after="100"/><w:outlineLvl w:val="1"/></w:pPr><w:rPr><w:b/><w:bCs/><w:color w:val="1F2A3A"/><w:sz w:val="30"/><w:szCs w:val="30"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Heading3"><w:name w:val="heading 3"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:keepNext/><w:spacing w:before="200" w:after="80"/><w:outlineLvl w:val="2"/></w:pPr><w:rPr><w:b/><w:bCs/><w:color w:val="41597A"/><w:sz w:val="26"/><w:szCs w:val="26"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="Quote"><w:name w:val="Quote"/><w:basedOn w:val="Normal"/><w:next w:val="Normal"/><w:qFormat/><w:pPr><w:pBdr><w:left w:val="single" w:sz="18" w:space="8" w:color="0BA765"/></w:pBdr><w:ind w:left="360"/></w:pPr><w:rPr><w:i/><w:iCs/><w:color w:val="41597A"/></w:rPr></w:style>
<w:style w:type="paragraph" w:styleId="ListParagraph"><w:name w:val="List Paragraph"/><w:basedOn w:val="Normal"/><w:qFormat/><w:pPr><w:spacing w:after="60"/><w:ind w:left="720"/><w:contextualSpacing/></w:pPr></w:style>
<w:style w:type="character" w:default="1" w:styleId="DefaultParagraphFont"><w:name w:val="Default Paragraph Font"/><w:uiPriority w:val="1"/><w:semiHidden/></w:style>
<w:style w:type="character" w:styleId="Hyperlink"><w:name w:val="Hyperlink"/><w:rPr><w:color w:val="168FFB"/><w:u w:val="single"/></w:rPr></w:style>
<w:style w:type="table" w:default="1" w:styleId="TableNormal"><w:name w:val="Normal Table"/><w:tblPr><w:tblInd w:w="0" w:type="dxa"/><w:tblCellMar><w:top w:w="0" w:type="dxa"/><w:left w:w="108" w:type="dxa"/><w:bottom w:w="0" w:type="dxa"/><w:right w:w="108" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style>
<w:style w:type="table" w:styleId="TableGrid"><w:name w:val="Table Grid"/><w:basedOn w:val="TableNormal"/><w:tblPr><w:tblBorders><w:top w:val="single" w:sz="4" w:space="0" w:color="AAB4C0"/><w:left w:val="single" w:sz="4" w:space="0" w:color="AAB4C0"/><w:bottom w:val="single" w:sz="4" w:space="0" w:color="AAB4C0"/><w:right w:val="single" w:sz="4" w:space="0" w:color="AAB4C0"/><w:insideH w:val="single" w:sz="4" w:space="0" w:color="AAB4C0"/><w:insideV w:val="single" w:sz="4" w:space="0" w:color="AAB4C0"/></w:tblBorders><w:tblCellMar><w:top w:w="40" w:type="dxa"/><w:left w:w="108" w:type="dxa"/><w:bottom w:w="40" w:type="dxa"/><w:right w:w="108" w:type="dxa"/></w:tblCellMar></w:tblPr></w:style>
</w:styles>"""
}
