package com.nirvor.app.ui.islamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.islamic.DayTimes
import com.nirvor.app.islamic.IslamicPrefs
import com.nirvor.app.islamic.Prayer
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val IslamicA = Color(0xFF0E9F6E)
internal val IslamicB = Color(0xFF065F46)
internal val Gold = Color(0xFFD4A537)
internal val GoldSoft = Color(0xFFFDF3D6)
internal val Mint = Color(0xFFE6F6EF)

private val CLOCK = DateTimeFormatter.ofPattern("h:mm", Locale.ENGLISH)
private val AMPM = DateTimeFormatter.ofPattern("a", Locale.ENGLISH)

/** "4:37" */
internal fun clock(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(CLOCK)

/** "AM" / "PM" */
internal fun ampm(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(AMPM)

/** "২ ঘণ্টা ১৪ মিনিট" (with seconds when [seconds]). */
internal fun leftText(ms: Long, seconds: Boolean = false): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val parts = mutableListOf<String>()
    if (h > 0) parts.add("${bnNum(h.toInt())} ঘণ্টা")
    if (m > 0 || h > 0) parts.add("${bnNum(m.toInt())} মিনিট")
    if (seconds || (h == 0L && m == 0L)) parts.add("${bnNum(s.toInt())} সেকেন্ড")
    return parts.joinToString(" ")
}

/** "02:14:05" */
internal fun countdown(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
}

/** The current time, ticking every second while shown. */
@Composable
internal fun rememberNow(stepMs: Long = 1000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(stepMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(stepMs - now % stepMs)
        }
    }
    return now
}

/** The prayer that is running now and the next one (with its time), from today's and tomorrow's times. */
internal class PrayerNow(val current: Prayer?, val currentStart: Long, val next: Prayer, val nextAt: Long)

internal fun prayerNow(prefs: IslamicPrefs, now: Long): PrayerNow? {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val y = prefs.times(today.minusDays(1), zone) ?: return null
    val t = prefs.times(today, zone) ?: return null
    val tm = prefs.times(today.plusDays(1), zone) ?: return null
    // every prayer start in order, yesterday's Isha → tomorrow's Fajr
    val marks = listOf(Prayer.Isha to y[Prayer.Isha]) +
        Prayer.entries.map { it to t[it] } +
        listOf(Prayer.Fajr to tm[Prayer.Fajr])
    val nextIdx = marks.indexOfFirst { it.second > now }.let { if (it < 0) marks.size - 1 else it }
    val prev = marks.getOrNull(nextIdx - 1)
    // after sunrise and before Dhuhr there is no obligatory prayer running
    val current = prev?.first?.takeIf { it != Prayer.Sunrise }
    return PrayerNow(current, prev?.second ?: now, marks[nextIdx].first, marks[nextIdx].second)
}

/** Today's times (or null without a location). */
internal fun todayTimes(prefs: IslamicPrefs): DayTimes? = prefs.times(LocalDate.now())

/** Round icon button used on Islamic pages. */
@Composable
internal fun RoundIcon(label: String, onClick: () -> Unit, bg: Color = Color.White.copy(alpha = 0.7f)) {
    Box(
        Modifier
            .size(58.r)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { RText(label, 24, FontWeight.Normal, C.Ink) }
}

/** Small rounded label. */
@Composable
internal fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    RText(
        text,
        15,
        FontWeight.SemiBold,
        fg,
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 12.r, vertical = 5.r),
    )
}
