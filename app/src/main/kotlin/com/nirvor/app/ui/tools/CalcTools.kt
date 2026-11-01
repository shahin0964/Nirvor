package com.nirvor.app.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.tools.Calc
import com.nirvor.app.tools.UnitCategory
import com.nirvor.app.tools.Units
import com.nirvor.app.tools.toolPrefs
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.sheets.NSheet
import com.nirvor.app.ui.sheets.SheetTitle
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import java.math.BigDecimal
import java.math.MathContext

// ================================================================ Calculator

private val SCI_KEYS = listOf(
    listOf("sin", "cos", "tan", "π"),
    listOf("asin", "acos", "atan", "e"),
    listOf("ln", "log", "√", "x²"),
    listOf("(", ")", "xʸ", "!"),
)
private val MAIN_KEYS = listOf(
    listOf("AC", "⌫", "%", "÷"),
    listOf("7", "8", "9", "×"),
    listOf("4", "5", "6", "−"),
    listOf("1", "2", "3", "+"),
    listOf("Ans", "0", ".", "="),
)
private val FUNCTION_KEYS = setOf("sin", "cos", "tan", "asin", "acos", "atan", "ln", "log", "√")

/** What a key adds to the expression. */
private fun keyText(key: String): String = when (key) {
    in FUNCTION_KEYS -> "$key("
    "x²" -> "²"
    "xʸ" -> "^"
    "−" -> "-"
    else -> key
}

/** A result written back into the expression (no grouping commas). */
private fun plain(v: Double): String =
    BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString()

@Composable
fun CalculatorTool(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { toolPrefs(context) }
    val haptic = LocalHapticFeedback.current
    var expr by rememberSaveable { mutableStateOf("") }
    var degrees by rememberSaveable { mutableStateOf(true) }
    var sci by rememberSaveable { mutableStateOf(true) }
    var ans by rememberSaveable { mutableDoubleStateOf(0.0) }
    var fresh by remember { mutableStateOf(false) } // the expression is a result just computed
    val history = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            prefs.getString("calc_history", "")?.split('\n')?.mapNotNull { line ->
                val parts = line.split('\u0001')
                if (parts.size == 2) parts[0] to parts[1] else null
            }?.let { addAll(it) }
        }
    }
    val preview = remember(expr, degrees, ans) {
        if (expr.isBlank()) null else try { Calc.eval(expr, degrees, ans) } catch (e: Exception) { null }
    }

    fun saveHistory() {
        prefs.edit().putString("calc_history", history.take(30).joinToString("\n") { it.first + "\u0001" + it.second }).apply()
    }

    fun press(key: String) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when (key) {
            "AC" -> {
                expr = ""
                fresh = false
            }
            "⌫" -> {
                val tokens = listOf("asin(", "acos(", "atan(", "sin(", "cos(", "tan(", "ln(", "log(", "√(", "Ans")
                val t = tokens.firstOrNull { expr.endsWith(it) }
                expr = if (t != null) expr.dropLast(t.length) else expr.dropLast(1)
                fresh = false
            }
            "=" -> {
                val v = try { Calc.eval(expr, degrees, ans) } catch (e: Exception) { null }
                if (v == null || v.isInfinite()) {
                    if (expr.isNotBlank()) Toast.makeText(context, "হিসাবটা ঠিক নেই", Toast.LENGTH_SHORT).show()
                } else {
                    history.add(0, expr to smart(v, 12))
                    while (history.size > 30) history.removeAt(history.size - 1)
                    saveHistory()
                    ans = v
                    expr = plain(v)
                    fresh = true
                }
            }
            else -> {
                val add = keyText(key)
                val startsNew = key.first().isDigit() || key == "." || key in FUNCTION_KEYS || key == "π" || key == "(" || key == "e"
                expr = if (fresh && startsNew) add else expr + add
                fresh = false
            }
        }
    }

    ToolScaffold("Calculator", "অ্যাডভান্সড ক্যালকুলেটর", onBack) {
        // display
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.r))
                .background(Brush.verticalGradient(listOf(Color(0xFF15124A), Color(0xFF0B0E22))))
                .padding(horizontal = 26.r, vertical = 22.r),
            horizontalAlignment = Alignment.End,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RText(
                    if (degrees) "DEG" else "RAD",
                    15,
                    FontWeight.SemiBold,
                    Color(0xFF34D399),
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x2234D399))
                        .clickable { degrees = !degrees }
                        .padding(horizontal = 14.r, vertical = 5.r),
                )
                Spacer(Modifier.width(10.r))
                RText(
                    if (sci) "সাধারণ" else "সায়েন্টিফিক",
                    15,
                    FontWeight.SemiBold,
                    Color(0xFF93C5FD),
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x2293C5FD))
                        .clickable { sci = !sci }
                        .padding(horizontal = 14.r, vertical = 5.r),
                )
                Spacer(Modifier.weight(1f))
                if (preview != null) {
                    RText(
                        "কপি",
                        15,
                        FontWeight.SemiBold,
                        Color(0xFFCBD5E1),
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                val cm = context.getSystemService(ClipboardManager::class.java)
                                cm?.setPrimaryClip(ClipData.newPlainText("result", plain(preview)))
                                Toast.makeText(context, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.r, vertical = 5.r),
                    )
                }
            }
            Spacer(Modifier.height(16.r))
            RText(
                expr.ifEmpty { "0" },
                if (expr.length > 16) 28 else 38,
                FontWeight.Medium,
                Color.White,
                maxLines = 3,
                align = TextAlign.End,
                lineHeight = if (expr.length > 16) 36 else 46,
            )
            Spacer(Modifier.height(8.r))
            RText(
                when {
                    fresh -> " "
                    preview != null -> "= " + smart(preview, 12)
                    else -> " "
                },
                30,
                FontWeight.SemiBold,
                Color(0xFF34D399),
                align = TextAlign.End,
            )
        }

        if (sci) {
            SCI_KEYS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                    row.forEach { key -> CalcKey(key, Modifier.weight(1f).aspectRatio(1.9f)) { press(key) } }
                }
            }
        }
        MAIN_KEYS.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                row.forEach { key -> CalcKey(key, Modifier.weight(1f).aspectRatio(1.3f)) { press(key) } }
            }
        }

        if (history.isNotEmpty()) {
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RText("ইতিহাস", 21, FontWeight.SemiBold, C.Ink, Modifier.weight(1f))
                    RText(
                        "মুছুন",
                        17,
                        FontWeight.Medium,
                        C.Danger,
                        Modifier.clip(RoundedCornerShape(50)).clickable {
                            history.clear()
                            saveHistory()
                        }.padding(horizontal = 12.r, vertical = 6.r),
                    )
                }
                history.take(12).forEach { (e, r) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.r))
                            .clickable {
                                expr = r.replace(",", "")
                                fresh = true
                            }
                            .padding(vertical = 8.r),
                        horizontalAlignment = Alignment.End,
                    ) {
                        RText(e, 16, FontWeight.Normal, C.Hint, align = TextAlign.End)
                        RText("= $r", 20, FontWeight.SemiBold, C.Ink, align = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcKey(key: String, modifier: Modifier, onClick: () -> Unit) {
    val isDigit = key.first().isDigit() || key == "." || key == "Ans"
    val isOp = key in setOf("÷", "×", "−", "+", "%")
    val isSci = key in setOf("sin", "cos", "tan", "π", "asin", "acos", "atan", "e", "ln", "log", "√", "x²", "(", ")", "xʸ", "!")
    val shape = RoundedCornerShape(20.r)
    val bg: Brush = when {
        key == "=" -> Brush.verticalGradient(listOf(Color(0xFF15B97A), Color(0xFF029C62)))
        key == "AC" -> Brush.verticalGradient(listOf(Color(0xFFFDECEC), Color(0xFFFDECEC)))
        isOp -> Brush.verticalGradient(listOf(Color(0xFFE3F6EC), Color(0xFFE3F6EC)))
        isSci -> Brush.verticalGradient(listOf(Color(0xFFEFF3F8), Color(0xFFEFF3F8)))
        else -> Brush.verticalGradient(listOf(Color.White, Color.White))
    }
    val fg = when {
        key == "=" -> Color.White
        key == "AC" -> C.Danger
        isOp -> Color(0xFF0A8F56)
        isSci -> C.StatLabel
        else -> C.Ink
    }
    Box(
        modifier
            .clip(shape)
            .background(bg)
            .border(1.r, if (isDigit) C.TodayBorder else Color.Transparent, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (key == "⌫") {
            GlyphIcon(Glyph.ArrowLeft, C.StatLabel, Modifier.size(30.r), strokeWidth = 2.2f)
        } else {
            RText(key, if (isSci) 20 else if (key == "Ans") 22 else 30, if (isSci) FontWeight.Medium else FontWeight.SemiBold, fg)
        }
    }
}

// ================================================================ Unit converter

@Composable
fun UnitConverterTool(onBack: () -> Unit) {
    var cat by rememberSaveable { mutableIntStateOf(0) }
    var from by rememberSaveable { mutableIntStateOf(0) }
    var to by rememberSaveable { mutableIntStateOf(1) }
    var input by rememberSaveable { mutableStateOf("1") }
    var picking by remember { mutableIntStateOf(0) } // 1 = from, 2 = to
    val category = Units.categories[cat]
    val f = from.coerceIn(0, category.units.size - 1)
    val t = to.coerceIn(0, category.units.size - 1)
    val value = parseNum(input)
    val result = value?.let { Units.convert(category, it, f, t) }

    ToolScaffold("Unit Converter", "একক রূপান্তর", onBack) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.r)) {
            Units.categories.forEachIndexed { i, c ->
                ChoiceChip("${c.emoji} ${c.title}", i == cat) {
                    if (i != cat) {
                        cat = i
                        from = 0
                        to = 1
                    }
                }
            }
        }
        ListCard {
            RText("এটা", 16, FontWeight.Medium, C.Hint)
            Spacer(Modifier.height(8.r))
            OutlinedTextField(
                value = input,
                onValueChange = { v -> input = v.filter { it.isDigit() || it == '.' || it == '-' || it == ',' } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(fontFamily = Inter, fontSize = 34.rs, fontWeight = FontWeight.Bold, color = C.Ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.r),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TGreen,
                    unfocusedBorderColor = C.FieldLine,
                    cursorColor = TGreen,
                ),
            )
            Spacer(Modifier.height(12.r))
            UnitButton(category.units[f].name, category.units[f].symbol) { picking = 1 }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(62.r)
                    .clip(CircleShape)
                    .background(TGreen)
                    .clickable {
                        val old = from
                        from = to
                        to = old
                        if (result != null) input = plain(result)
                    },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Swap, Color.White, Modifier.size(32.r).rotate(90f), strokeWidth = 2.4f)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.r))
                .background(Color(0xFFE3F6EC))
                .padding(horizontal = 26.r, vertical = 22.r),
        ) {
            RText("হবে", 16, FontWeight.Medium, Color(0xFF3F7E62))
            Spacer(Modifier.height(6.r))
            RText(result?.let { smart(it, 10) } ?: "—", 40, FontWeight.Bold, Color(0xFF0A8F56), maxLines = 2)
            Spacer(Modifier.height(12.r))
            UnitButton(category.units[t].name, category.units[t].symbol) { picking = 2 }
            if (!category.temperature) {
                Spacer(Modifier.height(10.r))
                RText(
                    "১ ${category.units[f].name} = ${smart(Units.convert(category, 1.0, f, t), 8)} ${category.units[t].name}",
                    16,
                    FontWeight.Normal,
                    Color(0xFF3F7E62),
                    maxLines = 2,
                )
            }
        }
        category.note?.let { RText("ℹ️ $it", 16, FontWeight.Normal, C.Hint, maxLines = 4, lineHeight = 23) }
        if (value != null) {
            ListCard {
                RText("সব এককে", 20, FontWeight.SemiBold, C.Ink)
                Spacer(Modifier.height(4.r))
                category.units.forEachIndexed { i, u ->
                    if (i != f) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.r))
                                .clickable { to = i }
                                .padding(vertical = 9.r),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RText(u.name, 18, FontWeight.Normal, C.StatLabel, Modifier.weight(1f))
                            RText(
                                smart(Units.convert(category, value, f, i), 9) + " " + u.symbol,
                                19,
                                FontWeight.SemiBold,
                                if (i == t) Color(0xFF0A8F56) else C.Ink,
                            )
                        }
                    }
                }
            }
        }
    }

    if (picking != 0) {
        UnitPicker(category, if (picking == 1) f else t, onDismiss = { picking = 0 }) { i ->
            if (picking == 1) from = i else to = i
            picking = 0
        }
    }
}

@Composable
private fun UnitButton(name: String, symbol: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .border(1.4.r, C.FieldLine, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.r, vertical = 11.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RText(name, 19, FontWeight.SemiBold, C.Ink)
        Spacer(Modifier.width(8.r))
        RText("($symbol)", 17, FontWeight.Normal, C.Hint)
        Spacer(Modifier.width(10.r))
        GlyphIcon(Glyph.ChevronDown, C.StatLabel, Modifier.size(22.r), strokeWidth = 2.2f)
    }
}

@Composable
private fun UnitPicker(category: UnitCategory, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    NSheet(onDismiss) {
        SheetTitle("${category.emoji} ${category.title}")
        category.units.forEachIndexed { i, u ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.r))
                    .background(if (i == selected) Color(0xFFE3F6EC) else Color.Transparent)
                    .clickable { onPick(i) }
                    .padding(horizontal = 16.r, vertical = 16.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RText(u.name, 21, if (i == selected) FontWeight.SemiBold else FontWeight.Normal, C.Ink, Modifier.weight(1f))
                RText(u.symbol, 18, FontWeight.Medium, C.Hint)
            }
        }
    }
}
