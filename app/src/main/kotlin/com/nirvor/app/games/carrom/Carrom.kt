package com.nirvor.app.games.carrom

import com.nirvor.app.games.BotLevel
import com.nirvor.app.games.MatchEnd
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/*
 * ক্যারাম. Sizes are millimetres of a real board (740 × 740 playing surface). Only + − × ÷ and sqrt
 * are used, so two phones work out the same shot the same way; online the shooter's final
 * positions are sent too, to be safe.
 */
object Carrom {
    const val SIZE = 740.0
    const val COIN = 15.9
    const val STRIKER = 20.6
    const val POCKET = 24.5
    const val COINS = 19
    const val QUEEN = 18
    const val STRIKER_AT = 19
    const val BASE_FROM_EDGE = 120.0
    const val BASE_MIN = 138
    const val BASE_MAX = 602
    const val MAX_SPEED = 2700.0

    fun radius(i: Int) = if (i == STRIKER_AT) STRIKER else COIN

    /** 1 white (player 1), 2 black (player 2), 0 queen / striker. */
    fun owner(i: Int) = if (i < 9) 1 else if (i < 18) 2 else 0

    fun baseY(player: Int) = if (player == 1) SIZE - BASE_FROM_EDGE else BASE_FROM_EDGE
}

/** Where everything is. Index 0-8 white, 9-17 black, 18 queen, 19 striker. */
class Table(val x: DoubleArray, val y: DoubleArray, val on: BooleanArray) {
    fun copy() = Table(x.copyOf(), y.copyOf(), on.copyOf())

    fun left(player: Int): Int = (0 until 18).count { on[it] && Carrom.owner(it) == player }

    /** True when a disc of radius [r] fits at ([px], [py]). */
    fun free(px: Double, py: Double, r: Double): Boolean = (0 until Carrom.COINS).none { i ->
        on[i] && run {
            val dx = x[i] - px
            val dy = y[i] - py
            val d = r + Carrom.COIN + 0.2
            dx * dx + dy * dy < d * d
        }
    }

    fun encode(): String = (0 until Carrom.COINS).joinToString(";") {
        if (on[it]) "${Math.round(x[it] * 100)},${Math.round(y[it] * 100)}" else "-"
    }

    companion object {
        fun decode(text: String): Table? {
            val parts = text.split(';')
            if (parts.size != Carrom.COINS) return null
            val t = Table(DoubleArray(20), DoubleArray(20), BooleanArray(20))
            for (i in parts.indices) {
                if (parts[i] == "-") continue
                val xy = parts[i].split(',').map { it.toIntOrNull() ?: return null }
                if (xy.size != 2 || xy.any { it < 0 || it > 74_000 }) return null
                t.x[i] = xy[0] / 100.0
                t.y[i] = xy[1] / 100.0
                t.on[i] = true
            }
            return t
        }

        fun initial(): Table {
            val t = Table(DoubleArray(20), DoubleArray(20), BooleanArray(20))
            val c = Carrom.SIZE / 2
            val d = Carrom.COIN * 2 + 0.3
            val h = sqrt(3.0) / 2
            // six directions, 60° apart
            val dir = arrayOf(0.0 to -1.0, h to -0.5, h to 0.5, 0.0 to 1.0, -h to 0.5, -h to -0.5)
            var w = 0
            var b = 9
            fun put(white: Boolean, px: Double, py: Double) {
                val i = if (white) w++ else b++
                t.x[i] = px
                t.y[i] = py
                t.on[i] = true
            }
            t.x[Carrom.QUEEN] = c
            t.y[Carrom.QUEEN] = c
            t.on[Carrom.QUEEN] = true
            for (k in 0..5) put(k % 2 == 0, c + dir[k].first * d, c + dir[k].second * d)
            for (k in 0..5) {
                val (ax, ay) = dir[k]
                val (nx, ny) = dir[(k + 1) % 6]
                put(true, c + ax * 2 * d, c + ay * 2 * d)
                put(false, c + (ax + nx) * d, c + (ay + ny) * d)
            }
            return t
        }
    }
}

/** A shot: where the striker stands (mm × 10) and its velocity (mm/s). */
class Shot(val x10: Int, val vx: Int, val vy: Int) {
    val x get() = x10 / 10.0

    fun encode() = "$x10,$vx,$vy"

    fun legal(): Boolean =
        x10 in Carrom.BASE_MIN * 10..Carrom.BASE_MAX * 10 && vx.toDouble() * vx + vy.toDouble() * vy <= (Carrom.MAX_SPEED + 5) * (Carrom.MAX_SPEED + 5)

    companion object {
        fun decode(text: String): Shot? {
            val p = text.split(',').map { it.toIntOrNull() ?: return null }
            return if (p.size == 3) Shot(p[0], p[1], p[2]).takeIf { it.legal() } else null
        }

        /** Scales ([dx], [dy]) to [speed]. */
        fun of(x: Double, dx: Double, dy: Double, speed: Double): Shot {
            val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-9)
            val v = speed.coerceIn(0.0, Carrom.MAX_SPEED)
            return Shot(Math.round(x * 10).toInt().coerceIn(Carrom.BASE_MIN * 10, Carrom.BASE_MAX * 10), (dx / len * v).toInt(), (dy / len * v).toInt())
        }
    }
}

enum class Sound { Hit, Wall, Pocket }

class SoundAt(val frame: Int, val sound: Sound, val power: Float)

/** [frames]: x, y of all 20 discs (NaN = not on the board), 60 a second; empty when not recorded. */
class Run(val end: Table, val pocketed: List<Int>, val foul: Boolean, val frames: List<FloatArray>, val sounds: List<SoundAt>, val firstHit: Int)

object Physics {
    private const val STEPS = 960
    private const val DT = 1.0 / STEPS
    private const val SLOW_COIN = 640.0 * DT
    private const val SLOW_STRIKER = 560.0 * DT
    private const val BOUNCE = 0.93
    private const val WALL = 0.72
    private const val MASS_COIN = 5.3
    private const val MASS_STRIKER = 14.0

    fun run(start: Table, player: Int, shot: Shot, record: Boolean): Run {
        val n = 20
        val t = start.copy()
        val x = t.x
        val y = t.y
        val on = t.on
        val vx = DoubleArray(n)
        val vy = DoubleArray(n)
        x[Carrom.STRIKER_AT] = shot.x
        y[Carrom.STRIKER_AT] = Carrom.baseY(player)
        on[Carrom.STRIKER_AT] = true
        vx[Carrom.STRIKER_AT] = shot.vx.toDouble()
        vy[Carrom.STRIKER_AT] = shot.vy.toDouble()
        val frames = ArrayList<FloatArray>()
        val sounds = ArrayList<SoundAt>()
        val pocketed = ArrayList<Int>()
        var foul = false
        var firstHit = -1

        fun snap() {
            frames += FloatArray(n * 2) { k -> val i = k / 2; if (!on[i]) Float.NaN else if (k % 2 == 0) x[i].toFloat() else y[i].toFloat() }
        }

        fun sound(s: Sound, speed: Double) {
            if (record && speed > 25) sounds += SoundAt(frames.size, s, (speed / 1600).toFloat().coerceIn(0.06f, 1f))
        }
        if (record) snap()
        var step = 0
        while (step < STEPS * 12) {
            step++
            var moving = false
            for (i in 0 until n) {
                if (!on[i]) continue
                val sp = sqrt(vx[i] * vx[i] + vy[i] * vy[i])
                if (sp == 0.0) continue
                val slow = if (i == Carrom.STRIKER_AT) SLOW_STRIKER else SLOW_COIN
                if (sp <= slow) {
                    vx[i] = 0.0
                    vy[i] = 0.0
                    continue
                }
                moving = true
                val k = (sp - slow) / sp
                vx[i] *= k
                vy[i] *= k
                x[i] += vx[i] * DT
                y[i] += vy[i] * DT
                val r = Carrom.radius(i)
                // pockets
                val px = if (x[i] < Carrom.SIZE / 2) Carrom.POCKET else Carrom.SIZE - Carrom.POCKET
                val py = if (y[i] < Carrom.SIZE / 2) Carrom.POCKET else Carrom.SIZE - Carrom.POCKET
                val dx = x[i] - px
                val dy = y[i] - py
                if (dx * dx + dy * dy < Carrom.POCKET * Carrom.POCKET * 0.92) {
                    on[i] = false
                    vx[i] = 0.0
                    vy[i] = 0.0
                    if (i == Carrom.STRIKER_AT) foul = true else pocketed += i
                    sound(Sound.Pocket, 900.0)
                    continue
                }
                if (x[i] < r) {
                    x[i] = r
                    sound(Sound.Wall, abs(vx[i]))
                    vx[i] = abs(vx[i]) * WALL
                } else if (x[i] > Carrom.SIZE - r) {
                    x[i] = Carrom.SIZE - r
                    sound(Sound.Wall, abs(vx[i]))
                    vx[i] = -abs(vx[i]) * WALL
                }
                if (y[i] < r) {
                    y[i] = r
                    sound(Sound.Wall, abs(vy[i]))
                    vy[i] = abs(vy[i]) * WALL
                } else if (y[i] > Carrom.SIZE - r) {
                    y[i] = Carrom.SIZE - r
                    sound(Sound.Wall, abs(vy[i]))
                    vy[i] = -abs(vy[i]) * WALL
                }
            }
            for (i in 0 until n) {
                if (!on[i]) continue
                val iStill = vx[i] == 0.0 && vy[i] == 0.0
                for (j in i + 1 until n) {
                    if (!on[j] || (iStill && vx[j] == 0.0 && vy[j] == 0.0)) continue
                    val dx = x[j] - x[i]
                    val dy = y[j] - y[i]
                    val rr = Carrom.radius(i) + Carrom.radius(j)
                    val d2 = dx * dx + dy * dy
                    if (d2 >= rr * rr || d2 == 0.0) continue
                    val d = sqrt(d2)
                    val nx = dx / d
                    val ny = dy / d
                    val mi = MASS_COIN // i < j, so i is never the striker
                    val mj = if (j == Carrom.STRIKER_AT) MASS_STRIKER else MASS_COIN
                    val total = mi + mj
                    val gap = rr - d
                    x[i] -= nx * gap * mj / total
                    y[i] -= ny * gap * mj / total
                    x[j] += nx * gap * mi / total
                    y[j] += ny * gap * mi / total
                    val rel = (vx[i] - vx[j]) * nx + (vy[i] - vy[j]) * ny
                    if (rel <= 0) continue
                    if (j == Carrom.STRIKER_AT && firstHit < 0) firstHit = i
                    // soft touches lose all bounce, so packed coins come to rest
                    val e = if (rel < 12) 0.0 else BOUNCE
                    val p = (1 + e) * rel * mi * mj / total
                    vx[i] -= p / mi * nx
                    vy[i] -= p / mi * ny
                    vx[j] += p / mj * nx
                    vy[j] += p / mj * ny
                    moving = true
                    sound(Sound.Hit, rel)
                }
            }
            if (record && step % (STEPS / 60) == 0) snap()
            if (!moving) break
        }
        on[Carrom.STRIKER_AT] = false
        // a coin pushed a hair into the frame by a neighbour
        for (i in 0 until Carrom.COINS) if (on[i]) {
            x[i] = x[i].coerceIn(Carrom.COIN, Carrom.SIZE - Carrom.COIN)
            y[i] = y[i].coerceIn(Carrom.COIN, Carrom.SIZE - Carrom.COIN)
        }
        return Run(t, pocketed, foul, frames, sounds, firstHit)
    }
}

/**
 * The match between shots. [pending]: who pocketed the queen and must cover it (0 = nobody);
 * [cover]: who covered it.
 */
class CarromState(val table: Table, val turn: Int, val pending: Int = 0, val cover: Int = 0, val shots: Int = 0, val note: String = "", val end: MatchEnd? = null) {

    fun pocketed(player: Int) = 9 - table.left(player)

    /** The rules, after the discs have come to rest. */
    fun after(run: Run): CarromState {
        val t = run.end.copy()
        val me = turn
        val other = 3 - me
        val own = run.pocketed.count { Carrom.owner(it) == me }
        val theirs = run.pocketed.count { Carrom.owner(it) == other }
        val queen = Carrom.QUEEN in run.pocketed
        var pend = pending
        var cov = cover
        var again = false
        val notes = ArrayList<String>()

        fun giveBack(i: Int) = place(t, i)
        fun giveBackOne(player: Int) = (0 until 18).firstOrNull { !t.on[it] && Carrom.owner(it) == player }?.let { giveBack(it) }

        if (run.foul) {
            notes += "ফাউল! স্ট্রাইকার পড়েছে"
            run.pocketed.filter { Carrom.owner(it) == me }.forEach { giveBack(it) }
            giveBackOne(me) // the penalty coin
            if (queen || pend == me) {
                giveBack(Carrom.QUEEN)
                pend = 0
            }
        } else {
            again = own > 0
            if (queen) {
                when {
                    own > 0 && (pocketed(me) > 0 || own > 1) -> {
                        cov = me
                        notes += "রানি কভার হয়েছে!"
                    }
                    pocketed(me) > 0 -> {
                        pend = me
                        again = true
                        notes += "রানি! এবার নিজের একটা গুটি ফেলে কভার করুন"
                    }
                    else -> {
                        giveBack(Carrom.QUEEN)
                        notes += "আগে নিজের গুটি ফেলতে হবে, রানি ফিরল"
                    }
                }
            } else if (pend == me) {
                pend = 0
                if (own > 0) {
                    cov = me
                    notes += "রানি কভার হয়েছে!"
                } else {
                    giveBack(Carrom.QUEEN)
                    notes += "কভার হয়নি, রানি ফিরল"
                }
            }
            if (own > 0 && notes.isEmpty()) notes += if (own > 1) "দারুণ! ${bn(own)}টা গুটি" else "দারুণ! আবার মারুন"
            if (theirs > 0 && own == 0 && notes.isEmpty()) notes += "বিপক্ষের গুটি পড়েছে"
        }

        // The last coin may not go before the queen is covered.
        for (p in intArrayOf(me, other)) {
            if (t.left(p) == 0 && cov == 0) {
                giveBackOne(p)
                notes += "রানি কভার না করে শেষ গুটি ফেলা যায় না"
                if (pend == me && p == me) {
                    giveBack(Carrom.QUEEN)
                    pend = 0
                }
                again = false
            }
        }
        val n = shots + 1
        val end = when {
            t.left(me) == 0 -> MatchEnd(me, "সব গুটি পকেটে" + queenNote(me, cov))
            t.left(other) == 0 -> MatchEnd(other, "সব গুটি পকেটে" + queenNote(other, cov))
            n >= SHOT_LIMIT -> {
                val a = 9 - t.left(1) + if (cov == 1) 3 else 0
                val b = 9 - t.left(2) + if (cov == 2) 3 else 0
                MatchEnd(if (a == b) 0 else if (a > b) 1 else 2, "${bn(SHOT_LIMIT)} শট শেষ, গুটি গুনে ফল")
            }
            else -> null
        }
        // a queen still to be covered only waits while the same player shoots
        if (!again && pend != 0) {
            giveBack(Carrom.QUEEN)
            pend = 0
        }
        return CarromState(t, if (again) me else other, pend, cov, n, notes.joinToString(" · "), end)
    }

    private fun queenNote(winner: Int, cov: Int) = if (cov == winner) " (রানিসহ)" else ""

    private fun bn(n: Int) = n.toString().map { if (it in '0'..'9') "০১২৩৪৫৬৭৮৯"[it - '0'] else it }.joinToString("")

    companion object {
        const val SHOT_LIMIT = 120

        fun initial(first: Int = 1) = CarromState(Table.initial(), first)

        /** Puts disc [i] back as near to the centre as there is room. */
        fun place(t: Table, i: Int) {
            if (t.on[i]) return
            val c = Carrom.SIZE / 2
            val d = Carrom.COIN * 2 + 0.4
            val h = sqrt(3.0) / 2
            val dir = arrayOf(0.0 to -1.0, h to -0.5, h to 0.5, 0.0 to 1.0, -h to 0.5, -h to -0.5)
            for (ring in 0..24) for (k in 0..5) for (part in 0 until max(1, ring)) {
                val (ax, ay) = dir[k]
                val (bx, by) = dir[(k + 2) % 6]
                val half = d / 2
                val px = c + (ax * ring + bx * part) * half
                val py = c + (ay * ring + by * part) * half
                if (px < 60 || py < 60 || px > Carrom.SIZE - 60 || py > Carrom.SIZE - 60) continue
                if (t.free(px, py, Carrom.COIN)) {
                    t.x[i] = px
                    t.y[i] = py
                    t.on[i] = true
                    return
                }
            }
        }
    }
}

class CarromBot(private val random: Random = Random.Default) {
    private class Try(val x: Double, val dx: Double, val dy: Double, val speed: Double, val guess: Double)

    /** [mistake]: how far the aim may be off (about this many radians). */
    private class Skill(val mistake: Double, val looks: Int)

    private fun skill(level: BotLevel) = when (level) {
        BotLevel.Easy -> Skill(0.045, 4)
        BotLevel.Medium -> Skill(0.026, 10)
        BotLevel.Hard -> Skill(0.004, 46)
    }

    fun choose(s: CarromState, level: BotLevel): Shot {
        val k = skill(level)
        val me = s.turn
        val tries = candidates(s).sortedByDescending { it.guess }.take(k.looks).toMutableList()
        val places = spots(s)
        // a few straight shots into the crowd, for when nothing can be pocketed
        repeat(5) {
            val x = places[random.nextInt(places.size)]
            val alive = (0 until Carrom.COINS).filter { i -> s.table.on[i] && Carrom.owner(i) != 3 - me }
            val i = alive[random.nextInt(alive.size)]
            tries += Try(x, s.table.x[i] - x, s.table.y[i] - Carrom.baseY(me), 1700.0 + random.nextInt(800), 0.0)
        }
        var best = tries[0]
        var bestScore = -1e9
        for (c in tries) {
            val score = value(s, s.after(Physics.run(s.table, me, Shot.of(c.x, c.dx, c.dy, c.speed), false)))
            if (score > bestScore) {
                bestScore = score
                best = c
            }
        }
        val off = (random.nextDouble() + random.nextDouble() - 1) * k.mistake * 1.6
        return Shot.of(best.x, best.dx - best.dy * off, best.dy + best.dx * off, best.speed * (1 + (random.nextDouble() - 0.5) * k.mistake * 4))
    }

    private fun value(s: CarromState, n: CarromState): Double {
        val me = s.turn
        val other = 3 - me
        n.end?.let { return if (it.winner == me) 1000.0 else if (it.winner == 0) 0.0 else -1000.0 }
        var v = (n.pocketed(me) - s.pocketed(me)) * 10.0 - (n.pocketed(other) - s.pocketed(other)) * 8.0
        if (n.cover == me && s.cover != me) v += 14
        if (n.pending == me) v += 6
        if (n.turn == me) v += 5
        // coins near a pocket are easy next time
        for (i in 0 until 18) if (n.table.on[i]) {
            val dx = min(n.table.x[i], Carrom.SIZE - n.table.x[i])
            val dy = min(n.table.y[i], Carrom.SIZE - n.table.y[i])
            val near = max(0.0, 1 - sqrt(dx * dx + dy * dy) / 200)
            v += if (Carrom.owner(i) == me) near else -near * 0.6
        }
        return v
    }

    private fun spots(s: CarromState): List<Double> {
        val y = Carrom.baseY(s.turn)
        val free = (Carrom.BASE_MIN..Carrom.BASE_MAX step 29).map { it.toDouble() }.filter { s.table.free(it, y, Carrom.STRIKER) }
        return free.ifEmpty { listOf(Carrom.SIZE / 2) }
    }

    /** Direct shots: striker → coin → pocket. */
    private fun candidates(s: CarromState): List<Try> {
        val t = s.table
        val me = s.turn
        val by = Carrom.baseY(me)
        val out = ArrayList<Try>()
        val targets = (0 until Carrom.COINS).filter { t.on[it] && Carrom.owner(it) != 3 - me && (it != Carrom.QUEEN || s.pocketed(me) > 0) }
        val pockets = arrayOf(18.0 to 18.0, Carrom.SIZE - 18 to 18.0, 18.0 to Carrom.SIZE - 18, Carrom.SIZE - 18 to Carrom.SIZE - 18)
        val places = spots(s)
        for (i in targets) for ((px, py) in pockets) {
            val cx = px - t.x[i]
            val cy = py - t.y[i]
            val cl = sqrt(cx * cx + cy * cy)
            if (cl < 1) continue
            val ux = cx / cl
            val uy = cy / cl
            if (blocked(t, i, t.x[i], t.y[i], px, py, Carrom.COIN)) continue
            // where the striker's centre has to be when they touch
            val gx = t.x[i] - ux * (Carrom.COIN + Carrom.STRIKER)
            val gy = t.y[i] - uy * (Carrom.COIN + Carrom.STRIKER)
            if (gx < Carrom.STRIKER || gy < Carrom.STRIKER || gx > Carrom.SIZE - Carrom.STRIKER || gy > Carrom.SIZE - Carrom.STRIKER) continue
            for (x in places) {
                val sx = gx - x
                val sy = gy - by
                val sl = sqrt(sx * sx + sy * sy)
                if (sl < 1) continue
                val cut = (sx * ux + sy * uy) / sl
                if (cut < 0.25 || blocked(t, i, x, by, gx, gy, Carrom.STRIKER)) continue
                // speed the coin needs to reach the pocket, then what the striker needs for that
                val need = sqrt(2 * 640.0 * cl) + 180
                val hit = need / (cut * 1.4)
                val speed = sqrt(hit * hit + 2 * 560.0 * sl)
                if (speed > Carrom.MAX_SPEED) continue
                out += Try(x, sx, sy, speed, cut * 2 - cl / 500 - sl / 900 + if (i == Carrom.QUEEN) 0.5 else 0.0)
            }
        }
        return out
    }

    private fun blocked(t: Table, skip: Int, ax: Double, ay: Double, bx: Double, by: Double, r: Double): Boolean {
        val dx = bx - ax
        val dy = by - ay
        val l2 = dx * dx + dy * dy
        for (i in 0 until Carrom.COINS) {
            if (i == skip || !t.on[i]) continue
            val k = (((t.x[i] - ax) * dx + (t.y[i] - ay) * dy) / l2).coerceIn(0.0, 1.0)
            val ex = t.x[i] - ax - dx * k
            val ey = t.y[i] - ay - dy * k
            val d = r + Carrom.COIN - 1
            if (ex * ex + ey * ey < d * d) return true
        }
        return false
    }
}
