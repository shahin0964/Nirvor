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
import com.nirvor.app.games.BotLevel
import com.nirvor.app.games.BuyResult
import com.nirvor.app.games.Counter
import com.nirvor.app.games.Economy
import com.nirvor.app.games.GameId
import com.nirvor.app.games.GameProfile
import com.nirvor.app.games.Slot
import com.nirvor.app.games.StoreItem
import com.nirvor.app.games.Stores
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.RoundButton
import com.nirvor.app.ui.tools.StatLine
import com.nirvor.app.ui.tools.TGreen
import com.nirvor.app.ui.tools.ToolScaffold

/** One game: its খেলা / স্টোর / হিসাব tabs, the online lobby, and the match itself. */
@Composable
fun GameHost(game: GameId, name: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = remember(game) { GameProfile.of(context, game) }
    var rival by remember(game) { mutableStateOf<Rival?>(null) }
    var lobby by remember(game) { mutableStateOf(false) }
    var tab by rememberSaveable(game) { mutableIntStateOf(0) }
    val shown = profile.player.nick.ifBlank { name }.ifBlank { "আপনি" }

    rival?.let { r ->
        val exit = { rival = null }
        when (game) {
            GameId.SholoGuti -> SholoGutiScreen(profile, shown, r, exit)
            GameId.Carrom -> CarromScreen(profile, shown, r, exit)
            GameId.KolomFight -> KolomScreen(profile, shown, r, exit)
            GameId.BaghBondi -> BaghScreen(profile, shown, r, exit)
        }
        return
    }
    if (lobby) {
        OnlineLobby(profile, onBack = { lobby = false }) {
            lobby = false
            rival = Rival.Online(it)
        }
        return
    }

    BackHandler(enabled = tab != 0) { tab = 0 }
    ToolScaffold(game.title, game.sub, onBack) {
        ProfileHeader(game, profile, shown)
        GameTabs(listOf("🎮 খেলা", "🛒 স্টোর", "📊 হিসাব"), tab, Color(game.colorB)) { tab = it }
        when (tab) {
            0 -> PlayTab(profile, onOnline = { lobby = true }) { rival = it }
            1 -> StoreTab(game, profile)
            else -> ProfileTab(profile)
        }
    }
}

@Composable
internal fun GameTabs(labels: List<String>, selected: Int, color: Color, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.r)).background(Color.White).padding(6.r),
        horizontalArrangement = Arrangement.spacedBy(6.r),
    ) {
        labels.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.r))
                    .background(if (selected == i) color else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 14.r),
                contentAlignment = Alignment.Center,
            ) { RText(label, 18, FontWeight.SemiBold, if (selected == i) Color.White else C.StatLabel) }
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

@Composable
private fun PlayTab(profile: GameProfile, onOnline: () -> Unit, onPlay: (Rival) -> Unit) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(false) }
    // বাঘবন্দি: which side the player takes against the bot
    var side by rememberSaveable { mutableIntStateOf(1) }

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
        if (profile.game == GameId.BaghBondi) {
            Row(Modifier.padding(bottom = 8.r), horizontalArrangement = Arrangement.spacedBy(10.r)) {
                ChoiceChip("🐐 ছাগল হব", side == 1) { side = 1 }
                ChoiceChip("🐯 বাঘ হব", side == 2) { side = 2 }
            }
        }
        BotLevel.entries.forEach { level ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.r)
                    .clip(RoundedCornerShape(18.r))
                    .background(C.CalBg)
                    .clickable { onPlay(Rival.Bot(level, if (profile.game == GameId.BaghBondi) side else 1)) }
                    .padding(horizontal = 18.r, vertical = 16.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RText(level.emoji, 28, FontWeight.Normal, C.Ink)
                Spacer(Modifier.width(14.r))
                Column(Modifier.weight(1f)) {
                    RText(level.bn, 21, FontWeight.SemiBold, C.Ink)
                    RText(
                        "জিতলে ${bnNum(level.xp)} XP · 🪙 ${bnNum(level.coins)}" + if (level.gems > 0) " · 💎 ${bnNum(level.gems)}" else "",
                        16,
                        FontWeight.Medium,
                        C.StatLabelBn,
                    )
                }
                RText("▶", 22, FontWeight.Bold, TGreen)
            }
        }
    }

    RoundButton("🌐 অনলাইনে খেলুন", TGreen, Modifier.fillMaxWidth()) { onOnline() }
    RoundButton("👥 এক ফোনে দুজন খেলুন", Color(profile.game.colorB), Modifier.fillMaxWidth()) { onPlay(Rival.Local) }
    RoundButton("📜 খেলার নিয়ম", Color(profile.game.colorB), Modifier.fillMaxWidth(), filled = false) { rules = true }

    if (rules) {
        AlertDialog(
            onDismissRequest = { rules = false },
            containerColor = Color.White,
            title = { RText("${profile.game.title}: নিয়ম", 24, FontWeight.Bold, C.Ink) },
            text = { RText(rulesOf(profile.game), 18, FontWeight.Normal, C.Body, maxLines = 60, lineHeight = 29) },
            confirmButton = { TextButton(onClick = { rules = false }) { RText("বুঝেছি", 19, FontWeight.SemiBold, TGreen) } },
        )
    }
}

private fun rulesOf(game: GameId): String = when (game) {
    GameId.SholoGuti ->
        "• দুজনের ১৬টা করে গুটি।\n• দাগ ধরে এক ঘর পাশের খালি বিন্দুতে চাল দিন।\n• পাশে বিপক্ষের গুটি আর তার পরের ঘর সোজা দাগে খালি থাকলে লাফ দিয়ে খান।\n• পরপর খাওয়া যায়, না চাইলে \"চাল শেষ\" চাপুন। খাওয়া বাধ্যতামূলক না।\n• সব গুটি খেলে বা বিপক্ষের চাল না থাকলে জয়।\n• টানা ৬০ চালে কেউ না খেলে গুটি গুনে ফল।"
    GameId.Carrom ->
        "• খেলোয়াড় ১ সাদা, খেলোয়াড় ২ কালো গুটি।\n• নিচের স্লাইডারে স্ট্রাইকার সরান। বোর্ডে আঙুল পেছনে টেনে ছাড়ুন, যত টানবেন তত জোর।\n• নিজের গুটি ফেললে আবার মারবেন।\n• লাল রানি ফেলতে আগে নিজের একটা গুটি ফেলতে হবে, আর রানির পর সাথে সাথে নিজের একটা গুটি ফেলে কভার করতে হবে।\n• স্ট্রাইকার পকেটে গেলে ফাউল: একটা গুটি ফেরত, পালা শেষ।\n• রানি কভার না হলে শেষ গুটি ফেলা যায় না।\n• আগে সব গুটি ফেললে জয়।"
    GameId.KolomFight ->
        "• নিজের কলমে আঙুল রেখে পেছনে টেনে ছাড়ুন। যেখানে ধরবেন সেখানে টোকা লাগবে, মাথায় ধরলে কলম ঘুরবে।\n• বিপক্ষের কলম টেবিল থেকে ফেললে রাউন্ড জয়।\n• নিজের কলম পড়লে রাউন্ড হার, দুটো পড়লেও।\n• আগে ৩ রাউন্ড জিতলে ম্যাচ জয়।"
    GameId.BaghBondi ->
        "• ৪টা বাঘ, ২০টা ছাগল। ছাগল আগে চাল দেয়।\n• প্রথমে ছাগল একটা একটা করে খালি ঘরে বসে। ২০টা বসার পর ছাগল চলতে পারে।\n• বাঘ পাশের ছাগলের ওপর দিয়ে সোজা লাফ দিয়ে খায়, এক চালে একটা।\n• বাঘ ৫টা ছাগল খেলে জেতে, সব বাঘ আটকে গেলে ছাগল জেতে।\n• টানা ৬০ চালে কিছু না হলে ড্র।"
} + "\n• এক ফোনে দুজনের খেলায় XP বা কয়েন নেই।\n• অনলাইনে প্রতি চালে ৯০ সেকেন্ড সময়।"

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
        item.slot == Slot.Striker && colors.size >= 2 -> Box(Modifier.height(92.r), contentAlignment = Alignment.Center) {
            Box(Modifier.size(74.r).clip(CircleShape).background(colors[0]).border(7.r, colors[1], CircleShape))
        }
        item.slot == Slot.Pen && colors.size >= 2 -> Row(Modifier.fillMaxWidth().height(92.r), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Box(Modifier.width(130.r).height(20.r).clip(RoundedCornerShape(topStart = 12.r, bottomStart = 12.r)).background(colors[0]))
            Box(Modifier.width(60.r).height(24.r).clip(RoundedCornerShape(topEnd = 12.r, bottomEnd = 12.r)).background(colors[1]))
        }
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
        else -> Box(Modifier.height(92.r), contentAlignment = Alignment.Center) { RText(item.emoji, if (item.slot == Slot.Piece) 44 else 54, FontWeight.Normal, C.Ink) }
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
        StatLine("মোট ম্যাচ (বট + অনলাইন)", bnNum(d.played))
        StatLine("জয়", bnNum(d.wins), TGreen)
        StatLine("হার", bnNum(d.losses), C.Danger)
        StatLine("ড্র", bnNum(d.draws))
        StatLine("জয়ের হার", if (d.played == 0) "—" else bnNum(d.wins * 100 / d.played) + "%")
        StatLine("টানা জয় (এখন / সেরা)", "${bnNum(d.streak)} / ${bnNum(d.bestStreak)}")
        StatLine("অনলাইনে জয়", bnNum(d.onlineWins))
        StatLine("মোট (${profile.game.score})", bnNum(d.captured))
        StatLine("মোট XP", bnNum(d.xp))
    }
    GameCard {
        RText("🎒 আপনার পাওয়ার", 21, FontWeight.Bold, C.Ink)
        Spacer(Modifier.height(8.r))
        if (profile.game.isBoard) {
            StatLine("↩️ চাল ফেরত", bnNum(d.counter(Counter.UNDO)))
            StatLine("💡 পরামর্শ", bnNum(d.counter(Counter.HINT)))
        } else {
            StatLine("🎯 লম্বা এইম", bnNum(d.counter(Counter.AIM)))
        }
        StatLine("⚡ ডাবল XP ম্যাচ", bnNum(d.counter(Counter.DOUBLE_XP)))
    }
    GameCard {
        RText("💎 জেম কীভাবে পাবেন", 21, FontWeight.Bold, C.Ink)
        Spacer(Modifier.height(8.r))
        StatLine("লেভেল বাড়লে", "💎 ${bnNum(Economy.LEVEL_GEMS)} + 🪙 ${bnNum(Economy.LEVEL_COINS)}")
        StatLine("দিনের প্রথম জয়", "💎 ${bnNum(Economy.FIRST_WIN_GEMS)}")
        StatLine("কঠিন বট বা অনলাইনে জয়", "💎 ১")
        StatLine("দৈনিক / সাপ্তাহিক ইভেন্ট", "💎 ১ / ৬")
        StatLine("৭ দিন পুরস্কার নিলে", "💎 ১৫")
    }
}
