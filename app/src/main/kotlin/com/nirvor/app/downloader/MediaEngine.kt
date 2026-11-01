package com.nirvor.app.downloader

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * The yt-dlp engine (with Python and FFmpeg) from youtubedl-android. It understands YouTube,
 * Facebook, Instagram, TikTok, X and about a thousand other sites.
 *
 * Speed notes:
 * - The engine is unpacked when the app starts ([warmUp]), not when the first download begins.
 * - Checking GitHub for a newer yt-dlp happens in the background and never holds up a download.
 * - The page is read once (for the format list); the download reuses that result
 *   ("--load-info-json") instead of reading the page a second time.
 */
object MediaEngine {
    private val initLock = Mutex()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var ready = false

    @Volatile
    private var updating = false

    private const val PREFS = "nirvor_downloader"
    private const val KEY_LAST_UPDATE = "engine_last_update"
    private const val UPDATE_EVERY_MS = 3L * 24 * 60 * 60 * 1000
    private const val INFO_KEEP_MS = 6L * 60 * 60 * 1000

    /** Called when the app opens: get the engine ready and look for updates, all in the background. */
    fun warmUp(context: Context) {
        val app = context.applicationContext
        background.launch {
            try {
                ensureReady(app)
            } catch (e: Exception) {
                return@launch
            }
            cleanOldInfo(app)
            maybeAutoUpdate(app)
        }
    }

    suspend fun ensureReady(context: Context) {
        if (ready) return
        initLock.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                val app = context.applicationContext
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready = true
            }
        }
    }

    /** Sites change often; keep yt-dlp fresh (at most every three days, never during a download). */
    private suspend fun maybeAutoUpdate(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = sp.getLong(KEY_LAST_UPDATE, 0L)
        if (System.currentTimeMillis() - last < UPDATE_EVERY_MS) return
        if (DownloadService.alive) return
        try {
            update(context)
        } catch (e: Exception) {
            // Offline or GitHub unreachable: try again next time.
        }
    }

    /** Returns true when a newer yt-dlp was installed, false when already up to date. */
    suspend fun update(context: Context): Boolean = withContext(Dispatchers.IO) {
        ensureReady(context)
        updating = true
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
            status == YoutubeDL.UpdateStatus.DONE
        } finally {
            updating = false
        }
    }

    /** Version of the installed yt-dlp (starts the engine if needed, without updating). */
    suspend fun currentVersion(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            ensureReady(context)
            YoutubeDL.getInstance().version(context.applicationContext)
        } catch (e: Exception) {
            null
        }
    }

    fun version(context: Context): String? = try {
        YoutubeDL.getInstance().version(context.applicationContext)
    } catch (e: Exception) {
        null
    }

    /**
     * Reads a page once and returns yt-dlp's full description of it (formats, title, …), also
     * saved to a file so the download can start straight away from it.
     */
    suspend fun infoJson(context: Context, url: String): Pair<JSONObject, File> = withContext(Dispatchers.IO) {
        ensureReady(context)
        val request = YoutubeDLRequest(url)
        request.addOption("--dump-single-json")
        request.addOption("--no-playlist")
        request.addOption("--no-warnings")
        cookiesFor(context, url)?.let { request.addOption("--cookies", it.absolutePath) }
        val out = YoutubeDL.getInstance().execute(request).out
        val start = out.indexOf('{')
        if (start < 0) throw IllegalStateException("Unsupported URL: no information")
        val text = out.substring(start).trim()
        val json = JSONObject(text)
        val dir = File(context.cacheDir, "ytinfo").apply { mkdirs() }
        val file = File(dir, sha1(url) + ".json")
        file.writeText(text)
        json to file
    }

    /**
     * Downloads into [outDir]. [onProgress] gets percent (0-100), seconds left and yt-dlp's
     * status line (which contains the speed).
     */
    suspend fun download(
        context: Context,
        url: String,
        args: List<String>,
        outDir: File,
        processId: String,
        onProgress: (Float, Long, String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        ensureReady(context)
        val request = YoutubeDLRequest(url)
        request.addCommands(args)
        request.addOption("--no-playlist")
        request.addOption("--no-mtime")
        request.addOption("-o", File(outDir, "%(title).90B.%(ext)s").absolutePath)
        cookiesFor(context, url)?.let { request.addOption("--cookies", it.absolutePath) }
        YoutubeDL.getInstance().execute(request, processId, onProgress)
    }

    fun cancel(processId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (e: Exception) {
            // already finished
        }
    }

    /** Saved page descriptions older than a few hours have expired links; remove them. */
    private fun cleanOldInfo(context: Context) {
        try {
            val now = System.currentTimeMillis()
            File(context.cacheDir, "ytinfo").listFiles()?.forEach {
                if (now - it.lastModified() > INFO_KEEP_MS) it.delete()
            }
        } catch (e: Exception) {
            // nothing to clean
        }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * Cookies from the built-in browser, so posts that need a login (Instagram, Facebook, …)
     * work after signing in once inside Nirvor's browser. Written as a Netscape cookies.txt.
     */
    private fun cookiesFor(context: Context, url: String): File? {
        return try {
            val host = Uri.parse(url).host ?: return null
            val raw = CookieManager.getInstance().getCookie(url)
            if (raw.isNullOrBlank()) return null
            val domain = baseDomain(host)
            val lines = StringBuilder("# Netscape HTTP Cookie File\n")
            raw.split(";").map { it.trim() }.filter { it.contains('=') }.forEach { pair ->
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=').trim()
                lines.append(".").append(domain).append("\tTRUE\t/\tTRUE\t2147483647\t")
                    .append(name).append('\t').append(value).append('\n')
            }
            val f = File(context.cacheDir, "cookies_${domain.hashCode()}.txt")
            f.writeText(lines.toString())
            f
        } catch (e: Exception) {
            null
        }
    }

    /** "m.facebook.com" -> "facebook.com", "www.youtube.com" -> "youtube.com" */
    internal fun baseDomain(host: String): String {
        val parts = host.lowercase().split('.').filter { it.isNotEmpty() }
        return if (parts.size <= 2) parts.joinToString(".") else parts.takeLast(2).joinToString(".")
    }
}
