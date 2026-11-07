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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r

@Composable
fun DuelXShopDialog(
    onBuyCoins: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val coinPacks = listOf(
        Pair("5,000", 5000),
        Pair("15K", 15000),
        Pair("40K", 40000),
        Pair("100K", 100000),
        Pair("300K", 300000),
    )

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1120)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.r, vertical = 14.r),
        ) {
            // Header with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    GlyphIcon(Glyph.ArrowLeft, Color.White, Modifier.size(24.r), strokeWidth = 2.2f)
                }
                Spacer(Modifier.width(10.r))
                RText("SHOP", 22, FontWeight.ExtraBold, Color.White)
            }

            Spacer(Modifier.height(14.r))

            RText("COINS", 13, FontWeight.Bold, Color(0xFF38BDF8))
            Spacer(Modifier.height(8.r))

            // Coins row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.r),
            ) {
                coinPacks.forEach { (label, amount) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.r))
                            .background(Color(0xFF131D31))
                            .border(1.r, Color(0xFF1E293B), RoundedCornerShape(12.r))
                            .clickable { onBuyCoins(amount) }
                            .padding(vertical = 12.r, horizontal = 4.r),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.r)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B)),
                            contentAlignment = Alignment.Center,
                        ) {
                            RText("9", 18, FontWeight.Bold, Color.Black)
                        }
                        Spacer(Modifier.height(8.r))
                        RText(label, 14, FontWeight.ExtraBold, Color.White)
                        Spacer(Modifier.height(6.r))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.r))
                                .background(Color(0xFF059669))
                                .padding(horizontal = 8.r, vertical = 3.r),
                        ) {
                            RText("CLAIM", 10, FontWeight.Bold, Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.r))

            RText("GEMS & CUES", 13, FontWeight.Bold, Color(0xFF38BDF8))
            Spacer(Modifier.height(8.r))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.r),
            ) {
                listOf(Pair("20", "GEMS"), Pair("60", "GEMS"), Pair("150", "GEMS")).forEach { (num, lbl) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.r))
                            .background(Color(0xFF131D31))
                            .border(1.r, Color(0xFF1E293B), RoundedCornerShape(12.r))
                            .padding(vertical = 12.r, horizontal = 4.r),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.r)
                                .clip(CircleShape)
                                .background(Color(0xFF8B5CF6)),
                            contentAlignment = Alignment.Center,
                        ) {
                            RText("✦", 18, FontWeight.Bold, Color.White)
                        }
                        Spacer(Modifier.height(8.r))
                        RText(num, 14, FontWeight.ExtraBold, Color.White)
                        RText(lbl, 10, FontWeight.Medium, Color(0xFF94A3B8))
                    }
                }

                // Cue Shop Card
                Box(
                    modifier = Modifier
                        .weight(2f)
                        .clip(RoundedCornerShape(12.r))
                        .background(Color(0xFF131D31))
                        .border(1.r, Color(0xFF3B82F6), RoundedCornerShape(12.r))
                        .padding(14.r),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RText("🎱", 28, FontWeight.Bold, Color.White)
                        Spacer(Modifier.width(10.r))
                        Column {
                            RText("CUE SHOP", 14, FontWeight.ExtraBold, Color.White)
                            RText("7 Cues to unlock with coins or gems", 11, FontWeight.Medium, Color(0xFF94A3B8))
                        }
                    }
                }
            }
        }
    }
}
