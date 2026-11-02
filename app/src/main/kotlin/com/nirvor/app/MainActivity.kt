package com.nirvor.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.nirvor.app.downloader.Downloads
import com.nirvor.app.ui.AppNav
import com.nirvor.app.ui.NavRequest
import com.nirvor.app.ui.NirvorRoot
import com.nirvor.app.ui.ProvideRef
import com.nirvor.app.ui.display.RefreshRateManager
import com.nirvor.app.ui.theme.NirvorTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN = "nirvor.open"
        const val EXTRA_URL = "nirvor.url"
        const val OPEN_DOWNLOADS = "downloads"
        const val OPEN_BROWSER = "browser"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the status and navigation bars, with dark icons on the light design.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        RefreshRateManager.applyOptimalRefreshRate(this)
        if (savedInstanceState == null) handle(intent)
        lifecycleScope.launch { Downloads.recover(this@MainActivity) }
        setContent {
            NirvorTheme {
                ProvideRef {
                    NirvorRoot()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RefreshRateManager.applyOptimalRefreshRate(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Notifications and the share sheet can ask to open the Download Hub or the browser. */
    private fun handle(intent: Intent?) {
        // The second app-drawer icon ("Nirvor Browser") opens straight into the browser.
        if (intent?.component?.className?.endsWith(".BrowserLauncher") == true) {
            AppNav.request.value = NavRequest.Browser(null)
            return
        }
        when (intent?.getStringExtra(EXTRA_OPEN)) {
            OPEN_DOWNLOADS -> AppNav.request.value = NavRequest.Downloads
            OPEN_BROWSER -> AppNav.request.value = NavRequest.Browser(intent.getStringExtra(EXTRA_URL))
        }
    }
}
