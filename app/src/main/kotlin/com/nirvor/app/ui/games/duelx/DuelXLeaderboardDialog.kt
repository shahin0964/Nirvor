package com.nirvor.app.ui.games.duelx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r

private data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val wins: Int,
    val coins: Int,
    val avatar: String,
    val isUser: Boolean = false,
)

@Composable
fun DuelXLeaderboardDialog(
    userProfile: DuelXProfile,
    onClose: () -> Unit,
) {
    val players = listOf(
        LeaderboardEntry(1, "TURBO_BALLER95", 2, 2509, "🥽"),
        LeaderboardEntry(2, "GOLDEN_CUE86", 2, 2017, "👑"),
        LeaderboardEntry(3, "PLAYER1", 1, 2500, "🧢"),
        LeaderboardEntry(4, "PLAYER9192", 1, 2000, "🧔🏻‍♂️"),
        LeaderboardEntry(5, userProfile.name, userProfile.wins, userProfile.coins, "🧔🏽‍♂️", isUser = true),
        LeaderboardEntry(6, "ROYAL_SNIPER21", 0, 2050, "🧔🏾‍♂️"),
        LeaderboardEntry(7, "PLAYER5380", 0, 2000, "🧔🏻‍♂️"),
        LeaderboardEntry(8, "ROYAL_BALLER70", 0, 1675, "👑"),
        LeaderboardEntry(9, "TURBO_SNIPER36", 0, 1475, "🥽"),
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1120)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.r, vertical = 14.r),
        ) {
            // Header with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    GlyphIcon(Glyph.ArrowLeft, Color.White, Modifier.size(24.r), strokeWidth = 2.2f)
                }
                Spacer(Modifier.width(10.r))
                RText("LEADERBOARD", 22, FontWeight.ExtraBold, Color.White)
            }

            Spacer(Modifier.height(14.r))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.r),
            ) {
                itemsIndexed(players) { _, player ->
                    val isUser = player.isUser
                    val borderColor = if (isUser) Color(0xFF10B981) else Color(0xFF1E293B)
                    val bg = if (isUser) Color(0xFF132A26) else Color(0xFF131D31)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.r))
                            .background(bg)
                            .border(1.5.r, borderColor, RoundedCornerShape(12.r))
                            .padding(horizontal = 16.r, vertical = 10.r),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Rank badge
                        RText(
                            "#${player.rank}",
                            16,
                            FontWeight.Bold,
                            when (player.rank) {
                                1 -> Color(0xFFFBBF24)
                                2 -> Color(0xFF94A3B8)
                                3 -> Color(0xFFD97706)
                                else -> Color(0xFF64748B)
                            },
                            Modifier.width(42.r),
                        )

                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(36.r)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B)),
                            contentAlignment = Alignment.Center,
                        ) {
                            RText(player.avatar, 20, FontWeight.Bold, Color.White)
                        }

                        Spacer(Modifier.width(14.r))

                        // Player Name
                        RText(
                            player.name + if (isUser) " (YOU)" else "",
                            15,
                            FontWeight.Bold,
                            if (isUser) Color(0xFF34D399) else Color.White,
                            modifier = Modifier.weight(1f),
                        )

                        // Wins
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.r))
                                .background(Color(0xFF10B981).copy(alpha = 0.2f))
                                .padding(horizontal = 8.r, vertical = 4.r),
                        ) {
                            RText("${player.wins} WINS", 12, FontWeight.Bold, Color(0xFF34D399))
                        }

                        Spacer(Modifier.width(16.r))

                        // Coins
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(18.r)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B)),
                                contentAlignment = Alignment.Center,
                            ) {
                                RText("9", 11, FontWeight.Bold, Color.Black)
                            }
                            Spacer(Modifier.width(6.r))
                            RText("${player.coins}", 14, FontWeight.Bold, Color(0xFFFBBF24))
                        }
                    }
                }
            }
        }
    }
}
