package com.nirvor.app.downloader

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Where downloads are written while running, and where they end up: Nirvor/Downloads/<type>
 * (the folder the user set up, see [NirvorFolder]), or Download/Nirvor/<type> if none is set.
 */
object Storage {
    const val FOLDER = "Nirvor"

    class Published(val uri: Uri, val name: String, val size: Long, val mime: String)

    /** Private working folder for one download (partial files live here until finished). */
    fun jobDir(context: Context, id: Long): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        return File(root, "jobs/$id").apply { mkdirs() }
    }

    fun deleteJobDir(context: Context, id: Long) {
        try {
            jobDir(context, id).deleteRecursively()
        } catch (e: Exception) {
            // nothing to clean
        }
    }

    fun canWritePublic(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    /** Free space on the phone's shared storage, in bytes. */
    fun freeBytes(): Long = try {
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes
    } catch (e: Exception) {
        0L
    }

    /** Moves a finished file to its folder so it shows up in Files and the gallery. */
    fun publish(context: Context, file: File, mime: String): Published {
        NirvorFolder.save(context, file, mime)?.let { return it }

        // No Nirvor folder chosen: the phone's Download/Nirvor/<type>.
        val name = file.name
        val size = file.length()
        val sub = NirvorFolder.categoryFor(name, mime).folder
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + "/" + sub)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create the file in Downloads")
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out, 256 * 1024) } }
                ?: throw IllegalStateException("Could not write the file")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            val finalName = queryName(context, uri) ?: name
            file.delete()
            Published(uri, finalName, size, mime)
        } else if (canWritePublic(context)) {
            @Suppress("DEPRECATION")
            val dir = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER), sub)
            dir.mkdirs()
            val target = uniqueFile(dir, name)
            file.inputStream().use { input -> target.outputStream().use { input.copyTo(it, 256 * 1024) } }
            file.delete()
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            Published(fileUri(context, target), target.name, size, mime)
        } else {
            // No storage permission on Android 9 or older: keep it in the app's own folder.
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "Nirvor").apply { mkdirs() }
            val target = uniqueFile(dir, name)
            if (!file.renameTo(target)) {
                file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
                file.delete()
            }
            Published(fileUri(context, target), target.name, size, mime)
        }
    }

    private fun queryName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext")
            i++
        }
        return f
    }

    private fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".files", file)

    fun openIntent(uri: Uri, mime: String?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun shareIntent(uri: Uri, mime: String?): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mime ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun deletePublished(context: Context, uri: Uri) {
        try {
            if (NirvorFolder.isDocument(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (e: Exception) {
            // already gone, or not ours to delete
        }
    }
}
