package com.nirvor.app.tools

import java.time.LocalDate

/** What could be read off a shop receipt. */
class ReceiptInfo(
    val total: Double?,
    /** The line the total came from (to highlight it), if any. */
    val totalLine: String?,
    val date: LocalDate?,
    val shop: String?,
)

/** Finds the total, date and shop name in OCR text of a receipt (Bangla or English). */
object ReceiptParser {
    private val digits = mapOf(
        '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
        '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9',
    )

    /** Bangla digits → 0-9. */
    fun normalize(s: String): String = buildString(s.length) { s.forEach { append(digits[it] ?: it) } }

    private val amount = Regex("""(\d{1,3}(?:,\d{2,3})+|\d+)(?:\.(\d{1,2}))?""")
    private val strong = listOf("grand total", "net total", "net payable", "total payable", "amount due", "payable", "সর্বমোট", "সর্ব মোট", "নিট", "নেট", "পরিশোধযোগ্য", "মোট প্রদেয়")
    private val weak = listOf("total", "amount", "মোট", "টাকা", "bill", "বিল")
    private val skip = listOf("sub total", "subtotal", "sub-total", "vat", "tax", "discount", "change", "cash", "ভ্যাট", "ছাড়", "ফেরত")
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private fun amounts(line: String): List<Double> = amount.findAll(line).mapNotNull { m ->
        val whole = m.groupValues[1].replace(",", "")
        val frac = m.groupValues[2]
        (if (frac.isEmpty()) whole else "$whole.$frac").toDoubleOrNull()
    }.filter { it > 0 && it < 10_000_000 }.toList()

    fun parse(raw: String): ReceiptInfo {
        val text = normalize(raw)
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        fun lower(s: String) = s.lowercase()

        var total: Double? = null
        var totalLine: String? = null
        // 1) a "grand total / সর্বমোট" line, 2) any "total / মোট" line (not sub total / VAT …)
        for (keys in listOf(strong, weak)) {
            for (i in lines.indices.reversed()) {
                val l = lower(lines[i])
                if (keys.none { l.contains(it) }) continue
                if (keys === weak && skip.any { l.contains(it) }) continue
                // amount on the same line, else on the next line
                val a = amounts(lines[i]).lastOrNull() ?: lines.getOrNull(i + 1)?.let { amounts(it).lastOrNull() }
                if (a != null) {
                    total = a
                    totalLine = lines[i]
                    break
                }
            }
            if (total != null) break
        }
        if (total == null) {
            // no keyword: the biggest amount with paisa, else the biggest number that is not a date / phone
            val all = lines.flatMap { l -> amount.findAll(l).map { it.value }.toList() }
            total = all.filter { it.contains('.') }.mapNotNull { it.replace(",", "").toDoubleOrNull() }.maxOrNull()
                ?: all.mapNotNull { it.replace(",", "").toDoubleOrNull() }.filter { it in 1.0..999_999.0 }.maxOrNull()
        }

        val date = findDate(text)
        val shop = lines.take(4).firstOrNull { l ->
            val letters = l.count { it.isLetter() }
            letters >= 3 && letters > l.count { it.isDigit() }
        }
        return ReceiptInfo(total, totalLine, date, shop?.take(40))
    }

    private fun findDate(text: String): LocalDate? {
        Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})""").findAll(text).forEach { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            var y = m.groupValues[3].toInt()
            if (y < 100) y += 2000
            try {
                return LocalDate.of(y, mo, d)
            } catch (e: Exception) {
                // try the next match
            }
        }
        Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").find(text)?.let { m ->
            try {
                return LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
            } catch (e: Exception) {
                // ignore
            }
        }
        Regex("""(\d{1,2})\s*([A-Za-z]{3})[a-z]*[,\s]+(\d{4})""").find(text)?.let { m ->
            val mo = months.indexOf(m.groupValues[2].lowercase()) + 1
            if (mo > 0) {
                try {
                    return LocalDate.of(m.groupValues[3].toInt(), mo, m.groupValues[1].toInt())
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        return null
    }
}
