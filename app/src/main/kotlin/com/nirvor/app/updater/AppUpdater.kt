package com.nirvor.app.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String,
    val downloadUrl: String,
    val apkSize: Long,
    val isUpdateAvailable: Boolean,
)

object AppUpdater {
    private const val GITHUB_API_LATEST = "https://api.github.com/repos/shahin0964/Nirvor/releases/latest"

    /** Parse semantic version components (e.g. "v2.1.4" -> [2, 1, 4]). */
    fun parseVersion(v: String): List<Int> {
        val clean = v.trim().removePrefix("v").removePrefix("V")
        return clean.split('.', '-', '_').mapNotNull { it.toIntOrNull() }
    }

    /** Returns true if [remote] is strictly higher than [current]. */
    fun isRemoteNewer(current: String, remote: String): Boolean {
        val curParts = parseVersion(current)
        val remParts = parseVersion(remote)
        if (curParts.isEmpty() || remParts.isEmpty()) {
            return current.trim().removePrefix("v") != remote.trim().removePrefix("v")
        }
        val maxLen = maxOf(curParts.size, remParts.size)
        for (i in 0 until maxLen) {
            val c = curParts.getOrElse(i) { 0 }
            val r = remParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /** Query GitHub latest release. */
    suspend fun checkForUpdates(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_LATEST)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Nirvor-Android-App")
            }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("GitHub API প্রতিক্রিয়া কোড: $code"))
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val tagName = json.optString("tag_name", "")
            val cleanVersion = tagName.removePrefix("v").removePrefix("V")
            val releaseTitle = json.optString("name", "Nirvor $tagName")
            val releaseNotes = json.optString("body", "কোনো বিবরণ পাওয়া যায়নি।")
            val publishedAt = json.optString("published_at", "")

            val assets = json.optJSONArray("assets")
            var downloadUrl = ""
            var apkSize = 0L

            if (assets != null) {
                // Prefer Nirvor-latest.apk or Nirvor-v*.apk or any .apk
                var selectedAsset: JSONObject? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.equals("Nirvor-latest.apk", ignoreCase = true)) {
                        selectedAsset = asset
                        break
                    } else if (name.endsWith(".apk", ignoreCase = true)) {
                        selectedAsset = asset
                    }
                }

                if (selectedAsset != null) {
                    downloadUrl = selectedAsset.optString("browser_download_url", "")
                    apkSize = selectedAsset.optLong("size", 0L)
                }
            }

            if (downloadUrl.isEmpty()) {
                downloadUrl = "https://github.com/shahin0964/Nirvor/releases/download/$tagName/Nirvor-latest.apk"
            }

            val updateAvailable = isRemoteNewer(currentVersion, tagName)

            Result.success(
                UpdateInfo(
                    version = cleanVersion,
                    tagName = tagName,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseNotes,
                    publishedAt = publishedAt,
                    downloadUrl = downloadUrl,
                    apkSize = apkSize,
                    isUpdateAvailable = updateAvailable,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Downloads APK from [downloadUrl] to [destination], reporting progress. */
    suspend fun downloadApk(
        downloadUrl: String,
        destination: File,
        onProgress: (downloaded: Long, total: Long, percent: Int) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val tempFile = File(destination.parentFile, destination.name + ".tmp")
        try {
            destination.parentFile?.mkdirs()
            if (tempFile.exists()) tempFile.delete()

            var targetUrl = downloadUrl
            var conn: HttpURLConnection
            var redirects = 0

            // Follow HTTP redirects safely (GitHub release download redirects to AWS S3 CDN)
            while (true) {
                conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "Nirvor-Android-App")
                }
                conn.connect()
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == 307 || code == 308
                ) {
                    val loc = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (loc != null && redirects < 6) {
                        targetUrl = loc
                        redirects++
                        continue
                    }
                }
                break
            }

            val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: conn.contentLength.toLong()
            var downloadedBytes = 0L

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    var lastPercent = -1

                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read

                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }

                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(downloadedBytes, totalBytes, percent)
                        }
                    }
                    output.flush()
                }
            }

            if (destination.exists()) destination.delete()
            if (!tempFile.renameTo(destination)) {
                tempFile.copyTo(destination, overwrite = true)
                tempFile.delete()
            }

            Result.success(destination)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    /** Returns true if unknown app installation is permitted on Android 8.0+. */
    fun canInstallApks(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** Opens settings screen to grant unknown app install permission for Nirvor. */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /** Launches Android Package Installer to install or update the APK. */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            val authority = "${context.packageName}.files"
            val uri = FileProvider.getUriForFile(context, authority, apkFile)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
