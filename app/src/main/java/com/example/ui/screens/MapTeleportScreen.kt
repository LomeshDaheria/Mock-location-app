package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WhereToVote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import com.example.map.InteractiveMapView
import com.example.ui.MainViewModel
import com.example.ui.components.SaveLocationDialog
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.SecondaryCyan
import com.example.ui.theme.SuccessGreen
import java.util.Locale

@Composable
fun MapTeleportScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val engineState by viewModel.engineState.collectAsState()
    val mapLat by viewModel.mapCenterLat.collectAsState()
    val mapLng by viewModel.mapCenterLng.collectAsState()
    val mapZoom by viewModel.mapZoom.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var showSaveBookmarkDialog by remember { mutableStateOf(false) }
    var isTuningOpen by remember { mutableStateOf(false) }
    var nudgeStepMeters by remember { mutableDoubleStateOf(10.0) }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Interactive Map View
        InteractiveMapView(
            centerLat = mapLat,
            centerLng = mapLng,
            zoomLevel = mapZoom,
            mockLat = if (engineState.isSpoofing) engineState.currentLat else null,
            mockLng = if (engineState.isSpoofing) engineState.currentLng else null,
            mockBearing = engineState.bearing,
            isSpoofing = engineState.isSpoofing,
            onMapMoved = { newLat, newLng, newZoom ->
                viewModel.updateMapViewport(newLat, newLng, newZoom)
            },
            onMapTapped = { tappedLat, tappedLng ->
                viewModel.updateMapViewport(tappedLat, tappedLng, mapZoom)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Search & Presets Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter)
        ) {
            // Search Input Field
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Search location or lat, lng (e.g. 37.77, -122.41)") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = PrimaryBlue)
                        },
                        trailingIcon = {
                            if (isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("map_search_input")
                    )

                    // Autocomplete Dropdown list
                    AnimatedVisibility(
                        visible = searchResults.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            searchResults.forEach { result ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.jumpToLocation(result.latitude, result.longitude, 16f, teleportNow = false)
                                            viewModel.clearSearch()
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.WhereToVote,
                                        contentDescription = null,
                                        tint = SecondaryCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = result.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                            maxLines = 1
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.5f, %.5f", result.latitude, result.longitude),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Popular Presets Carousel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                viewModel.presets.take(6).forEach { preset ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                viewModel.jumpToLocation(preset.latitude, preset.longitude, 16f, teleportNow = false)
                            }
                            .shadow(2.dp, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(${preset.city})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. Floating Map Controls (Zoom + Recenter + Nudge on right side)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom In (+)
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .clickable { viewModel.updateMapViewport(mapLat, mapLng, (mapZoom + 1f).coerceAtMost(18f)) }
                    .testTag("zoom_in_button"),
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Zoom Out (-)
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .clickable { viewModel.updateMapViewport(mapLat, mapLng, (mapZoom - 1f).coerceAtLeast(2f)) }
                    .testTag("zoom_out_button"),
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            // Recenter on Mock Location
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .clickable {
                        if (engineState.isSpoofing) {
                            viewModel.updateMapViewport(engineState.currentLat, engineState.currentLng, 16f)
                        } else {
                            viewModel.updateMapViewport(mapLat, mapLng, 16f)
                        }
                    }
                    .testTag("recenter_button"),
                color = if (engineState.isSpoofing) SuccessGreen else MaterialTheme.colorScheme.surface,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Center",
                        tint = if (engineState.isSpoofing) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Toggle Fine-tuning sliders
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .clickable { isTuningOpen = !isTuningOpen }
                    .testTag("toggle_tuning_button"),
                color = if (isTuningOpen) PrimaryBlue else MaterialTheme.colorScheme.surface,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Tune",
                        tint = if (isTuningOpen) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // 4. Bottom Teleport & Control Panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            // Fine-tuning drawer (Altitude, Jitter, Nudge)
            AnimatedVisibility(
                visible = isTuningOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .shadow(6.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Precision & GPS Emulation",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "GPS Jitter",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Switch(
                                    checked = engineState.isGpsJitterEnabled,
                                    onCheckedChange = { viewModel.toggleGpsJitter(it) },
                                    modifier = Modifier.testTag("jitter_switch")
                                )
                            }
                        }

                        // Precision Nudge Controls
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Precision Nudge (Step: ${nudgeStepMeters.toInt()}m)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(1.0, 10.0, 100.0).forEach { step ->
                                    FilterChip(
                                        selected = nudgeStepMeters == step,
                                        onClick = { nudgeStepMeters = step },
                                        label = { Text("${step.toInt()}m") }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { viewModel.nudgeCoordinates("WEST", nudgeStepMeters) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "West")
                                }
                                Column {
                                    IconButton(
                                        onClick = { viewModel.nudgeCoordinates("NORTH", nudgeStepMeters) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "North")
                                    }
                                    IconButton(
                                        onClick = { viewModel.nudgeCoordinates("SOUTH", nudgeStepMeters) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "South")
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.nudgeCoordinates("EAST", nudgeStepMeters) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "East")
                                }
                            }
                        }

                        // Altitude Slider
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Altitude: ${engineState.altitude.toInt()} meters", style = MaterialTheme.typography.labelSmall)
                        }
                        Slider(
                            value = engineState.altitude.toFloat(),
                            onValueChange = { viewModel.updateAltitude(it.toDouble()) },
                            valueRange = 0f..2500f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Main Teleport / Action Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (engineState.isSpoofing) "Spoofing Target Active" else "Crosshair Target",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (engineState.isSpoofing) SuccessGreen else PrimaryBlue
                            )
                            Text(
                                text = String.format(Locale.US, "%.6f, %.6f", mapLat, mapLng),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        IconButton(
                            onClick = { showSaveBookmarkDialog = true },
                            modifier = Modifier.testTag("bookmark_button")
                        ) {
                            Icon(
                                Icons.Default.BookmarkAdd,
                                contentDescription = "Save Bookmark",
                                tint = PrimaryBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (engineState.isSpoofing) {
                            Button(
                                onClick = { viewModel.stopSpoofing() },
                                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("stop_spoofing_button")
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Spoofing", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.teleportToCoordinates(mapLat, mapLng, "Teleported Point")
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (engineState.isSpoofing) PrimaryBlue else SuccessGreen
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(if (engineState.isSpoofing) 1f else 2f)
                                .height(48.dp)
                                .testTag("teleport_button")
                        ) {
                            Icon(Icons.Default.WhereToVote, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (engineState.isSpoofing) "Update Position" else "Teleport Here",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveBookmarkDialog) {
        SaveLocationDialog(
            initialLat = mapLat,
            initialLng = mapLng,
            onDismiss = { showSaveBookmarkDialog = false },
            onSave = { name, note, tag, radius ->
                viewModel.saveLocationBookmark(name, note, mapLat, mapLng, tag, radius)
                showSaveBookmarkDialog = false
            }
        )
    }
}
