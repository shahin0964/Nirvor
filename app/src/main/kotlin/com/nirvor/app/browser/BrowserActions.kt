package com.nirvor.app.browser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.nirvor.app.MainActivity
import com.nirvor.app.R
import com.nirvor.app.downloader.Analyzer
import com.nirvor.app.downloader.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Menu actions that talk to Android: sharing, printing, saving pages, shortcuts, cookies. */
object BrowserActions {

    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
        toast(context, "কপি হয়েছে")
    }

    fun shareLink(context: Context, url: String, title: String?) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
        if (!title.isNullOrBlank()) send.putExtra(Intent.EXTRA_SUBJECT, title)
        start(context, Intent.createChooser(send, "লিংক শেয়ার"))
    }

    /** Opens the page in another browser. */
    fun openInBrowser(context: Context, url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        start(context, Intent.createChooser(view, "কোন ব্রাউজারে খুলবেন"))
    }

    /** Opens the page in the site's own app (YouTube, Facebook, …) if there is one. */
    fun openInApp(context: Context, url: String) {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        start(context, Intent.createChooser(view, "কোন অ্যাপে খুলবেন"))
    }

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast(context, "খোলার মতো অ্যাপ পাওয়া যায়নি")
        }
    }

    // ---------------------------------------------------------------- shortcuts

    /** "Add to Home screen": a shortcut that opens this page in Nirvor's browser. */
    fun addToHomeScreen(context: Context, url: String, title: String, icon: android.graphics.Bitmap?) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            toast(context, "এই ফোনের হোম স্ক্রিনে শর্টকাট যোগ করা যায় না")
            return
        }
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_BROWSER)
            .putExtra(MainActivity.EXTRA_URL, url)
        val info = ShortcutInfoCompat.Builder(context, "web_" + url.hashCode())
            .setShortLabel(title.ifBlank { Search.displayHost(url) }.take(24))
            .setIcon(if (icon != null) IconCompat.createWithBitmap(icon) else IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    /** "Create shortcut": opens Nirvor straight into the browser. */
    fun addBrowserShortcut(context: Context) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            toast(context, "এই ফোনের হোম স্ক্রিনে শর্টকাট যোগ করা যায় না")
            return
        }
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_BROWSER)
        val info = ShortcutInfoCompat.Builder(context, "nirvor_browser")
            .setShortLabel("Nirvor Browser")
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    /** A second icon in the app drawer that opens the browser directly. */
    fun setLauncherIcon(context: Context, on: Boolean) {
        val component = ComponentName(context, "com.nirvor.app.BrowserLauncher")
        context.packageManager.setComponentEnabledSetting(
            component,
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    // ---------------------------------------------------------------- print / save

    fun print(context: Context, webView: WebView, title: String) {
        val pm = context.getSystemService(PrintManager::class.java)
        if (pm == null) {
            toast(context, "এই ফোনে প্রিন্ট করা যায় না")
            return
        }
        val name = title.ifBlank { "Nirvor page" }
        pm.print(name, webView.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
    }

    fun savedPagesDir(context: Context): File = File(context.filesDir, "saved_pages").apply { mkdirs() }

    /** Saves the page for reading offline (.mht), and a copy in Nirvor/Downloads/General. */
    fun savePage(context: Context, webView: WebView, title: String) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val base = if (BrowserSettings.titleForSavedPages && title.isNotBlank()) Analyzer.safeName(title).take(60) else "page_$stamp"
        var file = File(savedPagesDir(context), "$base.mht")
        if (file.exists()) file = File(savedPagesDir(context), "${base}_$stamp.mht")
        val app = context.applicationContext
        webView.saveWebArchive(file.absolutePath, false) { path ->
            if (path == null) {
                toast(app, "পেজ সেভ হয়নি")
                return@saveWebArchive
            }
            if (BrowserSettings.savePagesCopy) {
                val saved = File(path)
                Browser.io.launch {
                    try {
                        val tmp = File(app.cacheDir, saved.name)
                        saved.copyTo(tmp, overwrite = true)
                        Storage.publish(app, tmp, "multipart/related")
                    } catch (e: Exception) {
                        // the browser's own copy is still there
                    }
                }
            }
            toast(app, "পেজ সেভ হয়েছে")
        }
    }

    fun shareFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, Intent.createChooser(send, null))
    }

    /** "View page source with": opens the HTML in another app. */
    fun viewSourceWith(context: Context, html: String, title: String) {
        val file = File(context.cacheDir, Analyzer.safeName(title.ifBlank { "page" }).take(40) + ".html.txt")
        file.writeText(html)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/plain")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        start(context, Intent.createChooser(view, "কোন অ্যাপে সোর্স দেখবেন"))
    }

    /** evaluateJavascript returns JSON; a string result comes back quoted. */
    fun decodeJsString(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        return try {
            val v = JSONTokener(raw).nextValue()
            v as? String ?: v.toString()
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- clearing data

    class ClearChoice(
        var history: Boolean = true,
        var cookies: Boolean = false,
        var cache: Boolean = true,
        var storage: Boolean = false,
        var permissions: Boolean = false,
    )

    suspend fun clearData(context: Context, c: ClearChoice) {
        if (c.history) withContext(Dispatchers.IO) { BrowserDatabase.get(context).dao().clearHistory() }
        withContext(Dispatchers.Main) {
            if (c.cookies) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
            if (c.cache) {
                val wv = Browser.current?.webView ?: WebView(context.applicationContext)
                wv.clearCache(true)
            }
            if (c.storage) WebStorage.getInstance().deleteAllData()
            if (c.permissions) GeolocationPermissions.getInstance().clearAll()
        }
    }

    // ---------------------------------------------------------------- cookies

    /**
     * Imports cookies: either a Netscape "cookies.txt" file (from a desktop browser extension),
     * or "name=value; name2=value2" text for one site. Returns how many were added.
     */
    fun importCookies(text: String, site: String?): Int {
        val cm = CookieManager.getInstance()
        var n = 0
        val lines = text.lines()
        val netscape = lines.any { it.split('\t').size >= 7 }
        if (netscape) {
            lines.forEach { raw ->
                val line = raw.removePrefix("#HttpOnly_")
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val p = line.split('\t')
                if (p.size < 7) return@forEach
                val domain = p[0]
                val path = p[2].ifBlank { "/" }
                val secure = p[3].equals("TRUE", true)
                val expiry = p[4].toLongOrNull() ?: 0L
                val name = p[5]
                val value = p[6]
                val host = domain.trimStart('.')
                val sb = StringBuilder("$name=$value; Domain=$domain; Path=$path")
                if (secure) sb.append("; Secure")
                if (expiry > 0) {
                    val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                        .apply { timeZone = java.util.TimeZone.getTimeZone("GMT") }
                        .format(Date(expiry * 1000))
                    sb.append("; Expires=").append(date)
                }
                cm.setCookie((if (secure) "https://" else "http://") + host + path, sb.toString())
                n++
            }
        } else {
            val target = site?.trim().orEmpty()
            if (target.isEmpty()) return 0
            val url = if (target.startsWith("http")) target else "https://$target"
            text.split(';').map { it.trim() }.filter { it.contains('=') }.forEach { pair ->
                cm.setCookie(url, pair)
                n++
            }
        }
        cm.flush()
        return n
    }

    // ---------------------------------------------------------------- bookmarks backup

    suspend fun exportBookmarks(context: Context, out: Uri): Int = withContext(Dispatchers.IO) {
        val list = BrowserDatabase.get(context).dao().allBookmarks()
        val arr = JSONArray()
        list.forEach { b -> arr.put(JSONObject().put("url", b.url).put("title", b.title).put("folder", b.folder).put("created", b.createdAt)) }
        context.contentResolver.openOutputStream(out)?.use { it.write(arr.toString(2).toByteArray()) }
        list.size
    }

    suspend fun importBookmarks(context: Context, input: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(input)?.use { it.bufferedReader().readText() } ?: return@withContext 0
        val dao = BrowserDatabase.get(context).dao()
        var n = 0
        val trimmed = text.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("url")
                if (url.isBlank() || dao.bookmarkFor(url) != null) continue
                dao.addBookmark(BookmarkEntity(url = url, title = o.optString("title"), folder = o.optString("folder")))
                n++
            }
        } else {
            // Netscape bookmarks HTML exported by desktop browsers.
            Regex("""<A[^>]*HREF="([^"]+)"[^>]*>([^<]*)</A>""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
                val url = m.groupValues[1]
                if (url.startsWith("http") && dao.bookmarkFor(url) == null) {
                    dao.addBookmark(BookmarkEntity(url = url, title = m.groupValues[2]))
                    n++
                }
            }
        }
        n
    }
}
