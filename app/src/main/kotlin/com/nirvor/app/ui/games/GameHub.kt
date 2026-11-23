package com.nirvor.app.ui.games

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.games.GameId
import com.nirvor.app.games.GameProfile
import com.nirvor.app.games.ProfileData
import com.nirvor.app.games.Slot
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.tools.ToolScaffold

internal val CoinInk = Color(0xFF8A5A00)
internal val GemInk = Color(0xFF6D28D9)

internal fun gameBrush(game: GameId) = Brush.linearGradient(listOf(Color(game.colorA), Color(game.colorB)))

/** More → Game Hub: one card per game, each with its own level, coins and gems. */
@Composable
fun GameHubScreen(onBack: () -> Unit, onOpen: (GameId) -> Unit) {
    val context = LocalContext.current
    ToolScaffold("Game Hub", "দেশি খেলা, অফলাইনে", onBack) {
        GameId.entries.forEach { game ->
            val profile = if (game.ready) remember(game) { GameProfile.of(context, game) } else null
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.r))
                    .background(gameBrush(game))
                    .clickable {
                        if (game.ready) onOpen(game)
                        else Toast.makeText(context, "${game.title} পরের আপডেটে আসছে", Toast.LENGTH_SHORT).show()
                    }
                    .alpha(if (game.ready) 1f else 0.6f)
                    .padding(22.r),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(84.r).clip(RoundedCornerShape(24.r)).background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) { RText(game.emoji, 42, FontWeight.Normal, Color.White) }
                    Spacer(Modifier.width(18.r))
                    Column(Modifier.weight(1f)) {
                        RText(game.title, 28, FontWeight.Bold, Color.White)
                        Spacer(Modifier.height(3.r))
                        RText(game.sub, 17, FontWeight.Medium, Color.White.copy(alpha = 0.85f))
                    }
                    if (!game.ready) {
                        RText(
                            "শীঘ্রই",
                            16,
                            FontWeight.SemiBold,
                            Color.White,
                            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.25f)).padding(horizontal = 16.r, vertical = 7.r),
                        )
                    }
                }
                if (profile != null) {
                    Spacer(Modifier.height(16.r))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                        GlassPill("⭐ লেভেল ${bnNum(profile.data.level)}")
                        GlassPill("🪙 ${bnNum(profile.data.coins)}")
                        GlassPill("💎 ${bnNum(profile.data.gems)}")
                        if (profile.dailyReady()) GlassPill("🎁")
                    }
                }
            }
        }
    }
}

@Composable
internal fun GlassPill(text: String, modifier: Modifier = Modifier) {
    RText(
        text,
        17,
        FontWeight.SemiBold,
        Color.White,
        modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.2f)).padding(horizontal = 15.r, vertical = 7.r),
    )
}

/** Avatar, name, level bar, coins and gems of one game. */
@Composable
internal fun ProfileHeader(game: GameId, profile: GameProfile, name: String) {
    val d = profile.data
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.r)).background(gameBrush(game)).padding(22.r),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(92.r).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) { RText(profile.equipped(Slot.Avatar)?.emoji ?: "🙂", 46, FontWeight.Normal, Color.White) }
            Spacer(Modifier.width(18.r))
            Column(Modifier.weight(1f)) {
                RText(name.ifBlank { "খেলোয়াড়" }, 26, FontWeight.Bold, Color.White)
                Spacer(Modifier.height(8.r))
                Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                    GlassPill("🪙 ${bnNum(d.coins)}")
                    GlassPill("💎 ${bnNum(d.gems)}")
                }
            }
        }
        Spacer(Modifier.height(16.r))
        XpBar(d)
    }
}

@Composable
internal fun XpBar(d: ProfileData) {
    Row(Modifier.fillMaxWidth()) {
        RText("⭐ লেভেল ${bnNum(d.level)}", 17, FontWeight.SemiBold, Color.White, Modifier.weight(1f))
        RText("${bnNum(d.xpIn)} / ${bnNum(d.xpNeed)} XP", 16, FontWeight.Medium, Color.White.copy(alpha = 0.85f))
    }
    Spacer(Modifier.height(8.r))
    Box(Modifier.fillMaxWidth().height(14.r).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.22f))) {
        Box(
            Modifier
                .fillMaxWidth((d.xpIn.toFloat() / d.xpNeed).coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFFD54A)),
        )
    }
}
