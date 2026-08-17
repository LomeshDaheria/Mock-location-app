package com.example.simulation

import com.example.data.model.GeoPoint
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RouteSimulationStatus(
    val isPlaying: Boolean = false,
    val progress: Float = 0f, // 0.0 to 1.0
    val currentDistanceTraveledMeters: Double = 0.0,
    val totalDistanceMeters: Double = 0.0,
    val currentSpeedKmh: Float = 20f,
    val currentSegmentIndex: Int = 0,
    val totalSegments: Int = 0,
    val isLooping: Boolean = true,
    val waypoints: List<GeoPoint> = emptyList()
)

class RouteSimulator(
    private val engine: MockLocationEngine,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _status = MutableStateFlow(RouteSimulationStatus())
    val status: StateFlow<RouteSimulationStatus> = _status.asStateFlow()

    private var simulationJob: Job? = null
    private var waypoints: List<GeoPoint> = emptyList()

    companion object {
        const val EARTH_RADIUS_METERS = 6371000.0
        const val UPDATE_INTERVAL_MS = 500L // 2 Hz updates for super smooth movement
    }

    fun setWaypoints(points: List<GeoPoint>, defaultSpeedKmh: Float = 20f) {
        waypoints = points
        val totalDist = calculateTotalDistance(points)
        _status.value = _status.value.copy(
            waypoints = points,
            totalDistanceMeters = totalDist,
            currentSpeedKmh = defaultSpeedKmh,
            currentDistanceTraveledMeters = 0.0,
            progress = 0f,
            totalSegments = if (points.size > 1) points.size - 1 else 0
        )
    }

    fun setSpeed(speedKmh: Float) {
        _status.value = _status.value.copy(currentSpeedKmh = speedKmh)
    }

    fun setLooping(loop: Boolean) {
        _status.value = _status.value.copy(isLooping = loop)
    }

    fun startOrResume(autoStartEngine: Boolean = true) {
        if (waypoints.size < 2) return

        if (autoStartEngine && !engine.engineState.value.isSpoofing) {
            val startPoint = getPointAtDistance(_status.value.currentDistanceTraveledMeters)
            engine.startSpoofing(
                initialLat = startPoint.point.latitude,
                initialLng = startPoint.point.longitude,
                altitude = startPoint.point.altitude,
                speedKmh = _status.value.currentSpeedKmh,
                bearing = startPoint.bearing,
                mode = SimulationMode.ROUTE
            )
        } else {
            engine.setSimulationMode(SimulationMode.ROUTE)
        }

        _status.value = _status.value.copy(isPlaying = true)

        simulationJob?.cancel()
        simulationJob = scope.launch {
            val updateIntervalSec = UPDATE_INTERVAL_MS / 1000.0
            while (isActive && _status.value.isPlaying) {
                val current = _status.value
                if (current.totalDistanceMeters <= 0.0) break

                val speedMps = (current.currentSpeedKmh * 1000.0) / 3600.0
                val distanceStep = speedMps * updateIntervalSec
                var newDist = current.currentDistanceTraveledMeters + distanceStep

                if (newDist >= current.totalDistanceMeters) {
                    if (current.isLooping) {
                        newDist %= current.totalDistanceMeters
                    } else {
                        newDist = current.totalDistanceMeters
                        _status.value = _status.value.copy(
                            currentDistanceTraveledMeters = newDist,
                            progress = 1.0f,
                            isPlaying = false
                        )
                        break
                    }
                }

                val interpolated = getPointAtDistance(newDist)
                val newProgress = (newDist / current.totalDistanceMeters).toFloat().coerceIn(0f, 1f)

                _status.value = _status.value.copy(
                    currentDistanceTraveledMeters = newDist,
                    progress = newProgress,
                    currentSegmentIndex = interpolated.segmentIndex
                )

                engine.updateCoordinates(
                    lat = interpolated.point.latitude,
                    lng = interpolated.point.longitude,
                    altitude = interpolated.point.altitude,
                    speedKmh = current.currentSpeedKmh,
                    bearing = interpolated.bearing
                )
                engine.updateRouteProgress(
                    progress = newProgress,
                    currentIdx = interpolated.segmentIndex,
                    total = current.totalSegments
                )

                delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    fun pause() {
        simulationJob?.cancel()
        simulationJob = null
        _status.value = _status.value.copy(isPlaying = false)
    }

    fun seekToProgress(progressFraction: Float) {
        val clamped = progressFraction.coerceIn(0f, 1f)
        val total = _status.value.totalDistanceMeters
        val newDist = total * clamped
        val interpolated = getPointAtDistance(newDist)

        _status.value = _status.value.copy(
            progress = clamped,
            currentDistanceTraveledMeters = newDist,
            currentSegmentIndex = interpolated.segmentIndex
        )

        engine.updateCoordinates(
            lat = interpolated.point.latitude,
            lng = interpolated.point.longitude,
            altitude = interpolated.point.altitude,
            speedKmh = if (_status.value.isPlaying) _status.value.currentSpeedKmh else 0f,
            bearing = interpolated.bearing
        )
    }

    private data class InterpolatedResult(
        val point: GeoPoint,
        val bearing: Float,
        val segmentIndex: Int
    )

    private fun getPointAtDistance(targetDist: Double): InterpolatedResult {
        if (waypoints.isEmpty()) return InterpolatedResult(GeoPoint(0.0, 0.0), 0f, 0)
        if (waypoints.size == 1) return InterpolatedResult(waypoints[0], 0f, 0)

        var accumulated = 0.0
        for (i in 0 until waypoints.size - 1) {
            val p1 = waypoints[i]
            val p2 = waypoints[i + 1]
            val segmentDist = haversineDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)

            if (accumulated + segmentDist >= targetDist || i == waypoints.size - 2) {
                val remainingDist = (targetDist - accumulated).coerceAtLeast(0.0)
                val fraction = if (segmentDist > 0) (remainingDist / segmentDist).coerceIn(0.0, 1.0) else 0.0
                val lat = p1.latitude + (p2.latitude - p1.latitude) * fraction
                val lng = p1.longitude + (p2.longitude - p1.longitude) * fraction
                val alt = p1.altitude + (p2.altitude - p1.altitude) * fraction
                val bearing = calculateBearing(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
                return InterpolatedResult(GeoPoint(lat, lng, alt), bearing, i)
            }
            accumulated += segmentDist
        }
        val last = waypoints.last()
        return InterpolatedResult(last, 0f, waypoints.size - 1)
    }

    private fun calculateTotalDistance(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until points.size - 1) {
            sum += haversineDistance(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            )
        }
        return sum
    }

    fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val y = sin(dLon) * cos(rLat2)
        val x = cos(rLat1) * sin(rLat2) - sin(rLat1) * cos(rLat2) * cos(dLon)
        val bearing = (Math.toDegrees(atan2(y, x)) + 360) % 360
        return bearing.toFloat()
    }
}
