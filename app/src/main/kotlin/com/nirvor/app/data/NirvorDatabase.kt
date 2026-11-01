package com.nirvor.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        ReminderEntity::class,
        ExpenseEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        NoteEntity::class,
        IncomeEntity::class,
        IncomeRepeatEntity::class,
        MoneyCategoryEntity::class,
        LedgerPersonEntity::class,
        LedgerEntryEntity::class,
        InstallmentEntity::class,
        InstallmentPaymentEntity::class,
        PrayerLogEntity::class,
        QazaEntity::class,
        DhikrLogEntity::class,
        DhikrCustomEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class NirvorDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun moneyCategoryDao(): MoneyCategoryDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun installmentDao(): InstallmentDao
    abstract fun habitDao(): HabitDao
    abstract fun noteDao(): NoteDao
    abstract fun islamicDao(): IslamicDao

    companion object {
        /** v1.6: income (salary etc.), monthly income rules and the user's own categories. Keeps all old data. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `incomes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`amount` REAL NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`repeatId` INTEGER)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `income_repeats` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`amount` REAL NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`dayOfMonth` INTEGER NOT NULL, " +
                        "`lastEpochDay` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `money_categories` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v1.7: দেনা-পাওনা (people + entries) and কিস্তি (plans + payments). Keeps all old data. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ledger_people` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`phone` TEXT NOT NULL, " +
                        "`dueEpochDay` INTEGER, " +
                        "`remind` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ledger_entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`personId` INTEGER NOT NULL, " +
                        "`gave` INTEGER NOT NULL, " +
                        "`amount` REAL NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `installments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`lender` TEXT NOT NULL, " +
                        "`emoji` TEXT NOT NULL, " +
                        "`amount` REAL NOT NULL, " +
                        "`count` INTEGER NOT NULL, " +
                        "`weekly` INTEGER NOT NULL, " +
                        "`firstEpochDay` INTEGER NOT NULL, " +
                        "`addToMoney` INTEGER NOT NULL, " +
                        "`remind` INTEGER NOT NULL, " +
                        "`remindMinute` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `installment_payments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`installmentId` INTEGER NOT NULL, " +
                        "`number` INTEGER NOT NULL, " +
                        "`amount` REAL NOT NULL, " +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`expenseId` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        /** v2.1: Islamic Tools — prayer tracker, qaza counts, tasbih counts and own dhikr. Keeps all old data. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prayer_log` (" +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`prayer` INTEGER NOT NULL, " +
                        "`status` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`epochDay`, `prayer`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `qaza` (" +
                        "`prayer` INTEGER NOT NULL, " +
                        "`pending` INTEGER NOT NULL, " +
                        "`done` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`prayer`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `dhikr_log` (" +
                        "`epochDay` INTEGER NOT NULL, " +
                        "`dhikr` TEXT NOT NULL, " +
                        "`count` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`epochDay`, `dhikr`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `dhikr_custom` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`arabic` TEXT NOT NULL, " +
                        "`target` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun build(context: Context): NirvorDatabase =
            Room.databaseBuilder(context, NirvorDatabase::class.java, "nirvor.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
