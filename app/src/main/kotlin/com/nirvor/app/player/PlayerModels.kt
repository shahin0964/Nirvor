package com.nirvor.app.player

import android.net.Uri
import java.util.Locale

/**
 * Represents a single video or audio media file available to play in Nirvor Player.
 */
data class MediaItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val path: String,
    val uri: Uri,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val isVideo: Boolean,
    val mimeType: String,
    val width: Int = 0,
    val height: Int = 0,
    val folderName: String = "",
    val folderPath: String = "",
) {
    val formattedDuration: String get() {
        if (durationMs <= 0L) return if (isVideo) "00:00" else "--:--"
        val totalSec = durationMs / 1000
        val s = totalSec % 60
        val m = (totalSec / 60) % 60
        val h = totalSec / 3600
        return if (h > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    val formattedSize: String get() {
        if (sizeBytes <= 0) return ""
        val kb = sizeBytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }

    val resolutionOrFormat: String get() {
        return if (isVideo) {
            when {
                height >= 2160 || width >= 3840 -> "4K"
                height >= 1080 || width >= 1920 -> "1080p"
                height >= 720 || width >= 1280 -> "720p"
                height >= 480 || width >= 848 -> "480p"
                height > 0 -> "${height}p"
                else -> displayName.substringAfterLast('.', "").uppercase(Locale.US).ifEmpty { "VIDEO" }
            }
        } else {
            displayName.substringAfterLast('.', "").uppercase(Locale.US).ifEmpty { "AUDIO" }
        }
    }

    val isRecent: Boolean get() {
        val nowSec = System.currentTimeMillis() / 1000
        return (nowSec - dateAddedSec) < (7 * 24 * 3600) // 7 days
    }
}

/**
 * A folder containing media items (MX Player style).
 */
data class MediaFolder(
    val name: String,
    val path: String,
    val items: List<MediaItem>,
    val videoCount: Int,
    val audioCount: Int,
    val latestDateAdded: Long,
) {
    val totalCount: Int get() = items.size
    val summaryText: String get() {
        return when {
            videoCount > 0 && audioCount > 0 -> "$videoCount videos • $audioCount audios"
            videoCount > 0 -> if (videoCount == 1) "1 video" else "$videoCount videos"
            audioCount > 0 -> if (audioCount == 1) "1 audio" else "$audioCount audios"
            else -> if (totalCount == 1) "1 file" else "$totalCount files"
        }
    }
}

enum class ViewMode {
    AllFolders,
    Files,
    Folders,
}

enum class LayoutMode {
    List,
    Grid,
}

enum class SortField {
    Title,
    Date,
    Size,
    Length,
}

enum class SortOrder {
    Ascending,
    Descending,
}

enum class AspectRatioMode(val title: String) {
    Fit("Fit"),
    Crop("Crop / Zoom"),
    Stretch("Stretch"),
    Original("100%"),
}

enum class RepeatMode(val title: String) {
    Off("Off"),
    One("Repeat 1"),
    All("Repeat All"),
}
