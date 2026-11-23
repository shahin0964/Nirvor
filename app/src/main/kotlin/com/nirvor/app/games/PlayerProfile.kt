package com.nirvor.app.games

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The one profile shared by all games: nickname, avatars, sound, and event progress. */
class PlayerProfile private constructor(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("nirvor_player", Context.MODE_PRIVATE)
    val events = EventLog(context)

    /** Shown to other players online; "" until the player writes one. */
    var nick by mutableStateOf(sp.getString("nick", "").orEmpty())
        private set
    var avatar by mutableStateOf(sp.getString("avatar", "").orEmpty())
        private set
    var owned by mutableStateOf(sp.getString("avatars", "").orEmpty().split(',').filter { it.isNotEmpty() }.toSet())
        private set
    var sound by mutableStateOf(sp.getBoolean("sound", true))
        private set

    val avatarItem: StoreItem get() = Stores.avatars.firstOrNull { it.id == avatar } ?: Stores.avatars.first()

    fun ownsAvatar(id: String) = id in owned || id == Stores.avatars.first().id

    fun adopt(ids: List<String>, wear: String?) {
        owned = owned + ids
        if (wear != null && ownsAvatar(wear)) avatar = wear
        sp.edit().putString("avatars", owned.joinToString(",")).putString("avatar", avatar).apply()
    }

    fun rename(value: String) {
        nick = cleanNick(value)
        sp.edit().putString("nick", nick).apply()
    }

    fun switchSound() {
        sound = !sound
        sp.edit().putBoolean("sound", sound).apply()
    }

    companion object {
        private var one: PlayerProfile? = null

        fun of(context: Context): PlayerProfile = one ?: PlayerProfile(context).also { one = it }

        fun cleanNick(s: String) = s.filter { !it.isISOControl() }.trim().take(18)
    }
}
