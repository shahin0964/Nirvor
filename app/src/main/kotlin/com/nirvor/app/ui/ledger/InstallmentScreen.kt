package com.nirvor.app.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.nirvor.app.data.InstallmentEntity
import com.nirvor.app.data.InstallmentPaymentEntity
import com.nirvor.app.ledger.Kisti
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.SectionLabel
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.util.Fmt
import java.time.LocalDate

/** One installment plan: what is paid, what is left, every installment with its date. */
@Composable
fun InstallmentScreen(
    vm: MainViewModel,
    bottomPadding: Dp,
    installmentId: Long,
    onBack: () -> Unit,
    onEdit: (InstallmentEntity) -> Unit,
) {
    val plans by vm.installments.collectAsStateWithLifecycle()
    val payments by vm.installmentPayments.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var paying by remember { mutableStateOf<Int?>(null) }
    var unpaying by remember { mutableStateOf<InstallmentPaymentEntity?>(null) }
    var deleting by remember { mutableStateOf(false) }

    val k = plans.firstOrNull { it.id == installmentId }
    if (k == null) {
        PageScaffold("Lend & Borrow", "কিস্তি", onBack = onBack, bottomPadding = bottomPadding) {
            item { EmptyText("এই কিস্তির হিসাবটা আর নেই।") }
        }
        return
    }
    val s = Kisti.status(k, payments, today)

    PageScaffold(
        k.title,
        k.lender.ifBlank { "কিস্তির হিসাব" },
        onBack = onBack,
        bottomPadding = bottomPadding,
        actions = {
            RoundAction(onClick = { onEdit(k) }) {
                GlyphIcon(Glyph.Edit, C.Ink, Modifier.size(28.r), strokeWidth = 2f)
            }
        },
    ) {
        item(key = "hero") {
            HeroCard(if (s.overdue > 0) HeroRed else HeroGreen) {
                val soft = Color.White.copy(alpha = 0.88f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(66.r).background(Color.White.copy(alpha = 0.92f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        RText(k.emoji, 32, FontWeight.Normal, C.Ink, align = TextAlign.Center)
                    }
                    Spacer(Modifier.width(18.r))
                    Column(Modifier.weight(1f)) {
                        RText("${s.paidCount}/${k.count} কিস্তি দেওয়া", 19, FontWeight.Medium, soft)
                        Spacer(Modifier.height(4.r))
                        RText(
                            if (s.done) "✅ সব কিস্তি শেষ" else "বাকি ৳ " + Fmt.money(s.remainingAmount),
                            38,
                            FontWeight.Bold,
                            Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(18.r))
                Bar(if (k.count > 0) s.paidCount.toFloat() / k.count else 0f, Color.White, Color.White.copy(alpha = 0.25f))
                Spacer(Modifier.height(16.r))
                Row(Modifier.fillMaxWidth()) {
                    HeroStat("দেওয়া হয়েছে", "৳ " + Fmt.money(s.paidAmount), Modifier.weight(1f))
                    Spacer(Modifier.width(14.r))
                    HeroStat("মোট", "৳ " + Fmt.money(s.total), Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.r))
                RText(
                    "প্রতি কিস্তি ৳ ${Fmt.money(k.amount)} · ${Kisti.everyText(k)} · শেষ কিস্তি ${Fmt.date(s.lastDue)}",
                    17,
                    FontWeight.Medium,
                    soft,
                    maxLines = 2,
                    lineHeight = 25,
                )
                if (s.overdue > 0) {
                    Spacer(Modifier.height(8.r))
                    RText("⚠️ ${s.overdue}টা কিস্তির তারিখ পার হয়ে গেছে", 17, FontWeight.SemiBold, Color.White)
                }
            }
        }

        val next = s.nextNumber
        if (next != null) {
            item(key = "pay") {
                GreenButton("✓  কিস্তি $next দিলাম (৳ ${Fmt.money(k.amount)})", height = 78) { paying = next }
            }
        }

        item(key = "info") {
            ListCard {
                InfoLine(
                    "🔔",
                    if (k.remind) "কিস্তির দিন ${Fmt.minute(k.remindMinute)}-এ নোটিফিকেশন আসবে" else "নোটিফিকেশন বন্ধ",
                )
                InfoLine(
                    "💸",
                    if (k.addToMoney) "কিস্তি দিলে Money-তে খরচ হিসেবে যোগ হয়" else "কিস্তি Money-তে খরচ হিসেবে যায় না",
                )
                Spacer(Modifier.height(4.r))
                RText("বদলাতে ওপরের এডিট বাটন চাপুন", 15, FontWeight.Normal, C.Hint)
            }
        }

        item(key = "all") { SectionLabel("সব কিস্তি") }
        items((1..k.count).toList(), key = { "n$it" }) { n ->
            ScheduleRow(
                k = k,
                n = n,
                paid = s.paid[n],
                isNext = n == next,
                today = today,
                onPay = { paying = n },
                onUnpay = { unpaying = it },
            )
        }
        item(key = "delete") {
            RText(
                "🗑  এই কিস্তির হিসাব মুছে ফেলুন",
                18,
                FontWeight.Medium,
                C.Danger,
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.r))
                    .clickable { deleting = true }
                    .padding(vertical = 18.r),
                align = TextAlign.Center,
            )
        }
    }

    paying?.let { n ->
        PayKistiDialog(s, n, onDismiss = { paying = null }) { num, amount, day, toMoney ->
            vm.payInstallment(k, num, amount, day, toMoney)
        }
    }
    unpaying?.let { p ->
        ConfirmDialog(
            title = "কিস্তি ${p.number} দেওয়া হয়নি?",
            text = "এই কিস্তির “দেওয়া” চিহ্ন উঠে যাবে" +
                if (p.expenseId != null) ", আর Money থেকে এর খরচটাও মুছে যাবে।" else "।",
            confirm = "চিহ্ন তুলে দিন",
            danger = true,
            onDismiss = { unpaying = null },
        ) {
            vm.unpayInstallment(p)
        }
    }
    if (deleting) {
        ConfirmDialog(
            title = "${k.title} মুছবেন?",
            text = "এই কিস্তির পুরো হিসাব মুছে যাবে। Money-তে আগে লেখা খরচগুলো থেকে যাবে।",
            confirm = "মুছে ফেলুন",
            danger = true,
            onDismiss = { deleting = false },
        ) {
            vm.deleteInstallment(k)
            onBack()
        }
    }
}

@Composable
private fun InfoLine(emoji: String, text: String) {
    Row(Modifier.padding(vertical = 6.r), verticalAlignment = Alignment.CenterVertically) {
        RText(emoji, 20, FontWeight.Normal, C.Ink)
        Spacer(Modifier.width(12.r))
        RText(text, 18, FontWeight.Normal, C.StatLabel, Modifier.weight(1f), maxLines = 2)
    }
}

@Composable
private fun ScheduleRow(
    k: InstallmentEntity,
    n: Int,
    paid: InstallmentPaymentEntity?,
    isNext: Boolean,
    today: Long,
    onPay: () -> Unit,
    onUnpay: (InstallmentPaymentEntity) -> Unit,
) {
    val due = Kisti.dueDate(k, n).toEpochDay()
    val late = paid == null && due < today
    ListCard(onClick = { if (paid != null) onUnpay(paid) else onPay() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(50.r)
                    .background(
                        when {
                            paid != null -> Brand
                            late -> LRed.copy(alpha = 0.12f)
                            else -> Color(0xFFF1F4F7)
                        },
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (paid != null) {
                    GlyphIcon(Glyph.Check, Color.White, Modifier.size(28.r), strokeWidth = 2.6f)
                } else {
                    RText("$n", 18, FontWeight.SemiBold, if (late) LRed else C.StatLabel, align = TextAlign.Center)
                }
            }
            Spacer(Modifier.width(16.r))
            Column(Modifier.weight(1f)) {
                RText("কিস্তি $n", 20, FontWeight.SemiBold, C.Ink)
                Spacer(Modifier.height(3.r))
                RText(Fmt.date(due) + " · ৳ " + Fmt.money(paid?.amount ?: k.amount), 16, FontWeight.Normal, C.Time)
            }
            when {
                paid != null -> StatusChip("দেওয়া · " + Fmt.dayMonth(LocalDate.ofEpochDay(paid.epochDay)), LGreen)
                late -> StatusChip("বাকি পড়ে আছে", LRed)
                due == today -> StatusChip("আজ", LAmber)
                isNext -> StatusChip("পরের কিস্তি", C.StatLabel)
                else -> StatusChip("সামনে", C.Hint)
            }
        }
    }
}
