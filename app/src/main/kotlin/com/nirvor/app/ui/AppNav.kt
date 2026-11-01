package com.nirvor.app.ui

import kotlinx.coroutines.flow.MutableStateFlow

/** A request from outside the screens (a notification, the share sheet) to open a page. */
sealed interface NavRequest {
    data object Downloads : NavRequest
    data class Browser(val url: String?) : NavRequest
}

object AppNav {
    /** Set by MainActivity, consumed (set back to null) by the root screen. */
    val request = MutableStateFlow<NavRequest?>(null)
}
