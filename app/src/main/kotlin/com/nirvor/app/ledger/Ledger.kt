package com.nirvor.app.ledger

import com.nirvor.app.data.InstallmentEntity
import com.nirvor.app.data.InstallmentPaymentEntity
import com.nirvor.app.data.LedgerEntryEntity
import com.nirvor.app.data.LedgerPersonEntity
import java.time.LocalDate
import kotlin.math.abs

/** দেনা-পাওনা: one person and where the account with them stands. */
data class PersonBalance(
    val person: LedgerPersonEntity,
    /** > 0: they will pay me (পাওনা). < 0: I will pay them (দেনা). 0: settled. */
    val balance: Double,
    val entries: List<LedgerEntryEntity>,
    /** Which list the person belongs to when settled: the side of their first entry. */
    val startedAsLend: Boolean,
    val lastDay: Long,
) {
    val settled: Boolean get() = Ledger.isZero(balance)
    val owesMe: Boolean get() = !settled && balance > 0
    val iOwe: Boolean get() = !settled && balance < 0
}

object Ledger {
    fun isZero(v: Double): Boolean = abs(v) < 0.005

    /** Σ দিলাম − Σ পেলাম */
    fun balance(entries: List<LedgerEntryEntity>): Double =
        entries.sumOf { if (it.gave) it.amount else -it.amount }

    fun people(people: List<LedgerPersonEntity>, entries: List<LedgerEntryEntity>): List<PersonBalance> {
        val byPerson = entries.groupBy { it.personId }
        return people.map { p ->
            val list = byPerson[p.id].orEmpty().sortedWith(compareBy({ it.epochDay }, { it.createdAt }))
            PersonBalance(
                person = p,
                balance = balance(list),
                entries = list,
                startedAsLend = list.firstOrNull()?.gave ?: true,
                lastDay = list.lastOrNull()?.epochDay ?: (p.createdAt / 86_400_000L),
            )
        }
    }

    /** Days from [today] to [day]: negative when it is already past. */
    fun daysLeft(day: Long, today: Long): Long = day - today
}

/** Where an installment plan stands on [today]. */
data class KistiStatus(
    val plan: InstallmentEntity,
    val paid: Map<Int, InstallmentPaymentEntity>,
    val paidAmount: Double,
    val remainingAmount: Double,
    /** First unpaid installment, or null when all are paid. */
    val nextNumber: Int?,
    val nextDue: LocalDate?,
    /** Unpaid installments whose date has passed. */
    val overdue: Int,
    val lastDue: LocalDate,
) {
    val paidCount: Int get() = paid.size
    val done: Boolean get() = nextNumber == null
    val total: Double get() = plan.amount * plan.count
}

object Kisti {
    fun dueDate(k: InstallmentEntity, n: Int): LocalDate {
        val first = LocalDate.ofEpochDay(k.firstEpochDay)
        val steps = (n - 1).toLong()
        // plusMonths from the first date keeps the day (31st → 30th/28th only in short months).
        return if (k.weekly) first.plusWeeks(steps) else first.plusMonths(steps)
    }

    fun status(k: InstallmentEntity, payments: List<InstallmentPaymentEntity>, today: Long): KistiStatus {
        val paid = payments.filter { it.installmentId == k.id && it.number in 1..k.count }.associateBy { it.number }
        val next = (1..k.count).firstOrNull { it !in paid }
        val overdue = (1..k.count).count { it !in paid && dueDate(k, it).toEpochDay() < today }
        return KistiStatus(
            plan = k,
            paid = paid,
            paidAmount = paid.values.sumOf { it.amount },
            remainingAmount = (k.count - paid.size).coerceAtLeast(0) * k.amount,
            nextNumber = next,
            nextDue = next?.let { dueDate(k, it) },
            overdue = overdue,
            lastDue = dueDate(k, k.count.coerceAtLeast(1)),
        )
    }

    fun everyText(k: InstallmentEntity): String = if (k.weekly) "সাপ্তাহিক" else "মাসিক"
}
