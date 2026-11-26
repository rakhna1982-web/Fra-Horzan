package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CarSpec
import com.example.data.DriverProfile
import com.example.data.OwnedCar
import com.example.data.ZoneRecord
import com.example.game.CarState
import com.example.game.DriveCanvas
import com.example.game.DriveInput
import com.example.game.DriveMode
import com.example.game.DriveParticle
import com.example.game.HorizonWorldMap
import com.example.game.SkidMark
import com.example.game.ZoneAchievementEvent
import com.example.game.stepCarPhysics
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldDrift
import com.example.ui.theme.FestivalCoral
import com.example.ui.theme.HorizonCarbon
import com.example.ui.theme.HorizonObsidian
import com.example.ui.theme.SolarGold

@Composable
fun DriveScreen(
    profile: DriverProfile,
    carSpec: CarSpec,
    ownedCar: OwnedCar?,
    records: List<ZoneRecord>,
    driveMode: DriveMode,
    targetZoneId: String?,
    onZoneEvent: (ZoneAchievementEvent.RecordAchieved) -> Unit,
    onExitSession: (credits: Long, xp: Long, driftScore: Long, peakSpeed: Long, boardsDelta: Long) -> Unit
) {
    val initialSpawn = remember(targetZoneId, driveMode) {
        if (driveMode == DriveMode.CIRCUIT_TRIAL) {
            HorizonWorldMap.spawnPositionForZone(null)
        } else {
            HorizonWorldMap.spawnPositionForZone(targetZoneId)
        }
    }

    var carState by remember {
        mutableStateOf(
            CarState(
                x = initialSpawn.first.x,
                y = initialSpawn.first.y,
                angleRad = initialSpawn.second
            )
        )
    }

    val skids = remember { mutableStateListOf<SkidMark>() }
    val particles = remember { mutableStateListOf<DriveParticle>() }

    // Multi-touch simultaneous button states
    var leftHeld by remember { mutableStateOf(false) }
    var rightHeld by remember { mutableStateOf(false) }
    var gasHeld by remember { mutableStateOf(false) }
    var brakeHeld by remember { mutableStateOf(false) }
    var driftHeld by remember { mutableStateOf(false) }
    var nitroHeld by remember { mutableStateOf(false) }

    val paintColor = remember(ownedCar?.paintHex, carSpec.defaultPaintHex) {
        parseHexColor(ownedCar?.paintHex ?: carSpec.defaultPaintHex)
    }

    fun exitAndSave() {
        onExitSession(
            carState.sessionCredits,
            carState.sessionXp,
            carState.sessionDriftTotal,
            carState.sessionPeakSpeedKmh,
            carState.smashedBoardIds.size.toLong().coerceAtMost((50L - profile.boardsSmashed).coerceAtLeast(0L))
        )
    }

    BackHandler { exitAndSave() }

    // 60 FPS deterministic physics & particle loop
    LaunchedEffect(carSpec.carId, driveMode) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            val nowNanos = withFrameNanos { it }
            val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNanos = nowNanos

            val steerDir = (if (rightHeld) 1f else 0f) - (if (leftHeld) 1f else 0f)
            val input = DriveInput(
                steer = steerDir,
                throttle = if (gasHeld) 1f else 0f,
                brake = if (brakeHeld) 1f else 0f,
                handbrake = driftHeld,
                nitro = nitroHeld
            )

            val step = stepCarPhysics(carState, input, carSpec, ownedCar, dt, driveMode)
            carState = step.state

            if (step.newSkids.isNotEmpty()) {
                skids.addAll(step.newSkids)
                if (skids.size > 260) {
                    skids.removeRange(0, skids.size - 260)
                }
            }

            // Update existing particles
            val iter = particles.listIterator()
            while (iter.hasNext()) {
                val p = iter.next()
                val nextLife = p.life - dt
                if (nextLife <= 0f) {
                    iter.remove()
                } else {
                    iter.set(
                        p.copy(
                            x = p.x + p.vx * dt,
                            y = p.y + p.vy * dt,
                            life = nextLife
                        )
                    )
                }
            }
            if (step.newParticles.isNotEmpty()) {
                particles.addAll(step.newParticles)
                if (particles.size > 140) {
                    particles.removeRange(0, particles.size - 140)
                }
            }

            for (ev in step.zoneEvents) {
                if (ev is ZoneAchievementEvent.RecordAchieved) {
                    onZoneEvent(ev)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HorizonObsidian)
    ) {
        DriveCanvas(
            carState = carState,
            carPaintColor = paintColor,
            skids = skids,
            particles = particles,
            driveMode = driveMode,
            modifier = Modifier.fillMaxSize()
        )

        // Top Telemetry Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = HorizonCarbon.copy(alpha = 0.88f),
                    shape = CircleShape,
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    IconButton(
                        onClick = { exitAndSave() },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("exit_drive_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit to Festival Hub",
                            tint = Color.White
                        )
                    }
                }

                Surface(
                    color = HorizonCarbon.copy(alpha = 0.88f),
                    shape = CircleShape,
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    IconButton(
                        onClick = {
                            carState = carState.copy(
                                x = initialSpawn.first.x,
                                y = initialSpawn.first.y,
                                vx = 0f,
                                vy = 0f,
                                angleRad = initialSpawn.second,
                                checkpointIndex = 0,
                                lapElapsedMs = 0L
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("reset_car_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset car position",
                            tint = ElectricCyan
                        )
                    }
                }
            }

            // Center Mode / Event Pill
            Surface(
                color = HorizonCarbon.copy(alpha = 0.88f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (driveMode == DriveMode.CIRCUIT_TRIAL) {
                        val secs = carState.lapElapsedMs / 1000f
                        Text(
                            text = "LAP ${carState.lapNumber} · CP ${carState.checkpointIndex + 1}/${HorizonWorldMap.circuitCheckpoints.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldDrift
                        )
                        Text(
                            text = String.format("%.2fs", secs),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = carSpec.name.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                        Text(
                            text = "${carState.surfaceType.label} · +${carState.sessionCredits} CR",
                            style = MaterialTheme.typography.labelSmall,
                            color = SolarGold
                        )
                    }
                }
            }

            // Top-right Speedometer & Nitro Gauge
            Surface(
                color = HorizonCarbon.copy(alpha = 0.90f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .width(104.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${carState.speedKmh.toInt()}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (carState.isNitroActive) ElectricCyan else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "KM/H",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { carState.nitro },
                        color = if (carState.isNitroActive) FestivalCoral else ElectricCyan,
                        trackColor = Color(0xFF2B2440),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }

        // Live Drift Chain or Zone Achievement Banner
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 74.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (carState.currentDriftPoints >= 60f) {
                Surface(
                    color = FestivalCoral.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "DRIFT  ${carState.currentDriftPoints.toInt()}  ×${carState.driftMultiplier}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                    )
                }
            }

            if (carState.bannerTitle != null) {
                Surface(
                    color = HorizonCarbon.copy(alpha = 0.94f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.border(1.5.dp, SolarGold, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = carState.bannerTitle!!,
                            style = MaterialTheme.typography.labelLarge,
                            color = SolarGold
                        )
                        if (carState.bannerSubtitle != null) {
                            Text(
                                text = carState.bannerSubtitle!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Multi-Touch Bottom Driving Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // Left Thumb: Steering Pads
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TouchControlPad(
                    label = null,
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDesc = "Steer Left",
                    activeColor = ElectricCyan,
                    isPressed = leftHeld,
                    onPressChanged = { leftHeld = it },
                    modifier = Modifier
                        .size(width = 76.dp, height = 76.dp)
                        .testTag("steer_left_pad")
                )
                TouchControlPad(
                    label = null,
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDesc = "Steer Right",
                    activeColor = ElectricCyan,
                    isPressed = rightHeld,
                    onPressChanged = { rightHeld = it },
                    modifier = Modifier
                        .size(width = 76.dp, height = 76.dp)
                        .testTag("steer_right_pad")
                )
            }

            // Right Thumb: Drift + Nitro stacked above Brake + Gas Pedals
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TouchControlPad(
                        label = "NITRO",
                        icon = Icons.Filled.Bolt,
                        contentDesc = "Activate Nitro Boost",
                        activeColor = ElectricCyan,
                        isPressed = nitroHeld,
                        onPressChanged = { nitroHeld = it },
                        modifier = Modifier
                            .size(width = 78.dp, height = 54.dp)
                            .testTag("nitro_pad")
                    )
                    TouchControlPad(
                        label = "DRIFT",
                        icon = Icons.Filled.LocalFireDepartment,
                        contentDesc = "Handbrake Drift",
                        activeColor = SolarGold,
                        isPressed = driftHeld,
                        onPressChanged = { driftHeld = it },
                        modifier = Modifier
                            .size(width = 78.dp, height = 54.dp)
                            .testTag("drift_pad")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TouchControlPad(
                        label = "BRAKE",
                        icon = null,
                        contentDesc = "Brake or Reverse",
                        activeColor = FestivalCoral,
                        isPressed = brakeHeld,
                        onPressChanged = { brakeHeld = it },
                        modifier = Modifier
                            .size(width = 76.dp, height = 84.dp)
                            .testTag("brake_pad")
                    )
                    TouchControlPad(
                        label = "GAS",
                        icon = null,
                        contentDesc = "Accelerate",
                        activeColor = EmeraldDrift,
                        isPressed = gasHeld,
                        onPressChanged = { gasHeld = it },
                        modifier = Modifier
                            .size(width = 84.dp, height = 84.dp)
                            .testTag("gas_pad")
                    )
                }
            }
        }
    }
}

@Composable
private fun TouchControlPad(
    label: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    contentDesc: String,
    activeColor: Color,
    isPressed: Boolean,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isPressed) activeColor.copy(alpha = 0.85f) else HorizonCarbon.copy(alpha = 0.78f)
    val borderColor = if (isPressed) Color.White else activeColor.copy(alpha = 0.65f)
    val fgColor = if (isPressed) HorizonObsidian else Color.White

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyDown = event.changes.any { it.pressed }
                        onPressChanged(anyDown)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDesc,
                    tint = fgColor,
                    modifier = Modifier.size(if (label == null) 34.dp else 20.dp)
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 12.sp,
                    color = fgColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        FestivalCoral
    }
}
