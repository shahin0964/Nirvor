package com.nirvor.app.ui.games.duelx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun DuelXLudoGame(
    profile: DuelXProfile,
    onBack: () -> Unit,
) {
    var diceValue by remember { mutableIntStateOf(1) }
    var isRolling by remember { mutableStateOf(false) }
    var isPlayerTurn by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }
    var playerTokensHome by remember { mutableIntStateOf(0) }
    var botTokensHome by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf("YOUR TURN • ROLL THE DICE") }

    fun rollDice() {
        if (isRolling) return
        isRolling = true
        statusText = if (isPlayerTurn) "ROLLING..." else "BOT IS ROLLING..."
    }

    LaunchedEffect(isRolling) {
        if (isRolling) {
            for (i in 0 until 8) {
                diceValue = Random.nextInt(1, 7)
                delay(60)
            }
            isRolling = false
            if (isPlayerTurn) {
                if (diceValue == 6) {
                    playerTokensHome = (playerTokensHome + 1).coerceAtMost(4)
                    statusText = "YOU ROLLED 6! TOKEN ADVANCED! 🎉"
                } else {
                    statusText = "YOU ROLLED $diceValue"
                }
                delay(1200)
                isPlayerTurn = false
            } else {
                if (diceValue == 6) {
                    botTokensHome = (botTokensHome + 1).coerceAtMost(4)
                    statusText = "BOT ROLLED 6! BOT ADVANCED!"
                } else {
                    statusText = "BOT ROLLED $diceValue"
                }
                delay(1200)
                isPlayerTurn = true
                statusText = "YOUR TURN • ROLL THE DICE"
            }
        }
    }

    // Bot auto-roll
    LaunchedEffect(isPlayerTurn) {
        if (!isPlayerTurn && !isRolling) {
            delay(800)
            rollDice()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
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
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.r))
                            .background(Color(0xFF10B981).copy(alpha = 0.2f))
                            .padding(horizontal = 12.r, vertical = 4.r),
                    ) {
                        RText(statusText, 12, FontWeight.Bold, Color(0xFF34D399))
                    }
                    Spacer(Modifier.width(16.r))

                    RText("LUDO BOT", 13, FontWeight.Bold, Color.White)
                    Spacer(Modifier.width(8.r))
                    Box(
                        modifier = Modifier
                            .size(32.r)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.5.r, if (!isPlayerTurn) Color(0xFF10B981) else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        RText("🤖", 18, FontWeight.Bold, Color.White)
                    }
                }

                RText("LUDO", 14, FontWeight.ExtraBold, Color(0xFFFBBF24))
            }

            // Board and Controls
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(10.r),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                // Ludo Board Canvas
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.r))
                        .background(Color.White)
                        .border(2.r, Color(0xFF334155), RoundedCornerShape(14.r)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(6.r)) {
                        val s = size.minDimension
                        val quadSize = s * 0.4f

                        // 4 colored bases
                        drawRoundRect(Color(0xFFEF4444), Offset(0f, 0f), Size(quadSize, quadSize), CornerRadius(8f))
                        drawRoundRect(Color(0xFF10B981), Offset(s - quadSize, 0f), Size(quadSize, quadSize), CornerRadius(8f))
                        drawRoundRect(Color(0xFF3B82F6), Offset(0f, s - quadSize), Size(quadSize, quadSize), CornerRadius(8f))
                        drawRoundRect(Color(0xFFF59E0B), Offset(s - quadSize, s - quadSize), Size(quadSize, quadSize), CornerRadius(8f))

                        // Inner circles in bases
                        drawCircle(Color.White, quadSize * 0.28f, Offset(quadSize / 2f, quadSize / 2f))
                        drawCircle(Color.White, quadSize * 0.28f, Offset(s - quadSize / 2f, quadSize / 2f))
                        drawCircle(Color.White, quadSize * 0.28f, Offset(quadSize / 2f, s - quadSize / 2f))
                        drawCircle(Color.White, quadSize * 0.28f, Offset(s - quadSize / 2f, s - quadSize / 2f))

                        // Center home
                        drawRect(Color(0xFFE2E8F0), Offset(quadSize, quadSize), Size(s - quadSize * 2, s - quadSize * 2))
                    }

                    RText("🎲 LUDO", 20, FontWeight.ExtraBold, Color(0xFF0F172A))
                }

                Spacer(Modifier.width(24.r))

                // Dice Roll Area
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    RText(if (isPlayerTurn) "YOUR TURN" else "BOT'S TURN", 14, FontWeight.Bold, if (isPlayerTurn) Color(0xFF34D399) else Color(0xFF64748B))
                    Spacer(Modifier.height(14.r))

                    // 3D Dice Box
                    Box(
                        modifier = Modifier
                            .size(72.r)
                            .clip(RoundedCornerShape(16.r))
                            .background(Color.White)
                            .border(2.r, Color(0xFFFBBF24), RoundedCornerShape(16.r))
                            .clickable(enabled = isPlayerTurn && !isRolling) { rollDice() },
                        contentAlignment = Alignment.Center,
                    ) {
                        val diceIcon = when (diceValue) {
                            1 -> "⚀"
                            2 -> "⚁"
                            3 -> "⚂"
                            4 -> "⚃"
                            5 -> "⚄"
                            else -> "⚅"
                        }
                        RText(diceIcon, 54, FontWeight.Bold, Color(0xFF1E293B))
                    }

                    Spacer(Modifier.height(14.r))

                    Button(
                        onClick = { rollDice() },
                        enabled = isPlayerTurn && !isRolling,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.r),
                    ) {
                        RText("ROLL DICE", 14, FontWeight.Bold, Color.White)
                    }
                }
            }
        }

        // In-Game Menu
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
                        onClick = { onBack(); showMenu = false },
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
