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
import androidx.compose.runtime.mutableStateListOf
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

// Sholo Guti Node coordinates and adjacency
data class GutiNode(val id: Int, val x: Float, val z: Float)

enum class GutiOwner { None, Player1, Player2 }

object SholoGutiBoardDef {
    val nodes = ArrayList<GutiNode>()
    val adjMap = HashMap<Int, MutableList<Int>>()

    init {
        // Build 37 nodes
        // 1. Top triangle: 6 nodes (y-span -4.5 to -2.5)
        // Row -2: Apex
        nodes.add(GutiNode(0, 0f, -4.5f))
        // Row -1: 2 nodes
        nodes.add(GutiNode(1, -1.0f, -3.5f))
        nodes.add(GutiNode(2, 1.0f, -3.5f))
        // Row 0 of triangle (3 nodes connecting to grid top)
        nodes.add(GutiNode(3, -2.0f, -2.5f))
        nodes.add(GutiNode(4, 0f, -2.5f))
        nodes.add(GutiNode(5, 2.0f, -2.5f))

        // 2. Central 5x5 Grid (25 nodes, indices 6 to 30)
        // Grid spacing: x in [-2, -1, 0, 1, 2], z in [-2, -1, 0, 1, 2]
        for (row in 0 until 5) {
            val z = -2.0f + row * 1.0f
            for (col in 0 until 5) {
                val x = -2.0f + col * 1.0f
                nodes.add(GutiNode(nodes.size, x, z))
            }
        }

        // 3. Bottom triangle: 6 nodes (indices 31 to 36)
        // Triangle base connecting to grid bottom (3 nodes)
        nodes.add(GutiNode(31, -2.0f, 2.5f))
        nodes.add(GutiNode(32, 0f, 2.5f))
        nodes.add(GutiNode(33, 2.0f, 2.5f))
        // Row 1: 2 nodes
        nodes.add(GutiNode(34, -1.0f, 3.5f))
        nodes.add(GutiNode(35, 1.0f, 3.5f))
        // Apex
        nodes.add(GutiNode(36, 0f, 4.5f))

        // Build adjacency graph
        for (i in 0 until 37) adjMap[i] = ArrayList()

        fun connect(u: Int, v: Int) {
            adjMap[u]?.add(v)
            adjMap[v]?.add(u)
        }

        // Top triangle connections
        connect(0, 1); connect(0, 2); connect(0, 4)
        connect(1, 2); connect(1, 3); connect(1, 4)
        connect(2, 4); connect(2, 5)
        connect(3, 4); connect(4, 5)
        // Connect triangle to 5x5 top row (nodes 6, 8, 10)
        connect(3, 6); connect(4, 8); connect(5, 10)

        // Central 5x5 grid connections (horizontal, vertical, diagonals on even sum)
        for (r in 0 until 5) {
            for (c in 0 until 5) {
                val idx = 6 + r * 5 + c
                // Right neighbor
                if (c < 4) connect(idx, idx + 1)
                // Down neighbor
                if (r < 4) connect(idx, idx + 5)
                // Diagonals for intersection nodes
                if ((r + c) % 2 == 0) {
                    if (r < 4 && c < 4) connect(idx, idx + 6)
                    if (r < 4 && c > 0) connect(idx, idx + 4)
                }
            }
        }

        // Bottom triangle connections
        // Connect grid bottom row (nodes 26, 28, 30) to triangle base (31, 32, 33)
        connect(26, 31); connect(28, 32); connect(30, 33)
        connect(31, 32); connect(32, 33)
        connect(31, 34); connect(32, 34); connect(32, 35); connect(33, 35)
        connect(34, 35); connect(34, 36); connect(35, 36); connect(32, 36)
    }

    fun getJumpTarget(from: Int, over: Int): Int? {
        val nFrom = nodes[from]
        val nOver = nodes[over]
        val dx = nOver.x - nFrom.x
        val dz = nOver.z - nFrom.z

        val targetX = nOver.x + dx
        val targetZ = nOver.z + dz

        // Find if target node exists in adjacency of 'over'
        for (nbr in adjMap[over] ?: emptyList()) {
            val candidate = nodes[nbr]
            if (Math.abs(candidate.x - targetX) < 0.1f && Math.abs(candidate.z - targetZ) < 0.1f) {
                return candidate.id
            }
        }
        return null
    }
}

class SholoGuti3DRenderer : Renderer3D() {

    private lateinit var boardMesh: Mesh
    private lateinit var lineMesh: Mesh
    private lateinit var greenGutiMesh: Mesh
    private lateinit var goldGutiMesh: Mesh
    private lateinit var highlightMesh: Mesh

    // Board state: 37 positions
    val boardState = Array(37) { GutiOwner.None }
    var selectedNode: Int? = null
    val legalTargets = ArrayList<Int>()

    init {
        resetBoard()
    }

    override fun onSceneInit() {
        cameraPitch = 58f
        cameraYaw = 0f
        cameraDistance = 16f
        clearColor = floatArrayOf(0.10f, 0.12f, 0.18f, 1.0f)

        // 3D Polished Wooden Board
        boardMesh = MeshBuilder.buildBox(8.5f, 0.3f, 11.5f, floatArrayOf(0.24f, 0.14f, 0.08f, 1.0f))

        // Grid rail lines
        lineMesh = MeshBuilder.buildCylinder(0.04f, 1.0f, 8, floatArrayOf(0.92f, 0.82f, 0.55f, 1.0f))

        // 3D Bevelled Guti Pieces
        greenGutiMesh = MeshBuilder.buildCylinder(0.32f, 0.22f, 18, floatArrayOf(0.06f, 0.72f, 0.42f, 1.0f))
        goldGutiMesh = MeshBuilder.buildCylinder(0.32f, 0.22f, 18, floatArrayOf(0.92f, 0.65f, 0.18f, 1.0f))

        // Glowing selection indicator
        highlightMesh = MeshBuilder.buildCylinder(0.36f, 0.05f, 18, floatArrayOf(0.2f, 0.9f, 0.4f, 0.7f))

        resetBoard()
    }

    fun resetBoard() {
        for (i in 0 until 37) {
            boardState[i] = when {
                i < 16 -> GutiOwner.Player1 // Top 16 pieces (Green)
                i in 16..20 -> GutiOwner.None // Middle 5 nodes empty
                else -> GutiOwner.Player2 // Bottom 16 pieces (Gold)
            }
        }
        selectedNode = null
        legalTargets.clear()
    }

    fun findNodeNear(x: Float, z: Float): Int? {
        for (node in SholoGutiBoardDef.nodes) {
            val dx = node.x - x
            val dz = node.z - z
            if (sqrt(dx * dx + dz * dz) < 0.65f) {
                return node.id
            }
        }
        return null
    }

    override fun onRenderScene() {
        // 1. Draw Board Bed
        Matrix.setIdentityM(modelMatrix, 0)
        drawMesh(boardMesh, modelMatrix)

        // 2. Draw Board Lines connecting adjacent nodes
        for ((u, neighbors) in SholoGutiBoardDef.adjMap) {
            val p1 = SholoGutiBoardDef.nodes[u]
            for (v in neighbors) {
                if (u < v) {
                    val p2 = SholoGutiBoardDef.nodes[v]
                    val mx = (p1.x + p2.x) * 0.5f
                    val mz = (p1.z + p2.z) * 0.5f
                    val dx = p2.x - p1.x
                    val dz = p2.z - p1.z
                    val len = sqrt(dx * dx + dz * dz)
                    val ang = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dz.toDouble())).toFloat()

                    Matrix.setIdentityM(modelMatrix, 0)
                    Matrix.translateM(modelMatrix, 0, mx, 0.16f, mz)
                    Matrix.rotateM(modelMatrix, 0, ang, 0f, 1f, 0f)
                    Matrix.rotateM(modelMatrix, 0, 90f, 1f, 0f, 0f)
                    Matrix.scaleM(modelMatrix, 0, 1f, len, 1f)
                    drawMesh(lineMesh, modelMatrix)
                }
            }
        }

        // 3. Draw Highlights for Legal Moves
        for (t in legalTargets) {
            val node = SholoGutiBoardDef.nodes[t]
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, node.x, 0.17f, node.z)
            drawMesh(highlightMesh, modelMatrix)
        }

        // 4. Draw All Guti Pieces
        for (i in 0 until 37) {
            val owner = boardState[i]
            if (owner != GutiOwner.None) {
                val node = SholoGutiBoardDef.nodes[i]
                val isSel = (i == selectedNode)
                val elevY = if (isSel) 0.42f else 0.26f // Selected piece levitates slightly!

                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, node.x, elevY, node.z)
                val m = if (owner == GutiOwner.Player1) greenGutiMesh else goldGutiMesh
                drawMesh(m, modelMatrix)
            }
        }
    }
}

@Composable
fun SholoGuti3DScreen(
    mode: GamePlayMode = GamePlayMode.VsBot,
    difficulty: Difficulty = Difficulty.Medium,
    onBack: () -> Unit,
) {
    val renderer = remember { SholoGuti3DRenderer() }

    var turn by remember { mutableStateOf(GutiOwner.Player1) } // Player 1 (Green) vs Player 2 (Gold)
    var player1Count by remember { mutableIntStateOf(16) }
    var player2Count by remember { mutableIntStateOf(16) }
    var winner by remember { mutableStateOf<GutiOwner?>(null) }
    var isThinking by remember { mutableStateOf(false) }

    fun refreshCounts() {
        var c1 = 0; var c2 = 0
        for (i in 0 until 37) {
            if (renderer.boardState[i] == GutiOwner.Player1) c1++
            if (renderer.boardState[i] == GutiOwner.Player2) c2++
        }
        player1Count = c1
        player2Count = c2
        if (c1 == 0) winner = GutiOwner.Player2
        if (c2 == 0) winner = GutiOwner.Player1
    }

    // Compute Legal Moves for a given node
    fun getLegalMoves(nodeId: Int): List<Pair<Int, Int?>> {
        val owner = renderer.boardState[nodeId]
        if (owner == GutiOwner.None) return emptyList()
        val moves = ArrayList<Pair<Int, Int?>>() // Pair of (DestinationNode, CapturedNode?)

        for (nbr in SholoGutiBoardDef.adjMap[nodeId] ?: emptyList()) {
            val nbrOwner = renderer.boardState[nbr]
            if (nbrOwner == GutiOwner.None) {
                // Regular simple move
                moves.add(Pair(nbr, null))
            } else if (nbrOwner != owner) {
                // Check capture jump
                val jumpTarget = SholoGutiBoardDef.getJumpTarget(nodeId, nbr)
                if (jumpTarget != null && renderer.boardState[jumpTarget] == GutiOwner.None) {
                    moves.add(Pair(jumpTarget, nbr))
                }
            }
        }
        return moves
    }

    // Handle touch picking on the 3D board
    val touchListener = remember {
        object : Touch3DListener {
            override fun onTouchDown(worldX: Float, worldZ: Float, screenX: Float, screenY: Float): Boolean {
                if (winner != null || isThinking) return false

                val clickedNode = renderer.findNodeNear(worldX, worldZ) ?: return false

                if (renderer.selectedNode == null) {
                    // Try selecting a piece belonging to current player
                    if (renderer.boardState[clickedNode] == turn) {
                        renderer.selectedNode = clickedNode
                        val validMoves = getLegalMoves(clickedNode)
                        renderer.legalTargets.clear()
                        renderer.legalTargets.addAll(validMoves.map { it.first })
                        return true
                    }
                } else {
                    val sel = renderer.selectedNode!!
                    val validMoves = getLegalMoves(sel)
                    val matchingMove = validMoves.firstOrNull { it.first == clickedNode }

                    if (matchingMove != null) {
                        // Execute Move
                        renderer.boardState[clickedNode] = turn
                        renderer.boardState[sel] = GutiOwner.None
                        matchingMove.second?.let { capturedIdx ->
                            renderer.boardState[capturedIdx] = GutiOwner.None
                        }

                        renderer.selectedNode = null
                        renderer.legalTargets.clear()
                        refreshCounts()

                        // Switch Turn
                        turn = if (turn == GutiOwner.Player1) GutiOwner.Player2 else GutiOwner.Player1
                        return true
                    } else if (renderer.boardState[clickedNode] == turn) {
                        // Switch selection to another own piece
                        renderer.selectedNode = clickedNode
                        val newMoves = getLegalMoves(clickedNode)
                        renderer.legalTargets.clear()
                        renderer.legalTargets.addAll(newMoves.map { it.first })
                        return true
                    } else {
                        // Deselect
                        renderer.selectedNode = null
                        renderer.legalTargets.clear()
                    }
                }
                return false
            }

            override fun onTouchMove(worldX: Float, worldZ: Float, screenX: Float, screenY: Float) {}
            override fun onTouchUp(worldX: Float, worldZ: Float, screenX: Float, screenY: Float) {}
        }
    }

    // AI Bot Turn Execution
    LaunchedEffect(turn, winner) {
        if (winner == null && turn == GutiOwner.Player2 && mode == GamePlayMode.VsBot) {
            isThinking = true
            delay(700)

            // Gather all legal moves for Player2
            val allMoves = ArrayList<Triple<Int, Int, Int?>>() // from, to, captured
            for (i in 0 until 37) {
                if (renderer.boardState[i] == GutiOwner.Player2) {
                    val m = getLegalMoves(i)
                    for (move in m) {
                        allMoves.add(Triple(i, move.first, move.second))
                    }
                }
            }

            if (allMoves.isNotEmpty()) {
                // Prioritize captures (Greedy AI)
                val captureMoves = allMoves.filter { it.third != null }
                val chosen = if (captureMoves.isNotEmpty() && difficulty != Difficulty.Easy) {
                    captureMoves.shuffled().first()
                } else {
                    allMoves.shuffled().first()
                }

                renderer.boardState[chosen.second] = GutiOwner.Player2
                renderer.boardState[chosen.first] = GutiOwner.None
                chosen.third?.let { cap ->
                    renderer.boardState[cap] = GutiOwner.None
                }
                refreshCounts()
            } else {
                // No valid moves left -> Player 1 wins
                winner = GutiOwner.Player1
            }

            turn = GutiOwner.Player1
            isThinking = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F172A)) {
        Box(modifier = Modifier.fillMaxSize()) {

            // 3D Canvas
            Game3DCanvas(
                renderer = renderer,
                modifier = Modifier.fillMaxSize(),
                touchListener = touchListener,
                allowCameraOrbit = true,
            )

            // Top Header: Score & Turn Bar
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

                    // Guti Counter
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.r))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 16.r, vertical = 8.r),
                        horizontalArrangement = Arrangement.spacedBy(16.r),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("প্লেয়ার ১ (সবুজ)", 12, FontWeight.Bold, Color(0xFF34D399))
                            RText("$player1Count গুটি", 16, FontWeight.ExtraBold, Color.White)
                        }
                        Box(Modifier.width(1.r).height(24.r).background(Color.White.copy(alpha = 0.2f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText(if (mode == GamePlayMode.VsBot) "বট (সোনালী)" else "প্লেয়ার ২ (সোনালী)", 12, FontWeight.Bold, Color(0xFFFBBF24))
                            RText("$player2Count গুটি", 16, FontWeight.ExtraBold, Color.White)
                        }
                    }

                    // Reset button
                    Box(
                        modifier = Modifier
                            .size(42.r)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                renderer.resetBoard()
                                refreshCounts()
                                turn = GutiOwner.Player1
                                winner = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        RText("🔄", 16, FontWeight.Bold, Color.White)
                    }
                }

                Spacer(Modifier.height(8.r))
                val turnMsg = when {
                    winner != null -> if (winner == GutiOwner.Player1) "🎉 প্লেয়ার ১ বিজয়ী হয়েছে!" else "🏆 প্লেয়ার ২ বিজয়ী হয়েছে!"
                    isThinking -> "🤖 বট গভীর চিন্তা করছে..."
                    turn == GutiOwner.Player1 -> "👉 আপনার চাল (সবুজ গুটি বেছে নিন)"
                    else -> "👉 প্লেয়ার ২-এর চাল (সোনালী গুটি)"
                }
                val turnCol = if (turn == GutiOwner.Player1) Color(0xFF34D399) else Color(0xFFFBBF24)
                RText(turnMsg, 14, FontWeight.Bold, turnCol, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            // Winner Modal
            if (winner != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
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
                        val winDesc = if (winner == GutiOwner.Player1) "প্লেয়ার ১ সব গুটি দখল করে বিজয়ী!" else "প্লেয়ার ২ বিজয়ী হয়েছে!"
                        RText(winDesc, 14, FontWeight.Normal, Color(0xFF94A3B8), align = TextAlign.Center)
                        Spacer(Modifier.height(20.r))
                        Button(
                            onClick = {
                                renderer.resetBoard()
                                refreshCounts()
                                turn = GutiOwner.Player1
                                winner = null
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
