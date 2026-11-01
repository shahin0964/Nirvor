package com.nirvor.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.money.MoneyCategory
import com.nirvor.app.money.MoneyType
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import java.time.YearMonth

/** Green used for income amounts, orange-red for expenses. */
internal val IncomeGreen = Color(0xFF0A955A)
internal val ExpenseRed = Color(0xFFE5484D)

private val MONTHS = listOf(
    "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
    "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
)

/** "অক্টোবর 2026" */
internal fun monthLabel(m: YearMonth): String = MONTHS[m.monthValue - 1] + " " + m.year

/** Round emoji badge tinted with the category colour. */
@Composable
internal fun CategoryBubble(emoji: String, color: Long, diameter: Number, modifier: Modifier = Modifier) {
    val d = diameter.toFloat()
    Box(
        modifier.size(d.r).background(Color(color).copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        RText(emoji, d * 0.5f, FontWeight.Normal, C.Ink, align = TextAlign.Center)
    }
}

@Composable
internal fun CategoryBubble(cat: MoneyCategory, diameter: Number, modifier: Modifier = Modifier) {
    CategoryBubble(cat.emoji, cat.color, diameter, modifier)
}

/** Selectable "🍔 খাবার" chip in the add sheet. */
@Composable
internal fun CategoryChip(cat: MoneyCategory, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val tint = Color(cat.color)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) tint.copy(alpha = 0.12f) else Color(0xFFF1F4F7))
            .border(if (selected) 1.8.r else 1.2.r, if (selected) tint else Color(0xFFE1E7ED), shape)
            .clickable(onClick = onClick)
            .padding(start = 16.r, end = 22.r, top = 11.r, bottom = 11.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RText(cat.emoji, 21, FontWeight.Normal, C.Ink)
        Spacer(Modifier.width(9.r))
        RText(cat.label, 19, if (selected) FontWeight.SemiBold else FontWeight.Medium, if (selected) C.Ink else C.StatLabel)
    }
}

/** Outlined "＋ নতুন ক্যাটাগরি" chip that opens the new-category dialog. */
@Composable
internal fun NewCategoryChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(Color.White)
            .border(1.6.r, Color(0xFF0BA765), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.r, vertical = 11.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RText("＋", 20, FontWeight.Bold, Color(0xFF0BA765))
        Spacer(Modifier.width(8.r))
        RText("নতুন ক্যাটাগরি", 19, FontWeight.SemiBold, Color(0xFF0A8F56))
    }
}

/** Two-way switch: খরচ | আয়. */
@Composable
internal fun TypeSwitch(type: MoneyType, onChange: (MoneyType) -> Unit) {
    val outer = RoundedCornerShape(18.r)
    Row(
        Modifier
            .fillMaxWidth()
            .height(74.r)
            .background(Color(0xFFF1F4F7), outer)
            .padding(6.r),
    ) {
        listOf(MoneyType.Expense, MoneyType.Income).forEach { t ->
            val selected = t == type
            val inner = RoundedCornerShape(14.r)
            var m = Modifier.weight(1f).height(62.r)
            if (selected) m = m.shadow(3.r, inner, clip = false, ambientColor = C.ShadowSoft, spotColor = C.ShadowSoft)
            Box(
                m
                    .clip(inner)
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onChange(t) },
                contentAlignment = Alignment.Center,
            ) {
                val label = if (t == MoneyType.Expense) "💸  খরচ" else "💰  আয় / বেতন"
                val color = when {
                    !selected -> C.StatLabel
                    t == MoneyType.Expense -> ExpenseRed
                    else -> IncomeGreen
                }
                RText(label, 20, if (selected) FontWeight.SemiBold else FontWeight.Medium, color)
            }
        }
    }
}
