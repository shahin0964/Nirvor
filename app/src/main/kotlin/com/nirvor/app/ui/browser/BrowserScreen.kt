package com.nirvor.app.ui.browser

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.nirvor.app.browser.BookmarkEntity
import com.nirvor.app.browser.Browser
import com.nirvor.app.browser.BrowserActions
import com.nirvor.app.browser.BrowserDatabase
import com.nirvor.app.browser.BrowserSettings
import com.nirvor.app.browser.BrowserTab
import com.nirvor.app.browser.PageScripts
import com.nirvor.app.browser.Search
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.download.Dl
import com.nirvor.app.ui.r
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pages opened from the browser's menu. */
sealed interface BSub {
    data object Settings : BSub
    data class Section(val key: String) : BSub
    data object History : BSub
    data object Bookmarks : BSub
    data object SavedPages : BSub
    data object Resources : BSub
    data class Source(val html: String) : BSub
    data class Reader(val json: String) : BSub
    data object Adblock : BSub
    data object Sites : BSub
    data object About : BSub
    data object Faq : BSub
}

internal enum class BSheetKind { Menu, Tabs, Media, Grabber, Clear, Cookies }

/** Everything the menu can do. */
internal enum class MenuAction {
    NewTab, Incognito, ReturnApp, RememberExit, Exit, BlockAds, Capture, Resources, OpenInApp, ScanQr,
    ImportCookies, LoginExternal, Clone, Grabber, History, ClipboardLinks, ClearData, ViewSource,
    ViewSourceWith, Print, SavePage, SavedPages, Find, CopyLink, CopyAllLinks, AddHome, Bookmarks, Share,
    ShareBrowser, Dark, Desktop, JavaScript, AddBookmark, BookmarkAll, Reader, Adblock, Sites, Settings, About,
    Back, Forward, Reload, ToggleBookmark,
}

/** An image / video / audio / link found by the Grabber. */
data class GrabItem(val type: String, val url: String, val name: String)

/**
 * Nirvor's browser: tabs (normal and incognito), ad blocking, media capture, and the full menu
 * (history, bookmarks, saved pages, reader mode, page source, grabber, QR, cookies, settings…).
 */
@Composable
fun BrowserScreen(
    initialUrl: String?,
    onInitialUsed: () -> Unit,
    onClose: () -> Unit,
    onHub: () -> Unit,
    onAnalyze: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        Browser.start(context)
        onDispose { Browser.stop() }
    }
    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            Browser.newTab(initialUrl, Browser.current?.incognito ?: false)
            onInitialUsed()
        }
    }

    val tab = Browser.current
    val subs = remember { mutableStateListOf<BSub>() }
    var sheet by remember { mutableStateOf<BSheetKind?>(null) }
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var finding by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var grabbed by remember { mutableStateOf<List<GrabItem>>(emptyList()) }
    var bookmarkEdit by remember { mutableStateOf<BookmarkEntity?>(null) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun push(s: BSub) {
        sheet = null
        subs.add(s)
    }
    fun open(input: String) {
        val t = Browser.current ?: Browser.newTab(null)
        editing = false
        Browser.load(t, input)
    }
    fun exitBrowser(rememberTabs: Boolean) {
        Browser.exit(context, rememberTabs)
        onClose()
    }

    // Only the visible tab keeps running (when "pause timers" is on).
    LaunchedEffect(Browser.currentId) {
        Browser.tabs.forEach { t ->
            if (t.id == Browser.currentId) t.webView?.onResume() else if (BrowserSettings.pauseTimers) t.webView?.onPause()
        }
        editing = false
        finding = false
    }

    val bookmarked by remember(tab?.url) {
        val u = tab?.url
        if (u.isNullOrBlank()) flowOf(0) else BrowserDatabase.get(context).dao().isBookmarked(u)
    }.collectAsStateWithLifecycle(0)

    // ---------------------------------------------------------------- Android helpers

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents
        if (!text.isNullOrBlank()) {
            Browser.newTab(Search.toUrl(text, tab?.incognito == true), tab?.incognito ?: false)
        }
    }

    val chooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val pending = Browser.chooser
        Browser.chooser = null
        pending?.first?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
    }
    val chooser = Browser.chooser
    LaunchedEffect(chooser) {
        if (chooser != null) {
            try {
                chooserLauncher.launch(chooser.second.createIntent())
            } catch (e: Exception) {
                chooser.first.onReceiveValue(null)
                Browser.chooser = null
            }
        }
    }

    // Website permissions: ask (when set to "ask"), then get Android's permission too.
    var pendingPerm by remember { mutableStateOf<com.nirvor.app.browser.PermissionAsk?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val p = pendingPerm
        pendingPerm = null
        p?.onResult(res.values.any { it })
    }
    fun proceedPermission(p: com.nirvor.app.browser.PermissionAsk) {
        val missing = p.androidPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty() || p.androidPermissions.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            p.onResult(true)
        } else {
            pendingPerm = p
            permLauncher.launch(missing.toTypedArray())
        }
    }
    val permAsk = Browser.permissionAsk
    LaunchedEffect(permAsk) {
        if (permAsk != null && !permAsk.askUser) {
            Browser.permissionAsk = null
            proceedPermission(permAsk)
        }
    }

    // Full screen, hidden status bar, landscape video, screenshots blocked in incognito.
    val view = LocalView.current
    val full = Browser.fullscreen != null
    val hideAll = BrowserSettings.fullScreen
    val hideStatus = BrowserSettings.hideStatusBar
    DisposableEffect(full, hideAll, hideStatus) {
        val act = view.context.findActivity()
        val ctl = act?.window?.let { WindowCompat.getInsetsController(it, view) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        when {
            full || hideAll -> ctl?.hide(WindowInsetsCompat.Type.systemBars())
            hideStatus -> ctl?.hide(WindowInsetsCompat.Type.statusBars())
        }
        val landscape = full && BrowserSettings.landscapeFullscreen
        if (landscape) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            if (landscape) act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    val secure = tab?.incognito == true && BrowserSettings.incognitoSecure
    DisposableEffect(secure) {
        val w = view.context.findActivity()?.window
        if (secure) w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    BackHandler {
        val t = Browser.current
        when {
            Browser.fullscreen != null -> Browser.exitFullscreen()
            finding -> {
                finding = false
                t?.webView?.clearMatches()
            }
            editing -> editing = false
            t != null && !t.onStart && t.webView?.canGoBack() == true -> t.webView?.goBack()
            t != null && BrowserSettings.backAction == "close_tab" && Browser.tabs.size > 1 -> Browser.close(t)
            else -> onClose()
        }
    }

    // ---------------------------------------------------------------- actions

    fun downloadButton() {
        val t = Browser.current ?: return
        when {
            t.onStart || t.url.isEmpty() -> toast("আগে কোনো সাইট খুলে ভিডিওটা চালু করুন")
            t.sniffed.isEmpty() -> onAnalyze(t.url)
            else -> sheet = BSheetKind.Media
        }
    }

    fun act(a: MenuAction) {
        val t = Browser.current
        val wv = t?.webView
        val url = t?.webView?.url ?: t?.url.orEmpty()
        val hasPage = t != null && !t.onStart && url.isNotBlank()
        if (a != MenuAction.Find && a != MenuAction.ToggleBookmark) sheet = null
        when (a) {
            MenuAction.Back -> if (t != null) {
                if (t.onStart && url.isNotBlank()) t.onStart = false else wv?.goBack()
            }
            MenuAction.Forward -> wv?.goForward()
            MenuAction.Reload -> wv?.reload()
            MenuAction.ToggleBookmark -> if (hasPage) {
                val dao = BrowserDatabase.get(context).dao()
                scope.launch {
                    if (bookmarked > 0) {
                        dao.deleteBookmarkUrl(url)
                        toast("বুকমার্ক সরানো হয়েছে")
                    } else {
                        dao.addBookmark(BookmarkEntity(url = url, title = t?.title.orEmpty()))
                        toast("বুকমার্ক করা হয়েছে")
                    }
                }
            }
            MenuAction.NewTab -> Browser.newTab(null, incognito = false)
            MenuAction.Incognito -> {
                if (t?.incognito == true) {
                    Browser.tabs.lastOrNull { !it.incognito }?.let { Browser.select(it) } ?: Browser.newTab(null, incognito = false)
                } else {
                    Browser.tabs.lastOrNull { it.incognito }?.let { Browser.select(it) } ?: Browser.newTab(null, incognito = true)
                }
            }
            MenuAction.ReturnApp -> onHub()
            MenuAction.RememberExit -> exitBrowser(true)
            MenuAction.Exit -> exitBrowser(false)
            MenuAction.BlockAds -> {
                BrowserSettings.blockAds = !BrowserSettings.blockAds
                wv?.reload()
            }
            MenuAction.Capture -> BrowserSettings.captureMedia = !BrowserSettings.captureMedia
            MenuAction.Resources -> push(BSub.Resources)
            MenuAction.OpenInApp -> if (hasPage) BrowserActions.openInApp(context, url)
            MenuAction.ScanQr -> qrLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("QR কোডটা ফ্রেমের ভেতরে আনুন")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false),
            )
            MenuAction.ImportCookies -> sheet = BSheetKind.Cookies
            MenuAction.LoginExternal -> if (hasPage) {
                BrowserActions.openInBrowser(context, url)
                toast("অন্য ব্রাউজারে লগইন করে পোস্টের লিংকটা Nirvor-এ শেয়ার করুন")
            }
            MenuAction.Clone -> t?.let { Browser.clone(it) }
            MenuAction.Grabber -> if (wv != null && hasPage) {
                wv.evaluateJavascript(PageScripts.GRAB) { raw ->
                    val text = BrowserActions.decodeJsString(raw)
                    grabbed = try {
                        val arr = JSONArray(text ?: "[]")
                        (0 until arr.length()).map { i ->
                            val o = arr.getJSONObject(i)
                            GrabItem(o.optString("t"), o.optString("u"), o.optString("n"))
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }
                    if (grabbed.isEmpty()) toast("এই পেজে কিছু পাওয়া যায়নি") else sheet = BSheetKind.Grabber
                }
            }
            MenuAction.History -> push(BSub.History)
            MenuAction.ClipboardLinks -> {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                val links = Regex("""https?://[^\s<>"']+""").findAll(text).map { it.value.trimEnd('.', ',', ')') }.distinct().toList()
                if (links.isEmpty()) {
                    toast("ক্লিপবোর্ডে কোনো লিংক নেই")
                } else {
                    links.forEachIndexed { i, l -> Browser.newTab(l, t?.incognito ?: false, background = i < links.size - 1) }
                    toast("${links.size}টা ট্যাব খোলা হয়েছে")
                }
            }
            MenuAction.ClearData -> sheet = BSheetKind.Clear
            MenuAction.ViewSource, MenuAction.ViewSourceWith -> if (wv != null && hasPage) {
                wv.evaluateJavascript(PageScripts.SOURCE) { raw ->
                    val html = BrowserActions.decodeJsString(raw).orEmpty()
                    if (a == MenuAction.ViewSource) push(BSub.Source(html)) else BrowserActions.viewSourceWith(context, html, t?.title.orEmpty())
                }
            }
            MenuAction.Print -> if (wv != null && hasPage) BrowserActions.print(context, wv, t?.title.orEmpty())
            MenuAction.SavePage -> if (wv != null && hasPage) BrowserActions.savePage(context, wv, t?.title.orEmpty())
            MenuAction.SavedPages -> push(BSub.SavedPages)
            MenuAction.Find -> if (hasPage) {
                sheet = null
                finding = true
            }
            MenuAction.CopyLink -> if (hasPage) BrowserActions.copy(context, "link", url)
            MenuAction.CopyAllLinks -> BrowserActions.copy(
                context,
                "links",
                Browser.tabs.mapNotNull { (it.webView?.url ?: it.url).takeIf { u -> u.isNotBlank() } }.joinToString("\n"),
            )
            MenuAction.AddHome -> if (hasPage) BrowserActions.addToHomeScreen(context, url, t?.title.orEmpty(), t?.favicon)
            MenuAction.Bookmarks -> push(BSub.Bookmarks)
            MenuAction.Share -> if (hasPage) BrowserActions.shareLink(context, url, t?.title)
            MenuAction.ShareBrowser -> if (hasPage) BrowserActions.openInBrowser(context, url)
            MenuAction.Dark -> {
                BrowserSettings.darkPages = !BrowserSettings.darkPages
                Browser.refreshDisplay()
            }
            MenuAction.Desktop -> t?.let { Browser.setDesktop(it, !it.desktop) }
            MenuAction.JavaScript -> {
                BrowserSettings.javascript = !BrowserSettings.javascript
                Browser.applySettingsToAll()
                wv?.reload()
            }
            MenuAction.AddBookmark -> if (hasPage) bookmarkEdit = BookmarkEntity(url = url, title = t?.title.orEmpty())
            MenuAction.BookmarkAll -> {
                val folder = "ট্যাব " + SimpleDateFormat("d MMM, HH:mm", Locale.US).format(Date())
                val dao = BrowserDatabase.get(context).dao()
                val list = Browser.tabs.mapNotNull { tb ->
                    val u = tb.webView?.url ?: tb.url
                    if (tb.onStart || u.isBlank()) null else BookmarkEntity(url = u, title = tb.title, folder = folder)
                }
                scope.launch {
                    list.forEach { dao.addBookmark(it) }
                    toast("${list.size}টা ট্যাব বুকমার্ক হয়েছে")
                }
            }
            MenuAction.Reader -> if (wv != null && hasPage) {
                wv.evaluateJavascript(PageScripts.READER) { raw ->
                    val json = BrowserActions.decodeJsString(raw)
                    if (json.isNullOrBlank()) toast("এই পেজ রিডার মোডে দেখানো গেল না") else push(BSub.Reader(json))
                }
            }
            MenuAction.Adblock -> push(BSub.Adblock)
            MenuAction.Sites -> push(BSub.Sites)
            MenuAction.Settings -> push(BSub.Settings)
            MenuAction.About -> push(BSub.About)
        }
    }

    // ---------------------------------------------------------------- layout

    Box(Modifier.fillMaxSize().background(Dl.Bg)) {
        Column(Modifier.fillMaxSize()) {
            val bar: @Composable () -> Unit = {
                if (finding && tab != null) {
                    FindBar(tab, findQuery, { findQuery = it }) {
                        finding = false
                        findQuery = ""
                        tab.webView?.clearMatches()
                    }
                } else {
                    TopBar(
                        tab = tab,
                        editing = editing,
                        field = field,
                        onField = { field = it },
                        onStartEditing = {
                            val u = tab?.takeIf { !it.onStart }?.url.orEmpty()
                            field = TextFieldValue(u, if (BrowserSettings.selectUrlOnClick) TextRange(0, u.length) else TextRange(u.length))
                            editing = true
                        },
                        onStopEditing = { editing = false },
                        onSubmit = { open(it) },
                        onTabs = { sheet = BSheetKind.Tabs },
                        onMenu = { sheet = BSheetKind.Menu },
                    )
                }
            }
            if (!BrowserSettings.addressBarBottom) {
                Column(Modifier.background(Brush.verticalGradient(listOf(com.nirvor.app.ui.theme.C.SkyTop, Color.White))).statusBarsPadding()) {
                    bar()
                }
            } else {
                Spacer(Modifier.statusBarsPadding())
            }
            LoadLine(tab)

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    tab == null -> Unit
                    tab.onStart -> StartPage(tab.incognito) { open(it) }
                    else -> key(tab.id) {
                        AndroidView(
                            factory = { _ ->
                                val v = Browser.viewFor(tab)
                                (v.parent as? ViewGroup)?.removeView(v)
                                v
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (editing) {
                    Suggestions(field.text, tab?.incognito == true, onPick = { open(it) }, onFill = {
                        field = TextFieldValue(it, TextRange(it.length))
                    })
                }
                if (BrowserSettings.floatingDownload && tab != null && !tab.onStart) {
                    Box(Modifier.align(Alignment.BottomEnd).padding(18.r)) {
                        DownloadOrb(tab.sniffed.size) { downloadButton() }
                    }
                }
                UndoBar(Modifier.align(Alignment.BottomCenter))
            }

            if (BrowserSettings.addressBarBottom) {
                Column(Modifier.background(Color.White)) { bar() }
            }
            Dock(
                tab = tab,
                bookmarked = bookmarked > 0,
                showOrb = !BrowserSettings.floatingDownload,
                onBack = { act(MenuAction.Back) },
                onForward = { act(MenuAction.Forward) },
                onDownload = { downloadButton() },
                onHome = {
                    val t = Browser.current
                    val home = BrowserSettings.homepage
                    if (t != null) {
                        if (home.isNotBlank()) Browser.load(t, home) else t.onStart = true
                    }
                },
                onBookmark = { act(MenuAction.ToggleBookmark) },
            )
        }

        Browser.fullscreen?.let { (v, _) ->
            key(v) {
                AndroidView(
                    factory = { _ ->
                        (v.parent as? ViewGroup)?.removeView(v)
                        v
                    },
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                )
            }
        }

        // Pages from the menu cover the browser.
        if (subs.isNotEmpty()) {
            BackHandler { subs.removeAt(subs.size - 1) }
            val pop: () -> Unit = { if (subs.isNotEmpty()) subs.removeAt(subs.size - 1) }
            SubHost(
                sub = subs.last(),
                onBack = pop,
                onPush = { subs.add(it) },
                onOpen = { link ->
                    subs.clear()
                    open(link)
                },
                onOpenNewTab = { link ->
                    subs.clear()
                    Browser.newTab(link, Browser.current?.incognito ?: false)
                },
                onDownload = { link, name ->
                    val t = Browser.current
                    if (t != null) Browser.offer = Browser.offerFor(t, link, name)
                },
            )
        }
    }

    // ---------------------------------------------------------------- sheets & dialogs

    when (sheet) {
        BSheetKind.Menu -> if (tab != null) MenuSheet(tab, bookmarked > 0, onDismiss = { sheet = null }) { act(it) }
        BSheetKind.Tabs -> TabsSheet(onDismiss = { sheet = null })
        BSheetKind.Media -> if (tab != null) MediaSheet(
            tab = tab,
            onDismiss = { sheet = null },
            onAnalyze = {
                sheet = null
                onAnalyze(it)
            },
        )
        BSheetKind.Grabber -> if (tab != null) GrabberSheet(tab, grabbed, onDismiss = { sheet = null })
        BSheetKind.Clear -> ClearDataSheet(onDismiss = { sheet = null })
        BSheetKind.Cookies -> CookiesSheet(tab?.url, onDismiss = { sheet = null })
        null -> Unit
    }

    Browser.contextTarget?.let { target ->
        ContextSheet(
            target = target,
            onDismiss = { Browser.contextTarget = null },
            onAnalyze = {
                Browser.contextTarget = null
                onAnalyze(it)
            },
        )
    }

    Browser.offer?.let { o ->
        DownloadEditor(o, onDismiss = { Browser.offer = null }) { edited ->
            Browser.offer = null
            Browser.startDownload(context, edited)
        }
    }

    bookmarkEdit?.let { b ->
        BookmarkDialog(b, onDismiss = { bookmarkEdit = null }) { saved ->
            bookmarkEdit = null
            val dao = BrowserDatabase.get(context).dao()
            scope.launch {
                if (saved.id == 0L) dao.addBookmark(saved) else dao.updateBookmark(saved.id, saved.title, saved.url, saved.folder)
                toast("বুকমার্ক সেভ হয়েছে")
            }
        }
    }

    Browser.popupAsk?.let { p ->
        AskDialog(
            title = if (p.userGesture) "নতুন ট্যাবে খুলবেন?" else "এই সাইট একটা পপ-আপ খুলতে চাইছে",
            text = if (BrowserSettings.showPopupUrl) p.url.take(180) else "অনুমতি দেবেন?",
            yes = "খুলুন",
            no = "ব্লক",
            glyph = com.nirvor.app.ui.icons.Glyph.External,
            onYes = { Browser.answerPopup(true) },
            onNo = { Browser.answerPopup(false) },
        )
    }

    if (permAsk != null && permAsk.askUser) {
        AskDialog(
            title = "${permAsk.origin} চাইছে",
            text = permAsk.labels.joinToString(", ") + " ব্যবহারের অনুমতি দেবেন?",
            yes = "অনুমতি দিন",
            no = "না",
            glyph = com.nirvor.app.ui.icons.Glyph.Shield,
            onYes = {
                Browser.permissionAsk = null
                proceedPermission(permAsk)
            },
            onNo = {
                Browser.permissionAsk = null
                permAsk.onResult(false)
            },
        )
    }

    Browser.externalAsk?.let { e ->
        AskDialog(
            title = "অন্য অ্যাপ খুলবেন?",
            text = "এই পেজ ${e.label} খুলতে চাইছে।",
            yes = "খুলুন",
            no = "এখানেই থাকুন",
            glyph = com.nirvor.app.ui.icons.Glyph.Grid,
            onYes = { Browser.answerExternal(true) },
            onNo = { Browser.answerExternal(false) },
        )
    }
}

/** "Tab closed · Undo" for a few seconds after closing a tab. */
@Composable
private fun UndoBar(modifier: Modifier) {
    val closed = Browser.closedTab
    LaunchedEffect(closed) {
        if (closed != null) {
            delay(4000)
            Browser.closedTab = null
        }
    }
    if (closed == null || !BrowserSettings.tabClosedUndo) return
    Row(
        modifier
            .padding(16.r)
            .clip(RoundedCornerShape(16.r))
            .background(Color(0xF0222838))
            .padding(horizontal = 20.r, vertical = 14.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RText("ট্যাব বন্ধ হয়েছে", 18, FontWeight.Normal, Color.White)
        Spacer(Modifier.width(22.r))
        RText(
            "ফিরিয়ে আনুন",
            18,
            FontWeight.Bold,
            Color(0xFF5BE3A0),
            Modifier.clickable { Browser.undoClose() }.padding(4.r),
        )
    }
}

internal fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

