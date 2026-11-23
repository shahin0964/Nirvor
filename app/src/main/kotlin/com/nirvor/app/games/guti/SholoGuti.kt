package com.nirvor.app.games.guti

import com.nirvor.app.games.BotLevel
import com.nirvor.app.games.MatchEnd
import kotlin.random.Random

/**
 * ১৬ গুটি: 37 points. A 5×5 board with diagonals in the middle, and a two-row triangle above and
 * below it. Pure Kotlin (no Android), so the rules can be checked on their own.
 */
object GutiBoard {
    const val SIZE = 37

    class Pt(val x: Int, val y: Int)

    /** A jump from a point: over [over], landing on [to]. */
    class Jump(val over: Int, val to: Int)

    /** 0-5 top triangle (y -2, -1), 6-30 the 5×5 board (y 0-4), 31-36 bottom triangle (y 5, 6). */
    val points: List<Pt> = buildList {
        for (x in intArrayOf(0, 2, 4)) add(Pt(x, -2))
        for (x in 1..3) add(Pt(x, -1))
        for (y in 0..4) for (x in 0..4) add(Pt(x, y))
        for (x in 1..3) add(Pt(x, 5))
        for (x in intArrayOf(0, 2, 4)) add(Pt(x, 6))
    }

    private fun grid(x: Int, y: Int) = 6 + y * 5 + x

    /** Every drawn line segment, once. */
    val edges: List<Pair<Int, Int>> = buildList {
        for (y in 0..4) for (x in 0..4) {
            if (x < 4) add(grid(x, y) to grid(x + 1, y))
            if (y < 4) add(grid(x, y) to grid(x, y + 1))
            if ((x + y) % 2 == 0 && y < 4) {
                if (x < 4) add(grid(x, y) to grid(x + 1, y + 1))
                if (x > 0) add(grid(x, y) to grid(x - 1, y + 1))
            }
        }
        // triangles: outer row, inner row, and the point of the board they grow from
        for ((outer, inner, tip) in listOf(Triple(0, 3, grid(2, 0)), Triple(34, 31, grid(2, 4)))) {
            for (i in 0..1) {
                add(outer + i to outer + i + 1)
                add(inner + i to inner + i + 1)
            }
            for (i in 0..2) {
                add(outer + i to inner + i)
                add(inner + i to tip)
            }
        }
    }

    val neighbours: Array<IntArray> = Array(SIZE) { p ->
        edges.mapNotNull { (a, b) -> if (a == p) b else if (b == p) a else null }.toIntArray()
    }

    /** Straight-line jumps along the drawn lines. */
    val jumps: Array<Array<Jump>> = Array(SIZE) { from ->
        val out = ArrayList<Jump>()
        for (over in neighbours[from]) for (to in neighbours[over]) {
            val a = points[from]
            val b = points[over]
            val c = points[to]
            if (to != from && b.x - a.x == c.x - b.x && b.y - a.y == c.y - b.y) out += Jump(over, to)
        }
        out.toTypedArray()
    }
}

/** One whole turn: the piece at [from] goes through [path]; [captured] are the pieces it eats. */
class GutiMove(val from: Int, val path: IntArray, val captured: IntArray) {
    val isCapture get() = captured.isNotEmpty()

    /** How the move is sent to an online opponent. */
    fun encode(): String = from.toString() + ":" + path.joinToString(".")
}

/** A position. Cells hold 0 (empty), 1 (bottom player) or 2 (top player). Never changed in place. */
class GutiState(val cells: ByteArray, val turn: Int, val quiet: Int = 0, val turns: Int = 0) {

    fun count(player: Int): Int = cells.count { it.toInt() == player }

    fun steps(from: Int): List<Int> = GutiBoard.neighbours[from].filter { cells[it].toInt() == 0 }

    fun jumps(from: Int, player: Int = turn): List<GutiBoard.Jump> =
        GutiBoard.jumps[from].filter { cells[it.over].toInt() == 3 - player && cells[it.to].toInt() == 0 }

    /** Moves one hop; [over] = -1 for a plain step. The turn does not pass yet. */
    fun hop(from: Int, to: Int, over: Int): GutiState {
        val c = cells.copyOf()
        c[to] = c[from]
        c[from] = 0
        if (over >= 0) c[over] = 0
        return GutiState(c, turn, quiet, turns)
    }

    fun endTurn(captured: Boolean) = GutiState(cells, 3 - turn, if (captured) 0 else quiet + 1, turns + 1)

    fun play(m: GutiMove): GutiState {
        val c = cells.copyOf()
        c[m.from] = 0
        m.captured.forEach { c[it] = 0 }
        c[m.path.last()] = turn.toByte()
        return GutiState(c, 3 - turn, if (m.isCapture) 0 else quiet + 1, turns + 1)
    }

    /** All legal turns. Eating is a choice, and a chain may be stopped after any jump. */
    fun moves(): List<GutiMove> {
        val caps = ArrayList<GutiMove>()
        val plain = ArrayList<GutiMove>()
        for (p in 0 until GutiBoard.SIZE) {
            if (cells[p].toInt() != turn) continue
            for (s in steps(p)) plain += GutiMove(p, intArrayOf(s), IntArray(0))
            val c = cells.copyOf()
            c[p] = 0
            chain(p, p, c, IntArray(0), IntArray(0), caps)
        }
        caps.sortByDescending { it.captured.size }
        caps.addAll(plain)
        return caps
    }

    /** The legal move written as [text], or null. */
    fun decode(text: String): GutiMove? = moves().firstOrNull { it.encode() == text }

    private fun chain(start: Int, at: Int, c: ByteArray, path: IntArray, eaten: IntArray, out: MutableList<GutiMove>) {
        for (j in GutiBoard.jumps[at]) {
            if (c[j.over].toInt() != 3 - turn || c[j.to].toInt() != 0) continue
            c[j.over] = 0
            val p = path + j.to
            val e = eaten + j.over
            out += GutiMove(start, p, e)
            chain(start, j.to, c, p, e, out)
            c[j.over] = (3 - turn).toByte()
        }
    }

    private fun canMove(): Boolean = (0 until GutiBoard.SIZE).any {
        cells[it].toInt() == turn && (steps(it).isNotEmpty() || jumps(it).isNotEmpty())
    }

    /** The result when the game is over, else null. Asked at the start of a turn. */
    fun result(): MatchEnd? {
        val mine = count(turn)
        val other = count(3 - turn)
        return when {
            mine == 0 -> MatchEnd(3 - turn, "সব গুটি খাওয়া শেষ")
            !canMove() -> MatchEnd(3 - turn, "আর কোনো চাল নেই")
            quiet >= QUIET_LIMIT -> MatchEnd(
                if (mine == other) 0 else if (mine > other) turn else 3 - turn,
                "টানা $QUIET_LIMIT চালে কেউ গুটি খায়নি, গুটি গুনে ফল",
            )
            else -> null
        }
    }

    companion object {
        /** Turns in a row without eating before the pieces are counted. */
        const val QUIET_LIMIT = 60

        fun initial(first: Int = 1): GutiState = GutiState(
            ByteArray(GutiBoard.SIZE) { i -> if (i < 16) 2 else if (i > 20) 1 else 0 },
            first,
        )
    }
}

/** How deep the bot looks, how often it plays a random move (%), and how long it may think. */
private class Skill(val depth: Int, val mistakes: Int, val millis: Long)

private fun skill(level: BotLevel) = when (level) {
    BotLevel.Easy -> Skill(1, 35, 400)
    BotLevel.Medium -> Skill(3, 8, 800)
    BotLevel.Hard -> Skill(7, 0, 1500)
}

/** Alpha-beta search with a time limit. */
class GutiBot(private val random: Random = Random.Default) {
    private class Timeout : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    private var deadline = 0L
    private var nodes = 0

    fun choose(s: GutiState, botLevel: BotLevel): GutiMove? {
        val level = skill(botLevel)
        val all = s.moves()
        if (all.isEmpty()) return null
        if (level.mistakes > 0 && random.nextInt(100) < level.mistakes) return all[random.nextInt(all.size)]
        // Shuffled first so equal moves vary from game to game; captures still go first for pruning.
        val root = all.shuffled(random).sortedByDescending { it.captured.size }.toMutableList()
        var best = root[0]
        deadline = System.nanoTime() + level.millis * 1_000_000L
        try {
            for (depth in 1..level.depth) {
                var alpha = -INF
                var found = root[0]
                for (m in root) {
                    val v = -search(s.play(m), depth - 1, -INF, -alpha, 1)
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

    private fun search(s: GutiState, depth: Int, alpha0: Int, beta: Int, ply: Int): Int {
        if (++nodes and 127 == 0 && System.nanoTime() > deadline) throw Timeout()
        s.result()?.let { r -> return if (r.winner == 0) 0 else if (r.winner == s.turn) WIN - ply else ply - WIN }
        val moves = s.moves()
        // At the horizon only captures go on, so a piece about to be eaten is not missed.
        val quietOnly = depth <= 0
        var alpha = alpha0
        if (quietOnly) {
            val stand = eval(s)
            if (stand >= beta || ply > 24) return stand
            if (stand > alpha) alpha = stand
        }
        for (m in moves) {
            if (quietOnly && !m.isCapture) break
            val v = -search(s.play(m), depth - 1, -beta, -alpha, ply + 1)
            if (v >= beta) return v
            if (v > alpha) alpha = v
        }
        return alpha
    }

    private fun eval(s: GutiState): Int {
        var score = 0
        for (p in 0 until GutiBoard.SIZE) {
            val c = s.cells[p].toInt()
            if (c == 0) continue
            // pieces in well connected places are worth a little more
            val v = 100 + GutiBoard.neighbours[p].size
            score += if (c == s.turn) v else -v
        }
        return score
    }

    private companion object {
        const val INF = 1_000_000
        const val WIN = 100_000
    }
}
