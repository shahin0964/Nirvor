package com.nirvor.app.games.kolom

import com.nirvor.app.games.BotLevel
import com.nirvor.app.games.MatchEnd
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** কলম ফাইট: two pens on a table; flick yours to push the other one off. Pure Kotlin. */
object Kolom {
    const val W = 640.0
    const val H = 900.0

    /** Half the pen's length (between its round ends) and half its thickness. */
    const val HALF = 92.0
    const val R = 10.0
    const val MAX_SPEED = 1500.0
    const val ROUNDS_TO_WIN = 3
    const val ROUND_LIMIT = 40
}

/** A pen: centre and direction (unit vector along it). */
class Pen(val x: Double, val y: Double, val dx: Double, val dy: Double) {
    val off get() = x < 0 || y < 0 || x > Kolom.W || y > Kolom.H

    fun encode() = listOf(x, y, dx * 1000, dy * 1000).joinToString(",") { Math.round(it * 10).toString() }

    companion object {
        fun decode(text: String): Pen? {
            val p = text.split(',').map { it.toIntOrNull() ?: return null }
            if (p.size != 4 || p.any { abs(it) > 30_000 }) return null
            val l = sqrt(p[2].toDouble() * p[2] + p[3].toDouble() * p[3])
            return if (l < 1) null else Pen(p[0] / 10.0, p[1] / 10.0, p[2] / l, p[3] / l)
        }
    }
}

/** A flick at [at] (−100 … 100, along the pen) with velocity change ([vx], [vy]). */
class Flick(val at: Int, val vx: Int, val vy: Int) {
    fun encode() = "$at,$vx,$vy"

    companion object {
        fun decode(text: String): Flick? {
            val p = text.split(',').map { it.toIntOrNull() ?: return null }
            if (p.size != 3 || p[0] !in -100..100) return null
            val v = p[1].toDouble() * p[1] + p[2].toDouble() * p[2]
            return if (v <= (Kolom.MAX_SPEED + 5) * (Kolom.MAX_SPEED + 5)) Flick(p[0], p[1], p[2]) else null
        }

        fun of(at: Double, dx: Double, dy: Double, speed: Double): Flick {
            val l = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-9)
            val v = speed.coerceIn(0.0, Kolom.MAX_SPEED)
            return Flick(Math.round(at * 100).toInt().coerceIn(-100, 100), (dx / l * v).toInt(), (dy / l * v).toInt())
        }
    }
}

class HitAt(val frame: Int, val power: Float)

/** [frames]: x, y, dx, dy of pen 1 then pen 2, 60 a second. */
class KolomRun(val pens: List<Pen>, val frames: List<FloatArray>, val hits: List<HitAt>)

object KolomPhysics {
    private const val STEPS = 600
    private const val DT = 1.0 / STEPS
    private const val SLOW = 760.0 * DT
    private const val SPIN_SLOW = 9.5 * DT
    private const val BOUNCE = 0.5
    private const val INERTIA = Kolom.HALF * Kolom.HALF / 3 * 1.15

    fun run(pens: List<Pen>, player: Int, f: Flick, record: Boolean): KolomRun {
        val x = doubleArrayOf(pens[0].x, pens[1].x)
        val y = doubleArrayOf(pens[0].y, pens[1].y)
        val dx = doubleArrayOf(pens[0].dx, pens[1].dx)
        val dy = doubleArrayOf(pens[0].dy, pens[1].dy)
        val vx = DoubleArray(2)
        val vy = DoubleArray(2)
        val w = DoubleArray(2)
        val me = player - 1
        val arm = f.at / 100.0 * Kolom.HALF
        vx[me] = f.vx.toDouble()
        vy[me] = f.vy.toDouble()
        w[me] = arm * (dx[me] * f.vy - dy[me] * f.vx) / INERTIA
        val frames = ArrayList<FloatArray>()
        val hits = ArrayList<HitAt>()
        fun snap() {
            frames += FloatArray(8) { k ->
                val i = k / 4
                when (k % 4) {
                    0 -> x[i]
                    1 -> y[i]
                    2 -> dx[i]
                    else -> dy[i]
                }.toFloat()
            }
        }
        if (record) snap()
        var step = 0
        while (step < STEPS * 8) {
            step++
            var moving = false
            for (i in 0..1) {
                val sp = sqrt(vx[i] * vx[i] + vy[i] * vy[i])
                if (sp <= SLOW) {
                    vx[i] = 0.0
                    vy[i] = 0.0
                } else {
                    val k = (sp - SLOW) / sp
                    vx[i] *= k
                    vy[i] *= k
                    moving = true
                }
                if (abs(w[i]) <= SPIN_SLOW) w[i] = 0.0 else {
                    w[i] -= if (w[i] > 0) SPIN_SLOW else -SPIN_SLOW
                    moving = true
                }
                x[i] += vx[i] * DT
                y[i] += vy[i] * DT
                // turn the direction a little (no sin/cos: the same on every phone)
                val a = w[i] * DT
                val nx = dx[i] - dy[i] * a
                val ny = dy[i] + dx[i] * a
                val l = sqrt(nx * nx + ny * ny)
                dx[i] = nx / l
                dy[i] = ny / l
            }
            if (collide(x, y, dx, dy, vx, vy, w)?.also { if (record && it > 25) hits += HitAt(frames.size, (it / 1000).toFloat().coerceIn(0.1f, 1f)) } != null) moving = true
            if (record && step % (STEPS / 60) == 0) snap()
            if (!moving) break
        }
        return KolomRun(List(2) { Pen(x[it], y[it], dx[it], dy[it]) }, frames, hits)
    }

    /** Pushes touching pens apart; the closing speed when they hit, else null. A pen off the table is not there. */
    private fun collide(x: DoubleArray, y: DoubleArray, dx: DoubleArray, dy: DoubleArray, vx: DoubleArray, vy: DoubleArray, w: DoubleArray): Double? {
        for (i in 0..1) if (x[i] < 0 || y[i] < 0 || x[i] > Kolom.W || y[i] > Kolom.H) return null
        // closest points of the two middle lines (s on pen 0, t on pen 1, in −HALF … HALF)
        val rx = x[0] - x[1]
        val ry = y[0] - y[1]
        val b = dx[0] * dx[1] + dy[0] * dy[1]
        val d1 = dx[0] * rx + dy[0] * ry
        val d2 = dx[1] * rx + dy[1] * ry
        val den = 1 - b * b
        val h = Kolom.HALF
        var s = if (den > 1e-6) ((b * d2 - d1) / den).coerceIn(-h, h) else {
            // parallel: the middle of the overlap
            val c = -d1
            ((maxOf(-h, c - h) + minOf(h, c + h)) / 2).coerceIn(-h, h)
        }
        var t = (b * s + d2).coerceIn(-h, h)
        s = (b * t - d1).coerceIn(-h, h)
        t = (b * s + d2).coerceIn(-h, h)
        val px = x[0] + dx[0] * s
        val py = y[0] + dy[0] * s
        val qx = x[1] + dx[1] * t
        val qy = y[1] + dy[1] * t
        var nx = qx - px
        var ny = qy - py
        val dist = sqrt(nx * nx + ny * ny)
        if (dist >= Kolom.R * 2) return null
        if (dist > 1e-6) {
            nx /= dist
            ny /= dist
        } else {
            nx = -dy[0]
            ny = dx[0]
            if (nx * (x[1] - x[0]) + ny * (y[1] - y[0]) < 0) {
                nx = -nx
                ny = -ny
            }
        }
        val gap = (Kolom.R * 2 - dist) / 2
        x[0] -= nx * gap
        y[0] -= ny * gap
        x[1] += nx * gap
        y[1] += ny * gap
        val mx = (px + qx) / 2
        val my = (py + qy) / 2
        val ax = mx - x[0]
        val ay = my - y[0]
        val bx = mx - x[1]
        val by = my - y[1]
        val rel = (vx[0] - w[0] * ay - vx[1] + w[1] * by) * nx + (vy[0] + w[0] * ax - vy[1] - w[1] * bx) * ny
        if (rel <= 0) return null
        val ca = ax * ny - ay * nx
        val cb = bx * ny - by * nx
        val j = (1 + if (rel < 15) 0.0 else BOUNCE) * rel / (2 + ca * ca / INERTIA + cb * cb / INERTIA)
        vx[0] -= j * nx
        vy[0] -= j * ny
        w[0] -= ca * j / INERTIA
        vx[1] += j * nx
        vy[1] += j * ny
        w[1] += cb * j / INERTIA
        return rel
    }
}

/** The match between flicks. [wins]: rounds won by player 1 and 2. [fresh]: a new round has just been laid out. */
class KolomState(val pens: List<Pen>, val turn: Int, val w1: Int = 0, val w2: Int = 0, val flicks: Int = 0, val total: Int = 0, val note: String = "", val fresh: Boolean = true, val end: MatchEnd? = null) {

    fun wins(player: Int) = if (player == 1) w1 else w2

    fun after(pens: List<Pen>): KolomState {
        val me = turn
        val other = 3 - me
        val meOff = pens[me - 1].off
        val otherOff = pens[other - 1].off
        // Own pen off always loses the round, even when both fall.
        val winner = if (meOff) other else if (otherOff) me else 0
        if (winner == 0 && flicks + 1 < Kolom.ROUND_LIMIT) return KolomState(pens, other, w1, w2, flicks + 1, total + 1, "", false)
        val a = w1 + if (winner == 1) 1 else 0
        val b = w2 + if (winner == 2) 1 else 0
        val end = if (a >= Kolom.ROUNDS_TO_WIN || b >= Kolom.ROUNDS_TO_WIN) MatchEnd(if (a > b) 1 else 2, "রাউন্ড ${bn(a)}–${bn(b)}") else null
        val note = when {
            winner == 0 -> "রাউন্ড সমান, আবার শুরু"
            meOff && otherOff -> "দুটোই পড়েছে! যে টোকা দিয়েছে সে হেরেছে"
            meOff -> "নিজের কলম পড়ে গেছে!"
            else -> "কলম ফেলে দিয়েছে!"
        }
        // the one who lost the round begins the next
        return KolomState(start(), if (winner == 0) other else 3 - winner, a, b, 0, total + 1, note, true, end)
    }

    private fun bn(n: Int) = n.toString().map { "০১২৩৪৫৬৭৮৯"[it - '0'] }.joinToString("")

    companion object {
        fun start() = listOf(Pen(Kolom.W / 2, Kolom.H - 210, 1.0, 0.0), Pen(Kolom.W / 2, 210.0, 1.0, 0.0))

        fun initial(first: Int = 1) = KolomState(start(), first)
    }
}

class KolomBot(private val random: Random = Random.Default) {
    fun choose(s: KolomState, level: BotLevel): Flick {
        val (looks, aim) = when (level) {
            BotLevel.Easy -> 8 to 0.16
            BotLevel.Medium -> 24 to 0.11
            BotLevel.Hard -> 160 to 0.015
        }
        val me = s.pens[s.turn - 1]
        val foe = s.pens[2 - s.turn]
        var best: DoubleArray? = null
        var bestScore = -1e9
        repeat(looks) {
            val at = (random.nextDouble() * 2 - 1) * 0.7
            val on = (random.nextDouble() * 2 - 1) * Kolom.HALF
            val tx = foe.x + foe.dx * on - (me.x + me.dx * at * Kolom.HALF)
            val ty = foe.y + foe.dy * on - (me.y + me.dy * at * Kolom.HALF)
            val speed = 350 + random.nextDouble() * (Kolom.MAX_SPEED - 350)
            val v = value(s, KolomPhysics.run(s.pens, s.turn, Flick.of(at, tx, ty, speed), false).pens)
            if (v > bestScore) {
                bestScore = v
                best = doubleArrayOf(at, tx, ty, speed)
            }
        }
        val b = best!!
        val off = (random.nextDouble() + random.nextDouble() - 1) * aim
        return Flick.of(b[0], b[1] - b[2] * off, b[2] + b[1] * off, b[3] * (1 + (random.nextDouble() - 0.5) * aim))
    }

    private fun edge(p: Pen) = min(min(p.x, Kolom.W - p.x), min(p.y, Kolom.H - p.y))

    private fun value(s: KolomState, pens: List<Pen>): Double {
        val me = pens[s.turn - 1]
        val foe = pens[2 - s.turn]
        if (me.off) return -1000.0
        if (foe.off) return 1000 + edge(me)
        return edge(me) * 0.8 - edge(foe)
    }
}
