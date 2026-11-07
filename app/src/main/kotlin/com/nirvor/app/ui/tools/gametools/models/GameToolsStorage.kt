package com.nirvor.app.ui.tools.gametools.models

import android.content.Context
import android.content.SharedPreferences

object GameToolsStorage {
    private const val PREFS_NAME = "gametools_prefs"
    private const val KEY_NAME = "gt_player_name"
    private const val KEY_XP = "gt_player_xp"
    private const val KEY_COINS = "gt_player_coins"
    private const val KEY_MATCHES = "gt_matches_played"
    private const val KEY_WINS = "gt_matches_won"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadProfile(context: Context, defaultName: String = "SHAHIN"): GameToolsPlayerProfile {
        val prefs = getPrefs(context)
        val name = prefs.getString(KEY_NAME, defaultName) ?: defaultName
        val xp = prefs.getInt(KEY_XP, 0)
        val coins = prefs.getInt(KEY_COINS, 1000)
        val matches = prefs.getInt(KEY_MATCHES, 0)
        val wins = prefs.getInt(KEY_WINS, 0)
        val level = (xp / 100) + 1

        return GameToolsPlayerProfile(
            name = name,
            level = level,
            xp = xp,
            coins = coins,
            matchesPlayed = matches,
            matchesWon = wins,
        )
    }

    fun recordMatch(context: Context, won: Boolean, xpEarned: Int, coinsEarned: Int) {
        val prefs = getPrefs(context)
        val currentXp = prefs.getInt(KEY_XP, 0) + xpEarned
        val currentCoins = prefs.getInt(KEY_COINS, 1000) + coinsEarned
        val matches = prefs.getInt(KEY_MATCHES, 0) + 1
        val wins = prefs.getInt(KEY_WINS, 0) + (if (won) 1 else 0)

        prefs.edit()
            .putInt(KEY_XP, currentXp)
            .putInt(KEY_COINS, currentCoins)
            .putInt(KEY_MATCHES, matches)
            .putInt(KEY_WINS, wins)
            .apply()
    }
}
