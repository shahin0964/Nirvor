package com.nirvor.app.ui.games

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.nirvor.app.games.BotLevel
import com.nirvor.app.games.Counter
import com.nirvor.app.games.GameProfile
import com.nirvor.app.games.GameSound
import com.nirvor.app.games.MatchEnd
import com.nirvor.app.games.Slot
import com.nirvor.app.games.bagh.BaghBoard
import com.nirvor.app.games.bagh.BaghBot
import com.nirvor.app.games.bagh.BaghMove
import com.nirvor.app.games.bagh.BaghState
import com.nirvor.app.games.bagh.GOAT
import com.nirvor.app.games.bagh.TIGER
import com.nirvor.app.islamic.bnNum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min

private class BaghMatch {
    var state by mutableStateOf(BaghState.initial())
    var selected by mutableIntStateOf(-1)
    var busy by mutableStateOf(false)
    var moving by mutableStateOf<BaghMove?>(null)
    var progress by mutableFloatStateOf(0f)
    var hint by mutableStateOf<BaghMove?>(null)
    var last by mutableStateOf<BaghMove?>(null)
    var result by mutableStateOf<MatchEnd?>(null)
    var undo by mutableStateOf(listOf<BaghState>())
}

@Composable
fun BaghScreen(profile: GameProfile, name: String, rival: Rival, onExit: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val match = remember(round) { BaghMatch() }
    val anim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val sound = rememberSound(profile)
    val bot = remember { BaghBot() }
    val me = rival.me
    val s = match.state
    val myTurn = match.result == null && !match.busy && rival.mine(s.turn)

    suspend fun play(m: BaghMatch, move: BaghMove) {
        m.busy = true
        m.selected = -1
        m.hint = null
        m.moving = move
        m.progress = 0f
        anim.snapTo(0f)
        anim.animateTo(1f, tween(if (move.from < 0) 160 else 260)) { m.progress = value }
        sound.play(if (move.eats >= 0) GameSound.S.Pocket else GameSound.S.Tap)
        if (move.eats >= 0) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        m.moving = null
        m.last = move
        m.busy = false
        m.state = m.state.play(move)
        if (m.result == null) m.result = m.state.result()
    }

    LaunchedEffect(match, s.turn, match.result == null) {
        if (rival.mine(s.turn) || match.result != null) return@LaunchedEffect
        val from = match.state
        when (rival) {
            is Rival.Bot -> {
                match.busy = true
                delay(300)
                withContext(Dispatchers.Default) { bot.choose(from, rival.level) }?.let { play(match, it) }
            }
            is Rival.Online -> {
                val move = from.decode(rival.session.moves.receive())
                if (move == null) match.result = MatchEnd(me, "বিপক্ষের চাল ঠিক নেই") else play(match, move)
            }
            Rival.Local -> Unit
        }
    }

    fun tap(p: Int) {
        if (!myTurn) return
        val placing = s.turn == GOAT && s.hand > 0
        val move = if (placing) BaghMove(-1, p).takeIf { s.cells[p].toInt() == 0 } else {
            if (s.cells[p].toInt() == s.turn) {
                match.selected = if (match.selected == p) -1 else p
                return
            }
            if (match.selected < 0) null else s.movesFrom(match.selected).firstOrNull { it.to == p }
        } ?: return
        val m = match
        m.busy = true
        if (rival is Rival.Bot) m.undo = (m.undo + s).takeLast(30)
        scope.launch {
            play(m, move)
            if (rival is Rival.Online && !rival.session.send(move.encode()) && m.result == null) m.result = MatchEnd(3 - me, "চাল পাঠানো যায়নি")
        }
    }

    val skin = skinOf(profile)
    MatchFrame(
        profile, rival, name, round, s.turn, s.turns, 10, match.result,
        // goats eaten as tiger; a won game as goats counts the trapped tigers
        if (me == TIGER) s.eaten else if (match.result?.winner == GOAT) 4 else s.stuckTigers(),
        if (s.turn == GOAT && s.hand > 0) "খালি ঘরে ছাগল বসান" else "",
        badge = { if (it == GOAT) "${skin.second} হাতে ${bnNum(s.hand)}" else "${skin.first} খেয়েছে ${bnNum(s.eaten)}/৫" },
        sound = sound,
        onForceEnd = { if (match.result == null) match.result = it },
        onAgain = { round++ },
        onExit = onExit,
        buttons = {
            if (rival is Rival.Bot) {
                val d = profile.data
                SmallButton("↩️ ফেরত (${bnNum(d.counter(Counter.UNDO))})", Color(profile.game.colorB), myTurn && match.undo.isNotEmpty() && d.counter(Counter.UNDO) > 0) {
                    if (profile.use(Counter.UNDO)) {
                        match.state = match.undo.last()
                        match.undo = match.undo.dropLast(1)
                        match.selected = -1
                        match.hint = null
                        match.last = null
                    }
                }
                SmallButton("💡 পরামর্শ (${bnNum(d.counter(Counter.HINT))})", Color(profile.game.colorB), myTurn && match.hint == null && d.counter(Counter.HINT) > 0) {
                    if (profile.use(Counter.HINT)) {
                        val m = match
                        m.busy = true
                        scope.launch {
                            m.hint = withContext(Dispatchers.Default) { BaghBot().choose(m.state, BotLevel.Hard) }
                            m.selected = m.hint?.from ?: -1
                            m.busy = false
                        }
                    }
                }
            }
        },
    ) { mod -> BaghBoardView(profile, match, skin, mod, ::tap) }
}

/** Emoji of the tiger and the goat. */
private fun skinOf(profile: GameProfile): Pair<String, String> {
    val e = profile.equipped(Slot.Piece)?.emoji ?: "🐯🐐"
    val cut = e.offsetByCodePoints(0, 1)
    return e.substring(0, cut) to e.substring(cut)
}

private class BaghGeo(size: Size) {
    val cell = min(size.width, size.height) / 5.1f
    private val left = (size.width - cell * 4) / 2
    private val top = (size.height - cell * 4) / 2
    fun at(p: Int) = Offset(left + BaghBoard.x(p) * cell, top + BaghBoard.y(p) * cell)
    val frameAt = Offset(left - cell * 0.55f, top - cell * 0.55f)
    val frame = cell * 5.1f
}

@Composable
private fun BaghBoardView(profile: GameProfile, match: BaghMatch, skin: Pair<String, String>, modifier: Modifier, onTap: (Int) -> Unit) {
    val c = (profile.equipped(Slot.Board)?.colors ?: listOf(0xFFE9C48F, 0xFF8A5523, 0xFF5A3512)).map { Color(it) }
    val tap by rememberUpdatedState(onTap)
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier.pointerInput(Unit) {
            detectTapGestures { pos ->
                val g = BaghGeo(Size(size.width.toFloat(), size.height.toFloat()))
                val p = (0 until BaghBoard.SIZE).minBy { hypot(g.at(it).x - pos.x, g.at(it).y - pos.y) }
                if (hypot(g.at(p).x - pos.x, g.at(p).y - pos.y) < g.cell * 0.5f) tap(p)
            }
        },
    ) {
        val g = BaghGeo(size)
        val s = match.state
        val e = g.cell * 0.12f
        drawRoundRect(c[1], g.frameAt, Size(g.frame, g.frame), CornerRadius(g.cell * 0.3f))
        drawRoundRect(c[0], g.frameAt + Offset(e, e), Size(g.frame - e * 2, g.frame - e * 2), CornerRadius(g.cell * 0.22f))
        BaghBoard.edges.forEach { (a, b) -> drawLine(c[2], g.at(a), g.at(b), g.cell * 0.035f) }
        for (p in 0 until BaghBoard.SIZE) drawCircle(c[2], g.cell * 0.07f, g.at(p))
        match.last?.let { l -> listOf(l.from, l.to).filter { it >= 0 }.forEach { drawCircle(Color(0xFFFFC93C).copy(alpha = 0.5f), g.cell * 0.4f, g.at(it)) } }

        val mv = match.moving
        for (p in 0 until BaghBoard.SIZE) {
            val k = s.cells[p].toInt()
            if (k == 0 || p == mv?.from) continue
            if (p == mv?.eats && match.progress > 0.6f) continue
            piece(measurer, g.at(p), g.cell, k, skin, k == TIGER && s.tigerStuck(p))
        }
        if (mv != null) {
            val to = g.at(mv.to)
            if (mv.from < 0) piece(measurer, to, g.cell * match.progress.coerceAtLeast(0.2f), GOAT, skin, false)
            else {
                val a = g.at(mv.from)
                piece(measurer, a + (to - a) * match.progress, g.cell * 1.1f, s.cells[mv.from].toInt(), skin, false)
            }
        }
        if (match.selected >= 0 && mv == null) {
            drawCircle(Color(0xFF22C55E), g.cell * 0.42f, g.at(match.selected), style = Stroke(g.cell * 0.06f))
            s.movesFrom(match.selected).forEach {
                drawCircle((if (it.eats >= 0) Color(0xFFEF4444) else Color(0xFF22C55E)).copy(alpha = 0.85f), g.cell * 0.15f, g.at(it.to))
            }
        }
        match.hint?.let { h ->
            if (h.from >= 0) drawLine(Color(0xFF8B5CF6), g.at(h.from), g.at(h.to), g.cell * 0.1f)
            drawCircle(Color(0xFF8B5CF6), g.cell * 0.2f, g.at(h.to))
        }
    }
}

private fun DrawScope.piece(m: TextMeasurer, at: Offset, cell: Float, kind: Int, skin: Pair<String, String>, stuck: Boolean) {
    val r = cell * if (kind == TIGER) 0.39f else 0.34f
    drawCircle(Color.Black.copy(alpha = 0.25f), r, at + Offset(0f, r * 0.14f))
    drawCircle(if (stuck) Color(0xFFB9BEC6) else if (kind == TIGER) Color(0xFFFFB057) else Color(0xFFF7F3EA), r, at)
    drawCircle(Color.Black.copy(alpha = 0.3f), r, at, style = Stroke(r * 0.07f))
    val t = m.measure(if (kind == TIGER) skin.first else skin.second, TextStyle(fontSize = (r * 1.15f).toSp()))
    drawText(t, topLeft = at - Offset(t.size.width / 2f, t.size.height / 2f))
}
