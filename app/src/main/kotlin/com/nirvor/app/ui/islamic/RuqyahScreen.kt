package com.nirvor.app.ui.islamic

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.dp
import com.nirvor.app.islamic.RuqyahCategory
import com.nirvor.app.islamic.RuqyahData
import com.nirvor.app.islamic.RuqyahItem
import com.nirvor.app.ui.PageHeader
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

private val RuqyahGreenA = Color(0xFF0E9F6E)
private val RuqyahGreenB = Color(0xFF065F46)
private val RuqyahSoftMint = Color(0xFFE8F7F0)
private val RuqyahGold = Color(0xFFD4A537)
private val RuqyahCardBorder = Color(0xFFE2ECE7)

private fun formatTime(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@Composable
fun RuqyahScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(RuqyahCategory.All) }
    var showEtiquetteGuide by remember { mutableStateOf(false) }
    var expandedItemId by remember { mutableStateOf<String?>(null) }

    // Media Player State
    var activeItem by remember { mutableStateOf<RuqyahItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isLoadingAudio by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var isLooping by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    // Clean up player on leave
    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    // Progress updater coroutine
    LaunchedEffect(isPlaying, player) {
        while (isActive && isPlaying && player != null) {
            try {
                player?.let { p ->
                    if (p.isPlaying) {
                        currentPositionMs = p.currentPosition
                        durationMs = p.duration.coerceAtLeast(0)
                    }
                }
            } catch (_: Exception) {}
            delay(500)
        }
    }

    fun playAudio(item: RuqyahItem) {
        if (activeItem?.id == item.id && player != null) {
            // Toggle play / pause on the same item
            if (isPlaying) {
                player?.pause()
                isPlaying = false
            } else {
                player?.start()
                isPlaying = true
            }
            return
        }

        // New item selected: release previous player
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}

        activeItem = item
        isLoadingAudio = true
        isPlaying = false
        currentPositionMs = 0
        durationMs = 0

        try {
            val p = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(item.audioUrl)
                isLooping = this@RuqyahScreen.isLooping
                setOnPreparedListener { mp ->
                    isLoadingAudio = false
                    durationMs = mp.duration
                    mp.start()
                    isPlaying = true
                }
                setOnCompletionListener {
                    if (!this@RuqyahScreen.isLooping) {
                        isPlaying = false
                        currentPositionMs = durationMs
                    }
                }
                setOnErrorListener { _, _, _ ->
                    isLoadingAudio = false
                    isPlaying = false
                    Toast.makeText(context, "অডিও লোড করা সম্ভব হয়নি। ইন্টারনেট সংযোগ পরীক্ষা করুন।", Toast.LENGTH_SHORT).show()
                    true
                }
                prepareAsync()
            }
            player = p
        } catch (e: Exception) {
            isLoadingAudio = false
            Toast.makeText(context, "প্লেয়ার শুরু করতে সমস্যা: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleLoop() {
        val next = !isLooping
        isLooping = next
        player?.isLooping = next
        Toast.makeText(context, if (next) "লুপ চালু: অডিওটি বারবার চলবে" else "লুপ বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
    }

    fun seekRelative(offsetMs: Int) {
        player?.let { p ->
            try {
                val target = (p.currentPosition + offsetMs).coerceIn(0, durationMs.coerceAtLeast(p.duration))
                p.seekTo(target)
                currentPositionMs = target
            } catch (_: Exception) {}
        }
    }

    fun stopPlayer() {
        try {
            player?.stop()
            player?.release()
        } catch (_: Exception) {}
        player = null
        activeItem = null
        isPlaying = false
        isLoadingAudio = false
        currentPositionMs = 0
        durationMs = 0
    }

    // Filter items
    val filteredItems = remember(searchQuery, selectedCategory) {
        RuqyahData.items.filter { item ->
            val matchesCategory = selectedCategory == RuqyahCategory.All || item.category == selectedCategory
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                item.title.lowercase().contains(q) ||
                    item.reciter.lowercase().contains(q) ||
                    item.purpose.lowercase().contains(q) ||
                    item.benefits.lowercase().contains(q) ||
                    item.symptoms.any { it.lowercase().contains(q) } ||
                    item.category.bn.lowercase().contains(q)
            }
            matchesCategory && matchesSearch
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(C.Sheet)
    ) {
        PageHeader(
            title = "রুকইয়াহ শরইয়াহ",
            subtitle = "বদনজর, জাদু ও শিফার অডিও ও নিয়মাবলী",
            onBack = onBack
        )

        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.r, end = 20.r, top = 6.r, bottom = if (activeItem != null) 160.r else 40.r),
                verticalArrangement = Arrangement.spacedBy(16.r)
            ) {
                // Header Banner: What is Ruqyah & Adab
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.r))
                        .background(Brush.linearGradient(listOf(RuqyahGreenA, RuqyahGreenB)))
                        .clickable { showEtiquetteGuide = !showEtiquetteGuide }
                        .padding(20.r)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(42.r)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            RText("🛡️", 20, FontWeight.Normal, Color.White)
                        }
                        Spacer(Modifier.width(14.r))
                        Column(Modifier.weight(1f)) {
                            RText("রুকইয়াহ কী ও শোনার সুন্নাহ নিয়মাবলী", 17, FontWeight.Bold, Color.White)
                            RText("আমল ও তিলাওয়াত গ্রহণের গুরুত্বপূর্ণ দিকনির্দেশনা", 13, FontWeight.Normal, Color.White.copy(alpha = 0.85f))
                        }
                        GlyphIcon(
                            glyph = if (showEtiquetteGuide) Glyph.ChevronUp else Glyph.ChevronDown,
                            color = Color.White,
                            modifier = Modifier.size(20.r)
                        )
                    }

                    AnimatedVisibility(visible = showEtiquetteGuide) {
                        Column(
                            Modifier.padding(top = 16.r),
                            verticalArrangement = Arrangement.spacedBy(10.r)
                        ) {
                            Box(Modifier.fillMaxWidth().height(1.r).background(Color.White.copy(alpha = 0.25f)))
                            GuidePoint("১. নিয়্যাত ও তাওয়াক্কুল:", "বিশ্বাস রাখুন আরোগ্য ও শিফা প্রদানকারী একমাত্র মহান আল্লাহ তা'আলা। রুকইয়াহ একটি মাধ্যম।")
                            GuidePoint("২. শারীরিক পবিত্রতা:", "ওযু অবস্থায় কিবলামুখী হয়ে নিরিবিলি পরিবেশে একাগ্রচিত্তে বসুন বা শুয়ে পড়ুন।")
                            GuidePoint("৩. হেডফোনের ব্যবহার:", "উভয় কানে হেডফোন দিয়ে পর্যাপ্ত মনোযোগ সহকারে তিলাওয়াত শ্রবণ করলে অন্তরে তীব্র প্রভাব পড়ে।")
                            GuidePoint("৪. রুকইয়াহ পানি প্রস্তুত:", "একটি পাত্রে খাবার পানি রেখে তিলাওয়াত শেষে ৩ বার ফুঁ দিয়ে তা নিজে পান করুন বা অসুস্থ ব্যক্তিকে পান করান।")
                            GuidePoint("৫. অবিচল থাকা:", "শোনার সময় কান্না, হাই ওঠা বা অস্বস্তি লাগলে তিলাওয়াত বন্ধ করবেন না; শেষ পর্যন্ত শুনুন।")
                        }
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { RText("সমস্যা বা উপকারিতা লিখে খুঁজুন (যেমন: বদনজর, ঘুম, ভয়, শিশু)...", 14, FontWeight.Normal, C.Hint) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.r),
                    leadingIcon = {
                        GlyphIcon(Glyph.Search, C.Hint, Modifier.size(18.r))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RuqyahGreenA,
                        unfocusedBorderColor = RuqyahCardBorder,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        cursorColor = RuqyahGreenA,
                    )
                )

                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.r),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(RuqyahCategory.entries.toTypedArray()) { cat ->
                        ChoiceChip(
                            label = "${cat.icon} ${cat.bn}",
                            selected = selectedCategory == cat,
                            color = RuqyahGreenA,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }

                // Count Header
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.r),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RText(
                        "${filteredItems.size}টি রুকইয়াহ অডিও পাওয়া গেছে",
                        14,
                        FontWeight.SemiBold,
                        C.StatLabel
                    )
                    Spacer(Modifier.weight(1f))
                    if (searchQuery.isNotEmpty() || selectedCategory != RuqyahCategory.All) {
                        RText(
                            "রিসেট ফিল্টার",
                            13,
                            FontWeight.Medium,
                            RuqyahGreenA,
                            modifier = Modifier.clickable {
                                searchQuery = ""
                                selectedCategory = RuqyahCategory.All
                            }
                        )
                    }
                }

                // List of Ruqyah Cards
                if (filteredItems.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.r),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("🔍", 36, FontWeight.Normal, C.Hint)
                            Spacer(Modifier.height(8.r))
                            RText("কোনো রুকইয়াহ খুঁজে পাওয়া যায়নি", 16, FontWeight.SemiBold, C.Ink)
                            RText("ভিন্ন শব্দ লিখে অনুসন্ধান করে দেখুন", 13, FontWeight.Normal, C.Hint)
                        }
                    }
                } else {
                    filteredItems.forEach { item ->
                        RuqyahItemCard(
                            item = item,
                            isActive = activeItem?.id == item.id,
                            isPlaying = isPlaying && activeItem?.id == item.id,
                            isLoading = isLoadingAudio && activeItem?.id == item.id,
                            isExpanded = expandedItemId == item.id,
                            onTogglePlay = { playAudio(item) },
                            onToggleExpand = {
                                expandedItemId = if (expandedItemId == item.id) null else item.id
                            }
                        )
                    }
                }
            }

            // Sticky Bottom Audio Player Bar
            if (activeItem != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 14.r, vertical = 10.r)
                ) {
                    BottomPlayerCard(
                        item = activeItem!!,
                        isPlaying = isPlaying,
                        isLoading = isLoadingAudio,
                        currentMs = currentPositionMs,
                        durationMs = durationMs,
                        isLooping = isLooping,
                        onTogglePlay = { playAudio(activeItem!!) },
                        onToggleLoop = { toggleLoop() },
                        onRewind = { seekRelative(-10_000) },
                        onForward = { seekRelative(10_000) },
                        onSeekTo = { posMs ->
                            player?.seekTo(posMs)
                            currentPositionMs = posMs
                        },
                        onClose = { stopPlayer() }
                    )
                }
            }
        }
    }
}

@Composable
private fun GuidePoint(title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        RText(title, 13, FontWeight.Bold, RuqyahGold)
        Spacer(Modifier.width(6.r))
        RText(desc, 13, FontWeight.Normal, Color.White.copy(alpha = 0.9f), Modifier.weight(1f), lineHeight = 19)
    }
}

@Composable
private fun RuqyahItemCard(
    item: RuqyahItem,
    isActive: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    isExpanded: Boolean,
    onTogglePlay: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.r))
            .background(Color.White)
            .border(
                if (isActive) 1.8.r else 1.r,
                if (isActive) RuqyahGreenA else RuqyahCardBorder,
                RoundedCornerShape(20.r)
            )
            .padding(18.r)
    ) {
        // Top Badges & Category
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.r))
                    .background(RuqyahSoftMint)
                    .padding(horizontal = 10.r, vertical = 4.r)
            ) {
                RText("${item.category.icon} ${item.category.bn}", 12, FontWeight.SemiBold, RuqyahGreenA)
            }
            Spacer(Modifier.width(8.r))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.r))
                    .background(C.CalBg)
                    .padding(horizontal = 8.r, vertical = 4.r)
            ) {
                RText("⏱️ ${item.durationText}", 12, FontWeight.Medium, C.StatLabel)
            }
            Spacer(Modifier.weight(1f))
            if (isActive) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(RuqyahGreenA.copy(alpha = 0.15f))
                        .padding(horizontal = 10.r, vertical = 4.r)
                ) {
                    RText(if (isPlaying) "▶ চলছে" else "⏸ পজ করা", 12, FontWeight.Bold, RuqyahGreenA)
                }
            }
        }

        Spacer(Modifier.height(10.r))

        // Title and Arabic
        RText(item.title, 18, FontWeight.Bold, C.Ink)
        RText(item.arabicTitle, 14, FontWeight.Normal, RuqyahGreenB.copy(alpha = 0.7f))

        Spacer(Modifier.height(4.r))
        RText("🎙️ ${item.reciter}", 13, FontWeight.Medium, C.StatLabel)

        Spacer(Modifier.height(12.r))

        // Highlight Box: কোন কাজের উপকার (Purpose & Benefit)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.r))
                .background(RuqyahSoftMint.copy(alpha = 0.7f))
                .border(1.r, RuqyahGreenA.copy(alpha = 0.2f), RoundedCornerShape(14.r))
                .padding(12.r)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("✨ কাজের মূল উপকারিতা:", 13, FontWeight.Bold, RuqyahGreenB)
            }
            Spacer(Modifier.height(4.r))
            RText(item.purpose, 13, FontWeight.Normal, C.Ink, lineHeight = 19)
        }

        Spacer(Modifier.height(14.r))

        // Action Buttons Row: Play Button & Details Button
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.r)
        ) {
            // Play / Pause Button
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.r))
                    .background(if (isActive && isPlaying) RuqyahGreenB else RuqyahGreenA)
                    .clickable(onClick = onTogglePlay)
                    .padding(vertical = 12.r),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.r))
                        RText("লোড হচ্ছে...", 14, FontWeight.SemiBold, Color.White)
                    } else {
                        GlyphIcon(
                            glyph = if (isActive && isPlaying) Glyph.Pause else Glyph.Play,
                            color = Color.White,
                            modifier = Modifier.size(16.r)
                        )
                        Spacer(Modifier.width(8.r))
                        RText(
                            if (isActive && isPlaying) "বিরতি দিন" else if (isActive) "চালিয়ে যান" else "তিলাওয়াত শুনুন",
                            14,
                            FontWeight.SemiBold,
                            Color.White
                        )
                    }
                }
            }

            // Expand / Details Button
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.r))
                    .background(C.CalBg)
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 14.r, vertical = 12.r),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RText(if (isExpanded) "সংক্ষিপ্ত করুন" else "কাজের বিবরণ ও লক্ষণ", 13, FontWeight.SemiBold, C.Ink)
                    Spacer(Modifier.width(4.r))
                    GlyphIcon(
                        glyph = if (isExpanded) Glyph.ChevronUp else Glyph.ChevronDown,
                        color = C.Ink,
                        modifier = Modifier.size(16.r)
                    )
                }
            }
        }

        // Expanded Section with Detailed Information
        AnimatedVisibility(visible = isExpanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.r),
                verticalArrangement = Arrangement.spacedBy(12.r)
            ) {
                Box(Modifier.fillMaxWidth().height(1.r).background(RuqyahCardBorder))

                // Detailed Benefits
                DetailSection(
                    icon = "📖",
                    title = "বিস্তারিত উপকারিতা ও ফজিলত",
                    content = item.benefits
                )

                // Symptoms / When to Listen
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RText("🩺", 14, FontWeight.Normal, C.Ink)
                        Spacer(Modifier.width(6.r))
                        RText("কোন কোন সমস্যা বা লক্ষণে শুনবেন:", 13, FontWeight.Bold, C.Ink)
                    }
                    Spacer(Modifier.height(6.r))
                    item.symptoms.forEach { symptom ->
                        Row(
                            Modifier.padding(vertical = 2.r, horizontal = 4.r),
                            verticalAlignment = Alignment.Top
                        ) {
                            RText("•", 13, FontWeight.Bold, RuqyahGreenA)
                            Spacer(Modifier.width(6.r))
                            RText(symptom, 13, FontWeight.Normal, C.StatLabelBn, Modifier.weight(1f))
                        }
                    }
                }

                // Instructions / Adab
                DetailSection(
                    icon = "🤲",
                    title = "আমল ও তিলাওয়াত শোনার নিয়ম",
                    content = item.instructions
                )

                // Key Verses
                DetailSection(
                    icon = "📜",
                    title = "তিলাওয়াতকৃত মূল আয়াতসমূহ",
                    content = item.keyVerses
                )
            }
        }
    }
}

@Composable
private fun DetailSection(icon: String, title: String, content: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText(icon, 14, FontWeight.Normal, C.Ink)
            Spacer(Modifier.width(6.r))
            RText(title, 13, FontWeight.Bold, C.Ink)
        }
        Spacer(Modifier.height(4.r))
        RText(content, 13, FontWeight.Normal, C.StatLabelBn, lineHeight = 19)
    }
}

@Composable
private fun BottomPlayerCard(
    item: RuqyahItem,
    isPlaying: Boolean,
    isLoading: Boolean,
    currentMs: Int,
    durationMs: Int,
    isLooping: Boolean,
    onTogglePlay: () -> Unit,
    onToggleLoop: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSeekTo: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.r))
            .background(Color(0xFF1E293B))
            .padding(horizontal = 18.r, vertical = 14.r)
    ) {
        // Track Title & Close Button
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.r)
                    .clip(CircleShape)
                    .background(RuqyahGreenA.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                RText(item.category.icon, 18, FontWeight.Normal, Color.White)
            }
            Spacer(Modifier.width(10.r))
            Column(Modifier.weight(1f)) {
                RText(item.title, 15, FontWeight.Bold, Color.White, maxLines = 1)
                RText(item.reciter, 12, FontWeight.Medium, Color.White.copy(alpha = 0.7f), maxLines = 1)
            }

            // Loop Toggle Button
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(if (isLooping) RuqyahGreenA else Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onToggleLoop)
                    .padding(8.r)
            ) {
                RText(if (isLooping) "🔁 লুপ চালু" else "🔁", 12, FontWeight.Bold, Color.White)
            }

            Spacer(Modifier.width(8.r))

            // Close Player
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onClose)
                    .padding(8.r)
            ) {
                GlyphIcon(Glyph.Close, Color.White, Modifier.size(16.r))
            }
        }

        Spacer(Modifier.height(8.r))

        // Progress Slider
        val safeDuration = durationMs.coerceAtLeast(1)
        val progress = (currentMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)

        Slider(
            value = progress,
            onValueChange = { frac ->
                val targetMs = (frac * safeDuration).toInt()
                onSeekTo(targetMs)
            },
            colors = SliderDefaults.colors(
                thumbColor = RuqyahGreenA,
                activeTrackColor = RuqyahGreenA,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth().height(20.r)
        )

        // Elapsed / Remaining Time
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RText(formatTime(currentMs), 11, FontWeight.Medium, Color.White.copy(alpha = 0.7f))
            RText(if (durationMs > 0) formatTime(durationMs) else item.durationText, 11, FontWeight.Medium, Color.White.copy(alpha = 0.7f))
        }

        Spacer(Modifier.height(6.r))

        // Controls Row: -10s, Play/Pause, +10s
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // -10s
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onRewind)
                    .padding(horizontal = 14.r, vertical = 8.r)
            ) {
                RText("-১০ সে.", 12, FontWeight.Bold, Color.White)
            }

            Spacer(Modifier.width(20.r))

            // Play / Pause Circle
            Box(
                Modifier
                    .size(52.r)
                    .clip(CircleShape)
                    .background(RuqyahGreenA)
                    .clickable(onClick = onTogglePlay),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    GlyphIcon(
                        glyph = if (isPlaying) Glyph.Pause else Glyph.Play,
                        color = Color.White,
                        modifier = Modifier.size(24.r)
                    )
                }
            }

            Spacer(Modifier.width(20.r))

            // +10s
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onForward)
                    .padding(horizontal = 14.r, vertical = 8.r)
            ) {
                RText("+১০ সে.", 12, FontWeight.Bold, Color.White)
            }
        }
    }
}
