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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

class Pen3D(
    val id: Int,
    var x: Float,
    var y: Float = 0.22f,
    var z: Float,
    var angleDeg: Float,
    var vx: Float = 0f,
    var vz: Float = 0f,
    var vRot: Float = 0f,
    val length: Float = 2.4f,
    val radius: Float = 0.18f,
    var isFallenOff: Boolean = false,
    val mesh: Mesh,
)

class PenFight3DRenderer : Renderer3D() {

    private lateinit var deskMesh: Mesh
    private lateinit var legMesh: Mesh
    private lateinit var aimArrowMesh: Mesh

    lateinit var playerPen: Pen3D
    lateinit var opponentPen: Pen3D

    val deskW = 6.4f
    val deskL = 9.2f
    val halfW = deskW * 0.5f
    val halfL = deskL * 0.5f

    // Aim Line
    var isAiming: Boolean = false
    var aimPullX: Float = 0f
    var aimPullZ: Float = 0f

    init {
        val p1BodyColor = floatArrayOf(0.15f, 0.55f, 0.95f, 1.0f) // Neon Blue Pen
        val p1TipColor = floatArrayOf(0.85f, 0.75f, 0.60f, 1.0f)
        val p1LeadColor = floatArrayOf(0.10f, 0.10f, 0.10f, 1.0f)

        val p2BodyColor = floatArrayOf(0.92f, 0.25f, 0.20f, 1.0f) // Fiery Red Pen
        val p2TipColor = floatArrayOf(0.85f, 0.75f, 0.60f, 1.0f)
        val p2LeadColor = floatArrayOf(0.10f, 0.10f, 0.10f, 1.0f)

        val playerMesh = MeshBuilder.buildPen(0.18f, 2.0f, 0.5f, p1BodyColor, p1TipColor, p1LeadColor)
        val opponentMesh = MeshBuilder.buildPen(0.18f, 2.0f, 0.5f, p2BodyColor, p2TipColor, p2LeadColor)

        playerPen = Pen3D(1, x = 0f, z = 2.6f, angleDeg = -90f, mesh = playerMesh)
        opponentPen = Pen3D(2, x = 0f, z = -2.6f, angleDeg = 90f, mesh = opponentMesh)
    }

    override fun onSceneInit() {
        cameraPitch = 50f
        cameraYaw = 0f
        cameraDistance = 15f
        clearColor = floatArrayOf(0.11f, 0.13f, 0.17f, 1.0f)

        // 3D Wooden Classroom Desk
        deskMesh = MeshBuilder.buildBox(deskW, 0.35f, deskL, floatArrayOf(0.68f, 0.48f, 0.32f, 1.0f))
        legMesh = MeshBuilder.buildCylinder(0.18f, 4.0f, 12, floatArrayOf(0.22f, 0.22f, 0.25f, 1.0f))
        aimArrowMesh = MeshBuilder.buildCylinder(0.06f, 1.2f, 8, floatArrayOf(0.98f, 0.82f, 0.22f, 0.8f))
    }

    fun resetPositions() {
        playerPen.x = 0f
        playerPen.y = 0.22f
        playerPen.z = 2.6f
        playerPen.angleDeg = -90f
        playerPen.vx = 0f
        playerPen.vz = 0f
        playerPen.vRot = 0f
        playerPen.isFallenOff = false

        opponentPen.x = 0f
        opponentPen.y = 0.22f
        opponentPen.z = -2.6f
        opponentPen.angleDeg = 90f
        opponentPen.vx = 0f
        opponentPen.vz = 0f
        opponentPen.vRot = 0f
        opponentPen.isFallenOff = false

        isAiming = false
    }

    fun stepPhysics(): Boolean {
        var moving = false
        val friction = 0.978f
        val rotFriction = 0.965f

        val pens = listOf(playerPen, opponentPen)

        for (pen in pens) {
            if (pen.isFallenOff) {
                // Gravity fall down
                if (pen.y > -5f) {
                    pen.y -= 0.18f
                    pen.x += pen.vx
                    pen.z += pen.vz
                    moving = true
                }
                continue
            }

            if (Math.abs(pen.vx) > 0.001f || Math.abs(pen.vz) > 0.001f || Math.abs(pen.vRot) > 0.01f) {
                moving = true
                pen.x += pen.vx
                pen.z += pen.vz
                pen.angleDeg += pen.vRot

                pen.vx *= friction
                pen.vz *= friction
                pen.vRot *= rotFriction

                if (Math.abs(pen.vx) < 0.002f) pen.vx = 0f
                if (Math.abs(pen.vz) < 0.002f) pen.vz = 0f
                if (Math.abs(pen.vRot) < 0.02f) pen.vRot = 0f

                // Check Desk Fall-off
                val margin = 0.25f
                if (Math.abs(pen.x) > halfW - margin || Math.abs(pen.z) > halfL - margin) {
                    pen.isFallenOff = true
                }
            }
        }

        // Pen-to-Pen Impact Physics
        if (!playerPen.isFallenOff && !opponentPen.isFallenOff) {
            val dx = opponentPen.x - playerPen.x
            val dz = opponentPen.z - playerPen.z
            val dist = sqrt(dx * dx + dz * dz)
            val minDist = (playerPen.length + opponentPen.length) * 0.38f

            if (dist < minDist && dist > 0.001f) {
                val nx = dx / dist
                val nz = dz / dist
                val overlap = minDist - dist

                playerPen.x -= nx * overlap * 0.5f
                playerPen.z -= nz * overlap * 0.5f
                opponentPen.x += nx * overlap * 0.5f
                opponentPen.z += nz * overlap * 0.5f

                val kx = playerPen.vx - opponentPen.vx
                val kz = playerPen.vz - opponentPen.vz
                val p = (nx * kx + nz * kz)
                val restitution = 0.94f

                playerPen.vx -= p * nx * restitution
                playerPen.vz -= p * nz * restitution
                opponentPen.vx += p * nx * restitution
                opponentPen.vz += p * nz * restitution

                // Add spin on impact
                playerPen.vRot += (nx * 8f)
                opponentPen.vRot -= (nx * 8f)
                moving = true
            }
        }

        return moving
    }

    override fun onRenderScene() {
        stepPhysics()

        // 1. Draw Desk Surface
        Matrix.setIdentityM(modelMatrix, 0)
        drawMesh(deskMesh, modelMatrix)

        // 2. Draw 4 Desk Legs
        val legOffsets = arrayOf(
            floatArrayOf(-halfW + 0.35f, -halfL + 0.35f),
            floatArrayOf(halfW - 0.35f, -halfL + 0.35f),
            floatArrayOf(-halfW + 0.35f, halfL - 0.35f),
            floatArrayOf(halfW - 0.35f, halfL - 0.35f),
        )
        for (leg in legOffsets) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, leg[0], -2.0f, leg[1])
            drawMesh(legMesh, modelMatrix)
        }

        // 3. Draw Player Pen
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, playerPen.x, playerPen.y, playerPen.z)
        Matrix.rotateM(modelMatrix, 0, playerPen.angleDeg, 0f, 1f, 0f)
        drawMesh(playerPen.mesh, modelMatrix)

        // 4. Draw Opponent Pen
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, opponentPen.x, opponentPen.y, opponentPen.z)
        Matrix.rotateM(modelMatrix, 0, opponentPen.angleDeg, 0f, 1f, 0f)
        drawMesh(opponentPen.mesh, modelMatrix)

        // 5. Draw Aim Arrow during flick
        if (isAiming) {
            val dx = aimPullX - playerPen.x
            val dz = aimPullZ - playerPen.z
            val len = sqrt(dx * dx + dz * dz).coerceIn(0.2f, 2.5f)
            val ang = Math.toDegrees(atan2(-dx.toDouble(), -dz.toDouble())).toFloat()

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, playerPen.x, 0.28f, playerPen.z)
            Matrix.rotateM(modelMatrix, 0, ang, 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
            Matrix.scaleM(modelMatrix, 0, 1f, len, 1f)
            drawMesh(aimArrowMesh, modelMatrix)
        }
    }
}

@Composable
fun PenFight3DScreen(
    mode: GamePlayMode = GamePlayMode.VsBot,
    difficulty: Difficulty = Difficulty.Medium,
    onBack: () -> Unit,
) {
    val renderer = remember { PenFight3DRenderer() }

    var isPlayerTurn by remember { mutableStateOf(true) }
    var isShotInProgress by remember { mutableStateOf(false) }

    var playerRoundsWon by remember { mutableIntStateOf(0) }
    var opponentRoundsWon by remember { mutableIntStateOf(0) }
    var roundStatusText by remember { mutableStateOf("আপনার পালা — কলমটি টেনে ফ্লিক করুন") }
    var matchOverWinner by remember { mutableStateOf<String?>(null) }

    fun flickPen(pen: Pen3D, pullDx: Float, pullDz: Float) {
        val dist = sqrt(pullDx * pullDx + pullDz * pullDz).coerceIn(0.2f, 3.0f)
        val power = dist * 0.12f
        val rad = atan2(pullDz.toDouble(), pullDx.toDouble()).toFloat()

        pen.vx = -power * cos(rad)
        pen.vz = -power * sin(rad)
        pen.vRot = (Math.random().toFloat() - 0.5f) * 12f
        isShotInProgress = true
    }

    val touchListener = remember {
        object : Touch3DListener {
            override fun onTouchDown(worldX: Float, worldZ: Float, screenX: Float, screenY: Float): Boolean {
                if (!isPlayerTurn || isShotInProgress || matchOverWinner != null) return false
                val p = renderer.playerPen
                val dx = worldX - p.x
                val dz = worldZ - p.z
                if (sqrt(dx * dx + dz * dz) < 1.6f) {
                    renderer.isAiming = true
                    renderer.aimPullX = worldX
                    renderer.aimPullZ = worldZ
                    return true
                }
                return false
            }

            override fun onTouchMove(worldX: Float, worldZ: Float, screenX: Float, screenY: Float) {
                if (renderer.isAiming) {
                    renderer.aimPullX = worldX
                    renderer.aimPullZ = worldZ
                }
            }

            override fun onTouchUp(worldX: Float, worldZ: Float, screenX: Float, screenY: Float) {
                if (renderer.isAiming) {
                    renderer.isAiming = false
                    val p = renderer.playerPen
                    val dx = worldX - p.x
                    val dz = worldZ - p.z
                    flickPen(p, dx, dz)
                }
            }
        }
    }

    // Monitor Physics & Round KO
    LaunchedEffect(isShotInProgress) {
        if (isShotInProgress) {
            delay(250)
            while (true) {
                delay(80)
                val moving = Math.abs(renderer.playerPen.vx) > 0.001f || Math.abs(renderer.playerPen.vz) > 0.001f ||
                    Math.abs(renderer.opponentPen.vx) > 0.001f || Math.abs(renderer.opponentPen.vz) > 0.001f ||
                    (renderer.playerPen.isFallenOff && renderer.playerPen.y > -4.5f) ||
                    (renderer.opponentPen.isFallenOff && renderer.opponentPen.y > -4.5f)
                if (!moving) break
            }

            // Check KO winner for round
            if (renderer.opponentPen.isFallenOff && !renderer.playerPen.isFallenOff) {
                playerRoundsWon++
                roundStatusText = "💥 নকআউট! আপনি রাউন্ড জিতেছেন!"
            } else if (renderer.playerPen.isFallenOff && !renderer.opponentPen.isFallenOff) {
                opponentRoundsWon++
                roundStatusText = "😢 হায়! আপনার কলম পড়ে গেছে!"
            } else if (renderer.playerPen.isFallenOff && renderer.opponentPen.isFallenOff) {
                roundStatusText = "🤝 ড্র! উভয় কলমই পড়ে গেছে!"
            }

            if (playerRoundsWon >= 2) {
                matchOverWinner = "আপনি"
            } else if (opponentRoundsWon >= 2) {
                matchOverWinner = if (mode == GamePlayMode.VsBot) "বট" else "প্লেয়ার ২"
            } else {
                // Next turn or reset positions if a pen fell off
                if (renderer.playerPen.isFallenOff || renderer.opponentPen.isFallenOff) {
                    delay(1200)
                    renderer.resetPositions()
                }

                isPlayerTurn = !isPlayerTurn
                isShotInProgress = false

                // Bot Turn
                if (!isPlayerTurn && mode == GamePlayMode.VsBot && matchOverWinner == null) {
                    delay(800)
                    val p = renderer.opponentPen
                    val target = renderer.playerPen
                    val dx = target.x - p.x
                    val dz = target.z - p.z
                    val dist = sqrt(dx * dx + dz * dz)
                    val botPull = when (difficulty) {
                        Difficulty.Easy -> 1.0f
                        Difficulty.Medium -> 1.6f
                        Difficulty.Hard -> 2.2f
                    }
                    val dirX = (dx / dist) * botPull
                    val dirZ = (dz / dist) * botPull
                    // Flick towards player pen
                    flickPen(p, -dirX, -dirZ)
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
        Box(modifier = Modifier.fillMaxSize()) {

            // 3D Canvas
            Game3DCanvas(
                renderer = renderer,
                modifier = Modifier.fillMaxSize(),
                touchListener = touchListener,
                allowCameraOrbit = !renderer.isAiming,
            )

            // Top Header Bar
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

                    // Scoreboard (Best of 3)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.r))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 16.r, vertical = 8.r),
                        horizontalArrangement = Arrangement.spacedBy(16.r),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("নীল কলম (আপনি)", 11, FontWeight.Bold, Color(0xFF38BDF8))
                            RText("$playerRoundsWon জয়", 15, FontWeight.ExtraBold, Color.White)
                        }
                        Box(Modifier.width(1.r).height(24.r).background(Color.White.copy(alpha = 0.2f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText(if (mode == GamePlayMode.VsBot) "লাল কলম (বট)" else "লাল কলম (প্লেয়ার ২)", 11, FontWeight.Bold, Color(0xFFF87171))
                            RText("$opponentRoundsWon জয়", 15, FontWeight.ExtraBold, Color.White)
                        }
                    }

                    // Reset button
                    Box(
                        modifier = Modifier
                            .size(42.r)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                renderer.resetPositions()
                                playerRoundsWon = 0
                                opponentRoundsWon = 0
                                isPlayerTurn = true
                                isShotInProgress = false
                                matchOverWinner = null
                                roundStatusText = "আপনার পালা — কলমটি টেনে ফ্লিক করুন"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        RText("🔄", 16, FontWeight.Bold, Color.White)
                    }
                }

                Spacer(Modifier.height(8.r))
                val turnCol = if (isPlayerTurn) Color(0xFF38BDF8) else Color(0xFFFBBF24)
                RText(
                    roundStatusText,
                    13,
                    FontWeight.Bold,
                    turnCol,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Match Winner Modal
            if (matchOverWinner != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .width(280.r)
                            .clip(RoundedCornerShape(20.r))
                            .background(Color(0xFF1E293B))
                            .border(1.5.r, Color(0xFF334155), RoundedCornerShape(20.r))
                            .padding(24.r),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RText("ম্যাচ সমাপ্ত! 🏆", 22, FontWeight.ExtraBold, Color.White)
                        Spacer(Modifier.height(8.r))
                        RText("$matchOverWinner পেন ফাইট চ্যাম্পিয়ন!", 15, FontWeight.Bold, Color(0xFF38BDF8), align = TextAlign.Center)
                        Spacer(Modifier.height(20.r))
                        Button(
                            onClick = {
                                renderer.resetPositions()
                                playerRoundsWon = 0
                                opponentRoundsWon = 0
                                isPlayerTurn = true
                                isShotInProgress = false
                                matchOverWinner = null
                                roundStatusText = "আপনার পালা — কলমটি টেনে ফ্লিক করুন"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(12.r),
                            modifier = Modifier.fillMaxWidth().height(44.r),
                        ) {
                            RText("পুনরায় খেলুন 🔄", 15, FontWeight.Bold, Color.White)
                        }
                    }
                }
            }
        }
    }
}
