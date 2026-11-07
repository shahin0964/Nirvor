package com.nirvor.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.SectionLabel
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C

@Composable
fun GamesHubScreen(
    bottomPadding: Dp,
    onBack: () -> Unit,
    onOpenGame: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("সব") }
    val categories = listOf("সব", "পাজল", "আর্কেড", "স্ট্র্যাটেজি", "ব্রেইন", "লজিক")

    val filteredGames = GameInventory.games.filter {
        selectedCategory == "সব" || it.category.equals(selectedCategory, ignoreCase = true)
    }

    PageScaffold(
        title = "Game Hub",
        subtitle = "৫টি অফলাইন গেম · ১০০% ফাংশনাল ও স্মুথ",
        onBack = onBack,
        bottomPadding = bottomPadding,
    ) {
        // Hero Inventory Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.r))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFE11D48), Color(0xFFBE123C), Color(0xFF881337))
                        )
                    )
                    .padding(horizontal = 22.r, vertical = 20.r),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.r)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(Glyph.Gamepad, Color.White, Modifier.size(28.r), strokeWidth = 2f)
                        }
                        Spacer(Modifier.width(14.r))
                        Column {
                            RText("অফলাইন গেম ইনভেন্টরি", 22, FontWeight.Bold, Color.White)
                            RText("ইন্টারনেট ছাড়াই সম্পূর্ণ কার্যকর", 15, FontWeight.Normal, Color.White.copy(alpha = 0.85f))
                        }
                    }
                    Spacer(Modifier.height(14.r))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        HeroStatBadge("🎮 ৫টি গেম", "পূর্ণ ফাংশনাল")
                        HeroStatBadge("⚡ ৬০-১২০ FPS", "সিস্টেম সিঙ্ক")
                        HeroStatBadge("🔋 ব্যাটারি ফ্রেন্ডলি", "জিরো ল্যাগ")
                    }
                }
            }
        }

        // Category Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.r),
            ) {
                categories.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.r))
                            .background(if (isSelected) Color(0xFFE11D48) else Color.White)
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 14.r, vertical = 8.r),
                        contentAlignment = Alignment.Center,
                    ) {
                        RText(
                            cat,
                            14,
                            if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            if (isSelected) Color.White else C.Ink,
                        )
                    }
                }
            }
        }

        item { SectionLabel("গেম তালিকা") }

        if (filteredGames.isEmpty()) {
            item { EmptyText("এই ক্যাটাগরিতে কোনো গেম নেই।") }
        } else {
            items(filteredGames.size) { index ->
                val game = filteredGames[index]
                GameInventoryCard(
                    game = game,
                    onPlay = { onOpenGame(game.id) },
                )
            }
        }
    }
}

@Composable
private fun HeroStatBadge(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.r))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 10.r, vertical = 6.r),
    ) {
        RText(title, 13, FontWeight.Bold, Color.White)
        RText(subtitle, 11, FontWeight.Normal, Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun GameInventoryCard(
    game: GameInfo,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val highScore = GameStorage.getHighScore(context, game.id)
    val bestTime = GameStorage.getBestTime(context, game.id)

    ListCard(onClick = onPlay) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Gradient Icon Box
            Box(
                modifier = Modifier
                    .size(64.r)
                    .clip(RoundedCornerShape(18.r))
                    .background(Brush.linearGradient(game.gradient)),
                contentAlignment = Alignment.Center,
            ) {
                RText(game.emoji, 34, FontWeight.Normal, Color.White)
            }

            Spacer(Modifier.width(16.r))

            // Game Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RText(game.title, 22, FontWeight.Bold, C.Ink)
                    if (game.badge != null) {
                        Spacer(Modifier.width(8.r))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.r))
                                .background(game.accent.copy(alpha = 0.14f))
                                .padding(horizontal = 8.r, vertical = 2.r),
                        ) {
                            RText(game.badge, 11, FontWeight.Bold, game.accent)
                        }
                    }
                }

                Spacer(Modifier.height(3.r))
                RText(game.bn, 16, FontWeight.SemiBold, game.accent)
                Spacer(Modifier.height(4.r))
                RText(game.description, 13, FontWeight.Normal, C.Hint, maxLines = 2)

                // High score badge if available
                if (highScore > 0) {
                    Spacer(Modifier.height(6.r))
                    RText("🏆 সেরা স্কোর: $highScore", 12, FontWeight.SemiBold, Color(0xFFD97706))
                } else if (bestTime < 9999) {
                    Spacer(Modifier.height(6.r))
                    RText("⏱️ সেরা রেকর্ড: ${bestTime}s", 12, FontWeight.SemiBold, Color(0xFF059669))
                }
            }

            Spacer(Modifier.width(10.r))

            // Play Button
            Box(
                modifier = Modifier
                    .size(44.r)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Play, game.accent, Modifier.size(20.r), strokeWidth = 2f)
            }
        }
    }
}
