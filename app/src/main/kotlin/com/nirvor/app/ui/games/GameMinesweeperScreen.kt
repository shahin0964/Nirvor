package com.nirvor.app.ui.games

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import kotlinx.coroutines.delay
import kotlin.random.Random

private data class MineCell(
    val row: Int,
    val col: Int,
    val hasMine: Boolean = false,
    val isRevealed: Boolean = false,
    val isFlagged: Boolean = false,
    val adjacentMines: Int = 0,
)

private enum class ToolMode { Reveal, Flag }

@Composable
fun GameMinesweeperScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val rows = 9
    val cols = 9
    val totalMines = 10

    var cells by remember {
        mutableStateOf(
            List(rows * cols) { idx ->
                MineCell(row = idx / cols, col = idx % cols)
            }
        )
    }

    var isFirstMove by remember { mutableStateOf(true) }
    var isGameOver by remember { mutableStateOf(false) }
    var isWon by remember { mutableStateOf(false) }
    var toolMode by remember { mutableStateOf(ToolMode.Reveal) }
    var secondsElapsed by remember { mutableIntStateOf(0) }
    var isTimerActive by remember { mutableStateOf(false) }
    var bestTime by remember { mutableIntStateOf(GameStorage.getBestTime(context, "minesweeper")) }

    val flagsPlaced = cells.count { it.isFlagged }
    val remainingMines = (totalMines - flagsPlaced).coerceAtLeast(0)

    fun restartGame() {
        cells = List(rows * cols) { idx ->
            MineCell(row = idx / cols, col = idx % cols)
        }
        isFirstMove = true
        isGameOver = false
        isWon = false
        secondsElapsed = 0
        isTimerActive = false
    }

    // Stopwatch
    LaunchedEffect(isTimerActive, isGameOver, isWon) {
        while (isTimerActive && !isGameOver && !isWon) {
            delay(1000L)
            secondsElapsed += 1
        }
    }

    fun setupBoard(safeRow: Int, safeCol: Int) {
        val safeIndex = safeRow * cols + safeCol
        val allIndices = (0 until rows * cols).filter { it != safeIndex }.shuffled()
        val mineIndices = allIndices.take(totalMines).toSet()

        val tempCells = MutableList(rows * cols) { idx ->
            val r = idx / cols
            val c = idx % cols
            MineCell(row = r, col = c, hasMine = idx in mineIndices)
        }

        // Compute adjacent mines
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val idx = r * cols + c
                if (!tempCells[idx].hasMine) {
                    var count = 0
                    for (dr in -1..1) {
                        for (dc in -1..1) {
                            val nr = r + dr
                            val nc = c + dc
                            if (nr in 0 until rows && nc in 0 until cols) {
                                if (tempCells[nr * cols + nc].hasMine) count++
                            }
                        }
                    }
                    tempCells[idx] = tempCells[idx].copy(adjacentMines = count)
                }
            }
        }
        cells = tempCells
    }

    fun revealCell(r: Int, c: Int) {
        if (isGameOver || isWon) return
        val idx = r * cols + c
        val cell = cells[idx]
        if (cell.isRevealed || cell.isFlagged) return

        if (isFirstMove) {
            isFirstMove = false
            setupBoard(r, c)
            isTimerActive = true
        }

        // Re-fetch after setup
        val currentCell = cells[idx]
        if (currentCell.hasMine) {
            // Hit a mine!
            isGameOver = true
            isTimerActive = false
            cells = cells.map { if (it.hasMine) it.copy(isRevealed = true) else it }
            return
        }

        // Flood fill cascade for 0 adjacent
        val updated = cells.toMutableList()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(r, c))

        while (queue.isNotEmpty()) {
            val (cr, cc) = queue.removeFirst()
            val cIdx = cr * cols + cc
            val target = updated[cIdx]
            if (target.isRevealed || target.isFlagged || target.hasMine) continue

            updated[cIdx] = target.copy(isRevealed = true)

            if (target.adjacentMines == 0) {
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        val nr = cr + dr
                        val nc = cc + dc
                        if (nr in 0 until rows && nc in 0 until cols) {
                            val nIdx = nr * cols + nc
                            if (!updated[nIdx].isRevealed && !updated[nIdx].hasMine) {
                                queue.add(Pair(nr, nc))
                            }
                        }
                    }
                }
            }
        }

        cells = updated

        // Check Win: all non-mine cells revealed
        val nonMineCount = rows * cols - totalMines
        val revealedCount = updated.count { it.isRevealed && !it.hasMine }
        if (revealedCount == nonMineCount) {
            isWon = true
            isTimerActive = false
            if (secondsElapsed < bestTime) {
                bestTime = secondsElapsed
                GameStorage.saveBestTime(context, "minesweeper", secondsElapsed)
            }
        }
    }

    fun toggleFlag(r: Int, c: Int) {
        if (isGameOver || isWon) return
        val idx = r * cols + c
        val cell = cells[idx]
        if (cell.isRevealed) return

        val updated = cells.toMutableList()
        updated[idx] = cell.copy(isFlagged = !cell.isFlagged)
        cells = updated
    }

    fun handleCellClick(r: Int, c: Int) {
        if (toolMode == ToolMode.Reveal) {
            revealCell(r, c)
        } else {
            toggleFlag(r, c)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF9FAFB)) {
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
                        RText("Minesweeper", 24, FontWeight.Bold, Color(0xFF991B1B))
                        RText("ক্লাসিক মাইনসুইপার", 15, FontWeight.Normal, C.Hint)
                    }
                }

                // Smiley face restart button
                val smiley = when {
                    isWon -> "😎"
                    isGameOver -> "💥"
                    else -> "🙂"
                }
                Box(
                    modifier = Modifier
                        .size(46.r)
                        .clip(CircleShape)
                        .background(Color(0xFFFEE2E2))
                        .clickable { restartGame() },
                    contentAlignment = Alignment.Center,
                ) {
                    RText(smiley, 24, FontWeight.Bold, Color.Black)
                }
            }

            Spacer(Modifier.height(14.r))

            // Stats Card (Mines & Time)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.r),
            ) {
                MineStatCard("অবশিষ্ট মাইন", "💣 $remainingMines", Color(0xFFDC2626), Modifier.weight(1f))
                MineStatCard("সময়", "⏱️ ${secondsElapsed}s", Color(0xFFD97706), Modifier.weight(1f))
                val bestText = if (bestTime < 9999) "${bestTime}s" else "--"
                MineStatCard("রেকর্ড", "🏆 $bestText", Color(0xFF059669), Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.r))

            // Tool Mode Selector (Reveal vs Flag)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.r))
                    .background(Color(0xFFE5E7EB))
                    .padding(4.r),
            ) {
                ToolModeTab(
                    title = "⛏️ খনন করুন (Reveal)",
                    selected = toolMode == ToolMode.Reveal,
                    modifier = Modifier.weight(1f),
                ) { toolMode = ToolMode.Reveal }

                ToolModeTab(
                    title = "🚩 পতাকা দিন (Flag)",
                    selected = toolMode == ToolMode.Flag,
                    modifier = Modifier.weight(1f),
                ) { toolMode = ToolMode.Flag }
            }

            Spacer(Modifier.height(16.r))

            // 9x9 Board
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.r))
                    .background(Color(0xFFD1D5DB))
                    .border(1.r, Color(0xFF9CA3AF), RoundedCornerShape(16.r))
                    .padding(6.r),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(3.r),
                ) {
                    for (r in 0 until rows) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(3.r),
                        ) {
                            for (c in 0 until cols) {
                                val idx = r * cols + c
                                val cell = cells[idx]
                                MineCellView(
                                    cell = cell,
                                    modifier = Modifier.weight(1f),
                                    onClick = { handleCellClick(r, c) },
                                )
                            }
                        }
                    }
                }

                // Win Overlay
                AnimatedVisibility(visible = isWon, enter = fadeIn() + scaleIn()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.r))
                            .background(Color.White.copy(alpha = 0.94f))
                            .padding(16.r),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("অভিনন্দন! আপনি জিতেছেন! 🎉", 22, FontWeight.Bold, Color(0xFF059669), align = TextAlign.Center)
                            Spacer(Modifier.height(6.r))
                            RText("সব মাইন সফলভাবে সনাক্ত করেছেন। সময়: ${secondsElapsed}s", 14, FontWeight.Normal, C.Hint)
                            Spacer(Modifier.height(16.r))
                            Button(
                                onClick = { restartGame() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                shape = RoundedCornerShape(10.r),
                            ) {
                                RText("নতুন গেম শুরু করুন", 15, FontWeight.Bold, Color.White)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.r))

            RText("টিপস: প্রথম চাল সবসময় নিরাপদ। মোড পরিবর্তন করে পতাকা দিন।", 13, FontWeight.Normal, C.Hint, align = TextAlign.Center)

            Spacer(Modifier.height(24.r))
        }
    }
}

@Composable
private fun ToolModeTab(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.r))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.r),
        contentAlignment = Alignment.Center,
    ) {
        RText(title, 13, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) Color(0xFF111827) else Color(0xFF6B7280))
    }
}

@Composable
private fun MineStatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.r),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.r, horizontal = 6.r),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RText(label, 11, FontWeight.Medium, C.Hint, align = TextAlign.Center)
            Spacer(Modifier.height(2.r))
            RText(value, 16, FontWeight.Bold, color)
        }
    }
}

@Composable
private fun MineCellView(cell: MineCell, modifier: Modifier, onClick: () -> Unit) {
    val bg = when {
        cell.isRevealed && cell.hasMine -> Color(0xFFEF4444)
        cell.isRevealed -> Color(0xFFF3F4F6)
        else -> Color(0xFFE5E7EB)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.r))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            cell.isFlagged -> RText("🚩", 16, FontWeight.Bold, Color.Red)
            cell.isRevealed && cell.hasMine -> RText("💣", 16, FontWeight.Bold, Color.Black)
            cell.isRevealed && cell.adjacentMines > 0 -> {
                val color = when (cell.adjacentMines) {
                    1 -> Color(0xFF2563EB)
                    2 -> Color(0xFF16A34A)
                    3 -> Color(0xFFDC2626)
                    4 -> Color(0xFF1E3A8A)
                    else -> Color(0xFF7C3AED)
                }
                RText("${cell.adjacentMines}", 16, FontWeight.ExtraBold, color)
            }
        }
    }
}
