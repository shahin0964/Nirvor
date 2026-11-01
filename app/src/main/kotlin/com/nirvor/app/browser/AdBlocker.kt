package com.nirvor.app.browser

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Blocks ads, trackers and pop-under pages by host name (the site and all its sub-domains),
 * plus simple text filters the user adds. Sources:
 *  - a built-in list (assets/adblock_hosts.txt),
 *  - downloadable lists (hosts files such as StevenBlack / AdAway, or EasyList's "||host^" rules),
 *  - the user's own hosts / filters, and sites where blocking is turned off.
 */
object AdBlocker {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var hosts: HashSet<String> = HashSet()
    @Volatile private var filters: List<String> = emptyList()
    @Volatile private var allowSites: HashSet<String> = HashSet()

    /** Ready-made lists the user can switch on in "Adblock hosts & filters". */
    val presets = listOf(
        "StevenBlack (বিজ্ঞাপন + ট্র্যাকার)" to "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "AdAway" to "https://adaway.org/hosts.txt",
        "EasyList" to "https://easylist.to/easylist/easylist.txt",
        "Peter Lowe's list" to "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
    )

    fun reload(context: Context) {
        val app = context.applicationContext
        scope.launch { load(app) }
    }

    private suspend fun load(context: Context) {
        val h = HashSet<String>(4096)
        val f = ArrayList<String>()
        try {
            context.assets.open("adblock_hosts.txt").bufferedReader().useLines { lines ->
                lines.forEach { line -> parseLine(line, h, f) }
            }
        } catch (e: Exception) {
            // no built-in list
        }
        val dao = BrowserDatabase.get(context).dao()
        try {
            dao.allAdLists().filter { it.enabled }.forEach { list ->
                val file = listFile(context, list.id)
                if (file.exists()) file.bufferedReader().useLines { lines -> lines.forEach { parseLine(it, h, null) } }
            }
        } catch (e: Exception) {
            // unreadable list: skip
        }
        val allow = HashSet<String>()
        try {
            dao.allAdRules().forEach { r ->
                when (r.kind) {
                    "host" -> h += r.rule.lowercase(Locale.US)
                    "filter" -> f += r.rule.lowercase(Locale.US)
                    "allow" -> allow += r.rule.lowercase(Locale.US).removePrefix("www.")
                }
            }
        } catch (e: Exception) {
            // database not ready
        }
        hosts = h
        filters = f
        allowSites = allow
    }

    /** Reads one line of a hosts file / EasyList / plain list. */
    private fun parseLine(raw: String, h: HashSet<String>, f: MutableList<String>?) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || line.startsWith("[")) return
        if (line.startsWith("@@") || line.contains("##") || line.contains("#@#") || line.contains("#?#")) return
        if (line.startsWith("||")) {
            // EasyList network rule: ||ads.example.com^  (only whole-site rules are used)
            val body = line.removePrefix("||").substringBefore('$')
            val host = body.substringBefore('^').substringBefore('/')
            if (body.endsWith("^") && !host.contains('*') && host.contains('.')) h += host.lowercase(Locale.US)
            return
        }
        val parts = line.split(Regex("\\s+"))
        val host = when {
            parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::1") -> parts[1]
            parts.size == 1 && !line.contains('/') && line.contains('.') -> line
            parts.size == 1 && f != null -> {
                f += line.lowercase(Locale.US)
                return
            }
            else -> return
        }.lowercase(Locale.US)
        if (host == "localhost" || host == "0.0.0.0" || host.startsWith("local")) return
        h += host
    }

    /** True when [url] should not be loaded. [pageHost] is the site the user is on. */
    fun isBlocked(url: Uri, pageHost: String?): Boolean {
        if (pageHost != null && isAllowed(pageHost)) return false
        val host = url.host?.lowercase(Locale.US) ?: return false
        // The page's own site is never blocked by the host list.
        if (pageHost != null && sameSite(host, pageHost)) return matchesFilter(url)
        if (hostListed(host)) return true
        return matchesFilter(url)
    }

    /** Whole-page check (pop-unders / redirects to ad sites). */
    fun isAdPage(url: Uri): Boolean {
        val host = url.host?.lowercase(Locale.US) ?: return false
        return hostListed(host)
    }

    private fun hostListed(host: String): Boolean {
        val set = hosts
        var h = host
        while (true) {
            if (set.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0 || h.indexOf('.', dot + 1) < 0) return false
            h = h.substring(dot + 1)
        }
    }

    private fun matchesFilter(url: Uri): Boolean {
        val list = filters
        if (list.isEmpty()) return false
        val s = url.toString().lowercase(Locale.US)
        return list.any { it.isNotEmpty() && s.contains(it.trim('*')) }
    }

    private fun isAllowed(pageHost: String): Boolean {
        val set = allowSites
        if (set.isEmpty()) return false
        var h = pageHost.lowercase(Locale.US).removePrefix("www.")
        while (true) {
            if (set.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    private fun sameSite(a: String, b: String): Boolean {
        fun base(x: String): String {
            val p = x.split('.')
            return if (p.size <= 2) x else p.takeLast(2).joinToString(".")
        }
        return base(a) == base(b.lowercase(Locale.US))
    }

    val hostCount: Int get() = hosts.size

    // ---------------------------------------------------------------- lists

    private fun listFile(context: Context, id: Long) = File(File(context.filesDir, "adlists").apply { mkdirs() }, "$id.txt")

    /** Downloads a list and returns how many rules it has. */
    suspend fun updateList(context: Context, list: AdListEntity): Int = withContext(Dispatchers.IO) {
        val conn = (URL(list.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Nirvor")
        }
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val file = listFile(context, list.id)
            val tmp = File(file.path + ".tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            val h = HashSet<String>()
            tmp.bufferedReader().useLines { lines -> lines.forEach { parseLine(it, h, null) } }
            tmp.renameTo(file)
            BrowserDatabase.get(context).dao().setAdListUpdated(list.id, h.size, System.currentTimeMillis())
            load(context)
            h.size
        } finally {
            conn.disconnect()
        }
    }

    fun deleteListFile(context: Context, id: Long) {
        listFile(context, id).delete()
    }
}
