package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.auth.signOutFromHorizon
import com.example.data.HorizonCatalog
import com.example.game.DriveMode
import com.example.game.HorizonWorldMap
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldDrift
import com.example.ui.theme.FestivalCoral
import com.example.ui.theme.HorizonCarbon
import com.example.ui.theme.HorizonObsidian
import com.example.ui.theme.HorizonSurfaceHigh
import com.example.ui.theme.SolarGold

enum class HubTab {
    FESTIVAL,
    GARAGE,
    EVENTS,
    DRIVER
}

@Composable
fun FestivalRootScreen(
    viewModel: HorizonViewModel,
    userEmail: String
) {
    val uiState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeTab by rememberSaveable { mutableStateOf(HubTab.FESTIVAL) }
    var isDriving by rememberSaveable { mutableStateOf(false) }
    var activeDriveMode by rememberSaveable { mutableStateOf(DriveMode.FREE_ROAM) }
    var targetZoneId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    when (val state = uiState) {
        UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HorizonObsidian),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = FestivalCoral)
                    Text(
                        text = "LOADING HORIZON FESTIVAL...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(HorizonObsidian)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "CLOUD SYNC ERROR",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is UiState.Success -> {
            val data = state.data
            val selectedCarSpec = HorizonCatalog.getCar(data.profile.selectedCarId)
            val ownedActiveCar = data.garage.firstOrNull { it.carId == data.profile.selectedCarId }

            if (isDriving) {
                DriveScreen(
                    profile = data.profile,
                    carSpec = selectedCarSpec,
                    ownedCar = ownedActiveCar,
                    records = data.records,
                    driveMode = activeDriveMode,
                    targetZoneId = targetZoneId,
                    onZoneEvent = { event ->
                        val existing = data.records.firstOrNull { it.zoneId == event.zoneId }
                        viewModel.saveZoneEvent(event, selectedCarSpec.carId, existing)
                    },
                    onExitSession = { credits, xp, drift, peakSpeed, boardsDelta ->
                        viewModel.commitDriveSession(
                            currentProfile = data.profile,
                            earnedCredits = credits,
                            earnedXp = xp,
                            driftScoreAdded = drift,
                            peakSpeedKmh = peakSpeed,
                            boardsDelta = boardsDelta
                        )
                        isDriving = false
                    }
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val useRail = maxWidth >= 700.dp

                    Scaffold(
                        containerColor = HorizonObsidian,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar(
                                    containerColor = HorizonCarbon,
                                    modifier = Modifier.navigationBarsPadding()
                                ) {
                                    HubTab.entries.forEach { tab ->
                                        val selected = tab == activeTab
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { activeTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = tabIcon(tab, selected),
                                                    contentDescription = tabLabel(tab)
                                                )
                                            },
                                            label = { Text(tabLabel(tab), style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (useRail) {
                                NavigationRail(
                                    containerColor = HorizonCarbon,
                                    modifier = Modifier.statusBarsPadding()
                                ) {
                                    Spacer(Modifier.height(12.dp))
                                    HubTab.entries.forEach { tab ->
                                        val selected = tab == activeTab
                                        NavigationRailItem(
                                            selected = selected,
                                            onClick = { activeTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = tabIcon(tab, selected),
                                                    contentDescription = tabLabel(tab)
                                                )
                                            },
                                            label = { Text(tabLabel(tab), style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                            ) {
                                TopStatusRibbon(data)

                                Box(modifier = Modifier.weight(1f)) {
                                    when (activeTab) {
                                        HubTab.FESTIVAL -> FestivalHomeTab(
                                            data = data,
                                            activeCarName = selectedCarSpec.name,
                                            onLaunchDrive = { mode, zone ->
                                                activeDriveMode = mode
                                                targetZoneId = zone
                                                isDriving = true
                                            },
                                            onOpenGarage = { activeTab = HubTab.GARAGE }
                                        )

                                        HubTab.GARAGE -> GarageTab(
                                            data = data,
                                            onSelectCar = viewModel::selectCar,
                                            onBuyCar = { spec -> viewModel.buyCar(spec, data.profile.credits) },
                                            onPaintCar = viewModel::applyPaint,
                                            onUpgradeCar = { car, slot ->
                                                viewModel.upgradeCarSlot(car, slot, data.profile.credits)
                                            },
                                            onDriveCar = {
                                                activeDriveMode = DriveMode.FREE_ROAM
                                                targetZoneId = null
                                                isDriving = true
                                            }
                                        )

                                        HubTab.EVENTS -> EventsTab(
                                            records = data.records,
                                            onLaunchZone = { mode, zone ->
                                                activeDriveMode = mode
                                                targetZoneId = zone
                                                isDriving = true
                                            }
                                        )

                                        HubTab.DRIVER -> DriverProfileTab(
                                            data = data,
                                            userEmail = userEmail
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopStatusRibbon(data: FestivalDashboardData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HorizonCarbon)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(FestivalCoral.copy(alpha = 0.2f))
                    .border(1.5.dp, FestivalCoral, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${data.profile.level}",
                    style = MaterialTheme.typography.labelLarge,
                    color = FestivalCoral
                )
            }
            Column {
                Text(
                    text = data.profile.displayName.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
                val xpInLevel = (data.profile.xp % 1500L) / 1500f
                LinearProgressIndicator(
                    progress = { xpInLevel },
                    color = ElectricCyan,
                    trackColor = HorizonSurfaceHigh,
                    modifier = Modifier
                        .width(96.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }

        Surface(
            color = SolarGold.copy(alpha = 0.16f),
            shape = RoundedCornerShape(50),
            modifier = Modifier.border(1.dp, SolarGold.copy(alpha = 0.5f), RoundedCornerShape(50))
        ) {
            Text(
                text = "${data.profile.credits} CR",
                style = MaterialTheme.typography.labelLarge,
                color = SolarGold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun FestivalHomeTab(
    data: FestivalDashboardData,
    activeCarName: String,
    onLaunchDrive: (DriveMode, String?) -> Unit,
    onOpenGarage: () -> Unit
) {
    val totalStars = data.records.sumOf { it.stars }
    val maxStars = (HorizonWorldMap.speedTraps.size + HorizonWorldMap.driftZones.size + 1) * 3

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Festival Launch Banner
        item {
            Box(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_festival_hero),
                    contentDescription = "Apex Horizon open-world festival stage",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x220D0A14), Color(0xEE0D0A14))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "READY IN: ${activeCarName.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                    Text(
                        text = "HIT THE HORIZON CANYON",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onLaunchDrive(DriveMode.FREE_ROAM, null) },
                            colors = ButtonDefaults.buttonColors(containerColor = FestivalCoral),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("launch_free_roam_button")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_free_roam).uppercase(), style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = { onLaunchDrive(DriveMode.CIRCUIT_TRIAL, null) },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("launch_circuit_button")
                        ) {
                            Icon(Icons.Filled.Flag, contentDescription = null, tint = EmeraldDrift, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("CIRCUIT", style = MaterialTheme.typography.labelLarge, color = EmeraldDrift)
                        }
                    }
                }
            }
        }

        // Quick Stats Strip
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "TOP SPEED",
                    value = "${data.profile.topSpeedKmh} km/h",
                    accent = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "EVENT STARS",
                    value = "$totalStars / $maxStars ★",
                    accent = SolarGold,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "BOARDS",
                    value = "${data.profile.boardsSmashed} / ${HorizonWorldMap.bonusBoards.size}",
                    accent = FestivalCoral,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Quick Shortcuts
        item {
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
                Text(
                    text = "QUICK FESTIVAL PLAYLIST",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onLaunchDrive(DriveMode.FREE_ROAM, "hairpin_drift") },
                        colors = ButtonDefaults.buttonColors(containerColor = HorizonSurfaceHigh),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("DRIFT CANYON", style = MaterialTheme.typography.labelLarge, color = FestivalCoral)
                    }
                    Button(
                        onClick = onOpenGarage,
                        colors = ButtonDefaults.buttonColors(containerColor = HorizonSurfaceHigh),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("TUNE GARAGE", style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun DriverProfileTab(
    data: FestivalDashboardData,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

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
                    .clip(RoundedCornerShape(18.dp))
                    .background(HorizonCarbon)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(FestivalCoral.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = FestivalCoral, modifier = Modifier.size(28.dp))
                    }
                    Column {
                        Text(data.profile.displayName, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                        if (userEmail.isNotBlank()) {
                            Text(userEmail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Surface(
                    color = EmeraldDrift.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, tint = EmeraldDrift, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Cloud Garage & Horizon Records Synced",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldDrift
                        )
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("DRIFT SCORE", "${data.profile.totalDriftScore}", FestivalCoral, Modifier.weight(1f))
                    MetricTile("CARS OWNED", "${data.garage.size} / ${HorizonCatalog.cars.size}", ElectricCyan, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricTile("PEAK SPEED", "${data.profile.topSpeedKmh} km/h", SolarGold, Modifier.weight(1f))
                    MetricTile("TOTAL XP", "${data.profile.xp}", EmeraldDrift, Modifier.weight(1f))
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    signOutFromHorizon(credentialManager, scope)
                },
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = FestivalCoral)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_sign_out).uppercase(), style = MaterialTheme.typography.labelLarge, color = FestivalCoral)
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HorizonCarbon)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
    }
}

@Composable
private fun tabLabel(tab: HubTab): String = when (tab) {
    HubTab.FESTIVAL -> stringResource(R.string.nav_hub)
    HubTab.GARAGE -> stringResource(R.string.nav_garage)
    HubTab.EVENTS -> stringResource(R.string.nav_events)
    HubTab.DRIVER -> stringResource(R.string.nav_driver)
}

private fun tabIcon(tab: HubTab, selected: Boolean) = when (tab) {
    HubTab.FESTIVAL -> if (selected) Icons.Filled.Explore else Icons.Outlined.Explore
    HubTab.GARAGE -> if (selected) Icons.Filled.DirectionsCar else Icons.Outlined.DirectionsCar
    HubTab.EVENTS -> if (selected) Icons.Filled.EmojiEvents else Icons.Outlined.EmojiEvents
    HubTab.DRIVER -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
}
