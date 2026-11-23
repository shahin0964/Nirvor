package com.nirvor.app.games

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nirvor.app.islamic.bnNum
import java.time.LocalDate

enum class Goal { Play, Win, Score, Streak, Online, BeatMedium, BeatHard }

/** One thing to do in an event; the reward goes to [game]'s own coins and gems. */
class Mission(val id: String, val game: GameId, val goal: Goal, val target: Int, val xp: Int, val coins: Int, val gems: Int) {
    val title: String
        get() {
            val n = bnNum(target)
            return when (goal) {
                Goal.Play -> "$n ম্যাচ খেলুন"
                Goal.Win -> "$n ম্যাচ জিতুন"
                Goal.Score -> "মোট ${n}টা ${game.score}"
                Goal.Streak -> "টানা $n ম্যাচ জিতুন"
                Goal.Online -> "অনলাইনে $n ম্যাচ খেলুন"
                Goal.BeatMedium -> "মাঝারি বা কঠিন বটকে $n বার হারান"
                Goal.BeatHard -> "কঠিন বটকে $n বার হারান"
            }
        }
}

/**
 * New events every day and every week. They are worked out from the date alone, so everyone has
 * the same event and no server is needed.
 */
object Events {
    private val DAILY_NAMES = listOf("ভোরের লড়াই", "হাটের দিন", "আড্ডার আসর", "বিকেলের খেলা", "মাঠের রাজা", "চায়ের আড্ডা", "উঠানের খেলা", "বৃষ্টির দিন", "ছুটির মজা", "পাড়ার লিগ")
    private val WEEKLY_NAMES = listOf("পদ্মা কাপ", "বৈশাখী মেলা", "সুন্দরবন অভিযান", "নবান্ন উৎসব", "মেঘনা ট্রফি", "গ্রামবাংলা লিগ", "শীতের পিঠা কাপ", "যমুনা চ্যালেঞ্জ", "পাহাড়ি টুর্নামেন্ট", "রাজশাহী আম কাপ", "চাঁদনি রাত", "বিজয় ট্রফি")

    /** Small fixed random generator: the same on every phone and every app version. */
    private class Dice(seed: Long) {
        private var s = seed * 6364136223846793005L + 1442695040888963407L
        fun next(n: Int): Int {
            s = s * 6364136223846793005L + 1442695040888963407L
            return ((s ushr 33) % n).toInt()
        }
    }

    /** Weeks start on Saturday. */
    fun weekOf(day: Long): Long = Math.floorDiv(day + 5, 7L)

    fun weekEnd(day: Long): Long = weekOf(day) * 7 - 5 + 6

    fun dailyName(day: Long) = DAILY_NAMES[Math.floorMod(day, DAILY_NAMES.size.toLong()).toInt()]

    fun weeklyName(day: Long) = WEEKLY_NAMES[Math.floorMod(weekOf(day), WEEKLY_NAMES.size.toLong()).toInt()]

    /** The game of the day: double coins. */
    fun featured(day: Long): GameId = GameId.entries[Math.floorMod(day, GameId.entries.size.toLong()).toInt()]

    /** The game whose wallet gets the weekly chest. */
    fun weekGame(day: Long): GameId = GameId.entries[Math.floorMod(weekOf(day), GameId.entries.size.toLong()).toInt()]

    private fun scoreTarget(game: GameId, matches: Int) = matches * when (game) {
        GameId.SholoGuti -> 8
        GameId.Carrom -> 5
        GameId.KolomFight -> 2
        GameId.BaghBondi -> 2
    }

    fun daily(day: Long): List<Mission> = GameId.entries.map { game ->
        val dice = Dice(day * 31 + game.ordinal)
        val (goal, target) = when (dice.next(6)) {
            0 -> Goal.Play to 2 + dice.next(2)
            1 -> Goal.Win to 1 + dice.next(2)
            2 -> Goal.Score to scoreTarget(game, 1 + dice.next(2))
            3 -> Goal.Online to 1
            4 -> Goal.BeatMedium to 1
            else -> Goal.Streak to 2
        }
        Mission("d$day-${game.name}", game, goal, target, 30, 60 + dice.next(3) * 20, 1)
    }

    fun weekly(day: Long): List<Mission> {
        val week = weekOf(day)
        return GameId.entries.map { game ->
            val dice = Dice(week * 7919 + game.ordinal + 100_003)
            val (goal, target) = when (dice.next(6)) {
                0 -> Goal.Play to 10 + dice.next(3) * 2
                1 -> Goal.Win to 5 + dice.next(3)
                2 -> Goal.Score to scoreTarget(game, 6 + dice.next(3))
                3 -> Goal.Online to 4 + dice.next(3)
                4 -> Goal.BeatHard to 2 + dice.next(2)
                else -> Goal.Streak to 3 + dice.next(2)
            }
            Mission("w$week-${game.name}", game, goal, target, 120, 250 + dice.next(3) * 50, 6)
        }
    }

    const val CHEST_COINS = 500
    const val CHEST_GEMS = 20

    fun chestId(day: Long) = "w${weekOf(day)}-chest"
}

/** How far the player is in today's and this week's events. */
class EventLog(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("nirvor_game_events", Context.MODE_PRIVATE)

    /** mission id → progress; a taken reward is kept as -1. */
    var progress by mutableStateOf(load())
        private set

    private fun load(): Map<String, Int> = sp.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()

    fun of(m: Mission): Int = (progress[m.id] ?: 0).let { if (it < 0) m.target else it.coerceAtMost(m.target) }

    fun taken(id: String) = progress[id] == -1

    fun canTake(m: Mission) = !taken(m.id) && of(m) >= m.target

    private fun write(changes: Map<String, Int>, day: Long) {
        // only this day and this week are kept
        val d = "d$day-"
        val w = "w${Events.weekOf(day)}-"
        val next = (progress + changes).filterKeys { it.startsWith(d) || it.startsWith(w) }
        progress = next
        sp.edit().clear().apply { next.forEach { (k, v) -> putInt(k, v) } }.apply()
    }

    fun record(game: GameId, outcome: Outcome, bot: BotLevel?, score: Int, streak: Int) {
        val day = LocalDate.now().toEpochDay()
        val won = outcome == Outcome.Win
        val changes = HashMap<String, Int>()
        for (m in Events.daily(day) + Events.weekly(day)) {
            if (m.game != game || taken(m.id)) continue
            val now = progress[m.id] ?: 0
            val next = when (m.goal) {
                Goal.Play -> now + 1
                Goal.Win -> if (won) now + 1 else now
                Goal.Score -> now + score
                // counted inside the event, so an old streak does not finish it at once
                Goal.Streak -> if (now >= m.target) now else if (won) now + 1 else 0
                Goal.Online -> if (bot == null) now + 1 else now
                Goal.BeatMedium -> if (won && bot != null && bot != BotLevel.Easy) now + 1 else now
                Goal.BeatHard -> if (won && bot == BotLevel.Hard) now + 1 else now
            }
            if (next != now) changes[m.id] = next
        }
        if (changes.isNotEmpty()) write(changes, day)
    }

    fun take(m: Mission, profile: GameProfile): Boolean {
        val day = LocalDate.now().toEpochDay()
        val current = Events.daily(day) + Events.weekly(day)
        if (current.none { it.id == m.id } || !canTake(m)) return false
        write(mapOf(m.id to -1), day)
        profile.gift(m.xp, m.coins, m.gems)
        return true
    }

    fun chestReady(day: Long) = !taken(Events.chestId(day)) && Events.weekly(day).all { taken(it.id) }

    fun takeChest(profile: GameProfile): Boolean {
        val day = LocalDate.now().toEpochDay()
        if (!chestReady(day)) return false
        write(mapOf(Events.chestId(day) to -1), day)
        profile.gift(0, Events.CHEST_COINS, Events.CHEST_GEMS)
        return true
    }

    /** Rewards waiting to be taken (for the badge). */
    fun waiting(day: Long): Int = (Events.daily(day) + Events.weekly(day)).count { canTake(it) } + if (chestReady(day)) 1 else 0
}
