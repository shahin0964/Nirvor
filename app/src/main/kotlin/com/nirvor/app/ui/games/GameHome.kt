package com.nirvor.app.ui.games

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.games.BuyResult
import com.nirvor.app.games.Counter
import com.nirvor.app.games.Economy
import com.nirvor.app.games.GameId
import com.nirvor.app.games.GameProfile
import com.nirvor.app.games.Slot
import com.nirvor.app.games.StoreItem
import com.nirvor.app.games.Stores
import com.nirvor.app.games.guti.GutiLevel
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.StatLine
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.ToolScaffold

/** One game: its খেলা / স্টোর / প্রোফাইল tabs, and the match itself. */
@Composable
fun GameHost(game: GameId, name: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = remember(game) { GameProfile.of(context, game) }
    // "" = not playing, "two" = two players, else the bot's level
    var match by rememberSaveable(game) { mutableStateOf("") }
    var tab by rememberSaveable(game) { mutableIntStateOf(0) }

    if (match.isNotEmpty()) {
        when (game) {
            GameId.SholoGuti -> SholoGutiScreen(
                profile = profile,
                name = name.ifBlank { "আপনি" },
                level = GutiLevel.entries.firstOrNull { it.name == match },
                onExit = { match = "" },
            )
            else -> match = ""
        }
        return
    }

    BackHandler(enabled = tab != 0) { tab = 0 }
    ToolScaffold(game.title, game.sub, onBack) {
        ProfileHeader(game, profile, name)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.r)).background(Color.White).padding(6.r),
            horizontalArrangement = Arrangement.spacedBy(6.r),
        ) {
            listOf("🎮 খেলা", "🛒 স্টোর", "👤 প্রোফাইল").forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.r))
                        .background(if (tab == i) Color(game.colorB) else Color.Transparent)
                        .clickable { tab = i }
                        .padding(vertical = 14.r),
                    contentAlignment = Alignment.Center,
                ) { RText(label, 18, FontWeight.SemiBold, if (tab == i) Color.White else C.StatLabel) }
            }
        }
        when (tab) {
            0 -> PlayTab(profile) { match = it }
            1 -> StoreTab(game, profile)
            else -> ProfileTab(profile)
        }
    }
}

@Composable
internal fun GameCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.r))
            .background(Color.White)
            .border(1.r, C.TodayBorder, RoundedCornerShape(22.r))
            .padding(20.r),
        content = content,
    )
}

// ---------------------------------------------------------------- play

/** XP, coins and gems for beating each bot level. */
internal fun winPrize(level: GutiLevel): Triple<Int, Int, Int> = when (level) {
    GutiLevel.Easy -> Triple(20, 20, 0)
    GutiLevel.Medium -> Triple(40, 40, 0)
    GutiLevel.Hard -> Triple(80, 80, 1)
}

@Composable
private fun PlayTab(profile: GameProfile, onPlay: (String) -> Unit) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(false) }

    val ready = profile.dailyReady()
    val number = profile.dailyNumber()
    val (dCoins, dGems) = Economy.daily(number)
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RText("🎁", 34, FontWeight.Normal, C.Ink)
            Spacer(Modifier.width(14.r))
            Column(Modifier.weight(1f)) {
                RText("দৈনিক পুরস্কার · দিন ${bnNum((number - 1) % 7 + 1)}", 20, FontWeight.SemiBold, C.Ink)
                RText("🪙 ${bnNum(dCoins)}" + if (dGems > 0) "   💎 ${bnNum(dGems)}" else "", 17, FontWeight.Medium, C.StatLabel)
            }
            SmallButton(if (ready) "নিন" else "কাল আসুন", TGreen, ready) {
                profile.takeDaily()?.let { (c, g) ->
                    Toast.makeText(context, "🪙 +${bnNum(c)}" + if (g > 0) "  💎 +${bnNum(g)}" else "", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    GameCard {
        RText("🤖 বটের সাথে", 21, FontWeight.Bold, C.Ink)
        Spacer(Modifier.height(12.r))
        GutiLevel.entries.forEach { level ->
            val (xp, coins, gems) = winPrize(level)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.r)
                    .clip(RoundedCornerShape(18.r))
                    .background(C.CalBg)
                    .clickable { onPlay(level.name) }
                    .padding(horizontal = 18.r, vertical = 16.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RText(level.emoji, 28, FontWeight.Normal, C.Ink)
                Spacer(Modifier.width(14.r))
                Column(Modifier.weight(1f)) {
                    RText(level.bn, 21, FontWeight.SemiBold, C.Ink)
                    RText(
                        "জিতলে ${bnNum(xp)} XP · 🪙 ${bnNum(coins)}" + if (gems > 0) " · 💎 ${bnNum(gems)}" else "",
                        16,
                        FontWeight.Medium,
                        C.StatLabelBn,
                    )
                }
                RText("▶", 22, FontWeight.Bold, TGreen)
            }
        }
    }

    RoundButton("👥 এক ফোনে দুজন খেলুন", Color(profile.game.colorB), Modifier.fillMaxWidth()) { onPlay("two") }
    RoundButton("📜 খেলার নিয়ম", Color(profile.game.colorB), Modifier.fillMaxWidth(), filled = false) { rules = true }

    if (rules) {
        AlertDialog(
            onDismissRequest = { rules = false },
            containerColor = Color.White,
            title = { RText("${profile.game.title}: নিয়ম", 24, FontWeight.Bold, C.Ink) },
            text = { RText(GUTI_RULES, 18, FontWeight.Normal, C.Body, maxLines = 40, lineHeight = 29) },
            confirmButton = { TextButton(onClick = { rules = false }) { RText("বুঝেছি", 19, FontWeight.SemiBold, TGreen) } },
        )
    }
}

private const val GUTI_RULES =
    "• দুজনের ১৬টা করে গুটি। নিচের গুটি আপনার।\n" +
        "• দাগ ধরে এক ঘর পাশের খালি বিন্দুতে চাল দিন।\n" +
        "• পাশে বিপক্ষের গুটি আর তার ঠিক পরের ঘর সোজা দাগে খালি থাকলে লাফ দিয়ে গুটি খান।\n" +
        "• খাওয়ার পর একই গুটি দিয়ে আরও খাওয়া গেলে পরপর খেতে পারেন, না চাইলে \"চাল শেষ\" চাপুন।\n" +
        "• খাওয়া বাধ্যতামূলক না।\n" +
        "• বিপক্ষের সব গুটি খেলে, বা তার কোনো চাল না থাকলে আপনি জিতবেন।\n" +
        "• টানা ৬০ চালে কেউ গুটি না খেলে যার গুটি বেশি সে জেতে, সমান হলে ড্র।\n" +
        "• দুজনের খেলায় XP বা কয়েন নেই, শুধু মজা।"

@Composable
internal fun SmallButton(label: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    RText(
        label,
        17,
        FontWeight.SemiBold,
        if (enabled) Color.White else C.Hint,
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) color else C.CalBg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.r, vertical = 11.r),
        align = TextAlign.Center,
    )
}

// ---------------------------------------------------------------- store

@Composable
private fun StoreTab(game: GameId, profile: GameProfile) {
    val context = LocalContext.current
    var asking by remember { mutableStateOf<StoreItem?>(null) }
    val items = remember(game) { Stores.of(game) }
    Slot.entries.forEach { slot ->
        val list = items.filter { it.slot == slot }
        if (list.isEmpty()) return@forEach
        RText(slot.title, 22, FontWeight.Bold, C.Ink, Modifier.padding(top = 6.r))
        list.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.r)) {
                row.forEach { item ->
                    StoreCell(item, profile, Modifier.weight(1f)) {
                        if (item.keepable && profile.owns(item)) profile.equip(item) else asking = item
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    asking?.let { item ->
        AlertDialog(
            onDismissRequest = { asking = null },
            containerColor = Color.White,
            title = { RText("${item.name} কিনবেন?", 23, FontWeight.Bold, C.Ink) },
            text = { RText("দাম ${price(item)}", 19, FontWeight.Medium, C.StatLabel) },
            confirmButton = {
                TextButton(onClick = {
                    asking = null
                    val msg = when (profile.buy(item)) {
                        BuyResult.Ok -> "✅ ${item.name} কেনা হয়েছে"
                        BuyResult.NoMoney -> if (item.gems) "জেম কম আছে" else "কয়েন কম আছে"
                        BuyResult.LowLevel -> "লেভেল ${bnNum(item.level)} লাগবে"
                        BuyResult.Owned -> "আগেই কেনা আছে"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }) { RText("কিনুন", 19, FontWeight.SemiBold, TGreen) }
            },
            dismissButton = { TextButton(onClick = { asking = null }) { RText("না", 19, FontWeight.Medium, C.Hint) } },
        )
    }
}

private fun price(item: StoreItem) = (if (item.gems) "💎 " else "🪙 ") + bnNum(item.price)

@Composable
private fun StoreCell(item: StoreItem, profile: GameProfile, modifier: Modifier, onTap: () -> Unit) {
    val d = profile.data
    val owned = item.keepable && profile.owns(item)
    val used = owned && profile.equipped(item.slot)?.id == item.id
    val locked = !owned && d.level < item.level
    Column(
        modifier
            .clip(RoundedCornerShape(22.r))
            .background(Color.White)
            .border(if (used) 2.5.r else 1.r, if (used) TGreen else C.TodayBorder, RoundedCornerShape(22.r))
            .clickable(enabled = !used && !locked, onClick = onTap)
            .padding(16.r),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemPreview(item)
        Spacer(Modifier.height(10.r))
        RText(item.name, 19, FontWeight.SemiBold, C.Ink)
        if (item.note.isNotEmpty()) RText(item.note, 15, FontWeight.Normal, C.StatLabelBn, maxLines = 2, align = TextAlign.Center)
        if (item.slot == Slot.Power) RText("আছে ${bnNum(d.counter(item.counter))}", 15, FontWeight.Medium, C.Hint)
        Spacer(Modifier.height(10.r))
        val label = when {
            used -> "✔ চলছে"
            owned -> "ব্যবহার করুন"
            locked -> "🔒 লেভেল ${bnNum(item.level)}"
            else -> price(item)
        }
        RText(
            label,
            17,
            FontWeight.SemiBold,
            if (used) TGreen else if (locked) C.Hint else if (owned) C.Ink else if (item.gems) GemInk else CoinInk,
            Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        used -> TGreen.copy(alpha = 0.12f)
                        locked || owned -> C.CalBg
                        item.gems -> Color(0xFFEDE4FF)
                        else -> Color(0xFFFFF1CC)
                    }
                )
                .padding(horizontal = 18.r, vertical = 8.r),
        )
    }
}

@Composable
private fun ItemPreview(item: StoreItem) {
    val colors = item.colors.map { Color(it) }
    when {
        item.slot == Slot.Board && colors.size >= 3 -> Box(
            Modifier.fillMaxWidth().height(92.r).clip(RoundedCornerShape(16.r)).background(colors[0])
                .border(6.r, colors[1], RoundedCornerShape(16.r)),
            contentAlignment = Alignment.Center,
        ) { RText("╳", 44, FontWeight.Bold, colors[2]) }
        item.slot == Slot.Piece && colors.size >= 2 -> Row(
            Modifier.height(92.r),
            horizontalArrangement = Arrangement.spacedBy(14.r),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            colors.take(2).forEach { c ->
                Box(
                    Modifier.size(58.r).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(lighter(c), c)))
                        .border(2.r, Color.Black.copy(alpha = 0.25f), CircleShape),
                )
            }
        }
        else -> Box(Modifier.height(92.r), contentAlignment = Alignment.Center) { RText(item.emoji, 54, FontWeight.Normal, C.Ink) }
    }
}

internal fun lighter(c: Color) = androidx.compose.ui.graphics.lerp(c, Color.White, 0.45f)

// ---------------------------------------------------------------- profile

@Composable
private fun ProfileTab(profile: GameProfile) {
    val d = profile.data
    GameCard {
        RText("📊 হিসাব", 21, FontWeight.Bold, C.Ink)
        Spacer(Modifier.height(8.r))
        StatLine("মোট ম্যাচ (বটের সাথে)", bnNum(d.played))
        StatLine("জয়", bnNum(d.wins), TGreen)
        StatLine("হার", bnNum(d.losses), C.Danger)
        StatLine("ড্র", bnNum(d.draws))
        StatLine("জয়ের হার", if (d.played == 0) "—" else bnNum(d.wins * 100 / d.played) + "%")
        StatLine("টানা জয় (এখন / সেরা)", "${bnNum(d.streak)} / ${bnNum(d.bestStreak)}")
        StatLine("মোট গুটি খেয়েছেন", bnNum(d.captured))
        StatLine("মোট XP", bnNum(d.xp))
    }
    GameCard {
        RText("🎒 আপনার পাওয়ার", 21, FontWeight.Bold, C.Ink)
        Spacer(Modifier.height(8.r))
        StatLine("↩️ চাল ফেরত", bnNum(d.counter(Counter.UNDO)))
        StatLine("💡 পরামর্শ", bnNum(d.counter(Counter.HINT)))
        StatLine("⚡ ডাবল XP ম্যাচ", bnNum(d.counter(Counter.DOUBLE_XP)))
    }
    GameCard {
        RText("💎 জেম কীভাবে পাবেন", 21, FontWeight.Bold, C.Ink)
        Spacer(Modifier.height(8.r))
        StatLine("লেভেল বাড়লে", "💎 ${bnNum(Economy.LEVEL_GEMS)} + 🪙 ${bnNum(Economy.LEVEL_COINS)}")
        StatLine("দিনের প্রথম জয়", "💎 ${bnNum(Economy.FIRST_WIN_GEMS)}")
        StatLine("কঠিন বটকে হারালে", "💎 ১")
        StatLine("৭ দিন পুরস্কার নিলে", "💎 ১৫")
    }
}
