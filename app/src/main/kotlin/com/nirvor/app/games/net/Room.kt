package com.nirvor.app.games.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import kotlin.random.Random

class Person(val uid: String, val name: String, val avatar: String) {
    fun json(): JSONObject = JSONObject().put("uid", uid).put("name", name.take(24)).put("avatar", avatar.take(16))

    companion object {
        fun of(o: JSONObject?): Person? = o?.let {
            Person(it.optString("uid"), it.optString("name").filter { c -> !c.isISOControl() }.take(18), it.optString("avatar").take(16))
        }
    }
}

/** How an online match ended early. [winner] is player 1 or 2. */
class EarlyEnd(val winner: Int, val why: String)

/** Finding an opponent: an open room from the list, a room of one's own, or a room by its code. */
class Matchmaker(private val db: Rtdb, private val game: String, private val me: Person, private val timings: Timings = Timings()) {
    /** Shorter in tests. */
    class Timings(val beat: Long = 10_000, val gone: Long = 45_000, val turn: Long = 100_000, val scan: Long = 4_000, val fresh: Long = 40_000)

    private fun newId() = Random.nextInt(100_000, 1_000_000).toString()

    private suspend fun <T> io(block: () -> T): T = runInterruptible(Dispatchers.IO, block)

    /** Makes a room and returns its code. */
    suspend fun create(): String {
        repeat(6) {
            val id = newId()
            val room = JSONObject().put("game", game).put("host", me.json()).put("created", JSONObject().put(".sv", "timestamp"))
            try {
                io { db.put("rooms/$id", room.toString()) }
                return id
            } catch (e: DeniedException) {
                // The code is taken by another room, unless the rules are not there at all.
                val taken = try {
                    io { db.get("rooms/$id/game") } != null
                } catch (d: DeniedException) {
                    it < 2
                }
                if (!taken) throw e
            }
        }
        throw IOException("no free room")
    }

    /** Null when there is no such room, it is full, or it is for another game. */
    suspend fun join(id: String): Session? {
        val room = try {
            io { db.get("rooms/$id") as? JSONObject } ?: return null
        } catch (e: DeniedException) {
            return null
        }
        if (room.optString("game") != game || room.has("guest")) return null
        val host = Person.of(room.optJSONObject("host")) ?: return null
        if (host.uid == me.uid) return null
        try {
            io { db.put("rooms/$id/guest", me.json().toString()) }
        } catch (e: DeniedException) {
            return null // someone was faster
        }
        try {
            io { db.delete("lobby/$game/$id") }
        } catch (e: IOException) {
            // not in the list
        }
        return Session(db, id, 2, host, timings)
    }

    /** Waits until someone joins room [id]. Cancelling removes the room. */
    suspend fun waitForGuest(id: String): Session = guarded(id) {
        while (true) {
            guestOf(id)?.let { return@guarded Session(db, id, 1, it, timings) }
            delay(1500)
        }
        @Suppress("UNREACHABLE_CODE")
        error("")
    }

    /** Quick match: joins an open room or opens one and waits, whichever works first. */
    suspend fun quick(): Session {
        for (id in open()) join(id)?.let { return it }
        val mine = create()
        return guarded(mine) {
            var last = 0L
            while (true) {
                guestOf(mine)?.let { return@guarded Session(db, mine, 1, it, timings) }
                val now = System.currentTimeMillis()
                if (now - last > timings.scan) {
                    last = now
                    io { db.put("lobby/$game/$mine", JSONObject().put("host", me.uid).put("t", JSONObject().put(".sv", "timestamp")).toString()) }
                    // Two players may be waiting at once: only the one with the larger code moves.
                    val other = open().firstOrNull { it < mine }
                    if (other != null) {
                        // Give up the own room first; refused when somebody has just joined it.
                        val free = try {
                            io { db.delete("rooms/$mine") }
                            true
                        } catch (e: DeniedException) {
                            false
                        }
                        if (free) {
                            forget(mine)
                            return@guarded join(other) ?: quick()
                        }
                    }
                }
                delay(1200)
            }
            @Suppress("UNREACHABLE_CODE")
            error("")
        }
    }

    private suspend fun guestOf(id: String): Person? = Person.of(io { db.get("rooms/$id/guest") } as? JSONObject)

    /** Codes of rooms in the list whose host was seen lately, newest first. */
    private suspend fun open(): List<String> {
        val all = io { db.get("lobby/$game", "orderBy=%22t%22&limitToLast=12") } as? JSONObject ?: return emptyList()
        val rows = all.keys().asSequence().mapNotNull { k -> all.optJSONObject(k)?.let { Triple(k, it.optString("host"), it.optLong("t")) } }.toList()
        // compared with the newest entry (server time), not with this phone's clock
        val newest = maxOf(rows.maxOfOrNull { it.third } ?: 0L, System.currentTimeMillis().coerceAtMost((rows.maxOfOrNull { it.third } ?: 0L) + 60_000))
        return rows.filter { it.second != me.uid && newest - it.third < timings.fresh }.sortedByDescending { it.third }.map { it.first }
    }

    private suspend fun forget(id: String) {
        try {
            io { db.delete("lobby/$game/$id") }
        } catch (e: IOException) {
            // it was not in the list
        }
    }

    private suspend fun <T> guarded(id: String, block: suspend () -> T): T = try {
        val r = block()
        forget(id)
        r
    } catch (e: Throwable) {
        withContext(NonCancellable) {
            forget(id)
            try {
                io { db.delete("rooms/$id") }
            } catch (x: IOException) {
                // someone joined in the last moment: tell them we are gone
                try {
                    io { db.put("rooms/$id/end", """{"by":"h","why":"resign"}""") }
                    io { db.put("rooms/$id/left/h", "true") }
                } catch (y: IOException) {
                    // offline
                }
            }
        }
        throw e
    }
}

/** One online match. [me] is 1 (made the room) or 2. */
class Session(private val db: Rtdb, val room: String, val me: Int, val rival: Person, private val t: Matchmaker.Timings) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mine = if (me == 1) "h" else "g"
    private val theirs = if (me == 1) "g" else "h"
    private val lock = Mutex()
    private var count = 0
    private var connection: HttpURLConnection? = null
    private var beatValue = 0L
    private var beatAt = System.nanoTime()
    private var turnAt = System.nanoTime()
    private var finished = false

    /** The opponent's moves, in order. */
    val moves = Channel<String>(Channel.UNLIMITED)

    private val _end = MutableStateFlow<EarlyEnd?>(null)
    val end: StateFlow<EarlyEnd?> = _end

    /** False while the opponent or this phone seems to be offline. */
    private val _steady = MutableStateFlow(true)
    val steady: StateFlow<Boolean> = _steady

    /** Set by the screen: whose turn it is. Starts the opponent's clock. */
    @Volatile
    var rivalTurn = false
        set(value) {
            if (value != field) turnAt = System.nanoTime()
            field = value
        }

    init {
        scope.launch { follow() }
        scope.launch { beat() }
        scope.launch { watch() }
    }

    private fun secondsSince(at: Long) = (System.nanoTime() - at) / 1_000_000

    private suspend fun follow() {
        while (scope.isActive) {
            try {
                runInterruptible(Dispatchers.IO) { db.listen("rooms/$room", { connection = it }, ::onRoom) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // connect again below
            }
            delay(800)
        }
    }

    @Synchronized
    private fun onRoom(v: JSONObject?) {
        if (v == null) return
        val b = v.optJSONObject("beat")?.optLong(theirs) ?: 0L
        if (b != beatValue) {
            beatValue = b
            beatAt = System.nanoTime()
        }
        val all = v.optJSONObject("moves")
        while (all != null) {
            val m = all.optJSONObject(key(count)) ?: break
            count++
            if (m.optString("s") == theirs) {
                turnAt = System.nanoTime()
                moves.trySend(m.optString("d"))
            }
        }
        v.optJSONObject("end")?.let { e ->
            val by = if (e.optString("by") == "h") 1 else 2
            // resign: the one who wrote it lost; timeout / gone: the one who wrote it won
            val why = e.optString("why")
            if (_end.value == null) _end.value = EarlyEnd(if (why == "resign") 3 - by else by, why)
        }
    }

    private fun key(n: Int) = "m" + n.toString().padStart(4, '0')

    private suspend fun beat() {
        while (scope.isActive) {
            try {
                runInterruptible(Dispatchers.IO) { db.put("rooms/$room/beat/$mine", """{".sv":"timestamp"}""") }
                _steady.value = secondsSince(beatAt) < t.beat * 2 + 5_000
            } catch (e: IOException) {
                _steady.value = false
                // own connection is down: the opponent's silence proves nothing
                beatAt = System.nanoTime()
                turnAt = System.nanoTime()
            }
            delay(t.beat)
        }
    }

    private suspend fun watch() {
        while (scope.isActive) {
            delay(1000)
            if (finished || _end.value != null) continue
            val why = when {
                secondsSince(beatAt) > t.gone -> "gone"
                rivalTurn && secondsSince(turnAt) > t.turn -> "timeout"
                else -> continue
            }
            try {
                runInterruptible(Dispatchers.IO) { db.put("rooms/$room/end", """{"by":"$mine","why":"$why"}""") }
            } catch (e: IOException) {
                // the stream tells if the opponent ended it first
            }
        }
    }

    /** Sends a move; tries again for a while when the connection is bad. False = it could not be sent. */
    suspend fun send(data: String): Boolean = lock.withLock {
        val n = synchronized(this) { count++ }
        val body = JSONObject().put("s", mine).put("d", data).toString()
        repeat(20) { attempt ->
            try {
                runInterruptible(Dispatchers.IO) { db.put("rooms/$room/moves/${key(n)}", body) }
                return true
            } catch (e: DeniedException) {
                // An earlier try may have arrived although its answer was lost.
                val there = try {
                    runInterruptible(Dispatchers.IO) { db.get("rooms/$room/moves/${key(n)}") } as? JSONObject
                } catch (x: IOException) {
                    null
                }
                return there != null && there.optString("s") == mine && there.optString("d") == data
            } catch (e: IOException) {
                delay(500L + attempt * 250L)
            }
        }
        false
    }

    /** The game was played to its end: no more clocks. */
    fun finish() {
        finished = true
    }

    /** Leaves the match; before its end this is giving up. */
    fun close() {
        val giveUp = !finished && _end.value == null
        finished = true
        scope.launch(NonCancellable) {
            try {
                if (giveUp) db.put("rooms/$room/end", """{"by":"$mine","why":"resign"}""")
            } catch (e: IOException) {
                // already ended, or offline
            }
            try {
                db.put("rooms/$room/left/$mine", "true")
                if (db.get("rooms/$room/left/$theirs") == true) db.delete("rooms/$room")
            } catch (e: IOException) {
                // an old room is harmless
            }
            connection?.disconnect()
            scope.cancel()
        }
    }
}
