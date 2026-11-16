package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JarvisColorScheme = darkColorScheme(
    primary = ArcCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = HudPanelHighlight,
    onPrimaryContainer = ArcCyanSoft,
    secondary = ArcBlue,
    onSecondary = IceWhite,
    secondaryContainer = DeepHudSurface,
    onSecondaryContainer = IceWhite,
    tertiary = AmberAlert,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = IceWhite,
    surface = DeepHudSurface,
    onSurface = IceWhite,
    surfaceVariant = HudPanelElevated,
    onSurfaceVariant = SteelMuted,
    outline = HudBorderCyan,
    outlineVariant = ColorTokens.OutlineDim,
    error = CrimsonAlert,
    onError = ObsidianVoid,
)

private object ColorTokens {
    val OutlineDim = HudBorderCyan.copy(alpha = 0.45f)
}

private val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun JarvisTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        shapes = JarvisShapes,
        content = content,
    )
}
