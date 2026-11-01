package com.nirvor.app.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.PageHeader
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

internal val TGreen = Color(0xFF0BA765)
internal val TGreenDark = Color(0xFF0A8F56)
internal val TRed = Color(0xFFE5484D)
internal val TAmber = Color(0xFFE89B0C)
internal val TBlue = Color(0xFF168FFB)
internal val TSoft = Color(0xFFF1F4F7)

/** Page for one tool: the usual header, then the tool's own content. */
@Composable
fun ToolScaffold(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(C.Sheet)) {
        PageHeader(title, subtitle, onBack, actions)
        val base = Modifier.fillMaxWidth().weight(1f)
        Column(
            (if (scroll) base.verticalScroll(rememberScrollState()) else base)
                .navigationBarsPadding()
                .padding(start = 22.r, end = 22.r, top = 4.r, bottom = 30.r),
            verticalArrangement = Arrangement.spacedBy(16.r),
            content = content,
        )
    }
}

/** Number field with a label on top (decimal keyboard). */
@Composable
fun ToolNumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String = "",
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Decimal,
) {
    Column(modifier) {
        RText(label, 17, FontWeight.Medium, C.StatLabel)
        Spacer(Modifier.height(8.r))
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(fontFamily = Inter, fontSize = 24.rs, fontWeight = FontWeight.SemiBold, color = C.Ink),
            suffix = if (suffix.isNotEmpty()) ({ RText(suffix, 19, FontWeight.Medium, C.Hint) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            shape = RoundedCornerShape(16.r),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TGreen,
                unfocusedBorderColor = C.FieldLine,
                cursorColor = TGreen,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
        )
    }
}

/** Selectable chip row item (green when chosen). */
@Composable
fun ChoiceChip(label: String, selected: Boolean, color: Color = TGreen, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.12f) else TSoft)
            .border(if (selected) 1.6.r else 1.2.r, if (selected) color else Color(0xFFE1E7ED), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.r, vertical = 11.r),
    ) {
        RText(label, 18, if (selected) FontWeight.SemiBold else FontWeight.Medium, if (selected) C.Ink else C.StatLabel)
    }
}

/** Label + value line inside a card. */
@Composable
fun StatLine(label: String, value: String, valueColor: Color = C.Ink, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.r), verticalAlignment = Alignment.CenterVertically) {
        RText(label, 18, FontWeight.Normal, C.StatLabel, Modifier.weight(1f), maxLines = 2)
        Spacer(Modifier.width(12.r))
        RText(value, if (bold) 21 else 19, if (bold) FontWeight.Bold else FontWeight.SemiBold, valueColor)
    }
}

/** Three small numbers side by side (min / avg / max etc.). */
@Composable
fun TriStat(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, (label, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                RText(label, 16, FontWeight.Normal, C.Hint, align = TextAlign.Center)
                Spacer(Modifier.height(4.r))
                RText(value, 22, FontWeight.Bold, C.Ink, align = TextAlign.Center)
            }
            if (i < items.size - 1) Box(Modifier.width(1.r).height(52.r).background(C.Divider))
        }
    }
}

/** Big round action button. */
@Composable
fun RoundButton(label: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(70.r)
            .clip(shape)
            .background(if (filled) color else color.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        RText(label, 20, FontWeight.SemiBold, if (filled) Color.White else color)
    }
}

/** Text field helper: "1,250.5" -> 1250.5 */
fun parseNum(s: String): Double? = s.replace(",", "").trim().toDoubleOrNull()

/** Up to [decimals] decimals, grouped, trailing zeros removed. */
fun fmt(v: Double, decimals: Int = 2): String {
    if (v.isNaN() || v.isInfinite()) return "—"
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.maximumFractionDigits = decimals
    nf.minimumFractionDigits = 0
    return nf.format(v)
}

/** Shortest readable form for any size of number (used by the converter and calculator). */
fun smart(v: Double, digits: Int = 10): String {
    if (v.isNaN() || v.isInfinite()) return "—"
    if (v == 0.0) return "0"
    val a = abs(v)
    if (a >= 1e15 || a < 1e-6) {
        return String.format(Locale.US, "%.${(digits - 4).coerceAtLeast(2)}e", v).replace("e+0", "e").replace("e+", "e")
    }
    val bd = BigDecimal(v).round(java.math.MathContext(digits, RoundingMode.HALF_UP)).stripTrailingZeros()
    val plain = bd.toPlainString()
    val parts = plain.split('.')
    val intPart = parts[0].toBigInteger()
    val nf = NumberFormat.getIntegerInstance(Locale.US)
    val grouped = (if (plain.startsWith("-") && intPart.signum() == 0) "-" else "") + nf.format(intPart)
    return if (parts.size > 1) grouped + "." + parts[1] else grouped
}
