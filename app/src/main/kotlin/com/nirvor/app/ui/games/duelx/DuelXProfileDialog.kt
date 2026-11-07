package com.nirvor.app.ui.games.duelx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r

@Composable
fun DuelXProfileDialog(
    profile: DuelXProfile,
    onSaveProfile: (DuelXProfile) -> Unit,
    onClose: () -> Unit,
) {
    var selectedAvatar by remember { mutableIntStateOf(profile.avatarIndex) }
    var selectedTable by remember { mutableIntStateOf(profile.selectedTableIndex) }
    var soundEnabled by remember { mutableStateOf(profile.soundEnabled) }
    var shotClock by remember { mutableIntStateOf(profile.shotClockSec) }
    var botLevel by remember { mutableStateOf(profile.botLevel) }

    val avatarList = listOf(
        Pair("🧔🏻‍♂️", Color(0xFFEF4444)),
        Pair("🧔🏽‍♂️", Color(0xFFF97316)),
        Pair("🧔🏾‍♂️", Color(0xFF10B981)),
        Pair("🧢", Color(0xFF06B6D4)),
        Pair("🥽", Color(0xFF8B5CF6)),
        Pair("👑", Color(0xFFF59E0B)),
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1120)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.r, vertical = 14.r)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    onSaveProfile(
                        profile.copy(
                            avatarIndex = selectedAvatar,
                            selectedTableIndex = selectedTable,
                            soundEnabled = soundEnabled,
                            shotClockSec = shotClock,
                            botLevel = botLevel,
                        )
                    )
                    onClose()
                }) {
                    GlyphIcon(Glyph.ArrowLeft, Color.White, Modifier.size(24.r), strokeWidth = 2.2f)
                }
                Spacer(Modifier.width(10.r))
                RText("PROFILE & SETTINGS", 22, FontWeight.ExtraBold, Color.White)
            }

            Spacer(Modifier.height(14.r))

            // Player Section Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.r))
                    .background(Color(0xFF131D31))
                    .border(1.r, Color(0xFF1E293B), RoundedCornerShape(16.r))
                    .padding(16.r),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RText("PLAYER", 13, FontWeight.Bold, Color(0xFF38BDF8))
                        RText("${profile.wins} WINS / ${profile.gamesPlayed} GAMES  •  ${profile.coins} COINS", 13, FontWeight.Bold, Color(0xFFFBBF24))
                    }

                    Spacer(Modifier.height(10.r))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Current Avatar
                        Box(
                            modifier = Modifier
                                .size(64.r)
                                .clip(RoundedCornerShape(14.r))
                                .background(Color(0xFF1E293B))
                                .border(2.5.r, Color(0xFF10B981), RoundedCornerShape(14.r)),
                            contentAlignment = Alignment.Center,
                        ) {
                            RText(avatarList[selectedAvatar].first, 36, FontWeight.Bold, Color.White)
                        }

                        Spacer(Modifier.width(14.r))

                        Column(modifier = Modifier.weight(1f)) {
                            RText("Nickname (3-16: letters, numbers, _)", 11, FontWeight.Medium, Color(0xFF64748B))
                            RText(profile.name, 22, FontWeight.ExtraBold, Color.White)
                        }

                        // Avatar Picker Row
                        Row(horizontalArrangement = Arrangement.spacedBy(6.r)) {
                            avatarList.forEachIndexed { idx, (emoji, bg) ->
                                val isSelected = idx == selectedAvatar
                                Box(
                                    modifier = Modifier
                                        .size(40.r)
                                        .clip(RoundedCornerShape(10.r))
                                        .background(bg.copy(alpha = 0.2f))
                                        .border(2.r, if (isSelected) Color(0xFF10B981) else Color.Transparent, RoundedCornerShape(10.r))
                                        .clickable { selectedAvatar = idx },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RText(emoji, 22, FontWeight.Bold, Color.White)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.r))

            // Table Section Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.r))
                    .background(Color(0xFF131D31))
                    .border(1.r, Color(0xFF1E293B), RoundedCornerShape(16.r))
                    .padding(16.r),
            ) {
                Column {
                    RText("TABLE", 13, FontWeight.Bold, Color(0xFF38BDF8))
                    Spacer(Modifier.height(10.r))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.r),
                    ) {
                        DuelXTables.styles.take(4).forEachIndexed { idx, table ->
                            val isSelected = idx == selectedTable
                            TableTile(table, isSelected, Modifier.weight(1f)) { selectedTable = idx }
                        }
                    }

                    Spacer(Modifier.height(10.r))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.r),
                    ) {
                        DuelXTables.styles.drop(4).take(4).forEachIndexed { i, table ->
                            val idx = i + 4
                            val isSelected = idx == selectedTable
                            TableTile(table, isSelected, Modifier.weight(1f)) { selectedTable = idx }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.r))

            // Sound, Shot Clock & Bot Level Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.r),
            ) {
                // Sound Card
                SettingBox("SOUND", Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.r)) {
                        ToggleBtn("ON", soundEnabled) { soundEnabled = true }
                        ToggleBtn("OFF", !soundEnabled) { soundEnabled = false }
                    }
                }

                // Shot Clock Card
                SettingBox("SHOT CLOCK", Modifier.weight(1.5f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.r)) {
                        listOf(20, 30, 45, 60).forEach { sec ->
                            ToggleBtn("${sec} SEC", shotClock == sec) { shotClock = sec }
                        }
                    }
                }

                // Bot Level Card
                SettingBox("BOT LEVEL", Modifier.weight(1.2f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.r)) {
                        listOf("EASY", "NORMAL", "HARD").forEach { lvl ->
                            ToggleBtn(lvl, botLevel == lvl) { botLevel = lvl }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.r))
        }
    }
}

@Composable
private fun TableTile(table: TableStyle, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.r))
            .background(Color(0xFF1E293B))
            .border(2.r, if (isSelected) Color(0xFF10B981) else Color(0xFF334155), RoundedCornerShape(10.r))
            .clickable(onClick = onClick)
            .padding(6.r),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.r)
                    .clip(RoundedCornerShape(6.r))
                    .background(Brush.linearGradient(table.clothGradient))
                    .border(1.r, table.borderColor, RoundedCornerShape(6.r)),
            )
            Spacer(Modifier.height(4.r))
            RText(table.name, 10, FontWeight.Bold, if (isSelected) Color(0xFF10B981) else Color(0xFFCBD5E1), maxLines = 1)
        }
    }
}

@Composable
private fun SettingBox(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.r))
            .background(Color(0xFF131D31))
            .border(1.r, Color(0xFF1E293B), RoundedCornerShape(14.r))
            .padding(12.r),
    ) {
        Column {
            RText(title, 11, FontWeight.Bold, Color(0xFF38BDF8))
            Spacer(Modifier.height(8.r))
            content()
        }
    }
}

@Composable
private fun ToggleBtn(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.r))
            .background(if (selected) Color(0xFF10B981) else Color(0xFF1E293B))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.r, vertical = 6.r),
        contentAlignment = Alignment.Center,
    ) {
        RText(text, 12, FontWeight.Bold, if (selected) Color.White else Color(0xFF94A3B8))
    }
}
