package com.nirvor.app.player

import android.content.Context

/**
 * Stores settings and preferences for Nirvor Player (MX Player style).
 */
class PlayerPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("nirvor_player_prefs", Context.MODE_PRIVATE)

    /** Automatically rotate to landscape when playing any video */
    var autoLandscape: Boolean
        get() = sp.getBoolean("auto_landscape", true)
        set(v) = sp.edit().putBoolean("auto_landscape", v).apply()

    /** Fullscreen immersive mode (hide system status & navigation bars while playing) */
    var immersiveFullscreen: Boolean
        get() = sp.getBoolean("immersive_fullscreen", true)
        set(v) = sp.edit().putBoolean("immersive_fullscreen", v).apply()

    /** Auto-hide player controls timeout in seconds */
    var autoHideTimeoutSec: Int
        get() = sp.getInt("auto_hide_timeout_sec", 4)
        set(v) = sp.edit().putInt("auto_hide_timeout_sec", v).apply()

    /** Double tap seek step in seconds (5, 10, 15, 30) */
    var doubleTapSeekSec: Int
        get() = sp.getInt("double_tap_seek_sec", 10)
        set(v) = sp.edit().putInt("double_tap_seek_sec", v).apply()

    /** Resume playback from last known position */
    var resumePlayback: Boolean
        get() = sp.getBoolean("resume_playback", true)
        set(v) = sp.edit().putBoolean("resume_playback", v).apply()

    /** Continue playing audio in background when app is minimized */
    var backgroundPlay: Boolean
        get() = sp.getBoolean("background_play", false)
        set(v) = sp.edit().putBoolean("background_play", v).apply()

    /** Use hardware decoder by default (HW) */
    var hwDecoderDefault: Boolean
        get() = sp.getBoolean("hw_decoder_default", true)
        set(v) = sp.edit().putBoolean("hw_decoder_default", v).apply()

    /** Enable touch gestures (brightness, volume, seek) */
    var gesturesEnabled: Boolean
        get() = sp.getBoolean("gestures_enabled", true)
        set(v) = sp.edit().putBoolean("gestures_enabled", v).apply()

    /** Show NEW tag on media added in the last 7 days */
    var showNewTag: Boolean
        get() = sp.getBoolean("show_new_tag", true)
        set(v) = sp.edit().putBoolean("show_new_tag", v).apply()

    /** Display duration badge over thumbnail */
    var displayLengthOverThumbnail: Boolean
        get() = sp.getBoolean("display_length_over_thumb", true)
        set(v) = sp.edit().putBoolean("display_length_over_thumb", v).apply()

    /** Pause playback when headset is disconnected */
    var pauseOnHeadsetDisconnect: Boolean
        get() = sp.getBoolean("pause_on_headset_disconnect", true)
        set(v) = sp.edit().putBoolean("pause_on_headset_disconnect", v).apply()

    /** Volume boost up to 200% */
    var volumeBoost: Boolean
        get() = sp.getBoolean("volume_boost", false)
        set(v) = sp.edit().putBoolean("volume_boost", v).apply()

    fun saveLastPosition(mediaId: String, positionMs: Long) {
        if (positionMs > 2000L) {
            sp.edit().putLong("pos_$mediaId", positionMs).apply()
        }
    }

    fun getLastPosition(mediaId: String): Long {
        return sp.getLong("pos_$mediaId", 0L)
    }

    fun clearAllHistory() {
        val editor = sp.edit()
        sp.all.keys.filter { it.startsWith("pos_") }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun resetToDefaults() {
        sp.edit().clear().apply()
    }
}
