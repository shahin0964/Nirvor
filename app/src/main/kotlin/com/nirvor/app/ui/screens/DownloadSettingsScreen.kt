package com.nirvor.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.downloader.DownloadPrefs
import com.nirvor.app.downloader.DownloadService
import com.nirvor.app.downloader.Downloads
import com.nirvor.app.downloader.MediaEngine
import com.nirvor.app.downloader.NirvorFolder
import com.nirvor.app.downloader.Status
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.Pill
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.SectionLabel
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import kotlinx.coroutines.launch

private val CyanA = Color(0xFF38C8F0)
private val CyanB = Color(0xFF1C93D6)
private val VioletA = Color(0xFF9B7BF7)
private val VioletB = Color(0xFF6D4AE0)
private val Green = Color(0xFF0BA765)

/** Settings → ডাউনলোড: where files go, speed, network, the engine. */
@Composable
fun DownloadSettingsScreen(bottomPadding: Dp, onBack: () -> Unit, onOpenHub: () -> Unit, onFolder: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { DownloadPrefs(context) }
    var parallel by remember { mutableIntStateOf(prefs.parallel) }
    var parts by remember { mutableIntStateOf(prefs.parts) }
    var wifiOnly by remember { mutableStateOf(prefs.wifiOnly) }
    var notifyDone by remember { mutableStateOf(prefs.notifyDone) }

    var version by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var updating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        version = MediaEngine.currentVersion(context)
        checking = false
    }

    val list by remember { Downloads.dao(context).all() }.collectAsStateWithLifecycle(emptyList())

    // Folder state, re-read when the app comes back (or the folder page closes).
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val folderReady = remember(resumeTick) { NirvorFolder.isReady(context) }
    val folderPath = remember(resumeTick) { NirvorFolder.displayPath(context) }
    val finished = list.count { it.status == Status.DONE }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    PageScaffold("Download", "ডাউনলোড সেটিংস", onBack = onBack, bottomPadding = bottomPadding) {
        item {
            NavRow(
                Glyph.Download, VioletA, VioletB,
                "Download Manager",
                if (list.isEmpty()) "এখনো কিছু নামানো হয়নি" else "মোট ${bnDigits(list.size)}টা ডাউনলোড",
                onOpenHub,
            )
        }

        item { SectionLabel("সেভের জায়গা") }
        item {
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Glyph.Folder, C.NotesIconA, C.NotesIconB)
                    Spacer(Modifier.width(20.r))
                    Column(Modifier.weight(1f)) {
                        RText(if (folderReady) "Nirvor ফোল্ডার" else "ফোল্ডার ঠিক করা হয়নি", 22, FontWeight.Medium, C.Ink)
                        Spacer(Modifier.height(4.r))
                        RText(
                            if (folderReady) folderPath ?: "Nirvor/Downloads" else "এখন ফাইল যাচ্ছে ফোনের Download/Nirvor-এ",
                            17,
                            FontWeight.Normal,
                            if (folderReady) Green else C.StatLabelBn,
                            maxLines = 2,
                        )
                    }
                }
                Spacer(Modifier.height(14.r))
                RText(
                    "প্রতিটা ফাইল তার ধরন অনুযায়ী আলাদা ফোল্ডারে যায়: " +
                        NirvorFolder.Category.entries.joinToString(", ") { it.folder },
                    16,
                    FontWeight.Normal,
                    C.Hint,
                    maxLines = 3,
                    lineHeight = 23,
                )
                Spacer(Modifier.height(16.r))
                Button(
                    onClick = onFolder,
                    modifier = Modifier.fillMaxWidth().height(68.r),
                    shape = RoundedCornerShape(18.r),
                    colors = ButtonDefaults.buttonColors(containerColor = if (folderReady) Color(0xFFEAF6F0) else Green),
                ) {
                    RText(
                        if (folderReady) "ফোল্ডার বদলান" else "Nirvor ফোল্ডার ঠিক করুন",
                        20,
                        FontWeight.SemiBold,
                        if (folderReady) Color(0xFF0A955A) else Color.White,
                    )
                }
            }
        }

        item { SectionLabel("গতি") }
        item {
            ListCard {
                RText("একসাথে কয়টা ডাউনলোড চলবে", 21, FontWeight.Medium, C.Ink)
                Spacer(Modifier.height(14.r))
                Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
                    (1..4).forEach { n ->
                        Pill("${bnDigits(n)}টা", parallel == n) {
                            parallel = n
                            prefs.parallel = n
                        }
                    }
                }
                Spacer(Modifier.height(26.r))
                RText("প্রতি ফাইলে কানেকশন", 21, FontWeight.Medium, C.Ink)
                Spacer(Modifier.height(4.r))
                RText(
                    "বেশি হলে দ্রুত নামে। কোনো সাইটে আটকে গেলে কমিয়ে দিন",
                    17,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    maxLines = 2,
                )
                Spacer(Modifier.height(14.r))
                Row(horizontalArrangement = Arrangement.spacedBy(12.r)) {
                    listOf(1, 2, 4, 8).forEach { n ->
                        Pill(bnDigits(n), parts == n) {
                            parts = n
                            prefs.parts = n
                        }
                    }
                }
            }
        }

        item { SectionLabel("নেটওয়ার্ক ও নোটিফিকেশন") }
        item {
            ToggleRow(
                Glyph.Globe, CyanA, CyanB,
                "শুধু Wi-Fi-তে নামাবে",
                "মোবাইল ডেটায় ডাউনলোড অপেক্ষা করবে, Wi-Fi পেলেই শুরু",
                wifiOnly,
            ) {
                wifiOnly = it
                prefs.wifiOnly = it
                if (!it) {
                    scope.launch { if (Downloads.dao(context).activeCount() > 0) DownloadService.start(context) }
                }
            }
        }
        item {
            ToggleRow(
                Glyph.Bell, C.PurpleA, C.PurpleB,
                "শেষ হলে নোটিফিকেশন",
                "ডাউনলোড শেষ হলে জানাবে, চাপলেই ফাইল খুলবে",
                notifyDone,
            ) {
                notifyDone = it
                prefs.notifyDone = it
            }
        }

        item { SectionLabel("ডাউনলোড ইঞ্জিন") }
        item {
            ListCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Glyph.Engine, C.TaskGreenA, C.TaskGreenB)
                    Spacer(Modifier.width(20.r))
                    Column(Modifier.weight(1f)) {
                        RText("yt-dlp ইঞ্জিন", 22, FontWeight.Medium, C.Ink)
                        Spacer(Modifier.height(4.r))
                        RText(
                            when {
                                checking -> "দেখা হচ্ছে…"
                                version == null -> "প্রথম ডাউনলোডে চালু হবে"
                                else -> "ভার্সন $version"
                            },
                            17,
                            FontWeight.Normal,
                            C.StatLabelBn,
                        )
                    }
                }
                Spacer(Modifier.height(14.r))
                RText(
                    "YouTube, Facebook, Instagram, TikTok, X সহ হাজারের বেশি সাইট চেনে। " +
                        "সাইটগুলো প্রায়ই বদলায়, তাই ইঞ্জিন প্রতি ৩ দিনে নিজে থেকে আপডেট হয়।",
                    17,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    maxLines = 4,
                    lineHeight = 25,
                )
                Spacer(Modifier.height(18.r))
                Button(
                    onClick = {
                        updating = true
                        scope.launch {
                            val msg = try {
                                if (MediaEngine.update(context)) "নতুন ভার্সন ইনস্টল হয়েছে" else "ইঞ্জিন একদম আপ-টু-ডেট"
                            } catch (e: Exception) {
                                "আপডেট হয়নি। ইন্টারনেট সংযোগ দেখে আবার চেষ্টা করুন"
                            }
                            version = MediaEngine.currentVersion(context)
                            updating = false
                            toast(msg)
                        }
                    },
                    enabled = !updating,
                    modifier = Modifier.fillMaxWidth().height(72.r),
                    shape = RoundedCornerShape(18.r),
                    colors = ButtonDefaults.buttonColors(containerColor = Green),
                ) {
                    RText(if (updating) "আপডেট হচ্ছে…" else "এখনই আপডেট করুন", 21, FontWeight.SemiBold, Color.White)
                }
            }
        }

        item { SectionLabel("তালিকা") }
        item {
            NavRow(
                Glyph.Trash, C.RedA, C.RedB,
                "শেষ হওয়াগুলো তালিকা থেকে সরান",
                if (finished == 0) "সরানোর মতো কিছু নেই" else "${bnDigits(finished)}টা · ফাইলগুলো ফোনেই থেকে যাবে",
            ) {
                if (finished > 0) {
                    scope.launch {
                        Downloads.clearFinished(context)
                        toast("তালিকা পরিষ্কার হয়েছে")
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    glyph: Glyph,
    a: Color,
    b: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListCard(onClick = { onChange(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(glyph, a, b)
            Spacer(Modifier.width(20.r))
            Column(Modifier.weight(1f)) {
                RText(title, 22, FontWeight.Medium, C.Ink)
                Spacer(Modifier.height(4.r))
                RText(subtitle, 17, FontWeight.Normal, C.StatLabelBn, maxLines = 2)
            }
            Spacer(Modifier.width(12.r))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Green,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFD5DEE7),
                    uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}
