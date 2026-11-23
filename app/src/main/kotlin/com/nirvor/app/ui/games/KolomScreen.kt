package com.nirvor.app.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.nirvor.app.games.Counter
import com.nirvor.app.games.GameProfile
import com.nirvor.app.games.GameSound
import com.nirvor.app.games.MatchEnd
import com.nirvor.app.games.Slot
import com.nirvor.app.games.kolom.Flick
import com.nirvor.app.games.kolom.Kolom
import com.nirvor.app.games.kolom.KolomBot
import com.nirvor.app.games.kolom.KolomPhysics
import com.nirvor.app.games.kolom.KolomRun
import com.nirvor.app.games.kolom.KolomState
import com.nirvor.app.games.kolom.Pen
import com.nirvor.app.islamic.bnNum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.min

private class KolomMatch {
    var state by mutableStateOf(KolomState.initial())
    var run by mutableStateOf<KolomRun?>(null)
    var frame by mutableFloatStateOf(0f)
    var busy by mutableStateOf(false)
    var result by mutableStateOf<MatchEnd?>(null)

    /** Where on the pen the finger is (−1 … 1), and the pull in table units. */
    var hold by mutableFloatStateOf(0f)
    var pull by mutableStateOf<Offset?>(null)
    var longAim by mutableStateOf(false)
}

private const val PULL = 260f

@Composable
fun KolomScreen(profile: GameProfile, name: String, rival: Rival, onExit: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val match = remember(round) { KolomMatch() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val sound = rememberSound(profile)
    val bot = remember { KolomBot() }
    val me = rival.me
    val s = match.state
    val myTurn = match.result == null && !match.busy && rival.mine(s.turn)

    suspend fun flick(m: KolomMatch, f: Flick) {
        m.busy = true
        m.pull = null
        val before = m.state
        val run = withContext(Dispatchers.Default) { KolomPhysics.run(before.pens, before.turn, f, true) }
        m.frame = 0f
        m.run = run
        sound.play(GameSound.S.Tap)
        var next = 0
        val start = withFrameNanos { it }
        while (m.frame < run.frames.size) {
            m.frame = withFrameNanos { (it - start) / 1e9f * 60f }
            while (next < run.hits.size && run.hits[next].frame <= m.frame) {
                sound.play(GameSound.S.Hit, run.hits[next++].power)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        if (run.pens.any { it.off }) {
            sound.play(GameSound.S.Pocket)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(700)
        }
        m.state = before.after(run.pens)
        m.run = null
        m.busy = false
        if (m.result == null) m.result = m.state.end
    }

    LaunchedEffect(match, s.total, match.result == null) {
        if (rival.mine(s.turn) || match.result != null) return@LaunchedEffect
        when (rival) {
            is Rival.Bot -> {
                match.busy = true
                val f = withContext(Dispatchers.Default) { bot.choose(s, rival.level) }
                delay(700)
                flick(match, f)
            }
            is Rival.Online -> {
                val f = Flick.decode(rival.session.moves.receive())
                if (f == null) match.result = MatchEnd(me, "বিপক্ষের চাল ঠিক নেই") else flick(match, f)
            }
            Rival.Local -> Unit
        }
    }

    fun release() {
        val p = match.pull
        match.pull = null
        if (!myTurn || p == null) return
        val len = hypot(p.x, p.y)
        if (len < 14f) return
        val f = Flick.of(match.hold.toDouble(), -p.x.toDouble(), -p.y.toDouble(), 120 + (len / PULL).coerceAtMost(1f) * (Kolom.MAX_SPEED - 120))
        val m = match
        m.busy = true
        scope.launch {
            if (rival is Rival.Online) launch {
                if (!rival.session.send(f.encode()) && m.result == null) m.result = MatchEnd(3 - me, "টোকা পাঠানো যায়নি")
            }
            flick(m, f)
        }
    }

    MatchFrame(
        profile, rival, name, round, s.turn, s.total, 3, match.result, s.wins(me),
        if (match.run != null) "" else s.note.ifEmpty { if (myTurn) "নিজের কলম ধরে পেছনে টেনে ছাড়ুন" else "" },
        badge = { "🏆 ${bnNum(s.wins(it))}/৩" },
        sound = sound,
        onForceEnd = { if (match.result == null) match.result = it },
        onAgain = { round++ },
        onExit = onExit,
        buttons = {
            if (rival is Rival.Bot) {
                val n = profile.data.counter(Counter.AIM)
                SmallButton(if (match.longAim) "🎯 চালু" else "🎯 লম্বা এইম (${bnNum(n)})", Color(profile.game.colorB), !match.longAim && n > 0) {
                    if (profile.use(Counter.AIM)) match.longAim = true
                }
            }
        },
    ) { mod -> KolomTable(profile, match, me, myTurn, mod) { release() } }
}

private const val EDGE = 26f

private class KolomGeo(size: Size, val flip: Boolean) {
    // room around the table, so that a falling pen can be seen
    val k = min(size.width / (Kolom.W.toFloat() + 120), size.height / (Kolom.H.toFloat() + 120))
    private val left = (size.width - Kolom.W.toFloat() * k) / 2
    private val top = (size.height - Kolom.H.toFloat() * k) / 2
    fun at(x: Float, y: Float) = if (flip) Offset(left + (Kolom.W.toFloat() - x) * k, top + (Kolom.H.toFloat() - y) * k) else Offset(left + x * k, top + y * k)
    fun back(o: Offset): Offset {
        val x = (o.x - left) / k
        val y = (o.y - top) / k
        return if (flip) Offset(Kolom.W.toFloat() - x, Kolom.H.toFloat() - y) else Offset(x, y)
    }

    val corner = Offset(left, top)
}

@Composable
private fun KolomTable(profile: GameProfile, match: KolomMatch, me: Int, myTurn: Boolean, modifier: Modifier, onRelease: () -> Unit) {
    val table = (profile.equipped(Slot.Board)?.colors ?: listOf(0xFFCB9560, 0xFF6B3F1D, 0xFFB57D48)).map { Color(it) }
    val mine = (profile.equipped(Slot.Pen)?.colors ?: listOf(0xFF2563EB, 0xFF1E3A8A)).map { Color(it) }
    // the opponent's pen must look different from one's own
    val theirs = if (profile.equipped(Slot.Pen)?.id == "pn_red") listOf(Color(0xFF2563EB), Color(0xFF1E3A8A)) else listOf(Color(0xFFDC2626), Color(0xFF7F1D1D))
    val release by rememberUpdatedState(onRelease)
    val can by rememberUpdatedState(myTurn)
    val flip = me == 2
    Canvas(
        modifier.pointerInput(match) {
            detectDragGestures(
                onDragStart = { o ->
                    val g = KolomGeo(Size(size.width.toFloat(), size.height.toFloat()), flip)
                    val p = g.back(o)
                    val pen = match.state.pens[match.state.turn - 1]
                    val rx = p.x - pen.x
                    val ry = p.y - pen.y
                    val along = (rx * pen.dx + ry * pen.dy).coerceIn(-Kolom.HALF, Kolom.HALF)
                    val off = hypot(rx - pen.dx * along, ry - pen.dy * along)
                    if (can && off < 80) {
                        match.hold = (along / Kolom.HALF).toFloat()
                        match.pull = Offset.Zero
                    }
                },
                onDragEnd = { release() },
                onDragCancel = { match.pull = null },
            ) { change, drag ->
                change.consume()
                val g = KolomGeo(Size(size.width.toFloat(), size.height.toFloat()), flip)
                match.pull?.let { match.pull = it + drag / (if (flip) -g.k else g.k) }
            }
        },
    ) {
        val g = KolomGeo(size, flip)
        val w = Size(Kolom.W.toFloat() * g.k, Kolom.H.toFloat() * g.k)
        val e = EDGE * g.k
        drawRoundRect(Color.Black.copy(alpha = 0.22f), g.corner + Offset(8 * g.k, 14 * g.k), w, CornerRadius(14 * g.k))
        drawRoundRect(table[1], g.corner, w, CornerRadius(14 * g.k))
        drawRoundRect(table[0], g.corner + Offset(e / 4, e / 4), Size(w.width - e / 2, w.height - e / 2), CornerRadius(10 * g.k))
        for (i in 1..11) {
            val y = g.corner.y + w.height * i / 12
            drawLine(table[2].copy(alpha = 0.8f), Offset(g.corner.x + e, y), Offset(g.corner.x + w.width - e, y), 2.5f * g.k)
        }
        val run = match.run
        val colors = if (me == 1) listOf(mine, theirs) else listOf(theirs, mine)
        if (run == null) {
            match.state.pens.forEachIndexed { i, p -> pen(g, p, colors[i]) }
            val pull = match.pull
            if (pull != null) {
                val p = match.state.pens[match.state.turn - 1]
                val hx = (p.x + p.dx * match.hold * Kolom.HALF).toFloat()
                val hy = (p.y + p.dy * match.hold * Kolom.HALF).toFloat()
                val len = hypot(pull.x, pull.y)
                drawCircle(Color.White, 9 * g.k, g.at(hx, hy))
                if (len > 14) {
                    val power = (len / PULL).coerceAtMost(1f)
                    val color = lerp(Color(0xFF22C55E), Color(0xFFEF4444), power)
                    val far = (if (match.longAim) 620f else 190f) * (0.35f + 0.65f * power)
                    val d = pull / len
                    drawLine(color, g.at(hx, hy), g.at(hx - d.x * far, hy - d.y * far), 5 * g.k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16 * g.k, 9 * g.k)))
                    drawLine(color.copy(alpha = 0.8f), g.at(hx, hy), g.at(hx + d.x * 100 * power, hy + d.y * 100 * power), 9 * g.k, StrokeCap.Round)
                }
            }
        } else {
            val f = run.frames[match.frame.toInt().coerceIn(0, run.frames.size - 1)]
            for (i in 0..1) pen(g, Pen(f[i * 4].toDouble(), f[i * 4 + 1].toDouble(), f[i * 4 + 2].toDouble(), f[i * 4 + 3].toDouble()), colors[i])
        }
    }
}

private fun DrawScope.pen(g: KolomGeo, p: Pen, c: List<Color>) {
    val fallen = p.off
    val h = (Kolom.HALF * if (fallen) 0.8 else 1.0).toFloat()
    val a = if (fallen) 0.5f else 1f
    val w = Kolom.R.toFloat() * 2 * g.k * if (fallen) 0.8f else 1f
    fun q(t: Float) = g.at((p.x + p.dx * t).toFloat(), (p.y + p.dy * t).toFloat())
    val drop = Offset(3 * g.k, 6 * g.k)
    drawLine(Color.Black.copy(alpha = 0.28f * a), q(-h) + drop, q(h) + drop, w, StrokeCap.Round)
    drawLine(c[0].copy(alpha = a), q(-h), q(h), w, StrokeCap.Round)
    drawLine(c[1].copy(alpha = a), q(h * 0.35f), q(h), w * 1.12f, StrokeCap.Round)
    drawLine(lerp(c[0], Color.White, 0.55f).copy(alpha = 0.75f * a), q(-h * 0.8f), q(h * 0.25f), w * 0.2f, StrokeCap.Round)
    drawCircle(Color(0xFF1F2937).copy(alpha = a), w * 0.22f, q(-h - 7f))
}
