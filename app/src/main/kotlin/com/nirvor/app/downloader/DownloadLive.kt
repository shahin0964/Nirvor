package com.nirvor.app.downloader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** Live numbers for a running download (kept in memory, not in the database). */
data class LiveStat(
    val speedBps: Long = 0,
    val etaSec: Long = -1,
    val doneBytes: Long = 0,
    val totalBytes: Long = 0,
    /** Short Bangla note such as "অডিও নামছে" or "জোড়া লাগানো হচ্ছে". */
    val stage: String? = null,
)

object DownloadLive {
    private val _stats = MutableStateFlow<Map<Long, LiveStat>>(emptyMap())
    val stats: StateFlow<Map<Long, LiveStat>> = _stats.asStateFlow()

    fun set(id: Long, stat: LiveStat) = _stats.update { it + (id to stat) }

    fun clear(id: Long) = _stats.update { it - id }

    // ---------------------------------------------------------------- yt-dlp line parsing

    private val speedRegex = Regex("""at\s+~?\s*([\d.]+)\s*([KMG]?i?B)/s""")
    private val totalRegex = Regex("""of\s+~?\s*([\d.]+)\s*([KMG]?i?B)""")

    /** Reads speed and size from a yt-dlp status line like "[download] 45% of ~ 120MiB at 2.3MiB/s". */
    fun parseLine(line: String): Pair<Long, Long> {
        val speed = speedRegex.find(line)?.let { bytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
        val total = totalRegex.find(line)?.let { bytes(it.groupValues[1], it.groupValues[2]) } ?: 0L
        return speed to total
    }

    fun stageOf(line: String): String? = when {
        line.contains("[Merger]") -> "ভিডিও আর অডিও জোড়া লাগানো হচ্ছে"
        line.contains("[ExtractAudio]") -> "অডিও কনভার্ট হচ্ছে"
        line.contains("[VideoConvertor]") || line.contains("[FixupM3u8]") -> "ফাইল ঠিক করা হচ্ছে"
        line.contains("Destination:") && (line.contains(".m4a") || line.contains(".webm") && line.contains("f251")) -> "অডিও নামছে"
        else -> null
    }

    private fun bytes(num: String, unit: String): Long {
        val v = num.toDoubleOrNull() ?: return 0
        val mult = when (unit.uppercase(Locale.US).first()) {
            'K' -> 1024.0
            'M' -> 1024.0 * 1024
            'G' -> 1024.0 * 1024 * 1024
            else -> 1.0
        }
        return (v * mult).toLong()
    }

    fun speedText(bps: Long): String = if (bps <= 0) "" else Analyzer.sizeText(bps) + "/s"

    fun etaText(sec: Long): String {
        if (sec < 0) return ""
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }
}
