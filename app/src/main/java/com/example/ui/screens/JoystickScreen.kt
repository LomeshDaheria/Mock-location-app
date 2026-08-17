package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.MainViewModel
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.SecondaryCyan
import com.example.ui.theme.SuccessGreen
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun JoystickScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val engineState by viewModel.engineState.collectAsState()
    val joystickState by viewModel.joystickState.collectAsState()

    var customMaxSpeed by remember { mutableFloatStateOf(20f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // 1. Live Telemetry HUD Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Live Mock Location",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryBlue
                        )
                        Text(
                            text = String.format(Locale.US, "%.6f, %.6f", engineState.currentLat, engineState.currentLng),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    // Rotating Compass Heading
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Compass",
                            tint = if (joystickState.isEngaged) SecondaryCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(30.dp)
                                .rotate(if (joystickState.isEngaged) joystickState.angleDegrees else engineState.bearing)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatPill("Speed", "${(customMaxSpeed * joystickState.intensity).toInt()} km/h")
                    StatPill("Heading", "${joystickState.angleDegrees.toInt()}°")
                    StatPill("Altitude", "${engineState.altitude.toInt()} m")
                    StatPill("Accuracy", "${engineState.accuracyMeters.toInt()} m")
                }
            }
        }

        // 2. Virtual Joystick Analog Controller
        Box(
            modifier = Modifier
                .size(240.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .testTag("virtual_joystick_container"),
            contentAlignment = Alignment.Center
        ) {
            VirtualJoystickWidget(
                maxSpeedKmh = customMaxSpeed,
                onStickMoved = { angle, intensity ->
                    viewModel.joystickController.updateStickVector(angle, intensity)
                }
            )
        }

        // 3. Speed Mode Selector & Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Speed Preset Chips
            Text(
                text = "Max Movement Speed: ${customMaxSpeed.toInt()} km/h",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Pair("Walk (5 km/h)", 5f),
                    Pair("Run (12 km/h)", 12f),
                    Pair("Bike (25 km/h)", 25f),
                    Pair("Car (60 km/h)", 60f)
                ).forEach { (label, speed) ->
                    FilterChip(
                        selected = customMaxSpeed == speed,
                        onClick = {
                            customMaxSpeed = speed
                            viewModel.joystickController.setMaxSpeed(speed)
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Speed Fine Slider
            Slider(
                value = customMaxSpeed,
                onValueChange = {
                    customMaxSpeed = it
                    viewModel.joystickController.setMaxSpeed(it)
                },
                valueRange = 1f..150f,
                modifier = Modifier.fillMaxWidth().testTag("joystick_speed_slider")
            )

            // Stop Spoofing or Recenter buttons
            if (engineState.isSpoofing) {
                Button(
                    onClick = { viewModel.stopSpoofing() },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("joystick_stop_spoofing")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Spoofing Location", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun StatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun VirtualJoystickWidget(
    maxSpeedKmh: Float,
    onStickMoved: (angleDeg: Float, intensity: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadius = 85f // dp radius for stick bounds

    val primaryColor = PrimaryBlueLight
    val thumbColor = PrimaryBlue
    val ringColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val dragVector = offset - center
                        val dist = sqrt(dragVector.x * dragVector.x + dragVector.y * dragVector.y)
                        val maxPx = size.width / 2.6f
                        val clampedDist = min(dist, maxPx)
                        val angleRad = atan2(dragVector.y, dragVector.x)
                        thumbOffset = Offset(cos(angleRad) * clampedDist, sin(angleRad) * clampedDist)

                        // Calculate geographic angle (0 = North, 90 = East, 180 = South, 270 = West)
                        var angleDeg = (Math.toDegrees(angleRad.toDouble()) + 90).toFloat()
                        if (angleDeg < 0) angleDeg += 360f

                        val intensity = (clampedDist / maxPx).coerceIn(0f, 1f)
                        onStickMoved(angleDeg, intensity)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newOffset = thumbOffset + dragAmount
                        val dist = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                        val maxPx = size.width / 2.6f
                        val clampedDist = min(dist, maxPx)
                        val angleRad = atan2(newOffset.y, newOffset.x)
                        thumbOffset = Offset(cos(angleRad) * clampedDist, sin(angleRad) * clampedDist)

                        var angleDeg = (Math.toDegrees(angleRad.toDouble()) + 90).toFloat()
                        if (angleDeg < 0) angleDeg += 360f

                        val intensity = (clampedDist / maxPx).coerceIn(0f, 1f)
                        onStickMoved(angleDeg, intensity)
                    },
                    onDragEnd = {
                        thumbOffset = Offset.Zero
                        onStickMoved(0f, 0f)
                    },
                    onDragCancel = {
                        thumbOffset = Offset.Zero
                        onStickMoved(0f, 0f)
                    }
                )
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.width / 2.3f

        // Outer bounds circle
        drawCircle(color = ringColor, radius = outerRadius, center = center, style = Stroke(width = 3f))
        drawCircle(color = ringColor.copy(alpha = 0.15f), radius = outerRadius * 0.6f, center = center, style = Stroke(width = 1.5f))

        // Direction indicators (N, E, S, W)
        drawLine(color = ringColor, start = Offset(center.x, center.y - outerRadius), end = Offset(center.x, center.y - outerRadius + 14f), strokeWidth = 2f)
        drawLine(color = ringColor, start = Offset(center.x, center.y + outerRadius - 14f), end = Offset(center.x, center.y + outerRadius), strokeWidth = 2f)
        drawLine(color = ringColor, start = Offset(center.x - outerRadius, center.y), end = Offset(center.x - outerRadius + 14f, center.y), strokeWidth = 2f)
        drawLine(color = ringColor, start = Offset(center.x + outerRadius - 14f, center.y), end = Offset(center.x + outerRadius, center.y), strokeWidth = 2f)

        // Draw line from center to thumbstick
        if (thumbOffset != Offset.Zero) {
            drawLine(
                color = primaryColor.copy(alpha = 0.7f),
                start = center,
                end = center + thumbOffset,
                strokeWidth = 5f
            )
        }

        // Thumbstick knob
        val thumbCenter = center + thumbOffset
        drawCircle(color = primaryColor.copy(alpha = 0.3f), radius = 38f, center = thumbCenter)
        drawCircle(color = Color.White, radius = 30f, center = thumbCenter)
        drawCircle(color = thumbColor, radius = 24f, center = thumbCenter)
    }
}
