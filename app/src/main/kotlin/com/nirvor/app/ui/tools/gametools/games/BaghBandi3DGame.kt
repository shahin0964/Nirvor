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

enum class BaghRole { Tiger, Goat }
enum class BaghNodeState { Empty, Tiger, Goat }

data class BaghNode(val id: Int, val x: Float, val z: Float)

object BaghBandiBoardDef {
    val nodes = ArrayList<BaghNode>()
    val adjMap = HashMap<Int, MutableList<Int>>()

    init {
        // 5x5 Grid (25 nodes, indices 0 to 24)
        for (r in 0 until 5) {
            val z = -2.4f + r * 1.2f
            for (c in 0 until 5) {
                val x = -2.4f + c * 1.2f
                nodes.add(BaghNode(nodes.size, x, z))
            }
        }

        for (i in 0 until 25) adjMap[i] = ArrayList()

        fun connect(u: Int, v: Int) {
            adjMap[u]?.add(v)
            adjMap[v]?.add(u)
        }

        for (r in 0 until 5) {
            for (c in 0 until 5) {
                val idx = r * 5 + c
                if (c < 4) connect(idx, idx + 1)
                if (r < 4) connect(idx, idx + 5)
                if ((r + c) % 2 == 0) {
                    if (r < 4 && c < 4) connect(idx, idx + 6)
                    if (r < 4 && c > 0) connect(idx, idx + 4)
                }
            }
        }
    }

    fun getJumpTarget(from: Int, over: Int): Int? {
        val nFrom = nodes[from]
        val nOver = nodes[over]
        val dx = nOver.x - nFrom.x
        val dz = nOver.z - nFrom.z

        val targetX = nOver.x + dx
        val targetZ = nOver.z + dz

        for (nbr in adjMap[over] ?: emptyList()) {
            val candidate = nodes[nbr]
            if (Math.abs(candidate.x - targetX) < 0.1f && Math.abs(candidate.z - targetZ) < 0.1f) {
                return candidate.id
            }
        }
        return null
    }
}

class BaghBandi3DRenderer : Renderer3D() {

    private lateinit var boardMesh: Mesh
    private lateinit var lineMesh: Mesh
    private lateinit var tigerMesh: Mesh
    private lateinit var goatMesh: Mesh
    private lateinit var highlightMesh: Mesh

    val nodeStates = Array(25) { BaghNodeState.Empty }
    var selectedNode: Int? = null
    val legalTargets = ArrayList<Int>()

    init {
        resetBoard()
    }

    override fun onSceneInit() {
        cameraPitch = 56f
        cameraYaw = 0f
        cameraDistance = 14f
        clearColor = floatArrayOf(0.12f, 0.14f, 0.20f, 1.0f)

        // 3D Polished Terracotta / Dark Sandalwood Board
        boardMesh = MeshBuilder.buildBox(7.2f, 0.3f, 7.2f, floatArrayOf(0.36f, 0.22f, 0.14f, 1.0f))
        lineMesh = MeshBuilder.buildCylinder(0.04f, 1.0f, 8, floatArrayOf(0.92f, 0.78f, 0.45f, 1.0f))

        // 3D Tiger: Larger, Fierce Fiery Crimson with Gold Top
        tigerMesh = MeshBuilder.buildCylinder(0.42f, 0.32f, 20, floatArrayOf(0.95f, 0.22f, 0.15f, 1.0f))

        // 3D Goat: Pure Ivory Cream
        goatMesh = MeshBuilder.buildCylinder(0.28f, 0.22f, 18, floatArrayOf(0.95f, 0.95f, 0.92f, 1.0f))

        highlightMesh = MeshBuilder.buildCylinder(0.35f, 0.05f, 18, floatArrayOf(0.1f, 0.9f, 0.4f, 0.75f))

        resetBoard()
    }

    fun resetBoard() {
        for (i in 0 until 25) nodeStates[i] = BaghNodeState.Empty
        // 2 Tigers start at top corners
        nodeStates[0] = BaghNodeState.Tiger
        nodeStates[4] = BaghNodeState.Tiger

        selectedNode = null
        legalTargets.clear()
    }

    fun findNodeNear(x: Float, z: Float): Int? {
        for (node in BaghBandiBoardDef.nodes) {
            val dx = node.x - x
            val dz = node.z - z
            if (sqrt(dx * dx + dz * dz) < 0.65f) return node.id
        }
        return null
    }

    override fun onRenderScene() {
        // 1. Board Bed
        Matrix.setIdentityM(modelMatrix, 0)
        drawMesh(boardMesh, modelMatrix)

        // 2. Grid lines
        for ((u, neighbors) in BaghBandiBoardDef.adjMap) {
            val p1 = BaghBandiBoardDef.nodes[u]
            for (v in neighbors) {
                if (u < v) {
                    val p2 = BaghBandiBoardDef.nodes[v]
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

        // 3. Highlights
        for (t in legalTargets) {
            val node = BaghBandiBoardDef.nodes[t]
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, node.x, 0.17f, node.z)
            drawMesh(highlightMesh, modelMatrix)
        }

        // 4. Pieces
        for (i in 0 until 25) {
            val state = nodeStates[i]
            if (state != BaghNodeState.Empty) {
                val node = BaghBandiBoardDef.nodes[i]
                val isSel = (i == selectedNode)
                val elevY = if (isSel) 0.44f else 0.26f

                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, node.x, elevY, node.z)
                val m = if (state == BaghNodeState.Tiger) tigerMesh else goatMesh
                drawMesh(m, modelMatrix)
            }
        }
    }
}

@Composable
fun BaghBandi3DScreen(
    mode: GamePlayMode = GamePlayMode.VsBot,
    difficulty: Difficulty = Difficulty.Medium,
    onBack: () -> Unit,
) {
    val renderer = remember { BaghBandi3DRenderer() }

    var turn by remember { mutableStateOf(BaghRole.Goat) } // Goats move first
    var goatsPlaced by remember { mutableIntStateOf(0) }
    var goatsCaptured by remember { mutableIntStateOf(0) }
    var winner by remember { mutableStateOf<String?>(null) }
    var isThinking by remember { mutableStateOf(false) }

    fun checkWinCondition() {
        if (goatsCaptured >= 5) {
            winner = "বাঘ (Tigers Won!)"
            return
        }

        // Check if tigers are completely trapped
        var tigerCanMove = false
        for (i in 0 until 25) {
            if (renderer.nodeStates[i] == BaghNodeState.Tiger) {
                for (nbr in BaghBandiBoardDef.adjMap[i] ?: emptyList()) {
                    if (renderer.nodeStates[nbr] == BaghNodeState.Empty) tigerCanMove = true
                    val jump = BaghBandiBoardDef.getJumpTarget(i, nbr)
                    if (jump != null && renderer.nodeStates[jump] == BaghNodeState.Empty && renderer.nodeStates[nbr] == BaghNodeState.Goat) {
                        tigerCanMove = true
                    }
                }
            }
        }

        if (!tigerCanMove && goatsPlaced >= 10) {
            winner = "ছাগল (Goats Won — বাঘ বন্দী!)"
        }
    }

    fun getTigerMoves(tigerNode: Int): List<Pair<Int, Int?>> {
        val moves = ArrayList<Pair<Int, Int?>>()
        for (nbr in BaghBandiBoardDef.adjMap[tigerNode] ?: emptyList()) {
            if (renderer.nodeStates[nbr] == BaghNodeState.Empty) {
                moves.add(Pair(nbr, null))
            } else if (renderer.nodeStates[nbr] == BaghNodeState.Goat) {
                val jump = BaghBandiBoardDef.getJumpTarget(tigerNode, nbr)
                if (jump != null && renderer.nodeStates[jump] == BaghNodeState.Empty) {
                    moves.add(Pair(jump, nbr)) // Jump & capture
                }
            }
        }
        return moves
    }

    fun getGoatMoves(goatNode: Int): List<Int> {
        val moves = ArrayList<Int>()
        for (nbr in BaghBandiBoardDef.adjMap[goatNode] ?: emptyList()) {
            if (renderer.nodeStates[nbr] == BaghNodeState.Empty) moves.add(nbr)
        }
        return moves
    }

    val touchListener = remember {
        object : Touch3DListener {
            override fun onTouchDown(worldX: Float, worldZ: Float, screenX: Float, screenY: Float): Boolean {
                if (winner != null || isThinking) return false

                val clicked = renderer.findNodeNear(worldX, worldZ) ?: return false

                if (turn == BaghRole.Goat) {
                    // Phase 1: Goat placement (up to 15 goats)
                    if (goatsPlaced < 15) {
                        if (renderer.nodeStates[clicked] == BaghNodeState.Empty) {
                            renderer.nodeStates[clicked] = BaghNodeState.Goat
                            goatsPlaced++
                            checkWinCondition()
                            turn = BaghRole.Tiger
                            return true
                        }
                    } else {
                        // Phase 2: Goat movement
                        if (renderer.selectedNode == null) {
                            if (renderer.nodeStates[clicked] == BaghNodeState.Goat) {
                                renderer.selectedNode = clicked
                                val valid = getGoatMoves(clicked)
                                renderer.legalTargets.clear()
                                renderer.legalTargets.addAll(valid)
                                return true
                            }
                        } else {
                            val sel = renderer.selectedNode!!
                            val valid = getGoatMoves(sel)
                            if (clicked in valid) {
                                renderer.nodeStates[clicked] = BaghNodeState.Goat
                                renderer.nodeStates[sel] = BaghNodeState.Empty
                                renderer.selectedNode = null
                                renderer.legalTargets.clear()
                                checkWinCondition()
                                turn = BaghRole.Tiger
                                return true
                            } else if (renderer.nodeStates[clicked] == BaghNodeState.Goat) {
                                renderer.selectedNode = clicked
                                renderer.legalTargets.clear()
                                renderer.legalTargets.addAll(getGoatMoves(clicked))
                                return true
                            } else {
                                renderer.selectedNode = null
                                renderer.legalTargets.clear()
                            }
                        }
                    }
                } else if (turn == BaghRole.Tiger && mode == GamePlayMode.PassAndPlay) {
                    // 2-Player local tiger move
                    if (renderer.selectedNode == null) {
                        if (renderer.nodeStates[clicked] == BaghNodeState.Tiger) {
                            renderer.selectedNode = clicked
                            val valid = getTigerMoves(clicked)
                            renderer.legalTargets.clear()
                            renderer.legalTargets.addAll(valid.map { it.first })
                            return true
                        }
                    } else {
                        val sel = renderer.selectedNode!!
                        val valid = getTigerMoves(sel)
                        val matching = valid.firstOrNull { it.first == clicked }
                        if (matching != null) {
                            renderer.nodeStates[clicked] = BaghNodeState.Tiger
                            renderer.nodeStates[sel] = BaghNodeState.Empty
                            matching.second?.let {
                                renderer.nodeStates[it] = BaghNodeState.Empty
                                goatsCaptured++
                            }
                            renderer.selectedNode = null
                            renderer.legalTargets.clear()
                            checkWinCondition()
                            turn = BaghRole.Goat
                            return true
                        } else {
                            renderer.selectedNode = null
                            renderer.legalTargets.clear()
                        }
                    }
                }
                return false
            }

            override fun onTouchMove(worldX: Float, worldZ: Float, screenX: Float, screenY: Float) {}
            override fun onTouchUp(worldX: Float, worldZ: Float, screenX: Float, screenY: Float) {}
        }
    }

    // Bot Tiger Turn Execution
    LaunchedEffect(turn, winner) {
        if (winner == null && turn == BaghRole.Tiger && mode == GamePlayMode.VsBot) {
            isThinking = true
            delay(750)

            val allTigerMoves = ArrayList<Triple<Int, Int, Int?>>() // from, to, captured
            for (i in 0 until 25) {
                if (renderer.nodeStates[i] == BaghNodeState.Tiger) {
                    val m = getTigerMoves(i)
                    for (move in m) {
                        allTigerMoves.add(Triple(i, move.first, move.second))
                    }
                }
            }

            if (allTigerMoves.isNotEmpty()) {
                val jumps = allTigerMoves.filter { it.third != null }
                val chosen = if (jumps.isNotEmpty()) {
                    jumps.shuffled().first()
                } else {
                    allTigerMoves.shuffled().first()
                }

                renderer.boardStateAction(chosen.first, chosen.second, chosen.third)
                chosen.third?.let { goatsCaptured++ }
                checkWinCondition()
            } else {
                winner = "ছাগল (Goats Won — বাঘ বন্দী!)"
            }

            turn = BaghRole.Goat
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

                    // Goats Placed & Captured Counter
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.r))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 16.r, vertical = 8.r),
                        horizontalArrangement = Arrangement.spacedBy(16.r),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("ছাগল বাকি", 11, FontWeight.Bold, Color(0xFF34D399))
                            val remaining = (15 - goatsPlaced).coerceAtLeast(0)
                            RText("$remaining টি", 15, FontWeight.ExtraBold, Color.White)
                        }
                        Box(Modifier.width(1.r).height(24.r).background(Color.White.copy(alpha = 0.2f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            RText("বাঘের শিকার", 11, FontWeight.Bold, Color(0xFFF87171))
                            RText("$goatsCaptured / 5", 15, FontWeight.ExtraBold, Color.White)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(42.r)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable {
                                renderer.resetBoard()
                                turn = BaghRole.Goat
                                goatsPlaced = 0
                                goatsCaptured = 0
                                winner = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        RText("🔄", 16, FontWeight.Bold, Color.White)
                    }
                }

                Spacer(Modifier.height(8.r))
                val turnMsg = when {
                    winner != null -> "🎉 বিজয়ী: $winner"
                    isThinking -> "🐅 বাঘ শিকারের জন্য লক্ষ্য নির্ধারণ করছে..."
                    turn == BaghRole.Goat && goatsPlaced < 15 -> "🐐 ছাগল বসান (${15 - goatsPlaced}টি বাকি)"
                    turn == BaghRole.Goat -> "🐐 ছাগল সরান (বাঘকে বন্দি করার চেষ্টা করুন)"
                    else -> "🐅 বাঘের চাল"
                }
                val turnCol = if (turn == BaghRole.Goat) Color(0xFF34D399) else Color(0xFFF87171)
                RText(turnMsg, 13, FontWeight.Bold, turnCol, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }

            // Winner Modal
            if (winner != null) {
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
                        RText(winner!!, 15, FontWeight.Bold, Color(0xFFF59E0B), align = TextAlign.Center)
                        Spacer(Modifier.height(20.r))
                        Button(
                            onClick = {
                                renderer.resetBoard()
                                turn = BaghRole.Goat
                                goatsPlaced = 0
                                goatsCaptured = 0
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

private fun BaghBandi3DRenderer.boardStateAction(from: Int, to: Int, captured: Int?) {
    nodeStates[to] = BaghNodeState.Tiger
    nodeStates[from] = BaghNodeState.Empty
    captured?.let { nodeStates[it] = BaghNodeState.Empty }
}
