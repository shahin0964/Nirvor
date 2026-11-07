package com.nirvor.app.ui.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun Game2048Screen(onBack: () -> Unit) {
    val context = LocalContext.current
    var board by remember { mutableStateOf(IntArray(16) { 0 }) }
    var previousBoard by remember { mutableStateOf<IntArray?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var previousScore by remember { mutableIntStateOf(0) }
    var highScore by remember { mutableIntStateOf(GameStorage.getHighScore(context, "2048")) }
    var isGameOver by remember { mutableStateOf(false) }
    var hasWon by remember { mutableStateOf(false) }
    var dismissedWin by remember { mutableStateOf(false) }

    fun spawnTile(grid: IntArray): Boolean {
        val emptyIndices = grid.indices.filter { grid[it] == 0 }
        if (emptyIndices.isEmpty()) return false
        val index = emptyIndices[Random.nextInt(emptyIndices.size)]
        grid[index] = if (Random.nextFloat() < 0.9f) 2 else 4
        return true
    }

    fun restartGame() {
        val newBoard = IntArray(16) { 0 }
        spawnTile(newBoard)
        spawnTile(newBoard)
        board = newBoard
        previousBoard = null
        score = 0
        previousScore = 0
        isGameOver = false
        hasWon = false
        dismissedWin = false
    }

    LaunchedEffect(Unit) {
        if (board.all { it == 0 }) {
            restartGame()
        }
    }

    fun canMove(grid: IntArray): Boolean {
        if (grid.any { it == 0 }) return true
        for (r in 0 until 4) {
            for (c in 0 until 4) {
                val current = grid[r * 4 + c]
                if (c < 3 && current == grid[r * 4 + c + 1]) return true
                if (r < 3 && current == grid[(r + 1) * 4 + c]) return true
            }
        }
        return false
    }

    fun slideLine(line: List<Int>): Pair<List<Int>, Int> {
        val nonZero = line.filter { it != 0 }
        val result = mutableListOf<Int>()
        var pointsGained = 0
        var i = 0
        while (i < nonZero.size) {
            if (i + 1 < nonZero.size && nonZero[i] == nonZero[i + 1]) {
                val merged = nonZero[i] * 2
                result.add(merged)
                pointsGained += merged
                i += 2
            } else {
                result.add(nonZero[i])
                i += 1
            }
        }
        while (result.size < 4) {
            result.add(0)
        }
        return Pair(result, pointsGained)
    }

    fun move(direction: MoveDirection) {
        if (isGameOver) return
        val current = board.copyOf()
        val next = IntArray(16) { 0 }
        var totalGain = 0

        when (direction) {
            MoveDirection.Left -> {
                for (r in 0 until 4) {
                    val line = listOf(current[r * 4], current[r * 4 + 1], current[r * 4 + 2], current[r * 4 + 3])
                    val (res, gain) = slideLine(line)
                    totalGain += gain
                    for (c in 0 until 4) next[r * 4 + c] = res[c]
                }
            }
            MoveDirection.Right -> {
                for (r in 0 until 4) {
                    val line = listOf(current[r * 4 + 3], current[r * 4 + 2], current[r * 4 + 1], current[r * 4])
                    val (res, gain) = slideLine(line)
                    totalGain += gain
                    for (c in 0 until 4) next[r * 4 + 3 - c] = res[c]
                }
            }
            MoveDirection.Up -> {
                for (c in 0 until 4) {
                    val line = listOf(current[c], current[4 + c], current[8 + c], current[12 + c])
                    val (res, gain) = slideLine(line)
                    totalGain += gain
                    for (r in 0 until 4) next[r * 4 + c] = res[r]
                }
            }
            MoveDirection.Down -> {
                for (c in 0 until 4) {
                    val line = listOf(current[12 + c], current[8 + c], current[4 + c], current[c])
                    val (res, gain) = slideLine(line)
                    totalGain += gain
                    for (r in 0 until 4) next[(3 - r) * 4 + c] = res[r]
                }
            }
        }

        // If the board actually changed
        if (!current.contentEquals(next)) {
            previousBoard = current
            previousScore = score
            spawnTile(next)
            board = next
            val newScore = score + totalGain
            score = newScore
            if (newScore > highScore) {
                highScore = newScore
                GameStorage.saveHighScore(context, "2048", newScore)
            }
            if (!hasWon && !dismissedWin && next.any { it == 2048 }) {
                hasWon = true
            }
            if (!canMove(next)) {
                isGameOver = true
            }
        }
    }

    fun undo() {
        previousBoard?.let { prev ->
            board = prev.copyOf()
            score = previousScore
            previousBoard = null
            isGameOver = false
        }
    }

    var totalDragX = 0f
    var totalDragY = 0f

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFFBF8F5)) {
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
                        RText("2048 Classic", 24, FontWeight.Bold, Color(0xFF776E65))
                        RText("সংখ্যা জোড়া লাগান", 15, FontWeight.Normal, C.Hint)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.r)) {
                    ScoreBadge("স্কোর", score)
                    ScoreBadge("সেরা", highScore)
                }
            }

            Spacer(Modifier.height(18.r))

            // Action Row: Restart, Undo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { restartGame() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8F7A66)),
                    shape = RoundedCornerShape(12.r),
                ) {
                    GlyphIcon(Glyph.Refresh, Color.White, Modifier.size(18.r), strokeWidth = 2f)
                    Spacer(Modifier.width(6.r))
                    RText("নতুন গেম", 15, FontWeight.SemiBold, Color.White)
                }

                Button(
                    onClick = { undo() },
                    enabled = previousBoard != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0D5C7),
                        contentColor = Color(0xFF776E65),
                        disabledContainerColor = Color(0xFFEFEBE4),
                        disabledContentColor = Color(0xFFAAAAAA),
                    ),
                    shape = RoundedCornerShape(12.r),
                ) {
                    RText("পূর্বাবস্থায় (Undo)", 14, FontWeight.SemiBold, if (previousBoard != null) Color(0xFF776E65) else Color(0xFFAAAAAA))
                }
            }

            Spacer(Modifier.height(16.r))

            // 4x4 Game Board
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.r))
                    .background(Color(0xFFBBADA0))
                    .padding(10.r)
                    .pointerInput(isGameOver) {
                        detectDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                                totalDragY = 0f
                            },
                            onDrag = { _, dragAmount ->
                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y
                            },
                            onDragEnd = {
                                val minSwipe = 40f
                                if (abs(totalDragX) > abs(totalDragY)) {
                                    if (abs(totalDragX) > minSwipe) {
                                        if (totalDragX > 0) move(MoveDirection.Right) else move(MoveDirection.Left)
                                    }
                                } else {
                                    if (abs(totalDragY) > minSwipe) {
                                        if (totalDragY > 0) move(MoveDirection.Down) else move(MoveDirection.Up)
                                    }
                                }
                            },
                        )
                    },
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
                                val value = board[row * 4 + col]
                                TileView(value, Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Win Overlay
                if (hasWon && !dismissedWin) {
                    OverlayMessage(
                        title = "অভিনন্দন! 🎉",
                        subtitle = "আপনি ২০৪৮ তৈরি করে বিজয়ী হয়েছেন!",
                        primaryText = "চালিয়ে যান",
                        secondaryText = "নতুন গেম",
                        onPrimary = { dismissedWin = true },
                        onSecondary = { restartGame() },
                    )
                }

                // Game Over Overlay
                if (isGameOver) {
                    OverlayMessage(
                        title = "খেলা শেষ! 😢",
                        subtitle = "আর কোনো সম্ভাব্য চাল নেই। মোট স্কোর: $score",
                        primaryText = "আবার খেলুন",
                        secondaryText = null,
                        onPrimary = { restartGame() },
                        onSecondary = null,
                    )
                }
            }

            Spacer(Modifier.height(18.r))

            // Directional Buttons for Easy On-Screen Control
            RText("সোয়াইপ করুন অথবা নিচের বাটন চাপুন", 14, FontWeight.Normal, C.Hint)
            Spacer(Modifier.height(8.r))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ArrowBtn("⬆️", { move(MoveDirection.Up) })
                Row(horizontalArrangement = Arrangement.spacedBy(28.r)) {
                    ArrowBtn("⬅️", { move(MoveDirection.Left) })
                    ArrowBtn("⬇️", { move(MoveDirection.Down) })
                    ArrowBtn("➡️", { move(MoveDirection.Right) })
                }
            }

            Spacer(Modifier.height(24.r))
        }
    }
}

private enum class MoveDirection { Left, Right, Up, Down }

@Composable
private fun ArrowBtn(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(54.r)
            .clip(CircleShape)
            .background(Color(0xFFEAE3D9))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        RText(label, 20, FontWeight.Bold, Color(0xFF776E65))
    }
}

@Composable
private fun ScoreBadge(label: String, value: Int) {
    Card(
        shape = RoundedCornerShape(10.r),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFBBADA0)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.r, vertical = 6.r),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RText(label, 13, FontWeight.Bold, Color(0xFFEEE4DA))
            RText("$value", 18, FontWeight.Bold, Color.White)
        }
    }
}

@Composable
private fun TileView(value: Int, modifier: Modifier) {
    val (bg, textColor) = getTileColors(value)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.r))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        if (value > 0) {
            val textSize = when {
                value < 100 -> 24
                value < 1000 -> 20
                else -> 16
            }
            RText(
                text = "$value",
                size = textSize,
                weight = FontWeight.Bold,
                color = textColor,
                align = TextAlign.Center,
            )
        }
    }
}

private fun getTileColors(value: Int): Pair<Color, Color> {
    return when (value) {
        0 -> Pair(Color(0xFFCDC1B4), Color.Transparent)
        2 -> Pair(Color(0xFFEEE4DA), Color(0xFF776E65))
        4 -> Pair(Color(0xFFEDE0C8), Color(0xFF776E65))
        8 -> Pair(Color(0xFFF2B179), Color.White)
        16 -> Pair(Color(0xFFF59563), Color.White)
        32 -> Pair(Color(0xFFF67C5F), Color.White)
        64 -> Pair(Color(0xFFF65E3B), Color.White)
        128 -> Pair(Color(0xFFEDCF72), Color.White)
        256 -> Pair(Color(0xFFEDCC61), Color.White)
        512 -> Pair(Color(0xFFEDC850), Color.White)
        1024 -> Pair(Color(0xFFEDC53F), Color.White)
        2048 -> Pair(Color(0xFFEDC22E), Color.White)
        else -> Pair(Color(0xFF3C3A32), Color.White)
    }
}

@Composable
private fun OverlayMessage(
    title: String,
    subtitle: String,
    primaryText: String,
    secondaryText: String?,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.r))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(20.r),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RText(title, 26, FontWeight.Bold, Color(0xFF776E65))
            Spacer(Modifier.height(8.r))
            RText(subtitle, 16, FontWeight.Normal, Color(0xFF8F7A66), align = TextAlign.Center)
            Spacer(Modifier.height(20.r))
            Button(
                onClick = onPrimary,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8F7A66)),
                shape = RoundedCornerShape(12.r),
            ) {
                RText(primaryText, 16, FontWeight.Bold, Color.White)
            }
            if (secondaryText != null && onSecondary != null) {
                Spacer(Modifier.height(8.r))
                Button(
                    onClick = onSecondary,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBBADA0)),
                    shape = RoundedCornerShape(12.r),
                ) {
                    RText(secondaryText, 15, FontWeight.Normal, Color.White)
                }
            }
        }
    }
}
