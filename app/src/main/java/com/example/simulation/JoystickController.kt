package com.example.simulation

import com.example.data.model.SimulationMode
import com.example.service.MockLocationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class JoystickState(
    val isEngaged: Boolean = false,
    val angleDegrees: Float = 0f, // 0 = North, 90 = East, 180 = South, 270 = West
    val intensity: Float = 0f, // 0.0 to 1.0
    val maxSpeedKmh: Float = 25f
)

class JoystickController(
    private val engine: MockLocationEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _joystickState = MutableStateFlow(JoystickState())
    val joystickState: StateFlow<JoystickState> = _joystickState.asStateFlow()

    private var movementJob: Job? = null

    companion object {
        const val UPDATE_INTERVAL_MS = 250L // 4 Hz for responsive joystick movement
        const val EARTH_RADIUS_METERS = 6371000.0
    }

    fun updateStickVector(angleDeg: Float, intensity: Float) {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        val normalizedAngle = (angleDeg % 360 + 360) % 360
        val isMoving = clampedIntensity > 0.05f

        _joystickState.value = _joystickState.value.copy(
            angleDegrees = normalizedAngle,
            intensity = clampedIntensity,
            isEngaged = isMoving
        )

        if (isMoving && movementJob == null) {
            startMovementLoop()
        } else if (!isMoving && movementJob != null) {
            stopMovementLoop()
        }
    }

    fun setMaxSpeed(speedKmh: Float) {
        _joystickState.value = _joystickState.value.copy(maxSpeedKmh = speedKmh)
    }

    private fun startMovementLoop() {
        movementJob?.cancel()
        movementJob = scope.launch {
            val intervalSec = UPDATE_INTERVAL_MS / 1000.0
            while (isActive && _joystickState.value.isEngaged) {
                val state = _joystickState.value
                val engineState = engine.engineState.value

                val currentSpeedKmh = state.maxSpeedKmh * state.intensity
                val speedMps = (currentSpeedKmh * 1000.0) / 3600.0
                val distanceMeters = speedMps * intervalSec

                val nextPoint = computeDestinationPoint(
                    lat = engineState.currentLat,
                    lng = engineState.currentLng,
                    distanceMeters = distanceMeters,
                    bearingDeg = state.angleDegrees.toDouble()
                )

                if (!engineState.isSpoofing) {
                    engine.startSpoofing(
                        initialLat = nextPoint.first,
                        initialLng = nextPoint.second,
                        altitude = engineState.altitude,
                        speedKmh = currentSpeedKmh,
                        bearing = state.angleDegrees,
                        mode = SimulationMode.JOYSTICK
                    )
                } else {
                    engine.setSimulationMode(SimulationMode.JOYSTICK)
                    engine.updateCoordinates(
                        lat = nextPoint.first,
                        lng = nextPoint.second,
                        altitude = engineState.altitude,
                        speedKmh = currentSpeedKmh,
                        bearing = state.angleDegrees
                    )
                }

                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopMovementLoop() {
        movementJob?.cancel()
        movementJob = null
        val engineState = engine.engineState.value
        if (engineState.isSpoofing) {
            engine.updateCoordinates(
                lat = engineState.currentLat,
                lng = engineState.currentLng,
                speedKmh = 0f,
                bearing = engineState.bearing
            )
        }
    }

    private fun computeDestinationPoint(
        lat: Double,
        lng: Double,
        distanceMeters: Double,
        bearingDeg: Double
    ): Pair<Double, Double> {
        val distRatio = distanceMeters / EARTH_RADIUS_METERS
        val bearingRad = Math.toRadians(bearingDeg)
        val latRad = Math.toRadians(lat)
        val lngRad = Math.toRadians(lng)

        val newLatRad = asin(
            sin(latRad) * cos(distRatio) +
                    cos(latRad) * sin(distRatio) * cos(bearingRad)
        )
        val newLngRad = lngRad + atan2(
            sin(bearingRad) * sin(distRatio) * cos(latRad),
            cos(distRatio) - sin(latRad) * sin(newLatRad)
        )

        return Pair(Math.toDegrees(newLatRad), Math.toDegrees(newLngRad))
    }
}
