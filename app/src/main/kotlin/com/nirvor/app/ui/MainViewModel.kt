package com.nirvor.app.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.nirvor.app.NirvorApplication
import com.nirvor.app.data.ExpenseEntity
import com.nirvor.app.data.HabitEntity
import com.nirvor.app.data.HabitLogEntity
import com.nirvor.app.data.IncomeEntity
import com.nirvor.app.data.InstallmentEntity
import com.nirvor.app.data.InstallmentPaymentEntity
import com.nirvor.app.data.LedgerEntryEntity
import com.nirvor.app.data.LedgerPersonEntity
import com.nirvor.app.data.IncomeRepeatEntity
import com.nirvor.app.data.MoneyCategoryEntity
import com.nirvor.app.data.NoteEntity
import com.nirvor.app.data.ReminderEntity
import com.nirvor.app.data.SavedPlace
import com.nirvor.app.data.TaskEntity
import com.nirvor.app.money.MoneyCategories
import com.nirvor.app.money.MoneyCategory
import com.nirvor.app.money.MoneyType
import com.nirvor.app.money.toCategory
import com.nirvor.app.ledger.Kisti
import com.nirvor.app.reminders.DueAlarms
import com.nirvor.app.reminders.ReminderScheduler
import com.nirvor.app.ui.home.sky.SceneWeather
import com.nirvor.app.ui.home.sky.SkyCond
import com.nirvor.app.ui.home.sky.SkyInputs
import com.nirvor.app.weather.Place
import com.nirvor.app.weather.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.math.roundToLong

data class UpcomingReminder(val reminder: ReminderEntity, val at: LocalDateTime)

/** Why the weather cell cannot show real weather yet (decides what a tap on it does). */
enum class WeatherIssue { None, NoPermission, LocationOff, NoLocation, NoInternet }

data class WeatherUi(val temp: String, val desc: String, val issue: WeatherIssue = WeatherIssue.None)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NirvorApplication
    private val db = app.db
    private val prefs = app.prefs

    /** Serialises writes of monthly incomes so one month is never added twice. */
    private val repeatLock = Mutex()

    /** Ticks so that the greeting, "today" and upcoming reminders stay current. */
    val now: StateFlow<LocalDateTime> = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(15_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDateTime.now())

    val today: StateFlow<Long> = now
        .map { it.toLocalDate().toEpochDay() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now().toEpochDay())

    val todayTasks: StateFlow<List<TaskEntity>> = today
        .flatMapLatest { db.taskDao().forDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTasks: StateFlow<List<TaskEntity>> = db.taskDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = db.reminderDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val upcoming: StateFlow<List<UpcomingReminder>> = combine(reminders, now) { list, n ->
        list.mapNotNull { r -> ReminderScheduler.nextTrigger(r, n)?.let { UpcomingReminder(r, it) } }
            .sortedBy { it.at }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayExpenses: StateFlow<List<ExpenseEntity>> = today
        .flatMapLatest { db.expenseDao().forDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allExpenses: StateFlow<List<ExpenseEntity>> = db.expenseDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allIncomes: StateFlow<List<IncomeEntity>> = db.incomeDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Incomes added automatically every month (salary etc.). */
    val incomeRepeats: StateFlow<List<IncomeRepeatEntity>> = db.incomeDao().repeats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Categories the user made (both expense and income). */
    val customCategories: StateFlow<List<MoneyCategory>> = db.moneyCategoryDao().all()
        .map { list -> list.map { it.toCategory() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // দেনা-পাওনা and কিস্তি
    val ledgerPeople: StateFlow<List<LedgerPersonEntity>> = db.ledgerDao().people()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ledgerEntries: StateFlow<List<LedgerEntryEntity>> = db.ledgerDao().entries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val installments: StateFlow<List<InstallmentEntity>> = db.installmentDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val installmentPayments: StateFlow<List<InstallmentPaymentEntity>> = db.installmentDao().payments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val habits: StateFlow<List<HabitEntity>> = db.habitDao().habits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val doneHabitIds: StateFlow<Set<Long>> = today
        .flatMapLatest { db.habitDao().logsForDay(it) }
        .map { logs -> logs.map { it.habitId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Days in a row (ending today or yesterday) on which at least one habit was done. */
    val streak: StateFlow<Int> = combine(db.habitDao().logDays(), today) { days, t ->
        val set = days.toHashSet()
        var d = if (t in set) t else t - 1
        var n = 0
        while (d in set) {
            n++
            d--
        }
        n
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val notes: StateFlow<List<NoteEntity>> = db.noteDao().all()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _name = MutableStateFlow(prefs.name)
    val name: StateFlow<String> = _name.asStateFlow()

    private val _onboarded = MutableStateFlow(prefs.onboarded)
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    private val _photo = MutableStateFlow<ImageBitmap?>(null)
    val photo: StateFlow<ImageBitmap?> = _photo.asStateFlow()

    private val _weather = MutableStateFlow(initialWeather())
    val weather: StateFlow<WeatherUi> = _weather.asStateFlow()

    private val _city = MutableStateFlow(prefs.city())
    /** Hand-picked weather city, or null when the phone's location is used. */
    val city: StateFlow<SavedPlace?> = _city.asStateFlow()

    // ---- header sky
    private val _skyCoords = MutableStateFlow(prefs.city()?.let { it.lat to it.lon } ?: prefs.lastCoords())
    private val _skyWeather = MutableStateFlow(initialSkyWeather())
    private val _skyPreview = MutableStateFlow<SkyCond?>(null)

    /** Weather picked in the preview sheet (null = live weather). Not saved. */
    val skyPreview: StateFlow<SkyCond?> = _skyPreview.asStateFlow()

    /** Location (for sunrise/sunset) and weather that drive the animated header. */
    val skyInputs: StateFlow<SkyInputs> = combine(_skyCoords, _skyWeather, _skyPreview) { c, w, preview ->
        SkyInputs(c?.first, c?.second, preview?.let { SceneWeather.preview(it) } ?: w)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SkyInputs(_skyCoords.value?.first, _skyCoords.value?.second, _skyWeather.value),
    )

    private var weatherJob: Job? = null

    init {
        loadPhoto()
        rescheduleAll()
        // Monthly incomes: write the months that came due (also after the app was closed for a while).
        viewModelScope.launch { today.collect { catchUpRepeats() } }
        // কিস্তি / দেনা-পাওনার তারিখের নোটিফিকেশন (alarms can be lost after an update or force-stop).
        viewModelScope.launch { DueAlarms.rescheduleAll(app, db) }
        viewModelScope.launch {
            while (true) {
                refreshWeather(force = false)
                delay(30 * 60_000L)
            }
        }
    }

    // ---------- Tasks ----------
    fun addTask(title: String, epochDay: Long, minuteOfDay: Int?) {
        if (title.isBlank()) return
        viewModelScope.launch {
            db.taskDao().insert(TaskEntity(title = title.trim(), epochDay = epochDay, minuteOfDay = minuteOfDay))
        }
    }

    fun toggleTask(task: TaskEntity) {
        viewModelScope.launch { db.taskDao().setDone(task.id, !task.done) }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch { db.taskDao().delete(task) }
    }

    // ---------- Reminders ----------
    fun addReminder(title: String, epochDay: Long, minuteOfDay: Int, repeatDaily: Boolean, icon: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val r = ReminderEntity(
                title = title.trim(),
                epochDay = epochDay,
                minuteOfDay = minuteOfDay,
                repeatDaily = repeatDaily,
                icon = icon,
            )
            val id = db.reminderDao().insert(r)
            ReminderScheduler.schedule(app, r.copy(id = id))
        }
    }

    fun deleteReminder(r: ReminderEntity) {
        viewModelScope.launch {
            ReminderScheduler.cancel(app, r.id)
            db.reminderDao().delete(r)
        }
    }

    fun rescheduleAll() {
        viewModelScope.launch {
            db.reminderDao().allOnce().forEach { ReminderScheduler.schedule(app, it) }
        }
    }

    // ---------- Expenses ----------
    fun addExpense(amount: Double, category: String, note: String, epochDay: Long) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            db.expenseDao().insert(
                ExpenseEntity(amount = amount, category = category, note = note.trim(), epochDay = epochDay)
            )
        }
    }

    fun deleteExpense(e: ExpenseEntity) {
        viewModelScope.launch { db.expenseDao().delete(e) }
    }

    // ---------- Income (salary etc.) ----------
    /**
     * Adds an income. With [monthly] it is also added again every month on the same date
     * (salary), until the rule is stopped from the Money page.
     */
    fun addIncome(amount: Double, category: String, note: String, epochDay: Long, monthly: Boolean) {
        if (amount <= 0.0) return
        viewModelScope.launch {
            repeatLock.withLock {
                db.withTransaction {
                    val repeatId = if (monthly) {
                        db.incomeDao().insertRepeat(
                            IncomeRepeatEntity(
                                amount = amount,
                                category = category,
                                note = note.trim(),
                                dayOfMonth = LocalDate.ofEpochDay(epochDay).dayOfMonth,
                                lastEpochDay = epochDay,
                            )
                        )
                    } else {
                        null
                    }
                    db.incomeDao().insert(
                        IncomeEntity(
                            amount = amount,
                            category = category,
                            note = note.trim(),
                            epochDay = epochDay,
                            repeatId = repeatId,
                        )
                    )
                }
            }
            // A start date in an earlier month: fill in the months since then.
            if (monthly) catchUpRepeats()
        }
    }

    fun deleteIncome(i: IncomeEntity) {
        viewModelScope.launch { db.incomeDao().delete(i) }
    }

    /** Stops a monthly income. Entries already added stay. */
    fun stopRepeat(r: IncomeRepeatEntity) {
        viewModelScope.launch {
            repeatLock.withLock {
                db.withTransaction {
                    db.incomeDao().deleteRepeat(r.id)
                    db.incomeDao().detachRepeat(r.id)
                }
            }
        }
    }

    private suspend fun catchUpRepeats() {
        repeatLock.withLock {
            val todayDay = LocalDate.now().toEpochDay()
            db.incomeDao().repeatsOnce().forEach { r ->
                var last = r.lastEpochDay
                // At most 10 years at once, in case the clock is far off.
                var guard = 0
                while (guard++ < 120) {
                    val next = nextMonthly(last, r.dayOfMonth)
                    if (next > todayDay) break
                    db.withTransaction {
                        db.incomeDao().insert(
                            IncomeEntity(
                                amount = r.amount,
                                category = r.category,
                                note = r.note,
                                epochDay = next,
                                repeatId = r.id,
                            )
                        )
                        db.incomeDao().setRepeatLast(r.id, next)
                    }
                    last = next
                }
            }
        }
    }

    /** The same date one month after [lastDay] (the month's last day when it is shorter). */
    private fun nextMonthly(lastDay: Long, dayOfMonth: Int): Long {
        val month = LocalDate.ofEpochDay(lastDay).withDayOfMonth(1).plusMonths(1)
        return month.withDayOfMonth(min(dayOfMonth, month.lengthOfMonth())).toEpochDay()
    }

    // ---------- Money categories ----------
    /** Makes a new category and hands its key to [onCreated] (to select it right away). */
    fun addCategory(type: MoneyType, name: String, emoji: String, color: Long, onCreated: (String) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = db.moneyCategoryDao().insert(
                MoneyCategoryEntity(type = type.key, name = name.trim(), emoji = emoji.ifBlank { "📦" }, color = color)
            )
            onCreated(MoneyCategories.customKey(id))
        }
    }

    fun updateCategory(category: MoneyCategory, name: String, emoji: String, color: Long) {
        if (!category.custom || name.isBlank()) return
        viewModelScope.launch {
            db.moneyCategoryDao().update(category.customId, name.trim(), emoji.ifBlank { category.emoji }, color)
        }
    }

    /** Deletes one of the user's categories; its entries move to "অন্যান্য". */
    fun deleteCategory(category: MoneyCategory) {
        if (!category.custom) return
        viewModelScope.launch {
            db.withTransaction {
                if (category.type == MoneyType.Income) {
                    db.incomeDao().moveCategory(category.key, MoneyCategories.OTHER_INCOME)
                    db.incomeDao().moveRepeatCategory(category.key, MoneyCategories.OTHER_INCOME)
                } else {
                    db.expenseDao().moveCategory(category.key, MoneyCategories.OTHER_EXPENSE)
                }
                db.moneyCategoryDao().delete(category.customId)
            }
        }
    }

    // ---------- দেনা-পাওনা ----------
    /**
     * Writes one "দিলাম" ([gave] = true) or "পেলাম". With [personId] = null a new person named
     * [newName] is made. With [setDue] the person's settle-up day becomes [due] (null = no date)
     * and its notification [remind]; otherwise the day already saved stays.
     */
    fun addLedgerEntry(
        personId: Long?,
        newName: String,
        phone: String,
        gave: Boolean,
        amount: Double,
        note: String,
        epochDay: Long,
        due: Long?,
        setDue: Boolean,
        remind: Boolean,
    ) {
        if (amount <= 0.0) return
        if (personId == null && newName.isBlank()) return
        viewModelScope.launch {
            val id = db.withTransaction {
                val pid = if (personId == null) {
                    db.ledgerDao().insertPerson(
                        LedgerPersonEntity(
                            name = newName.trim(),
                            phone = phone.trim(),
                            dueEpochDay = if (setDue) due else null,
                            remind = setDue && remind && due != null,
                        )
                    )
                } else {
                    val p = db.ledgerDao().person(personId)
                    if (p != null && setDue) {
                        db.ledgerDao().updatePerson(p.copy(dueEpochDay = due, remind = remind && due != null))
                    }
                    personId
                }
                db.ledgerDao().insertEntry(
                    LedgerEntryEntity(personId = pid, gave = gave, amount = amount, note = note.trim(), epochDay = epochDay)
                )
                pid
            }
            reschedulePerson(id)
        }
    }

    fun deleteLedgerEntry(e: LedgerEntryEntity) {
        viewModelScope.launch {
            db.ledgerDao().deleteEntry(e)
            reschedulePerson(e.personId)
        }
    }

    /** Saves name, phone, settle-up day and its notification. */
    fun updatePerson(p: LedgerPersonEntity) {
        if (p.name.isBlank()) return
        viewModelScope.launch {
            db.ledgerDao().updatePerson(
                p.copy(name = p.name.trim(), phone = p.phone.trim(), remind = p.remind && p.dueEpochDay != null)
            )
            reschedulePerson(p.id)
        }
    }

    fun deletePerson(p: LedgerPersonEntity) {
        viewModelScope.launch {
            db.withTransaction {
                db.ledgerDao().deleteEntriesFor(p.id)
                db.ledgerDao().deletePerson(p.id)
            }
            DueAlarms.cancelPerson(app, p.id)
        }
    }

    private suspend fun reschedulePerson(id: Long) {
        val p = db.ledgerDao().person(id)
        if (p == null) {
            DueAlarms.cancelPerson(app, id)
        } else {
            DueAlarms.schedulePerson(app, p, db.ledgerDao().entriesFor(id))
        }
    }

    // ---------- কিস্তি ----------
    /**
     * Adds a plan ([existing] = null; the first [alreadyPaid] installments are marked as paid
     * without touching Money) or saves changes to one (payments past a shortened plan are removed).
     */
    fun saveInstallment(existing: InstallmentEntity?, plan: InstallmentEntity, alreadyPaid: Int) {
        if (plan.title.isBlank() || plan.amount <= 0.0 || plan.count <= 0) return
        viewModelScope.launch {
            val id = db.withTransaction {
                if (existing == null) {
                    val newId = db.installmentDao().insert(plan.copy(id = 0, title = plan.title.trim(), lender = plan.lender.trim()))
                    for (n in 1..alreadyPaid.coerceIn(0, plan.count)) {
                        db.installmentDao().insertPayment(
                            InstallmentPaymentEntity(
                                installmentId = newId,
                                number = n,
                                amount = plan.amount,
                                epochDay = Kisti.dueDate(plan, n).toEpochDay(),
                            )
                        )
                    }
                    newId
                } else {
                    db.installmentDao().update(
                        plan.copy(
                            id = existing.id,
                            title = plan.title.trim(),
                            lender = plan.lender.trim(),
                            createdAt = existing.createdAt,
                        )
                    )
                    db.installmentDao().deletePaymentsAfter(existing.id, plan.count)
                    existing.id
                }
            }
            rescheduleKisti(id)
        }
    }

    /** Marks installment [number] as paid; with [addToMoney] it is also written as a Money expense. */
    fun payInstallment(k: InstallmentEntity, number: Int, amount: Double, epochDay: Long, addToMoney: Boolean) {
        if (amount <= 0.0 || number !in 1..k.count) return
        viewModelScope.launch {
            db.withTransaction {
                val already = db.installmentDao().paymentsFor(k.id).any { it.number == number }
                if (!already) {
                    val expenseId = if (addToMoney) {
                        db.expenseDao().insert(
                            ExpenseEntity(
                                amount = amount,
                                category = MoneyCategories.LOAN,
                                note = "${k.title} · কিস্তি $number/${k.count}",
                                epochDay = epochDay,
                            )
                        )
                    } else {
                        null
                    }
                    db.installmentDao().insertPayment(
                        InstallmentPaymentEntity(
                            installmentId = k.id,
                            number = number,
                            amount = amount,
                            epochDay = epochDay,
                            expenseId = expenseId,
                        )
                    )
                }
                already
            }
            rescheduleKisti(k.id)
        }
    }

    /** Takes a payment back (its Money expense too). */
    fun unpayInstallment(p: InstallmentPaymentEntity) {
        viewModelScope.launch {
            db.withTransaction {
                db.installmentDao().deletePayment(p)
                p.expenseId?.let { db.expenseDao().deleteById(it) }
            }
            rescheduleKisti(p.installmentId)
        }
    }

    /** Deletes a plan and its payment marks. Expenses already written to Money stay. */
    fun deleteInstallment(k: InstallmentEntity) {
        viewModelScope.launch {
            db.withTransaction {
                db.installmentDao().deletePaymentsFor(k.id)
                db.installmentDao().delete(k.id)
            }
            DueAlarms.cancelKisti(app, k.id)
        }
    }

    private suspend fun rescheduleKisti(id: Long) {
        val k = db.installmentDao().byId(id)
        if (k == null) {
            DueAlarms.cancelKisti(app, id)
        } else {
            DueAlarms.scheduleKisti(app, k, db.installmentDao().paymentsFor(id))
        }
    }

    // ---------- Habits ----------
    fun addHabit(name: String, icon: String) {
        if (name.isBlank()) return
        viewModelScope.launch { db.habitDao().insertHabit(HabitEntity(name = name.trim(), icon = icon)) }
    }

    fun toggleHabit(habitId: Long) {
        viewModelScope.launch {
            val day = today.value
            if (habitId in doneHabitIds.value) {
                db.habitDao().deleteLog(habitId, day)
            } else {
                db.habitDao().insertLog(HabitLogEntity(habitId = habitId, epochDay = day))
            }
        }
    }

    fun deleteHabit(h: HabitEntity) {
        viewModelScope.launch {
            db.habitDao().deleteLogsFor(h.id)
            db.habitDao().deleteHabit(h)
        }
    }

    // ---------- Notes ----------
    fun saveNote(existing: NoteEntity?, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            if (existing == null) {
                db.noteDao().insert(NoteEntity(text = text.trim()))
            } else {
                db.noteDao().update(existing.copy(text = text.trim(), updatedAt = System.currentTimeMillis()))
            }
        }
    }

    fun deleteNote(n: NoteEntity) {
        viewModelScope.launch { db.noteDao().delete(n) }
    }

    // ---------- Profile ----------
    fun setName(value: String) {
        prefs.name = value.trim()
        _name.value = prefs.name
    }

    fun finishOnboarding(value: String) {
        setName(value)
        prefs.onboarded = true
        _onboarded.value = true
    }

    fun askedLocationBefore(): Boolean = prefs.askedLocation

    fun markAskedLocation() {
        prefs.askedLocation = true
    }

    private fun photoFile() = File(app.filesDir, "profile.jpg")

    private fun loadPhoto() {
        viewModelScope.launch {
            val f = photoFile()
            _photo.value = withContext(Dispatchers.IO) {
                if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
            }
        }
    }

    fun setPhoto(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    savePhoto(uri)
                } catch (e: Exception) {
                    // Unreadable image: keep the old photo.
                }
            }
            loadPhoto()
        }
    }

    private fun savePhoto(uri: Uri) {
        val cr = app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 512 && bounds.outHeight / (sample * 2) >= 512) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return
        val orientation = try {
            cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val side = minOf(bmp.width, bmp.height)
        val matrix = Matrix()
        if (degrees != 0f) matrix.postRotate(degrees)
        val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side, matrix, true)
        val out = if (side > 512) Bitmap.createScaledBitmap(square, 512, 512, true) else square
        photoFile().outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    fun removePhoto() {
        photoFile().delete()
        _photo.value = null
    }

    // ---------- Weather ----------
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun formatTemp(t: Double) = "${t.roundToLong()}°C"

    private fun initialWeather(): WeatherUi {
        val cached = prefs.weather()
        return if (cached != null) {
            WeatherUi(formatTemp(cached.temp), WeatherService.describe(cached.code))
        } else if (prefs.city() == null && !hasLocationPermission()) {
            WeatherUi("--°C", "Tap to enable", WeatherIssue.NoPermission)
        } else {
            WeatherUi("--°C", "Loading…")
        }
    }

    /** Cached weather drives the sky only while it is reasonably fresh (3 hours). */
    private fun initialSkyWeather(): SceneWeather {
        val c = prefs.weather() ?: return SceneWeather.DEFAULT
        if (System.currentTimeMillis() - c.at > 3 * 60 * 60_000L) return SceneWeather.DEFAULT
        return SceneWeather.fromWeather(c.code, c.cloud, c.wind, c.precip)
    }

    private fun issueText(issue: WeatherIssue) = when (issue) {
        WeatherIssue.NoPermission -> "Tap to enable"
        WeatherIssue.LocationOff -> "Location off"
        WeatherIssue.NoLocation -> "No location"
        WeatherIssue.NoInternet -> "No internet"
        WeatherIssue.None -> "—"
    }

    fun refreshWeather(force: Boolean) {
        val cached = prefs.weather()
        if (!force && cached != null && System.currentTimeMillis() - cached.at < 30 * 60_000L) return
        if (weatherJob?.isActive == true) return
        weatherJob = viewModelScope.launch {
            val city = prefs.city()
            var coords: Pair<Double, Double>? = city?.let { it.lat to it.lon }
            var issue = WeatherIssue.None
            if (coords == null) {
                when {
                    !hasLocationPermission() -> issue = WeatherIssue.NoPermission
                    !WeatherService.isLocationEnabled(app) -> issue = WeatherIssue.LocationOff
                    else -> {
                        if (cached == null) _weather.value = WeatherUi("--°C", "Loading…")
                        val loc = WeatherService.currentLocation(app)
                        if (loc != null) {
                            coords = loc.latitude to loc.longitude
                            prefs.saveCoords(loc.latitude, loc.longitude)
                        } else {
                            issue = WeatherIssue.NoLocation
                        }
                    }
                }
                // Fall back to the last place the phone reported.
                if (coords == null) coords = prefs.lastCoords()
            }
            if (coords == null) {
                if (cached == null) _weather.value = WeatherUi("--°C", issueText(issue), issue)
                return@launch
            }
            _skyCoords.value = coords
            if (cached == null) _weather.value = WeatherUi("--°C", "Loading…")
            val w = WeatherService.fetch(coords.first, coords.second)
            if (w != null) {
                prefs.saveWeather(w.temp, w.code, w.cloud, w.wind, w.precip, System.currentTimeMillis())
                _weather.value = WeatherUi(formatTemp(w.temp), WeatherService.describe(w.code))
                _skyWeather.value = SceneWeather.fromWeather(w.code, w.cloud, w.wind, w.precip)
            } else if (cached == null) {
                _weather.value = WeatherUi("--°C", issueText(WeatherIssue.NoInternet), WeatherIssue.NoInternet)
            }
        }
    }

    fun weatherPermissionDenied() {
        if (prefs.weather() == null && prefs.city() == null) {
            _weather.value = WeatherUi("--°C", "Tap to enable", WeatherIssue.NoPermission)
        }
    }

    /** Use a hand-picked city for weather (null = go back to the phone's location). */
    fun setCity(place: Place?) {
        val saved = place?.let { SavedPlace(it.name, it.lat, it.lon) }
        prefs.setCity(saved)
        _city.value = saved
        prefs.clearWeather()
        _weather.value = WeatherUi("--°C", "Loading…")
        _skyCoords.value = saved?.let { it.lat to it.lon } ?: prefs.lastCoords()
        weatherJob?.cancel()
        refreshWeather(force = true)
    }

    suspend fun searchPlaces(query: String): List<Place>? = WeatherService.searchPlaces(query)

    fun setSkyPreview(cond: SkyCond?) {
        _skyPreview.value = cond
    }
}
