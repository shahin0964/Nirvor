package com.nirvor.app.ui.tools.gametools.models

import androidx.compose.ui.graphics.Color

enum class GameId(
    val titleBn: String,
    val titleEn: String,
    val subtitleBn: String,
    val descriptionBn: String,
    val playerModeDesc: String,
    val accentColor: Color,
    val iconEmoji: String,
) {
    Carrom(
        titleBn = "ক্যারম",
        titleEn = "Carrom 3D",
        subtitleBn = "বোর্ড ফিজিক্স ও স্ট্রাইকার ম্যাচ",
        descriptionBn = "আসল কাঠের বোর্ড, মসৃণ স্ট্রাইকার ও ১৯টি কয়েন নিয়ে খাঁটি ৩ডি ক্যারম বোর্ড ফিজিক্স।",
        playerModeDesc = "১ বা ২ খেলোয়াড় • কম্পিউটার এআই",
        accentColor = Color(0xFFD97706),
        iconEmoji = "🎯",
    ),
    SholoGuti(
        titleBn = "১৬ গুটি",
        titleEn = "16 Guti 3D",
        subtitleBn = "ঐতিহ্যবাহী কৌশল ও বুদ্ধির চাল",
        descriptionBn = "বাংলার শতবর্ষ প্রাচীন বুদ্ধির খেলা। প্রতিপক্ষে ১৬টি গুটি দখল করে সম্পূর্ণ বোর্ড জয়ের রোমাঞ্চ।",
        playerModeDesc = "২ খেলোয়াড় • স্মার্ট এআই",
        accentColor = Color(0xFF059669),
        iconEmoji = "⚔️",
    ),
    PenFight(
        titleBn = "কলম মারামারি",
        titleEn = "Pen Fight 3D",
        subtitleBn = "টেবিল বাউন্ডারি ও কলম স্লাইড ফিজিক্স",
        descriptionBn = "স্কুল জীবনের জনপ্রিয় কলম লড়াই। ৩ডি ডেস্কে নিখুঁত কোণে ফ্লিক করে প্রতিপক্ষের কলম টেবিল থেকে নিচে ফেলুন।",
        playerModeDesc = "১ বনাম ১ • টেবিল নকআউট",
        accentColor = Color(0xFF2563EB),
        iconEmoji = "✏️",
    ),
    BaghBandi(
        titleBn = "বাগদান",
        titleEn = "Bagh-Bandi 3D",
        subtitleBn = "বাঘ বনাম ছাগলের বুদ্ধির লড়াই",
        descriptionBn = "বাঘের শিকারী ঝাঁপ ও ছাগলের ঐক্যবদ্ধ ঘেরাও কৌশলের ক্লাসিক ঐতিহ্যবাহী দ্বৈরথ।",
        playerModeDesc = "বাঘ বনাম ছাগল • ২ প্লেয়ার / বট",
        accentColor = Color(0xFFDC2626),
        iconEmoji = "🐅",
    ),
    DaagKata(
        titleBn = "দাগ কাটা",
        titleEn = "Dots & Boxes 3D",
        subtitleBn = "দাগ কেটে ঘর বানানোর মস্তিষ্ক খেলা",
        descriptionBn = "বিন্দুগুলোর মাঝে ৩ডি দাগ টেনে চারপাশের ঘর নিজের দখলে নিন। সর্বোচ্চ ঘর পূরণকারীই বিজয়ী!",
        playerModeDesc = "১ বা ২ খেলোয়াড় • ব্রেন পাজল",
        accentColor = Color(0xFF7C3AED),
        iconEmoji = "📐",
    ),
}

enum class GamePlayMode(val labelBn: String) {
    VsBot("কম্পিউটার এআই (Vs Bot)"),
    PassAndPlay("২ খেলোয়াড় (Pass & Play)"),
}

enum class Difficulty(val labelBn: String, val multiplier: Float) {
    Easy("সহজ", 1.0f),
    Medium("মাঝারি", 1.5f),
    Hard("কঠিন", 2.0f),
}

data class GameToolsPlayerProfile(
    val name: String = "SHAHIN",
    val level: Int = 1,
    val xp: Int = 0,
    val coins: Int = 1000,
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
)
