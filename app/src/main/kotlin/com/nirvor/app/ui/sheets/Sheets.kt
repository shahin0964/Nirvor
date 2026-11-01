package com.nirvor.app.ui.sheets

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.nirvor.app.data.NoteEntity
import com.nirvor.app.ui.Pill
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.home.HabitIcon
import com.nirvor.app.ui.home.reminderGlyph
import com.nirvor.app.ui.home.sky.SkyCond
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import com.nirvor.app.util.Fmt
import java.time.LocalDate
import java.time.LocalTime

enum class SheetKind { Chooser, Task, Reminder, Expense, Income, MoneyCategories, LedgerEntry, Kisti, Habit, Note, SkyPreview }

// ------------------------------------------------------------ building blocks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Color.White,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 30.r, end = 30.r, bottom = 34.r),
            content = content,
        )
    }
}

@Composable
internal fun SheetTitle(text: String) {
    RText(text, 30, FontWeight.Bold, C.Ink, Modifier.padding(bottom = 22.r))
}

@Composable
internal fun FieldLabel(text: String) {
    RText(text, 19, FontWeight.Medium, C.StatLabel, Modifier.padding(top = 22.r, bottom = 12.r))
}

@Composable
internal fun NField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    minHeight: Number = 0,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().heightIn(min = minHeight.r),
        placeholder = { RText(placeholder, 21, FontWeight.Normal, C.Hint) },
        singleLine = singleLine,
        textStyle = TextStyle(fontFamily = Inter, fontSize = 22.rs, color = C.Ink),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.r),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF0BA765),
            unfocusedBorderColor = C.FieldLine,
            cursorColor = Color(0xFF0BA765),
        ),
    )
}

@Composable
internal fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    Spacer(Modifier.height(30.r))
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(78.r),
        shape = RoundedCornerShape(18.r),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BA765)),
    ) {
        RText("সেভ করুন", 22, FontWeight.SemiBold, Color.White)
    }
}

fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

fun pickTime(context: Context, initialMinute: Int?, onPicked: (Int) -> Unit) {
    val now = LocalTime.now()
    val h = if (initialMinute != null) initialMinute / 60 else now.hour
    val m = if (initialMinute != null) initialMinute % 60 else now.minute
    TimePickerDialog(context, { _, hour, minute -> onPicked(hour * 60 + minute) }, h, m, false).show()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayPicker(day: LocalDate, onChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.r),
        verticalArrangement = Arrangement.spacedBy(12.r),
    ) {
        Pill("আজ", day == today) { onChange(today) }
        Pill("আগামীকাল", day == tomorrow) { onChange(tomorrow) }
        val custom = day != today && day != tomorrow
        Pill(if (custom) Fmt.date(day) else "তারিখ বাছুন", custom) { pickDate(context, day, onChange) }
    }
}

// ------------------------------------------------------------ chooser

@Composable
fun ChooserSheet(onDismiss: () -> Unit, onPick: (SheetKind) -> Unit) {
    NSheet(onDismiss) {
        SheetTitle("কী যোগ করবেন?")
        val options = listOf(
            Triple(SheetKind.Task, "কাজ", Glyph.TaskBox),
            Triple(SheetKind.Reminder, "রিমাইন্ডার", Glyph.Bell),
            Triple(SheetKind.Expense, "খরচ", Glyph.Wallet),
            Triple(SheetKind.Income, "আয় / বেতন", Glyph.Coins),
            Triple(SheetKind.Habit, "হ্যাবিট", Glyph.Target),
            Triple(SheetKind.Note, "নোট", Glyph.Note),
        )
        val tints = listOf(
            listOf(C.TaskGreenA, C.TaskGreenB),
            listOf(C.PurpleA, C.PurpleB),
            listOf(C.OrangeIconA, C.OrangeIconB),
            listOf(C.FabA, C.FabB),
            listOf(C.HabGreenA, C.HabGreenB),
            listOf(C.NotesIconA, C.NotesIconB),
        )
        options.forEachIndexed { i, (kind, label, glyph) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.r))
                    .clickable { onPick(kind) }
                    .padding(vertical = 14.r, horizontal = 8.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(58.r).background(Brush.linearGradient(tints[i]), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val filled = glyph == Glyph.Bell || glyph == Glyph.Wallet
                    GlyphIcon(
                        glyph,
                        Color.White,
                        Modifier.size(34.r),
                        strokeWidth = 2.0f,
                        accent = if (filled) tints[i][1] else Color.Transparent,
                    )
                }
                Spacer(Modifier.size(22.r))
                RText(label, 23, FontWeight.Medium, C.Ink)
            }
        }
    }
}

// ------------------------------------------------------------ task

@Composable
fun AddTaskSheet(onDismiss: () -> Unit, onSave: (String, Long, Int?) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var minute by remember { mutableStateOf<Int?>(null) }
    NSheet(onDismiss) {
        SheetTitle("নতুন কাজ")
        NField(title, { title = it }, "কাজের নাম লিখুন")
        FieldLabel("দিন")
        DayPicker(day) { day = it }
        FieldLabel("সময় (ঐচ্ছিক)")
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            val m = minute
            Pill(if (m != null) Fmt.minute(m) else "সময় বাছুন", m != null) {
                pickTime(context, minute) { minute = it }
            }
            if (m != null) Pill("সময় বাদ দিন", false) { minute = null }
        }
        SaveButton(title.isNotBlank()) {
            onSave(title, day.toEpochDay(), minute)
            onDismiss()
        }
    }
}

// ------------------------------------------------------------ reminder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddReminderSheet(onDismiss: () -> Unit, onSave: (String, Long, Int, Boolean, String) -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var daily by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var minute by remember { mutableStateOf<Int?>(null) }
    var icon by remember { mutableStateOf("bell") }
    NSheet(onDismiss) {
        SheetTitle("নতুন রিমাইন্ডার")
        NField(title, { title = it }, "কী মনে করাবো?")
        FieldLabel("আইকন")
        Row(horizontalArrangement = Arrangement.spacedBy(14.r)) {
            listOf("bell", "pill", "calendar", "bill", "moon").forEach { key ->
                val selected = icon == key
                Box(
                    Modifier
                        .size(66.r)
                        .clip(CircleShape)
                        .background(if (selected) C.RemItemBg else Color(0xFFF3F5F8))
                        .border(1.6.r, if (selected) C.RemIcon else Color.Transparent, CircleShape)
                        .clickable { icon = key },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(reminderGlyph(key), C.RemIcon, Modifier.size(32.r), accent = Color.White.copy(alpha = 0.85f))
                }
            }
        }
        FieldLabel("কবে")
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            Pill("একবার", !daily) { daily = false }
            Pill("প্রতিদিন", daily) { daily = true }
        }
        if (!daily) {
            Spacer(Modifier.height(12.r))
            DayPicker(day) { day = it }
        }
        FieldLabel("সময়")
        val m = minute
        Pill(if (m != null) Fmt.minute(m) else "সময় বাছুন", m != null) {
            pickTime(context, minute) { minute = it }
        }
        SaveButton(title.isNotBlank() && m != null) {
            if (m != null) {
                onSave(title, day.toEpochDay(), m, daily, icon)
                onDismiss()
            }
        }
    }
}

// The expense / income sheet lives in ui/money/MoneySheets.kt.

// ------------------------------------------------------------ habit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddHabitSheet(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("drop") }
    NSheet(onDismiss) {
        SheetTitle("নতুন হ্যাবিট")
        NField(name, { name = it }, "হ্যাবিটের নাম")
        FieldLabel("আইকন")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.r),
            verticalArrangement = Arrangement.spacedBy(14.r),
        ) {
            listOf("drop", "book", "lotus", "phone", "moon", "target", "star").forEach { key ->
                val selected = icon == key
                Box(
                    Modifier
                        .size(66.r)
                        .clip(CircleShape)
                        .background(if (selected) Color(0xFFE3F6EC) else Color(0xFFF3F5F8))
                        .border(1.6.r, if (selected) Color(0xFF0BA765) else Color.Transparent, CircleShape)
                        .clickable { icon = key },
                    contentAlignment = Alignment.Center,
                ) {
                    HabitIcon(key, Modifier.size(32.r))
                }
            }
        }
        SaveButton(name.isNotBlank()) {
            onSave(name, icon)
            onDismiss()
        }
    }
}

// ------------------------------------------------------------ note

@Composable
fun NoteSheet(existing: NoteEntity?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(existing?.text ?: "") }
    NSheet(onDismiss) {
        SheetTitle(if (existing == null) "নতুন নোট" else "নোট এডিট")
        NField(text, { text = it }, "এখানে লিখুন…", singleLine = false, minHeight = 220)
        SaveButton(text.isNotBlank()) {
            onSave(text)
            onDismiss()
        }
    }
}

// ------------------------------------------------------------ header animation preview

/**
 * Opened by long-pressing the header. Lets you watch a whole day in 18 seconds and see each
 * weather look without waiting for it. Nothing here is saved; the header goes back to live
 * time and live weather on its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkyPreviewSheet(
    current: SkyCond?,
    onDismiss: () -> Unit,
    onTimelapse: () -> Unit,
    onWeather: (SkyCond?) -> Unit,
) {
    NSheet(onDismiss) {
        SheetTitle("হেডার অ্যানিমেশন প্রিভিউ")
        RText(
            "শুধু দেখার জন্য। কিছু সেভ হয় না, হেডার নিজে থেকেই আবার আসল সময় আর আবহাওয়ায় ফিরে যায়।",
            19,
            FontWeight.Normal,
            C.StatLabelBn,
            maxLines = 3,
        )
        Spacer(Modifier.height(22.r))
        Button(
            onClick = onTimelapse,
            modifier = Modifier.fillMaxWidth().height(78.r),
            shape = RoundedCornerShape(18.r),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BA765)),
        ) {
            RText("২৪ ঘণ্টার টাইম-ল্যাপস দেখুন (১৮ সেকেন্ড)", 21, FontWeight.SemiBold, Color.White)
        }
        FieldLabel("আবহাওয়া")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.r),
            verticalArrangement = Arrangement.spacedBy(12.r),
        ) {
            Pill("লাইভ (আসল)", current == null) { onWeather(null) }
            SkyCond.entries.forEach { c ->
                Pill(c.label, current == c) { onWeather(c) }
            }
        }
    }
}

// ------------------------------------------------------------ first launch

@Composable
fun NameDialog(initial: String, onDone: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(name) },
        containerColor = Color.White,
        title = { RText("আপনার নাম কী?", 28, FontWeight.Bold, C.Ink) },
        text = {
            Column {
                RText(
                    "হোম স্ক্রিনে শুভেচ্ছার সাথে আপনার নাম দেখাবে।",
                    20,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    Modifier.padding(bottom = 18.r),
                    maxLines = 3,
                )
                NField(name, { name = it }, "নাম লিখুন")
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(name) }) {
                RText("ঠিক আছে", 21, FontWeight.SemiBold, Color(0xFF0BA765))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDone("") }) {
                RText("পরে", 21, FontWeight.Medium, C.Hint)
            }
        },
    )
}
