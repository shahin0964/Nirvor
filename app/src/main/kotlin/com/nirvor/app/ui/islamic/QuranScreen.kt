package com.nirvor.app.ui.islamic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.islamic.Ayah
import com.nirvor.app.islamic.QuranData
import com.nirvor.app.islamic.SurahMeta
import com.nirvor.app.islamic.SurahType
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.tools.toolPrefs
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.ToolScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val QuranEmerald = Color(0xFF0D6E4F)
private val QuranGold = Color(0xFFD4A537)
private val QuranCardBg = Color(0xFFF9FAF9)

@Composable
fun QuranScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = remember { toolPrefs(context) }
    var selectedSurah by remember { mutableStateOf<SurahMeta?>(null) }
    var search by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: All, 1: Makki, 2: Madani, 3: Favs
    var favIds by remember {
        mutableStateOf(sp.getStringSet("quran_favs", emptySet()) ?: emptySet())
    }
    var lastReadId by remember {
        mutableIntStateOf(sp.getInt("quran_last_read", 1))
    }

    if (selectedSurah != null) {
        SurahDetailScreen(
            surah = selectedSurah!!,
            onBack = { selectedSurah = null },
            isFav = favIds.contains(selectedSurah!!.number.toString()),
            onToggleFav = {
                val num = selectedSurah!!.number.toString()
                val next = if (favIds.contains(num)) favIds - num else favIds + num
                favIds = next
                sp.edit().putStringSet("quran_favs", next).apply()
            },
        )
        return
    }

    val filtered = remember(search, selectedTab, favIds) {
        QuranData.surahs.filter { s ->
            val matchesTab = when (selectedTab) {
                1 -> s.type == SurahType.Makki
                2 -> s.type == SurahType.Madani
                3 -> favIds.contains(s.number.toString())
                else -> true
            }
            val q = search.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                s.bn.lowercase().contains(q) ||
                s.arabic.contains(q) ||
                s.meaning.lowercase().contains(q) ||
                s.number.toString() == q ||
                bnNum(s.number) == q
            matchesTab && matchesQuery
        }
    }

    val lastSurah = remember(lastReadId) {
        QuranData.surahs.firstOrNull { it.number == lastReadId } ?: QuranData.surahs[0]
    }

    ToolScaffold(
        title = "পবিত্র কুরআন",
        subtitle = "১১৪টি সূরা, আরবি তিলাওয়াত ও বাংলা অনুবাদ",
        onBack = onBack,
    ) {
        // Last Read Banner
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.r))
                .background(Brush.linearGradient(listOf(QuranEmerald, IslamicB)))
                .clickable {
                    selectedSurah = lastSurah
                }
                .padding(20.r),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.r)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    RText("📖", 22, FontWeight.Normal, Color.White)
                }
                Spacer(Modifier.width(14.r))
                Column(Modifier.weight(1f)) {
                    RText("সর্বশেষ পঠিত সূরা", 14, FontWeight.Medium, Color.White.copy(alpha = 0.8f))
                    RText("সূরা ${lastSurah.bn} (${lastSurah.arabic})", 20, FontWeight.Bold, Color.White)
                }
                RText("পড়ুন ➔", 15, FontWeight.SemiBold, QuranGold)
            }
        }

        // Search Bar
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { RText("সূরা খুঁজুন (যেমন: ফাতিহা, ইয়াসীন, ১১৪)", 15, FontWeight.Normal, C.Hint) },
            singleLine = true,
            shape = RoundedCornerShape(16.r),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QuranEmerald,
                unfocusedBorderColor = C.TodayBorder,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Filter Chips
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.r),
        ) {
            ChoiceChip("সকল (${QuranData.surahs.size})", selectedTab == 0, color = QuranEmerald) { selectedTab = 0 }
            ChoiceChip("মাক্কী (৮৬)", selectedTab == 1, color = QuranEmerald) { selectedTab = 1 }
            ChoiceChip("মাদানী (২৮)", selectedTab == 2, color = QuranEmerald) { selectedTab = 2 }
            ChoiceChip("পছন্দ (${favIds.size})", selectedTab == 3, color = QuranGold) { selectedTab = 3 }
        }

        // Surahs list
        filtered.forEach { s ->
            val isFav = favIds.contains(s.number.toString())
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.r))
                    .background(Color.White)
                    .border(1.r, C.TodayBorder, RoundedCornerShape(18.r))
                    .clickable {
                        selectedSurah = s
                        sp.edit().putInt("quran_last_read", s.number).apply()
                        lastReadId = s.number
                    }
                    .padding(16.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Surah Number Diamond / Badge
                Box(
                    Modifier
                        .size(46.r)
                        .clip(RoundedCornerShape(12.r))
                        .background(Mint),
                    contentAlignment = Alignment.Center,
                ) {
                    RText(bnNum(s.number), 18, FontWeight.Bold, QuranEmerald)
                }
                Spacer(Modifier.width(14.r))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RText(s.bn, 19, FontWeight.Bold, C.Ink)
                        Spacer(Modifier.width(8.r))
                        Pill(s.type.bn, if (s.type == SurahType.Makki) Mint else GoldSoft, if (s.type == SurahType.Makki) QuranEmerald else Color(0xFF9A7414))
                    }
                    Spacer(Modifier.height(3.r))
                    RText("${s.meaning} · ${bnNum(s.verses)} আয়াত", 14, FontWeight.Normal, C.StatLabelBn)
                }
                Column(horizontalAlignment = Alignment.End) {
                    RText(s.arabic, 22, FontWeight.Bold, QuranEmerald)
                    Spacer(Modifier.height(4.r))
                    RText(
                        if (isFav) "★" else "☆",
                        20,
                        FontWeight.Normal,
                        if (isFav) QuranGold else C.Hint,
                        Modifier.clickable {
                            val num = s.number.toString()
                            val next = if (isFav) favIds - num else favIds + num
                            favIds = next
                            sp.edit().putStringSet("quran_favs", next).apply()
                        },
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(40.r),
                contentAlignment = Alignment.Center,
            ) {
                RText("কোনো সূরা খুঁজে পাওয়া যায়নি", 17, FontWeight.Medium, C.Hint)
            }
        }
    }
}

/** Detail view for reading a Surah. */
@Composable
private fun SurahDetailScreen(
    surah: SurahMeta,
    onBack: () -> Unit,
    isFav: Boolean,
    onToggleFav: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fontScale by remember { mutableFloatStateOf(1f) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoadingAudio by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    var verses by remember { mutableStateOf<List<Ayah>>(QuranData.offlineVerses[surah.number] ?: emptyList()) }
    var isLoadingVerses by remember { mutableStateOf(verses.isEmpty()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Fetch from Al-Quran Cloud API if not offline
    LaunchedEffect(surah.number) {
        if (verses.isEmpty()) {
            isLoadingVerses = true
            errorMsg = null
            withContext(Dispatchers.IO) {
                try {
                    val url = URL("https://api.alquran.cloud/v1/surah/${surah.number}/editions/quran-uthmani,bn.bengali")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val root = JSONObject(body)
                        val dataArray = root.getJSONArray("data")
                        val uthmani = dataArray.getJSONObject(0).getJSONArray("ayahs")
                        val bengali = dataArray.getJSONObject(1).getJSONArray("ayahs")

                        val list = mutableListOf<Ayah>()
                        for (i in 0 until uthmani.length()) {
                            val uObj = uthmani.getJSONObject(i)
                            val bObj = bengali.getJSONObject(i)
                            val ayNum = uObj.getInt("numberInSurah")
                            var arText = uObj.getString("text")
                            // Clean bismillah from first ayah if repeated
                            if (ayNum == 1 && surah.number != 1 && arText.startsWith("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ")) {
                                arText = arText.replace("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", "").trim()
                            }
                            val bnText = bObj.getString("text")
                            list.add(Ayah(ayNum, arText, "", bnText))
                        }
                        withContext(Dispatchers.Main) {
                            verses = list
                            isLoadingVerses = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            errorMsg = "ইন্টারনেট সংযোগ চেক করুন"
                            isLoadingVerses = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        errorMsg = "সূরাটি লোড করা যায়নি। ইন্টারনেট সংযোগ দিন।"
                        isLoadingVerses = false
                    }
                }
            }
        }
    }

    DisposableEffect(surah.number) {
        onDispose {
            player?.release()
            player = null
        }
    }

    fun toggleAudio() {
        if (isPlaying) {
            player?.pause()
            isPlaying = false
        } else {
            if (player == null) {
                isLoadingAudio = true
                try {
                    val p = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build(),
                        )
                        setDataSource(surah.audioUrl)
                        setOnPreparedListener {
                            isLoadingAudio = false
                            start()
                            isPlaying = true
                        }
                        setOnCompletionListener {
                            isPlaying = false
                        }
                        setOnErrorListener { _, _, _ ->
                            isLoadingAudio = false
                            isPlaying = false
                            Toast.makeText(context, "অডিও লোড করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                            true
                        }
                        prepareAsync()
                    }
                    player = p
                } catch (e: Exception) {
                    isLoadingAudio = false
                    Toast.makeText(context, "অডিও প্লেয়ার সমস্যা: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                player?.start()
                isPlaying = true
            }
        }
    }

    ToolScaffold(
        title = "সূরা ${surah.bn}",
        subtitle = "${surah.meaning} · ${surah.type.bn} · ${bnNum(surah.verses)} আয়াত",
        onBack = {
            player?.stop()
            onBack()
        },
        actions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Font Size Adjuster
                Box(
                    Modifier
                        .size(38.r)
                        .clip(CircleShape)
                        .background(Mint)
                        .clickable { fontScale = (fontScale - 0.15f).coerceAtLeast(0.8f) },
                    contentAlignment = Alignment.Center,
                ) {
                    RText("A-", 13, FontWeight.Bold, QuranEmerald)
                }
                Spacer(Modifier.width(6.r))
                Box(
                    Modifier
                        .size(38.r)
                        .clip(CircleShape)
                        .background(Mint)
                        .clickable { fontScale = (fontScale + 0.15f).coerceAtMost(1.6f) },
                    contentAlignment = Alignment.Center,
                ) {
                    RText("A+", 13, FontWeight.Bold, QuranEmerald)
                }
                Spacer(Modifier.width(8.r))
                // Favorite Button
                Box(
                    Modifier
                        .size(42.r)
                        .clip(CircleShape)
                        .background(if (isFav) GoldSoft else Color.White)
                        .clickable(onClick = onToggleFav),
                    contentAlignment = Alignment.Center,
                ) {
                    RText(if (isFav) "★" else "☆", 22, FontWeight.Bold, if (isFav) QuranGold else C.Hint)
                }
            }
        },
    ) {
        // Audio Recitation Player Header
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.r))
                .background(Brush.linearGradient(listOf(QuranEmerald, IslamicB)))
                .padding(horizontal = 18.r, vertical = 14.r),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.r)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = ::toggleAudio),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoadingAudio) {
                    CircularProgressIndicator(Modifier.size(24.r), color = QuranEmerald, strokeWidth = 2.r)
                } else {
                    RText(if (isPlaying) "⏸" else "▶", 20, FontWeight.Bold, QuranEmerald)
                }
            }
            Spacer(Modifier.width(14.r))
            Column(Modifier.weight(1f)) {
                RText("মিশারি রাশিদ আল-আফাসী", 16, FontWeight.SemiBold, Color.White)
                RText(if (isPlaying) "তিলাওয়াত চলছে..." else "পূর্ণ সূরা অডিও তিলাওয়াত শুনুন", 13, FontWeight.Normal, Color.White.copy(alpha = 0.8f))
            }
        }

        // Bismillah Banner (except Surah Tawbah)
        if (surah.number != 9) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.r),
                contentAlignment = Alignment.Center,
            ) {
                RText(
                    "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                    26 * fontScale,
                    FontWeight.Bold,
                    QuranEmerald,
                    align = TextAlign.Center,
                )
            }
        }

        if (isLoadingVerses) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(50.r),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = QuranEmerald)
                    Spacer(Modifier.height(14.r))
                    RText("আয়াতসমূহ লোড হচ্ছে...", 16, FontWeight.Medium, C.Hint)
                }
            }
        } else if (errorMsg != null && verses.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(30.r),
                contentAlignment = Alignment.Center,
            ) {
                RText(errorMsg!!, 16, FontWeight.Medium, Color(0xFFC0392B), align = TextAlign.Center)
            }
        } else {
            // Verses List
            verses.forEach { a ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.r))
                        .background(QuranCardBg)
                        .border(1.r, C.TodayBorder, RoundedCornerShape(18.r))
                        .padding(18.r),
                ) {
                    // Ayah number badge & copy button
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(34.r)
                                .clip(CircleShape)
                                .background(Mint),
                            contentAlignment = Alignment.Center,
                        ) {
                            RText(bnNum(a.number), 14, FontWeight.Bold, QuranEmerald)
                        }
                        Spacer(Modifier.weight(1f))
                        RText(
                            "📋 কপি",
                            13,
                            FontWeight.Medium,
                            C.Hint,
                            Modifier
                                .clip(RoundedCornerShape(8.r))
                                .background(Color.White)
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val text = "${a.arabic}\n\n${a.translation}\n(সূরা ${surah.bn}: ${a.number})"
                                    cm.setPrimaryClip(ClipData.newPlainText("Ayah", text))
                                    Toast.makeText(context, "আয়াত কপি হয়েছে", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 10.r, vertical = 4.r),
                        )
                    }

                    Spacer(Modifier.height(12.r))

                    // Arabic Text
                    RText(
                        a.arabic,
                        26 * fontScale,
                        FontWeight.Bold,
                        QuranEmerald,
                        Modifier.fillMaxWidth(),
                        align = TextAlign.End,
                        lineHeight = 44 * fontScale,
                    )

                    if (a.transliteration.isNotBlank()) {
                        Spacer(Modifier.height(10.r))
                        RText(
                            a.transliteration,
                            15 * fontScale,
                            FontWeight.Medium,
                            Color(0xFF8B6F2D),
                            Modifier.fillMaxWidth(),
                            lineHeight = 22 * fontScale,
                        )
                    }

                    Spacer(Modifier.height(8.r))

                    // Bangla Translation
                    RText(
                        a.translation,
                        16 * fontScale,
                        FontWeight.Normal,
                        C.Ink,
                        Modifier.fillMaxWidth(),
                        lineHeight = 24 * fontScale,
                    )
                }
            }
        }
    }
}
