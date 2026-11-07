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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private data class PoolBall(
    val number: Int,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val color: Color,
    val isStripe: Boolean = false,
    var isPocketed: Boolean = false,
)

private enum class BilliardState { Lobby, Matchmaking, Playing }

@Composable
fun DuelXBilliardGame(
    profile: DuelXProfile,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf(BilliardState.Lobby) }
    var selectedPrize by remember { mutableIntStateOf(40) }
    var showMenu by remember { mutableStateOf(false) }

    // Matchmaking opponent
    var opponentFound by remember { mutableStateOf(false) }
    val opponentName = "KINGAYAN"

    // Game state
    var isPlayerTurn by remember { mutableStateOf(false) } // in video KingAyan breaks
    var statusText by remember { mutableStateOf("8-BALL: KingAyan BREAKS") }
    var aimAngle by remember { mutableFloatStateOf(0f) }
    var shootPower by remember { mutableFloatStateOf(0f) } // 0f to 1f

    val balls = remember { mutableStateListOf<PoolBall>() }

    fun setupPoolTable(w: Float, h: Float) {
        balls.clear()
        val ballR = h * 0.038f

        // Cue ball on the left side
        balls.add(PoolBall(0, w * 0.28f, h * 0.5f, 0f, 0f, Color.White))

        // 15 racked balls on the right in triangle
        val startX = w * 0.68f
        val centerY = h * 0.5f
        val dx = ballR * 1.8f
        val dy = ballR * 2.05f

        val ballColors = listOf(
            Color(0xFFFBBF24), // 1 Yellow
            Color(0xFF2563EB), // 2 Blue
            Color(0xFFDC2626), // 3 Red
            Color(0xFF7C3AED), // 4 Purple
            Color(0xFFEA580C), // 5 Orange
            Color(0xFF16A34A), // 6 Green
            Color(0xFF991B1B), // 7 Maroon
            Color(0xFF111827), // 8 Black
            Color(0xFFFBBF24), // 9 Stripe Yellow
            Color(0xFF2563EB), // 10 Stripe Blue
            Color(0xFFDC2626), // 11 Stripe Red
            Color(0xFF7C3AED), // 12 Stripe Purple
            Color(0xFFEA580C), // 13 Stripe Orange
            Color(0xFF16A34A), // 14 Stripe Green
            Color(0xFF991B1B), // 15 Stripe Maroon
        )

        var bIdx = 0
        for (col in 0 until 5) {
            val count = col + 1
            val colX = startX + col * dx
            val topY = centerY - (count - 1) * dy * 0.5f
            for (row in 0 until count) {
                val py = topY + row * dy
                val num = bIdx + 1
                val colr = ballColors.getOrElse(bIdx) { Color.Yellow }
                balls.add(PoolBall(num, colX, py, 0f, 0f, colr, isStripe = num > 8))
                bIdx++
            }
        }
    }

    // Matchmaking transition
    LaunchedEffect(state) {
        if (state == BilliardState.Matchmaking) {
            opponentFound = false
            delay(1400)
            opponentFound = true
            delay(1000)
            state = BilliardState.Playing
            // Opponent breaks automatically
            delay(800)
            val cue = balls.firstOrNull { it.number == 0 }
            if (cue != null) {
                cue.vx = 22f + Random.nextFloat() * 4f
                cue.vy = Random.nextFloat() * 4f - 2f
                statusText = "KingAyan BREAKS!"
            }
        }
    }

    // Bot Shot when it's bot turn
    LaunchedEffect(isPlayerTurn, state) {
        if (state == BilliardState.Playing && !isPlayerTurn) {
            delay(1800)
            val cue = balls.firstOrNull { it.number == 0 }
            val target = balls.filter { it.number != 0 && !it.isPocketed }.shuffled().firstOrNull()
            if (cue != null && target != null) {
                val dx = target.x - cue.x
                val dy = target.y - cue.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val power = Random.nextFloat() * 12f + 16f
                cue.vx = (dx / dist) * power
                cue.vy = (dy / dist) * power
                statusText = "KingAyan SHOOTS!"
            }
        }
    }

    when (state) {
        BilliardState.Lobby -> {
            // Table Buy-In Selection (Matches video 00:49 - 00:50)
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1120)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.r, vertical = 14.r),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = onBack) {
                            GlyphIcon(Glyph.ArrowLeft, Color.White, Modifier.size(24.r), strokeWidth = 2.2f)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("BILLIARDS 1 V 1", 24, FontWeight.ExtraBold, Color.White)
                            RText("WINNER TAKES THE PRIZE", 12, FontWeight.Bold, Color(0xFF94A3B8))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(22.r)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B)),
                                contentAlignment = Alignment.Center,
                            ) {
                                RText("9", 12, FontWeight.Bold, Color.Black)
                            }
                            Spacer(Modifier.width(6.r))
                            RText("${profile.coins}", 15, FontWeight.Bold, Color(0xFFFBBF24))
                        }
                    }

                    Spacer(Modifier.height(18.r))

                    // 5 Prize Table Cards
                    val tables = listOf(
                        Triple(40, 21, 2),
                        Triple(200, 105, 5),
                        Triple(1000, 525, 12),
                        Triple(4000, 2100, 4),
                        Triple(10000, 5250, 1),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.r),
                    ) {
                        tables.forEach { (prize, buyIn, online) ->
                            val isSelected = prize == selectedPrize
                            val bg = if (isSelected) Color(0xFF13382C) else Color(0xFF131D31)
                            val border = if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B)

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(180.r)
                                    .clip(RoundedCornerShape(16.r))
                                    .background(bg)
                                    .border(2.r, border, RoundedCornerShape(16.r))
                                    .clickable {
                                        selectedPrize = prize
                                        state = BilliardState.Matchmaking
                                    }
                                    .padding(12.r),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    RText("PRIZE", 11, FontWeight.Bold, Color(0xFF94A3B8))
                                    RText(if (prize >= 10000) "10K" else "$prize", 22, FontWeight.ExtraBold, Color.White)
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    RText("BUY IN: $buyIn", 12, FontWeight.Bold, Color(0xFF38BDF8))
                                    Spacer(Modifier.height(4.r))
                                    RText("PLAYERS ONLINE: $online", 10, FontWeight.Medium, Color(0xFF64748B))
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.r))
                                        .background(if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B))
                                        .padding(vertical = 6.r),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RText("TAP TO PLAY", 11, FontWeight.Bold, Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        BilliardState.Matchmaking -> {
            // Matchmaking Screen (Matches video 00:51 - 00:57)
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    RText(if (opponentFound) "MATCH FOUND" else "MATCHMAKING", 24, FontWeight.ExtraBold, Color.White)
                    RText("PRIZE: $selectedPrize", 14, FontWeight.Bold, Color(0xFFFBBF24))

                    Spacer(Modifier.height(30.r))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(30.r),
                    ) {
                        // User Avatar
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(90.r)
                                    .clip(RoundedCornerShape(16.r))
                                    .background(Color(0xFF1E293B))
                                    .border(2.5.r, Color(0xFF10B981), RoundedCornerShape(16.r)),
                                contentAlignment = Alignment.Center,
                            ) {
                                RText("🧔🏽‍♂️", 50, FontWeight.Bold, Color.White)
                            }
                            Spacer(Modifier.height(8.r))
                            RText(profile.name, 16, FontWeight.Bold, Color.White)
                        }

                        // VS
                        Box(
                            modifier = Modifier
                                .size(50.r)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center,
                        ) {
                            RText("VS", 18, FontWeight.ExtraBold, Color.Black)
                        }

                        // Opponent Avatar
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(90.r)
                                    .clip(RoundedCornerShape(16.r))
                                    .background(Color(0xFF1E293B))
                                    .border(2.5.r, if (opponentFound) Color(0xFF3B82F6) else Color(0xFF64748B), RoundedCornerShape(16.r)),
                                contentAlignment = Alignment.Center,
                            ) {
                                RText(if (opponentFound) "🤠" else "❓", 50, FontWeight.Bold, Color.White)
                            }
                            Spacer(Modifier.height(8.r))
                            RText(if (opponentFound) opponentName else "LOOKING...", 16, FontWeight.Bold, if (opponentFound) Color(0xFF38BDF8) else Color(0xFF64748B))
                        }
                    }
                }
            }
        }

        BilliardState.Playing -> {
            // Billiard Table Screen (Matches video 00:58 - 01:15)
            val tableStyle = DuelXTables.styles.getOrElse(profile.selectedTableIndex) { DuelXTables.styles.first() }

            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Top Bar: Scoreboard & Turn Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0B1120))
                            .padding(horizontal = 16.r, vertical = 6.r),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = { showMenu = true }) {
                            GlyphIcon(Glyph.Grid, Color.White, Modifier.size(22.r))
                        }

                        // Center Scoreboard
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.r)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .border(1.5.r, if (isPlayerTurn) Color(0xFF10B981) else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                RText("🧔🏽‍♂️", 18, FontWeight.Bold, Color.White)
                            }
                            Spacer(Modifier.width(8.r))
                            RText(profile.name, 13, FontWeight.Bold, Color.White)

                            Spacer(Modifier.width(16.r))
                            // Status message badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.r))
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                    .padding(horizontal = 12.r, vertical = 4.r),
                            ) {
                                RText(statusText, 12, FontWeight.Bold, Color(0xFF34D399))
                            }
                            Spacer(Modifier.width(16.r))

                            RText(opponentName, 13, FontWeight.Bold, Color.White)
                            Spacer(Modifier.width(8.r))
                            Box(
                                modifier = Modifier
                                    .size(32.r)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1E293B))
                                    .border(1.5.r, if (!isPlayerTurn) Color(0xFF10B981) else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                RText("🤠", 18, FontWeight.Bold, Color.White)
                            }
                        }

                        RText("8-BALL", 14, FontWeight.ExtraBold, Color(0xFFFBBF24))
                    }

                    // Table Area with Left Power Gauge
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(10.r),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left Power Gauge Slider
                        Column(
                            modifier = Modifier
                                .width(40.r)
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
                                    .pointerInput(isPlayerTurn) {
                                        detectDragGestures(
                                            onDrag = { change, _ ->
                                                if (isPlayerTurn) {
                                                    val fraction = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                                    shootPower = fraction
                                                }
                                            },
                                            onDragEnd = {
                                                if (isPlayerTurn && shootPower > 0.05f) {
                                                    val cue = balls.firstOrNull { it.number == 0 }
                                                    if (cue != null) {
                                                        val p = shootPower * 30f
                                                        cue.vx = cos(aimAngle) * p
                                                        cue.vy = sin(aimAngle) * p
                                                        shootPower = 0f
                                                        statusText = "SHAHIN SHOOTS!"
                                                        isPlayerTurn = false
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
                                        .background(Color(0xFFF59E0B)),
                                )
                            }
                            Spacer(Modifier.height(6.r))
                            RText("PWR", 9, FontWeight.Bold, Color(0xFF94A3B8))
                        }

                        // Pool Table Canvas
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(2f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(isPlayerTurn) {
                                        detectDragGestures(
                                            onDrag = { change, _ ->
                                                if (isPlayerTurn) {
                                                    val cue = balls.firstOrNull { it.number == 0 }
                                                    if (cue != null) {
                                                        aimAngle = atan2(change.position.y - cue.y, change.position.x - cue.x)
                                                    }
                                                }
                                            },
                                        )
                                    },
                            ) {
                                val tableW = size.width
                                val tableH = size.height

                                if (balls.isEmpty()) {
                                    setupPoolTable(tableW, tableH)
                                }

                                // Wooden Outer Rail
                                val railThickness = tableH * 0.08f
                                drawRoundRect(
                                    color = Color(0xFF382212),
                                    size = Size(tableW, tableH),
                                    cornerRadius = CornerRadius(16f),
                                )

                                // Felt Play Surface
                                val feltW = tableW - railThickness * 2
                                val feltH = tableH - railThickness * 2
                                drawRoundRect(
                                    brush = Brush.linearGradient(tableStyle.clothGradient),
                                    topLeft = Offset(railThickness, railThickness),
                                    size = Size(feltW, feltH),
                                    cornerRadius = CornerRadius(8f),
                                )

                                // 6 Pockets
                                val pocketR = tableH * 0.07f
                                val pockets = listOf(
                                    Offset(railThickness, railThickness), // Top-left
                                    Offset(tableW / 2f, railThickness * 0.8f), // Top-mid
                                    Offset(tableW - railThickness, railThickness), // Top-right
                                    Offset(railThickness, tableH - railThickness), // Bottom-left
                                    Offset(tableW / 2f, tableH - railThickness * 0.8f), // Bottom-mid
                                    Offset(tableW - railThickness, tableH - railThickness), // Bottom-right
                                )

                                pockets.forEach { p ->
                                    drawCircle(Color(0xFF0F172A), pocketR, p)
                                    drawCircle(Color(0xFF475569), pocketR, p, style = Stroke(2f))
                                }

                                // Cue Stick Aim Guideline
                                val cue = balls.firstOrNull { it.number == 0 }
                                if (cue != null && isPlayerTurn && !cue.isPocketed) {
                                    val guideLen = 220f
                                    val guideEnd = Offset(cue.x + cos(aimAngle) * guideLen, cue.y + sin(aimAngle) * guideLen)
                                    drawLine(
                                        color = Color.White.copy(alpha = 0.85f),
                                        start = Offset(cue.x, cue.y),
                                        end = guideEnd,
                                        strokeWidth = 2.5f,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                                    )
                                    // Cue stick behind ball
                                    val stickLen = 140f
                                    val stickStart = Offset(cue.x - cos(aimAngle) * (cue.x * 0.04f + 8f), cue.y - sin(aimAngle) * (cue.x * 0.04f + 8f))
                                    val stickEnd = Offset(cue.x - cos(aimAngle) * stickLen, cue.y - sin(aimAngle) * stickLen)
                                    drawLine(Color(0xFFD4A373), stickStart, stickEnd, strokeWidth = 5f)
                                }

                                // Physics Simulation
                                val friction = 0.985f
                                val ballRadius = tableH * 0.038f

                                for (b in balls) {
                                    if (b.isPocketed) continue
                                    b.x += b.vx
                                    b.y += b.vy
                                    b.vx *= friction
                                    b.vy *= friction

                                    if (sqrt(b.vx * b.vx + b.vy * b.vy) < 0.05f) { b.vx = 0f; b.vy = 0f }

                                    // Rail bounces
                                    val minX = railThickness + ballRadius
                                    val maxX = tableW - railThickness - ballRadius
                                    val minY = railThickness + ballRadius
                                    val maxY = tableH - railThickness - ballRadius

                                    if (b.x < minX) { b.x = minX; b.vx = -b.vx * 0.85f }
                                    if (b.x > maxX) { b.x = maxX; b.vx = -b.vx * 0.85f }
                                    if (b.y < minY) { b.y = minY; b.vy = -b.vy * 0.85f }
                                    if (b.y > maxY) { b.y = maxY; b.vy = -b.vy * 0.85f }

                                    // Pocket check
                                    pockets.forEach { pkt ->
                                        val dist = sqrt((b.x - pkt.x) * (b.x - pkt.x) + (b.y - pkt.y) * (b.y - pkt.y))
                                        if (dist < pocketR * 1.1f) {
                                            b.isPocketed = true
                                            b.vx = 0f
                                            b.vy = 0f
                                            if (b.number == 0) {
                                                // Scratch: reset cue ball
                                                b.x = tableW * 0.28f
                                                b.y = tableH * 0.5f
                                                b.isPocketed = false
                                                statusText = "CUE BALL SCRATCH!"
                                            } else {
                                                statusText = "BALL ${b.number} POCKETED!"
                                            }
                                        }
                                    }
                                }

                                // Ball to ball collision
                                for (i in balls.indices) {
                                    val b1 = balls[i]
                                    if (b1.isPocketed) continue
                                    for (j in i + 1 until balls.size) {
                                        val b2 = balls[j]
                                        if (b2.isPocketed) continue
                                        val dx = b2.x - b1.x
                                        val dy = b2.y - b1.y
                                        val dist = sqrt(dx * dx + dy * dy)
                                        val minDist = ballRadius * 2
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
                                        drawCircle(b.color, ballRadius, Offset(b.x, b.y))
                                        if (b.isStripe) {
                                            drawCircle(Color.White, ballRadius * 0.6f, Offset(b.x, b.y))
                                            drawCircle(b.color, ballRadius * 0.35f, Offset(b.x, b.y))
                                        }
                                        drawCircle(Color.Black.copy(alpha = 0.2f), ballRadius, Offset(b.x, b.y), style = Stroke(1.5f))
                                    }
                                }
                            }
                        }
                    }
                }

                // In-Game Menu
                if (showMenu) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .width(260.r)
                                .clip(RoundedCornerShape(18.r))
                                .background(Color(0xFF1E293B))
                                .border(1.5.r, Color(0xFF334155), RoundedCornerShape(18.r))
                                .padding(20.r),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.r),
                        ) {
                            RText("MENU", 20, FontWeight.ExtraBold, Color.White)
                            Spacer(Modifier.height(4.r))

                            Button(
                                onClick = { showMenu = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.r),
                                modifier = Modifier.fillMaxWidth().height(42.r),
                            ) {
                                RText("▶ RESUME", 14, FontWeight.Bold, Color.White)
                            }

                            Button(
                                onClick = { showMenu = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                shape = RoundedCornerShape(10.r),
                                modifier = Modifier.fillMaxWidth().height(42.r),
                            ) {
                                RText("🔊 SOUND: ON", 14, FontWeight.Bold, Color.White)
                            }

                            Button(
                                onClick = { state = BilliardState.Lobby; showMenu = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                shape = RoundedCornerShape(10.r),
                                modifier = Modifier.fillMaxWidth().height(42.r),
                            ) {
                                RText("🚪 QUIT MATCH", 14, FontWeight.Bold, Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
