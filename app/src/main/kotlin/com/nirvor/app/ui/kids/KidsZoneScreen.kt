package com.nirvor.app.ui.kids

import android.app.Activity
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirvor.app.NirvorApplication
import com.nirvor.app.islamic.bnDigits
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun KidsZoneScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as NirvorApplication
    val prefs = app.prefs
    val activity = context as? Activity

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Videos, 1: Learning, 2: Drawing, 3: Games
    var activePlayingVideo by remember { mutableStateOf<KidVideo?>(null) }
    var selectedVideoCategory by remember { mutableStateOf("সবগুলো") }
    var selectedLearnGroup by remember { mutableStateOf("স্বরবর্ণ") }

    // Screen Time Limit
    var remainingSeconds by remember {
        val mins = prefs.kidsTimeLimitMins
        mutableIntStateOf(if (mins > 0) mins * 60 else -1)
    }
    var isTimeExpired by remember { mutableStateOf(false) }

    // Pin Dialog State
    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogTarget by remember { mutableStateOf("exit") } // "exit", "settings", "unlock_sleep"
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Parent Settings Modal
    var showParentSettings by remember { mutableStateOf(false) }
    var timeLimitChoice by remember { mutableIntStateOf(prefs.kidsTimeLimitMins) }
    var pinningEnabled by remember { mutableStateOf(prefs.kidsScreenPinningEnabled) }

    // TextToSpeech for pronunciation
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var speech: TextToSpeech? = null
        speech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speech?.let { s ->
                    val res = s.setLanguage(Locale("bn", "BD"))
                    if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                        s.setLanguage(Locale.US)
                    }
                }
            }
        }
        tts = speech
        onDispose {
            speech?.stop()
            speech?.shutdown()
        }
    }

    // Try starting lock task if enabled
    LaunchedEffect(Unit) {
        if (pinningEnabled) {
            try {
                activity?.startLockTask()
            } catch (_: Exception) {}
        }
    }

    // Timer Countdown
    LaunchedEffect(remainingSeconds, isTimeExpired) {
        if (remainingSeconds > 0 && !isTimeExpired) {
            delay(1000L)
            remainingSeconds--
            if (remainingSeconds <= 0) {
                isTimeExpired = true
            }
        }
    }

    // Intercept Back Press to require Parental PIN
    BackHandler(enabled = true) {
        pinDialogTarget = "exit"
        enteredPin = ""
        pinError = false
        showPinDialog = true
    }

    fun exitKidsZone() {
        try {
            activity?.stopLockTask()
        } catch (_: Exception) {}
        onBack()
    }

    // Main Container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Playful Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFF6584), Color(0xFFFF8E53))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Kids Avatar & Name
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🧸", fontSize = 24.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "কিডস জোন",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "মজার পড়া ও খেলাধুলা",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.5.sp,
                        )
                    }
                }

                // Timer Chip & Action Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (remainingSeconds > 0) {
                        val minsLeft = remainingSeconds / 60
                        val secsLeft = remainingSeconds % 60
                        val isUrgent = remainingSeconds < 300 // under 5m
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isUrgent) Color(0xFFEF4444) else Color.Black.copy(alpha = 0.22f))
                                .padding(horizontal = 9.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = "⏱️ ${bnDigits(minsLeft.toString())}:${bnDigits(secsLeft.toString().padStart(2, '0'))}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    // Parent Settings / Lock Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable {
                                pinDialogTarget = "settings"
                                enteredPin = ""
                                pinError = false
                                showPinDialog = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("⚙️", fontSize = 16.sp)
                    }

                    Spacer(Modifier.width(8.dp))

                    // Exit Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable {
                                pinDialogTarget = "exit"
                                enteredPin = ""
                                pinError = false
                                showPinDialog = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🚪", fontSize = 16.sp)
                    }
                }
            }

            // 2. Playful Tab Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                val tabs = listOf(
                    "🎬 কার্টুন" to 0,
                    "📖 পড়ালেখা" to 1,
                    "🎨 ড্রয়িং" to 2,
                    "🎮 খেলাধুলা" to 3,
                )
                tabs.forEach { (title, idx) ->
                    val isSel = selectedTab == idx
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSel) Color(0xFFFF6584) else Color.Transparent)
                            .clickable { selectedTab = idx }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = title,
                            color = if (isSel) Color.White else Color(0xFF64748B),
                            fontSize = 13.5.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }

            // 3. Tab Contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (selectedTab) {
                    // TAB 0: কার্টুন ও ভিডিও
                    0 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Category Filter
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(KidsRepository.categories) { cat ->
                                    val isCatSel = selectedVideoCategory == cat
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isCatSel) Color(0xFF0F172A) else Color.White)
                                            .clickable { selectedVideoCategory = cat }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isCatSel) Color.White else Color(0xFF475569),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }

                            // Video List
                            val filteredVideos = if (selectedVideoCategory == "সবগুলো") {
                                KidsRepository.videos
                            } else {
                                KidsRepository.videos.filter { it.category == selectedVideoCategory }
                            }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(filteredVideos) { video ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { activePlayingVideo = video },
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // Play Thumbnail Avatar
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(Color(video.color)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text("▶️", fontSize = 26.sp)
                                            }

                                            Spacer(Modifier.width(14.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = video.title,
                                                    color = Color(0xFF0F172A),
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 2,
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = "📺 ${video.channel}",
                                                        color = Color(0xFF64748B),
                                                        fontSize = 11.5.sp,
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "⏱️ ${video.duration}",
                                                        color = Color(0xFFE11D48),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(16.dp)) }
                            }
                        }
                    }

                    // TAB 1: পড়াশোনা ও বর্ণমালা
                    1 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Learning sub-group switcher
                            val learnGroups = listOf("স্বরবর্ণ", "ব্যঞ্জনবর্ণ", "English", "সংখ্যা")
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(learnGroups) { grp ->
                                    val isGrpSel = selectedLearnGroup == grp
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isGrpSel) Color(0xFF8B5CF6) else Color.White)
                                            .clickable { selectedLearnGroup = grp }
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                    ) {
                                        Text(
                                            text = grp,
                                            color = if (isGrpSel) Color.White else Color(0xFF475569),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            val itemsList = when (selectedLearnGroup) {
                                "স্বরবর্ণ" -> KidsRepository.swarabarna
                                "ব্যঞ্জনবর্ণ" -> KidsRepository.byanjanbarna
                                "English" -> KidsRepository.englishAlphabets
                                else -> KidsRepository.numbers
                            }

                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(itemsList) { item ->
                                    Card(
                                        modifier = Modifier
                                            .height(125.dp)
                                            .clickable {
                                                // Speak with TTS
                                                tts?.speak(
                                                    "${item.char}. ${item.word}. ${item.meaning}",
                                                    TextToSpeech.QUEUE_FLUSH,
                                                    null,
                                                    "kid_tts_${item.char}"
                                                )
                                                Toast.makeText(context, "${item.char} - ${item.word}", Toast.LENGTH_SHORT).show()
                                            },
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                text = item.char,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF1E293B),
                                            )
                                            Text(
                                                text = item.emoji,
                                                fontSize = 24.sp,
                                            )
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = item.word,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF475569),
                                            )
                                        }
                                    }
                                }
                                item { Spacer(Modifier.height(20.dp)) }
                            }
                        }
                    }

                    // TAB 2: ম্যাজিক স্লেট (Drawing)
                    2 -> {
                        KidsDrawingCanvas()
                    }

                    // TAB 3: খেলাধুলা (Mini Games)
                    3 -> {
                        var selectedGame by remember { mutableIntStateOf(0) }
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (selectedGame == 0) Color(0xFF0284C7) else Color.White)
                                        .clickable { selectedGame = 0 }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("🎈 বেলুন পপ", color = if (selectedGame == 0) Color.White else Color(0xFF334155), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (selectedGame == 1) Color(0xFF0284C7) else Color.White)
                                        .clickable { selectedGame = 1 }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("🐾 ছবি মেলানো", color = if (selectedGame == 1) Color.White else Color(0xFF334155), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                            ) {
                                if (selectedGame == 0) {
                                    BalloonPopGame()
                                } else {
                                    MemoryMatchGame()
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Playing Video Fullscreen Modal
        activePlayingVideo?.let { v ->
            KidsVideoPlayerDialog(
                video = v,
                onClose = { activePlayingVideo = null },
            )
        }

        // Screen Time Expired (Sleep Mode) Fullscreen Lock Overlay
        if (isTimeExpired) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0B132B).copy(alpha = 0.98f))
                    .clickable(onClick = {}),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text("🌙", fontSize = 72.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "আজকের মতো খেলার সময় শেষ!",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "চোখের বিশ্রামের জন্য ফোন রেখে ঘুমিয়ে পড়ো বা বই পড়ো! 😴",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(28.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF3B82F6))
                            .clickable {
                                pinDialogTarget = "unlock_sleep"
                                enteredPin = ""
                                pinError = false
                                showPinDialog = true
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = "🔓 অভিভাবক আনলক করুন",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Parental PIN Dialog
        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isTimeExpired) showPinDialog = false
                },
                title = {
                    Text("🔒 অভিভাবক যাচাইকরণ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (pinDialogTarget == "exit") "কিডস জোন থেকে বের হতে পিন দিন"
                            else if (pinDialogTarget == "settings") "সেটিংস খুলতে পিন দিন"
                            else "স্ক্রিন আনলক করতে পিন দিন",
                            fontSize = 13.5.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))

                        // 4 Dots for PIN
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (i in 0 until 4) {
                                val isFilled = enteredPin.length > i
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isFilled) Color(0xFF2563EB)
                                            else Color(0xFFE2E8F0)
                                        )
                                        .border(1.dp, Color(0xFF94A3B8), CircleShape),
                                )
                            }
                        }

                        if (pinError) {
                            Spacer(Modifier.height(8.dp))
                            Text("❌ ভুল পিন! আবার চেষ্টা করুন।", color = Color(0xFFDC2626), fontSize = 12.sp)
                        }

                        Spacer(Modifier.height(18.dp))

                        // Custom Numeric Keypad
                        val keypad = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                            listOf("C", "0", "⌫"),
                        )
                        keypad.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                row.forEach { key ->
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF1F5F9))
                                            .clickable {
                                                when (key) {
                                                    "C" -> enteredPin = ""
                                                    "⌫" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                                    else -> {
                                                        if (enteredPin.length < 4) {
                                                            enteredPin += key
                                                            if (enteredPin.length == 4) {
                                                                if (enteredPin == prefs.kidsPin) {
                                                                    showPinDialog = false
                                                                    when (pinDialogTarget) {
                                                                        "exit" -> exitKidsZone()
                                                                        "settings" -> showParentSettings = true
                                                                        "unlock_sleep" -> {
                                                                            isTimeExpired = false
                                                                            remainingSeconds = prefs.kidsTimeLimitMins * 60
                                                                        }
                                                                    }
                                                                } else {
                                                                    pinError = true
                                                                    enteredPin = ""
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(key, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    if (!isTimeExpired) {
                        TextButton(onClick = { showPinDialog = false }) {
                            Text("বাতিল", color = Color(0xFF64748B))
                        }
                    }
                },
            )
        }

        // Parent Settings Dialog
        if (showParentSettings) {
            AlertDialog(
                onDismissRequest = { showParentSettings = false },
                title = {
                    Text("⚙️ অভিভাবক কন্ট্রোল", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                text = {
                    Column {
                        Text("স্ক্রিন টাইম লিমিট নির্ধারণ করুন:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        val timeOptions = listOf(
                            "আনলিমিটেড" to 0,
                            "১৫ মিনিট" to 15,
                            "৩০ মিনিট" to 30,
                            "৪৫ মিনিট" to 45,
                            "১ ঘণ্টা" to 60,
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            timeOptions.forEach { (label, mins) ->
                                val isOptSel = timeLimitChoice == mins
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isOptSel) Color(0xFF2563EB) else Color(0xFFF1F5F9))
                                        .clickable { timeLimitChoice = mins }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOptSel) Color.White else Color(0xFF334155),
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Screen Pinning Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("অ্যাপ পিন / কিয়স্ক মোড", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text("বাচ্চারা যেন হোম বা ব্যাক চেপে বের না হতে পারে", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                            Switch(
                                checked = pinningEnabled,
                                onCheckedChange = { pinningEnabled = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF2563EB)),
                            )
                        }

                        Spacer(Modifier.height(14.dp))
                        Text("ডিফল্ট প্যারেন্টাল পিন: 1234", fontSize = 12.sp, color = Color(0xFF059669), fontWeight = FontWeight.Medium)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.kidsTimeLimitMins = timeLimitChoice
                        prefs.kidsScreenPinningEnabled = pinningEnabled
                        remainingSeconds = if (timeLimitChoice > 0) timeLimitChoice * 60 else -1
                        showParentSettings = false
                        Toast.makeText(context, "কিডস জোন সেটিংস সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("সংরক্ষণ", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showParentSettings = false }) {
                        Text("বন্ধ", color = Color(0xFF64748B))
                    }
                },
            )
        }
    }
}
