package com.nirvor.app.ui.games.duelx

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import com.nirvor.app.ui.games.Game2048Screen
import com.nirvor.app.ui.games.GameMemoryMatchScreen
import com.nirvor.app.ui.games.GameMinesweeperScreen
import com.nirvor.app.ui.games.GameSnakeScreen
import com.nirvor.app.ui.games.GameTicTacToeScreen
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r

private enum class ActiveDuelScreen {
    Lobby, Billiard, BilliardPoker, Carrom, Ludo, BrickBreaker, Archery,
    Snake, Game2048, TicTacToe, Minesweeper, Memory,
    Profile, Leaderboard, Shop, DailyBonus, Missions, Friends
}

@Composable
fun DuelXMainHubScreen(
    userName: String = "SHAHIN",
    onExitGameHub: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Auto rotate to landscape when entering Duel X, and restore to portrait when exiting!
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    var profile by remember { mutableStateOf(DuelXStorage.loadProfile(context, defaultName = userName)) }
    var activeScreen by remember { mutableStateOf(ActiveDuelScreen.Lobby) }

    when (activeScreen) {
        ActiveDuelScreen.Billiard -> {
            DuelXBilliardGame(profile = profile, onBack = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.BilliardPoker -> {
            DuelXBilliardPokerGame(profile = profile, onBack = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.Carrom -> {
            DuelXCarromGame(profile = profile, onBack = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.Ludo -> {
            DuelXLudoGame(profile = profile, onBack = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.BrickBreaker -> {
            DuelXBrickBreakerGame(profile = profile, onBack = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.Archery -> {
            DuelXArcheryGame(profile = profile, onBack = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.Snake -> {
            GameSnakeScreen(onBack = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.Game2048 -> {
            Game2048Screen(onBack = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.TicTacToe -> {
            GameTicTacToeScreen(onBack = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.Minesweeper -> {
            GameMinesweeperScreen(onBack = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.Memory -> {
            GameMemoryMatchScreen(onBack = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.Profile -> {
            DuelXProfileDialog(
                profile = profile,
                onSaveProfile = {
                    profile = it
                    DuelXStorage.saveProfile(context, it)
                },
                onClose = { activeScreen = ActiveDuelScreen.Lobby },
            )
        }

        ActiveDuelScreen.Leaderboard -> {
            DuelXLeaderboardDialog(userProfile = profile, onClose = { activeScreen = ActiveDuelScreen.Lobby })
        }

        ActiveDuelScreen.Shop -> {
            DuelXShopDialog(
                onBuyCoins = { amount ->
                    val newCoins = DuelXStorage.addCoins(context, amount)
                    profile = profile.copy(coins = newCoins)
                },
                onClose = { activeScreen = ActiveDuelScreen.Lobby },
            )
        }

        else -> {
            // Main Duel X Lobby (Exact Match to video 00:00 - 00:02)
            Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF070B14)) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.r, vertical = 6.r),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 1. Top Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Left: Exit Hub & Profile Avatar
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onExitGameHub) {
                                    GlyphIcon(Glyph.ArrowLeft, Color.White, Modifier.size(22.r))
                                }

                                Spacer(Modifier.width(4.r))

                                // Logo
                                RText("DUEL X", 20, FontWeight.Black, Color(0xFFE11D48))

                                Spacer(Modifier.width(14.r))

                                // Avatar & Level
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.r))
                                        .background(Color(0xFF131D31))
                                        .clickable { activeScreen = ActiveDuelScreen.Profile }
                                        .padding(horizontal = 8.r, vertical = 3.r),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.r)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1E293B))
                                            .border(1.5.r, Color(0xFF10B981), CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        RText("🧔🏽‍♂️", 20, FontWeight.Bold, Color.White)
                                    }
                                    Spacer(Modifier.width(8.r))
                                    Column {
                                        RText(profile.name, 12, FontWeight.ExtraBold, Color.White)
                                        RText("LV. ${profile.level}", 10, FontWeight.Bold, Color(0xFF10B981))
                                    }
                                }
                            }

                            // Center/Right: Coins, Gems, Notifications & Settings
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.r)) {
                                // Coins Chip
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.r))
                                        .background(Color(0xFF131D31))
                                        .clickable { activeScreen = ActiveDuelScreen.Shop }
                                        .padding(horizontal = 10.r, vertical = 4.r),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.r)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF59E0B)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        RText("9", 11, FontWeight.Bold, Color.Black)
                                    }
                                    Spacer(Modifier.width(6.r))
                                    RText("${profile.coins}", 13, FontWeight.Bold, Color(0xFFFBBF24))
                                    Spacer(Modifier.width(8.r))
                                    RText("+", 13, FontWeight.ExtraBold, Color(0xFF10B981))
                                }

                                // Gems Chip
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.r))
                                        .background(Color(0xFF131D31))
                                        .clickable { activeScreen = ActiveDuelScreen.Shop }
                                        .padding(horizontal = 10.r, vertical = 4.r),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RText("✦", 14, FontWeight.Bold, Color(0xFF8B5CF6))
                                    Spacer(Modifier.width(6.r))
                                    RText("${profile.gems}", 13, FontWeight.Bold, Color.White)
                                    Spacer(Modifier.width(8.r))
                                    RText("+", 13, FontWeight.ExtraBold, Color(0xFF8B5CF6))
                                }

                                // Mail Icon
                                IconButton(onClick = { activeScreen = ActiveDuelScreen.Missions }) {
                                    RText("✉️", 18, FontWeight.Bold, Color.White)
                                }

                                // Bell Icon
                                IconButton(onClick = { activeScreen = ActiveDuelScreen.DailyBonus }) {
                                    RText("🔔", 18, FontWeight.Bold, Color.White)
                                }

                                // Settings Icon
                                IconButton(onClick = { activeScreen = ActiveDuelScreen.Profile }) {
                                    GlyphIcon(Glyph.Gear, Color.White, Modifier.size(20.r))
                                }
                            }
                        }

                        // 2. Center Content: Left Widgets + 4 Game Cards + Right Widgets
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 4.r),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Left Side Panel
                            Column(
                                modifier = Modifier
                                    .width(105.r)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // Real Daily Bonus
                                val canClaim = DuelXStorage.canClaimDailyBonus(profile)
                                val countdown = DuelXStorage.getDailyBonusCountdown(profile)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.r))
                                        .background(Color(0xFF131D31))
                                        .border(1.r, if (canClaim) Color(0xFF10B981) else Color(0xFFF59E0B), RoundedCornerShape(10.r))
                                        .clickable { activeScreen = ActiveDuelScreen.DailyBonus }
                                        .padding(6.r),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    RText("DAILY BONUS", 9, FontWeight.Bold, Color(0xFFFBBF24))
                                    RText(countdown, 9, FontWeight.Medium, if (canClaim) Color(0xFF34D399) else Color(0xFF94A3B8))
                                    Spacer(Modifier.height(3.r))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.r))
                                            .background(if (canClaim) Color(0xFF10B981) else Color(0xFFF59E0B))
                                            .padding(horizontal = 6.r, vertical = 2.r),
                                    ) {
                                        RText(if (canClaim) "CLAIM NOW" else "TIMER", 8, FontWeight.Bold, Color.White)
                                    }
                                }

                                // Rank
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.r))
                                        .background(Color(0xFF131D31))
                                        .padding(6.r),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RText("🛡️", 18, FontWeight.Bold, Color.White)
                                    Spacer(Modifier.width(6.r))
                                    Column {
                                        RText("RANK", 9, FontWeight.Bold, Color(0xFF94A3B8))
                                        RText("BRONZE I", 10, FontWeight.Bold, Color(0xFFF59E0B))
                                    }
                                }

                                // Mission & Players Online
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.r))
                                            .background(Color(0xFF131D31))
                                            .clickable { activeScreen = ActiveDuelScreen.Missions }
                                            .padding(horizontal = 8.r, vertical = 5.r),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        RText("📜", 14, FontWeight.Bold, Color.White)
                                        Spacer(Modifier.width(6.r))
                                        RText("MISSION", 10, FontWeight.Bold, Color.White)
                                    }
                                    Spacer(Modifier.height(4.r))
                                    RText("🟢 1 ONLINE", 9, FontWeight.Bold, Color(0xFF34D399))
                                }
                            }

                            Spacer(Modifier.width(10.r))

                            // Center 4 Featured Game Cards + Quick Play Shelf
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                RText("CHOOSE YOUR TABLE", 17, FontWeight.ExtraBold, Color.White)
                                RText("PICK YOUR GAME AND START DUEL", 10, FontWeight.Bold, Color(0xFF38BDF8))
                                Spacer(Modifier.height(6.r))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.r),
                                ) {
                                    // 1. BILLIARD
                                    DuelGameCard(
                                        title = "BILLIARD",
                                        sub = "8, 9 OR 10 BALL\nPOOL DUEL",
                                        badge = "🎱",
                                        buttonColor = Color(0xFF10B981),
                                        modifier = Modifier.weight(1f),
                                        onClick = { activeScreen = ActiveDuelScreen.Billiard },
                                    )

                                    // 2. BILLIARD POKER
                                    DuelGameCard(
                                        title = "BILLIARD POKER",
                                        sub = "WIN PRIZES WITH\nPOKER HANDS",
                                        badge = "🃏",
                                        buttonColor = Color(0xFF8B5CF6),
                                        modifier = Modifier.weight(1f),
                                        onClick = { activeScreen = ActiveDuelScreen.BilliardPoker },
                                    )

                                    // 3. LUDO
                                    DuelGameCard(
                                        title = "LUDO",
                                        sub = "2 OR 4 PLAYERS\nROLL & RACE HOME",
                                        badge = "🎲",
                                        buttonColor = Color(0xFF10B981),
                                        modifier = Modifier.weight(1f),
                                        onClick = { activeScreen = ActiveDuelScreen.Ludo },
                                    )

                                    // 4. CARROM
                                    DuelGameCard(
                                        title = "CARROM",
                                        sub = "POCKET YOUR NINE\nCOVER THE QUEEN",
                                        badge = "🎯",
                                        buttonColor = Color(0xFFEA580C),
                                        modifier = Modifier.weight(1f),
                                        onClick = { activeScreen = ActiveDuelScreen.Carrom },
                                    )
                                }

                                Spacer(Modifier.height(6.r))

                                // Quick Play Arcade Shelf
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.r),
                                ) {
                                    QuickArcadePill("🧱 BRICK", Modifier.weight(1f)) { activeScreen = ActiveDuelScreen.BrickBreaker }
                                    QuickArcadePill("🏹 ARCHERY", Modifier.weight(1f)) { activeScreen = ActiveDuelScreen.Archery }
                                    QuickArcadePill("🐍 SNAKE", Modifier.weight(1f)) { activeScreen = ActiveDuelScreen.Snake }
                                    QuickArcadePill("🔢 2048", Modifier.weight(1f)) { activeScreen = ActiveDuelScreen.Game2048 }
                                    QuickArcadePill("❌ XO", Modifier.weight(1f)) { activeScreen = ActiveDuelScreen.TicTacToe }
                                    QuickArcadePill("💣 MINES", Modifier.weight(1f)) { activeScreen = ActiveDuelScreen.Minesweeper }
                                    QuickArcadePill("🃏 MEMORY", Modifier.weight(1f)) { activeScreen = ActiveDuelScreen.Memory }
                                }
                            }

                            Spacer(Modifier.width(10.r))

                            // Right Side Panel
                            Column(
                                modifier = Modifier
                                    .width(120.r)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // Jackpot Wheel
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.r))
                                        .background(Color(0xFF131D31))
                                        .clickable { activeScreen = ActiveDuelScreen.DailyBonus }
                                        .padding(6.r),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    RText("JACKPOT", 10, FontWeight.Bold, Color(0xFFFBBF24))
                                    RText("UP TO 10,000", 11, FontWeight.ExtraBold, Color.White)
                                    RText("POKER WHEEL", 8, FontWeight.Normal, Color(0xFF94A3B8))
                                }

                                // Practice
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.r))
                                        .background(Color(0xFF131D31))
                                        .clickable { activeScreen = ActiveDuelScreen.Billiard }
                                        .padding(6.r),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    RText("PRACTICE", 10, FontWeight.Bold, Color(0xFF34D399))
                                    RText("PLAY FREE BOTS", 9, FontWeight.Medium, Color.White)
                                }

                                // Play With Friends
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.r))
                                        .background(Color(0xFF131D31))
                                        .border(1.r, Color(0xFF3B82F6), RoundedCornerShape(10.r))
                                        .clickable { activeScreen = ActiveDuelScreen.Friends }
                                        .padding(6.r),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    RText("PLAY WITH FRIENDS", 9, FontWeight.Bold, Color(0xFF38BDF8))
                                    RText("PRIVATE ROOM", 9, FontWeight.Medium, Color.White)
                                }
                            }
                        }

                        // 3. Bottom Navigation Bar (7 Tabs matching video)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.r))
                                .background(Color(0xFF0F172A))
                                .border(1.r, Color(0xFF1E293B), RoundedCornerShape(14.r))
                                .padding(vertical = 4.r, horizontal = 10.r),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BottomTab("HOME", "🏠", active = true) { activeScreen = ActiveDuelScreen.Lobby }
                            BottomTab("SHOP", "🛒", active = false) { activeScreen = ActiveDuelScreen.Shop }
                            BottomTab("RANKINGS", "🏆", active = false) { activeScreen = ActiveDuelScreen.Leaderboard }
                            BottomTab("ITEMS", "🎒", active = false) { activeScreen = ActiveDuelScreen.Profile }
                            BottomTab("FRIENDS", "👥", active = false) { activeScreen = ActiveDuelScreen.Friends }
                            BottomTab("CHALLENGE", "⚔️", active = false) { activeScreen = ActiveDuelScreen.Billiard }
                            BottomTab("CUES", "🎱", active = false) { activeScreen = ActiveDuelScreen.Shop }
                        }
                    }

                    // Modals
                    if (activeScreen == ActiveDuelScreen.DailyBonus) {
                        DuelXDailyBonusDialog(
                            profile = profile,
                            onClaim = {
                                val (amount, _) = DuelXStorage.claimDailyBonus(context)
                                profile = DuelXStorage.loadProfile(context)
                            },
                            onClose = { activeScreen = ActiveDuelScreen.Lobby },
                        )
                    }

                    if (activeScreen == ActiveDuelScreen.Missions) {
                        DuelXMissionsDialog(onClose = { activeScreen = ActiveDuelScreen.Lobby })
                    }

                    if (activeScreen == ActiveDuelScreen.Friends) {
                        DuelXPlayFriendsDialog(
                            onSelectGame = { g ->
                                when {
                                    g.contains("CARROM", ignoreCase = true) -> activeScreen = ActiveDuelScreen.Carrom
                                    g.contains("POKER", ignoreCase = true) -> activeScreen = ActiveDuelScreen.BilliardPoker
                                    else -> activeScreen = ActiveDuelScreen.Billiard
                                }
                            },
                            onClose = { activeScreen = ActiveDuelScreen.Lobby },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickArcadePill(text: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.r))
            .background(Color(0xFF1E293B))
            .clickable(onClick = onClick)
            .padding(vertical = 4.r),
        contentAlignment = Alignment.Center,
    ) {
        RText(text, 9, FontWeight.Bold, Color(0xFFE2E8F0), maxLines = 1)
    }
}

@Composable
private fun DuelGameCard(
    title: String,
    sub: String,
    badge: String,
    buttonColor: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(152.r)
            .clip(RoundedCornerShape(12.r))
            .background(Color(0xFF131D31))
            .border(1.r, Color(0xFF1E293B), RoundedCornerShape(12.r))
            .padding(8.r),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        RText(badge, 30, FontWeight.Bold, Color.White)

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RText(title, 13, FontWeight.ExtraBold, Color.White)
            Spacer(Modifier.height(2.r))
            RText(sub, 8, FontWeight.Medium, Color(0xFF94A3B8), align = TextAlign.Center, lineHeight = 10)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.r))
                .background(buttonColor)
                .clickable(onClick = onClick)
                .padding(vertical = 4.r),
            contentAlignment = Alignment.Center,
        ) {
            RText("PLAY NOW", 10, FontWeight.Bold, Color.White)
        }
    }
}

@Composable
private fun BottomTab(label: String, icon: String, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.r))
            .background(if (active) Color(0xFF132A26) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.r, vertical = 3.r),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RText(icon, 14, FontWeight.Bold, Color.White)
        RText(label, 9, FontWeight.Bold, if (active) Color(0xFF10B981) else Color(0xFF64748B))
    }
}
