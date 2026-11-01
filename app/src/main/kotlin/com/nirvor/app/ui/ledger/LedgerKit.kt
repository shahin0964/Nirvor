package com.nirvor.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.Pill
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.cardSurface
import com.nirvor.app.ui.r
import com.nirvor.app.ui.sheets.pickDate
import com.nirvor.app.ui.theme.C
import com.nirvor.app.util.Fmt
import java.time.LocalDate

internal val LGreen = Color(0xFF0A955A)
internal val LRed = Color(0xFFE5484D)
internal val LAmber = Color(0xFFD97F06)
internal val Brand = Color(0xFF0BA765)

/** Hero gradients: someone owes me / I owe / settled. */
internal val HeroGreen = listOf(Color(0xFF16C47B), Color(0xFF03A35B))
internal val HeroRed = listOf(Color(0xFFFF8A5B), Color(0xFFE5484D))
internal val HeroSlate = listOf(Color(0xFF7D8DA3), Color(0xFF56667C))

private val AVATAR_COLORS = listOf(
    0xFF0BA765, 0xFF168FFB, 0xFFF76B15, 0xFF8A43DB, 0xFFE0428E, 0xFF0E9F8E, 0xFF4F5BD5, 0xFFE89B0C,
)

/** Round avatar with the first letter of the name. */
@Composable
internal fun InitialAvatar(name: String, diameter: Number, modifier: Modifier = Modifier) {
    val d = diameter.toFloat()
    val letter = name.trim().take(1).uppercase().ifEmpty { "?" }
    val color = Color(AVATAR_COLORS[Math.floorMod(name.trim().hashCode(), AVATAR_COLORS.size)])
    Box(
        modifier.size(d.r).background(color.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        RText(letter, d * 0.42f, FontWeight.Bold, color, align = TextAlign.Center)
    }
}

/** Round emoji badge on a soft tint. */
@Composable
internal fun EmojiBubble(emoji: String, tint: Color, diameter: Number) {
    val d = diameter.toFloat()
    Box(
        Modifier.size(d.r).background(tint.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        RText(emoji, d * 0.5f, FontWeight.Normal, C.Ink, align = TextAlign.Center)
    }
}

/** Segmented switch (2 or 3 parts). Each option: label + colour of its text when selected. */
@Composable
internal fun Segments(options: List<Pair<String, Color>>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(74.r)
            .background(Color(0xFFF1F4F7), RoundedCornerShape(18.r))
            .padding(6.r),
    ) {
        options.forEachIndexed { i, (label, color) ->
            val on = i == selected
            val inner = RoundedCornerShape(14.r)
            var m = Modifier.weight(1f).height(62.r)
            if (on) m = m.shadow(3.r, inner, clip = false, ambientColor = C.ShadowSoft, spotColor = C.ShadowSoft)
            Box(
                m.clip(inner).background(if (on) Color.White else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                RText(label, 19, if (on) FontWeight.SemiBold else FontWeight.Medium, if (on) color else C.StatLabel)
            }
        }
    }
}

/** Label + switch, the whole line taps. */
@Composable
internal fun ToggleLine(label: String, checked: Boolean, sub: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.r))
            .clickable { onChange(!checked) }
            .padding(vertical = 10.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            RText(label, 20, FontWeight.Medium, C.Ink, maxLines = 2)
            if (sub != null) {
                Spacer(Modifier.height(3.r))
                RText(sub, 16, FontWeight.Normal, C.Hint, maxLines = 3, lineHeight = 23)
            }
        }
        Spacer(Modifier.width(12.r))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Brand,
                uncheckedThumbColor = Color(0xFF97A3B2),
                uncheckedTrackColor = Color(0xFFF1F4F7),
                uncheckedBorderColor = Color(0xFFDCE4EA),
            ),
        )
    }
}

/** Small coloured status label. */
@Composable
internal fun StatusChip(text: String, fg: Color) {
    RText(
        text,
        15,
        FontWeight.SemiBold,
        fg,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(fg.copy(alpha = 0.11f))
            .padding(horizontal = 14.r, vertical = 6.r),
    )
}

/** White card-button: emoji + label. */
@Composable
internal fun ButtonCard(emoji: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.r)
    Row(
        modifier
            .height(76.r)
            .cardSurface(shape, SolidColor(Color.White), C.TodayBorder, 1.r, 4.r)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.r),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        RText(emoji, 23, FontWeight.Normal, C.Ink)
        Spacer(Modifier.width(10.r))
        RText(label, 19, FontWeight.SemiBold, color)
    }
}

/** Green filled button. */
@Composable
internal fun GreenButton(label: String, modifier: Modifier = Modifier, height: Number = 70, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height.r)
            .clip(RoundedCornerShape(16.r))
            .background(Brush.horizontalGradient(listOf(Color(0xFF15B97A), Color(0xFF029C62))))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        RText(label, 20, FontWeight.SemiBold, Color.White)
    }
}

/** Coloured gradient card used at the top of the ledger pages. */
@Composable
internal fun HeroCard(colors: List<Color>, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(26.r)
    val glow = colors.last()
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.r, shape, clip = false, ambientColor = glow, spotColor = glow)
            .background(Brush.linearGradient(colors), shape)
            .padding(horizontal = 28.r, vertical = 26.r),
        content = content,
    )
}

/** Thin progress bar. */
@Composable
internal fun Bar(fraction: Float, fill: Color, track: Color, height: Number = 10) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(height.r)
            .clip(RoundedCornerShape(50))
            .background(track)
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(fill, RoundedCornerShape(50))
            )
        }
    }
}

/** "5 দিন বাকি" / "আজ" / "3 দিন পার হয়েছে" for a date, with its colour. */
internal fun dueWords(day: Long, today: Long): Pair<String, Color> {
    val d = day - today
    return when {
        d > 1 -> "$d দিন বাকি" to C.Time
        d == 1L -> "কাল" to LAmber
        d == 0L -> "আজ" to LAmber
        else -> "${-d} দিন পার হয়েছে" to LRed
    }
}

/** Settle-up day choice: none, +1 week, +1 month or a picked date. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DueChooser(due: Long?, from: LocalDate, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    val week = from.plusWeeks(1).toEpochDay()
    val month = from.plusMonths(1).toEpochDay()
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.r),
        verticalArrangement = Arrangement.spacedBy(10.r),
    ) {
        Pill("তারিখ নেই", due == null) { onChange(null) }
        Pill("এক সপ্তাহ পর", due == week) { onChange(week) }
        Pill("এক মাস পর", due == month) { onChange(month) }
        val custom = due != null && due != week && due != month
        Pill(if (custom && due != null) Fmt.date(due) else "তারিখ বাছুন", custom) {
            pickDate(context, if (due != null) LocalDate.ofEpochDay(due) else from.plusDays(7)) {
                onChange(it.toEpochDay())
            }
        }
    }
}

/** Yes / no question. */
@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    danger: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { RText(title, 25, FontWeight.Bold, C.Ink, maxLines = 2) },
        text = { RText(text, 20, FontWeight.Normal, C.StatLabelBn, maxLines = 6, lineHeight = 29) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                RText(confirm, 21, FontWeight.SemiBold, if (danger) C.Danger else Brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { RText("বাতিল", 21, FontWeight.Medium, C.Hint) }
        },
    )
}

/** Round button for the page header (call, edit). */
@Composable
internal fun RoundAction(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(58.r)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.75f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Translucent number box inside a [HeroCard]. */
@Composable
internal fun HeroStat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.r))
            .padding(horizontal = 16.r, vertical = 12.r),
    ) {
        RText(label, 16, FontWeight.Normal, Color.White.copy(alpha = 0.88f))
        Spacer(Modifier.height(2.r))
        RText(value, 20, FontWeight.SemiBold, Color.White)
    }
}
