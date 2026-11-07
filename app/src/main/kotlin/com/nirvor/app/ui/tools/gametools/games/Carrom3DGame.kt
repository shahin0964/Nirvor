package com.nirvor.app.ui.tools.gametools.games

import android.opengl.Matrix
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.nirvor.app.ui.RText
import com.nirvor.app.ui.r
import com.nirvor.app.ui.tools.gametools.engine.Game3DCanvas
import com.nirvor.app.ui.tools.gametools.engine.Mesh
import com.nirvor.app.ui.tools.gametools.engine.MeshBuilder
import com.nirvor.app.ui.tools.gametools.engine.Renderer3D
import com.nirvor.app.ui.tools.gametools.engine.Touch3DListener
import com.nirvor.app.ui.tools.gametools.models.Difficulty
import com.nirvor.app.ui.tools.gametools.models.GamePlayMode
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CarromDisc3D(
    val id: Int,
    var x: Float,
    var z: Float,
    var vx: Float = 0f,
    var vz: Float = 0f,
    val radius: Float,
    val height: Float,
    val isQueen: Boolean = false,
    val isWhite: Boolean = false,
    val isStriker: Boolean = false,
    var isPocketed: Boolean = false,
    val mesh: Mesh,
)

class Carrom3DRenderer : Renderer3D() {

    private lateinit var boardMesh: Mesh
    private lateinit var frameNorthMesh: Mesh
    private lateinit var frameSouthMesh: Mesh
    private lateinit var frameEastMesh: Mesh
    private lateinit var frameWestMesh: Mesh
    private lateinit var pocketMesh: Mesh

    val discs = ArrayList<CarromDisc3D>()

    // Striker aiming line & power
    var strikerAimAngle: Float = -90f // pointing towards board center
    var strikerPower: Float = 0f

    val boardSize = 8.0f
    val halfBoard = boardSize * 0.5f
    val playableLimit = halfBoard - 0.45f

    init {
        initPieces()
    }

    override fun onSceneInit() {
        cameraPitch = 60f
        cameraYaw = 0f
        cameraDistance = 15f
        clearColor = floatArrayOf(0.12f, 0.15f, 0.20f, 1.0f)

        // 1. Board playing bed (Warm smooth wood veneer)
        boardMesh = MeshBuilder.buildBox(boardSize, 0.2f, boardSize, floatArrayOf(0.93f, 0.86f, 0.73f, 1.0f))

        // 2. 3D Dark Hardwood Frame Borders
        val frameThick = 0.5f
        val frameH = 0.5f
        val frameColor = floatArrayOf(0.32f, 0.18f, 0.10f, 1.0f)
        frameNorthMesh = MeshBuilder.buildBox(boardSize + frameThick * 2, frameH, frameThick, frameColor)
        frameSouthMesh = MeshBuilder.buildBox(boardSize + frameThick * 2, frameH, frameThick, frameColor)
        frameEastMesh = MeshBuilder.buildBox(frameThick, frameH, boardSize, frameColor)
        frameWestMesh = MeshBuilder.buildBox(frameThick, frameH, boardSize, frameColor)

        // 3. Pockets (Dark depth)
        pocketMesh = MeshBuilder.buildCylinder(0.48f, 0.25f, 16, floatArrayOf(0.08f, 0.08f, 0.08f, 1.0f))

        initPieces()
    }

    fun initPieces() {
        discs.clear()

        val whiteMesh = MeshBuilder.buildCylinder(0.28f, 0.14f, 20, floatArrayOf(0.96f, 0.95f, 0.90f, 1.0f))
        val blackMesh = MeshBuilder.buildCylinder(0.28f, 0.14f, 20, floatArrayOf(0.18f, 0.18f, 0.20f, 1.0f))
        val queenMesh = MeshBuilder.buildCylinder(0.28f, 0.14f, 20, floatArrayOf(0.88f, 0.15f, 0.18f, 1.0f))
        val strikerMesh = MeshBuilder.buildCylinder(0.42f, 0.16f, 24, floatArrayOf(0.98f, 0.82f, 0.22f, 1.0f))

        // Center Red Queen
        discs.add(CarromDisc3D(0, 0f, 0f, 0f, 0f, 0.28f, 0.14f, isQueen = true, mesh = queenMesh))

        // Inner circle of 6 coins (alternating White & Black)
        val r1 = 0.62f
        for (i in 0 until 6) {
            val ang = Math.toRadians((i * 60.0)).toFloat()
            val isW = (i % 2 == 0)
            discs.add(
                CarromDisc3D(
                    id = i + 1,
                    x = r1 * cos(ang),
                    z = r1 * sin(ang),
                    radius = 0.28f,
                    height = 0.14f,
                    isWhite = isW,
                    mesh = if (isW) whiteMesh else blackMesh
                )
            )
        }

        // Outer circle of 12 coins
        val r2 = 1.22f
        for (i in 0 until 12) {
            val ang = Math.toRadians((i * 30.0 + 15.0)).toFloat()
            val isW = (i % 2 != 0)
            discs.add(
                CarromDisc3D(
                    id = i + 7,
                    x = r2 * cos(ang),
                    z = r2 * sin(ang),
                    radius = 0.28f,
                    height = 0.14f,
                    isWhite = isW,
                    mesh = if (isW) whiteMesh else blackMesh
                )
            )
        }

        // Striker placed on baseline
        discs.add(
            CarromDisc3D(
                id = 99,
                x = 0f,
                z = 2.4f,
                radius = 0.42f,
                height = 0.16f,
                isStriker = true,
                mesh = strikerMesh
            )
        )
    }

    fun stepPhysics(): Boolean {
        var anyMoving = false
        val friction = 0.982f
        val pocketRadius = 0.52f
        val pocketOffsets = arrayOf(
            floatArrayOf(-3.45f, -3.45f),
            floatArrayOf(3.45f, -3.45f),
            floatArrayOf(-3.45f, 3.45f),
            floatArrayOf(3.45f, 3.45f),
        )

        for (i in 0 until discs.size) {
            val d1 = discs[i]
            if (d1.isPocketed) continue

            if (Math.abs(d1.vx) > 0.001f || Math.abs(d1.vz) > 0.001f) {
                anyMoving = true
                d1.x += d1.vx
                d1.z += d1.vz
                d1.vx *= friction
                d1.vz *= friction

                if (Math.abs(d1.vx) < 0.002f) d1.vx = 0f
                if (Math.abs(d1.vz) < 0.002f) d1.vz = 0f
            }

            // Check pocket sink
            for (p in pocketOffsets) {
                val pdx = d1.x - p[0]
                val pdz = d1.z - p[1]
                val pdist = sqrt(pdx * pdx + pdz * pdz)
                if (pdist < pocketRadius) {
                    d1.isPocketed = true
                    d1.vx = 0f
                    d1.vz = 0f
                    break
                }
            }

            // Wall Collisions
            val bound = playableLimit - d1.radius
            if (d1.x > bound) { d1.x = bound; d1.vx = -Math.abs(d1.vx) * 0.88f }
            if (d1.x < -bound) { d1.x = -bound; d1.vx = Math.abs(d1.vx) * 0.88f }
            if (d1.z > bound) { d1.z = bound; d1.vz = -Math.abs(d1.vz) * 0.88f }
            if (d1.z < -bound) { d1.z = -bound; d1.vz = Math.abs(d1.vz) * 0.88f }

            // Disc-to-Disc Elastic Collision
            for (j in i + 1 until discs.size) {
                val d2 = discs[j]
                if (d2.isPocketed) continue

                val dx = d2.x - d1.x
                val dz = d2.z - d1.z
                val dist = sqrt(dx * dx + dz * dz)
                val minDist = d1.radius + d2.radius

                if (dist < minDist && dist > 0.0001f) {
                    val nx = dx / dist
                    val nz = dz / dist
                    val overlap = minDist - dist

                    // Separate to prevent sticking
                    d1.x -= nx * overlap * 0.5f
                    d1.z -= nz * overlap * 0.5f
                    d2.x += nx * overlap * 0.5f
                    d2.z += nz * overlap * 0.5f

                    // Momentum transfer
                    val kx = d1.vx - d2.vx
                    val kz = d1.vz - d2.vz
                    val p = (nx * kx + nz * kz)
                    val restitution = 0.92f

                    d1.vx -= p * nx * restitution
                    d1.vz -= p * nz * restitution
                    d2.vx += p * nx * restitution
                    d2.vz += p * nz * restitution
                }
            }
        }
        return anyMoving
    }

    override fun onRenderScene() {
        stepPhysics()

        // 1. Draw Board Bed
        Matrix.setIdentityM(modelMatrix, 0)
        drawMesh(boardMesh, modelMatrix)

        // 2. Draw 4 Frame Borders
        val fh = halfBoard + 0.25f
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, 0.15f, -fh)
        drawMesh(frameNorthMesh, modelMatrix)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, 0.15f, fh)
        drawMesh(frameSouthMesh, modelMatrix)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, fh, 0.15f, 0f)
        drawMesh(frameEastMesh, modelMatrix)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, -fh, 0.15f, 0f)
        drawMesh(frameWestMesh, modelMatrix)

        // 3. Draw 4 Corner Pockets
        val pOffsets = arrayOf(
            floatArrayOf(-3.45f, -3.45f),
            floatArrayOf(3.45f, -3.45f),
            floatArrayOf(-3.45f, 3.45f),
            floatArrayOf(3.45f, 3.45f),
        )
        for (p in pOffsets) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, p[0], 0.11f, p[1])
            drawMesh(pocketMesh, modelMatrix)
        }

        // 4. Draw All 3D Discs
        for (d in discs) {
            if (d.isPocketed) continue
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, d.x, 0.1f + d.height * 0.5f, d.z)
            drawMesh(d.mesh, modelMatrix)
        }
    }
}

@Composable
fun Carrom3DScreen(
    mode: GamePlayMode = GamePlayMode.VsBot,
    difficulty: Difficulty = Difficulty.Medium,
    onBack: () -> Unit,
) {
    val renderer = remember { Carrom3DRenderer() }

    var strikerBaselineX by remember { mutableFloatStateOf(0f) }
    var aimAngleDeg by remember { mutableFloatStateOf(-90f) }
    var shotPower by remember { mutableFloatStateOf(0.5f) }

    var isPlayerTurn by remember { mutableStateOf(true) }
    var isShotInProgress by remember { mutableStateOf(false) }

    var playerScore by remember { mutableIntStateOf(0) }
    var opponentScore by remember { mutableIntStateOf(0) }
    var queenPocketed by remember { mutableStateOf(false) }

    // Reposition striker when baseline slider moves
    fun updateStrikerBaseline(newX: Float) {
        strikerBaselineX = newX
        val striker = renderer.discs.firstOrNull { it.isStriker }
        if (striker != null && !isShotInProgress) {
            striker.x = newX
            striker.z = if (isPlayerTurn) 2.4f else -2.4f
            striker.vx = 0f
            striker.vz = 0f
            striker.isPocketed = false
        }
    }

    // Fire Striker Shot
    fun shoot(angleDeg: Float, power: Float) {
        val striker = renderer.discs.firstOrNull { it.isStriker } ?: return
        if (isShotInProgress) return

        isShotInProgress = true
        val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
        val speed = 0.08f + power * 0.32f
        striker.vx = speed * cos(rad)
        striker.vz = speed * sin(rad)
    }

    // Monitor Physics & Sunk Coins
    LaunchedEffect(isShotInProgress) {
        if (isShotInProgress) {
            delay(300) // Initial delay for physics impulse
            while (true) {
                delay(100)
                val moving = renderer.discs.any { Math.abs(it.vx) > 0.001f || Math.abs(it.vz) > 0.001f }
                if (!moving) break
            }

            // Check if any coins were pocketed
            var whiteCount = 0
            var blackCount = 0
            var queenSunk = false
            for (d in renderer.discs) {
                if (d.isPocketed) {
                    if (d.isWhite) whiteCount++
                    else if (!d.isStriker && !d.isQueen) blackCount++
                    else if (d.isQueen) queenSunk = true
                }
            }

            if (isPlayerTurn) {
                playerScore = whiteCount * 10 + (if (queenSunk) 25 else 0)
            } else {
                opponentScore = blackCount * 10 + (if (queenSunk) 25 else 0)
            }
            if (queenSunk) queenPocketed = true

            // Reset Striker to baseline
            val striker = renderer.discs.firstOrNull { it.isStriker }
            if (striker != null) {
                striker.isPocketed = false
                striker.vx = 0f
                striker.vz = 0f
                striker.x = 0f
                striker.z = if (isPlayerTurn) -2.4f else 2.4f
            }

            isPlayerTurn = !isPlayerTurn
            isShotInProgress = false

            // If Bot Turn, execute AI Shot
            if (!isPlayerTurn && mode == GamePlayMode.VsBot) {
                delay(800)
                val targetCoin = renderer.discs.filter { !it.isStriker && !it.isPocketed }.shuffled().firstOrNull()
                if (targetCoin != null) {
                    val sX = 0f; val sZ = -2.4f
                    val dx = targetCoin.x - sX
                    val dz = targetCoin.z - sZ
                    val angle = Math.toDegrees(atan2(dz.toDouble(), dx.toDouble())).toFloat()
                    val botPower = when (difficulty) {
                        Difficulty.Easy -> 0.45f
                        Difficulty.Medium -> 0.65f
                        Difficulty.Hard -> 0.82f
                    }
                    shoot(angle, botPower)
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
        Box(modifier = Modifier.fillMaxSize()) {

            // 3D Carrom Canvas
            Game3DCanvas(
                renderer = renderer,
                modifier = Modifier.fillMaxSize(),
                allowCameraOrbit = !isShotInProgress,
            )

            // Top Header: Scores & Turn
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 18.r, vertical = 10.r),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back button
                    Box(
                        modifier = Modifier
                            .size(42.r)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center,
                    ) {
                        RText("←", 20, FontWeight.Bold, Color.White)
                    }

                    // Scoreboard
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.r))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 16.r, vertical = 8.r),
                        horizontalArrangement = Arrangement.spacedBy(16.r),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("আপনি (সাদা)", 12, FontWeight.Bold, Color(0xFF34D399))
                            RText("$playerScore", 18, FontWeight.ExtraBold, Color.White)
                        }
                        Box(Modifier.width(1.r).height(24.r).background(Color.White.copy(alpha = 0.2f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText(if (mode == GamePlayMode.VsBot) "বট (কালো)" else "প্লেয়ার ২ (কালো)", 12, FontWeight.Bold, Color(0xFFF87171))
                            RText("$opponentScore", 18, FontWeight.ExtraBold, Color.White)
                        }
                    }

                    // Reset button
                    Box(
                        modifier = Modifier
                            .size(42.r)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                renderer.initPieces()
                                playerScore = 0
                                opponentScore = 0
                                isPlayerTurn = true
                                isShotInProgress = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        RText("🔄", 16, FontWeight.Bold, Color.White)
                    }
                }

                Spacer(Modifier.height(8.r))
                val turnText = if (isPlayerTurn) "🎯 আপনার চাল — স্ট্রাইকার লক্ষ্য স্থির করুন" else "⏳ প্রতিপক্ষের চাল..."
                val turnColor = if (isPlayerTurn) Color(0xFF34D399) else Color(0xFFFBBF24)
                RText(turnText, 13, FontWeight.SemiBold, turnColor, align = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            // Bottom Controls (Visible during Player Turn)
            if (isPlayerTurn && !isShotInProgress) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(18.r)
                        .clip(RoundedCornerShape(20.r))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(16.r),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Striker Base Position
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RText("বেসলাইন:", 13, FontWeight.Bold, Color.White)
                        Spacer(Modifier.width(8.r))
                        Slider(
                            value = strikerBaselineX,
                            onValueChange = { updateStrikerBaseline(it) },
                            valueRange = -2.0f..2.0f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFF59E0B), activeTrackColor = Color(0xFFF59E0B)),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Aim Angle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RText("কোণ:", 13, FontWeight.Bold, Color.White)
                        Spacer(Modifier.width(8.r))
                        Slider(
                            value = aimAngleDeg,
                            onValueChange = { aimAngleDeg = it },
                            valueRange = -160f..-20f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981)),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Shoot Button with Power
                    Button(
                        onClick = { shoot(aimAngleDeg, shotPower) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.r),
                        modifier = Modifier.fillMaxWidth().height(46.r),
                    ) {
                        RText("💥 স্ট্রাইক মারুন (Shoot)", 16, FontWeight.Bold, Color.White)
                    }
                }
            }
        }
    }
}
