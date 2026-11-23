package com.nirvor.app.games.net

import android.content.Context
import com.nirvor.app.BuildConfig
import com.nirvor.app.games.GameId
import com.nirvor.app.games.PlayerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Anonymous sign-in. The same player keeps the same id on this phone. */
object Online {
    private var idToken = ""
    private var uid = ""
    private var expiresAt = 0L

    /** False in a build made without google-services.json. */
    val available: Boolean get() = BuildConfig.FIREBASE_KEY.isNotEmpty() && BuildConfig.FIREBASE_URL.isNotEmpty()

    private fun post(url: String, type: String, body: String): JSONObject {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", type)
        }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode !in 200..299) throw if (c.responseCode in 400..403) DeniedException() else IOException("sign-in ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    @Synchronized
    private fun token(context: Context): String {
        if (idToken.isNotEmpty() && System.currentTimeMillis() < expiresAt) return idToken
        val sp = context.getSharedPreferences("nirvor_online", Context.MODE_PRIVATE)
        val key = BuildConfig.FIREBASE_KEY
        val saved = sp.getString("refresh", null)
        var refresh = ""
        if (saved != null) {
            try {
                val o = post(
                    "https://securetoken.googleapis.com/v1/token?key=$key",
                    "application/x-www-form-urlencoded",
                    "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(saved, "UTF-8"),
                )
                idToken = o.getString("id_token")
                uid = o.getString("user_id")
                refresh = o.getString("refresh_token")
                expiresAt = System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000 - 300_000
            } catch (e: DeniedException) {
                idToken = "" // that account is gone: make a new one
            }
        }
        if (refresh.isEmpty()) {
            val o = post("https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$key", "application/json", """{"returnSecureToken":true}""")
            idToken = o.getString("idToken")
            uid = o.getString("localId")
            refresh = o.getString("refreshToken")
            expiresAt = System.currentTimeMillis() + o.optLong("expiresIn", 3600) * 1000 - 300_000
        }
        sp.edit().putString("refresh", refresh).apply()
        return idToken
    }

    /** Signs in (when needed) and gets ready to look for an opponent. */
    suspend fun matchmaker(context: Context, game: GameId, player: PlayerProfile): Matchmaker = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        token(app)
        val db = Rtdb(BuildConfig.FIREBASE_URL) { token(app) }
        Matchmaker(db, game.name, Person(uid, player.nick.ifBlank { "খেলোয়াড়" }, player.avatarItem.emoji))
    }
}
