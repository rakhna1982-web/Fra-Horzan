package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val HorizonDarkColorScheme = darkColorScheme(
    primary = FestivalCoral,
    onPrimary = TextPrimary,
    primaryContainer = FestivalCoralDark,
    onPrimaryContainer = TextPrimary,
    secondary = ElectricCyan,
    onSecondary = HorizonObsidian,
    secondaryContainer = ElectricCyanDark,
    onSecondaryContainer = ElectricCyan,
    tertiary = SolarGold,
    onTertiary = HorizonObsidian,
    background = HorizonObsidian,
    onBackground = TextPrimary,
    surface = HorizonCarbon,
    onSurface = TextPrimary,
    surfaceVariant = HorizonSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = HorizonSurfaceOutline,
    error = CrimsonAlert,
    onError = TextPrimary
)

private val HorizonShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

@Composable
fun ApexHorizonTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HorizonDarkColorScheme,
        typography = Typography,
        shapes = HorizonShapes,
        content = content
    )
}
