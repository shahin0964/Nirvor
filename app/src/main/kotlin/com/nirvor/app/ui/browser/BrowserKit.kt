package com.nirvor.app.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.cardSurface
import com.nirvor.app.ui.download.Dl
import com.nirvor.app.ui.download.SheetHandle
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter

/** Full page inside the browser (History, Bookmarks, Settings, …), in the app's page style. */
@Composable
fun SubPage(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 40.dp
    Box(
        Modifier
            .background(C.Sheet)
            // Pages cover the browser: taps must not reach the web page underneath.
            .clickable(enabled = true, onClick = {}, indication = null, interactionSource = remember {
                androidx.compose.foundation.interaction.MutableInteractionSource()
            })
    ) {
        PageScaffold(title, subtitle, onBack = onBack, bottomPadding = bottom, actions = actions, content = content)
    }
}

/** White card holding several setting rows. */
@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.r)
    Column(
        Modifier
            .fillMaxWidth()
            .cardSurface(shape, SolidColor(Color.White), C.TodayBorder, 1.r, 5.r)
            .clip(shape),
        content = content,
    )
}

@Composable
fun GroupDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 24.r).height(1.r).background(C.Divider))
}

@Composable
fun GroupLabel(text: String) {
    RText(text, 20, FontWeight.SemiBold, C.StatLabel, Modifier.padding(start = 6.r, top = 12.r, bottom = 2.r))
}

@Composable
private fun RowBase(onClick: (() -> Unit)?, content: @Composable RowScope.() -> Unit) {
    var m = Modifier.fillMaxWidth().heightIn(min = 86.r)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 24.r, vertical = 16.r), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun RowTexts(title: String, subtitle: String?, modifier: Modifier, titleColor: Color = C.Ink, enabled: Boolean = true) {
    Column(modifier) {
        RText(title, 20, FontWeight.Medium, if (enabled) titleColor else C.Hint, maxLines = 3, lineHeight = 27)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(3.r))
            RText(subtitle, 16, FontWeight.Normal, if (enabled) C.StatLabelBn else C.Hint, maxLines = 4, lineHeight = 23)
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    RowBase(if (enabled) ({ onChange(!checked) }) else null) {
        RowTexts(title, subtitle, Modifier.weight(1f), enabled = enabled)
        Spacer(Modifier.width(14.r))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onChange else null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Dl.Green,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD5DEE7),
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun ActionRow(
    title: String,
    subtitle: String? = null,
    glyph: Glyph? = null,
    danger: Boolean = false,
    chevron: Boolean = true,
    onClick: () -> Unit,
) {
    RowBase(onClick) {
        if (glyph != null) {
            GlyphIcon(glyph, if (danger) C.Danger else C.NavIcon, Modifier.size(30.r), strokeWidth = 1.9f)
            Spacer(Modifier.width(18.r))
        }
        RowTexts(title, subtitle, Modifier.weight(1f), if (danger) C.Danger else C.Ink)
        if (chevron) GlyphIcon(Glyph.ChevronRight, C.RemChevron, Modifier.size(24.r), strokeWidth = 2f)
    }
}

/** A setting with several choices; opens a list to pick from. */
@Composable
fun ChoiceRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == selected }?.second ?: selected
    RowBase({ open = true }) {
        RowTexts(title, label, Modifier.weight(1f))
        GlyphIcon(Glyph.ChevronDown, C.RemChevron, Modifier.size(24.r), strokeWidth = 2f)
    }
    if (open) {
        ChoiceDialog(title, options, selected, onDismiss = { open = false }) {
            open = false
            onSelect(it)
        }
    }
}

@Composable
fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { RText(title, 24, FontWeight.Bold, C.Ink, maxLines = 2) },
        text = {
            Column(Modifier.heightIn(max = 520.r).verticalScroll(rememberScrollState())) {
                options.forEach { (key, label) ->
                    val on = key == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.r))
                            .clickable { onPick(key) }
                            .padding(vertical = 14.r, horizontal = 6.r),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(28.r).border(2.4.r, if (on) Dl.Green else C.HabOpen, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (on) Box(Modifier.size(14.r).background(Dl.Green, CircleShape))
                        }
                        Spacer(Modifier.width(16.r))
                        RText(label, 19, FontWeight.Normal, C.Ink, maxLines = 2)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { RText("বন্ধ", 19, FontWeight.Medium, C.Hint) } },
    )
}

/** A setting typed by the user (home page, custom user agent, …). */
@Composable
fun TextRow(title: String, value: String, placeholder: String, emptyLabel: String, keyboard: KeyboardType = KeyboardType.Text, onSave: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    RowBase({ open = true }) {
        RowTexts(title, value.ifBlank { emptyLabel }, Modifier.weight(1f))
        GlyphIcon(Glyph.Edit, C.RemChevron, Modifier.size(24.r), strokeWidth = 1.9f)
    }
    if (open) {
        InputDialog(title, value, placeholder, keyboard, onDismiss = { open = false }) {
            open = false
            onSave(it)
        }
    }
}

@Composable
fun InputDialog(
    title: String,
    initial: String,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    confirm: String = "সেভ",
    lines: Int = 1,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { RText(title, 24, FontWeight.Bold, C.Ink, maxLines = 2) },
        text = { InputBox(text, { text = it }, placeholder, keyboard, lines) },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { RText(confirm, 19, FontWeight.Bold, Dl.GreenText) } },
        dismissButton = { TextButton(onClick = onDismiss) { RText("বাতিল", 19, FontWeight.Medium, C.Hint) } },
    )
}

@Composable
fun InputBox(value: String, onChange: (String) -> Unit, placeholder: String, keyboard: KeyboardType = KeyboardType.Text, lines: Int = 1) {
    val shape = RoundedCornerShape(16.r)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.2.r, C.FieldLine, shape)
            .padding(horizontal = 18.r, vertical = 16.r)
    ) {
        if (value.isEmpty()) RText(placeholder, 19, FontWeight.Normal, C.Hint, maxLines = lines)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(fontFamily = Inter, fontSize = 19.rs, color = C.Ink),
            cursorBrush = SolidColor(Dl.Green),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            singleLine = lines == 1,
            maxLines = lines,
            minLines = if (lines > 1) 3 else 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SliderRow(title: String, value: Int, range: IntRange, step: Int, suffix: String, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.r, vertical = 16.r)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText(title, 20, FontWeight.Medium, C.Ink, Modifier.weight(1f))
            RText("$value$suffix", 18, FontWeight.SemiBold, Dl.GreenText)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).toInt() * step) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = ((range.last - range.first) / step - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(thumbColor = Dl.Green, activeTrackColor = Dl.Green, inactiveTrackColor = Dl.Track),
        )
    }
}

/** White bottom sheet used by the browser's menus. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.r), content = content)
    }
}

/** Simple yes / no dialog. */
@Composable
fun AskDialog(title: String, text: String, yes: String, no: String, onYes: () -> Unit, onNo: () -> Unit, glyph: Glyph? = null, danger: Boolean = false) {
    AlertDialog(
        onDismissRequest = onNo,
        containerColor = Color.White,
        icon = if (glyph != null) ({
            Box(
                Modifier.size(64.r).background(Brush.linearGradient(if (danger) Dl.red else Dl.brand), CircleShape),
                contentAlignment = Alignment.Center,
            ) { GlyphIcon(glyph, Color.White, Modifier.size(34.r), strokeWidth = 2f) }
        }) else null,
        title = { RText(title, 23, FontWeight.Bold, C.Ink, maxLines = 3) },
        text = { RText(text, 18, FontWeight.Normal, C.StatLabelBn, maxLines = 8, lineHeight = 26) },
        confirmButton = { TextButton(onClick = onYes) { RText(yes, 19, FontWeight.Bold, if (danger) C.Danger else Dl.GreenText) } },
        dismissButton = { TextButton(onClick = onNo) { RText(no, 19, FontWeight.Medium, C.Hint) } },
    )
}
