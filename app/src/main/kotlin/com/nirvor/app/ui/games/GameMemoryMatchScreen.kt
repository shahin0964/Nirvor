package com.nirvor.app.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import kotlinx.coroutines.delay

private data class MemoryCard(
    val id: Int,
    val emoji: String,
    val isFlipped: Boolean = false,
    val isMatched: Boolean = false,
)

@Composable
fun GameMemoryMatchScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val emojis = listOf("🚀", "💎", "🎮", "🍕", "🐱", "🌟", "🎸", "🏆")

    fun initDeck(): List<MemoryCard> {
        val paired = (emojis + emojis).shuffled()
        return paired.mapIndexed { index, emoji ->
            MemoryCard(id = index, emoji = emoji)
        }
    }

    var cards by remember { mutableStateOf(initDeck()) }
    var moves by remember { mutableIntStateOf(0) }
    var matchesFound by remember { mutableIntStateOf(0) }
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var selectedFirst by remember { mutableStateOf<Int?>(null) }
    var selectedSecond by remember { mutableStateOf<Int?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var bestTime by remember { mutableIntStateOf(GameStorage.getBestTime(context, "memory")) }

    val isWon = matchesFound == emojis.size

    fun restartGame() {
        cards = initDeck()
        moves = 0
        matchesFound = 0
        secondsElapsed = 0
        isTimerRunning = false
        selectedFirst = null
        selectedSecond = null
        isChecking = false
    }

    // Stopwatch loop
    LaunchedEffect(isTimerRunning, isWon) {
        while (isTimerRunning && !isWon) {
            delay(1000L)
            secondsElapsed += 1
        }
    }

    // Match checking logic
    LaunchedEffect(selectedFirst, selectedSecond) {
        val first = selectedFirst
        val second = selectedSecond
        if (first != null && second != null) {
            isChecking = true
            moves += 1
            if (cards[first].emoji == cards[second].emoji) {
                delay(300L)
                cards = cards.mapIndexed { idx, card ->
                    if (idx == first || idx == second) card.copy(isMatched = true) else card
                }
                matchesFound += 1
            } else {
                delay(700L)
                cards = cards.mapIndexed { idx, card ->
                    if (idx == first || idx == second) card.copy(isFlipped = false) else card
                }
            }
            selectedFirst = null
            selectedSecond = null
            isChecking = false
        }
    }

    // Win check
    LaunchedEffect(isWon) {
        if (isWon) {
            isTimerRunning = false
            if (secondsElapsed < bestTime) {
                bestTime = secondsElapsed
                GameStorage.saveBestTime(context, "memory", secondsElapsed)
            }
        }
    }

    fun onCardClick(index: Int) {
        if (isChecking || isWon) return
        val card = cards[index]
        if (card.isFlipped || card.isMatched) return

        if (!isTimerRunning) isTimerRunning = true

        cards = cards.mapIndexed { idx, c ->
            if (idx == index) c.copy(isFlipped = true) else c
        }

        if (selectedFirst == null) {
            selectedFirst = index
        } else if (selectedSecond == null && selectedFirst != index) {
            selectedSecond = index
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFBF7FF)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.r)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(14.r))

            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        GlyphIcon(Glyph.ArrowLeft, C.Ink, Modifier.size(24.r), strokeWidth = 2f)
                    }
                    Spacer(Modifier.width(6.r))
                    Column {
                        RText("Memory Match", 24, FontWeight.Bold, Color(0xFF5B21B6))
                        RText("স্মৃতি মেলানো কার্ড", 15, FontWeight.Normal, C.Hint)
                    }
                }

                Button(
                    onClick = { restartGame() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(12.r),
                ) {
                    GlyphIcon(Glyph.Refresh, Color.White, Modifier.size(16.r), strokeWidth = 2f)
                    Spacer(Modifier.width(6.r))
                    RText("রিসেট", 14, FontWeight.SemiBold, Color.White)
                }
            }

            Spacer(Modifier.height(14.r))

            // Scoreboard Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.r),
            ) {
                StatCard("চাল (Moves)", "$moves", Color(0xFF7C3AED), Modifier.weight(1f))
                StatCard("মিল (Pairs)", "$matchesFound/8", Color(0xFF059669), Modifier.weight(1f))
                StatCard("সময় (Time)", "${secondsElapsed}s", Color(0xFFD97706), Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.r))

            // 4x4 Cards Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.r))
                    .background(Color.White)
                    .border(1.r, Color(0xFFE9D5FF), RoundedCornerShape(20.r))
                    .padding(10.r),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.r),
                ) {
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.r),
                        ) {
                            for (col in 0 until 4) {
                                val idx = row * 4 + col
                                val card = cards[idx]
                                FlipCardView(
                                    card = card,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onCardClick(idx) },
                                )
                            }
                        }
                    }
                }

                // Win Celebration Overlay
                AnimatedVisibility(visible = isWon, enter = fadeIn() + scaleIn()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.r))
                            .background(Color.White.copy(alpha = 0.96f))
                            .padding(20.r),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("অসাধারণ! 🏆", 26, FontWeight.Bold, Color(0xFF5B21B6))
                            Spacer(Modifier.height(6.r))
                            val stars = when {
                                moves <= 14 -> "⭐⭐⭐ চমৎকার স্মৃতিশক্তি!"
                                moves <= 22 -> "⭐⭐ দারুণ খেলা!"
                                else -> "⭐ সফলভাবে সম্পন্ন!"
                            }
                            RText(stars, 16, FontWeight.SemiBold, Color(0xFFD97706))
                            Spacer(Modifier.height(8.r))
                            RText("মোট চাল: $moves · সময়: ${secondsElapsed} সেকেন্ড", 14, FontWeight.Normal, C.Hint)
                            Spacer(Modifier.height(18.r))
                            Button(
                                onClick = { restartGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                shape = RoundedCornerShape(12.r),
                            ) {
                                RText("আবার খেলুন 🔄", 15, FontWeight.Bold, Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.r))

            RText("একই ছবির দুটি কার্ড খুঁজে বের করে জোড়া মিলান", 14, FontWeight.Normal, C.Hint)

            Spacer(Modifier.height(24.r))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.r),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.r, horizontal = 8.r),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RText(label, 12, FontWeight.Medium, C.Hint, align = TextAlign.Center)
            Spacer(Modifier.height(2.r))
            RText(value, 18, FontWeight.Bold, color)
        }
    }
}

@Composable
private fun FlipCardView(card: MemoryCard, modifier: Modifier, onClick: () -> Unit) {
    val rotation by animateFloatAsState(
        targetValue = if (card.isFlipped || card.isMatched) 180f else 0f,
        animationSpec = tween(300),
        label = "cardFlip",
    )

    val isRevealed = rotation > 90f

    val bg = when {
        card.isMatched -> Color(0xFFD1FAE5)
        isRevealed -> Color(0xFFEDE9FE)
        else -> Color(0xFF8B5CF6)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(10.r))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isRevealed) {
            Box(
                modifier = Modifier.graphicsLayer { rotationY = 180f },
                contentAlignment = Alignment.Center,
            ) {
                RText(card.emoji, 26, FontWeight.Bold, Color.Black)
            }
        } else {
            RText("❓", 20, FontWeight.Bold, Color.White)
        }
    }
}
