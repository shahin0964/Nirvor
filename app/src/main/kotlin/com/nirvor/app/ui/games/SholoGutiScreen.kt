package com.nirvor.app.ui.games

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.games.Counter
import com.nirvor.app.games.GameProfile
import com.nirvor.app.games.Outcome
import com.nirvor.app.games.Reward
import com.nirvor.app.games.Slot
import com.nirvor.app.games.guti.GutiBoard
import com.nirvor.app.games.guti.GutiBot
import com.nirvor.app.games.guti.GutiLevel
import com.nirvor.app.games.guti.GutiMove
import com.nirvor.app.games.guti.GutiResult
import com.nirvor.app.games.guti.GutiState
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.PageHeader
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.TGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min

private const val ME = 1

/** The state of one match on screen. Player 1 is at the bottom; with a bot, the bot is player 2. */
private class GutiMatch {
    var state by mutableStateOf(GutiState.initial())
    var selected by mutableIntStateOf(-1)

    /** The piece in the middle of eating several, or -1. */
    var chain by mutableIntStateOf(-1)
    var busy by mutableStateOf(false)
    var movingTo by mutableIntStateOf(-1)
    var movingFrom by mutableIntStateOf(-1)
    var progress by mutableFloatStateOf(0f)
    var hint by mutableStateOf<GutiMove?>(null)
    var lastFrom by mutableIntStateOf(-1)
    var lastTo by mutableIntStateOf(-1)
    var result by mutableStateOf<GutiResult?>(null)
    var reward by mutableStateOf<Reward?>(null)
    var undo by mutableStateOf(listOf<GutiState>())

    /** The position when this turn began (what "undo" goes back to). */
    var turnStart = state

    suspend fun hop(from: Int, to: Int, over: Int, anim: Animatable<Float, AnimationVector1D>) {
        movingFrom = from
        movingTo = to
        progress = 0f
        anim.snapTo(0f)
        anim.animateTo(1f, tween(230)) { progress = value }
        state = state.hop(from, to, over)
        movingFrom = -1
        movingTo = -1
        lastFrom = from
        lastTo = to
    }

    fun endTurn(captured: Boolean) {
        selected = -1
        chain = -1
        hint = null
        busy = false
        state = state.endTurn(captured)
        turnStart = state
        result = state.result()
    }
}

@Composable
fun SholoGutiScreen(profile: GameProfile, name: String, level: GutiLevel?, onExit: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val match = remember(round) { GutiMatch() }
    val anim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val bot = remember { GutiBot() }
    var leaving by remember { mutableStateOf(false) }
    val vsBot = level != null
    val s = match.state
    val myTurn = match.result == null && !match.busy && (!vsBot || s.turn == ME)

    fun over() = match.result != null

    /** Leaving a started match against the bot counts as a loss (without a reward). */
    fun leave() {
        if (vsBot && !over() && s.turns >= 4) profile.finish(Outcome.Loss, 0, 0, 0, 16 - s.count(2))
        onExit()
    }

    fun back() {
        if (over() || s.turns < 2) leave() else leaving = true
    }
    BackHandler { back() }

    // the bot's turn
    LaunchedEffect(match, s.turn, match.result == null) {
        if (level == null || s.turn == ME || match.result != null) return@LaunchedEffect
        match.busy = true
        delay(350)
        val from = match.state
        val move = withContext(Dispatchers.Default) { bot.choose(from, level) } ?: return@LaunchedEffect
        var at = move.from
        move.path.forEachIndexed { i, to ->
            match.hop(at, to, move.captured.getOrElse(i) { -1 }, anim)
            if (move.isCapture) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            at = to
        }
        match.endTurn(move.isCapture)
    }

    // the reward, once per match
    LaunchedEffect(match.result) {
        val r = match.result ?: return@LaunchedEffect
        if (level != null && match.reward == null) {
            val eaten = 16 - match.state.count(2)
            val (xp, coins, gems) = winPrize(level)
            match.reward = when (r.winner) {
                ME -> profile.finish(Outcome.Win, xp, coins, gems, eaten)
                0 -> profile.finish(Outcome.Draw, xp / 2, coins / 2, 0, eaten)
                else -> profile.finish(Outcome.Loss, 8, 5, 0, eaten)
            }
        }
    }

    fun tap(p: Int) {
        if (!myTurn || over()) return
        val mine = s.cells[p].toInt() == s.turn
        if (match.chain < 0 && mine) {
            match.selected = if (match.selected == p) -1 else p
            return
        }
        val from = match.selected
        if (from < 0) return
        val jump = s.jumps(from).firstOrNull { it.to == p }
        val step = match.chain < 0 && p in s.steps(from)
        if (jump == null && !step) return
        if (match.chain < 0 && vsBot) match.undo = (match.undo + match.turnStart).takeLast(30)
        val m = match
        m.busy = true
        m.hint = null
        scope.launch {
            m.selected = -1
            m.hop(from, p, jump?.over ?: -1, anim)
            if (jump == null) {
                m.endTurn(false)
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (m.state.jumps(p).isNotEmpty()) {
                    m.chain = p
                    m.selected = p
                    m.busy = false
                } else {
                    m.endTurn(true)
                }
            }
        }
    }

    fun useUndo() {
        if (!myTurn || match.undo.isEmpty() || !profile.use(Counter.UNDO)) return
        match.state = match.undo.last()
        match.turnStart = match.state
        match.undo = match.undo.dropLast(1)
        match.selected = -1
        match.chain = -1
        match.hint = null
        match.lastFrom = -1
        match.lastTo = -1
    }

    fun useHint() {
        if (!myTurn || match.chain >= 0 || match.hint != null || !profile.use(Counter.HINT)) return
        val m = match
        m.busy = true
        scope.launch {
            val found = withContext(Dispatchers.Default) { GutiBot().choose(m.state, GutiLevel.Hard) }
            m.hint = found
            m.selected = found?.from ?: -1
            m.busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(C.Sheet)) {
        PageHeader(profile.game.title, if (level != null) "বট · ${level.bn}" else "দুজনের খেলা", onBack = { back() })
        Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.r, vertical = 8.r)) {
            PlayerBar(
                emoji = if (vsBot) "🤖" else "🧑",
                name = if (vsBot) "বট" else "খেলোয়াড় ২",
                left = s.count(2),
                color = pieceColors(profile)[1],
                active = s.turn == 2 && !over(),
                note = if (vsBot && s.turn == 2 && !over()) "ভাবছে…" else "",
            )
            Board(profile, match, Modifier.fillMaxWidth().weight(1f).padding(vertical = 10.r), onTap = ::tap)
            PlayerBar(
                emoji = profile.equipped(Slot.Avatar)?.emoji ?: "🙂",
                name = if (vsBot) name else "খেলোয়াড় ১",
                left = s.count(1),
                color = pieceColors(profile)[0],
                active = s.turn == 1 && !over(),
                note = "",
            )
            Spacer(Modifier.height(12.r))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.r, Alignment.CenterHorizontally)) {
                if (match.chain >= 0) {
                    SmallButton("✔ চাল শেষ", TGreen, myTurn) { match.endTurn(true) }
                }
                if (vsBot) {
                    val d = profile.data
                    SmallButton(
                        "↩️ ফেরত (${bnNum(d.counter(Counter.UNDO))})",
                        Color(profile.game.colorB),
                        myTurn && match.undo.isNotEmpty() && d.counter(Counter.UNDO) > 0,
                    ) { useUndo() }
                    SmallButton(
                        "💡 পরামর্শ (${bnNum(d.counter(Counter.HINT))})",
                        Color(profile.game.colorB),
                        myTurn && match.chain < 0 && match.hint == null && d.counter(Counter.HINT) > 0,
                    ) { useHint() }
                }
            }
            Spacer(Modifier.height(6.r))
        }
    }

    if (leaving) {
        AlertDialog(
            onDismissRequest = { leaving = false },
            containerColor = Color.White,
            title = { RText("খেলা ছেড়ে দেবেন?", 24, FontWeight.Bold, C.Ink) },
            text = {
                RText(if (vsBot && s.turns >= 4) "এই ম্যাচ হার হিসেবে গোনা হবে।" else "এই ম্যাচ সেভ থাকবে না।", 18, FontWeight.Normal, C.StatLabel, maxLines = 2)
            },
            confirmButton = {
                TextButton(onClick = {
                    leaving = false
                    leave()
                }) { RText("ছেড়ে দিন", 19, FontWeight.SemiBold, C.Danger) }
            },
            dismissButton = { TextButton(onClick = { leaving = false }) { RText("খেলব", 19, FontWeight.SemiBold, TGreen) } },
        )
    }

    match.result?.let { r ->
        val title = when {
            r.winner == 0 -> "🤝 ড্র"
            !vsBot -> "🏆 খেলোয়াড় ${bnNum(r.winner)} জিতেছে"
            r.winner == ME -> "🏆 আপনি জিতেছেন!"
            else -> "😔 বট জিতেছে"
        }
        AlertDialog(
            onDismissRequest = { },
            containerColor = Color.White,
            title = { RText(title, 27, FontWeight.Bold, C.Ink) },
            text = {
                Column {
                    RText(r.reason, 18, FontWeight.Normal, C.StatLabel, maxLines = 3, lineHeight = 26)
                    match.reward?.let { w ->
                        Spacer(Modifier.height(14.r))
                        RText(
                            "⭐ +${bnNum(w.xp)} XP   🪙 +${bnNum(w.coins)}" + if (w.gems > 0) "   💎 +${bnNum(w.gems)}" else "",
                            21,
                            FontWeight.Bold,
                            TGreen,
                        )
                        if (w.firstWin) RText("🌞 দিনের প্রথম জয়ের বোনাসসহ", 17, FontWeight.Medium, GemInk, Modifier.padding(top = 8.r))
                        if (w.levelUps > 0) RText("🎉 লেভেল ${bnNum(profile.data.level)} হয়েছে!", 20, FontWeight.Bold, CoinInk, Modifier.padding(top = 8.r))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { round++ }) { RText("আবার খেলুন", 19, FontWeight.SemiBold, TGreen) } },
            dismissButton = { TextButton(onClick = onExit) { RText("বের হন", 19, FontWeight.Medium, C.Hint) } },
        )
    }
}

private fun pieceColors(profile: GameProfile): List<Color> =
    (profile.equipped(Slot.Piece)?.colors ?: listOf(0xFFD6352B, 0xFF262B33)).map { Color(it) }

@Composable
private fun PlayerBar(emoji: String, name: String, left: Int, color: Color, active: Boolean, note: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.r))
            .background(if (active) TGreen.copy(alpha = 0.14f) else Color.White)
            .padding(horizontal = 18.r, vertical = 12.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RText(emoji, 30, FontWeight.Normal, C.Ink)
        Spacer(Modifier.width(12.r))
        RText(name, 20, FontWeight.SemiBold, C.Ink, Modifier.weight(1f))
        if (note.isNotEmpty()) RText(note, 16, FontWeight.Medium, C.Hint, Modifier.padding(end = 12.r))
        Box(Modifier.size(26.r).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.r))
        RText(bnNum(left), 22, FontWeight.Bold, C.Ink)
    }
}

/** Where the points are inside a canvas of this size. */
private class Geo(size: Size) {
    val cell = min(size.width / 5.2f, size.height / 9.2f)
    private val left = (size.width - cell * 4) / 2
    private val top = (size.height - cell * 8) / 2
    fun at(p: Int): Offset = GutiBoard.points[p].let { Offset(left + it.x * cell, top + (it.y + 2) * cell) }
    val frameAt = Offset(left - cell * 0.6f, top - cell * 0.6f)
    val frameSize = Size(cell * 5.2f, cell * 9.2f)
}

@Composable
private fun Board(profile: GameProfile, match: GutiMatch, modifier: Modifier, onTap: (Int) -> Unit) {
    val board = (profile.equipped(Slot.Board)?.colors ?: listOf(0xFFE9C48F, 0xFF8A5523, 0xFF5A3512)).map { Color(it) }
    val pieces = pieceColors(profile)
    val tap = rememberUpdatedState(onTap)
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { pos ->
                val geo = Geo(Size(size.width.toFloat(), size.height.toFloat()))
                val p = (0 until GutiBoard.SIZE).minByOrNull { geo.at(it).let { o -> hypot(o.x - pos.x, o.y - pos.y) } }
                if (p != null && geo.at(p).let { hypot(it.x - pos.x, it.y - pos.y) } < geo.cell * 0.6f) tap.value(p)
            }
        },
    ) {
        val geo = Geo(size)
        val s = match.state
        drawRoundRect(board[1], geo.frameAt, geo.frameSize, CornerRadius(geo.cell * 0.35f))
        val edge = geo.cell * 0.12f
        drawRoundRect(
            board[0],
            geo.frameAt + Offset(edge, edge),
            Size(geo.frameSize.width - edge * 2, geo.frameSize.height - edge * 2),
            CornerRadius(geo.cell * 0.26f),
        )
        GutiBoard.edges.forEach { (a, b) -> drawLine(board[2], geo.at(a), geo.at(b), geo.cell * 0.045f) }
        for (p in 0 until GutiBoard.SIZE) drawCircle(board[2], geo.cell * 0.08f, geo.at(p))

        listOf(match.lastFrom, match.lastTo).filter { it >= 0 }.forEach {
            drawCircle(Color(0xFFFFC93C).copy(alpha = 0.55f), geo.cell * 0.43f, geo.at(it))
        }

        val radius = geo.cell * 0.34f
        for (p in 0 until GutiBoard.SIZE) {
            val c = s.cells[p].toInt()
            if (c == 0 || p == match.movingFrom) continue
            guti(geo.at(p), radius, pieces[c - 1])
        }
        if (match.movingFrom >= 0) {
            val a = geo.at(match.movingFrom)
            val b = geo.at(match.movingTo)
            val t = match.progress
            guti(Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t), radius * 1.12f, pieces[s.cells[match.movingFrom].toInt().coerceAtLeast(1) - 1])
        }

        val sel = match.selected
        if (sel >= 0 && match.movingFrom < 0) {
            drawCircle(Color(0xFF22C55E), radius * 1.15f, geo.at(sel), style = Stroke(geo.cell * 0.07f))
            val jumps = s.jumps(sel)
            if (match.chain < 0) s.steps(sel).forEach { drawCircle(Color(0xFF22C55E).copy(alpha = 0.75f), geo.cell * 0.16f, geo.at(it)) }
            jumps.forEach {
                drawCircle(Color(0xFFEF4444).copy(alpha = 0.85f), geo.cell * 0.19f, geo.at(it.to))
                drawCircle(Color(0xFFEF4444), radius * 1.1f, geo.at(it.over), style = Stroke(geo.cell * 0.05f))
            }
        }
        match.hint?.let { h ->
            var at = h.from
            h.path.forEach { to ->
                drawLine(Color(0xFF8B5CF6), geo.at(at), geo.at(to), geo.cell * 0.11f)
                at = to
            }
            drawCircle(Color(0xFF8B5CF6), geo.cell * 0.22f, geo.at(at))
        }
    }
}

private fun DrawScope.guti(at: Offset, radius: Float, color: Color) {
    drawCircle(Color.Black.copy(alpha = 0.28f), radius, at + Offset(radius * 0.08f, radius * 0.16f))
    drawCircle(
        Brush.radialGradient(listOf(lighter(color), color), center = at - Offset(radius * 0.3f, radius * 0.35f), radius = radius * 1.3f),
        radius,
        at,
    )
    drawCircle(Color.Black.copy(alpha = 0.25f), radius, at, style = Stroke(radius * 0.08f))
    drawCircle(Color.White.copy(alpha = 0.25f), radius * 0.62f, at, style = Stroke(radius * 0.07f))
}
