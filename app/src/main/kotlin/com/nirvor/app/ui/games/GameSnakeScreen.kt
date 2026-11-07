package com.nirvor.app.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

private data class SnakePoint(val x: Int, val y: Int)

private enum class SnakeDirection { Up, Down, Left, Right }

@Composable
fun GameSnakeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val gridWidth = 16
    val gridHeight = 20

    var snake by remember {
        mutableStateOf(
            listOf(
                SnakePoint(8, 10),
                SnakePoint(8, 11),
                SnakePoint(8, 12),
            )
        )
    }
    var direction by remember { mutableStateOf(SnakeDirection.Up) }
    var nextDirection by remember { mutableStateOf(SnakeDirection.Up) }
    var food by remember { mutableStateOf(SnakePoint(8, 5)) }
    var bonusFood by remember { mutableStateOf<SnakePoint?>(null) }
    var foodEatenCount by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(GameStorage.getHighScore(context, "snake")) }
    var isPlaying by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }
    var speedMs by remember { mutableLongStateOf(150L) } // normal

    fun spawnFood(currentSnake: List<SnakePoint>): SnakePoint {
        val occupied = currentSnake.toSet()
        val emptyCells = mutableListOf<SnakePoint>()
        for (x in 0 until gridWidth) {
            for (y in 0 until gridHeight) {
                val pt = SnakePoint(x, y)
                if (pt !in occupied) emptyCells.add(pt)
            }
        }
        return if (emptyCells.isNotEmpty()) emptyCells[Random.nextInt(emptyCells.size)] else SnakePoint(0, 0)
    }

    fun restartGame() {
        val initSnake = listOf(SnakePoint(8, 10), SnakePoint(8, 11), SnakePoint(8, 12))
        snake = initSnake
        direction = SnakeDirection.Up
        nextDirection = SnakeDirection.Up
        food = spawnFood(initSnake)
        bonusFood = null
        foodEatenCount = 0
        score = 0
        isGameOver = false
        isPlaying = true
    }

    fun changeDirection(newDir: SnakeDirection) {
        val isOpposite = when (direction) {
            SnakeDirection.Up -> newDir == SnakeDirection.Down
            SnakeDirection.Down -> newDir == SnakeDirection.Up
            SnakeDirection.Left -> newDir == SnakeDirection.Right
            SnakeDirection.Right -> newDir == SnakeDirection.Left
        }
        if (!isOpposite) {
            nextDirection = newDir
            if (!isPlaying && !isGameOver) isPlaying = true
        }
    }

    // Game loop
    LaunchedEffect(isPlaying, isGameOver, speedMs) {
        while (isPlaying && !isGameOver) {
            delay(speedMs)
            direction = nextDirection
            val head = snake.first()
            val newHead = when (direction) {
                SnakeDirection.Up -> SnakePoint(head.x, head.y - 1)
                SnakeDirection.Down -> SnakePoint(head.x, head.y + 1)
                SnakeDirection.Left -> SnakePoint(head.x - 1, head.y)
                SnakeDirection.Right -> SnakePoint(head.x + 1, head.y)
            }

            // Wall collision
            if (newHead.x !in 0 until gridWidth || newHead.y !in 0 until gridHeight) {
                isGameOver = true
                isPlaying = false
                break
            }

            // Self collision (ignore tail end since it will move, unless we eat food)
            val bodyToCheck = snake.dropLast(1)
            if (newHead in bodyToCheck) {
                isGameOver = true
                isPlaying = false
                break
            }

            val ateNormal = newHead == food
            val ateBonus = bonusFood != null && newHead == bonusFood

            val newSnake = mutableListOf(newHead)
            newSnake.addAll(snake)
            if (!ateNormal && !ateBonus) {
                newSnake.removeAt(newSnake.size - 1)
            }

            snake = newSnake

            if (ateNormal) {
                val newScore = score + 10
                score = newScore
                foodEatenCount += 1
                food = spawnFood(newSnake)
                if (foodEatenCount % 5 == 0) {
                    bonusFood = spawnFood(newSnake)
                }
                if (newScore > highScore) {
                    highScore = newScore
                    GameStorage.saveHighScore(context, "snake", newScore)
                }
            } else if (ateBonus) {
                val newScore = score + 50
                score = newScore
                bonusFood = null
                if (newScore > highScore) {
                    highScore = newScore
                    GameStorage.saveHighScore(context, "snake", newScore)
                }
            }
        }
    }

    var dragX = 0f
    var dragY = 0f

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF1F7F4)) {
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
                        RText("Retro Snake", 24, FontWeight.Bold, Color(0xFF065F46))
                        RText("ক্লাসিক সাপ খেলা", 15, FontWeight.Normal, C.Hint)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.r)) {
                    SnakeScoreCard("স্কোর", score)
                    SnakeScoreCard("সেরা", highScore)
                }
            }

            Spacer(Modifier.height(14.r))

            // Speed Selector & Play/Pause
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.r)) {
                    SpeedChip("সহজ", speedMs == 200L) { speedMs = 200L }
                    SpeedChip("মাঝারি", speedMs == 150L) { speedMs = 150L }
                    SpeedChip("দ্রুত", speedMs == 90L) { speedMs = 90L }
                }

                Button(
                    onClick = {
                        if (isGameOver) {
                            restartGame()
                        } else {
                            isPlaying = !isPlaying
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    shape = RoundedCornerShape(12.r),
                ) {
                    RText(
                        when {
                            isGameOver -> "নতুন গেম"
                            isPlaying -> "বিরতি"
                            else -> "শুরু করুন"
                        },
                        14,
                        FontWeight.SemiBold,
                        Color.White,
                    )
                }
            }

            Spacer(Modifier.height(14.r))

            // Snake Grid Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(gridWidth.toFloat() / gridHeight.toFloat())
                    .clip(RoundedCornerShape(16.r))
                    .background(Color(0xFF1E293B))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragX = 0f; dragY = 0f },
                            onDrag = { _, delta -> dragX += delta.x; dragY += delta.y },
                            onDragEnd = {
                                val minSwipe = 30f
                                if (abs(dragX) > abs(dragY)) {
                                    if (abs(dragX) > minSwipe) {
                                        if (dragX > 0) changeDirection(SnakeDirection.Right) else changeDirection(SnakeDirection.Left)
                                    }
                                } else {
                                    if (abs(dragY) > minSwipe) {
                                        if (dragY > 0) changeDirection(SnakeDirection.Down) else changeDirection(SnakeDirection.Up)
                                    }
                                }
                            },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(6.r)) {
                    val cellW = size.width / gridWidth
                    val cellH = size.height / gridHeight

                    // Draw subtle grid lines
                    for (x in 0..gridWidth) {
                        drawLine(
                            color = Color(0xFF334155).copy(alpha = 0.25f),
                            start = Offset(x * cellW, 0f),
                            end = Offset(x * cellW, size.height),
                            strokeWidth = 1f,
                        )
                    }
                    for (y in 0..gridHeight) {
                        drawLine(
                            color = Color(0xFF334155).copy(alpha = 0.25f),
                            start = Offset(0f, y * cellH),
                            end = Offset(size.width, y * cellH),
                            strokeWidth = 1f,
                        )
                    }

                    // Draw Apple (Food)
                    drawCircle(
                        color = Color(0xFFEF4444),
                        radius = (cellW / 2.2f),
                        center = Offset(food.x * cellW + cellW / 2f, food.y * cellH + cellH / 2f),
                    )

                    // Draw Bonus Star (if any)
                    bonusFood?.let { bonus ->
                        drawCircle(
                            color = Color(0xFFFBBF24),
                            radius = (cellW / 1.8f),
                            center = Offset(bonus.x * cellW + cellW / 2f, bonus.y * cellH + cellH / 2f),
                        )
                    }

                    // Draw Snake Body
                    snake.forEachIndexed { index, pt ->
                        val isHead = index == 0
                        val color = if (isHead) Color(0xFF34D399) else Color(0xFF10B981)
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(pt.x * cellW + 1.5f, pt.y * cellH + 1.5f),
                            size = Size(cellW - 3f, cellH - 3f),
                            cornerRadius = CornerRadius(if (isHead) 8f else 4f),
                        )
                    }
                }

                // Game Over Overlay
                if (isGameOver) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("খেলা শেষ! 💥", 26, FontWeight.Bold, Color.White)
                            Spacer(Modifier.height(6.r))
                            RText("আপনার স্কোর: $score", 18, FontWeight.SemiBold, Color(0xFF34D399))
                            Spacer(Modifier.height(16.r))
                            Button(
                                onClick = { restartGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.r),
                            ) {
                                RText("আবার খেলুন", 15, FontWeight.Bold, Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.r))

            // D-Pad Controls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SnakeDirBtn("⬆️") { changeDirection(SnakeDirection.Up) }
                Row(horizontalArrangement = Arrangement.spacedBy(36.r)) {
                    SnakeDirBtn("⬅️") { changeDirection(SnakeDirection.Left) }
                    SnakeDirBtn("⬇️") { changeDirection(SnakeDirection.Down) }
                    SnakeDirBtn("➡️") { changeDirection(SnakeDirection.Right) }
                }
            }

            Spacer(Modifier.height(24.r))
        }
    }
}

@Composable
private fun SnakeScoreCard(label: String, value: Int) {
    Card(
        shape = RoundedCornerShape(10.r),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF065F46)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.r, vertical = 6.r),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RText(label, 12, FontWeight.Bold, Color(0xFF6EE7B7))
            RText("$value", 18, FontWeight.Bold, Color.White)
        }
    }
}

@Composable
private fun SpeedChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.r))
            .background(if (selected) Color(0xFF059669) else Color(0xFFE2E8F0))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.r, vertical = 5.r),
        contentAlignment = Alignment.Center,
    ) {
        RText(text, 13, FontWeight.SemiBold, if (selected) Color.White else Color(0xFF475569))
    }
}

@Composable
private fun SnakeDirBtn(icon: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(54.r)
            .clip(CircleShape)
            .background(Color(0xFFCBD5E1))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        RText(icon, 20, FontWeight.Bold, Color(0xFF0F172A))
    }
}
