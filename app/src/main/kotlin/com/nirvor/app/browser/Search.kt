package com.nirvor.app.browser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Search engines, address-bar text → web address, and suggestions while typing. */
object Search {

    class Engine(val key: String, val name: String, val query: String, val home: String)

    val engines = listOf(
        Engine("google", "Google", "https://www.google.com/search?q=", "https://www.google.com"),
        Engine("bing", "Bing", "https://www.bing.com/search?q=", "https://www.bing.com"),
        Engine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=", "https://duckduckgo.com"),
        Engine("brave", "Brave Search", "https://search.brave.com/search?q=", "https://search.brave.com"),
        Engine("yahoo", "Yahoo", "https://search.yahoo.com/search?p=", "https://search.yahoo.com"),
        Engine("yandex", "Yandex", "https://yandex.com/search/?text=", "https://yandex.com"),
        Engine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=", "https://www.ecosia.org"),
        Engine("startpage", "Startpage", "https://www.startpage.com/do/search?q=", "https://www.startpage.com"),
    )

    fun engine(key: String): Engine = engines.firstOrNull { it.key == key } ?: engines.first()

    /** Reverse image search ("Search by image"). */
    val imageEngines = listOf(
        "google" to "Google Lens",
        "bing" to "Bing Visual Search",
        "yandex" to "Yandex Images",
    )

    fun imageSearchUrl(imageUrl: String): String {
        val u = Uri.encode(imageUrl)
        return when (BrowserSettings.imageSearch) {
            "bing" -> "https://www.bing.com/images/search?view=detailv2&iss=sbi&q=imgurl:$u"
            "yandex" -> "https://yandex.com/images/search?rpt=imageview&url=$u"
            else -> "https://lens.google.com/uploadbyurl?url=$u"
        }
    }

    private val hostLike = Regex("""^[\p{L}\d-]+(\.[\p{L}\d-]+)+(:\d+)?([/?#].*)?$""")

    /** What the user typed → a web address, or a search with the chosen engine. */
    fun toUrl(input: String, incognito: Boolean): String {
        val t = input.trim()
        if (t.startsWith("http://", true) || t.startsWith("https://", true) ||
            t.startsWith("file://", true) || t.startsWith("about:", true)
        ) return t
        if (t.startsWith("localhost", true)) return "http://$t"
        if (!t.contains(' ') && hostLike.matches(t)) return "https://$t"
        val e = engine(if (incognito) BrowserSettings.searchEngineIncognito else BrowserSettings.searchEngine)
        return e.query + Uri.encode(t)
    }

    class Suggestion(val text: String, val url: String?, val kind: String)

    /**
     * Suggestions for the address bar: matching bookmarks and history first, then the search
     * engine's own suggestions (when turned on).
     */
    suspend fun suggest(context: Context, query: String, incognito: Boolean): List<Suggestion> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val out = mutableListOf<Suggestion>()
        val like = "%$q%"
        val dao = BrowserDatabase.get(context).dao()
        try {
            if (BrowserSettings.bookmarksInSuggestions) {
                dao.searchBookmarks(like, 3).forEach { out += Suggestion(it.title.ifBlank { it.url }, it.url, "bookmark") }
            }
            if (!incognito) {
                dao.searchHistory(like, 4).distinctBy { it.url }.forEach { h ->
                    if (out.none { it.url == h.url }) out += Suggestion(h.title.ifBlank { h.url }, h.url, "history")
                }
            }
        } catch (e: Exception) {
            // database busy
        }
        val online = if (incognito) BrowserSettings.suggestionsIncognito else BrowserSettings.suggestions
        if (online) {
            try {
                val conn = (URL("https://suggestqueries.google.com/complete/search?client=firefox&q=" + Uri.encode(q))
                    .openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val arr = JSONArray(text).optJSONArray(1)
                if (arr != null) {
                    for (i in 0 until minOf(arr.length(), 6)) {
                        val s = arr.optString(i)
                        if (s.isNotBlank() && out.none { it.text.equals(s, true) }) out += Suggestion(s, null, "search")
                    }
                }
            } catch (e: Exception) {
                // offline: local suggestions only
            }
        }
        out
    }

    /** User agents for "User Agent" / "Request desktop site". */
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    const val IPHONE_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    const val ANDROID_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    fun displayHost(url: String): String =
        (Uri.parse(url).host ?: url).lowercase(Locale.US).removePrefix("www.").removePrefix("m.")
}
