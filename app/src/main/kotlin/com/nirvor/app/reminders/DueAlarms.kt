package com.nirvor.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nirvor.app.NirvorApplication
import com.nirvor.app.data.InstallmentEntity
import com.nirvor.app.data.InstallmentPaymentEntity
import com.nirvor.app.data.LedgerEntryEntity
import com.nirvor.app.data.LedgerPersonEntity
import com.nirvor.app.data.NirvorDatabase
import com.nirvor.app.ledger.Kisti
import com.nirvor.app.ledger.Ledger
import com.nirvor.app.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Notifications on the day a কিস্তি is due, and on the day money should come back / be paid back
 * (দেনা-পাওনা). One alarm per plan / person; when it rings the next one is booked.
 */
object DueAlarms {
    private const val KIND_KISTI = "kisti"
    private const val KIND_PERSON = "person"
    const val EXTRA_KIND = "due_kind"
    const val EXTRA_ID = "due_id"
    const val EXTRA_TITLE = "due_title"
    const val EXTRA_TEXT = "due_text"

    // Request codes / notification ids, kept apart from normal reminders (which use their row id).
    private const val KISTI_BASE = 1_000_000
    private const val PERSON_BASE = 2_000_000

    /** Person reminders ring at 10:00 AM on the due day. */
    private val PERSON_TIME: LocalTime = LocalTime.of(10, 0)

    private fun code(kind: String, id: Long): Int =
        (if (kind == KIND_KISTI) KISTI_BASE else PERSON_BASE) + (id % 900_000).toInt()

    private fun intent(context: Context, kind: String, id: Long): Intent =
        Intent(context, DueReceiver::class.java).apply {
            // A distinct data URI keeps every plan's / person's alarm separate.
            data = android.net.Uri.parse("nirvor://due/$kind/$id")
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_ID, id)
        }

    private fun set(context: Context, kind: String, id: Long, at: LocalDateTime, title: String, text: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val i = intent(context, kind, id).apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            code(kind, id),
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (ReminderScheduler.canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        }
    }

    private fun cancel(context: Context, kind: String, id: Long) {
        val pi = PendingIntent.getBroadcast(
            context,
            code(kind, id),
            intent(context, kind, id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pi != null) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pi)
            pi.cancel()
        }
    }

    fun cancelKisti(context: Context, id: Long) = cancel(context, KIND_KISTI, id)

    fun cancelPerson(context: Context, id: Long) = cancel(context, KIND_PERSON, id)

    /** Books the alarm for the next unpaid installment that is still ahead. */
    fun scheduleKisti(context: Context, k: InstallmentEntity, payments: List<InstallmentPaymentEntity>) {
        cancelKisti(context, k.id)
        if (!k.remind) return
        val now = LocalDateTime.now()
        val time = LocalTime.of(k.remindMinute / 60, k.remindMinute % 60)
        val paid = payments.filter { it.installmentId == k.id }.map { it.number }.toSet()
        val n = (1..k.count).firstOrNull { it !in paid && Kisti.dueDate(k, it).atTime(time).isAfter(now) } ?: return
        val at = Kisti.dueDate(k, n).atTime(time)
        val where = if (k.lender.isNotBlank()) " · ${k.lender}" else ""
        set(
            context,
            KIND_KISTI,
            k.id,
            at,
            "${k.emoji} আজ কিস্তির দিন: ${k.title}",
            "কিস্তি $n/${k.count} · ৳ ${Fmt.money(k.amount)}$where",
        )
    }

    /** Books the alarm for a person's settle-up day while money is still open with them. */
    fun schedulePerson(context: Context, p: LedgerPersonEntity, entries: List<LedgerEntryEntity>) {
        cancelPerson(context, p.id)
        val due = p.dueEpochDay ?: return
        if (!p.remind) return
        val balance = Ledger.balance(entries.filter { it.personId == p.id })
        if (Ledger.isZero(balance)) return
        val at = LocalDate.ofEpochDay(due).atTime(PERSON_TIME)
        if (!at.isAfter(LocalDateTime.now())) return
        val amount = "৳ " + Fmt.money(abs(balance))
        val title = if (balance > 0) "আজ ${p.name} টাকা ফেরত দেওয়ার কথা" else "আজ ${p.name}-কে টাকা ফেরত দেওয়ার দিন"
        val text = if (balance > 0) "${p.name}-এর কাছে আপনার পাওনা $amount" else "${p.name}-কে আপনার দিতে হবে $amount"
        set(context, KIND_PERSON, p.id, at, "🤝 $title", text)
    }

    suspend fun rescheduleAll(context: Context, db: NirvorDatabase) {
        val payments = db.installmentDao().paymentsOnce()
        db.installmentDao().allOnce().forEach { scheduleKisti(context, it, payments) }
        val entries = db.ledgerDao().entriesOnce()
        db.ledgerDao().peopleOnce().forEach { schedulePerson(context, it, entries) }
    }

    internal fun notificationId(kind: String, id: Long): Int = code(kind, id)
}

/** Shows the due-day notification, then books the next one for that plan / person. */
class DueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(DueAlarms.EXTRA_KIND) ?: return
        val id = intent.getLongExtra(DueAlarms.EXTRA_ID, -1L)
        if (id < 0) return
        val title = intent.getStringExtra(DueAlarms.EXTRA_TITLE) ?: return
        val text = intent.getStringExtra(DueAlarms.EXTRA_TEXT) ?: ""
        Notifications.showText(context, DueAlarms.notificationId(kind, id), title, text)

        val pending = goAsync()
        val app = context.applicationContext as NirvorApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DueAlarms.rescheduleAll(context, app.db)
            } finally {
                pending.finish()
            }
        }
    }
}
