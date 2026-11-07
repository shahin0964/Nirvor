package com.nirvor.app.ui.games.duelx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private data class PokerCard(
    val rank: String,
    val suit: String,
    val color: Color,
    val value: Int,
)

private data class PokerBall(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val card: PokerCard,
    var isPocketed: Boolean = false,
)

@Composable
fun DuelXBilliardPokerGame(
    profile: DuelXProfile,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var shotsLeft by remember { mutableIntStateOf(5) }
    var showMenu by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }
    var wonCoins by remember { mutableIntStateOf(0) }
    var handResult by remember { mutableStateOf("HIGH CARD") }

    var aimAngle by remember { mutableFloatStateOf(0f) }
    var shootPower by remember { mutableFloatStateOf(0f) }

    val handCards = remember { mutableStateListOf<PokerCard>() }
    val balls = remember { mutableStateListOf<PokerBall>() }

    val suits = listOf(
        Pair("♠", Color(0xFF0F172A)),
        Pair("♥", Color(0xFFDC2626)),
        Pair("♣", Color(0xFF1E293B)),
        Pair("♦", Color(0xFFDC2626)),
    )
    val ranks = listOf(
        Pair("A", 14), Pair("K", 13), Pair("Q", 12), Pair("J", 11),
        Pair("10", 10), Pair("9", 9), Pair("8", 8), Pair("7", 7),
    )

    fun evaluateHand(cards: List<PokerCard>): Pair<String, Int> {
        if (cards.isEmpty()) return Pair("NO HAND", 0)
        val rankCounts = cards.groupBy { it.value }.mapValues { it.value.size }
        val counts = rankCounts.values.sortedDescending()
        val isFlush = cards.size >= 5 && cards.groupBy { it.suit }.any { it.value.size >= 5 }

        return when {
            counts.contains(4) -> Pair("FOUR OF A KIND! 🎉", 500)
            counts.contains(3) && counts.contains(2) -> Pair("FULL HOUSE! 🌟", 350)
            isFlush -> Pair("FLUSH! 💎", 250)
            counts.contains(3) -> Pair("THREE OF A KIND! ⚡", 150)
            counts.count { it == 2 } >= 2 -> Pair("TWO PAIR! 🎯", 100)
            counts.contains(2) -> Pair("ONE PAIR! 🎲", 50)
            else -> Pair("HIGH CARD", 20)
        }
    }

    fun setupTable(w: Float, h: Float) {
        balls.clear()
        handCards.clear()
        shotsLeft = 5
        isGameOver = false
        wonCoins = 0
        val ballR = h * 0.042f

        // Cue ball
        balls.add(
            PokerBall(
                id = 0,
                x = w * 0.25f,
                y = h * 0.5f,
                card = PokerCard("CUE", "⚪", Color.White, 0),
            )
        )

        // 8 Target balls with poker cards
        val deck = mutableListOf<PokerCard>()
        for (s in suits) {
            for (r in ranks) {
                deck.add(PokerCard(r.first, s.first, s.second, r.second))
            }
        }
        deck.shuffle()

        val startX = w * 0.65f
        val centerY = h * 0.5f
        val spacing = ballR * 2.3f

        for (i in 0 until 8) {
            val row = i % 4
            val col = i / 4
            val px = startX + col * spacing * 1.3f
            val py = (centerY - 1.5f * spacing) + row * spacing
            balls.add(PokerBall(i + 1, px, py, 0f, 0f, deck[i]))
        }
    }

    fun finishGame() {
        isGameOver = true
        val (name, prize) = evaluateHand(handCards)
        handResult = name
        wonCoins = prize
        DuelXStorage.addCoins(context, prize)
    }

    val tableStyle = DuelXTables.styles.getOrElse(profile.selectedTableIndex) { DuelXTables.styles.first() }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF070B14)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 16.r, vertical = 6.r),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        GlyphIcon(Glyph.ArrowLeft, Color.White, Modifier.size(22.r))
                    }
                    Spacer(Modifier.width(6.r))
                    RText("BILLIARD POKER", 16, FontWeight.ExtraBold, Color(0xFF8B5CF6))
                    Spacer(Modifier.width(10.r))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.r))
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.2f))
                            .padding(horizontal = 10.r, vertical = 3.r),
                    ) {
                        RText("SHOTS LEFT: $shotsLeft", 12, FontWeight.Bold, Color(0xFFC084FC))
                    }
                }

                // Current Hand Cards in Top Bar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.r),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RText("YOUR HAND: ", 11, FontWeight.Bold, Color(0xFF94A3B8))
                    if (handCards.isEmpty()) {
                        RText("POCKET BALLS TO DRAW CARDS", 11, FontWeight.Normal, Color(0xFF64748B))
                    } else {
                        handCards.forEach { card ->
                            Box(
                                modifier = Modifier
                                    .width(28.r)
                                    .height(34.r)
                                    .clip(RoundedCornerShape(4.r))
                                    .background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    RText(card.rank, 10, FontWeight.ExtraBold, card.color)
                                    RText(card.suit, 10, FontWeight.Bold, card.color)
                                }
                            }
                        }
                    }
                }

                IconButton(onClick = { showMenu = true }) {
                    GlyphIcon(Glyph.Grid, Color.White, Modifier.size(20.r))
                }
            }

            // Pool Table Area
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left Power Meter
                Column(
                    modifier = Modifier
                        .width(36.r)
                        .fillMaxHeight()
                        .padding(vertical = 14.r),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(10.r)
                            .weight(1f)
                            .clip(RoundedCornerShape(5.r))
                            .background(Color(0xFF1E293B))
                            .border(1.r, Color(0xFF334155), RoundedCornerShape(5.r))
                            .pointerInput(shotsLeft > 0 && !isGameOver) {
                                detectDragGestures(
                                    onDrag = { change, _ ->
                                        shootPower = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    },
                                    onDragEnd = {
                                        if (shootPower > 0.05f && shotsLeft > 0) {
                                            val cue = balls.firstOrNull { it.id == 0 }
                                            if (cue != null) {
                                                val p = shootPower * 28f
                                                cue.vx = cos(aimAngle) * p
                                                cue.vy = sin(aimAngle) * p
                                                shootPower = 0f
                                                shotsLeft -= 1
                                                if (shotsLeft == 0) {
                                                    finishGame()
                                                }
                                            }
                                        }
                                    },
                                )
                            },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(shootPower)
                                .background(Color(0xFF8B5CF6)),
                        )
                    }
                    Spacer(Modifier.height(4.r))
                    RText("PWR", 8, FontWeight.Bold, Color(0xFF94A3B8))
                }

                // Canvas Table
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(2.05f),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(shotsLeft > 0 && !isGameOver) {
                                detectDragGestures(
                                    onDrag = { change, _ ->
                                        val cue = balls.firstOrNull { it.id == 0 }
                                        if (cue != null) {
                                            aimAngle = atan2(change.position.y - cue.y, change.position.x - cue.x)
                                        }
                                    },
                                )
                            },
                    ) {
                        val tableW = size.width
                        val tableH = size.height

                        if (balls.isEmpty()) {
                            setupTable(tableW, tableH)
                        }

                        // Wooden Border
                        val rail = tableH * 0.08f
                        drawRoundRect(
                            color = Color(0xFF26190F),
                            size = Size(tableW, tableH),
                            cornerRadius = CornerRadius(16f),
                        )

                        // Felt Surface
                        val feltW = tableW - rail * 2
                        val feltH = tableH - rail * 2
                        drawRoundRect(
                            brush = Brush.linearGradient(tableStyle.clothGradient),
                            topLeft = Offset(rail, rail),
                            size = Size(feltW, feltH),
                            cornerRadius = CornerRadius(8f),
                        )

                        // 6 Pockets
                        val pocketR = tableH * 0.072f
                        val pockets = listOf(
                            Offset(rail, rail),
                            Offset(tableW / 2f, rail * 0.8f),
                            Offset(tableW - rail, rail),
                            Offset(rail, tableH - rail),
                            Offset(tableW / 2f, tableH - rail * 0.8f),
                            Offset(tableW - rail, tableH - rail),
                        )

                        pockets.forEach { p ->
                            drawCircle(Color(0xFF0F172A), pocketR, p)
                            drawCircle(Color(0xFF475569), pocketR, p, style = Stroke(2f))
                        }

                        // Aim Guideline
                        val cue = balls.firstOrNull { it.id == 0 }
                        if (cue != null && shotsLeft > 0 && !cue.isPocketed) {
                            val guideLen = 220f
                            val guideEnd = Offset(cue.x + cos(aimAngle) * guideLen, cue.y + sin(aimAngle) * guideLen)
                            drawLine(
                                color = Color.White.copy(alpha = 0.8f),
                                start = Offset(cue.x, cue.y),
                                end = guideEnd,
                                strokeWidth = 2.5f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                            )
                            val stickLen = 130f
                            val sStart = Offset(cue.x - cos(aimAngle) * (cue.x * 0.04f + 8f), cue.y - sin(aimAngle) * (cue.x * 0.04f + 8f))
                            val sEnd = Offset(cue.x - cos(aimAngle) * stickLen, cue.y - sin(aimAngle) * stickLen)
                            drawLine(Color(0xFFC084FC), sStart, sEnd, strokeWidth = 5f)
                        }

                        // Physics
                        val friction = 0.985f
                        val ballR = tableH * 0.042f

                        for (b in balls) {
                            if (b.isPocketed) continue
                            b.x += b.vx
                            b.y += b.vy
                            b.vx *= friction
                            b.vy *= friction

                            if (sqrt(b.vx * b.vx + b.vy * b.vy) < 0.05f) { b.vx = 0f; b.vy = 0f }

                            // Rails
                            val minX = rail + ballR
                            val maxX = tableW - rail - ballR
                            val minY = rail + ballR
                            val maxY = tableH - rail - ballR

                            if (b.x < minX) { b.x = minX; b.vx = -b.vx * 0.85f }
                            if (b.x > maxX) { b.x = maxX; b.vx = -b.vx * 0.85f }
                            if (b.y < minY) { b.y = minY; b.vy = -b.vy * 0.85f }
                            if (b.y > maxY) { b.y = maxY; b.vy = -b.vy * 0.85f }

                            // Pockets
                            pockets.forEach { pkt ->
                                val dist = sqrt((b.x - pkt.x) * (b.x - pkt.x) + (b.y - pkt.y) * (b.y - pkt.y))
                                if (dist < pocketR * 1.1f) {
                                    b.isPocketed = true
                                    b.vx = 0f
                                    b.vy = 0f
                                    if (b.id == 0) {
                                        b.x = tableW * 0.25f
                                        b.y = tableH * 0.5f
                                        b.isPocketed = false
                                    } else {
                                        handCards.add(b.card)
                                        if (handCards.size >= 5) {
                                            finishGame()
                                        }
                                    }
                                }
                            }
                        }

                        // Ball to Ball Collisions
                        for (i in balls.indices) {
                            val b1 = balls[i]
                            if (b1.isPocketed) continue
                            for (j in i + 1 until balls.size) {
                                val b2 = balls[j]
                                if (b2.isPocketed) continue
                                val dx = b2.x - b1.x
                                val dy = b2.y - b1.y
                                val dist = sqrt(dx * dx + dy * dy)
                                val minDist = ballR * 2
                                if (dist < minDist && dist > 0.001f) {
                                    val nx = dx / dist
                                    val ny = dy / dist
                                    val overlap = minDist - dist
                                    b1.x -= nx * overlap * 0.5f
                                    b1.y -= ny * overlap * 0.5f
                                    b2.x += nx * overlap * 0.5f
                                    b2.y += ny * overlap * 0.5f

                                    val kx = b1.vx - b2.vx
                                    val ky = b1.vy - b2.vy
                                    val p = 2f * (nx * kx + ny * ky) / 2f
                                    b1.vx -= p * nx * 0.92f
                                    b1.vy -= p * ny * 0.92f
                                    b2.vx += p * nx * 0.92f
                                    b2.vy += p * ny * 0.92f
                                }
                            }
                        }

                        // Draw Balls
                        balls.forEach { b ->
                            if (!b.isPocketed) {
                                val col = if (b.id == 0) Color.White else Color(0xFFFEF3C7)
                                drawCircle(col, ballR, Offset(b.x, b.y))
                                drawCircle(Color.Black.copy(alpha = 0.25f), ballR, Offset(b.x, b.y), style = Stroke(1.5f))
                            }
                        }
                    }

                    // Game Over Result Overlay
                    if (isGameOver) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(20.r),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                RText(handResult, 26, FontWeight.ExtraBold, Color(0xFFFBBF24))
                                Spacer(Modifier.height(8.r))
                                RText("YOU WON +$wonCoins COINS!", 18, FontWeight.Bold, Color(0xFF34D399))
                                Spacer(Modifier.height(18.r))
                                Button(
                                    onClick = { setupTable(800f, 400f) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                    shape = RoundedCornerShape(12.r),
                                ) {
                                    RText("PLAY AGAIN 🔄", 15, FontWeight.Bold, Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
