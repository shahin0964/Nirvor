package com.nirvor.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.money.MoneyCategories
import com.nirvor.app.money.MoneyCategory
import com.nirvor.app.money.MoneyType
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.Pill
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.TrashButton
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.sheets.DayPicker
import com.nirvor.app.ui.sheets.FieldLabel
import com.nirvor.app.ui.sheets.NField
import com.nirvor.app.ui.sheets.NSheet
import com.nirvor.app.ui.sheets.SaveButton
import com.nirvor.app.ui.sheets.SheetTitle
import com.nirvor.app.ui.theme.C
import java.time.LocalDate

// ------------------------------------------------------------ add expense / income

/**
 * One sheet for both money directions. The switch on top picks খরচ or আয়; categories come with
 * their emoji, and "＋ নতুন ক্যাটাগরি" makes a new one (selected right after it is made).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddMoneySheet(vm: MainViewModel, initialType: MoneyType, onDismiss: () -> Unit) {
    val custom by vm.customCategories.collectAsStateWithLifecycle()
    var type by remember { mutableStateOf(initialType) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MoneyCategories.defaults(initialType).first().key) }
    var note by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var monthly by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val value = amount.replace(",", "").trim().toDoubleOrNull()
    val categories = MoneyCategories.list(type, custom)
    val income = type == MoneyType.Income

    NSheet(onDismiss) {
        SheetTitle(if (income) "নতুন আয়" else "নতুন খরচ")
        TypeSwitch(type) {
            if (it != type) {
                type = it
                category = MoneyCategories.defaults(it).first().key
            }
        }
        Spacer(Modifier.height(22.r))
        NField(amount, { amount = it }, "টাকার পরিমাণ (৳)", keyboardType = KeyboardType.Decimal)
        FieldLabel(if (income) "কোথা থেকে এলো" else "ক্যাটাগরি")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.r),
            verticalArrangement = Arrangement.spacedBy(10.r),
        ) {
            categories.forEach { cat ->
                CategoryChip(cat, category == cat.key) { category = cat.key }
            }
            NewCategoryChip { creating = true }
        }
        FieldLabel("বিবরণ (ঐচ্ছিক)")
        NField(note, { note = it }, if (income) "যেমন: অক্টোবর মাসের বেতন" else "যেমন: দুপুরের খাবার")
        FieldLabel(if (income) "কবে পেয়েছেন" else "দিন")
        DayPicker(day) { day = it }
        if (income) {
            FieldLabel("কতবার")
            Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
                Pill("একবার", !monthly) { monthly = false }
                Pill("🔁 প্রতি মাসে", monthly) { monthly = true }
            }
            if (monthly) {
                Spacer(Modifier.height(12.r))
                RText(
                    "প্রতি মাসের ${day.dayOfMonth} তারিখে এই টাকা নিজে থেকেই আয় হিসেবে যোগ হবে " +
                        "(যেমন বেতন)। Money পেজ থেকে যেকোনো সময় বন্ধ করা যাবে।",
                    18,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    maxLines = 4,
                    lineHeight = 27,
                )
            }
        }
        SaveButton(value != null && value > 0.0) {
            if (value != null && value > 0.0) {
                if (income) {
                    vm.addIncome(value, category, note, day.toEpochDay(), monthly)
                } else {
                    vm.addExpense(value, category, note, day.toEpochDay())
                }
                onDismiss()
            }
        }
    }

    if (creating) {
        CategoryDialog(
            type = type,
            existing = null,
            onDismiss = { creating = false },
        ) { name, emoji, color ->
            val forType = type
            vm.addCategory(forType, name, emoji, color) { key ->
                if (type == forType) category = key
            }
            creating = false
        }
    }
}

// ------------------------------------------------------------ make / edit a category

/** Name + emoji + colour for a category the user makes (or edits). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    type: MoneyType,
    existing: MoneyCategory?,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, color: Long) -> Unit,
) {
    val usedColors = MoneyCategories.palette
    var name by remember { mutableStateOf(existing?.label ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: if (type == MoneyType.Income) "💰" else "📌") }
    var color by remember { mutableStateOf(existing?.color ?: usedColors.first()) }
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            RText(
                if (existing == null) {
                    if (type == MoneyType.Income) "নতুন আয়ের ক্যাটাগরি" else "নতুন খরচের ক্যাটাগরি"
                } else {
                    "ক্যাটাগরি এডিট"
                },
                26,
                FontWeight.Bold,
                C.Ink,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // Live preview of the chip.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBubble(emoji, color, 70)
                    Spacer(Modifier.width(18.r))
                    RText(
                        name.ifBlank { "ক্যাটাগরির নাম" },
                        23,
                        FontWeight.SemiBold,
                        if (name.isBlank()) C.Hint else C.Ink,
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(20.r))
                NField(name, { if (it.length <= 30) name = it }, "নাম লিখুন (যেমন: রেস্টুরেন্ট)")
                FieldLabel("ইমোজি বাছুন")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.r),
                    verticalArrangement = Arrangement.spacedBy(8.r),
                ) {
                    MoneyCategories.emojis.forEach { e ->
                        val selected = e == emoji
                        Box(
                            Modifier
                                .size(56.r)
                                .clip(CircleShape)
                                .background(if (selected) Color(color).copy(alpha = 0.16f) else Color(0xFFF3F5F8))
                                .border(if (selected) 2.r else 0.r, if (selected) Color(color) else Color.Transparent, CircleShape)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) {
                            RText(e, 27, FontWeight.Normal, C.Ink, align = TextAlign.Center)
                        }
                    }
                }
                Spacer(Modifier.height(16.r))
                NField(
                    typed,
                    { v ->
                        typed = v
                        firstSymbol(v)?.let { emoji = it }
                    },
                    "অন্য ইমোজি? কীবোর্ড থেকে লিখুন",
                )
                FieldLabel("রং")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.r),
                    verticalArrangement = Arrangement.spacedBy(12.r),
                ) {
                    usedColors.forEach { c ->
                        val selected = c == color
                        Box(
                            Modifier
                                .size(50.r)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (selected) 3.r else 0.r, if (selected) Color.White else Color.Transparent, CircleShape)
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) GlyphIcon(Glyph.Check, Color.White, Modifier.size(28.r), strokeWidth = 2.6f)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, emoji, color) }, enabled = name.isNotBlank()) {
                RText("সেভ করুন", 21, FontWeight.SemiBold, if (name.isNotBlank()) Color(0xFF0BA765) else C.Hint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                RText("বাতিল", 21, FontWeight.Medium, C.Hint)
            }
        },
    )
}

/** The first symbol the user typed (an emoji can be several chars, e.g. 👨‍👩‍👧 or a flag). */
private fun firstSymbol(text: String): String? {
    val t = text.trim()
    if (t.isEmpty()) return null
    val bi = android.icu.text.BreakIterator.getCharacterInstance()
    bi.setText(t)
    val end = bi.next()
    return if (end > 0) t.substring(0, end) else t
}

// ------------------------------------------------------------ manage categories

/** Lists every category with its emoji; the user's own ones can be edited or deleted. */
@Composable
fun MoneyCategoriesSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val custom by vm.customCategories.collectAsStateWithLifecycle()
    var type by remember { mutableStateOf(MoneyType.Expense) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MoneyCategory?>(null) }
    var deleting by remember { mutableStateOf<MoneyCategory?>(null) }
    val mine = custom.filter { it.type == type }

    NSheet(onDismiss) {
        SheetTitle("ক্যাটাগরি")
        TypeSwitch(type) { type = it }
        Spacer(Modifier.height(20.r))
        Row(
            Modifier
                .fillMaxWidth()
                .height(74.r)
                .clip(RoundedCornerShape(18.r))
                .background(Color(0xFFE3F6EC))
                .border(1.4.r, Color(0xFF0BA765), RoundedCornerShape(18.r))
                .clickable { creating = true },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            GlyphIcon(Glyph.Plus, Color(0xFF0A8F56), Modifier.size(26.r), strokeWidth = 2.4f)
            Spacer(Modifier.width(12.r))
            RText("নিজের ক্যাটাগরি বানান", 21, FontWeight.SemiBold, Color(0xFF0A8F56))
        }

        FieldLabel("আপনার বানানো")
        if (mine.isEmpty()) {
            RText(
                "এখনো কিছু বানানো হয়নি। উপরের বাটনে চাপ দিয়ে নাম, ইমোজি আর রং দিয়ে নিজের ক্যাটাগরি বানান।",
                18,
                FontWeight.Normal,
                C.Hint,
                maxLines = 3,
                lineHeight = 27,
            )
        }
        mine.forEach { cat ->
            CategoryLine(cat) {
                GlyphIcon(
                    Glyph.Edit,
                    Color(0xFF5C6D81),
                    Modifier.size(52.r).clip(CircleShape).clickable { editing = cat }.padding(13.r),
                    strokeWidth = 1.8f,
                )
                TrashButton { deleting = cat }
            }
        }

        FieldLabel("ডিফল্ট ক্যাটাগরি")
        MoneyCategories.defaults(type).forEach { cat -> CategoryLine(cat) {} }
    }

    if (creating) {
        CategoryDialog(type, null, onDismiss = { creating = false }) { name, emoji, color ->
            vm.addCategory(type, name, emoji, color)
            creating = false
        }
    }
    editing?.let { cat ->
        CategoryDialog(cat.type, cat, onDismiss = { editing = null }) { name, emoji, color ->
            vm.updateCategory(cat, name, emoji, color)
            editing = null
        }
    }
    deleting?.let { cat ->
        val other = if (cat.type == MoneyType.Income) "অন্যান্য আয়" else "অন্যান্য"
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Color.White,
            title = { RText(cat.emoji + "  " + cat.label + " মুছবেন?", 25, FontWeight.Bold, C.Ink, maxLines = 2) },
            text = {
                RText(
                    "এই ক্যাটাগরির আগের সব হিসাব “$other”-তে চলে যাবে, কোনো টাকার হিসাব হারাবে না।",
                    20,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    maxLines = 4,
                    lineHeight = 29,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCategory(cat)
                    deleting = null
                }) {
                    RText("মুছে ফেলুন", 21, FontWeight.SemiBold, C.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    RText("বাতিল", 21, FontWeight.Medium, C.Hint)
                }
            },
        )
    }
}

@Composable
private fun CategoryLine(cat: MoneyCategory, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBubble(cat, 56)
        Spacer(Modifier.width(18.r))
        RText(cat.label, 21, FontWeight.Medium, C.Ink, Modifier.weight(1f))
        Box(Modifier.size(12.r).background(Color(cat.color), CircleShape))
        Spacer(Modifier.width(8.r))
        actions()
    }
}
