package com.nirvor.app.game.carrom

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Persistent local store for Carrom Pool: Coins, Gems, Inventory, Upgrades and Stats. */
class CarromStorage(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("nirvor_carrom_game", Context.MODE_PRIVATE)

    var coins by mutableIntStateOf(sp.getInt(KEY_COINS, 1500))
        private set

    var gems by mutableIntStateOf(sp.getInt(KEY_GEMS, 30))
        private set

    var playerLevel by mutableIntStateOf(sp.getInt(KEY_LEVEL, 1))
        private set

    var playerXp by mutableIntStateOf(sp.getInt(KEY_XP, 0))
        private set

    var equippedStrikerId by mutableStateOf(sp.getString(KEY_EQUIPPED_STRIKER, "striker_classic") ?: "striker_classic")
        private set

    var equippedBoardId by mutableStateOf(sp.getString(KEY_EQUIPPED_BOARD, "board_classic_rosewood") ?: "board_classic_rosewood")
        private set

    var equippedPuckId by mutableStateOf(sp.getString(KEY_EQUIPPED_PUCK, "puck_classic") ?: "puck_classic")
        private set

    var matchesPlayed by mutableIntStateOf(sp.getInt(KEY_MATCHES_PLAYED, 0))
        private set

    var matchesWon by mutableIntStateOf(sp.getInt(KEY_MATCHES_WON, 0))
        private set

    var bestCombo by mutableIntStateOf(sp.getInt(KEY_BEST_COMBO, 0))
        private set

    var soundEnabled by mutableStateOf(sp.getBoolean(KEY_SOUND, true))
        private set

    var vibrationEnabled by mutableStateOf(sp.getBoolean(KEY_VIBRATION, true))
        private set

    var aimGuideEnabled by mutableStateOf(sp.getBoolean(KEY_AIM_GUIDE, true))
        private set

    // In-memory reactive maps for owned strikers (id -> level) and owned skins
    val strikerLevels = mutableStateMapOf<String, Int>()
    val ownedBoards = mutableStateMapOf<String, Boolean>()
    val ownedPucks = mutableStateMapOf<String, Boolean>()

    init {
        // Load striker levels (Default striker is always owned at least Level 1)
        CarromStoreCatalog.STRIKERS.forEach { item ->
            val lv = sp.getInt("striker_lv_${item.id}", if (item.id == "striker_classic") 1 else 0)
            if (lv > 0) strikerLevels[item.id] = lv
        }
        // Load board ownership
        CarromStoreCatalog.BOARDS.forEach { item ->
            val owned = sp.getBoolean("board_owned_${item.id}", item.id == "board_classic_rosewood")
            if (owned) ownedBoards[item.id] = true
        }
        // Load puck ownership
        CarromStoreCatalog.PUCKS.forEach { item ->
            val owned = sp.getBoolean("puck_owned_${item.id}", item.id == "puck_classic")
            if (owned) ownedPucks[item.id] = true
        }
    }

    fun strikerLevel(strikerId: String): Int = strikerLevels[strikerId] ?: 0

    fun isStrikerOwned(strikerId: String): Boolean = strikerLevel(strikerId) > 0

    fun isBoardOwned(boardId: String): Boolean = ownedBoards[boardId] == true

    fun isPuckOwned(puckId: String): Boolean = ownedPucks[puckId] == true

    fun activeStriker(): CarromStrikerItem =
        CarromStoreCatalog.STRIKERS.firstOrNull { it.id == equippedStrikerId } ?: CarromStoreCatalog.STRIKERS[0]

    fun activeBoard(): CarromBoardSkin =
        CarromStoreCatalog.BOARDS.firstOrNull { it.id == equippedBoardId } ?: CarromStoreCatalog.BOARDS[0]

    fun activePuck(): CarromPuckSkin =
        CarromStoreCatalog.PUCKS.firstOrNull { it.id == equippedPuckId } ?: CarromStoreCatalog.PUCKS[0]

    fun addCoins(amount: Int) {
        val next = (coins + amount).coerceAtLeast(0)
        coins = next
        sp.edit().putInt(KEY_COINS, next).apply()
    }

    fun addGems(amount: Int) {
        val next = (gems + amount).coerceAtLeast(0)
        gems = next
        sp.edit().putInt(KEY_GEMS, next).apply()
    }

    fun buyStriker(item: CarromStrikerItem): Boolean {
        if (isStrikerOwned(item.id)) return false
        if (coins < item.priceCoins || gems < item.priceGems) return false

        coins -= item.priceCoins
        gems -= item.priceGems
        strikerLevels[item.id] = 1
        equippedStrikerId = item.id

        sp.edit()
            .putInt(KEY_COINS, coins)
            .putInt(KEY_GEMS, gems)
            .putInt("striker_lv_${item.id}", 1)
            .putString(KEY_EQUIPPED_STRIKER, item.id)
            .apply()
        return true
    }

    fun upgradeStriker(item: CarromStrikerItem): Boolean {
        val currentLv = strikerLevel(item.id)
        if (currentLv <= 0 || currentLv >= item.maxLevel) return false

        val coinCost = item.upgradeCoinCost(currentLv)
        val gemCost = item.upgradeGemCost(currentLv)
        if (coins < coinCost || gems < gemCost) return false

        coins -= coinCost
        gems -= gemCost
        val nextLv = currentLv + 1
        strikerLevels[item.id] = nextLv

        // Award XP for upgrade
        addXp(120)

        sp.edit()
            .putInt(KEY_COINS, coins)
            .putInt(KEY_GEMS, gems)
            .putInt("striker_lv_${item.id}", nextLv)
            .apply()
        return true
    }

    fun equipStriker(strikerId: String) {
        if (!isStrikerOwned(strikerId)) return
        equippedStrikerId = strikerId
        sp.edit().putString(KEY_EQUIPPED_STRIKER, strikerId).apply()
    }

    fun buyBoard(item: CarromBoardSkin): Boolean {
        if (isBoardOwned(item.id)) return false
        if (coins < item.priceCoins || gems < item.priceGems) return false

        coins -= item.priceCoins
        gems -= item.priceGems
        ownedBoards[item.id] = true
        equippedBoardId = item.id

        sp.edit()
            .putInt(KEY_COINS, coins)
            .putInt(KEY_GEMS, gems)
            .putBoolean("board_owned_${item.id}", true)
            .putString(KEY_EQUIPPED_BOARD, item.id)
            .apply()
        return true
    }

    fun equipBoard(boardId: String) {
        if (!isBoardOwned(boardId)) return
        equippedBoardId = boardId
        sp.edit().putString(KEY_EQUIPPED_BOARD, boardId).apply()
    }

    fun buyPuck(item: CarromPuckSkin): Boolean {
        if (isPuckOwned(item.id)) return false
        if (coins < item.priceCoins || gems < item.priceGems) return false

        coins -= item.priceCoins
        gems -= item.priceGems
        ownedPucks[item.id] = true
        equippedPuckId = item.id

        sp.edit()
            .putInt(KEY_COINS, coins)
            .putInt(KEY_GEMS, gems)
            .putBoolean("puck_owned_${item.id}", true)
            .putString(KEY_EQUIPPED_PUCK, item.id)
            .apply()
        return true
    }

    fun equipPuck(puckId: String) {
        if (!isPuckOwned(puckId)) return
        equippedPuckId = puckId
        sp.edit().putString(KEY_EQUIPPED_PUCK, puckId).apply()
    }

    fun addXp(amount: Int) {
        var totalXp = playerXp + amount
        var curLv = playerLevel
        val xpNeeded = curLv * 300
        if (totalXp >= xpNeeded) {
            totalXp -= xpNeeded
            curLv += 1
            // Level-up reward
            addCoins(250)
            addGems(5)
        }
        playerXp = totalXp
        playerLevel = curLv
        sp.edit().putInt(KEY_XP, playerXp).putInt(KEY_LEVEL, playerLevel).apply()
    }

    fun recordMatch(won: Boolean, combo: Int) {
        matchesPlayed += 1
        if (won) {
            matchesWon += 1
            addCoins(400)
            addGems(2)
            addXp(150)
        } else {
            addCoins(80)
            addXp(40)
        }
        if (combo > bestCombo) {
            bestCombo = combo
            sp.edit().putInt(KEY_BEST_COMBO, bestCombo).apply()
        }
        sp.edit()
            .putInt(KEY_MATCHES_PLAYED, matchesPlayed)
            .putInt(KEY_MATCHES_WON, matchesWon)
            .apply()
    }

    fun toggleSound() {
        soundEnabled = !soundEnabled
        sp.edit().putBoolean(KEY_SOUND, soundEnabled).apply()
    }

    fun toggleVibration() {
        vibrationEnabled = !vibrationEnabled
        sp.edit().putBoolean(KEY_VIBRATION, vibrationEnabled).apply()
    }

    fun toggleAimGuide() {
        aimGuideEnabled = !aimGuideEnabled
        sp.edit().putBoolean(KEY_AIM_GUIDE, aimGuideEnabled).apply()
    }

    fun claimDailyBonus(): Pair<Int, Int> {
        val earnedCoins = 500
        val earnedGems = 10
        addCoins(earnedCoins)
        addGems(earnedGems)
        return earnedCoins to earnedGems
    }

    companion object {
        private const val KEY_COINS = "carrom_coins"
        private const val KEY_GEMS = "carrom_gems"
        private const val KEY_LEVEL = "carrom_level"
        private const val KEY_XP = "carrom_xp"
        private const val KEY_EQUIPPED_STRIKER = "carrom_equipped_striker"
        private const val KEY_EQUIPPED_BOARD = "carrom_equipped_board"
        private const val KEY_EQUIPPED_PUCK = "carrom_equipped_puck"
        private const val KEY_MATCHES_PLAYED = "carrom_matches_played"
        private const val KEY_MATCHES_WON = "carrom_matches_won"
        private const val KEY_BEST_COMBO = "carrom_best_combo"
        private const val KEY_SOUND = "carrom_sound"
        private const val KEY_VIBRATION = "carrom_vibration"
        private const val KEY_AIM_GUIDE = "carrom_aim_guide"
    }
}
