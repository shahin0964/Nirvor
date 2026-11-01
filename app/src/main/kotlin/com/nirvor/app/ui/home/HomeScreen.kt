package com.nirvor.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.data.ExpenseEntity
import com.nirvor.app.data.HabitEntity
import com.nirvor.app.data.NoteEntity
import com.nirvor.app.data.TaskEntity
import com.nirvor.app.ui.Avatar
import com.nirvor.app.ui.HAlign
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.Ref
import com.nirvor.app.ui.LocalRef
import com.nirvor.app.ui.UpcomingReminder
import com.nirvor.app.ui.WeatherUi
import com.nirvor.app.ui.at
import com.nirvor.app.ui.atBaseline
import com.nirvor.app.ui.atCenter
import com.nirvor.app.ui.cardSurface
import com.nirvor.app.ui.home.sky.SCENE_H
import com.nirvor.app.ui.home.sky.SCENE_W
import com.nirvor.app.ui.home.sky.SkyClock
import com.nirvor.app.ui.home.sky.SkyCond
import com.nirvor.app.ui.home.sky.SkyController
import com.nirvor.app.ui.home.sky.SkyScene
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import com.nirvor.app.util.ExpenseCategory
import com.nirvor.app.util.Fmt

/** Height of the home page in reference px (top of screen to top of the bottom bar). */
private const val PAGE_HEIGHT = 1354f

/** Bottom of the status bar in the reference. Taller real status bars push the page down. */
private const val REF_STATUS_BAR = 66f

@Composable
fun HomeScreen(
    vm: MainViewModel,
    bottomExtra: Dp,
    onOpenTasks: () -> Unit,
    onOpenMoney: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenProfile: () -> Unit,
    onAddTask: () -> Unit,
    onAddReminder: () -> Unit,
    onAddHabit: () -> Unit,
    onWeatherTap: () -> Unit,
    skyClock: SkyClock,
    onSkyLongPress: () -> Unit,
    onHeaderDark: (Boolean) -> Unit,
    onOpenSideBar: () -> Unit,
) {
    val ref = LocalRef.current
    val density = LocalDensity.current
    val statusRef = WindowInsets.statusBars.getTop(density) / ref.px
    val extraTop = (statusRef - REF_STATUS_BAR).coerceAtLeast(0f)

    val skyInputs = vm.skyInputs.collectAsStateWithLifecycle()
    val sky = remember(skyClock) { SkyController(skyClock, skyInputs) }
    val skyPreview by vm.skyPreview.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val headerDark by remember {
        derivedStateOf { sky.darkText.value > 0.5f && scroll.value < 170f * ref.px }
    }
    LaunchedEffect(headerDark) { onHeaderDark(headerDark) }

    val now by vm.now.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    val photo by vm.photo.collectAsStateWithLifecycle()
    val tasks by vm.todayTasks.collectAsStateWithLifecycle()
    val upcoming by vm.upcoming.collectAsStateWithLifecycle()
    val expenses by vm.todayExpenses.collectAsStateWithLifecycle()
    val habits by vm.habits.collectAsStateWithLifecycle()
    val doneHabits by vm.doneHabitIds.collectAsStateWithLifecycle()
    val streak by vm.streak.collectAsStateWithLifecycle()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
    ) {
        if (extraTop > 0f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(extraTop.r)
                    .drawBehind { drawRect(sky.frame.value.p.skyTop) }
            )
        }
        Box(Modifier.fillMaxWidth().height(PAGE_HEIGHT.r)) {
            Header(
                ref = ref,
                sky = sky,
                name = name,
                photo = photo,
                preview = skyPreview,
                onProfile = onOpenProfile,
                onSettings = onOpenSideBar,
                onLongPress = onSkyLongPress,
                onClearPreview = {
                    vm.setSkyPreview(null)
                    skyClock.stopTimelapse()
                },
            )

            // Light sheet that holds all cards (rounded top corners over the illustration).
            Box(
                Modifier
                    .at(ref, 0, 246)
                    .size(766.r, (PAGE_HEIGHT - 246f).r)
                    .background(C.Sheet, RoundedCornerShape(topStart = 34.r, topEnd = 34.r))
            )

            TodayCard(
                ref = ref,
                dateText = Fmt.date(now.toLocalDate()),
                completed = tasks.count { it.done },
                remaining = tasks.count { !it.done },
                streak = streak,
                weather = weather,
                onDate = onOpenTasks,
                onWeather = onWeatherTap,
            )
            TasksCard(ref, tasks, onToggle = { vm.toggleTask(it) }, onViewAll = onOpenTasks, onAdd = onAddTask)
            RemindersCard(ref, upcoming, today = now.toLocalDate().toEpochDay(), onViewAll = onOpenReminders, onAdd = onAddReminder)
            ExpenseCard(ref, expenses, onViewAll = onOpenMoney)
            HabitCard(ref, habits, doneHabits, onToggle = { vm.toggleHabit(it) }, onViewAll = onOpenHabits, onAdd = onAddHabit)
            NotesCard(ref, notes.firstOrNull(), onClick = onOpenNotes)
        }
        Spacer(Modifier.height(bottomExtra))
    }
}

// ---------------------------------------------------------------- Header

@Composable
private fun Header(
    ref: Ref,
    sky: SkyController,
    name: String,
    photo: ImageBitmap?,
    preview: SkyCond?,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onLongPress: () -> Unit,
    onClearPreview: () -> Unit,
) {
    // Live animated sky: sun / moon / clouds / rain etc. drawn under the text.
    SkyScene(sky, Modifier.at(ref, 0, 0).size(SCENE_W.r, SCENE_H.r))
    Box(
        Modifier
            .at(ref, 0, 0)
            .size(766.r, 246.r)
            .pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }
    )

    val dark by sky.darkText
    val hour by sky.hour
    val ink = lerp(C.HeaderInk, Color.White, dark)
    val glow = Shadow(
        color = lerp(Color.White.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.35f), dark),
        offset = Offset.Zero,
        blurRadius = 9f * ref.px,
    )

    RText(Fmt.greeting(hour), 25.5, FontWeight.Medium, ink, Modifier.atBaseline(ref, 29, 110.3), shadow = glow)

    Row(
        Modifier.atBaseline(ref, 29, 161.9).widthIn(max = 578.r),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (name.isNotBlank()) {
            RText(
                name,
                45.5,
                FontWeight.Bold,
                ink,
                Modifier.alignByBaseline().weight(1f, fill = false),
                shadow = glow,
            )
            Spacer(Modifier.width(20.r))
        }
        Text(
            text = "\uD83D\uDC4B",
            modifier = Modifier.alignByBaseline(),
            style = TextStyle(fontFamily = Inter, fontSize = 37.rs),
        )
    }

    RText(
        subtitleFor(hour),
        21,
        FontWeight.Normal,
        ink,
        Modifier.atBaseline(ref, 27, 205.5),
        shadow = glow,
    )

    // Profile photo with a soft glow behind it.
    Box(
        Modifier
            .atCenter(ref, 651.5, 114.5)
            .size(94.r)
            .background(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.6f * (1f - 0.6f * dark)), Color.White.copy(alpha = 0f)),
                ),
                CircleShape,
            )
    )
    Avatar(
        photo,
        Modifier
            .atCenter(ref, 651.5, 114.5)
            .size(72.r)
            .clip(CircleShape)
            .clickable(onClick = onProfile),
    )
    Box(
        Modifier
            .atCenter(ref, 723.5, 114.5)
            .size(60.r)
            .clip(CircleShape)
            .clickable(onClick = onSettings),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.Gear, ink, Modifier.size(43.r), strokeWidth = 1.75f)
    }

    // Small pill while a preview or time-lapse is showing; tap to go back to live.
    val timelapse = sky.clock.timelapseStartUptime != null
    if (preview != null || timelapse) {
        val simSlot by remember { derivedStateOf { sky.clock.sim / 600_000L } }
        val label = buildString {
            append(if (timelapse) "টাইম-ল্যাপস " + Fmt.timeAt(simSlot * 600_000L) else "প্রিভিউ")
            if (preview != null) append(" · " + preview.label)
            append("  ✕")
        }
        Box(
            Modifier
                .atCenter(ref, 440, 100)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onClearPreview)
                .padding(horizontal = 16.r, vertical = 7.r)
        ) {
            RText(label, 15, FontWeight.Medium, Color.White)
        }
    }
}

/** Second line under the name; the first one is exactly the reference's morning text. */
private fun subtitleFor(hour: Int): String = when (hour) {
    in 5..11 -> "আজকের দিনটা সুন্দরভাবে শুরু করি।"
    in 12..16 -> "দিনটা ভালো কাটুক।"
    in 17..20 -> "সন্ধ্যাটা শান্তিতে কাটুক।"
    else -> "শুভ রাত্রি, ভালো ঘুম হোক।"
}

// ---------------------------------------------------------------- Today card

@Composable
private fun TodayCard(
    ref: Ref,
    dateText: String,
    completed: Int,
    remaining: Int,
    streak: Int,
    weather: WeatherUi,
    onDate: () -> Unit,
    onWeather: () -> Unit,
) {
    val shape = RoundedCornerShape(24.r)
    Box(
        Modifier
            .at(ref, 21, 264)
            .size(728.r, 251.5.r)
            .cardSurface(shape, SolidColor(Color.White), C.TodayBorder, 1.2.r, 13.r)
    )
    Box(Modifier.atCenter(ref, 64.5, 299).size(50.r).background(C.CalBg, CircleShape))
    GlyphIcon(Glyph.Calendar, C.CalIcon, Modifier.atCenter(ref, 64.5, 299).size(34.r), strokeWidth = 1.75f)
    RText("Today", 24, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 103, 306.4))

    // Date + chevron (opens the task list)
    Box(
        Modifier
            .at(ref, 570, 278)
            .size(170.r, 44.r)
            .clip(RoundedCornerShape(12.r))
            .clickable(onClick = onDate)
    )
    RText(dateText, 18, FontWeight.Normal, C.Date, Modifier.atBaseline(ref, 700, 307.2, HAlign.End))
    GlyphIcon(Glyph.ChevronRight, C.DateChevron, Modifier.atCenter(ref, 724, 300.5).size(21.r), strokeWidth = 2.0f)

    val cols = listOf(114.5f, 292.5f, 476.5f, 660.5f)
    listOf(206f, 384f, 568f).forEach { x ->
        Box(Modifier.at(ref, x, 344).size(1.2.r, 145.r).background(C.Divider))
    }

    // Completed
    StatCircle(ref, cols[0], C.GreenA, C.GreenB) {
        GlyphIcon(Glyph.Check, Color.White, Modifier.size(26.r), strokeWidth = 2.7f)
    }
    StatTexts(ref, cols[0], "$completed", "Completed", "কাজ সম্পন্ন")

    // Remaining
    StatCircle(ref, cols[1], C.OrangeA, C.OrangeB) {
        GlyphIcon(Glyph.ClockHands, Color.White, Modifier.size(26.r), strokeWidth = 2.7f)
    }
    StatTexts(ref, cols[1], "$remaining", "Remaining", "বাকি কাজ")

    // Day streak
    StatCircle(ref, cols[2], C.RedA, C.RedB) {
        GlyphIcon(Glyph.Flame, Color.White, Modifier.size(29.r), accent = Color(0xFFFA5446))
    }
    StatTexts(ref, cols[2], "$streak", "Day Streak", "হ্যাবিট ধারাবাহিকতা")

    // Weather
    GlyphIcon(
        Glyph.Sun,
        C.Sun,
        Modifier.atCenter(ref, cols[3], 364.5).size(43.r),
        strokeWidth = 1.5f,
        accent = C.SunFill,
    )
    StatTexts(ref, cols[3], weather.temp, weather.desc, "আবহাওয়া")
    Box(
        Modifier
            .at(ref, cols[3] - 88f, 336)
            .size(176.r, 160.r)
            .clip(RoundedCornerShape(16.r))
            .clickable(onClick = onWeather)
    )
}

@Composable
private fun StatCircle(ref: Ref, cx: Float, a: Color, b: Color, content: @Composable () -> Unit) {
    Box(
        Modifier
            .atCenter(ref, cx, 364.5)
            .size(42.r)
            .background(Brush.linearGradient(listOf(a, b)), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun StatTexts(ref: Ref, cx: Float, value: String, label: String, labelBn: String) {
    RText(value, 24.5, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, cx, 424.4, HAlign.Center))
    RText(label, 17.5, FontWeight.Normal, C.StatLabel, Modifier.atBaseline(ref, cx, 454.9, HAlign.Center))
    RText(labelBn, 15.5, FontWeight.Normal, C.StatLabelBn, Modifier.atBaseline(ref, cx, 483.5, HAlign.Center))
}

// ---------------------------------------------------------------- Shared bits

@Composable
private fun HeaderIcon(ref: Ref, cx: Number, cy: Number, d: Number, a: Color, b: Color, content: @Composable () -> Unit) {
    Box(
        Modifier
            .atCenter(ref, cx, cy)
            .size(d.r)
            .background(Brush.linearGradient(listOf(a, b)), CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ViewAll(ref: Ref, x: Number, baseline: Number, size: Number, color: Color, chevronColor: Color, chevronCx: Number, chevronCy: Number, onClick: () -> Unit) {
    Box(
        Modifier
            .at(ref, x.toFloat() - 10f, baseline.toFloat() - 26f)
            .size(92.r, 38.r)
            .clip(RoundedCornerShape(10.r))
            .clickable(onClick = onClick)
    )
    RText("View All", size, FontWeight.Normal, color, Modifier.atBaseline(ref, x, baseline))
    GlyphIcon(Glyph.ChevronRight, chevronColor, Modifier.atCenter(ref, chevronCx, chevronCy).size(17.r), strokeWidth = 2.3f)
}

private fun habitGlyph(key: String): Glyph = when (key) {
    "drop" -> Glyph.Drop
    "book" -> Glyph.Book
    "lotus" -> Glyph.Lotus
    "phone" -> Glyph.Phone
    "moon" -> Glyph.Moon
    "target" -> Glyph.Target
    else -> Glyph.Star
}

@Composable
fun HabitIcon(key: String, modifier: Modifier) {
    when (key) {
        "drop" -> GlyphIcon(Glyph.Drop, C.Drop, modifier, accent = C.DropEdge)
        "moon" -> GlyphIcon(Glyph.Moon, C.HabMoon, modifier)
        else -> GlyphIcon(habitGlyph(key), C.HabIcon, modifier, strokeWidth = 1.5f)
    }
}

fun reminderGlyph(key: String): Glyph = when (key) {
    "pill" -> Glyph.Pill
    "calendar" -> Glyph.CalendarFill
    "bill" -> Glyph.Bill
    "moon" -> Glyph.MoonStar
    else -> Glyph.Bell
}

// ---------------------------------------------------------------- Today's Tasks

@Composable
private fun TasksCard(
    ref: Ref,
    tasks: List<TaskEntity>,
    onToggle: (TaskEntity) -> Unit,
    onViewAll: () -> Unit,
    onAdd: () -> Unit,
) {
    val shape = RoundedCornerShape(14.r)
    Box(
        Modifier
            .at(ref, 15.5, 532.5)
            .size(371.5.r, 391.r)
            .cardSurface(shape, SolidColor(C.TasksBg), C.TasksBorder, 1.3.r, 4.r)
    )
    HeaderIcon(ref, 57.5, 575, 48, C.TaskGreenA, C.TaskGreenB) {
        GlyphIcon(Glyph.Check, Color.White, Modifier.size(30.r), strokeWidth = 2.5f)
    }
    RText("Today's Tasks", 20, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 97, 581.6))
    ViewAll(ref, 301.5, 578.6, 14, C.TaskViewAll, C.TaskViewAllChevron, 365.5, 573.8, onViewAll)

    if (tasks.isEmpty()) {
        RText("আজকের কোনো কাজ নেই", 16, FontWeight.Normal, C.Hint, Modifier.atBaseline(ref, 201, 735, HAlign.Center))
        AddLink(ref, "+ কাজ যোগ করুন", 201, 770, C.TaskViewAll, onAdd)
        return
    }

    tasks.take(4).forEachIndexed { i, task ->
        val titleBase = 640.5f + 73f * i
        val timeBase = 667.1f + 73f * i
        Box(
            Modifier
                .atCenter(ref, 55.5, titleBase - 3f)
                .size(58.r)
                .clip(CircleShape)
                .clickable { onToggle(task) },
            contentAlignment = Alignment.Center,
        ) {
            if (task.done) {
                Box(
                    Modifier
                        .size(30.r)
                        .background(Brush.linearGradient(listOf(C.CheckA, C.CheckB)), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Check, Color.White, Modifier.size(21.r), strokeWidth = 2.7f)
                }
            } else {
                Box(Modifier.size(30.r).border(1.9.r, C.OpenRing, CircleShape))
            }
        }
        RText(
            task.title,
            15.5,
            FontWeight.Normal,
            if (task.done) C.DoneText else C.Body,
            Modifier.atBaseline(ref, 91, titleBase).width(268.r),
            decoration = if (task.done) TextDecoration.LineThrough else null,
        )
        RText(
            task.minuteOfDay?.let { Fmt.minute(it) } ?: "যেকোনো সময়",
            15,
            FontWeight.Normal,
            if (task.done) C.DoneText else C.Time,
            Modifier.atBaseline(ref, 91.5, timeBase),
        )
        if (i < minOf(tasks.size, 4) - 1) {
            Box(Modifier.at(ref, 91, 684f + 73f * i).size(266.5.r, 1.2.r).background(C.TaskDivider))
        }
    }
}

@Composable
private fun AddLink(ref: Ref, text: String, cx: Number, baseline: Number, color: Color, onClick: () -> Unit) {
    RText(
        text,
        16,
        FontWeight.SemiBold,
        color,
        Modifier
            .atBaseline(ref, cx, baseline, HAlign.Center)
            .clip(RoundedCornerShape(8.r))
            .clickable(onClick = onClick),
    )
}

// ---------------------------------------------------------------- Upcoming Reminders

@Composable
private fun RemindersCard(
    ref: Ref,
    upcoming: List<UpcomingReminder>,
    today: Long,
    onViewAll: () -> Unit,
    onAdd: () -> Unit,
) {
    val shape = RoundedCornerShape(14.r)
    Box(
        Modifier
            .at(ref, 402, 532.5)
            .size(350.5.r, 391.r)
            .cardSurface(shape, SolidColor(C.RemBg), Color.Transparent, 0.r, 4.r)
    )
    Box(
        Modifier
            .at(ref, 402, 532.5)
            .size(350.5.r, 90.r)
            .background(C.RemHeaderBg, RoundedCornerShape(topStart = 14.r, topEnd = 14.r))
    )
    Box(
        Modifier
            .at(ref, 402, 532.5)
            .size(350.5.r, 391.r)
            .border(1.3.r, C.RemBorder, shape)
    )
    HeaderIcon(ref, 449, 578, 51, C.PurpleA, C.PurpleB) {
        GlyphIcon(Glyph.Bell, Color.White, Modifier.size(30.r))
    }
    RText("Upcoming", 20, FontWeight.Bold, C.InkNavy, Modifier.atBaseline(ref, 495, 575.9))
    RText("Reminders", 20, FontWeight.Bold, C.InkNavy, Modifier.atBaseline(ref, 495, 600.5))
    ViewAll(ref, 667, 578.6, 14, C.RemViewAll, C.RemViewAll, 728.5, 573.8, onViewAll)

    if (upcoming.isEmpty()) {
        RText("কোনো রিমাইন্ডার নেই", 16, FontWeight.Normal, C.Hint, Modifier.atBaseline(ref, 577, 735, HAlign.Center))
        AddLink(ref, "+ রিমাইন্ডার যোগ করুন", 577, 770, C.RemViewAll, onAdd)
        return
    }

    val shown = upcoming.take(4)
    shown.forEachIndexed { i, item ->
        val cy = 661f + 72f * i
        Box(
            Modifier
                .at(ref, 410, cy - 34f)
                .size(334.r, 68.r)
                .clip(RoundedCornerShape(12.r))
                .clickable(onClick = onViewAll)
        )
        Box(
            Modifier.atCenter(ref, 452.5, cy).size(53.r).background(C.RemItemBg, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                reminderGlyph(item.reminder.icon),
                C.RemIcon,
                Modifier.size(27.r),
                accent = Color.White.copy(alpha = 0.85f),
            )
        }
        RText(
            item.reminder.title,
            15.5,
            FontWeight.Normal,
            C.Body,
            Modifier.atBaseline(ref, 494, cy - 3.5f).width(212.r),
        )
        val day = item.at.toLocalDate().toEpochDay()
        val timeText = if (day == today || item.reminder.repeatDaily) {
            Fmt.time(item.at)
        } else {
            Fmt.dayMonth(item.at.toLocalDate()) + ", " + Fmt.time(item.at)
        }
        RText(timeText, 14.5, FontWeight.Normal, C.RemTime, Modifier.atBaseline(ref, 495, cy + 20.7f))
        GlyphIcon(Glyph.ChevronRight, C.RemChevron, Modifier.atCenter(ref, 723.5, cy).size(19.r), strokeWidth = 2.0f)
        if (i < shown.size - 1) {
            Box(Modifier.at(ref, 494, cy + 37.5f).size(236.r, 1.2.r).background(C.RemDivider))
        }
    }
}

// ---------------------------------------------------------------- Today's Expense

@Composable
private fun ExpenseCard(ref: Ref, expenses: List<ExpenseEntity>, onViewAll: () -> Unit) {
    val shape = RoundedCornerShape(14.r)
    Box(
        Modifier
            .at(ref, 15.5, 940.5)
            .size(370.5.r, 275.r)
            .cardSurface(shape, Brush.verticalGradient(listOf(C.ExpBgTop, C.ExpBgBottom)), C.ExpBorder, 1.3.r, 4.r)
    )
    HeaderIcon(ref, 57.5, 980.5, 48, C.OrangeIconA, C.OrangeIconB) {
        GlyphIcon(Glyph.Wallet, Color.White, Modifier.size(28.r), accent = Color(0xFFF96A31))
    }
    RText("Today's Expense", 19, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 98, 985.8))
    ViewAll(ref, 302, 985.9, 14.5, C.ViewAllGrey, C.ViewAllGreyChevron, 365.5, 980.8, onViewAll)

    val total = expenses.sumOf { it.amount }
    RText("৳", 33, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 33, 1056.3))
    RText(Fmt.money(total), 33, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 66, 1056.3).width(300.r))
    RText("আজকের মোট খরচ", 15.5, FontWeight.Normal, C.ExpSub, Modifier.atBaseline(ref, 33, 1087.5))

    Box(
        Modifier
            .at(ref, 28, 1110)
            .size(345.5.r, 91.5.r)
            .background(Color.White, RoundedCornerShape(14.r))
            .border(1.r, C.InnerBorder, RoundedCornerShape(14.r))
    )
    Box(Modifier.at(ref, 134, 1131).size(1.2.r, 50.r).background(C.InnerDivider))
    Box(Modifier.at(ref, 258, 1131).size(1.2.r, 50.r).background(C.InnerDivider))

    val columns = listOf(
        Triple(ExpenseCategory.Food, 44f, 59f),
        Triple(ExpenseCategory.Transport, 163.5f, 179f),
        Triple(ExpenseCategory.Other, 288.5f, 304f),
    )
    columns.forEach { (cat, dotX, textX) ->
        val sum = expenses.filter { ExpenseCategory.of(it.category) == cat }.sumOf { it.amount }
        Box(Modifier.atCenter(ref, dotX, 1143).size(9.5.r).background(Color(cat.color), CircleShape))
        RText(cat.label, 14, FontWeight.Normal, C.CatLabel, Modifier.atBaseline(ref, textX, 1148.5))
        RText("৳", 14.5, FontWeight.Medium, C.CatAmount, Modifier.atBaseline(ref, textX, 1176.7))
        RText(Fmt.money(sum), 14.5, FontWeight.Medium, C.CatAmount, Modifier.atBaseline(ref, textX + 15f, 1176.7).width(82.r))
    }
}

// ---------------------------------------------------------------- Today's Habit

@Composable
private fun HabitCard(
    ref: Ref,
    habits: List<HabitEntity>,
    done: Set<Long>,
    onToggle: (Long) -> Unit,
    onViewAll: () -> Unit,
    onAdd: () -> Unit,
) {
    val shape = RoundedCornerShape(14.r)
    Box(
        Modifier
            .at(ref, 401, 940.5)
            .size(351.5.r, 275.r)
            .cardSurface(shape, SolidColor(C.HabBg), C.HabBorder, 1.3.r, 4.r)
    )
    HeaderIcon(ref, 441.5, 980.5, 48, C.HabGreenA, C.HabGreenB) {
        GlyphIcon(Glyph.Target, Color.White, Modifier.size(31.r), strokeWidth = 1.9f)
    }
    RText("Today's Habit", 19, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 482, 986.6))
    ViewAll(ref, 667, 985.9, 14.5, C.ViewAllGrey, C.ViewAllGreyChevron, 731, 980.8, onViewAll)

    val total = habits.size
    val doneCount = habits.count { it.id in done }
    val ringStroke = 9.5f
    Canvas(Modifier.atCenter(ref, 475.5, 1087).size(113.r)) {
        val sw = ringStroke * ref.px
        val radius = size.minDimension / 2f - sw / 2f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        drawCircle(Color.White, radius = radius - sw / 2f + 0.5f, center = center)
        drawArc(C.RingTrack, 0f, 360f, false, topLeft, arcSize, style = Stroke(width = sw))
        if (total > 0 && doneCount > 0) {
            drawArc(
                C.RingProgress,
                -90f,
                360f * doneCount / total,
                false,
                topLeft,
                arcSize,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
        }
    }
    RText("$doneCount/$total", 25, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 475.5, 1093.1, HAlign.Center))
    RText("Completed", 10.5, FontWeight.Normal, C.RingLabel, Modifier.atBaseline(ref, 476, 1110.8, HAlign.Center))

    if (habits.isEmpty()) {
        RText("কোনো হ্যাবিট নেই", 14, FontWeight.Normal, C.Hint, Modifier.atBaseline(ref, 655, 1080, HAlign.Center))
        AddLink(ref, "+ যোগ করুন", 655, 1112, C.HabCheck, onAdd)
        return
    }

    habits.take(5).forEachIndexed { i, habit ->
        val cy = 1031.5f + 36f * i
        val isDone = habit.id in done
        Box(
            Modifier
                .at(ref, 556, cy - 17f)
                .size(190.r, 34.r)
                .clip(RoundedCornerShape(8.r))
                .clickable { onToggle(habit.id) }
        )
        if (isDone) {
            Box(
                Modifier.atCenter(ref, 571.5, cy).size(18.r).background(C.HabCheck, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Check, Color.White, Modifier.size(14.r), strokeWidth = 3.0f)
            }
        } else {
            Box(Modifier.atCenter(ref, 571.5, cy).size(18.r).border(1.3.r, C.HabOpen, CircleShape))
        }
        HabitIcon(habit.icon, Modifier.atCenter(ref, 605, cy).size(21.r))
        RText(habit.name, 13, FontWeight.Normal, C.HabText, Modifier.atBaseline(ref, 626.5, cy + 5f).width(118.r))
    }
}

// ---------------------------------------------------------------- Quick Notes

@Composable
private fun NotesCard(ref: Ref, latest: NoteEntity?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(15.r)
    Box(
        Modifier
            .at(ref, 15.5, 1232)
            .size(574.r, 104.5.r)
            .cardSurface(shape, SolidColor(C.NotesBg), C.NotesBorder, 1.3.r, 3.r)
            .clip(shape)
            .clickable(onClick = onClick)
    )
    Box(
        Modifier
            .at(ref, 41, 1247)
            .size(35.r)
            .background(Brush.linearGradient(listOf(C.NotesIconA, C.NotesIconB)), RoundedCornerShape(8.r)),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.Note, Color.White, Modifier.size(25.r), strokeWidth = 1.9f)
    }
    RText("Quick Notes", 16.5, FontWeight.SemiBold, C.NotesTitle, Modifier.atBaseline(ref, 92, 1269.6))
    GlyphIcon(Glyph.ChevronRight, C.NotesChevron, Modifier.atCenter(ref, 556, 1264.5).size(21.r), strokeWidth = 2.0f)
    val text = latest?.text?.lineSequence()?.firstOrNull { it.isNotBlank() }
    if (text != null) {
        RText(text, 14, FontWeight.Normal, C.NotesText, Modifier.atBaseline(ref, 92, 1301.5).width(470.r))
    } else {
        RText("নোট লিখতে এখানে ট্যাপ করুন", 14, FontWeight.Normal, Color(0xFF9A8F72), Modifier.atBaseline(ref, 92, 1301.5))
    }
}

// ---------------------------------------------------------------- Bottom bar + FAB

enum class Tab { Home, Tasks, Money, More }

@Composable
fun BottomBar(heightRef: Float, selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val ref = LocalRef.current
    Box(
        modifier
            .fillMaxWidth()
            .height(heightRef.r)
            .background(C.NavBg)
    ) {
        Box(Modifier.fillMaxWidth().height(1.2.r).background(C.NavLine))
        val items = listOf(
            Triple(Tab.Home, "Home", 88f),
            Triple(Tab.Tasks, "Tasks", 279.5f),
            Triple(Tab.Money, "Money", 474.5f),
            Triple(Tab.More, "More", 674.5f),
        )
        items.forEach { (tab, label, cx) ->
            val active = tab == selected
            val glyph = when (tab) {
                Tab.Home -> Glyph.Home
                Tab.Tasks -> Glyph.TaskBox
                Tab.Money -> Glyph.WalletOutline
                Tab.More -> Glyph.Person
            }
            Box(
                Modifier
                    .at(ref, cx - 95f, 0)
                    .size(190.r, 125.r)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(tab) }
            )
            GlyphIcon(
                glyph,
                if (active) C.NavActive else C.NavIcon,
                Modifier.atCenter(ref, cx, 40).size(44.r),
                strokeWidth = 1.5f,
            )
            RText(
                label,
                17.5,
                if (active) FontWeight.Medium else FontWeight.Normal,
                if (active) C.NavActiveText else C.NavText,
                Modifier.atBaseline(ref, cx, 85.8, HAlign.Center),
            )
            if (active) {
                Box(
                    Modifier
                        .atCenter(ref, cx, 102)
                        .size(57.r, 4.r)
                        .background(C.NavActive, RoundedCornerShape(2.r))
                )
            }
        }
    }
}

@Composable
fun Fab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(96.r)
            .cardSurface(CircleShape, Brush.linearGradient(listOf(C.FabA, C.FabB)), Color.Transparent, 0.r, 14.r, C.ShadowGreen)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.Plus, Color.White, Modifier.size(48.r), strokeWidth = 2.4f)
    }
}
