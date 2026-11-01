package com.nirvor.app.browser

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Every option of the browser (menu switches and the browser's own Settings pages).
 * Values are Compose state, so screens update by themselves, and saved immediately.
 */
object BrowserSettings {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("nirvor_browser", Context.MODE_PRIVATE)
    }

    private class B(private val key: String, private val def: Boolean) : ReadWriteProperty<Any?, Boolean> {
        private var state: MutableState<Boolean>? = null
        private fun st() = state ?: mutableStateOf(sp.getBoolean(key, def)).also { state = it }
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = st().value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            st().value = value
            sp.edit().putBoolean(key, value).apply()
        }
    }

    private class I(private val key: String, private val def: Int) : ReadWriteProperty<Any?, Int> {
        private var state: MutableState<Int>? = null
        private fun st() = state ?: mutableStateOf(sp.getInt(key, def)).also { state = it }
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = st().value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            st().value = value
            sp.edit().putInt(key, value).apply()
        }
    }

    private class S(private val key: String, private val def: String) : ReadWriteProperty<Any?, String> {
        private var state: MutableState<String>? = null
        private fun st() = state ?: mutableStateOf(sp.getString(key, def) ?: def).also { state = it }
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = st().value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            st().value = value
            sp.edit().putString(key, value).apply()
        }
    }

    // ------------------------------------------------------------ menu switches
    var blockAds by B("block_ads", true)
    var captureMedia by B("capture_media", true)
    var darkPages by B("dark_pages", false)
    var desktopSite by B("desktop_site", false)
    var javascript by B("javascript", true)

    // ------------------------------------------------------------ General
    var pullToRefresh by B("pull_to_refresh", true)
    var selectUrlOnClick by B("select_url_on_click", true)
    var landscapeFullscreen by B("landscape_fullscreen", true)
    var floatingDownload by B("floating_download", false)
    var skipEditorCaptured by B("skip_editor_captured", false)
    var skipEditorPageDownload by B("skip_editor_page_download", false)
    var skipEditorImage by B("skip_editor_image", false)
    var serverNameForImages by B("server_name_images", false)
    var blockImages by B("block_images", false)
    var tabReorder by B("tab_reorder", true)
    /** default | desktop | iphone | android | custom */
    var userAgentMode by S("ua_mode", "default")
    var customUserAgent by S("ua_custom", "")
    var desktopPersistent by B("desktop_persistent", false)
    var homepage by S("homepage", "")
    var searchEngine by S("search_engine", "google")
    var searchEngineIncognito by S("search_engine_incognito", "duckduckgo")
    var suggestions by B("suggestions", true)
    var suggestionsIncognito by B("suggestions_incognito", false)
    var imageSearch by S("image_search", "google")

    // ------------------------------------------------------------ Bookmarks
    var bookmarkFoldersFirst by B("bookmark_folders_first", true)
    var bookmarksInSuggestions by B("bookmarks_in_suggestions", true)

    // ------------------------------------------------------------ Display
    /** theme | on | off  (pages' dark mode follows the phone, or always on / off) */
    var darkModeBrowser by S("dark_mode_browser", "off")
    var hideStatusBar by B("hide_status_bar", false)
    var forceZoom by B("force_zoom", false)
    var showTabTotal by B("show_tab_total", false)
    var fullScreen by B("full_screen", false)
    var wideViewport by B("wide_viewport", true)
    var overviewMode by B("overview_mode", true)
    var textReflow by B("text_reflow", false)
    var textSize by I("text_size", 100)

    // ------------------------------------------------------------ Privacy
    var blockThirdPartyCookies by B("block_3p_cookies", false)
    var ignoreSslErrors by B("ignore_ssl", false)
    var incognitoSecure by B("incognito_secure", true)
    var saveData by B("save_data", false)
    var doNotTrack by B("do_not_track", false)
    var removeXRequestedWith by B("remove_xrw", true)
    var clearCacheOnExit by B("clear_cache_exit", false)
    var clearHistoryOnExit by B("clear_history_exit", false)
    var clearCookiesOnExit by B("clear_cookies_exit", false)
    var clearStorageOnExit by B("clear_storage_exit", false)

    // ------------------------------------------------------------ Website permissions (ask | allow | block)
    var permCamera by S("perm_camera", "ask")
    var permMic by S("perm_mic", "ask")
    var permLocation by S("perm_location", "ask")
    var permCameraIncognito by S("perm_camera_inc", "block")
    var permMicIncognito by S("perm_mic_inc", "block")
    var permLocationIncognito by S("perm_location_inc", "block")
    var allowNewWindows by B("allow_new_windows", true)
    var externalApps by S("external_apps", "ask")
    var drm by S("drm", "ask")

    // ------------------------------------------------------------ Accessibility
    var addressBarBottom by B("address_bar_bottom", false)
    var swipeAddressBar by B("swipe_address_bar", true)
    /** close_tab | exit : what Back does when a tab has no page to go back to */
    var backAction by S("back_action", "close_tab")
    var launcherIcon by B("launcher_icon", false)

    // ------------------------------------------------------------ Advanced
    var extraCaptureTypes by S("extra_capture_types", "")
    var askNewTabs by B("ask_new_tabs", false)
    var newTabsBackground by B("new_tabs_background", false)
    var forceTextSelection by B("force_text_selection", false)
    var popupHandling by S("popup_handling", "ask")
    var showPopupUrl by B("show_popup_url", true)
    var incognitoDefault by B("incognito_default", false)
    var tabClosedUndo by B("tab_closed_undo", true)
    var closeTabAfterDownload by B("close_tab_after_download", false)
    var titleAsFileName by B("title_as_file_name", true)
    var showTitleInCaptured by B("show_title_in_captured", false)
    var cookies by B("cookies", true)
    var cookiesIncognito by B("cookies_incognito", true)
    var restoreTabs by B("restore_tabs", true)
    var safeBrowsing by B("safe_browsing", true)
    var pauseTimers by B("pause_timers", true)
    var textEncoding by S("text_encoding", "UTF-8")
    /** normal | grayscale | invert | invert_grayscale | high_contrast */
    var renderingMode by S("rendering_mode", "normal")
    /** url | title | domain */
    var urlBoxContents by S("url_box", "domain")
    var titleForSavedPages by B("title_for_saved_pages", true)
    /** Saved pages also go to Nirvor/Downloads/General (besides the browser's own list). */
    var savePagesCopy by B("save_pages_copy", true)
}
