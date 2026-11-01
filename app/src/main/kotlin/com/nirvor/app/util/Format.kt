package com.nirvor.app.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)

    /** "09:00 AM" */
    fun minute(minuteOfDay: Int): String = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(TIME)

    fun time(t: LocalDateTime): String = t.format(TIME)

    fun timeAt(epochMillis: Long): String =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneId.systemDefault()).format(TIME)

    /** "24 Apr 2025" */
    fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DATE)

    fun date(d: LocalDate): String = d.format(DATE)

    fun dayMonth(d: LocalDate): String = d.format(DAY_MONTH)

    /** 420 -> "420", 1250.5 -> "1,250.5" */
    fun money(v: Double): String {
        val nf = NumberFormat.getNumberInstance(Locale.US)
        nf.maximumFractionDigits = 2
        nf.minimumFractionDigits = 0
        return nf.format(v)
    }

    fun greeting(hour: Int): String = when (hour) {
        in 5..11 -> "Good Morning,"
        in 12..16 -> "Good Afternoon,"
        in 17..20 -> "Good Evening,"
        else -> "Good Night,"
    }
}

/**
 * The three columns of the home screen's Today's Expense card (kept exactly as in the design).
 * Every category other than food and transport — the new built-in ones and the user's own —
 * is added into "অন্যান্য", so the three columns always add up to the day's total.
 * The full category list lives in money/MoneyCategories.kt.
 */
enum class ExpenseCategory(val key: String, val label: String, val color: Long) {
    Food("food", "খাবার", 0xFF0AA35C),
    Transport("transport", "যাতায়াত", 0xFF168FFB),
    Other("other", "অন্যান্য", 0xFF8A43DB);

    companion object {
        fun of(key: String): ExpenseCategory = entries.firstOrNull { it.key == key } ?: Other
    }
}
