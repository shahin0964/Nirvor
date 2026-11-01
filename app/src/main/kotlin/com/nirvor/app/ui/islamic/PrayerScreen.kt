package com.nirvor.app.ui.islamic

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.NirvorApplication
import com.nirvor.app.data.PrayerLogEntity
import com.nirvor.app.data.QazaEntity
import com.nirvor.app.islamic.AdhanSound
import com.nirvor.app.islamic.Hijri
import com.nirvor.app.islamic.IslamicPrefs
import com.nirvor.app.islamic.Prayer
import com.nirvor.app.islamic.PrayerAlarms
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.reminders.Notifications
import com.nirvor.app.reminders.ReminderScheduler
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.sheets.pickTime
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.ToolScaffold
import com.nirvor.app.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PRAYER_EMOJI = mapOf(
    Prayer.Fajr to "🌄", Prayer.Sunrise to "🌅", Prayer.Dhuhr to "☀️",
    Prayer.Asr to "🌤️", Prayer.Maghrib to "🌇", Prayer.Isha to "🌙",
)

/** Tracker states: 1 prayed, 2 in jama'ah, 3 missed (qaza). */
internal val STATUS_LABEL = mapOf(1 to "✓ পড়েছি", 2 to "👥 জামাতে", 3 to "✕ কাজা")
internal val STATUS_COLOR = mapOf(1 to Color(0xFF0E9F6E), 2 to Color(0xFF168FFB), 3 to Color(0xFFE5484D))

private val DAY_FMT = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

/**
 * Saves a prayer's tracker state. Marking it missed adds one to that prayer's qaza;
 * changing a missed one back takes it off again.
 */
internal suspend fun setPrayerStatus(app: NirvorApplication, day: Long, prayer: Int, status: Int?) {
    val dao = app.db.islamicDao()
    val old = dao.prayerLog(day, prayer)?.status
    if (old == status) return
    val q = dao.qazaOf(prayer) ?: QazaEntity(prayer)
    if (old == 3) dao.putQaza(QazaEntity(prayer, (q.pending - 1).coerceAtLeast(0), q.done))
    if (status == 3) dao.putQaza((dao.qazaOf(prayer) ?: QazaEntity(prayer)).let { QazaEntity(prayer, it.pending + 1, it.done) })
    if (status == null) dao.deletePrayerLog(day, prayer) else dao.putPrayerLog(PrayerLogEntity(day, prayer, status))
}

/** Prayer times: next prayer with a countdown ring, the day's times, adhan, tracker, jama'ah. */
@Composable
fun PrayerScreen(onBack: () -> Unit, onOpen: (IslamicTool) -> Unit, onLocation: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as NirvorApplication
    val prefs = remember { IslamicPrefs(context) }
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    var adhanMask by remember { mutableIntStateOf(prefs.adhanMask) }
    var notifOk by remember { mutableStateOf(Notifications.canPost(context)) }
    val notifAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOk = it }

    LaunchedEffect(Unit) {
        if (!prefs.prayerSetUp) {
            prefs.prayerSetUp = true
            if (!notifOk && Build.VERSION.SDK_INT >= 33) notifAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PrayerAlarms.reschedule(context)
    }
    fun changed() {
        version++
        PrayerAlarms.reschedule(context)
    }

    val today = LocalDate.now()
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(day)
    val now = rememberNow()
    val place = remember { prefs.place() }
    val times = remember(day, version, place) { prefs.times(date) }
    val pn = remember(now / 60_000, version, place) { prayerNow(prefs, now) }
    val logs by app.db.islamicDao().prayerLogsFrom(today.toEpochDay() - 6).collectAsStateWithLifecycle(emptyList())
    val qaza by app.db.islamicDao().qaza().collectAsStateWithLifecycle(emptyList())

    ToolScaffold(
        "নামাজের সময়",
        place?.let { "📍 ${it.name}" } ?: "লোকেশন নেই",
        onBack,
        actions = { RoundIcon("⚙️", { onOpen(IslamicTool.NamazSettings) }) },
    ) {
        if (times == null || pn == null) {
            ListCard {
                RText("📍 লোকেশন পাওয়া যায়নি", 21, FontWeight.SemiBold, C.Ink)
                Spacer(Modifier.height(6.r))
                RText("আবহাওয়ার লোকেশনে শহর বাছুন বা ফোনের লোকেশন চালু করুন, তারপর এখানে ফিরে আসুন।", 17, FontWeight.Normal, C.StatLabel, maxLines = 3, lineHeight = 25)
                Spacer(Modifier.height(14.r))
                RoundButton("লোকেশন ঠিক করুন", IslamicA, Modifier.fillMaxWidth(), onClick = onLocation)
            }
            return@ToolScaffold
        }

        if (day == today.toEpochDay()) NextPrayerCard(pn, now)

        // day switcher
        val hijri = remember(day, version) { Hijri.of(date, prefs.hijriAdjust) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIcon("‹", { day-- }, C.CalBg)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                RText(if (day == today.toEpochDay()) "আজ · ${date.format(DAY_FMT)}" else date.format(DAY_FMT), 19, FontWeight.SemiBold, C.Ink)
                RText(hijri.text, 15, FontWeight.Medium, IslamicB)
            }
            RoundIcon("›", { day++ }, C.CalBg)
        }

        // the day's times
        ListCard {
            Prayer.entries.forEachIndexed { k, p ->
                if (k > 0) Box(Modifier.fillMaxWidth().height(1.r).background(C.Divider))
                val at = times[p]
                val running = day == today.toEpochDay() && pn.current == p
                val index = Prayer.five.indexOf(p)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.r))
                        .background(if (running) Mint else Color.Transparent)
                        .padding(horizontal = 8.r, vertical = 12.r),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RText(PRAYER_EMOJI[p] ?: "", 26, FontWeight.Normal, C.Ink)
                    Spacer(Modifier.width(12.r))
                    Column(Modifier.weight(1f)) {
                        val name = if (p == Prayer.Dhuhr && date.dayOfWeek == DayOfWeek.FRIDAY) "জুমা" else p.bn
                        RText(name, 20, if (p.obligatory) FontWeight.SemiBold else FontWeight.Normal, if (p.obligatory) C.Ink else C.StatLabel)
                        if (running) RText("এখন চলছে", 14, FontWeight.SemiBold, IslamicA)
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        RText(clock(at), 22, FontWeight.Bold, if (p.obligatory) C.Ink else C.StatLabel)
                        Spacer(Modifier.width(4.r))
                        RText(ampm(at), 13, FontWeight.Medium, C.Hint, Modifier.padding(bottom = 3.r))
                    }
                    if (index >= 0) {
                        Spacer(Modifier.width(8.r))
                        val on = adhanMask and (1 shl index) != 0
                        Box(
                            Modifier
                                .size(44.r)
                                .clip(CircleShape)
                                .clickable {
                                    adhanMask = adhanMask xor (1 shl index)
                                    prefs.adhanMask = adhanMask
                                    changed()
                                    Toast.makeText(context, if (!on) "${p.bnOf} আজানের নোটিফিকেশন চালু" else "${p.bnOf} আজানের নোটিফিকেশন বন্ধ", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center,
                        ) { RText(if (on) "🔔" else "🔕", 20, FontWeight.Normal, C.Ink) }
                        val status = logs.firstOrNull { it.epochDay == day && it.prayer == index }?.status
                        val reached = at <= now
                        TrackChip(status, reached) {
                            val next = when (status) {
                                null -> 1
                                1 -> 2
                                2 -> 3
                                else -> null
                            }
                            scope.launch(Dispatchers.IO) { setPrayerStatus(app, day, index, next) }
                        }
                    } else {
                        Spacer(Modifier.width(52.r + 96.r))
                    }
                }
            }
        }
        RText("🔔 চাপলে আজানের নোটিফিকেশন চালু/বন্ধ · ডানের ঘরে চাপ দিয়ে লিখুন: পড়েছি → জামাতে → কাজা", 14, FontWeight.Normal, C.Hint, maxLines = 2, lineHeight = 20)

        if (!notifOk) {
            ListCard {
                RText("🔕 নোটিফিকেশন বন্ধ", 19, FontWeight.SemiBold, C.Ink)
                RText("আজান আর জামাতের রিমাইন্ডার পেতে নোটিফিকেশনের অনুমতি দিন।", 16, FontWeight.Normal, C.StatLabel, maxLines = 2)
                Spacer(Modifier.height(10.r))
                RoundButton("অনুমতি দিন", IslamicA, Modifier.fillMaxWidth()) {
                    if (Build.VERSION.SDK_INT >= 33) notifAsk.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else if (!ReminderScheduler.canScheduleExact(context) && Build.VERSION.SDK_INT >= 31) {
            ListCard {
                RText("⏰ ঠিক সময়ে বাজার অনুমতি", 19, FontWeight.SemiBold, C.Ink)
                RText("এটা না দিলে আজানের নোটিফিকেশন কয়েক মিনিট দেরিতে আসতে পারে।", 16, FontWeight.Normal, C.StatLabel, maxLines = 2)
                Spacer(Modifier.height(10.r))
                RoundButton("অনুমতি দিন", IslamicA, Modifier.fillMaxWidth()) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)))
                    } catch (e: Exception) {
                        // no such screen on this phone
                    }
                }
            }
        }

        WeekTracker(logs, today)
        val pending = qaza.sumOf { it.pending }
        ListCard(onClick = { onOpen(IslamicTool.Qaza) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("📋", 30, FontWeight.Normal, C.Ink)
                Spacer(Modifier.width(14.r))
                Column(Modifier.weight(1f)) {
                    RText("কাজা নামাজ", 20, FontWeight.SemiBold, C.Ink)
                    RText(if (pending == 0) "কোনো কাজা বাকি নেই" else "${bnNum(pending)} ওয়াক্ত আদায় বাকি", 16, FontWeight.Normal, if (pending == 0) IslamicA else C.Danger)
                }
                RText("›", 30, FontWeight.Normal, C.Hint)
            }
        }
        AdhanSoundCard(prefs) { changed() }
        JamaatCard(prefs, times, date) { changed() }
        RText(
            "সময় হিসাব হয়: ${prefs.method.bn} · আসর ${if (prefs.hanafiAsr) "হানাফি" else "শাফেয়ি"} মতে। মসজিদের সময়ের সাথে ১-২ মিনিট পার্থক্য হলে ⚙️ থেকে মিলিয়ে নিন।",
            14,
            FontWeight.Normal,
            C.Hint,
            maxLines = 4,
            lineHeight = 20,
        )
    }
}

/** The tracker chip of one prayer row. */
@Composable
private fun TrackChip(status: Int?, reached: Boolean, onClick: () -> Unit) {
    val color = status?.let { STATUS_COLOR[it] } ?: C.Hint
    Box(
        Modifier
            .width(96.r)
            .height(40.r)
            .clip(RoundedCornerShape(50))
            .background(if (status != null) color.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.4.r, if (status != null) color else C.FieldLine, RoundedCornerShape(50))
            .clickable(enabled = reached || status != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        RText(status?.let { STATUS_LABEL[it] } ?: if (reached) "লিখুন" else "—", 14, FontWeight.SemiBold, if (status != null) color else C.Hint)
    }
}

/** Big next-prayer card: a ring filling up from the last prayer to the next, with the time left. */
@Composable
private fun NextPrayerCard(pn: PrayerNow, now: Long) {
    val span = (pn.nextAt - pn.currentStart).coerceAtLeast(1)
    val done = ((now - pn.currentStart).toFloat() / span).coerceIn(0f, 1f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.r))
            .background(Brush.linearGradient(listOf(IslamicA, IslamicB)))
            .padding(22.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(136.r), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(136.r)) {
                val sw = size.width * 0.085f
                drawArc(Color.White.copy(alpha = 0.18f), -90f, 360f, false, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw))
                drawArc(Gold, -90f, 360f * done, false, topLeft = Offset(sw / 2, sw / 2), size = Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RText(PRAYER_EMOJI[pn.next] ?: "", 30, FontWeight.Normal, Color.White)
                RText("${bnNum((done * 100).toInt())}%", 14, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f))
            }
        }
        Spacer(Modifier.width(20.r))
        Column(Modifier.weight(1f)) {
            RText(if (pn.next == Prayer.Sunrise) "ফজরের ওয়াক্ত শেষ" else "পরের নামাজ", 16, FontWeight.Medium, Color.White.copy(alpha = 0.8f))
            RText(if (pn.next == Prayer.Sunrise) "সূর্যোদয় ${clock(pn.nextAt)}" else "${pn.next.bn} ${clock(pn.nextAt)}", 26, FontWeight.Bold, Color.White)
            Spacer(Modifier.height(6.r))
            RText(countdown(pn.nextAt - now), 34, FontWeight.Bold, Gold)
            RText("${leftText(pn.nextAt - now)} বাকি", 15, FontWeight.Medium, Color.White.copy(alpha = 0.8f), maxLines = 2)
        }
    }
}

/** Last 7 days × 5 prayers as coloured dots. */
@Composable
private fun WeekTracker(logs: List<PrayerLogEntity>, today: LocalDate) {
    ListCard {
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val marked = logs.count { it.status == 1 || it.status == 2 }
        RText("📊 গত ৭ দিন", 19, FontWeight.SemiBold, C.Ink)
        RText("${bnNum(marked)} / ${bnNum(35)} ওয়াক্ত পড়া হয়েছে", 15, FontWeight.Normal, C.StatLabel)
        Spacer(Modifier.height(12.r))
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(56.r))
            days.forEach { d ->
                RText(d.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)), 13, FontWeight.Medium, C.Hint, Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Prayer.five.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.r), verticalAlignment = Alignment.CenterVertically) {
                RText(p.bn, 14, FontWeight.Medium, C.StatLabel, Modifier.width(56.r))
                days.forEach { d ->
                    val s = logs.firstOrNull { it.epochDay == d.toEpochDay() && it.prayer == i }?.status
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(20.r)
                                .clip(CircleShape)
                                .background(s?.let { STATUS_COLOR[it] } ?: C.CalBg),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.r))
        Row(horizontalArrangement = Arrangement.spacedBy(14.r)) {
            listOf(1 to "পড়েছি", 2 to "জামাতে", 3 to "কাজা").forEach { (s, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.r).clip(CircleShape).background(STATUS_COLOR[s] ?: C.Hint))
                    Spacer(Modifier.width(5.r))
                    RText(l, 13, FontWeight.Normal, C.StatLabel)
                }
            }
        }
    }
}

/** How the adhan sounds; the user's own adhan audio can be chosen and tried. */
@Composable
private fun AdhanSoundCard(prefs: IslamicPrefs, onChange: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sound by remember { mutableStateOf(prefs.adhanSound) }
    var fileName by remember { mutableStateOf(prefs.adhanFileName) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose { player?.release() }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = withContext(Dispatchers.IO) {
                    try {
                        val f = prefs.adhanFile
                        f.parentFile?.mkdirs()
                        context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) c.getString(0) else null
                        } ?: "আজান"
                    } catch (e: Exception) {
                        null
                    }
                }
                if (name == null) {
                    Toast.makeText(context, "অডিওটা নেওয়া গেল না", Toast.LENGTH_SHORT).show()
                } else {
                    prefs.adhanFileName = name
                    fileName = name
                    prefs.adhanSound = AdhanSound.Adhan
                    sound = AdhanSound.Adhan
                    onChange()
                }
            }
        }
    }
    ListCard {
        RText("📢 আজানের শব্দ", 19, FontWeight.SemiBold, C.Ink)
        Spacer(Modifier.height(10.r))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.r)) {
            AdhanSound.entries.forEach { s ->
                ChoiceChip(s.bn, sound == s, IslamicA) {
                    sound = s
                    prefs.adhanSound = s
                    onChange()
                }
            }
        }
        if (sound == AdhanSound.Adhan) {
            Spacer(Modifier.height(12.r))
            val has = fileName.isNotEmpty() && prefs.adhanFile.exists()
            RText(
                if (has) "🎵 $fileName" else "ফোন থেকে আজানের অডিও (mp3 / m4a) বাছুন। অডিও না থাকলে নোটিফিকেশনের শব্দ বাজবে।",
                16,
                FontWeight.Medium,
                if (has) C.Ink else C.StatLabel,
                maxLines = 3,
                lineHeight = 22,
            )
            Spacer(Modifier.height(10.r))
            Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                RoundButton(if (has) "অডিও বদলান" else "🎵 অডিও বাছুন", IslamicA, Modifier.weight(1f), filled = !has) { pick.launch(arrayOf("audio/*")) }
                if (has) {
                    RoundButton(if (player != null) "⏹ থামান" else "▶ শুনুন", IslamicA, Modifier.weight(1f), filled = false) {
                        val p = player
                        if (p != null) {
                            p.release()
                            player = null
                        } else {
                            player = try {
                                MediaPlayer().apply {
                                    setDataSource(prefs.adhanFile.absolutePath)
                                    setOnCompletionListener {
                                        it.release()
                                        player = null
                                    }
                                    prepare()
                                    start()
                                }
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }
            }
            RText("ফোন সাইলেন্ট বা ভাইব্রেট মোডে থাকলে অডিও বাজবে না, শুধু নোটিফিকেশন আসবে।", 14, FontWeight.Normal, C.Hint, maxLines = 2)
        }
    }
}

/** Jama'ah reminders: on/off and the mosque's time for each prayer, how long before to remind. */
@Composable
private fun JamaatCard(prefs: IslamicPrefs, times: com.nirvor.app.islamic.DayTimes, date: LocalDate, onChange: () -> Unit) {
    val context = LocalContext.current
    var mask by remember { mutableIntStateOf(prefs.jamaatMask) }
    var minutes by remember { mutableStateOf(prefs.jamaatMinutes) }
    var lead by remember { mutableIntStateOf(prefs.jamaatLead) }
    var after by remember { mutableIntStateOf(prefs.jamaatAfter) }
    ListCard {
        RText("🤝 জামাতের রিমাইন্ডার", 19, FontWeight.SemiBold, C.Ink)
        RText("মসজিদের জামাতের সময় দিন — তার আগে মনে করিয়ে দেবে", 15, FontWeight.Normal, C.StatLabel, maxLines = 2)
        Spacer(Modifier.height(8.r))
        Prayer.five.forEachIndexed { i, p ->
            val on = mask and (1 shl i) != 0
            Row(Modifier.fillMaxWidth().padding(vertical = 6.r), verticalAlignment = Alignment.CenterVertically) {
                RText(p.bn, 18, FontWeight.Medium, C.Ink, Modifier.width(80.r))
                val at = prefs.jamaatAt(i, date, times)
                RText(
                    if (minutes[i] >= 0) "🕐 ${Fmt.minute(minutes[i])}" else "আজানের ${bnNum(after)} মিনিট পর (${clock(at)})",
                    15,
                    FontWeight.SemiBold,
                    if (on) IslamicB else C.Hint,
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.r))
                        .background(if (on) Mint else C.CalBg)
                        .clickable {
                            pickTime(context, if (minutes[i] >= 0) minutes[i] else null) { m ->
                                minutes = minutes.toMutableList().also { it[i] = m }
                                prefs.jamaatMinutes = minutes
                                mask = mask or (1 shl i)
                                prefs.jamaatMask = mask
                                onChange()
                            }
                        }
                        .padding(horizontal = 12.r, vertical = 9.r),
                )
                Spacer(Modifier.width(8.r))
                Switch(on, {
                    mask = mask xor (1 shl i)
                    prefs.jamaatMask = mask
                    onChange()
                }, colors = SwitchDefaults.colors(checkedTrackColor = IslamicA))
            }
        }
        Spacer(Modifier.height(8.r))
        RText("কত আগে মনে করাবে", 15, FontWeight.Medium, C.StatLabel)
        Spacer(Modifier.height(6.r))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.r)) {
            listOf(5, 10, 15, 20, 30).forEach { m ->
                ChoiceChip("${bnNum(m)} মিনিট", lead == m, IslamicA) {
                    lead = m
                    prefs.jamaatLead = m
                    onChange()
                }
            }
        }
        Spacer(Modifier.height(10.r))
        RText("সময় না দিলে জামাত ধরা হবে আজানের পর", 15, FontWeight.Medium, C.StatLabel)
        Spacer(Modifier.height(6.r))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.r)) {
            listOf(10, 15, 20, 30).forEach { m ->
                ChoiceChip("${bnNum(m)} মিনিট", after == m, IslamicA) {
                    after = m
                    prefs.jamaatAfter = m
                    onChange()
                }
            }
            ChoiceChip("সব সময় মুছুন", false, C.Danger) {
                minutes = List(5) { -1 }
                prefs.jamaatMinutes = minutes
                onChange()
            }
        }
    }
}
