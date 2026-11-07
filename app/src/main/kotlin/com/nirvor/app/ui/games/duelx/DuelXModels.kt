package com.nirvor.app.ui.games.duelx

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.util.concurrent.TimeUnit

data class DuelXProfile(
    val name: String = "SHAHIN",
    val level: Int = 2,
    val coins: Int = 1625,
    val gems: Int = 5,
    val avatarIndex: Int = 0,
    val selectedTableIndex: Int = 0,
    val soundEnabled: Boolean = true,
    val shotClockSec: Int = 30,
    val botLevel: String = "NORMAL",
    val wins: Int = 1,
    val gamesPlayed: Int = 3,
    val lastDailyBonusClaim: Long = 0L,
    val dailyBonusStreak: Int = 1,
)

data class TableStyle(
    val id: String,
    val name: String,
    val feltColor: Color,
    val borderColor: Color,
    val clothGradient: List<Color>,
)

object DuelXTables {
    val styles = listOf(
        TableStyle("club_teal", "CLUB TEAL", Color(0xFF007A87), Color(0xFF5A3825), listOf(Color(0xFF008B99), Color(0xFF005861))),
        TableStyle("classic_green", "CLASSIC GREEN", Color(0xFF0D7838), Color(0xFF4A2E1B), listOf(Color(0xFF108A40), Color(0xFF0A5828))),
        TableStyle("tournament_blue", "TOURNAMENT BLUE", Color(0xFF155CB0), Color(0xFF2B3A4A), listOf(Color(0xFF196FD4), Color(0xFF0D4380))),
        TableStyle("casino_red", "CASINO RED", Color(0xFF9E1B28), Color(0xFF381014), listOf(Color(0xFFB82030), Color(0xFF75121D))),
        TableStyle("midnight_gold", "MIDNIGHT GOLD", Color(0xFF1A1E29), Color(0xFF8C733E), listOf(Color(0xFF222736), Color(0xFF12151D))),
        TableStyle("royal_blue", "ROYAL BLUE", Color(0xFF103A82), Color(0xFFB89230), listOf(Color(0xFF1447A0), Color(0xFF0C2B61))),
        TableStyle("seriouz", "SERIOUZ", Color(0xFF18667A), Color(0xFF2D3748), listOf(Color(0xFF1F819A), Color(0xFF124A58))),
        TableStyle("pharaoh", "PHARAOH", Color(0xFF254B4B), Color(0xFFD4AF37), listOf(Color(0xFF2E5E5E), Color(0xFF193333))),
    )
}

object DuelXStorage {
    private const val PREFS = "duel_x_game_prefs"

    fun loadProfile(context: Context, defaultName: String? = null): DuelXProfile {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedName = sp.getString("name", null)
        val nameToUse = if (!savedName.isNullOrBlank()) savedName else if (!defaultName.isNullOrBlank()) defaultName else "SHAHIN"

        return DuelXProfile(
            name = nameToUse,
            level = sp.getInt("level", 2),
            coins = sp.getInt("coins", 1625),
            gems = sp.getInt("gems", 5),
            avatarIndex = sp.getInt("avatar_idx", 0),
            selectedTableIndex = sp.getInt("table_idx", 0),
            soundEnabled = sp.getBoolean("sound", true),
            shotClockSec = sp.getInt("shot_clock", 30),
            botLevel = sp.getString("bot_lvl", "NORMAL") ?: "NORMAL",
            wins = sp.getInt("wins", 1),
            gamesPlayed = sp.getInt("games_played", 3),
            lastDailyBonusClaim = sp.getLong("last_claim", 0L),
            dailyBonusStreak = sp.getInt("daily_streak", 1),
        )
    }

    fun saveProfile(context: Context, profile: DuelXProfile) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        sp.edit()
            .putString("name", profile.name)
            .putInt("level", profile.level)
            .putInt("coins", profile.coins)
            .putInt("gems", profile.gems)
            .putInt("avatar_idx", profile.avatarIndex)
            .putInt("table_idx", profile.selectedTableIndex)
            .putBoolean("sound", profile.soundEnabled)
            .putInt("shot_clock", profile.shotClockSec)
            .putString("bot_lvl", profile.botLevel)
            .putInt("wins", profile.wins)
            .putInt("games_played", profile.gamesPlayed)
            .putLong("last_claim", profile.lastDailyBonusClaim)
            .putInt("daily_streak", profile.dailyBonusStreak)
            .apply()
    }

    fun addCoins(context: Context, delta: Int): Int {
        val current = loadProfile(context)
        val updated = current.copy(coins = (current.coins + delta).coerceAtLeast(0))
        saveProfile(context, updated)
        return updated.coins
    }

    fun recordMatchResult(context: Context, won: Boolean, coinsDelta: Int): DuelXProfile {
        val current = loadProfile(context)
        val newWins = if (won) current.wins + 1 else current.wins
        val newPlayed = current.gamesPlayed + 1
        val newLevel = 1 + (newPlayed / 3)
        val newCoins = (current.coins + coinsDelta).coerceAtLeast(0)
        val updated = current.copy(
            wins = newWins,
            gamesPlayed = newPlayed,
            level = newLevel,
            coins = newCoins,
        )
        saveProfile(context, updated)
        return updated
    }

    fun canClaimDailyBonus(profile: DuelXProfile): Boolean {
        val now = System.currentTimeMillis()
        val diff = now - profile.lastDailyBonusClaim
        return diff >= TimeUnit.HOURS.toMillis(24) || profile.lastDailyBonusClaim == 0L
    }

    fun getDailyBonusCountdown(profile: DuelXProfile): String {
        val now = System.currentTimeMillis()
        val diff = now - profile.lastDailyBonusClaim
        val oneDay = TimeUnit.HOURS.toMillis(24)
        if (diff >= oneDay || profile.lastDailyBonusClaim == 0L) return "READY!"

        val remainMs = oneDay - diff
        val hours = TimeUnit.MILLISECONDS.toHours(remainMs)
        val mins = TimeUnit.MILLISECONDS.toMinutes(remainMs) % 60
        val secs = TimeUnit.MILLISECONDS.toSeconds(remainMs) % 60
        return String.format("%02d:%02d:%02d", hours, mins, secs)
    }

    fun claimDailyBonus(context: Context): Pair<Int, Int> {
        val current = loadProfile(context)
        val rewards = listOf(200, 300, 400, 500, 700, 900, 1500)
        val streak = current.dailyBonusStreak.coerceIn(1, 7)
        val rewardAmount = rewards[streak - 1]

        val nextStreak = if (streak >= 7) 1 else streak + 1
        val updated = current.copy(
            coins = current.coins + rewardAmount,
            lastDailyBonusClaim = System.currentTimeMillis(),
            dailyBonusStreak = nextStreak,
        )
        saveProfile(context, updated)
        return Pair(rewardAmount, streak)
    }
}
