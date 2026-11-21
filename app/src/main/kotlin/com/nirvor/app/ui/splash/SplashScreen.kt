package com.nirvor.app.ui.splash

import android.app.Activity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nirvor.app.R
import com.nirvor.app.ui.RText
import kotlinx.coroutines.delay

/**
 * Beautiful full-screen edge-to-edge splash screen with custom Nirvor artwork,
 * smooth 360-degree rotating gradient ring loading animation, and clean status bar styling.
 */
@Composable
fun SplashScreen(
    durationMs: Long = 1800L,
    onFinished: () -> Unit,
) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        onDispose {
            val window = activity?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(durationMs)
        onFinished()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splash_rotation",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF03164D)),
    ) {
        // Fullscreen edge-to-edge artwork
        Image(
            painter = painterResource(R.drawable.splash_background),
            contentDescription = "Nirvor Splash",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Animated loader at the bottom exactly where user's artwork specifies
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.splash_spinner),
                contentDescription = "Loading",
                modifier = Modifier
                    .size(52.dp)
                    .rotate(rotation),
            )
            Spacer(Modifier.height(10.dp))
            RText(
                "Loading Nirvor...",
                15,
                FontWeight.Medium,
                Color.White.copy(alpha = 0.85f),
                align = TextAlign.Center,
            )
            Spacer(Modifier.height(44.dp))
        }
    }
}
