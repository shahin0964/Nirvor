package com.nirvor.app.games.bagh

import com.nirvor.app.games.BotLevel
import com.nirvor.app.games.MatchEnd
import kotlin.random.Random

/** বাঘবন্দি: 4 tigers against 20 goats on a 5×5 board with diagonals. Pure Kotlin. */
object BaghBoard {
    const val SIZE = 25

    fun x(p: Int) = p % 5
    fun y(p: Int) = p / 5

    val edges: List<Pair<Int, Int>> = buildList {
        for (y in 0..4) for (x in 0..4) {
            val p = y * 5 + x
            if (x < 4) add(p to p + 1)
            if (y < 4) add(p to p + 5)
            if ((x + y) % 2 == 0 && y < 4) {
                if (x < 4) add(p to p + 6)
                if (x > 0) add(p to p + 4)
            }
        }
    }

    val neighbours: Array<IntArray> = Array(SIZE) { p ->
        edges.mapNotNull { (a, b) -> if (a == p) b else if (b == p) a else null }.toIntArray()
    }

    /** Straight jumps: pairs (over, to). */
    val jumps: Array<List<Pair<Int, Int>>> = Array(SIZE) { from ->
        neighbours[from].toList().mapNotNull { over ->
            val tx = 2 * x(over) - x(from)
            val ty = 2 * y(over) - y(from)
            if (tx in 0..4 && ty in 0..4 && ty * 5 + tx in neighbours[over]) over to ty * 5 + tx else null
        }
    }
}

const val GOAT = 1
const val TIGER = 2

/** [from] = -1: a goat is placed. [eats] = -1: nothing is eaten. */
data class BaghMove(val from: Int, val to: Int, val eats: Int = -1) {
    fun encode() = "$from.$to"
}

/** A position. Player 1 has the goats and begins; player 2 has the tigers. Never changed in place. */
class BaghState(
    val cells: ByteArray,
    val turn: Int = GOAT,
    val hand: Int = GOATS,
    val eaten: Int = 0,
    val quiet: Int = 0,
    val turns: Int = 0,
) {
    fun movesFrom(p: Int): List<BaghMove> {
        if (cells[p].toInt() != turn || (turn == GOAT && hand > 0)) return emptyList()
        val out = ArrayList<BaghMove>()
        if (turn == TIGER) {
            for ((over, to) in BaghBoard.jumps[p]) if (cells[over].toInt() == GOAT && cells[to].toInt() == 0) out += BaghMove(p, to, over)
        }
        for (n in BaghBoard.neighbours[p]) if (cells[n].toInt() == 0) out += BaghMove(p, n)
        return out
    }

    fun moves(): List<BaghMove> =
        if (turn == GOAT && hand > 0) (0 until BaghBoard.SIZE).filter { cells[it].toInt() == 0 }.map { BaghMove(-1, it) }
        else (0 until BaghBoard.SIZE).flatMap { movesFrom(it) }

    fun decode(text: String): BaghMove? = moves().firstOrNull { it.encode() == text }

    fun play(m: BaghMove): BaghState {
        val c = cells.copyOf()
        if (m.from >= 0) c[m.from] = 0
        if (m.eats >= 0) c[m.eats] = 0
        c[m.to] = turn.toByte()
        val placed = m.from < 0
        return BaghState(
            c,
            3 - turn,
            hand - if (placed) 1 else 0,
            eaten + if (m.eats >= 0) 1 else 0,
            if (placed || m.eats >= 0) 0 else quiet + 1,
            turns + 1,
        )
    }

    fun tigerStuck(p: Int): Boolean =
        BaghBoard.neighbours[p].none { cells[it].toInt() == 0 } &&
            BaghBoard.jumps[p].none { (over, to) -> cells[over].toInt() == GOAT && cells[to].toInt() == 0 }

    fun stuckTigers(): Int = (0 until BaghBoard.SIZE).count { cells[it].toInt() == TIGER && tigerStuck(it) }

    fun result(): MatchEnd? = when {
        eaten >= EAT_TO_WIN -> MatchEnd(TIGER, "বাঘ ${EAT_TO_WIN}টা ছাগল খেয়েছে")
        stuckTigers() == 4 -> MatchEnd(GOAT, "সব বাঘ আটকে গেছে")
        turn == GOAT && moves().isEmpty() -> MatchEnd(TIGER, "ছাগলের আর চাল নেই")
        quiet >= QUIET_LIMIT -> MatchEnd(0, "টানা $QUIET_LIMIT চালে কিছু হয়নি")
        else -> null
    }

    companion object {
        const val GOATS = 20
        const val EAT_TO_WIN = 5
        const val QUIET_LIMIT = 60

        fun initial() = BaghState(ByteArray(BaghBoard.SIZE).also { c -> for (p in intArrayOf(0, 4, 20, 24)) c[p] = TIGER.toByte() })
    }
}

class BaghBot(private val random: Random = Random.Default) {
    private class Timeout : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    private var deadline = 0L
    private var nodes = 0

    fun choose(s: BaghState, level: BotLevel): BaghMove? {
        val (depth, mistakes, millis) = when (level) {
            BotLevel.Easy -> Triple(1, 35, 300L)
            BotLevel.Medium -> Triple(3, 8, 800L)
            BotLevel.Hard -> Triple(9, 0, 1500L)
        }
        val root = s.moves().shuffled(random).sortedByDescending { it.eats }.toMutableList()
        if (root.isEmpty()) return null
        if (random.nextInt(100) < mistakes) return root[random.nextInt(root.size)]
        var best = root[0]
        deadline = System.nanoTime() + millis * 1_000_000
        try {
            for (d in 1..depth) {
                var alpha = -INF
                var found = root[0]
                for (m in root) {
                    val v = -search(s.play(m), d - 1, -INF, -alpha, 1)
                    if (v > alpha) {
                        alpha = v
                        found = m
                    }
                }
                best = found
                root.remove(found)
                root.add(0, found)
                if (alpha > WIN / 2 || alpha < -WIN / 2) break
            }
        } catch (e: Timeout) {
            // keep the best move of the last finished depth
        }
        return best
    }

    private fun search(s: BaghState, depth: Int, alpha0: Int, beta: Int, ply: Int): Int {
        if (++nodes and 63 == 0 && System.nanoTime() > deadline) throw Timeout()
        s.result()?.let { r -> return if (r.winner == 0) 0 else if (r.winner == s.turn) WIN - ply else ply - WIN }
        if (depth <= 0) return eval(s)
        var alpha = alpha0
        for (m in s.moves().sortedByDescending { it.eats }) {
            val v = -search(s.play(m), depth - 1, -beta, -alpha, ply + 1)
            if (v >= beta) return v
            if (v > alpha) alpha = v
        }
        return alpha
    }

    /** From the side to move. Goats eaten, goats in danger, and how free the tigers are. */
    private fun eval(s: BaghState): Int {
        var tiger = s.eaten * 220
        for (p in 0 until BaghBoard.SIZE) {
            if (s.cells[p].toInt() != TIGER) continue
            var free = 0
            for (n in BaghBoard.neighbours[p]) if (s.cells[n].toInt() == 0) free++
            var bites = 0
            for ((over, to) in BaghBoard.jumps[p]) if (s.cells[over].toInt() == GOAT && s.cells[to].toInt() == 0) bites++
            tiger += free * 6 + bites * if (s.turn == TIGER) 90 else 25
            if (free == 0 && bites == 0) tiger -= 110
        }
        return if (s.turn == TIGER) tiger else -tiger
    }

    private companion object {
        const val INF = 1_000_000
        const val WIN = 100_000
    }
}
