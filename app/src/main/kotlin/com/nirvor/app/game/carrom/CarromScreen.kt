package com.nirvor.app.game.carrom

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nirvor.app.islamic.bnNum
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.icons.Glyph
import com.nirvor.app.ui.icons.GlyphIcon
import com.nirvor.app.ui.r
import com.nirvor.app.util.Fmt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun CarromScreen(
    storage: CarromStorage,
    onBack: () -> Unit,
    onOpenStore: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var gameMode by remember { mutableStateOf(CarromPlayMode.VsBot) }
    var currentTurn by remember { mutableStateOf(CarromTurn.Player1) }
    var isSimulating by remember { mutableStateOf(false) }

    // Striker baseline slider position (Player 1: 180f .. 620f)
    var strikerSliderPos by remember { mutableFloatStateOf(CarromPhysics.BOARD_SIZE / 2) }

    // Aim drag state
    var isAiming by remember { mutableStateOf(false) }
    var aimStart by remember { mutableStateOf<Offset?>(null) }
    var aimCurrent by remember { mutableStateOf<Offset?>(null) }

    // Game stats & scores
    var p1Score by remember { mutableIntStateOf(0) }
    var p2Score by remember { mutableIntStateOf(0) }
    var currentCombo by remember { mutableIntStateOf(0) }
    var isBreakShot by remember { mutableStateOf(true) }
    var bannerText by remember { mutableStateOf<String?>(null) }
    var bannerColor by remember { mutableStateOf(Color(0xFFF59E0B)) }

    // Dialogs
    var showPauseMenu by remember { mutableStateOf(false) }
    var showGameOver by remember { mutableStateOf(false) }
    var winnerName by remember { mutableStateOf("") }

    // Active equipped items
    val activeStriker = remember(storage.equippedStrikerId) { storage.activeStriker() }
    val activeBoard = remember(storage.equippedBoardId) { storage.activeBoard() }
    val activePuck = remember(storage.equippedPuckId) { storage.activePuck() }
    val strikerLevel = storage.strikerLevel(activeStriker.id).coerceAtLeast(1)

    // Current discs on the board
    val discs = remember { mutableStateListOf<CarromDisc>() }

    fun resetBoard() {
        discs.clear()
        discs.addAll(CarromPhysics.initialDiscs(strikerSliderPos, isPlayer1 = currentTurn == CarromTurn.Player1))
        p1Score = 0
        p2Score = 0
        currentCombo = 0
        isBreakShot = true
        bannerText = "ব্রেক শট (BREAK SHOT!)"
        bannerColor = Color(0xFFF59E0B)
        currentTurn = CarromTurn.Player1
        strikerSliderPos = CarromPhysics.BOARD_SIZE / 2
    }

    LaunchedEffect(Unit) {
        resetBoard()
    }

    // Trigger haptic bump
    fun triggerHaptic(strong: Boolean = false) {
        if (!storage.vibrationEnabled) return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    if (strong) VibrationEffect.createWaveform(longArrayOf(0, 45, 25, 45), -1)
                    else VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val v = context.getSystemService(Vibrator::class.java)
                v?.vibrate(if (strong) 40L else 18L)
            }
        } catch (_: Exception) {}
    }

    // Physics Simulation Loop
    LaunchedEffect(isSimulating) {
        if (isSimulating) {
            var anyCoinPocketedInTurn = false
            var turnPocketCount = 0

            while (true) {
                withFrameNanos { _ -> }

                val moving = CarromPhysics.updatePhysics(
                    discs = discs,
                    frictionFactor = 0.981f + (activeStriker.time(strikerLevel) / 100f) * 0.006f,
                    cushionRestitution = 0.78f,
                    coinRestitution = 0.88f,
                    onPocket = { pocketedDisc ->
                        triggerHaptic(strong = true)
                        when (pocketedDisc.type) {
                            CarromDiscType.White -> {
                                if (currentTurn == CarromTurn.Player1) p1Score += 20 else p2Score += 20
                                anyCoinPocketedInTurn = true
                                turnPocketCount++
                            }
                            CarromDiscType.Black -> {
                                if (currentTurn == CarromTurn.Player1) p1Score += 10 else p2Score += 10
                                anyCoinPocketedInTurn = true
                                turnPocketCount++
                            }
                            CarromDiscType.Queen -> {
                                if (currentTurn == CarromTurn.Player1) p1Score += 50 else p2Score += 50
                                anyCoinPocketedInTurn = true
                                bannerText = "👑 রানি পকেট হয়েছে! (QUEEN!)"
                                bannerColor = Color(0xFFE11D48)
                            }
                            CarromDiscType.Striker -> {
                                // Foul: striker fell into pocket!
                                if (currentTurn == CarromTurn.Player1) p1Score = (p1Score - 10).coerceAtLeast(0)
                                else p2Score = (p2Score - 10).coerceAtLeast(0)
                                bannerText = "⚠️ ফাউল! স্ট্রাইকার পকেটে পড়েছে"
                                bannerColor = Color(0xFFEF4444)
                            }
                        }
                    },
                    onCollide = { impulse ->
                        if (impulse > 1.2f) triggerHaptic(strong = false)
                    },
                )

                if (!moving) {
                    isSimulating = false
                    break
                }
            }

            // Turn Resolution after all discs stopped
            if (turnPocketCount > 1) {
                currentCombo = turnPocketCount
                bannerText = "🔥 কম্বো শট! ($turnPocketCount COINS!)"
                bannerColor = Color(0xFF10B981)
            }

            // Check Win Condition
            val remainingCoins = discs.filter { !it.isPocketed && it.type != CarromDiscType.Striker }
            if (remainingCoins.isEmpty()) {
                val p1Won = p1Score >= p2Score
                winnerName = if (p1Won) "প্লেয়ার ১ (আপনি)" else "প্লেয়ার ২"
                storage.recordMatch(won = p1Won, combo = currentCombo)
                showGameOver = true
            } else {
                // If a coin was pocketed, player gets another shot! Otherwise switch turns.
                if (!anyCoinPocketedInTurn) {
                    currentTurn = if (currentTurn == CarromTurn.Player1) CarromTurn.Player2 else CarromTurn.Player1
                    bannerText = null
                }

                // Reset striker on baseline
                val striker = discs.firstOrNull { it.type == CarromDiscType.Striker }
                if (striker != null) {
                    striker.isPocketed = false
                    striker.sinkScale = 1f
                    striker.sinkAlpha = 1f
                    striker.vx = 0f
                    striker.vy = 0f
                    striker.x = strikerSliderPos
                    striker.y = if (currentTurn == CarromTurn.Player1) CarromPhysics.BASELINE_Y_BOTTOM else CarromPhysics.BASELINE_Y_TOP
                }

                // If next turn is Bot, trigger automated AI shot
                if (currentTurn == CarromTurn.Player2 && gameMode == CarromPlayMode.VsBot) {
                    scope.launch {
                        delay(900)
                        val (botX, shotVec) = CarromPhysics.calculateBotShot(
                            discs = discs,
                            targetType = CarromDiscType.Black,
                            accuracy = 0.88f,
                        )
                        striker?.x = botX
                        delay(400)
                        striker?.vx = shotVec.x * 0.14f
                        striker?.vy = shotVec.y * 0.14f
                        isSimulating = true
                    }
                }
            }
        }
    }

    // Keep striker position synced with slider when not simulating
    LaunchedEffect(strikerSliderPos) {
        if (!isSimulating) {
            val striker = discs.firstOrNull { it.type == CarromDiscType.Striker }
            if (striker != null && currentTurn == CarromTurn.Player1) {
                striker.x = strikerSliderPos
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // TOP HUD: Rolex vs Opponent Header (matches Carrom Pool reference video)
        TopGameHud(
            p1Name = "Rolex (আপনি)",
            p1Score = p1Score,
            p2Name = if (gameMode == CarromPlayMode.VsBot) "মাস্টার AI বট" else "প্লেয়ার ২",
            p2Score = p2Score,
            isP1Turn = currentTurn == CarromTurn.Player1,
            potAmount = 5000,
            onPause = { showPauseMenu = true },
            onStore = onOpenStore,
        )

        // Banner notifications (Break Shot, Combo Shot, Queen etc.)
        Box(
            Modifier
                .fillMaxWidth()
                .height(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (bannerText != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(bannerColor)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    RText(bannerText ?: "", 13, FontWeight.Bold, Color.White)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // THE CARROM BOARD CANVAS
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .pointerInput(isSimulating, currentTurn) {
                    if (isSimulating || (currentTurn == CarromTurn.Player2 && gameMode == CarromPlayMode.VsBot)) return@pointerInput

                    detectDragGestures(
                        onDragStart = { offset ->
                            val scale = size.width / CarromPhysics.BOARD_SIZE
                            val striker = discs.firstOrNull { it.type == CarromDiscType.Striker }
                            if (striker != null) {
                                val sX = striker.x * scale
                                val sY = striker.y * scale
                                val dx = offset.x - sX
                                val dy = offset.y - sY
                                // Touch within or near striker to start aiming
                                if (sqrt(dx * dx + dy * dy) < (striker.radius + 35f) * scale) {
                                    isAiming = true
                                    aimStart = Offset(sX, sY)
                                    aimCurrent = offset
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            if (isAiming) {
                                aimCurrent = change.position
                            }
                        },
                        onDragEnd = {
                            if (isAiming && aimStart != null && aimCurrent != null) {
                                val scale = size.width / CarromPhysics.BOARD_SIZE
                                val pullX = (aimStart!!.x - aimCurrent!!.x) / scale
                                val pullY = (aimStart!!.y - aimCurrent!!.y) / scale
                                val pullDist = sqrt(pullX * pullX + pullY * pullY)

                                if (pullDist > 15f) {
                                    // Power scaling influenced by active striker's upgraded power
                                    val powerFactor = 0.16f + (activeStriker.power(strikerLevel) / 100f) * 0.08f
                                    val striker = discs.firstOrNull { it.type == CarromDiscType.Striker }
                                    if (striker != null) {
                                        striker.vx = pullX * powerFactor
                                        striker.vy = pullY * powerFactor
                                        isSimulating = true
                                        isBreakShot = false
                                        triggerHaptic(strong = true)
                                    }
                                }
                            }
                            isAiming = false
                            aimStart = null
                            aimCurrent = null
                        },
                        onDragCancel = {
                            isAiming = false
                            aimStart = null
                            aimCurrent = null
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / CarromPhysics.BOARD_SIZE

                // 1. Draw Wooden Frame & Corner Pockets
                drawCarromBoard(activeBoard, scale)

                // 2. Draw Discs (Coins & Queen)
                for (disc in discs) {
                    if (disc.isPocketed) continue
                    drawCarromDisc(disc, activePuck, activeStriker, scale)
                }

                // 3. Draw Aiming Guideline and Deflection (Carrom Pool Raycast Trajectory)
                if (isAiming && aimStart != null && aimCurrent != null && storage.aimGuideEnabled) {
                    val striker = discs.firstOrNull { it.type == CarromDiscType.Striker }
                    if (striker != null) {
                        val pullX = (aimStart!!.x - aimCurrent!!.x) / scale
                        val pullY = (aimStart!!.y - aimCurrent!!.y) / scale
                        val guideLength = 320f + (activeStriker.aim(strikerLevel) / 100f) * 220f

                        val prediction = CarromPhysics.predictTrajectory(
                            striker = striker,
                            aimDx = pullX,
                            aimDy = pullY,
                            maxGuideDist = guideLength,
                            discs = discs,
                        )

                        // Dotted trajectory line
                        drawLine(
                            color = Color(0xFFF59E0B),
                            start = Offset(prediction.start.x * scale, prediction.start.y * scale),
                            end = Offset(prediction.hitPoint.x * scale, prediction.hitPoint.y * scale),
                            strokeWidth = 3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f),
                            cap = StrokeCap.Round,
                        )

                        // Circle marker at hit point
                        drawCircle(
                            color = Color(0xFFF59E0B).copy(alpha = 0.5f),
                            radius = striker.radius * scale,
                            center = Offset(prediction.hitPoint.x * scale, prediction.hitPoint.y * scale),
                            style = Stroke(width = 2.dp.toPx()),
                        )

                        // Target coin deflection line towards pocket
                        if (prediction.targetDeflection != null) {
                            drawLine(
                                color = Color(0xFF34D399),
                                start = Offset(prediction.hitPoint.x * scale, prediction.hitPoint.y * scale),
                                end = Offset(prediction.targetDeflection.x * scale, prediction.targetDeflection.y * scale),
                                strokeWidth = 3.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f),
                                cap = StrokeCap.Round,
                            )
                        }

                        // Striker bounce deflection
                        if (prediction.strikerDeflection != null) {
                            drawLine(
                                color = Color(0xFF60A5FA).copy(alpha = 0.8f),
                                start = Offset(prediction.hitPoint.x * scale, prediction.hitPoint.y * scale),
                                end = Offset(prediction.strikerDeflection.x * scale, prediction.strikerDeflection.y * scale),
                                strokeWidth = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // BOTTOM BASELINE SLIDER (Exact match to the slider bar in the reference video)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RText("🎯 স্ট্রাইকার পজিশন স্লাইডার", 13, FontWeight.Medium, Color(0xFF94A3B8))
                RText("টেনে বসান", 12, FontWeight.Normal, Color(0xFF64748B))
            }

            Slider(
                value = strikerSliderPos,
                onValueChange = { pos ->
                    if (!isSimulating && currentTurn == CarromTurn.Player1) {
                        strikerSliderPos = pos
                    }
                },
                valueRange = CarromPhysics.BASELINE_MIN_X..CarromPhysics.BASELINE_MAX_X,
                enabled = !isSimulating && currentTurn == CarromTurn.Player1,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFF59E0B),
                    activeTrackColor = Color(0xFFD97706),
                    inactiveTrackColor = Color(0xFF1E293B),
                ),
                modifier = Modifier.fillMaxWidth().height(32.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        // BOTTOM ACTIONS (Store, Mode, Settings)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onOpenStore,
                modifier = Modifier.weight(1f).height(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(14.dp),
            ) {
                RText("🛒 ক্যারম শপ ও আপগ্রেড", 14, FontWeight.Bold, Color.White)
            }

            Button(
                onClick = { resetBoard() },
                modifier = Modifier.size(46.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                GlyphIcon(Glyph.Refresh, Color.White, Modifier.size(20.r))
            }
        }
    }

    // Pause & Game Settings Menu
    if (showPauseMenu) {
        AlertDialog(
            onDismissRequest = { showPauseMenu = false },
            title = { RText("ক্যারম সেটিংস ও বিকল্প", 18, FontWeight.Bold, Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RText("শব্দ ইফেক্ট (Sound)", 14, FontWeight.Medium, Color.White)
                        Switch(
                            checked = storage.soundEnabled,
                            onCheckedChange = { storage.toggleSound() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF059669)),
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RText("হ্যাপটিক ভাইব্রেশন", 14, FontWeight.Medium, Color.White)
                        Switch(
                            checked = storage.vibrationEnabled,
                            onCheckedChange = { storage.toggleVibration() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF059669)),
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RText("ডটেড এইম লাইন দেখান", 14, FontWeight.Medium, Color.White)
                        Switch(
                            checked = storage.aimGuideEnabled,
                            onCheckedChange = { storage.toggleAimGuide() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF059669)),
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    RText("খেলার মোড নির্বাচন করুন:", 13, FontWeight.Bold, Color(0xFFFDE68A))

                    CarromPlayMode.entries.forEach { mode ->
                        val selected = gameMode == mode
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Color(0xFF2563EB) else Color(0xFF334155))
                                .clickable {
                                    gameMode = mode
                                    resetBoard()
                                    showPauseMenu = false
                                }
                                .padding(10.dp),
                        ) {
                            Column {
                                RText(mode.labelBn, 13, FontWeight.Bold, Color.White)
                                RText(mode.subtitleBn, 11, FontWeight.Normal, Color(0xFFCBD5E1))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showPauseMenu = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                ) {
                    RText("খেলায় ফিরুন", 13, FontWeight.Bold, Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showPauseMenu = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                ) {
                    RText("ম্যাচ ত্যাগ করুন", 13, FontWeight.Bold, Color.White)
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(20.dp),
        )
    }

    // Game Over Win Dialog
    if (showGameOver) {
        AlertDialog(
            onDismissRequest = { showGameOver = false },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    RText("🎉 ম্যাচ সমাপ্ত!", 24, FontWeight.Bold, Color(0xFFF59E0B))
                    Spacer(Modifier.height(4.dp))
                    RText("বিজয়ী: $winnerName", 17, FontWeight.Bold, Color.White)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    RText("পুরস্কার হিসেবে যোগ হয়েছে: +৪০০ কয়েন ও +২ জেমস! 🪙💎", 14, FontWeight.Normal, Color(0xFFA7F3D0))
                    Spacer(Modifier.height(10.dp))
                    RText("প্লেয়ার ১ পয়েন্ট: ${bnNum(p1Score)} | প্লেয়ার ২ পয়েন্ট: ${bnNum(p2Score)}", 13, FontWeight.Medium, Color(0xFFCBD5E1))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGameOver = false
                        resetBoard()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    RText("পুনরায় খেলুন (Play Again)", 14, FontWeight.Bold, Color.White)
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(20.dp),
        )
    }
}

@Composable
private fun TopGameHud(
    p1Name: String,
    p1Score: Int,
    p2Name: String,
    p2Score: Int,
    isP1Turn: Boolean,
    potAmount: Int,
    onPause: () -> Unit,
    onStore: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Player 1 Card (Left)
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isP1Turn) Color(0xFF1E3A8A) else Color(0xFF1E293B))
                .border(1.5.dp, if (isP1Turn) Color(0xFF60A5FA) else Color.Transparent, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB)),
                contentAlignment = Alignment.Center,
            ) {
                RText("👤", 18, FontWeight.Normal, Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                RText(p1Name, 12, FontWeight.Bold, Color.White, maxLines = 1)
                RText("স্কোর: ${bnNum(p1Score)}", 12, FontWeight.Bold, Color(0xFFFDE68A))
            }
        }

        // Center Pot Badge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E293B))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RText("🪙", 13, FontWeight.Normal, Color.White)
                Spacer(Modifier.width(4.dp))
                RText(Fmt.num(potAmount.toLong()), 14, FontWeight.Bold, Color(0xFFF59E0B))
            }
            RText("প্রাইজ পট", 10, FontWeight.Normal, Color(0xFF94A3B8))
        }

        // Player 2 Card (Right)
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (!isP1Turn) Color(0xFF831843) else Color(0xFF1E293B))
                .border(1.5.dp, if (!isP1Turn) Color(0xFFF43F5E) else Color.Transparent, RoundedCornerShape(16.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                RText(p2Name, 12, FontWeight.Bold, Color.White, maxLines = 1)
                RText("স্কোর: ${bnNum(p2Score)}", 12, FontWeight.Bold, Color(0xFFFDE68A))
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFBE123C)),
                contentAlignment = Alignment.Center,
            ) {
                RText("🤖", 18, FontWeight.Normal, Color.White)
            }
        }

        // Settings / Pause button
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B))
                .clickable(onClick = onPause),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Sliders, Color.White, Modifier.size(18.r))
        }
    }
}

/** Draws the traditional wooden Carrom Board with 4 corner pockets, concentric circles, and baseline markings. */
private fun DrawScope.drawCarromBoard(boardSkin: CarromBoardSkin, scale: Float) {
    val bSize = CarromPhysics.BOARD_SIZE * scale
    val bWidth = CarromPhysics.BORDER_WIDTH * scale

    // 1. Wooden Outer Frame
    drawRoundRect(
        color = boardSkin.woodBorderColor,
        size = Size(bSize, bSize),
        cornerRadius = CornerRadius(22.dp.toPx()),
    )

    // Inner bevel shadow
    drawRect(
        color = Color.Black.copy(alpha = 0.25f),
        topLeft = Offset(bWidth - 4f, bWidth - 4f),
        size = Size(bSize - 2 * bWidth + 8f, bSize - 2 * bWidth + 8f),
    )

    // 2. Playing Surface
    drawRect(
        color = boardSkin.surfaceColor,
        topLeft = Offset(bWidth, bWidth),
        size = Size(bSize - 2 * bWidth, bSize - 2 * bWidth),
    )

    val cx = bSize / 2
    val cy = bSize / 2

    // 3. Center Circles & Floral Pattern
    drawCircle(
        color = boardSkin.ringColor,
        radius = 80f * scale,
        center = Offset(cx, cy),
        style = Stroke(width = 2.5f * scale),
    )
    drawCircle(
        color = Color(0xFFDC2626),
        radius = 24f * scale,
        center = Offset(cx, cy),
        style = Stroke(width = 2f * scale),
    )
    drawCircle(
        color = Color(0xFFDC2626),
        radius = 7f * scale,
        center = Offset(cx, cy),
    )

    // 4 Baselines (Top, Bottom, Left, Right)
    val blMin = CarromPhysics.BASELINE_MIN_X * scale
    val blMax = CarromPhysics.BASELINE_MAX_X * scale
    val blYBot = CarromPhysics.BASELINE_Y_BOTTOM * scale
    val blYTop = CarromPhysics.BASELINE_Y_TOP * scale

    fun drawBaseLine(y: Float) {
        drawLine(boardSkin.ringColor, Offset(blMin, y), Offset(blMax, y), strokeWidth = 1.8f * scale)
        drawLine(boardSkin.ringColor, Offset(blMin, y + 14f * scale), Offset(blMax, y + 14f * scale), strokeWidth = 1.2f * scale)
        drawCircle(boardSkin.foulLineColor, radius = 9f * scale, center = Offset(blMin, y + 7f * scale))
        drawCircle(boardSkin.foulLineColor, radius = 9f * scale, center = Offset(blMax, y + 7f * scale))
    }

    drawBaseLine(blYBot)
    drawBaseLine(blYTop)

    // 4 Diagonal Foul Lines with Red Circles
    val cornerInset = (CarromPhysics.BORDER_WIDTH + 60f) * scale
    val centerInset = 110f * scale

    drawLine(boardSkin.foulLineColor, Offset(cornerInset, cornerInset), Offset(cx - centerInset, cy - centerInset), strokeWidth = 1.5f * scale)
    drawLine(boardSkin.foulLineColor, Offset(bSize - cornerInset, cornerInset), Offset(cx + centerInset, cy - centerInset), strokeWidth = 1.5f * scale)
    drawLine(boardSkin.foulLineColor, Offset(cornerInset, bSize - cornerInset), Offset(cx - centerInset, cy + centerInset), strokeWidth = 1.5f * scale)
    drawLine(boardSkin.foulLineColor, Offset(bSize - cornerInset, bSize - cornerInset), Offset(cx + centerInset, cy + centerInset), strokeWidth = 1.5f * scale)

    // 4 Corner Pockets
    for (pocket in CarromPhysics.POCKETS) {
        val px = pocket.x * scale
        val py = pocket.y * scale

        // Pocket outer rim
        drawCircle(
            color = Color.Black.copy(alpha = 0.5f),
            radius = (CarromPhysics.POCKET_RADIUS + 4f) * scale,
            center = Offset(px, py),
        )
        // Deep pocket hole
        drawCircle(
            color = boardSkin.pocketColor,
            radius = CarromPhysics.POCKET_RADIUS * scale,
            center = Offset(px, py),
        )
        // Pocket inner net detail
        drawCircle(
            color = Color.Black,
            radius = (CarromPhysics.POCKET_RADIUS - 6f) * scale,
            center = Offset(px, py),
        )
    }
}

/** Draws individual coin, queen, or player striker with 3D depth and highlights. */
private fun DrawScope.drawCarromDisc(
    disc: CarromDisc,
    puckSkin: CarromPuckSkin,
    strikerItem: CarromStrikerItem,
    scale: Float,
) {
    val x = disc.x * scale
    val y = disc.y * scale
    val r = disc.radius * scale * disc.sinkScale
    val alpha = disc.sinkAlpha

    // Drop shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.35f * alpha),
        radius = r * 1.05f,
        center = Offset(x + 3f * scale, y + 4f * scale),
    )

    when (disc.type) {
        CarromDiscType.White -> {
            drawCircle(color = puckSkin.whiteDiscColor.copy(alpha = alpha), radius = r, center = Offset(x, y))
            drawCircle(color = Color(0xFFD1D5DB).copy(alpha = alpha), radius = r * 0.7f, center = Offset(x, y), style = Stroke(width = 1.5f * scale))
            drawCircle(color = Color(0xFF9CA3AF).copy(alpha = alpha), radius = r * 0.3f, center = Offset(x, y))
        }
        CarromDiscType.Black -> {
            drawCircle(color = puckSkin.blackDiscColor.copy(alpha = alpha), radius = r, center = Offset(x, y))
            drawCircle(color = Color(0xFF475569).copy(alpha = alpha), radius = r * 0.7f, center = Offset(x, y), style = Stroke(width = 1.5f * scale))
            drawCircle(color = Color(0xFF64748B).copy(alpha = alpha), radius = r * 0.3f, center = Offset(x, y))
        }
        CarromDiscType.Queen -> {
            drawCircle(color = puckSkin.queenDiscColor.copy(alpha = alpha), radius = r, center = Offset(x, y))
            drawCircle(color = Color(0xFFFDE047).copy(alpha = alpha), radius = r * 0.7f, center = Offset(x, y), style = Stroke(width = 2f * scale))
            drawCircle(color = Color(0xFFFBBF24).copy(alpha = alpha), radius = r * 0.35f, center = Offset(x, y))
        }
        CarromDiscType.Striker -> {
            // Striker Base
            drawCircle(color = strikerItem.primaryColor.copy(alpha = alpha), radius = r, center = Offset(x, y))
            // Ring
            drawCircle(color = strikerItem.ringColor.copy(alpha = alpha), radius = r * 0.75f, center = Offset(x, y), style = Stroke(width = 3f * scale))
            // Center Core
            drawCircle(color = strikerItem.coreColor.copy(alpha = alpha), radius = r * 0.38f, center = Offset(x, y))
            // Highlight shine
            drawCircle(color = Color.White.copy(alpha = 0.4f * alpha), radius = r * 0.16f, center = Offset(x - r * 0.28f, y - r * 0.28f))
        }
    }
}
