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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import kotlin.random.Random

@Composable
fun DuelXPlayFriendsDialog(
    onSelectGame: (String) -> Unit,
    onClose: () -> Unit,
) {
    val roomCode = remember { "#DX-${Random.nextInt(1000, 9999)}" }
    var copied by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .clip(RoundedCornerShape(22.r))
                .background(Color(0xFF0F172A))
                .border(2.r, Color(0xFF3B82F6), RoundedCornerShape(22.r))
                .clickable(enabled = false) {}
                .padding(22.r),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RText("PLAY WITH FRIENDS", 22, FontWeight.ExtraBold, Color.White)
                Spacer(Modifier.height(4.r))
                RText(
                    "A private room with a code to share. Friendly matches are free: no buy-in, no prize.",
                    12,
                    FontWeight.Medium,
                    Color(0xFF94A3B8),
                    align = TextAlign.Center,
                )

                Spacer(Modifier.height(14.r))

                // Room Code Box
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.r))
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 16.r, vertical = 6.r),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RText("ROOM CODE: ", 12, FontWeight.Bold, Color(0xFF94A3B8))
                    RText(roomCode, 16, FontWeight.ExtraBold, Color(0xFF38BDF8))
                    Spacer(Modifier.width(10.r))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.r))
                            .background(Color(0xFF3B82F6))
                            .clickable { copied = true }
                            .padding(horizontal = 8.r, vertical = 4.r),
                    ) {
                        RText(if (copied) "COPIED!" else "COPY", 10, FontWeight.Bold, Color.White)
                    }
                }

                Spacer(Modifier.height(16.r))

                // Game Buttons (Matches video 00:20 - 00:21)
                val games = listOf("BILLIARD POKER", "8-BALL", "9-BALL", "10-BALL", "CARROM")
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.r),
                ) {
                    games.forEach { g ->
                        Button(
                            onClick = { onSelectGame(g) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (g == "BILLIARD POKER") Color(0xFF8B5CF6) else Color(0xFF3B82F6),
                            ),
                            shape = RoundedCornerShape(12.r),
                            modifier = Modifier.fillMaxWidth().height(44.r),
                        ) {
                            RText(g, 15, FontWeight.Bold, Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(14.r))

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(10.r),
                ) {
                    RText("CLOSE", 13, FontWeight.Bold, Color.White)
                }
            }
        }
    }
}
