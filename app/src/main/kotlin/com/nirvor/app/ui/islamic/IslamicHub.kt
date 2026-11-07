package com.nirvor.app.ui.islamic

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.islamic.Hijri
import com.nirvor.app.islamic.IslamicPrefs
import com.nirvor.app.islamic.Prayer
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ToolScaffold
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything inside More → Islamic Tools. [ready] = false: comes in a later update. */
enum class IslamicTool(val emoji: String, val title: String, val sub: String, val ready: Boolean) {
    Namaz("🕋", "নামাজ", "সময়, আজান, ট্র্যাকার", true),
    Qibla("🧭", "কিবলা", "কম্পাস ও ক্যামেরা", true),
    Tasbih("📿", "তাসবিহ", "ট্যাপ করে গুনুন", true),
    Qaza("📋", "কাজা নামাজ", "বাকি নামাজের হিসাব", true),
    Quran("📖", "কুরআন", "পড়া, অনুবাদ, তিলাওয়াত", true),
    Dua("🤲", "দোয়া ও আযকার", "সকাল-সন্ধ্যা ও প্রতিদিনের", true),
    Ruqyah("🛡️", "রুকইয়াহ", "বদনজর, জাদু ও শিফার অডিও", true),
    Ramadan("🌙", "রমজান", "সাহরি-ইফতার, রোজা", true),
    Zakat("💰", "যাকাত", "হিসাব ও নিসাব", true),
    NamazSettings("⚙️", "নামাজের সময়ের নিয়ম", "", true);

    companion object {
        fun of(name: String): IslamicTool? = entries.firstOrNull { it.name == name }
    }
}

private val GREG = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

/** More → Islamic Tools: today's date, the next prayer with a countdown, and the tools. */
@Composable
fun IslamicHubScreen(onBack: () -> Unit, onOpen: (IslamicTool) -> Unit, onLocation: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { IslamicPrefs(context) }
    val now = rememberNow()
    val today = LocalDate.now()
    val hijri = remember(today, prefs.hijriAdjust) { Hijri.of(today, prefs.hijriAdjust) }
    val place = remember { prefs.place() }
    val pn = remember(now / 60_000, place) { prayerNow(prefs, now) }
    val times = remember(today, place) { todayTimes(prefs) }

    ToolScaffold("Islamic Tools", "নামাজ, কিবলা, কুরআন, দোয়া", onBack) {
        // hero: date, next prayer, countdown
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.r))
                .background(Brush.linearGradient(listOf(IslamicA, IslamicB)))
                .clickable { onOpen(IslamicTool.Namaz) }
                .padding(horizontal = 26.r, vertical = 24.r),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("☪ ${hijri.text}", 18, FontWeight.SemiBold, Gold, Modifier.weight(1f))
                RText(today.format(GREG), 15, FontWeight.Medium, Color.White.copy(alpha = 0.75f))
            }
            Spacer(Modifier.height(18.r))
            if (pn == null) {
                RText("📍 লোকেশন পাওয়া যায়নি", 24, FontWeight.Bold, Color.White)
                Spacer(Modifier.height(6.r))
                RText(
                    "নামাজের সময় বের করতে শহর বা ফোনের লোকেশন লাগবে।",
                    17,
                    FontWeight.Normal,
                    Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    lineHeight = 24,
                )
                Spacer(Modifier.height(14.r))
                RText(
                    "লোকেশন ঠিক করুন",
                    18,
                    FontWeight.SemiBold,
                    IslamicB,
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(onClick = onLocation)
                        .padding(horizontal = 20.r, vertical = 10.r),
                )
            } else {
                val label = if (pn.next == Prayer.Sunrise) "ফজরের ওয়াক্ত শেষ হবে" else "পরের নামাজ: ${pn.next.bn}"
                RText(label, 18, FontWeight.Medium, Color.White.copy(alpha = 0.85f))
                Row(verticalAlignment = Alignment.Bottom) {
                    RText(clock(pn.nextAt), 52, FontWeight.Bold, Color.White)
                    Spacer(Modifier.width(8.r))
                    RText(ampm(pn.nextAt), 20, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f), Modifier.padding(bottom = 10.r))
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        RText("বাকি", 15, FontWeight.Medium, Color.White.copy(alpha = 0.75f))
                        RText(countdown(pn.nextAt - now), 30, FontWeight.Bold, Gold)
                    }
                }
                Spacer(Modifier.height(4.r))
                RText(
                    (pn.current?.let { "এখন ${it.bnOf} ওয়াক্ত · " } ?: "") + "📍 ${place?.name.orEmpty()}",
                    15,
                    FontWeight.Medium,
                    Color.White.copy(alpha = 0.8f),
                )
                if (times != null) {
                    Spacer(Modifier.height(16.r))
                    Row(Modifier.fillMaxWidth()) {
                        Prayer.five.forEach { p ->
                            val on = pn.current == p
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.r))
                                    .background(if (on) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                                    .padding(vertical = 8.r),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                RText(p.bn, 15, FontWeight.Medium, Color.White.copy(alpha = 0.85f))
                                RText(clock(times[p]), 18, FontWeight.Bold, Color.White)
                            }
                        }
                    }
                }
            }
        }

        // the tools, two to a row
        IslamicTool.entries.filter { it != IslamicTool.NamazSettings }.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.r)) {
                row.forEach { t ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.r))
                            .background(Color.White)
                            .border(1.r, C.TodayBorder, RoundedCornerShape(22.r))
                            .clickable {
                                if (t.ready) onOpen(t) else Toast.makeText(context, "${t.title} পরের আপডেটে আসছে", Toast.LENGTH_SHORT).show()
                            }
                            .padding(18.r),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier
                                    .size(56.r)
                                    .clip(RoundedCornerShape(16.r))
                                    .background(if (t.ready) Mint else C.CalBg),
                                contentAlignment = Alignment.Center,
                            ) { RText(t.emoji, 28, FontWeight.Normal, C.Ink, Modifier.alpha(if (t.ready) 1f else 0.5f)) }
                            Spacer(Modifier.weight(1f))
                            if (!t.ready) Pill("শীঘ্রই", GoldSoft, Color(0xFF9A7414))
                        }
                        Spacer(Modifier.height(12.r))
                        RText(t.title, 20, FontWeight.SemiBold, if (t.ready) C.Ink else C.Hint)
                        RText(t.sub, 15, FontWeight.Normal, C.StatLabelBn, maxLines = 2, align = TextAlign.Start)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Opens one of the Islamic tools. */
@Composable
fun IslamicHost(tool: IslamicTool, onBack: () -> Unit, onOpen: (IslamicTool) -> Unit, onLocation: () -> Unit) {
    when (tool) {
        IslamicTool.Namaz -> PrayerScreen(onBack, onOpen, onLocation)
        IslamicTool.NamazSettings -> PrayerSettingsScreen(onBack)
        IslamicTool.Qibla -> QiblaScreen(onBack, onLocation)
        IslamicTool.Tasbih -> TasbihScreen(onBack)
        IslamicTool.Qaza -> QazaScreen(onBack)
        IslamicTool.Quran -> QuranScreen(onBack)
        IslamicTool.Dua -> DuaScreen(onBack)
        IslamicTool.Ruqyah -> RuqyahScreen(onBack)
        IslamicTool.Ramadan -> RamadanScreen(onBack, onLocation)
        IslamicTool.Zakat -> ZakatScreen(onBack)
    }
}
