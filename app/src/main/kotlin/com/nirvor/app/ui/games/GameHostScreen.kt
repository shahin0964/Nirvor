package com.nirvor.app.ui.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun GameHostScreen(
    gameId: String,
    onBack: () -> Unit,
) {
    when (gameId.lowercase()) {
        "2048" -> Game2048Screen(onBack = onBack)
        "snake" -> GameSnakeScreen(onBack = onBack)
        "tictactoe" -> GameTicTacToeScreen(onBack = onBack)
        "memory" -> GameMemoryMatchScreen(onBack = onBack)
        "minesweeper" -> GameMinesweeperScreen(onBack = onBack)
        else -> {
            LaunchedEffect(Unit) { onBack() }
        }
    }
}
