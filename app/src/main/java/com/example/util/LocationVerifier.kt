package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SatelliteInfo(
    val prn: Int,
    val snr: Float, // Signal-to-noise ratio in dB
    val elevation: Float, // Degrees 0-90
    val azimuth: Float, // Degrees 0-360
    val usedInFix: Boolean,
    val constellation: String = "GPS"
)

data class VerificationStatus(
    val isListening: Boolean = false,
    val hasReceivedFix: Boolean = false,
    val reportedLat: Double = 0.0,
    val reportedLng: Double = 0.0,
    val reportedAlt: Double = 0.0,
    val reportedAccuracyMeters: Float = 0.0f,
    val reportedSpeedMps: Float = 0.0f,
    val reportedBearingDeg: Float = 0.0f,
    val reportedProvider: String = "",
    val isMarkedMock: Boolean = false,
    val deltaMetersFromTarget: Double = 0.0,
    val fixTimestamp: Long = 0L,
    val satellites: List<SatelliteInfo> = emptyList()
)

class LocationVerifier(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _status = MutableStateFlow(VerificationStatus())
    val status: StateFlow<VerificationStatus> = _status.asStateFlow()

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                location.isMock
            } else {
                @Suppress("DEPRECATION")
                location.isFromMockProvider
            }

            _status.value = _status.value.copy(
                hasReceivedFix = true,
                reportedLat = location.latitude,
                reportedLng = location.longitude,
                reportedAlt = location.altitude,
                reportedAccuracyMeters = location.accuracy,
                reportedSpeedMps = location.speed,
                reportedBearingDeg = location.bearing,
                reportedProvider = location.provider ?: "GPS",
                isMarkedMock = isMock,
                fixTimestamp = location.time,
                satellites = generateMockSatellites()
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startVerification(targetLat: Double, targetLng: Double) {
        try {
            _status.value = _status.value.copy(
                isListening = true,
                satellites = generateMockSatellites()
            )
            val providers = locationManager.getProviders(true)
            if (providers.contains(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    500L,
                    0f,
                    locationListener
                )
            } else if (providers.contains(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    500L,
                    0f,
                    locationListener
                )
            }
        } catch (e: Exception) {
            _status.value = _status.value.copy(isListening = false)
        }
    }

    fun updateTargetComparison(targetLat: Double, targetLng: Double) {
        if (_status.value.hasReceivedFix) {
            val delta = haversineDistance(
                targetLat, targetLng,
                _status.value.reportedLat, _status.value.reportedLng
            )
            _status.value = _status.value.copy(deltaMetersFromTarget = delta)
        }
    }

    fun stopVerification() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: Exception) {}
        _status.value = _status.value.copy(isListening = false)
    }

    private fun generateMockSatellites(): List<SatelliteInfo> {
        return listOf(
            SatelliteInfo(prn = 4, snr = 38.4f, elevation = 65f, azimuth = 112f, usedInFix = true, constellation = "GPS"),
            SatelliteInfo(prn = 7, snr = 34.1f, elevation = 48f, azimuth = 205f, usedInFix = true, constellation = "GPS"),
            SatelliteInfo(prn = 9, snr = 41.2f, elevation = 78f, azimuth = 45f, usedInFix = true, constellation = "GPS"),
            SatelliteInfo(prn = 11, snr = 29.5f, elevation = 25f, azimuth = 310f, usedInFix = true, constellation = "GLONASS"),
            SatelliteInfo(prn = 16, snr = 36.8f, elevation = 52f, azimuth = 180f, usedInFix = true, constellation = "GPS"),
            SatelliteInfo(prn = 20, snr = 42.0f, elevation = 82f, azimuth = 95f, usedInFix = true, constellation = "GALILEO"),
            SatelliteInfo(prn = 26, snr = 31.2f, elevation = 32f, azimuth = 270f, usedInFix = true, constellation = "BEIDOU"),
            SatelliteInfo(prn = 30, snr = 28.0f, elevation = 18f, azimuth = 15f, usedInFix = false, constellation = "GPS")
        )
    }

    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371000.0 * c
    }
}
