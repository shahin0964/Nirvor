package com.nirvor.app.ui.download

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.MutableContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.downloader.Analyzer
import com.nirvor.app.downloader.Downloads
import com.nirvor.app.downloader.Kind
import com.nirvor.app.downloader.Status
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import kotlinx.coroutines.launch
import java.util.Locale

/** A media file the page loaded while playing (video, audio or a stream playlist). */
data class Sniffed(val url: String, val kind: Kind, val ext: String, val stream: Boolean, val host: String)

/** A file the page itself offered (a download link or button). */
data class FileOffer(val url: String, val name: String, val mime: String, val size: Long, val referer: String?)

/**
 * The browser lives here instead of inside the screen, so the page stays where it was (and a
 * video keeps its place) while the user looks at the Download Hub and comes back.
 */
internal object BrowserSession {

    @SuppressLint("StaticFieldLeak")
    private var webView: WebView? = null

    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)

    /** Showing Nirvor's own start page instead of a site. */
    var onStart by mutableStateOf(true)

    /** Media the current page has loaded, newest last. */
    val sniffed = mutableStateListOf<Sniffed>()

    var offer by mutableStateOf<FileOffer?>(null)
    var fullscreen by mutableStateOf<Pair<View, WebChromeClient.CustomViewCallback>?>(null)
    var chooser by mutableStateOf<Pair<ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams>?>(null)

    private var sniffPage = ""
    private val main = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(context: Context): WebView {
        webView?.let { wv ->
            (wv.context as? MutableContextWrapper)?.baseContext = context
            (wv.parent as? ViewGroup)?.removeView(wv)
            return wv
        }
        val wv = WebView(MutableContextWrapper(context))
        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Look like the phone's normal Chrome, so sites serve their full mobile pages.
            userAgentString = userAgentString.replace("; wv", "")
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)
        wv.webViewClient = Client
        wv.webChromeClient = Chrome
        wv.setDownloadListener { link, _, disposition, mime, length -> onPageDownload(wv, link, disposition, mime, length) }
        webView = wv
        return wv
    }

    /** The screen is gone: keep the page, but let go of the activity. */
    fun release(appContext: Context) {
        exitFullscreen()
        val wv = webView ?: return
        (wv.parent as? ViewGroup)?.removeView(wv)
        (wv.context as? MutableContextWrapper)?.baseContext = appContext
        wv.onPause()
        CookieManager.getInstance().flush()
    }

    fun exitFullscreen() {
        val fs = fullscreen ?: return
        fullscreen = null
        try {
            fs.second.onCustomViewHidden()
        } catch (e: Exception) {
            // page already closed it
        }
    }

    private fun onPageDownload(wv: WebView, link: String?, disposition: String?, mime: String?, length: Long) {
        if (link.isNullOrBlank()) return
        if (!link.startsWith("http")) {
            Toast.makeText(wv.context, "এই ফাইলটা সরাসরি নামানো যাচ্ছে না। ভিডিও হলে নিচের ডাউনলোড বাটন চাপুন।", Toast.LENGTH_LONG).show()
            return
        }
        val cleanMime = mime?.substringBefore(';')?.trim()?.takeIf { it.isNotEmpty() && it != "application/octet-stream" }
        val name = URLUtil.guessFileName(link, disposition, cleanMime)
        offer = FileOffer(
            url = link,
            name = name,
            mime = cleanMime ?: Analyzer.mimeForName(name, "application/octet-stream"),
            size = length,
            referer = url.ifEmpty { null },
        )
    }

    private fun syncNav(view: WebView?) {
        view ?: return
        canBack = view.canGoBack()
        canForward = view.canGoForward()
    }

    // ---------------------------------------------------------------- media sniffing

    private val mediaExt = mapOf(
        "mp4" to Kind.Video, "webm" to Kind.Video, "mkv" to Kind.Video, "mov" to Kind.Video,
        "m4v" to Kind.Video, "3gp" to Kind.Video, "flv" to Kind.Video,
        "m3u8" to Kind.Video, "mpd" to Kind.Video,
        "mp3" to Kind.Audio, "m4a" to Kind.Audio, "aac" to Kind.Audio, "ogg" to Kind.Audio,
        "opus" to Kind.Audio, "wav" to Kind.Audio, "flac" to Kind.Audio,
    )

    /** Facebook-style partial requests ask for a byte range; drop it to get the whole file. */
    private val rangeParam = Regex("""[?&](?:bytestart|byteend|range)=[^&#]*""")

    private fun stripRange(u: String): String {
        if (!rangeParam.containsMatchIn(u)) return u
        return u.replace(rangeParam) { m -> if (m.value.startsWith("?")) "?" else "" }
            .replace("?&", "?")
            .trimEnd('?', '&')
    }

    /** Runs on WebView's network thread for every request the page makes. */
    private fun sniff(uri: Uri) {
        try {
            val scheme = uri.scheme ?: return
            if (scheme != "http" && scheme != "https") return
            val host = uri.host?.lowercase(Locale.US) ?: return
            // YouTube streams arrive in small pieces; the page link gives every quality instead.
            if (host.endsWith("googlevideo.com")) return
            // Adverts.
            if (host.contains("doubleclick") || host.contains("googlesyndication") || host.contains("imasdk")) return
            val last = uri.lastPathSegment?.lowercase(Locale.US) ?: return
            val ext = last.substringAfterLast('.', "")
            val kind = mediaExt[ext] ?: return
            val clean = stripRange(uri.toString())
            val item = Sniffed(clean, kind, ext, ext == "m3u8" || ext == "mpd", host)
            main.post {
                if (sniffed.none { it.url == clean } && sniffed.size < 40) sniffed.add(item)
            }
        } catch (e: Exception) {
            // odd URL: ignore
        }
    }

    // ---------------------------------------------------------------- clients

    private fun handleScheme(view: WebView?, uri: Uri): Boolean {
        return when (uri.scheme?.lowercase(Locale.US)) {
            "http", "https", "about", "data", "blob", "javascript" -> false
            "intent" -> {
                // "Open in app" links: stay here and use the web address they carry.
                val fallback = try {
                    Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url")
                } catch (e: Exception) {
                    null
                }
                if (fallback != null) view?.loadUrl(fallback)
                true
            }
            "tel", "mailto", "sms", "smsto", "whatsapp", "tg", "market", "geo" -> {
                try {
                    view?.context?.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    // no app for it
                }
                true
            }
            // youtube://, fb://, instagram://, …: don't jump to the other app.
            else -> true
        }
    }

    private object Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            return BrowserSession.handleScheme(view, uri)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (url != null) BrowserSession.url = url
            BrowserSession.syncNav(view)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (url != null) BrowserSession.url = url
            view?.title?.takeIf { it.isNotBlank() }?.let { BrowserSession.title = it }
            BrowserSession.syncNav(view)
            CookieManager.getInstance().flush()
        }

        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
            if (url != null) {
                if (!isReload && url != BrowserSession.sniffPage) {
                    BrowserSession.sniffPage = url
                    BrowserSession.sniffed.clear()
                }
                BrowserSession.url = url
            }
            BrowserSession.syncNav(view)
        }

        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            request?.url?.let { BrowserSession.sniff(it) }
            return null
        }
    }

    private object Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            BrowserSession.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView?, title: String?) {
            if (!title.isNullOrBlank()) BrowserSession.title = title
        }

        override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
            if (view == null || callback == null) return
            BrowserSession.exitFullscreen()
            BrowserSession.fullscreen = view to callback
        }

        override fun onHideCustomView() {
            BrowserSession.fullscreen = null
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: WebChromeClient.FileChooserParams?,
        ): Boolean {
            if (filePathCallback == null || fileChooserParams == null) return false
            BrowserSession.chooser?.first?.onReceiveValue(null)
            BrowserSession.chooser = filePathCallback to fileChooserParams
            return true
        }
    }
}

/** What the user typed in the address bar → a web address (or a Google search). */
private val hostLike = Regex("""^[\p{L}\d-]+(\.[\p{L}\d-]+)+(:\d+)?([/?#].*)?$""")

internal fun toWebUrl(input: String): String {
    val t = input.trim()
    if (t.startsWith("http://", true) || t.startsWith("https://", true)) return t
    if (!t.contains(' ') && hostLike.matches(t)) return "https://$t"
    return "https://www.google.com/search?q=" + Uri.encode(t)
}

private fun displayHost(url: String): String =
    (Analyzer.hostOf(url) ?: url).removePrefix("www.").removePrefix("m.")

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

// ==================================================================== screen

/**
 * Nirvor's own browser. Sites open inside the app; whatever the page plays is noticed, and the
 * glowing button at the bottom offers every format of the page (or the files it loaded).
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
    val s = BrowserSession
    val webView = remember { s.obtain(context) }
    DisposableEffect(webView) {
        webView.onResume()
        onDispose { s.release(context.applicationContext) }
    }

    val downloads by remember { Downloads.dao(context).all() }.collectAsStateWithLifecycle(emptyList())
    val active = downloads.count { it.status == Status.RUNNING || it.status == Status.QUEUED }
    var showMedia by remember { mutableStateOf(false) }

    fun go(input: String) {
        s.onStart = false
        webView.loadUrl(toWebUrl(input))
    }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            go(initialUrl)
            onInitialUsed()
        }
    }

    BackHandler {
        when {
            s.fullscreen != null -> s.exitFullscreen()
            !s.onStart && webView.canGoBack() -> webView.goBack()
            else -> onClose()
        }
    }

    // File uploads from pages (profile photo, posting, …).
    val chooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val pending = s.chooser
        s.chooser = null
        pending?.first?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data))
    }
    val chooser = s.chooser
    LaunchedEffect(chooser) {
        if (chooser != null) {
            try {
                chooserLauncher.launch(chooser.second.createIntent())
            } catch (e: Exception) {
                chooser.first.onReceiveValue(null)
                s.chooser = null
            }
        }
    }

    // Full-screen video: landscape, without the system bars, while it plays.
    val view = LocalView.current
    val isFull = s.fullscreen != null
    DisposableEffect(isFull) {
        val activity = view.context.activity()
        val ctl = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        if (isFull) {
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            if (isFull) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    Box(Modifier.fillMaxSize().background(Dl.Bg)) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(C.SkyTop, Color.White)))
                    .statusBarsPadding()
            ) {
                AddressBar(
                    url = if (s.onStart) "" else s.url,
                    loading = !s.onStart && s.progress < 100,
                    onStart = s.onStart,
                    activeDownloads = active,
                    onClose = onClose,
                    onSubmit = { go(it) },
                    onReload = { webView.reload() },
                    onStop = { webView.stopLoading() },
                    onHub = onHub,
                )
                LoadLine(s.progress, visible = !s.onStart && s.progress < 100)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (s.onStart) {
                    StartPage(onGo = { go(it) })
                } else {
                    AndroidView(
                        factory = { _ ->
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Dock(
                canBack = if (s.onStart) s.url.isNotEmpty() else s.canBack,
                canForward = !s.onStart && s.canForward,
                mediaCount = if (s.onStart) 0 else s.sniffed.size,
                onBack = {
                    if (s.onStart) {
                        if (s.url.isNotEmpty()) s.onStart = false
                    } else if (webView.canGoBack()) {
                        webView.goBack()
                    }
                },
                onForward = { if (webView.canGoForward()) webView.goForward() },
                onDownload = {
                    when {
                        s.onStart || s.url.isEmpty() ->
                            Toast.makeText(context, "আগে কোনো সাইট খুলে ভিডিওটা চালু করুন", Toast.LENGTH_SHORT).show()
                        s.sniffed.isEmpty() -> onAnalyze(s.url)
                        else -> showMedia = true
                    }
                },
                onHome = { s.onStart = true },
                onShare = {
                    if (s.url.isNotEmpty() && !s.onStart) {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, s.url)
                        try {
                            context.startActivity(Intent.createChooser(send, null))
                        } catch (e: Exception) {
                            // nothing to share with
                        }
                    }
                },
            )
        }

        s.fullscreen?.let { (v, _) ->
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
    }

    if (showMedia) {
        MediaSheet(
            pageUrl = s.url,
            items = s.sniffed.toList(),
            onDismiss = { showMedia = false },
            onAnalyze = {
                showMedia = false
                onAnalyze(it)
            },
            onFile = { item ->
                showMedia = false
                scope.launch {
                    val base = Analyzer.safeName(s.title.ifBlank { item.host })
                    val name = "$base.${item.ext}"
                    Downloads.enqueueFile(
                        context,
                        url = item.url,
                        fileName = name,
                        mime = Analyzer.mimeForName(name, if (item.kind == Kind.Audio) "audio/mpeg" else "video/mp4"),
                        referer = s.url,
                        size = 0,
                    )
                    Toast.makeText(context, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    s.offer?.let { o ->
        OfferDialog(
            o,
            onDismiss = { s.offer = null },
            onDownload = {
                s.offer = null
                scope.launch {
                    Downloads.enqueueFile(context, o.url, o.name, o.mime, o.referer, o.size)
                    Toast.makeText(context, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

// ==================================================================== top bar

@Composable
private fun AddressBar(
    url: String,
    loading: Boolean,
    onStart: Boolean,
    activeDownloads: Int,
    onClose: () -> Unit,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onHub: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var hadFocus by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(editing) { if (editing) focus.requestFocus() }

    fun stopEditing() {
        editing = false
        hadFocus = false
        focusManager.clearFocus()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.r, end = 18.r, top = 14.r, bottom = 12.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundButton(Glyph.ChevronLeft, onClose, size = 62, background = Dl.Soft)
        Spacer(Modifier.width(12.r))

        val shape = RoundedCornerShape(50)
        Row(
            Modifier
                .weight(1f)
                .height(66.r)
                .clip(shape)
                .background(Dl.Soft)
                .border(1.2.r, if (editing) Brush.horizontalGradient(Dl.brand) else SolidColor(Dl.Line), shape)
                .clickable(enabled = !editing) {
                    field = TextFieldValue(url, TextRange(0, url.length))
                    editing = true
                }
                .padding(start = 20.r, end = 8.r),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val secure = url.startsWith("https://")
            GlyphIcon(
                when {
                    editing || url.isEmpty() -> Glyph.Search
                    secure -> Glyph.Shield
                    else -> Glyph.Globe
                },
                if (secure && !editing) Dl.GreenText else Dl.Label,
                Modifier.size(26.r),
                strokeWidth = 2.1f,
            )
            Spacer(Modifier.width(12.r))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (editing) {
                    if (field.text.isEmpty()) RText("সার্চ বা লিংক", 20, FontWeight.Normal, Dl.Hint)
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Inter, fontSize = 20.rs, color = Dl.Ink),
                        cursorBrush = SolidColor(Dl.Green),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            if (field.text.isNotBlank()) {
                                onSubmit(field.text)
                                stopEditing()
                            }
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged { state ->
                                if (state.isFocused) {
                                    hadFocus = true
                                } else if (hadFocus) {
                                    editing = false
                                    hadFocus = false
                                }
                            },
                    )
                } else if (url.isEmpty()) {
                    RText("সার্চ করুন বা লিংক লিখুন", 20, FontWeight.Normal, Dl.Hint)
                } else {
                    RText(displayHost(url), 20, FontWeight.Medium, Dl.Ink)
                }
            }
            when {
                editing -> BarIcon(Glyph.Close) { stopEditing() }
                !onStart && url.isNotEmpty() -> if (loading) BarIcon(Glyph.Close, onStop) else BarIcon(Glyph.Refresh, onReload)
            }
        }

        Spacer(Modifier.width(12.r))
        Box {
            RoundButton(Glyph.Folder, onHub, size = 62, tint = C.NavIcon, background = Dl.Soft)
            if (activeDownloads > 0) CountBadge(activeDownloads, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun BarIcon(glyph: Glyph, onClick: () -> Unit) {
    GlyphIcon(
        glyph,
        Dl.Sub,
        Modifier.size(50.r).clip(CircleShape).clickable(onClick = onClick).padding(13.r),
        strokeWidth = 2.2f,
    )
}

@Composable
private fun CountBadge(n: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 28.r)
            .widthIn(min = 28.r)
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(Dl.red))
            .padding(horizontal = 7.r),
        contentAlignment = Alignment.Center,
    ) {
        RText(if (n > 9) "9+" else n.toString(), 15, FontWeight.Bold, Color.White)
    }
}

@Composable
private fun LoadLine(progress: Int, visible: Boolean) {
    val p by animateFloatAsState(progress / 100f, label = "load")
    Box(Modifier.fillMaxWidth().height(4.r)) {
        if (visible) {
            Box(
                Modifier
                    .fillMaxWidth(p.coerceIn(0.03f, 1f))
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(Dl.brand))
            )
        }
    }
}

// ==================================================================== bottom dock

@Composable
private fun Dock(
    canBack: Boolean,
    canForward: Boolean,
    mediaCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onDownload: () -> Unit,
    onHome: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().height(1.2.r).background(C.NavLine))
        Row(
            Modifier.fillMaxWidth().height(108.r).padding(horizontal = 14.r),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            DockButton(Glyph.ArrowLeft, canBack, onBack)
            DockButton(Glyph.ArrowRight, canForward, onForward)
            DownloadOrb(mediaCount, onDownload)
            DockButton(Glyph.Home, true, onHome)
            DockButton(Glyph.Share, true, onShare)
        }
    }
}

@Composable
private fun DockButton(glyph: Glyph, enabled: Boolean, onClick: () -> Unit) {
    GlyphIcon(
        glyph,
        if (enabled) C.NavIcon else Dl.Hint.copy(alpha = 0.45f),
        Modifier
            .size(64.r)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(18.r),
        strokeWidth = 2.2f,
    )
}

/** The glowing download button; it pulses when the page has played something. */
@Composable
private fun DownloadOrb(count: Int, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "pulse",
    )
    val ring = 3.r
    Box(Modifier.size(104.r), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r0 = size.minDimension * 0.39f
            drawCircle(
                Brush.radialGradient(
                    listOf(C.FabA.copy(alpha = if (count > 0) 0.38f else 0.18f), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2,
                ),
                radius = size.minDimension / 2,
            )
            if (count > 0) {
                drawCircle(
                    C.FabA.copy(alpha = 0.8f * (1f - pulse)),
                    radius = r0 + size.minDimension * 0.11f * pulse,
                    style = Stroke(width = ring.toPx()),
                )
            }
        }
        Box(
            Modifier
                .size(78.r)
                .clip(CircleShape)
                .background(Brush.linearGradient(Dl.brand))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Download, Color.White, Modifier.size(38.r), strokeWidth = 2.5f)
        }
        if (count > 0) CountBadge(count, Modifier.align(Alignment.TopEnd).padding(top = 4.r, end = 4.r))
    }
}

// ==================================================================== start page

private class QuickSite(val name: String, val code: String, val url: String, val colors: List<Color>)

private val quickSites = listOf(
    QuickSite("YouTube", "YT", "https://m.youtube.com", listOf(Color(0xFFFF3B3B), Color(0xFFC81E1E))),
    QuickSite("Facebook", "FB", "https://m.facebook.com", listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))),
    QuickSite("Instagram", "IG", "https://www.instagram.com", listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6))),
    QuickSite("TikTok", "TK", "https://www.tiktok.com", listOf(Color(0xFF22D3EE), Color(0xFFF43F5E))),
    QuickSite("X", "X", "https://x.com", listOf(Color(0xFF64748B), Color(0xFF1E293B))),
    QuickSite("Pinterest", "PN", "https://www.pinterest.com", listOf(Color(0xFFFB7185), Color(0xFFBE123C))),
    QuickSite("Vimeo", "VM", "https://vimeo.com", listOf(Color(0xFF38BDF8), Color(0xFF0369A1))),
    QuickSite("Google", "GO", "https://www.google.com", listOf(Color(0xFF34D399), Color(0xFF2563EB))),
)

@Composable
private fun StartPage(onGo: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White, Dl.Bg)))) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.r),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(34.r))
            HoloGlobe()
            Spacer(Modifier.height(16.r))
            RText("Nirvor Browser", 38, FontWeight.Bold, Dl.Ink)
            Spacer(Modifier.height(6.r))
            RText("দেখুন, চালান, এক চাপে ডাউনলোড", 19, FontWeight.Normal, Dl.Sub)
            Spacer(Modifier.height(30.r))
            SearchBox(q, { q = it }) { if (q.isNotBlank()) onGo(q) }
            Spacer(Modifier.height(34.r))
            RText("দ্রুত খুলুন", 20, FontWeight.SemiBold, Dl.Sub, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.r))
            quickSites.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { site -> QuickTile(site, Modifier.weight(1f)) { onGo(site.url) } }
                }
                Spacer(Modifier.height(18.r))
            }
            Spacer(Modifier.height(8.r))
            DlCard {
                HintLine(Glyph.Download, Dl.brand, "ভিডিও চালু করুন, তারপর নিচের ডাউনলোড বাটন চাপুন — সব কোয়ালিটি দেখাবে")
                Spacer(Modifier.height(16.r))
                HintLine(Glyph.Shield, Dl.sky, "প্রাইভেট পোস্ট? এখানে একবার লগইন করলে সেগুলোও নামানো যাবে")
            }
            Spacer(Modifier.height(34.r))
        }
    }
}

/** A slowly turning hologram ring around a globe. */
@Composable
private fun HoloGlobe() {
    val t = rememberInfiniteTransition(label = "globe")
    val spin by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "spin",
    )
    Box(Modifier.size(200.r), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val c = center
            val rr = size.minDimension / 2
            drawCircle(
                Brush.radialGradient(listOf(C.FabA.copy(alpha = 0.16f), Color.Transparent), center = c, radius = rr),
                radius = rr,
                center = c,
            )
            rotate(spin, c) {
                drawArc(
                    Brush.sweepGradient(listOf(Color.Transparent, Dl.sky.first(), C.FabA, Color.Transparent), c),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(c.x - rr * 0.88f, c.y - rr * 0.88f),
                    size = Size(rr * 1.76f, rr * 1.76f),
                    style = Stroke(width = rr * 0.03f, cap = StrokeCap.Round),
                )
            }
            rotate(-spin * 1.6f, c) {
                val box = Size(rr * 1.42f, rr * 1.42f)
                val tl = Offset(c.x - rr * 0.71f, c.y - rr * 0.71f)
                drawArc(C.FabB.copy(alpha = 0.7f), 30f, 110f, false, tl, box, style = Stroke(rr * 0.02f, cap = StrokeCap.Round))
                drawArc(Dl.sky.last().copy(alpha = 0.7f), 210f, 110f, false, tl, box, style = Stroke(rr * 0.02f, cap = StrokeCap.Round))
            }
        }
        GradIcon(Glyph.Globe, Dl.sky, size = 104, iconScale = 0.52f)
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, onGo: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .fillMaxWidth()
            .height(80.r)
            .clip(shape)
            .background(Color.White)
            .border(1.6.r, Brush.horizontalGradient(Dl.brand), shape)
            .padding(start = 26.r, end = 9.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Search, Dl.Label, Modifier.size(28.r), strokeWidth = 2.2f)
        Spacer(Modifier.width(14.r))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) RText("সার্চ করুন বা লিংক লিখুন", 21, FontWeight.Normal, Dl.Hint)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Inter, fontSize = 21.rs, color = Dl.Ink),
                cursorBrush = SolidColor(Dl.Green),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier
                .size(62.r)
                .clip(CircleShape)
                .background(Brush.linearGradient(Dl.brand))
                .clickable(onClick = onGo),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.ArrowRight, Color.White, Modifier.size(28.r), strokeWidth = 2.4f)
        }
    }
}

@Composable
private fun QuickTile(site: QuickSite, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.r))
            .clickable(onClick = onClick)
            .padding(vertical = 8.r),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(74.r)
                .clip(RoundedCornerShape(24.r))
                .background(Brush.linearGradient(site.colors)),
            contentAlignment = Alignment.Center,
        ) {
            RText(site.code, 23, FontWeight.Bold, Color.White)
        }
        Spacer(Modifier.height(10.r))
        RText(site.name, 16, FontWeight.Medium, Dl.Label)
    }
}

@Composable
private fun HintLine(glyph: Glyph, colors: List<Color>, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GradIcon(glyph, colors, size = 48)
        Spacer(Modifier.width(16.r))
        RText(text, 17, FontWeight.Normal, Dl.Sub, Modifier.weight(1f), maxLines = 3, lineHeight = 25)
    }
}

// ==================================================================== sheets & dialogs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaSheet(
    pageUrl: String,
    items: List<Sniffed>,
    onDismiss: () -> Unit,
    onAnalyze: (String) -> Unit,
    onFile: (Sniffed) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 26.r, end = 26.r, bottom = 26.r, top = 8.r)
        ) {
            RText("এই পেজ থেকে ডাউনলোড", 26, FontWeight.Bold, Dl.Ink)
            Spacer(Modifier.height(4.r))
            RText(displayHost(pageUrl), 18, FontWeight.Normal, Dl.Sub)
            Spacer(Modifier.height(18.r))
            DlCard(accent = Dl.brand, onClick = { onAnalyze(pageUrl) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Glyph.Bolt, Dl.brand, size = 64)
                    Spacer(Modifier.width(18.r))
                    Column(Modifier.weight(1f)) {
                        RText("সব ফরম্যাট দেখুন", 22, FontWeight.Bold, Dl.Ink)
                        Spacer(Modifier.height(2.r))
                        RText("ভিডিওর সব কোয়ালিটি, অডিও (MP3) আর ছবি", 17, FontWeight.Normal, Dl.Sub, maxLines = 2)
                    }
                    GlyphIcon(Glyph.ChevronRight, Dl.Sub, Modifier.size(26.r), strokeWidth = 2.2f)
                }
            }
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(22.r))
                RText("পেজে চলা মিডিয়া · ${items.size}", 19, FontWeight.SemiBold, Dl.Sub)
                Spacer(Modifier.height(12.r))
                Column(
                    Modifier.heightIn(max = 560.r).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.r),
                ) {
                    items.forEach { item -> SniffRow(item) { if (item.stream) onAnalyze(item.url) else onFile(item) } }
                }
            }
        }
    }
}

@Composable
private fun SniffRow(item: Sniffed, onClick: () -> Unit) {
    val colors = Dl.kindColors(item.kind)
    val shape = RoundedCornerShape(20.r)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White)
            .border(1.2.r, C.FieldLine, shape)
            .clickable(onClick = onClick)
            .padding(16.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradIcon(Dl.kindGlyph(item.kind), colors, size = 54)
        Spacer(Modifier.width(16.r))
        Column(Modifier.weight(1f)) {
            RText(
                (if (item.stream) "স্ট্রিম" else item.kind.bn) + " · " + item.ext.uppercase(Locale.US),
                19,
                FontWeight.SemiBold,
                Dl.Ink,
            )
            Spacer(Modifier.height(2.r))
            RText(
                item.host + " · " + (Uri.parse(item.url).lastPathSegment ?: "").takeLast(28),
                15,
                FontWeight.Normal,
                Dl.Hint,
            )
        }
        Spacer(Modifier.width(10.r))
        Tag(if (item.stream) "দেখুন" else "নামান", colors, filled = true)
    }
}

@Composable
private fun OfferDialog(o: FileOffer, onDismiss: () -> Unit, onDownload: () -> Unit) {
    val kind = Analyzer.kindForMime(o.mime)
    val ext = o.name.substringAfterLast('.', "").uppercase(Locale.US)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { GradIcon(Dl.kindGlyph(kind), Dl.kindColors(kind), size = 76) },
        title = {
            RText("ফাইল ডাউনলোড", 26, FontWeight.Bold, Dl.Ink, Modifier.fillMaxWidth(), align = TextAlign.Center)
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                RText(o.name, 19, FontWeight.Medium, Dl.Ink, maxLines = 3, align = TextAlign.Center, lineHeight = 26)
                Spacer(Modifier.height(8.r))
                RText(
                    listOf(ext, Analyzer.sizeText(o.size), Analyzer.hostOf(o.url) ?: "")
                        .filter { it.isNotEmpty() }
                        .joinToString(" · "),
                    16,
                    FontWeight.Normal,
                    Dl.Sub,
                    align = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) { RText("ডাউনলোড", 20, FontWeight.Bold, Dl.GreenText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { RText("বাতিল", 19, FontWeight.Medium, Dl.Sub) }
        },
    )
}
