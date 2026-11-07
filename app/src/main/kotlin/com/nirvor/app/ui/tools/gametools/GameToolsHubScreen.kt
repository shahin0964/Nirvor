package com.nirvor.app.ui.tools.gametools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.gametools.games.BaghBandi3DScreen
import com.nirvor.app.ui.tools.gametools.games.Carrom3DScreen
import com.nirvor.app.ui.tools.gametools.games.DaagKata3DScreen
import com.nirvor.app.ui.tools.gametools.games.PenFight3DScreen
import com.nirvor.app.ui.tools.gametools.games.SholoGuti3DScreen
import com.nirvor.app.ui.tools.gametools.models.Difficulty
import com.nirvor.app.ui.tools.gametools.models.GameId
import com.nirvor.app.ui.tools.gametools.models.GamePlayMode
import com.nirvor.app.ui.tools.gametools.models.GameToolsStorage

@Composable
fun GameToolsHubScreen(
    userName: String = "SHAHIN",
    bottomPadding: Dp,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf(GameToolsStorage.loadProfile(context, userName)) }

    var activeGame by remember { mutableStateOf<GameId?>(null) }
    var selectedMode by remember { mutableStateOf(GamePlayMode.VsBot) }
    var selectedDifficulty by remember { mutableStateOf(Difficulty.Medium) }

    when (val game = activeGame) {
        GameId.Carrom -> Carrom3DScreen(
            mode = selectedMode,
            difficulty = selectedDifficulty,
            onBack = { activeGame = null },
        )
        GameId.SholoGuti -> SholoGuti3DScreen(
            mode = selectedMode,
            difficulty = selectedDifficulty,
            onBack = { activeGame = null },
        )
        GameId.PenFight -> PenFight3DScreen(
            mode = selectedMode,
            difficulty = selectedDifficulty,
            onBack = { activeGame = null },
        )
        GameId.BaghBandi -> BaghBandi3DScreen(
            mode = selectedMode,
            difficulty = selectedDifficulty,
            onBack = { activeGame = null },
        )
        GameId.DaagKata -> DaagKata3DScreen(
            mode = selectedMode,
            difficulty = selectedDifficulty,
            onBack = { activeGame = null },
        )
        null -> {
            PageScaffold(
                title = "Game Tools",
                subtitle = "প্রফেশনাল ৩ডি বোর্ড ও ক্লাসিক খেলা",
                onBack = onBack,
                bottomPadding = bottomPadding,
            ) {
                // Top Player Profile & Stats Banner
                item {
                    GameToolsProfileBanner(
                        userName = profile.name,
                        level = profile.level,
                        xp = profile.xp,
                        coins = profile.coins,
                    )
                }

                // Mode Selector Bar (Vs Bot vs 2 Players)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.r))
                            .background(Color.White)
                            .border(1.r, C.TodayBorder, RoundedCornerShape(14.r))
                            .padding(4.r),
                        horizontalArrangement = Arrangement.spacedBy(4.r),
                    ) {
                        GamePlayMode.values().forEach { m ->
                            val isSelected = (m == selectedMode)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.r))
                                    .background(
                                        if (isSelected) Brush.horizontalGradient(listOf(C.TaskGreenA, C.TaskGreenB))
                                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    )
                                    .clickable { selectedMode = m }
                                    .padding(vertical = 10.r),
                                contentAlignment = Alignment.Center,
                            ) {
                                RText(
                                    text = m.labelBn,
                                    size = 13,
                                    weight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else C.Body,
                                )
                            }
                        }
                    }
                }

                // 5 Game Cards
                items(GameId.values()) { g ->
                    GameCardItem(
                        game = g,
                        onPlay = { activeGame = g },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameToolsProfileBanner(
    userName: String,
    level: Int,
    xp: Int,
    coins: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.r))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF064E3B), // Deep Emerald
                        Color(0xFF047857),
                    )
                )
            )
            .padding(18.r),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.r)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        RText("🎮", 22, FontWeight.Bold, Color.White)
                    }
                    Spacer(Modifier.width(12.r))
                    Column {
                        RText(userName, 18, FontWeight.Bold, Color.White)
                        Spacer(Modifier.height(2.r))
                        RText("লেভেল $level প্লেয়ার • $xp XP", 12, FontWeight.Normal, Color(0xFFA7F3D0))
                    }
                }

                // Coins Counter
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.r))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 12.r, vertical = 6.r),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RText("🪙", 14, FontWeight.Bold, Color.White)
                    Spacer(Modifier.width(6.r))
                    RText("$coins", 14, FontWeight.ExtraBold, Color(0xFFFDE68A))
                }
            }
        }
    }
}

@Composable
private fun GameCardItem(
    game: GameId,
    onPlay: () -> Unit,
) {
    ListCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 3D Preview Badge
            Box(
                modifier = Modifier
                    .size(64.r)
                    .clip(RoundedCornerShape(16.r))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                game.accentColor.copy(alpha = 0.18f),
                                game.accentColor.copy(alpha = 0.32f),
                            )
                        )
                    )
                    .border(1.5.r, game.accentColor.copy(alpha = 0.45f), RoundedCornerShape(16.r)),
                contentAlignment = Alignment.Center,
            ) {
                RText(game.iconEmoji, 32, FontWeight.Bold, Color.White)
            }

            Spacer(Modifier.width(16.r))

            // Game Titles & Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RText(game.titleBn, 18, FontWeight.Bold, C.Ink)
                    Spacer(Modifier.width(8.r))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.r))
                            .background(game.accentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.r, vertical = 2.r),
                    ) {
                        RText(game.titleEn, 10, FontWeight.Bold, game.accentColor)
                    }
                }
                Spacer(Modifier.height(4.r))
                RText(game.descriptionBn, 12, FontWeight.Normal, C.Hint, maxLines = 2)
                Spacer(Modifier.height(6.r))
                RText("👥 ${game.playerModeDesc}", 11, FontWeight.SemiBold, Color(0xFF047857))
            }
        }

        Spacer(Modifier.height(14.r))

        // Play Now Button
        Button(
            onClick = onPlay,
            colors = ButtonDefaults.buttonColors(
                containerColor = C.TaskGreenA,
            ),
            shape = RoundedCornerShape(12.r),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.r),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("খেলুন (Play Now)", 15, FontWeight.Bold, Color.White)
                Spacer(Modifier.width(6.r))
                RText("▶", 12, FontWeight.Bold, Color.White)
            }
        }
    }
}
