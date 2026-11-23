package com.nirvor.app.games

/** Every game of the Game Hub. [score] is what its points are called. */
enum class GameId(val emoji: String, val title: String, val sub: String, val score: String, val colorA: Long, val colorB: Long) {
    SholoGuti("⚫", "১৬ গুটি", "গ্রামবাংলার বুদ্ধির খেলা", "গুটি খান", 0xFFB7791F, 0xFF7B4A12),
    Carrom("🎯", "ক্যারাম", "স্ট্রাইকার আর গুটি", "গুটি ফেলুন", 0xFFE08E45, 0xFFA8551D),
    KolomFight("🖊️", "কলম ফাইট", "টোকা মেরে কলম ফেলে দিন", "কলম ফেলুন", 0xFF3B82F6, 0xFF1D4ED8),
    BaghBondi("🐯", "বাঘবন্দি", "বাঘ বনাম ছাগল", "ছাগল খান বা বাঘ আটকান", 0xFFEF6C3A, 0xFFB93815);

    /** Board games have undo and hint; flick games have a longer aim line. */
    val isBoard get() = this == SholoGuti || this == BaghBondi

    companion object {
        fun of(name: String): GameId? = entries.firstOrNull { it.name == name }
    }
}

enum class BotLevel(val bn: String, val emoji: String, val xp: Int, val coins: Int, val gems: Int) {
    Easy("সহজ", "🌱", 20, 20, 0),
    Medium("মাঝারি", "🔥", 40, 40, 0),
    Hard("কঠিন", "💀", 80, 80, 1),
}

enum class Slot(val title: String) {
    Board("বোর্ড"), Piece("গুটি"), Striker("স্ট্রাইকার"), Pen("কলম"), Avatar("অ্যাভাটার"), Power("পাওয়ার"), Exchange("জেম → কয়েন")
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
    const val ONLINE_XP = 60
    const val ONLINE_COINS = 50
    const val ONLINE_GEMS = 1
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
    const val AIM = "aim"
}

object Stores {
    val avatars = listOf(
        StoreItem("av_smile", Slot.Avatar, "হাসিমুখ", "🙂"),
        StoreItem("av_cool", Slot.Avatar, "স্টাইলিশ", "😎", 100),
        StoreItem("av_farmer", Slot.Avatar, "কৃষক", "👨‍🌾", 150),
        StoreItem("av_lion", Slot.Avatar, "সিংহ", "🦁", 250),
        StoreItem("av_tiger", Slot.Avatar, "বাঘ", "🐯", 400, level = 2),
        StoreItem("av_eagle", Slot.Avatar, "ঈগল", "🦅", 15, gems = true, level = 3),
        StoreItem("av_king", Slot.Avatar, "রাজা", "👑", 30, gems = true, level = 5),
    )

    private val xpPower = StoreItem("pw_xp", Slot.Power, "ডাবল XP ×৫", "⚡", 15, gems = true, counter = Counter.DOUBLE_XP, amount = 5, note = "পরের ৫ ম্যাচে দ্বিগুণ XP")

    private val flickPowers = listOf(
        StoreItem("pw_aim", Slot.Power, "লম্বা এইম ×৫", "🎯", 120, counter = Counter.AIM, amount = 5, note = "৫ ম্যাচে লম্বা এইম লাইন (বটের সাথে)"),
        xpPower,
    )

    private val powers = listOf(
        StoreItem("pw_undo", Slot.Power, "চাল ফেরত ×৩", "↩️", 120, counter = Counter.UNDO, amount = 3, note = "ভুল চাল ফিরিয়ে নিন"),
        StoreItem("pw_hint", Slot.Power, "পরামর্শ ×৩", "💡", 150, counter = Counter.HINT, amount = 3, note = "ভালো একটা চাল দেখাবে"),
        xpPower,
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

    private val bagh = listOf(
        StoreItem("bd_wood", Slot.Board, "কাঠের বোর্ড", colors = listOf(0xFFE9C48F, 0xFF8A5523, 0xFF5A3512)),
        StoreItem("bb_village", Slot.Board, "গ্রামের মাটি", price = 300, colors = listOf(0xFFD3A77A, 0xFF7A4B27, 0xFFFFF3DF)),
        StoreItem("bb_sundarban", Slot.Board, "সুন্দরবন", price = 500, level = 2, colors = listOf(0xFF7DBB72, 0xFF23512B, 0xFF163A1D)),
        StoreItem("bb_night", Slot.Board, "জঙ্গলের রাত", price = 900, level = 3, colors = listOf(0xFF15324A, 0xFF07131F, 0xFF7FD1B9)),
        StoreItem("bb_fire", Slot.Board, "আগুন", price = 25, gems = true, level = 2, colors = listOf(0xFFFFB38A, 0xFF9B2C12, 0xFF6B1A08)),
        StoreItem("bb_gold", Slot.Board, "রাজকীয় সোনা", price = 60, gems = true, level = 5, colors = listOf(0xFFFFE9A8, 0xFFB8860B, 0xFF7A5600)),
        // emoji: the tiger, then the goat
        StoreItem("bp_classic", Slot.Piece, "বাঘ-ছাগল", "🐯🐐"),
        StoreItem("bp_lion", Slot.Piece, "সিংহ-ভেড়া", "🦁🐑", 250),
        StoreItem("bp_wolf", Slot.Piece, "নেকড়ে-খরগোশ", "🐺🐰", 400, level = 2),
        StoreItem("bp_cat", Slot.Piece, "বিড়াল-ইঁদুর", "🐱🐭", 600, level = 2),
        StoreItem("bp_dragon", Slot.Piece, "ড্রাগন-মুরগি", "🐲🐔", 25, gems = true, level = 3),
        StoreItem("bp_fox", Slot.Piece, "শেয়াল-হাঁস", "🦊🦆", 40, gems = true, level = 4),
    ) + avatars + powers + exchange

    /** Carrom board: playing surface, frame, line. Coins: white, black. Striker: body, ring. */
    private val carrom = listOf(
        StoreItem("cb_classic", Slot.Board, "ক্লাসিক", colors = listOf(0xFFF0D2A0, 0xFF5B3216, 0xFF3A2413)),
        StoreItem("cb_birch", Slot.Board, "বার্চ কাঠ", price = 300, colors = listOf(0xFFF7E6C8, 0xFF8B5E34, 0xFF6B3F1D)),
        StoreItem("cb_green", Slot.Board, "সবুজ ফেল্ট", price = 500, level = 2, colors = listOf(0xFF6FAE83, 0xFF1F4D36, 0xFF0F2E20)),
        StoreItem("cb_night", Slot.Board, "মিডনাইট", price = 900, level = 3, colors = listOf(0xFF28324F, 0xFF0B0F1D, 0xFF9DB7FF)),
        StoreItem("cb_rose", Slot.Board, "রোজউড", price = 25, gems = true, level = 2, colors = listOf(0xFFE7B690, 0xFF4A0F17, 0xFF6B1420)),
        StoreItem("cb_royal", Slot.Board, "রাজকীয়", price = 60, gems = true, level = 5, colors = listOf(0xFFFFECB3, 0xFF7A0C2E, 0xFFB8860B)),
        StoreItem("cc_classic", Slot.Piece, "সাদা-কালো", colors = listOf(0xFFF3E3C3, 0xFF2B2622)),
        StoreItem("cc_sea", Slot.Piece, "আকাশি-নেভি", price = 250, colors = listOf(0xFF9BD8FF, 0xFF15305E)),
        StoreItem("cc_candy", Slot.Piece, "ক্যান্ডি", price = 450, level = 2, colors = listOf(0xFFFFC1DA, 0xFF7C2D92)),
        StoreItem("cc_metal", Slot.Piece, "সোনা-ইস্পাত", price = 30, gems = true, level = 3, colors = listOf(0xFFE9B949, 0xFF4B5563)),
        StoreItem("st_classic", Slot.Striker, "আইভরি", colors = listOf(0xFFFFF7E6, 0xFFC0392B)),
        StoreItem("st_blue", Slot.Striker, "নীল তারা", price = 200, colors = listOf(0xFF5AA9FF, 0xFFFFFFFF)),
        StoreItem("st_flag", Slot.Striker, "লাল-সবুজ", price = 350, colors = listOf(0xFF006A4E, 0xFFF42A41)),
        StoreItem("st_neon", Slot.Striker, "নিয়ন", price = 600, level = 2, colors = listOf(0xFF111827, 0xFF39FF88)),
        StoreItem("st_ruby", Slot.Striker, "চুনি", price = 25, gems = true, level = 3, colors = listOf(0xFFE0115F, 0xFFFFD1E1)),
        StoreItem("st_gold", Slot.Striker, "সোনার", price = 50, gems = true, level = 4, colors = listOf(0xFFF2B705, 0xFF7A5600)),
    ) + avatars + flickPowers + exchange

    /** Table: top, edge, line. Pen: body, cap. */
    private val kolom = listOf(
        StoreItem("tb_bench", Slot.Board, "স্কুলের বেঞ্চ", colors = listOf(0xFFCB9560, 0xFF6B3F1D, 0xFFB57D48)),
        StoreItem("tb_desk", Slot.Board, "টিচারের টেবিল", price = 300, colors = listOf(0xFF8C5A3C, 0xFF3E2415, 0xFF774A30)),
        StoreItem("tb_paper", Slot.Board, "খাতার পাতা", price = 500, level = 2, colors = listOf(0xFFFDFBF3, 0xFF9AA7B8, 0xFFA9C8F0)),
        StoreItem("tb_board", Slot.Board, "ব্ল্যাকবোর্ড", price = 900, level = 3, colors = listOf(0xFF2F4F43, 0xFF7B5231, 0xFF3E6456)),
        StoreItem("tb_ice", Slot.Board, "বরফ", price = 25, gems = true, level = 3, colors = listOf(0xFFDDF3FF, 0xFF6FB6DD, 0xFFBFE6FB)),
        StoreItem("pn_blue", Slot.Pen, "নীল কলম", colors = listOf(0xFF2563EB, 0xFF1E3A8A)),
        StoreItem("pn_red", Slot.Pen, "লাল কলম", price = 100, colors = listOf(0xFFDC2626, 0xFF7F1D1D)),
        StoreItem("pn_wood", Slot.Pen, "কাঠ পেন্সিল", price = 200, colors = listOf(0xFFF2B233, 0xFFE879A6)),
        StoreItem("pn_flag", Slot.Pen, "লাল-সবুজ", price = 350, colors = listOf(0xFF006A4E, 0xFFF42A41)),
        StoreItem("pn_marker", Slot.Pen, "মার্কার", price = 600, level = 2, colors = listOf(0xFF22C55E, 0xFF111827)),
        StoreItem("pn_fountain", Slot.Pen, "ফাউন্টেন পেন", price = 25, gems = true, level = 3, colors = listOf(0xFF111827, 0xFFE5A900)),
        StoreItem("pn_gold", Slot.Pen, "সোনার কলম", price = 50, gems = true, level = 4, colors = listOf(0xFFF2B705, 0xFF8A5A00)),
    ) + avatars + flickPowers + exchange

    fun of(game: GameId): List<StoreItem> = when (game) {
        GameId.SholoGuti -> guti
        GameId.Carrom -> carrom
        GameId.KolomFight -> kolom
        GameId.BaghBondi -> bagh
    }

    /** What is owned and in use from the start: the first item of a slot. */
    fun default(game: GameId, slot: Slot): StoreItem? = of(game).firstOrNull { it.slot == slot && it.keepable }
}
