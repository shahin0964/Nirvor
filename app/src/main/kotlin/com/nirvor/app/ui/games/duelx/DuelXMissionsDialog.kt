package com.nirvor.app.ui.games.duelx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r

@Composable
fun DuelXMissionsDialog(onClose: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .clip(RoundedCornerShape(20.r))
                .background(Color(0xFF0F172A))
                .border(2.r, Color(0xFF10B981), RoundedCornerShape(20.r))
                .clickable(enabled = false) {}
                .padding(24.r),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RText("MISSIONS", 22, FontWeight.ExtraBold, Color.White)
                Spacer(Modifier.height(10.r))
                RText(
                    "Daily and weekly missions are on the way. Until then, claim your daily bonus and climb the ranks.",
                    13,
                    FontWeight.Medium,
                    Color(0xFF94A3B8),
                    align = TextAlign.Center,
                )
                Spacer(Modifier.height(20.r))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(12.r),
                    modifier = Modifier.width(130.r).height(44.r),
                ) {
                    RText("OK", 15, FontWeight.Bold, Color.White)
                }
            }
        }
    }
}
