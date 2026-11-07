package com.nirvor.app.ui.games

import android.content.Context
import androidx.compose.ui.graphics.Color

data class GameInfo(
    val id: String,
    val title: String,
    val bn: String,
    val description: String,
    val emoji: String,
    val category: String,
    val badge: String? = null,
    val accent: Color,
    val gradient: List<Color>,
)

object GameInventory {
    val games: List<GameInfo> = listOf(
        GameInfo(
            id = "2048",
            title = "2048 Classic",
            bn = "২০৪৮ পাজল",
            description = "টাইলস সোয়াইপ করে সংখ্যা মিলিয়ে ২০৪৮ তৈরি করুন। দারুণ ব্রেইন গেম!",
            emoji = "🔢",
            category = "পাজল",
            badge = "জনপ্রিয়",
            accent = Color(0xFFF59E0B),
            gradient = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
        ),
        GameInfo(
            id = "snake",
            title = "Retro Snake",
            bn = "রেট্রো সাপ খেলা",
            description = "নস্টালজিক সাপ খেলা! সুস্বাদু আপেল খান, বড় হন এবং দেওয়াল এড়িয়ে চলুন।",
            emoji = "🐍",
            category = "আর্কেড",
            badge = "ক্লাসিক",
            accent = Color(0xFF10B981),
            gradient = listOf(Color(0xFF10B981), Color(0xFF059669)),
        ),
        GameInfo(
            id = "tictactoe",
            title = "Tic Tac Toe",
            bn = "টিক ট্যাক টো (XO)",
            description = "বুদ্ধিদীপ্ত AI এর বিরুদ্ধে একা খেলুন অথবা বন্ধুকে সাথে নিয়ে ২ জন খেলোয়াড় মোডে খেলুন।",
            emoji = "❌",
            category = "স্ট্র্যাটেজি",
            badge = "২ প্লেয়ার",
            accent = Color(0xFF3B82F6),
            gradient = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
        ),
        GameInfo(
            id = "memory",
            title = "Memory Match",
            bn = "স্মৃতি মেলানো কার্ড",
            description = "লুকানো কার্ড উল্টে একজোড়া একরকম ছবি মিলান। একাগ্রতা ও স্মৃতিশক্তি বৃদ্ধির খেলা।",
            emoji = "🃏",
            category = "ব্রেইন",
            badge = "মস্তিষ্কচর্চা",
            accent = Color(0xFF8B5CF6),
            gradient = listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)),
        ),
        GameInfo(
            id = "minesweeper",
            title = "Minesweeper",
            bn = "মাইনসুইপার",
            description = "লুকানো মাইন বা বোমা চিহ্নিত করুন এবং সব নিরাপদ ঘর বুদ্ধিমত্তার সাথে উন্মোচন করুন।",
            emoji = "💣",
            category = "লজিক",
            badge = "চ্যালেঞ্জ",
            accent = Color(0xFFEF4444),
            gradient = listOf(Color(0xFFEF4444), Color(0xFFB91C1C)),
        ),
    )

    fun getById(id: String): GameInfo? = games.find { it.id.equals(id, ignoreCase = true) }
}

object GameStorage {
    private const val PREF_NAME = "nirvor_games_prefs"

    fun getHighScore(context: Context, gameId: String): Int {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getInt("high_score_$gameId", 0)
    }

    /**
     * Saves new score if higher than current high score.
     * Returns true if a new high score was set.
     */
    fun saveHighScore(context: Context, gameId: String, score: Int): Boolean {
        if (score <= 0) return false
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = sp.getInt("high_score_$gameId", 0)
        return if (score > current) {
            sp.edit().putInt("high_score_$gameId", score).apply()
            true
        } else {
            false
        }
    }

    fun getBestTime(context: Context, gameId: String): Int {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getInt("best_time_$gameId", 9999)
    }

    fun saveBestTime(context: Context, gameId: String, timeSec: Int): Boolean {
        if (timeSec <= 0) return false
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val current = sp.getInt("best_time_$gameId", 9999)
        return if (timeSec < current) {
            sp.edit().putInt("best_time_$gameId", timeSec).apply()
            true
        } else {
            false
        }
    }
}
