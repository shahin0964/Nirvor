package com.nirvor.app.ui.islamic

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.islamic.IslamicPrefs
import com.nirvor.app.islamic.Prayer
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.tools.toolPrefs
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.ToolScaffold
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RamadanPurpleA = Color(0xFF1E3A8A)
private val RamadanPurpleB = Color(0xFF0F172A)
private val RamadanGold = Color(0xFFF59E0B)

@Composable
fun RamadanScreen(onBack: () -> Unit, onLocation: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { IslamicPrefs(context) }
    val sp = remember { toolPrefs(context) }
    val today = LocalDate.now()
    val now = rememberNow()
    val place = remember { prefs.place() }
    val times = remember(today, place) { prefs.times(today) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Today, 1: 30-Day Table, 2: Duas & Deeds, 3: Mas'ala

    // Fast Tracker state: count of kept fasts & today's status
    val todayEpoch = today.toEpochDay()
    val keptFasts = remember {
        sp.getStringSet("ramadan_kept_days", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    }
    var isTodayKept by remember { mutableStateOf(keptFasts.contains(todayEpoch)) }

    // Sehri end is Fajr start time; Iftar is Maghrib start time
    val sehriTime = times?.get(Prayer.Fajr) ?: (now + 3600_000)
    val iftarTime = times?.get(Prayer.Maghrib) ?: (now + 7200_000)

    val isFastingHours = now in sehriTime until iftarTime
    val nextTargetTime = if (now < sehriTime) sehriTime else if (now < iftarTime) iftarTime else (sehriTime + 86400_000)
    val countdownLabel = if (isFastingHours) "ইফতারের বাকি" else "সাহরির শেষ সময় বাকি"

    ToolScaffold(
        title = "পবিত্র মাহে রমজান",
        subtitle = "সাহরি-ইফতারের সময়সূচি, রোজা ট্র্যাকার ও আমল",
        onBack = onBack,
    ) {
        // Hero Card
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.r))
                .background(Brush.linearGradient(listOf(RamadanPurpleA, RamadanPurpleB)))
                .padding(22.r),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("🌙 পবিত্র মাহে রমজান", 17, FontWeight.Bold, RamadanGold, Modifier.weight(1f))
                RText(
                    "📍 ${place?.name ?: "লোকেশন সেট করুন"}",
                    14,
                    FontWeight.Medium,
                    Color.White.copy(alpha = 0.8f),
                    Modifier.clickable(onClick = onLocation),
                )
            }

            Spacer(Modifier.height(14.r))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    RText(countdownLabel, 15, FontWeight.Medium, Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(4.r))
                    RText(countdown(nextTargetTime - now), 42, FontWeight.Bold, RamadanGold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    RText(if (isFastingHours) "ইফতার" else "সাহরি শেষ", 14, FontWeight.Medium, Color.White.copy(alpha = 0.75f))
                    RText(
                        clock(if (isFastingHours) iftarTime else sehriTime) + " " + ampm(if (isFastingHours) iftarTime else sehriTime),
                        22,
                        FontWeight.Bold,
                        Color.White,
                    )
                }
            }

            Spacer(Modifier.height(16.r))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(Modifier.height(16.r))

            // Fast tracker row
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.r))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable {
                        isTodayKept = !isTodayKept
                        val set = keptFasts.toMutableSet()
                        if (isTodayKept) set.add(todayEpoch) else set.remove(todayEpoch)
                        sp.edit().putStringSet("ramadan_kept_days", set.map { it.toString() }.toSet()).apply()
                    }
                    .padding(horizontal = 16.r, vertical = 12.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RText(
                    if (isTodayKept) "✓ আজকের রোজা সম্পন্ন হয়েছে" else "○ আজকের রোজা ট্র্যাকার (ট্যাপ করুন)",
                    15,
                    FontWeight.SemiBold,
                    if (isTodayKept) Color(0xFF6EE7B7) else Color.White,
                )
                Spacer(Modifier.weight(1f))
                Pill(
                    "${bnNum(keptFasts.size)}টি রোজা",
                    if (isTodayKept) Color(0xFF065F46) else Color.White.copy(alpha = 0.2f),
                    if (isTodayKept) Color(0xFF6EE7B7) else Color.White,
                )
            }
        }

        // Tab selection
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.r),
        ) {
            ChoiceChip("আজকের সময়", selectedTab == 0, color = RamadanPurpleA) { selectedTab = 0 }
            ChoiceChip("রমজান ক্যালেন্ডার", selectedTab == 1, color = RamadanPurpleA) { selectedTab = 1 }
            ChoiceChip("দু'আ ও আমল", selectedTab == 2, color = RamadanPurpleA) { selectedTab = 2 }
            ChoiceChip("মাসআলা", selectedTab == 3, color = RamadanPurpleA) { selectedTab = 3 }
        }

        when (selectedTab) {
            0 -> TodayTimesView(times, sehriTime, iftarTime)
            1 -> RamadanCalendarView(prefs, today)
            2 -> RamadanDuasView()
            3 -> RamadanMasalaView()
        }
    }
}

@Composable
private fun TodayTimesView(times: com.nirvor.app.islamic.DayTimes?, sehriTime: Long, iftarTime: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(12.r)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
            // Sehri Card
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.r))
                    .background(Color.White)
                    .border(1.r, C.TodayBorder, RoundedCornerShape(18.r))
                    .padding(18.r),
            ) {
                RText("সাহরির শেষ সময়", 14, FontWeight.Medium, C.StatLabelBn)
                Spacer(Modifier.height(6.r))
                RText(clock(sehriTime), 32, FontWeight.Bold, Color(0xFF1E3A8A))
                RText(ampm(sehriTime), 15, FontWeight.SemiBold, C.Hint)
            }

            // Iftar Card
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.r))
                    .background(Color.White)
                    .border(1.r, C.TodayBorder, RoundedCornerShape(18.r))
                    .padding(18.r),
            ) {
                RText("ইফতারের সময়", 14, FontWeight.Medium, C.StatLabelBn)
                Spacer(Modifier.height(6.r))
                RText(clock(iftarTime), 32, FontWeight.Bold, Color(0xFFD97706))
                RText(ampm(iftarTime), 15, FontWeight.SemiBold, C.Hint)
            }
        }

        // Niyyah Card
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.r))
                .background(Color.White)
                .border(1.r, C.TodayBorder, RoundedCornerShape(18.r))
                .padding(18.r),
        ) {
            RText("রোজার নিয়ত (সাহরির পর)", 18, FontWeight.Bold, C.Ink)
            Spacer(Modifier.height(8.r))
            RText(
                "نَوَيْتُ أَنْ أَصُومَ غَدًا مِنْ شَهْرِ رَمَضَانَ الْمُبَارَكِ فَرْضًا لَكَ يَا اللَّهُ فَتَقَبَّلْ مِنِّي",
                20,
                FontWeight.Bold,
                Color(0xFF0F766E),
                align = TextAlign.End,
                lineHeight = 34,
            )
            Spacer(Modifier.height(6.r))
            RText("উচ্চারণ: নাওয়াইতু আন আসূমা গাদান মিন শাহরি রমাদানাল মুবারাকি ফারদ্বাল্লাকা ইয়া আল্লাহু ফাতাক্বাব্বাল মিন্নী।", 14, FontWeight.Medium, Color(0xFF8B6F2D))
            Spacer(Modifier.height(4.r))
            RText("অর্থ: হে আল্লাহ! আমি আগামীকাল পবিত্র মাহে রমজানের ফরজ রোজা রাখার নিয়ত করলাম। অতএব আপনি আমার পক্ষ থেকে তা কবুল করুন।", 14, FontWeight.Normal, C.Ink)
        }

        // Iftar Dua Card
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.r))
                .background(Color.White)
                .border(1.r, C.TodayBorder, RoundedCornerShape(18.r))
                .padding(18.r),
        ) {
            RText("ইফতারের দু'আ", 18, FontWeight.Bold, C.Ink)
            Spacer(Modifier.height(8.r))
            RText(
                "ذَهَبَ الظَّمَأُ وَابْتَلَّتِ الْعُرُوقُ وَثَبَتَ الأَجْرُ إِنْ شَاءَ اللَّهُ",
                22,
                FontWeight.Bold,
                Color(0xFFD97706),
                align = TextAlign.End,
                lineHeight = 36,
            )
            Spacer(Modifier.height(6.r))
            RText("উচ্চারণ: যাহাবায জামা'উ, ওয়াবতাল্লাতিল 'উরূকু, ওয়া সাবাতাল আজরু ইনশাআল্লাহ।", 14, FontWeight.Medium, Color(0xFF8B6F2D))
            Spacer(Modifier.height(4.r))
            RText("অর্থ: পিপাসা দূরীভূত হলো, শিরা-উপশিরা সিক্ত হলো এবং আল্লাহর ইচ্ছায় সওয়াব নির্ধারিত হলো। (আবু দাউদ: ২৩৫৭)", 14, FontWeight.Normal, C.Ink)
        }
    }
}

@Composable
private fun RamadanCalendarView(prefs: IslamicPrefs, today: LocalDate) {
    val dFmt = remember { DateTimeFormatter.ofPattern("d MMM, EEE", Locale.ENGLISH) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.r))
            .background(Color.White)
            .border(1.r, C.TodayBorder, RoundedCornerShape(20.r))
            .padding(16.r),
    ) {
        RText("রমজান সময়সূচি (৩০ দিন)", 18, FontWeight.Bold, C.Ink)
        Spacer(Modifier.height(4.r))
        RText("আপনার নির্বাচিত অঞ্চলের ভিত্তিতে প্রস্তুতকৃত", 13, FontWeight.Normal, C.Hint)
        Spacer(Modifier.height(14.r))

        // Table Header
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.r))
                .background(C.CalBg)
                .padding(horizontal = 10.r, vertical = 8.r),
        ) {
            RText("রমজান", 13, FontWeight.Bold, C.Ink, Modifier.weight(0.9f))
            RText("তারিখ", 13, FontWeight.Bold, C.Ink, Modifier.weight(1.3f))
            RText("সাহরি শেষ", 13, FontWeight.Bold, Color(0xFF1E3A8A), Modifier.weight(1.1f), align = TextAlign.Center)
            RText("ইফতার", 13, FontWeight.Bold, Color(0xFFD97706), Modifier.weight(1.1f), align = TextAlign.End)
        }

        Spacer(Modifier.height(6.r))

        for (i in 0 until 30) {
            val date = today.plusDays(i.toLong())
            val dayTimes = prefs.times(date)
            val sTime = dayTimes?.get(Prayer.Fajr)
            val iTime = dayTimes?.get(Prayer.Maghrib)
            val isCurrent = i == 0

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.r))
                    .background(if (isCurrent) Mint else Color.Transparent)
                    .padding(horizontal = 10.r, vertical = 9.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RText("${bnNum(i + 1)} রমজান", 13, if (isCurrent) FontWeight.Bold else FontWeight.Medium, if (isCurrent) IslamicB else C.Ink, Modifier.weight(0.9f))
                RText(date.format(dFmt), 13, FontWeight.Normal, C.StatLabelBn, Modifier.weight(1.3f))
                RText(sTime?.let { clock(it) + " AM" } ?: "--", 13, FontWeight.SemiBold, Color(0xFF1E3A8A), Modifier.weight(1.1f), align = TextAlign.Center)
                RText(iTime?.let { clock(it) + " PM" } ?: "--", 13, FontWeight.SemiBold, Color(0xFFD97706), Modifier.weight(1.1f), align = TextAlign.End)
            }
            if (i < 29) HorizontalDivider(color = C.TodayBorder.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun RamadanDuasView() {
    Column(verticalArrangement = Arrangement.spacedBy(14.r)) {
        // Laylatul Qadr Dua
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.r))
                .background(Color.White)
                .border(1.r, C.TodayBorder, RoundedCornerShape(18.r))
                .padding(18.r),
        ) {
            Pill("লাইলাতুল কদর", GoldSoft, Color(0xFF9A7414))
            Spacer(Modifier.height(8.r))
            RText("শবে কদরের শ্রেষ্ঠ দু'আ", 18, FontWeight.Bold, C.Ink)
            Spacer(Modifier.height(8.r))
            RText(
                "اللَّهُمَّ إِنَّكَ عَفُوٌّ تُحِبُّ الْعَفْوَ فَاعْفُ عَنِّي",
                22,
                FontWeight.Bold,
                Color(0xFF0F766E),
                align = TextAlign.End,
                lineHeight = 36,
            )
            Spacer(Modifier.height(6.r))
            RText("উচ্চারণ: আল্লাহুম্মা ইন্নাকা 'আফুওবুন, তুহিব্বুল 'আফওয়া, ফা'ফু 'আন্নী।", 14, FontWeight.Medium, Color(0xFF8B6F2D))
            Spacer(Modifier.height(4.r))
            RText("অর্থ: হে আল্লাহ! নিশ্চয়ই আপনি ক্ষমাশীল, আপনি ক্ষমা করতে ভালোবাসেন; অতএব আমাকে ক্ষমা করে দিন। (তিরমিযী: ৩৫১৩)", 14, FontWeight.Normal, C.Ink)
        }

        // Taraweeh Munajat
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.r))
                .background(Color.White)
                .border(1.r, C.TodayBorder, RoundedCornerShape(18.r))
                .padding(18.r),
        ) {
            Pill("তারাবীহ", Mint, Color(0xFF065F46))
            Spacer(Modifier.height(8.r))
            RText("তারাবীহ নামাজের চার রাকাত পরপর দু'আ", 18, FontWeight.Bold, C.Ink)
            Spacer(Modifier.height(8.r))
            RText(
                "سُبْحَانَ ذِي الْمُلْكِ وَالْمَلَكُوتِ، سُبْحَانَ ذِي الْعِزَّةِ وَالْعَظَمَةِ وَالْهَيْبَةِ وَالْقُدْرَةِ وَالْكِبْرِيَاءِ وَالْجَبَرُوتِ، سُبْحَانَ الْمَلِكِ الْحَيِّ الَّذِي لَا يَنَامُ وَلَا يَمُوتُ أَبَدًا أَبَدًا، سُبُّوحٌ قُدُّوسٌ رَبُّنَا وَرَبُّ الْمَلَائِكَةِ وَالرُّوحِ",
                20,
                FontWeight.Bold,
                Color(0xFF1E3A8A),
                align = TextAlign.End,
                lineHeight = 34,
            )
            Spacer(Modifier.height(6.r))
            RText("উচ্চারণ: সুবহানা যিল মুলকি ওয়াল মালাকূত, সুবহানা যিল 'ইয্যাতি ওয়াল 'আযামাতি ওয়াল হাইবাতি ওয়াল কুদরাতি ওয়াল কিবরিয়ায়ি ওয়াল জাবারূত...", 14, FontWeight.Medium, Color(0xFF8B6F2D))
        }
    }
}

@Composable
private fun RamadanMasalaView() {
    val items = listOf(
        "যে কারণে রোজা ভঙ্গ হয়" to listOf(
            "ইচ্ছাকৃত পানাহার বা ধূমপান করলে।",
            "ইচ্ছাকৃত মুখ ভরে বমি করলে।",
            "স্ত্রী সহবাসে লিপ্ত হলে (এতে কাজা ও কাফফারা উভয়ই ওয়াজিব হয়)।",
            "নাক বা কান দিয়ে এমন কোনো ওষুধ প্রবেশ করানো যা পেটে বা মস্তিষ্কে পৌঁছায়।",
        ),
        "যেগুলোতে রোজা ভঙ্গ হয় না" to listOf(
            "ভুলবশত কিছু খেয়ে বা পান করে ফেললে (মনে পড়ার সাথে সাথে খাওয়া বন্ধ করতে হবে)।",
            "চোখে ড্রপ, সুরমা বা আতর ব্যবহার করলে।",
            "অনিচ্ছাকৃত বমি হলে।",
            "মেসওয়াক বা টুথব্রাশ ব্যবহার করলে (পেটে টুথপেস্ট না গেলে)।",
            "ইনজেকশন বা ইনসুলিন গ্রহণ করলে (যা শক্তিবর্ধক স্যালাইন নয়)।",
            "স্বপ্নদোষ হলে বা রক্ত পরীক্ষা করার জন্য রক্ত দিলে।",
        ),
        "যাদের জন্য রোজা ভঙ্গ করার অনুমতি রয়েছে" to listOf(
            "কঠিন অসুস্থ ব্যক্তি (পরে সুস্থ হলে কাজা করতে হবে)।",
            "মুসাফির (ভ্রমণকারী ব্যক্তি)।",
            "গর্ভবতী ও দুগ্ধদানকারী মা (নিজের বা সন্তানের ক্ষতির আশঙ্কা হলে)।",
            "অতিশয় বৃদ্ধ ব্যক্তি (যার রোজা রাখার সামর্থ্য নেই, তিনি ফিদয়া প্রদান করবেন)।",
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.r)) {
        items.forEach { (title, list) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.r))
                    .background(Color.White)
                    .border(1.r, C.TodayBorder, RoundedCornerShape(18.r))
                    .padding(18.r),
            ) {
                RText(title, 18, FontWeight.Bold, C.Ink)
                Spacer(Modifier.height(10.r))
                list.forEach { p ->
                    Row(Modifier.padding(vertical = 3.r)) {
                        RText("•", 16, FontWeight.Bold, Color(0xFF0F766E))
                        Spacer(Modifier.width(8.r))
                        RText(p, 15, FontWeight.Normal, C.Ink, lineHeight = 22)
                    }
                }
            }
        }
    }
}
