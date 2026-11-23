package com.nirvor.app.ui.games

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.games.EventLog
import com.nirvor.app.games.Events
import com.nirvor.app.games.GameId
import com.nirvor.app.games.Mission
import com.nirvor.app.games.PlayerProfile
import com.nirvor.app.games.Stores
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.StatLine
import com.nirvor.app.ui.tools.TGreen
import java.time.LocalDate
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

/** More → Game Hub: the games, the events, and the one profile shared by all games. */
@Composable
fun GameHubScreen(name: String, onBack: () -> Unit, onOpen: (GameId) -> Unit) {
    val context = LocalContext.current
    val player = remember { PlayerProfile.of(context) }
    val profiles = remember { GameId.entries.associateWith { GameProfile.of(context, it) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val day = LocalDate.now().toEpochDay()
    val waiting = player.events.waiting(day)
    BackHandler(enabled = tab != 0) { tab = 0 }
    ToolScaffold("Game Hub", "দেশি খেলা, অফলাইন ও অনলাইন", onBack) {
        GameTabs(listOf("🎮 গেম", "🎉 ইভেন্ট" + if (waiting > 0) " (${bnNum(waiting)})" else "", "👤 প্রোফাইল"), tab, Color(0xFFB93815)) { tab = it }
        when (tab) {
            0 -> GameId.entries.forEach { game -> HubCard(game, profiles.getValue(game), Events.featured(day) == game) { onOpen(game) } }
            1 -> EventsTab(player, profiles, day)
            else -> SharedProfileTab(player, profiles, name)
        }
    }
}

@Composable
private fun HubCard(game: GameId, profile: GameProfile, featured: Boolean, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.r)).background(gameBrush(game)).clickable(onClick = onOpen).padding(22.r)) {
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
            if (featured) GlassPill("🎉 ×২ কয়েন")
        }
        Spacer(Modifier.height(16.r))
        Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
            GlassPill("⭐ লেভেল ${bnNum(profile.data.level)}")
            GlassPill("🪙 ${bnNum(profile.data.coins)}")
            GlassPill("💎 ${bnNum(profile.data.gems)}")
            if (profile.dailyReady()) GlassPill("🎁")
        }
    }
}

@Composable
private fun EventsTab(player: PlayerProfile, profiles: Map<GameId, GameProfile>, day: Long) {
    val log = player.events
    val game = Events.featured(day)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.r)).background(gameBrush(game)).padding(22.r)) {
        RText("☀️ আজকের ইভেন্ট: ${Events.dailyName(day)}", 24, FontWeight.Bold, Color.White)
        Spacer(Modifier.height(6.r))
        RText("আজকের খেলা ${game.emoji} ${game.title}: দ্বিগুণ কয়েন", 18, FontWeight.Medium, Color.White.copy(alpha = 0.9f))
    }
    Events.daily(day).forEach { MissionCard(it, log, profiles.getValue(it.game)) }

    val week = Events.weekGame(day)
    val left = Events.weekEnd(day) - day + 1
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.r)).background(gameBrush(week)).padding(22.r)) {
        RText("🏆 সাপ্তাহিক: ${Events.weeklyName(day)}", 24, FontWeight.Bold, Color.White)
        Spacer(Modifier.height(6.r))
        RText("বাকি ${bnNum(left.toInt())} দিন · চারটা পূরণ করলে ${week.title}-এর সিন্দুক", 17, FontWeight.Medium, Color.White.copy(alpha = 0.9f), maxLines = 2)
    }
    Events.weekly(day).forEach { MissionCard(it, log, profiles.getValue(it.game)) }
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText("🎁", 36, FontWeight.Normal, C.Ink)
            Spacer(Modifier.width(14.r))
            Column(Modifier.weight(1f)) {
                RText("সাপ্তাহিক সিন্দুক", 20, FontWeight.SemiBold, C.Ink)
                RText("🪙 ${bnNum(Events.CHEST_COINS)}  💎 ${bnNum(Events.CHEST_GEMS)}", 17, FontWeight.Medium, C.StatLabel)
            }
            val taken = log.taken(Events.chestId(day))
            SmallButton(if (taken) "✔" else "নিন", TGreen, log.chestReady(day)) { log.takeChest(profiles.getValue(week)) }
        }
    }
}

@Composable
private fun MissionCard(m: Mission, log: EventLog, profile: GameProfile) {
    val now = log.of(m)
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText(m.game.emoji, 32, FontWeight.Normal, C.Ink)
            Spacer(Modifier.width(14.r))
            Column(Modifier.weight(1f)) {
                RText(m.title, 19, FontWeight.SemiBold, C.Ink, maxLines = 2)
                RText("${m.game.title} · ⭐${bnNum(m.xp)} 🪙${bnNum(m.coins)} 💎${bnNum(m.gems)}", 15, FontWeight.Medium, C.StatLabelBn)
            }
            SmallButton(if (log.taken(m.id)) "✔" else if (log.canTake(m)) "নিন" else "${bnNum(now)}/${bnNum(m.target)}", TGreen, log.canTake(m)) { log.take(m, profile) }
        }
        Spacer(Modifier.height(10.r))
        Box(Modifier.fillMaxWidth().height(9.r).clip(RoundedCornerShape(50)).background(C.CalBg)) {
            Box(Modifier.fillMaxWidth(now.toFloat() / m.target).fillMaxHeight().clip(RoundedCornerShape(50)).background(TGreen))
        }
    }
}

@Composable
private fun SharedProfileTab(player: PlayerProfile, profiles: Map<GameId, GameProfile>, name: String) {
    var nick by remember { mutableStateOf(player.nick.ifBlank { name }) }
    val all = profiles.values.map { it.data }
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText(player.avatarItem.emoji, 60, FontWeight.Normal, C.Ink)
            Spacer(Modifier.width(16.r))
            Column {
                RText(player.nick.ifBlank { name }.ifBlank { "খেলোয়াড়" }, 25, FontWeight.Bold, C.Ink)
                RText("মোট লেভেল ${bnNum(all.sumOf { it.level })} · জয় ${bnNum(all.sumOf { it.wins })} / ${bnNum(all.sumOf { it.played })}", 17, FontWeight.Medium, C.StatLabelBn)
            }
        }
        Spacer(Modifier.height(16.r))
        RText("ডাকনাম (অনলাইনে অন্যরা দেখবে)", 17, FontWeight.Medium, C.StatLabel)
        Spacer(Modifier.height(8.r))
        OutlinedTextField(nick, { nick = it.take(18) }, Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.r))
        SmallButton("সেভ", TGreen, PlayerProfile.cleanNick(nick).let { it.isNotEmpty() && it != player.nick }) { player.rename(nick) }
    }
    GameCard {
        RText("অ্যাভাটার", 21, FontWeight.Bold, C.Ink)
        RText("নতুন অ্যাভাটার যেকোনো গেমের স্টোরে পাবেন", 15, FontWeight.Normal, C.StatLabelBn)
        Spacer(Modifier.height(10.r))
        Stores.avatars.filter { player.ownsAvatar(it.id) }.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                row.forEach { a ->
                    Box(
                        Modifier.size(84.r).clip(CircleShape)
                            .background(if (player.avatarItem.id == a.id) TGreen.copy(alpha = 0.2f) else C.CalBg)
                            .clickable { player.adopt(emptyList(), a.id) },
                        contentAlignment = Alignment.Center,
                    ) { RText(a.emoji, 40, FontWeight.Normal, C.Ink) }
                }
            }
        }
    }
    profiles.forEach { (game, p) ->
        val d = p.data
        GameCard {
            RText("${game.emoji} ${game.title}", 21, FontWeight.Bold, C.Ink)
            StatLine("লেভেল / XP", "${bnNum(d.level)} / ${bnNum(d.xp)}")
            StatLine("🪙 কয়েন / 💎 জেম", "${bnNum(d.coins)} / ${bnNum(d.gems)}")
            StatLine("জয় / হার / ড্র", "${bnNum(d.wins)} / ${bnNum(d.losses)} / ${bnNum(d.draws)}")
        }
    }
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText("🔊 গেমের শব্দ", 20, FontWeight.SemiBold, C.Ink, Modifier.weight(1f))
            SmallButton(if (player.sound) "চালু" else "বন্ধ", if (player.sound) TGreen else C.Hint) { player.switchSound() }
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
