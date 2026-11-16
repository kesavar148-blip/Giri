package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.ArcBlue
import com.example.ui.theme.ArcCyan
import com.example.ui.theme.ArcCyanSoft
import com.example.ui.theme.IceWhite
import com.example.ui.theme.NominalGreen
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SteelMuted
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class AssistantVisualState {
    IDLE,
    WAKE_READY,
    LISTENING,
    THINKING,
    SPEAKING,
}

@Composable
fun JarvisCoreVisualizer(
    visualState: AssistantVisualState,
    rmsLevel: Float,
    modifier: Modifier = Modifier,
    sizeDp: Dp = 188.dp,
    statusSubtitle: String = "",
    onCoreClick: (() -> Unit)? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_core")

    val slowOrbit by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "slow_orbit",
    )

    val fastOrbit by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fast_orbit",
    )

    val tickOrbit by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tick_orbit",
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.68f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "core_pulse",
    )

    val sonarExpand by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sonar_expand",
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave_phase",
    )

    val smoothRms by animateFloatAsState(
        targetValue = rmsLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 460f),
        label = "smooth_rms",
    )

    val coreColor by animateColorAsState(
        targetValue = when (visualState) {
            AssistantVisualState.IDLE -> ArcCyan
            AssistantVisualState.WAKE_READY -> ArcBlue
            AssistantVisualState.LISTENING -> NominalGreen
            AssistantVisualState.THINKING -> AmberAlert
            AssistantVisualState.SPEAKING -> ArcCyanSoft
        },
        animationSpec = tween(250),
        label = "core_color",
    )

    val statusText = when (visualState) {
        AssistantVisualState.IDLE -> "STANDBY"
        AssistantVisualState.WAKE_READY -> "WAKE ARMED"
        AssistantVisualState.LISTENING -> "LISTENING"
        AssistantVisualState.THINKING -> "PROCESSING"
        AssistantVisualState.SPEAKING -> "SPEAKING"
    }

    val interactiveModifier = if (onCoreClick != null) {
        Modifier
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClick = onCoreClick,
            )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .then(interactiveModifier)
            .semantics {
                contentDescription = "JARVIS circular visualizer, status $statusText"
            }
            .testTag("jarvis_core_visualizer"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(sizeDp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension / 2f

            // 1. Deep ambient reactor aura
            val glowAlpha = when (visualState) {
                AssistantVisualState.LISTENING -> 0.38f + smoothRms * 0.25f
                AssistantVisualState.SPEAKING -> 0.40f * pulse
                AssistantVisualState.THINKING -> 0.34f * pulse
                AssistantVisualState.WAKE_READY -> 0.26f * pulse
                AssistantVisualState.IDLE -> 0.22f * pulse
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = glowAlpha),
                        ArcBlue.copy(alpha = glowAlpha * 0.45f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = maxRadius,
                ),
                radius = maxRadius,
                center = center,
            )

            // 2. Expanding HUD pulse wave during active voice/thinking states
            if (visualState != AssistantVisualState.IDLE) {
                val waveRadius = maxRadius * (0.46f + sonarExpand * 0.48f)
                val waveAlpha = (1f - sonarExpand) * 0.38f
                drawCircle(
                    color = coreColor.copy(alpha = waveAlpha),
                    radius = waveRadius,
                    center = center,
                    style = Stroke(width = (maxRadius * 0.015f).coerceAtLeast(1.5f)),
                )
            }

            // 3. Outer hairline calibration ring + rotating azimuth tick marks
            val tickOuterRadius = maxRadius * 0.94f
            drawCircle(
                color = coreColor.copy(alpha = 0.22f),
                radius = tickOuterRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )

            val tickCount = 60
            for (i in 0 until tickCount) {
                val isMajor = i % 5 == 0
                val angleRad = (i * (360f / tickCount) + tickOrbit) * (PI / 180f)
                val innerTick = tickOuterRadius - maxRadius * (if (isMajor) 0.055f else 0.026f)
                drawLine(
                    color = coreColor.copy(alpha = if (isMajor) 0.65f else 0.26f),
                    start = Offset(
                        x = center.x + (innerTick * cos(angleRad)).toFloat(),
                        y = center.y + (innerTick * sin(angleRad)).toFloat(),
                    ),
                    end = Offset(
                        x = center.x + (tickOuterRadius * cos(angleRad)).toFloat(),
                        y = center.y + (tickOuterRadius * sin(angleRad)).toFloat(),
                    ),
                    strokeWidth = if (isMajor) 1.8.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // 4. Outer segmented Stark-style HUD ring
            val outerRadius = maxRadius * 0.83f
            val outerStroke = (maxRadius * 0.036f).coerceAtLeast(2.5f)
            val outerSpeedAngle = if (visualState == AssistantVisualState.THINKING) {
                (slowOrbit * 2.6f) % 360f
            } else {
                slowOrbit
            }
            for (i in 0 until 4) {
                val startDeg = outerSpeedAngle + i * 90f + 10f
                drawArc(
                    color = coreColor.copy(alpha = 0.82f),
                    startAngle = startDeg,
                    sweepAngle = 64f,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = Size(outerRadius * 2f, outerRadius * 2f),
                    style = Stroke(width = outerStroke, cap = StrokeCap.Round),
                )
                // Glowing orbital node at leading edge
                val nodeRad = (startDeg + 64f) * (PI / 180f)
                drawCircle(
                    color = IceWhite.copy(alpha = 0.9f),
                    radius = outerStroke * 0.55f,
                    center = Offset(
                        x = center.x + (outerRadius * cos(nodeRad)).toFloat(),
                        y = center.y + (outerRadius * sin(nodeRad)).toFloat(),
                    ),
                )
            }

            // 5. Counter-rotating mid telemetry arcs
            val midRadius = maxRadius * 0.71f
            val midStroke = (maxRadius * 0.028f).coerceAtLeast(2f)
            val midAngleBase = if (visualState == AssistantVisualState.THINKING) {
                (fastOrbit * 1.8f) % 360f
            } else {
                fastOrbit
            }
            for (i in 0 until 6) {
                drawArc(
                    color = ArcBlue.copy(alpha = 0.88f),
                    startAngle = midAngleBase + i * 60f + 8f,
                    sweepAngle = 36f,
                    useCenter = false,
                    topLeft = Offset(center.x - midRadius, center.y - midRadius),
                    size = Size(midRadius * 2f, midRadius * 2f),
                    style = Stroke(width = midStroke, cap = StrokeCap.Round),
                )
            }

            // 6. Radial voice waveform visualizer bars (48 harmonic bars)
            val barCount = 48
            val baseBarStart = maxRadius * 0.52f
            for (i in 0 until barCount) {
                val angleRad = (i * (360f / barCount) + slowOrbit * 0.35f) * (PI / 180f)
                val harmonic = (sin(wavePhase + i * 0.48f) * 0.5f + 0.5f)
                val barIntensity = when (visualState) {
                    AssistantVisualState.LISTENING -> (0.30f + smoothRms * 0.70f) * (0.40f + 0.60f * harmonic)
                    AssistantVisualState.SPEAKING -> 0.35f + 0.65f * harmonic
                    AssistantVisualState.THINKING -> 0.28f + 0.52f * sin(wavePhase * 1.5f + i * 0.35f).toFloat().coerceAtLeast(0f)
                    AssistantVisualState.WAKE_READY -> 0.18f + 0.18f * harmonic
                    AssistantVisualState.IDLE -> 0.14f + 0.10f * harmonic
                }
                val barLen = maxRadius * (0.035f + 0.135f * barIntensity)
                val start = Offset(
                    x = center.x + (baseBarStart * cos(angleRad)).toFloat(),
                    y = center.y + (baseBarStart * sin(angleRad)).toFloat(),
                )
                val end = Offset(
                    x = center.x + ((baseBarStart + barLen) * cos(angleRad)).toFloat(),
                    y = center.y + ((baseBarStart + barLen) * sin(angleRad)).toFloat(),
                )
                drawLine(
                    color = coreColor.copy(alpha = 0.42f + 0.55f * barIntensity),
                    start = start,
                    end = end,
                    strokeWidth = (maxRadius * 0.018f).coerceAtLeast(1.8f),
                    cap = StrokeCap.Round,
                )
            }

            // 7. Inner arc reactor housing + crosshair reticle ticks
            val innerRadius = maxRadius * 0.43f
            drawCircle(
                color = ObsidianVoid.copy(alpha = 0.75f),
                radius = innerRadius,
                center = center,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.24f * pulse),
                        ArcBlue.copy(alpha = 0.08f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = innerRadius,
                ),
                radius = innerRadius * 0.94f,
                center = center,
            )
            drawCircle(
                color = coreColor.copy(alpha = 0.90f),
                radius = innerRadius,
                center = center,
                style = Stroke(width = (maxRadius * 0.022f).coerceAtLeast(2f)),
            )

            // Cardinal reticle markers inside inner ring
            for (i in 0 until 4) {
                val rad = (i * 90f) * (PI / 180f)
                val r1 = innerRadius * 0.84f
                val r2 = innerRadius * 0.98f
                drawLine(
                    color = IceWhite.copy(alpha = 0.65f),
                    start = Offset(center.x + (r1 * cos(rad)).toFloat(), center.y + (r1 * sin(rad)).toFloat()),
                    end = Offset(center.x + (r2 * cos(rad)).toFloat(), center.y + (r2 * sin(rad)).toFloat()),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = coreColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (statusSubtitle.isNotBlank()) {
                Text(
                    text = statusSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = SteelMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
