package com.example.service

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.data.model.MockEngineState
import com.example.data.model.MockProviderStatus
import com.example.data.model.SimulationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random

class MockLocationEngine(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val scope = CoroutineScope(Dispatchers.Default)
    private var spoofLoopJob: Job? = null

    private val _engineState = MutableStateFlow(MockEngineState())
    val engineState: StateFlow<MockEngineState> = _engineState.asStateFlow()

    private val providersToMock = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER
    )

    private var isProviderRegistered = false

    companion object {
        private const val TAG = "MockLocationEngine"
        private const val TICK_INTERVAL_MS = 1000L

        @Volatile
        private var INSTANCE: MockLocationEngine? = null

        fun getInstance(context: Context): MockLocationEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MockLocationEngine(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    fun startSpoofing(
        initialLat: Double = _engineState.value.currentLat,
        initialLng: Double = _engineState.value.currentLng,
        altitude: Double = _engineState.value.altitude,
        speedKmh: Float = _engineState.value.speedKmh,
        bearing: Float = _engineState.value.bearing,
        mode: SimulationMode = _engineState.value.simulationMode
    ): Boolean {
        val registered = registerTestProviders()
        if (!registered) {
            _engineState.value = _engineState.value.copy(
                isSpoofing = false,
                status = MockProviderStatus.PERMISSION_REQUIRED,
                errorMessage = "Mock location not allowed. Please select this app in Developer Options -> Select mock location app."
            )
            return false
        }

        _engineState.value = _engineState.value.copy(
            isSpoofing = true,
            currentLat = initialLat,
            currentLng = initialLng,
            altitude = altitude,
            speedKmh = speedKmh,
            bearing = bearing,
            status = MockProviderStatus.ACTIVE,
            errorMessage = null,
            simulationMode = mode,
            lastUpdateTimestamp = System.currentTimeMillis()
        )

        // Push initial point immediately
        pushLocationUpdate(initialLat, initialLng, altitude, speedKmh, bearing)

        startPeriodicBroadcastLoop()
        return true
    }

    fun updateCoordinates(
        lat: Double,
        lng: Double,
        altitude: Double = _engineState.value.altitude,
        speedKmh: Float = _engineState.value.speedKmh,
        bearing: Float = _engineState.value.bearing
    ) {
        _engineState.value = _engineState.value.copy(
            currentLat = lat,
            currentLng = lng,
            altitude = altitude,
            speedKmh = speedKmh,
            bearing = bearing,
            lastUpdateTimestamp = System.currentTimeMillis()
        )
        if (_engineState.value.isSpoofing) {
            pushLocationUpdate(lat, lng, altitude, speedKmh, bearing)
        }
    }

    fun setSimulationMode(mode: SimulationMode) {
        _engineState.value = _engineState.value.copy(simulationMode = mode)
    }

    fun setGpsJitterEnabled(enabled: Boolean) {
        _engineState.value = _engineState.value.copy(isGpsJitterEnabled = enabled)
    }

    fun setRouteLooping(looping: Boolean) {
        _engineState.value = _engineState.value.copy(isRouteLooping = looping)
    }

    fun updateRouteProgress(progress: Float, currentIdx: Int, total: Int) {
        _engineState.value = _engineState.value.copy(
            routeProgress = progress,
            currentWaypointIndex = currentIdx,
            totalWaypoints = total
        )
    }

    fun stopSpoofing() {
        spoofLoopJob?.cancel()
        spoofLoopJob = null
        removeTestProviders()

        _engineState.value = _engineState.value.copy(
            isSpoofing = false,
            status = MockProviderStatus.IDLE,
            speedKmh = 0f
        )
    }

    private fun registerTestProviders(): Boolean {
        var allSuccess = true
        for (provider in providersToMock) {
            try {
                try {
                    locationManager.removeTestProvider(provider)
                } catch (_: Exception) {
                    // Ignore if not present
                }

                locationManager.addTestProvider(
                    provider,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true,  // supportsAltitude
                    true,  // supportsSpeed
                    true,  // supportsBearing
                    Criteria.POWER_LOW,
                    Criteria.ACCURACY_FINE
                )
                locationManager.setTestProviderEnabled(provider, true)
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException registering test provider for $provider", e)
                allSuccess = false
            } catch (e: Exception) {
                Log.e(TAG, "Error registering test provider for $provider", e)
                allSuccess = false
            }
        }
        isProviderRegistered = allSuccess
        return allSuccess
    }

    private fun removeTestProviders() {
        for (provider in providersToMock) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                Log.d(TAG, "Failed to remove test provider $provider: ${e.message}")
            }
        }
        isProviderRegistered = false
    }

    private fun startPeriodicBroadcastLoop() {
        spoofLoopJob?.cancel()
        spoofLoopJob = scope.launch {
            while (isActive && _engineState.value.isSpoofing) {
                val state = _engineState.value
                val (effectiveLat, effectiveLng, accuracy) = if (state.isGpsJitterEnabled) {
                    val rng = java.util.concurrent.ThreadLocalRandom.current()
                    // Slight Gaussian drift (+- 1 to 3 meters) for realistic GPS noise simulation
                    val driftLat = (rng.nextGaussian() * 0.00002)
                    val driftLng = (rng.nextGaussian() * 0.00002)
                    val jitterAcc = (3.0f + rng.nextFloat() * 4.0f)
                    Triple(state.currentLat + driftLat, state.currentLng + driftLng, jitterAcc)
                } else {
                    Triple(state.currentLat, state.currentLng, state.accuracyMeters)
                }

                pushLocationUpdate(
                    effectiveLat,
                    effectiveLng,
                    state.altitude,
                    state.speedKmh,
                    state.bearing,
                    accuracy
                )

                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun pushLocationUpdate(
        lat: Double,
        lng: Double,
        alt: Double,
        speedKmh: Float,
        bearing: Float,
        accuracyMeters: Float = _engineState.value.accuracyMeters
    ) {
        val speedMps = (speedKmh * 1000f) / 3600f
        val now = System.currentTimeMillis()
        val elapsedNanos = SystemClock.elapsedRealtimeNanos()

        for (provider in providersToMock) {
            try {
                val mockLoc = Location(provider).apply {
                    latitude = lat
                    longitude = lng
                    altitude = alt
                    time = now
                    elapsedRealtimeNanos = elapsedNanos
                    accuracy = accuracyMeters
                    speed = speedMps
                    this.bearing = bearing

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        bearingAccuracyDegrees = 1.0f
                        verticalAccuracyMeters = 1.0f
                        speedAccuracyMetersPerSecond = 0.2f
                    }
                }
                locationManager.setTestProviderLocation(provider, mockLoc)
            } catch (e: SecurityException) {
                Log.w(TAG, "Mock permission lost during location broadcast", e)
                _engineState.value = _engineState.value.copy(
                    isSpoofing = false,
                    status = MockProviderStatus.PERMISSION_REQUIRED,
                    errorMessage = "Mock permission not granted in Developer options."
                )
                spoofLoopJob?.cancel()
                break
            } catch (e: Exception) {
                Log.e(TAG, "Error setting mock location on provider $provider", e)
            }
        }
    }

    fun testMockProviderStatus(): MockProviderStatus {
        return try {
            locationManager.addTestProvider(
                "test_probe",
                false, false, false, false, false, false, false,
                Criteria.POWER_LOW, Criteria.ACCURACY_COARSE
            )
            locationManager.removeTestProvider("test_probe")
            MockProviderStatus.ACTIVE
        } catch (e: SecurityException) {
            MockProviderStatus.PERMISSION_REQUIRED
        } catch (e: Exception) {
            MockProviderStatus.IDLE
        }
    }
}
