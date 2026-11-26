package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DriveCanvas(
    carState: CarState,
    carPaintColor: Color,
    skids: List<SkidMark>,
    particles: List<DriveParticle>,
    driveMode: DriveMode,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Dynamic camera zoom: zooms out slightly at high speed for wider horizon visibility
        val speedZoom = 1f - (carState.speedKmh / 340f).coerceIn(0f, 0.30f)
        val scale = (minOf(w, h) / 680f).coerceIn(0.65f, 1.45f) * speedZoom

        // Camera look-ahead along velocity vector
        val camX = carState.x + carState.vx * 0.22f
        val camY = carState.y + carState.vy * 0.22f

        // Sky-warmed canyon desert base
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF3E2922), Color(0xFF281A1A), Color(0xFF1A1219)),
                center = Offset(w * 0.5f, h * 0.5f),
                radius = maxOf(w, h) * 0.85f
            )
        )

        translate(left = w * 0.5f, top = h * 0.5f) {
            drawContext.transform.scale(scale, scale, Offset.Zero)
            translate(left = -camX, top = -camY) {
                drawDesertContours(camX, camY, w / scale, h / scale)
                drawFestivalPlaza()
                drawRoadNetwork()
                drawEventZones(carState, driveMode)
                drawBonusBoards(carState)
                drawSkidMarks(skids)
                drawObstacles()
                drawParticles(particles)
                drawPlayerCar(carState, carPaintColor)
            }
        }

        // Top-left Mini-Map Radar
        drawMiniMap(carState, driveMode)
    }
}

private fun DrawScope.drawDesertContours(camX: Float, camY: Float, viewW: Float, viewH: Float) {
    val gridStep = 180f
    val startX = ((camX - viewW * 0.6f) / gridStep).toInt() * gridStep
    val endX = camX + viewW * 0.6f
    val startY = ((camY - viewH * 0.6f) / gridStep).toInt() * gridStep
    val endY = camY + viewH * 0.6f

    val lineColor = Color(0x18FFB703)
    var gx = startX
    while (gx <= endX) {
        drawLine(lineColor, Offset(gx, startY), Offset(gx, endY), strokeWidth = 2f)
        gx += gridStep
    }
    var gy = startY
    while (gy <= endY) {
        drawLine(lineColor, Offset(startX, gy), Offset(endX, gy), strokeWidth = 2f)
        gy += gridStep
    }

    // World boundary fence
    drawRect(
        color = Color(0xFFFF4D6D),
        topLeft = Offset(40f, 40f),
        size = Size(HorizonWorldMap.WORLD_SIZE - 80f, HorizonWorldMap.WORLD_SIZE - 80f),
        style = Stroke(width = 10f)
    )
}

private fun DrawScope.drawFestivalPlaza() {
    // South Horizon Main Stage Plaza
    drawRoundRect(
        color = Color(0xFF2B2240),
        topLeft = Offset(1580f, 2580f),
        size = Size(440f, 180f),
        cornerRadius = CornerRadius(24f, 24f)
    )
    drawCircle(
        color = Color(0x33FF4D6D),
        radius = 120f,
        center = Offset(1800f, 2650f)
    )
    drawCircle(
        color = Color(0x3300E5FF),
        radius = 80f,
        center = Offset(1800f, 2650f)
    )
}

private fun DrawScope.drawRoadNetwork() {
    // 1. Dirt Trail
    val dirtPath = Path().apply {
        val pts = HorizonWorldMap.dirtTrail
        if (pts.isNotEmpty()) {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        }
    }
    drawPath(
        path = dirtPath,
        color = Color(0xFF6B4E37),
        style = Stroke(width = HorizonWorldMap.DIRT_HALF_WIDTH * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = dirtPath,
        color = Color(0xFF836145),
        style = Stroke(width = HorizonWorldMap.DIRT_HALF_WIDTH * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // 2. Main Asphalt Circuit Loop
    val asphaltPath = Path().apply {
        val pts = HorizonWorldMap.asphaltLoop
        if (pts.isNotEmpty()) {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
    }
    // Outer red-white festival kerb
    drawPath(
        path = asphaltPath,
        color = Color(0xFFFF4D6D),
        style = Stroke(
            width = HorizonWorldMap.ASPHALT_HALF_WIDTH * 2f + 16f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    // Asphalt body
    drawPath(
        path = asphaltPath,
        color = Color(0xFF282B36),
        style = Stroke(
            width = HorizonWorldMap.ASPHALT_HALF_WIDTH * 2f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
    // Dashed centerline
    drawPath(
        path = asphaltPath,
        color = Color(0xAAFFB703),
        style = Stroke(
            width = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 24f), 0f)
        )
    )

    // Start / Finish Gantry at Festival Straight
    drawLine(
        color = Color(0xFF00E5FF),
        start = Offset(HorizonWorldMap.START_X, HorizonWorldMap.START_Y - HorizonWorldMap.ASPHALT_HALF_WIDTH),
        end = Offset(HorizonWorldMap.START_X, HorizonWorldMap.START_Y + HorizonWorldMap.ASPHALT_HALF_WIDTH),
        strokeWidth = 10f
    )
}

private fun DrawScope.drawEventZones(carState: CarState, driveMode: DriveMode) {
    // Drift Zones
    for (zone in HorizonWorldMap.driftZones) {
        val active = carState.activeDriftZoneId == zone.zoneId
        drawCircle(
            color = if (active) Color(0x33FF4D6D) else Color(0x18FF4D6D),
            radius = zone.radius,
            center = Offset(zone.centerX, zone.centerY)
        )
        drawCircle(
            color = if (active) Color(0xFFFF4D6D) else Color(0x88FF4D6D),
            radius = zone.radius,
            center = Offset(zone.centerX, zone.centerY),
            style = Stroke(width = 5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 16f)))
        )
    }

    // Speed Traps
    for (trap in HorizonWorldMap.speedTraps) {
        drawCircle(
            color = Color(0x2800E5FF),
            radius = trap.gateRadius,
            center = Offset(trap.x, trap.y)
        )
        drawCircle(
            color = Color(0xFF00E5FF),
            radius = trap.gateRadius,
            center = Offset(trap.x, trap.y),
            style = Stroke(width = 5f)
        )
    }

    // Active Circuit Checkpoint Gate
    if (driveMode == DriveMode.CIRCUIT_TRIAL) {
        val cp = HorizonWorldMap.circuitCheckpoints[carState.checkpointIndex]
        drawCircle(
            color = Color(0x3800E676),
            radius = cp.radius,
            center = Offset(cp.x, cp.y)
        )
        drawCircle(
            color = Color(0xFF00E676),
            radius = cp.radius,
            center = Offset(cp.x, cp.y),
            style = Stroke(width = 8f)
        )
    }
}

private fun DrawScope.drawBonusBoards(carState: CarState) {
    for (board in HorizonWorldMap.bonusBoards) {
        if (carState.smashedBoardIds.contains(board.boardId)) continue
        drawCircle(
            color = Color(0x44FFB703),
            radius = 28f,
            center = Offset(board.x, board.y)
        )
        drawRoundRect(
            color = Color(0xFFFFB703),
            topLeft = Offset(board.x - 16f, board.y - 11f),
            size = Size(32f, 22f),
            cornerRadius = CornerRadius(5f, 5f)
        )
        drawRoundRect(
            color = Color(0xFF161224),
            topLeft = Offset(board.x - 11f, board.y - 6f),
            size = Size(22f, 12f),
            cornerRadius = CornerRadius(3f, 3f)
        )
    }
}

private fun DrawScope.drawSkidMarks(skids: List<SkidMark>) {
    for (skid in skids) {
        drawLine(
            color = Color(0xFF0B0910).copy(alpha = skid.alpha),
            start = Offset(skid.x1, skid.y1),
            end = Offset(skid.x2, skid.y2),
            strokeWidth = 5f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawObstacles() {
    for (obs in HorizonWorldMap.obstacles) {
        when (obs.kind) {
            ObstacleKind.MESA_ROCK -> {
                drawCircle(
                    color = Color(0x44000000),
                    radius = obs.radius + 5f,
                    center = Offset(obs.x + 6f, obs.y + 8f)
                )
                drawCircle(
                    color = Color(0xFF8C4630),
                    radius = obs.radius,
                    center = Offset(obs.x, obs.y)
                )
                drawCircle(
                    color = Color(0xFFB55D3D),
                    radius = obs.radius * 0.65f,
                    center = Offset(obs.x - obs.radius * 0.14f, obs.y - obs.radius * 0.14f)
                )
            }
            ObstacleKind.PINE_TREE -> {
                drawCircle(
                    color = Color(0x44000000),
                    radius = obs.radius + 4f,
                    center = Offset(obs.x + 5f, obs.y + 6f)
                )
                drawCircle(
                    color = Color(0xFF1B4D3E),
                    radius = obs.radius,
                    center = Offset(obs.x, obs.y)
                )
                drawCircle(
                    color = Color(0xFF2D7A5E),
                    radius = obs.radius * 0.62f,
                    center = Offset(obs.x - 3f, obs.y - 3f)
                )
            }
            ObstacleKind.FESTIVAL_TOWER -> {
                drawCircle(
                    color = Color(0xFFFF4D6D),
                    radius = obs.radius,
                    center = Offset(obs.x, obs.y)
                )
                drawCircle(
                    color = Color(0xFF00E5FF),
                    radius = obs.radius * 0.55f,
                    center = Offset(obs.x, obs.y)
                )
            }
        }
    }
}

private fun DrawScope.drawParticles(particles: List<DriveParticle>) {
    for (p in particles) {
        val t = (p.life / p.maxLife).coerceIn(0f, 1f)
        val color = when {
            p.isGoldBurst -> Color(0xFFFFB703).copy(alpha = t)
            p.isFlame -> Color(0xFF00E5FF).copy(alpha = t * 0.9f)
            else -> Color(0xFFE0DDE8).copy(alpha = t * 0.45f)
        }
        drawCircle(
            color = color,
            radius = p.radius * (1.2f - t * 0.3f),
            center = Offset(p.x, p.y)
        )
    }
}

private fun DrawScope.drawPlayerCar(carState: CarState, paintColor: Color) {
    val deg = (carState.angleRad * 180f / PI.toFloat())
    translate(left = carState.x, top = carState.y) {
        rotate(degrees = deg, pivot = Offset.Zero) {
            // Soft shadow under chassis
            drawRoundRect(
                color = Color(0x66000000),
                topLeft = Offset(-24f, -13f),
                size = Size(50f, 28f),
                cornerRadius = CornerRadius(9f, 9f)
            )

            // Four tires (front tires rotate visibly with steering angle)
            val steerDeg = carState.steerVisualRad * 180f / PI.toFloat()
            val wheelPositions = listOf(
                Triple(14f, -14f, steerDeg),
                Triple(14f, 14f, steerDeg),
                Triple(-15f, -14f, 0f),
                Triple(-15f, 14f, 0f)
            )
            for ((wx, wy, wAngle) in wheelPositions) {
                translate(left = wx, top = wy) {
                    rotate(degrees = wAngle, pivot = Offset.Zero) {
                        drawRoundRect(
                            color = Color(0xFF101015),
                            topLeft = Offset(-6f, -3f),
                            size = Size(12f, 6f),
                            cornerRadius = CornerRadius(2f, 2f)
                        )
                    }
                }
            }

            // Headlight beams
            drawOval(
                color = Color(0x28FFF8DC),
                topLeft = Offset(22f, -20f),
                size = Size(90f, 40f)
            )

            // Main painted car body
            drawRoundRect(
                color = paintColor,
                topLeft = Offset(-24f, -12f),
                size = Size(48f, 24f),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // Racing stripe
            drawRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(-22f, -2.5f),
                size = Size(44f, 5f)
            )

            // Cabin / tinted windshield
            drawRoundRect(
                color = Color(0xFF131522),
                topLeft = Offset(-10f, -9f),
                size = Size(22f, 18f),
                cornerRadius = CornerRadius(5f, 5f)
            )

            // Brake / tail lights
            val tailColor = if (carState.isBraking) Color(0xFFFF2442) else Color(0xAA991122)
            drawRect(tailColor, topLeft = Offset(-24.5f, -10f), size = Size(3f, 6f))
            drawRect(tailColor, topLeft = Offset(-24.5f, 4f), size = Size(3f, 6f))

            // Headlight LEDs
            drawRect(Color(0xFFCCF8FF), topLeft = Offset(21.5f, -10f), size = Size(3f, 5f))
            drawRect(Color(0xFFCCF8FF), topLeft = Offset(21.5f, 5f), size = Size(3f, 5f))
        }
    }
}

private fun DrawScope.drawMiniMap(carState: CarState, driveMode: DriveMode) {
    val mapSize = 112f
    val padX = 18f
    val padY = 86f
    val scale = mapSize / HorizonWorldMap.WORLD_SIZE

    translate(left = padX, top = padY) {
        drawRoundRect(
            color = Color(0xBB0D0A14),
            topLeft = Offset.Zero,
            size = Size(mapSize, mapSize),
            cornerRadius = CornerRadius(14f, 14f)
        )
        drawRoundRect(
            color = Color(0x55393056),
            topLeft = Offset.Zero,
            size = Size(mapSize, mapSize),
            cornerRadius = CornerRadius(14f, 14f),
            style = Stroke(width = 2f)
        )

        val roadPath = Path().apply {
            val pts = HorizonWorldMap.asphaltLoop
            if (pts.isNotEmpty()) {
                moveTo(pts.first().x * scale, pts.first().y * scale)
                for (i in 1 until pts.size) lineTo(pts[i].x * scale, pts[i].y * scale)
                close()
            }
        }
        drawPath(roadPath, color = Color(0xAA7D7696), style = Stroke(width = 3.5f))

        // Speed Traps (Cyan) & Drift Zones (Coral)
        for (trap in HorizonWorldMap.speedTraps) {
            drawCircle(Color(0xFF00E5FF), radius = 3.2f, center = Offset(trap.x * scale, trap.y * scale))
        }
        for (dz in HorizonWorldMap.driftZones) {
            drawCircle(Color(0xFFFF4D6D), radius = 3.5f, center = Offset(dz.centerX * scale, dz.centerY * scale))
        }

        if (driveMode == DriveMode.CIRCUIT_TRIAL) {
            val cp = HorizonWorldMap.circuitCheckpoints[carState.checkpointIndex]
            drawCircle(Color(0xFF00E676), radius = 4.5f, center = Offset(cp.x * scale, cp.y * scale))
        }

        // Player arrow
        val px = carState.x * scale
        val py = carState.y * scale
        drawCircle(Color(0xFFFFB703), radius = 5f, center = Offset(px, py))
        drawLine(
            color = Color.White,
            start = Offset(px, py),
            end = Offset(px + cos(carState.angleRad) * 9f, py + sin(carState.angleRad) * 9f),
            strokeWidth = 2.5f
        )
    }
}
