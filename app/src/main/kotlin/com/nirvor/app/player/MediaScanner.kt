package com.nirvor.app.player

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object MediaScanner {

    private val thumbnailCache = object : LruCache<String, Bitmap>(80) {}

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val video = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            video || audio
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun scanAllMedia(
        context: Context,
        includeAudio: Boolean = true,
        includeVideo: Boolean = true,
    ): List<MediaFolder> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        val seenPaths = HashSet<String>()

        // 1. Scan Videos from MediaStore
        if (includeVideo) {
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Video.Media.DATA,
            )

            try {
                context.contentResolver.query(
                    videoUri,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                    val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                    val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                    val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                    val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                    val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                    val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    } else -1

                    while (cursor.moveToNext()) {
                        val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                        val data = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: File(data).name else File(data).name
                        val title = if (titleCol >= 0) cursor.getString(titleCol) ?: name else name
                        val dur = if (durCol >= 0) cursor.getLong(durCol) else 0L
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                        val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                        val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "video/*" else "video/*"
                        val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                        val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0

                        val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                        val folder = when {
                            bucketCol >= 0 -> cursor.getString(bucketCol)
                            data.isNotEmpty() -> File(data).parentFile?.name
                            else -> null
                        } ?: "Videos"

                        val folderPath = if (data.isNotEmpty()) File(data).parent ?: "" else ""

                        if (data.isNotEmpty()) seenPaths.add(data)

                        items.add(
                            MediaItem(
                                id = id,
                                title = title,
                                displayName = name,
                                path = data,
                                uri = contentUri,
                                durationMs = dur,
                                sizeBytes = size,
                                dateAddedSec = dateAdded,
                                isVideo = true,
                                mimeType = mime,
                                width = width,
                                height = height,
                                folderName = folder,
                                folderPath = folderPath,
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Scan Audio from MediaStore
        if (includeAudio) {
            val audioUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.MIME_TYPE,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.BUCKET_DISPLAY_NAME else MediaStore.Audio.Media.DATA,
            )

            try {
                context.contentResolver.query(
                    audioUri,
                    projection,
                    "${MediaStore.Audio.Media.DURATION} > 10000", // filter > 10s to skip UI sounds
                    null,
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                    val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                    val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                    val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                    } else -1

                    while (cursor.moveToNext()) {
                        val id = if (idCol >= 0) cursor.getLong(idCol) else 0L
                        val data = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                        val name = if (nameCol >= 0) cursor.getString(nameCol) ?: File(data).name else File(data).name
                        val title = if (titleCol >= 0) cursor.getString(titleCol) ?: name else name
                        val dur = if (durCol >= 0) cursor.getLong(durCol) else 0L
                        val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                        val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                        val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "audio/*" else "audio/*"

                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                        val folder = when {
                            bucketCol >= 0 -> cursor.getString(bucketCol)
                            data.isNotEmpty() -> File(data).parentFile?.name
                            else -> null
                        } ?: "Audio"

                        val folderPath = if (data.isNotEmpty()) File(data).parent ?: "" else ""

                        if (data.isNotEmpty()) seenPaths.add(data)

                        items.add(
                            MediaItem(
                                id = id,
                                title = title,
                                displayName = name,
                                path = data,
                                uri = contentUri,
                                durationMs = dur,
                                sizeBytes = size,
                                dateAddedSec = dateAdded,
                                isVideo = false,
                                mimeType = mime,
                                width = 0,
                                height = 0,
                                folderName = folder,
                                folderPath = folderPath,
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Scan physical Nirvor and Download directories for newly downloaded files
        scanPhysicalDirectories(seenPaths, items)

        // 4. Group items by folder name
        val grouped = items.groupBy { it.folderName }
        val folders = grouped.map { (folderName, folderItems) ->
            val videoCount = folderItems.count { it.isVideo }
            val audioCount = folderItems.count { !it.isVideo }
            val latest = folderItems.maxOfOrNull { it.dateAddedSec } ?: 0L
            val folderPath = folderItems.firstOrNull()?.folderPath ?: ""
            MediaFolder(
                name = folderName,
                path = folderPath,
                items = folderItems.sortedByDescending { it.dateAddedSec },
                videoCount = videoCount,
                audioCount = audioCount,
                latestDateAdded = latest,
            )
        }.sortedByDescending { it.latestDateAdded }

        folders
    }

    private fun scanPhysicalDirectories(seenPaths: MutableSet<String>, items: MutableList<MediaItem>) {
        val candidateDirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nirvor"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nirvor/Downloads/Videos"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nirvor/Downloads/Music"),
            File(Environment.getExternalStorageDirectory(), "Nirvor/Downloads/Videos"),
            File(Environment.getExternalStorageDirectory(), "Nirvor/Downloads/Music"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
        )

        for (dir in candidateDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val files = dir.listFiles() ?: continue
            for (file in files) {
                if (file.isDirectory || file.length() < 1024) continue
                if (seenPaths.contains(file.absolutePath)) continue

                val ext = file.extension.lowercase(Locale.US)
                val isVid = ext in listOf("mp4", "mkv", "webm", "avi", "3gp", "mov", "ts", "flv", "m4v")
                val isAud = ext in listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")

                if (isVid || isAud) {
                    seenPaths.add(file.absolutePath)
                    items.add(
                        MediaItem(
                            id = file.hashCode().toLong(),
                            title = file.nameWithoutExtension,
                            displayName = file.name,
                            path = file.absolutePath,
                            uri = Uri.fromFile(file),
                            durationMs = 0L,
                            sizeBytes = file.length(),
                            dateAddedSec = file.lastModified() / 1000,
                            isVideo = isVid,
                            mimeType = if (isVid) "video/$ext" else "audio/$ext",
                            folderName = dir.name.ifEmpty { "Downloads" },
                            folderPath = dir.absolutePath,
                        )
                    )
                }
            }
        }
    }

    suspend fun getThumbnail(context: Context, item: MediaItem): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = "${item.id}_${item.path}_${item.isVideo}"
        thumbnailCache.get(cacheKey)?.let { return@withContext it }

        var bmp: Bitmap? = null

        try {
            if (item.isVideo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && item.uri.scheme == "content") {
                    try {
                        bmp = context.contentResolver.loadThumbnail(item.uri, Size(320, 240), null)
                    } catch (_: Exception) {}
                }
                if (bmp == null && item.path.isNotEmpty() && File(item.path).exists()) {
                    bmp = ThumbnailUtils.createVideoThumbnail(item.path, MediaStore.Images.Thumbnails.MINI_KIND)
                }
                if (bmp == null && item.path.isNotEmpty()) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(item.path)
                        bmp = retriever.frameAtTime
                    } catch (_: Exception) {
                    } finally {
                        retriever.release()
                    }
                }
            } else {
                // Audio album art
                if (item.path.isNotEmpty() && File(item.path).exists()) {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(item.path)
                        val art = retriever.embeddedPicture
                        if (art != null) {
                            bmp = BitmapFactory.decodeByteArray(art, 0, art.size)
                        }
                    } catch (_: Exception) {
                    } finally {
                        retriever.release()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (bmp != null) {
            thumbnailCache.put(cacheKey, bmp)
        }
        bmp
    }
}
