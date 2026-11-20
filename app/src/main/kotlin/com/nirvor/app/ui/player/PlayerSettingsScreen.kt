package com.nirvor.app.ui.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirvor.app.player.PlayerPrefs
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon

@Composable
fun PlayerSettingsScreen(
    prefs: PlayerPrefs,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // Reactive states backed by PlayerPrefs
    var autoLandscape by remember { mutableStateOf(prefs.autoLandscape) }
    var immersiveFullscreen by remember { mutableStateOf(prefs.immersiveFullscreen) }
    var resumePlayback by remember { mutableStateOf(prefs.resumePlayback) }
    var doubleTapSeekSec by remember { mutableIntStateOf(prefs.doubleTapSeekSec) }
    var autoHideTimeoutSec by remember { mutableIntStateOf(prefs.autoHideTimeoutSec) }
    var backgroundPlay by remember { mutableStateOf(prefs.backgroundPlay) }
    var gesturesEnabled by remember { mutableStateOf(prefs.gesturesEnabled) }
    var hwDecoderDefault by remember { mutableStateOf(prefs.hwDecoderDefault) }
    var volumeBoost by remember { mutableStateOf(prefs.volumeBoost) }
    var pauseOnHeadsetDisconnect by remember { mutableStateOf(prefs.pauseOnHeadsetDisconnect) }
    var showNewTag by remember { mutableStateOf(prefs.showNewTag) }
    var displayLengthOverThumbnail by remember { mutableStateOf(prefs.displayLengthOverThumbnail) }

    // Dialogs
    var showSeekDialog by remember { mutableStateOf(false) }
    var showTimeoutDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // TOP APP BAR (with statusBarsPadding to prevent overlapping notification area)
        Surface(
            color = Color(0xFF1E293B),
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(Glyph.ArrowLeft, Color.White, Modifier.size(22.dp))
                }

                Spacer(Modifier.width(12.dp))

                Text(
                    text = "Player Settings",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Reset button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { showResetDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(Glyph.Refresh, Color(0xFF94A3B8), Modifier.size(20.dp))
                }
            }
        }

        // SCROLLABLE SETTINGS CONTENT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. PLAYBACK & SCREEN
            SettingsGroup(title = "প্লেব্যাক ও ওরিয়েন্টেশন (Screen & Playback)") {
                SettingSwitchRow(
                    title = "অটো ল্যান্ডস্কেপ মোড (Auto Landscape)",
                    subtitle = "ভিডিও প্লে করলে সাথে সাথে ল্যান্ডস্কেপ হবে (ফোনের রোটেশন অফ থাকলেও)",
                    checked = autoLandscape,
                    onCheckedChange = {
                        autoLandscape = it
                        prefs.autoLandscape = it
                    }
                )

                SettingDivider()

                SettingSwitchRow(
                    title = "ফুলস্ক্রিন ইমার্সিভ মোড (Immersive)",
                    subtitle = "প্লেয়ার চলাকালীন স্ট্যাটাস বার ও নেভিগেশন বার স্বয়ংক্রিয়ভাবে হাইড হবে",
                    checked = immersiveFullscreen,
                    onCheckedChange = {
                        immersiveFullscreen = it
                        prefs.immersiveFullscreen = it
                    }
                )

                SettingDivider()

                SettingSwitchRow(
                    title = "আগের স্থান থেকে চালু (Resume Playback)",
                    subtitle = "যেখানে ভিডিও শেষ করেছিলেন সেখান থেকে আবার শুরু হবে",
                    checked = resumePlayback,
                    onCheckedChange = {
                        resumePlayback = it
                        prefs.resumePlayback = it
                    }
                )

                SettingDivider()

                SettingClickableRow(
                    title = "ডাবল ট্যাপে স্কিপ (Double-tap to Seek)",
                    subtitle = "${doubleTapSeekSec} সেকেন্ড আগে বা পরে স্কিপ হবে",
                    onClick = { showSeekDialog = true }
                )

                SettingDivider()

                SettingClickableRow(
                    title = "কন্ট্রোল বার লুকানোর সময় (Auto-hide Timeout)",
                    subtitle = "${autoHideTimeoutSec} সেকেন্ড পর কন্ট্রোল বার নিজে থেকেই লুকাবে",
                    onClick = { showTimeoutDialog = true }
                )

                SettingDivider()

                SettingSwitchRow(
                    title = "ব্যাকগ্রাউন্ড প্লে (Background Play)",
                    subtitle = "অ্যাপ মিনিমাইজ করলেও অডিও ও ভিডিও ব্যাকগ্রাউন্ডে চলতে থাকবে",
                    checked = backgroundPlay,
                    onCheckedChange = {
                        backgroundPlay = it
                        prefs.backgroundPlay = it
                    }
                )
            }

            // 2. GESTURES & CONTROLS
            SettingsGroup(title = "টাচ জেসচার ও কন্ট্রোল (Touch Gestures)") {
                SettingSwitchRow(
                    title = "স্ক্রিন সোয়াইপ জেসচার (Screen Gestures)",
                    subtitle = "বাম পাশে ব্রাইটনেস, ডান পাশে ভলিউম এবং আড়াআড়ি সোয়াইপে সিক",
                    checked = gesturesEnabled,
                    onCheckedChange = {
                        gesturesEnabled = it
                        prefs.gesturesEnabled = it
                    }
                )
            }

            // 3. DECODER
            SettingsGroup(title = "ডিকোডার সেটিংস (Decoder)") {
                SettingSwitchRow(
                    title = "হার্ডওয়্যার ডিকোডার (HW Decoder)",
                    subtitle = "ডিভাইসের চিপসেট ব্যবহার করে মসৃণ হার্ডওয়্যার অ্যাকসেলেরেশন",
                    checked = hwDecoderDefault,
                    onCheckedChange = {
                        hwDecoderDefault = it
                        prefs.hwDecoderDefault = it
                    }
                )
            }

            // 4. AUDIO
            SettingsGroup(title = "অডিও সেটিংস (Audio)") {
                SettingSwitchRow(
                    title = "হেডফোন খুললে পজ (Pause on Headset Disconnect)",
                    subtitle = "হেডফোন বা ব্লুটুথ ডিভাইস ডিসকানেক্ট হলে নিজে থেকেই পজ হবে",
                    checked = pauseOnHeadsetDisconnect,
                    onCheckedChange = {
                        pauseOnHeadsetDisconnect = it
                        prefs.pauseOnHeadsetDisconnect = it
                    }
                )

                SettingDivider()

                SettingSwitchRow(
                    title = "ভলিউম বুস্ট ২০০% (Volume Boost)",
                    subtitle = "সফটওয়্যার ডিকোডারের মাধ্যমে অতিরিক্ত উচ্চ সাউন্ড আউটপুট",
                    checked = volumeBoost,
                    onCheckedChange = {
                        volumeBoost = it
                        prefs.volumeBoost = it
                    }
                )
            }

            // 5. LIST & FOLDER
            SettingsGroup(title = "লিস্ট ও মিডিয়া ফোল্ডার (List & Folders)") {
                SettingSwitchRow(
                    title = "নতুন মিডিয়াতে 'NEW' ট্যাগ প্রদর্শন",
                    subtitle = "গত ৭ দিনের মধ্যে ডাউনলোড বা তোলা ফাইলে লাল NEW ট্যাগ থাকবে",
                    checked = showNewTag,
                    onCheckedChange = {
                        showNewTag = it
                        prefs.showNewTag = it
                    }
                )

                SettingDivider()

                SettingSwitchRow(
                    title = "থাম্বনেইলের ওপর সময়কাল প্রদর্শন",
                    subtitle = "ভিডিও থাম্বনেইলের কোণায় সময়কালের কালো ব্যাজ দেখাবে",
                    checked = displayLengthOverThumbnail,
                    onCheckedChange = {
                        displayLengthOverThumbnail = it
                        prefs.displayLengthOverThumbnail = it
                    }
                )
            }

            // 6. MAINTENANCE
            SettingsGroup(title = "রক্ষণাবেক্ষণ ও হিস্ট্রি (Maintenance)") {
                SettingClickableRow(
                    title = "প্লেব্যাক হিস্ট্রি মুছুন (Clear Playback History)",
                    subtitle = "সব ভিডিওর পূর্বে থেমে থাকা পজিশন মেমোরি ক্লিয়ার করুন",
                    onClick = {
                        prefs.clearAllHistory()
                        Toast.makeText(context, "Playback history cleared", Toast.LENGTH_SHORT).show()
                    }
                )

                SettingDivider()

                SettingClickableRow(
                    title = "ডিফল্ট সেটিংসে ফিরিয়ে নিন (Reset Settings)",
                    subtitle = "প্লেয়ারের সকল কনফিগারেশন ফ্যাক্টরি ডিফল্টে রিসেট করুন",
                    onClick = { showResetDialog = true }
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    // DIALOG: Double tap seek options
    if (showSeekDialog) {
        AlertDialog(
            onDismissRequest = { showSeekDialog = false },
            containerColor = Color(0xFF1E293B),
            title = { Text("Double-tap Seek Interval", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { sec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (doubleTapSeekSec == sec) Color(0xFF2563EB) else Color(0xFF334155))
                                .clickable {
                                    doubleTapSeekSec = sec
                                    prefs.doubleTapSeekSec = sec
                                    showSeekDialog = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$sec seconds",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // DIALOG: Auto-hide controls timeout
    if (showTimeoutDialog) {
        AlertDialog(
            onDismissRequest = { showTimeoutDialog = false },
            containerColor = Color(0xFF1E293B),
            title = { Text("Auto-hide Controls Timeout", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 4, 6, 8).forEach { sec ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (autoHideTimeoutSec == sec) Color(0xFF2563EB) else Color(0xFF334155))
                                .clickable {
                                    autoHideTimeoutSec = sec
                                    prefs.autoHideTimeoutSec = sec
                                    showTimeoutDialog = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$sec seconds",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // DIALOG: Reset settings confirmation
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = Color(0xFF1E293B),
            title = { Text("Reset Player Settings?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "All Nirvor Player settings will be restored to default values.",
                    color = Color(0xFF94A3B8)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.resetToDefaults()
                        autoLandscape = prefs.autoLandscape
                        immersiveFullscreen = prefs.immersiveFullscreen
                        resumePlayback = prefs.resumePlayback
                        doubleTapSeekSec = prefs.doubleTapSeekSec
                        autoHideTimeoutSec = prefs.autoHideTimeoutSec
                        backgroundPlay = prefs.backgroundPlay
                        gesturesEnabled = prefs.gesturesEnabled
                        hwDecoderDefault = prefs.hwDecoderDefault
                        volumeBoost = prefs.volumeBoost
                        pauseOnHeadsetDisconnect = prefs.pauseOnHeadsetDisconnect
                        showNewTag = prefs.showNewTag
                        displayLengthOverThumbnail = prefs.displayLengthOverThumbnail
                        showResetDialog = false
                        Toast.makeText(context, "Settings reset to default", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("RESET", color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("CANCEL", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

// ---------------------------------------------------------------- COMPONENT HELPERS

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            color = Color(0xFF38BDF8),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E293B))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF2563EB),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF334155)
            )
        )
    }
}

@Composable
private fun SettingClickableRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }
        GlyphIcon(Glyph.ChevronRight, Color(0xFF64748B), Modifier.size(18.dp))
    }
}

@Composable
private fun SettingDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF334155).copy(alpha = 0.5f))
    )
}
