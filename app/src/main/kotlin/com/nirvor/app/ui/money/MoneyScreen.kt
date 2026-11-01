package com.nirvor.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.data.ExpenseEntity
import com.nirvor.app.data.IncomeEntity
import com.nirvor.app.data.IncomeRepeatEntity
import com.nirvor.app.money.MoneyCategories
import com.nirvor.app.money.MoneyCategory
import com.nirvor.app.money.MoneyType
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.Pill
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.TrashButton
import com.nirvor.app.ui.cardSurface
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.util.Fmt
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt

/** One line of the money list: an expense or an income, with its category resolved. */
private class MoneyEntry(
    val key: String,
    val income: Boolean,
    val cat: MoneyCategory,
    val amount: Double,
    val note: String,
    val epochDay: Long,
    val createdAt: Long,
    val auto: Boolean,
    val expense: ExpenseEntity?,
    val incomeEntry: IncomeEntity?,
)

private fun ExpenseEntity.entry(custom: List<MoneyCategory>) = MoneyEntry(
    "e$id", false, MoneyCategories.of(MoneyType.Expense, category, custom),
    amount, note, epochDay, createdAt, false, this, null,
)

private fun IncomeEntity.entry(custom: List<MoneyCategory>) = MoneyEntry(
    "i$id", true, MoneyCategories.of(MoneyType.Income, category, custom),
    amount, note, epochDay, createdAt, repeatId != null, null, this,
)

private fun pct(fraction: Double): String {
    val p = fraction * 100.0
    return when {
        p <= 0.0 -> "0%"
        p < 1.0 -> "<1%"
        else -> "${p.roundToInt()}%"
    }
}

// ---------------------------------------------------------------- Money tab

/**
 * Income and expenses month by month: what came in (salary etc.), what went out, what is left,
 * where it went (by category, with emoji) and every entry of the month.
 */
@Composable
fun MoneyScreen(
    vm: MainViewModel,
    bottomPadding: Dp,
    onAdd: (MoneyType) -> Unit,
    onCategories: () -> Unit,
) {
    val expenses by vm.allExpenses.collectAsStateWithLifecycle()
    val incomes by vm.allIncomes.collectAsStateWithLifecycle()
    val repeats by vm.incomeRepeats.collectAsStateWithLifecycle()
    val custom by vm.customCategories.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()

    val thisMonth = YearMonth.from(LocalDate.ofEpochDay(today))
    // The month being looked at, saved as "2026-10" so it survives rotation ("" = this month).
    var picked by rememberSaveable { mutableStateOf("") }
    val month = if (picked.isEmpty()) thisMonth else YearMonth.parse(picked)
    val isCurrent = month == thisMonth
    val days = expenses.map { it.epochDay } + incomes.map { it.epochDay }
    val firstMonth = days.minOrNull()?.let { minOf(YearMonth.from(LocalDate.ofEpochDay(it)), thisMonth) } ?: thisMonth
    val lastMonth = days.maxOrNull()?.let { maxOf(YearMonth.from(LocalDate.ofEpochDay(it)), thisMonth) } ?: thisMonth

    val start = month.atDay(1).toEpochDay()
    val end = month.atEndOfMonth().toEpochDay()
    val monthExpenses = expenses.filter { it.epochDay in start..end }
    val monthIncomes = incomes.filter { it.epochDay in start..end }
    val expenseTotal = monthExpenses.sumOf { it.amount }
    val incomeTotal = monthIncomes.sumOf { it.amount }
    val allBalance = incomes.sumOf { it.amount } - expenses.sumOf { it.amount }
    val todayExpense = expenses.filter { it.epochDay == today }.sumOf { it.amount }

    val expenseParts = monthExpenses
        .groupBy { MoneyCategories.of(MoneyType.Expense, it.category, custom) }
        .map { (cat, list) -> cat to list.sumOf { it.amount } }
        .sortedByDescending { it.second }
    val incomeParts = monthIncomes
        .groupBy { MoneyCategories.of(MoneyType.Income, it.category, custom) }
        .map { (cat, list) -> cat to list.sumOf { it.amount } }
        .sortedByDescending { it.second }

    // 0 = all, 1 = expenses, 2 = income
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val entries = buildList {
        if (filter != 2) monthExpenses.forEach { add(it.entry(custom)) }
        if (filter != 1) monthIncomes.forEach { add(it.entry(custom)) }
    }.sortedWith(compareByDescending<MoneyEntry> { it.epochDay }.thenByDescending { it.createdAt })
    val byDay = entries.groupBy { it.epochDay }

    var stopping by remember { mutableStateOf<IncomeRepeatEntity?>(null) }

    PageScaffold(
        "Money",
        "আয়-খরচের হিসাব",
        onBack = null,
        bottomPadding = bottomPadding,
        actions = { CategoriesButton(onCategories) },
    ) {
        item(key = "month") {
            MonthSwitcher(
                month = month,
                isCurrent = isCurrent,
                canPrev = month > firstMonth,
                canNext = month < lastMonth,
                onPrev = { picked = month.minusMonths(1).toString() },
                onNext = { picked = month.plusMonths(1).toString() },
                onThisMonth = { picked = "" },
            )
        }
        item(key = "hero") {
            BalanceHero(
                income = incomeTotal,
                expense = expenseTotal,
                isCurrent = isCurrent,
                todayExpense = todayExpense,
                allBalance = allBalance,
                onAddIncome = { onAdd(MoneyType.Income) },
            )
        }
        item(key = "quick") {
            Row(Modifier.fillMaxWidth()) {
                QuickAdd("💰", "আয় / বেতন যোগ", IncomeGreen, Modifier.weight(1f)) { onAdd(MoneyType.Income) }
                Spacer(Modifier.width(14.r))
                QuickAdd("💸", "খরচ যোগ", ExpenseRed, Modifier.weight(1f)) { onAdd(MoneyType.Expense) }
            }
        }
        if (expenseParts.isNotEmpty()) {
            item(key = "exp-parts") { Breakdown("💸  খরচ কোথায় গেল", expenseTotal, expenseParts) }
        }
        if (incomeParts.isNotEmpty()) {
            item(key = "inc-parts") { Breakdown("💰  আয় কোথা থেকে এলো", incomeTotal, incomeParts) }
        }
        if (repeats.isNotEmpty()) {
            item(key = "repeats") { RepeatsCard(repeats, custom) { stopping = it } }
        }
        item(key = "filter") {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RText("লেনদেন", 21, FontWeight.SemiBold, C.StatLabel, Modifier.weight(1f).padding(start = 6.r))
                Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                    Pill("সব", filter == 0) { filter = 0 }
                    Pill("💸 খরচ", filter == 1) { filter = 1 }
                    Pill("💰 আয়", filter == 2) { filter = 2 }
                }
            }
        }
        if (entries.isEmpty()) {
            item(key = "empty") {
                EmptyText(
                    when (filter) {
                        1 -> "${monthLabel(month)}-এ কোনো খরচ নেই।"
                        2 -> "${monthLabel(month)}-এ কোনো আয় যোগ করা হয়নি।\nউপরের “আয় / বেতন যোগ” বাটনে চাপ দিন।"
                        else -> "${monthLabel(month)}-এ এখনো কোনো আয় বা খরচ নেই।\nউপরের বাটন বা নিচের + দিয়ে যোগ করুন।"
                    }
                )
            }
        }
        byDay.forEach { (day, list) ->
            item(key = "d$day") { DayHeader(day, today, list) }
            items(list, key = { it.key }) { e ->
                EntryCard(e) {
                    e.expense?.let { vm.deleteExpense(it) }
                    e.incomeEntry?.let { vm.deleteIncome(it) }
                }
            }
        }
    }

    stopping?.let { r ->
        AlertDialog(
            onDismissRequest = { stopping = null },
            containerColor = Color.White,
            title = { RText("মাসিক আয় বন্ধ করবেন?", 26, FontWeight.Bold, C.Ink) },
            text = {
                RText(
                    "সামনের মাস থেকে ৳ ${Fmt.money(r.amount)} আর নিজে থেকে যোগ হবে না। " +
                        "আগে যোগ হওয়া টাকার হিসাব যেমন আছে তেমনই থাকবে।",
                    20,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    maxLines = 4,
                    lineHeight = 29,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.stopRepeat(r)
                    stopping = null
                }) {
                    RText("বন্ধ করুন", 21, FontWeight.SemiBold, C.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { stopping = null }) {
                    RText("থাক", 21, FontWeight.Medium, C.Hint)
                }
            },
        )
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun CategoriesButton(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.75f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.r, vertical = 12.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Grid, C.Ink, Modifier.size(24.r), strokeWidth = 1.9f)
        Spacer(Modifier.width(10.r))
        RText("ক্যাটাগরি", 18, FontWeight.SemiBold, C.Ink)
    }
}

@Composable
private fun MonthSwitcher(
    month: YearMonth,
    isCurrent: Boolean,
    canPrev: Boolean,
    canNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onThisMonth: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundNav(Glyph.ChevronLeft, canPrev, onPrev)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            RText(monthLabel(month), 25, FontWeight.Bold, C.Ink, align = TextAlign.Center)
            Spacer(Modifier.height(2.r))
            if (isCurrent) {
                RText("এই মাস", 16, FontWeight.Medium, C.Hint)
            } else {
                RText(
                    "এই মাসে ফিরুন",
                    16,
                    FontWeight.SemiBold,
                    IncomeGreen,
                    Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onThisMonth).padding(horizontal = 10.r, vertical = 2.r),
                )
            }
        }
        RoundNav(Glyph.ChevronRight, canNext, onNext)
    }
}

@Composable
private fun RoundNav(glyph: Glyph, enabled: Boolean, onClick: () -> Unit) {
    GlyphIcon(
        glyph,
        if (enabled) C.Ink else C.Ink.copy(alpha = 0.22f),
        Modifier
            .size(58.r)
            .clip(CircleShape)
            .background(Color.White)
            .border(1.r, C.TodayBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.r),
        strokeWidth = 2.2f,
    )
}

/** Green card: income − expenses of the month (orange-red when more went out than came in). */
@Composable
private fun BalanceHero(
    income: Double,
    expense: Double,
    isCurrent: Boolean,
    todayExpense: Double,
    allBalance: Double,
    onAddIncome: () -> Unit,
) {
    val balance = income - expense
    val noIncome = income <= 0.0
    val negative = !noIncome && balance < 0.0
    val colors = if (negative) listOf(Color(0xFFFF8A5B), Color(0xFFE5484D)) else listOf(C.TaskGreenA, C.TaskGreenB)
    val glow = if (negative) Color(0xFFB4383C) else C.ShadowGreen
    val shape = RoundedCornerShape(26.r)
    val soft = Color.White.copy(alpha = 0.88f)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.r, shape, clip = false, ambientColor = glow, spotColor = glow)
            .background(Brush.linearGradient(colors), shape)
            .padding(horizontal = 28.r, vertical = 26.r)
    ) {
        val title = when {
            noIncome -> "এই মাসের মোট খরচ"
            negative -> "আয়ের চেয়ে বেশি খরচ হয়েছে"
            else -> "এই মাসে হাতে আছে"
        }
        val big = when {
            noIncome -> "৳ " + Fmt.money(expense)
            negative -> "− ৳ " + Fmt.money(abs(balance))
            else -> "৳ " + Fmt.money(balance)
        }
        RText(title, 19, FontWeight.Medium, soft)
        Spacer(Modifier.height(6.r))
        RText(big, 46, FontWeight.Bold, Color.White)
        Spacer(Modifier.height(20.r))
        Row(Modifier.fillMaxWidth()) {
            HeroStat("💰", "আয়", income, Modifier.weight(1f))
            Spacer(Modifier.width(14.r))
            HeroStat("💸", "খরচ", expense, Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.r))
        if (noIncome) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.r))
                    .background(Color.White.copy(alpha = 0.18f))
                    .clickable(onClick = onAddIncome)
                    .padding(horizontal = 18.r, vertical = 14.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RText("＋", 22, FontWeight.Bold, Color.White)
                Spacer(Modifier.width(12.r))
                RText(
                    "এই মাসের বেতন / আয় যোগ করুন — তাহলে খরচের পর কত টাকা বাকি থাকলো সেটা দেখাবে",
                    17,
                    FontWeight.Medium,
                    Color.White,
                    Modifier.weight(1f),
                    maxLines = 3,
                    lineHeight = 25,
                )
            }
        } else {
            val fraction = expense / income
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.r)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color.White, RoundedCornerShape(50))
                )
            }
            Spacer(Modifier.height(10.r))
            RText(
                if (expense <= income) "আয়ের ${pct(fraction)} খরচ হয়েছে" else "আয়ের চেয়ে ৳ ${Fmt.money(expense - income)} বেশি খরচ",
                17,
                FontWeight.Medium,
                soft,
            )
        }
        if (isCurrent) {
            Spacer(Modifier.height(18.r))
            Box(Modifier.fillMaxWidth().height(1.r).background(Color.White.copy(alpha = 0.28f)))
            Spacer(Modifier.height(16.r))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    RText("আজকের খরচ", 16, FontWeight.Normal, soft)
                    Spacer(Modifier.height(2.r))
                    RText("৳ " + Fmt.money(todayExpense), 20, FontWeight.SemiBold, Color.White)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    RText("সব মাস মিলিয়ে হাতে", 16, FontWeight.Normal, soft)
                    Spacer(Modifier.height(2.r))
                    RText(
                        (if (allBalance < 0.0) "− ৳ " else "৳ ") + Fmt.money(abs(allBalance)),
                        20,
                        FontWeight.SemiBold,
                        Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(emoji: String, label: String, value: Double, modifier: Modifier) {
    Row(
        modifier
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.r))
            .padding(horizontal = 14.r, vertical = 14.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.r).background(Color.White.copy(alpha = 0.92f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            RText(emoji, 23, FontWeight.Normal, C.Ink, align = TextAlign.Center)
        }
        Spacer(Modifier.width(12.r))
        Column {
            RText(label, 16, FontWeight.Normal, Color.White.copy(alpha = 0.88f))
            RText("৳ " + Fmt.money(value), 21, FontWeight.SemiBold, Color.White)
        }
    }
}

@Composable
private fun QuickAdd(emoji: String, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.r)
    Row(
        modifier
            .height(74.r)
            .cardSurface(shape, SolidColor(Color.White), C.TodayBorder, 1.r, 4.r)
            .clip(shape)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        RText(emoji, 23, FontWeight.Normal, C.Ink)
        Spacer(Modifier.width(10.r))
        RText(label, 19, FontWeight.SemiBold, color)
    }
}

/** Category list with amount, share bar and percentage. */
@Composable
private fun Breakdown(title: String, total: Double, parts: List<Pair<MoneyCategory, Double>>) {
    ListCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText(title, 22, FontWeight.SemiBold, C.Ink, Modifier.weight(1f))
            RText("৳ " + Fmt.money(total), 20, FontWeight.SemiBold, C.StatLabel)
        }
        Spacer(Modifier.height(8.r))
        parts.forEach { (cat, sum) ->
            val fraction = if (total > 0.0) sum / total else 0.0
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBubble(cat, 56)
                Spacer(Modifier.width(16.r))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RText(cat.label, 20, FontWeight.Medium, C.Ink, Modifier.weight(1f))
                        Spacer(Modifier.width(10.r))
                        RText("৳ " + Fmt.money(sum), 20, FontWeight.SemiBold, C.Ink)
                    }
                    Spacer(Modifier.height(9.r))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(8.r)
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFEFF2F6))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Color(cat.color), RoundedCornerShape(50))
                            )
                        }
                        RText(pct(fraction), 16, FontWeight.Medium, C.Hint, Modifier.width(62.r), align = TextAlign.End)
                    }
                }
            }
        }
    }
}

/** Incomes that are added by themselves every month (salary), each with a stop button. */
@Composable
private fun RepeatsCard(repeats: List<IncomeRepeatEntity>, custom: List<MoneyCategory>, onStop: (IncomeRepeatEntity) -> Unit) {
    ListCard {
        RText("🔁  প্রতি মাসে নিজে থেকে যোগ হয়", 22, FontWeight.SemiBold, C.Ink)
        Spacer(Modifier.height(4.r))
        RText("নির্দিষ্ট তারিখ এলেই আয় হিসেবে যোগ হয়ে যায়", 17, FontWeight.Normal, C.Hint)
        Spacer(Modifier.height(6.r))
        repeats.forEach { r ->
            val cat = MoneyCategories.of(MoneyType.Income, r.category, custom)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 10.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryBubble(cat, 56)
                Spacer(Modifier.width(16.r))
                Column(Modifier.weight(1f)) {
                    RText(r.note.ifBlank { cat.label }, 20, FontWeight.Medium, C.Ink)
                    Spacer(Modifier.height(3.r))
                    val dayText = if (r.dayOfMonth >= 29) "${r.dayOfMonth} তারিখ (ছোট মাসে শেষ দিন)" else "${r.dayOfMonth} তারিখ"
                    RText("৳ ${Fmt.money(r.amount)} · প্রতি মাসের $dayText", 17, FontWeight.Normal, C.Time, maxLines = 2)
                }
                Spacer(Modifier.width(10.r))
                RText(
                    "বন্ধ",
                    17,
                    FontWeight.SemiBold,
                    C.Danger,
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFDEEEE))
                        .clickable { onStop(r) }
                        .padding(horizontal = 18.r, vertical = 10.r),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(day: Long, today: Long, list: List<MoneyEntry>) {
    val label = when (day) {
        today -> "আজ"
        today - 1 -> "গতকাল"
        else -> Fmt.date(day)
    }
    val out = list.filter { !it.income }.sumOf { it.amount }
    val inn = list.filter { it.income }.sumOf { it.amount }
    val summary = listOfNotNull(
        if (inn > 0.0) "আয় ৳ " + Fmt.money(inn) else null,
        if (out > 0.0) "খরচ ৳ " + Fmt.money(out) else null,
    ).joinToString("  ·  ")
    Row(
        Modifier.fillMaxWidth().padding(start = 6.r, end = 6.r, top = 10.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RText(label, 21, FontWeight.SemiBold, C.StatLabel, Modifier.weight(1f))
        RText(summary, 17, FontWeight.Medium, C.Hint)
    }
}

@Composable
private fun EntryCard(e: MoneyEntry, onDelete: () -> Unit) {
    ListCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBubble(e.cat, 60)
            Spacer(Modifier.width(16.r))
            Column(Modifier.weight(1f)) {
                RText(e.cat.label, 22, FontWeight.Medium, C.Ink)
                val sub = listOfNotNull(e.note.ifBlank { null }, if (e.auto) "🔁 মাসিক (অটো)" else null).joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Spacer(Modifier.height(4.r))
                    RText(sub, 18, FontWeight.Normal, C.Time, maxLines = 2)
                }
            }
            Spacer(Modifier.width(10.r))
            RText(
                (if (e.income) "+ ৳ " else "− ৳ ") + Fmt.money(e.amount),
                22,
                FontWeight.SemiBold,
                if (e.income) IncomeGreen else C.Ink,
            )
            TrashButton(onDelete)
        }
    }
}
