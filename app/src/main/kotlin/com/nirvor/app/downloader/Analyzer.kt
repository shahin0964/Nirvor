package com.nirvor.app.downloader

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** A text value from yt-dlp's JSON, or null when missing / null. */
private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() && it != "null" }

/** One thing the user can pick in the format sheet. */
data class DlOption(
    val kind: Kind,
    val label: String,
    val detail: String,
    val sizeBytes: Long,
    val engine: String,
    /** yt-dlp arguments for [EngineType.YTDLP]. */
    val args: List<String> = emptyList(),
    /** File address for [EngineType.DIRECT]. */
    val directUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val fileName: String? = null,
    val mime: String? = null,
    val badge: String? = null,
)

/** What a link turned out to contain. */
data class MediaInfo(
    val pageUrl: String,
    val title: String,
    val thumbnail: String?,
    val source: String?,
    val durationSec: Int,
    val options: List<DlOption>,
)

class AnalyzeException(message: String) : Exception(message)

/** Turns a link (shared from another app, typed, or the browser's current page) into choices. */
object Analyzer {

    const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    private val urlRegex = Regex("""https?://[^\s<>"']+""")

    /** Finds the first web link in shared text such as "Look at this https://youtu.be/xyz". */
    fun extractUrl(text: String?): String? = text?.let { urlRegex.find(it)?.value?.trimEnd('.', ',', ')', ']') }

    suspend fun analyze(context: Context, rawUrl: String): MediaInfo = withContext(Dispatchers.IO) {
        val url = normalize(rawUrl)

        // 1. A plain file (pdf, zip, mp4, jpg, apk, …): offer it directly.
        val probe = probe(url)
        if (probe != null && probe.isFile) return@withContext directInfo(url, probe)

        // 2. A media page: ask yt-dlp for every format.
        var engineError: Exception? = null
        try {
            val (info, file) = MediaEngine.infoJson(context, url)
            val built = fromInfoJson(url, info, file)
            if (built.options.isNotEmpty()) return@withContext built
        } catch (e: Exception) {
            engineError = e
        }

        // 3. Fallback for photo posts and simple pages: the page's own preview image / video.
        val og = openGraph(url)
        if (og != null && og.options.isNotEmpty()) return@withContext og

        throw AnalyzeException(friendlyError(engineError))
    }

    fun normalize(raw: String): String {
        val t = raw.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    private fun friendlyError(e: Exception?): String {
        val msg = e?.message.orEmpty()
        return when {
            msg.contains("login", true) || msg.contains("cookies", true) || msg.contains("private", true) ->
                "এই পোস্ট দেখতে লগইন লাগে। Nirvor-এর ব্রাউজারে একবার লগইন করে আবার চেষ্টা করুন।"
            msg.contains("Unsupported URL", true) ->
                "এই লিংক থেকে ডাউনলোডযোগ্য কিছু পাওয়া যায়নি।"
            msg.contains("Unable to resolve host", true) || msg.contains("network", true) ->
                "ইন্টারনেট সংযোগ পাওয়া যায়নি।"
            else -> "এই লিংক থেকে কিছু পাওয়া যায়নি। লিংকটা ঠিক আছে কিনা দেখুন, অথবা ইঞ্জিন আপডেট করে আবার চেষ্টা করুন।"
        }
    }

    // ------------------------------------------------------------------ direct files

    private class Probe(
        val finalUrl: String,
        val contentType: String,
        val length: Long,
        val disposition: String?,
    ) {
        val isFile: Boolean
            get() = contentType.isNotEmpty() &&
                !contentType.startsWith("text/html") &&
                !contentType.startsWith("application/xhtml") &&
                !contentType.startsWith("text/plain") &&
                !contentType.contains("json") &&
                // Stream playlists (HLS / DASH) are handed to yt-dlp, which joins the pieces.
                !contentType.contains("mpegurl") &&
                !contentType.contains("dash+xml")
    }

    private fun probe(url: String): Probe? {
        var conn: HttpURLConnection? = null
        return try {
            var current = url
            repeat(5) {
                conn = (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", MOBILE_UA)
                    setRequestProperty("Range", "bytes=0-0")
                    CookieManager.getInstance().getCookie(current)?.let { setRequestProperty("Cookie", it) }
                }
                val c = conn!!
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return null
                    current = URL(URL(current), loc).toString()
                    c.disconnect()
                } else {
                    val range = c.getHeaderField("Content-Range")
                    val total = range?.substringAfter('/')?.toLongOrNull() ?: c.contentLengthLong
                    return Probe(
                        finalUrl = current,
                        contentType = (c.contentType ?: "").lowercase(Locale.US),
                        length = total,
                        disposition = c.getHeaderField("Content-Disposition"),
                    )
                }
            }
            null
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun directInfo(pageUrl: String, p: Probe): MediaInfo {
        val mime = p.contentType.substringBefore(';').trim()
        val name = URLUtil.guessFileName(p.finalUrl, p.disposition, mime)
        val kind = kindForMime(mime)
        val ext = name.substringAfterLast('.', "").uppercase(Locale.US)
        val option = DlOption(
            kind = kind,
            label = name,
            detail = listOf(ext.ifEmpty { mime }, sizeText(p.length)).filter { it.isNotEmpty() }.joinToString(" · "),
            sizeBytes = p.length,
            engine = EngineType.DIRECT,
            directUrl = p.finalUrl,
            headers = headersFor(p.finalUrl, pageUrl),
            fileName = name,
            mime = mime,
        )
        return MediaInfo(pageUrl, name, if (kind == Kind.Photo) p.finalUrl else null, hostOf(pageUrl), 0, listOf(option))
    }

    fun kindForMime(mime: String): Kind = when {
        mime.startsWith("video/") -> Kind.Video
        mime.startsWith("audio/") -> Kind.Audio
        mime.startsWith("image/") -> Kind.Photo
        else -> Kind.Other
    }

    fun headersFor(fileUrl: String, referer: String?): Map<String, String> {
        val h = linkedMapOf("User-Agent" to MOBILE_UA)
        CookieManager.getInstance().getCookie(fileUrl)?.takeIf { it.isNotBlank() }?.let { h["Cookie"] = it }
        if (!referer.isNullOrBlank()) h["Referer"] = referer
        return h
    }

    // ------------------------------------------------------------------ yt-dlp formats

    /** One entry of yt-dlp's "formats" list. */
    private class Fmt(o: JSONObject) {
        val id: String? = o.str("format_id")
        val ext: String? = o.str("ext")
        val vcodec: String? = o.str("vcodec")
        val acodec: String? = o.str("acodec")
        val height: Int = o.optInt("height", 0)
        val fps: Int = o.optDouble("fps", 0.0).let { if (it.isNaN()) 0 else it.toInt() }
        val tbr: Double = o.optDouble("tbr", 0.0).let { if (it.isNaN()) 0.0 else it }
        val abr: Int = o.optDouble("abr", 0.0).let { if (it.isNaN()) 0 else it.toInt() }
        val size: Long = o.optLong("filesize", 0L).takeIf { it > 0 } ?: o.optLong("filesize_approx", 0L)
        val isVideo: Boolean get() = vcodec != null && vcodec != "none" && height > 0
        val hasAudio: Boolean get() = acodec != null && acodec != "none"
        val isAudioOnly: Boolean get() = vcodec == "none" && hasAudio
    }

    private fun fromInfoJson(pageUrl: String, info: JSONObject, infoFile: File): MediaInfo {
        val arr = info.optJSONArray("formats")
        val formats = (0 until (arr?.length() ?: 0)).mapNotNull { i -> arr?.optJSONObject(i)?.let { Fmt(it) } }
        val duration = info.optDouble("duration", 0.0).let { if (it.isNaN()) 0 else it.toInt() }
        val options = mutableListOf<DlOption>()
        val merge = listOf("--merge-output-format", "mp4/mkv")
        // The download starts from the saved description instead of reading the page again.
        val fromInfo = listOf("--load-info-json", infoFile.absolutePath)

        val audioOnly = formats.filter { it.isAudioOnly }
        val bestAudio = audioOnly.maxWithOrNull(compareBy<Fmt>({ if (it.ext == "m4a") 1 else 0 }, { it.abr }, { it.size }))

        // ---- video
        val videos = formats.filter { it.isVideo }
        if (videos.isNotEmpty()) {
            options += DlOption(
                kind = Kind.Video,
                label = "সেরা কোয়ালিটি",
                detail = "অটো · সবচেয়ে ভালো ভিডিও + অডিও",
                sizeBytes = 0,
                engine = EngineType.YTDLP,
                args = fromInfo + listOf("-f", "bv*+ba/b") + merge,
                badge = "AUTO",
            )
            val byHeight = videos.groupBy { it.height }.toSortedMap(compareByDescending { it })
            for ((height, list) in byHeight) {
                val best = list.maxWithOrNull(
                    compareBy<Fmt>({ if (it.hasAudio) 1 else 0 }, { if (it.ext == "mp4") 1 else 0 }, { it.fps }, { it.tbr })
                ) ?: continue
                val id = best.id ?: continue
                val withAudio = best.hasAudio
                val args = if (withAudio) listOf("-f", id) else listOf("-f", "$id+bestaudio[ext=m4a]/$id+bestaudio/$id") + merge
                val total = best.size + if (withAudio) 0 else (bestAudio?.size ?: 0)
                val ext = if (withAudio) (best.ext ?: "mp4") else "mp4"
                val fps = if (best.fps > 30) " · ${best.fps}fps" else ""
                options += DlOption(
                    kind = Kind.Video,
                    label = "${height}p",
                    detail = ext.uppercase(Locale.US) + fps + sizeSuffix(total),
                    sizeBytes = total,
                    engine = EngineType.YTDLP,
                    args = fromInfo + args,
                    badge = when {
                        height >= 2160 -> "4K"
                        height >= 1440 -> "2K"
                        height >= 1080 -> "FHD"
                        height >= 720 -> "HD"
                        else -> null
                    },
                )
            }
        } else if (formats.isNotEmpty() || info.str("url") != null) {
            val single = info.optLong("filesize", 0L).takeIf { it > 0 } ?: info.optLong("filesize_approx", 0L)
            options += DlOption(
                kind = Kind.Video,
                label = "ভিডিও",
                detail = (info.str("ext") ?: "mp4").uppercase(Locale.US) + sizeSuffix(single),
                sizeBytes = single,
                engine = EngineType.YTDLP,
                args = fromInfo + listOf("-f", "b/bv*+ba") + merge,
            )
        }

        // ---- audio
        val hasAnyAudio = audioOnly.isNotEmpty() || formats.any { it.hasAudio } || info.str("url") != null
        if (hasAnyAudio) {
            for (kbps in listOf(320, 192, 128)) {
                val est = if (duration > 0) duration.toLong() * kbps * 125 else 0
                options += DlOption(
                    kind = Kind.Audio,
                    label = "MP3 ${kbps}kbps",
                    detail = "MP3" + sizeSuffix(est),
                    sizeBytes = est,
                    engine = EngineType.YTDLP,
                    args = fromInfo + listOf("-f", "ba/b", "-x", "--audio-format", "mp3", "--audio-quality", "${kbps}K"),
                    badge = if (kbps == 320) "HQ" else null,
                )
            }
            val m4a = audioOnly.filter { it.ext == "m4a" }.maxByOrNull { it.abr }
            if (m4a?.id != null) {
                options += DlOption(
                    kind = Kind.Audio,
                    label = "M4A ${if (m4a.abr > 0) "${m4a.abr}kbps" else "অরিজিনাল"}",
                    detail = "অরিজিনাল অডিও, কনভার্ট ছাড়া" + sizeSuffix(m4a.size),
                    sizeBytes = m4a.size,
                    engine = EngineType.YTDLP,
                    args = fromInfo + listOf("-f", m4a.id),
                )
            }
            val opus = audioOnly.filter { it.ext == "webm" || it.acodec == "opus" }.maxByOrNull { it.abr }
            if (opus?.id != null) {
                options += DlOption(
                    kind = Kind.Audio,
                    label = "OPUS ${if (opus.abr > 0) "${opus.abr}kbps" else "অরিজিনাল"}",
                    detail = "WEBM · অরিজিনাল অডিও" + sizeSuffix(opus.size),
                    sizeBytes = opus.size,
                    engine = EngineType.YTDLP,
                    args = fromInfo + listOf("-f", opus.id),
                )
            }
        }

        // ---- photo (cover / thumbnail at full size)
        val title = info.str("title") ?: info.str("fulltitle") ?: hostOf(pageUrl) ?: "ডাউনলোড"
        val thumb = info.str("thumbnail")
        if (!thumb.isNullOrBlank()) {
            val name = safeName(title) + "." + imageExt(thumb)
            options += DlOption(
                kind = Kind.Photo,
                label = "কভার ছবি",
                detail = imageExt(thumb).uppercase(Locale.US) + " · সর্বোচ্চ রেজোলিউশন",
                sizeBytes = 0,
                engine = EngineType.DIRECT,
                directUrl = thumb,
                headers = headersFor(thumb, pageUrl),
                fileName = name,
                mime = "image/" + imageExt(thumb).let { if (it == "jpg") "jpeg" else it },
            )
        }

        return MediaInfo(
            pageUrl = pageUrl,
            title = title,
            thumbnail = thumb,
            source = info.str("uploader") ?: info.str("extractor_key") ?: hostOf(pageUrl),
            durationSec = duration,
            options = options,
        )
    }

    // ------------------------------------------------------------------ Open Graph fallback

    private fun openGraph(pageUrl: String): MediaInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("User-Agent", MOBILE_UA)
                CookieManager.getInstance().getCookie(pageUrl)?.let { setRequestProperty("Cookie", it) }
            }
            val html = conn!!.inputStream.bufferedReader().use { r ->
                val buf = CharArray(400_000)
                val n = r.read(buf)
                if (n > 0) String(buf, 0, n) else ""
            }
            fun meta(prop: String): String? {
                val r1 = Regex("""<meta[^>]+(?:property|name)=["']$prop["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                val r2 = Regex("""<meta[^>]+content=["']([^"']+)["'][^>]*(?:property|name)=["']$prop["']""", RegexOption.IGNORE_CASE)
                return (r1.find(html) ?: r2.find(html))?.groupValues?.get(1)?.replace("&amp;", "&")
            }
            val title = meta("og:title") ?: Regex("<title>([^<]{1,200})</title>", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.trim() ?: hostOf(pageUrl) ?: "ডাউনলোড"
            val image = meta("og:image") ?: meta("twitter:image")
            val video = meta("og:video:secure_url") ?: meta("og:video:url") ?: meta("og:video")
            val options = mutableListOf<DlOption>()
            if (!video.isNullOrBlank() && video.startsWith("http")) {
                options += DlOption(
                    kind = Kind.Video,
                    label = "ভিডিও",
                    detail = "পেজের ভিডিও",
                    sizeBytes = 0,
                    engine = EngineType.DIRECT,
                    directUrl = video,
                    headers = headersFor(video, pageUrl),
                    fileName = safeName(title) + ".mp4",
                    mime = "video/mp4",
                )
            }
            if (!image.isNullOrBlank() && image.startsWith("http")) {
                options += DlOption(
                    kind = Kind.Photo,
                    label = "ছবি",
                    detail = imageExt(image).uppercase(Locale.US) + " · পেজের মূল ছবি",
                    sizeBytes = 0,
                    engine = EngineType.DIRECT,
                    directUrl = image,
                    headers = headersFor(image, pageUrl),
                    fileName = safeName(title) + "." + imageExt(image),
                    mime = "image/" + imageExt(image).let { if (it == "jpg") "jpeg" else it },
                )
            }
            MediaInfo(pageUrl, title, image, hostOf(pageUrl), 0, options)
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    // ------------------------------------------------------------------ helpers

    fun hostOf(url: String): String? = try {
        Uri.parse(url).host?.removePrefix("www.")?.removePrefix("m.")
    } catch (e: Exception) {
        null
    }

    private fun imageExt(url: String): String {
        val path = Uri.parse(url).lastPathSegment.orEmpty().lowercase(Locale.US)
        val ext = path.substringAfterLast('.', "")
        return when (ext) {
            "jpg", "jpeg" -> "jpg"
            "png", "webp", "gif" -> ext
            else -> "jpg"
        }
    }

    fun safeName(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("\\s+"), " ").trim().take(90).ifEmpty { "nirvor" }

    fun mimeForName(name: String, fallback: String): String {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: fallback
    }

    private fun sizeSuffix(bytes: Long) = if (bytes > 0) " · ~" + sizeText(bytes) else ""

    fun sizeText(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }
}
