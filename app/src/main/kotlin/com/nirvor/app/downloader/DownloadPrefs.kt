package com.nirvor.app.downloader

import android.content.Context

/** User choices for the download manager (Settings → ডাউনলোড). */
class DownloadPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("nirvor_downloader", Context.MODE_PRIVATE)

    /** How many downloads run at the same time. */
    var parallel: Int
        get() = sp.getInt("parallel", 2).coerceIn(1, 4)
        set(v) {
            sp.edit().putInt("parallel", v.coerceIn(1, 4)).apply()
        }

    /** Connections per file (direct files are split into this many parts; yt-dlp fragments too). */
    var parts: Int
        get() = sp.getInt("parts", 4).coerceIn(1, 8)
        set(v) {
            sp.edit().putInt("parts", v.coerceIn(1, 8)).apply()
        }

    var wifiOnly: Boolean
        get() = sp.getBoolean("wifi_only", false)
        set(v) {
            sp.edit().putBoolean("wifi_only", v).apply()
        }

    var notifyDone: Boolean
        get() = sp.getBoolean("notify_done", true)
        set(v) {
            sp.edit().putBoolean("notify_done", v).apply()
        }

    val lastEngineUpdate: Long
        get() = sp.getLong("engine_last_update", 0L)
}
