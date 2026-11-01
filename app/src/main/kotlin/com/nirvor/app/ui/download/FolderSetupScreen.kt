package com.nirvor.app.ui.download

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.downloader.NirvorFolder
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface SetupState {
    data object Intro : SetupState
    data object Working : SetupState
    data class Done(val path: String) : SetupState
    data class Error(val message: String) : SetupState
}

/**
 * First-run page: explains the Nirvor folder, opens the phone's file manager so the user can
 * make a folder called "Nirvor", then creates Downloads and every category folder inside it.
 *
 * [auto] opens the file manager by itself after a short countdown (first launch).
 */
@Composable
fun FolderSetupScreen(auto: Boolean, onDone: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<SetupState>(SetupState.Intro) }
    var countdown by remember { mutableStateOf(if (auto) 3 else 0) }
    var note by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
            state = SetupState.Intro
            note = "ফোল্ডার বাছা হয়নি। আবার চেষ্টা করুন।"
        } else {
            state = SetupState.Working
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { NirvorFolder.adopt(context, uri) } }
                state = result.fold(
                    onSuccess = { SetupState.Done(it.path) },
                    onFailure = { SetupState.Error("ফোল্ডার বানানো গেল না: " + (it.message ?: it.javaClass.simpleName).take(120)) },
                )
            }
        }
    }

    fun openPicker() {
        countdown = 0
        note = null
        try {
            picker.launch(NirvorFolder.pickerStart())
        } catch (e: Exception) {
            state = SetupState.Error("এই ফোনে ফোল্ডার বাছার স্ক্রিন পাওয়া যায়নি।")
        }
    }

    // First launch: the file manager opens by itself after 3 seconds.
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1000)
            if (countdown == 0) break // already opened by the button
            countdown--
            if (countdown == 0 && state == SetupState.Intro) openPicker()
        }
    }

    BackHandler(enabled = state != SetupState.Working) {
        if (state is SetupState.Done) onDone() else onSkip()
    }

    val done = state is SetupState.Done

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to C.SkyTop, 0.35f to C.Sheet, 1f to C.Sheet))
            // Covers the app underneath: taps on empty space must not reach the home screen.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.r),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 18.r), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                if (!done && state != SetupState.Working) {
                    RText(
                        "এখন না",
                        19,
                        FontWeight.Medium,
                        Dl.Sub,
                        Modifier
                            .clip(RoundedCornerShape(12.r))
                            .clickable(onClick = onSkip)
                            .padding(horizontal = 14.r, vertical = 10.r),
                    )
                }
            }

            // Hero
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(6.r))
                Box {
                    GradIcon(Glyph.Folder, Dl.amber, size = 124, iconScale = 0.52f)
                    Box(Modifier.align(Alignment.BottomEnd)) {
                        Box(
                            Modifier.size(50.r).background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            GradIcon(if (done) Glyph.Check else Glyph.Download, Dl.brand, size = 42, iconScale = 0.56f)
                        }
                    }
                }
                Spacer(Modifier.height(20.r))
                RText(
                    if (done) "ফোল্ডার তৈরি হয়েছে" else "ফাইল রাখার ফোল্ডার",
                    32,
                    FontWeight.Bold,
                    Dl.Ink,
                )
                Spacer(Modifier.height(8.r))
                RText(
                    if (done) {
                        "এখন থেকে প্রতিটা ডাউনলোড তার ধরন অনুযায়ী নিজের ফোল্ডারে যাবে"
                    } else {
                        "ডাউনলোড করা সব ফাইল গুছিয়ে রাখতে ফোনে \"Nirvor\" নামে একটা ফোল্ডার লাগবে। বাকি ফোল্ডার অ্যাপ নিজেই বানাবে।"
                    },
                    19,
                    FontWeight.Normal,
                    Dl.Sub,
                    align = TextAlign.Center,
                    maxLines = 3,
                    lineHeight = 28,
                )
            }
            Spacer(Modifier.height(24.r))

            FolderTree(done, (state as? SetupState.Done)?.path)
            Spacer(Modifier.height(16.r))

            if (!done) {
                ListCard {
                    RText("কীভাবে করবেন", 21, FontWeight.SemiBold, Dl.Ink)
                    Spacer(Modifier.height(8.r))
                    Step("১", "নিচের বাটনে চাপলে ফোনের ফাইল ম্যানেজার খুলবে")
                    Step("২", "\"নতুন ফোল্ডার\" (বা মেনু → New folder) চাপুন, নাম দিন Nirvor")
                    Step("৩", "Nirvor ফোল্ডারটা খুলে \"এই ফোল্ডার ব্যবহার করুন\" → \"অনুমতি দিন\" চাপুন")
                }
                Spacer(Modifier.height(16.r))
            }
        }

        // Bottom actions
        Column(Modifier.fillMaxWidth().padding(start = 26.r, end = 26.r, top = 10.r, bottom = 22.r)) {
            when (val s = state) {
                is SetupState.Error -> {
                    RText(s.message, 17, FontWeight.Normal, Dl.Failed, Modifier.fillMaxWidth(), align = TextAlign.Center, maxLines = 3)
                    Spacer(Modifier.height(12.r))
                }
                SetupState.Intro -> note?.let {
                    RText(it, 17, FontWeight.Normal, Dl.Paused, Modifier.fillMaxWidth(), align = TextAlign.Center, maxLines = 2)
                    Spacer(Modifier.height(12.r))
                }
                else -> Unit
            }
            when (state) {
                is SetupState.Done -> GreenButton("ঠিক আছে", Modifier.fillMaxWidth(), glyph = Glyph.Check, onClick = onDone)
                SetupState.Working -> GreenButton("ফোল্ডার বানানো হচ্ছে…", Modifier.fillMaxWidth(), enabled = false) {}
                else -> GreenButton(
                    if (state is SetupState.Error) "আবার চেষ্টা করুন" else "ফাইল ম্যানেজার খুলুন",
                    Modifier.fillMaxWidth(),
                    glyph = Glyph.Folder,
                    onClick = { openPicker() },
                )
            }
            if (countdown > 0 && state == SetupState.Intro) {
                Spacer(Modifier.height(12.r))
                RText(
                    "${"০১২৩৪৫৬৭৮৯"[countdown]} সেকেন্ডে নিজে থেকেই খুলবে…",
                    16,
                    FontWeight.Normal,
                    Dl.Hint,
                    Modifier.fillMaxWidth(),
                    align = TextAlign.Center,
                )
            }
        }
    }
}

/** Nirvor → Downloads → the ten type folders, ticked once they exist. */
@Composable
private fun FolderTree(done: Boolean, path: String?) {
    ListCard {
        TreeRow(0, NirvorFolder.ROOT, if (done) null else "আপনি বানাবেন", done, big = true)
        TreeRow(1, NirvorFolder.DOWNLOADS, if (done) null else "অ্যাপ বানাবে", done, big = true)
        Spacer(Modifier.height(6.r))
        val cats = NirvorFolder.Category.entries
        cats.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(start = 64.r)) {
                pair.forEach { c ->
                    Row(Modifier.weight(1f).padding(vertical = 7.r), verticalAlignment = Alignment.CenterVertically) {
                        GlyphIcon(Glyph.Folder, Color(0xFFF4A905), Modifier.size(24.r), strokeWidth = 2f)
                        Spacer(Modifier.width(10.r))
                        RText(c.folder, 18, FontWeight.Medium, Dl.Ink, Modifier.weight(1f))
                        if (done) {
                            GlyphIcon(Glyph.Check, Dl.Done, Modifier.size(22.r), strokeWidth = 2.6f)
                            Spacer(Modifier.width(10.r))
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (path != null) {
            Spacer(Modifier.height(10.r))
            Box(Modifier.fillMaxWidth().height(1.r).background(C.Divider))
            Spacer(Modifier.height(12.r))
            RText(path, 17, FontWeight.Medium, Dl.GreenText, maxLines = 2)
        }
    }
}

@Composable
private fun TreeRow(level: Int, name: String, tag: String?, done: Boolean, big: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(start = (level * 32).r, top = 6.r, bottom = 6.r),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (level > 0) {
            // tree connector: a small "L" from the parent folder
            val line = 2.r
            Canvas(Modifier.size(24.r, 34.r)) {
                val w = line.toPx()
                drawLine(C.HabOpen, Offset(w, -size.height * 0.3f), Offset(w, size.height / 2), strokeWidth = w)
                drawLine(C.HabOpen, Offset(w, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = w)
            }
            Spacer(Modifier.width(8.r))
        }
        GradIcon(Glyph.Folder, Dl.amber, size = if (big) 46 else 34, iconScale = 0.55f)
        Spacer(Modifier.width(14.r))
        RText(name, 22, FontWeight.SemiBold, Dl.Ink, Modifier.weight(1f))
        when {
            done -> GlyphIcon(Glyph.Check, Dl.Done, Modifier.size(26.r), strokeWidth = 2.6f)
            tag != null -> Tag(tag, if (level == 0) Dl.amber else Dl.brand)
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 7.r), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier.size(36.r).clip(CircleShape).background(Brush.linearGradient(Dl.brand)),
            contentAlignment = Alignment.Center,
        ) { RText(n, 17, FontWeight.Bold, Color.White) }
        Spacer(Modifier.width(14.r))
        RText(text, 18, FontWeight.Normal, Dl.Sub, Modifier.weight(1f), maxLines = 3, lineHeight = 26)
    }
}
