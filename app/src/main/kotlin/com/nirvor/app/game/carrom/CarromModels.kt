package com.nirvor.app.game.carrom

import androidx.compose.ui.graphics.Color

enum class CarromDiscType {
    Striker, White, Black, Queen
}

data class CarromDisc(
    val id: Int,
    val type: CarromDiscType,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    val radius: Float,
    var isPocketed: Boolean = false,
    var sinkScale: Float = 1f,
    var sinkAlpha: Float = 1f,
)

enum class CarromPlayMode(val labelBn: String, val subtitleBn: String) {
    TwoPlayer("২ প্লেয়ার (পাস অ্যান্ড প্লে)", "একই ফোনে দুই বন্ধু সামনাসামনি খেলুন"),
    VsBot("কম্পিউটার বটের সাথে খেলুন", "স্মার্ট AI অপোনেন্টের সাথে একক ম্যাচ"),
    Practice("অনুশীলন মোড (ফ্রি শট)", "শট ও অ্যাঙ্গেল নিখুঁত করার প্র্যাকটিস"),
}

enum class CarromTurn(val labelBn: String) {
    Player1("প্লেয়ার ১ এর চাল"),
    Player2("প্লেয়ার ২ এর চাল"),
}

/** Individual striker with upgradable attributes: Power, Aim Line, Time / Control. */
data class CarromStrikerItem(
    val id: String,
    val nameEn: String,
    val nameBn: String,
    val descriptionBn: String,
    val basePower: Int, // 1..100
    val baseAim: Int,   // 1..100
    val baseTime: Int,  // 1..100
    val priceCoins: Int,
    val priceGems: Int,
    val primaryColor: Color,
    val ringColor: Color,
    val coreColor: Color,
    val maxLevel: Int = 5,
) {
    fun power(level: Int): Int = (basePower + (level - 1) * 7).coerceAtMost(100)
    fun aim(level: Int): Int = (baseAim + (level - 1) * 7).coerceAtMost(100)
    fun time(level: Int): Int = (baseTime + (level - 1) * 6).coerceAtMost(100)

    fun upgradeCoinCost(currentLevel: Int): Int = currentLevel * 450
    fun upgradeGemCost(currentLevel: Int): Int = (currentLevel - 1) * 4
}

data class CarromBoardSkin(
    val id: String,
    val nameEn: String,
    val nameBn: String,
    val priceCoins: Int,
    val priceGems: Int,
    val woodBorderColor: Color,
    val surfaceColor: Color,
    val ringColor: Color,
    val pocketColor: Color,
    val foulLineColor: Color,
)

data class CarromPuckSkin(
    val id: String,
    val nameEn: String,
    val nameBn: String,
    val priceCoins: Int,
    val priceGems: Int,
    val whiteDiscColor: Color,
    val blackDiscColor: Color,
    val queenDiscColor: Color,
)

/** Catalog presets of items available in the Carrom Store. */
object CarromStoreCatalog {

    val STRIKERS = listOf(
        CarromStrikerItem(
            id = "striker_classic",
            nameEn = "Classic Wood",
            nameBn = "ক্লাসিক উড",
            descriptionBn = "প্রাকৃতিক সেগুন কাঠের স্ট্যান্ডার্ড ব্যালান্সড স্ট্রাইকার।",
            basePower = 60,
            baseAim = 55,
            baseTime = 50,
            priceCoins = 0,
            priceGems = 0,
            primaryColor = Color(0xFFD97706),
            ringColor = Color(0xFFFEF3C7),
            coreColor = Color(0xFF92400E),
        ),
        CarromStrikerItem(
            id = "striker_iron_fist",
            nameEn = "Iron Crusher",
            nameBn = "আয়রন ক্রাশার",
            descriptionBn = "ভারী ধাতব স্ট্রাইকার। প্রচণ্ড গতি ও ব্রেক শটে কয়েন ছত্রভঙ্গ করে।",
            basePower = 82,
            baseAim = 60,
            baseTime = 48,
            priceCoins = 800,
            priceGems = 0,
            primaryColor = Color(0xFF475569),
            ringColor = Color(0xFFE2E8F0),
            coreColor = Color(0xFF1E293B),
        ),
        CarromStrikerItem(
            id = "striker_emerald_falcon",
            nameEn = "Emerald Falcon",
            nameBn = "এমেরাল্ড ফ্যালকন",
            descriptionBn = "পান্না পাথরের স্মুথ স্ট্রাইকার। অতি দীর্ঘ নিখুঁত এইমিং গাইডলাইন।",
            basePower = 70,
            baseAim = 88,
            baseTime = 65,
            priceCoins = 1500,
            priceGems = 10,
            primaryColor = Color(0xFF059669),
            ringColor = Color(0xFFA7F3D0),
            coreColor = Color(0xFF065F46),
        ),
        CarromStrikerItem(
            id = "striker_neon_cyber",
            nameEn = "Neon Cyber",
            nameBn = "নিয়ন সাইবার",
            descriptionBn = "উচ্চ প্রযুক্তির ফ্রিকশনহীন গ্লাইড স্ট্রাইকার। দীর্ঘ স্থিতিশীল নিয়ন্ত্রণ।",
            basePower = 78,
            baseAim = 82,
            baseTime = 90,
            priceCoins = 2500,
            priceGems = 25,
            primaryColor = Color(0xFF2563EB),
            ringColor = Color(0xFF93C5FD),
            coreColor = Color(0xFF1D4ED8),
        ),
        CarromStrikerItem(
            id = "striker_gold_maharaja",
            nameEn = "Gold Maharaja",
            nameBn = "গোল্ড মহারাজা",
            descriptionBn = "কিংবদন্তি সোনার তৈরি স্ট্রাইকার। সর্বোচ্চ পাওয়ার, এইম ও মসৃণতা!",
            basePower = 94,
            baseAim = 94,
            baseTime = 85,
            priceCoins = 5000,
            priceGems = 50,
            primaryColor = Color(0xFFF59E0B),
            ringColor = Color(0xFFFEF08A),
            coreColor = Color(0xFFB45309),
        ),
    )

    val BOARDS = listOf(
        CarromBoardSkin(
            id = "board_classic_rosewood",
            nameEn = "Classic Rosewood",
            nameBn = "রোজউড ক্লাসিক",
            priceCoins = 0,
            priceGems = 0,
            woodBorderColor = Color(0xFF451A03),
            surfaceColor = Color(0xFFFDE68A),
            ringColor = Color(0xFFB45309),
            pocketColor = Color(0xFF1E293B),
            foulLineColor = Color(0xFFDC2626),
        ),
        CarromBoardSkin(
            id = "board_royal_blue",
            nameEn = "Royal Blue Arena",
            nameBn = "রয়্যাল ব্লু অ্যারেনা",
            priceCoins = 1200,
            priceGems = 10,
            woodBorderColor = Color(0xFF1E293B),
            surfaceColor = Color(0xFF1E3A8A),
            ringColor = Color(0xFF60A5FA),
            pocketColor = Color(0xFF0F172A),
            foulLineColor = Color(0xFFF59E0B),
        ),
        CarromBoardSkin(
            id = "board_emerald_felt",
            nameEn = "Emerald Velvet",
            nameBn = "এমেরাল্ড ভেলভেট",
            priceCoins = 2200,
            priceGems = 20,
            woodBorderColor = Color(0xFF064E3B),
            surfaceColor = Color(0xFF065F46),
            ringColor = Color(0xFF34D399),
            pocketColor = Color(0xFF022C22),
            foulLineColor = Color(0xFFFDE047),
        ),
        CarromBoardSkin(
            id = "board_dark_mahogany",
            nameEn = "Dark Mahogany Pro",
            nameBn = "ডার্ক মেহগনি প্রো",
            priceCoins = 3500,
            priceGems = 35,
            woodBorderColor = Color(0xFF29150B),
            surfaceColor = Color(0xFFE7C496),
            ringColor = Color(0xFF78350F),
            pocketColor = Color(0xFF0F172A),
            foulLineColor = Color(0xFF991B1B),
        ),
    )

    val PUCKS = listOf(
        CarromPuckSkin(
            id = "puck_classic",
            nameEn = "Classic Ivory & Ebony",
            nameBn = "ক্লাসিক আইভরি",
            priceCoins = 0,
            priceGems = 0,
            whiteDiscColor = Color(0xFFFFFBEB),
            blackDiscColor = Color(0xFF1F2937),
            queenDiscColor = Color(0xFFDC2626),
        ),
        CarromPuckSkin(
            id = "puck_glossy_marble",
            nameEn = "Glossy Marble Discs",
            nameBn = "গ্লসি মার্বেল",
            priceCoins = 1000,
            priceGems = 8,
            whiteDiscColor = Color(0xFFF8FAFC),
            blackDiscColor = Color(0xFF334155),
            queenDiscColor = Color(0xFFE11D48),
        ),
        CarromPuckSkin(
            id = "puck_neon_cyber",
            nameEn = "Neon Glow Discs",
            nameBn = "নিয়ন গ্লো ডিস্ক",
            priceCoins = 2000,
            priceGems = 18,
            whiteDiscColor = Color(0xFF67E8F9),
            blackDiscColor = Color(0xFF64748B),
            queenDiscColor = Color(0xFFF43F5E),
        ),
    )
}
