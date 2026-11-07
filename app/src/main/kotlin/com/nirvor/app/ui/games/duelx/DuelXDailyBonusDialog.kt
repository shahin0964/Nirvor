package com.nirvor.app.ui.games.duelx

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r

@Composable
fun DuelXDailyBonusDialog(
    onClaim: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var claimed by remember { mutableStateOf(false) }

    val days = listOf(
        Pair("DAY 1", "200"),
        Pair("DAY 2", "300"),
        Pair("DAY 3", "400"),
        Pair("DAY 4", "500"),
        Pair("DAY 5", "700"),
        Pair("DAY 6", "900"),
        Pair("DAY 7", "1,500\n+5 GEMS"),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(22.r))
                .background(Color(0xFF0F172A))
                .border(2.r, Color(0xFFFBBF24), RoundedCornerShape(22.r))
                .clickable(enabled = false) {}
                .padding(20.r),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RText("DAILY BONUS", 26, FontWeight.ExtraBold, Color(0xFFFBBF24))
                Spacer(Modifier.height(4.r))
                RText("COME BACK EVERY DAY - THE REWARD GROWS", 13, FontWeight.Bold, Color(0xFF94A3B8))

                Spacer(Modifier.height(18.r))

                // 7 Day Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.r),
                ) {
                    days.forEachIndexed { index, (dayLabel, rewardText) ->
                        val isDay1 = index == 0
                        val cardBg = if (isDay1 && !claimed) Color(0xFF1E293B) else Color(0xFF141E33)
                        val borderColor = if (isDay1 && !claimed) Color(0xFF10B981) else Color(0xFF334155)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.r))
                                .background(cardBg)
                                .border(1.5.r, borderColor, RoundedCornerShape(10.r))
                                .padding(vertical = 10.r, horizontal = 4.r),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RText(dayLabel, 11, FontWeight.Bold, if (isDay1) Color(0xFF34D399) else Color(0xFF64748B))
                            Spacer(Modifier.height(6.r))
                            // Gold 9 coin badge
                            Box(
                                modifier = Modifier
                                    .size(24.r)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF59E0B)),
                                contentAlignment = Alignment.Center,
                            ) {
                                RText("9", 14, FontWeight.Bold, Color.Black)
                            }
                            Spacer(Modifier.height(6.r))
                            RText(rewardText, 12, FontWeight.Bold, Color.White, align = TextAlign.Center)
                        }
                    }
                }

                Spacer(Modifier.height(20.r))

                if (claimed) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.r))
                            .background(Color(0xFF065F46))
                            .padding(horizontal = 20.r, vertical = 8.r),
                    ) {
                        RText("+200 COINS CLAIMED! 🎉", 16, FontWeight.Bold, Color(0xFF34D399))
                    }
                    Spacer(Modifier.height(14.r))
                }

                // Action Buttons: CLAIM & CLOSE
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.r),
                ) {
                    if (!claimed) {
                        Button(
                            onClick = {
                                claimed = true
                                onClaim(200)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(14.r),
                            modifier = Modifier.width(140.r).height(46.r),
                        ) {
                            RText("CLAIM", 16, FontWeight.Bold, Color.White)
                        }
                    }

                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(14.r),
                        modifier = Modifier.width(140.r).height(46.r),
                    ) {
                        RText("CLOSE", 16, FontWeight.Bold, Color.White)
                    }
                }
            }
        }
    }
}
