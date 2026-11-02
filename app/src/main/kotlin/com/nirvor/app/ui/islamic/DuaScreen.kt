package com.nirvor.app.ui.islamic

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.islamic.DuaCategory
import com.nirvor.app.islamic.DuaData
import com.nirvor.app.islamic.DuaItem
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.tools.toolPrefs
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.ToolScaffold

private val DuaTeal = Color(0xFF0F766E)
private val DuaCardBg = Color(0xFFFBFDFB)

private fun vibrate(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator?.vibrate(30)
        }
    } catch (_: Exception) {}
}

@Composable
fun DuaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sp = remember { toolPrefs(context) }
    var search by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<DuaCategory?>(null) }
    var onlyFavs by remember { mutableStateOf(false) }

    var favIds by remember {
        mutableStateOf(sp.getStringSet("dua_favs", emptySet()) ?: emptySet())
    }

    // Interactive counts per dua ID
    val counts = remember { mutableStateMapOf<String, Int>() }

    val filtered = remember(search, selectedCategory, onlyFavs, favIds) {
        DuaData.items.filter { d ->
            val matchesCategory = selectedCategory == null || d.category == selectedCategory
            val matchesFav = !onlyFavs || favIds.contains(d.id)
            val q = search.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                d.title.lowercase().contains(q) ||
                d.meaning.lowercase().contains(q) ||
                d.pronunciation.lowercase().contains(q) ||
                d.reference.lowercase().contains(q)
            matchesCategory && matchesFav && matchesQuery
        }
    }

    ToolScaffold(
        title = "দোয়া ও আযকার",
        subtitle = "হিসনুল মুসলিম থেকে দৈনন্দিন সহীহ দোয়া ও ফজিলত",
        onBack = onBack,
    ) {
        // Search Field
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { RText("দোয়া খুঁজুন (যেমন: ঘুম, খাবার, ক্ষমা, বিপদ)", 15, FontWeight.Normal, C.Hint) },
            singleLine = true,
            shape = RoundedCornerShape(16.r),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DuaTeal,
                unfocusedBorderColor = C.TodayBorder,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        // Filter Categories
        Column(verticalArrangement = Arrangement.spacedBy(8.r)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.r),
            ) {
                ChoiceChip("সকল দোয়া", selectedCategory == null && !onlyFavs, color = DuaTeal) {
                    selectedCategory = null
                    onlyFavs = false
                }
                ChoiceChip("সকাল ও সন্ধ্যা", selectedCategory == DuaCategory.MorningEvening && !onlyFavs, color = DuaTeal) {
                    selectedCategory = DuaCategory.MorningEvening
                    onlyFavs = false
                }
                ChoiceChip("পছন্দ (${favIds.size})", onlyFavs, color = Gold) {
                    onlyFavs = true
                    selectedCategory = null
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.r),
            ) {
                ChoiceChip("দৈনন্দিন", selectedCategory == DuaCategory.Daily && !onlyFavs, color = DuaTeal) {
                    selectedCategory = DuaCategory.Daily
                    onlyFavs = false
                }
                ChoiceChip("নামাজ ও ইবাদত", selectedCategory == DuaCategory.Prayer && !onlyFavs, color = DuaTeal) {
                    selectedCategory = DuaCategory.Prayer
                    onlyFavs = false
                }
                ChoiceChip("বিপদ ও শেফা", selectedCategory == DuaCategory.Protection && !onlyFavs, color = DuaTeal) {
                    selectedCategory = DuaCategory.Protection
                    onlyFavs = false
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.r),
            ) {
                ChoiceChip("কুরআনের শ্রেষ্ঠ দোয়া", selectedCategory == DuaCategory.Quranic && !onlyFavs, color = DuaTeal) {
                    selectedCategory = DuaCategory.Quranic
                    onlyFavs = false
                }
            }
        }

        // Dua Cards
        filtered.forEach { d ->
            val isFav = favIds.contains(d.id)
            val currentCount = counts[d.id] ?: 0

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.r))
                    .background(Color.White)
                    .border(1.r, C.TodayBorder, RoundedCornerShape(20.r))
                    .padding(18.r),
            ) {
                // Category & Actions Row
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(d.category.bn, Mint, DuaTeal)
                    Spacer(Modifier.weight(1f))
                    // Copy button
                    RText(
                        "📋 কপি",
                        13,
                        FontWeight.Medium,
                        C.Hint,
                        Modifier
                            .clip(RoundedCornerShape(8.r))
                            .background(C.CalBg)
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val text = "${d.title}\n\n${d.arabic}\n\nউচ্চারণ: ${d.pronunciation}\n\nঅর্থ: ${d.meaning}\n\nতথ্যসূত্র: ${d.reference}"
                                cm.setPrimaryClip(ClipData.newPlainText("Dua", text))
                                Toast.makeText(context, "দোয়া কপি হয়েছে", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.r, vertical = 4.r),
                    )
                    Spacer(Modifier.width(8.r))
                    // Fav button
                    RText(
                        if (isFav) "★" else "☆",
                        22,
                        FontWeight.Bold,
                        if (isFav) Gold else C.Hint,
                        Modifier.clickable {
                            val next = if (isFav) favIds - d.id else favIds + d.id
                            favIds = next
                            sp.edit().putStringSet("dua_favs", next).apply()
                        },
                    )
                }

                Spacer(Modifier.height(10.r))

                // Title
                RText(d.title, 20, FontWeight.Bold, C.Ink)

                Spacer(Modifier.height(14.r))

                // Arabic Text
                RText(
                    d.arabic,
                    24,
                    FontWeight.Bold,
                    DuaTeal,
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.r))
                        .background(DuaCardBg)
                        .padding(14.r),
                    align = TextAlign.End,
                    lineHeight = 40,
                )

                Spacer(Modifier.height(12.r))

                // Pronunciation
                RText("উচ্চারণ:", 13, FontWeight.SemiBold, C.Hint)
                RText(d.pronunciation, 15, FontWeight.Medium, Color(0xFF8B6F2D), lineHeight = 22)

                Spacer(Modifier.height(8.r))

                // Meaning
                RText("অর্থ:", 13, FontWeight.SemiBold, C.Hint)
                RText(d.meaning, 16, FontWeight.Normal, C.Ink, lineHeight = 24)

                Spacer(Modifier.height(10.r))

                // Reference / Virtues
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.r))
                        .background(GoldSoft.copy(alpha = 0.5f))
                        .padding(horizontal = 12.r, vertical = 8.r),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RText("📖 ${d.reference}", 13, FontWeight.Medium, Color(0xFF825D0A))
                }

                // Interactive Counter (if target > 1)
                if (d.targetCount > 1) {
                    Spacer(Modifier.height(14.r))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.r))
                            .background(Mint)
                            .clickable {
                                val next = if (currentCount + 1 >= d.targetCount) {
                                    vibrate(context)
                                    Toast.makeText(context, "আলহামদুলিল্লাহ! ${d.targetCount} বার সম্পন্ন হয়েছে", Toast.LENGTH_SHORT).show()
                                    0
                                } else {
                                    vibrate(context)
                                    currentCount + 1
                                }
                                counts[d.id] = next
                            }
                            .padding(horizontal = 16.r, vertical = 10.r),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RText("ট্যাপ করে গুনুন (${bnNum(d.targetCount)} বার)", 15, FontWeight.SemiBold, DuaTeal)
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(36.r)
                                .clip(CircleShape)
                                .background(DuaTeal),
                            contentAlignment = Alignment.Center,
                        ) {
                            RText("${bnNum(currentCount)}/${bnNum(d.targetCount)}", 13, FontWeight.Bold, Color.White)
                        }
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(40.r),
                contentAlignment = Alignment.Center,
            ) {
                RText("কোনো দোয়া খুঁজে পাওয়া যায়নি", 17, FontWeight.Medium, C.Hint)
            }
        }
    }
}
