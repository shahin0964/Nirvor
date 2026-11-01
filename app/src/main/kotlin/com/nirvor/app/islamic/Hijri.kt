package com.nirvor.app.islamic

import android.icu.util.Calendar
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import java.time.LocalDate
import java.time.ZoneOffset

/** A Hijri date; [month] 1 = Muharram … 12 = Dhul Hijjah. */
data class HijriDate(val day: Int, val month: Int, val year: Int) {
    val monthBn: String get() = Hijri.MONTHS_BN[(month - 1).coerceIn(0, 11)]
    val text: String get() = "${bnNum(day)} $monthBn ${bnNum(year)} হিজরি"
}

/**
 * Hijri dates from the Umm al-Qura calendar (Android's own ICU calendar). Moon sighting in
 * Bangladesh is often a day behind, so the user can move it by [adjust] days.
 */
object Hijri {
    val MONTHS_BN = listOf(
        "মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জমাদিউল আউয়াল", "জমাদিউস সানি",
        "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ",
    )

    fun of(date: LocalDate, adjust: Int = 0): HijriDate {
        val cal = IslamicCalendar(TimeZone.getTimeZone("UTC"))
        cal.setCalculationType(IslamicCalendar.CalculationType.ISLAMIC_UMALQURA)
        // midday UTC of the civil day, so no time zone can push it to the next day
        cal.timeInMillis = date.plusDays(adjust.toLong()).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return HijriDate(
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.YEAR),
        )
    }
}

private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

/** 1448 → "১৪৪৮" */
fun bnNum(n: Int): String = n.toString().map { c -> if (c in '0'..'9') BN_DIGITS[c - '0'] else c }.joinToString("")

/** Any digits in [s] as Bangla digits. */
fun bnDigits(s: String): String = s.map { c -> if (c in '0'..'9') BN_DIGITS[c - '0'] else c }.joinToString("")
