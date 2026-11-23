package com.nirvor.app.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import com.nirvor.app.games.Counter
import com.nirvor.app.games.GameProfile
import com.nirvor.app.games.GameSound
import com.nirvor.app.games.MatchEnd
import com.nirvor.app.games.Slot
import com.nirvor.app.games.carrom.Carrom
import com.nirvor.app.games.carrom.CarromBot
import com.nirvor.app.games.carrom.CarromState
import com.nirvor.app.games.carrom.Physics
import com.nirvor.app.games.carrom.Run
import com.nirvor.app.games.carrom.Shot
import com.nirvor.app.games.carrom.Sound
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.r
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A coin falling into a pocket: which, where, and at which frame. */
private class Drop(val disc: Int, val x: Float, val y: Float, val frame: Int)

private class CarromMatch {
    var state by mutableStateOf(CarromState.initial())
    var striker by mutableFloatStateOf((Carrom.SIZE / 2).toFloat())

    /** The pull, in board units (from where the finger went down). */
    var pull by mutableStateOf<Offset?>(null)
    var run by mutableStateOf<Run?>(null)
    var frame by mutableFloatStateOf(0f)
    var drops = listOf<Drop>()
    var busy by mutableStateOf(false)
    var result by mutableStateOf<MatchEnd?>(null)
    var longAim by mutableStateOf(false)
}

private const val FULL_PULL = 250f

/** Pull → velocity direction and speed; null when too short. */
private fun aimOf(pull: Offset?): Triple<Double, Double, Double>? {
    val p = pull ?: return null
    val len = hypot(p.x, p.y)
    if (len < 14f) return null
    val power = (len / FULL_PULL).coerceAtMost(1f).toDouble()
    return Triple(-p.x / len.toDouble(), -p.y / len.toDouble(), 160 + power * (Carrom.MAX_SPEED - 160))
}

@Composable
fun CarromScreen(profile: GameProfile, name: String, rival: Rival, onExit: () -> Unit) {
    var round by remember { mutableIntStateOf(0) }
    val match = remember(round) { CarromMatch() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val sound = rememberSound(profile)
    val bot = remember { CarromBot() }
    val me = rival.me
    val s = match.state
    val myTurn = match.result == null && !match.busy && rival.mine(s.turn)

    /** Plays the shot on screen, then applies the rules. */
    suspend fun shoot(m: CarromMatch, shot: Shot) {
        m.busy = true
        m.pull = null
        val before = m.state
        val run = withContext(Dispatchers.Default) { Physics.run(before.table, before.turn, shot, true) }
        m.drops = (0 until 20).mapNotNull { d ->
            val f = run.frames.indexOfFirst { it[d * 2].isNaN() }
            if (f > 0) Drop(d, run.frames[f - 1][d * 2], run.frames[f - 1][d * 2 + 1], f) else null
        }
        m.frame = 0f
        m.run = run
        var next = 0
        val start = withFrameNanos { it }
        val last = run.frames.size - 1
        val stop = last + if (m.drops.any { it.frame > last - 14 }) 14 else 2
        while (m.frame < stop) {
            m.frame = withFrameNanos { (it - start) / 1e9f * 60f }
            while (next < run.sounds.size && run.sounds[next].frame <= m.frame) {
                val h = run.sounds[next++]
                sound.play(
                    when (h.sound) {
                        Sound.Hit -> GameSound.S.Hit
                        Sound.Wall -> GameSound.S.Wall
                        Sound.Pocket -> GameSound.S.Pocket
                    },
                    h.power,
                )
                if (h.sound == Sound.Pocket) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        m.striker = (Carrom.SIZE / 2).toFloat()
        m.state = before.after(run)
        m.run = null
        m.busy = false
        if (m.result == null) m.result = m.state.end
    }

    // keyed by the number of shots: the same player may shoot several times
    LaunchedEffect(match, s.shots, match.result == null) {
        if (rival.mine(s.turn) || match.result != null) return@LaunchedEffect
        when (rival) {
            is Rival.Bot -> {
                match.busy = true
                val shot = withContext(Dispatchers.Default) { bot.choose(s, rival.level) }
                // the striker slides to its place, as a person would do it
                val from = match.striker
                for (i in 1..18) {
                    withFrameNanos { }
                    match.striker = from + (shot.x.toFloat() - from) * i / 18f
                }
                delay(450)
                shoot(match, shot)
            }
            is Rival.Online -> {
                val shot = Shot.decode(rival.session.moves.receive())
                if (shot == null || !s.table.free(shot.x, Carrom.baseY(s.turn), Carrom.STRIKER)) match.result = MatchEnd(me, "বিপক্ষের চাল ঠিক নেই")
                else {
                    match.striker = shot.x.toFloat()
                    match.busy = true
                    delay(500)
                    shoot(match, shot)
                }
            }
            Rival.Local -> Unit
        }
    }

    val fits = s.table.free(match.striker.toDouble(), Carrom.baseY(s.turn), Carrom.STRIKER)

    fun release() {
        val aim = aimOf(match.pull)
        match.pull = null
        if (!myTurn || aim == null || !fits) return
        val shot = Shot.of(match.striker.toDouble(), aim.first, aim.second, aim.third)
        val m = match
        m.busy = true
        scope.launch {
            if (rival is Rival.Online) launch {
                if (!rival.session.send(shot.encode()) && m.result == null) m.result = MatchEnd(3 - me, "শট পাঠানো যায়নি")
            }
            shoot(m, shot)
        }
    }

    val note = when {
        match.run != null -> ""
        myTurn && !fits -> "এখানে গুটি আছে, স্ট্রাইকার সরান"
        s.note.isNotEmpty() -> s.note
        myTurn -> "বোর্ডে আঙুল পেছনে টেনে ছাড়ুন"
        else -> ""
    }
    MatchFrame(
        profile, rival, name, round, s.turn, s.shots, 6, match.result, s.pocketed(me), note,
        badge = { p -> (if (s.cover == p) "👑 " else if (s.pending == p) "❗ " else "") + (if (p == 1) "⚪ " else "⚫ ") + "${bnNum(s.pocketed(p))}/৯" },
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
    ) { mod ->
        Column(mod) {
            CarromBoard(profile, match, me == 2, myTurn, fits, Modifier.fillMaxWidth().weight(1f)) { release() }
            val flip = me == 2
            val shown = if (flip) Carrom.BASE_MIN + Carrom.BASE_MAX - match.striker else match.striker
            Slider(
                value = shown.coerceIn(Carrom.BASE_MIN.toFloat(), Carrom.BASE_MAX.toFloat()),
                onValueChange = { v -> if (myTurn) match.striker = if (flip) Carrom.BASE_MIN + Carrom.BASE_MAX - v else v },
                enabled = myTurn,
                valueRange = Carrom.BASE_MIN.toFloat()..Carrom.BASE_MAX.toFloat(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 40.r),
                colors = SliderDefaults.colors(thumbColor = Color(profile.game.colorB), activeTrackColor = Color(profile.game.colorA), inactiveTrackColor = Color(profile.game.colorA).copy(alpha = 0.35f)),
            )
        }
    }
}

private const val FRAME = 44f

private class CarromGeo(size: Size, val flip: Boolean) {
    val side = min(size.width, size.height)
    val k = side / (Carrom.SIZE.toFloat() + FRAME * 2)
    val left = (size.width - side) / 2
    val top = (size.height - side) / 2
    fun at(x: Float, y: Float): Offset {
        val s = Carrom.SIZE.toFloat()
        return Offset(left + (FRAME + if (flip) s - x else x) * k, top + (FRAME + if (flip) s - y else y) * k)
    }

    fun at(x: Double, y: Double) = at(x.toFloat(), y.toFloat())
}

@Composable
private fun CarromBoard(profile: GameProfile, match: CarromMatch, flip: Boolean, myTurn: Boolean, fits: Boolean, modifier: Modifier, onRelease: () -> Unit) {
    val board = (profile.equipped(Slot.Board)?.colors ?: listOf(0xFFF0D2A0, 0xFF5B3216, 0xFF3A2413)).map { Color(it) }
    val coins = (profile.equipped(Slot.Piece)?.colors ?: listOf(0xFFF3E3C3, 0xFF2B2622)).map { Color(it) }
    val strike = (profile.equipped(Slot.Striker)?.colors ?: listOf(0xFFFFF7E6, 0xFFC0392B)).map { Color(it) }
    val release by rememberUpdatedState(onRelease)
    val can by rememberUpdatedState(myTurn)
    Canvas(
        modifier.pointerInput(match) {
            detectDragGestures(
                onDragStart = { if (can) match.pull = Offset.Zero },
                onDragEnd = { release() },
                onDragCancel = { match.pull = null },
            ) { change, drag ->
                change.consume()
                val g = CarromGeo(Size(size.width.toFloat(), size.height.toFloat()), flip)
                match.pull?.let { match.pull = it + drag / (if (flip) -g.k else g.k) }
            }
        },
    ) {
        val g = CarromGeo(size, flip)
        drawBoard(g, board)
        val s = match.state
        val run = match.run
        if (run == null) {
            for (i in 0 until Carrom.COINS) if (s.table.on[i]) coin(g, g.at(s.table.x[i], s.table.y[i]), i, coins, 1f)
            if (match.result == null) {
                val at = g.at(match.striker.toDouble(), Carrom.baseY(s.turn))
                aimOf(match.pull)?.takeIf { fits }?.let { guide(g, match, it) }
                striker(g, at, strike, if (fits) 1f else 0.5f)
                if (!fits) drawCircle(Color.Red, Carrom.STRIKER.toFloat() * g.k, at, style = Stroke(3f * g.k))
            }
        } else {
            val f = match.frame
            val a = run.frames[f.toInt().coerceIn(0, run.frames.size - 1)]
            val b = run.frames[(f.toInt() + 1).coerceIn(0, run.frames.size - 1)]
            val t = f - f.toInt()
            for (d in match.drops) {
                val age = f - d.frame
                if (age < 0 || age > 14) continue
                val px = if (d.x < Carrom.SIZE / 2) Carrom.POCKET else Carrom.SIZE - Carrom.POCKET
                val py = if (d.y < Carrom.SIZE / 2) Carrom.POCKET else Carrom.SIZE - Carrom.POCKET
                val q = age / 14f
                val at = g.at(d.x + (px.toFloat() - d.x) * q, d.y + (py.toFloat() - d.y) * q)
                if (d.disc == Carrom.STRIKER_AT) striker(g, at, strike, 1 - q) else coin(g, at, d.disc, coins, 1 - q)
            }
            for (i in 0 until 20) {
                val x = a[i * 2]
                if (x.isNaN()) continue
                val y = a[i * 2 + 1]
                val nx = if (b[i * 2].isNaN()) x else x + (b[i * 2] - x) * t
                val ny = if (b[i * 2].isNaN()) y else y + (b[i * 2 + 1] - y) * t
                if (i == Carrom.STRIKER_AT) striker(g, g.at(nx, ny), strike, 1f) else coin(g, g.at(nx, ny), i, coins, 1f)
            }
        }
    }
}

private fun DrawScope.drawBoard(g: CarromGeo, c: List<Color>) {
    val k = g.k
    val s = Carrom.SIZE.toFloat()
    val o = Offset(g.left, g.top)
    // frame
    drawRoundRect(Brush.linearGradient(listOf(lerp(c[1], Color.White, 0.22f), c[1], lerp(c[1], Color.Black, 0.35f)), o, o + Offset(g.side, g.side)), o, Size(g.side, g.side), CornerRadius(46 * k))
    drawRoundRect(Color.Black.copy(alpha = 0.35f), o + Offset(3 * k, 3 * k), Size(g.side - 6 * k, g.side - 6 * k), CornerRadius(43 * k), style = Stroke(2.5f * k))
    // surface
    val p = o + Offset(FRAME * k, FRAME * k)
    drawRect(Brush.radialGradient(listOf(lerp(c[0], Color.White, 0.28f), c[0], lerp(c[0], Color.Black, 0.1f)), p + Offset(s / 2 * k, s / 2 * k), s * 0.78f * k), p, Size(s * k, s * k))
    // the frame's shadow on the surface
    for (i in 0..5) drawRect(Color.Black.copy(alpha = 0.05f), p, Size(s * k, s * k), style = Stroke((4 + i * 5) * k))
    val line = c[2]
    // base lines on four sides
    val lo = Carrom.BASE_MIN.toFloat()
    val hi = Carrom.BASE_MAX.toFloat()
    val mid = Carrom.BASE_FROM_EDGE.toFloat()
    for (e in floatArrayOf(mid - 16, mid + 16)) for (far in booleanArrayOf(false, true)) {
        val v = if (far) s - e else e
        drawLine(line, g.at(lo, v), g.at(hi, v), 2.2f * k)
        drawLine(line, g.at(v, lo), g.at(v, hi), 2.2f * k)
    }
    val red = Color(0xFFB3261E)
    for (far in booleanArrayOf(false, true)) for (end in floatArrayOf(lo, hi)) {
        val v = if (far) s - mid else mid
        for (at in listOf(g.at(end, v), g.at(v, end))) {
            drawCircle(line, 16 * k, at, style = Stroke(2.2f * k))
            drawCircle(red, 11.5f * k, at)
        }
    }
    // arrows towards the pockets
    for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
        fun q(d: Float) = g.at(s / 2 + sx * d, s / 2 + sy * d)
        drawLine(line, q(150f), q(292f), 2f * k)
        val tip = q(292f)
        val dir = (q(292f) - q(150f)).let { it / hypot(it.x, it.y) }
        val n = Offset(-dir.y, dir.x)
        drawLine(line, tip, tip - dir * (15 * k) + n * (8 * k), 2f * k)
        drawLine(line, tip, tip - dir * (15 * k) - n * (8 * k), 2f * k)
        drawCircle(line, 22 * k, q(150f), style = Stroke(1.6f * k))
    }
    // centre
    val m = g.at(s / 2, s / 2)
    drawCircle(line, 85 * k, m, style = Stroke(2.4f * k))
    drawCircle(line, 79 * k, m, style = Stroke(1.2f * k))
    drawCircle(red.copy(alpha = 0.85f), 16 * k, m)
    for (i in 0 until 8) {
        val a = i * Math.PI / 4
        drawLine(red.copy(alpha = 0.7f), m + Offset(cos(a).toFloat(), sin(a).toFloat()) * (20 * k), m + Offset(cos(a).toFloat(), sin(a).toFloat()) * (74 * k), 2.4f * k)
    }
    // pockets
    for (x in floatArrayOf(Carrom.POCKET.toFloat(), s - Carrom.POCKET.toFloat())) for (y in floatArrayOf(Carrom.POCKET.toFloat(), s - Carrom.POCKET.toFloat())) {
        val at = g.at(x, y)
        drawCircle(lerp(c[1], Color.Black, 0.3f), 27.5f * k, at)
        drawCircle(Brush.radialGradient(listOf(Color.Black, Color(0xFF1A1411)), at, 24.5f * k), 24.5f * k, at)
        drawCircle(Color.White.copy(alpha = 0.16f), 25.5f * k, at, style = Stroke(1.5f * k))
    }
}

private fun DrawScope.coin(g: CarromGeo, at: Offset, i: Int, colors: List<Color>, scale: Float) {
    val c = if (i == Carrom.QUEEN) Color(0xFFD3193B) else if (i < 9) colors[0] else colors[1]
    val r = Carrom.COIN.toFloat() * g.k * scale
    if (r <= 0) return
    drawCircle(Color.Black.copy(alpha = 0.3f), r, at + Offset(r * 0.12f, r * 0.2f))
    drawCircle(Brush.radialGradient(listOf(lerp(c, Color.White, 0.4f), c, lerp(c, Color.Black, 0.18f)), at - Offset(r * 0.35f, r * 0.4f), r * 1.7f), r, at)
    val ring = if (c.red + c.green + c.blue > 1.5f) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.3f)
    drawCircle(ring, r * 0.95f, at, style = Stroke(r * 0.07f))
    drawCircle(ring, r * 0.66f, at, style = Stroke(r * 0.08f))
    drawCircle(ring, r * 0.32f, at, style = Stroke(r * 0.08f))
    if (i == Carrom.QUEEN) drawCircle(Color(0xFFFFD54A), r * 0.2f, at)
}

private fun DrawScope.striker(g: CarromGeo, at: Offset, c: List<Color>, scale: Float) {
    val r = Carrom.STRIKER.toFloat() * g.k * scale
    if (r <= 0) return
    drawCircle(Color.Black.copy(alpha = 0.34f), r, at + Offset(r * 0.12f, r * 0.2f))
    drawCircle(Brush.radialGradient(listOf(lerp(c[0], Color.White, 0.5f), c[0]), at - Offset(r * 0.35f, r * 0.4f), r * 1.6f), r, at)
    drawCircle(c[1], r * 0.88f, at, style = Stroke(r * 0.12f))
    for (i in 0 until 6) {
        val a = i * Math.PI / 3
        drawCircle(c[1], r * 0.12f, at + Offset(cos(a).toFloat(), sin(a).toFloat()) * (r * 0.5f))
    }
    drawCircle(c[1], r * 0.2f, at)
}

/** The aim line: to the first coin or cushion, and where that coin would go. */
private fun DrawScope.guide(g: CarromGeo, match: CarromMatch, aim: Triple<Double, Double, Double>) {
    val (dx, dy, speed) = aim
    val t = match.state.table
    val x = match.striker.toDouble()
    val y = Carrom.baseY(match.state.turn)
    val r = Carrom.STRIKER
    var best = 2000.0
    if (dx > 0) best = min(best, (Carrom.SIZE - r - x) / dx) else if (dx < 0) best = min(best, (r - x) / dx)
    if (dy > 0) best = min(best, (Carrom.SIZE - r - y) / dy) else if (dy < 0) best = min(best, (r - y) / dy)
    var hit = -1
    for (i in 0 until Carrom.COINS) {
        if (!t.on[i]) continue
        val cx = t.x[i] - x
        val cy = t.y[i] - y
        val along = cx * dx + cy * dy
        if (along <= 0) continue
        val off2 = cx * cx + cy * cy - along * along
        val rr = r + Carrom.COIN
        if (off2 >= rr * rr) continue
        val d = along - sqrt(rr * rr - off2)
        if (d < best) {
            best = d
            hit = i
        }
    }
    val from = g.at(x, y)
    val to = g.at(x + dx * best, y + dy * best)
    val power = ((speed - 160) / (Carrom.MAX_SPEED - 160)).toFloat()
    val color = lerp(Color(0xFF22C55E), Color(0xFFEF4444), power)
    drawLine(color, from, to, 4 * g.k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16 * g.k, 9 * g.k)))
    drawCircle(Color.White.copy(alpha = 0.85f), r.toFloat() * g.k, to, style = Stroke(2.5f * g.k))
    if (hit >= 0) {
        val nx = t.x[hit] - (x + dx * best)
        val ny = t.y[hit] - (y + dy * best)
        val n = hypot(nx, ny)
        val len = if (match.longAim) 330.0 else 80.0
        drawLine(Color.White, g.at(t.x[hit], t.y[hit]), g.at(t.x[hit] + nx / n * len, t.y[hit] + ny / n * len), 3.5f * g.k)
    }
    // how hard: a bow behind the striker
    val back = g.at(x - dx * (30 + 70 * power), y - dy * (30 + 70 * power))
    drawLine(color.copy(alpha = 0.8f), from, back, 9 * g.k)
    drawCircle(color, 9 * g.k, back)
}
