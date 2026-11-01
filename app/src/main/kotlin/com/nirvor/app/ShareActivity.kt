package com.nirvor.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.downloader.Analyzer
import com.nirvor.app.ui.ProvideRef
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.download.Dl
import com.nirvor.app.ui.download.FormatPicker
import com.nirvor.app.ui.download.GradIcon
import com.nirvor.app.ui.download.GreenButton
import com.nirvor.app.ui.download.SheetHandle
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.NirvorTheme

/**
 * Opens when the user picks "Nirvor দিয়ে ডাউনলোড" in another app's share menu (YouTube,
 * Facebook, Instagram, TikTok, a browser, …). The format picker slides up on top of that app;
 * after choosing, the download continues in the background and this window closes.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString
        val url = Analyzer.extractUrl(text)
        setContent {
            NirvorTheme {
                ProvideRef {
                    ShareSheet(url = url, onClose = { finish() }, onOpenBrowser = { openInBrowser(it) })
                }
            }
        }
    }

    private fun openInBrowser(url: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_BROWSER)
                .putExtra(MainActivity.EXTRA_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(url: String?, onClose: () -> Unit, onOpenBrowser: (String) -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = state,
        containerColor = androidx.compose.ui.graphics.Color.White,
        dragHandle = { SheetHandle() },
    ) {
        if (url == null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(26.r),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GradIcon(Glyph.Link, Dl.red, size = 90, iconScale = 0.5f)
                Spacer(Modifier.height(18.r))
                RText("শেয়ার করা লেখায় কোনো লিংক পাওয়া যায়নি", 22, FontWeight.Bold, Dl.Ink, align = TextAlign.Center, maxLines = 2)
                Spacer(Modifier.height(24.r))
                GreenButton("বন্ধ করুন", Modifier.fillMaxWidth(), onClick = onClose)
            }
        } else {
            FormatPicker(url = url, onClose = onClose, onOpenBrowser = onOpenBrowser, onStarted = onClose)
        }
    }
}
