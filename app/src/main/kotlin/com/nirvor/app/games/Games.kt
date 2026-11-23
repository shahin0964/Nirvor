package com.nirvor.app.games

/** Every game of the Game Hub. [ready] = false: comes in a later update (shown with "শীঘ্রই"). */
enum class GameId(val emoji: String, val title: String, val sub: String, val colorA: Long, val colorB: Long, val ready: Boolean) {
    SholoGuti("⚫", "১৬ গুটি", "গ্রামবাংলার বুদ্ধির খেলা", 0xFFB7791F, 0xFF7B4A12, true),
    Carrom("🎯", "ক্যারাম", "স্ট্রাইকার আর গুটি", 0xFFE08E45, 0xFFA8551D, false),
    KolomFight("🖊️", "কলম ফাইট", "টোকা মেরে কলম ফেলে দিন", 0xFF3B82F6, 0xFF1D4ED8, false),
    BaghBondi("🐯", "বাঘবন্দি", "বাঘ বনাম ছাগল", 0xFFEF6C3A, 0xFFB93815, false);

    companion object {
        fun of(name: String): GameId? = entries.firstOrNull { it.name == name }
    }
}

enum class Slot(val title: String) {
    Board("বোর্ড"), Piece("গুটি"), Avatar("অ্যাভাটার"), Power("পাওয়ার"), Exchange("জেম → কয়েন")
}

/**
 * One thing in a game's store. Board / Piece / Avatar are bought once and then used; Power adds
 * [amount] to the counter [counter]; Exchange gives [amount] coins.
 */
class StoreItem(
    val id: String,
    val slot: Slot,
    val name: String,
    val emoji: String = "",
    val price: Int = 0,
    val gems: Boolean = false,
    val level: Int = 1,
    val counter: String = "",
    val amount: Int = 0,
    val note: String = "",
    val colors: List<Long> = emptyList(),
) {
    val keepable get() = slot != Slot.Power && slot != Slot.Exchange
}

/** Level, XP and reward rules, the same for every game (each game keeps its own numbers). */
object Economy {
    const val START_COINS = 200
    const val START_GEMS = 10
    const val LEVEL_COINS = 100
    const val LEVEL_GEMS = 5
    const val FIRST_WIN_GEMS = 2
    private val DAILY = listOf(50 to 0, 75 to 0, 100 to 2, 125 to 0, 150 to 3, 175 to 0, 250 to 10)

    /** XP needed to go from [level] to the next. */
    fun need(level: Int): Int = 100 + 50 * (level - 1)

    /** Total XP → (level, XP inside that level). */
    fun levelOf(xp: Int): Pair<Int, Int> {
        var level = 1
        var left = xp
        while (left >= need(level)) {
            left -= need(level)
            level++
        }
        return level to left
    }

    /** Coins and gems of daily reward number [streak] (1 = first day; repeats every 7 days). */
    fun daily(streak: Int): Pair<Int, Int> = DAILY[(streak - 1).coerceAtLeast(0) % DAILY.size]
}

/** Counter names of the things a Power item adds to. */
object Counter {
    const val UNDO = "undo"
    const val HINT = "hint"
    const val DOUBLE_XP = "double_xp"
}

object Stores {
    private val avatars = listOf(
        StoreItem("av_smile", Slot.Avatar, "হাসিমুখ", "🙂"),
        StoreItem("av_cool", Slot.Avatar, "স্টাইলিশ", "😎", 100),
        StoreItem("av_farmer", Slot.Avatar, "কৃষক", "👨‍🌾", 150),
        StoreItem("av_lion", Slot.Avatar, "সিংহ", "🦁", 250),
        StoreItem("av_tiger", Slot.Avatar, "বাঘ", "🐯", 400, level = 2),
        StoreItem("av_eagle", Slot.Avatar, "ঈগল", "🦅", 15, gems = true, level = 3),
        StoreItem("av_king", Slot.Avatar, "রাজা", "👑", 30, gems = true, level = 5),
    )

    private val powers = listOf(
        StoreItem("pw_undo", Slot.Power, "চাল ফেরত ×৩", "↩️", 120, counter = Counter.UNDO, amount = 3, note = "ভুল চাল ফিরিয়ে নিন"),
        StoreItem("pw_hint", Slot.Power, "পরামর্শ ×৩", "💡", 150, counter = Counter.HINT, amount = 3, note = "ভালো একটা চাল দেখাবে"),
        StoreItem("pw_xp", Slot.Power, "ডাবল XP ×৫", "⚡", 15, gems = true, counter = Counter.DOUBLE_XP, amount = 5, note = "পরের ৫ ম্যাচে দ্বিগুণ XP"),
    )

    private val exchange = listOf(
        StoreItem("ex_s", Slot.Exchange, "৩০০ কয়েন", "🪙", 10, gems = true, amount = 300),
        StoreItem("ex_l", Slot.Exchange, "১০০০ কয়েন", "💰", 30, gems = true, amount = 1000),
    )

    /** Board colours: background, frame, line. Piece colours: player 1, player 2. */
    private val guti = listOf(
        StoreItem("bd_wood", Slot.Board, "কাঠের বোর্ড", colors = listOf(0xFFE9C48F, 0xFF8A5523, 0xFF5A3512)),
        StoreItem("bd_soil", Slot.Board, "মাটির উঠান", price = 300, colors = listOf(0xFFC99A6B, 0xFF7A4B27, 0xFFFFF3DF)),
        StoreItem("bd_field", Slot.Board, "সবুজ মাঠ", price = 500, level = 2, colors = listOf(0xFF8CCB7E, 0xFF2F6B35, 0xFF1E4A23)),
        StoreItem("bd_night", Slot.Board, "রাতের আকাশ", price = 900, level = 3, colors = listOf(0xFF1E2A52, 0xFF0B1230, 0xFF8FB4FF)),
        StoreItem("bd_marble", Slot.Board, "মার্বেল", price = 25, gems = true, level = 2, colors = listOf(0xFFF3F4F6, 0xFF9CA3AF, 0xFF4B5563)),
        StoreItem("bd_gold", Slot.Board, "রাজকীয় সোনা", price = 60, gems = true, level = 5, colors = listOf(0xFFFFE9A8, 0xFFB8860B, 0xFF7A5600)),
        StoreItem("pc_classic", Slot.Piece, "লাল-কালো", colors = listOf(0xFFD6352B, 0xFF262B33)),
        StoreItem("pc_seed", Slot.Piece, "তেঁতুল বিচি", price = 200, colors = listOf(0xFF6B2F1A, 0xFFF2DDB4)),
        StoreItem("pc_flag", Slot.Piece, "লাল-সবুজ", price = 350, colors = listOf(0xFFF42A41, 0xFF006A4E)),
        StoreItem("pc_sea", Slot.Piece, "নীল-কমলা", price = 600, level = 2, colors = listOf(0xFF1D7FE0, 0xFFF28C28)),
        StoreItem("pc_ruby", Slot.Piece, "চুনি-পান্না", price = 25, gems = true, level = 3, colors = listOf(0xFFE0115F, 0xFF12A66A)),
        StoreItem("pc_royal", Slot.Piece, "সোনা-রুপা", price = 50, gems = true, level = 4, colors = listOf(0xFFE5A900, 0xFFB9C0CA)),
    ) + avatars + powers + exchange

    fun of(game: GameId): List<StoreItem> = when (game) {
        GameId.SholoGuti -> guti
        else -> emptyList()
    }

    /** What is owned and in use from the start: the first item of a slot. */
    fun default(game: GameId, slot: Slot): StoreItem? = of(game).firstOrNull { it.slot == slot && it.keepable }
}
