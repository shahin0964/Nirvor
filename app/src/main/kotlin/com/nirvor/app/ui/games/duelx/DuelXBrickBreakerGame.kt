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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.games.GameStorage
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class Brick(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val color: Color,
    var isDestroyed: Boolean = false,
)

@Composable
fun DuelXBrickBreakerGame(
    profile: DuelXProfile,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var score by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(GameStorage.getHighScore(context, "brick_breaker")) }
    var lives by remember { mutableIntStateOf(3) }
    var isPlaying by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }
    var isWon by remember { mutableStateOf(false) }

    // Paddle
    var paddleX by remember { mutableFloatStateOf(0.5f) } // 0f to 1f normalized

    // Ball
    var ballX by remember { mutableFloatStateOf(0.5f) }
    var ballY by remember { mutableFloatStateOf(0.85f) }
    var ballVx by remember { mutableFloatStateOf(0.006f) }
    var ballVy by remember { mutableFloatStateOf(-0.010f) }

    val bricks = remember { mutableStateListOf<Brick>() }

    fun initBricks(w: Float, h: Float) {
        bricks.clear()
        val cols = 8
        val rows = 4
        val pad = w * 0.02f
        val brickW = (w - pad * (cols + 1)) / cols
        val brickH = h * 0.05f

        val colors = listOf(Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFFBBF24), Color(0xFF10B981))

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val bx = pad + c * (brickW + pad)
                val by = h * 0.12f + r * (brickH + pad * 0.8f)
                bricks.add(Brick(bx, by, brickW, brickH, colors[r]))
            }
        }
    }

    fun restart() {
        score = 0
        lives = 3
        isGameOver = false
        isWon = false
        paddleX = 0.5f
        ballX = 0.5f
        ballY = 0.85f
        ballVx = 0.006f * (if (Random.nextBoolean()) 1 else -1)
        ballVy = -0.010f
        isPlaying = true
    }

    // 60-120 FPS game tick loop
    LaunchedEffect(isPlaying, isGameOver, isWon) {
        while (isPlaying && !isGameOver && !isWon) {
            delay(16) // ~60-120hz smooth frame
            ballX += ballVx
            ballY += ballVy

            // Wall bounce
            if (ballX <= 0.02f) { ballX = 0.02f; ballVx = -ballVx }
            if (ballX >= 0.98f) { ballX = 0.98f; ballVx = -ballVx }
            if (ballY <= 0.02f) { ballY = 0.02f; ballVy = -ballVy }

            // Paddle bounce
            val paddleW = 0.22f
            val paddleY = 0.90f
            if (ballY >= paddleY - 0.02f && ballY <= paddleY + 0.02f) {
                if (ballX >= paddleX - paddleW / 2f && ballX <= paddleX + paddleW / 2f) {
                    ballVy = -kotlin.math.abs(ballVy)
                    val offset = (ballX - paddleX) / (paddleW / 2f)
                    ballVx = offset * 0.012f
                }
            }

            // Bottom out (lose life)
            if (ballY > 0.98f) {
                lives -= 1
                if (lives <= 0) {
                    isGameOver = true
                    isPlaying = false
                } else {
                    ballX = paddleX
                    ballY = 0.85f
                    ballVy = -0.010f
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF070B14))
                    .padding(horizontal = 16.r, vertical = 8.r),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        GlyphIcon(Glyph.ArrowLeft, Color.White, Modifier.size(22.r))
                    }
                    Spacer(Modifier.width(6.r))
                    RText("BRICK BREAKER", 18, FontWeight.ExtraBold, Color(0xFF38BDF8))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.r), verticalAlignment = Alignment.CenterVertically) {
                    RText("LIVES: " + "❤️".repeat(lives), 13, FontWeight.Bold, Color.White)
                    RText("SCORE: $score", 15, FontWeight.Bold, Color(0xFFFBBF24))
                    RText("BEST: $highScore", 13, FontWeight.Bold, Color(0xFF34D399))
                }
            }

            // Canvas Game Arena
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.r),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.r))
                        .background(Color(0xFF020617))
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                paddleX = (change.position.x / size.width).coerceIn(0.12f, 0.88f)
                            }
                        },
                ) {
                    val w = size.width
                    val h = size.height

                    if (bricks.isEmpty()) {
                        initBricks(w, h)
                        isPlaying = true
                    }

                    // Draw Bricks
                    bricks.forEach { b ->
                        if (!b.isDestroyed) {
                            drawRoundRect(
                                color = b.color,
                                topLeft = Offset(b.x, b.y),
                                size = Size(b.w, b.h),
                                cornerRadius = CornerRadius(4f),
                            )

                            // Check ball collision with brick
                            val bxPx = ballX * w
                            val byPx = ballY * h
                            if (bxPx >= b.x && bxPx <= b.x + b.w && byPx >= b.y && byPx <= b.y + b.h) {
                                b.isDestroyed = true
                                ballVy = -ballVy
                                score += 20
                                if (score > highScore) {
                                    highScore = score
                                    GameStorage.saveHighScore(context, "brick_breaker", score)
                                }
                                if (bricks.all { it.isDestroyed }) {
                                    isWon = true
                                    isPlaying = false
                                }
                            }
                        }
                    }

                    // Draw Paddle
                    val pwPx = w * 0.22f
                    val phPx = h * 0.024f
                    val pxPx = paddleX * w - pwPx / 2f
                    val pyPx = h * 0.90f
                    drawRoundRect(
                        color = Color(0xFF38BDF8),
                        topLeft = Offset(pxPx, pyPx),
                        size = Size(pwPx, phPx),
                        cornerRadius = CornerRadius(6f),
                    )

                    // Draw Ball
                    val ballRPx = w * 0.015f
                    drawCircle(Color(0xFFFBBF24), ballRPx, Offset(ballX * w, ballY * h))
                }

                // Game Over Overlay
                AnimatedVisibility(visible = isGameOver || isWon, enter = fadeIn() + scaleIn()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText(if (isWon) "YOU WIN! 🏆" else "GAME OVER 💥", 26, FontWeight.ExtraBold, if (isWon) Color(0xFF10B981) else Color(0xFFEF4444))
                            Spacer(Modifier.height(8.r))
                            RText("FINAL SCORE: $score", 18, FontWeight.Bold, Color.White)
                            Spacer(Modifier.height(18.r))
                            Button(
                                onClick = {
                                    initBricks(800f, 400f)
                                    restart()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
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
