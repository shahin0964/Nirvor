package com.nirvor.app.ui.browser

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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.nirvor.app.browser.Browser
import com.nirvor.app.browser.BrowserSettings
import com.nirvor.app.browser.BrowserTab
import com.nirvor.app.browser.Search
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.download.Dl
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter
import kotlinx.coroutines.delay

// ==================================================================== address bar

@Composable
internal fun TopBar(
    tab: BrowserTab?,
    editing: Boolean,
    field: TextFieldValue,
    onField: (TextFieldValue) -> Unit,
    onStartEditing: () -> Unit,
    onStopEditing: () -> Unit,
    onSubmit: (String) -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(editing) {
        if (editing) focus.requestFocus() else focusManager.clearFocus()
    }
    val url = tab?.takeIf { !it.onStart }?.url.orEmpty()
    val incognito = tab?.incognito == true

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.r, end = 10.r, top = 12.r, bottom = 10.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabCounter(incognito, onTabs)
        Spacer(Modifier.width(12.r))

        val shape = RoundedCornerShape(50)
        var drag by remember { mutableFloatStateOf(0f) }
        Row(
            Modifier
                .weight(1f)
                .height(66.r)
                .clip(shape)
                .background(if (incognito) Color(0xFFE6E9F0) else Dl.Soft)
                .border(1.2.r, if (editing) Brush.horizontalGradient(Dl.brand) else SolidColor(Dl.Line), shape)
                .pointerInput(editing) {
                    if (!editing && BrowserSettings.swipeAddressBar) {
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                val tabs = Browser.tabs
                                val i = tabs.indexOfFirst { it.id == Browser.currentId }
                                if (i >= 0 && kotlin.math.abs(drag) > 120f) {
                                    val j = if (drag < 0) i + 1 else i - 1
                                    tabs.getOrNull(j)?.let { Browser.select(it) }
                                }
                            },
                            onHorizontalDrag = { _, amount -> drag += amount },
                        )
                    }
                }
                .clickable(enabled = !editing, onClick = onStartEditing)
                .padding(start = 18.r, end = 6.r),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val secure = url.startsWith("https://")
            GlyphIcon(
                when {
                    editing || url.isEmpty() -> Glyph.Search
                    incognito -> Glyph.Incognito
                    secure -> Glyph.Lock
                    else -> Glyph.Globe
                },
                if (secure && !editing && !incognito) Dl.GreenText else C.NavIcon,
                Modifier.size(24.r),
                strokeWidth = 2.1f,
            )
            Spacer(Modifier.width(12.r))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (editing) {
                    if (field.text.isEmpty()) RText("সার্চ বা লিংক", 20, FontWeight.Normal, Dl.Hint)
                    BasicTextField(
                        value = field,
                        onValueChange = onField,
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Inter, fontSize = 20.rs, color = Dl.Ink),
                        cursorBrush = SolidColor(Dl.Green),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (field.text.isNotBlank()) onSubmit(field.text) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                } else if (url.isEmpty()) {
                    RText(if (incognito) "ইনকগনিটো · সার্চ বা লিংক" else "সার্চ করুন বা লিংক লিখুন", 20, FontWeight.Normal, Dl.Hint)
                } else {
                    RText(addressText(tab), 19, FontWeight.Medium, Dl.Ink)
                }
            }
            when {
                editing -> BarIcon(Glyph.Close, onStopEditing)
                tab != null && url.isNotEmpty() -> if (tab.progress < 100) {
                    BarIcon(Glyph.Close) { tab.webView?.stopLoading() }
                } else {
                    BarIcon(Glyph.Refresh) { tab.webView?.reload() }
                }
            }
        }
        Spacer(Modifier.width(4.r))
        GlyphIcon(
            Glyph.Dots,
            C.NavIcon,
            Modifier.size(56.r).clip(CircleShape).clickable(onClick = onMenu).padding(14.r),
            strokeWidth = 2.2f,
        )
    }
}

/** URL box contents: the whole address, the page title, or just the site. */
private fun addressText(tab: BrowserTab?): String {
    tab ?: return ""
    return when (BrowserSettings.urlBoxContents) {
        "url" -> tab.url
        "title" -> tab.title.ifBlank { Search.displayHost(tab.url) }
        else -> Search.displayHost(tab.url)
    }
}

@Composable
private fun TabCounter(incognito: Boolean, onClick: () -> Unit) {
    val count = Browser.tabs.size
    val index = Browser.tabs.indexOfFirst { it.id == Browser.currentId } + 1
    val text = if (BrowserSettings.showTabTotal) "$index/$count" else if (count > 99) ":D" else count.toString()
    val shape = RoundedCornerShape(12.r)
    Box(
        Modifier
            .heightIn(min = 44.r)
            .widthIn(min = 44.r)
            .clip(shape)
            .background(if (incognito) Color(0xFF3B4558) else Color.White)
            .border(2.2.r, if (incognito) Color(0xFF3B4558) else C.Ink, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.r),
        contentAlignment = Alignment.Center,
    ) {
        RText(text, 18, FontWeight.Bold, if (incognito) Color.White else C.Ink)
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

// ==================================================================== find in page

@Composable
internal fun FindBar(tab: BrowserTab, query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) {
        if (query.isNotEmpty()) tab.webView?.findAllAsync(query) else tab.webView?.clearMatches()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.r, end = 10.r, top = 12.r, bottom = 10.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarIcon(Glyph.Close, onClose)
        val shape = RoundedCornerShape(50)
        Box(
            Modifier
                .weight(1f)
                .height(62.r)
                .clip(shape)
                .background(Dl.Soft)
                .border(1.2.r, Brush.horizontalGradient(Dl.brand), shape)
                .padding(horizontal = 20.r),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) RText("পেজে খুঁজুন", 20, FontWeight.Normal, Dl.Hint)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Inter, fontSize = 20.rs, color = Dl.Ink),
                cursorBrush = SolidColor(Dl.Green),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { tab.webView?.findNext(true) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Spacer(Modifier.width(10.r))
        val shown = if (tab.findMatches == 0) "0/0" else "${tab.findIndex + 1}/${tab.findMatches}"
        RText(shown, 17, FontWeight.Medium, Dl.Sub)
        BarIcon(Glyph.ChevronUp) { tab.webView?.findNext(false) }
        BarIcon(Glyph.ChevronDown) { tab.webView?.findNext(true) }
    }
}

// ==================================================================== loading line

@Composable
internal fun LoadLine(tab: BrowserTab?) {
    val visible = tab != null && !tab.onStart && tab.progress < 100
    val p by animateFloatAsState((tab?.progress ?: 100) / 100f, label = "load")
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

// ==================================================================== suggestions

@Composable
internal fun Suggestions(text: String, incognito: Boolean, onPick: (String) -> Unit, onFill: (String) -> Unit) {
    val context = LocalContext.current
    var list by remember { mutableStateOf<List<Search.Suggestion>>(emptyList()) }
    LaunchedEffect(text) {
        if (text.isBlank()) {
            list = emptyList()
            return@LaunchedEffect
        }
        delay(220)
        list = Search.suggest(context, text, incognito)
    }
    if (list.isEmpty()) return
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .heightIn(max = 620.r)
            .verticalScroll(rememberScrollState())
    ) {
        list.forEach { s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(s.url ?: s.text) }
                    .padding(horizontal = 22.r, vertical = 14.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(
                    when (s.kind) {
                        "bookmark" -> Glyph.Bookmark
                        "history" -> Glyph.History
                        else -> Glyph.Search
                    },
                    C.NavIcon,
                    Modifier.size(24.r),
                    strokeWidth = 1.9f,
                )
                Spacer(Modifier.width(18.r))
                Column(Modifier.weight(1f)) {
                    RText(s.text, 19, FontWeight.Normal, Dl.Ink)
                    if (s.url != null && s.url != s.text) RText(s.url, 15, FontWeight.Normal, Dl.Hint)
                }
                GlyphIcon(
                    Glyph.ArrowLeft,
                    Dl.Hint,
                    Modifier.size(44.r).clip(CircleShape).clickable { onFill(s.url ?: s.text) }.padding(10.r),
                    strokeWidth = 1.9f,
                )
            }
            Box(Modifier.fillMaxWidth().padding(start = 64.r).height(1.r).background(C.Divider))
        }
    }
}

// ==================================================================== bottom dock

@Composable
internal fun Dock(
    tab: BrowserTab?,
    bookmarked: Boolean,
    showOrb: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onDownload: () -> Unit,
    onHome: () -> Unit,
    onBookmark: () -> Unit,
) {
    val canBack = tab != null && ((tab.onStart && tab.url.isNotEmpty()) || (!tab.onStart && tab.canBack))
    val canForward = tab != null && !tab.onStart && tab.canForward
    Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.2.r).background(C.NavLine))
        Row(
            Modifier.fillMaxWidth().height(100.r).padding(horizontal = 14.r),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            DockButton(Glyph.ArrowLeft, canBack, onClick = onBack)
            DockButton(Glyph.ArrowRight, canForward, onClick = onForward)
            if (showOrb) DownloadOrb(if (tab == null || tab.onStart) 0 else tab.sniffed.size, onDownload)
            DockButton(Glyph.Home, true, onClick = onHome)
            DockButton(
                Glyph.Star,
                tab != null && !tab.onStart,
                tint = if (bookmarked) Color(0xFFF4A905) else null,
                onClick = onBookmark,
            )
        }
    }
}

@Composable
private fun DockButton(glyph: Glyph, enabled: Boolean, tint: Color? = null, onClick: () -> Unit) {
    GlyphIcon(
        glyph,
        tint ?: if (enabled) C.NavIcon else Dl.Hint.copy(alpha = 0.45f),
        Modifier
            .size(62.r)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(17.r),
        strokeWidth = 2.2f,
    )
}

/** The green download button; it pulses when the page has played something. */
@Composable
internal fun DownloadOrb(count: Int, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "pulse",
    )
    val ring = 3.r
    Box(Modifier.size(96.r), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r0 = size.minDimension * 0.39f
            drawCircle(
                Brush.radialGradient(
                    listOf(C.FabA.copy(alpha = if (count > 0) 0.38f else 0.16f), Color.Transparent),
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
                .size(74.r)
                .clip(CircleShape)
                .background(Brush.linearGradient(Dl.brand))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Download, Color.White, Modifier.size(36.r), strokeWidth = 2.5f)
        }
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.r, end = 4.r)
                    .heightIn(min = 28.r)
                    .widthIn(min = 28.r)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(Dl.red))
                    .padding(horizontal = 7.r),
                contentAlignment = Alignment.Center,
            ) {
                RText(if (count > 9) "9+" else count.toString(), 15, FontWeight.Bold, Color.White)
            }
        }
    }
}
