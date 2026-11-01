package com.nirvor.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C

/**
 * Left side bar: a short menu in the home screen's style. Each option opens its own page;
 * all settings live under "সেটিংস". Opens by swiping from the left or the gear on Home.
 */
@Composable
fun SideBar(onClose: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current

    ModalDrawerSheet(
        modifier = Modifier.width(610.r),
        drawerShape = RoundedCornerShape(topEnd = 34.r, bottomEnd = 34.r),
        drawerContainerColor = C.Sheet,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        // Header
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(C.SkyTop, C.Sheet)))
                .statusBarsPadding()
                .padding(start = 30.r, end = 22.r, top = 34.r, bottom = 24.r)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBubble(Glyph.Check, C.FabA, C.FabB, size = 80)
                Spacer(Modifier.width(20.r))
                Column(Modifier.weight(1f)) {
                    RText("Nirvor", 34, FontWeight.Bold, C.Ink)
                    Spacer(Modifier.height(2.r))
                    RText("নির্ভর", 20, FontWeight.Normal, C.StatLabelBn)
                }
                GlyphIcon(
                    Glyph.ChevronLeft,
                    C.Ink,
                    Modifier
                        .size(56.r)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.7f))
                        .clickable(onClick = onClose)
                        .padding(14.r),
                    strokeWidth = 2.2f,
                )
            }
        }

        // Menu
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 22.r, vertical = 8.r),
            verticalArrangement = Arrangement.spacedBy(14.r),
        ) {
            NavRow(Glyph.Gear, Color(0xFF6E86A6), Color(0xFF4A6182), "সেটিংস", "প্রোফাইল, আবহাওয়া, পারমিশন", onSettings)
            NavRow(Glyph.Info, C.TaskGreenA, C.TaskGreenB, "অ্যাপ সম্পর্কে", "ভার্সন ও তথ্য", onAbout)
        }

        RText(
            "Nirvor " + appVersion(context),
            17,
            FontWeight.Normal,
            C.Hint,
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 26.r),
            align = TextAlign.Center,
        )
    }
}
