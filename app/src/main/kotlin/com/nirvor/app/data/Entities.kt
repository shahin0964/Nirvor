package com.nirvor.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A to-do item for a specific day. [minuteOfDay] is null when the task has no time. */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val epochDay: Long,
    val minuteOfDay: Int? = null,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A reminder that fires once on [epochDay] or every day when [repeatDaily] is true. */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val epochDay: Long,
    val minuteOfDay: Int,
    val repeatDaily: Boolean,
    val icon: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One expense. [category] is a built-in key from MoneyCategories (e.g. "food") or
 * "c<id>" for a category the user made.
 */
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String,
    val note: String = "",
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Money that came in (salary, business, ...). [repeatId] points to the monthly rule
 * that added this entry automatically, or is null for an entry added by hand.
 */
@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String,
    val note: String = "",
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val repeatId: Long? = null,
)

/**
 * "Add this income every month" (e.g. salary). A new [IncomeEntity] is written for every month
 * on [dayOfMonth] (or the month's last day when it is shorter); [lastEpochDay] is the newest
 * day already written.
 */
@Entity(tableName = "income_repeats")
data class IncomeRepeatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val category: String,
    val note: String = "",
    val dayOfMonth: Int,
    val lastEpochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A category the user made. [type] is "expense" or "income"; [color] is ARGB. */
@Entity(tableName = "money_categories")
data class MoneyCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val name: String,
    val emoji: String,
    val color: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A row here means the habit was completed on that day. */
@Entity(tableName = "habit_logs", primaryKeys = ["habitId", "epochDay"])
data class HabitLogEntity(
    val habitId: Long,
    val epochDay: Long,
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

// ---------------------------------------------------------------- দেনা-পাওনা (v1.7)

/** A person the user lends to or borrows from. [dueEpochDay]: when the money should be settled. */
@Entity(tableName = "ledger_people")
data class LedgerPersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String = "",
    val dueEpochDay: Long? = null,
    val remind: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One money movement with a person. [gave] = true: "আমি দিলাম" (they owe me more / I paid back),
 * false: "আমি পেলাম" (I owe them more / they paid back). Balance = Σ দিলাম − Σ পেলাম.
 */
@Entity(tableName = "ledger_entries")
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val gave: Boolean,
    val amount: Double,
    val note: String = "",
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * An installment plan (কিস্তি): [count] payments of [amount], monthly (or [weekly]) from
 * [firstEpochDay]. [addToMoney]: each payment is also written as a Money expense.
 */
@Entity(tableName = "installments")
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val lender: String = "",
    val emoji: String = "💳",
    val amount: Double,
    val count: Int,
    val weekly: Boolean = false,
    val firstEpochDay: Long,
    val addToMoney: Boolean = true,
    val remind: Boolean = true,
    val remindMinute: Int = 600,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Installment number [number] (1-based) was paid. [expenseId]: the Money expense written for it. */
@Entity(tableName = "installment_payments")
data class InstallmentPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val installmentId: Long,
    val number: Int,
    val amount: Double,
    val epochDay: Long,
    val expenseId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Islamic Tools: what happened to one prayer on one day. [prayer] is 0 Fajr … 4 Isha;
 * [status] 1 = prayed, 2 = prayed in jama'ah, 3 = missed (counted as qaza).
 */
@Entity(tableName = "prayer_log", primaryKeys = ["epochDay", "prayer"])
data class PrayerLogEntity(
    val epochDay: Long,
    val prayer: Int,
    val status: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Qaza (missed prayers to make up): [prayer] 0 Fajr … 4 Isha, 5 Witr; [pending] still owed, [done] made up. */
@Entity(tableName = "qaza")
data class QazaEntity(
    @PrimaryKey val prayer: Int,
    val pending: Int = 0,
    val done: Int = 0,
)

/** Digital tasbih: how many times [dhikr] (a preset key or "c<id>") was counted on a day. */
@Entity(tableName = "dhikr_log", primaryKeys = ["epochDay", "dhikr"])
data class DhikrLogEntity(
    val epochDay: Long,
    val dhikr: String,
    val count: Int,
)

/** A dhikr the user added to the tasbih. */
@Entity(tableName = "dhikr_custom")
data class DhikrCustomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val arabic: String = "",
    val target: Int = 33,
    val createdAt: Long = System.currentTimeMillis(),
)
