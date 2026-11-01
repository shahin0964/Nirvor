package com.nirvor.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ProfileStore
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.nirvor.app.downloader.Analyzer
import com.nirvor.app.downloader.Downloads
import com.nirvor.app.downloader.Kind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.Locale

/** A media file a page loaded (video, audio, stream playlist, or an extra type the user asked for). */
data class Sniffed(val url: String, val kind: Kind, val ext: String, val stream: Boolean, val host: String, val pageTitle: String)

/** Something the page requested; shown in "View page resources". */
data class PageResource(val url: String, val type: String, val blocked: Boolean)

/** A file about to be downloaded (the "download editor" lets the user rename it first). */
data class FileOffer(val url: String, val name: String, val mime: String, val size: Long, val referer: String?, val tabId: Long)

/** What was long-pressed: a link, an image, or an image inside a link. */
data class ContextTarget(val link: String?, val image: String?, val tabId: Long)

/** A page wants to open a new window. */
data class PopupAsk(val url: String, val fromTab: Long, val incognito: Boolean, val userGesture: Boolean)

/** A page wants the camera / microphone / location / protected media. */
class PermissionAsk(
    val origin: String,
    val labels: List<String>,
    val androidPermissions: List<String>,
    val askUser: Boolean,
    val onResult: (Boolean) -> Unit,
)

/** A page wants to open another app. */
data class ExternalAsk(val intent: Intent, val label: String)

/** One browser tab. Its WebView is created when the tab is first shown. */
class BrowserTab(val id: Long, val incognito: Boolean) {
    var webView: WebView? = null
        internal set
    var container: SwipeRefreshLayout? = null
        internal set

    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var favicon by mutableStateOf<Bitmap?>(null)
    var progress by mutableIntStateOf(100)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var onStart by mutableStateOf(true)
    var desktop by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
    var findMatches by mutableIntStateOf(0)
    var findIndex by mutableIntStateOf(0)
    val sniffed = mutableStateListOf<Sniffed>()
    val resources = mutableStateListOf<PageResource>()

    /** Address to load when the tab is first shown (restored / background tabs). */
    internal var pending: String? = null
    internal var openedForDownload = false
    internal var historyId = 0L
    internal var historyUrl = ""
    internal var sniffPage = ""

    @Volatile internal var pageHost: String? = null
}

/**
 * All tabs and everything the browser does that is not drawing: creating and configuring
 * WebViews, ad blocking, capturing media, history, pop-ups, permissions and downloads.
 */
@SuppressLint("StaticFieldLeak")
object Browser {
    const val INCOGNITO_PROFILE = "nirvor_incognito"

    val tabs = mutableStateListOf<BrowserTab>()
    var currentId by mutableLongStateOf(-1L)
    val current: BrowserTab? get() = tabs.firstOrNull { it.id == currentId }

    var offer by mutableStateOf<FileOffer?>(null)
    var contextTarget by mutableStateOf<ContextTarget?>(null)
    var popupAsk by mutableStateOf<PopupAsk?>(null)
    var permissionAsk by mutableStateOf<PermissionAsk?>(null)
    var externalAsk by mutableStateOf<ExternalAsk?>(null)
    var fullscreen by mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null)
    var chooser by mutableStateOf<Pair<ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams>?>(null)
    /** Last closed tab, for "Undo". */
    var closedTab by mutableStateOf<Pair<String, Boolean>?>(null)

    private var nextId = 1L
    private var appContext: Context? = null
    private var activity: Context? = null
    private val main = Handler(Looper.getMainLooper())
    internal val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Per-site choices (host → settings), cached from the database. */
    @Volatile var sites: Map<String, SiteSettingEntity> = emptyMap()
        private set

    // ---------------------------------------------------------------- lifecycle

    /** The browser screen opened. */
    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        activity = context
        tabs.forEach { t -> (t.webView?.context as? MutableContextWrapper)?.baseContext = context }
        if (tabs.isEmpty()) restoreTabs()
        AdBlocker.reload(app)
        refreshSites()
        if (BrowserSettings.pauseTimers) tabs.firstOrNull { it.webView != null }?.webView?.resumeTimers()
        tabs.forEach { it.webView?.onResume() }
    }

    /** The browser screen closed (tabs stay alive unless the user exits the browser). */
    fun stop() {
        val app = appContext ?: return
        exitFullscreen()
        tabs.forEach { t ->
            (t.container?.parent as? ViewGroup)?.removeView(t.container)
            (t.webView?.context as? MutableContextWrapper)?.baseContext = app
            t.webView?.onPause()
        }
        if (BrowserSettings.pauseTimers) tabs.firstOrNull { it.webView != null }?.webView?.pauseTimers()
        activity = null
        CookieManager.getInstance().flush()
        // After "Exit" the list is empty on purpose; don't overwrite what was remembered.
        if (tabs.isNotEmpty()) saveTabs()
    }

    fun refreshSites() {
        val app = appContext ?: return
        io.launch {
            try {
                sites = BrowserDatabase.get(app).dao().allSiteSettings().associateBy { it.host }
            } catch (e: Exception) {
                // keep the old map
            }
        }
    }

    fun siteFor(url: String?): SiteSettingEntity? {
        val host = url?.let { Uri.parse(it).host }?.lowercase(Locale.US)?.removePrefix("www.") ?: return null
        return sites[host]
    }

    // ---------------------------------------------------------------- tabs

    fun newTab(url: String?, incognito: Boolean = BrowserSettings.incognitoDefault, background: Boolean = false): BrowserTab {
        val tab = BrowserTab(nextId++, incognito)
        tab.desktop = BrowserSettings.desktopPersistent && BrowserSettings.desktopSite
        if (url.isNullOrBlank()) {
            val home = BrowserSettings.homepage
            if (home.isNotBlank()) {
                tab.pending = Search.toUrl(home, incognito)
                tab.onStart = false
                tab.url = tab.pending ?: ""
            }
        } else {
            tab.pending = url
            tab.onStart = false
            tab.url = url
        }
        val at = tabs.indexOfFirst { it.id == currentId }
        if (at >= 0 && background) tabs.add(at + 1, tab) else tabs.add(tab)
        if (!background) currentId = tab.id
        saveTabs()
        return tab
    }

    fun select(tab: BrowserTab) {
        currentId = tab.id
    }

    fun close(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        if (tab.url.isNotBlank() && !tab.onStart) closedTab = tab.url to tab.incognito
        destroy(tab)
        tabs.removeAt(index)
        if (currentId == tab.id) {
            val next = tabs.getOrNull(index) ?: tabs.getOrNull(index - 1)
            currentId = next?.id ?: -1L
        }
        if (tabs.none { it.incognito }) dropIncognitoData()
        if (tabs.isEmpty()) newTab(null, incognito = false)
        saveTabs()
    }

    fun closeAll(incognitoOnly: Boolean = false) {
        tabs.filter { !incognitoOnly || it.incognito }.forEach { destroy(it) }
        tabs.removeAll { !incognitoOnly || it.incognito }
        dropIncognitoData()
        if (tabs.isEmpty()) newTab(null, incognito = false) else if (current == null) currentId = tabs.last().id
        saveTabs()
    }

    fun clone(tab: BrowserTab) {
        val url = tab.webView?.url ?: tab.url
        newTab(url.ifBlank { null }, tab.incognito)
    }

    fun move(tab: BrowserTab, delta: Int) {
        val i = tabs.indexOf(tab)
        val j = (i + delta).coerceIn(0, tabs.size - 1)
        if (i < 0 || i == j) return
        tabs.removeAt(i)
        tabs.add(j, tab)
        saveTabs()
    }

    fun undoClose() {
        val (url, inc) = closedTab ?: return
        closedTab = null
        newTab(url, inc)
    }

    private fun destroy(tab: BrowserTab) {
        val wv = tab.webView ?: return
        (tab.container?.parent as? ViewGroup)?.removeView(tab.container)
        tab.container?.removeAllViews()
        wv.stopLoading()
        wv.destroy()
        tab.webView = null
        tab.container = null
    }

    /** Leaving the browser for good ("Exit Browser"). */
    fun exit(context: Context, rememberTabs: Boolean) {
        if (rememberTabs) saveTabs()
        tabs.forEach { destroy(it) }
        tabs.clear()
        currentId = -1L
        dropIncognitoData()
        if (!rememberTabs) saveTabs()
        clearOnExit(context)
    }

    private fun dropIncognitoData() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return
        try {
            ProfileStore.getInstance().deleteProfile(INCOGNITO_PROFILE)
        } catch (e: Exception) {
            // still in use or never created
        }
    }

    private fun clearOnExit(context: Context) {
        if (BrowserSettings.clearCookiesOnExit) CookieManager.getInstance().removeAllCookies(null)
        if (BrowserSettings.clearStorageOnExit) WebStorage.getInstance().deleteAllData()
        if (BrowserSettings.clearCacheOnExit) WebView(context.applicationContext).apply { clearCache(true); destroy() }
        if (BrowserSettings.clearHistoryOnExit) {
            val app = context.applicationContext
            io.launch { BrowserDatabase.get(app).dao().clearHistory() }
        }
    }

    // ---------------------------------------------------------------- saving / restoring tabs

    private fun prefs() = appContext?.getSharedPreferences("nirvor_browser_tabs", Context.MODE_PRIVATE)

    fun saveTabs() {
        val sp = prefs() ?: return
        val arr = JSONArray()
        tabs.filter { !it.incognito }.forEach { t ->
            val u = t.webView?.url ?: t.pending ?: t.url
            if (!t.onStart && u.isNotBlank()) arr.put(JSONObject().put("url", u).put("title", t.title))
        }
        val cur = tabs.filter { !it.incognito && !it.onStart }.indexOfFirst { it.id == currentId }
        sp.edit().putString("tabs", arr.toString()).putInt("current", cur).apply()
    }

    private fun restoreTabs() {
        val sp = prefs()
        if (BrowserSettings.restoreTabs && sp != null) {
            try {
                val arr = JSONArray(sp.getString("tabs", "[]"))
                val cur = sp.getInt("current", 0)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val t = newTab(o.optString("url"), incognito = false, background = true)
                    t.title = o.optString("title")
                }
                tabs.getOrNull(cur.coerceAtLeast(0))?.let { currentId = it.id }
            } catch (e: Exception) {
                // nothing saved
            }
        }
        if (tabs.isEmpty()) newTab(null)
        if (current == null) currentId = tabs.last().id
    }

    // ---------------------------------------------------------------- WebView

    /** Gets the tab's WebView (creating and setting it up the first time). */
    fun viewFor(tab: BrowserTab): SwipeRefreshLayout {
        tab.container?.let { return it }
        val ctx = activity ?: appContext ?: throw IllegalStateException("browser not started")
        val wv = WebView(MutableContextWrapper(ctx))
        if (tab.incognito && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE)
                WebViewCompat.setProfile(wv, INCOGNITO_PROFILE)
            } catch (e: Exception) {
                // falls back to the normal profile
            }
        }
        tab.webView = wv
        configure(tab)
        wv.webViewClient = TabClient(tab)
        wv.webChromeClient = TabChrome(tab)
        wv.setDownloadListener { link, _, disposition, mime, length -> onPageDownload(tab, link, disposition, mime, length) }
        wv.setOnLongClickListener { onLongPress(tab, wv) }
        wv.setFindListener { active, count, _ ->
            tab.findIndex = active
            tab.findMatches = count
        }
        val swipe = SwipeRefreshLayout(appContext ?: ctx)
        swipe.addView(wv, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        swipe.setColorSchemeColors(0xFF0BA765.toInt())
        swipe.setOnRefreshListener { wv.reload() }
        swipe.setOnChildScrollUpCallback { _, _ -> wv.scrollY > 0 }
        swipe.isEnabled = BrowserSettings.pullToRefresh
        tab.container = swipe
        tab.pending?.let { url ->
            tab.pending = null
            load(tab, url)
        }
        return swipe
    }

    private fun cookieManager(tab: BrowserTab): CookieManager {
        val wv = tab.webView
        if (tab.incognito && wv != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            try {
                return WebViewCompat.getProfile(wv).cookieManager
            } catch (e: Exception) {
                // default
            }
        }
        return CookieManager.getInstance()
    }

    private val defaultUa: String by lazy {
        try {
            WebSettings.getDefaultUserAgent(appContext ?: return@lazy Search.ANDROID_UA).replace("; wv", "")
        } catch (e: Exception) {
            Search.ANDROID_UA
        }
    }

    fun userAgent(tab: BrowserTab): String {
        if (tab.desktop) return Search.DESKTOP_UA
        return when (BrowserSettings.userAgentMode) {
            "desktop" -> Search.DESKTOP_UA
            "iphone" -> Search.IPHONE_UA
            "android" -> Search.ANDROID_UA
            "custom" -> BrowserSettings.customUserAgent.ifBlank { defaultUa }
            else -> defaultUa
        }
    }

    /** Applies the settings (and this site's own choices) to the tab's WebView. */
    @SuppressLint("SetJavaScriptEnabled")
    fun configure(tab: BrowserTab, url: String? = tab.url) {
        val wv = tab.webView ?: return
        val site = siteFor(url)
        site?.desktop?.takeIf { it >= 0 }?.let { tab.desktop = it == 1 }
        with(wv.settings) {
            javaScriptEnabled = site?.javascript?.takeIf { it >= 0 }?.let { it == 1 } ?: BrowserSettings.javascript
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = BrowserSettings.overviewMode || tab.desktop
            useWideViewPort = BrowserSettings.wideViewport || tab.desktop
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = BrowserSettings.popupHandling != "block"
            setSupportMultipleWindows(BrowserSettings.allowNewWindows)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            blockNetworkImage = site?.images?.takeIf { it >= 0 }?.let { it == 0 } ?: BrowserSettings.blockImages
            loadsImagesAutomatically = true
            textZoom = BrowserSettings.textSize
            layoutAlgorithm = if (BrowserSettings.textReflow) WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING else WebSettings.LayoutAlgorithm.NORMAL
            defaultTextEncodingName = BrowserSettings.textEncoding
            userAgentString = userAgent(tab)
            allowFileAccess = false
            setGeolocationEnabled(true)
            if (Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = BrowserSettings.safeBrowsing
            if (tab.incognito && !WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
        }
        if (BrowserSettings.removeXRequestedWith &&
            WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)
        ) {
            try {
                @Suppress("DEPRECATION")
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(wv.settings, emptySet())
            } catch (e: Exception) {
                // not supported by this WebView
            }
        }
        val cm = cookieManager(tab)
        cm.setAcceptCookie(if (tab.incognito) BrowserSettings.cookiesIncognito else BrowserSettings.cookies)
        cm.setAcceptThirdPartyCookies(wv, !BrowserSettings.blockThirdPartyCookies)
        tab.container?.isEnabled = BrowserSettings.pullToRefresh
    }

    /** Re-applies settings to every open tab (after a setting changed). */
    fun applySettingsToAll() {
        tabs.forEach { configure(it) }
    }

    /** Extra request headers ("Save-Data", "Do Not Track"). */
    private fun headers(): Map<String, String> {
        val h = HashMap<String, String>()
        if (BrowserSettings.saveData) h["Save-Data"] = "on"
        if (BrowserSettings.doNotTrack) h["DNT"] = "1"
        return h
    }

    /** Opens [input] (a web address or words to search) in the tab. */
    fun load(tab: BrowserTab, input: String) {
        val url = Search.toUrl(input, tab.incognito)
        tab.onStart = false
        tab.url = url
        val wv = tab.webView
        if (wv == null) {
            tab.pending = url
            return
        }
        configure(tab, url)
        tab.pageHost = Uri.parse(url).host
        val h = headers()
        if (h.isEmpty()) wv.loadUrl(url) else wv.loadUrl(url, h)
    }

    fun setDesktop(tab: BrowserTab, on: Boolean) {
        tab.desktop = on
        BrowserSettings.desktopSite = on
        configure(tab)
        tab.webView?.reload()
    }

    // ---------------------------------------------------------------- page scripts

    internal fun injectDisplayScripts(tab: BrowserTab, view: WebView) {
        val site = siteFor(tab.url)
        val dark = site?.darkMode?.takeIf { it >= 0 }?.let { it == 1 } ?: pagesDark()
        view.evaluateJavascript(if (dark) PageScripts.DARK_ON else PageScripts.DARK_OFF, null)
        if (BrowserSettings.renderingMode != "normal") view.evaluateJavascript(PageScripts.rendering(BrowserSettings.renderingMode), null)
        if (BrowserSettings.forceTextSelection) view.evaluateJavascript(PageScripts.FORCE_SELECT, null)
    }

    /** Whether pages are shown dark (menu switch, or the browser's dark-mode setting). */
    fun pagesDark(): Boolean {
        if (BrowserSettings.darkPages) return true
        return when (BrowserSettings.darkModeBrowser) {
            "on" -> true
            "theme" -> {
                val ui = appContext?.resources?.configuration?.uiMode ?: 0
                (ui and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            else -> false
        }
    }

    fun refreshDisplay() {
        tabs.forEach { t -> t.webView?.let { injectDisplayScripts(t, it) } }
    }

    // ---------------------------------------------------------------- media capture & resources

    private val mediaExt = mapOf(
        "mp4" to Kind.Video, "webm" to Kind.Video, "mkv" to Kind.Video, "mov" to Kind.Video,
        "m4v" to Kind.Video, "3gp" to Kind.Video, "flv" to Kind.Video, "avi" to Kind.Video,
        "m3u8" to Kind.Video, "mpd" to Kind.Video,
        "mp3" to Kind.Audio, "m4a" to Kind.Audio, "aac" to Kind.Audio, "ogg" to Kind.Audio,
        "opus" to Kind.Audio, "wav" to Kind.Audio, "flac" to Kind.Audio,
    )

    private val rangeParam = Regex("""[?&](?:bytestart|byteend|range)=[^&#]*""")

    private fun stripRange(u: String): String {
        if (!rangeParam.containsMatchIn(u)) return u
        return u.replace(rangeParam) { m -> if (m.value.startsWith("?")) "?" else "" }.replace("?&", "?").trimEnd('?', '&')
    }

    private fun extraTypes(): Set<String> =
        BrowserSettings.extraCaptureTypes.split(',', ' ', ';').map { it.trim().trimStart('.').lowercase(Locale.US) }
            .filter { it.isNotEmpty() }.toSet()

    internal fun resourceType(uri: Uri, request: WebResourceRequest): String {
        if (request.isForMainFrame) return "page"
        val ext = (uri.lastPathSegment ?: "").substringAfterLast('.', "").lowercase(Locale.US)
        return when {
            ext in setOf("jpg", "jpeg", "png", "gif", "webp", "svg", "ico", "bmp", "avif") -> "image"
            ext == "js" || ext == "mjs" -> "script"
            ext == "css" -> "style"
            ext in mediaExt.keys || ext == "ts" || ext == "m4s" -> "media"
            ext in setOf("woff", "woff2", "ttf", "otf", "eot") -> "font"
            request.requestHeaders["Accept"]?.contains("image") == true -> "image"
            request.requestHeaders["Accept"]?.contains("text/css") == true -> "style"
            else -> "other"
        }
    }

    /** Runs on the WebView network thread for every request. */
    internal fun onRequest(tab: BrowserTab, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url ?: return null
        val scheme = uri.scheme ?: return null
        if (scheme != "http" && scheme != "https") return null
        val type = resourceType(uri, request)
        val adOn = siteFor("https://" + (tab.pageHost ?: ""))?.adblock?.takeIf { it >= 0 }?.let { it == 1 } ?: BrowserSettings.blockAds
        val block = adOn && !request.isForMainFrame && AdBlocker.isBlocked(uri, tab.pageHost)
        val link = uri.toString()
        main.post {
            if (tab.resources.size < 1500) tab.resources.add(PageResource(link, type, block))
            if (block) tab.blocked++
        }
        if (block) return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        if (BrowserSettings.captureMedia) sniff(tab, uri)
        return null
    }

    private fun sniff(tab: BrowserTab, uri: Uri) {
        try {
            val host = uri.host?.lowercase(Locale.US) ?: return
            if (host.endsWith("googlevideo.com")) return
            if (host.contains("doubleclick") || host.contains("googlesyndication") || host.contains("imasdk")) return
            val last = uri.lastPathSegment?.lowercase(Locale.US) ?: return
            val ext = last.substringAfterLast('.', "")
            val kind = mediaExt[ext] ?: if (ext.isNotEmpty() && ext in extraTypes()) Kind.Other else return
            val clean = stripRange(uri.toString())
            val title = tab.title
            main.post {
                if (tab.sniffed.none { it.url == clean } && tab.sniffed.size < 60) {
                    tab.sniffed.add(Sniffed(clean, kind, ext, ext == "m3u8" || ext == "mpd", host, title))
                }
            }
        } catch (e: Exception) {
            // odd address: ignore
        }
    }

    // ---------------------------------------------------------------- downloads

    /** File name for something captured from a page. */
    fun nameFor(item: Sniffed): String {
        val base = if (BrowserSettings.titleAsFileName && item.pageTitle.isNotBlank()) {
            Analyzer.safeName(item.pageTitle)
        } else {
            Analyzer.safeName(Uri.parse(item.url).lastPathSegment?.substringBeforeLast('.') ?: item.host)
        }
        return "$base.${item.ext}"
    }

    private fun onPageDownload(tab: BrowserTab, link: String?, disposition: String?, mime: String?, length: Long) {
        val ctx = activity ?: appContext ?: return
        if (link.isNullOrBlank()) return
        if (!link.startsWith("http")) {
            Toast.makeText(ctx, "এই ফাইলটা সরাসরি নামানো যাচ্ছে না। ভিডিও হলে ডাউনলোড বাটন চাপুন।", Toast.LENGTH_LONG).show()
            return
        }
        val cleanMime = mime?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() && it != "application/octet-stream" }
        val name = URLUtil.guessFileName(link, disposition, cleanMime)
        val o = FileOffer(link, name, cleanMime ?: Analyzer.mimeForName(name, "application/octet-stream"), length, tab.url.ifBlank { null }, tab.id)
        if (BrowserSettings.skipEditorPageDownload) startDownload(ctx, o) else offer = o
    }

    /** Starts a download (from the editor, or directly when the editor is skipped). */
    fun startDownload(context: Context, o: FileOffer) {
        val app = context.applicationContext
        io.launch {
            Downloads.enqueueFile(app, o.url, o.name, o.mime, o.referer, o.size)
            main.post { Toast.makeText(app, "ডাউনলোড শুরু হয়েছে: ${o.name}", Toast.LENGTH_SHORT).show() }
        }
        val tab = tabs.firstOrNull { it.id == o.tabId }
        if (tab != null && BrowserSettings.closeTabAfterDownload && tab.openedForDownload && tab.webView?.canGoBack() != true) {
            main.post { close(tab) }
        }
    }

    fun offerFor(tab: BrowserTab, url: String, nameHint: String? = null): FileOffer {
        val guessed = URLUtil.guessFileName(url, null, null)
        val name = nameHint?.takeIf { it.isNotBlank() } ?: guessed
        return FileOffer(url, name, Analyzer.mimeForName(name, "application/octet-stream"), 0, tab.url.ifBlank { null }, tab.id)
    }

    /** Long-pressed image: download it (editor unless skipped). */
    fun downloadImage(context: Context, tab: BrowserTab, imageUrl: String) {
        if (imageUrl.startsWith("data:")) {
            Toast.makeText(context, "এই ছবিটা পেজের ভেতরে আঁকা, আলাদা ফাইল নেই", Toast.LENGTH_SHORT).show()
            return
        }
        val name = if (BrowserSettings.serverNameForImages) {
            URLUtil.guessFileName(imageUrl, null, null)
        } else {
            val ext = Uri.parse(imageUrl).lastPathSegment?.substringAfterLast('.', "")?.lowercase(Locale.US)
                ?.takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif", "svg") } ?: "jpg"
            Analyzer.safeName(tab.title.ifBlank { "image" }) + ".$ext"
        }
        val o = FileOffer(imageUrl, name, Analyzer.mimeForName(name, "image/jpeg"), 0, tab.url.ifBlank { null }, tab.id)
        if (BrowserSettings.skipEditorImage) startDownload(context, o) else offer = o
    }

    // ---------------------------------------------------------------- long press

    private fun onLongPress(tab: BrowserTab, wv: WebView): Boolean {
        val r = wv.hitTestResult
        return when (r.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                contextTarget = ContextTarget(r.extra, null, tab.id)
                true
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                contextTarget = ContextTarget(null, r.extra, tab.id)
                true
            }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val image = r.extra
                val handler = Handler(Looper.getMainLooper()) { msg: Message ->
                    val link = msg.data.getString("url")
                    contextTarget = ContextTarget(link, image, tab.id)
                    true
                }
                wv.requestFocusNodeHref(handler.obtainMessage())
                true
            }
            else -> false
        }
    }

    // ---------------------------------------------------------------- full screen

    fun exitFullscreen() {
        val fs = fullscreen ?: return
        fullscreen = null
        try {
            fs.second.onCustomViewHidden()
        } catch (e: Exception) {
            // page already closed it
        }
    }

    // ---------------------------------------------------------------- history

    internal fun record(tab: BrowserTab, url: String, title: String) {
        if (tab.incognito || url.isBlank() || url.startsWith("about:") || url.startsWith("file:")) return
        val app = appContext ?: return
        val dao = BrowserDatabase.get(app).dao()
        io.launch {
            try {
                if (tab.historyUrl == url && tab.historyId > 0) {
                    dao.touchHistory(tab.historyId, title, System.currentTimeMillis())
                } else {
                    tab.historyUrl = url
                    tab.historyId = dao.addHistory(HistoryEntity(url = url, title = title))
                }
            } catch (e: Exception) {
                // history is optional
            }
        }
    }

    // ---------------------------------------------------------------- links to other apps

    internal fun handleScheme(tab: BrowserTab, view: WebView?, uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "data" || scheme == "blob" ||
            scheme == "javascript" || scheme == "file"
        ) return false
        val intent = if (scheme == "intent") {
            try {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
            } catch (e: Exception) {
                null
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
        val fallback = intent?.getStringExtra("browser_fallback_url")
        when (BrowserSettings.externalApps) {
            "block" -> {
                if (fallback != null) view?.loadUrl(fallback)
                tab.blocked++
            }
            "allow" -> {
                if (intent != null) {
                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    intent.component = null
                    intent.selector = null
                }
                openExternal(intent, fallback, view)
            }
            else -> {
                if (intent != null) {
                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                    intent.component = null
                    intent.selector = null
                    externalAsk = ExternalAsk(intent, intent.`package` ?: uri.scheme ?: "অ্যাপ")
                } else if (fallback != null) {
                    view?.loadUrl(fallback)
                }
            }
        }
        return true
    }

    private fun openExternal(intent: Intent?, fallback: String?, view: WebView?) {
        val ctx = activity ?: appContext ?: return
        try {
            if (intent == null) throw IllegalStateException()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (e: Exception) {
            if (fallback != null) view?.loadUrl(fallback)
        }
    }

    fun answerExternal(allow: Boolean) {
        val ask = externalAsk ?: return
        externalAsk = null
        if (allow) openExternal(ask.intent, ask.intent.getStringExtra("browser_fallback_url"), current?.webView)
        else ask.intent.getStringExtra("browser_fallback_url")?.let { current?.webView?.loadUrl(it) }
    }

    // ---------------------------------------------------------------- pop-ups

    internal fun onPopup(tab: BrowserTab, url: String, userGesture: Boolean) {
        val uri = Uri.parse(url)
        if (BrowserSettings.blockAds && AdBlocker.isAdPage(uri)) {
            tab.blocked++
            return
        }
        val mode = BrowserSettings.popupHandling
        val ask = when {
            mode == "allow" -> false
            mode == "block" && !userGesture -> {
                tab.blocked++
                return
            }
            mode == "block" -> BrowserSettings.askNewTabs
            !userGesture -> true
            else -> BrowserSettings.askNewTabs
        }
        if (ask) {
            popupAsk = PopupAsk(url, tab.id, tab.incognito, userGesture)
        } else {
            val t = newTab(url, tab.incognito, background = BrowserSettings.newTabsBackground)
            t.openedForDownload = true
        }
    }

    fun answerPopup(allow: Boolean) {
        val p = popupAsk ?: return
        popupAsk = null
        if (allow) {
            val t = newTab(p.url, p.incognito, background = BrowserSettings.newTabsBackground)
            t.openedForDownload = true
        }
    }

    // ---------------------------------------------------------------- website permissions

    internal fun onPermission(tab: BrowserTab, request: PermissionRequest) {
        val wanted = request.resources.toList()
        val grant = mutableListOf<String>()
        val labels = mutableListOf<String>()
        val needs = mutableListOf<String>()
        var ask = false
        for (r in wanted) {
            val mode = when (r) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    labels += "ক্যামেরা"
                    needs += android.Manifest.permission.CAMERA
                    if (tab.incognito) BrowserSettings.permCameraIncognito else BrowserSettings.permCamera
                }
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    labels += "মাইক্রোফোন"
                    needs += android.Manifest.permission.RECORD_AUDIO
                    if (tab.incognito) BrowserSettings.permMicIncognito else BrowserSettings.permMic
                }
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> {
                    labels += "সুরক্ষিত (DRM) ভিডিও"
                    BrowserSettings.drm
                }
                else -> "block"
            }
            when (mode) {
                "allow" -> grant += r
                "ask" -> {
                    grant += r
                    ask = true
                }
                else -> Unit
            }
        }
        if (grant.isEmpty()) {
            request.deny()
            return
        }
        permissionAsk = PermissionAsk(
            origin = request.origin?.host ?: tab.url,
            labels = labels,
            androidPermissions = needs,
            askUser = ask,
            onResult = { ok -> if (ok) request.grant(grant.toTypedArray()) else request.deny() },
        )
    }

    internal fun onGeolocation(tab: BrowserTab, origin: String, callback: GeolocationPermissions.Callback) {
        val mode = if (tab.incognito) BrowserSettings.permLocationIncognito else BrowserSettings.permLocation
        if (mode == "block") {
            callback.invoke(origin, false, false)
            return
        }
        permissionAsk = PermissionAsk(
            origin = Uri.parse(origin).host ?: origin,
            labels = listOf("লোকেশন"),
            androidPermissions = listOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION),
            askUser = mode == "ask",
            onResult = { ok -> callback.invoke(origin, ok, false) },
        )
    }

    // ---------------------------------------------------------------- clients

    internal fun syncNav(tab: BrowserTab, view: WebView?) {
        view ?: return
        tab.canBack = view.canGoBack()
        tab.canForward = view.canGoForward()
    }

    internal fun onSsl(handler: SslErrorHandler) {
        handler.cancel()
    }

    internal fun mainPost(block: () -> Unit) {
        main.post(block)
    }
}

/** Page events for one tab. */
private class TabClient(private val tab: BrowserTab) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (request == null) return false
        val uri = request.url ?: return false
        if (Browser.handleScheme(tab, view, uri)) return true
        if (request.isForMainFrame) {
            val adOn = Browser.siteFor(tab.url)?.adblock?.takeIf { it >= 0 }?.let { it == 1 } ?: BrowserSettings.blockAds
            if (adOn && !request.hasGesture() && AdBlocker.isAdPage(uri) && Uri.parse(tab.url).host != uri.host) {
                tab.blocked++
                return true
            }
            if (Browser.siteFor(uri.toString()) != null) Browser.configure(tab, uri.toString())
        }
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (url != null) {
            tab.url = url
            tab.pageHost = Uri.parse(url).host
        }
        tab.favicon = favicon
        tab.blocked = 0
        Browser.syncNav(tab, view)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        if (view != null) Browser.injectDisplayScripts(tab, view)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (url != null) tab.url = url
        view?.title?.takeIf { it.isNotBlank() }?.let { tab.title = it }
        Browser.syncNav(tab, view)
        tab.container?.isRefreshing = false
        if (view != null) {
            Browser.injectDisplayScripts(tab, view)
            if (BrowserSettings.forceZoom) view.evaluateJavascript(PageScripts.FORCE_ZOOM, null)
        }
        if (url != null) Browser.record(tab, url, tab.title)
        CookieManager.getInstance().flush()
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        if (url != null) {
            if (!isReload && url != tab.sniffPage) {
                tab.sniffPage = url
                tab.sniffed.clear()
                tab.resources.clear()
            }
            tab.url = url
            tab.pageHost = Uri.parse(url).host
        }
        Browser.syncNav(tab, view)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        return request?.let { Browser.onRequest(tab, it) }
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        if (handler != null) Browser.onSsl(handler)
    }
}

/** Browser-window events for one tab. */
private class TabChrome(private val tab: BrowserTab) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        tab.progress = newProgress
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        if (!title.isNullOrBlank()) tab.title = title
    }

    override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
        if (icon != null) tab.favicon = icon
    }

    override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (view == null || callback == null) return
        Browser.exitFullscreen()
        Browser.fullscreen = view to callback
    }

    override fun onHideCustomView() {
        Browser.fullscreen = null
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?,
    ): Boolean {
        if (filePathCallback == null || fileChooserParams == null) return false
        Browser.chooser?.first?.onReceiveValue(null)
        Browser.chooser = filePathCallback to fileChooserParams
        return true
    }

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
        if (!BrowserSettings.allowNewWindows || view == null || resultMsg == null) return false
        // A throw-away WebView catches the address the page wants to open.
        val probe = WebView(view.context)
        probe.webViewClient = object : WebViewClient() {
            private var done = false
            private fun take(url: String?) {
                if (done || url.isNullOrBlank() || url == "about:blank") return
                done = true
                Browser.mainPost {
                    Browser.onPopup(tab, url, isUserGesture)
                    probe.stopLoading()
                    probe.destroy()
                }
            }

            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                take(r?.url?.toString())
                return true
            }

            override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                take(url)
            }
        }
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        transport.webView = probe
        resultMsg.sendToTarget()
        return true
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        Browser.mainPost { Browser.onPermission(tab, request) }
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
        if (origin == null || callback == null) return
        Browser.onGeolocation(tab, origin, callback)
    }
}
