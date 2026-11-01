package com.nirvor.app.ui.browser

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import com.nirvor.app.browser.BookmarkEntity
import com.nirvor.app.browser.Browser
import com.nirvor.app.browser.BrowserActions
import com.nirvor.app.browser.BrowserSettings
import com.nirvor.app.browser.BrowserTab
import com.nirvor.app.browser.ContextTarget
import com.nirvor.app.browser.FileOffer
import com.nirvor.app.browser.Search
import com.nirvor.app.browser.Sniffed
import com.nirvor.app.downloader.Analyzer
import com.nirvor.app.downloader.Kind
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.download.Dl
import com.nirvor.app.ui.download.DlCard
import com.nirvor.app.ui.download.GradIcon
import com.nirvor.app.ui.download.GreenButton
import com.nirvor.app.ui.download.LightButton
import com.nirvor.app.ui.download.Tag
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import kotlinx.coroutines.launch
import java.util.Locale

// ==================================================================== main menu

@Composable
internal fun MenuSheet(tab: BrowserTab, bookmarked: Boolean, onDismiss: () -> Unit, onAction: (MenuAction) -> Unit) {
    BSheet(onDismiss) {
        // Quick row: back, forward, bookmark, reload, settings
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.r, vertical = 4.r),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            HeaderIcon(Glyph.ArrowLeft, tab.canBack || tab.onStart) { onAction(MenuAction.Back) }
            HeaderIcon(Glyph.ArrowRight, tab.canForward) { onAction(MenuAction.Forward) }
            HeaderIcon(Glyph.Star, !tab.onStart, tint = if (bookmarked) Color(0xFFF4A905) else null) { onAction(MenuAction.ToggleBookmark) }
            HeaderIcon(Glyph.Refresh, !tab.onStart) { onAction(MenuAction.Reload) }
            HeaderIcon(Glyph.Gear, true) { onAction(MenuAction.Settings) }
        }
        Box(Modifier.fillMaxWidth().height(1.r).background(C.Divider))
        Column(Modifier.heightIn(max = 1180.r).verticalScroll(rememberScrollState())) {
            val m: @Composable (Glyph, String, MenuAction, Boolean?, String?) -> Unit = { g, label, a, checked, note ->
                MenuItem(g, label, checked, note) { onAction(a) }
            }
            m(Glyph.Plus, "নতুন ট্যাব", MenuAction.NewTab, null, null)
            m(Glyph.Incognito, if (tab.incognito) "সাধারণ ট্যাবে যান" else "ইনকগনিটো ট্যাব", MenuAction.Incognito, null, null)
            m(Glyph.Download, "ডাউনলোড ম্যানেজারে ফিরুন", MenuAction.ReturnApp, null, null)
            m(Glyph.Save, "ট্যাব মনে রেখে বের হন", MenuAction.RememberExit, null, null)
            m(Glyph.Exit, "ব্রাউজার বন্ধ করুন", MenuAction.Exit, null, null)
            MenuDivider()
            m(Glyph.AdBlock, "বিজ্ঞাপন ব্লক", MenuAction.BlockAds, BrowserSettings.blockAds, if (tab.blocked > 0) "এই পেজে ${tab.blocked}টা ব্লক" else null)
            m(Glyph.Binoculars, "অডিও / ভিডিও ধরুন", MenuAction.Capture, BrowserSettings.captureMedia, if (tab.sniffed.isNotEmpty()) "${tab.sniffed.size}টা পাওয়া গেছে" else null)
            m(Glyph.Layers, "পেজের রিসোর্স", MenuAction.Resources, null, null)
            m(Glyph.Grid, "অ্যাপে খুলুন", MenuAction.OpenInApp, null, null)
            m(Glyph.Qr, "QR কোড স্ক্যান", MenuAction.ScanQr, null, null)
            m(Glyph.Cookie, "কুকি ইমপোর্ট", MenuAction.ImportCookies, null, null)
            m(Glyph.External, "অন্য ব্রাউজারে লগইন", MenuAction.LoginExternal, null, null)
            m(Glyph.Copy, "ট্যাব ক্লোন", MenuAction.Clone, null, null)
            m(Glyph.Magnet, "গ্র্যাবার (সব ছবি, ভিডিও, লিংক)", MenuAction.Grabber, null, null)
            m(Glyph.History, "হিস্ট্রি", MenuAction.History, null, null)
            m(Glyph.Clipboard, "ক্লিপবোর্ডের সব লিংক খুলুন", MenuAction.ClipboardLinks, null, null)
            m(Glyph.Trash, "ব্রাউজিং ডেটা মুছুন", MenuAction.ClearData, null, null)
            MenuDivider()
            m(Glyph.Code, "পেজের সোর্স", MenuAction.ViewSource, null, null)
            m(Glyph.External, "অন্য অ্যাপে সোর্স দেখুন", MenuAction.ViewSourceWith, null, null)
            m(Glyph.Printer, "প্রিন্ট / PDF", MenuAction.Print, null, null)
            m(Glyph.Save, "পেজ সেভ (অফলাইন)", MenuAction.SavePage, null, null)
            m(Glyph.Folder, "সেভ করা পেজ", MenuAction.SavedPages, null, null)
            m(Glyph.Search, "পেজে খুঁজুন", MenuAction.Find, null, null)
            m(Glyph.Link, "লিংক কপি", MenuAction.CopyLink, null, null)
            m(Glyph.Copy, "সব ট্যাবের লিংক কপি", MenuAction.CopyAllLinks, null, null)
            m(Glyph.AddBox, "হোম স্ক্রিনে যোগ করুন", MenuAction.AddHome, null, null)
            m(Glyph.Bookmark, "বুকমার্ক", MenuAction.Bookmarks, null, null)
            m(Glyph.Share, "লিংক শেয়ার", MenuAction.Share, null, null)
            m(Glyph.Globe, "অন্য ব্রাউজারে খুলুন", MenuAction.ShareBrowser, null, null)
            MenuDivider()
            m(Glyph.Moon, "ডার্ক মোড", MenuAction.Dark, BrowserSettings.darkPages, null)
            m(Glyph.Desktop, "ডেস্কটপ সাইট", MenuAction.Desktop, tab.desktop, null)
            m(Glyph.Code, "JavaScript চালু", MenuAction.JavaScript, BrowserSettings.javascript, null)
            m(Glyph.BookmarkAdd, "বুকমার্ক যোগ করুন", MenuAction.AddBookmark, null, null)
            m(Glyph.Layers, "সব ট্যাব বুকমার্ক", MenuAction.BookmarkAll, null, null)
            m(Glyph.Book, "রিডার মোড", MenuAction.Reader, null, null)
            m(Glyph.Filter, "অ্যাডব্লক হোস্ট ও ফিল্টার", MenuAction.Adblock, null, null)
            m(Glyph.Sliders, "সাইট সেটিংস তালিকা", MenuAction.Sites, null, null)
            m(Glyph.Gear, "সেটিংস", MenuAction.Settings, null, null)
            m(Glyph.Info, "সম্পর্কে", MenuAction.About, null, null)
        }
    }
}

@Composable
private fun HeaderIcon(glyph: Glyph, enabled: Boolean, tint: Color? = null, onClick: () -> Unit) {
    GlyphIcon(
        glyph,
        tint ?: if (enabled) C.Ink else C.Hint.copy(alpha = 0.5f),
        Modifier.size(64.r).clip(CircleShape).clickable(enabled = enabled, onClick = onClick).padding(18.r),
        strokeWidth = 2.1f,
    )
}

@Composable
private fun MenuDivider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.r).height(1.r).background(C.Divider))
}

@Composable
private fun MenuItem(glyph: Glyph, label: String, checked: Boolean?, note: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 26.r, vertical = 15.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, C.NavIcon, Modifier.size(28.r), strokeWidth = 1.9f)
        Spacer(Modifier.width(22.r))
        Column(Modifier.weight(1f)) {
            RText(label, 20, FontWeight.Normal, C.Ink)
            if (note != null) RText(note, 15, FontWeight.Normal, Dl.GreenText)
        }
        if (checked != null) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(checkedColor = Dl.Green, uncheckedColor = C.HabOpen),
            )
        }
    }
}

// ==================================================================== tabs

@Composable
internal fun TabsSheet(onDismiss: () -> Unit) {
    var incognito by remember { mutableStateOf(Browser.current?.incognito ?: false) }
    val list = Browser.tabs.filter { it.incognito == incognito }
    BSheet(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.r, vertical = 6.r), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(18.r)).background(Dl.Soft).padding(5.r),
            ) {
                listOf(false to "ট্যাব", true to "ইনকগনিটো").forEach { (inc, label) ->
                    val on = inc == incognito
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.r))
                            .background(if (on) Color.White else Color.Transparent)
                            .clickable { incognito = inc }
                            .padding(vertical = 12.r),
                        contentAlignment = Alignment.Center,
                    ) {
                        val n = Browser.tabs.count { it.incognito == inc }
                        RText("$label ($n)", 18, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) C.Ink else Dl.Sub)
                    }
                }
            }
        }
        Column(Modifier.heightIn(max = 900.r).verticalScroll(rememberScrollState()).padding(horizontal = 18.r)) {
            if (list.isEmpty()) {
                RText(
                    if (incognito) "কোনো ইনকগনিটো ট্যাব নেই" else "কোনো ট্যাব নেই",
                    18,
                    FontWeight.Normal,
                    Dl.Hint,
                    Modifier.fillMaxWidth().padding(vertical = 30.r),
                    align = TextAlign.Center,
                )
            }
            list.forEach { t -> TabRow(t, onPick = { Browser.select(t); onDismiss() }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.r, vertical = 10.r), horizontalArrangement = Arrangement.spacedBy(12.r)) {
            GreenButton(
                if (incognito) "নতুন ইনকগনিটো" else "নতুন ট্যাব",
                Modifier.weight(1f),
                glyph = Glyph.Plus,
            ) {
                Browser.newTab(null, incognito)
                onDismiss()
            }
            if (list.isNotEmpty()) {
                LightButton("সব বন্ধ", Glyph.Close, Modifier.weight(0.8f)) {
                    if (incognito) Browser.closeAll(incognitoOnly = true) else list.forEach { Browser.close(it) }
                }
            }
        }
    }
}

@Composable
private fun TabRow(t: BrowserTab, onPick: () -> Unit) {
    val current = t.id == Browser.currentId
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.r)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.r)
            .clip(shape)
            .background(if (current) Color(0xFFEFFAF4) else Color.White)
            .border(if (current) 2.r else 1.r, if (current) Dl.Green else Dl.Line, shape)
            .clickable(onClick = onPick)
            .padding(horizontal = 16.r, vertical = 14.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = t.favicon
        Box(Modifier.size(40.r).clip(RoundedCornerShape(10.r)).background(Dl.Soft), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(icon.asImageBitmap(), null, Modifier.size(28.r))
            } else {
                GlyphIcon(if (t.incognito) Glyph.Incognito else Glyph.Globe, C.NavIcon, Modifier.size(24.r), strokeWidth = 1.9f)
            }
        }
        Spacer(Modifier.width(14.r))
        Column(Modifier.weight(1f)) {
            RText(if (t.onStart) "নতুন ট্যাব" else t.title.ifBlank { Search.displayHost(t.url) }, 18, FontWeight.Medium, C.Ink)
            if (!t.onStart) RText(Search.displayHost(t.url), 15, FontWeight.Normal, Dl.Hint)
        }
        if (BrowserSettings.tabReorder) {
            GlyphIcon(
                Glyph.Dots,
                Dl.Sub,
                Modifier.size(44.r).clip(CircleShape).clickable { menu = true }.padding(10.r),
            )
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { RText("ওপরে সরান", 18, FontWeight.Normal, C.Ink) },
                    onClick = { menu = false; Browser.move(t, -1) },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { RText("নিচে সরান", 18, FontWeight.Normal, C.Ink) },
                    onClick = { menu = false; Browser.move(t, 1) },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { RText("ক্লোন", 18, FontWeight.Normal, C.Ink) },
                    onClick = { menu = false; Browser.clone(t) },
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { RText("বাকি সব বন্ধ", 18, FontWeight.Normal, C.Ink) },
                    onClick = {
                        menu = false
                        Browser.tabs.filter { it.incognito == t.incognito && it.id != t.id }.forEach { Browser.close(it) }
                    },
                )
            }
        }
        GlyphIcon(
            Glyph.Close,
            Dl.Sub,
            Modifier.size(44.r).clip(CircleShape).clickable { Browser.close(t) }.padding(11.r),
            strokeWidth = 2.1f,
        )
    }
}

// ==================================================================== captured media

@Composable
internal fun MediaSheet(tab: BrowserTab, onDismiss: () -> Unit, onAnalyze: (String) -> Unit) {
    val context = LocalContext.current
    BSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 26.r)) {
            RText("এই পেজ থেকে ডাউনলোড", 26, FontWeight.Bold, Dl.Ink)
            Spacer(Modifier.height(4.r))
            RText(Search.displayHost(tab.url), 18, FontWeight.Normal, Dl.Sub)
            Spacer(Modifier.height(16.r))
            DlCard(accent = Dl.brand, onClick = { onAnalyze(tab.url) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradIcon(Glyph.Bolt, Dl.brand, size = 62)
                    Spacer(Modifier.width(18.r))
                    Column(Modifier.weight(1f)) {
                        RText("সব ফরম্যাট দেখুন", 22, FontWeight.Bold, Dl.Ink)
                        Spacer(Modifier.height(2.r))
                        RText("ভিডিওর সব কোয়ালিটি, অডিও (MP3) আর ছবি", 17, FontWeight.Normal, Dl.Sub, maxLines = 2)
                    }
                    GlyphIcon(Glyph.ChevronRight, Dl.Sub, Modifier.size(26.r), strokeWidth = 2.2f)
                }
            }
            Spacer(Modifier.height(20.r))
            RText("পেজে চলা মিডিয়া · ${tab.sniffed.size}", 19, FontWeight.SemiBold, Dl.Sub)
            Spacer(Modifier.height(10.r))
        }
        Column(
            Modifier.heightIn(max = 640.r).verticalScroll(rememberScrollState()).padding(horizontal = 26.r),
            verticalArrangement = Arrangement.spacedBy(10.r),
        ) {
            tab.sniffed.toList().forEach { item ->
                SniffRow(item) {
                    if (item.stream) {
                        onAnalyze(item.url)
                    } else {
                        val name = Browser.nameFor(item)
                        val o = FileOffer(item.url, name, Analyzer.mimeForName(name, if (item.kind == Kind.Audio) "audio/mpeg" else "video/mp4"), 0, tab.url, tab.id)
                        onDismiss()
                        if (BrowserSettings.skipEditorCaptured) Browser.startDownload(context, o) else Browser.offer = o
                    }
                }
            }
        }
    }
}

@Composable
private fun SniffRow(item: Sniffed, onClick: () -> Unit) {
    val colors = Dl.kindColors(item.kind)
    val shape = RoundedCornerShape(18.r)
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
        GradIcon(Dl.kindGlyph(item.kind), colors, size = 52)
        Spacer(Modifier.width(16.r))
        Column(Modifier.weight(1f)) {
            RText(
                (if (item.stream) "স্ট্রিম" else item.kind.bn) + " · " + item.ext.uppercase(Locale.US),
                19,
                FontWeight.SemiBold,
                Dl.Ink,
            )
            Spacer(Modifier.height(2.r))
            val sub = if (BrowserSettings.showTitleInCaptured && item.pageTitle.isNotBlank()) item.pageTitle
            else item.host + " · " + (Uri.parse(item.url).lastPathSegment ?: "").takeLast(28)
            RText(sub, 15, FontWeight.Normal, Dl.Hint)
        }
        Spacer(Modifier.width(10.r))
        Tag(if (item.stream) "দেখুন" else "নামান", colors, filled = true)
    }
}

// ==================================================================== grabber

@Composable
internal fun GrabberSheet(tab: BrowserTab, items: List<GrabItem>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val types = listOf("img" to "ছবি", "video" to "ভিডিও", "audio" to "অডিও", "link" to "লিংক")
    var type by remember { mutableStateOf(types.firstOrNull { t -> items.any { it.type == t.first } }?.first ?: "img") }
    val picked = remember { mutableStateListOf<String>() }
    val shown = items.filter { it.type == type }
    BSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 24.r)) {
            RText("গ্র্যাবার", 26, FontWeight.Bold, C.Ink)
            RText("পেজের সব ছবি, ভিডিও, অডিও আর লিংক", 17, FontWeight.Normal, Dl.Sub)
            Spacer(Modifier.height(14.r))
            Row(horizontalArrangement = Arrangement.spacedBy(10.r)) {
                types.forEach { (k, label) ->
                    val n = items.count { it.type == k }
                    com.nirvor.app.ui.Pill("$label $n", k == type) {
                        type = k
                        picked.clear()
                    }
                }
            }
            Spacer(Modifier.height(10.r))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("${picked.size}টা বাছা", 17, FontWeight.Medium, Dl.Sub, Modifier.weight(1f))
                RText(
                    if (picked.size == shown.size && shown.isNotEmpty()) "কিছুই না" else "সব বাছুন",
                    17,
                    FontWeight.SemiBold,
                    Dl.GreenText,
                    Modifier.clip(RoundedCornerShape(10.r)).clickable {
                        if (picked.size == shown.size) picked.clear() else {
                            picked.clear()
                            picked.addAll(shown.map { it.url })
                        }
                    }.padding(8.r),
                )
            }
        }
        Column(Modifier.heightIn(max = 760.r).verticalScroll(rememberScrollState()).padding(horizontal = 18.r)) {
            shown.forEach { g ->
                val on = g.url in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.r))
                        .clickable { if (on) picked.remove(g.url) else picked.add(g.url) }
                        .padding(horizontal = 8.r, vertical = 8.r),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = on,
                        onCheckedChange = { if (on) picked.remove(g.url) else picked.add(g.url) },
                        colors = CheckboxDefaults.colors(checkedColor = Dl.Green, uncheckedColor = C.HabOpen),
                    )
                    if (g.type == "img") {
                        AsyncImage(
                            model = g.url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.r).clip(RoundedCornerShape(10.r)).background(Dl.Soft),
                        )
                        Spacer(Modifier.width(12.r))
                    }
                    Column(Modifier.weight(1f)) {
                        RText(g.name.ifBlank { Uri.parse(g.url).lastPathSegment ?: g.url }, 17, FontWeight.Medium, C.Ink)
                        RText(g.url, 14, FontWeight.Normal, Dl.Hint)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.r, vertical = 10.r), horizontalArrangement = Arrangement.spacedBy(12.r)) {
            GreenButton("ডাউনলোড (${picked.size})", Modifier.weight(1f), glyph = Glyph.Download, enabled = picked.isNotEmpty()) {
                picked.toList().forEach { u ->
                    val g = items.firstOrNull { it.url == u } ?: return@forEach
                    val base = Browser.offerFor(tab, u, null)
                    val name = if (g.type == "img" && !base.name.contains('.')) base.name + ".jpg" else base.name
                    Browser.startDownload(context, base.copy(name = name, mime = Analyzer.mimeForName(name, base.mime)))
                }
                onDismiss()
            }
            LightButton("কপি", Glyph.Copy, Modifier.weight(0.6f)) {
                if (picked.isNotEmpty()) BrowserActions.copy(context, "links", picked.joinToString("\n"))
            }
        }
    }
}

// ==================================================================== long press

@Composable
internal fun ContextSheet(target: ContextTarget, onDismiss: () -> Unit, onAnalyze: (String) -> Unit) {
    val context = LocalContext.current
    val tab = Browser.tabs.firstOrNull { it.id == target.tabId }
    BSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 24.r)) {
            if (target.image != null) {
                AsyncImage(
                    model = target.image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.r).clip(RoundedCornerShape(16.r)).background(Dl.Soft),
                )
                Spacer(Modifier.height(10.r))
            }
            RText(target.link ?: target.image ?: "", 15, FontWeight.Normal, Dl.Hint, maxLines = 2)
            Spacer(Modifier.height(6.r))
        }
        val item: @Composable (Glyph, String, () -> Unit) -> Unit = { g, label, action ->
            MenuItem(g, label, null, null) {
                onDismiss()
                action()
            }
        }
        val link = target.link
        val image = target.image
        if (link != null) {
            item(Glyph.Plus, "নতুন ট্যাবে খুলুন") { Browser.newTab(link, tab?.incognito ?: false) }
            item(Glyph.Layers, "পেছনে নতুন ট্যাবে খুলুন") { Browser.newTab(link, tab?.incognito ?: false, background = true) }
            item(Glyph.Incognito, "ইনকগনিটো ট্যাবে খুলুন") { Browser.newTab(link, true) }
            item(Glyph.Download, "লিংকটা ডাউনলোড করুন") { onAnalyze(link) }
            item(Glyph.Link, "লিংক কপি") { BrowserActions.copy(context, "link", link) }
            item(Glyph.Share, "লিংক শেয়ার") { BrowserActions.shareLink(context, link, null) }
        }
        if (image != null) {
            item(Glyph.Image, "ছবি ডাউনলোড") { if (tab != null) Browser.downloadImage(context, tab, image) }
            item(Glyph.Eye, "ছবি নতুন ট্যাবে দেখুন") { Browser.newTab(image, tab?.incognito ?: false) }
            item(Glyph.Search, "ছবি দিয়ে সার্চ") { Browser.newTab(Search.imageSearchUrl(image), tab?.incognito ?: false) }
            item(Glyph.Copy, "ছবির লিংক কপি") { BrowserActions.copy(context, "image", image) }
            item(Glyph.Share, "ছবির লিংক শেয়ার") { BrowserActions.shareLink(context, image, null) }
        }
    }
}

// ==================================================================== download editor

/** Shows what will be downloaded; the file name can be changed first. */
@Composable
internal fun DownloadEditor(o: FileOffer, onDismiss: () -> Unit, onStart: (FileOffer) -> Unit) {
    var name by remember(o) { mutableStateOf(o.name) }
    val kind = Analyzer.kindForMime(o.mime)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        icon = { GradIcon(Dl.kindGlyph(kind), Dl.kindColors(kind), size = 72) },
        title = { RText("ফাইল ডাউনলোড", 25, FontWeight.Bold, C.Ink, Modifier.fillMaxWidth(), align = TextAlign.Center) },
        text = {
            Column {
                RText("ফাইলের নাম", 17, FontWeight.Medium, C.StatLabel)
                Spacer(Modifier.height(8.r))
                InputBox(name, { name = it }, "file name", KeyboardType.Text, lines = 2)
                Spacer(Modifier.height(12.r))
                RText(
                    listOf(
                        name.substringAfterLast('.', "").uppercase(Locale.US),
                        Analyzer.sizeText(o.size),
                        Analyzer.hostOf(o.url) ?: "",
                    ).filter { it.isNotEmpty() }.joinToString(" · "),
                    16,
                    FontWeight.Normal,
                    Dl.Sub,
                )
                Spacer(Modifier.height(4.r))
                RText(o.url, 14, FontWeight.Normal, Dl.Hint, maxLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = Analyzer.safeName(name).ifBlank { o.name }
                onStart(o.copy(name = clean, mime = Analyzer.mimeForName(clean, o.mime)))
            }) { RText("ডাউনলোড", 20, FontWeight.Bold, Dl.GreenText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { RText("বাতিল", 19, FontWeight.Medium, Dl.Sub) } },
    )
}

// ==================================================================== bookmarks

@Composable
internal fun BookmarkDialog(b: BookmarkEntity, onDismiss: () -> Unit, onSave: (BookmarkEntity) -> Unit) {
    var title by remember(b) { mutableStateOf(b.title) }
    var url by remember(b) { mutableStateOf(b.url) }
    var folder by remember(b) { mutableStateOf(b.folder) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { RText(if (b.id == 0L) "বুকমার্ক যোগ" else "বুকমার্ক এডিট", 24, FontWeight.Bold, C.Ink) },
        text = {
            Column {
                RText("নাম", 16, FontWeight.Medium, C.StatLabel)
                Spacer(Modifier.height(6.r))
                InputBox(title, { title = it }, "নাম")
                Spacer(Modifier.height(12.r))
                RText("লিংক", 16, FontWeight.Medium, C.StatLabel)
                Spacer(Modifier.height(6.r))
                InputBox(url, { url = it }, "https://…", KeyboardType.Uri)
                Spacer(Modifier.height(12.r))
                RText("ফোল্ডার (ঐচ্ছিক)", 16, FontWeight.Medium, C.StatLabel)
                Spacer(Modifier.height(6.r))
                InputBox(folder, { folder = it }, "যেমন: ভিডিও সাইট")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (url.isNotBlank()) onSave(b.copy(title = title.trim(), url = url.trim(), folder = folder.trim()))
            }) { RText("সেভ", 19, FontWeight.Bold, Dl.GreenText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { RText("বাতিল", 19, FontWeight.Medium, Dl.Sub) } },
    )
}

// ==================================================================== clear browsing data

@Composable
internal fun ClearDataSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val choice = remember { BrowserActions.ClearChoice() }
    var history by remember { mutableStateOf(choice.history) }
    var cookies by remember { mutableStateOf(choice.cookies) }
    var cache by remember { mutableStateOf(choice.cache) }
    var storage by remember { mutableStateOf(choice.storage) }
    var perms by remember { mutableStateOf(choice.permissions) }
    BSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 24.r)) {
            RText("ব্রাউজিং ডেটা মুছুন", 25, FontWeight.Bold, C.Ink)
            Spacer(Modifier.height(10.r))
            CheckLine("হিস্ট্রি", "দেখা পেজের তালিকা", history) { history = it }
            CheckLine("কুকি", "সব সাইট থেকে লগআউট হয়ে যাবেন", cookies) { cookies = it }
            CheckLine("ক্যাশ", "জমে থাকা ছবি ও ফাইল", cache) { cache = it }
            CheckLine("সাইটের ডেটা", "লোকাল স্টোরেজ, ডেটাবেস", storage) { storage = it }
            CheckLine("লোকেশন অনুমতি", "সাইটগুলোকে দেওয়া অনুমতি", perms) { perms = it }
            Spacer(Modifier.height(14.r))
            GreenButton("মুছে ফেলুন", Modifier.fillMaxWidth(), glyph = Glyph.Trash, colors = Dl.red) {
                scope.launch {
                    BrowserActions.clearData(context, BrowserActions.ClearChoice(history, cookies, cache, storage, perms))
                    Toast.makeText(context, "মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun CheckLine(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.r)).clickable { onChange(!checked) }.padding(vertical = 6.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(checkedColor = Dl.Green, uncheckedColor = C.HabOpen),
        )
        Spacer(Modifier.width(6.r))
        Column {
            RText(title, 19, FontWeight.Medium, C.Ink)
            RText(sub, 15, FontWeight.Normal, Dl.Sub)
        }
    }
}

// ==================================================================== import cookies

@Composable
internal fun CookiesSheet(currentUrl: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var site by remember { mutableStateOf(currentUrl?.let { Search.displayHost(it) }.orEmpty()) }
    var text by remember { mutableStateOf("") }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val content = try {
                context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }.orEmpty()
            } catch (e: Exception) {
                ""
            }
            val n = BrowserActions.importCookies(content, site)
            Toast.makeText(context, if (n > 0) "${n}টা কুকি যোগ হয়েছে" else "ফাইলে কুকি পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
            if (n > 0) onDismiss()
        }
    }
    BSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 24.r)) {
            RText("কুকি ইমপোর্ট", 25, FontWeight.Bold, C.Ink)
            Spacer(Modifier.height(4.r))
            RText(
                "কম্পিউটারের ব্রাউজার থেকে নেওয়া cookies.txt ফাইল দিন, অথবা কোনো সাইটের কুকি লিখে দিন। লগইন লাগে এমন ভিডিও নামাতে কাজে লাগে।",
                16,
                FontWeight.Normal,
                Dl.Sub,
                maxLines = 4,
                lineHeight = 23,
            )
            Spacer(Modifier.height(14.r))
            LightButton("cookies.txt ফাইল বাছুন", Glyph.FileDoc, Modifier.fillMaxWidth()) {
                fileLauncher.launch(arrayOf("text/*", "application/octet-stream"))
            }
            Spacer(Modifier.height(16.r))
            RText("সাইট", 16, FontWeight.Medium, C.StatLabel)
            Spacer(Modifier.height(6.r))
            InputBox(site, { site = it }, "যেমন: instagram.com", KeyboardType.Uri)
            Spacer(Modifier.height(10.r))
            RText("কুকি", 16, FontWeight.Medium, C.StatLabel)
            Spacer(Modifier.height(6.r))
            InputBox(text, { text = it }, "name=value; name2=value2", lines = 5)
            Spacer(Modifier.height(14.r))
            GreenButton("যোগ করুন", Modifier.fillMaxWidth(), glyph = Glyph.Cookie, enabled = text.isNotBlank() && site.isNotBlank()) {
                val n = BrowserActions.importCookies(text, site)
                Toast.makeText(context, if (n > 0) "${n}টা কুকি যোগ হয়েছে" else "কুকি পড়া গেল না", Toast.LENGTH_SHORT).show()
                if (n > 0) onDismiss()
            }
        }
    }
}

