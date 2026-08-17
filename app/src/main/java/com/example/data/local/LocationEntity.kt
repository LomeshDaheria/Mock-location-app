package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val note: String = "",
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 15.0,
    val tag: String = "General",
    val radiusMeters: Float = 50f, // For geofence testing notes
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val waypointsJson: String, // List of lat,lng,alt serialized as JSON string
    val totalDistanceMeters: Double = 0.0,
    val estimatedDurationSeconds: Long = 0L,
    val transportMode: String = "WALK",
    val speedKmh: Float = 5f,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "location_history")
data class HistoryLocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 15.0,
    val mode: String = "FIXED",
    val timestamp: Long = System.currentTimeMillis()
)
