package com.nirvor.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.ledger.Kisti
import com.nirvor.app.ledger.KistiStatus
import com.nirvor.app.ledger.Ledger
import com.nirvor.app.ledger.PersonBalance
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.SectionLabel
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.util.Fmt
import kotlin.math.abs

/** Tabs of the দেনা-পাওনা page. */
const val LEDGER_TAB_OWED = 0
const val LEDGER_TAB_OWE = 1
const val LEDGER_TAB_KISTI = 2

/**
 * More → দেনা-পাওনা. Three parts: পাওনা (people who will pay me), দেনা (people I will pay) and
 * কিস্তি (installment plans). Totals on top; anything whose date has passed is listed first.
 */
@Composable
fun LedgerScreen(
    vm: MainViewModel,
    bottomPadding: Dp,
    tab: Int,
    onTab: (Int) -> Unit,
    onBack: () -> Unit,
    onPerson: (Long) -> Unit,
    onKisti: (Long) -> Unit,
    onAddEntry: (gave: Boolean) -> Unit,
    onAddKisti: () -> Unit,
) {
    val people by vm.ledgerPeople.collectAsStateWithLifecycle()
    val entries by vm.ledgerEntries.collectAsStateWithLifecycle()
    val plans by vm.installments.collectAsStateWithLifecycle()
    val payments by vm.installmentPayments.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()

    val balances = Ledger.people(people, entries)
    val owedToMe = balances.filter { it.owesMe }
    val iOwe = balances.filter { it.iOwe }
    val statuses = plans.map { Kisti.status(it, payments, today) }
    val running = statuses.filter { !it.done }
    var paying by remember { mutableStateOf<KistiStatus?>(null) }

    // Dates that already passed: people (with money still open) and installments.
    val latePeople = balances.filter { !it.settled && (it.person.dueEpochDay ?: Long.MAX_VALUE) < today }
    val lateKisti = running.filter { it.overdue > 0 }

    PageScaffold("Lend & Borrow", "দেনা-পাওনা ও কিস্তি", onBack = onBack, bottomPadding = bottomPadding) {
        item(key = "summary") {
            ListCard {
                Row(Modifier.fillMaxWidth()) {
                    SummaryCol(
                        "💰 পাবো",
                        owedToMe.sumOf { it.balance },
                        LGreen,
                        if (owedToMe.isEmpty()) "কেউ নেই" else "${owedToMe.size} জনের কাছে",
                        tab == LEDGER_TAB_OWED,
                        Modifier.weight(1f),
                    ) { onTab(LEDGER_TAB_OWED) }
                    SummaryCol(
                        "💸 দেবো",
                        iOwe.sumOf { -it.balance },
                        LRed,
                        if (iOwe.isEmpty()) "কেউ নেই" else "${iOwe.size} জনকে",
                        tab == LEDGER_TAB_OWE,
                        Modifier.weight(1f),
                    ) { onTab(LEDGER_TAB_OWE) }
                    SummaryCol(
                        "💳 কিস্তি বাকি",
                        running.sumOf { it.remainingAmount },
                        LAmber,
                        if (running.isEmpty()) "চলছে না" else "${running.size}টা চলছে",
                        tab == LEDGER_TAB_KISTI,
                        Modifier.weight(1f),
                    ) { onTab(LEDGER_TAB_KISTI) }
                }
            }
        }

        if (latePeople.isNotEmpty() || lateKisti.isNotEmpty()) {
            item(key = "late") {
                LateCard(latePeople, lateKisti, today, onPerson, onKisti)
            }
        }

        item(key = "tabs") {
            Segments(
                listOf("💰 পাওনা" to LGreen, "💸 দেনা" to LRed, "💳 কিস্তি" to LAmber),
                tab,
                onTab,
            )
        }

        when (tab) {
            LEDGER_TAB_KISTI -> {
                item(key = "add-kisti") {
                    AddLine("＋ নতুন কিস্তি যোগ করুন", "লোন, মোবাইল, বাইক, এনজিও — মাসিক বা সাপ্তাহিক", onAddKisti)
                }
                if (statuses.isEmpty()) {
                    item(key = "empty-k") {
                        EmptyText("কোনো কিস্তি যোগ করা হয়নি।\nকিস্তি যোগ করলে কোনটা দেওয়া হলো আর কোনটা বাকি, সব হিসাব থাকবে।")
                    }
                }
                val sorted = running.sortedBy { it.nextDue?.toEpochDay() ?: Long.MAX_VALUE }
                if (sorted.isNotEmpty()) item(key = "k-run") { SectionLabel("চলছে") }
                items(sorted, key = { "k" + it.plan.id }) { s ->
                    KistiCard(s, today, onOpen = { onKisti(s.plan.id) }, onPay = { paying = s })
                }
                val finished = statuses.filter { it.done }
                if (finished.isNotEmpty()) item(key = "k-done") { SectionLabel("শেষ হয়েছে") }
                items(finished, key = { "kd" + it.plan.id }) { s ->
                    KistiCard(s, today, onOpen = { onKisti(s.plan.id) }, onPay = {})
                }
            }

            else -> {
                val lend = tab == LEDGER_TAB_OWED
                val open = (if (lend) owedToMe else iOwe).sortedWith(
                    compareBy<PersonBalance> { it.person.dueEpochDay ?: Long.MAX_VALUE }.thenByDescending { abs(it.balance) }
                )
                val settled = balances.filter { it.settled && it.startedAsLend == lend }.sortedByDescending { it.lastDay }
                item(key = "add-p") {
                    if (lend) {
                        AddLine("＋ ধার দিলাম", "কাকে কত দিলেন লিখে রাখুন — ফেরত পেলে পাওনা কমবে", { onAddEntry(true) })
                    } else {
                        AddLine("＋ ধার নিলাম", "কার কাছ থেকে কত নিলেন লিখে রাখুন — ফেরত দিলে দেনা কমবে", { onAddEntry(false) })
                    }
                }
                if (open.isEmpty()) {
                    item(key = "empty-p") {
                        EmptyText(
                            if (lend) "এখন কারো কাছে টাকা পাওনা নেই।" else "এখন কাউকে টাকা দেওয়ার নেই।"
                        )
                    }
                } else {
                    item(key = "p-open") {
                        SectionLabel(if (lend) "যাদের কাছে পাবো" else "যাদের দেবো")
                    }
                }
                items(open, key = { "p" + it.person.id }) { pb -> PersonRow(pb, today) { onPerson(pb.person.id) } }
                if (settled.isNotEmpty()) item(key = "p-settled") { SectionLabel("হিসাব মিটে গেছে") }
                items(settled, key = { "ps" + it.person.id }) { pb -> PersonRow(pb, today) { onPerson(pb.person.id) } }
            }
        }
    }

    paying?.let { s ->
        PayKistiDialog(s, s.nextNumber ?: 1, onDismiss = { paying = null }) { n, amount, day, toMoney ->
            vm.payInstallment(s.plan, n, amount, day, toMoney)
        }
    }
}

@Composable
private fun SummaryCol(
    title: String,
    amount: Double,
    color: Color,
    sub: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.r))
            .background(if (selected) color.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.r, horizontal = 6.r),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RText(title, 17, FontWeight.Medium, C.StatLabel, align = TextAlign.Center)
        Spacer(Modifier.height(6.r))
        RText("৳ " + Fmt.money(amount), 23, FontWeight.Bold, color, align = TextAlign.Center)
        Spacer(Modifier.height(3.r))
        RText(sub, 15, FontWeight.Normal, C.Hint, align = TextAlign.Center)
    }
}

/** Red-tinted card: dates that already passed. */
@Composable
private fun LateCard(
    people: List<PersonBalance>,
    plans: List<KistiStatus>,
    today: Long,
    onPerson: (Long) -> Unit,
    onKisti: (Long) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.r))
            .background(Color(0xFFFDF0F0))
            .padding(horizontal = 22.r, vertical = 18.r),
    ) {
        RText("⚠️  তারিখ পার হয়ে গেছে", 20, FontWeight.SemiBold, LRed)
        Spacer(Modifier.height(6.r))
        plans.take(4).forEach { s ->
            LateLine("${s.plan.emoji} ${s.plan.title}: ${s.overdue}টা কিস্তি বাকি পড়ে আছে") { onKisti(s.plan.id) }
        }
        people.take(4).forEach { pb ->
            val days = today - (pb.person.dueEpochDay ?: today)
            val text = if (pb.owesMe) {
                "${pb.person.name}-এর কাছে ৳ ${Fmt.money(pb.balance)} পাওনা · $days দিন পার"
            } else {
                "${pb.person.name}-কে ৳ ${Fmt.money(-pb.balance)} দেওয়ার কথা · $days দিন পার"
            }
            LateLine(text) { onPerson(pb.person.id) }
        }
    }
}

@Composable
private fun LateLine(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.r))
            .clickable(onClick = onClick)
            .padding(vertical = 7.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RText(text, 18, FontWeight.Normal, Color(0xFF7A2E31), Modifier.weight(1f), maxLines = 2)
        RText("›", 22, FontWeight.Medium, LRed)
    }
}

/** Light-green "add" line at the top of each tab. */
@Composable
private fun AddLine(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.r))
            .background(Color(0xFFE3F6EC))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.r, vertical = 18.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            RText(title, 21, FontWeight.SemiBold, Color(0xFF0A8F56))
            Spacer(Modifier.height(3.r))
            RText(sub, 16, FontWeight.Normal, Color(0xFF3F7E62), maxLines = 2)
        }
    }
}

@Composable
private fun PersonRow(pb: PersonBalance, today: Long, onClick: () -> Unit) {
    val p = pb.person
    ListCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(p.name, 60)
            Spacer(Modifier.width(16.r))
            Column(Modifier.weight(1f)) {
                RText(p.name, 22, FontWeight.SemiBold, C.Ink)
                Spacer(Modifier.height(4.r))
                val due = p.dueEpochDay
                when {
                    pb.settled -> RText("✅ শেষ লেনদেন " + Fmt.date(pb.lastDay), 17, FontWeight.Normal, C.Time)
                    due != null -> {
                        val (words, color) = dueWords(due, today)
                        RText("📅 ${Fmt.date(due)} · $words", 17, FontWeight.Medium, color)
                    }
                    else -> RText("শেষ লেনদেন " + Fmt.date(pb.lastDay), 17, FontWeight.Normal, C.Time)
                }
            }
            Spacer(Modifier.width(10.r))
            Column(horizontalAlignment = Alignment.End) {
                val color = when {
                    pb.owesMe -> LGreen
                    pb.iOwe -> LRed
                    else -> C.Hint
                }
                RText("৳ " + Fmt.money(abs(pb.balance)), 22, FontWeight.Bold, color)
                Spacer(Modifier.height(2.r))
                RText(
                    when {
                        pb.owesMe -> "পাবো"
                        pb.iOwe -> "দেবো"
                        else -> "মিটে গেছে"
                    },
                    15,
                    FontWeight.Medium,
                    C.Hint,
                )
            }
        }
    }
}

@Composable
internal fun KistiCard(s: KistiStatus, today: Long, onOpen: () -> Unit, onPay: () -> Unit) {
    val k = s.plan
    ListCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble(k.emoji, LAmber, 60)
            Spacer(Modifier.width(16.r))
            Column(Modifier.weight(1f)) {
                RText(k.title, 22, FontWeight.SemiBold, C.Ink)
                Spacer(Modifier.height(4.r))
                val where = if (k.lender.isNotBlank()) k.lender + " · " else ""
                RText(where + Kisti.everyText(k) + " ৳ " + Fmt.money(k.amount), 17, FontWeight.Normal, C.Time)
            }
            Spacer(Modifier.width(10.r))
            Column(horizontalAlignment = Alignment.End) {
                RText("৳ " + Fmt.money(s.remainingAmount), 22, FontWeight.Bold, if (s.done) C.Hint else C.Ink)
                Spacer(Modifier.height(2.r))
                RText("বাকি", 15, FontWeight.Medium, C.Hint)
            }
        }
        Spacer(Modifier.height(16.r))
        Bar(if (k.count > 0) s.paidCount.toFloat() / k.count else 0f, Brand, Color(0xFFEFF2F6), 9)
        Spacer(Modifier.height(10.r))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText("${s.paidCount}/${k.count} কিস্তি দেওয়া", 17, FontWeight.Medium, C.StatLabel, Modifier.weight(1f))
            val next = s.nextDue
            when {
                s.done -> StatusChip("✅ সব কিস্তি শেষ", LGreen)
                s.overdue > 0 -> StatusChip("${s.overdue}টা বাকি পড়ে আছে", LRed)
                next != null -> {
                    val (words, color) = dueWords(next.toEpochDay(), today)
                    StatusChip("পরের কিস্তি ${Fmt.dayMonth(next)} · $words", if (color == C.Time) C.StatLabel else color)
                }
            }
        }
        if (!s.done) {
            Spacer(Modifier.height(14.r))
            GreenButton("✓  কিস্তি ${s.nextNumber} দিলাম (৳ ${Fmt.money(k.amount)})", height = 62, onClick = onPay)
        }
    }
}
