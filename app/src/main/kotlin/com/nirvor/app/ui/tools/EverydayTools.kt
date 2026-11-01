package com.nirvor.app.ui.tools

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.data.InstallmentEntity
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.sheets.DayPicker
import com.nirvor.app.ui.sheets.NField
import com.nirvor.app.ui.sheets.pickDate
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import com.nirvor.app.util.Fmt
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

// ================================================================ shared

/** Ring chart: [parts] = value + colour; text in the middle. */
@Composable
private fun Donut(parts: List<Pair<Double, Color>>, centerTop: String, centerBig: String, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val small = TextStyle(fontFamily = Inter, fontSize = 16.rs, color = Color(0xFF63748A), textAlign = TextAlign.Center)
    val big = TextStyle(fontFamily = Inter, fontSize = 30.rs, color = Color(0xFF0B0E22), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Canvas(modifier) {
        val total = parts.sumOf { it.first }.takeIf { it > 0 } ?: 1.0
        val sw = size.minDimension * 0.13f
        val d = size.minDimension - sw
        val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        drawArc(Color(0xFFEFF2F6), 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
        var start = -90f
        parts.forEach { (v, col) ->
            val sweep = (v / total * 360.0).toFloat()
            if (sweep > 0.3f) drawArc(col, start, sweep, false, tl, Size(d, d), style = Stroke(sw, cap = StrokeCap.Butt))
            start += sweep
        }
        val l1 = tm.measure(centerTop, small)
        val l2 = tm.measure(centerBig, big)
        drawText(l1, topLeft = Offset(center.x - l1.size.width / 2f, center.y - l2.size.height / 2f - l1.size.height))
        drawText(l2, topLeft = Offset(center.x - l2.size.width / 2f, center.y - l2.size.height / 2f + 4f))
    }
}

@Composable
private fun Legend(color: Color, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.r), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.r).background(color, RoundedCornerShape(5.r)))
        Spacer(Modifier.width(12.r))
        RText(label, 18, FontWeight.Normal, C.StatLabel, Modifier.weight(1f))
        RText(value, 19, FontWeight.SemiBold, C.Ink)
    }
}

/** Two-part horizontal bar. */
@Composable
private fun SplitBar(first: Double, second: Double, a: Color, b: Color) {
    val total = (first + second).takeIf { it > 0 } ?: 1.0
    Row(Modifier.fillMaxWidth().height(22.r).clip(RoundedCornerShape(50))) {
        val fa = (first / total).toFloat().coerceIn(0f, 1f)
        if (fa > 0f) Box(Modifier.weight(fa.coerceAtLeast(0.001f)).height(22.r).background(a))
        if (fa < 1f) Box(Modifier.weight((1f - fa).coerceAtLeast(0.001f)).height(22.r).background(b))
    }
}

// ================================================================ BMI

private fun bmiInfo(b: Double): Pair<String, Color> = when {
    b < 18.5 -> "কম ওজন" to Color(0xFF168FFB)
    b < 25 -> "স্বাভাবিক ওজন" to Color(0xFF0BA765)
    b < 30 -> "বেশি ওজন" to Color(0xFFE89B0C)
    else -> "স্থূলতা (Obesity)" to Color(0xFFE5484D)
}

@Composable
fun BmiTool(onBack: () -> Unit) {
    var metric by rememberSaveable { mutableStateOf(false) }
    var ft by rememberSaveable { mutableStateOf("5") }
    var inch by rememberSaveable { mutableStateOf("6") }
    var cm by rememberSaveable { mutableStateOf("") }
    var kg by rememberSaveable { mutableStateOf("") }

    val heightM: Double? = if (metric) {
        parseNum(cm)?.div(100.0)
    } else {
        val f = parseNum(ft) ?: 0.0
        val i = parseNum(inch) ?: 0.0
        if (f + i > 0) (f * 12.0 + i) * 0.0254 else null
    }
    val w = parseNum(kg)
    val bmi = if (heightM != null && heightM > 0.5 && heightM < 2.6 && w != null && w > 1) w / (heightM * heightM) else null

    ToolScaffold("BMI", "ওজন ঠিক আছে কিনা", onBack) {
        ListCard {
            RText("উচ্চতা", 19, FontWeight.SemiBold, C.Ink)
            Spacer(Modifier.height(10.r))
            Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                ChoiceChip("ফুট-ইঞ্চি", !metric) { metric = false }
                ChoiceChip("সেন্টিমিটার", metric) { metric = true }
            }
            Spacer(Modifier.height(14.r))
            if (metric) {
                ToolNumberField("সেন্টিমিটার", cm, { cm = it }, "সেমি")
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(14.r)) {
                    ToolNumberField("ফুট", ft, { ft = it }, "ফুট", Modifier.weight(1f))
                    ToolNumberField("ইঞ্চি", inch, { inch = it }, "ইঞ্চি", Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.r))
            ToolNumberField("ওজন", kg, { kg = it }, "কেজি")
        }
        if (bmi != null && heightM != null && w != null) {
            val (label, color) = bmiInfo(bmi)
            ListCard {
                RText("আপনার BMI", 17, FontWeight.Medium, C.Hint)
                Row(verticalAlignment = Alignment.Bottom) {
                    RText(fmt(bmi, 1), 56, FontWeight.Bold, color)
                    Spacer(Modifier.width(14.r))
                    RText(label, 22, FontWeight.SemiBold, color, Modifier.padding(bottom = 12.r))
                }
                Spacer(Modifier.height(10.r))
                BmiBar(bmi, Modifier.fillMaxWidth().height(86.r))
                Spacer(Modifier.height(14.r))
                val low = 18.5 * heightM * heightM
                val high = 24.9 * heightM * heightM
                StatLine("আপনার উচ্চতায় স্বাভাবিক ওজন", "${fmt(low, 0)}–${fmt(high, 0)} কেজি", bold = true)
                when {
                    w > high -> StatLine("স্বাভাবিক সীমায় আসতে কমাতে হবে", "প্রায় ${fmt(w - high, 1)} কেজি", Color(0xFFE89B0C))
                    w < low -> StatLine("স্বাভাবিক সীমায় আসতে বাড়াতে হবে", "প্রায় ${fmt(low - w, 1)} কেজি", Color(0xFF168FFB))
                    else -> StatLine("অবস্থা", "✅ সীমার মধ্যে", Color(0xFF0BA765))
                }
            }
        }
        RText(
            "BMI = ওজন (কেজি) ÷ উচ্চতা² (মিটার)। WHO-র হিসাবে 18.5–24.9 স্বাভাবিক। দক্ষিণ এশিয়ার মানুষের ক্ষেত্রে 23-এর বেশি হলেই স্বাস্থ্যঝুঁকি বাড়তে পারে। এটা শুধু একটা সাধারণ হিসাব, ডাক্তারি পরামর্শ নয়।",
            16,
            FontWeight.Normal,
            C.Hint,
            maxLines = 6,
            lineHeight = 23,
        )
    }
}

@Composable
private fun BmiBar(bmi: Double, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val style = TextStyle(fontFamily = Inter, fontSize = 14.rs, color = Color(0xFF63748A))
    Canvas(modifier) {
        val min = 12.0
        val max = 40.0
        fun x(v: Double) = ((v - min) / (max - min)).toFloat().coerceIn(0f, 1f) * size.width
        val barTop = size.height * 0.32f
        val barH = size.height * 0.22f
        val bands = listOf(18.5 to Color(0xFF60A5FA), 25.0 to Color(0xFF34D399), 30.0 to Color(0xFFFBBF24), max to Color(0xFFF87171))
        var start = min
        bands.forEach { (end, col) ->
            drawRect(col, Offset(x(start), barTop), Size(x(end) - x(start), barH))
            start = end
        }
        listOf(18.5, 25.0, 30.0).forEach { v ->
            val layout = tm.measure(fmt(v, 1), style)
            drawText(layout, topLeft = Offset(x(v) - layout.size.width / 2f, barTop + barH + 6f))
        }
        val px = x(bmi)
        val tri = Path().apply {
            moveTo(px, barTop - 2f)
            lineTo(px - 14f, barTop - 26f)
            lineTo(px + 14f, barTop - 26f)
            close()
        }
        drawPath(tri, Color(0xFF0B0E22))
        drawLine(Color(0xFF0B0E22), Offset(px, barTop), Offset(px, barTop + barH), 4f)
    }
}

// ================================================================ Age

private fun weekday(d: DayOfWeek): String = when (d) {
    DayOfWeek.SATURDAY -> "শনিবার"
    DayOfWeek.SUNDAY -> "রবিবার"
    DayOfWeek.MONDAY -> "সোমবার"
    DayOfWeek.TUESDAY -> "মঙ্গলবার"
    DayOfWeek.WEDNESDAY -> "বুধবার"
    DayOfWeek.THURSDAY -> "বৃহস্পতিবার"
    DayOfWeek.FRIDAY -> "শুক্রবার"
}

@Composable
fun AgeTool(onBack: () -> Unit) {
    val context = LocalContext.current
    var birth by rememberSaveable { mutableStateOf<Long?>(null) }
    var until by rememberSaveable { mutableStateOf<Long?>(null) }
    val today = LocalDate.now()
    val end = until?.let { LocalDate.ofEpochDay(it) } ?: today

    ToolScaffold("Age", "বয়স হিসাব", onBack) {
        ListCard {
            RText("জন্ম তারিখ", 17, FontWeight.Medium, C.Hint)
            Spacer(Modifier.height(8.r))
            val b = birth
            DateButton(if (b != null) "🎂  " + Fmt.date(b) else "🎂  তারিখ বাছুন") {
                pickDate(context, b?.let { LocalDate.ofEpochDay(it) } ?: today.minusYears(25)) { birth = it.toEpochDay() }
            }
            Spacer(Modifier.height(16.r))
            RText("কোন দিন পর্যন্ত", 17, FontWeight.Medium, C.Hint)
            Spacer(Modifier.height(8.r))
            Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                ChoiceChip("আজ", until == null) { until = null }
                ChoiceChip(if (until != null) Fmt.date(end) else "অন্য তারিখ", until != null) {
                    pickDate(context, end) { until = it.toEpochDay() }
                }
            }
        }
        val b = birth?.let { LocalDate.ofEpochDay(it) }
        if (b != null && b.isAfter(end)) {
            RText("জন্ম তারিখ শেষ তারিখের পরে হতে পারে না।", 18, FontWeight.Medium, C.Danger)
        }
        if (b != null && !b.isAfter(end)) {
            val p = Period.between(b, end)
            val days = ChronoUnit.DAYS.between(b, end)
            ListCard {
                Row(Modifier.fillMaxWidth()) {
                    BigBox("${p.years}", "বছর", Modifier.weight(1f))
                    Spacer(Modifier.width(10.r))
                    BigBox("${p.months}", "মাস", Modifier.weight(1f))
                    Spacer(Modifier.width(10.r))
                    BigBox("${p.days}", "দিন", Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.r))
                StatLine("মোট মাস", fmt(p.toTotalMonths().toDouble(), 0))
                StatLine("মোট সপ্তাহ", "${fmt((days / 7).toDouble(), 0)} সপ্তাহ ${days % 7} দিন")
                StatLine("মোট দিন", fmt(days.toDouble(), 0))
                StatLine("মোট ঘণ্টা (প্রায়)", fmt(days * 24.0, 0))
                StatLine("জন্মের দিন ছিল", weekday(b.dayOfWeek))
            }
            var next = b.withYear(end.year)
            if (next.isBefore(end)) next = b.withYear(end.year + 1)
            val left = ChronoUnit.DAYS.between(end, next)
            val last = b.withYear(next.year - 1)
            val span = ChronoUnit.DAYS.between(last, next).coerceAtLeast(1)
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(
                        listOf((span - left).toDouble() to Color(0xFFE0428E), left.toDouble() to Color(0xFFEFF2F6)),
                        "বাকি",
                        if (left == 0L) "আজ" else "$left দিন",
                        Modifier.size(190.r),
                    )
                    Spacer(Modifier.width(20.r))
                    Column(Modifier.weight(1f)) {
                        RText(if (left == 0L) "🎉 আজ জন্মদিন!" else "পরের জন্মদিন", 20, FontWeight.SemiBold, C.Ink)
                        Spacer(Modifier.height(6.r))
                        RText(Fmt.date(next), 19, FontWeight.Medium, C.StatLabel)
                        RText(weekday(next.dayOfWeek), 17, FontWeight.Normal, C.Hint)
                        Spacer(Modifier.height(6.r))
                        RText("${p.years + if (left == 0L) 0 else 1} বছরে পা দেবেন", 17, FontWeight.Normal, C.Hint)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigBox(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.background(Color(0xFFF7F2FC), RoundedCornerShape(18.r)).padding(vertical = 16.r),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RText(value, 40, FontWeight.Bold, Color(0xFF6E56CF))
        RText(label, 17, FontWeight.Medium, C.StatLabel)
    }
}

@Composable
private fun DateButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.r))
            .background(Color(0xFFF1F4F7))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.r, vertical = 18.r),
    ) {
        RText(text, 22, FontWeight.SemiBold, C.Ink)
    }
}

// ================================================================ Percentage

private val PERCENT_MODES = listOf("X-এর Y% কত", "X, Y-এর কত %", "কত % বাড়লো/কমলো", "% যোগ / বাদ")

@Composable
fun PercentTool(onBack: () -> Unit) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    val x = parseNum(a)
    val y = parseNum(b)
    val labels = when (mode) {
        0 -> "সংখ্যা (X)" to "শতকরা (Y%)"
        1 -> "অংশ (X)" to "মোট (Y)"
        2 -> "আগে ছিল" to "এখন"
        else -> "সংখ্যা" to "শতকরা %"
    }

    ToolScaffold("Percentage", "শতকরা হিসাব", onBack) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.r)) {
            PERCENT_MODES.forEachIndexed { i, m -> ChoiceChip(m, mode == i) { mode = i } }
        }
        ListCard {
            ToolNumberField(labels.first, a, { a = it })
            Spacer(Modifier.height(14.r))
            ToolNumberField(labels.second, b, { b = it }, if (mode == 0 || mode == 3) "%" else "")
        }
        if (x != null && y != null) {
            ListCard {
                when (mode) {
                    0 -> {
                        val r = x * y / 100.0
                        ResultBig(fmt(r, 4), "${fmt(x, 4)}-এর ${fmt(y, 4)}% = ${fmt(r, 4)}")
                        Spacer(Modifier.height(14.r))
                        Donut(
                            listOf(y.coerceIn(0.0, 100.0) to TGreen, (100.0 - y.coerceIn(0.0, 100.0)) to Color(0xFFEFF2F6)),
                            "শতকরা",
                            "${fmt(y, 2)}%",
                            Modifier.fillMaxWidth().height(220.r),
                        )
                    }
                    1 -> if (y == 0.0) {
                        RText("মোট শূন্য হলে শতকরা বের করা যায় না।", 18, FontWeight.Medium, C.Danger)
                    } else {
                        val r = x / y * 100.0
                        ResultBig("${fmt(r, 2)}%", "${fmt(x, 4)} হলো ${fmt(y, 4)}-এর ${fmt(r, 2)}%")
                        Spacer(Modifier.height(14.r))
                        Donut(
                            listOf(r.coerceIn(0.0, 100.0) to TBlue, (100.0 - r.coerceIn(0.0, 100.0)) to Color(0xFFEFF2F6)),
                            "অংশ",
                            "${fmt(r, 1)}%",
                            Modifier.fillMaxWidth().height(220.r),
                        )
                    }
                    2 -> if (x == 0.0) {
                        RText("আগের মান শূন্য হলে শতকরা বের করা যায় না।", 18, FontWeight.Medium, C.Danger)
                    } else {
                        val r = (y - x) / abs(x) * 100.0
                        val up = r >= 0
                        ResultBig(
                            (if (up) "▲ " else "▼ ") + "${fmt(abs(r), 2)}%",
                            if (up) "${fmt(y - x, 4)} বেড়েছে" else "${fmt(x - y, 4)} কমেছে",
                            if (up) TGreen else TRed,
                        )
                        Spacer(Modifier.height(14.r))
                        val maxV = maxOf(abs(x), abs(y)).takeIf { it > 0 } ?: 1.0
                        BarLine("আগে", x, maxV, Color(0xFF94A3B8))
                        BarLine("এখন", y, maxV, if (up) TGreen else TRed)
                    }
                    else -> {
                        val plus = x * (1 + y / 100.0)
                        val minus = x * (1 - y / 100.0)
                        StatLine("${fmt(y, 2)}% যোগ করলে", fmt(plus, 4), TGreenDark, bold = true)
                        StatLine("${fmt(y, 2)}% বাদ দিলে", fmt(minus, 4), TRed, bold = true)
                        StatLine("${fmt(y, 2)}% পরিমাণ", fmt(x * y / 100.0, 4))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultBig(big: String, line: String, color: Color = C.Ink) {
    RText(big, 46, FontWeight.Bold, color)
    Spacer(Modifier.height(4.r))
    RText(line, 18, FontWeight.Normal, C.StatLabel, maxLines = 2)
}

@Composable
private fun BarLine(label: String, v: Double, max: Double, color: Color) {
    Column(Modifier.padding(vertical = 6.r)) {
        Row {
            RText(label, 17, FontWeight.Normal, C.StatLabel, Modifier.weight(1f))
            RText(fmt(v, 2), 17, FontWeight.SemiBold, C.Ink)
        }
        Spacer(Modifier.height(6.r))
        val f = (abs(v) / max).toFloat().coerceIn(0.01f, 1f)
        Row(Modifier.fillMaxWidth().height(16.r).clip(RoundedCornerShape(50)).background(Color(0xFFEFF2F6))) {
            Box(Modifier.weight(f).height(16.r).background(color, RoundedCornerShape(50)))
            if (f < 1f) Spacer(Modifier.weight(1f - f))
        }
    }
}

// ================================================================ Discount

@Composable
fun DiscountTool(onBack: () -> Unit) {
    var price by rememberSaveable { mutableStateOf("") }
    var off by rememberSaveable { mutableStateOf("") }
    var percent by rememberSaveable { mutableStateOf(true) }
    var extra by rememberSaveable { mutableStateOf("") }
    var vat by rememberSaveable { mutableStateOf("") }
    val p = parseNum(price)
    val d = parseNum(off) ?: 0.0
    val e = parseNum(extra) ?: 0.0
    val t = parseNum(vat) ?: 0.0

    ToolScaffold("Discount", "ছাড়ের পর দাম", onBack) {
        ListCard {
            ToolNumberField("আসল দাম", price, { price = it }, "৳")
            Spacer(Modifier.height(14.r))
            Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                ChoiceChip("% ছাড়", percent) { percent = true }
                ChoiceChip("৳ ছাড়", !percent) { percent = false }
            }
            Spacer(Modifier.height(10.r))
            ToolNumberField("ছাড়", off, { off = it }, if (percent) "%" else "৳")
            if (percent) {
                Spacer(Modifier.height(10.r))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.r)) {
                    listOf(5, 10, 15, 20, 25, 30, 40, 50, 70).forEach { v ->
                        ChoiceChip("$v%", off == v.toString()) { off = v.toString() }
                    }
                }
            }
            Spacer(Modifier.height(14.r))
            Row(horizontalArrangement = Arrangement.spacedBy(14.r)) {
                ToolNumberField("বাড়তি ছাড় (ঐচ্ছিক)", extra, { extra = it }, "%", Modifier.weight(1f))
                ToolNumberField("ভ্যাট / ট্যাক্স (ঐচ্ছিক)", vat, { vat = it }, "%", Modifier.weight(1f))
            }
        }
        if (p != null && p > 0) {
            val after1 = (if (percent) p * (1 - d / 100.0) else p - d).coerceAtLeast(0.0)
            val after2 = after1 * (1 - e / 100.0)
            val final = after2 * (1 + t / 100.0)
            val saved = (p - after2).coerceAtLeast(0.0)
            ListCard {
                RText("দিতে হবে", 17, FontWeight.Medium, C.Hint)
                RText("৳ " + fmt(final, 2), 50, FontWeight.Bold, TGreenDark)
                Spacer(Modifier.height(6.r))
                RText("মোট ছাড় ৳ ${fmt(saved, 2)} (${fmt(saved / p * 100.0, 1)}%)", 19, FontWeight.SemiBold, TAmber)
                Spacer(Modifier.height(16.r))
                SplitBar(after2, saved, TGreen, Color(0xFFFBBF24))
                Spacer(Modifier.height(8.r))
                Legend(TGreen, "দাম (ছাড়ের পর)", "৳ " + fmt(after2, 2))
                Legend(Color(0xFFFBBF24), "বাঁচলো", "৳ " + fmt(saved, 2))
                if (t > 0) Legend(Color(0xFF94A3B8), "ভ্যাট / ট্যাক্স", "৳ " + fmt(final - after2, 2))
            }
        }
    }
}

// ================================================================ EMI

@Composable
fun EmiTool(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var amount by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    var tenure by rememberSaveable { mutableStateOf("") }
    var years by rememberSaveable { mutableStateOf(true) }
    var monthly by rememberSaveable { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    val p = parseNum(amount)
    val yr = parseNum(rate)
    val tn = parseNum(tenure)
    val n = if (tn != null) (if (years) tn * 12 else tn).roundToInt() else 0
    val r = (yr ?: 0.0) / 12.0 / 100.0
    val emi = if (p != null && p > 0 && n in 1..600 && yr != null && yr >= 0) {
        if (r == 0.0) p / n else p * r * (1 + r).pow(n) / ((1 + r).pow(n) - 1)
    } else {
        null
    }

    ToolScaffold("EMI", "লোনের কিস্তি হিসাব", onBack) {
        ListCard {
            ToolNumberField("লোনের পরিমাণ", amount, { amount = it }, "৳")
            Spacer(Modifier.height(14.r))
            ToolNumberField("বছরে সুদের হার", rate, { rate = it }, "%")
            Spacer(Modifier.height(14.r))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.r)) {
                ToolNumberField("সময়", tenure, { tenure = it }, if (years) "বছর" else "মাস", Modifier.weight(1f), KeyboardType.Number)
                ChoiceChip("বছর", years) { years = true }
                ChoiceChip("মাস", !years) { years = false }
            }
        }
        if (emi != null && p != null) {
            val total = emi * n
            val interest = total - p
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(
                        listOf(p to TGreen, interest.coerceAtLeast(0.0) to Color(0xFFFBBF24)),
                        "মাসিক কিস্তি",
                        "৳ " + fmt(emi, 0),
                        Modifier.size(230.r),
                    )
                    Spacer(Modifier.width(18.r))
                    Column(Modifier.weight(1f)) {
                        Legend(TGreen, "আসল", "৳ " + fmt(p, 0))
                        Legend(Color(0xFFFBBF24), "মোট সুদ", "৳ " + fmt(interest, 0))
                    }
                }
                Spacer(Modifier.height(12.r))
                StatLine("প্রতি মাসে কিস্তি", "৳ " + fmt(emi, 2), TGreenDark, bold = true)
                StatLine("কিস্তির সংখ্যা", "$n টা")
                StatLine("সব মিলিয়ে দিতে হবে", "৳ " + fmt(total, 2), bold = true)
            }
            RoundButton("💳 কিস্তি হিসেবে যোগ করুন", TGreen, Modifier.fillMaxWidth()) { adding = true }
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RText("পরিশোধের তালিকা", 20, FontWeight.SemiBold, C.Ink, Modifier.weight(1f))
                    ChoiceChip("বছর", !monthly) { monthly = false }
                    Spacer(Modifier.width(8.r))
                    ChoiceChip("মাস", monthly) { monthly = true }
                }
                Spacer(Modifier.height(10.r))
                Row(Modifier.fillMaxWidth()) {
                    listOf(if (monthly) "মাস" else "বছর", "আসল", "সুদ", "বাকি").forEach {
                        RText(it, 15, FontWeight.SemiBold, C.Hint, Modifier.weight(1f), align = TextAlign.End)
                    }
                }
                var balance = p
                var rowP = 0.0
                var rowI = 0.0
                for (m in 1..n) {
                    val i = balance * r
                    val pr = (emi - i).coerceAtMost(balance)
                    balance -= pr
                    rowP += pr
                    rowI += i
                    if (monthly || m % 12 == 0 || m == n) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.r)) {
                            val label = if (monthly) "$m" else "${(m + 11) / 12}"
                            listOf(label, fmt(rowP, 0), fmt(rowI, 0), fmt(balance.coerceAtLeast(0.0), 0)).forEachIndexed { k, s ->
                                RText(s, 16, if (k == 0) FontWeight.SemiBold else FontWeight.Normal, C.Ink, Modifier.weight(1f), align = TextAlign.End)
                            }
                        }
                        rowP = 0.0
                        rowI = 0.0
                    }
                }
            }
        }
        RText(
            "ব্যাংকভেদে প্রসেসিং ফি বা অন্য খরচ থাকতে পারে; এখানে শুধু আসল + সুদ (রিডিউসিং ব্যালান্স) হিসাব করা হয়েছে।",
            16,
            FontWeight.Normal,
            C.Hint,
            maxLines = 3,
            lineHeight = 23,
        )
    }

    if (adding && emi != null) {
        var title by remember { mutableStateOf("লোন") }
        var first by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = Color.White,
            title = { RText("কিস্তি হিসেবে যোগ", 25, FontWeight.Bold, C.Ink) },
            text = {
                Column {
                    RText("৳ ${fmt(emi, 2)} × $n মাস · More → Lend & Borrow → কিস্তি-তে যোগ হবে", 18, FontWeight.Normal, C.StatLabelBn, maxLines = 3)
                    Spacer(Modifier.height(14.r))
                    NField(title, { title = it }, "নাম (যেমন: হোম লোন)")
                    Spacer(Modifier.height(14.r))
                    RText("প্রথম কিস্তি", 17, FontWeight.Medium, C.StatLabel)
                    Spacer(Modifier.height(8.r))
                    DayPicker(first) { first = it }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveInstallment(
                        null,
                        InstallmentEntity(
                            title = title.ifBlank { "লোন" },
                            emoji = "🏦",
                            amount = Math.round(emi * 100.0) / 100.0,
                            count = n,
                            firstEpochDay = first.toEpochDay(),
                        ),
                        0,
                    )
                    Toast.makeText(context, "কিস্তি যোগ হয়েছে", Toast.LENGTH_SHORT).show()
                    adding = false
                }) { RText("যোগ করুন", 20, FontWeight.SemiBold, TGreen) }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) { RText("বাতিল", 20, FontWeight.Medium, C.Hint) }
            },
        )
    }
}
