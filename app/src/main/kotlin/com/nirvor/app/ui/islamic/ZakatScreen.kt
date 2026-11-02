package com.nirvor.app.ui.islamic

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.islamic.bnDigits
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.tools.ChoiceChip
import com.nirvor.app.ui.tools.ToolScaffold
import java.text.NumberFormat
import java.util.Locale

private val ZakatGreenA = Color(0xFF047857)
private val ZakatGreenB = Color(0xFF064E3B)
private val ZakatGold = Color(0xFFF59E0B)

private fun fmtTaka(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.maximumFractionDigits = 2
    nf.minimumFractionDigits = 0
    return "৳ " + bnDigits(nf.format(amount))
}

@Composable
fun ZakatScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Calculator, 1: 8 Beneficiaries, 2: Rules

    // Assets inputs
    var goldSilver by remember { mutableStateOf("") }
    var cashBank by remember { mutableStateOf("") }
    var businessGoods by remember { mutableStateOf("") }
    var investments by remember { mutableStateOf("") }
    var receivables by remember { mutableStateOf("") }

    // Liabilities inputs
    var debts by remember { mutableStateOf("") }
    var billsDue by remember { mutableStateOf("") }

    // Nisab threshold (Default Silver Nisab ~ 120,000 BDT)
    var nisabThreshold by remember { mutableDoubleStateOf(120_000.0) }
    var isEditingNisab by remember { mutableStateOf(false) }
    var nisabInput by remember { mutableStateOf("120000") }

    val totalAssets = remember(goldSilver, cashBank, businessGoods, investments, receivables) {
        (goldSilver.toDoubleOrNull() ?: 0.0) +
            (cashBank.toDoubleOrNull() ?: 0.0) +
            (businessGoods.toDoubleOrNull() ?: 0.0) +
            (investments.toDoubleOrNull() ?: 0.0) +
            (receivables.toDoubleOrNull() ?: 0.0)
    }

    val totalLiabilities = remember(debts, billsDue) {
        (debts.toDoubleOrNull() ?: 0.0) + (billsDue.toDoubleOrNull() ?: 0.0)
    }

    val netWealth = (totalAssets - totalLiabilities).coerceAtLeast(0.0)
    val isSahibNisab = netWealth >= nisabThreshold
    val zakatPayable = if (isSahibNisab) netWealth * 0.025 else 0.0

    ToolScaffold(
        title = "যাকাত ক্যালকুলেটর",
        sub = "ইসলামিক শরিয়াহ অনুযায়ী নির্ভুল যাকাত ও নিসাব হিসাব",
        onBack = onBack,
    ) {
        // Result Summary Header
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.r))
                .background(Brush.linearGradient(listOf(ZakatGreenA, ZakatGreenB)))
                .padding(22.r),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("💰 প্রদেয় যাকাত (২.৫%)", 16, FontWeight.Bold, Color.White.copy(alpha = 0.9f), Modifier.weight(1f))
                Pill(
                    if (isSahibNisab) "✓ যাকাত ফরজ" else "নিসাবের নিচে",
                    if (isSahibNisab) Color(0xFF6EE7B7) else Color.White.copy(alpha = 0.2f),
                    if (isSahibNisab) Color(0xFF064E3B) else Color.White,
                )
            }

            Spacer(Modifier.height(10.r))

            RText(fmtTaka(zakatPayable), 44, FontWeight.Bold, if (isSahibNisab) ZakatGold else Color.White)

            Spacer(Modifier.height(14.r))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(Modifier.height(14.r))

            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    RText("মোট সম্পদ", 13, FontWeight.Normal, Color.White.copy(alpha = 0.75f))
                    RText(fmtTaka(totalAssets), 16, FontWeight.Bold, Color.White)
                }
                Column(Modifier.weight(1f)) {
                    RText("মোট ঋণ/দেনা", 13, FontWeight.Normal, Color.White.copy(alpha = 0.75f))
                    RText(fmtTaka(totalLiabilities), 16, FontWeight.Bold, Color.White)
                }
                Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                    RText("নিট যাকাতযোগ্য সম্পদ", 13, FontWeight.Normal, Color.White.copy(alpha = 0.75f))
                    RText(fmtTaka(netWealth), 16, FontWeight.Bold, if (isSahibNisab) Color(0xFF6EE7B7) else Color.White)
                }
            }
        }

        // Tab selection
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.r),
        ) {
            ChoiceChip("হিসাব ক্যালকুলেটর", selectedTab == 0, color = ZakatGreenA) { selectedTab = 0 }
            ChoiceChip("যাকাতের ৮টি খাত", selectedTab == 1, color = ZakatGreenA) { selectedTab = 1 }
            ChoiceChip("জরুরি নিয়মাবলি", selectedTab == 2, color = ZakatGreenA) { selectedTab = 2 }
        }

        when (selectedTab) {
            0 -> {
                // Nisab Banner & Editor
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.r))
                        .background(GoldSoft)
                        .border(1.r, Color(0xFFE2C988), RoundedCornerShape(18.r))
                        .padding(16.r),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            RText("বর্তমান নিসাব মানদণ্ড (রূপা: ৫২.৫ তোলা)", 14, FontWeight.SemiBold, Color(0xFF8B6F2D))
                            RText(fmtTaka(nisabThreshold), 20, FontWeight.Bold, Color(0xFF78350F))
                        }
                        RText(
                            if (isEditingNisab) "সংরক্ষণ" else "পরিবর্তন",
                            14,
                            FontWeight.Bold,
                            ZakatGreenA,
                            Modifier
                                .clip(RoundedCornerShape(10.r))
                                .background(Color.White)
                                .clickable {
                                    if (isEditingNisab) {
                                        nisabThreshold = nisabInput.toDoubleOrNull() ?: 120_000.0
                                    }
                                    isEditingNisab = !isEditingNisab
                                }
                                .padding(horizontal = 14.r, vertical = 6.r),
                        )
                    }
                    if (isEditingNisab) {
                        Spacer(Modifier.height(10.r))
                        OutlinedTextField(
                            value = nisabInput,
                            onValueChange = { nisabInput = it },
                            label = { RText("নিসাব পরিমাণ (টাকায়)", 13, FontWeight.Normal, C.Hint) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(12.r),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Assets Input Fields
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.r))
                        .background(Color.White)
                        .border(1.r, C.TodayBorder, RoundedCornerShape(20.r))
                        .padding(18.r),
                    verticalArrangement = Arrangement.spacedBy(12.r),
                ) {
                    RText("১. যাকাতযোগ্য সম্পদসমূহ (টাকায়)", 18, FontWeight.Bold, C.Ink)

                    ZakatInputField("🪙 স্বর্ণ ও রূপার বাজারমূল্য", goldSilver) { goldSilver = it }
                    ZakatInputField("💵 নগদ টাকা ও ব্যাংক ব্যালেন্স (সেভিংস, ডিপিএস)", cashBank) { cashBank = it }
                    ZakatInputField("🏬 ব্যবসায়িক পণ্য ও মজুদের পাইকারি মূল্য", businessGoods) { businessGoods = it }
                    ZakatInputField("📈 বিনিয়োগ, শেয়ার ও প্রাইজবন্ড", investments) { investments = it }
                    ZakatInputField("🤝 ফেরতযোগ্য পাওনা ঋণ", receivables) { receivables = it }
                }

                // Liabilities / Deductions Input Fields
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.r))
                        .background(Color.White)
                        .border(1.r, C.TodayBorder, RoundedCornerShape(20.r))
                        .padding(18.r),
                    verticalArrangement = Arrangement.spacedBy(12.r),
                ) {
                    RText("২. বাদ দেওয়ার মতো ঋণ ও চলতি দেনা (টাকায়)", 18, FontWeight.Bold, Color(0xFFC0392B))

                    ZakatInputField("💳 ব্যক্তিগত ঋণ ও বর্তমান প্রদেয় কিস্তি", debts) { debts = it }
                    ZakatInputField("👥 বকেয়া বেতন, বাড়িভাড়া ও ইউটিলিটি বিল", billsDue) { billsDue = it }
                }

                // Clear button
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    RText(
                        "🗑️ হিসাব রিসেট করুন",
                        15,
                        FontWeight.SemiBold,
                        Color(0xFFC0392B),
                        Modifier
                            .clip(RoundedCornerShape(12.r))
                            .background(Color(0xFFFDE8E8))
                            .clickable {
                                goldSilver = ""
                                cashBank = ""
                                businessGoods = ""
                                investments = ""
                                receivables = ""
                                debts = ""
                                billsDue = ""
                            }
                            .padding(horizontal = 20.r, vertical = 10.r),
                    )
                }
            }
            1 -> EightBeneficiariesView()
            2 -> ZakatRulesView()
        }
    }
}

@Composable
private fun ZakatInputField(label: String, value: String, onChange: (String) -> Unit) {
    Column {
        RText(label, 14, FontWeight.Medium, C.Ink)
        Spacer(Modifier.height(4.r))
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                if (input.isEmpty() || input.all { it.isDigit() || it == '.' }) {
                    onChange(input)
                }
            },
            placeholder = { RText("০.০০", 15, FontWeight.Normal, C.Hint) },
            prefix = { RText("৳ ", 15, FontWeight.Bold, C.Ink) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = RoundedCornerShape(14.r),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ZakatGreenA,
                unfocusedBorderColor = C.TodayBorder,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EightBeneficiariesView() {
    val items = listOf(
        "১. ফকির / দরিদ্র" to "যার কাছে অতি সামান্য অর্থ বা জীবিকা রয়েছে কিন্তু তা দিয়ে মৌলিক প্রয়োজন মেটে না।",
        "২. মিসকিন / নিঃস্ব" to "যার নিজের কোনো সম্বল বা সম্পদ নেই এবং মানুষের কাছে হাত পাততেও লজ্জা পায়।",
        "৩. যাকাত সংগ্রাহক ও কর্মচারী" to "ইসলামি রাষ্ট্র বা সংস্থা কর্তৃক যাকাত আদায় ও বণ্টনের দায়িত্বপ্রাপ্ত কর্মকর্তা।",
        "৪. নও-মুসলিম বা অমুসলিমদের মন জয়" to "যাদের অন্তরে ইসলামের প্রতি সহানুভূতি ও ভালোবাসা সৃষ্টির প্রয়োজন রয়েছে।",
        "৫. দাসমুক্তি / বন্দিমুক্তি" to "দাসত্ব বা অন্যায় বন্দিদশা থেকে মুক্ত করার উদ্দেশ্যে আর্থিক সহায়তা।",
        "৬. ঋণগ্রস্ত ব্যক্তি" to "যে ব্যক্তি সৎ উদ্দেশ্যে ঋণগ্রস্ত হয়ে তা পরিশোধ করতে সম্পূর্ণ অক্ষম হয়ে পড়েছে।",
        "৭. ফি সাবিলিল্লাহ (আল্লাহর পথে)" to "দ্বীনের হেফাজত, ইসলাম প্রচার ও সত্য প্রতিষ্ঠার কাজে নিয়োজিত ব্যক্তিদের কল্যাণে।",
        "৮. মুসাফির / বিপদগ্রস্ত পথিক" to "ভ্রমণকালে অর্থকষ্টে পড়ে নিঃস্ব হয়ে যাওয়া মুসাফির, নিজ দেশে ধনী হলেও তাকে সাহায্য করা জায়েজ।",
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.r)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.r))
                .background(Mint)
                .padding(16.r),
        ) {
            RText(
                "\"যাকাত কেবল ফকির, মিসকিন, যাকাত আদায়কারী কর্মচারী এবং যাদের মন জয় করা উদ্দেশ্য তাদের জন্য; এবং দাসমুক্তির কাজে, ঋণগ্রস্তদের ঋণ পরিশোধে, আল্লাহর পথে ও মুসাফিরদের জন্য। এটি আল্লাহর নির্ধারিত বিধান।\" (সূরা আত-তাওবাহ: ৬০)",
                14,
                FontWeight.Medium,
                Color(0xFF065F46),
                lineHeight = 22,
            )
        }

        items.forEach { (title, desc) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.r))
                    .background(Color.White)
                    .border(1.r, C.TodayBorder, RoundedCornerShape(16.r))
                    .padding(16.r),
            ) {
                RText(title, 17, FontWeight.Bold, ZakatGreenA)
                Spacer(Modifier.height(4.r))
                RText(desc, 14, FontWeight.Normal, C.Ink, lineHeight = 20)
            }
        }
    }
}

@Composable
private fun ZakatRulesView() {
    val rules = listOf(
        "যাকাত ফরজ হওয়ার শর্তাবলি" to listOf(
            "মুসলিম হওয়া।",
            "স্বাধীন ও প্রাপ্তবয়স্ক হওয়া।",
            "জ্ঞানবান বা সুস্থ মস্তিষ্কের অধিকারী হওয়া।",
            "নিসাব পরিমাণ সম্পদের পূর্ণ মালিকানা থাকা।",
            "সম্পদ মৌলিক প্রয়োজনের অতিরিক্ত হওয়া।",
            "উক্ত সম্পদ এক চান্দ্র বছর (হাওলানুল হাওল) নিজের অধিকারে থাকা।",
        ),
        "যাদেরকে যাকাত দেওয়া যাবে না" to listOf(
            "নিজের পিতা-মাতা, দাদা-দাদী, নানা-নানী (উর্ধ্বতন বংশধর)।",
            "নিজের সন্তান, নাতি-নাতনি (অধস্তন বংশধর)।",
            "স্বামী তার স্ত্রীকে অথবা স্ত্রী তার স্বামীকে যাকাত দিতে পারবে না।",
            "অমুসলিম ব্যক্তিকে ফরজ যাকাত দেওয়া জায়েজ নেই (তবে নফল দান করা যাবে)।",
            "বনু হাশিম অর্থাৎ মহানবী (সাঃ)-এর খান্দান বা বংশধরদের জন্য যাকাত হারাম।",
            "ধনী ও সম্পদশালী ব্যক্তিকে যাকাত দেওয়া জায়েজ নেই।",
        ),
        "যাকাত আদায়ের উত্তম তরিকা" to listOf(
            "গোপনে প্রদান করা উত্তম যাতে গ্রহীতার আত্মমর্যাদা ক্ষুণ্ণ না হয়।",
            "নিকটাত্মীয়দের মধ্যে যারা দরিদ্র তাদের অগ্রাধিকার দেওয়া (এতে আত্মীয়তার হক ও যাকাত উভয়ের সওয়াব মিলে)।",
            "প্রয়োজন অনুপাতে এককালীন এমন পরিমাণ দেওয়া যাতে সে স্বাবলম্বী হতে পারে।",
        ),
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.r)) {
        rules.forEach { (title, list) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.r))
                    .background(Color.White)
                    .border(1.r, C.TodayBorder, RoundedCornerShape(18.r))
                    .padding(18.r),
            ) {
                RText(title, 18, FontWeight.Bold, C.Ink)
                Spacer(Modifier.height(10.r))
                list.forEach { r ->
                    Row(Modifier.padding(vertical = 3.r)) {
                        RText("•", 16, FontWeight.Bold, ZakatGreenA)
                        Spacer(Modifier.width(8.r))
                        RText(r, 15, FontWeight.Normal, C.Ink, lineHeight = 22)
                    }
                }
            }
        }
    }
}
