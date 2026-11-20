package com.nirvor.app.game.carrom

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.ListCard
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.util.Fmt

private enum class StoreTab(val labelBn: String, val icon: String) {
    Store("দোকান (Store)", "🛒"),
    Inventory("ইনভেন্টরি ও আপগ্রেড", "🎒"),
    Profile("প্রোফাইল ও বোনাস", "👑"),
}

private enum class StoreSubCategory(val labelBn: String) {
    Strikers("স্ট্রাইকার"),
    Boards("বোর্ড স্কিন"),
    Pucks("গুটি স্কিন"),
}

@Composable
fun CarromStoreScreen(
    storage: CarromStorage,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(StoreTab.Store) }
    var subCategory by remember { mutableStateOf(StoreSubCategory.Strikers) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .statusBarsPadding()
    ) {
        // Top Navigation Bar
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
                RText("ক্যারম শপ ও ইনভেন্টরি", 21, FontWeight.Bold, Color.White)
                RText("স্ট্রাইকার আপগ্রেড ও প্রিমিয়াম স্কিন", 14, FontWeight.Normal, Color(0xFF94A3B8))
            }

            // Coin & Gem Badges
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Coins
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFFD97706).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RText("🪙", 14, FontWeight.Normal, Color.White)
                    Spacer(Modifier.width(5.dp))
                    RText(Fmt.num(storage.coins.toLong()), 14, FontWeight.Bold, Color(0xFFFDE68A))
                }

                // Gems
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RText("💎", 14, FontWeight.Normal, Color.White)
                    Spacer(Modifier.width(5.dp))
                    RText(Fmt.num(storage.gems.toLong()), 14, FontWeight.Bold, Color(0xFF93C5FD))
                }
            }
        }

        // Main Store Tabs
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E293B))
                .padding(4.dp),
        ) {
            StoreTab.entries.forEach { tab ->
                val selected = currentTab == tab
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFFD97706) else Color.Transparent)
                        .clickable { currentTab = tab }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    RText(
                        "${tab.icon} ${tab.labelBn}",
                        14,
                        if (selected) FontWeight.Bold else FontWeight.Medium,
                        if (selected) Color.White else Color(0xFF94A3B8),
                        maxLines = 1,
                    )
                }
            }
        }

        when (currentTab) {
            StoreTab.Store -> {
                // Sub category picker (Strikers / Boards / Pucks)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StoreSubCategory.entries.forEach { sub ->
                        val active = subCategory == sub
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (active) Color(0xFF2563EB) else Color(0xFF1E293B))
                                .clickable { subCategory = sub }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            RText(
                                sub.labelBn,
                                13,
                                if (active) FontWeight.Bold else FontWeight.Normal,
                                if (active) Color.White else Color(0xFF94A3B8),
                            )
                        }
                    }
                }

                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (subCategory) {
                        StoreSubCategory.Strikers -> {
                            items(CarromStoreCatalog.STRIKERS, key = { it.id }) { item ->
                                val owned = storage.isStrikerOwned(item.id)
                                val equipped = storage.equippedStrikerId == item.id
                                val lv = storage.strikerLevel(item.id).coerceAtLeast(1)

                                StrikerCard(
                                    item = item,
                                    level = lv,
                                    isOwned = owned,
                                    isEquipped = equipped,
                                    canAfford = storage.coins >= item.priceCoins && storage.gems >= item.priceGems,
                                    onBuy = {
                                        if (storage.buyStriker(item)) {
                                            Toast.makeText(context, "${item.nameBn} সফলভাবে কেনা হয়েছে!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "পর্যাপ্ত কয়েন বা জেমস নেই!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onEquip = {
                                        storage.equipStriker(item.id)
                                        Toast.makeText(context, "${item.nameBn} স্ট্রাইকার সেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                        StoreSubCategory.Boards -> {
                            items(CarromStoreCatalog.BOARDS, key = { it.id }) { board ->
                                val owned = storage.isBoardOwned(board.id)
                                val equipped = storage.equippedBoardId == board.id
                                BoardCard(
                                    board = board,
                                    isOwned = owned,
                                    isEquipped = equipped,
                                    canAfford = storage.coins >= board.priceCoins && storage.gems >= board.priceGems,
                                    onBuy = {
                                        if (storage.buyBoard(board)) {
                                            Toast.makeText(context, "${board.nameBn} বোর্ড কেনা হয়েছে!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "পর্যাপ্ত কয়েন বা জেমস নেই!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onEquip = {
                                        storage.equipBoard(board.id)
                                        Toast.makeText(context, "${board.nameBn} বোর্ড সেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                        StoreSubCategory.Pucks -> {
                            items(CarromStoreCatalog.PUCKS, key = { it.id }) { puck ->
                                val owned = storage.isPuckOwned(puck.id)
                                val equipped = storage.equippedPuckId == puck.id
                                PuckCard(
                                    puck = puck,
                                    isOwned = owned,
                                    isEquipped = equipped,
                                    canAfford = storage.coins >= puck.priceCoins && storage.gems >= puck.priceGems,
                                    onBuy = {
                                        if (storage.buyPuck(puck)) {
                                            Toast.makeText(context, "${puck.nameBn} গুটি স্কিন কেনা হয়েছে!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "পর্যাপ্ত কয়েন বা জেমস নেই!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onEquip = {
                                        storage.equipPuck(puck.id)
                                        Toast.makeText(context, "${puck.nameBn} গুটি সেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }

            StoreTab.Inventory -> {
                // Inventory & Upgrade Screen
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        RText(
                            "আপনার স্ট্রাইকার ও আপগ্রেড সেন্টার",
                            18,
                            FontWeight.Bold,
                            Color(0xFFFDE68A),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        RText(
                            "লেভেল বাড়িয়ে পাওয়ার, এইম গাইডলাইন ও স্ট্যাবিলিটি সর্বোচ্চ করুন!",
                            13,
                            FontWeight.Normal,
                            Color(0xFF94A3B8),
                        )
                    }

                    val ownedStrikers = CarromStoreCatalog.STRIKERS.filter { storage.isStrikerOwned(it.id) }
                    items(ownedStrikers, key = { it.id }) { item ->
                        val level = storage.strikerLevel(item.id)
                        val isEquipped = storage.equippedStrikerId == item.id
                        val coinCost = item.upgradeCoinCost(level)
                        val gemCost = item.upgradeGemCost(level)
                        val canUpgrade = level < item.maxLevel && storage.coins >= coinCost && storage.gems >= gemCost

                        StrikerUpgradeCard(
                            item = item,
                            level = level,
                            isEquipped = isEquipped,
                            coinCost = coinCost,
                            gemCost = gemCost,
                            canUpgrade = canUpgrade,
                            onUpgrade = {
                                if (storage.upgradeStriker(item)) {
                                    Toast.makeText(context, "${item.nameBn} লেভেল ${level + 1}-এ উন্নীত হয়েছে! 🚀", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "আপগ্রেড করার মতো পর্যাপ্ত কয়েন/জেমস নেই!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onEquip = {
                                storage.equipStriker(item.id)
                                Toast.makeText(context, "${item.nameBn} সজ্জিত করা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }

            StoreTab.Profile -> {
                // Profile, Stats & Daily Free Coins/Gems
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        // Level and Rank Card
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF334155))))
                                .padding(20.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFD97706)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    RText("🏆", 32, FontWeight.Normal, Color.White)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    RText("ক্যারম মাস্টার (লেভেল ${bnNum(storage.playerLevel)})", 20, FontWeight.Bold, Color.White)
                                    RText("পরবর্তী লেভেলে পৌঁছাতে আরও এক্সপি অর্জন করুন", 13, FontWeight.Normal, Color(0xFFCBD5E1))
                                }
                            }

                            Spacer(Modifier.height(14.dp))
                            val neededXp = storage.playerLevel * 300
                            val progress = (storage.playerXp.toFloat() / neededXp).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                color = Color(0xFFF59E0B),
                                trackColor = Color(0xFF475569),
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                RText("XP: ${bnNum(storage.playerXp)} / ${bnNum(neededXp)}", 12, FontWeight.Medium, Color(0xFF94A3B8))
                                RText("লেভেল ${bnNum(storage.playerLevel + 1)}", 12, FontWeight.Bold, Color(0xFFFDE68A))
                            }
                        }
                    }

                    // Free Daily Gift Button
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF047857))))
                                .clickable {
                                    val (c, g) = storage.claimDailyBonus()
                                    Toast.makeText(context, "বোনাস উপহার: +$c কয়েন ও +$g জেমস পেলেন! 🎉", Toast.LENGTH_LONG).show()
                                }
                                .padding(18.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RText("🎁", 36, FontWeight.Normal, Color.White)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    RText("ফ্রি টেস্ট বোনাস (মুহূর্তেই কয়েন ও জেম পান)", 16, FontWeight.Bold, Color.White)
                                    RText("ট্যাপ করলেই +৫০০ কয়েন ও +১০ জেম যোগ হবে স্টোরে কেনাকাটার জন্য!", 13, FontWeight.Normal, Color(0xFFA7F3D0))
                                }
                            }
                        }
                    }

                    // Match Statistics
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF1E293B))
                                .padding(18.dp),
                        ) {
                            RText("📊 ক্যারম পরিসংখ্যান (Stats)", 17, FontWeight.Bold, Color.White)
                            Spacer(Modifier.height(14.dp))

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                StatItem("মোট ম্যাচ", bnNum(storage.matchesPlayed))
                                StatItem("জয়ী ম্যাচ", bnNum(storage.matchesWon))
                                val winRate = if (storage.matchesPlayed > 0) (storage.matchesWon * 100 / storage.matchesPlayed) else 0
                                StatItem("জয়ের হার", "${bnNum(winRate)}%")
                                StatItem("বেস্ট কম্বো", bnNum(storage.bestCombo))
                            }
                        }
                    }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatItem(title: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RText(value, 18, FontWeight.Bold, Color(0xFFF59E0B))
        Spacer(Modifier.height(4.dp))
        RText(title, 12, FontWeight.Normal, Color(0xFF94A3B8))
    }
}

@Composable
private fun StrikerCard(
    item: CarromStrikerItem,
    level: Int,
    isOwned: Boolean,
    isEquipped: Boolean,
    canAfford: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E293B))
            .border(
                1.5.dp,
                if (isEquipped) Color(0xFFF59E0B) else Color(0xFF334155),
                RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Striker Visual Preview
            Box(
                Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(item.primaryColor)
                    .border(3.dp, item.ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(item.coreColor),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RText(item.nameBn, 18, FontWeight.Bold, Color.White)
                    Spacer(Modifier.width(8.dp))
                    if (isOwned) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFD97706).copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            RText("লেভেল ${bnNum(level)}", 11, FontWeight.Bold, Color(0xFFFDE68A))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                RText(item.descriptionBn, 13, FontWeight.Normal, Color(0xFF94A3B8), maxLines = 2)
            }

            // Buy or Equip Button
            if (isEquipped) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF059669))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    RText("সজ্জিত", 13, FontWeight.Bold, Color.White)
                }
            } else if (isOwned) {
                Button(
                    onClick = onEquip,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    RText("ব্যবহার করুন", 13, FontWeight.SemiBold, Color.White)
                }
            } else {
                Button(
                    onClick = onBuy,
                    colors = ButtonDefaults.buttonColors(containerColor = if (canAfford) Color(0xFFD97706) else Color(0xFF475569)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        RText(
                            if (item.priceGems > 0) "🪙 ${bnNum(item.priceCoins)} + 💎 ${bnNum(item.priceGems)}" else "🪙 ${bnNum(item.priceCoins)}",
                            13,
                            FontWeight.Bold,
                            Color.White,
                        )
                        RText("আনলক", 11, FontWeight.Normal, Color(0xFFFEF3C7))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Attribute Bars (Power, Aim, Time)
        AttributeBar("পাওয়ার (Power)", item.power(level), Color(0xFFEF4444))
        Spacer(Modifier.height(6.dp))
        AttributeBar("এইম গাইডলাইন (Aim)", item.aim(level), Color(0xFF10B981))
        Spacer(Modifier.height(6.dp))
        AttributeBar("স্থিতিশীলতা (Control)", item.time(level), Color(0xFF3B82F6))
    }
}

@Composable
private fun AttributeBar(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RText(label, 12, FontWeight.Medium, Color(0xFFCBD5E1), modifier = Modifier.width(135.dp))
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
            color = color,
            trackColor = Color(0xFF334155),
        )
        Spacer(Modifier.width(10.dp))
        RText("${bnNum(value)}%", 11, FontWeight.Bold, Color.White, modifier = Modifier.width(36.dp), align = TextAlign.End)
    }
}

@Composable
private fun StrikerUpgradeCard(
    item: CarromStrikerItem,
    level: Int,
    isEquipped: Boolean,
    coinCost: Int,
    gemCost: Int,
    canUpgrade: Boolean,
    onUpgrade: () -> Unit,
    onEquip: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E293B))
            .border(
                1.5.dp,
                if (isEquipped) Color(0xFFF59E0B) else Color(0xFF334155),
                RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(item.primaryColor)
                    .border(3.dp, item.ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(item.coreColor))
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                RText(item.nameBn, 18, FontWeight.Bold, Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RText("লেভেল ${bnNum(level)} / ${bnNum(item.maxLevel)}", 13, FontWeight.Bold, Color(0xFFF59E0B))
                    if (isEquipped) {
                        Spacer(Modifier.width(8.dp))
                        RText("• সজ্জিত স্ট্রাইকার", 12, FontWeight.Normal, Color(0xFF34D399))
                    }
                }
            }

            if (!isEquipped) {
                Button(
                    onClick = onEquip,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    RText("ব্যবহার করুন", 12, FontWeight.Medium, Color.White)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Level Progression Bars with Next Boost preview
        val nextLevel = (level + 1).coerceAtMost(item.maxLevel)
        AttributeUpgradeRow("পাওয়ার", item.power(level), item.power(nextLevel), Color(0xFFEF4444))
        AttributeUpgradeRow("এইম গাইড", item.aim(level), item.aim(nextLevel), Color(0xFF10B981))
        AttributeUpgradeRow("কন্ট্রোল", item.time(level), item.time(nextLevel), Color(0xFF3B82F6))

        Spacer(Modifier.height(14.dp))

        if (level < item.maxLevel) {
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth(),
                enabled = canUpgrade,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF059669),
                    disabledContainerColor = Color(0xFF334155),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                val costStr = if (gemCost > 0) "🪙 ${bnNum(coinCost)} + 💎 ${bnNum(gemCost)}" else "🪙 ${bnNum(coinCost)}"
                RText("লেভেল ${bnNum(level + 1)}-এ আপগ্রেড করুন ($costStr)", 14, FontWeight.Bold, Color.White)
            }
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFD97706).copy(alpha = 0.2f))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                RText("⭐ সর্বোচ্চ লেভেল অর্জিত হয়েছে (MAX LEVEL)", 13, FontWeight.Bold, Color(0xFFFDE68A))
            }
        }
    }
}

@Composable
private fun AttributeUpgradeRow(label: String, cur: Int, next: Int, color: Color) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        RText(label, 12, FontWeight.Medium, Color(0xFF94A3B8), modifier = Modifier.width(90.dp))
        LinearProgressIndicator(
            progress = { cur / 100f },
            modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
            color = color,
            trackColor = Color(0xFF334155),
        )
        Spacer(Modifier.width(10.dp))
        RText("${bnNum(cur)}%", 11, FontWeight.Bold, Color.White)
        if (next > cur) {
            RText(" ➔ +${bnNum(next - cur)}%", 11, FontWeight.Bold, Color(0xFF34D399))
        }
    }
}

@Composable
private fun BoardCard(
    board: CarromBoardSkin,
    isOwned: Boolean,
    isEquipped: Boolean,
    canAfford: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E293B))
            .border(
                1.5.dp,
                if (isEquipped) Color(0xFFF59E0B) else Color(0xFF334155),
                RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Board visual thumbnail preview
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(board.woodBorderColor)
                    .padding(5.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(board.surfaceColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(board.ringColor),
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                RText(board.nameBn, 17, FontWeight.Bold, Color.White)
                RText(board.nameEn, 13, FontWeight.Normal, Color(0xFF94A3B8))
            }

            if (isEquipped) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF059669))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    RText("নির্বাচিত", 13, FontWeight.Bold, Color.White)
                }
            } else if (isOwned) {
                Button(
                    onClick = onEquip,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    RText("সেট করুন", 13, FontWeight.SemiBold, Color.White)
                }
            } else {
                Button(
                    onClick = onBuy,
                    colors = ButtonDefaults.buttonColors(containerColor = if (canAfford) Color(0xFFD97706) else Color(0xFF475569)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    RText(
                        if (board.priceGems > 0) "🪙 ${bnNum(board.priceCoins)} + 💎 ${bnNum(board.priceGems)}" else "🪙 ${bnNum(board.priceCoins)}",
                        13,
                        FontWeight.Bold,
                        Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun PuckCard(
    puck: CarromPuckSkin,
    isOwned: Boolean,
    isEquipped: Boolean,
    canAfford: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E293B))
            .border(
                1.5.dp,
                if (isEquipped) Color(0xFFF59E0B) else Color(0xFF334155),
                RoundedCornerShape(18.dp),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Three mini pucks preview
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(puck.whiteDiscColor))
                Box(Modifier.size(18.dp).clip(CircleShape).background(puck.blackDiscColor))
                Box(Modifier.size(18.dp).clip(CircleShape).background(puck.queenDiscColor))
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                RText(puck.nameBn, 17, FontWeight.Bold, Color.White)
                RText(puck.nameEn, 13, FontWeight.Normal, Color(0xFF94A3B8))
            }

            if (isEquipped) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF059669))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    RText("নির্বাচিত", 13, FontWeight.Bold, Color.White)
                }
            } else if (isOwned) {
                Button(
                    onClick = onEquip,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    RText("সেট করুন", 13, FontWeight.SemiBold, Color.White)
                }
            } else {
                Button(
                    onClick = onBuy,
                    colors = ButtonDefaults.buttonColors(containerColor = if (canAfford) Color(0xFFD97706) else Color(0xFF475569)),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    RText(
                        if (puck.priceGems > 0) "🪙 ${bnNum(puck.priceCoins)} + 💎 ${bnNum(puck.priceGems)}" else "🪙 ${bnNum(puck.priceCoins)}",
                        13,
                        FontWeight.Bold,
                        Color.White,
                    )
                }
            }
        }
    }
}
