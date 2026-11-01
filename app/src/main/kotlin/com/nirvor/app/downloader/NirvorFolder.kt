package com.nirvor.app.downloader

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

/**
 * The app's own folder on the phone, chosen once by the user in the system file manager:
 *
 *     Nirvor/
 *     └── Downloads/
 *         ├── Compressed   zip, rar, 7z …
 *         ├── Documents    pdf, doc, xls, ppt, txt …
 *         ├── General      web pages and data (html, json, xml)
 *         ├── Music        mp3, m4a, opus …
 *         ├── Others       anything else
 *         ├── Photos       jpg, png, webp …
 *         ├── Programs     apk, exe …
 *         ├── Subtitles    srt, vtt, ass
 *         ├── Torrents     .torrent files
 *         └── Videos       mp4, mkv, webm …
 *
 * The user only creates "Nirvor" (Android does not let apps create folders in the phone's main
 * storage without asking); everything inside is created here, and re-created if deleted.
 */
object NirvorFolder {
    const val ROOT = "Nirvor"
    const val DOWNLOADS = "Downloads"

    private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

    enum class Category(val folder: String, val bn: String) {
        Compressed("Compressed", "কম্প্রেসড"),
        Documents("Documents", "ডকুমেন্ট"),
        General("General", "সাধারণ"),
        Music("Music", "গান ও অডিও"),
        Others("Others", "অন্যান্য"),
        Photos("Photos", "ছবি"),
        Programs("Programs", "অ্যাপ"),
        Subtitles("Subtitles", "সাবটাইটেল"),
        Torrents("Torrents", "টরেন্ট ফাইল"),
        Videos("Videos", "ভিডিও"),
    }

    private val byExt: Map<String, Category> = buildMap {
        listOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso").forEach { put(it, Category.Compressed) }
        listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "txt", "csv", "epub", "md")
            .forEach { put(it, Category.Documents) }
        listOf("html", "htm", "mhtml", "mht", "json", "xml").forEach { put(it, Category.General) }
        listOf("mp3", "m4a", "aac", "opus", "ogg", "oga", "wav", "flac", "wma", "amr", "mka").forEach { put(it, Category.Music) }
        listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif", "svg").forEach { put(it, Category.Photos) }
        listOf("apk", "xapk", "apks", "apkm", "exe", "msi", "dmg", "deb", "rpm", "appimage", "jar").forEach { put(it, Category.Programs) }
        listOf("srt", "vtt", "ass", "ssa", "sub", "sbv", "lrc").forEach { put(it, Category.Subtitles) }
        put("torrent", Category.Torrents)
        listOf("mp4", "mkv", "webm", "mov", "avi", "flv", "3gp", "m4v", "ts", "wmv", "mpg", "mpeg").forEach { put(it, Category.Videos) }
    }

    fun categoryFor(fileName: String, mime: String?): Category {
        byExt[fileName.substringAfterLast('.', "").lowercase(Locale.US)]?.let { return it }
        val m = mime.orEmpty()
        return when {
            m.startsWith("video/") -> Category.Videos
            m.startsWith("audio/") -> Category.Music
            m.startsWith("image/") -> Category.Photos
            m == "application/vnd.android.package-archive" -> Category.Programs
            m == "application/pdf" || m.startsWith("text/plain") -> Category.Documents
            m.contains("zip") || m.contains("compressed") || m.contains("x-rar") -> Category.Compressed
            m.startsWith("text/html") -> Category.General
            else -> Category.Others
        }
    }

    /** Where the folder picker opens: the top of the phone's storage. */
    fun pickerStart(): Uri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE, "primary:")

    // ---------------------------------------------------------------- saved choice

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("nirvor_downloader", Context.MODE_PRIVATE)

    private fun tree(context: Context): Uri? = prefs(context).getString("folder_tree", null)?.let(Uri::parse)
    private fun rootId(context: Context): String? = prefs(context).getString("folder_root_id", null)

    /** The first-run folder page was shown (finished or skipped). */
    fun setupSeen(context: Context): Boolean = prefs(context).getBoolean("folder_setup_seen", false)

    fun markSetupSeen(context: Context) {
        prefs(context).edit().putBoolean("folder_setup_seen", true).apply()
    }

    /** True when the Nirvor folder is chosen, still allowed and still there. */
    fun isReady(context: Context): Boolean {
        val t = tree(context) ?: return false
        val id = rootId(context) ?: return false
        val allowed = context.contentResolver.persistedUriPermissions.any { it.uri == t && it.isWritePermission }
        return allowed && exists(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(t, id))
    }

    /** "Nirvor/Downloads" as the user sees it in the Files app (e.g. "Internal storage › Nirvor/Downloads"). */
    fun displayPath(context: Context): String? {
        val id = rootId(context) ?: return null
        val place = if (id.startsWith("primary:")) "ফোন স্টোরেজ" else "মেমোরি কার্ড"
        return "$place › " + id.substringAfter(':').trimEnd('/') + "/" + DOWNLOADS
    }

    // ---------------------------------------------------------------- setup

    class Setup(val path: String, val created: List<String>)

    /**
     * Called with the folder the user picked. If it is not itself called "Nirvor", a Nirvor
     * folder is made inside it. Then Downloads and every category folder are created.
     */
    fun adopt(context: Context, picked: Uri): Setup {
        val resolver = context.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(picked, flags)

        // Let go of an older choice.
        val old = tree(context)
        if (old != null && old != picked) {
            try {
                resolver.releasePersistableUriPermission(old, flags)
            } catch (e: Exception) {
                // already released
            }
        }

        val pickedId = DocumentsContract.getTreeDocumentId(picked)
        val pickedName = nameOf(resolver, DocumentsContract.buildDocumentUriUsingTree(picked, pickedId))
        val created = mutableListOf<String>()
        val rootId = if (pickedName.equals(ROOT, ignoreCase = true)) {
            pickedId
        } else {
            created += ROOT
            ensureDir(resolver, picked, pickedId, ROOT)
        }
        val downloadsId = ensureDir(resolver, picked, rootId, DOWNLOADS)
        created += DOWNLOADS
        Category.entries.forEach {
            ensureDir(resolver, picked, downloadsId, it.folder)
            created += it.folder
        }
        prefs(context).edit()
            .putString("folder_tree", picked.toString())
            .putString("folder_root_id", rootId)
            .putBoolean("folder_setup_seen", true)
            .apply()
        return Setup(displayPath(context) ?: "$ROOT/$DOWNLOADS", created)
    }

    // ---------------------------------------------------------------- saving

    /**
     * Copies a finished download into Nirvor/Downloads/<category>. Returns null when no folder is
     * set up (or it was removed), so the caller can fall back to the phone's Download folder.
     */
    fun save(context: Context, file: File, mime: String): Storage.Published? {
        val t = tree(context) ?: return null
        val rid = rootId(context) ?: return null
        val resolver = context.contentResolver
        return try {
            if (!exists(resolver, DocumentsContract.buildDocumentUriUsingTree(t, rid))) return null
            val category = categoryFor(file.name, mime)
            val downloadsId = ensureDir(resolver, t, rid, DOWNLOADS)
            val dirId = ensureDir(resolver, t, downloadsId, category.folder)
            val parent = DocumentsContract.buildDocumentUriUsingTree(t, dirId)
            val docMime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(file.name.substringAfterLast('.', "").lowercase(Locale.US))
                ?: "application/octet-stream"
            val doc = DocumentsContract.createDocument(resolver, parent, docMime, file.name) ?: return null
            val size = file.length()
            (resolver.openOutputStream(doc) ?: return null).use { out ->
                file.inputStream().use { it.copyTo(out, 256 * 1024) }
            }
            file.delete()
            val name = nameOf(resolver, doc) ?: file.name
            scan(context, DocumentsContract.getDocumentId(doc), mime)
            Storage.Published(doc, name, size, mime)
        } catch (e: Exception) {
            null
        }
    }

    /** Lets the gallery / music apps see the new file straight away. */
    private fun scan(context: Context, docId: String, mime: String) {
        if (!docId.startsWith("primary:")) return
        try {
            @Suppress("DEPRECATION")
            val path = File(Environment.getExternalStorageDirectory(), docId.substringAfter(':')).absolutePath
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime), null)
        } catch (e: Exception) {
            // the file is saved either way
        }
    }

    // ---------------------------------------------------------------- document helpers

    private fun exists(resolver: ContentResolver, doc: Uri): Boolean = try {
        resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { it.moveToFirst() } ?: false
    } catch (e: Exception) {
        false
    }

    private fun nameOf(resolver: ContentResolver, doc: Uri): String? = try {
        resolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /** Finds the child folder [name] (any letter case), creating it if missing. Returns its id. */
    private fun ensureDir(resolver: ContentResolver, tree: Uri, parentId: String, name: String): String {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                if (isDir && c.getString(1).equals(name, ignoreCase = true)) return c.getString(0)
            }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val made = DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw IllegalStateException("ফোল্ডার বানানো গেল না: $name")
        return DocumentsContract.getDocumentId(made)
    }

    fun isDocument(context: Context, uri: Uri): Boolean = DocumentsContract.isDocumentUri(context, uri)
}
