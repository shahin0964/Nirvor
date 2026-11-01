package com.nirvor.app.ui.ledger

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.data.LedgerEntryEntity
import com.nirvor.app.ledger.Ledger
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.SectionLabel
import com.nirvor.app.ui.TrashButton
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.util.Fmt
import kotlin.math.abs

/** One person's account: where it stands, settle-up day, দিলাম / পেলাম and the full history. */
@Composable
fun LedgerPersonScreen(
    vm: MainViewModel,
    bottomPadding: Dp,
    personId: Long,
    onBack: () -> Unit,
    onAdd: (gave: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val people by vm.ledgerPeople.collectAsStateWithLifecycle()
    val entries by vm.ledgerEntries.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var dueEdit by remember { mutableStateOf(false) }
    var settling by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<LedgerEntryEntity?>(null) }

    val person = people.firstOrNull { it.id == personId }
    if (person == null) {
        PageScaffold("Lend & Borrow", "দেনা-পাওনা", onBack = onBack, bottomPadding = bottomPadding) {
            item { EmptyText("এই হিসাবটা আর নেই।") }
        }
        return
    }

    val list = entries.filter { it.personId == person.id }.sortedWith(compareBy({ it.epochDay }, { it.createdAt }))
    val balance = Ledger.balance(list)
    val settled = Ledger.isZero(balance)
    // Balance right after each entry, newest first.
    var runningBalance = 0.0
    val history = list.map { e ->
        runningBalance += if (e.gave) e.amount else -e.amount
        e to runningBalance
    }.reversed()
    val name = person.name

    PageScaffold(
        name,
        person.phone.ifBlank { "দেনা-পাওনার হিসাব" },
        onBack = onBack,
        bottomPadding = bottomPadding,
        actions = {
            if (person.phone.isNotBlank()) {
                RoundAction(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + person.phone)))
                    } catch (e: Exception) {
                        // No phone app.
                    }
                }) {
                    RText("📞", 24, FontWeight.Normal, C.Ink, align = TextAlign.Center)
                }
                Spacer(Modifier.width(10.r))
            }
            RoundAction(onClick = { editing = true }) {
                GlyphIcon(Glyph.Edit, C.Ink, Modifier.size(28.r), strokeWidth = 2f)
            }
        },
    ) {
        item(key = "hero") {
            HeroCard(
                when {
                    settled -> HeroSlate
                    balance > 0 -> HeroGreen
                    else -> HeroRed
                }
            ) {
                val soft = Color.White.copy(alpha = 0.88f)
                RText(
                    when {
                        settled -> "হিসাব মিটে গেছে"
                        balance > 0 -> "$name আপনাকে দেবে"
                        else -> "আপনি $name-কে দেবেন"
                    },
                    19,
                    FontWeight.Medium,
                    soft,
                )
                Spacer(Modifier.height(6.r))
                RText(if (settled) "✅ ৳ 0" else "৳ " + Fmt.money(abs(balance)), 46, FontWeight.Bold, Color.White)
                Spacer(Modifier.height(18.r))
                Row(Modifier.fillMaxWidth()) {
                    HeroStat("💸 মোট দিলাম", "৳ " + Fmt.money(list.filter { it.gave }.sumOf { it.amount }), Modifier.weight(1f))
                    Spacer(Modifier.width(14.r))
                    HeroStat("💰 মোট পেলাম", "৳ " + Fmt.money(list.filter { !it.gave }.sumOf { it.amount }), Modifier.weight(1f))
                }
                val due = person.dueEpochDay
                if (!settled || due != null) {
                    Spacer(Modifier.height(16.r))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.r))
                            .background(Color.White.copy(alpha = 0.18f))
                            .clickable { dueEdit = true }
                            .padding(horizontal = 18.r, vertical = 14.r),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RText("📅", 21, FontWeight.Normal, Color.White)
                        Spacer(Modifier.width(12.r))
                        val text = if (due != null) {
                            "ফেরতের তারিখ ${Fmt.date(due)} · ${dueWords(due, today).first}" + if (person.remind) "  🔔" else ""
                        } else {
                            "ফেরতের তারিখ ঠিক করুন"
                        }
                        RText(text, 18, FontWeight.Medium, Color.White, Modifier.weight(1f), maxLines = 2)
                        RText("বদলান", 16, FontWeight.SemiBold, Color.White)
                    }
                }
            }
        }

        item(key = "buttons") {
            Row(Modifier.fillMaxWidth()) {
                ButtonCard("💸", "দিলাম", LRed, Modifier.weight(1f)) { onAdd(true) }
                Spacer(Modifier.width(14.r))
                ButtonCard("💰", "পেলাম", LGreen, Modifier.weight(1f)) { onAdd(false) }
            }
        }

        if (!settled) {
            item(key = "settle") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.r))
                        .background(Color(0xFFE3F6EC))
                        .clickable { settling = true }
                        .padding(horizontal = 24.r, vertical = 16.r),
                ) {
                    RText(
                        "✅  পুরো ৳ ${Fmt.money(abs(balance))} " + if (balance > 0) "ফেরত পেয়েছি" else "ফেরত দিয়েছি",
                        20,
                        FontWeight.SemiBold,
                        Color(0xFF0A8F56),
                    )
                    Spacer(Modifier.height(3.r))
                    RText("এক চাপে হিসাব মিটিয়ে দিন", 16, FontWeight.Normal, Color(0xFF3F7E62))
                }
            }
        }

        item(key = "h") { SectionLabel("লেনদেন (${list.size})") }
        if (history.isEmpty()) {
            item(key = "empty") { EmptyText("এখনো কোনো লেনদেন নেই।") }
        }
        items(history, key = { it.first.id }) { (e, after) ->
            EntryRow(e, after) { removing = e }
        }
        item(key = "delete") {
            RText(
                "🗑  $name-এর পুরো হিসাব মুছে ফেলুন",
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

    if (editing) {
        PersonEditDialog(person, onDismiss = { editing = false }) { n, phone ->
            vm.updatePerson(person.copy(name = n, phone = phone))
        }
    }
    if (dueEdit) {
        DueDialog(person, balance, onDismiss = { dueEdit = false }) { due, remind ->
            vm.updatePerson(person.copy(dueEpochDay = due, remind = remind))
        }
    }
    if (settling) {
        val owesMe = balance > 0
        ConfirmDialog(
            title = if (owesMe) "পুরো টাকা ফেরত পেয়েছেন?" else "পুরো টাকা ফেরত দিয়েছেন?",
            text = "আজকের তারিখে ৳ ${Fmt.money(abs(balance))} " +
                (if (owesMe) "“পেলাম”" else "“দিলাম”") + " লেখা হবে, তারপর $name-এর হিসাব মিটে যাবে।",
            confirm = "হ্যাঁ, মিটিয়ে দিন",
            danger = false,
            onDismiss = { settling = false },
        ) {
            vm.addLedgerEntry(
                personId = person.id,
                newName = "",
                phone = "",
                gave = !owesMe,
                amount = abs(balance),
                note = "পুরো হিসাব মিটিয়ে দেওয়া হলো",
                epochDay = today,
                due = null,
                setDue = true,
                remind = false,
            )
        }
    }
    removing?.let { e ->
        ConfirmDialog(
            title = "এই লেনদেন মুছবেন?",
            text = (if (e.gave) "দিলাম" else "পেলাম") + " ৳ ${Fmt.money(e.amount)} · ${Fmt.date(e.epochDay)}",
            confirm = "মুছে ফেলুন",
            danger = true,
            onDismiss = { removing = null },
        ) {
            vm.deleteLedgerEntry(e)
        }
    }
    if (deleting) {
        ConfirmDialog(
            title = "$name-এর হিসাব মুছবেন?",
            text = "সব লেনদেনসহ পুরো হিসাব মুছে যাবে। এটা আর ফেরানো যাবে না।",
            confirm = "মুছে ফেলুন",
            danger = true,
            onDismiss = { deleting = false },
        ) {
            vm.deletePerson(person)
            onBack()
        }
    }
}

@Composable
private fun EntryRow(e: LedgerEntryEntity, after: Double, onDelete: () -> Unit) {
    ListCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EmojiBubble(if (e.gave) "💸" else "💰", if (e.gave) LRed else LGreen, 56)
            Spacer(Modifier.width(16.r))
            Column(Modifier.weight(1f)) {
                RText(if (e.gave) "দিলাম" else "পেলাম", 21, FontWeight.SemiBold, C.Ink)
                Spacer(Modifier.height(3.r))
                val sub = listOf(e.note, Fmt.date(e.epochDay)).filter { it.isNotBlank() }.joinToString(" · ")
                RText(sub, 17, FontWeight.Normal, C.Time, maxLines = 2)
                Spacer(Modifier.height(3.r))
                RText(
                    when {
                        Ledger.isZero(after) -> "এরপর হিসাব মিটে গেল"
                        after > 0 -> "এরপর পাবো ৳ " + Fmt.money(after)
                        else -> "এরপর দেবো ৳ " + Fmt.money(-after)
                    },
                    15,
                    FontWeight.Medium,
                    C.Hint,
                )
            }
            Spacer(Modifier.width(10.r))
            RText("৳ " + Fmt.money(e.amount), 21, FontWeight.Bold, if (e.gave) LRed else LGreen)
            TrashButton(onDelete)
        }
    }
}
