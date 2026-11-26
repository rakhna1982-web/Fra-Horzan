package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CarSpec
import com.example.data.HorizonCatalog
import com.example.data.OwnedCar
import com.example.data.ZoneRecord
import com.example.game.DriveMode
import com.example.game.HorizonWorldMap
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldDrift
import com.example.ui.theme.FestivalCoral
import com.example.ui.theme.HorizonCarbon
import com.example.ui.theme.HorizonObsidian
import com.example.ui.theme.HorizonSurfaceHigh
import com.example.ui.theme.SolarGold

@Composable
fun GarageTab(
    data: FestivalDashboardData,
    onSelectCar: (String) -> Unit,
    onBuyCar: (CarSpec) -> Unit,
    onPaintCar: (OwnedCar, String) -> Unit,
    onUpgradeCar: (OwnedCar, UpgradeSlot) -> Unit,
    onDriveCar: () -> Unit
) {
    val ownedMap = data.garage.associateBy { it.carId }
    val activeCarId = data.profile.selectedCarId

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "HORIZON GARAGE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Unlock festival machines, paint liveries, and tune performance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(HorizonCatalog.cars, key = { it.carId }) { spec ->
            val owned = ownedMap[spec.carId]
            val isSelected = spec.carId == activeCarId
            val paintHex = owned?.paintHex ?: spec.defaultPaintHex

            GarageCarCard(
                spec = spec,
                owned = owned,
                isSelected = isSelected,
                credits = data.profile.credits,
                paintColor = parseHexColor(paintHex),
                onSelect = { onSelectCar(spec.carId) },
                onBuy = { onBuyCar(spec) },
                onPaint = { hex -> if (owned != null) onPaintCar(owned, hex) },
                onUpgrade = { slot -> if (owned != null) onUpgradeCar(owned, slot) },
                onDrive = onDriveCar
            )
        }
    }
}

@Composable
private fun GarageCarCard(
    spec: CarSpec,
    owned: OwnedCar?,
    isSelected: Boolean,
    credits: Long,
    paintColor: Color,
    onSelect: () -> Unit,
    onBuy: () -> Unit,
    onPaint: (String) -> Unit,
    onUpgrade: (UpgradeSlot) -> Unit,
    onDrive: () -> Unit
) {
    val borderColor = when {
        isSelected -> ElectricCyan
        owned != null -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HorizonCarbon)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = spec.carClass,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (isSelected) {
                Surface(
                    color = ElectricCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Active Car",
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                }
            } else if (owned == null) {
                Surface(
                    color = SolarGold.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked",
                            tint = SolarGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${spec.price} CR",
                            style = MaterialTheme.typography.labelSmall,
                            color = SolarGold
                        )
                    }
                }
            }
        }

        // Stylized Car Showcase Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(HorizonObsidian, paintColor.copy(alpha = 0.22f), HorizonObsidian)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                // Road shadow
                drawOval(
                    color = Color(0x88000000),
                    topLeft = Offset(cx - 105f, cy + 18f),
                    size = Size(210f, 20f)
                )
                // Side silhouette body
                drawRoundRect(
                    color = paintColor,
                    topLeft = Offset(cx - 90f, cy - 12f),
                    size = Size(180f, 32f),
                    cornerRadius = CornerRadius(14f, 14f)
                )
                // Aerodynamic cabin dome
                drawRoundRect(
                    color = Color(0xFF141826),
                    topLeft = Offset(cx - 42f, cy - 26f),
                    size = Size(86f, 22f),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                // Wheels
                drawCircle(Color(0xFF0E0E14), radius = 17f, center = Offset(cx - 56f, cy + 20f))
                drawCircle(Color(0xFF7D7696), radius = 9f, center = Offset(cx - 56f, cy + 20f))
                drawCircle(Color(0xFF0E0E14), radius = 17f, center = Offset(cx + 56f, cy + 20f))
                drawCircle(Color(0xFF7D7696), radius = 9f, center = Offset(cx + 56f, cy + 20f))
            }
        }

        Text(
            text = spec.tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Stat bars
        val engMult = 1f + ((owned?.engineLevel ?: 1L) - 1L) * 0.06f
        val tireMult = 1f + ((owned?.tiresLevel ?: 1L) - 1L) * 0.08f
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CarStatBar("TOP SPEED", ((spec.baseTopSpeedKmh * engMult) / 390f).coerceIn(0f, 1f), FestivalCoral)
            CarStatBar("HANDLING", ((spec.asphaltGrip * tireMult) / 10f).coerceIn(0f, 1f), ElectricCyan)
            CarStatBar("OFFROAD", (spec.offroadGrip / 6.5f).coerceIn(0f, 1f), SolarGold)
        }

        if (owned != null) {
            // Paint Swatches
            Text(
                text = "LIVERY PAINT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(HorizonCatalog.paintSwatches) { swatch ->
                    val swatchColor = parseHexColor(swatch.hex)
                    val selectedPaint = owned.paintHex.equals(swatch.hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(HorizonSurfaceHigh)
                            .clickable { onPaint(swatch.hex) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (selectedPaint) 2.5.dp else 1.dp,
                                    color = if (selectedPaint) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }

            // Upgrade Slots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UpgradeButton(
                    title = "ENGINE",
                    level = owned.engineLevel,
                    onClick = { onUpgrade(UpgradeSlot.ENGINE) },
                    modifier = Modifier.weight(1f)
                )
                UpgradeButton(
                    title = "TIRES",
                    level = owned.tiresLevel,
                    onClick = { onUpgrade(UpgradeSlot.TIRES) },
                    modifier = Modifier.weight(1f)
                )
                UpgradeButton(
                    title = "NITRO",
                    level = owned.nitroLevel,
                    onClick = { onUpgrade(UpgradeSlot.NITRO) },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isSelected) {
                    OutlinedButton(
                        onClick = onSelect,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("select_car_${spec.carId}")
                    ) {
                        Text("SELECT CAR", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Button(
                    onClick = {
                        if (!isSelected) onSelect()
                        onDrive()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FestivalCoral),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("drive_car_${spec.carId}")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("DRIVE NOW", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            Button(
                onClick = onBuy,
                enabled = credits >= spec.price,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SolarGold,
                    contentColor = HorizonObsidian
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("buy_car_${spec.carId}")
            ) {
                Icon(Icons.Filled.DirectionsCar, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (credits >= spec.price) "UNLOCK FOR ${spec.price} CR" else "NEED ${spec.price} CR",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun CarStatBar(label: String, value: Float, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp)
        )
        LinearProgressIndicator(
            progress = { value },
            color = color,
            trackColor = HorizonSurfaceHigh,
            modifier = Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun UpgradeButton(
    title: String,
    level: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cost = HorizonCatalog.upgradeCost(level)
    Surface(
        onClick = onClick,
        color = HorizonSurfaceHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(60.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.Upgrade, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(14.dp))
                Text(
                    text = "$title T$level",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = if (level >= 5L) "MAXED" else "$cost CR",
                style = MaterialTheme.typography.labelSmall,
                color = if (level >= 5L) EmeraldDrift else SolarGold
            )
        }
    }
}

@Composable
fun EventsTab(
    records: List<ZoneRecord>,
    onLaunchZone: (DriveMode, String?) -> Unit
) {
    val recordMap = records.associateBy { it.zoneId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "FESTIVAL EVENTS & STUNTS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Warp directly to any Speed Trap, Drift Zone, or Time Trial.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Horizon Circuit Time Trial
        item {
            val circuitRec = recordMap[HorizonWorldMap.CIRCUIT_ZONE_ID]
            val bestText = if (circuitRec != null && circuitRec.bestValue > 0L) {
                String.format("%.2fs", circuitRec.bestValue / 1000f)
            } else "No lap set"
            EventZoneCard(
                badge = "CIRCUIT TIME TRIAL",
                title = HorizonWorldMap.CIRCUIT_NAME,
                targetText = "3★ Target: < 38.00s · 2★ < 48.00s",
                bestText = "Best Lap: $bestText",
                stars = circuitRec?.stars ?: 0L,
                accent = EmeraldDrift,
                icon = Icons.Filled.EmojiEvents,
                onLaunch = { onLaunchZone(DriveMode.CIRCUIT_TRIAL, null) }
            )
        }

        // Speed Traps
        items(HorizonWorldMap.speedTraps, key = { it.zoneId }) { trap ->
            val rec = recordMap[trap.zoneId]
            val bestText = if (rec != null) "${rec.bestValue} km/h" else "Not clocked"
            EventZoneCard(
                badge = "SPEED TRAP",
                title = trap.name,
                targetText = "3★ Target: ${trap.threeStarKmh} km/h · 2★ ${trap.twoStarKmh} km/h",
                bestText = "Personal Best: $bestText",
                stars = rec?.stars ?: 0L,
                accent = ElectricCyan,
                icon = Icons.Filled.Speed,
                onLaunch = { onLaunchZone(DriveMode.FREE_ROAM, trap.zoneId) }
            )
        }

        // Drift Zones
        items(HorizonWorldMap.driftZones, key = { it.zoneId }) { dz ->
            val rec = recordMap[dz.zoneId]
            val bestText = if (rec != null) "${rec.bestValue} pts" else "No score"
            EventZoneCard(
                badge = "DRIFT ZONE",
                title = dz.name,
                targetText = "3★ Target: ${dz.threeStarPts} pts · 2★ ${dz.twoStarPts} pts",
                bestText = "Personal Best: $bestText",
                stars = rec?.stars ?: 0L,
                accent = FestivalCoral,
                icon = Icons.Filled.LocalFireDepartment,
                onLaunch = { onLaunchZone(DriveMode.FREE_ROAM, dz.zoneId) }
            )
        }
    }
}

@Composable
private fun EventZoneCard(
    badge: String,
    title: String,
    targetText: String,
    bestText: String,
    stars: Long,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onLaunch: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HorizonCarbon)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = accent)
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (s in 1..3) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (s <= stars) SolarGold else HorizonSurfaceHigh,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Text(targetText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(bestText, style = MaterialTheme.typography.labelLarge, color = SolarGold)
            Button(
                onClick = onLaunch,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = HorizonObsidian),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("WARP & DRIVE", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
