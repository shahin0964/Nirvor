package com.nirvor.app.ui.browser

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import com.nirvor.app.browser.Browser
import com.nirvor.app.browser.BrowserActions
import com.nirvor.app.browser.BrowserDatabase
import com.nirvor.app.browser.BrowserSettings
import com.nirvor.app.browser.Search
import com.nirvor.app.ui.icons.Glyph
import kotlinx.coroutines.launch

private val askAllowBlock = listOf("ask" to "প্রতিবার জিজ্ঞেস করবে", "allow" to "সবসময় অনুমতি", "block" to "ব্লক")

/** Browser → Settings (main list). */
@Composable
internal fun BrowserSettingsPage(onBack: () -> Unit, onPush: (BSub) -> Unit) {
    SubPage("Browser settings", "ব্রাউজারের সেটিংস", onBack) {
        item {
            Group {
                ActionRow("সাধারণ", "সার্চ ইঞ্জিন, হোম পেজ, ইউজার এজেন্ট, ডাউনলোড এডিটর", Glyph.Info) { onPush(BSub.Section("general")) }
                GroupDivider()
                ActionRow("বুকমার্ক", "ফোল্ডার, ব্যাকআপ, ইমপোর্ট", Glyph.Bookmark) { onPush(BSub.Section("bookmark")) }
                GroupDivider()
                ActionRow("দেখানো", "ডার্ক মোড, লেখার সাইজ, ফুল-স্ক্রিন, জুম", Glyph.Desktop) { onPush(BSub.Section("display")) }
                GroupDivider()
                ActionRow("প্রাইভেসি", "কুকি, ট্র্যাকিং, বের হলে ডেটা মোছা", Glyph.Lock) { onPush(BSub.Section("privacy")) }
                GroupDivider()
                ActionRow("ওয়েবসাইটের অনুমতি", "ক্যামেরা, মাইক্রোফোন, লোকেশন, পপ-আপ", Glyph.Shield) { onPush(BSub.Section("permissions")) }
                GroupDivider()
                ActionRow("সাইট সেটিংস তালিকা", "প্রতিটা সাইটের আলাদা সেটিংস", Glyph.Sliders) { onPush(BSub.Sites) }
                GroupDivider()
                ActionRow("সহজ ব্যবহার", "অ্যাড্রেস বার নিচে, সোয়াইপ, শর্টকাট", Glyph.Person) { onPush(BSub.Section("accessibility")) }
                GroupDivider()
                ActionRow("অ্যাডভান্সড", "পপ-আপ, ট্যাব, কুকি, রেন্ডারিং, ফাইলের নাম", Glyph.Engine) { onPush(BSub.Section("advanced")) }
                GroupDivider()
                ActionRow("অ্যাডব্লক হোস্ট ও ফিল্টার", null, Glyph.Filter) { onPush(BSub.Adblock) }
                GroupDivider()
                ActionRow("প্রশ্ন ও উত্তর (FAQ)", null, Glyph.Info) { onPush(BSub.Faq) }
            }
        }
    }
}

/** One settings section. Every change applies to open tabs right away. */
@Composable
internal fun SettingsSection(key: String, onBack: () -> Unit, onPush: (BSub) -> Unit) {
    val s = BrowserSettings
    fun apply() = Browser.applySettingsToAll()
    when (key) {
        "general" -> SubPage("General", "সাধারণ", onBack) {
            item { GroupLabel("সার্চ") }
            item {
                Group {
                    ChoiceRow("সার্চ ইঞ্জিন", Search.engines.map { it.key to it.name }, s.searchEngine) { s.searchEngine = it }
                    GroupDivider()
                    SwitchRow("সার্চের পরামর্শ", "লেখার সময় Google-এর পরামর্শ দেখাবে", s.suggestions) { s.suggestions = it }
                    GroupDivider()
                    ChoiceRow("ইনকগনিটোর সার্চ ইঞ্জিন", Search.engines.map { it.key to it.name }, s.searchEngineIncognito) { s.searchEngineIncognito = it }
                    GroupDivider()
                    SwitchRow("ইনকগনিটোতে সার্চের পরামর্শ", null, s.suggestionsIncognito) { s.suggestionsIncognito = it }
                    GroupDivider()
                    ChoiceRow("ছবি দিয়ে সার্চ", Search.imageEngines, s.imageSearch) { s.imageSearch = it }
                }
            }
            item { GroupLabel("পেজ") }
            item {
                Group {
                    TextRow("হোম পেজ", s.homepage, "https://… (খালি রাখলে Nirvor-এর শুরুর পেজ)", "Nirvor-এর শুরুর পেজ", KeyboardType.Uri) { s.homepage = it }
                    GroupDivider()
                    ChoiceRow(
                        "ইউজার এজেন্ট",
                        listOf("default" to "সাধারণ (এই ফোন)", "android" to "Android Chrome", "iphone" to "iPhone Safari", "desktop" to "ডেস্কটপ Chrome", "custom" to "নিজে লিখুন"),
                        s.userAgentMode,
                    ) {
                        s.userAgentMode = it
                        apply()
                    }
                    if (s.userAgentMode == "custom") {
                        GroupDivider()
                        TextRow("নিজের ইউজার এজেন্ট", s.customUserAgent, "Mozilla/5.0 …", "লেখা হয়নি") {
                            s.customUserAgent = it
                            apply()
                        }
                    }
                    GroupDivider()
                    SwitchRow("ডেস্কটপ সাইট সব ট্যাবে মনে রাখুন", "চালু করলে নতুন ট্যাবেও ডেস্কটপ সাইট খুলবে", s.desktopPersistent) { s.desktopPersistent = it }
                    GroupDivider()
                    SwitchRow("টেনে রিফ্রেশ", "পেজ ওপর থেকে নিচে টানলে রিলোড হবে", s.pullToRefresh) {
                        s.pullToRefresh = it
                        apply()
                    }
                    GroupDivider()
                    SwitchRow("অ্যাড্রেস বারে চাপলে পুরো লিংক বাছাই", null, s.selectUrlOnClick) { s.selectUrlOnClick = it }
                    GroupDivider()
                    SwitchRow("JavaScript চালু", null, s.javascript) {
                        s.javascript = it
                        apply()
                    }
                    GroupDivider()
                    SwitchRow("ছবি ব্লক", "ডেটা বাঁচাতে পেজের ছবি লোড হবে না", s.blockImages) {
                        s.blockImages = it
                        apply()
                    }
                    GroupDivider()
                    SwitchRow("ট্যাব সাজানো যাবে", "ট্যাব তালিকায় ওপরে / নিচে সরানো", s.tabReorder) { s.tabReorder = it }
                }
            }
            item { GroupLabel("ভিডিও ও ডাউনলোড") }
            item {
                Group {
                    SwitchRow("ফুল-স্ক্রিন ভিডিও আড়াআড়ি", null, s.landscapeFullscreen) { s.landscapeFullscreen = it }
                    GroupDivider()
                    SwitchRow("ডাউনলোড বাটন ভাসমান", "নিচের বারের বদলে ডান কোণে ভাসবে", s.floatingDownload) { s.floatingDownload = it }
                    GroupDivider()
                    SwitchRow("ধরা মিডিয়া চাপলে সরাসরি ডাউনলোড", "ফাইলের নাম বদলানোর ধাপ বাদ", s.skipEditorCaptured) { s.skipEditorCaptured = it }
                    GroupDivider()
                    SwitchRow("সাইটের ডাউনলোড লিংকে সরাসরি ডাউনলোড", "ফাইলের নাম বদলানোর ধাপ বাদ", s.skipEditorPageDownload) { s.skipEditorPageDownload = it }
                    GroupDivider()
                    SwitchRow("ছবিতে লম্বা চাপে সরাসরি ডাউনলোড", null, s.skipEditorImage) { s.skipEditorImage = it }
                    GroupDivider()
                    SwitchRow("ছবির নাম সার্ভার থেকে নিন", "বন্ধ থাকলে পেজের নাম দিয়ে নাম হবে", s.serverNameForImages) { s.serverNameForImages = it }
                }
            }
        }

        "bookmark" -> BookmarkSettings(onBack)

        "display" -> SubPage("Display", "দেখানো", onBack) {
            item {
                Group {
                    ChoiceRow(
                        "পেজের ডার্ক মোড",
                        listOf("off" to "বন্ধ", "on" to "সবসময় চালু", "theme" to "ফোনের ডার্ক মোড অনুযায়ী"),
                        s.darkModeBrowser,
                    ) {
                        s.darkModeBrowser = it
                        Browser.refreshDisplay()
                    }
                    GroupDivider()
                    SliderRow("লেখার সাইজ", s.textSize, 50..200, 10, "%") {
                        s.textSize = it
                        apply()
                    }
                    GroupDivider()
                    SwitchRow("ফুল-স্ক্রিন মোড", "স্ট্যাটাস বার আর নেভিগেশন বার লুকানো থাকবে", s.fullScreen) { s.fullScreen = it }
                    GroupDivider()
                    SwitchRow("ব্রাউজ করার সময় স্ট্যাটাস বার লুকান", null, s.hideStatusBar) { s.hideStatusBar = it }
                    GroupDivider()
                    SwitchRow("ট্যাব নম্বর আর মোট সংখ্যা দেখান", "যেমন 2/5", s.showTabTotal) { s.showTabTotal = it }
                    GroupDivider()
                    SwitchRow("জুম বন্ধ রাখা পেজেও জুম", null, s.forceZoom) { s.forceZoom = it }
                    GroupDivider()
                    SwitchRow("চওড়া ভিউপোর্ট", "প্রস্তাবিত", s.wideViewport) {
                        s.wideViewport = it
                        apply()
                    }
                    GroupDivider()
                    SwitchRow("পুরো পেজ দেখিয়ে খোলা (overview)", "প্রস্তাবিত", s.overviewMode) {
                        s.overviewMode = it
                        apply()
                    }
                    GroupDivider()
                    SwitchRow("লেখা স্ক্রিনে মানিয়ে নেওয়া (text reflow)", null, s.textReflow) {
                        s.textReflow = it
                        apply()
                    }
                }
            }
        }

        "privacy" -> PrivacySettings(onBack)

        "permissions" -> SubPage("Website permissions", "ওয়েবসাইটের অনুমতি", onBack) {
            item { GroupLabel("সাধারণ ট্যাব") }
            item {
                Group {
                    ChoiceRow("ক্যামেরা", askAllowBlock, s.permCamera) { s.permCamera = it }
                    GroupDivider()
                    ChoiceRow("মাইক্রোফোন", askAllowBlock, s.permMic) { s.permMic = it }
                    GroupDivider()
                    ChoiceRow("লোকেশন", askAllowBlock, s.permLocation) { s.permLocation = it }
                }
            }
            item { GroupLabel("ইনকগনিটো ট্যাব") }
            item {
                Group {
                    ChoiceRow("ক্যামেরা", askAllowBlock, s.permCameraIncognito) { s.permCameraIncognito = it }
                    GroupDivider()
                    ChoiceRow("মাইক্রোফোন", askAllowBlock, s.permMicIncognito) { s.permMicIncognito = it }
                    GroupDivider()
                    ChoiceRow("লোকেশন", askAllowBlock, s.permLocationIncognito) { s.permLocationIncognito = it }
                }
            }
            item { GroupLabel("অন্যান্য") }
            item {
                Group {
                    SwitchRow("সাইট নতুন উইন্ডো খুলতে পারবে", "প্রস্তাবিত (target=_blank লিংক)", s.allowNewWindows) {
                        s.allowNewWindows = it
                        apply()
                    }
                    GroupDivider()
                    ChoiceRow("অন্য অ্যাপ খোলা (YouTube, WhatsApp…)", askAllowBlock, s.externalApps) { s.externalApps = it }
                    GroupDivider()
                    ChoiceRow("সুরক্ষিত (DRM) ভিডিও", askAllowBlock, s.drm) { s.drm = it }
                }
            }
        }

        "accessibility" -> AccessibilitySettings(onBack)

        "advanced" -> SubPage("Advanced", "অ্যাডভান্সড", onBack) {
            item { GroupLabel("ধরা ফাইল") }
            item {
                Group {
                    TextRow(
                        "আরও ধরন ধরুন",
                        s.extraCaptureTypes,
                        "যেমন: pdf, zip, apk",
                        "শুধু ভিডিও আর অডিও",
                    ) { s.extraCaptureTypes = it }
                    GroupDivider()
                    SwitchRow("পেজের নাম দিয়ে ফাইলের নাম", "ধরা ভিডিও / অডিওর নাম পেজের শিরোনাম হবে", s.titleAsFileName) { s.titleAsFileName = it }
                    GroupDivider()
                    SwitchRow("ধরা তালিকায় পেজের নাম দেখান", null, s.showTitleInCaptured) { s.showTitleInCaptured = it }
                    GroupDivider()
                    SwitchRow("ডাউনলোড শুরু হলে সেই ট্যাব বন্ধ", "শুধু ডাউনলোডের জন্য খোলা নতুন ট্যাব", s.closeTabAfterDownload) { s.closeTabAfterDownload = it }
                }
            }
            item { GroupLabel("পপ-আপ ও ট্যাব") }
            item {
                Group {
                    ChoiceRow(
                        "পপ-আপ",
                        listOf("ask" to "প্রতিবার জিজ্ঞেস করবে", "allow" to "সব খুলবে", "block" to "নিজে থেকে খোলা পপ-আপ ব্লক"),
                        s.popupHandling,
                    ) {
                        s.popupHandling = it
                        apply()
                    }
                    GroupDivider()
                    SwitchRow("পপ-আপ বার্তায় লিংক দেখান", null, s.showPopupUrl) { s.showPopupUrl = it }
                    GroupDivider()
                    SwitchRow("নতুন ট্যাব খোলার আগে জিজ্ঞেস", null, s.askNewTabs) { s.askNewTabs = it }
                    GroupDivider()
                    SwitchRow("নতুন ট্যাব পেছনে খুলুন", null, s.newTabsBackground) { s.newTabsBackground = it }
                    GroupDivider()
                    SwitchRow("নতুন ট্যাব ইনকগনিটো", null, s.incognitoDefault) { s.incognitoDefault = it }
                    GroupDivider()
                    SwitchRow("ট্যাব বন্ধ হলে \"ফিরিয়ে আনুন\" দেখান", null, s.tabClosedUndo) { s.tabClosedUndo = it }
                    GroupDivider()
                    SwitchRow("খোলার সময় আগের ট্যাব ফিরিয়ে আনুন", "ইনকগনিটো ট্যাব ফেরে না", s.restoreTabs) { s.restoreTabs = it }
                    GroupDivider()
                    SwitchRow("ট্যাব বদলালে পেছনের ট্যাব থামান", "ব্যাটারি বাঁচায়", s.pauseTimers) { s.pauseTimers = it }
                }
            }
            item { GroupLabel("পেজ") }
            item {
                Group {
                    SwitchRow("লেখা বাছাই / কপি সব পেজে", "যে পেজ কপি আটকায় সেখানেও", s.forceTextSelection) { s.forceTextSelection = it }
                    GroupDivider()
                    SwitchRow("কুকি চালু", "প্রস্তাবিত (লগইন থাকে)", s.cookies) {
                        s.cookies = it
                        apply()
                    }
                    GroupDivider()
                    SwitchRow("ইনকগনিটোতে কুকি", null, s.cookiesIncognito) {
                        s.cookiesIncognito = it
                        apply()
                    }
                    GroupDivider()
                    SwitchRow("সেফ ব্রাউজিং", "ক্ষতিকর সাইট সম্পর্কে সতর্ক করবে", s.safeBrowsing) {
                        s.safeBrowsing = it
                        apply()
                    }
                    GroupDivider()
                    ChoiceRow(
                        "লেখার এনকোডিং",
                        listOf("UTF-8", "ISO-8859-1", "GBK", "Big5", "ISO-2022-JP", "SHIFT_JIS", "EUC-JP", "EUC-KR", "ISO-8859-9", "Cp1254").map { it to it },
                        s.textEncoding,
                    ) {
                        s.textEncoding = it
                        apply()
                    }
                    GroupDivider()
                    ChoiceRow(
                        "রেন্ডারিং",
                        listOf("normal" to "সাধারণ", "grayscale" to "সাদা-কালো", "invert" to "উল্টো রং", "invert_grayscale" to "উল্টো সাদা-কালো", "high_contrast" to "বেশি কনট্রাস্ট"),
                        s.renderingMode,
                    ) {
                        s.renderingMode = it
                        Browser.refreshDisplay()
                    }
                    GroupDivider()
                    ChoiceRow(
                        "অ্যাড্রেস বারে দেখান",
                        listOf("domain" to "শুধু সাইটের নাম", "url" to "পুরো লিংক", "title" to "পেজের নাম"),
                        s.urlBoxContents,
                    ) { s.urlBoxContents = it }
                }
            }
            item { GroupLabel("পেজ সেভ") }
            item {
                Group {
                    SwitchRow("পেজের নাম দিয়ে সেভ", null, s.titleForSavedPages) { s.titleForSavedPages = it }
                    GroupDivider()
                    SwitchRow("Nirvor/Downloads/General-এও কপি রাখুন", "বন্ধ থাকলে শুধু ব্রাউজারের \"সেভ করা পেজ\"-এ থাকবে", s.savePagesCopy) { s.savePagesCopy = it }
                }
            }
        }

        else -> BrowserSettingsPage(onBack, onPush)
    }
}

@Composable
private fun BookmarkSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = BrowserSettings
    var confirm by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val n = try {
                BrowserActions.exportBookmarks(context, uri)
            } catch (e: Exception) {
                -1
            }
            Toast.makeText(context, if (n >= 0) "${n}টা বুকমার্ক ব্যাকআপ হয়েছে" else "ব্যাকআপ হয়নি", Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = try {
                BrowserActions.importBookmarks(context, uri)
            } catch (e: Exception) {
                -1
            }
            Toast.makeText(context, if (n >= 0) "${n}টা বুকমার্ক যোগ হয়েছে" else "ফাইল পড়া গেল না", Toast.LENGTH_SHORT).show()
        }
    }
    SubPage("Bookmarks", "বুকমার্ক সেটিংস", onBack) {
        item {
            Group {
                SwitchRow("ফোল্ডার আগে দেখান", null, s.bookmarkFoldersFirst) { s.bookmarkFoldersFirst = it }
                GroupDivider()
                SwitchRow("সার্চের পরামর্শে বুকমার্ক", null, s.bookmarksInSuggestions) { s.bookmarksInSuggestions = it }
            }
        }
        item {
            Group {
                ActionRow("বুকমার্ক ব্যাকআপ (ফাইলে)", "JSON ফাইল হিসেবে সেভ", Glyph.Save) { exporter.launch("nirvor-bookmarks.json") }
                GroupDivider()
                ActionRow("ব্যাকআপ থেকে ইমপোর্ট", "Nirvor-এর JSON বা Chrome / Firefox-এর bookmarks.html", Glyph.FileDoc) {
                    importer.launch(arrayOf("application/json", "text/html", "text/*", "application/octet-stream"))
                }
                GroupDivider()
                ActionRow("সব বুকমার্ক মুছুন", null, Glyph.Trash, danger = true) { confirm = true }
            }
        }
    }
    if (confirm) {
        AskDialog("সব বুকমার্ক মুছবেন?", "এটা আর ফেরানো যাবে না।", "মুছুন", "না", danger = true, glyph = Glyph.Trash, onYes = {
            confirm = false
            scope.launch { BrowserDatabase.get(context).dao().clearBookmarks() }
        }, onNo = { confirm = false })
    }
}

@Composable
private fun PrivacySettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = BrowserSettings
    fun apply() = Browser.applySettingsToAll()
    fun clear(c: BrowserActions.ClearChoice, done: String) {
        scope.launch {
            BrowserActions.clearData(context, c)
            Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
        }
    }
    SubPage("Privacy", "প্রাইভেসি", onBack) {
        item {
            Group {
                SwitchRow("থার্ড-পার্টি কুকি ব্লক", "বিজ্ঞাপনের ট্র্যাকিং কমে; কিছু লগইন ভাঙতে পারে", s.blockThirdPartyCookies) {
                    s.blockThirdPartyCookies = it
                    apply()
                }
                GroupDivider()
                SwitchRow("SSL ভুল থাকলেও পেজ খুলুন", "ঝুঁকিপূর্ণ; শুধু জানা সাইটে কাজে লাগে", s.ignoreSslErrors) { s.ignoreSslErrors = it }
                GroupDivider()
                SwitchRow("ইনকগনিটোতে স্ক্রিনশট বন্ধ", null, s.incognitoSecure) { s.incognitoSecure = it }
                GroupDivider()
                SwitchRow("'Save-Data' পাঠান", "সাইটকে কম ডেটার পেজ দিতে বলে", s.saveData) { s.saveData = it }
                GroupDivider()
                SwitchRow("'Do Not Track' পাঠান", null, s.doNotTrack) { s.doNotTrack = it }
                GroupDivider()
                SwitchRow("চেনা যায় এমন হেডার সরান", "X-Requested-With (ফোনের WebView সমর্থন করলে)", s.removeXRequestedWith) {
                    s.removeXRequestedWith = it
                    apply()
                }
            }
        }
        item { GroupLabel("ব্রাউজার বন্ধ করলে মুছবে") }
        item {
            Group {
                SwitchRow("ক্যাশ", null, s.clearCacheOnExit) { s.clearCacheOnExit = it }
                GroupDivider()
                SwitchRow("হিস্ট্রি", null, s.clearHistoryOnExit) { s.clearHistoryOnExit = it }
                GroupDivider()
                SwitchRow("কুকি", null, s.clearCookiesOnExit) { s.clearCookiesOnExit = it }
                GroupDivider()
                SwitchRow("সাইটের ডেটা", null, s.clearStorageOnExit) { s.clearStorageOnExit = it }
            }
        }
        item { GroupLabel("এখনই মুছুন") }
        item {
            Group {
                ActionRow("ক্যাশ মুছুন", null, Glyph.Trash, chevron = false) {
                    clear(BrowserActions.ClearChoice(history = false, cookies = false, cache = true, storage = false), "ক্যাশ মুছে গেছে")
                }
                GroupDivider()
                ActionRow("হিস্ট্রি মুছুন", null, Glyph.History, chevron = false) {
                    clear(BrowserActions.ClearChoice(history = true, cookies = false, cache = false, storage = false), "হিস্ট্রি মুছে গেছে")
                }
                GroupDivider()
                ActionRow("কুকি মুছুন", "সব সাইট থেকে লগআউট হবেন", Glyph.Cookie, chevron = false) {
                    clear(BrowserActions.ClearChoice(history = false, cookies = true, cache = false, storage = false), "কুকি মুছে গেছে")
                }
                GroupDivider()
                ActionRow("সাইটের ডেটা মুছুন", null, Glyph.Layers, chevron = false) {
                    clear(BrowserActions.ClearChoice(history = false, cookies = false, cache = false, storage = true), "সাইটের ডেটা মুছে গেছে")
                }
            }
        }
    }
}

@Composable
private fun AccessibilitySettings(onBack: () -> Unit) {
    val context = LocalContext.current
    val s = BrowserSettings
    SubPage("Accessibility", "সহজ ব্যবহার", onBack) {
        item {
            Group {
                ActionRow("হোম স্ক্রিনে শর্টকাট", "Nirvor Browser সরাসরি খুলবে", Glyph.AddBox) { BrowserActions.addBrowserShortcut(context) }
                GroupDivider()
                SwitchRow("অ্যাপ ড্রয়ারে আলাদা ব্রাউজার আইকন", "কয়েক সেকেন্ড পর দেখা যাবে", s.launcherIcon) {
                    s.launcherIcon = it
                    BrowserActions.setLauncherIcon(context, it)
                }
                GroupDivider()
                SwitchRow("অ্যাড্রেস বার নিচে", "এক হাতে ব্যবহার সহজ", s.addressBarBottom) { s.addressBarBottom = it }
                GroupDivider()
                SwitchRow("অ্যাড্রেস বারে ডানে-বাঁয়ে টেনে ট্যাব বদল", null, s.swipeAddressBar) { s.swipeAddressBar = it }
                GroupDivider()
                ChoiceRow(
                    "পেছনে যাওয়ার কিছু না থাকলে Back চাপলে",
                    listOf("close_tab" to "ট্যাব বন্ধ হবে", "exit" to "ব্রাউজার থেকে বের হবে"),
                    s.backAction,
                ) { s.backAction = it }
            }
        }
    }
}
