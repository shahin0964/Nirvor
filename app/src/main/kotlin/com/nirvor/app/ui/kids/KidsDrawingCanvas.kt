package com.nirvor.app.ui.kids

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nirvor.app.ui.r

data class DrawPath(
    val path: Path,
    val color: Color,
    val strokeWidth: Float,
)

@Composable
fun KidsDrawingCanvas(modifier: Modifier = Modifier) {
    val paths = remember { mutableStateListOf<DrawPath>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var currentColor by remember { mutableStateOf(Color(0xFFFF3366)) }
    var currentStroke by remember { mutableFloatStateOf(14f) }
    var isEraser by remember { mutableStateOf(false) }
    var isChalkboard by remember { mutableStateOf(true) }

    val canvasBg = if (isChalkboard) Color(0xFF1E293B) else Color.White
    val colors = listOf(
        Color(0xFFFF3366), // Pink
        Color(0xFFFF9900), // Orange
        Color(0xFFFFCC00), // Yellow
        Color(0xFF33CC66), // Green
        Color(0xFF00CCFF), // Sky
        Color(0xFF6666FF), // Indigo
        Color(0xFFCC33FF), // Purple
        if (isChalkboard) Color.White else Color(0xFF1E293B), // Chalk / Ink
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Colors
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                colors.forEach { c ->
                    val isSelected = !isEraser && currentColor == c
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 32.dp else 26.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1),
                                shape = CircleShape,
                            )
                            .clickable {
                                currentColor = c
                                isEraser = false
                            },
                    )
                }
            }

            // Eraser & Tools
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Eraser
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isEraser) Color(0xFFFFE4E6) else Color(0xFFF1F5F9))
                        .border(
                            width = 1.dp,
                            color = if (isEraser) Color(0xFFF43F5E) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { isEraser = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text("🧹 রবার", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isEraser) Color(0xFFE11D48) else Color(0xFF475569))
                }

                // Undo
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFF1F5F9))
                        .clickable { if (paths.isNotEmpty()) paths.removeAt(paths.size - 1) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text("↩️ রিভার্স", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF475569))
                }

                // Clear
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEE2E2))
                        .clickable { paths.clear() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text("🗑️ পরিষ্কার", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Drawing Area (Slate)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(canvasBg)
                .border(3.dp, Color(0xFFF59E0B), RoundedCornerShape(22.dp)),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val p = Path().apply { moveTo(offset.x, offset.y) }
                                currentPath = p
                                val color = if (isEraser) canvasBg else currentColor
                                val width = if (isEraser) currentStroke * 2.5f else currentStroke
                                paths.add(DrawPath(p, color, width))
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPath?.lineTo(change.position.x, change.position.y)
                            },
                            onDragEnd = {
                                currentPath = null
                            },
                        )
                    },
            ) {
                paths.forEach { dp ->
                    drawPath(
                        path = dp.path,
                        color = dp.color,
                        style = Stroke(
                            width = dp.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }

            // Quick Background Switcher (Chalkboard / Paper)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { isChalkboard = !isChalkboard }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (isChalkboard) "📝 সাদা খাতা" else "🖤 ব্ল্যাকবোর্ড",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
