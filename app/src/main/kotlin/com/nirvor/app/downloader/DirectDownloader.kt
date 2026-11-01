package com.nirvor.app.downloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Downloads a plain file link over HTTP. When the server allows it, the file is split into
 * [parts] pieces fetched at the same time (faster on most servers). Progress is saved next to
 * the file, so a paused or interrupted download continues where it stopped.
 */
class DirectDownloader(
    private val url: String,
    private val headers: Map<String, String>,
    private val outFile: File,
    private val parts: Int,
) {
    private class Part(val start: Long, val end: Long, var done: Long)

    private val stateFile = File(outFile.path + ".parts")

    suspend fun start(onProgress: (done: Long, total: Long) -> Unit) = withContext(Dispatchers.IO) {
        val (total, ranges) = probe()
        val useParts = ranges && total > 2L * 1024 * 1024 && parts > 1
        if (useParts) segmented(total, onProgress) else single(total, ranges, onProgress)
        stateFile.delete()
    }

    private fun open(range: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            if (range != null) setRequestProperty("Range", range)
        }

    /** Returns (total length or -1, server supports byte ranges). */
    private fun probe(): Pair<Long, Boolean> {
        val c = open("bytes=0-0")
        try {
            val code = c.responseCode
            if (code == 206) {
                val total = c.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                return total to (total > 0)
            }
            if (code in 200..299) return c.contentLengthLong to false
            throw IOException("সার্ভার উত্তর দিয়েছে $code")
        } finally {
            c.disconnect()
        }
    }

    // ---------------------------------------------------------------- one connection

    private suspend fun single(total: Long, ranges: Boolean, onProgress: (Long, Long) -> Unit) {
        val existing = if (ranges && outFile.exists()) outFile.length() else 0L
        if (total > 0 && existing >= total) {
            onProgress(total, total)
            return
        }
        val c = open(if (existing > 0) "bytes=$existing-" else null)
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("সার্ভার উত্তর দিয়েছে $code")
            val append = code == 206 && existing > 0
            val size = if (total > 0) total else c.contentLengthLong.let { if (it > 0) it + (if (append) existing else 0) else -1 }
            var done = if (append) existing else 0L
            var lastReport = 0L
            RandomAccessFile(outFile, "rw").use { raf ->
                if (!append) raf.setLength(0)
                raf.seek(done)
                c.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 300) {
                            onProgress(done, size)
                            lastReport = now
                        }
                    }
                }
            }
            onProgress(done, if (size > 0) size else done)
        } finally {
            c.disconnect()
        }
    }

    // ---------------------------------------------------------------- several connections

    private suspend fun segmented(total: Long, onProgress: (Long, Long) -> Unit) {
        val pieces = loadState(total) ?: makeParts(total)
        RandomAccessFile(outFile, "rw").use { if (it.length() != total) it.setLength(total) }
        val downloaded = AtomicLong(pieces.sumOf { it.done })

        coroutineScope {
            val reporter = launch {
                while (isActive) {
                    onProgress(downloaded.get(), total)
                    saveState(pieces)
                    delay(400)
                }
            }
            try {
                val workers = pieces.filter { it.start + it.done <= it.end }.map { part ->
                    launch(Dispatchers.IO) { fetchPart(part, downloaded) }
                }
                workers.forEach { it.join() }
            } finally {
                saveState(pieces)
                reporter.cancel()
            }
        }
        onProgress(total, total)
    }

    private suspend fun fetchPart(part: Part, downloaded: AtomicLong) {
        var attempts = 0
        while (part.start + part.done <= part.end) {
            try {
                val from = part.start + part.done
                val c = open("bytes=$from-${part.end}")
                try {
                    if (c.responseCode != 206) throw IOException("সার্ভার ভাগে ভাগে দিতে রাজি হয়নি")
                    RandomAccessFile(outFile, "rw").use { raf ->
                        raf.seek(from)
                        c.inputStream.use { input ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val remaining = part.end - (part.start + part.done) + 1
                                if (remaining <= 0) break
                                val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                                if (n < 0) break
                                raf.write(buf, 0, n)
                                part.done += n
                                downloaded.addAndGet(n.toLong())
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
            } catch (e: IOException) {
                attempts++
                if (attempts >= 5) throw e
                delay(1500L * attempts)
            }
        }
    }

    private fun makeParts(total: Long): List<Part> {
        val n = parts.coerceIn(1, 8)
        val chunk = total / n
        return List(n) { i ->
            val start = i * chunk
            val end = if (i == n - 1) total - 1 else start + chunk - 1
            Part(start, end, 0)
        }
    }

    @Synchronized
    private fun saveState(pieces: List<Part>) {
        try {
            stateFile.writeText(pieces.joinToString("\n") { "${it.start},${it.end},${it.done}" })
        } catch (e: Exception) {
            // progress file is only a convenience
        }
    }

    private fun loadState(total: Long): List<Part>? {
        if (!stateFile.exists() || !outFile.exists() || outFile.length() != total) return null
        return try {
            val list = stateFile.readLines().filter { it.isNotBlank() }.map { line ->
                val (s, e, d) = line.split(',').map { it.trim().toLong() }
                Part(s, e, d)
            }
            if (list.isEmpty() || list.last().end != total - 1) null else list
        } catch (e: Exception) {
            null
        }
    }
}
