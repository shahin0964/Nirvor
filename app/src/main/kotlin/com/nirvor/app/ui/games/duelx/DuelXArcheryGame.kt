package com.nirvor.app.ui.games.duelx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.games.GameStorage
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import kotlin.random.Random

private data class HitPoint(val x: Float, val y: Float, val pts: Int)

@Composable
fun DuelXArcheryGame(
    profile: DuelXProfile,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var arrowsLeft by remember { mutableIntStateOf(5) }
    var totalScore by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(GameStorage.getHighScore(context, "archery")) }
    var windSpeed by remember { mutableFloatStateOf(1.6f) }
    var windDir by remember { mutableStateOf("EAST") }
    var lastHitText by remember { mutableStateOf("PULL & RELEASE TO SHOOT") }
    var isGameOver by remember { mutableStateOf(false) }

    // Aim position (crosshair)
    var aimX by remember { mutableFloatStateOf(0.5f) }
    var aimY by remember { mutableFloatStateOf(0.5f) }
    var isAiming by remember { mutableStateOf(false) }

    val hits = remember { mutableStateListOf<HitPoint>() }

    fun shootArrow() {
        if (arrowsLeft <= 0 || isGameOver) return
        arrowsLeft -= 1

        // Wind effect calculation
        val windDrift = (windSpeed * 0.012f) * if (windDir == "EAST") 1f else -1f
        val hitX = (aimX + windDrift + (Random.nextFloat() * 0.02f - 0.01f)).coerceIn(0.1f, 0.9f)
        val hitY = (aimY + (Random.nextFloat() * 0.02f - 0.01f)).coerceIn(0.1f, 0.9f)

        // Calculate distance from center (0.5, 0.5)
        val dx = hitX - 0.5f
        val dy = hitY - 0.5f
        val dist = sqrt(dx * dx + dy * dy)

        val pts = when {
            dist < 0.045f -> 100 // Bullseye
            dist < 0.10f -> 80
            dist < 0.17f -> 60
            dist < 0.25f -> 40
            dist < 0.35f -> 20
            else -> 0
        }

        hits.add(HitPoint(hitX, hitY, pts))
        totalScore += pts
        lastHitText = when (pts) {
            100 -> "🎯 BULLSEYE! +100"
            80 -> "🌟 GREAT SHOT! +80"
            60 -> "✨ GOOD! +60"
            40 -> "ARROW HIT +40"
            20 -> "OUTER RING +20"
            else -> "MISSED!"
        }

        // Change wind
        windSpeed = Random.nextFloat() * 2.8f + 0.5f
        windDir = if (Random.nextBoolean()) "EAST" else "WEST"

        if (totalScore > highScore) {
            highScore = totalScore
            GameStorage.saveHighScore(context, "archery", totalScore)
        }

        if (arrowsLeft == 0) {
            isGameOver = true
        }
    }

    fun restart() {
        arrowsLeft = 5
        totalScore = 0
        hits.clear()
        isGameOver = false
        lastHitText = "PULL & RELEASE TO SHOOT"
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
                    RText("ARCHERY MASTER", 18, FontWeight.ExtraBold, Color(0xFFF59E0B))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.r), verticalAlignment = Alignment.CenterVertically) {
                    RText("ARROWS: " + "🏹".repeat(arrowsLeft), 12, FontWeight.Bold, Color.White)
                    RText("SCORE: $totalScore", 15, FontWeight.Bold, Color(0xFF34D399))
                    RText("💨 ${String.format("%.1f", windSpeed)} m/s $windDir", 12, FontWeight.Bold, Color(0xFF38BDF8))
                }
            }

            // Target Canvas
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
                        .background(Color(0xFF064E3B))
                        .pointerInput(arrowsLeft > 0 && !isGameOver) {
                            detectDragGestures(
                                onDragStart = { isAiming = true },
                                onDrag = { change, _ ->
                                    aimX = (change.position.x / size.width).coerceIn(0.15f, 0.85f)
                                    aimY = (change.position.y / size.height).coerceIn(0.15f, 0.85f)
                                },
                                onDragEnd = {
                                    isAiming = false
                                    shootArrow()
                                },
                            )
                        },
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val maxR = size.height * 0.42f

                    // Target rings
                    drawCircle(Color.White, maxR, Offset(cx, cy))
                    drawCircle(Color.Black, maxR, Offset(cx, cy), style = Stroke(2f))

                    drawCircle(Color(0xFF0284C7), maxR * 0.72f, Offset(cx, cy))
                    drawCircle(Color.Black, maxR * 0.72f, Offset(cx, cy), style = Stroke(2f))

                    drawCircle(Color(0xFFDC2626), maxR * 0.48f, Offset(cx, cy))
                    drawCircle(Color.Black, maxR * 0.48f, Offset(cx, cy), style = Stroke(2f))

                    drawCircle(Color(0xFFFBBF24), maxR * 0.28f, Offset(cx, cy))
                    drawCircle(Color.Black, maxR * 0.28f, Offset(cx, cy), style = Stroke(2f))

                    drawCircle(Color(0xFFEA580C), maxR * 0.12f, Offset(cx, cy))

                    // Draw Hit Arrows
                    hits.forEach { hit ->
                        val hx = hit.x * size.width
                        val hy = hit.y * size.height
                        drawCircle(Color(0xFF1E293B), 6f, Offset(hx, hy))
                        drawCircle(Color(0xFFDC2626), 4f, Offset(hx, hy))
                    }

                    // Aiming Crosshair
                    if (isAiming && arrowsLeft > 0) {
                        val ax = aimX * size.width
                        val ay = aimY * size.height
                        val chLen = 24f
                        drawLine(Color.White, Offset(ax - chLen, ay), Offset(ax + chLen, ay), strokeWidth = 2.5f)
                        drawLine(Color.White, Offset(ax, ay - chLen), Offset(ax, ay + chLen), strokeWidth = 2.5f)
                        drawCircle(Color.White, 16f, Offset(ax, ay), style = Stroke(2f))
                    }
                }

                // Status Toast
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.r)
                        .clip(RoundedCornerShape(8.r))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 16.r, vertical = 6.r),
                ) {
                    RText(lastHitText, 14, FontWeight.Bold, Color(0xFFFBBF24))
                }

                // Game Over Overlay
                AnimatedVisibility(visible = isGameOver, enter = fadeIn() + scaleIn()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.82f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("ROUND FINISHED! 🏹", 24, FontWeight.ExtraBold, Color.White)
                            Spacer(Modifier.height(8.r))
                            RText("TOTAL SCORE: $totalScore / 500", 18, FontWeight.Bold, Color(0xFFFBBF24))
                            Spacer(Modifier.height(18.r))
                            Button(
                                onClick = { restart() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                                shape = RoundedCornerShape(12.r),
                            ) {
                                RText("SHOOT AGAIN 🔄", 15, FontWeight.Bold, Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}
