package com.nirvor.app.ui.ledger

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.data.InstallmentEntity
import com.nirvor.app.data.LedgerPersonEntity
import com.nirvor.app.ledger.Kisti
import com.nirvor.app.ledger.KistiStatus
import com.nirvor.app.ledger.Ledger
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.Pill
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.sheets.DayPicker
import com.nirvor.app.ui.sheets.FieldLabel
import com.nirvor.app.ui.sheets.NField
import com.nirvor.app.ui.sheets.NSheet
import com.nirvor.app.ui.sheets.SaveButton
import com.nirvor.app.ui.sheets.SheetTitle
import com.nirvor.app.ui.sheets.pickTime
import com.nirvor.app.ui.theme.C
import com.nirvor.app.util.Fmt
import java.time.LocalDate

/** 2500.0 -> "2500", 99.5 -> "99.5" (for editing amounts). */
internal fun plainAmount(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

private fun parseAmount(text: String): Double? = text.replace(",", "").trim().toDoubleOrNull()

/** "রহিম আপনাকে দেবে ৳ 3,000" / "আপনি রহিমকে দেবেন ৳ 500" / settled. */
internal fun balanceWords(name: String, balance: Double): String = when {
    Ledger.isZero(balance) -> "হিসাব মিটে গেছে ✅"
    balance > 0 -> "$name আপনাকে দেবে ৳ ${Fmt.money(balance)}"
    else -> "আপনি $name-কে দেবেন ৳ ${Fmt.money(-balance)}"
}

// ------------------------------------------------------------ দিলাম / পেলাম

/**
 * Writes one money movement with a person. [presetPersonId] = null lets the user type a new
 * name or pick someone already in the list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LedgerEntrySheet(
    vm: MainViewModel,
    presetPersonId: Long?,
    presetGave: Boolean,
    onDismiss: () -> Unit,
    onNeedNotifications: () -> Unit,
) {
    val people by vm.ledgerPeople.collectAsStateWithLifecycle()
    val entries by vm.ledgerEntries.collectAsStateWithLifecycle()
    var gave by remember { mutableStateOf(presetGave) }
    var personId by remember { mutableStateOf(presetPersonId) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var due by remember { mutableStateOf<Long?>(null) }
    var dueTouched by remember { mutableStateOf(false) }
    var remind by remember { mutableStateOf(true) }

    val value = parseAmount(amount)
    val chosen = people.firstOrNull { it.id == personId }
    val now = if (chosen != null) Ledger.balance(entries.filter { it.personId == chosen.id }) else 0.0
    val after = now + (if (gave) 1.0 else -1.0) * (value ?: 0.0)
    val who = chosen?.name ?: name.trim().ifEmpty { "উনি" }
    val shownDue = if (dueTouched) due else chosen?.dueEpochDay

    NSheet(onDismiss) {
        SheetTitle(if (presetPersonId != null && chosen != null) chosen.name else "দেনা-পাওনা লিখুন")
        Segments(
            listOf("💸  আমি দিলাম" to LRed, "💰  আমি পেলাম" to LGreen),
            if (gave) 0 else 1,
        ) { gave = it == 0 }
        Spacer(Modifier.height(12.r))
        val hint = if (chosen == null) {
            if (gave) {
                "টাকা দিলেন (ধার দিলেন) — এই টাকা আপনি ফেরত পাবেন, তাই এটা আপনার পাওনা।"
            } else {
                "টাকা পেলেন (ধার নিলেন) — এই টাকা আপনাকে ফেরত দিতে হবে, তাই এটা আপনার দেনা।"
            }
        } else {
            "এখন: " + balanceWords(who, now) + (if (value != null && value > 0) "\nএরপর: " + balanceWords(who, after) else "")
        }
        RText(hint, 18, FontWeight.Normal, C.StatLabelBn, maxLines = 4, lineHeight = 27)

        if (presetPersonId == null) {
            FieldLabel(if (gave) "কাকে দিলেন?" else "কার কাছ থেকে পেলেন?")
            NField(
                if (chosen != null) chosen.name else name,
                {
                    name = it
                    personId = null
                },
                "নাম লিখুন",
            )
            val typed = name.trim()
            val matches = if (chosen != null) {
                emptyList()
            } else {
                people.filter { typed.isEmpty() || it.name.contains(typed, ignoreCase = true) }.take(8)
            }
            if (matches.isNotEmpty()) {
                Spacer(Modifier.height(12.r))
                RText(if (typed.isEmpty()) "আগের কেউ?" else "মিলে যাওয়া নাম", 16, FontWeight.Medium, C.Hint)
                Spacer(Modifier.height(8.r))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.r),
                    verticalArrangement = Arrangement.spacedBy(10.r),
                ) {
                    matches.forEach { p ->
                        PersonChip(p) {
                            personId = p.id
                            name = p.name
                        }
                    }
                }
            }
            if (chosen == null && typed.isNotEmpty()) {
                FieldLabel("মোবাইল নম্বর (ঐচ্ছিক)")
                NField(phone, { phone = it }, "যেমন: 01XXXXXXXXX", keyboardType = KeyboardType.Phone)
            }
        }

        FieldLabel("টাকা")
        NField(amount, { amount = it }, "টাকার পরিমাণ (৳)", keyboardType = KeyboardType.Decimal)
        FieldLabel("বিবরণ (ঐচ্ছিক)")
        NField(note, { note = it }, if (gave) "যেমন: বাসা ভাড়ার জন্য ধার দিলাম" else "যেমন: দোকানের জন্য ধার নিলাম")
        FieldLabel("কবে")
        DayPicker(day) { day = it }

        if (!Ledger.isZero(after) || shownDue != null) {
            FieldLabel(if (after >= 0) "কবে ফেরত পাবেন? (ঐচ্ছিক)" else "কবে ফেরত দেবেন? (ঐচ্ছিক)")
            DueChooser(shownDue, day) {
                due = it
                dueTouched = true
            }
            if (shownDue != null) {
                Spacer(Modifier.height(8.r))
                ToggleLine("🔔 ওই দিন সকাল 10টায় নোটিফিকেশন", remind) { remind = it }
            }
        }

        val ok = value != null && value > 0.0 && (chosen != null || name.isNotBlank())
        SaveButton(ok) {
            if (value != null && ok) {
                vm.addLedgerEntry(
                    personId = chosen?.id,
                    newName = name,
                    phone = phone,
                    gave = gave,
                    amount = value,
                    note = note,
                    epochDay = day.toEpochDay(),
                    due = shownDue,
                    setDue = dueTouched || chosen == null,
                    remind = remind,
                )
                if (remind && shownDue != null) onNeedNotifications()
                onDismiss()
            }
        }
    }
}

@Composable
private fun PersonChip(p: LedgerPersonEntity, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(Color(0xFFF1F4F7))
            .border(1.2.r, Color(0xFFE1E7ED), shape)
            .clickable(onClick = onClick)
            .padding(start = 8.r, end = 20.r, top = 7.r, bottom = 7.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(p.name, 38)
        Spacer(Modifier.width(10.r))
        RText(p.name, 19, FontWeight.Medium, C.StatLabel)
    }
}

// ------------------------------------------------------------ কিস্তি: add / edit

private val KISTI_EMOJIS = listOf(
    "💳", "🏦", "🤝", "📱", "💻", "🏍️", "🛵", "🚗", "🏠", "📺", "❄️", "🛋️", "💍", "🎓", "🐄", "🌾",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KistiSheet(
    vm: MainViewModel,
    existing: InstallmentEntity?,
    onDismiss: () -> Unit,
    onNeedNotifications: () -> Unit,
) {
    val context = LocalContext.current
    var emoji by remember { mutableStateOf(existing?.emoji ?: "💳") }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var lender by remember { mutableStateOf(existing?.lender ?: "") }
    var amount by remember { mutableStateOf(existing?.amount?.let { plainAmount(it) } ?: "") }
    var count by remember { mutableStateOf(existing?.count?.toString() ?: "") }
    var weekly by remember { mutableStateOf(existing?.weekly ?: false) }
    var first by remember { mutableStateOf(existing?.let { LocalDate.ofEpochDay(it.firstEpochDay) } ?: LocalDate.now()) }
    var paidBefore by remember { mutableStateOf("") }
    var toMoney by remember { mutableStateOf(existing?.addToMoney ?: true) }
    var remind by remember { mutableStateOf(existing?.remind ?: true) }
    var minute by remember { mutableStateOf(existing?.remindMinute ?: 600) }

    val amt = parseAmount(amount)
    val cnt = count.trim().toIntOrNull()
    val valid = title.isNotBlank() && amt != null && amt > 0.0 && cnt != null && cnt in 1..600

    NSheet(onDismiss) {
        SheetTitle(if (existing == null) "নতুন কিস্তি" else "কিস্তি এডিট")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.r),
            verticalArrangement = Arrangement.spacedBy(8.r),
        ) {
            KISTI_EMOJIS.forEach { e ->
                val on = e == emoji
                Box(
                    Modifier
                        .size(58.r)
                        .clip(CircleShape)
                        .background(if (on) LAmber.copy(alpha = 0.16f) else Color(0xFFF3F5F8))
                        .border(if (on) 2.r else 0.r, if (on) LAmber else Color.Transparent, CircleShape)
                        .clickable { emoji = e },
                    contentAlignment = Alignment.Center,
                ) {
                    RText(e, 27, FontWeight.Normal, C.Ink, align = TextAlign.Center)
                }
            }
        }
        FieldLabel("কিসের কিস্তি?")
        NField(title, { if (it.length <= 40) title = it }, "যেমন: মোবাইল, বাইক, ব্যাংক লোন")
        FieldLabel("কোথায় / কাকে দেন (ঐচ্ছিক)")
        NField(lender, { lender = it }, "যেমন: ব্র্যাক, গ্রামীণ, দোকানের নাম")
        FieldLabel("প্রতি কিস্তি আর মোট কিস্তি")
        Row {
            Box(Modifier.weight(1f)) {
                NField(amount, { amount = it }, "৳ প্রতি কিস্তি", keyboardType = KeyboardType.Decimal)
            }
            Spacer(Modifier.width(14.r))
            Box(Modifier.weight(1f)) {
                NField(count, { if (it.length <= 3) count = it.filter { c -> c.isDigit() } }, "কয়টা কিস্তি", keyboardType = KeyboardType.Number)
            }
        }
        FieldLabel("কতদিন পরপর")
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            Pill("মাসিক", !weekly) { weekly = false }
            Pill("সাপ্তাহিক", weekly) { weekly = true }
        }
        FieldLabel("প্রথম কিস্তির তারিখ")
        DayPicker(first) { first = it }
        if (amt != null && amt > 0.0 && cnt != null && cnt in 1..600) {
            val preview = InstallmentEntity(title = "", amount = amt, count = cnt, weekly = weekly, firstEpochDay = first.toEpochDay())
            Spacer(Modifier.height(14.r))
            RText(
                "মোট ৳ ${Fmt.money(amt * cnt)} · শেষ কিস্তি ${Fmt.date(Kisti.dueDate(preview, cnt))}",
                19,
                FontWeight.SemiBold,
                C.Ink,
                maxLines = 2,
            )
        }
        if (existing == null) {
            FieldLabel("আগেই কয়টা দেওয়া হয়ে গেছে? (ঐচ্ছিক)")
            NField(paidBefore, { if (it.length <= 3) paidBefore = it.filter { c -> c.isDigit() } }, "যেমন: 3", keyboardType = KeyboardType.Number)
            Spacer(Modifier.height(6.r))
            RText(
                "চলতি কিস্তি যোগ করলে আগের দেওয়াগুলো এখানে লিখুন। এগুলো Money-তে খরচ হিসেবে যাবে না।",
                16,
                FontWeight.Normal,
                C.Hint,
                maxLines = 3,
                lineHeight = 23,
            )
        }
        Spacer(Modifier.height(14.r))
        ToggleLine(
            "💸 কিস্তি দিলে Money-তে খরচ হিসেবেও লিখুন",
            toMoney,
            sub = "Money-র “💳 ঋণ ও কিস্তি” ক্যাটাগরিতে যাবে",
        ) { toMoney = it }
        ToggleLine("🔔 কিস্তির দিন নোটিফিকেশন", remind) { remind = it }
        if (remind) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("সময়", 18, FontWeight.Medium, C.StatLabel)
                Spacer(Modifier.width(14.r))
                Pill(Fmt.minute(minute), true) { pickTime(context, minute) { minute = it } }
            }
        }
        SaveButton(valid) {
            if (valid && amt != null && cnt != null) {
                vm.saveInstallment(
                    existing,
                    InstallmentEntity(
                        title = title,
                        lender = lender,
                        emoji = emoji,
                        amount = amt,
                        count = cnt,
                        weekly = weekly,
                        firstEpochDay = first.toEpochDay(),
                        addToMoney = toMoney,
                        remind = remind,
                        remindMinute = minute,
                        note = existing?.note ?: "",
                    ),
                    paidBefore.toIntOrNull() ?: 0,
                )
                if (remind) onNeedNotifications()
                onDismiss()
            }
        }
    }
}

// ------------------------------------------------------------ কিস্তি দিলাম

@Composable
fun PayKistiDialog(
    s: KistiStatus,
    number: Int,
    onDismiss: () -> Unit,
    onPay: (number: Int, amount: Double, epochDay: Long, toMoney: Boolean) -> Unit,
) {
    val k = s.plan
    var amount by remember { mutableStateOf(plainAmount(k.amount)) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var toMoney by remember { mutableStateOf(k.addToMoney) }
    val value = parseAmount(amount)
    val ok = value != null && value > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { RText("${k.emoji}  কিস্তি $number/${k.count} দিলাম", 25, FontWeight.Bold, C.Ink) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RText(
                    "${k.title} · তারিখ ছিল ${Fmt.date(Kisti.dueDate(k, number))}",
                    18,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    maxLines = 2,
                )
                Spacer(Modifier.height(16.r))
                NField(amount, { amount = it }, "টাকার পরিমাণ (৳)", keyboardType = KeyboardType.Decimal)
                FieldLabel("কবে দিলেন")
                DayPicker(day) { day = it }
                Spacer(Modifier.height(12.r))
                ToggleLine("💸 Money-তে খরচ হিসেবেও লিখুন", toMoney, sub = "💳 ঋণ ও কিস্তি") { toMoney = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value != null && ok) {
                        onPay(number, value, day.toEpochDay(), toMoney)
                        onDismiss()
                    }
                },
                enabled = ok,
            ) {
                RText("সেভ করুন", 21, FontWeight.SemiBold, if (ok) Brand else C.Hint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { RText("বাতিল", 21, FontWeight.Medium, C.Hint) }
        },
    )
}

// ------------------------------------------------------------ person: name / phone, settle-up day

@Composable
fun PersonEditDialog(p: LedgerPersonEntity, onDismiss: () -> Unit, onSave: (name: String, phone: String) -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var phone by remember { mutableStateOf(p.phone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { RText("নাম ও নম্বর", 25, FontWeight.Bold, C.Ink) },
        text = {
            Column {
                NField(name, { name = it }, "নাম")
                Spacer(Modifier.height(14.r))
                NField(phone, { phone = it }, "মোবাইল নম্বর (ঐচ্ছিক)", keyboardType = KeyboardType.Phone)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(name, phone)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
            ) {
                RText("সেভ করুন", 21, FontWeight.SemiBold, if (name.isNotBlank()) Brand else C.Hint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { RText("বাতিল", 21, FontWeight.Medium, C.Hint) }
        },
    )
}

@Composable
fun DueDialog(p: LedgerPersonEntity, balance: Double, onDismiss: () -> Unit, onSave: (due: Long?, remind: Boolean) -> Unit) {
    var due by remember { mutableStateOf(p.dueEpochDay) }
    var remind by remember { mutableStateOf(p.remind || p.dueEpochDay == null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            RText(
                if (balance >= 0) "কবে ফেরত পাবেন?" else "কবে ফেরত দেবেন?",
                25,
                FontWeight.Bold,
                C.Ink,
            )
        },
        text = {
            Column {
                RText(balanceWords(p.name, balance), 19, FontWeight.Normal, C.StatLabelBn, maxLines = 2)
                Spacer(Modifier.height(16.r))
                DueChooser(due, LocalDate.now()) { due = it }
                if (due != null) {
                    Spacer(Modifier.height(10.r))
                    ToggleLine("🔔 ওই দিন সকাল 10টায় নোটিফিকেশন", remind) { remind = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(due, remind)
                onDismiss()
            }) {
                RText("সেভ করুন", 21, FontWeight.SemiBold, Brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { RText("বাতিল", 21, FontWeight.Medium, C.Hint) }
        },
    )
}
