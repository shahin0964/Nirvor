package com.nirvor.app.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bangla + English text recognition (Tesseract, fully on the phone). The language data
 * (tessdata_fast: Bangla ~1 MB, English ~4 MB) is downloaded once into the app's own folder.
 */
object Ocr {
    private const val BASE = "https://github.com/tesseract-ocr/tessdata_fast/raw/main/"
    val LANGS = listOf("ben", "eng")

    class Box(val text: String, val rect: Rect, val confidence: Float)

    class Result(val text: String, val words: List<Box>, val lines: List<Box>)

    private fun root(context: Context) = File(context.filesDir, "ocr")

    private fun data(context: Context, lang: String) = File(root(context), "tessdata/$lang.traineddata")

    fun ready(context: Context): Boolean = LANGS.all { data(context, it).length() > 100_000 }

    /** Downloads the missing language files. [onProgress] gets 0..1. */
    suspend fun download(context: Context, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val dir = File(root(context), "tessdata").apply { mkdirs() }
        LANGS.forEachIndexed { index, lang ->
            val target = data(context, lang)
            if (target.length() > 100_000) return@forEachIndexed
            val part = File(dir, "$lang.part")
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(BASE + "$lang.traineddata").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20_000
                    readTimeout = 40_000
                    instanceFollowRedirects = true
                }
                if (conn.responseCode !in 200..299) return@withContext false
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: 4_000_000L
                var got = 0L
                conn.inputStream.use { input ->
                    part.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            got += n
                            val p = (index + (got.toFloat() / total).coerceAtMost(1f)) / LANGS.size
                            withContext(Dispatchers.Main) { onProgress(p) }
                        }
                    }
                }
                if (part.length() < 100_000 || !part.renameTo(target)) {
                    part.delete()
                    return@withContext false
                }
            } catch (e: Exception) {
                part.delete()
                return@withContext false
            } finally {
                conn?.disconnect()
            }
        }
        true
    }

    /** Reads the text in [bitmap]. [langs]: "ben+eng", "eng" or "ben". Null when the engine could not start. */
    suspend fun read(context: Context, bitmap: Bitmap, langs: String = "ben+eng"): Result? = withContext(Dispatchers.Default) {
        val api = TessBaseAPI()
        try {
            if (!api.init(root(context).absolutePath, langs)) return@withContext null
            api.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)
            val img = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
            api.setImage(img)
            val text = api.getUTF8Text() ?: ""
            val words = mutableListOf<Box>()
            val lines = mutableListOf<Box>()
            val iter = api.getResultIterator()
            if (iter != null) {
                for ((level, into) in listOf(
                    TessBaseAPI.PageIteratorLevel.RIL_WORD to words,
                    TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE to lines,
                )) {
                    iter.begin()
                    do {
                        val t = iter.getUTF8Text(level)
                        val r = iter.getBoundingRect(level)
                        if (!t.isNullOrBlank() && r != null) into.add(Box(t.trim(), r, iter.confidence(level)))
                    } while (iter.next(level))
                }
                iter.delete()
            }
            Result(text.trim(), words, lines)
        } catch (e: Exception) {
            null
        } finally {
            api.recycle()
        }
    }
}
