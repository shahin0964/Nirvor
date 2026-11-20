package com.nirvor.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.nirvor.app.NirvorApplication
import com.nirvor.app.data.DhikrCustomEntity
import com.nirvor.app.data.DhikrLogEntity
import com.nirvor.app.data.ExpenseEntity
import com.nirvor.app.data.HabitEntity
import com.nirvor.app.data.HabitLogEntity
import com.nirvor.app.data.IncomeEntity
import com.nirvor.app.data.IncomeRepeatEntity
import com.nirvor.app.data.InstallmentEntity
import com.nirvor.app.data.InstallmentPaymentEntity
import com.nirvor.app.data.LedgerEntryEntity
import com.nirvor.app.data.LedgerPersonEntity
import com.nirvor.app.data.MoneyCategoryEntity
import com.nirvor.app.data.NoteEntity
import com.nirvor.app.data.PrayerLogEntity
import com.nirvor.app.data.QazaEntity
import com.nirvor.app.data.ReminderEntity
import com.nirvor.app.data.TaskEntity
import com.nirvor.app.widget.NirvorWidgetUpdater
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    private const val FOLDER_NAME = "Nirvor_Backup"

    fun getBackupDirectory(context: Context): File {
        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME)
        if (!publicDir.exists()) publicDir.mkdirs()
        if (publicDir.canWrite()) return publicDir
        val fallback = File(context.getExternalFilesDir(null), FOLDER_NAME)
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    suspend fun exportJson(context: Context): String {
        val app = context.applicationContext as NirvorApplication
        val db = app.db
        val prefs = app.prefs

        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "Nirvor")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("dateStr", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        root.put("userName", prefs.name)

        // 1. Tasks
        val tasks = db.taskDao().allOnce()
        val tasksArray = JSONArray()
        for (t in tasks) {
            val o = JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("epochDay", t.epochDay)
                if (t.minuteOfDay != null) put("minuteOfDay", t.minuteOfDay)
                put("done", t.done)
                put("createdAt", t.createdAt)
            }
            tasksArray.put(o)
        }
        root.put("tasks", tasksArray)

        // 2. Reminders
        val reminders = db.reminderDao().allOnce()
        val remindersArray = JSONArray()
        for (r in reminders) {
            val o = JSONObject().apply {
                put("id", r.id)
                put("title", r.title)
                put("epochDay", r.epochDay)
                put("minuteOfDay", r.minuteOfDay)
                put("repeatDaily", r.repeatDaily)
                put("icon", r.icon)
                put("createdAt", r.createdAt)
            }
            remindersArray.put(o)
        }
        root.put("reminders", remindersArray)

        // 3. Expenses
        val expenses = db.expenseDao().allOnce()
        val expensesArray = JSONArray()
        for (e in expenses) {
            val o = JSONObject().apply {
                put("id", e.id)
                put("amount", e.amount)
                put("category", e.category)
                put("note", e.note)
                put("epochDay", e.epochDay)
                put("createdAt", e.createdAt)
            }
            expensesArray.put(o)
        }
        root.put("expenses", expensesArray)

        // 4. Incomes
        val incomes = db.incomeDao().allOnce()
        val incomesArray = JSONArray()
        for (i in incomes) {
            val o = JSONObject().apply {
                put("id", i.id)
                put("amount", i.amount)
                put("category", i.category)
                put("note", i.note)
                put("epochDay", i.epochDay)
                put("createdAt", i.createdAt)
                if (i.repeatId != null) put("repeatId", i.repeatId)
            }
            incomesArray.put(o)
        }
        root.put("incomes", incomesArray)

        // 5. Income Repeats
        val repeats = db.incomeDao().repeatsOnce()
        val repeatsArray = JSONArray()
        for (r in repeats) {
            val o = JSONObject().apply {
                put("id", r.id)
                put("amount", r.amount)
                put("category", r.category)
                put("note", r.note)
                put("dayOfMonth", r.dayOfMonth)
                put("lastEpochDay", r.lastEpochDay)
                put("createdAt", r.createdAt)
            }
            repeatsArray.put(o)
        }
        root.put("income_repeats", repeatsArray)

        // 6. Money Categories
        val cats = db.moneyCategoryDao().allOnce()
        val catsArray = JSONArray()
        for (c in cats) {
            val o = JSONObject().apply {
                put("id", c.id)
                put("type", c.type)
                put("name", c.name)
                put("emoji", c.emoji)
                put("color", c.color)
                put("createdAt", c.createdAt)
            }
            catsArray.put(o)
        }
        root.put("money_categories", catsArray)

        // 7. Habits
        val habits = db.habitDao().habitsOnce()
        val habitsArray = JSONArray()
        for (h in habits) {
            val o = JSONObject().apply {
                put("id", h.id)
                put("name", h.name)
                put("icon", h.icon)
                put("createdAt", h.createdAt)
            }
            habitsArray.put(o)
        }
        root.put("habits", habitsArray)

        // 8. Habit Logs
        val habitLogs = db.habitDao().logsOnce()
        val habitLogsArray = JSONArray()
        for (l in habitLogs) {
            val o = JSONObject().apply {
                put("habitId", l.habitId)
                put("epochDay", l.epochDay)
            }
            habitLogsArray.put(o)
        }
        root.put("habit_logs", habitLogsArray)

        // 9. Notes
        val notes = db.noteDao().allOnce()
        val notesArray = JSONArray()
        for (n in notes) {
            val o = JSONObject().apply {
                put("id", n.id)
                put("text", n.text)
                put("createdAt", n.createdAt)
                put("updatedAt", n.updatedAt)
            }
            notesArray.put(o)
        }
        root.put("notes", notesArray)

        // 10. Ledger People
        val people = db.ledgerDao().peopleOnce()
        val peopleArray = JSONArray()
        for (p in people) {
            val o = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("phone", p.phone)
                if (p.dueEpochDay != null) put("dueEpochDay", p.dueEpochDay)
                put("remind", p.remind)
                put("createdAt", p.createdAt)
            }
            peopleArray.put(o)
        }
        root.put("ledger_people", peopleArray)

        // 11. Ledger Entries
        val ledgerEntries = db.ledgerDao().entriesOnce()
        val ledgerEntriesArray = JSONArray()
        for (e in ledgerEntries) {
            val o = JSONObject().apply {
                put("id", e.id)
                put("personId", e.personId)
                put("gave", e.gave)
                put("amount", e.amount)
                put("note", e.note)
                put("epochDay", e.epochDay)
                put("createdAt", e.createdAt)
            }
            ledgerEntriesArray.put(o)
        }
        root.put("ledger_entries", ledgerEntriesArray)

        // 12. Installments
        val installments = db.installmentDao().allOnce()
        val installmentsArray = JSONArray()
        for (ins in installments) {
            val o = JSONObject().apply {
                put("id", ins.id)
                put("title", ins.title)
                put("lender", ins.lender)
                put("emoji", ins.emoji)
                put("amount", ins.amount)
                put("count", ins.count)
                put("weekly", ins.weekly)
                put("firstEpochDay", ins.firstEpochDay)
                put("addToMoney", ins.addToMoney)
                put("remind", ins.remind)
                put("remindMinute", ins.remindMinute)
                put("note", ins.note)
                put("createdAt", ins.createdAt)
            }
            installmentsArray.put(o)
        }
        root.put("installments", installmentsArray)

        // 13. Installment Payments
        val payments = db.installmentDao().paymentsOnce()
        val paymentsArray = JSONArray()
        for (pay in payments) {
            val o = JSONObject().apply {
                put("id", pay.id)
                put("installmentId", pay.installmentId)
                put("number", pay.number)
                put("amount", pay.amount)
                put("epochDay", pay.epochDay)
                if (pay.expenseId != null) put("expenseId", pay.expenseId)
                put("createdAt", pay.createdAt)
            }
            paymentsArray.put(o)
        }
        root.put("installment_payments", paymentsArray)

        // 14. Prayer Log
        val prayerLogs = db.islamicDao().prayerLogsOnce()
        val prayerLogsArray = JSONArray()
        for (pl in prayerLogs) {
            val o = JSONObject().apply {
                put("epochDay", pl.epochDay)
                put("prayer", pl.prayer)
                put("status", pl.status)
                put("updatedAt", pl.updatedAt)
            }
            prayerLogsArray.put(o)
        }
        root.put("prayer_log", prayerLogsArray)

        // 15. Qaza
        val qazas = db.islamicDao().qazaOnce()
        val qazaArray = JSONArray()
        for (q in qazas) {
            val o = JSONObject().apply {
                put("prayer", q.prayer)
                put("pending", q.pending)
                put("done", q.done)
            }
            qazaArray.put(o)
        }
        root.put("qaza", qazaArray)

        // 16. Dhikr Log
        val dhikrLogs = db.islamicDao().dhikrLogsOnce()
        val dhikrLogsArray = JSONArray()
        for (dl in dhikrLogs) {
            val o = JSONObject().apply {
                put("epochDay", dl.epochDay)
                put("dhikr", dl.dhikr)
                put("count", dl.count)
            }
            dhikrLogsArray.put(o)
        }
        root.put("dhikr_log", dhikrLogsArray)

        // 17. Dhikr Custom
        val dhikrCustom = db.islamicDao().customDhikrOnce()
        val dhikrCustomArray = JSONArray()
        for (dc in dhikrCustom) {
            val o = JSONObject().apply {
                put("id", dc.id)
                put("name", dc.name)
                put("arabic", dc.arabic)
                put("target", dc.target)
                put("createdAt", dc.createdAt)
            }
            dhikrCustomArray.put(o)
        }
        root.put("dhikr_custom", dhikrCustomArray)

        return root.toString(2)
    }

    suspend fun saveOfflineBackup(context: Context, isAuto: Boolean = false): File {
        val json = exportJson(context)
        val dir = getBackupDirectory(context)
        val timeTag = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = if (isAuto) "nirvor_autobackup_$timeTag.json" else "nirvor_backup_$timeTag.json"
        val file = File(dir, filename)
        file.writeText(json, Charsets.UTF_8)

        val prefs = (context.applicationContext as NirvorApplication).prefs
        prefs.lastBackupTime = System.currentTimeMillis()

        if (isAuto) rotateAutoBackups(dir, keep = 7)
        return file
    }

    private fun rotateAutoBackups(dir: File, keep: Int) {
        val autos = dir.listFiles { f -> f.isFile && f.name.startsWith("nirvor_autobackup_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified()} ?: return
        if (autos.size > keep) {
            for (i in keep until autos.size) {
                autos[i].delete()
            }
        }
    }

    fun listLocalBackups(context: Context): List<File> {
        val dir = getBackupDirectory(context)
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    suspend fun restoreFromJson(context: Context, jsonString: String): Result<Int> {
        return try {
            val root = JSONObject(jsonString)
            val app = context.applicationContext as NirvorApplication
            val db = app.db

            var count = 0

            // 1. Tasks
            if (root.has("tasks")) {
                val arr = root.getJSONArray("tasks")
                val list = mutableListOf<TaskEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        TaskEntity(
                            id = o.optLong("id", 0L),
                            title = o.getString("title"),
                            epochDay = o.getLong("epochDay"),
                            minuteOfDay = if (o.has("minuteOfDay")) o.getInt("minuteOfDay") else null,
                            done = o.optBoolean("done", false),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                db.taskDao().clearAll()
                db.taskDao().insertAll(list)
                count += list.size
            }

            // 2. Reminders
            if (root.has("reminders")) {
                val arr = root.getJSONArray("reminders")
                val list = mutableListOf<ReminderEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        ReminderEntity(
                            id = o.optLong("id", 0L),
                            title = o.getString("title"),
                            epochDay = o.optLong("epochDay", 0L),
                            minuteOfDay = o.getInt("minuteOfDay"),
                            repeatDaily = o.optBoolean("repeatDaily", true),
                            icon = o.optString("icon", "bell"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                db.reminderDao().clearAll()
                db.reminderDao().insertAll(list)
                count += list.size
            }

            // 3. Expenses
            if (root.has("expenses")) {
                val arr = root.getJSONArray("expenses")
                val list = mutableListOf<ExpenseEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        ExpenseEntity(
                            id = o.optLong("id", 0L),
                            amount = o.getDouble("amount"),
                            category = o.getString("category"),
                            note = o.optString("note", ""),
                            epochDay = o.getLong("epochDay"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                db.expenseDao().clearAll()
                db.expenseDao().insertAll(list)
                count += list.size
            }

            // 4. Incomes
            if (root.has("incomes")) {
                val arr = root.getJSONArray("incomes")
                val list = mutableListOf<IncomeEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        IncomeEntity(
                            id = o.optLong("id", 0L),
                            amount = o.getDouble("amount"),
                            category = o.getString("category"),
                            note = o.optString("note", ""),
                            epochDay = o.getLong("epochDay"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            repeatId = if (o.has("repeatId")) o.getLong("repeatId") else null
                        )
                    )
                }
                db.incomeDao().clearAll()
                db.incomeDao().insertAll(list)
                count += list.size
            }

            // 5. Income Repeats
            if (root.has("income_repeats")) {
                val arr = root.getJSONArray("income_repeats")
                val list = mutableListOf<IncomeRepeatEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        IncomeRepeatEntity(
                            id = o.optLong("id", 0L),
                            amount = o.getDouble("amount"),
                            category = o.getString("category"),
                            note = o.optString("note", ""),
                            dayOfMonth = o.getInt("dayOfMonth"),
                            lastEpochDay = o.optLong("lastEpochDay", 0L),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                db.incomeDao().clearRepeats()
                db.incomeDao().insertAllRepeats(list)
                count += list.size
            }

            // 6. Money Categories
            if (root.has("money_categories")) {
                val arr = root.getJSONArray("money_categories")
                val list = mutableListOf<MoneyCategoryEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        MoneyCategoryEntity(
                            id = o.optLong("id", 0L),
                            type = o.getString("type"),
                            name = o.getString("name"),
                            emoji = o.getString("emoji"),
                            color = o.getLong("color"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                db.moneyCategoryDao().clearAll()
                db.moneyCategoryDao().insertAll(list)
                count += list.size
            }

            // 7. Habits
            if (root.has("habits")) {
                val arr = root.getJSONArray("habits")
                val list = mutableListOf<HabitEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        HabitEntity(
                            id = o.optLong("id", 0L),
                            name = o.getString("name"),
                            icon = o.optString("icon", o.optString("emoji", "star")),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                db.habitDao().clearHabits()
                db.habitDao().insertAllHabits(list)
                count += list.size
            }

            // 8. Habit Logs
            if (root.has("habit_logs")) {
                val arr = root.getJSONArray("habit_logs")
                val list = mutableListOf<HabitLogEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        HabitLogEntity(
                            habitId = o.getLong("habitId"),
                            epochDay = o.getLong("epochDay")
                        )
                    )
                }
                db.habitDao().clearLogs()
                db.habitDao().insertAllLogs(list)
                count += list.size
            }

            // 9. Notes
            if (root.has("notes")) {
                val arr = root.getJSONArray("notes")
                val list = mutableListOf<NoteEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        NoteEntity(
                            id = o.optLong("id", 0L),
                            text = o.getString("text"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
                db.noteDao().clearAll()
                db.noteDao().insertAll(list)
                count += list.size
            }

            // 10. Ledger People
            if (root.has("ledger_people")) {
                val arr = root.getJSONArray("ledger_people")
                val list = mutableListOf<LedgerPersonEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        LedgerPersonEntity(
                            id = o.optLong("id", 0L),
                            name = o.getString("name"),
                            phone = o.optString("phone", ""),
                            dueEpochDay = if (o.has("dueEpochDay")) o.getLong("dueEpochDay") else null,
                            remind = o.optBoolean("remind", true),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                db.ledgerDao().clearPeople()
                db.ledgerDao().insertAllPeople(list)
                count += list.size
            }

            // 11. Ledger Entries
            if (root.has("ledger_entries")) {
                val arr = root.getJSONArray("ledger_entries")
                val list = mutableListOf<LedgerEntryEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        LedgerEntryEntity(
                            id = o.optLong("id", 0L),
                            personId = o.getLong("personId"),
                            gave = o.getBoolean("gave"),
                            amount = o.getDouble("amount"),
                            note = o.optString("note", ""),
                            epochDay = o.getLong("epochDay"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                db.ledgerDao().clearEntries()
                db.ledgerDao().insertAllEntries(list)
                count += list.size
            }

            // 12. Installments
            if (root.has("installments")) {
                val arr = root.getJSONArray("installments")
                val list = mutableListOf<InstallmentEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        InstallmentEntity(
                            id = o.optLong("id", 0L),
                            title = o.getString("title"),
                            lender = o.getString("lender"),
                            emoji = o.getString("emoji"),
                            amount = o.getDouble("amount"),
                            count = o.getInt("count"),
                            weekly = o.getBoolean("weekly"),
                            firstEpochDay = o.getLong("firstEpochDay"),
                            addToMoney = o.getBoolean("addToMoney"),
                            remind = o.getBoolean("remind"),
                            remindMinute = o.getInt("remindMinute"),
                            note = o.optString("note", ""),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                db.installmentDao().clearInstallments()
                db.installmentDao().insertAllInstallments(list)
                count += list.size
            }

            // 13. Installment Payments
            if (root.has("installment_payments")) {
                val arr = root.getJSONArray("installment_payments")
                val list = mutableListOf<InstallmentPaymentEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        InstallmentPaymentEntity(
                            id = o.optLong("id", 0L),
                            installmentId = o.getLong("installmentId"),
                            number = o.getInt("number"),
                            amount = o.getDouble("amount"),
                            epochDay = o.getLong("epochDay"),
                            expenseId = if (o.has("expenseId")) o.getLong("expenseId") else null,
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                db.installmentDao().clearPayments()
                db.installmentDao().insertAllPayments(list)
                count += list.size
            }

            // 14. Prayer Log
            if (root.has("prayer_log")) {
                val arr = root.getJSONArray("prayer_log")
                val list = mutableListOf<PrayerLogEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        PrayerLogEntity(
                            epochDay = o.getLong("epochDay"),
                            prayer = o.getInt("prayer"),
                            status = o.getInt("status"),
                            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
                db.islamicDao().clearPrayerLogs()
                db.islamicDao().insertAllPrayerLogs(list)
                count += list.size
            }

            // 15. Qaza
            if (root.has("qaza")) {
                val arr = root.getJSONArray("qaza")
                val list = mutableListOf<QazaEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        QazaEntity(
                            prayer = o.getInt("prayer"),
                            pending = o.getInt("pending"),
                            done = o.getInt("done")
                        )
                    )
                }
                db.islamicDao().clearQaza()
                db.islamicDao().insertAllQaza(list)
                count += list.size
            }

            // 16. Dhikr Log
            if (root.has("dhikr_log")) {
                val arr = root.getJSONArray("dhikr_log")
                val list = mutableListOf<DhikrLogEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        DhikrLogEntity(
                            epochDay = o.getLong("epochDay"),
                            dhikr = o.getString("dhikr"),
                            count = o.getInt("count")
                        )
                    )
                }
                db.islamicDao().clearDhikrLogs()
                db.islamicDao().insertAllDhikrLogs(list)
                count += list.size
            }

            // 17. Dhikr Custom
            if (root.has("dhikr_custom")) {
                val arr = root.getJSONArray("dhikr_custom")
                val list = mutableListOf<DhikrCustomEntity>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        DhikrCustomEntity(
                            id = o.optLong("id", 0L),
                            name = o.getString("name"),
                            arabic = o.getString("arabic"),
                            target = o.getInt("target"),
                            createdAt = o.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
                db.islamicDao().clearCustomDhikr()
                db.islamicDao().insertAllCustomDhikr(list)
                count += list.size
            }

            NirvorWidgetUpdater.updateAll(context)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun writeJsonToUri(context: Context, uri: Uri, json: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun readJsonFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inp ->
                inp.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shareBackupFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "ব্যাকআপ ফাইল সংরক্ষণ বা শেয়ার করুন (Google Drive / Apps)")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
