package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransportMode
import com.example.map.InteractiveMapView
import com.example.ui.MainViewModel
import com.example.ui.components.SaveRouteDialog
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.SecondaryCyan
import com.example.ui.theme.SuccessGreen
import java.util.Locale

@Composable
fun RouteSimulationScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val engineState by viewModel.engineState.collectAsState()
    val routeState by viewModel.routeState.collectAsState()
    val waypoints by viewModel.routeWaypoints.collectAsState()
    val selectedMode by viewModel.selectedTransportMode.collectAsState()
    val customSpeed by viewModel.customSpeedKmh.collectAsState()

    val mapLat by viewModel.mapCenterLat.collectAsState()
    val mapLng by viewModel.mapCenterLng.collectAsState()
    val mapZoom by viewModel.mapZoom.collectAsState()

    var showSaveRouteDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Upper Half: Interactive Route Map Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
        ) {
            InteractiveMapView(
                centerLat = mapLat,
                centerLng = mapLng,
                zoomLevel = mapZoom,
                mockLat = if (engineState.isSpoofing) engineState.currentLat else null,
                mockLng = if (engineState.isSpoofing) engineState.currentLng else null,
                mockBearing = engineState.bearing,
                isSpoofing = engineState.isSpoofing,
                waypoints = waypoints,
                onMapMoved = { newLat, newLng, newZoom ->
                    viewModel.updateMapViewport(newLat, newLng, newZoom)
                },
                onMapTapped = { tappedLat, tappedLng ->
                    viewModel.updateMapViewport(tappedLat, tappedLng, mapZoom)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Add waypoint button overlay on map
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .shadow(8.dp, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .clickable { viewModel.addRouteWaypoint(mapLat, mapLng) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("add_waypoint_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Waypoint",
                        tint = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Waypoint",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Stats Pill in top left
            if (waypoints.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${waypoints.size} Stops • ${String.format(Locale.US, "%.2f km", routeState.totalDistanceMeters / 1000.0)}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }

        // Lower Half: Route Controller & Waypoints List
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Playback & Timeline Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Progress bar & time
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (routeState.isPlaying) "Simulating Route..." else "Simulation Paused",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (routeState.isPlaying) SuccessGreen else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${(routeState.progress * 100).toInt()}% (${String.format(Locale.US, "%.1f", routeState.currentDistanceTraveledMeters / 1000.0)} / ${String.format(Locale.US, "%.1f km", routeState.totalDistanceMeters / 1000.0)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Scrubber Slider
                            Slider(
                                value = routeState.progress,
                                onValueChange = { viewModel.seekRouteSimulation(it) },
                                modifier = Modifier.fillMaxWidth().testTag("route_progress_slider")
                            )

                            // Transport Modes Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TransportMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = selectedMode == mode,
                                        onClick = { viewModel.setTransportMode(mode) },
                                        leadingIcon = {
                                            Icon(mode.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                        },
                                        label = { Text("${mode.label} (${mode.defaultSpeedKmh.toInt()} km/h)") }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Playback Buttons & Switches
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Repeat, contentDescription = "Loop", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Loop", style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = routeState.isLooping,
                                        onCheckedChange = { viewModel.toggleRouteLooping(it) },
                                        modifier = Modifier.testTag("loop_switch")
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (waypoints.isNotEmpty()) {
                                        IconButton(
                                            onClick = { showSaveRouteDialog = true },
                                            modifier = Modifier.testTag("save_route_button")
                                        ) {
                                            Icon(Icons.Default.Bookmark, contentDescription = "Save Route", tint = PrimaryBlue)
                                        }
                                    }

                                    if (routeState.isPlaying) {
                                        Button(
                                            onClick = { viewModel.pauseRouteSimulation() },
                                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("pause_route_button")
                                        ) {
                                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pause")
                                        }
                                    } else {
                                        Button(
                                            onClick = { viewModel.startRouteSimulation() },
                                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                            shape = RoundedCornerShape(12.dp),
                                            enabled = waypoints.size >= 2,
                                            modifier = Modifier.testTag("start_route_button")
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Start")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Start Route")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Waypoint List Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Route Waypoints (${waypoints.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        if (waypoints.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearRouteWaypoints() },
                                modifier = Modifier.testTag("clear_waypoints_button")
                            ) {
                                Text("Clear All", color = ErrorRed)
                            }
                        }
                    }
                }

                // Waypoint items
                if (waypoints.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Route,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No waypoints added yet",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Pan map to your starting location and tap 'Add Waypoint'",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(waypoints) { index, pt ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.updateMapViewport(pt.latitude, pt.longitude, 16f)
                                },
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (index) {
                                                0 -> SuccessGreen
                                                waypoints.size - 1 -> ErrorRed
                                                else -> PrimaryBlue
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (index == 0) "Start Point" else if (index == waypoints.size - 1) "Destination" else "Waypoint ${index + 1}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.5f, %.5f", pt.latitude, pt.longitude),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.removeRouteWaypoint(index) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove waypoint",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveRouteDialog) {
        SaveRouteDialog(
            waypointCount = waypoints.size,
            distanceMeters = routeState.totalDistanceMeters,
            onDismiss = { showSaveRouteDialog = false },
            onSave = { title, desc ->
                viewModel.saveCurrentRoute(title, desc)
                showSaveRouteDialog = false
            }
        )
    }
}
