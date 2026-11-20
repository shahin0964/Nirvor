package com.nirvor.app.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nirvor.app.player.AspectRatioMode
import com.nirvor.app.player.MediaItem
import com.nirvor.app.player.RepeatMode
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun NirvorVideoPlayer(
    playlist: List<MediaItem>,
    initialIndex: Int = 0,
    streamUrl: String? = null,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val scope = rememberCoroutineScope()

    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))) }
    val currentItem = playlist.getOrNull(currentIndex)

    // Player state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }

    // Playback settings
    var speed by remember { mutableFloatStateOf(1.0f) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.Fit) }
    var repeatMode by remember { mutableStateOf(RepeatMode.Off) }
    var isHwDecoder by remember { mutableStateOf(true) }
    var isLandscape by remember { mutableStateOf(false) }

    // Gesture HUD overlay states (MX Player signature)
    var brightnessLevel by remember {
        val cur = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (cur < 0) 0.5f else cur)
    }
    var showBrightnessHud by remember { mutableStateOf(false) }

    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var volumeLevel by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }
    var showVolumeHud by remember { mutableStateOf(false) }

    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var showSeekHud by remember { mutableStateOf(false) }

    var doubleTapFeedback by remember { mutableStateOf<String?>(null) } // "<< 10s" or "10s >>"

    // Sleep timer
    var sleepTimerMinutes by remember { mutableIntStateOf(0) }
    var sleepMenuOpen by remember { mutableStateOf(false) }

    // ExoPlayer initialization
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            this.repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Set media source
    LaunchedEffect(currentIndex, streamUrl) {
        val uri = if (streamUrl != null) {
            Uri.parse(streamUrl)
        } else {
            currentItem?.uri ?: return@LaunchedEffect
        }

        val exoItem = ExoMediaItem.fromUri(uri)
        exoPlayer.setMediaItem(exoItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    // Progress poller
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.isPlaying) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                totalDurationMs = exoPlayer.duration.coerceAtLeast(0L)
                isPlaying = true
            } else {
                isPlaying = exoPlayer.playWhenReady
            }
            delay(500)
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(isControlsVisible, isPlaying, isLocked) {
        if (isControlsVisible && isPlaying && !isLocked) {
            delay(4000)
            isControlsVisible = false
        }
    }

    // Sleep timer countdown
    LaunchedEffect(sleepTimerMinutes) {
        if (sleepTimerMinutes > 0) {
            delay(sleepTimerMinutes * 60 * 1000L)
            exoPlayer.pause()
            sleepTimerMinutes = 0
        }
    }

    // Cleanup when leaving player: reset orientation and screen brightness
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.attributes?.let {
                it.screenBrightness = -1f
                activity.window.attributes = it
            }
        }
    }

    BackHandler {
        if (isLocked) {
            isLocked = false
        } else {
            onClose()
        }
    }

    fun applyBrightness(fraction: Float) {
        val clamped = fraction.coerceIn(0.01f, 1.0f)
        brightnessLevel = clamped
        activity?.let {
            val lp = it.window.attributes
            lp.screenBrightness = clamped
            it.window.attributes = lp
        }
    }

    fun applyVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, maxVolume)
        volumeLevel = clamped
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped.toInt(), 0)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val boxWidthPx = constraints.maxWidth.toFloat()
        val boxHeightPx = constraints.maxHeight.toFloat()

        // 1. Surface/Video View
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked) {
                    if (isLocked) {
                        detectTapGestures(
                            onTap = { isControlsVisible = !isControlsVisible }
                        )
                        return@pointerInput
                    }

                    var dragDirection = 0 // 0: undetermined, 1: vertical, 2: horizontal
                    var startX = 0f
                    var startY = 0f
                    var initialBrightness = 0f
                    var initialVolume = 0f
                    var initialSeek = 0L

                    detectDragGestures(
                        onDragStart = { offset ->
                            startX = offset.x
                            startY = offset.y
                            dragDirection = 0
                            initialBrightness = brightnessLevel
                            initialVolume = volumeLevel
                            initialSeek = exoPlayer.currentPosition
                        },
                        onDragEnd = {
                            if (dragDirection == 2 && showSeekHud) {
                                exoPlayer.seekTo(seekTargetMs)
                            }
                            showBrightnessHud = false
                            showVolumeHud = false
                            showSeekHud = false
                            dragDirection = 0
                        },
                        onDragCancel = {
                            showBrightnessHud = false
                            showVolumeHud = false
                            showSeekHud = false
                            dragDirection = 0
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val totalDx = change.position.x - startX
                            val totalDy = change.position.y - startY

                            if (dragDirection == 0) {
                                if (abs(totalDx) > 30f && abs(totalDx) > abs(totalDy) * 1.5f) {
                                    dragDirection = 2 // Horizontal seek
                                } else if (abs(totalDy) > 30f) {
                                    dragDirection = 1 // Vertical brightness or volume
                                }
                            }

                            if (dragDirection == 1) {
                                // Vertical: Left half = Brightness, Right half = Volume
                                val dyFraction = -dragAmount.y / (boxHeightPx * 0.75f)
                                if (startX < boxWidthPx * 0.5f) {
                                    val newB = (brightnessLevel + dyFraction).coerceIn(0.01f, 1f)
                                    applyBrightness(newB)
                                    showBrightnessHud = true
                                    showVolumeHud = false
                                } else {
                                    val newV = (volumeLevel + (dyFraction * maxVolume)).coerceIn(0f, maxVolume)
                                    applyVolume(newV)
                                    showVolumeHud = true
                                    showBrightnessHud = false
                                }
                            } else if (dragDirection == 2) {
                                // Horizontal Seek
                                val dur = exoPlayer.duration.coerceAtLeast(1L)
                                val seekFraction = totalDx / boxWidthPx
                                val delta = (seekFraction * 90000L).toLong() // up to 90s seek
                                val target = (initialSeek + delta).coerceIn(0L, dur)
                                seekDeltaMs = delta
                                seekTargetMs = target
                                showSeekHud = true
                            }
                        }
                    )
                }
                .pointerInput(isLocked) {
                    if (isLocked) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (offset.x < boxWidthPx * 0.5f) {
                                val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(newPos)
                                doubleTapFeedback = "<< 10s"
                            } else {
                                val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(newPos)
                                doubleTapFeedback = "10s >>"
                            }
                            scope.launch {
                                delay(700)
                                doubleTapFeedback = null
                            }
                        },
                        onTap = {
                            isControlsVisible = !isControlsVisible
                        }
                    )
                }
        ) {
            // Render ExoPlayer
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = when (aspectRatioMode) {
                            AspectRatioMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            AspectRatioMode.Crop -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            AspectRatioMode.Stretch -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioMode.Original -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.resizeMode = when (aspectRatioMode) {
                        AspectRatioMode.Fit -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.Crop -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.Stretch -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.Original -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Audio Player Visualizer Mode (if not a video)
            if (currentItem != null && !currentItem.isVideo && streamUrl == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)))),
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(Glyph.Music, Color.White, Modifier.size(64.dp))
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = currentItem.title,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Nirvor Audio Player • ${currentItem.formattedDuration}",
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // 2. Gesture HUD Overlays (MX Player Signature)
        // Brightness HUD
        if (showBrightnessHud) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Sun, Color(0xFFFBBF24), Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Brightness ${(brightnessLevel * 100).toInt()}%",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(brightnessLevel)
                                    .background(Color(0xFFFBBF24))
                            )
                        }
                    }
                }
            }
        }

        // Volume HUD
        if (showVolumeHud) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Volume, Color(0xFF38BDF8), Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val volPct = if (maxVolume > 0) ((volumeLevel / maxVolume) * 100).toInt() else 0
                        Text(
                            text = "Volume $volPct%",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(if (maxVolume > 0) volumeLevel / maxVolume else 0f)
                                    .background(Color(0xFF38BDF8))
                            )
                        }
                    }
                }
            }
        }

        // Seek HUD
        if (showSeekHud) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val sign = if (seekDeltaMs >= 0) "+" else ""
                    val deltaSec = seekDeltaMs / 1000
                    Text(
                        text = "[$sign${formatTime(abs(seekDeltaMs))}]",
                        color = if (seekDeltaMs >= 0) Color(0xFF34D399) else Color(0xFFF87171),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${formatTime(seekTargetMs)} / ${formatTime(totalDurationMs)}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Double Tap 10s Feedback
        doubleTapFeedback?.let { feedback ->
            Box(
                modifier = Modifier
                    .align(if (feedback.startsWith("<<")) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .padding(18.dp)
            ) {
                Text(
                    text = feedback,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 3. Screen Lock Floating Button (when player is locked)
        if (isLocked) {
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable {
                            isLocked = false
                            isControlsVisible = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    GlyphIcon(Glyph.Lock, Color(0xFFF87171), Modifier.size(28.dp))
                }
            }
        }

        // 4. Main Player HUD Controls (Top and Bottom bars)
        AnimatedVisibility(
            visible = isControlsVisible && !isLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    ),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // TOP BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.ArrowLeft, Color.White, Modifier.size(24.dp))
                    }

                    Spacer(Modifier.width(10.dp))

                    // Title
                    val titleText = streamUrl ?: (currentItem?.displayName ?: "Nirvor Player")
                    Text(
                        text = titleText,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(8.dp))

                    // Decoder chip (HW / SW)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .clickable { isHwDecoder = !isHwDecoder }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isHwDecoder) "HW" else "SW",
                            color = if (isHwDecoder) Color(0xFF34D399) else Color(0xFFFBBF24),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Aspect ratio mode
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                aspectRatioMode = when (aspectRatioMode) {
                                    AspectRatioMode.Fit -> AspectRatioMode.Crop
                                    AspectRatioMode.Crop -> AspectRatioMode.Stretch
                                    AspectRatioMode.Stretch -> AspectRatioMode.Original
                                    AspectRatioMode.Original -> AspectRatioMode.Fit
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        GlyphIcon(Glyph.AspectRatio, Color.White, Modifier.size(20.dp))
                    }

                    Spacer(Modifier.width(6.dp))

                    // Playback speed
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .clickable { speedMenuOpen = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${speed}x",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        DropdownMenu(
                            expanded = speedMenuOpen,
                            onDismissRequest = { speedMenuOpen = false }
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s}x") },
                                    onClick = {
                                        speed = s
                                        exoPlayer.playbackParameters = PlaybackParameters(s)
                                        speedMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    // Sleep Timer
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { sleepMenuOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(
                                Glyph.ClockHands,
                                if (sleepTimerMinutes > 0) Color(0xFF38BDF8) else Color.White,
                                Modifier.size(20.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = sleepMenuOpen,
                            onDismissRequest = { sleepMenuOpen = false }
                        ) {
                            listOf(0 to "Sleep Timer Off", 15 to "15 minutes", 30 to "30 minutes", 60 to "60 minutes").forEach { (min, lbl) ->
                                DropdownMenuItem(
                                    text = { Text(lbl) },
                                    onClick = {
                                        sleepTimerMinutes = min
                                        sleepMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }

                // BOTTOM CONTROLS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Time and Duration row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTime(currentPositionMs),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = formatTime(totalDurationMs),
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Seekbar Slider
                    Slider(
                        value = if (totalDurationMs > 0) (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 0f,
                        onValueChange = { fraction ->
                            val target = (fraction * totalDurationMs).toLong()
                            currentPositionMs = target
                            exoPlayer.seekTo(target)
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF3B82F6),
                            activeTrackColor = Color(0xFF3B82F6),
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Screen Lock button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable {
                                    isLocked = true
                                    isControlsVisible = false
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(Glyph.Unlock, Color.White, Modifier.size(22.dp))
                        }

                        // Previous button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable(enabled = playlist.isNotEmpty() && currentIndex > 0) {
                                    if (currentIndex > 0) {
                                        currentIndex--
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(
                                Glyph.ChevronLeft,
                                if (currentIndex > 0) Color.White else Color.Gray,
                                Modifier.size(24.dp)
                            )
                        }

                        // Rewind 10s button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(newPos)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(Glyph.Rewind10, Color.White, Modifier.size(24.dp))
                        }

                        // Large Play / Pause button
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2563EB))
                                .clickable {
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        exoPlayer.play()
                                        isPlaying = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(
                                if (isPlaying) Glyph.Pause else Glyph.Play,
                                Color.White,
                                Modifier.size(28.dp)
                            )
                        }

                        // Fast-forward 10s button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val newPos = (exoPlayer.currentPosition + 10000L).coerceAtMost(exoPlayer.duration)
                                    exoPlayer.seekTo(newPos)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(Glyph.Forward10, Color.White, Modifier.size(24.dp))
                        }

                        // Next button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable(enabled = playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
                                    if (currentIndex < playlist.size - 1) {
                                        currentIndex++
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(
                                Glyph.ChevronRight,
                                if (currentIndex < playlist.size - 1) Color.White else Color.Gray,
                                Modifier.size(24.dp)
                            )
                        }

                        // Rotate / Orientation button
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .clickable {
                                    isLandscape = !isLandscape
                                    activity?.requestedOrientation = if (isLandscape) {
                                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            GlyphIcon(Glyph.Rotate, Color.White, Modifier.size(22.dp))
                        }

                        // Picture in Picture (PiP)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        try {
                                            activity?.enterPictureInPictureMode(
                                                PictureInPictureParams.Builder().build()
                                            )
                                        } catch (_: Exception) {}
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                GlyphIcon(Glyph.Pip, Color.White, Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000
    val s = totalSec % 60
    val m = (totalSec / 60) % 60
    val h = totalSec / 3600
    return if (h > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}
