package com.nirvor.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.input.ImeAction
import com.nirvor.app.weather.Place
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirvor.app.ui.Avatar
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.MainViewModel
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.SectionLabel
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter

// ---------------------------------------------------------------- More tab

@Composable
fun MoreScreen(
    bottomPadding: Dp,
    onDownloads: () -> Unit,
    onLedger: () -> Unit,
    onTools: () -> Unit,
    onIslamic: () -> Unit,
    onGames: () -> Unit,
    onReminders: () -> Unit,
    onHabits: () -> Unit,
    onNotes: () -> Unit,
    onUpdate: () -> Unit,
) {
    PageScaffold("More", "ফিচার ও সেটিংস", onBack = null, bottomPadding = bottomPadding) {
        item { SectionLabel("ফিচার") }
        item { MenuRow(Glyph.Download, C.FabA, C.FabB, "Download Manager", "ডাউনলোড ম্যানেজার", onDownloads) }
        item { MenuRow(Glyph.Swap, C.OrangeA, C.OrangeB, "Lend & Borrow", "দেনা-পাওনা ও কিস্তি", onLedger) }
        item { MenuRow(Glyph.Grid, Color(0xFF5B8DEF), Color(0xFF3B5BDB), "Tool Box", "দরকারি সব টুল এক জায়গায়", onTools) }
        item { MenuRow(Glyph.MoonStar, Color(0xFF0E9F6E), Color(0xFF065F46), "Islamic Tools", "নামাজ, কিবলা, কুরআন, দোয়া", onIslamic) }
        item { MenuRow(Glyph.Gamepad, Color(0xFFE11D48), Color(0xFFBE123C), "DUEL X Games", "বিলিয়ার্ড, ক্যারম, লুডো ও আর্কেড", onGames) }
        item { MenuRow(Glyph.Bell, C.PurpleA, C.PurpleB, "Upcoming Reminders", "রিমাইন্ডার", onReminders) }
        item { MenuRow(Glyph.Target, C.HabGreenA, C.HabGreenB, "Today's Habit", "হ্যাবিট", onHabits) }
        item { MenuRow(Glyph.Note, C.NotesIconA, C.NotesIconB, "Quick Notes", "নোট", onNotes) }

        item { SectionLabel("অ্যাপ ও সিস্টেম") }
        item { MenuRow(Glyph.Refresh, Color(0xFF2563EB), Color(0xFF1D4ED8), "App Update (OTA)", "নতুন ভার্সন আপডেট ও রিলিজ নোট", onUpdate) }
    }
}

@Composable
private fun MenuRow(glyph: Glyph, a: Color, b: Color, title: String, subtitle: String, onClick: () -> Unit) {
    ListCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(58.r).background(Brush.linearGradient(listOf(a, b)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph, Color.White, Modifier.size(32.r), strokeWidth = 2f)
            }
            Spacer(Modifier.width(20.r))
            Column(Modifier.weight(1f)) {
                RText(title, 23, FontWeight.SemiBold, C.Ink)
                Spacer(Modifier.height(4.r))
                RText(subtitle, 18, FontWeight.Normal, C.StatLabelBn)
            }
            GlyphIcon(Glyph.ChevronRight, C.RemChevron, Modifier.size(26.r), strokeWidth = 2f)
        }
    }
}

// ---------------------------------------------------------------- Profile page

@Composable
fun ProfileScreen(vm: MainViewModel, bottomPadding: Dp, onBack: () -> Unit) {
    val name by vm.name.collectAsStateWithLifecycle()
    val photo by vm.photo.collectAsStateWithLifecycle()
    var draft by remember(name) { mutableStateOf(name) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.setPhoto(uri)
    }

    PageScaffold("Profile", "নাম ও ছবি", onBack = onBack, bottomPadding = bottomPadding) {
        item {
            ListCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        Avatar(
                            photo,
                            Modifier
                                .size(170.r)
                                .clip(CircleShape)
                                .clickable {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                        )
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(54.r)
                                .background(Color(0xFF0BA765), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(Glyph.Camera, Color.White, Modifier.size(30.r), strokeWidth = 2f)
                        }
                    }
                    Spacer(Modifier.height(14.r))
                    Row {
                        RText(
                            "ছবি বাছুন",
                            19,
                            FontWeight.SemiBold,
                            Color(0xFF0A955A),
                            Modifier
                                .clip(RoundedCornerShape(10.r))
                                .clickable {
                                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                                .padding(10.r),
                        )
                        if (photo != null) {
                            Spacer(Modifier.width(18.r))
                            RText(
                                "ছবি সরান",
                                19,
                                FontWeight.SemiBold,
                                C.Danger,
                                Modifier
                                    .clip(RoundedCornerShape(10.r))
                                    .clickable { vm.removePhoto() }
                                    .padding(10.r),
                            )
                        }
                    }
                }
            }
        }
        item {
            ListCard {
                RText("নাম", 19, FontWeight.Medium, C.StatLabel, Modifier.padding(bottom = 12.r))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Inter, fontSize = 22.rs, color = C.Ink),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(16.r),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF0BA765),
                        unfocusedBorderColor = C.FieldLine,
                        cursorColor = Color(0xFF0BA765),
                    ),
                    placeholder = { RText("আপনার নাম", 21, FontWeight.Normal, C.Hint) },
                )
                Spacer(Modifier.height(18.r))
                Button(
                    onClick = {
                        vm.setName(draft)
                        onBack()
                    },
                    enabled = draft.trim() != name,
                    modifier = Modifier.fillMaxWidth().height(74.r),
                    shape = RoundedCornerShape(18.r),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0BA765)),
                ) {
                    RText("সেভ করুন", 22, FontWeight.SemiBold, Color.White)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Weather city picker

@Composable
internal fun CityDialog(
    current: String?,
    onSearch: suspend (String) -> List<Place>?,
    onPick: (Place?) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun search() {
        if (query.isBlank() || busy) return
        busy = true
        status = "খুঁজছি…"
        scope.launch {
            val r = onSearch(query)
            busy = false
            when {
                r == null -> { results = emptyList(); status = "ইন্টারনেট সংযোগ পাওয়া যায়নি" }
                r.isEmpty() -> { results = emptyList(); status = "কিছু পাওয়া যায়নি। ইংরেজিতে লিখে দেখুন (যেমন: Dhaka)" }
                else -> { results = r; status = null }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = { RText("আবহাওয়ার লোকেশন", 28, FontWeight.Bold, C.Ink) },
        text = {
            Column {
                RText(
                    "ফোনের লোকেশন ব্যবহার করতে \"অটো\" রাখুন, অথবা শহরের নাম ইংরেজিতে লিখে খুঁজুন।",
                    19,
                    FontWeight.Normal,
                    C.StatLabelBn,
                    maxLines = 3,
                )
                Spacer(Modifier.height(14.r))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Inter, fontSize = 21.rs, color = C.Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search() }),
                        shape = RoundedCornerShape(16.r),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0BA765),
                            unfocusedBorderColor = C.FieldLine,
                            cursorColor = Color(0xFF0BA765),
                        ),
                        placeholder = { RText("যেমন: Dhaka", 20, FontWeight.Normal, C.Hint) },
                    )
                    Spacer(Modifier.width(10.r))
                    RText(
                        "খুঁজুন",
                        20,
                        FontWeight.SemiBold,
                        Color(0xFF0A955A),
                        Modifier
                            .clip(RoundedCornerShape(10.r))
                            .clickable { search() }
                            .padding(12.r),
                    )
                }
                status?.let {
                    Spacer(Modifier.height(10.r))
                    RText(it, 18, FontWeight.Normal, C.Hint, maxLines = 2)
                }
                Spacer(Modifier.height(8.r))
                Column(Modifier.heightIn(max = 420.r).verticalScroll(rememberScrollState())) {
                    results.forEach { p ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.r))
                                .clickable { onPick(p) }
                                .padding(vertical = 12.r, horizontal = 8.r)
                        ) {
                            RText(p.name, 21, FontWeight.Medium, C.Ink)
                            if (p.region.isNotBlank()) RText(p.region, 17, FontWeight.Normal, C.StatLabelBn)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(null) }) {
                RText("অটো (ফোনের লোকেশন)", 19, FontWeight.SemiBold, Color(0xFF0A955A))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                RText(if (current == null) "বন্ধ" else "বাতিল", 19, FontWeight.Medium, C.Hint)
            }
        },
    )
}
