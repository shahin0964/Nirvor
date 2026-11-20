package com.nirvor.app.game

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nirvor.app.game.carrom.CarromStorage
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.util.Fmt

@Composable
fun GameHubScreen(
    storage: CarromStorage,
    onBack: () -> Unit,
    onOpenCarrom: () -> Unit,
    onOpenCarromStore: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .statusBarsPadding(),
    ) {
        // Top Bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.r)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.ChevronLeft, Color.White, Modifier.size(22.r))
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                RText("Game Hub (খেলার মেলা)", 21, FontWeight.Bold, Color.White)
                RText("দেশীয় ও নস্টালজিক গেমের ভুবন", 14, FontWeight.Normal, Color(0xFF94A3B8))
            }

            // Coin & Gem Counters
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFFD97706).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RText("🪙", 14, FontWeight.Normal, Color.White)
                    Spacer(Modifier.width(4.dp))
                    RText(Fmt.num(storage.coins.toLong()), 14, FontWeight.Bold, Color(0xFFFDE68A))
                }

                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RText("💎", 14, FontWeight.Normal, Color.White)
                    Spacer(Modifier.width(4.dp))
                    RText(Fmt.num(storage.gems.toLong()), 14, FontWeight.Bold, Color(0xFF93C5FD))
                }
            }
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // HERO CARD: Carrom Pool (Ready to Play!)
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1E3A8A), Color(0xFF1E293B), Color(0xFF0F172A))
                            )
                        )
                        .border(2.dp, Color(0xFF3B82F6).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                        .padding(20.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF059669))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            RText("🔥 খেলার জন্য প্রস্তুত (READY)", 12, FontWeight.Bold, Color.White)
                        }

                        RText("🏆 লেভেল ${bnNum(storage.playerLevel)}", 13, FontWeight.Bold, Color(0xFFF59E0B))
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD97706)),
                            contentAlignment = Alignment.Center,
                        ) {
                            RText("🎯", 36, FontWeight.Normal, Color.White)
                        }

                        Spacer(Modifier.width(16.dp))

                        Column {
                            RText("ক্যারম পুল (Carrom Pool)", 22, FontWeight.Bold, Color.White)
                            RText("রিয়েল ফিজিক্স, ডটেড এইম ও আপগ্রেড শপ", 13, FontWeight.Medium, Color(0xFF93C5FD))
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    RText(
                        "স্ট্রাইকার স্লাইডার, ডটেড এইমিং গাইডলাইন, ফিজিক্স বাউন্স, ২ প্লেয়ার মোড এবং স্মার্ট AI বটের সাথে খেলুন। প্রতি খেলায় জিতে কয়েন দিয়ে আনলক ও আপগ্রেড করুন নতুন সব স্ট্রাইকার!",
                        13,
                        FontWeight.Normal,
                        Color(0xFFCBD5E1),
                        lineHeight = 19,
                    )

                    Spacer(Modifier.height(18.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onOpenCarrom,
                            modifier = Modifier.weight(1.2f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            RText("▶ এখন খেলুন (Play)", 15, FontWeight.Bold, Color.White)
                        }

                        Button(
                            onClick = onOpenCarromStore,
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            RText("🛒 স্টোর ও আপগ্রেড", 13, FontWeight.Bold, Color.White)
                        }
                    }
                }
            }

            // Catalog Section Header
            item {
                RText("🎮 অন্যান্য জনপ্রিয় গেম (শীঘ্রই যুক্ত হচ্ছে)", 18, FontWeight.Bold, Color(0xFFFDE68A))
            }

            // Other games in catalog
            val upcomingGames = GameCatalog.entries.filter { !it.isReady }
            items(upcomingGames, key = { it.id }) { game ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(18.dp))
                        .clickable {
                            Toast.makeText(context, "${game.titleBn} গেমটি পরবর্তী আপডেটে উন্মুক্ত হবে!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF334155)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val icon = when (game) {
                            GameCatalog.SholoGuti -> "⚔️"
                            GameCatalog.PenFight -> "🖊️"
                            GameCatalog.Ludo -> "🎲"
                            GameCatalog.RetroSnake -> "🐍"
                            GameCatalog.Chess -> "♟️"
                            else -> "🎮"
                        }
                        RText(icon, 24, FontWeight.Normal, Color.White)
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RText(game.titleBn, 17, FontWeight.Bold, Color.White)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF64748B).copy(alpha = 0.3f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                RText("শীঘ্রই আসছে", 10, FontWeight.Medium, Color(0xFF94A3B8))
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        RText(game.description, 12, FontWeight.Normal, Color(0xFF94A3B8), maxLines = 2)
                    }
                }
            }

            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}
