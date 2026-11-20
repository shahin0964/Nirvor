package com.nirvor.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query(
        "SELECT * FROM tasks WHERE epochDay = :day " +
            "ORDER BY CASE WHEN minuteOfDay IS NULL THEN 1 ELSE 0 END, minuteOfDay, id"
    )
    fun forDay(day: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY epochDay DESC, id DESC")
    fun all(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY epochDay DESC, id DESC")
    suspend fun allOnce(): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<TaskEntity>)

    @Query("UPDATE tasks SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY minuteOfDay, id")
    fun all(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun allOnce(): List<ReminderEntity>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders")
    suspend fun clearAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE epochDay = :day ORDER BY createdAt DESC")
    fun forDay(day: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY epochDay DESC, createdAt DESC")
    fun all(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY epochDay DESC, createdAt DESC")
    suspend fun allOnce(): List<ExpenseEntity>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()

    /** Used when a category is deleted: its expenses move to another category. */
    @Query("UPDATE expenses SET category = :to WHERE category = :from")
    suspend fun moveCategory(from: String, to: String)
}

@Dao
interface IncomeDao {
    @Query("SELECT * FROM incomes ORDER BY epochDay DESC, createdAt DESC")
    fun all(): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes ORDER BY epochDay DESC, createdAt DESC")
    suspend fun allOnce(): List<IncomeEntity>

    @Insert
    suspend fun insert(income: IncomeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(incomes: List<IncomeEntity>)

    @Delete
    suspend fun delete(income: IncomeEntity)

    @Query("DELETE FROM incomes")
    suspend fun clearAll()

    @Query("UPDATE incomes SET category = :to WHERE category = :from")
    suspend fun moveCategory(from: String, to: String)

    @Query("SELECT * FROM income_repeats ORDER BY createdAt")
    fun repeats(): Flow<List<IncomeRepeatEntity>>

    @Query("SELECT * FROM income_repeats")
    suspend fun repeatsOnce(): List<IncomeRepeatEntity>

    @Insert
    suspend fun insertRepeat(repeat: IncomeRepeatEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRepeats(repeats: List<IncomeRepeatEntity>)

    @Query("UPDATE income_repeats SET lastEpochDay = :day WHERE id = :id")
    suspend fun setRepeatLast(id: Long, day: Long)

    @Query("DELETE FROM income_repeats WHERE id = :id")
    suspend fun deleteRepeat(id: Long)

    @Query("DELETE FROM income_repeats")
    suspend fun clearRepeats()

    /** Entries made by a rule that was stopped become ordinary entries. */
    @Query("UPDATE incomes SET repeatId = NULL WHERE repeatId = :id")
    suspend fun detachRepeat(id: Long)

    @Query("UPDATE income_repeats SET category = :to WHERE category = :from")
    suspend fun moveRepeatCategory(from: String, to: String)
}

@Dao
interface MoneyCategoryDao {
    @Query("SELECT * FROM money_categories ORDER BY createdAt, id")
    fun all(): Flow<List<MoneyCategoryEntity>>

    @Query("SELECT * FROM money_categories ORDER BY createdAt, id")
    suspend fun allOnce(): List<MoneyCategoryEntity>

    @Insert
    suspend fun insert(category: MoneyCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<MoneyCategoryEntity>)

    @Query("UPDATE money_categories SET name = :name, emoji = :emoji, color = :color WHERE id = :id")
    suspend fun update(id: Long, name: String, emoji: String, color: Long)

    @Query("DELETE FROM money_categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM money_categories")
    suspend fun clearAll()
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY createdAt, id")
    fun habits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY createdAt, id")
    suspend fun habitsOnce(): List<HabitEntity>

    @Query("SELECT * FROM habit_logs WHERE epochDay = :day")
    fun logsForDay(day: Long): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs")
    suspend fun logsOnce(): List<HabitLogEntity>

    @Query("SELECT DISTINCT epochDay FROM habit_logs ORDER BY epochDay DESC")
    fun logDays(): Flow<List<Long>>

    @Insert
    suspend fun insertHabit(habit: HabitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllHabits(habits: List<HabitEntity>)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("DELETE FROM habits")
    suspend fun clearHabits()

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogsFor(habitId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLog(log: HabitLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLogs(logs: List<HabitLogEntity>)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId AND epochDay = :day")
    suspend fun deleteLog(habitId: Long, day: Long)

    @Query("DELETE FROM habit_logs")
    suspend fun clearLogs()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun all(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun allOnce(): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes")
    suspend fun clearAll()
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_people ORDER BY createdAt, id")
    fun people(): Flow<List<LedgerPersonEntity>>

    @Query("SELECT * FROM ledger_entries ORDER BY epochDay DESC, createdAt DESC")
    fun entries(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_people")
    suspend fun peopleOnce(): List<LedgerPersonEntity>

    @Query("SELECT * FROM ledger_entries")
    suspend fun entriesOnce(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_people WHERE id = :id")
    suspend fun person(id: Long): LedgerPersonEntity?

    @Query("SELECT * FROM ledger_entries WHERE personId = :personId")
    suspend fun entriesFor(personId: Long): List<LedgerEntryEntity>

    @Insert
    suspend fun insertPerson(person: LedgerPersonEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPeople(people: List<LedgerPersonEntity>)

    @Update
    suspend fun updatePerson(person: LedgerPersonEntity)

    @Query("DELETE FROM ledger_people WHERE id = :id")
    suspend fun deletePerson(id: Long)

    @Query("DELETE FROM ledger_people")
    suspend fun clearPeople()

    @Query("DELETE FROM ledger_entries WHERE personId = :personId")
    suspend fun deleteEntriesFor(personId: Long)

    @Insert
    suspend fun insertEntry(entry: LedgerEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllEntries(entries: List<LedgerEntryEntity>)

    @Delete
    suspend fun deleteEntry(entry: LedgerEntryEntity)

    @Query("DELETE FROM ledger_entries")
    suspend fun clearEntries()
}

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installments ORDER BY createdAt, id")
    fun all(): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installment_payments ORDER BY number")
    fun payments(): Flow<List<InstallmentPaymentEntity>>

    @Query("SELECT * FROM installments")
    suspend fun allOnce(): List<InstallmentEntity>

    @Query("SELECT * FROM installment_payments")
    suspend fun paymentsOnce(): List<InstallmentPaymentEntity>

    @Query("SELECT * FROM installments WHERE id = :id")
    suspend fun byId(id: Long): InstallmentEntity?

    @Query("SELECT * FROM installment_payments WHERE installmentId = :id")
    suspend fun paymentsFor(id: Long): List<InstallmentPaymentEntity>

    @Insert
    suspend fun insert(installment: InstallmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInstallments(installments: List<InstallmentEntity>)

    @Update
    suspend fun update(installment: InstallmentEntity)

    @Query("DELETE FROM installments WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM installments")
    suspend fun clearInstallments()

    @Query("DELETE FROM installment_payments WHERE installmentId = :id")
    suspend fun deletePaymentsFor(id: Long)

    @Query("DELETE FROM installment_payments")
    suspend fun clearPayments()

    /** Payments past a shortened plan's last number. */
    @Query("DELETE FROM installment_payments WHERE installmentId = :id AND number > :count")
    suspend fun deletePaymentsAfter(id: Long, count: Int)

    @Insert
    suspend fun insertPayment(payment: InstallmentPaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPayments(payments: List<InstallmentPaymentEntity>)

    @Delete
    suspend fun deletePayment(payment: InstallmentPaymentEntity)
}

@Dao
interface IslamicDao {
    @Query("SELECT * FROM prayer_log WHERE epochDay >= :from ORDER BY epochDay")
    fun prayerLogsFrom(from: Long): Flow<List<PrayerLogEntity>>

    @Query("SELECT * FROM prayer_log")
    suspend fun prayerLogsOnce(): List<PrayerLogEntity>

    @Query("SELECT * FROM prayer_log WHERE epochDay = :day AND prayer = :prayer")
    suspend fun prayerLog(day: Long, prayer: Int): PrayerLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPrayerLog(log: PrayerLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPrayerLogs(logs: List<PrayerLogEntity>)

    @Query("DELETE FROM prayer_log WHERE epochDay = :day AND prayer = :prayer")
    suspend fun deletePrayerLog(day: Long, prayer: Int)

    @Query("DELETE FROM prayer_log")
    suspend fun clearPrayerLogs()

    @Query("SELECT * FROM qaza ORDER BY prayer")
    fun qaza(): Flow<List<QazaEntity>>

    @Query("SELECT * FROM qaza")
    suspend fun qazaOnce(): List<QazaEntity>

    @Query("SELECT * FROM qaza WHERE prayer = :prayer")
    suspend fun qazaOf(prayer: Int): QazaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putQaza(q: QazaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllQaza(qazas: List<QazaEntity>)

    @Query("DELETE FROM qaza")
    suspend fun clearQaza()

    @Query("SELECT * FROM dhikr_log WHERE epochDay >= :from")
    fun dhikrLogsFrom(from: Long): Flow<List<DhikrLogEntity>>

    @Query("SELECT * FROM dhikr_log")
    suspend fun dhikrLogsOnce(): List<DhikrLogEntity>

    @Query("SELECT * FROM dhikr_log WHERE epochDay = :day AND dhikr = :dhikr")
    suspend fun dhikrLog(day: Long, dhikr: String): DhikrLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDhikrLog(log: DhikrLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDhikrLogs(logs: List<DhikrLogEntity>)

    @Query("DELETE FROM dhikr_log")
    suspend fun clearDhikrLogs()

    @Query("SELECT * FROM dhikr_custom ORDER BY createdAt")
    fun customDhikr(): Flow<List<DhikrCustomEntity>>

    @Query("SELECT * FROM dhikr_custom")
    suspend fun customDhikrOnce(): List<DhikrCustomEntity>

    @Insert
    suspend fun insertCustomDhikr(d: DhikrCustomEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCustomDhikr(dhikrs: List<DhikrCustomEntity>)

    @Query("DELETE FROM dhikr_custom WHERE id = :id")
    suspend fun deleteCustomDhikr(id: Long)

    @Query("DELETE FROM dhikr_custom")
    suspend fun clearCustomDhikr()
}
