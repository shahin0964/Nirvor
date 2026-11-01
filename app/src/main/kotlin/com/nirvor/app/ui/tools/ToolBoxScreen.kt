package com.nirvor.app.ui.tools

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.nirvor.app.tools.ToolId
import com.nirvor.app.tools.ToolSection
import com.nirvor.app.ui.EmptyText
import com.nirvor.app.ui.PageScaffold
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.cardSurface
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.rs
import com.nirvor.app.ui.theme.C
import com.nirvor.app.ui.theme.Inter

/** More → Tool Box: every tool, grouped, three to a row, with a search box. */
@Composable
fun ToolBoxScreen(bottomPadding: Dp, onBack: () -> Unit, onOpen: (ToolId) -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim()
    val shown = ToolId.entries.filter {
        q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.bn.contains(q, ignoreCase = true)
    }
    val readyCount = ToolId.entries.count { it.ready }

    PageScaffold("Tool Box", "$readyCount টা টুল চালু · বাকিগুলো শীঘ্রই", onBack = onBack, bottomPadding = bottomPadding) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { RText("টুল খুঁজুন… (যেমন: কম্পাস, EMI)", 19, FontWeight.Normal, C.Hint) },
                leadingIcon = { GlyphIcon(Glyph.Search, C.Hint, Modifier.size(26.r), strokeWidth = 2f) },
                textStyle = TextStyle(fontFamily = Inter, fontSize = 20.rs, color = C.Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(18.r),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TGreen,
                    unfocusedBorderColor = C.FieldLine,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = TGreen,
                ),
            )
        }
        if (shown.isEmpty()) {
            item(key = "none") { EmptyText("“$q” নামে কোনো টুল নেই।") }
        }
        ToolSection.entries.forEach { section ->
            val tools = shown.filter { it.section == section }
            if (tools.isNotEmpty()) {
                item(key = "s-" + section.name) { SectionHead(section) }
                tools.chunked(3).forEachIndexed { i, row ->
                    item(key = "r-" + section.name + i) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.r)) {
                            row.forEach { tool ->
                                ToolTile(tool, Modifier.weight(1f)) {
                                    if (tool.ready) {
                                        onOpen(tool)
                                    } else {
                                        Toast.makeText(context, "${tool.title} পরের আপডেটে আসছে", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHead(section: ToolSection) {
    Row(Modifier.padding(start = 6.r, top = 12.r), verticalAlignment = Alignment.CenterVertically) {
        RText(section.emoji, 20, FontWeight.Normal, C.Ink)
        Spacer(Modifier.width(10.r))
        Column {
            RText(section.title, 22, FontWeight.Bold, C.Ink)
            RText(section.sub, 16, FontWeight.Normal, C.Hint)
        }
    }
}

@Composable
private fun ToolTile(tool: ToolId, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.r)
    val tint = Color(tool.section.color)
    Box(
        modifier
            .height(178.r)
            .cardSurface(shape, SolidColor(Color.White), C.TodayBorder, 1.r, 4.r)
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .alpha(if (tool.ready) 1f else 0.5f)
                .padding(horizontal = 10.r, vertical = 18.r),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.r).background(tint.copy(alpha = 0.12f), RoundedCornerShape(20.r)),
                contentAlignment = Alignment.Center,
            ) {
                RText(tool.emoji, 32, FontWeight.Normal, C.Ink, align = TextAlign.Center)
            }
            Spacer(Modifier.height(12.r))
            RText(tool.title, 17, FontWeight.SemiBold, C.Ink, align = TextAlign.Center, maxLines = 2, lineHeight = 21)
            Spacer(Modifier.height(3.r))
            RText(tool.bn, 14, FontWeight.Normal, C.Hint, align = TextAlign.Center, maxLines = 1)
        }
        if (!tool.ready) {
            RText(
                "শীঘ্রই",
                13,
                FontWeight.SemiBold,
                tint,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.r)
                    .clip(RoundedCornerShape(50))
                    .background(tint.copy(alpha = 0.12f))
                    .padding(horizontal = 10.r, vertical = 3.r),
            )
        }
    }
}
