package com.nirvor.app.ui.kids

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

// ---------------------------------------------------------------- Balloon Pop Game

data class Balloon(
    val id: Int,
    val text: String,
    val color: Color,
    val xFraction: Float,
    var isPopped: Boolean = false,
)

@Composable
fun BalloonPopGame(modifier: Modifier = Modifier) {
    var score by remember { mutableIntStateOf(0) }
    val balloons = remember { mutableStateListOf<Balloon>() }
    var balloonCounter by remember { mutableIntStateOf(0) }

    val emojis = listOf("⭐", "🎈", "🍎", "🐱", "১", "২", "A", "B", "🌸", "🚗")
    val balloonColors = listOf(
        Color(0xFFFF3366), Color(0xFF3B82F6), Color(0xFF10B981),
        Color(0xFFF59E0B), Color(0xFF8B5CF6), Color(0xFFEC4899)
    )

    // Spawn balloons periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(1200L)
            if (balloons.size < 7) {
                balloonCounter++
                balloons.add(
                    Balloon(
                        id = balloonCounter,
                        text = emojis.random(),
                        color = balloonColors.random(),
                        xFraction = Random.nextFloat().coerceIn(0.1f, 0.85f),
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF38BDF8), Color(0xFF93C5FD), Color(0xFFE0F2FE))
                )
            ),
    ) {
        // Top score bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "🎯 স্কোর: $score",
                    color = Color(0xFF0F172A),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFDE047))
                    .clickable {
                        score = 0
                        balloons.clear()
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "🔄 নতুন খেলা",
                    color = Color(0xFF78350F),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Balloons flying
        balloons.forEach { b ->
            if (!b.isPopped) {
                FloatingBalloon(
                    balloon = b,
                    onPop = {
                        b.isPopped = true
                        score += 10
                        balloons.remove(b)
                    },
                    onOutOfScreen = {
                        balloons.remove(b)
                    },
                )
            }
        }
    }
}

@Composable
private fun FloatingBalloon(
    balloon: Balloon,
    onPop: () -> Unit,
    onOutOfScreen: () -> Unit,
) {
    val yAnim = remember { Animatable(1000f) }

    LaunchedEffect(Unit) {
        yAnim.animateTo(
            targetValue = -200f,
            animationSpec = tween(durationMillis = 6500, easing = LinearEasing),
        )
        onOutOfScreen()
    }

    Box(
        modifier = Modifier
            .offset { IntOffset((balloon.xFraction * 900).toInt(), yAnim.value.toInt()) }
            .size(76.dp)
            .clip(CircleShape)
            .background(balloon.color)
            .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            .clickable { onPop() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = balloon.text,
            fontSize = 28.sp,
        )
    }
}

// ---------------------------------------------------------------- Animal Matching Game

data class CardItem(
    val id: Int,
    val icon: String,
    val name: String,
    var isFlipped: Boolean = false,
    var isMatched: Boolean = false,
)

@Composable
fun MemoryMatchGame(modifier: Modifier = Modifier) {
    val initialAnimals = remember {
        listOf(
            "🐱" to "বিড়াল",
            "🐶" to "কুকুর",
            "🐰" to "খরগোশ",
            "🦁" to "সিংহ",
        )
    }

    val cards = remember {
        mutableStateListOf<CardItem>().apply {
            val list = mutableListOf<CardItem>()
            var idx = 0
            initialAnimals.forEach { (emoji, name) ->
                list.add(CardItem(idx++, emoji, name))
                list.add(CardItem(idx++, emoji, name))
            }
            list.shuffle()
            addAll(list)
        }
    }

    var selectedFirst by remember { mutableStateOf<CardItem?>(null) }
    var selectedSecond by remember { mutableStateOf<CardItem?>(null) }
    var matchesFound by remember { mutableIntStateOf(0) }

    fun restart() {
        cards.clear()
        val list = mutableListOf<CardItem>()
        var idx = 0
        initialAnimals.forEach { (emoji, name) ->
            list.add(CardItem(idx++, emoji, name))
            list.add(CardItem(idx++, emoji, name))
        }
        list.shuffle()
        cards.addAll(list)
        selectedFirst = null
        selectedSecond = null
        matchesFound = 0
    }

    LaunchedEffect(selectedFirst, selectedSecond) {
        if (selectedFirst != null && selectedSecond != null) {
            delay(700L)
            if (selectedFirst!!.icon == selectedSecond!!.icon) {
                selectedFirst!!.isMatched = true
                selectedSecond!!.isMatched = true
                matchesFound++
            } else {
                selectedFirst!!.isFlipped = false
                selectedSecond!!.isFlipped = false
            }
            selectedFirst = null
            selectedSecond = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFF8FAFC))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🐾 মেমরি ম্যাচ (${matchesFound}/4 জোড়া)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE2E8F0))
                    .clickable { restart() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("🔄 রিসেট", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
            }
        }

        Spacer(Modifier.height(14.dp))

        if (matchesFound == 4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFDCFCE7))
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🎉 অভিনন্দন! সব জোড়া মিলে গেছে! 🌟",
                    color = Color(0xFF15803D),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(cards) { _, card ->
                val showFront = card.isFlipped || card.isMatched
                Box(
                    modifier = Modifier
                        .height(95.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (card.isMatched) Color(0xFFBBF7D0)
                            else if (showFront) Color.White
                            else Color(0xFF6366F1)
                        )
                        .border(
                            width = 2.dp,
                            color = if (card.isMatched) Color(0xFF22C55E) else Color(0xFFCBD5E1),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable(enabled = !showFront && selectedSecond == null) {
                            card.isFlipped = true
                            if (selectedFirst == null) {
                                selectedFirst = card
                            } else {
                                selectedSecond = card
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (showFront) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(card.icon, fontSize = 34.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(card.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                        }
                    } else {
                        Text("❓", fontSize = 28.sp)
                    }
                }
            }
        }
    }
}
