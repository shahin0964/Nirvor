package com.nirvor.app.ui.islamic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.NirvorApplication
import com.nirvor.app.data.DhikrCustomEntity
import com.nirvor.app.data.DhikrLogEntity
import com.nirvor.app.islamic.Dhikr
import com.nirvor.app.islamic.Dhikrs
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.tools.KeepScreenOn
import com.nirvor.app.tools.toolPrefs
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.sheets.NField
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.ToolScaffold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun vibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else context.getSystemService(Vibrator::class.java)

/** One chosen dhikr: a preset, the 33-33-34 after-prayer set, or the user's own. */
private class Pick(val key: String, val arabic: String, val bn: String, val meaning: String, val target: Int)

private fun Dhikr.pick() = Pick(key, arabic, bn, meaning, target)

/** Digital tasbih: tap anywhere on the big circle, a ring fills to the target, counts are kept per day. */
@Composable
fun TasbihScreen(onBack: () -> Unit) {
    KeepScreenOn()
    val context = LocalContext.current
    val view = LocalView.current
    val app = context.applicationContext as NirvorApplication
    val dao = app.db.islamicDao()
    val sp = remember { toolPrefs(context) }
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now().toEpochDay() }
    val customs by dao.customDhikr().collectAsState(emptyList())
    val logs by dao.dhikrLogsFrom(today - 6).collectAsState(emptyList())

    var key by rememberSaveable { mutableStateOf(sp.getString("tasbih_key", Dhikrs.presets[0].key) ?: Dhikrs.presets[0].key) }
    var count by rememberSaveable { mutableIntStateOf(0) }
    var step by rememberSaveable { mutableIntStateOf(0) } // after-prayer set: 0, 1, 2
    var customTarget by rememberSaveable { mutableIntStateOf(0) }
    var vibrate by remember { mutableStateOf(sp.getBoolean("tasbih_vibrate", true)) }
    var adding by remember { mutableStateOf(false) }
    var targetDialog by remember { mutableStateOf(false) }

    val afterPrayer = key == Dhikrs.AFTER_PRAYER_KEY
    val pick: Pick = when {
        afterPrayer -> Dhikrs.afterPrayer[step.coerceIn(0, 2)].let { (d, n) -> Pick(d.key, d.arabic, d.bn, d.meaning, n) }
        key.startsWith("c") -> customs.firstOrNull { "c${it.id}" == key }?.let { Pick(key, it.arabic, it.name, "", it.target) }
            ?: Dhikrs.presets[0].pick()
        else -> (Dhikrs.of(key) ?: Dhikrs.presets[0]).pick()
    }
    val target = if (customTarget > 0 && !afterPrayer) customTarget else pick.target
    val logKey = pick.key

    // today's total for this dhikr: what is saved plus the taps not saved yet
    var unsaved by remember { mutableIntStateOf(0) }
    val savedToday = logs.firstOrNull { it.epochDay == today && it.dhikr == logKey }?.count ?: 0
    LaunchedEffect(unsaved, logKey) {
        if (unsaved == 0) return@LaunchedEffect
        delay(500)
        val add = unsaved
        unsaved = 0
        scope.launch(Dispatchers.IO) {
            val old = dao.dhikrLog(today, logKey)?.count ?: 0
            dao.putDhikrLog(DhikrLogEntity(today, logKey, old + add))
        }
    }
    DisposableEffect(logKey) {
        onDispose {
            // leaving this dhikr (or the page): save what is still waiting, under this dhikr
            val add = unsaved
            unsaved = 0
            if (add > 0) {
                CoroutineScope(Dispatchers.IO).launch {
                    val old = dao.dhikrLog(today, logKey)?.count ?: 0
                    dao.putDhikrLog(DhikrLogEntity(today, logKey, old + add))
                }
            }
        }
    }

    fun choose(k: String) {
        key = k
        sp.edit().putString("tasbih_key", k).apply()
        count = 0
        step = 0
        customTarget = 0
    }

    fun tap() {
        count++
        unsaved++
        val reached = count == target
        if (vibrate) {
            if (reached) {
                vibrator(context)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 80, 120), -1))
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
        if (reached && afterPrayer && step < 2) {
            step++
            count = 0
        }
    }

    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(90)
            pressed = false
        }
    }

    ToolScaffold("তাসবিহ", "ট্যাপ করে জিকির গুনুন", onBack) {
        // which dhikr
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.r)) {
            ChoiceChip("নামাজের পরে ৩৩·৩৩·৩৪", afterPrayer, IslamicA) { choose(Dhikrs.AFTER_PRAYER_KEY) }
            Dhikrs.presets.forEach { d -> ChoiceChip(d.bn, key == d.key, IslamicA) { choose(d.key) } }
            customs.forEach { c -> ChoiceChip(c.name, key == "c${c.id}", IslamicA) { choose("c${c.id}") } }
            ChoiceChip("➕ নিজের জিকির", false, Gold) { adding = true }
        }

        // the counter
        val progress = (count.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
        val done = count >= target
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .scale(press)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(IslamicA, IslamicB)))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    pressed = true
                    tap()
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(16.r)) {
                val sw = size.width * 0.045f
                drawArc(Color.White.copy(alpha = 0.16f), -90f, 360f, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw))
                drawArc(Gold, -90f, 360f * progress, false, Offset(sw / 2, sw / 2), Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
                // a bead for every count of the target (up to 100)
                val beads = target.coerceAtMost(100)
                val r = size.width / 2 - sw * 2.2f
                for (i in 0 until beads) {
                    val a = Math.toRadians(-90.0 + 360.0 * i / beads)
                    val on = i < count.coerceAtMost(beads)
                    drawCircle(
                        if (on) Gold else Color.White.copy(alpha = 0.25f),
                        if (beads > 40) sw * 0.18f else sw * 0.3f,
                        Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat()),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 60.r)) {
                if (pick.arabic.isNotEmpty()) RText(pick.arabic, 30, FontWeight.Bold, Color.White, maxLines = 2, align = TextAlign.Center)
                RText(pick.bn, 18, FontWeight.Medium, Color.White.copy(alpha = 0.85f), maxLines = 2, align = TextAlign.Center)
                Spacer(Modifier.height(6.r))
                RText(bnNum(count), 92, FontWeight.Bold, Color.White)
                RText(if (done) "✓ ${bnNum(target)} পূর্ণ হয়েছে" else "লক্ষ্য ${bnNum(target)}", 18, FontWeight.SemiBold, if (done) Gold else Color.White.copy(alpha = 0.8f))
                if (afterPrayer) RText("ধাপ ${bnNum(step + 1)} / ৩", 15, FontWeight.Medium, Color.White.copy(alpha = 0.7f))
            }
        }
        if (pick.meaning.isNotEmpty()) RText("অর্থ: ${pick.meaning}", 16, FontWeight.Normal, C.StatLabel, Modifier.fillMaxWidth(), maxLines = 2, align = TextAlign.Center)

        Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
            SmallKey("↺ শূন্য করুন", Modifier.weight(1f)) {
                count = 0
                step = 0
            }
            if (!afterPrayer) SmallKey("🎯 লক্ষ্য: ${bnNum(target)}", Modifier.weight(1f)) { targetDialog = true }
            SmallKey(if (vibrate) "📳 কম্পন চালু" else "📴 কম্পন বন্ধ", Modifier.weight(1f)) {
                vibrate = !vibrate
                sp.edit().putBoolean("tasbih_vibrate", vibrate).apply()
            }
        }

        // today and the last 7 days
        ListCard {
            val todayAll = logs.filter { it.epochDay == today }.sumOf { it.count } + unsaved
            RText("আজ মোট ${bnNum(todayAll)} বার জিকির", 19, FontWeight.SemiBold, C.Ink)
            RText("এই জিকির আজ ${bnNum(savedToday + unsaved)} বার", 15, FontWeight.Normal, C.StatLabel)
            Spacer(Modifier.height(14.r))
            val days = (6 downTo 0).map { today - it }
            val totals = days.map { d -> logs.filter { it.epochDay == d }.sumOf { it.count } + if (d == today) unsaved else 0 }
            val max = (totals.maxOrNull() ?: 0).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(120.r), verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, d ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (totals[i] > 0) RText(bnNum(totals[i]), 12, FontWeight.SemiBold, IslamicB)
                        Box(
                            Modifier
                                .width(22.r)
                                .fillMaxHeight(0.75f * totals[i] / max + 0.02f)
                                .clip(RoundedCornerShape(topStart = 6.r, topEnd = 6.r))
                                .background(if (d == today) IslamicA else IslamicA.copy(alpha = 0.35f)),
                        )
                        RText(LocalDate.ofEpochDay(d).format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)), 12, FontWeight.Medium, C.Hint)
                    }
                }
            }
        }
        if (key.startsWith("c")) {
            SmallKey("🗑 এই জিকির মুছুন", Modifier.fillMaxWidth()) {
                val id = key.removePrefix("c").toLongOrNull()
                if (id != null) scope.launch(Dispatchers.IO) { dao.deleteCustomDhikr(id) }
                choose(Dhikrs.presets[0].key)
            }
        }
    }

    if (adding) AddDhikrDialog(onDismiss = { adding = false }) { name, arabic, t ->
        scope.launch(Dispatchers.IO) {
            val id = dao.insertCustomDhikr(DhikrCustomEntity(name = name, arabic = arabic, target = t))
            launch(Dispatchers.Main) { choose("c$id") }
        }
    }
    if (targetDialog) {
        AlertDialog(
            onDismissRequest = { targetDialog = false },
            containerColor = Color.White,
            title = { RText("কত বার গুনবেন?", 22, FontWeight.Bold, C.Ink) },
            text = {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.r)) {
                    listOf(33, 34, 99, 100, 500, 1000).forEach { n ->
                        ChoiceChip(bnNum(n), target == n, IslamicA) {
                            customTarget = n
                            targetDialog = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { targetDialog = false }) { RText("ঠিক আছে", 19, FontWeight.SemiBold, IslamicA) } },
        )
    }
}

@Composable
private fun SmallKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    RText(
        label,
        15,
        FontWeight.SemiBold,
        C.Ink,
        modifier
            .clip(RoundedCornerShape(14.r))
            .background(Color.White)
            .border(1.r, C.TodayBorder, RoundedCornerShape(14.r))
            .clickable(onClick = onClick)
            .padding(vertical = 13.r),
        align = TextAlign.Center,
    )
}

@Composable
private fun AddDhikrDialog(onDismiss: () -> Unit, onSave: (String, String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var arabic by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("33") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { RText("নিজের জিকির", 22, FontWeight.Bold, C.Ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.r)) {
                NField(name, { name = it }, "নাম (বাংলায়)")
                NField(arabic, { arabic = it }, "আরবি (না দিলেও চলবে)")
                NField(target, { v -> target = v.filter { it.isDigit() }.take(5) }, "কত বার", keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) {
                    onSave(name.trim(), arabic.trim(), target.toIntOrNull()?.coerceIn(1, 100000) ?: 33)
                    onDismiss()
                }
            }) { RText("রাখুন", 19, FontWeight.SemiBold, IslamicA) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { RText("বাতিল", 19, FontWeight.Medium, C.Hint) } },
    )
}
