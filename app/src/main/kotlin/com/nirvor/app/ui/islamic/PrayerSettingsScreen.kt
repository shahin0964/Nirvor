package com.nirvor.app.ui.islamic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.NirvorApplication
import com.nirvor.app.data.QazaEntity
import com.nirvor.app.islamic.CalcMethod
import com.nirvor.app.islamic.Hijri
import com.nirvor.app.islamic.IslamicPrefs
import com.nirvor.app.islamic.Prayer
import com.nirvor.app.islamic.PrayerAlarms
import com.nirvor.app.islamic.bnDigits
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.sheets.NField
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Small "−  value  +" control. */
@Composable
internal fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepKey("−", onMinus)
        RText(value, 18, FontWeight.SemiBold, C.Ink, Modifier.width(92.r), align = androidx.compose.ui.text.style.TextAlign.Center)
        StepKey("+", onPlus)
    }
}

@Composable
private fun StepKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.r)
            .clip(CircleShape)
            .background(Mint)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { RText(label, 24, FontWeight.Bold, IslamicB) }
}

/** 18.0 → "১৮", 19.5 → "১৯.৫" */
private fun deg(d: Double) = bnDigits(if (d % 1.0 == 0.0) d.toInt().toString() else d.toString())

private fun signed(n: Int) = when {
    n > 0 -> "+${bnNum(n)}"
    n < 0 -> "−${bnNum(-n)}"
    else -> bnNum(0)
}

/** How the times are worked out: method, Asr, minute corrections, Hijri date. */
@Composable
fun PrayerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { IslamicPrefs(context) }
    var method by remember { mutableStateOf(prefs.method) }
    var hanafi by remember { mutableStateOf(prefs.hanafiAsr) }
    var offsets by remember { mutableStateOf(prefs.offsets) }
    var hijri by remember { mutableIntStateOf(prefs.hijriAdjust) }
    fun saved() = PrayerAlarms.reschedule(context)
    val today = remember { LocalDate.now() }
    val times = remember(method, hanafi, offsets) { prefs.times(today) }

    ToolScaffold("নামাজের সময়ের নিয়ম", "হিসাবের পদ্ধতি ও মিনিট ঠিক করা", onBack) {
        if (times != null) {
            // live result of the current settings
            ListCard {
                RText("আজকের সময় (এই নিয়মে)", 16, FontWeight.Medium, C.StatLabel)
                Spacer(Modifier.height(8.r))
                Row(Modifier.fillMaxWidth()) {
                    Prayer.five.forEach { p ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            RText(p.bn, 14, FontWeight.Medium, C.StatLabel)
                            RText(clock(times[p]), 18, FontWeight.Bold, C.Ink)
                        }
                    }
                }
            }
        }
        RText("হিসাবের পদ্ধতি", 20, FontWeight.SemiBold, C.Ink)
        CalcMethod.entries.forEach { m ->
            val on = method == m
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.r))
                    .background(if (on) Mint else Color.White)
                    .border(if (on) 2.r else 1.r, if (on) IslamicA else C.TodayBorder, RoundedCornerShape(18.r))
                    .clickable {
                        method = m
                        prefs.method = m
                        saved()
                    }
                    .padding(horizontal = 18.r, vertical = 14.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    RText(m.bn, 17, FontWeight.SemiBold, C.Ink, maxLines = 2)
                    val isha = if (m.ishaMinutes > 0) "মাগরিবের ${bnNum(m.ishaMinutes)} মিনিট পর" else "${deg(m.isha)}°"
                    RText("ফজর ${deg(m.fajr)}° · এশা $isha", 14, FontWeight.Normal, C.StatLabel)
                }
                if (on) RText("✓", 22, FontWeight.Bold, IslamicA)
            }
        }
        RText("আসরের সময়", 20, FontWeight.SemiBold, C.Ink)
        Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
            ChoiceChip("হানাফি (ছায়া দ্বিগুণ)", hanafi, IslamicA) {
                hanafi = true
                prefs.hanafiAsr = true
                saved()
            }
            ChoiceChip("শাফেয়ি ও অন্যান্য (ছায়া সমান)", !hanafi, IslamicA) {
                hanafi = false
                prefs.hanafiAsr = false
                saved()
            }
        }
        ListCard {
            RText("মিনিট মিলিয়ে নিন", 19, FontWeight.SemiBold, C.Ink)
            RText("আপনার মসজিদ বা ক্যালেন্ডারের সাথে মেলাতে প্রতিটা সময় আগে-পরে করুন", 15, FontWeight.Normal, C.StatLabel, maxLines = 2)
            Spacer(Modifier.height(8.r))
            Prayer.entries.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.r), verticalAlignment = Alignment.CenterVertically) {
                    RText(p.bn, 18, FontWeight.Medium, C.Ink, Modifier.weight(1f))
                    Stepper("${signed(offsets[i])} মিনিট", onMinus = {
                        offsets = offsets.toMutableList().also { it[i] = (it[i] - 1).coerceAtLeast(-30) }
                        prefs.offsets = offsets
                        saved()
                    }, onPlus = {
                        offsets = offsets.toMutableList().also { it[i] = (it[i] + 1).coerceAtMost(30) }
                        prefs.offsets = offsets
                        saved()
                    })
                }
            }
        }
        ListCard {
            RText("হিজরি তারিখ", 19, FontWeight.SemiBold, C.Ink)
            RText("বাংলাদেশে চাঁদ দেখা অনেক সময় এক দিন পরে হয় — দরকার হলে দিন সরান", 15, FontWeight.Normal, C.StatLabel, maxLines = 2)
            Spacer(Modifier.height(8.r))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText(Hijri.of(today, hijri).text, 18, FontWeight.SemiBold, IslamicB, Modifier.weight(1f), maxLines = 2)
                Stepper("${signed(hijri)} দিন", onMinus = {
                    hijri = (hijri - 1).coerceAtLeast(-2)
                    prefs.hijriAdjust = hijri
                    saved()
                }, onPlus = {
                    hijri = (hijri + 1).coerceAtMost(2)
                    prefs.hijriAdjust = hijri
                    saved()
                })
            }
        }
        RText(
            "সময়গুলো ফোনেই সূর্যের অবস্থান থেকে হিসাব করা হয়: ফজর ও এশা সূর্য দিগন্তের নির্দিষ্ট ডিগ্রি নিচে থাকলে, " +
                "সূর্যোদয় ও মাগরিব সূর্যের কিনারা দিগন্তে থাকলে, আসর বস্তুর ছায়া সমান (বা হানাফি মতে দ্বিগুণ) হলে।",
            14,
            FontWeight.Normal,
            C.Hint,
            maxLines = 6,
            lineHeight = 20,
        )
    }
}

private val QAZA_NAMES = listOf("ফজর", "যোহর", "আসর", "মাগরিব", "এশা", "বিতর")

/** Qaza: how many of each prayer are still owed; mark one made up, or add a block of missed days. */
@Composable
fun QazaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NirvorApplication
    val dao = app.db.islamicDao()
    val scope = rememberCoroutineScope()
    val rows by dao.qaza().collectAsState(emptyList())
    var days by remember { mutableStateOf("") }
    fun row(i: Int) = rows.firstOrNull { it.prayer == i } ?: QazaEntity(i)
    fun put(q: QazaEntity) {
        scope.launch(Dispatchers.IO) { dao.putQaza(q) }
    }
    val pending = rows.sumOf { it.pending }
    val done = rows.sumOf { it.done }

    ToolScaffold("কাজা নামাজ", "বাকি নামাজ আদায়ের হিসাব", onBack) {
        ListCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    RText("বাকি", 16, FontWeight.Medium, C.StatLabel)
                    RText(bnNum(pending), 40, FontWeight.Bold, if (pending == 0) IslamicA else C.Danger)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    RText("আদায় করেছেন", 16, FontWeight.Medium, C.StatLabel)
                    RText(bnNum(done), 40, FontWeight.Bold, IslamicA)
                }
            }
            if (pending + done > 0) {
                Spacer(Modifier.height(10.r))
                Box(Modifier.fillMaxWidth().height(12.r).clip(RoundedCornerShape(50)).background(C.CalBg)) {
                    Box(
                        Modifier
                            .fillMaxWidth(done.toFloat() / (pending + done))
                            .height(12.r)
                            .clip(RoundedCornerShape(50))
                            .background(IslamicA),
                    )
                }
            }
        }
        ListCard {
            QAZA_NAMES.forEachIndexed { i, name ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.r).background(C.Divider))
                val q = row(i)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.r), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        RText(name, 19, FontWeight.SemiBold, C.Ink)
                        RText("বাকি ${bnNum(q.pending)} · আদায় ${bnNum(q.done)}", 15, FontWeight.Normal, C.StatLabel)
                    }
                    RText(
                        "✓ আদায়",
                        15,
                        FontWeight.SemiBold,
                        if (q.pending > 0) Color.White else C.Hint,
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (q.pending > 0) IslamicA else C.CalBg)
                            .clickable(enabled = q.pending > 0) { put(QazaEntity(i, q.pending - 1, q.done + 1)) }
                            .padding(horizontal = 14.r, vertical = 9.r),
                    )
                    Spacer(Modifier.width(8.r))
                    StepKey("−") { if (q.pending > 0) put(QazaEntity(i, q.pending - 1, q.done)) }
                    Spacer(Modifier.width(6.r))
                    StepKey("+") { put(QazaEntity(i, q.pending + 1, q.done)) }
                }
            }
            RText("বিতর হানাফি মতে ওয়াজিব, তাই আলাদা হিসাব রাখা হয়।", 14, FontWeight.Normal, C.Hint, maxLines = 2)
        }
        ListCard {
            RText("অনেক দিনের কাজা একসাথে যোগ করুন", 19, FontWeight.SemiBold, C.Ink)
            RText("যত দিনের নামাজ বাকি, সেই সংখ্যা দিন — প্রতিটা ওয়াক্তে (বিতরসহ) ততগুলো যোগ হবে", 15, FontWeight.Normal, C.StatLabel, maxLines = 3, lineHeight = 21)
            Spacer(Modifier.height(10.r))
            NField(days, { v -> days = v.filter { it.isDigit() }.take(5) }, "কত দিন? যেমন: 30", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            Spacer(Modifier.height(10.r))
            RoundButton("যোগ করুন", IslamicA, Modifier.fillMaxWidth()) {
                val n = days.toIntOrNull() ?: 0
                if (n > 0) {
                    val current = (0..5).map { row(it) }
                    scope.launch(Dispatchers.IO) { current.forEach { dao.putQaza(QazaEntity(it.prayer, it.pending + n, it.done)) } }
                    days = ""
                }
            }
        }
        RText("নামাজের পাতায় কোনো ওয়াক্তে \"কাজা\" লিখলে এখানে নিজে যোগ হয়।", 14, FontWeight.Normal, C.Hint, maxLines = 2)
    }
}
