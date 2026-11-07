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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import kotlinx.coroutines.delay
import kotlin.random.Random

private enum class TttMode { SinglePlayer, TwoPlayers }

@Composable
fun GameTicTacToeScreen(onBack: () -> Unit) {
    var mode by remember { mutableStateOf(TttMode.SinglePlayer) }
    var board by remember { mutableStateOf(List(9) { "" }) }
    var isXTurn by remember { mutableStateOf(true) }
    var isThinking by remember { mutableStateOf(false) }

    var xWins by remember { mutableIntStateOf(0) }
    var oWins by remember { mutableIntStateOf(0) }
    var draws by remember { mutableIntStateOf(0) }

    val winLines = listOf(
        listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8), // rows
        listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8), // cols
        listOf(0, 4, 8), listOf(2, 4, 6),                 // diags
    )

    fun checkWinner(b: List<String>): Pair<String?, List<Int>?> {
        for (line in winLines) {
            val (a, bIdx, c) = line
            if (b[a].isNotEmpty() && b[a] == b[bIdx] && b[a] == b[c]) {
                return Pair(b[a], line)
            }
        }
        if (b.all { it.isNotEmpty() }) {
            return Pair("DRAW", null)
        }
        return Pair(null, null)
    }

    val (winner, winningIndices) = checkWinner(board)
    val isGameOver = winner != null

    fun resetBoard() {
        board = List(9) { "" }
        isXTurn = true
        isThinking = false
    }

    fun resetMatch() {
        resetBoard()
        xWins = 0
        oWins = 0
        draws = 0
    }

    // Award score upon game finish
    LaunchedEffect(winner) {
        when (winner) {
            "X" -> xWins += 1
            "O" -> oWins += 1
            "DRAW" -> draws += 1
        }
    }

    // AI Move
    fun findBestMove(b: List<String>): Int {
        // 1. Can AI (O) win?
        for (i in b.indices) {
            if (b[i].isEmpty()) {
                val temp = b.toMutableList()
                temp[i] = "O"
                if (checkWinner(temp).first == "O") return i
            }
        }
        // 2. Can player (X) win? Block it!
        for (i in b.indices) {
            if (b[i].isEmpty()) {
                val temp = b.toMutableList()
                temp[i] = "X"
                if (checkWinner(temp).first == "X") return i
            }
        }
        // 3. Take center if available
        if (b[4].isEmpty()) return 4
        // 4. Take corners
        val corners = listOf(0, 2, 6, 8).filter { b[it].isEmpty() }
        if (corners.isNotEmpty()) return corners[Random.nextInt(corners.size)]
        // 5. Any remaining empty
        val remaining = b.indices.filter { b[it].isEmpty() }
        return if (remaining.isNotEmpty()) remaining[Random.nextInt(remaining.size)] else -1
    }

    LaunchedEffect(isXTurn, mode, isGameOver) {
        if (!isXTurn && mode == TttMode.SinglePlayer && !isGameOver) {
            isThinking = true
            delay(350)
            val move = findBestMove(board)
            if (move != -1) {
                val next = board.toMutableList()
                next[move] = "O"
                board = next
                isXTurn = true
            }
            isThinking = false
        }
    }

    fun onCellClick(index: Int) {
        if (isGameOver || board[index].isNotEmpty() || isThinking) return
        if (mode == TttMode.SinglePlayer && !isXTurn) return

        val next = board.toMutableList()
        next[index] = if (isXTurn) "X" else "O"
        board = next
        isXTurn = !isXTurn
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF3F6FC)) {
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
                        RText("Tic Tac Toe", 24, FontWeight.Bold, Color(0xFF1E3A8A))
                        RText("টিক ট্যাক টো (XO)", 15, FontWeight.Normal, C.Hint)
                    }
                }

                Button(
                    onClick = { resetBoard() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.r),
                ) {
                    GlyphIcon(Glyph.Refresh, Color.White, Modifier.size(16.r), strokeWidth = 2f)
                    Spacer(Modifier.width(6.r))
                    RText("রিসেট", 14, FontWeight.SemiBold, Color.White)
                }
            }

            Spacer(Modifier.height(14.r))

            // Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.r))
                    .background(Color(0xFFE2E8F0))
                    .padding(4.r),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TttModeTab(
                    title = "🤖 বনাম AI",
                    selected = mode == TttMode.SinglePlayer,
                    modifier = Modifier.weight(1f),
                ) {
                    mode = TttMode.SinglePlayer
                    resetMatch()
                }
                TttModeTab(
                    title = "👥 ২ জন খেলোয়াড়",
                    selected = mode == TttMode.TwoPlayers,
                    modifier = Modifier.weight(1f),
                ) {
                    mode = TttMode.TwoPlayers
                    resetMatch()
                }
            }

            Spacer(Modifier.height(16.r))

            // Scoreboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.r),
            ) {
                ScoreCard("খেলোয়াড় X", xWins, Color(0xFF0284C7), Modifier.weight(1f))
                ScoreCard("ড্র (Draw)", draws, Color(0xFF64748B), Modifier.weight(1f))
                ScoreCard(
                    if (mode == TttMode.SinglePlayer) "AI (O)" else "খেলোয়াড় O",
                    oWins,
                    Color(0xFFE11D48),
                    Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.r))

            // Turn Indicator Banner
            val turnText = when {
                isGameOver -> when (winner) {
                    "X" -> "🎉 খেলোয়াড় X বিজয়ী হয়েছে!"
                    "O" -> if (mode == TttMode.SinglePlayer) "🤖 AI বিজয়ী হয়েছে!" else "🎉 খেলোয়াড় O বিজয়ী হয়েছে!"
                    else -> "🤝 খেলা ড্র হয়েছে!"
                }
                isThinking -> "🤖 AI চিন্তা করছে..."
                isXTurn -> "👉 খেলোয়াড় X এর চাল"
                else -> if (mode == TttMode.SinglePlayer) "🤖 AI এর চাল" else "👉 খেলোয়াড় O এর চাল"
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.r))
                    .background(Color.White)
                    .padding(vertical = 10.r, horizontal = 16.r),
                contentAlignment = Alignment.Center,
            ) {
                RText(
                    turnText,
                    16,
                    FontWeight.SemiBold,
                    when {
                        winner == "X" -> Color(0xFF0284C7)
                        winner == "O" -> Color(0xFFE11D48)
                        winner == "DRAW" -> Color(0xFF64748B)
                        isXTurn -> Color(0xFF0284C7)
                        else -> Color(0xFFE11D48)
                    },
                )
            }

            Spacer(Modifier.height(16.r))

            // 3x3 Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.r))
                    .background(Color.White)
                    .border(1.r, Color(0xFFCBD5E1), RoundedCornerShape(20.r))
                    .padding(12.r),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.r),
                ) {
                    for (row in 0 until 3) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.r),
                        ) {
                            for (col in 0 until 3) {
                                val idx = row * 3 + col
                                val symbol = board[idx]
                                val isWinningCell = winningIndices?.contains(idx) == true
                                TttCell(
                                    symbol = symbol,
                                    isWinning = isWinningCell,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onCellClick(idx) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.r))

            // Post-Game Action
            if (isGameOver) {
                Button(
                    onClick = { resetBoard() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(14.r),
                    modifier = Modifier.fillMaxWidth().height(50.r),
                ) {
                    RText("পরের রাউন্ড খেলুন 🔄", 16, FontWeight.Bold, Color.White)
                }
            }

            Spacer(Modifier.height(24.r))
        }
    }
}

@Composable
private fun TttModeTab(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.r))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.r),
        contentAlignment = Alignment.Center,
    ) {
        RText(title, 14, if (selected) FontWeight.Bold else FontWeight.Medium, if (selected) Color(0xFF1E293B) else Color(0xFF64748B))
    }
}

@Composable
private fun ScoreCard(label: String, score: Int, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.r),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.r, horizontal = 8.r),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RText(label, 12, FontWeight.Medium, C.Hint, align = TextAlign.Center)
            Spacer(Modifier.height(2.r))
            RText("$score", 20, FontWeight.Bold, color)
        }
    }
}

@Composable
private fun TttCell(symbol: String, isWinning: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bgColor = when {
        isWinning -> Color(0xFFDCFCE7)
        symbol.isNotEmpty() -> Color(0xFFF8FAFC)
        else -> Color(0xFFF1F5F9)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.r))
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (symbol.isNotEmpty()) {
            val color = if (symbol == "X") Color(0xFF0284C7) else Color(0xFFE11D48)
            RText(symbol, 42, FontWeight.ExtraBold, color, align = TextAlign.Center)
        }
    }
}
