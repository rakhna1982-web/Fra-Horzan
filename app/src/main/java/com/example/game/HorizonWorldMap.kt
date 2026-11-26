package com.example.game

import com.example.data.ZoneType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float) = Vec2(x * scalar, y * scalar)
    fun length(): Float = hypot(x, y)
    fun normalized(): Vec2 {
        val len = length()
        return if (len > 1e-4f) Vec2(x / len, y / len) else Vec2(1f, 0f)
    }
    fun dot(other: Vec2): Float = x * other.x + y * other.y
}

enum class SurfaceType(val label: String) {
    ASPHALT("ASPHALT"),
    DIRT("CANYON DIRT"),
    OFFROAD("DESERT OFFROAD")
}

enum class ObstacleKind {
    MESA_ROCK,
    PINE_TREE,
    FESTIVAL_TOWER
}

data class WorldObstacle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val kind: ObstacleKind
)

data class SpeedTrapSpec(
    val zoneId: String,
    val name: String,
    val x: Float,
    val y: Float,
    val angleRad: Float,
    val gateRadius: Float,
    val oneStarKmh: Long,
    val twoStarKmh: Long,
    val threeStarKmh: Long
)

data class DriftZoneSpec(
    val zoneId: String,
    val name: String,
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val oneStarPts: Long,
    val twoStarPts: Long,
    val threeStarPts: Long
)

data class CircuitCheckpoint(
    val index: Int,
    val x: Float,
    val y: Float,
    val radius: Float = 115f
)

data class BonusBoardSpec(
    val boardId: String,
    val x: Float,
    val y: Float,
    val rewardCredits: Long = 500L,
    val rewardXp: Long = 250L
)

object HorizonWorldMap {
    const val WORLD_SIZE = 3600f
    const val ASPHALT_HALF_WIDTH = 62f
    const val DIRT_HALF_WIDTH = 44f
    const val START_X = 1800f
    const val START_Y = 2780f
    const val START_ANGLE_RAD = 0f

    // Main closed-loop Horizon Festival highway (smoothly sampled via Catmull-Rom)
    private val mainControlPoints = listOf(
        Vec2(1800f, 2780f), // Festival Main Stage straight (South)
        Vec2(2580f, 2760f), // East Horizon straight
        Vec2(3040f, 2380f), // Coastal sweeping turn
        Vec2(3080f, 1680f), // Coastal speedway
        Vec2(2820f, 1020f), // North-East ridge
        Vec2(2240f, 680f),  // Red-Rock Arch climb
        Vec2(1580f, 760f),  // Canyon switchback entry
        Vec2(1140f, 1180f), // Canyon hairpin
        Vec2(720f, 940f),   // West mesa loop
        Vec2(540f, 1480f),  // Desert valley sweep
        Vec2(760f, 2120f),  // Pine ridge curve
        Vec2(1180f, 2660f)  // Festival return chicane
    )

    // Cross-canyon desert dirt trail cutting across the valley
    private val dirtControlPoints = listOf(
        Vec2(760f, 2120f),
        Vec2(1280f, 1920f),
        Vec2(1820f, 1780f),
        Vec2(2400f, 1620f),
        Vec2(3080f, 1680f)
    )

    val asphaltLoop: List<Vec2> = sampleClosedCatmullRom(mainControlPoints, subdivisions = 14)
    val dirtTrail: List<Vec2> = sampleOpenCatmullRom(dirtControlPoints, subdivisions = 14)

    val speedTraps = listOf(
        SpeedTrapSpec(
            zoneId = "canyon_speed_trap",
            name = "Festival Straight Trap",
            x = 2220f,
            y = 2770f,
            angleRad = 0f,
            gateRadius = 90f,
            oneStarKmh = 165L,
            twoStarKmh = 215L,
            threeStarKmh = 265L
        ),
        SpeedTrapSpec(
            zoneId = "coastal_radar",
            name = "Coastal Blitz Radar",
            x = 3070f,
            y = 1850f,
            angleRad = (-PI / 2).toFloat(),
            gateRadius = 90f,
            oneStarKmh = 175L,
            twoStarKmh = 230L,
            threeStarKmh = 285L
        ),
        SpeedTrapSpec(
            zoneId = "ridge_blitz",
            name = "Red Rock Ridge Trap",
            x = 1880f,
            y = 700f,
            angleRad = PI.toFloat(),
            gateRadius = 90f,
            oneStarKmh = 155L,
            twoStarKmh = 205L,
            threeStarKmh = 250L
        )
    )

    val driftZones = listOf(
        DriftZoneSpec(
            zoneId = "hairpin_drift",
            name = "Canyon Hairpin Drift",
            centerX = 1140f,
            centerY = 1180f,
            radius = 270f,
            oneStarPts = 2500L,
            twoStarPts = 6500L,
            threeStarPts = 12000L
        ),
        DriftZoneSpec(
            zoneId = "switchback_slide",
            name = "Pine Valley S-Slide",
            centerX = 760f,
            centerY = 2120f,
            radius = 280f,
            oneStarPts = 3000L,
            twoStarPts = 7500L,
            threeStarPts = 14000L
        ),
        DriftZoneSpec(
            zoneId = "plaza_gymkhana",
            name = "Horizon Plaza Drift",
            centerX = 1820f,
            centerY = 1780f,
            radius = 290f,
            oneStarPts = 2000L,
            twoStarPts = 5500L,
            threeStarPts = 10500L
        )
    )

    const val CIRCUIT_ZONE_ID = "horizon_circuit"
    const val CIRCUIT_NAME = "Horizon Canyon Circuit"
    const val CIRCUIT_THREE_STAR_MS = 38_000L
    const val CIRCUIT_TWO_STAR_MS = 48_000L
    const val CIRCUIT_ONE_STAR_MS = 62_000L

    val circuitCheckpoints: List<CircuitCheckpoint> = listOf(
        CircuitCheckpoint(0, 2580f, 2760f),
        CircuitCheckpoint(1, 3080f, 1680f),
        CircuitCheckpoint(2, 2240f, 680f),
        CircuitCheckpoint(3, 1140f, 1180f),
        CircuitCheckpoint(4, 540f, 1480f),
        CircuitCheckpoint(5, 760f, 2120f),
        CircuitCheckpoint(6, 1180f, 2660f),
        CircuitCheckpoint(7, 1800f, 2780f) // Finish / Start line
    )

    val bonusBoards: List<BonusBoardSpec> = listOf(
        BonusBoardSpec("board_1", 1500f, 2400f),
        BonusBoardSpec("board_2", 2180f, 2320f),
        BonusBoardSpec("board_3", 2720f, 2060f),
        BonusBoardSpec("board_4", 2680f, 1340f),
        BonusBoardSpec("board_5", 1920f, 1160f),
        BonusBoardSpec("board_6", 1420f, 1460f),
        BonusBoardSpec("board_7", 920f, 1560f),
        BonusBoardSpec("board_8", 1120f, 2240f),
        BonusBoardSpec("board_9", 1800f, 1480f),
        BonusBoardSpec("board_10", 3320f, 1280f),
        BonusBoardSpec("board_11", 400f, 1080f),
        BonusBoardSpec("board_12", 2320f, 3040f)
    )

    val obstacles: List<WorldObstacle> = buildObstacles()

    fun detectSurface(x: Float, y: Float): SurfaceType {
        val dAsphalt = minDistanceToPolyline(x, y, asphaltLoop, closed = true)
        if (dAsphalt <= ASPHALT_HALF_WIDTH) return SurfaceType.ASPHALT

        val dDirt = minDistanceToPolyline(x, y, dirtTrail, closed = false)
        if (dDirt <= DIRT_HALF_WIDTH) return SurfaceType.DIRT

        return SurfaceType.OFFROAD
    }

    fun zoneDisplayName(zoneId: String): String {
        speedTraps.firstOrNull { it.zoneId == zoneId }?.let { return it.name }
        driftZones.firstOrNull { it.zoneId == zoneId }?.let { return it.name }
        if (zoneId == CIRCUIT_ZONE_ID) return CIRCUIT_NAME
        return zoneId
    }

    fun zoneTypeFor(zoneId: String): ZoneType {
        if (speedTraps.any { it.zoneId == zoneId }) return ZoneType.SPEED_TRAP
        if (driftZones.any { it.zoneId == zoneId }) return ZoneType.DRIFT_ZONE
        return ZoneType.CIRCUIT
    }

    fun spawnPositionForZone(zoneId: String?): Pair<Vec2, Float> {
        if (zoneId == null) return Vec2(START_X, START_Y) to START_ANGLE_RAD
        speedTraps.firstOrNull { it.zoneId == zoneId }?.let { trap ->
            // Spawn 320 units upstream of the speed trap so player can accelerate through it
            val backX = trap.x - cos(trap.angleRad) * 320f
            val backY = trap.y - sin(trap.angleRad) * 320f
            return Vec2(backX, backY) to trap.angleRad
        }
        driftZones.firstOrNull { it.zoneId == zoneId }?.let { zone ->
            return Vec2(zone.centerX - 180f, zone.centerY + 80f) to (-0.4f)
        }
        return Vec2(START_X, START_Y) to START_ANGLE_RAD
    }

    private fun buildObstacles(): List<WorldObstacle> {
        val result = mutableListOf<WorldObstacle>()
        // Festival Stage Towers flanking the South Plaza
        result.add(WorldObstacle(1660f, 2660f, 28f, ObstacleKind.FESTIVAL_TOWER))
        result.add(WorldObstacle(1940f, 2660f, 28f, ObstacleKind.FESTIVAL_TOWER))

        // Deterministic desert mesas and pine clusters placed safely away from roads & zones
        var seed = 918273L
        fun nextFloat(): Float {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 40).toFloat() / 16777216f)
        }

        for (i in 0 until 95) {
            val x = 220f + nextFloat() * (WORLD_SIZE - 440f)
            val y = 220f + nextFloat() * (WORLD_SIZE - 440f)
            val dRoad = minDistanceToPolyline(x, y, asphaltLoop, closed = true)
            val dDirt = minDistanceToPolyline(x, y, dirtTrail, closed = false)
            if (dRoad < ASPHALT_HALF_WIDTH + 95f || dDirt < DIRT_HALF_WIDTH + 75f) continue
            if (speedTraps.any { hypot(x - it.x, y - it.y) < 160f }) continue
            if (driftZones.any { hypot(x - it.centerX, y - it.centerY) < it.radius + 40f }) continue
            if (bonusBoards.any { hypot(x - it.x, y - it.y) < 90f }) continue

            val kind = if (x < 1400f && y > 1700f) ObstacleKind.PINE_TREE else ObstacleKind.MESA_ROCK
            val radius = if (kind == ObstacleKind.PINE_TREE) 24f + nextFloat() * 14f else 34f + nextFloat() * 28f
            result.add(WorldObstacle(x, y, radius, kind))
        }
        return result
    }

    private fun minDistanceToPolyline(px: Float, py: Float, points: List<Vec2>, closed: Boolean): Float {
        if (points.size < 2) return Float.MAX_VALUE
        var minDist = Float.MAX_VALUE
        val limit = if (closed) points.size else points.size - 1
        for (i in 0 until limit) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val dist = distancePointToSegment(px, py, a.x, a.y, b.x, b.y)
            if (dist < minDist) minDist = dist
        }
        return minDist
    }

    private fun distancePointToSegment(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {
        val abx = bx - ax
        val aby = by - ay
        val lenSq = abx * abx + aby * aby
        if (lenSq < 1e-4f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * abx + (py - ay) * aby) / lenSq).coerceIn(0f, 1f)
        val projX = ax + t * abx
        val projY = ay + t * aby
        return hypot(px - projX, py - projY)
    }

    private fun sampleClosedCatmullRom(pts: List<Vec2>, subdivisions: Int): List<Vec2> {
        val out = mutableListOf<Vec2>()
        val n = pts.size
        for (i in 0 until n) {
            val p0 = pts[(i - 1 + n) % n]
            val p1 = pts[i]
            val p2 = pts[(i + 1) % n]
            val p3 = pts[(i + 2) % n]
            for (s in 0 until subdivisions) {
                out.add(evalCatmullRom(p0, p1, p2, p3, s.toFloat() / subdivisions))
            }
        }
        return out
    }

    private fun sampleOpenCatmullRom(pts: List<Vec2>, subdivisions: Int): List<Vec2> {
        val out = mutableListOf<Vec2>()
        val n = pts.size
        for (i in 0 until n - 1) {
            val p0 = pts[(i - 1).coerceAtLeast(0)]
            val p1 = pts[i]
            val p2 = pts[i + 1]
            val p3 = pts[(i + 2).coerceAtMost(n - 1)]
            for (s in 0 until subdivisions) {
                out.add(evalCatmullRom(p0, p1, p2, p3, s.toFloat() / subdivisions))
            }
        }
        out.add(pts.last())
        return out
    }

    private fun evalCatmullRom(p0: Vec2, p1: Vec2, p2: Vec2, p3: Vec2, t: Float): Vec2 {
        val t2 = t * t
        val t3 = t2 * t
        val x = 0.5f * ((2f * p1.x) + (-p0.x + p2.x) * t +
            (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
            (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3)
        val y = 0.5f * ((2f * p1.y) + (-p0.y + p2.y) * t +
            (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
            (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3)
        return Vec2(x, y)
    }
}
