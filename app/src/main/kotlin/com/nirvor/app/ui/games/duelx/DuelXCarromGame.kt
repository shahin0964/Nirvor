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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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

private data class CarromPiece(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val radius: Float,
    val type: PieceType,
    var isPocketed: Boolean = false,
)

private enum class PieceType { White, Black, Queen, Striker }

@Composable
fun DuelXCarromGame(
    profile: DuelXProfile,
    onBack: () -> Unit,
) {
    var inLobby by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }

    // Game state
    var playerScore by remember { mutableIntStateOf(0) }
    var enemyScore by remember { mutableIntStateOf(0) }
    var isPlayerTurn by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("SHAHIN BREAKS") }

    // Striker baseline slider (-1f left to +1f right)
    var strikerSliderPos by remember { mutableFloatStateOf(0f) }

    // Aim drag
    var isAiming by remember { mutableStateOf(false) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var dragCurrentOffset by remember { mutableStateOf(Offset.Zero) }

    val pieces = remember { mutableStateListOf<CarromPiece>() }

    fun setupBoard(sizePx: Float) {
        pieces.clear()
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val pieceR = sizePx * 0.026f
        val strikerR = sizePx * 0.038f

        // Queen at center
        pieces.add(CarromPiece(0, cx, cy, 0f, 0f, pieceR, PieceType.Queen))

        // Inner ring: 6 pieces (alternating White and Black)
        val r1 = pieceR * 2.05f
        for (i in 0 until 6) {
            val angle = (i * 60f) * (PI.toFloat() / 180f)
            val px = cx + cos(angle) * r1
            val py = cy + sin(angle) * r1
            val type = if (i % 2 == 0) PieceType.White else PieceType.Black
            pieces.add(CarromPiece(i + 1, px, py, 0f, 0f, pieceR, type))
        }

        // Outer ring: 12 pieces
        val r2 = pieceR * 4.05f
        for (i in 0 until 12) {
            val angle = (i * 30f + 15f) * (PI.toFloat() / 180f)
            val px = cx + cos(angle) * r2
            val py = cy + sin(angle) * r2
            val type = if (i % 2 == 0) PieceType.White else PieceType.Black
            pieces.add(CarromPiece(i + 7, px, py, 0f, 0f, pieceR, type))
        }

        // Striker at player baseline
        val baselineY = sizePx * 0.82f
        val strikerX = cx + strikerSliderPos * (sizePx * 0.28f)
        pieces.add(CarromPiece(99, strikerX, baselineY, 0f, 0f, strikerR, PieceType.Striker))
    }

    fun resetStriker(sizePx: Float, forPlayer: Boolean) {
        val cx = sizePx / 2f
        val baselineY = if (forPlayer) sizePx * 0.82f else sizePx * 0.18f
        val strikerX = cx + (if (forPlayer) strikerSliderPos else Random.nextFloat() * 0.4f - 0.2f) * (sizePx * 0.28f)
        val striker = pieces.find { it.type == PieceType.Striker }
        if (striker != null) {
            striker.x = strikerX
            striker.y = baselineY
            striker.vx = 0f
            striker.vy = 0f
            striker.isPocketed = false
        }
    }

    // Bot Shot
    LaunchedEffect(isPlayerTurn, inLobby) {
        if (!inLobby && !isPlayerTurn) {
            statusText = "ENEMY 1 IS AIMING..."
            delay(1200)
            val striker = pieces.find { it.type == PieceType.Striker }
            val target = pieces.filter { !it.isPocketed && it.type != PieceType.Striker }.shuffled().firstOrNull()
            if (striker != null && target != null) {
                val dx = target.x - striker.x
                val dy = target.y - striker.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val power = Random.nextFloat() * 14f + 18f
                striker.vx = (dx / dist) * power
                striker.vy = (dy / dist) * power
                statusText = "ENEMY 1 SHOOTS!"
            }
        }
    }

    if (inLobby) {
        // Carrom Lobby Screen (Matches video 00:33 - 00:35)
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
                        RText("CARROM", 24, FontWeight.ExtraBold, Color.White)
                        RText("1 V 1 • POCKET YOUR NINE PIECES, COVER THE QUEEN", 12, FontWeight.Bold, Color(0xFF94A3B8))
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

                Spacer(Modifier.height(16.r))

                // Customization Selectors
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(16.r))
                        .background(Color(0xFF131D31))
                        .border(1.r, Color(0xFF1E293B), RoundedCornerShape(16.r))
                        .padding(16.r),
                ) {
                    RText("YOUR STRIKER: CLASSIC", 12, FontWeight.Bold, Color(0xFF38BDF8))
                    Spacer(Modifier.height(8.r))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                        listOf(Color(0xFFEF4444), Color(0xFF06B6D4), Color(0xFFF59E0B), Color(0xFF8B5CF6), Color.White).forEachIndexed { i, col ->
                            Box(
                                modifier = Modifier
                                    .size(36.r)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(2.r, if (i == 0) Color(0xFF10B981) else Color.Transparent, CircleShape),
                            )
                        }
                    }

                    Spacer(Modifier.height(14.r))

                    RText("YOUR PIECES: CLASSIC", 12, FontWeight.Bold, Color(0xFF38BDF8))
                    Spacer(Modifier.height(8.r))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                        listOf(
                            Triple(Color.White, Color.Black, Color.Red),
                            Triple(Color(0xFF38BDF8), Color(0xFF64748B), Color(0xFFF43F5E)),
                            Triple(Color(0xFFFDE047), Color(0xFF334155), Color(0xFFA855F7)),
                        ).forEachIndexed { i, (w, b, q) ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.r))
                                    .background(Color(0xFF1E293B))
                                    .border(1.5.r, if (i == 0) Color(0xFF10B981) else Color.Transparent, RoundedCornerShape(20.r))
                                    .padding(horizontal = 8.r, vertical = 4.r),
                                horizontalArrangement = Arrangement.spacedBy(4.r),
                            ) {
                                Box(Modifier.size(16.r).background(w, CircleShape))
                                Box(Modifier.size(16.r).background(b, CircleShape))
                                Box(Modifier.size(16.r).background(q, CircleShape))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.r))

                // Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(16.r)) {
                    Button(
                        onClick = { inLobby = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(14.r),
                        modifier = Modifier.width(170.r).height(48.r),
                    ) {
                        RText("PRACTICE (FREE)", 15, FontWeight.Bold, Color.White)
                    }

                    Button(
                        onClick = { inLobby = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        shape = RoundedCornerShape(14.r),
                        modifier = Modifier.width(170.r).height(48.r),
                    ) {
                        RText("PLAY FOR COINS", 15, FontWeight.Bold, Color.Black)
                    }
                }
            }
        }
    } else {
        // Active In-Game Screen (Matches video 00:36 - 00:46)
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1E1611)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left Player Card: SHAHIN
                Column(
                    modifier = Modifier
                        .width(130.r)
                        .padding(horizontal = 8.r),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.r)
                            .clip(CircleShape)
                            .background(Color(0xFF2E2218))
                            .clickable { showMenu = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(Glyph.Dots, Color.White, Modifier.size(20.r))
                    }

                    Spacer(Modifier.height(14.r))

                    Box(
                        modifier = Modifier
                            .size(56.r)
                            .clip(RoundedCornerShape(12.r))
                            .background(Color(0xFF261D17))
                            .border(2.r, if (isPlayerTurn) Color(0xFF10B981) else Color.Transparent, RoundedCornerShape(12.r)),
                        contentAlignment = Alignment.Center,
                    ) {
                        RText("🧔🏽‍♂️", 32, FontWeight.Bold, Color.White)
                    }
                    Spacer(Modifier.height(4.r))
                    RText(profile.name, 14, FontWeight.ExtraBold, Color.White)
                    RText("$playerScore PTS", 13, FontWeight.Bold, Color(0xFFFBBF24))
                }

                // Center Carrom Board Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(isPlayerTurn) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        if (isPlayerTurn) {
                                            isAiming = true
                                            dragStartOffset = offset
                                            dragCurrentOffset = offset
                                        }
                                    },
                                    onDrag = { _, dragAmount ->
                                        if (isAiming) {
                                            dragCurrentOffset += dragAmount
                                        }
                                    },
                                    onDragEnd = {
                                        if (isAiming) {
                                            isAiming = false
                                            val striker = pieces.find { it.type == PieceType.Striker }
                                            if (striker != null) {
                                                val pullVector = dragStartOffset - dragCurrentOffset
                                                val power = (sqrt(pullVector.x * pullVector.x + pullVector.y * pullVector.y) * 0.18f).coerceIn(4f, 32f)
                                                val angle = atan2(pullVector.y, pullVector.x)
                                                striker.vx = cos(angle) * power
                                                striker.vy = sin(angle) * power
                                                statusText = "SHAHIN SHOOTS!"
                                            }
                                        }
                                    },
                                )
                            },
                    ) {
                        val boardSize = size.minDimension
                        val cornerRadius = boardSize * 0.06f

                        if (pieces.isEmpty()) {
                            setupBoard(boardSize)
                        }

                        // Outer wooden frame
                        drawRoundRect(
                            color = Color(0xFF4A2F1A),
                            size = Size(boardSize, boardSize),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
                        )

                        // Inner playing surface
                        val inset = boardSize * 0.055f
                        val playSize = boardSize - inset * 2
                        drawRoundRect(
                            color = Color(0xFFE6C594),
                            topLeft = Offset(inset, inset),
                            size = Size(playSize, playSize),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius * 0.6f),
                        )

                        // Pockets (4 corners)
                        val pocketR = boardSize * 0.048f
                        val pocketInset = inset + pocketR * 0.9f
                        val pockets = listOf(
                            Offset(pocketInset, pocketInset),
                            Offset(boardSize - pocketInset, pocketInset),
                            Offset(pocketInset, boardSize - pocketInset),
                            Offset(boardSize - pocketInset, boardSize - pocketInset),
                        )

                        pockets.forEach { p ->
                            drawCircle(Color(0xFF1A1A1A), pocketR, p)
                            drawCircle(Color(0xFF333333), pocketR, p, style = Stroke(2f))
                        }

                        // Center Circle
                        val center = Offset(boardSize / 2f, boardSize / 2f)
                        drawCircle(Color(0xFFB8860B), boardSize * 0.11f, center, style = Stroke(2.5f))
                        drawCircle(Color(0xFF8B0000), boardSize * 0.025f, center)

                        // Player Baseline
                        val baseInset = inset + boardSize * 0.08f
                        val baseBottomY = boardSize * 0.82f
                        val baseTopY = boardSize * 0.18f
                        drawLine(Color(0xFF8B4513), Offset(baseInset, baseBottomY), Offset(boardSize - baseInset, baseBottomY), strokeWidth = 2f)
                        drawLine(Color(0xFF8B4513), Offset(baseInset, baseTopY), Offset(boardSize - baseInset, baseTopY), strokeWidth = 2f)

                        // Aiming guideline
                        if (isAiming) {
                            val striker = pieces.find { it.type == PieceType.Striker }
                            if (striker != null) {
                                val pullVector = dragStartOffset - dragCurrentOffset
                                val angle = atan2(pullVector.y, pullVector.x)
                                val guideLen = 140f
                                val guideEnd = Offset(striker.x + cos(angle) * guideLen, striker.y + sin(angle) * guideLen)
                                drawLine(
                                    color = Color.White,
                                    start = Offset(striker.x, striker.y),
                                    end = guideEnd,
                                    strokeWidth = 3f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                                )
                                drawCircle(Color.White.copy(alpha = 0.4f), striker.radius, guideEnd)
                            }
                        }

                        // Physics simulation step
                        val friction = 0.978f
                        var isAnyMoving = false

                        for (i in pieces.indices) {
                            val p = pieces[i]
                            if (p.isPocketed) continue

                            p.x += p.vx
                            p.y += p.vy
                            p.vx *= friction
                            p.vy *= friction

                            if (sqrt(p.vx * p.vx + p.vy * p.vy) > 0.08f) isAnyMoving = true else { p.vx = 0f; p.vy = 0f }

                            // Wall bounce
                            if (p.x - p.radius < inset) { p.x = inset + p.radius; p.vx = -p.vx * 0.85f }
                            if (p.x + p.radius > boardSize - inset) { p.x = boardSize - inset - p.radius; p.vx = -p.vx * 0.85f }
                            if (p.y - p.radius < inset) { p.y = inset + p.radius; p.vy = -p.vy * 0.85f }
                            if (p.y + p.radius > boardSize - inset) { p.y = boardSize - inset - p.radius; p.vy = -p.vy * 0.85f }

                            // Pocket check
                            pockets.forEach { pkt ->
                                val dx = p.x - pkt.x
                                val dy = p.y - pkt.y
                                if (sqrt(dx * dx + dy * dy) < pocketR) {
                                    p.isPocketed = true
                                    p.vx = 0f
                                    p.vy = 0f
                                    if (p.type == PieceType.White) {
                                        playerScore += 10
                                        statusText = "WHITE POCKETED (+10)!"
                                    } else if (p.type == PieceType.Queen) {
                                        playerScore += 25
                                        statusText = "QUEEN COVERED (+25)!"
                                    } else if (p.type == PieceType.Black) {
                                        enemyScore += 10
                                        statusText = "BLACK POCKETED (+10)!"
                                    } else if (p.type == PieceType.Striker) {
                                        statusText = "STRIKER FOUL (-5)!"
                                        playerScore = (playerScore - 5).coerceAtLeast(0)
                                    }
                                }
                            }
                        }

                        // Piece to Piece Collisions
                        for (i in pieces.indices) {
                            val p1 = pieces[i]
                            if (p1.isPocketed) continue
                            for (j in i + 1 until pieces.size) {
                                val p2 = pieces[j]
                                if (p2.isPocketed) continue
                                val dx = p2.x - p1.x
                                val dy = p2.y - p1.y
                                val dist = sqrt(dx * dx + dy * dy)
                                val minDist = p1.radius + p2.radius
                                if (dist < minDist && dist > 0.001f) {
                                    val nx = dx / dist
                                    val ny = dy / dist
                                    val overlap = minDist - dist
                                    p1.x -= nx * overlap * 0.5f
                                    p1.y -= ny * overlap * 0.5f
                                    p2.x += nx * overlap * 0.5f
                                    p2.y += ny * overlap * 0.5f

                                    val kx = p1.vx - p2.vx
                                    val ky = p1.vy - p2.vy
                                    val p = 2f * (nx * kx + ny * ky) / 2f
                                    p1.vx -= p * nx * 0.9f
                                    p1.vy -= p * ny * 0.9f
                                    p2.vx += p * nx * 0.9f
                                    p2.vy += p * ny * 0.9f
                                }
                            }
                        }

                        // Draw Pieces
                        pieces.forEach { piece ->
                            if (!piece.isPocketed) {
                                val col = when (piece.type) {
                                    PieceType.White -> Color(0xFFF8FAFC)
                                    PieceType.Black -> Color(0xFF1E293B)
                                    PieceType.Queen -> Color(0xFFDC2626)
                                    PieceType.Striker -> Color(0xFFE11D48)
                                }
                                drawCircle(col, piece.radius, Offset(piece.x, piece.y))
                                drawCircle(Color.Black.copy(alpha = 0.35f), piece.radius, Offset(piece.x, piece.y), style = Stroke(2f))
                                if (piece.type == PieceType.Striker) {
                                    drawCircle(Color(0xFFFBBF24), piece.radius * 0.5f, Offset(piece.x, piece.y), style = Stroke(2f))
                                }
                            }
                        }
                    }
                }

                // Right Player Card: ENEMY 1
                Column(
                    modifier = Modifier
                        .width(130.r)
                        .padding(horizontal = 8.r),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.r)
                            .clip(RoundedCornerShape(12.r))
                            .background(Color(0xFF261D17))
                            .border(2.r, if (!isPlayerTurn) Color(0xFF10B981) else Color.Transparent, RoundedCornerShape(12.r)),
                        contentAlignment = Alignment.Center,
                    ) {
                        RText("🤠", 32, FontWeight.Bold, Color.White)
                    }
                    Spacer(Modifier.height(4.r))
                    RText("ENEMY 1", 14, FontWeight.ExtraBold, Color.White)
                    RText("$enemyScore PTS", 13, FontWeight.Bold, Color(0xFFFBBF24))

                    Spacer(Modifier.height(18.r))

                    // Striker baseline slider
                    if (isPlayerTurn) {
                        RText("STRIKER POSITION", 10, FontWeight.Bold, Color(0xFF94A3B8))
                        Slider(
                            value = strikerSliderPos,
                            onValueChange = { pos ->
                                strikerSliderPos = pos
                                val striker = pieces.find { it.type == PieceType.Striker }
                                if (striker != null) {
                                    val sizePx = 300f // relative scale
                                    striker.x = (sizePx / 2f) + pos * (sizePx * 0.28f)
                                }
                            },
                            valueRange = -1f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF10B981),
                                activeTrackColor = Color(0xFF10B981),
                                inactiveTrackColor = Color(0xFF334155),
                            ),
                        )
                    }
                }
            }

            // In-Game Menu Modal (Matches video 00:44 - 00:45)
            AnimatedVisibility(visible = showMenu, enter = fadeIn() + scaleIn()) {
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
                            onClick = { inLobby = true; showMenu = false },
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
