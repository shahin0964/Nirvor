package com.nirvor.app.ui.islamic

import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.sheets.NField
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.ToolScaffold
import com.nirvor.app.widget.NirvorWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/** Small "−  value  +" control with optional clickable value to type directly. */
@Composable
internal fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onValueClick: (() -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepKey("−", onMinus)
        Box(
            modifier = Modifier
                .width(100.r)
                .clip(RoundedCornerShape(8.r))
                .then(if (onValueClick != null) Modifier.clickable(onClick = onValueClick) else Modifier)
                .padding(vertical = 4.r),
            contentAlignment = Alignment.Center,
        ) {
            RText(value, 17, FontWeight.SemiBold, C.Ink, align = TextAlign.Center)
        }
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

private fun toAsciiDigits(s: String): String = s.map { c ->
    when (c) {
        in '০'..'৯' -> ('0' + (c - '০'))
        in '0'..'9' -> c
        else -> c
    }
}.joinToString("")

private fun formatCustomTime12(timeStr: String): String {
    val (h, m) = IslamicPrefs.parseHourMinute(timeStr)
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "${bnNum(h12)}:${String.format(Locale.US, "%02d", m).let { bnDigits(it) }} $ampm"
}

/** How the times are worked out: method, Asr, minute corrections, custom manual times, Hijri date. */
@Composable
fun PrayerSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { IslamicPrefs(context) }
    var method by remember { mutableStateOf(prefs.method) }
    var hanafi by remember { mutableStateOf(prefs.hanafiAsr) }
    var offsets by remember { mutableStateOf(prefs.offsets) }
    var hijri by remember { mutableIntStateOf(prefs.hijriAdjust) }
    var customEnabled by remember { mutableStateOf(prefs.customTimesEnabled) }
    var customTimesList by remember { mutableStateOf(prefs.customTimes) }
    var editingPrayerIndex by remember { mutableStateOf<Int?>(null) }
    var editingOffsetIndex by remember { mutableStateOf<Int?>(null) }

    fun saved() {
        PrayerAlarms.reschedule(context)
        NirvorWidgetUpdater.updateAll(context)
    }

    val today = remember { LocalDate.now() }
    val times = remember(method, hanafi, offsets, customEnabled, customTimesList) { prefs.times(today) }

    ToolScaffold("নামাজের সময়ের নিয়ম", "হিসাবের পদ্ধতি ও কাস্টম সময়", onBack) {
        // Mode Selector: Auto calculation vs Custom (type yourself)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.r),
            horizontalArrangement = Arrangement.spacedBy(10.r),
        ) {
            ChoiceChip("☀️ স্বয়ংক্রিয় হিসাব", !customEnabled, IslamicA) {
                customEnabled = false
                prefs.customTimesEnabled = false
                saved()
            }
            ChoiceChip("✍️ কাস্টম সময় (নিজে লিখুন)", customEnabled, IslamicA) {
                customEnabled = true
                prefs.customTimesEnabled = true
                if (!prefs.hasCustomTimes()) {
                    prefs.copyCalculatedToCustom(today)
                    customTimesList = prefs.customTimes
                }
                saved()
            }
        }

        if (times != null) {
            // live result of the current settings
            ListCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RText(
                        if (customEnabled) "আজকের সময় (আপনার কাস্টম নিয়ম)" else "আজকের সময় (হিসাবের নিয়মে)",
                        16,
                        FontWeight.Medium,
                        C.StatLabel,
                        Modifier.weight(1f),
                    )
                    if (customEnabled) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Mint)
                                .padding(horizontal = 10.r, vertical = 3.r),
                        ) {
                            RText("✍️ কাস্টম মোড", 13, FontWeight.SemiBold, IslamicA)
                        }
                    }
                }
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

        if (customEnabled) {
            // Custom Prayer Times Section
            ListCard {
                Column {
                    RText("✍️ নামাজের সময় নিজে লিখুন", 19, FontWeight.SemiBold, C.Ink)
                    Spacer(Modifier.height(2.r))
                    RText("প্রতিটি ওয়াক্তের উপর ট্যাপ করে সরাসরি ইচ্ছামতো সময় লিখে দিন", 14, FontWeight.Normal, C.StatLabel)
                }
                Spacer(Modifier.height(10.r))
                RoundButton("🔄 স্বয়ংক্রিয় হিসাব থেকে সময় কপি করুন", IslamicA, Modifier.fillMaxWidth()) {
                    prefs.copyCalculatedToCustom(today)
                    customTimesList = prefs.customTimes
                    saved()
                    Toast.makeText(context, "বর্তমান হিসাবের সময়গুলো কপি করা হয়েছে", Toast.LENGTH_SHORT).show()
                }
                Spacer(Modifier.height(10.r))
                Prayer.entries.forEachIndexed { i, p ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.r).background(C.Divider))
                    val currentStr = customTimesList.getOrElse(i) { "05:00" }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.r))
                            .clickable { editingPrayerIndex = i }
                            .padding(vertical = 10.r, horizontal = 4.r),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            RText(p.bn, 18, FontWeight.SemiBold, C.Ink)
                            val sub = if (p == Prayer.Sunrise) "সূর্যোদয়ের সময়" else "${p.bnOf} ওয়াক্ত শুরু"
                            RText(sub, 13, FontWeight.Normal, C.StatLabel)
                        }
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(12.r))
                                .background(Mint)
                                .padding(horizontal = 14.r, vertical = 8.r),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RText(formatCustomTime12(currentStr), 16, FontWeight.Bold, IslamicB)
                            Spacer(Modifier.width(6.r))
                            RText("✏️", 13, FontWeight.Normal, IslamicB)
                        }
                    }
                }
            }
        } else {
            // Auto calculation options
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
        }

        // Minute adjustments
        ListCard {
            RText("মিনিট মিলিয়ে নিন", 19, FontWeight.SemiBold, C.Ink)
            RText("মসজিদ বা ক্যালেন্ডারের সাথে মেলাতে প্রতিটা সময় আগে-পরে করুন বা সংখ্যায় ট্যাপ করে সরাসরি মিনিট লিখুন", 15, FontWeight.Normal, C.StatLabel, maxLines = 2)
            Spacer(Modifier.height(8.r))
            Prayer.entries.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.r), verticalAlignment = Alignment.CenterVertically) {
                    RText(p.bn, 18, FontWeight.Medium, C.Ink, Modifier.weight(1f))
                    Stepper(
                        value = "${signed(offsets[i])} মিনিট",
                        onMinus = {
                            offsets = offsets.toMutableList().also { it[i] = (it[i] - 1).coerceAtLeast(-60) }
                            prefs.offsets = offsets
                            saved()
                        },
                        onPlus = {
                            offsets = offsets.toMutableList().also { it[i] = (it[i] + 1).coerceAtMost(60) }
                            prefs.offsets = offsets
                            saved()
                        },
                        onValueClick = {
                            editingOffsetIndex = i
                        },
                    )
                }
            }
        }

        // Hijri date adjustments
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
            if (customEnabled) {
                "কাস্টম মোড চালু থাকায় আপনার লিখে দেওয়া সময়গুলোই অ্যাপের মূল নামাজের তালিকা, হোমস্ক্রিন উইজেট, আজান নোটিফিকেশন এবং ওয়াক্তের কাউন্টডাউনে ব্যবহৃত হচ্ছে।"
            } else {
                "সময়গুলো ফোনেই সূর্যের অবস্থান থেকে হিসাব করা হয়: ফজর ও এশা সূর্য দিগন্তের নির্দিষ্ট ডিগ্রি নিচে থাকলে, " +
                    "সূর্যোদয় ও মাগরিব সূর্যের কিনারা দিগন্তে থাকলে, আসর বস্তুর ছায়া সমান (বা হানাফি মতে দ্বিগুণ) হলে।"
            },
            14,
            FontWeight.Normal,
            C.Hint,
            maxLines = 6,
            lineHeight = 20,
        )
    }

    // Dialog for writing custom prayer time
    editingPrayerIndex?.let { idx ->
        val p = Prayer.entries[idx]
        val initStr = customTimesList.getOrElse(idx) { "05:00" }
        CustomPrayerTimeDialog(
            prayer = p,
            initialTime24 = initStr,
            onDismiss = { editingPrayerIndex = null },
            onSave = { newTime24 ->
                prefs.setCustomTime(idx, newTime24)
                customTimesList = prefs.customTimes
                saved()
                editingPrayerIndex = null
                Toast.makeText(context, "${p.bn} এর সময় সংরক্ষণ করা হয়েছে", Toast.LENGTH_SHORT).show()
            },
        )
    }

    // Dialog for typing minute adjustment directly
    editingOffsetIndex?.let { idx ->
        val p = Prayer.entries[idx]
        val currentOffset = offsets.getOrElse(idx) { 0 }
        MinuteOffsetDialog(
            prayer = p,
            currentOffset = currentOffset,
            onDismiss = { editingOffsetIndex = null },
            onSave = { newOffset ->
                offsets = offsets.toMutableList().also { it[idx] = newOffset }
                prefs.offsets = offsets
                saved()
                editingOffsetIndex = null
            },
        )
    }
}

/** Dialog where users can write hour, minute, and choose AM/PM for a prayer time. */
@Composable
private fun CustomPrayerTimeDialog(
    prayer: Prayer,
    initialTime24: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val (initH, initM) = IslamicPrefs.parseHourMinute(initialTime24)
    val initIsPm = initH >= 12
    val initH12 = when {
        initH == 0 -> 12
        initH > 12 -> initH - 12
        else -> initH
    }
    var hourText by remember { mutableStateOf(initH12.toString()) }
    var minText by remember { mutableStateOf(String.format(Locale.US, "%02d", initM)) }
    var isPm by remember { mutableStateOf(initIsPm) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Column {
                RText("${prayer.bn} ওয়াক্তের সময়", 21, FontWeight.Bold, C.Ink)
                Spacer(Modifier.height(4.r))
                RText("ঘণ্টা ও মিনিট লিখে সময় নির্ধারণ করুন", 14, FontWeight.Normal, C.StatLabel)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                // Time inputs row: Hour : Minute  [AM / PM]
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    // Hour Box
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RText("ঘণ্টা (১-১২)", 12, FontWeight.Medium, C.StatLabel)
                        Spacer(Modifier.height(4.r))
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { v ->
                                val clean = toAsciiDigits(v).filter { it.isDigit() }.take(2)
                                hourText = clean
                                val num = clean.toIntOrNull()
                                if (num != null && num > 12) {
                                    isPm = true
                                }
                            },
                            modifier = Modifier.width(76.r),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = Inter,
                                fontSize = 24.rs,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = C.Ink,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.r),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = IslamicA,
                                unfocusedBorderColor = C.FieldLine,
                                cursorColor = IslamicA,
                            ),
                        )
                    }

                    RText(":", 28, FontWeight.Bold, C.Ink, Modifier.padding(horizontal = 8.r))

                    // Minute Box
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RText("মিনিট (০০-৫৯)", 12, FontWeight.Medium, C.StatLabel)
                        Spacer(Modifier.height(4.r))
                        OutlinedTextField(
                            value = minText,
                            onValueChange = { v ->
                                minText = toAsciiDigits(v).filter { it.isDigit() }.take(2)
                            },
                            modifier = Modifier.width(76.r),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = Inter,
                                fontSize = 24.rs,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = C.Ink,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.r),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = IslamicA,
                                unfocusedBorderColor = C.FieldLine,
                                cursorColor = IslamicA,
                            ),
                        )
                    }

                    Spacer(Modifier.width(12.r))

                    // AM / PM Column
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RText("বেলা", 12, FontWeight.Medium, C.StatLabel)
                        Spacer(Modifier.height(4.r))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.r)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.r))
                                    .background(if (!isPm) IslamicA else C.CalBg)
                                    .clickable { isPm = false }
                                    .padding(horizontal = 10.r, vertical = 12.r),
                                contentAlignment = Alignment.Center,
                            ) {
                                RText("AM", 14, FontWeight.Bold, if (!isPm) Color.White else C.Ink)
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.r))
                                    .background(if (isPm) IslamicA else C.CalBg)
                                    .clickable { isPm = true }
                                    .padding(horizontal = 10.r, vertical = 12.r),
                                contentAlignment = Alignment.Center,
                            ) {
                                RText("PM", 14, FontWeight.Bold, if (isPm) Color.White else C.Ink)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.r))

                // Preview Box
                val previewH = toAsciiDigits(hourText).toIntOrNull()?.coerceIn(0, 23) ?: initH12
                val previewM = toAsciiDigits(minText).toIntOrNull()?.coerceIn(0, 59) ?: initM
                val displayH12 = when {
                    previewH == 0 -> 12
                    previewH > 12 -> previewH - 12
                    else -> previewH
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.r))
                        .background(Mint)
                        .padding(12.r),
                    contentAlignment = Alignment.Center,
                ) {
                    RText(
                        "নির্বাচিত সময়: ${bnNum(displayH12)}:${String.format(Locale.US, "%02d", previewM).let { bnDigits(it) }} ${if (isPm) "PM" else "AM"}",
                        16,
                        FontWeight.SemiBold,
                        IslamicB,
                    )
                }

                Spacer(Modifier.height(10.r))

                // Quick minute nudges
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    listOf("-5", "-1", "+1", "+5").forEach { label ->
                        val delta = label.toInt()
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.r))
                                .background(C.CalBg)
                                .clickable {
                                    val curM = toAsciiDigits(minText).toIntOrNull() ?: 0
                                    val newM = (curM + delta + 60) % 60
                                    minText = String.format(Locale.US, "%02d", newM)
                                }
                                .padding(horizontal = 10.r, vertical = 6.r),
                        ) {
                            RText("${if (delta > 0) "+" else ""}${bnNum(delta)}মি", 13, FontWeight.Medium, C.Ink)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hRaw = toAsciiDigits(hourText).toIntOrNull() ?: 12
                val mRaw = toAsciiDigits(minText).toIntOrNull() ?: 0
                val validH = hRaw.coerceIn(0, 23)
                val validM = mRaw.coerceIn(0, 59)
                val finalH24 = when {
                    validH >= 13 -> validH
                    validH == 12 && !isPm -> 0
                    validH == 12 && isPm -> 12
                    isPm -> validH + 12
                    else -> validH
                }
                val timeStr = String.format(Locale.US, "%02d:%02d", finalH24, validM)
                onSave(timeStr)
            }) {
                RText("সংরক্ষণ করুন", 16, FontWeight.Bold, IslamicA)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                RText("বাতিল", 16, FontWeight.Normal, C.StatLabel)
            }
        },
    )
}

/** Dialog where users can directly type the minute offset for a prayer. */
@Composable
private fun MinuteOffsetDialog(
    prayer: Prayer,
    currentOffset: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(currentOffset.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Column {
                RText("${prayer.bn} ওয়াক্তের মিনিট ঠিক করুন", 20, FontWeight.Bold, C.Ink)
                Spacer(Modifier.height(4.r))
                RText("মসজিদ বা ক্যালেন্ডারের সাথে মেলাতে মিনিট লিখুন (যেমন: 10 বা -5)", 14, FontWeight.Normal, C.StatLabel)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v ->
                        val clean = toAsciiDigits(v).filter { it.isDigit() || it == '-' || it == '+' }.take(4)
                        text = clean
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { RText("যেমন: 10 বা -5", 16, FontWeight.Normal, C.Hint) },
                    textStyle = TextStyle(
                        fontFamily = Inter,
                        fontSize = 20.rs,
                        fontWeight = FontWeight.Bold,
                        color = C.Ink,
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.r),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IslamicA,
                        unfocusedBorderColor = C.FieldLine,
                        cursorColor = IslamicA,
                    ),
                )

                Spacer(Modifier.height(10.r))

                // Presets
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.r)) {
                    listOf(-10, -5, 0, 5, 10, 15).forEach { preset ->
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.r))
                                .background(if (text == preset.toString()) Mint else C.CalBg)
                                .clickable { text = preset.toString() }
                                .padding(vertical = 6.r),
                            contentAlignment = Alignment.Center,
                        ) {
                            RText("${if (preset > 0) "+" else ""}${bnNum(preset)}", 13, FontWeight.SemiBold, if (text == preset.toString()) IslamicA else C.Ink)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val num = toAsciiDigits(text).toIntOrNull() ?: 0
                onSave(num.coerceIn(-120, 120))
            }) {
                RText("ঠিক আছে", 16, FontWeight.Bold, IslamicA)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                RText("বাতিল", 16, FontWeight.Normal, C.StatLabel)
            }
        },
    )
}

private val QAZA_NAMES = listOf("ফজর", "যোহর", "আসর", "মাগরিব", "এশা", "বিতর")

/** Qaza: how many of each prayer are still owed; mark one made up, or add a block of missed days. */
@Composable
fun QazaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NirvorApplication
    val dao = app.db.islamicDao()
    val scope = rememberCoroutineScope()
    val rows by dao.qaza().collectAsStateWithLifecycle(emptyList())
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
            NField(days, { v -> days = v.filter { it.isDigit() }.take(5) }, "কত দিন? যেমন: 30", keyboardType = KeyboardType.Number)
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
