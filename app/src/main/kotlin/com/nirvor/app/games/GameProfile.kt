package com.nirvor.app.games

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** Everything saved for one game. */
data class ProfileData(
    val xp: Int = 0,
    val coins: Int = Economy.START_COINS,
    val gems: Int = Economy.START_GEMS,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val captured: Int = 0,
    val onlineWins: Int = 0,
    val owned: Set<String> = emptySet(),
    val equipped: Map<Slot, String> = emptyMap(),
    val counters: Map<String, Int> = mapOf(Counter.UNDO to 3, Counter.HINT to 3, Counter.AIM to 3),
    val dailyDay: Long = 0,
    val dailyStreak: Int = 0,
    val firstWinDay: Long = 0,
) {
    val level get() = Economy.levelOf(xp).first
    val xpIn get() = Economy.levelOf(xp).second
    val xpNeed get() = Economy.need(level)
    val played get() = wins + losses + draws
    fun counter(name: String) = counters[name] ?: 0
}

enum class Outcome { Win, Loss, Draw }

/** What a match gave, for the result window. */
class Reward(val xp: Int, val coins: Int, val gems: Int, val levelUps: Int, val firstWin: Boolean, val featured: Boolean)

enum class BuyResult { Ok, NoMoney, LowLevel, Owned }

/** One game's own data (level, coins, gems, store things). Avatars and the name are shared, see [PlayerProfile]. Main thread only. */
class GameProfile private constructor(context: Context, val game: GameId) {
    val player = PlayerProfile.of(context)
    private val sp = context.applicationContext.getSharedPreferences("nirvor_game_" + game.name, Context.MODE_PRIVATE)

    var data by mutableStateOf(load())
        private set

    init {
        // v2.1.35 kept avatars inside the game
        val (mine, rest) = data.owned.partition { it.startsWith("av_") }
        val worn = data.equipped[Slot.Avatar]
        if (mine.isNotEmpty() || worn != null) {
            player.adopt(mine, worn)
            save(data.copy(owned = rest.toSet(), equipped = data.equipped - Slot.Avatar))
        }
    }

    fun equipped(slot: Slot): StoreItem? {
        if (slot == Slot.Avatar) return player.avatarItem
        val id = data.equipped[slot]
        return Stores.of(game).firstOrNull { it.id == id && it.slot == slot } ?: Stores.default(game, slot)
    }

    fun owns(item: StoreItem): Boolean =
        if (item.slot == Slot.Avatar) player.ownsAvatar(item.id) else item.id in data.owned || Stores.default(game, item.slot)?.id == item.id

    fun buy(item: StoreItem): BuyResult {
        val d = data
        if (item.keepable && owns(item)) return BuyResult.Owned
        if (d.level < item.level) return BuyResult.LowLevel
        if ((if (item.gems) d.gems else d.coins) < item.price) return BuyResult.NoMoney
        var n = if (item.gems) d.copy(gems = d.gems - item.price) else d.copy(coins = d.coins - item.price)
        n = when (item.slot) {
            Slot.Power -> n.copy(counters = n.counters + (item.counter to n.counter(item.counter) + item.amount))
            Slot.Exchange -> n.copy(coins = n.coins + item.amount)
            Slot.Avatar -> {
                player.adopt(listOf(item.id), item.id)
                n
            }
            else -> n.copy(owned = n.owned + item.id, equipped = n.equipped + (item.slot to item.id))
        }
        save(n)
        return BuyResult.Ok
    }

    fun equip(item: StoreItem) {
        if (item.slot == Slot.Avatar) player.adopt(emptyList(), item.id)
        else if (item.keepable && owns(item)) save(data.copy(equipped = data.equipped + (item.slot to item.id)))
    }

    /** Uses one of [counter]; false when there is none left. */
    fun use(counter: String): Boolean {
        val left = data.counter(counter)
        if (left <= 0) return false
        save(data.copy(counters = data.counters + (counter to left - 1)))
        return true
    }

    // Clock moved back: the day already taken is "later" than today, so it is not given twice.
    fun dailyReady(): Boolean = LocalDate.now().toEpochDay() > data.dailyDay

    /** Number (1…) of the daily reward that can be taken now or comes next. */
    fun dailyNumber(): Int {
        val today = LocalDate.now().toEpochDay()
        return if (today - data.dailyDay <= 1) data.dailyStreak + 1 else 1
    }

    fun takeDaily(): Pair<Int, Int>? {
        if (!dailyReady()) return null
        val number = dailyNumber()
        val (coins, gems) = Economy.daily(number)
        save(
            data.copy(
                coins = data.coins + coins,
                gems = data.gems + gems,
                dailyDay = LocalDate.now().toEpochDay(),
                dailyStreak = number,
            )
        )
        return coins to gems
    }

    /**
     * Writes a match against a bot ([bot]) or an online player ([bot] = null) and gives its reward.
     * [short]: it ended almost at once, so there is no real reward and no event progress.
     */
    fun finish(outcome: Outcome, bot: BotLevel?, score: Int, short: Boolean): Reward {
        val d = data
        val today = LocalDate.now().toEpochDay()
        val featured = !short && Events.featured(today) == game
        val first = !short && outcome == Outcome.Win && today > d.firstWinDay
        val winXp = bot?.xp ?: Economy.ONLINE_XP
        val winCoins = bot?.coins ?: Economy.ONLINE_COINS
        val xp = if (short) 0 else when (outcome) {
            Outcome.Win -> winXp
            Outcome.Draw -> winXp / 2
            Outcome.Loss -> if (bot == null) 15 else 8
        }
        val baseCoins = if (short) 0 else when (outcome) {
            Outcome.Win -> winCoins
            Outcome.Draw -> winCoins / 2
            Outcome.Loss -> if (bot == null) 10 else 5
        }
        val coins = if (featured) baseCoins * 2 else baseCoins
        val gems = if (!short && outcome == Outcome.Win) bot?.gems ?: Economy.ONLINE_GEMS else 0
        val boosted = d.counter(Counter.DOUBLE_XP) > 0 && xp > 0
        val gotXp = if (boosted) xp * 2 else xp
        val streak = if (outcome == Outcome.Win) d.streak + 1 else 0
        val won = if (outcome == Outcome.Win) 1 else 0
        val ups = add(
            d.copy(
                wins = d.wins + won,
                losses = d.losses + if (outcome == Outcome.Loss) 1 else 0,
                draws = d.draws + if (outcome == Outcome.Draw) 1 else 0,
                onlineWins = d.onlineWins + if (bot == null) won else 0,
                streak = streak,
                bestStreak = maxOf(d.bestStreak, streak),
                captured = d.captured + score,
                counters = if (boosted) d.counters + (Counter.DOUBLE_XP to d.counter(Counter.DOUBLE_XP) - 1) else d.counters,
                firstWinDay = if (first) today else d.firstWinDay,
            ),
            gotXp,
            coins,
            gems + if (first) Economy.FIRST_WIN_GEMS else 0,
        )
        if (!short) player.events.record(game, outcome, bot, score, streak)
        return Reward(
            gotXp,
            coins + ups * Economy.LEVEL_COINS,
            gems + ups * Economy.LEVEL_GEMS + if (first) Economy.FIRST_WIN_GEMS else 0,
            ups,
            first,
            featured,
        )
    }

    /** Gives XP, coins and gems (with the level-up presents); returns how many levels were gained. */
    private fun add(d: ProfileData, xp: Int, coins: Int, gems: Int): Int {
        val ups = Economy.levelOf(d.xp + xp).first - d.level
        save(
            d.copy(
                xp = d.xp + xp,
                coins = d.coins + coins + ups * Economy.LEVEL_COINS,
                gems = d.gems + gems + ups * Economy.LEVEL_GEMS,
            )
        )
        return ups
    }

    fun gift(xp: Int, coins: Int, gems: Int): Int = add(data, xp, coins, gems)

    // ---------------------------------------------------------------- saving

    private fun save(d: ProfileData) {
        data = d
        sp.edit()
            .putInt("xp", d.xp).putInt("coins", d.coins).putInt("gems", d.gems)
            .putInt("wins", d.wins).putInt("losses", d.losses).putInt("draws", d.draws)
            .putInt("streak", d.streak).putInt("best", d.bestStreak).putInt("captured", d.captured).putInt("onlineWins", d.onlineWins)
            .putString("owned", d.owned.joinToString(","))
            .putString("equipped", d.equipped.entries.joinToString(",") { "${it.key.name}=${it.value}" })
            .putString("counters", d.counters.entries.joinToString(",") { "${it.key}=${it.value}" })
            .putLong("dailyDay", d.dailyDay).putInt("dailyStreak", d.dailyStreak).putLong("firstWin", d.firstWinDay)
            .apply()
    }

    private fun pairs(key: String): List<Pair<String, String>>? =
        sp.getString(key, null)?.split(',')?.filter { '=' in it }?.map { it.substringBefore('=') to it.substringAfter('=') }

    private fun load(): ProfileData {
        val z = ProfileData()
        return ProfileData(
            xp = sp.getInt("xp", z.xp),
            coins = sp.getInt("coins", z.coins),
            gems = sp.getInt("gems", z.gems),
            wins = sp.getInt("wins", 0),
            losses = sp.getInt("losses", 0),
            draws = sp.getInt("draws", 0),
            streak = sp.getInt("streak", 0),
            bestStreak = sp.getInt("best", 0),
            captured = sp.getInt("captured", 0),
            onlineWins = sp.getInt("onlineWins", 0),
            owned = sp.getString("owned", "").orEmpty().split(',').filter { it.isNotEmpty() }.toSet(),
            equipped = pairs("equipped").orEmpty().mapNotNull { (k, v) -> Slot.entries.firstOrNull { it.name == k }?.let { it to v } }.toMap(),
            counters = z.counters + pairs("counters").orEmpty().associate { (k, v) -> k to (v.toIntOrNull() ?: 0) },
            dailyDay = sp.getLong("dailyDay", 0),
            dailyStreak = sp.getInt("dailyStreak", 0),
            firstWinDay = sp.getLong("firstWin", 0),
        )
    }

    companion object {
        private val all = HashMap<GameId, GameProfile>()

        fun of(context: Context, game: GameId): GameProfile = all.getOrPut(game) { GameProfile(context, game) }
    }
}
