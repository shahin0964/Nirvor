package com.nirvor.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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

import androidx.compose.ui.platform.LocalContext
import com.nirvor.app.islamic.Hijri
import com.nirvor.app.islamic.IslamicPrefs
import com.nirvor.app.islamic.Prayer
import com.nirvor.app.islamic.PrayerTimes
import com.nirvor.app.ui.money.monthNameBn
import java.time.LocalDate
import java.time.ZoneId

/** Extra vertical spacing introduced by the new Islamic wide card below TodayCard. */
private const val CARD_SHIFT = 216f

/** Height of the home page in reference px (top of screen to top of the bottom bar). */
private const val PAGE_HEIGHT = 1354f + CARD_SHIFT

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
    onOpenIslamic: () -> Unit,
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
    val monthExpenses by vm.thisMonthExpenses.collectAsStateWithLifecycle()
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
                weather = weather,
                onProfile = onOpenProfile,
                onSettings = onOpenSideBar,
                onWeatherTap = onWeatherTap,
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
                habitsDone = habits.count { it.id in doneHabits },
                habitsTotal = habits.size,
                streak = streak,
                onDate = onOpenTasks,
                onHabits = onOpenHabits,
            )
            IslamicTodayCard(ref, date = now.toLocalDate(), onClick = onOpenIslamic)
            TasksCard(ref, tasks, onToggle = { vm.toggleTask(it) }, onViewAll = onOpenTasks, onAdd = onAddTask)
            RemindersCard(ref, upcoming, today = now.toLocalDate().toEpochDay(), onViewAll = onOpenReminders, onAdd = onAddReminder)
            ExpenseCard(ref, monthExpenses, monthName = monthNameBn(now.monthValue), onViewAll = onOpenMoney)
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
    weather: WeatherUi,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onWeatherTap: () -> Unit,
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
        Modifier.atBaseline(ref, 29, 161.9).widthIn(max = 530.r),
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

    // Weather Chip in Header (Compact weather temperature and condition)
    val weatherDark = dark > 0.5f
    val weatherPillBg = if (weatherDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.16f)
    val weatherBorder = if (weatherDark) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.45f)

    Box(
        Modifier
            .at(ref, 572, 160)
            .size(172.r, 50.r)
            .clip(RoundedCornerShape(25.r))
            .background(weatherPillBg)
            .border(1.2.r, weatherBorder, RoundedCornerShape(25.r))
            .clickable(onClick = onWeatherTap)
            .padding(horizontal = 10.r),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.r),
        ) {
            GlyphIcon(
                Glyph.Sun,
                if (weatherDark) Color(0xFFFFD54F) else C.Sun,
                Modifier.size(24.r),
                strokeWidth = 1.6f,
                accent = C.SunFill,
            )
            Column(verticalArrangement = Arrangement.Center) {
                RText(
                    weather.temp.ifBlank { "--°C" },
                    17,
                    FontWeight.Bold,
                    ink,
                    shadow = glow,
                )
                if (weather.desc.isNotBlank()) {
                    RText(
                        weather.desc,
                        11,
                        FontWeight.Medium,
                        ink.copy(alpha = 0.85f),
                        maxLines = 1,
                        shadow = glow,
                    )
                }
            }
        }
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
    habitsDone: Int,
    habitsTotal: Int,
    streak: Int,
    onDate: () -> Unit,
    onHabits: () -> Unit,
) {
    val shape = RoundedCornerShape(24.r)
    Box(
        Modifier
            .at(ref, 21, 264)
            .size(728.r, 251.5.r)
            .cardSurface(shape, SolidColor(Color.White), C.TodayBorder, 1.2.r, 8.r)
    )
    Box(Modifier.atCenter(ref, 64.5, 299).size(50.r).background(C.CalBg, CircleShape))
    GlyphIcon(Glyph.Calendar, C.CalIcon, Modifier.atCenter(ref, 64.5, 299).size(32.r), strokeWidth = 1.75f)
    RText("Today", 23, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 103, 306.4))

    // Interactive Date Pill
    Box(
        Modifier
            .at(ref, 530, 276)
            .size(205.r, 46.r)
            .clip(RoundedCornerShape(14.r))
            .background(C.CalBg)
            .border(1.r, C.Divider, RoundedCornerShape(14.r))
            .clickable(onClick = onDate)
            .padding(horizontal = 12.r),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.r),
        ) {
            RText(dateText, 16.5, FontWeight.Medium, C.Date)
            GlyphIcon(Glyph.ChevronRight, C.DateChevron, Modifier.size(16.r), strokeWidth = 2.2f)
        }
    }

    val cols = listOf(114.5f, 292.5f, 476.5f, 660.5f)
    listOf(206f, 384f, 568f).forEach { x ->
        Box(Modifier.at(ref, x, 344).size(1.2.r, 145.r).background(C.Divider))
    }

    // 1. Completed
    StatCircle(ref, cols[0], C.GreenA, C.GreenB) {
        GlyphIcon(Glyph.Check, Color.White, Modifier.size(26.r), strokeWidth = 2.7f)
    }
    StatTexts(ref, cols[0], "$completed", "Completed", "কাজ সম্পন্ন")
    Box(
        Modifier
            .at(ref, cols[0] - 88f, 336)
            .size(176.r, 160.r)
            .clip(RoundedCornerShape(16.r))
            .clickable(onClick = onDate)
    )

    // 2. Remaining
    StatCircle(ref, cols[1], C.OrangeA, C.OrangeB) {
        GlyphIcon(Glyph.ClockHands, Color.White, Modifier.size(26.r), strokeWidth = 2.7f)
    }
    StatTexts(ref, cols[1], "$remaining", "Remaining", "বাকি কাজ")
    Box(
        Modifier
            .at(ref, cols[1] - 88f, 336)
            .size(176.r, 160.r)
            .clip(RoundedCornerShape(16.r))
            .clickable(onClick = onDate)
    )

    // 3. Habits Done
    StatCircle(ref, cols[2], Color(0xFF06B6D4), Color(0xFF0891B2)) {
        GlyphIcon(Glyph.Target, Color.White, Modifier.size(26.r), strokeWidth = 2.2f)
    }
    StatTexts(ref, cols[2], "$habitsDone/$habitsTotal", "Habits", "হ্যাবিট সম্পন্ন")
    Box(
        Modifier
            .at(ref, cols[2] - 88f, 336)
            .size(176.r, 160.r)
            .clip(RoundedCornerShape(16.r))
            .clickable(onClick = onHabits)
    )

    // 4. Day streak
    StatCircle(ref, cols[3], C.RedA, C.RedB) {
        GlyphIcon(Glyph.Flame, Color.White, Modifier.size(29.r), accent = Color(0xFFFA5446))
    }
    StatTexts(ref, cols[3], "$streak", "Streak", "ধারাবাহিকতা")
    Box(
        Modifier
            .at(ref, cols[3] - 88f, 336)
            .size(176.r, 160.r)
            .clip(RoundedCornerShape(16.r))
            .clickable(onClick = onHabits)
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

// ---------------------------------------------------------------- Islamic Wide Card (Today)

@Composable
private fun IslamicTodayCard(ref: Ref, date: LocalDate, onClick: () -> Unit) {
    val context = LocalContext.current
    val hijri = remember(date) { Hijri.of(date) }
    val islamicPrefs = remember { IslamicPrefs(context) }
    val dayTimes = remember(date) {
        islamicPrefs.times(date) ?: PrayerTimes.day(date, 23.8103, 90.4125, ZoneId.systemDefault())
    }
    val shape = RoundedCornerShape(22.r)
    Box(
        Modifier
            .at(ref, 21, 532.5)
            .size(728.r, 196.r)
            .cardSurface(
                shape,
                Brush.verticalGradient(listOf(Color.White, Color(0xFFF0FDF4))),
                Color(0xFFBBF7D0),
                1.2.r,
                6.r,
            )
            .clip(shape)
            .clickable(onClick = onClick)
    )

    HeaderIcon(ref, 57.5, 565.5, 46, Color(0xFF0E9F6E), Color(0xFF047857)) {
        GlyphIcon(Glyph.MoonStar, Color.White, Modifier.size(26.r), strokeWidth = 2.0f)
    }

    RText("Islamic Times & Fasting", 19, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 96, 571.8))
    val hijriSub = if (hijri.month == 9) "${hijri.text} · 🌙 পবিত্র মাহে রমজান" else hijri.text
    RText(hijriSub, 14.5, FontWeight.Medium, Color(0xFF047857), Modifier.atBaseline(ref, 96, 597.5))

    val fajrTime = Fmt.timeAt(dayTimes[Prayer.Fajr])
    val maghribTime = Fmt.timeAt(dayTimes[Prayer.Maghrib])
    val nowMillis = System.currentTimeMillis()

    val nextBadge = when {
        nowMillis < dayTimes[Prayer.Fajr] -> "পরবর্তী: ফজর ($fajrTime)"
        nowMillis < dayTimes[Prayer.Sunrise] -> "সূর্যোদয়: ${Fmt.timeAt(dayTimes[Prayer.Sunrise])}"
        nowMillis < dayTimes[Prayer.Dhuhr] -> "পরবর্তী: যোহর (${Fmt.timeAt(dayTimes[Prayer.Dhuhr])})"
        nowMillis < dayTimes[Prayer.Asr] -> "পরবর্তী: আসর (${Fmt.timeAt(dayTimes[Prayer.Asr])})"
        nowMillis < dayTimes[Prayer.Maghrib] -> {
            val mins = ((dayTimes[Prayer.Maghrib] - nowMillis) / 60_000L).coerceAtLeast(1L)
            "ইফতারের বাকি $mins মিনিট"
        }
        nowMillis < dayTimes[Prayer.Isha] -> "পরবর্তী: এশা (${Fmt.timeAt(dayTimes[Prayer.Isha])})"
        else -> "পরবর্তী: ফজর ($fajrTime)"
    }

    Box(
        Modifier
            .at(ref, 462, 548)
            .clip(RoundedCornerShape(20.r))
            .background(Color(0xFFE6F4EA))
            .border(1.r, Color(0xFFA7F3D0), RoundedCornerShape(20.r))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.r, vertical = 6.r),
    ) {
        RText(nextBadge, 13.5, FontWeight.SemiBold, Color(0xFF065F46))
    }

    // 4 Column Box inside
    Box(
        Modifier
            .at(ref, 36, 614)
            .size(698.r, 96.r)
            .background(Color.White, RoundedCornerShape(14.r))
            .border(1.r, Color(0xFFDCFCE7), RoundedCornerShape(14.r))
    )
    Box(Modifier.at(ref, 210, 626).size(1.2.r, 72.r).background(Color(0xFFE2E8F0)))
    Box(Modifier.at(ref, 385, 626).size(1.2.r, 72.r).background(Color(0xFFE2E8F0)))
    Box(Modifier.at(ref, 550, 626).size(1.2.r, 72.r).background(Color(0xFFE2E8F0)))

    // Col 1: সেহরি শেষ (Fajr)
    Box(Modifier.atCenter(ref, 54, 638).size(9.r).background(Color(0xFF10B981), CircleShape))
    RText("সেহরি শেষ", 14, FontWeight.Normal, C.CatLabel, Modifier.atBaseline(ref, 68, 642.5))
    RText(fajrTime, 16, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 54, 682))

    // Col 2: ইফতারের সময় (Maghrib)
    Box(Modifier.atCenter(ref, 228, 638).size(9.r).background(Color(0xFFF97316), CircleShape))
    RText("ইফতারের সময়", 14, FontWeight.Normal, C.CatLabel, Modifier.atBaseline(ref, 242, 642.5))
    RText(maghribTime, 16, FontWeight.Bold, Color(0xFFEA580C), Modifier.atBaseline(ref, 228, 682))

    // Col 3: যোহর / নামাজের সময়
    val dhuhr = Fmt.timeAt(dayTimes[Prayer.Dhuhr])
    Box(Modifier.atCenter(ref, 403, 638).size(9.r).background(Color(0xFF2563EB), CircleShape))
    RText("যোহরের সময়", 14, FontWeight.Normal, C.CatLabel, Modifier.atBaseline(ref, 417, 642.5))
    RText(dhuhr, 16, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 403, 682))

    // Col 4: ইসলামিক টুলস (কিবলা ও কুরআন)
    Box(Modifier.atCenter(ref, 568, 638).size(9.r).background(Color(0xFF8B5CF6), CircleShape))
    RText("টুলস ও দোয়া", 14, FontWeight.Normal, C.CatLabel, Modifier.atBaseline(ref, 582, 642.5))
    RText("কিবলা · কুরআন", 14.5, FontWeight.SemiBold, Color(0xFF047857), Modifier.atBaseline(ref, 568, 682))
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
    val shape = RoundedCornerShape(18.r)
    Box(
        Modifier
            .at(ref, 15.5, 532.5 + CARD_SHIFT)
            .size(371.5.r, 391.r)
            .cardSurface(shape, SolidColor(C.TasksBg), C.TasksBorder, 1.2.r, 4.r)
    )
    HeaderIcon(ref, 57.5, 575 + CARD_SHIFT, 46, C.TaskGreenA, C.TaskGreenB) {
        GlyphIcon(Glyph.Check, Color.White, Modifier.size(28.r), strokeWidth = 2.6f)
    }
    RText("Today's Tasks", 19.5, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 96, 581.6 + CARD_SHIFT))
    ViewAll(ref, 301.5, 578.6 + CARD_SHIFT, 14, C.TaskViewAll, C.TaskViewAllChevron, 365.5, 573.8 + CARD_SHIFT, onViewAll)

    if (tasks.isEmpty()) {
        Box(
            Modifier
                .atCenter(ref, 201, 675 + CARD_SHIFT)
                .size(60.r)
                .background(Color(0xFFE8F7F0), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Check, C.TaskGreenA, Modifier.size(28.r), strokeWidth = 2.6f)
        }
        RText("আজকের কোনো কাজ নেই", 15, FontWeight.Medium, C.Hint, Modifier.atBaseline(ref, 201, 735 + CARD_SHIFT, HAlign.Center))
        Box(
            Modifier
                .atCenter(ref, 201, 785 + CARD_SHIFT)
                .clip(RoundedCornerShape(20.r))
                .background(Brush.horizontalGradient(listOf(C.TaskGreenA, C.TaskGreenB)))
                .clickable(onClick = onAdd)
                .padding(horizontal = 18.r, vertical = 9.r),
            contentAlignment = Alignment.Center,
        ) {
            RText("+ কাজ যোগ করুন", 14.5, FontWeight.SemiBold, Color.White)
        }
        return
    }

    tasks.take(4).forEachIndexed { i, task ->
        val titleBase = 640.5f + CARD_SHIFT + 73f * i
        val timeBase = 667.1f + CARD_SHIFT + 73f * i
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
            Box(Modifier.at(ref, 91, 684f + CARD_SHIFT + 73f * i).size(266.5.r, 1.2.r).background(C.TaskDivider))
        }
    }
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
    val shape = RoundedCornerShape(18.r)
    Box(
        Modifier
            .at(ref, 402, 532.5 + CARD_SHIFT)
            .size(350.5.r, 391.r)
            .cardSurface(shape, SolidColor(C.RemBg), C.RemBorder, 1.2.r, 4.r)
    )
    HeaderIcon(ref, 442, 575 + CARD_SHIFT, 46, C.PurpleA, C.PurpleB) {
        GlyphIcon(Glyph.Bell, Color.White, Modifier.size(26.r))
    }
    RText("Reminders", 19.5, FontWeight.Bold, C.InkNavy, Modifier.atBaseline(ref, 478, 581.6 + CARD_SHIFT))
    ViewAll(ref, 667, 578.6 + CARD_SHIFT, 14, C.RemViewAll, C.RemViewAll, 728.5, 573.8 + CARD_SHIFT, onViewAll)

    if (upcoming.isEmpty()) {
        Box(
            Modifier
                .atCenter(ref, 577, 675 + CARD_SHIFT)
                .size(60.r)
                .background(Color(0xFFF3EDFD), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Bell, C.PurpleA, Modifier.size(28.r))
        }
        RText("কোনো রিমাইন্ডার নেই", 15, FontWeight.Medium, C.Hint, Modifier.atBaseline(ref, 577, 735 + CARD_SHIFT, HAlign.Center))
        Box(
            Modifier
                .atCenter(ref, 577, 785 + CARD_SHIFT)
                .clip(RoundedCornerShape(20.r))
                .background(Brush.horizontalGradient(listOf(C.PurpleA, C.PurpleB)))
                .clickable(onClick = onAdd)
                .padding(horizontal = 18.r, vertical = 9.r),
            contentAlignment = Alignment.Center,
        ) {
            RText("+ রিমাইন্ডার যোগ করুন", 14.5, FontWeight.SemiBold, Color.White)
        }
        return
    }

    val shown = upcoming.take(4)
    shown.forEachIndexed { i, item ->
        val cy = 661f + CARD_SHIFT + 72f * i
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

// ---------------------------------------------------------------- Monthly Expense

@Composable
private fun ExpenseCard(
    ref: Ref,
    expenses: List<ExpenseEntity>,
    monthName: String,
    onViewAll: () -> Unit,
) {
    val shape = RoundedCornerShape(18.r)
    Box(
        Modifier
            .at(ref, 15.5, 940.5 + CARD_SHIFT)
            .size(370.5.r, 275.r)
            .cardSurface(shape, Brush.verticalGradient(listOf(C.ExpBgTop, C.ExpBgBottom)), C.ExpBorder, 1.2.r, 4.r)
    )
    HeaderIcon(ref, 57.5, 980.5 + CARD_SHIFT, 46, C.OrangeIconA, C.OrangeIconB) {
        GlyphIcon(Glyph.Wallet, Color.White, Modifier.size(26.r), accent = Color(0xFFF96A31))
    }
    RText("Monthly Expense", 19, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 96, 985.8 + CARD_SHIFT))
    ViewAll(ref, 302, 985.9 + CARD_SHIFT, 14.5, C.ViewAllGrey, C.ViewAllGreyChevron, 365.5, 980.8 + CARD_SHIFT, onViewAll)

    val total = expenses.sumOf { it.amount }
    Row(
        Modifier.atBaseline(ref, 33, 1056.3 + CARD_SHIFT),
        verticalAlignment = Alignment.Bottom,
    ) {
        RText("৳", 31, FontWeight.Bold, Color(0xFFEA580C))
        Spacer(Modifier.width(6.r))
        RText(Fmt.money(total), 31, FontWeight.Bold, C.Ink, Modifier.widthIn(max = 280.r))
    }
    RText("$monthName মাসের মোট খরচ", 15, FontWeight.Normal, C.ExpSub, Modifier.atBaseline(ref, 33, 1087.5 + CARD_SHIFT))

    Box(
        Modifier
            .at(ref, 28, 1110 + CARD_SHIFT)
            .size(345.5.r, 91.5.r)
            .background(Color.White, RoundedCornerShape(14.r))
            .border(1.r, C.InnerBorder, RoundedCornerShape(14.r))
    )
    Box(Modifier.at(ref, 134, 1131 + CARD_SHIFT).size(1.2.r, 50.r).background(C.InnerDivider))
    Box(Modifier.at(ref, 258, 1131 + CARD_SHIFT).size(1.2.r, 50.r).background(C.InnerDivider))

    val columns = listOf(
        Triple(ExpenseCategory.Food, 44f, 59f),
        Triple(ExpenseCategory.Transport, 163.5f, 179f),
        Triple(ExpenseCategory.Other, 288.5f, 304f),
    )
    columns.forEach { (cat, dotX, textX) ->
        val sum = expenses.filter { ExpenseCategory.of(it.category) == cat }.sumOf { it.amount }
        Box(Modifier.atCenter(ref, dotX, 1143 + CARD_SHIFT).size(9.5.r).background(Color(cat.color), CircleShape))
        RText(cat.label, 14, FontWeight.Normal, C.CatLabel, Modifier.atBaseline(ref, textX, 1148.5 + CARD_SHIFT))
        RText("৳", 14.5, FontWeight.Medium, C.CatAmount, Modifier.atBaseline(ref, textX, 1176.7 + CARD_SHIFT))
        RText(Fmt.money(sum), 14.5, FontWeight.Medium, C.CatAmount, Modifier.atBaseline(ref, textX + 15f, 1176.7 + CARD_SHIFT).width(82.r))
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
    val shape = RoundedCornerShape(18.r)
    Box(
        Modifier
            .at(ref, 401, 940.5 + CARD_SHIFT)
            .size(351.5.r, 275.r)
            .cardSurface(shape, SolidColor(C.HabBg), C.HabBorder, 1.2.r, 4.r)
    )
    HeaderIcon(ref, 441.5, 980.5 + CARD_SHIFT, 46, C.HabGreenA, C.HabGreenB) {
        GlyphIcon(Glyph.Target, Color.White, Modifier.size(28.r), strokeWidth = 1.9f)
    }
    RText("Today's Habit", 19, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 480, 986.6 + CARD_SHIFT))
    ViewAll(ref, 667, 985.9 + CARD_SHIFT, 14.5, C.ViewAllGrey, C.ViewAllGreyChevron, 731, 980.8 + CARD_SHIFT, onViewAll)

    val total = habits.size
    val doneCount = habits.count { it.id in done }
    val ringStroke = 9.5f
    Canvas(Modifier.atCenter(ref, 475.5, 1087 + CARD_SHIFT).size(113.r)) {
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
    RText("$doneCount/$total", 24, FontWeight.Bold, C.Ink, Modifier.atBaseline(ref, 475.5, 1093.1 + CARD_SHIFT, HAlign.Center))
    RText("Completed", 10.5, FontWeight.Normal, C.RingLabel, Modifier.atBaseline(ref, 476, 1110.8 + CARD_SHIFT, HAlign.Center))

    if (habits.isEmpty()) {
        RText("কোনো হ্যাবিট নেই", 14.5, FontWeight.Medium, C.Hint, Modifier.atBaseline(ref, 655, 1070 + CARD_SHIFT, HAlign.Center))
        Box(
            Modifier
                .atCenter(ref, 655, 1110 + CARD_SHIFT)
                .clip(RoundedCornerShape(16.r))
                .background(Brush.horizontalGradient(listOf(C.HabGreenA, C.HabGreenB)))
                .clickable(onClick = onAdd)
                .padding(horizontal = 14.r, vertical = 7.r),
            contentAlignment = Alignment.Center,
        ) {
            RText("+ যোগ করুন", 13.5, FontWeight.SemiBold, Color.White)
        }
        return
    }

    habits.take(5).forEachIndexed { i, habit ->
        val cy = 1031.5f + CARD_SHIFT + 36f * i
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
    val shape = RoundedCornerShape(18.r)
    Box(
        Modifier
            .at(ref, 15.5, 1232 + CARD_SHIFT)
            .size(574.r, 104.5.r)
            .cardSurface(shape, SolidColor(C.NotesBg), C.NotesBorder, 1.2.r, 3.r)
            .clip(shape)
            .clickable(onClick = onClick)
    )
    Box(
        Modifier
            .at(ref, 38, 1247 + CARD_SHIFT)
            .size(38.r)
            .background(Brush.linearGradient(listOf(C.NotesIconA, C.NotesIconB)), RoundedCornerShape(10.r)),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(Glyph.Note, Color.White, Modifier.size(24.r), strokeWidth = 1.9f)
    }
    RText("Quick Notes", 17, FontWeight.Bold, C.NotesTitle, Modifier.atBaseline(ref, 88, 1269.6 + CARD_SHIFT))
    GlyphIcon(Glyph.ChevronRight, C.NotesChevron, Modifier.atCenter(ref, 554, 1264.5 + CARD_SHIFT).size(20.r), strokeWidth = 2.0f)
    val text = latest?.text?.lineSequence()?.firstOrNull { it.isNotBlank() }
    if (text != null) {
        RText(text, 14, FontWeight.Normal, C.NotesText, Modifier.atBaseline(ref, 88, 1301.5 + CARD_SHIFT).width(470.r))
    } else {
        RText("নোট লিখতে এখানে ট্যাপ করুন...", 14, FontWeight.Normal, Color(0xFF9A8F72), Modifier.atBaseline(ref, 88, 1301.5 + CARD_SHIFT))
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
