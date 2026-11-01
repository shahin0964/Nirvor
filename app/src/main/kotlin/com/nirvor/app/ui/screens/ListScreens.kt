package com.nirvor.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.data.NoteEntity
import com.nirvor.app.data.TaskEntity
import com.nirvor.app.reminders.ReminderScheduler
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.SectionLabel
import com.nirvor.app.ui.TickCircle
import com.nirvor.app.ui.TrashButton
import com.nirvor.app.ui.home.HabitIcon
import com.nirvor.app.ui.home.reminderGlyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.util.Fmt
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private val taskOrder: Comparator<TaskEntity> =
    compareBy<TaskEntity>({ it.minuteOfDay == null }, { it.minuteOfDay ?: 0 }, { it.id })

// ---------------------------------------------------------------- Tasks tab

@Composable
fun TasksScreen(vm: MainViewModel, bottomPadding: Dp) {
    val tasks by vm.allTasks.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val todayList = tasks.filter { it.epochDay == today }.sortedWith(taskOrder)
    val upcoming = tasks.filter { it.epochDay > today }
        .sortedWith(compareBy<TaskEntity> { it.epochDay }.then(taskOrder))
    val past = tasks.filter { it.epochDay < today }

    PageScaffold("Tasks", "আপনার সব কাজ", onBack = null, bottomPadding = bottomPadding) {
        if (tasks.isEmpty()) {
            item { EmptyText("এখনো কোনো কাজ যোগ করা হয়নি।\nনিচের + বাটনে চাপ দিয়ে কাজ যোগ করুন।") }
        }
        if (todayList.isNotEmpty()) {
            item { SectionLabel("আজ") }
            items(todayList, key = { it.id }) { t -> TaskRow(t, showDate = false, vm) }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionLabel("সামনের কাজ") }
            items(upcoming, key = { it.id }) { t -> TaskRow(t, showDate = true, vm) }
        }
        if (past.isNotEmpty()) {
            item { SectionLabel("আগের কাজ") }
            items(past, key = { it.id }) { t -> TaskRow(t, showDate = true, vm) }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, showDate: Boolean, vm: MainViewModel) {
    ListCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.r).clip(CircleShape).clickable { vm.toggleTask(task) },
                contentAlignment = Alignment.Center,
            ) {
                TickCircle(task.done, 34)
            }
            Spacer(Modifier.width(14.r))
            Column(Modifier.weight(1f)) {
                RText(
                    task.title,
                    23,
                    FontWeight.Normal,
                    if (task.done) C.DoneText else C.Body,
                    maxLines = 3,
                    decoration = if (task.done) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(6.r))
                val time = task.minuteOfDay?.let { Fmt.minute(it) } ?: "যেকোনো সময়"
                RText(
                    if (showDate) Fmt.date(task.epochDay) + " · " + time else time,
                    18,
                    FontWeight.Normal,
                    C.Time,
                )
            }
            TrashButton { vm.deleteTask(task) }
        }
    }
}

// The Money tab lives in ui/money/MoneyScreen.kt.

// ---------------------------------------------------------------- Reminders page

@Composable
fun RemindersScreen(vm: MainViewModel, bottomPadding: Dp, onBack: () -> Unit) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val withNext = reminders.map { it to ReminderScheduler.nextTrigger(it, now) }
        .sortedWith(compareBy({ it.second == null }, { it.second ?: LocalDateTime.MAX }))

    PageScaffold("Reminders", "আসন্ন রিমাইন্ডার", onBack = onBack, bottomPadding = bottomPadding) {
        if (reminders.isEmpty()) {
            item { EmptyText("কোনো রিমাইন্ডার নেই।\nনিচের + বাটনে চাপ দিয়ে রিমাইন্ডার যোগ করুন।") }
        }
        items(withNext, key = { it.first.id }) { (r, next) ->
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(62.r).background(C.RemItemBg, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(reminderGlyph(r.icon), C.RemIcon, Modifier.size(32.r), accent = Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.width(18.r))
                    Column(Modifier.weight(1f)) {
                        RText(r.title, 23, FontWeight.Normal, C.Body, maxLines = 2)
                        Spacer(Modifier.height(6.r))
                        val sub = when {
                            r.repeatDaily -> "প্রতিদিন · " + Fmt.minute(r.minuteOfDay)
                            next != null -> Fmt.date(r.epochDay) + " · " + Fmt.minute(r.minuteOfDay)
                            else -> Fmt.date(r.epochDay) + " · শেষ হয়েছে"
                        }
                        RText(sub, 18, FontWeight.Normal, C.RemTime)
                    }
                    TrashButton { vm.deleteReminder(r) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Habits page

@Composable
fun HabitsScreen(vm: MainViewModel, bottomPadding: Dp, onBack: () -> Unit) {
    val habits by vm.habits.collectAsStateWithLifecycle()
    val done by vm.doneHabitIds.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()

    PageScaffold("Habits", "আজকের হ্যাবিট · ধারাবাহিকতা $streak দিন", onBack = onBack, bottomPadding = bottomPadding) {
        if (habits.isEmpty()) {
            item { EmptyText("কোনো হ্যাবিট নেই।\nনিচের + বাটনে চাপ দিয়ে হ্যাবিট যোগ করুন।") }
        }
        items(habits, key = { it.id }) { h ->
            val isDone = h.id in done
            ListCard(onClick = { vm.toggleHabit(h.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TickCircle(isDone, 34, ringColor = C.HabOpen)
                    Spacer(Modifier.width(18.r))
                    HabitIcon(h.icon, Modifier.size(32.r))
                    Spacer(Modifier.width(16.r))
                    RText(h.name, 23, FontWeight.Normal, C.HabText, Modifier.weight(1f), maxLines = 2)
                    TrashButton { vm.deleteHabit(h) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Notes page

@Composable
fun NotesScreen(vm: MainViewModel, bottomPadding: Dp, onBack: () -> Unit, onEdit: (NoteEntity) -> Unit) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    PageScaffold("Quick Notes", "আপনার নোট", onBack = onBack, bottomPadding = bottomPadding) {
        if (notes.isEmpty()) {
            item { EmptyText("কোনো নোট নেই।\nনিচের + বাটনে চাপ দিয়ে নোট লিখুন।") }
        }
        items(notes, key = { it.id }) { n ->
            ListCard(onClick = { onEdit(n) }) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        RText(n.text, 21, FontWeight.Normal, C.NotesText, maxLines = 6)
                        Spacer(Modifier.height(10.r))
                        val d = Instant.ofEpochMilli(n.updatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        RText(Fmt.date(d), 17, FontWeight.Normal, C.Time)
                    }
                    TrashButton { vm.deleteNote(n) }
                }
            }
        }
    }
}
