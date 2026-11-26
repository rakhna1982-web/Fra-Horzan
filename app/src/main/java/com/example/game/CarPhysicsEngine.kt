package com.example.game

import com.example.data.CarSpec
import com.example.data.OwnedCar
import com.example.data.ZoneType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class DriveMode {
    FREE_ROAM,
    CIRCUIT_TRIAL
}

data class DriveInput(
    val steer: Float = 0f,     // -1f (left) to +1f (right)
    val throttle: Float = 0f,  // 0f to 1f
    val brake: Float = 0f,     // 0f to 1f
    val handbrake: Boolean = false,
    val nitro: Boolean = false
)

data class SkidMark(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val alpha: Float
)

data class DriveParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val life: Float,
    val maxLife: Float,
    val isFlame: Boolean,
    val isGoldBurst: Boolean = false
)

sealed interface ZoneAchievementEvent {
    data class RecordAchieved(
        val zoneId: String,
        val zoneType: ZoneType,
        val value: Long,
        val stars: Long,
        val bonusCredits: Long,
        val bonusXp: Long
    ) : ZoneAchievementEvent
}

data class CarState(
    val x: Float = HorizonWorldMap.START_X,
    val y: Float = HorizonWorldMap.START_Y,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val angleRad: Float = HorizonWorldMap.START_ANGLE_RAD,
    val steerVisualRad: Float = 0f,
    val speedKmh: Float = 0f,
    val nitro: Float = 1f,
    val isDrifting: Boolean = false,
    val isBraking: Boolean = false,
    val isNitroActive: Boolean = false,
    val surfaceType: SurfaceType = SurfaceType.ASPHALT,
    val currentDriftPoints: Float = 0f,
    val driftMultiplier: Int = 1,
    val driftIdleTimer: Float = 0f,
    val sessionCredits: Long = 0L,
    val sessionXp: Long = 0L,
    val sessionDriftTotal: Long = 0L,
    val sessionPeakSpeedKmh: Long = 0L,
    val smashedBoardIds: Set<String> = emptySet(),
    val activeDriftZoneId: String? = null,
    val activeDriftZoneScore: Float = 0f,
    val lastSpeedTrapId: String? = null,
    val speedTrapCooldown: Float = 0f,
    val checkpointIndex: Int = 0,
    val lapNumber: Int = 1,
    val lapElapsedMs: Long = 0L,
    val bestLapMs: Long = 0L,
    val bannerTitle: String? = null,
    val bannerSubtitle: String? = null,
    val bannerTimerSec: Float = 0f
)

data class PhysicsStepResult(
    val state: CarState,
    val newSkids: List<SkidMark>,
    val newParticles: List<DriveParticle>,
    val zoneEvents: List<ZoneAchievementEvent>
)

fun stepCarPhysics(
    current: CarState,
    input: DriveInput,
    carSpec: CarSpec,
    ownedCar: OwnedCar?,
    dt: Float,
    driveMode: DriveMode
): PhysicsStepResult {
    val safeDt = dt.coerceIn(0.001f, 0.05f)
    val engineLvl = (ownedCar?.engineLevel ?: 1L).toFloat()
    val tiresLvl = (ownedCar?.tiresLevel ?: 1L).toFloat()
    val nitroLvl = (ownedCar?.nitroLevel ?: 1L).toFloat()

    // Scale car capability with upgrades
    val maxSpeedUnits = (carSpec.baseTopSpeedKmh * (1f + (engineLvl - 1f) * 0.06f)) * 2.25f
    val accelForce = carSpec.baseAcceleration * (1f + (engineLvl - 1f) * 0.08f)

    val forwardX = cos(current.angleRad)
    val forwardY = sin(current.angleRad)
    val rightX = -forwardY
    val rightY = forwardX

    var vx = current.vx
    var vy = current.vy
    var forwardVel = vx * forwardX + vy * forwardY
    var lateralVel = vx * rightX + vy * rightY

    val surface = HorizonWorldMap.detectSurface(current.x, current.y)

    val nitroActive = input.nitro && current.nitro > 0.03f && input.throttle > 0.1f
    val nextNitro = if (nitroActive) {
        (current.nitro - safeDt * (0.28f / (1f + (nitroLvl - 1f) * 0.12f))).coerceIn(0f, 1f)
    } else {
        val rechargeRate = if (current.isDrifting) 0.14f else 0.05f
        (current.nitro + safeDt * rechargeRate).coerceIn(0f, 1f)
    }

    // Acceleration, braking & reverse
    var brakingNow = false
    val boostMult = if (nitroActive) 1.42f else 1f
    val effectiveMaxSpeed = maxSpeedUnits * (if (nitroActive) 1.18f else 1f)

    if (input.throttle > 0.01f) {
        if (forwardVel < -25f) {
            forwardVel += accelForce * 1.8f * input.throttle * safeDt
            brakingNow = true
        } else if (forwardVel < effectiveMaxSpeed) {
            val speedRatio = (forwardVel / effectiveMaxSpeed).coerceIn(0f, 1f)
            val curveFactor = 1.15f - 0.55f * speedRatio
            forwardVel += accelForce * boostMult * curveFactor * input.throttle * safeDt
        }
    }

    if (input.brake > 0.01f) {
        if (forwardVel > 20f) {
            forwardVel -= accelForce * 1.95f * input.brake * safeDt
            brakingNow = true
        } else {
            val maxRev = -maxSpeedUnits * 0.34f
            if (forwardVel > maxRev) {
                forwardVel -= accelForce * 0.72f * input.brake * safeDt
            }
        }
    }

    if (input.handbrake) {
        forwardVel *= (1f - 1.1f * safeDt).coerceAtLeast(0f)
        brakingNow = true
    }

    // Rolling resistance (higher offroad unless driving an offroad/rally spec)
    val dragCoeff = when (surface) {
        SurfaceType.ASPHALT -> 0.32f
        SurfaceType.DIRT -> 0.55f
        SurfaceType.OFFROAD -> if (carSpec.offroadGrip > 4.5f) 0.58f else 1.05f
    }
    forwardVel *= (1f - dragCoeff * safeDt).coerceAtLeast(0f)

    // Steering response
    val speedMag = hypot(forwardVel, lateralVel)
    val speedKmh = (speedMag / 2.25f)
    val steerSpeedFactor = (speedMag / 140f).coerceIn(0f, 1f)
    val highSpeedDamp = 1f - 0.38f * (speedMag / effectiveMaxSpeed).coerceIn(0f, 1f)
    val turnDirection = if (forwardVel >= -10f) 1f else -1f
    val turnRate = 2.85f * highSpeedDamp * (if (input.handbrake) 1.28f else 1f)
    val nextAngle = current.angleRad + input.steer * turnRate * steerSpeedFactor * turnDirection * safeDt

    // Recompute forward/right basis after turning & update lateral tire grip
    val baseGrip = when (surface) {
        SurfaceType.ASPHALT -> carSpec.asphaltGrip
        SurfaceType.DIRT -> (carSpec.asphaltGrip + carSpec.offroadGrip) * 0.5f
        SurfaceType.OFFROAD -> carSpec.offroadGrip
    } * (1f + (tiresLvl - 1f) * 0.08f)

    val driftGrip = if (input.handbrake) baseGrip * 0.24f else baseGrip
    lateralVel *= (1f - (driftGrip * safeDt).coerceAtMost(0.92f))

    val nextForwardX = cos(nextAngle)
    val nextForwardY = sin(nextAngle)
    val nextRightX = -nextForwardY
    val nextRightY = nextForwardX

    vx = nextForwardX * forwardVel + nextRightX * lateralVel
    vy = nextForwardY * forwardVel + nextRightY * lateralVel

    var nextX = (current.x + vx * safeDt).coerceIn(80f, HorizonWorldMap.WORLD_SIZE - 80f)
    var nextY = (current.y + vy * safeDt).coerceIn(80f, HorizonWorldMap.WORLD_SIZE - 80f)

    // Collision check against canyon boulders, trees, and towers
    var hardCrash = false
    val carRadius = 22f
    for (obs in HorizonWorldMap.obstacles) {
        val dx = nextX - obs.x
        val dy = nextY - obs.y
        val minDist = carRadius + obs.radius
        val distSq = dx * dx + dy * dy
        if (distSq < minDist * minDist && distSq > 1e-4f) {
            val dist = kotlin.math.sqrt(distSq)
            val nx = dx / dist
            val ny = dy / dist
            nextX = obs.x + nx * minDist
            nextY = obs.y + ny * minDist

            val impactSpeed = vx * nx + vy * ny
            if (impactSpeed < 0f) {
                if (abs(impactSpeed) > 155f) hardCrash = true
                vx -= 1.35f * impactSpeed * nx
                vy -= 1.35f * impactSpeed * ny
                vx *= 0.7f
                vy *= 0.7f
            }
        }
    }

    // Drift detection & Skill Chain scoring
    val lateralSlipRatio = abs(lateralVel)
    val isDriftingNow = speedKmh > 38f && (lateralSlipRatio > 58f || (input.handbrake && abs(input.steer) > 0.2f))

    var driftPts = current.currentDriftPoints
    var driftMult = current.driftMultiplier
    var driftIdle = current.driftIdleTimer
    var bankedCredits = 0L
    var bankedXp = 0L
    var bankedDrift = 0L
    var bannerTitle = current.bannerTitle
    var bannerSubtitle = current.bannerSubtitle
    var bannerTimer = (current.bannerTimerSec - safeDt).coerceAtLeast(0f)

    if (hardCrash && driftPts > 0f) {
        driftPts = 0f
        driftMult = 1
        driftIdle = 0f
        bannerTitle = "COLLISION!"
        bannerSubtitle = "Skill chain lost"
        bannerTimer = 1.6f
    } else if (isDriftingNow) {
        val ptsGain = (lateralSlipRatio * 0.85f + speedKmh * 0.4f) * carSpec.driftFactor * safeDt * 12f
        driftPts += ptsGain
        driftMult = (1 + (driftPts / 1200f).toInt()).coerceIn(1, 5)
        driftIdle = 0f
    } else if (driftPts > 0f) {
        driftIdle += safeDt
        if (driftIdle >= 1.35f) {
            val totalSkill = (driftPts * driftMult).toLong()
            if (totalSkill >= 150L) {
                bankedDrift = totalSkill
                bankedCredits = (totalSkill / 10L).coerceIn(50L, 4500L)
                bankedXp = (totalSkill / 12L).coerceIn(30L, 2500L)
                bannerTitle = "DRIFT CHAIN +$totalSkill"
                bannerSubtitle = "+$bankedCredits CR · +$bankedXp XP"
                bannerTimer = 2.2f
            }
            driftPts = 0f
            driftMult = 1
            driftIdle = 0f
        }
    }

    val newSkids = mutableListOf<SkidMark>()
    val newParticles = mutableListOf<DriveParticle>()

    // Rear wheel skid marks & smoke/flame particles
    if (isDriftingNow || (input.handbrake && speedKmh > 25f)) {
        val rearX = nextX - nextForwardX * 18f
        val rearY = nextY - nextForwardY * 18f
        val prevRearX = current.x - forwardX * 18f
        val prevRearY = current.y - forwardY * 18f
        val trackOffset = 11f
        newSkids.add(
            SkidMark(
                prevRearX + rightX * trackOffset,
                prevRearY + rightY * trackOffset,
                rearX + nextRightX * trackOffset,
                rearY + nextRightY * trackOffset,
                0.55f
            )
        )
        newSkids.add(
            SkidMark(
                prevRearX - rightX * trackOffset,
                prevRearY - rightY * trackOffset,
                rearX - nextRightX * trackOffset,
                rearY - nextRightY * trackOffset,
                0.55f
            )
        )
        newParticles.add(
            DriveParticle(
                x = rearX + (nextRightX * ((Math.random().toFloat() - 0.5f) * 20f)),
                y = rearY + (nextRightY * ((Math.random().toFloat() - 0.5f) * 20f)),
                vx = -vx * 0.12f + (Math.random().toFloat() - 0.5f) * 35f,
                vy = -vy * 0.12f + (Math.random().toFloat() - 0.5f) * 35f,
                radius = 7f + Math.random().toFloat() * 7f,
                life = 0.55f,
                maxLife = 0.55f,
                isFlame = false
            )
        )
    }

    if (nitroActive) {
        val exhaustX = nextX - nextForwardX * 26f
        val exhaustY = nextY - nextForwardY * 26f
        newParticles.add(
            DriveParticle(
                x = exhaustX,
                y = exhaustY,
                vx = -nextForwardX * 120f + (Math.random().toFloat() - 0.5f) * 30f,
                vy = -nextForwardY * 120f + (Math.random().toFloat() - 0.5f) * 30f,
                radius = 6f + Math.random().toFloat() * 5f,
                life = 0.25f,
                maxLife = 0.25f,
                isFlame = true
            )
        )
    }

    // World events: Bonus boards, Speed Traps, Drift Zones, Circuit Checkpoints
    val events = mutableListOf<ZoneAchievementEvent>()
    val smashedSet = current.smashedBoardIds.toMutableSet()

    for (board in HorizonWorldMap.bonusBoards) {
        if (!smashedSet.contains(board.boardId) && hypot(nextX - board.x, nextY - board.y) < 38f) {
            smashedSet.add(board.boardId)
            bankedCredits += board.rewardCredits
            bankedXp += board.rewardXp
            bannerTitle = "BONUS BOARD SMASHED!"
            bannerSubtitle = "+${board.rewardCredits} CR · +${board.rewardXp} XP"
            bannerTimer = 2.4f
            repeat(12) {
                val a = (it / 12f) * 6.283f
                newParticles.add(
                    DriveParticle(
                        x = board.x,
                        y = board.y,
                        vx = cos(a) * 160f,
                        vy = sin(a) * 160f,
                        radius = 6f,
                        life = 0.55f,
                        maxLife = 0.55f,
                        isFlame = false,
                        isGoldBurst = true
                    )
                )
            }
        }
    }

    var cooldown = (current.speedTrapCooldown - safeDt).coerceAtLeast(0f)
    var lastTrap = current.lastSpeedTrapId
    for (trap in HorizonWorldMap.speedTraps) {
        if (hypot(nextX - trap.x, nextY - trap.y) < trap.gateRadius) {
            if (cooldown <= 0f || lastTrap != trap.zoneId) {
                val recordedKmh = speedKmh.toLong().coerceAtLeast(1L)
                val stars = when {
                    recordedKmh >= trap.threeStarKmh -> 3L
                    recordedKmh >= trap.twoStarKmh -> 2L
                    recordedKmh >= trap.oneStarKmh -> 1L
                    else -> 0L
                }
                val bonusCr = 250L + stars * 400L
                val bonusXp = 150L + stars * 200L
                bankedCredits += bonusCr
                bankedXp += bonusXp
                bannerTitle = "${trap.name}: $recordedKmh KM/H"
                bannerSubtitle = "${stars}★ SPEED TRAP · +$bonusCr CR"
                bannerTimer = 2.6f
                events.add(
                    ZoneAchievementEvent.RecordAchieved(
                        zoneId = trap.zoneId,
                        zoneType = ZoneType.SPEED_TRAP,
                        value = recordedKmh,
                        stars = stars,
                        bonusCredits = bonusCr,
                        bonusXp = bonusXp
                    )
                )
                lastTrap = trap.zoneId
                cooldown = 6.0f
            }
        }
    }

    // Drift Zone tracking
    val insideDriftZone = HorizonWorldMap.driftZones.firstOrNull {
        hypot(nextX - it.centerX, nextY - it.centerY) <= it.radius
    }
    var activeDriftId = current.activeDriftZoneId
    var activeDriftZoneScore = current.activeDriftZoneScore

    if (insideDriftZone != null) {
        if (activeDriftId != insideDriftZone.zoneId) {
            activeDriftId = insideDriftZone.zoneId
            activeDriftZoneScore = 0f
            bannerTitle = insideDriftZone.name.uppercase()
            bannerSubtitle = "Slide to score drift points!"
            bannerTimer = 1.8f
        } else if (isDriftingNow) {
            activeDriftZoneScore += (lateralSlipRatio * 0.9f + speedKmh * 0.5f) * carSpec.driftFactor * safeDt * 18f
        }
    } else if (activeDriftId != null) {
        val finishedZone = HorizonWorldMap.driftZones.firstOrNull { it.zoneId == activeDriftId }
        val finalPts = activeDriftZoneScore.toLong()
        if (finishedZone != null && finalPts >= 400L) {
            val stars = when {
                finalPts >= finishedZone.threeStarPts -> 3L
                finalPts >= finishedZone.twoStarPts -> 2L
                finalPts >= finishedZone.oneStarPts -> 1L
                else -> 0L
            }
            val bonusCr = 300L + stars * 500L
            val bonusXp = 200L + stars * 250L
            bankedCredits += bonusCr
            bankedXp += bonusXp
            bannerTitle = "${finishedZone.name}: $finalPts PTS"
            bannerSubtitle = "${stars}★ DRIFT ZONE · +$bonusCr CR"
            bannerTimer = 2.6f
            events.add(
                ZoneAchievementEvent.RecordAchieved(
                    zoneId = finishedZone.zoneId,
                    zoneType = ZoneType.DRIFT_ZONE,
                    value = finalPts,
                    stars = stars,
                    bonusCredits = bonusCr,
                    bonusXp = bonusXp
                )
            )
        }
        activeDriftId = null
        activeDriftZoneScore = 0f
    }

    // Circuit Time Trial progression
    var cpIndex = current.checkpointIndex
    var lapNum = current.lapNumber
    var lapMs = current.lapElapsedMs
    var bestLapMs = current.bestLapMs

    if (driveMode == DriveMode.CIRCUIT_TRIAL) {
        lapMs += (safeDt * 1000f).toLong()
        val targetCp = HorizonWorldMap.circuitCheckpoints[cpIndex]
        if (hypot(nextX - targetCp.x, nextY - targetCp.y) <= targetCp.radius) {
            if (cpIndex == HorizonWorldMap.circuitCheckpoints.lastIndex) {
                val finishedLapMs = lapMs
                val stars = when {
                    finishedLapMs <= HorizonWorldMap.CIRCUIT_THREE_STAR_MS -> 3L
                    finishedLapMs <= HorizonWorldMap.CIRCUIT_TWO_STAR_MS -> 2L
                    finishedLapMs <= HorizonWorldMap.CIRCUIT_ONE_STAR_MS -> 1L
                    else -> 0L
                }
                val bonusCr = 800L + stars * 700L
                val bonusXp = 450L + stars * 300L
                bankedCredits += bonusCr
                bankedXp += bonusXp
                if (bestLapMs == 0L || finishedLapMs < bestLapMs) {
                    bestLapMs = finishedLapMs
                }
                val secStr = String.format("%.2fs", finishedLapMs / 1000f)
                bannerTitle = "LAP $lapNum COMPLETE · $secStr"
                bannerSubtitle = "${stars}★ CIRCUIT · +$bonusCr CR"
                bannerTimer = 3.0f
                events.add(
                    ZoneAchievementEvent.RecordAchieved(
                        zoneId = HorizonWorldMap.CIRCUIT_ZONE_ID,
                        zoneType = ZoneType.CIRCUIT,
                        value = finishedLapMs,
                        stars = stars,
                        bonusCredits = bonusCr,
                        bonusXp = bonusXp
                    )
                )
                cpIndex = 0
                lapNum += 1
                lapMs = 0L
            } else {
                cpIndex += 1
            }
        }
    }

    val peakSpeed = maxOf(current.sessionPeakSpeedKmh, speedKmh.toLong())

    val updated = current.copy(
        x = nextX,
        y = nextY,
        vx = vx,
        vy = vy,
        angleRad = nextAngle,
        steerVisualRad = input.steer * 0.45f,
        speedKmh = speedKmh,
        nitro = nextNitro,
        isDrifting = isDriftingNow,
        isBraking = brakingNow,
        isNitroActive = nitroActive,
        surfaceType = surface,
        currentDriftPoints = driftPts,
        driftMultiplier = driftMult,
        driftIdleTimer = driftIdle,
        sessionCredits = current.sessionCredits + bankedCredits,
        sessionXp = current.sessionXp + bankedXp,
        sessionDriftTotal = current.sessionDriftTotal + bankedDrift,
        sessionPeakSpeedKmh = peakSpeed,
        smashedBoardIds = smashedSet,
        activeDriftZoneId = activeDriftId,
        activeDriftZoneScore = activeDriftZoneScore,
        lastSpeedTrapId = lastTrap,
        speedTrapCooldown = cooldown,
        checkpointIndex = cpIndex,
        lapNumber = lapNum,
        lapElapsedMs = lapMs,
        bestLapMs = bestLapMs,
        bannerTitle = if (bannerTimer > 0f) bannerTitle else null,
        bannerSubtitle = if (bannerTimer > 0f) bannerSubtitle else null,
        bannerTimerSec = bannerTimer
    )

    return PhysicsStepResult(updated, newSkids, newParticles, events)
}
