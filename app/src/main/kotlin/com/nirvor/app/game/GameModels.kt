package com.nirvor.app.game

/** Overview and catalog info for games available in Nirvor Game Hub. */
enum class GameCatalog(
    val id: String,
    val titleEn: String,
    val titleBn: String,
    val tag: String,
    val description: String,
    val isReady: Boolean = false,
) {
    Carrom(
        id = "carrom",
        titleEn = "Carrom Pool",
        titleBn = "ক্যারম পুল",
        tag = "জনপ্রিয় বোর্ড গেম",
        description = "রিয়েল ফিজিক্স, ডটেড এইমিং গাইড, স্ট্রাইকার স্লাইডার, আপগ্রেড স্টোর এবং AI বট সহ পূর্ণাঙ্গ ক্যারম গেম।",
        isReady = true,
    ),
    SholoGuti(
        id = "sholo_guti",
        titleEn = "Sholo Guti",
        titleBn = "১৬ গুটি",
        tag = "ঐতিহ্যবাহী রণকৌশল",
        description = "গ্রামবাংলার ঐতিহ্যবাহী বুদ্ধির লড়াই। প্রতিপক্ষের গুটি কেটে জয়লাভ করুন।",
        isReady = false,
    ),
    PenFight(
        id = "pen_fight",
        titleEn = "Pen Fight 3D",
        titleBn = "পেন ফাইট",
        tag = "নস্টালজিক অ্যাকশন",
        description = "স্কুল বেঞ্চের নস্টালজিয়া! পেন ফ্লিক করে প্রতিপক্ষের কলম ফেলে দিন।",
        isReady = false,
    ),
    Ludo(
        id = "ludo",
        titleEn = "Ludo Classic",
        titleBn = "লুডো ক্লাসিক",
        tag = "পারিবারিক আনন্দ",
        description = "ডাইস রোল ও টোকেন হোম রানের ক্লাসিক ২-৪ প্লেয়ার গেম।",
        isReady = false,
    ),
    RetroSnake(
        id = "retro_snake",
        titleEn = "Retro Snake",
        titleBn = "রেট্রো স্নেক",
        tag = "চিরচেনা আর্কেড",
        description = "নোকিয়ার ক্লাসিক সাপ খেলা। আপেল খান আর হাই-স্কোর গড়ুন।",
        isReady = false,
    ),
    Chess(
        id = "chess",
        titleEn = "Chess Master",
        titleBn = "দাবা",
        tag = "বুদ্ধিমত্তার খেলা",
        description = "বৈধ চাল, চেক/চেকমেট ও কম্পিউটার বটের সাথে দাবার লড়াই।",
        isReady = false,
    ),
}
