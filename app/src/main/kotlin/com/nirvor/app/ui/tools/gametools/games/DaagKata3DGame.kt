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
import kotlin.math.sqrt

enum class BoxOwner { None, Player1, Player2 }

// Horizontal and Vertical Edge representations
data class BoardEdge(
    val id: Int,
    val isHorizontal: Boolean,
    val row: Int,
    val col: Int,
    val x1: Float, val z1: Float,
    val x2: Float, val z2: Float,
    var owner: BoxOwner = BoxOwner.None,
)

class DaagKata3DRenderer : Renderer3D() {

    private lateinit var boardMesh: Mesh
    private lateinit var pegMesh: Mesh
    private lateinit var hLineP1Mesh: Mesh
    private lateinit var hLineP2Mesh: Mesh
    private lateinit var vLineP1Mesh: Mesh
    private lateinit var vLineP2Mesh: Mesh
    private lateinit var tileP1Mesh: Mesh
    private lateinit var tileP2Mesh: Mesh

    val gridSize = 4 // 4x4 dots = 3x3 boxes = 9 boxes total
    val dotSpacing = 1.6f
    val halfExtent = (gridSize - 1) * dotSpacing * 0.5f

    val hEdges = ArrayList<BoardEdge>() // 4 rows * 3 cols = 12 edges
    val vEdges = ArrayList<BoardEdge>() // 3 rows * 4 cols = 12 edges
    val boxOwners = Array(3 * 3) { BoxOwner.None }

    init {
        initEdges()
    }

    override fun onSceneInit() {
        cameraPitch = 60f
        cameraYaw = 0f
        cameraDistance = 14f
        clearColor = floatArrayOf(0.11f, 0.13f, 0.18f, 1.0f)

        // 1. Board Bed
        boardMesh = MeshBuilder.buildBox(7.2f, 0.3f, 7.2f, floatArrayOf(0.20f, 0.22f, 0.28f, 1.0f))

        // 2. 3D Golden Pegs
        pegMesh = MeshBuilder.buildCylinder(0.18f, 0.35f, 16, floatArrayOf(0.95f, 0.78f, 0.25f, 1.0f))

        // 3. 3D Connecting Beams (Lines)
        val p1Color = floatArrayOf(0.10f, 0.75f, 0.45f, 1.0f) // Emerald Green
        val p2Color = floatArrayOf(0.60f, 0.35f, 0.90f, 1.0f) // Royal Purple

        hLineP1Mesh = MeshBuilder.buildBox(dotSpacing - 0.2f, 0.15f, 0.15f, p1Color)
        hLineP2Mesh = MeshBuilder.buildBox(dotSpacing - 0.2f, 0.15f, 0.15f, p2Color)

        vLineP1Mesh = MeshBuilder.buildBox(0.15f, 0.15f, dotSpacing - 0.2f, p1Color)
        vLineP2Mesh = MeshBuilder.buildBox(0.15f, 0.15f, dotSpacing - 0.2f, p2Color)

        // 4. 3D Raised Box Tiles
        tileP1Mesh = MeshBuilder.buildBox(dotSpacing - 0.24f, 0.12f, dotSpacing - 0.24f, floatArrayOf(0.10f, 0.75f, 0.45f, 0.85f))
        tileP2Mesh = MeshBuilder.buildBox(dotSpacing - 0.24f, 0.12f, dotSpacing - 0.24f, floatArrayOf(0.60f, 0.35f, 0.90f, 0.85f))

        initEdges()
    }

    fun initEdges() {
        hEdges.clear()
        vEdges.clear()
        for (i in 0 until 9) boxOwners[i] = BoxOwner.None

        var edgeId = 0

        // Build 12 Horizontal Edges
        for (r in 0 until gridSize) {
            val z = -halfExtent + r * dotSpacing
            for (c in 0 until gridSize - 1) {
                val x1 = -halfExtent + c * dotSpacing
                val x2 = x1 + dotSpacing
                hEdges.add(BoardEdge(edgeId++, true, r, c, x1, z, x2, z))
            }
        }

        // Build 12 Vertical Edges
        for (r in 0 until gridSize - 1) {
            val z1 = -halfExtent + r * dotSpacing
            val z2 = z1 + dotSpacing
            for (c in 0 until gridSize) {
                val x = -halfExtent + c * dotSpacing
                vEdges.add(BoardEdge(edgeId++, false, r, c, x, z1, x, z2))
            }
        }
    }

    fun findEdgeNear(worldX: Float, worldZ: Float): BoardEdge? {
        val thresh = 0.55f
        for (edge in hEdges) {
            val midX = (edge.x1 + edge.x2) * 0.5f
            val midZ = edge.z1
            val dx = worldX - midX
            val dz = worldZ - midZ
            if (sqrt(dx * dx + dz * dz) < thresh && edge.owner == BoxOwner.None) {
                return edge
            }
        }
        for (edge in vEdges) {
            val midX = edge.x1
            val midZ = (edge.z1 + edge.z2) * 0.5f
            val dx = worldX - midX
            val dz = worldZ - midZ
            if (sqrt(dx * dx + dz * dz) < thresh && edge.owner == BoxOwner.None) {
                return edge
            }
        }
        return null
    }

    override fun onRenderScene() {
        // 1. Board Bed
        Matrix.setIdentityM(modelMatrix, 0)
        drawMesh(boardMesh, modelMatrix)

        // 2. 16 Pegs
        for (r in 0 until gridSize) {
            val z = -halfExtent + r * dotSpacing
            for (c in 0 until gridSize) {
                val x = -halfExtent + c * dotSpacing
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, x, 0.28f, z)
                drawMesh(pegMesh, modelMatrix)
            }
        }

        // 3. Drawn Horizontal Lines
        for (edge in hEdges) {
            if (edge.owner != BoxOwner.None) {
                val mx = (edge.x1 + edge.x2) * 0.5f
                val mz = edge.z1
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, mx, 0.22f, mz)
                val m = if (edge.owner == BoxOwner.Player1) hLineP1Mesh else hLineP2Mesh
                drawMesh(m, modelMatrix)
            }
        }

        // 4. Drawn Vertical Lines
        for (edge in vEdges) {
            if (edge.owner != BoxOwner.None) {
                val mx = edge.x1
                val mz = (edge.z1 + edge.z2) * 0.5f
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, mx, 0.22f, mz)
                val m = if (edge.owner == BoxOwner.Player1) vLineP1Mesh else vLineP2Mesh
                drawMesh(m, modelMatrix)
            }
        }

        // 5. Captured 3D Box Tiles
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                val idx = r * 3 + c
                val owner = boxOwners[idx]
                if (owner != BoxOwner.None) {
                    val cx = -halfExtent + (c + 0.5f) * dotSpacing
                    val cz = -halfExtent + (r + 0.5f) * dotSpacing
                    Matrix.setIdentityM(modelMatrix, 0)
                    Matrix.translateM(modelMatrix, 0, cx, 0.20f, cz)
                    val m = if (owner == BoxOwner.Player1) tileP1Mesh else tileP2Mesh
                    drawMesh(m, modelMatrix)
                }
            }
        }
    }
}

@Composable
fun DaagKata3DScreen(
    mode: GamePlayMode = GamePlayMode.VsBot,
    difficulty: Difficulty = Difficulty.Medium,
    onBack: () -> Unit,
) {
    val renderer = remember { DaagKata3DRenderer() }

    var turn by remember { mutableStateOf(BoxOwner.Player1) }
    var p1Score by remember { mutableIntStateOf(0) }
    var p2Score by remember { mutableIntStateOf(0) }
    var isGameOver by remember { mutableStateOf(false) }
    var isThinking by remember { mutableStateOf(false) }

    // Check if drawing an edge completed any boxes (returns count of newly completed boxes)
    fun checkCompletedBoxes(activePlayer: BoxOwner): Int {
        var completed = 0
        for (r in 0 until 3) {
            for (c in 0 until 3) {
                val idx = r * 3 + c
                if (renderer.boxOwners[idx] == BoxOwner.None) {
                    // Box (r, c) borders:
                    // Top H edge: r, c
                    // Bottom H edge: r+1, c
                    // Left V edge: r, c
                    // Right V edge: r, c+1
                    val topEdge = renderer.hEdges.firstOrNull { it.row == r && it.col == c }
                    val bottomEdge = renderer.hEdges.firstOrNull { it.row == r + 1 && it.col == c }
                    val leftEdge = renderer.vEdges.firstOrNull { it.row == r && it.col == c }
                    val rightEdge = renderer.vEdges.firstOrNull { it.row == r && it.col == c + 1 }

                    val allDrawn = topEdge?.owner != BoxOwner.None &&
                        bottomEdge?.owner != BoxOwner.None &&
                        leftEdge?.owner != BoxOwner.None &&
                        rightEdge?.owner != BoxOwner.None

                    if (allDrawn) {
                        renderer.boxOwners[idx] = activePlayer
                        completed++
                    }
                }
            }
        }
        return completed
    }

    val touchListener = remember {
        object : Touch3DListener {
            override fun onTouchDown(worldX: Float, worldZ: Float, screenX: Float, screenY: Float): Boolean {
                if (isGameOver || isThinking) return false

                val edge = renderer.findEdgeNear(worldX, worldZ) ?: return false

                edge.owner = turn
                val closed = checkCompletedBoxes(turn)

                if (closed > 0) {
                    if (turn == BoxOwner.Player1) p1Score += closed else p2Score += closed
                    if (p1Score + p2Score >= 9) isGameOver = true
                    // Bonus turn! Keeps turn
                } else {
                    turn = if (turn == BoxOwner.Player1) BoxOwner.Player2 else BoxOwner.Player1
                }
                return true
            }

            override fun onTouchMove(worldX: Float, worldZ: Float, screenX: Float, screenY: Float) {}
            override fun onTouchUp(worldX: Float, worldZ: Float, screenX: Float, screenY: Float) {}
        }
    }

    // Bot Turn
    LaunchedEffect(turn, isGameOver) {
        if (!isGameOver && turn == BoxOwner.Player2 && mode == GamePlayMode.VsBot) {
            isThinking = true
            delay(650)

            // Collect all available edges
            val available = ArrayList<BoardEdge>()
            available.addAll(renderer.hEdges.filter { it.owner == BoxOwner.None })
            available.addAll(renderer.vEdges.filter { it.owner == BoxOwner.None })

            if (available.isNotEmpty()) {
                // Check if any edge immediately closes a box (Greedy completion)
                var bestEdge: BoardEdge? = null
                for (edge in available) {
                    edge.owner = BoxOwner.Player2
                    // Test if closes
                    var testClosed = 0
                    for (r in 0 until 3) {
                        for (c in 0 until 3) {
                            val idx = r * 3 + c
                            if (renderer.boxOwners[idx] == BoxOwner.None) {
                                val topEdge = renderer.hEdges.firstOrNull { it.row == r && it.col == c }
                                val bottomEdge = renderer.hEdges.firstOrNull { it.row == r + 1 && it.col == c }
                                val leftEdge = renderer.vEdges.firstOrNull { it.row == r && it.col == c }
                                val rightEdge = renderer.vEdges.firstOrNull { it.row == r && it.col == c + 1 }
                                if (topEdge?.owner != BoxOwner.None && bottomEdge?.owner != BoxOwner.None &&
                                    leftEdge?.owner != BoxOwner.None && rightEdge?.owner != BoxOwner.None) {
                                    testClosed++
                                }
                            }
                        }
                    }
                    edge.owner = BoxOwner.None // Revert test

                    if (testClosed > 0) {
                        bestEdge = edge
                        break
                    }
                }

                val chosen = bestEdge ?: available.shuffled().first()
                chosen.owner = BoxOwner.Player2
                val closed = checkCompletedBoxes(BoxOwner.Player2)

                if (closed > 0) {
                    p2Score += closed
                    if (p1Score + p2Score >= 9) isGameOver = true
                    // Bot gets bonus turn, keeps turn if not game over
                } else {
                    turn = BoxOwner.Player1
                }
            } else {
                isGameOver = true
            }

            isThinking = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
        Box(modifier = Modifier.fillMaxSize()) {

            Game3DCanvas(
                renderer = renderer,
                modifier = Modifier.fillMaxSize(),
                touchListener = touchListener,
                allowCameraOrbit = true,
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

                    // Box Score Counter
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.r))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 16.r, vertical = 8.r),
                        horizontalArrangement = Arrangement.spacedBy(16.r),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("প্লেয়ার ১ (সবুজ)", 11, FontWeight.Bold, Color(0xFF34D399))
                            RText("$p1Score ঘর", 15, FontWeight.ExtraBold, Color.White)
                        }
                        Box(Modifier.width(1.r).height(24.r).background(Color.White.copy(alpha = 0.2f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText(if (mode == GamePlayMode.VsBot) "বট (বেগুনি)" else "প্লেয়ার ২ (বেগুনি)", 11, FontWeight.Bold, Color(0xFFA78BFA))
                            RText("$p2Score ঘর", 15, FontWeight.ExtraBold, Color.White)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(42.r)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                renderer.initEdges()
                                p1Score = 0
                                p2Score = 0
                                turn = BoxOwner.Player1
                                isGameOver = false
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        RText("🔄", 16, FontWeight.Bold, Color.White)
                    }
                }

                Spacer(Modifier.height(8.r))
                val turnMsg = when {
                    isGameOver -> if (p1Score > p2Score) "🎉 প্লেয়ার ১ বিজয়ী হয়েছে!" else if (p2Score > p1Score) "🏆 প্লেয়ার ২ বিজয়ী হয়েছে!" else "🤝 সমান সমান ড্র!"
                    isThinking -> "🤖 বট দাগ টানার হিসাব করছে..."
                    turn == BoxOwner.Player1 -> "👉 আপনার চাল — দুটি বিন্দুর মাঝে দাগ টানুন"
                    else -> "👉 প্লেয়ার ২-এর চাল"
                }
                val turnCol = if (turn == BoxOwner.Player1) Color(0xFF34D399) else Color(0xFFA78BFA)
                RText(turnMsg, 13, FontWeight.Bold, turnCol, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            // Game Over Modal
            if (isGameOver) {
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
                        RText("খেলা সমাপ্ত! 🏆", 22, FontWeight.ExtraBold, Color.White)
                        Spacer(Modifier.height(8.r))
                        val winDesc = if (p1Score > p2Score) "অভিনন্দন! আপনি $p1Score - $p2Score স্কোরে জিতেছেন!" else if (p2Score > p1Score) "প্রতিপক্ষ $p2Score - $p1Score স্কোরে জিতেছে!" else "ম্যাচ ড্র হয়েছে!"
                        RText(winDesc, 14, FontWeight.Normal, Color(0xFF94A3B8), align = TextAlign.Center)
                        Spacer(Modifier.height(20.r))
                        Button(
                            onClick = {
                                renderer.initEdges()
                                p1Score = 0
                                p2Score = 0
                                turn = BoxOwner.Player1
                                isGameOver = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
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
