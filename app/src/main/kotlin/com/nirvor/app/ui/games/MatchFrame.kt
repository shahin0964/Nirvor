package com.nirvor.app.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
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
import com.nirvor.app.games.BotLevel
import com.nirvor.app.games.GameProfile
import com.nirvor.app.games.GameSound
import com.nirvor.app.games.MatchEnd
import com.nirvor.app.games.Outcome
import com.nirvor.app.games.Reward
import com.nirvor.app.games.net.Session
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.PageHeader
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.TGreen
import kotlinx.coroutines.delay

/** Who the match is against. */
sealed interface Rival {
    /** The player number of the person holding the phone (two players share it when [Local]). */
    val me: Int

    data object Local : Rival {
        override val me = 1
    }

    class Bot(val level: BotLevel, override val me: Int = 1) : Rival

    class Online(val session: Session) : Rival {
        override val me get() = session.me
    }
}

/** True when the person holding the phone moves for player [turn]. */
internal fun Rival.mine(turn: Int) = this is Rival.Local || turn == me

/** Sounds for as long as a match is on screen. */
@Composable
internal fun rememberSound(profile: GameProfile): GameSound {
    val context = LocalContext.current
    val sound = remember { GameSound(context.applicationContext, profile.player) }
    DisposableEffect(sound) { onDispose { sound.release() } }
    return sound
}

private const val TURN_SECONDS = 90

/** What every match has around its board: player bars, leaving, online clock, result and reward. */
@Composable
internal fun MatchFrame(
    profile: GameProfile,
    rival: Rival,
    name: String,
    round: Int,
    turn: Int,
    turns: Int,
    /** Fewer turns than this give no reward. */
    minTurns: Int,
    result: MatchEnd?,
    /** Points of the person holding the phone, for statistics and events. */
    score: Int,
    note: String,
    badge: (Int) -> String,
    sound: GameSound,
    onForceEnd: (MatchEnd) -> Unit,
    onAgain: () -> Unit,
    onExit: () -> Unit,
    buttons: @Composable RowScope.() -> Unit = {},
    board: @Composable (Modifier) -> Unit,
) {
    val me = rival.me
    var leaving by remember(round) { mutableStateOf(false) }
    var reward by remember(round) { mutableStateOf<Reward?>(null) }
    var clock by remember { mutableIntStateOf(TURN_SECONDS) }
    val bot = (rival as? Rival.Bot)?.level
    val session = (rival as? Rival.Online)?.session
    val counted = rival !is Rival.Local

    fun leave() {
        if (result == null && counted && (session != null || turns >= 2)) profile.finish(Outcome.Loss, bot, score, true)
        onExit()
    }
    BackHandler { if (result != null || (turns < 2 && session == null)) leave() else leaving = true }

    if (session != null) {
        val early by session.end.collectAsState()
        SideEffect {
            session.rivalTurn = result == null && turn != me
            if (result != null) session.finish()
        }
        DisposableEffect(session) { onDispose { session.close() } }
        LaunchedEffect(early) {
            val e = early ?: return@LaunchedEffect
            if (result == null) {
                val won = e.winner == me
                val why = when (e.why) {
                    "resign" -> if (won) "বিপক্ষ খেলা ছেড়ে দিয়েছে" else "আপনি খেলা ছেড়েছেন"
                    "timeout" -> if (won) "বিপক্ষের সময় শেষ" else "আপনার সময় শেষ"
                    else -> if (won) "বিপক্ষের সংযোগ নেই" else "আপনার সংযোগ ছিল না"
                }
                onForceEnd(MatchEnd(e.winner, why))
            }
        }
        LaunchedEffect(turn, turns, result == null) {
            clock = TURN_SECONDS
            while (result == null && clock > 0) {
                delay(1000)
                clock--
            }
        }
    }

    LaunchedEffect(round, result) {
        val r = result ?: return@LaunchedEffect
        if (!counted) {
            sound.play(GameSound.S.Win)
        } else if (reward == null) {
            val outcome = if (r.winner == 0) Outcome.Draw else if (r.winner == me) Outcome.Win else Outcome.Loss
            sound.play(if (outcome == Outcome.Loss) GameSound.S.Lose else GameSound.S.Win)
            reward = profile.finish(outcome, bot, score, turns < minTurns)
        }
    }

    fun seat(p: Int): Pair<String, String> = when {
        p == me -> profile.player.avatarItem.emoji to if (counted) name else "খেলোয়াড় ১"
        bot != null -> "🤖" to "বট"
        session != null -> session.rival.avatar.ifBlank { "🙂" } to session.rival.name.ifBlank { "খেলোয়াড়" }
        else -> "🧑" to "খেলোয়াড় ২"
    }

    @Composable
    fun bar(p: Int) {
        val (emoji, label) = seat(p)
        val active = result == null && turn == p
        val steady = session?.steady?.collectAsState()?.value ?: true
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.r))
                .background(if (active) TGreen.copy(alpha = 0.16f) else Color.White)
                .padding(horizontal = 18.r, vertical = 11.r),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RText(emoji, 30, FontWeight.Normal, C.Ink)
            Spacer(Modifier.width(12.r))
            RText(label, 20, FontWeight.SemiBold, C.Ink, Modifier.weight(1f))
            val extra = when {
                !active -> ""
                session != null && !steady -> "📶 সংযোগ…"
                session != null -> "⏱ ${bnNum(clock)}"
                bot != null && p != me -> "ভাবছে…"
                else -> "চাল দিন"
            }
            if (extra.isNotEmpty()) {
                RText(extra, 16, FontWeight.Medium, if (session != null && clock < 15) C.Danger else C.Hint, Modifier.padding(end = 14.r))
            }
            RText(badge(p), 20, FontWeight.Bold, C.Ink)
        }
    }

    Column(Modifier.fillMaxSize().background(C.Sheet)) {
        val sub = if (bot != null) "বট · ${bot.bn}" else if (session != null) "অনলাইন ম্যাচ" else "দুজনের খেলা"
        PageHeader(profile.game.title, sub, onBack = { if (result != null || (turns < 2 && session == null)) leave() else leaving = true })
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 18.r, vertical = 6.r)) {
            bar(3 - me)
            board(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.r))
            Box(Modifier.fillMaxWidth().height(34.r), contentAlignment = Alignment.Center) {
                RText(note, 18, FontWeight.SemiBold, CoinInk, align = TextAlign.Center)
            }
            bar(me)
            Spacer(Modifier.height(10.r))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.r, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttons()
                SmallButton(if (profile.player.sound) "🔊" else "🔇", Color(profile.game.colorB)) { profile.player.switchSound() }
            }
            Spacer(Modifier.height(6.r))
        }
    }

    if (leaving && result == null) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            containerColor = Color.White,
            title = { RText("খেলা ছেড়ে দেবেন?", 24, FontWeight.Bold, C.Ink) },
            text = { RText(if (counted) "এই ম্যাচ হার হিসেবে গোনা হবে।" else "এই ম্যাচ সেভ থাকবে না।", 18, FontWeight.Normal, C.StatLabel, maxLines = 2) },
            confirmButton = {
                TextButton(onClick = {
                    leaving = false
                    leave()
                }) { RText("ছেড়ে দিন", 19, FontWeight.SemiBold, C.Danger) }
            },
            dismissButton = { TextButton(onClick = { leaving = false }) { RText("খেলব", 19, FontWeight.SemiBold, TGreen) } },
        )
    }

    result?.let { end ->
        val title = when {
            end.winner == 0 -> "🤝 ড্র"
            !counted -> "🏆 খেলোয়াড় ${bnNum(end.winner)} জিতেছে"
            end.winner == me -> "🏆 আপনি জিতেছেন!"
            else -> "😔 আপনি হেরেছেন"
        }
        AlertDialog(
            onDismissRequest = { },
            containerColor = Color.White,
            title = { RText(title, 27, FontWeight.Bold, C.Ink) },
            text = {
                Column {
                    RText(end.reason, 18, FontWeight.Normal, C.StatLabel, maxLines = 3, lineHeight = 26)
                    reward?.let { w ->
                        Spacer(Modifier.height(14.r))
                        RText(
                            "⭐ +${bnNum(w.xp)}  🪙 +${bnNum(w.coins)}" + if (w.gems > 0) "  💎 +${bnNum(w.gems)}" else "",
                            21, FontWeight.Bold, TGreen,
                        )
                        if (w.featured) RText("🎉 আজকের খেলা: দ্বিগুণ কয়েন", 17, FontWeight.Medium, CoinInk, Modifier.padding(top = 8.r))
                        if (w.firstWin) RText("🌞 দিনের প্রথম জয়ের বোনাসসহ", 17, FontWeight.Medium, GemInk, Modifier.padding(top = 8.r))
                        if (w.levelUps > 0) RText("⭐ লেভেল ${bnNum(profile.data.level)} হয়েছে!", 20, FontWeight.Bold, CoinInk, Modifier.padding(top = 8.r))
                        if (w.xp == 0) RText("ম্যাচ খুব ছোট ছিল, তাই পুরস্কার নেই", 16, FontWeight.Normal, C.Hint, Modifier.padding(top = 8.r))
                    }
                }
            },
            confirmButton = {
                if (session == null) TextButton(onClick = onAgain) { RText("আবার খেলুন", 19, FontWeight.SemiBold, TGreen) }
                else TextButton(onClick = onExit) { RText("ঠিক আছে", 19, FontWeight.SemiBold, TGreen) }
            },
            dismissButton = { if (session == null) TextButton(onClick = onExit) { RText("বের হন", 19, FontWeight.Medium, C.Hint) } },
        )
    }
}
