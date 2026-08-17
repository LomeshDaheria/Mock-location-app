package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 15.0,
    val name: String = "",
    val address: String = ""
)

enum class MockProviderStatus {
    IDLE,
    ACTIVE,
    PERMISSION_REQUIRED,
    ERROR
}

enum class SimulationMode {
    FIXED,
    ROUTE,
    JOYSTICK
}

enum class TransportMode(
    val label: String,
    val defaultSpeedKmh: Float,
    val icon: ImageVector
) {
    WALK("Walk", 5f, Icons.Default.DirectionsWalk),
    RUN("Run", 12f, Icons.Default.DirectionsRun),
    BIKE("Bicycle", 20f, Icons.Default.DirectionsBike),
    CAR("City Car", 45f, Icons.Default.DirectionsCar),
    HIGHWAY("Highway", 100f, Icons.Default.Speed),
    AIR("Flight", 450f, Icons.Default.Flight)
}

data class MockEngineState(
    val isSpoofing: Boolean = false,
    val currentLat: Double = 37.774929, // Default SF
    val currentLng: Double = -122.419416,
    val altitude: Double = 15.0,
    val speedKmh: Float = 0f,
    val bearing: Float = 0f,
    val accuracyMeters: Float = 3.5f,
    val status: MockProviderStatus = MockProviderStatus.IDLE,
    val errorMessage: String? = null,
    val lastUpdateTimestamp: Long = 0L,
    val simulationMode: SimulationMode = SimulationMode.FIXED,
    val isGpsJitterEnabled: Boolean = false,
    val isRouteLooping: Boolean = true,
    val routeProgress: Float = 0f,
    val currentWaypointIndex: Int = 0,
    val totalWaypoints: Int = 0
)

data class PresetLocation(
    val name: String,
    val city: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 20.0,
    val category: String = "Popular"
)
