package com.nirvor.app.ui.browser

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nirvor.app.browser.AdBlocker
import com.nirvor.app.browser.AdListEntity
import com.nirvor.app.browser.AdRuleEntity
import com.nirvor.app.browser.BookmarkEntity
import com.nirvor.app.browser.Browser
import com.nirvor.app.browser.BrowserActions
import com.nirvor.app.browser.BrowserDatabase
import com.nirvor.app.browser.BrowserSettings
import com.nirvor.app.browser.PageResource
import com.nirvor.app.browser.Search
import com.nirvor.app.browser.SiteSettingEntity
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.Pill
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.download.Dl
import com.nirvor.app.ui.download.GradIcon
import com.nirvor.app.ui.download.Tag
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shows the page on top of the browser's page stack. */
@Composable
internal fun SubHost(
    sub: BSub,
    onBack: () -> Unit,
    onPush: (BSub) -> Unit,
    onOpen: (String) -> Unit,
    onOpenNewTab: (String) -> Unit,
    onDownload: (String, String?) -> Unit,
) {
    when (sub) {
        BSub.Settings -> BrowserSettingsPage(onBack, onPush)
        is BSub.Section -> SettingsSection(sub.key, onBack, onPush)
        BSub.History -> HistoryPage(onBack, onOpen)
        BSub.Bookmarks -> BookmarksPage(onBack, onOpen, onOpenNewTab)
        BSub.SavedPages -> SavedPagesPage(onBack, onOpenNewTab)
        BSub.Resources -> ResourcesPage(onBack, onOpenNewTab, onDownload)
        is BSub.Source -> SourcePage(sub.html, onBack)
        is BSub.Reader -> ReaderPage(sub.json, onBack)
        BSub.Adblock -> AdblockPage(onBack)
        BSub.Sites -> SitesPage(onBack)
        BSub.About -> AboutPage(onBack, onPush)
        BSub.Faq -> FaqPage(onBack)
    }
}

private val dateFmt = SimpleDateFormat("d MMM, h:mm a", Locale.US)

// ==================================================================== history

@Composable
private fun HistoryPage(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { BrowserDatabase.get(context).dao() }
    var q by remember { mutableStateOf("") }
    val list by remember(q) { dao.history("%${q.trim()}%") }.collectAsStateWithLifecycle(emptyList())
    var confirm by remember { mutableStateOf(false) }
    SubPage("History", "হিস্ট্রি", onBack, actions = {
        RoundButtonSmall(Glyph.Trash) { confirm = true }
    }) {
        item { InputBox(q, { q = it }, "হিস্ট্রিতে খুঁজুন") }
        if (list.isEmpty()) item { EmptyText(if (q.isBlank()) "এখনো কোনো পেজ দেখা হয়নি" else "কিছু পাওয়া যায়নি") }
        items(list, key = { it.id }) { h ->
            LinkRow(h.title.ifBlank { h.url }, h.url, dateFmt.format(Date(h.visitedAt)), Glyph.History, onClick = { onOpen(h.url) }) {
                scope.launch { dao.deleteHistory(h.id) }
            }
        }
    }
    if (confirm) {
        AskDialog("সব হিস্ট্রি মুছবেন?", "দেখা পেজের পুরো তালিকা মুছে যাবে।", "মুছুন", "না", danger = true, glyph = Glyph.Trash, onYes = {
            confirm = false
            scope.launch { dao.clearHistory() }
        }, onNo = { confirm = false })
    }
}

@Composable
private fun LinkRow(title: String, url: String, meta: String?, glyph: Glyph, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    ListCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(glyph, C.NavIcon, Modifier.size(28.r), strokeWidth = 1.9f)
            Spacer(Modifier.width(18.r))
            Column(Modifier.weight(1f)) {
                RText(title, 19, FontWeight.Medium, C.Ink, maxLines = 2, lineHeight = 26)
                RText(url, 15, FontWeight.Normal, Dl.Hint)
                if (meta != null) RText(meta, 14, FontWeight.Normal, Dl.Sub)
            }
            if (onDelete != null) {
                GlyphIcon(
                    Glyph.Close,
                    Dl.Sub,
                    Modifier.size(44.r).clip(CircleShape).clickable(onClick = onDelete).padding(11.r),
                    strokeWidth = 2f,
                )
            }
        }
    }
}

@Composable
internal fun RoundButtonSmall(glyph: Glyph, onClick: () -> Unit) {
    GlyphIcon(
        glyph,
        C.Ink,
        Modifier.size(58.r).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)).clickable(onClick = onClick).padding(15.r),
        strokeWidth = 2.1f,
    )
}

// ==================================================================== bookmarks

@Composable
private fun BookmarksPage(onBack: () -> Unit, onOpen: (String) -> Unit, onOpenNewTab: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { BrowserDatabase.get(context).dao() }
    val list by remember { dao.bookmarks() }.collectAsStateWithLifecycle(emptyList())
    var edit by remember { mutableStateOf<BookmarkEntity?>(null) }
    val groups = list.groupBy { it.folder }.toList().let { g ->
        if (BrowserSettings.bookmarkFoldersFirst) g.sortedBy { if (it.first.isBlank()) 1 else 0 } else g.sortedBy { if (it.first.isBlank()) 0 else 1 }
    }
    SubPage("Bookmarks", "বুকমার্ক · ${list.size}", onBack) {
        if (list.isEmpty()) item { EmptyText("কোনো বুকমার্ক নেই। মেনু → \"বুকমার্ক যোগ করুন\"") }
        groups.forEach { (folder, items) ->
            if (folder.isNotBlank() || groups.size > 1) item { GroupLabel(folder.ifBlank { "অন্যান্য" }) }
            items(items, key = { it.id }) { b ->
                ListCard(onClick = { onOpen(b.url) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Bookmark, Color(0xFFF4A905), Modifier.size(28.r), strokeWidth = 1.9f)
                        Spacer(Modifier.width(18.r))
                        Column(Modifier.weight(1f)) {
                            RText(b.title.ifBlank { b.url }, 19, FontWeight.Medium, C.Ink, maxLines = 2, lineHeight = 26)
                            RText(b.url, 15, FontWeight.Normal, Dl.Hint)
                        }
                        GlyphIcon(Glyph.Plus, Dl.Sub, Modifier.size(42.r).clip(CircleShape).clickable { onOpenNewTab(b.url) }.padding(10.r), strokeWidth = 2f)
                        GlyphIcon(Glyph.Edit, Dl.Sub, Modifier.size(42.r).clip(CircleShape).clickable { edit = b }.padding(10.r), strokeWidth = 1.9f)
                        GlyphIcon(Glyph.Trash, Dl.Sub, Modifier.size(42.r).clip(CircleShape).clickable { scope.launch { dao.deleteBookmark(b.id) } }.padding(10.r), strokeWidth = 1.8f)
                    }
                }
            }
        }
    }
    edit?.let { b ->
        BookmarkDialog(b, onDismiss = { edit = null }) { saved ->
            edit = null
            scope.launch { dao.updateBookmark(saved.id, saved.title, saved.url, saved.folder) }
        }
    }
}

// ==================================================================== saved pages

@Composable
private fun SavedPagesPage(onBack: () -> Unit, onOpenNewTab: (String) -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val files = remember(tick) {
        BrowserActions.savedPagesDir(context).listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
    }
    SubPage("Saved pages", "সেভ করা পেজ (অফলাইনে পড়ুন)", onBack) {
        if (files.isEmpty()) item { EmptyText("কোনো পেজ সেভ করা নেই। মেনু → \"পেজ সেভ (অফলাইন)\"") }
        items(files, key = { it.absolutePath }) { f ->
            ListCard(onClick = { onOpenNewTab("file://" + f.absolutePath) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Save, C.NavIcon, Modifier.size(28.r), strokeWidth = 1.9f)
                    Spacer(Modifier.width(18.r))
                    Column(Modifier.weight(1f)) {
                        RText(f.nameWithoutExtension, 19, FontWeight.Medium, C.Ink, maxLines = 2, lineHeight = 26)
                        RText(dateFmt.format(Date(f.lastModified())) + " · " + com.nirvor.app.downloader.Analyzer.sizeText(f.length()), 15, FontWeight.Normal, Dl.Hint)
                    }
                    GlyphIcon(Glyph.Share, Dl.Sub, Modifier.size(42.r).clip(CircleShape).clickable { BrowserActions.shareFile(context, f, "multipart/related") }.padding(10.r), strokeWidth = 1.9f)
                    GlyphIcon(Glyph.Trash, Dl.Sub, Modifier.size(42.r).clip(CircleShape).clickable {
                        f.delete()
                        tick++
                    }.padding(10.r), strokeWidth = 1.8f)
                }
            }
        }
    }
}

// ==================================================================== page resources

@Composable
private fun ResourcesPage(onBack: () -> Unit, onOpenNewTab: (String) -> Unit, onDownload: (String, String?) -> Unit) {
    val context = LocalContext.current
    val tab = Browser.current
    val all = tab?.resources?.toList().orEmpty()
    val types = listOf("all" to "সব", "media" to "মিডিয়া", "image" to "ছবি", "script" to "স্ক্রিপ্ট", "style" to "স্টাইল", "page" to "পেজ", "other" to "অন্যান্য", "blocked" to "ব্লক")
    var type by remember { mutableStateOf("all") }
    var pick by remember { mutableStateOf<PageResource?>(null) }
    val shown = all.filter {
        when (type) {
            "all" -> true
            "blocked" -> it.blocked
            else -> it.type == type
        }
    }
    SubPage("Page resources", "এই পেজ যা যা লোড করেছে · ${all.size}", onBack) {
        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                items(types) { (k, label) ->
                    val n = if (k == "all") all.size else if (k == "blocked") all.count { it.blocked } else all.count { it.type == k }
                    Pill("$label $n", k == type) { type = k }
                }
            }
        }
        if (shown.isEmpty()) item { EmptyText("কিছু নেই") }
        items(shown.take(800)) { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.r))
                    .background(Color.White)
                    .clickable { pick = r }
                    .padding(horizontal = 16.r, vertical = 12.r),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(if (r.blocked) "ব্লক" else typeLabel(r.type), if (r.blocked) Dl.red else typeColors(r.type))
                Spacer(Modifier.width(12.r))
                RText(r.url, 15, FontWeight.Normal, C.Ink, Modifier.weight(1f), maxLines = 2, lineHeight = 21)
            }
        }
    }
    pick?.let { r ->
        ChoiceDialog(
            "কী করবেন?",
            listOf("open" to "নতুন ট্যাবে খুলুন", "download" to "ডাউনলোড করুন", "copy" to "লিংক কপি"),
            "",
            onDismiss = { pick = null },
        ) { k ->
            pick = null
            when (k) {
                "open" -> onOpenNewTab(r.url)
                "download" -> onDownload(r.url, null)
                else -> BrowserActions.copy(context, "link", r.url)
            }
        }
    }
}

private fun typeLabel(t: String) = when (t) {
    "media" -> "মিডিয়া"
    "image" -> "ছবি"
    "script" -> "JS"
    "style" -> "CSS"
    "page" -> "পেজ"
    "font" -> "ফন্ট"
    else -> "অন্য"
}

private fun typeColors(t: String) = when (t) {
    "media" -> Dl.purple
    "image" -> Dl.orange
    "script" -> Dl.amber
    "style" -> Dl.sky
    "page" -> Dl.brand
    else -> Dl.slate
}

// ==================================================================== page source

@Composable
private fun SourcePage(html: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val chunks = remember(html) { html.chunked(3000) }
    SubPage("Page source", "${html.length} অক্ষর", onBack, actions = {
        RoundButtonSmall(Glyph.Copy) { BrowserActions.copy(context, "source", html.take(900_000)) }
        Spacer(Modifier.width(10.r))
        RoundButtonSmall(Glyph.External) { BrowserActions.viewSourceWith(context, html, Browser.current?.title.orEmpty()) }
    }) {
        items(chunks.size) { i ->
            SelectionContainer {
                Text(
                    chunks[i],
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.rs, color = C.Ink, lineHeight = 19.rs),
                    modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 14.r, vertical = 2.r),
                )
            }
        }
    }
}

// ==================================================================== reader mode

@Composable
private fun ReaderPage(json: String, onBack: () -> Unit) {
    val data = remember(json) {
        try {
            JSONObject(json)
        } catch (e: Exception) {
            JSONObject()
        }
    }
    val title = data.optString("title")
    val paras = remember(data) {
        val arr = data.optJSONArray("paragraphs")
        (0 until (arr?.length() ?: 0)).map { arr!!.optString(it) }
    }
    var size by remember { mutableIntStateOf(21) }
    SubPage("Reader", Search.displayHost(Browser.current?.url ?: ""), onBack, actions = {
        RoundButtonSmall(Glyph.ChevronDown) { size = (size - 2).coerceAtLeast(15) }
        Spacer(Modifier.width(10.r))
        RoundButtonSmall(Glyph.ChevronUp) { size = (size + 2).coerceAtMost(33) }
    }) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.r)).background(Color.White).padding(26.r)) {
                RText(title, size + 9, FontWeight.Bold, C.Ink, maxLines = 6, lineHeight = (size + 9) * 1.3f)
                val by = data.optString("byline")
                if (by.isNotBlank()) {
                    Spacer(Modifier.height(6.r))
                    RText(by, 16, FontWeight.Normal, Dl.Sub)
                }
                val img = data.optString("image")
                if (img.isNotBlank()) {
                    Spacer(Modifier.height(16.r))
                    AsyncImage(img, null, Modifier.fillMaxWidth().clip(RoundedCornerShape(14.r)), contentScale = ContentScale.FillWidth)
                }
            }
        }
        if (paras.isEmpty()) item { EmptyText("এই পেজে পড়ার মতো লেখা পাওয়া যায়নি") }
        items(paras.size) { i ->
            val p = paras[i]
            val heading = p.startsWith("## ")
            SelectionContainer {
                Text(
                    if (heading) p.removePrefix("## ") else p,
                    style = TextStyle(
                        fontFamily = com.nirvor.app.ui.theme.Inter,
                        fontSize = (if (heading) size + 4 else size).rs,
                        fontWeight = if (heading) FontWeight.Bold else FontWeight.Normal,
                        color = C.Ink,
                        lineHeight = ((if (heading) size + 4 else size) * 1.55f).rs,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.r, vertical = 4.r),
                )
            }
        }
    }
}

// ==================================================================== ad block manager

@Composable
private fun AdblockPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { BrowserDatabase.get(context).dao() }
    val lists by remember { dao.adLists() }.collectAsStateWithLifecycle(emptyList())
    val rules by remember { dao.adRules() }.collectAsStateWithLifecycle(emptyList())
    var adding by remember { mutableStateOf<String?>(null) }
    var updating by remember { mutableStateOf<Long?>(null) }
    var hostCount by remember { mutableIntStateOf(AdBlocker.hostCount) }
    LaunchedEffect(lists, rules) {
        kotlinx.coroutines.delay(600)
        hostCount = AdBlocker.hostCount
    }
    fun toast(t: String) = Toast.makeText(context, t, Toast.LENGTH_SHORT).show()
    fun update(l: AdListEntity) {
        updating = l.id
        scope.launch {
            try {
                val n = AdBlocker.updateList(context, l)
                toast("${l.name}: ${n}টা হোস্ট")
            } catch (e: Exception) {
                toast("ডাউনলোড হয়নি: " + (e.message ?: "ইন্টারনেট দেখুন"))
            }
            updating = null
            hostCount = AdBlocker.hostCount
        }
    }

    SubPage("Adblock", "হোস্ট ও ফিল্টার · মোট $hostCount", onBack) {
        item {
            Group {
                SwitchRow("বিজ্ঞাপন ব্লক", "বিজ্ঞাপন, ট্র্যাকার আর পপ-আপ সাইট আটকায়", BrowserSettings.blockAds) { BrowserSettings.blockAds = it }
            }
        }
        item { GroupLabel("ব্লক লিস্ট (ডাউনলোড করে নিন)") }
        item {
            Group {
                AdBlocker.presets.forEachIndexed { i, (name, url) ->
                    val saved = lists.firstOrNull { it.url == url }
                    if (i > 0) GroupDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.r, vertical = 14.r), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            RText(name, 19, FontWeight.Medium, C.Ink)
                            RText(
                                when {
                                    updating == saved?.id && saved != null -> "ডাউনলোড হচ্ছে…"
                                    saved == null -> "চালু নেই"
                                    saved.updatedAt == 0L -> "এখনো ডাউনলোড হয়নি"
                                    else -> "${saved.count}টা · " + dateFmt.format(Date(saved.updatedAt))
                                },
                                15,
                                FontWeight.Normal,
                                Dl.Sub,
                            )
                        }
                        if (saved != null) {
                            GlyphIcon(Glyph.Refresh, Dl.GreenText, Modifier.size(44.r).clip(CircleShape).clickable { update(saved) }.padding(10.r), strokeWidth = 2f)
                        }
                        androidx.compose.material3.Switch(
                            checked = saved?.enabled == true,
                            onCheckedChange = { on ->
                                scope.launch {
                                    if (saved == null) {
                                        val id = dao.addAdList(AdListEntity(name = name, url = url))
                                        update(AdListEntity(id = id, name = name, url = url))
                                    } else {
                                        dao.setAdListEnabled(saved.id, on)
                                        AdBlocker.reload(context)
                                    }
                                }
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Dl.Green, uncheckedBorderColor = Color.Transparent, uncheckedTrackColor = Color(0xFFD5DEE7), uncheckedThumbColor = Color.White),
                        )
                    }
                }
                lists.filter { l -> AdBlocker.presets.none { it.second == l.url } }.forEach { l ->
                    GroupDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.r, vertical = 14.r), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            RText(l.name, 19, FontWeight.Medium, C.Ink)
                            RText(if (updating == l.id) "ডাউনলোড হচ্ছে…" else "${l.count}টা · ${l.url}", 15, FontWeight.Normal, Dl.Sub, maxLines = 2)
                        }
                        GlyphIcon(Glyph.Refresh, Dl.GreenText, Modifier.size(44.r).clip(CircleShape).clickable { update(l) }.padding(10.r), strokeWidth = 2f)
                        GlyphIcon(Glyph.Trash, Dl.Sub, Modifier.size(44.r).clip(CircleShape).clickable {
                            scope.launch {
                                dao.deleteAdList(l.id)
                                AdBlocker.deleteListFile(context, l.id)
                                AdBlocker.reload(context)
                            }
                        }.padding(10.r), strokeWidth = 1.8f)
                    }
                }
                GroupDivider()
                ActionRow("নিজের লিস্ট যোগ করুন", "hosts ফাইল বা EasyList-এর লিংক", Glyph.Plus) { adding = "list" }
            }
        }
        item { GroupLabel("আমার নিয়ম") }
        item {
            Group {
                ActionRow("হোস্ট ব্লক করুন", "যেমন: ads.example.com (সাব-ডোমেইনসহ ব্লক হবে)", Glyph.AdBlock) { adding = "host" }
                GroupDivider()
                ActionRow("ফিল্টার যোগ করুন", "লিংকের ভেতরে এই লেখা থাকলে ব্লক, যেমন: /ads/", Glyph.Filter) { adding = "filter" }
                GroupDivider()
                ActionRow("এই সাইটে ব্লক করবেন না", "যে সাইটে বিজ্ঞাপন ব্লক বন্ধ রাখতে চান", Glyph.Shield) { adding = "allow" }
            }
        }
        if (rules.isNotEmpty()) {
            items(rules, key = { it.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.r)).background(Color.White).padding(horizontal = 16.r, vertical = 10.r),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tag(
                        when (r.kind) {
                            "host" -> "হোস্ট"
                            "filter" -> "ফিল্টার"
                            else -> "অনুমতি"
                        },
                        if (r.kind == "allow") Dl.brand else Dl.red,
                    )
                    Spacer(Modifier.width(12.r))
                    RText(r.rule, 17, FontWeight.Normal, C.Ink, Modifier.weight(1f))
                    GlyphIcon(Glyph.Close, Dl.Sub, Modifier.size(42.r).clip(CircleShape).clickable {
                        scope.launch {
                            dao.deleteAdRule(r.id)
                            AdBlocker.reload(context)
                        }
                    }.padding(11.r), strokeWidth = 2f)
                }
            }
        }
    }

    adding?.let { kind ->
        val (title, hint) = when (kind) {
            "list" -> "লিস্টের লিংক" to "https://…/hosts.txt"
            "host" -> "হোস্ট" to "ads.example.com"
            "filter" -> "ফিল্টার" to "/ads/"
            else -> "সাইট" to "example.com"
        }
        InputDialog(title, "", hint, if (kind == "filter") KeyboardType.Text else KeyboardType.Uri, confirm = "যোগ করুন", onDismiss = { adding = null }) { value ->
            adding = null
            if (value.isBlank()) return@InputDialog
            scope.launch {
                if (kind == "list") {
                    val url = if (value.startsWith("http")) value else "https://$value"
                    val id = dao.addAdList(AdListEntity(name = Search.displayHost(url), url = url))
                    update(AdListEntity(id = id, name = Search.displayHost(url), url = url))
                } else {
                    val rule = if (kind == "filter") value else Search.displayHost(if (value.startsWith("http")) value else "https://$value")
                    dao.addAdRule(AdRuleEntity(rule = rule, kind = kind))
                    AdBlocker.reload(context)
                }
            }
        }
    }
}

// ==================================================================== site settings

@Composable
private fun SitesPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { BrowserDatabase.get(context).dao() }
    val sites by remember { dao.siteSettings() }.collectAsStateWithLifecycle(emptyList())
    var edit by remember { mutableStateOf<SiteSettingEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    val currentHost = Browser.current?.url?.takeIf { it.startsWith("http") }?.let { Search.displayHost(it) }
    SubPage("Site settings", "প্রতিটা সাইটের নিজস্ব সেটিংস", onBack) {
        item {
            Group {
                if (currentHost != null) {
                    ActionRow("এই সাইট: $currentHost", "এই সাইটের জন্য আলাদা সেটিংস", Glyph.Sliders) {
                        edit = sites.firstOrNull { it.host == currentHost } ?: SiteSettingEntity(host = currentHost)
                    }
                    GroupDivider()
                }
                ActionRow("সাইট যোগ করুন", "নাম লিখে", Glyph.Plus) { adding = true }
            }
        }
        if (sites.isEmpty()) item { EmptyText("এখনো কোনো সাইটের আলাদা সেটিংস নেই") }
        items(sites, key = { it.host }) { s ->
            ListCard(onClick = { edit = s }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Glyph.Globe, Dl.sky, size = 46)
                    Spacer(Modifier.width(16.r))
                    Column(Modifier.weight(1f)) {
                        RText(s.host, 19, FontWeight.Medium, C.Ink)
                        RText(siteSummary(s), 15, FontWeight.Normal, Dl.Sub, maxLines = 2)
                    }
                    GlyphIcon(Glyph.Trash, Dl.Sub, Modifier.size(42.r).clip(CircleShape).clickable {
                        scope.launch {
                            dao.deleteSite(s.host)
                            Browser.refreshSites()
                        }
                    }.padding(10.r), strokeWidth = 1.8f)
                }
            }
        }
    }
    if (adding) {
        InputDialog("সাইট", "", "example.com", KeyboardType.Uri, confirm = "পরের ধাপ", onDismiss = { adding = false }) { v ->
            adding = false
            if (v.isNotBlank()) edit = SiteSettingEntity(host = Search.displayHost(if (v.startsWith("http")) v else "https://$v"))
        }
    }
    edit?.let { s ->
        SiteDialog(s, onDismiss = { edit = null }) { saved ->
            edit = null
            scope.launch {
                dao.saveSite(saved)
                Browser.refreshSites()
                Browser.applySettingsToAll()
            }
        }
    }
}

private fun siteSummary(s: SiteSettingEntity): String {
    fun v(x: Int) = when (x) {
        1 -> "চালু"
        0 -> "বন্ধ"
        else -> null
    }
    return listOfNotNull(
        v(s.javascript)?.let { "JS $it" },
        v(s.desktop)?.let { "ডেস্কটপ $it" },
        v(s.adblock)?.let { "অ্যাড ব্লক $it" },
        v(s.images)?.let { "ছবি $it" },
        v(s.darkMode)?.let { "ডার্ক $it" },
    ).joinToString(" · ").ifBlank { "সব সাধারণ সেটিংস অনুযায়ী" }
}

@Composable
private fun SiteDialog(s: SiteSettingEntity, onDismiss: () -> Unit, onSave: (SiteSettingEntity) -> Unit) {
    var js by remember { mutableIntStateOf(s.javascript) }
    var desktop by remember { mutableIntStateOf(s.desktop) }
    var adblock by remember { mutableIntStateOf(s.adblock) }
    var images by remember { mutableIntStateOf(s.images) }
    var dark by remember { mutableIntStateOf(s.darkMode) }
    val opts = listOf("-1" to "সাধারণ সেটিংস অনুযায়ী", "1" to "চালু", "0" to "বন্ধ")
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { RText(s.host, 23, FontWeight.Bold, C.Ink, maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChoiceRow("JavaScript", opts, js.toString()) { js = it.toInt() }
                ChoiceRow("ডেস্কটপ সাইট", opts, desktop.toString()) { desktop = it.toInt() }
                ChoiceRow("বিজ্ঞাপন ব্লক", opts, adblock.toString()) { adblock = it.toInt() }
                ChoiceRow("ছবি দেখাবে", opts, images.toString()) { images = it.toInt() }
                ChoiceRow("ডার্ক মোড", opts, dark.toString()) { dark = it.toInt() }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onSave(s.copy(javascript = js, desktop = desktop, adblock = adblock, images = images, darkMode = dark))
            }) { RText("সেভ", 19, FontWeight.Bold, Dl.GreenText) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { RText("বাতিল", 19, FontWeight.Medium, Dl.Sub) } },
    )
}

// ==================================================================== about & FAQ

@Composable
private fun AboutPage(onBack: () -> Unit, onPush: (BSub) -> Unit) {
    val context = LocalContext.current
    SubPage("About", "Nirvor Browser", onBack) {
        item {
            ListCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    GradIcon(Glyph.Globe, Dl.sky, size = 100, iconScale = 0.52f)
                    Spacer(Modifier.height(14.r))
                    RText("Nirvor Browser", 30, FontWeight.Bold, C.Ink)
                    RText("ভার্সন " + com.nirvor.app.ui.screens.appVersion(context), 18, FontWeight.Normal, Dl.Sub)
                    Spacer(Modifier.height(10.r))
                    RText(
                        "দেখুন, চালান, ডাউনলোড করুন। বিজ্ঞাপন ব্লক, ইনকগনিটো, গ্র্যাবার, রিডার মোড — সব এক জায়গায়।",
                        17,
                        FontWeight.Normal,
                        Dl.Sub,
                        maxLines = 3,
                        lineHeight = 25,
                        align = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
        item {
            Group {
                ActionRow("প্রশ্ন ও উত্তর (FAQ)", null, Glyph.Info) { onPush(BSub.Faq) }
                GroupDivider()
                ActionRow("ব্রাউজার ইঞ্জিন", android.webkit.WebView.getCurrentWebViewPackage()?.versionName?.let { "Android System WebView $it" } ?: "Android System WebView", Glyph.Engine, chevron = false) {}
                GroupDivider()
                ActionRow("ডাউনলোড ইঞ্জিন", "yt-dlp · youtubedl-android", Glyph.Download, chevron = false) {}
            }
        }
    }
}

@Composable
private fun FaqPage(onBack: () -> Unit) {
    val faq = listOf(
        "ভিডিও কীভাবে নামাবো?" to "ভিডিও চালু করুন, তারপর নিচের সবুজ ডাউনলোড বাটন চাপুন। বাটনে সংখ্যা দেখালে বুঝবেন ভিডিও ধরা পড়েছে। \"সব ফরম্যাট দেখুন\" চাপলে সব কোয়ালিটি আর MP3 পাবেন।",
        "ডাউনলোড বাটনে সংখ্যা আসছে না" to "মেনু → \"অডিও / ভিডিও ধরুন\" চালু আছে কিনা দেখুন। YouTube-এর মতো সাইটে সরাসরি \"সব ফরম্যাট দেখুন\" কাজ করে।",
        "প্রাইভেট পোস্ট নামাতে পারছি না" to "এই ব্রাউজারে সাইটটায় একবার লগইন করুন। লগইনের কুকি ডাউনলোডেও ব্যবহার হয়। লগইন না হলে কম্পিউটার থেকে cookies.txt এনে \"কুকি ইমপোর্ট\" করুন।",
        "Google-এ লগইন হচ্ছে না" to "Google অ্যাপের ভেতরের ব্রাউজারে লগইন আটকে দেয়। \"অন্য ব্রাউজারে লগইন\" দিয়ে Chrome-এ খুলুন।",
        "বিজ্ঞাপন এখনো আসছে" to "মেনু → অ্যাডব্লক হোস্ট ও ফিল্টার → StevenBlack বা EasyList চালু করুন। দরকার হলে নিজের হোস্ট যোগ করুন।",
        "কোনো সাইট ঠিকমতো চলছে না" to "সাইট সেটিংস তালিকা থেকে সেই সাইটে অ্যাড ব্লক বন্ধ করে দেখুন, বা ডেস্কটপ সাইট বদলে দেখুন।",
        "ইনকগনিটোতে কী হয়?" to "হিস্ট্রি সেভ হয় না, ট্যাব মনে রাখা হয় না, স্ক্রিনশট নেওয়া যায় না, আর সব ইনকগনিটো ট্যাব বন্ধ করলে কুকি মুছে যায় (ফোনের WebView নতুন হলে)।",
        "সেভ করা পেজ কোথায়?" to "মেনু → সেভ করা পেজ। একটা কপি Nirvor/Downloads/General ফোল্ডারেও থাকে।",
    )
    SubPage("FAQ", "প্রশ্ন ও উত্তর", onBack) {
        items(faq.size) { i ->
            val (q, a) = faq[i]
            ListCard {
                RText(q, 20, FontWeight.SemiBold, C.Ink, maxLines = 3, lineHeight = 27)
                Spacer(Modifier.height(6.r))
                RText(a, 17, FontWeight.Normal, C.StatLabelBn, maxLines = 8, lineHeight = 25)
            }
        }
    }
}

