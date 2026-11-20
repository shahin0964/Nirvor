package com.nirvor.app.game.carrom

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

data class TrajectoryPrediction(
    val start: Offset,
    val hitPoint: Offset,
    val targetDiscId: Int? = null,
    val targetDeflection: Offset? = null,
    val strikerDeflection: Offset? = null,
    val cushionBounce: Offset? = null,
)

object CarromPhysics {

    const val BOARD_SIZE = 800f
    const val BORDER_WIDTH = 58f

    const val PLAY_MIN_X = BORDER_WIDTH
    const val PLAY_MAX_X = BOARD_SIZE - BORDER_WIDTH
    const val PLAY_MIN_Y = BORDER_WIDTH
    const val PLAY_MAX_Y = BOARD_SIZE - BORDER_WIDTH

    const val POCKET_RADIUS = 34f
    const val POCKET_SINK_DIST = 32f

    const val COIN_RADIUS = 18f
    const val STRIKER_RADIUS = 25f

    const val BASELINE_Y_BOTTOM = 650f
    const val BASELINE_Y_TOP = 150f
    const val BASELINE_MIN_X = 180f
    const val BASELINE_MAX_X = 620f

    val POCKETS = listOf(
        Offset(BORDER_WIDTH + 24f, BORDER_WIDTH + 24f),
        Offset(BOARD_SIZE - BORDER_WIDTH - 24f, BORDER_WIDTH + 24f),
        Offset(BORDER_WIDTH + 24f, BOARD_SIZE - BORDER_WIDTH - 24f),
        Offset(BOARD_SIZE - BORDER_WIDTH - 24f, BOARD_SIZE - BORDER_WIDTH - 24f),
    )

    /** Sets up initial classic Carrom layout with center Queen, 9 White and 9 Black coins. */
    fun initialDiscs(strikerX: Float = BOARD_SIZE / 2, isPlayer1: Boolean = true): List<CarromDisc> {
        val list = mutableListOf<CarromDisc>()
        val cx = BOARD_SIZE / 2
        val cy = BOARD_SIZE / 2

        // Center Queen (Red)
        list.add(CarromDisc(0, CarromDiscType.Queen, cx, cy, radius = COIN_RADIUS))

        // Inner ring: 6 coins around Queen (alternating White & Black)
        val r1 = COIN_RADIUS * 2.05f
        for (i in 0 until 6) {
            val angle = i * (Math.PI * 2 / 6)
            val type = if (i % 2 == 0) CarromDiscType.White else CarromDiscType.Black
            val x = (cx + r1 * cos(angle)).toFloat()
            val y = (cy + r1 * sin(angle)).toFloat()
            list.add(CarromDisc(list.size, type, x, y, radius = COIN_RADIUS))
        }

        // Outer ring: 12 coins around inner ring (alternating White & Black)
        val r2 = COIN_RADIUS * 4.08f
        for (i in 0 until 12) {
            val angle = (i + 0.5) * (Math.PI * 2 / 12)
            val type = if (i % 2 == 0) CarromDiscType.Black else CarromDiscType.White
            val x = (cx + r2 * cos(angle)).toFloat()
            val y = (cy + r2 * sin(angle)).toFloat()
            list.add(CarromDisc(list.size, type, x, y, radius = COIN_RADIUS))
        }

        // Striker
        val strikerY = if (isPlayer1) BASELINE_Y_BOTTOM else BASELINE_Y_TOP
        val striker = CarromDisc(99, CarromDiscType.Striker, strikerX, strikerY, radius = STRIKER_RADIUS)
        list.add(striker)

        return list
    }

    /** Simulates 1 physics step: moves discs, handles collisions, friction, and pocket sinks. */
    fun updatePhysics(
        discs: List<CarromDisc>,
        frictionFactor: Float = 0.982f,
        cushionRestitution: Float = 0.76f,
        coinRestitution: Float = 0.88f,
        onPocket: (CarromDisc) -> Unit,
        onCollide: (Float) -> Unit,
    ): Boolean {
        var anyMoving = false

        // 1. Move discs and check bounds / cushions
        for (d in discs) {
            if (d.isPocketed) continue

            // Sink animation if already dropping
            if (d.sinkScale < 1f) {
                d.sinkScale -= 0.12f
                d.sinkAlpha -= 0.15f
                if (d.sinkScale <= 0.1f) {
                    d.isPocketed = true
                    d.vx = 0f
                    d.vy = 0f
                    onPocket(d)
                }
                anyMoving = true
                continue
            }

            d.x += d.vx
            d.y += d.vy

            // Friction deceleration
            d.vx *= frictionFactor
            d.vy *= frictionFactor
            if (abs(d.vx) < 0.05f) d.vx = 0f
            if (abs(d.vy) < 0.05f) d.vy = 0f

            val speedSq = d.vx * d.vx + d.vy * d.vy
            if (speedSq > 0.002f) anyMoving = true

            // Pocket sink detection
            for (p in POCKETS) {
                val dx = d.x - p.x
                val dy = d.y - p.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < POCKET_SINK_DIST) {
                    d.sinkScale = 0.95f
                    d.vx *= 0.3f
                    d.vy *= 0.3f
                    anyMoving = true
                    break
                }
            }

            // Cushion rebounds
            if (d.x - d.radius < PLAY_MIN_X) {
                d.x = PLAY_MIN_X + d.radius
                d.vx = -d.vx * cushionRestitution
                onCollide(abs(d.vx))
            } else if (d.x + d.radius > PLAY_MAX_X) {
                d.x = PLAY_MAX_X - d.radius
                d.vx = -d.vx * cushionRestitution
                onCollide(abs(d.vx))
            }

            if (d.y - d.radius < PLAY_MIN_Y) {
                d.y = PLAY_MIN_Y + d.radius
                d.vy = -d.vy * cushionRestitution
                onCollide(abs(d.vy))
            } else if (d.y + d.radius > PLAY_MAX_Y) {
                d.y = PLAY_MAX_Y - d.radius
                d.vy = -d.vy * cushionRestitution
                onCollide(abs(d.vy))
            }
        }

        // 2. Circle-to-Circle Elastic Collisions
        val active = discs.filter { !it.isPocketed && it.sinkScale >= 1f }
        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val a = active[i]
                val b = active[j]

                val dx = b.x - a.x
                val dy = b.y - a.y
                val dist = sqrt(dx * dx + dy * dy)
                val minDist = a.radius + b.radius

                if (dist < minDist && dist > 0.001f) {
                    // Normal vector
                    val nx = dx / dist
                    val ny = dy / dist

                    // Separate overlap
                    val overlap = 0.5f * (minDist - dist)
                    a.x -= nx * overlap
                    a.y -= ny * overlap
                    b.x += nx * overlap
                    b.y += ny * overlap

                    // Relative velocity along normal
                    val kx = a.vx - b.vx
                    val ky = a.vy - b.vy
                    val p = 2f * (nx * kx + ny * ky) / (1f + 1f) // assume equal effective mass factor

                    if (p > 0) {
                        val impulse = p * coinRestitution
                        a.vx -= impulse * nx
                        a.vy -= impulse * ny
                        b.vx += impulse * nx
                        b.vy += impulse * ny
                        onCollide(abs(impulse))
                    }
                }
            }
        }

        return anyMoving
    }

    /**
     * Raycasts along shot direction to calculate aiming trajectory, first disc collision,
     * target disc deflection, and striker bounce (identical to Carrom Pool).
     */
    fun predictTrajectory(
        striker: CarromDisc,
        aimDx: Float,
        aimDy: Float,
        maxGuideDist: Float,
        discs: List<CarromDisc>,
    ): TrajectoryPrediction {
        val len = sqrt(aimDx * aimDx + aimDy * aimDy)
        if (len < 0.001f) return TrajectoryPrediction(Offset(striker.x, striker.y), Offset(striker.x, striker.y))

        val dirX = aimDx / len
        val dirY = aimDy / len
        val start = Offset(striker.x, striker.y)

        var closestDist = maxGuideDist
        var targetDisc: CarromDisc? = null

        val activeCoins = discs.filter { it.id != striker.id && !it.isPocketed && it.sinkScale >= 1f }

        // Find nearest circle intersection along ray
        for (coin in activeCoins) {
            val ocX = coin.x - striker.x
            val ocY = coin.y - striker.y
            val tProj = ocX * dirX + ocY * dirY

            if (tProj > 0) {
                val perpDistSq = (ocX * ocX + ocY * ocY) - (tProj * tProj)
                val hitDist = striker.radius + coin.radius
                if (perpDistSq < hitDist * hitDist) {
                    val dt = sqrt(hitDist * hitDist - perpDistSq)
                    val intersectDist = tProj - dt
                    if (intersectDist in 0.0f..closestDist) {
                        closestDist = intersectDist
                        targetDisc = coin
                    }
                }
            }
        }

        val hitX = striker.x + dirX * closestDist
        val hitY = striker.y + dirY * closestDist
        val hitPoint = Offset(hitX, hitY)

        if (targetDisc != null) {
            // Calculate normal from hit point to target center
            val normX = targetDisc.x - hitX
            val normY = targetDisc.y - hitY
            val normLen = sqrt(normX * normX + normY * normY)

            if (normLen > 0.001f) {
                val nx = normX / normLen
                val ny = normY / normLen

                // Target deflection line towards pocket
                val defLen = 140f
                val targetDeflection = Offset(targetDisc.x + nx * defLen, targetDisc.y + ny * defLen)

                // Striker deflection vector (tangent)
                val dot = dirX * nx + dirY * ny
                val sDx = dirX - dot * nx
                val sDy = dirY - dot * ny
                val sLen = sqrt(sDx * sDx + sDy * sDy)
                val strikerDeflection = if (sLen > 0.001f) {
                    Offset(hitX + (sDx / sLen) * 90f, hitY + (sDy / sLen) * 90f)
                } else null

                return TrajectoryPrediction(
                    start = start,
                    hitPoint = hitPoint,
                    targetDiscId = targetDisc.id,
                    targetDeflection = targetDeflection,
                    strikerDeflection = strikerDeflection,
                )
            }
        }

        // No disc hit: check cushion bounce
        var cushionHit = hitPoint
        var cushionBounce: Offset? = null

        val rayLen = min(maxGuideDist, 450f)
        val endX = striker.x + dirX * rayLen
        val endY = striker.y + dirY * rayLen

        var boundHit = false
        var bounceX = 0f
        var bounceY = 0f

        if (endX < PLAY_MIN_X + striker.radius) {
            val t = (PLAY_MIN_X + striker.radius - striker.x) / dirX
            val cy = striker.y + dirY * t
            cushionHit = Offset(PLAY_MIN_X + striker.radius, cy)
            bounceX = -(dirX) * 100f
            bounceY = dirY * 100f
            boundHit = true
        } else if (endX > PLAY_MAX_X - striker.radius) {
            val t = (PLAY_MAX_X - striker.radius - striker.x) / dirX
            val cy = striker.y + dirY * t
            cushionHit = Offset(PLAY_MAX_X - striker.radius, cy)
            bounceX = -(dirX) * 100f
            bounceY = dirY * 100f
            boundHit = true
        }

        if (endY < PLAY_MIN_Y + striker.radius) {
            val t = (PLAY_MIN_Y + striker.radius - striker.y) / dirY
            val cx = striker.x + dirX * t
            cushionHit = Offset(cx, PLAY_MIN_Y + striker.radius)
            bounceX = dirX * 100f
            bounceY = -(dirY) * 100f
            boundHit = true
        } else if (endY > PLAY_MAX_Y - striker.radius) {
            val t = (PLAY_MAX_Y - striker.radius - striker.y) / dirY
            val cx = striker.x + dirX * t
            cushionHit = Offset(cx, PLAY_MAX_Y - striker.radius)
            bounceX = dirX * 100f
            bounceY = -(dirY) * 100f
            boundHit = true
        }

        if (boundHit) {
            cushionBounce = Offset(cushionHit.x + bounceX, cushionHit.y + bounceY)
        }

        return TrajectoryPrediction(
            start = start,
            hitPoint = cushionHit,
            cushionBounce = cushionBounce,
        )
    }

    /** AI Bot shot computation for Solo / Vs Bot mode. Evaluates best coin-to-pocket angle. */
    fun calculateBotShot(
        discs: List<CarromDisc>,
        targetType: CarromDiscType,
        accuracy: Float = 0.85f,
    ): Pair<Float, Offset> {
        val candidates = discs.filter { !it.isPocketed && (it.type == targetType || it.type == CarromDiscType.Queen) }
        val targets = if (candidates.isNotEmpty()) candidates else discs.filter { !it.isPocketed && it.type != CarromDiscType.Striker }

        var bestTarget: CarromDisc? = null
        var bestPocket: Offset? = null
        var bestDistance = Float.MAX_VALUE

        for (coin in targets) {
            for (p in POCKETS) {
                val dx = p.x - coin.x
                val dy = p.y - coin.y
                val dist = sqrt(dx * dx + dy * dy)
                if (dist < bestDistance) {
                    bestDistance = dist
                    bestTarget = coin
                    bestPocket = p
                }
            }
        }

        val target = bestTarget ?: discs.firstOrNull { it.type != CarromDiscType.Striker && !it.isPocketed }
        val pocket = bestPocket ?: POCKETS[0]

        if (target != null) {
            // Direction from coin to pocket
            val toPockX = pocket.x - target.x
            val toPockY = pocket.y - target.y
            val toPockLen = sqrt(toPockX * toPockX + toPockY * toPockY).coerceAtLeast(1f)
            val pockDirX = toPockX / toPockLen
            val pockDirY = toPockY / toPockLen

            // Ghost striker position directly behind target coin
            val ghostX = target.x - pockDirX * (COIN_RADIUS + STRIKER_RADIUS)
            val botBaselineX = ghostX.coerceIn(BASELINE_MIN_X, BASELINE_MAX_X)

            // Shot vector from striker baseline to target
            val shotDx = (target.x - botBaselineX) + (1f - accuracy) * (Random.nextFloat() * 18f - 9f)
            val shotDy = (target.y - BASELINE_Y_TOP) + (1f - accuracy) * (Random.nextFloat() * 18f - 9f)

            return botBaselineX to Offset(shotDx, shotDy)
        }

        return (BOARD_SIZE / 2) to Offset(0f, 150f)
    }
}
