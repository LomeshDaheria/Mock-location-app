package com.example.data.repository

import com.example.data.local.HistoryLocationEntity
import com.example.data.local.LocationDao
import com.example.data.local.SavedLocationEntity
import com.example.data.local.SavedRouteEntity
import com.example.data.model.PresetLocation
import kotlinx.coroutines.flow.Flow

class LocationRepository(private val locationDao: LocationDao) {

    val savedLocations: Flow<List<SavedLocationEntity>> = locationDao.getAllSavedLocations()
    val savedRoutes: Flow<List<SavedRouteEntity>> = locationDao.getAllSavedRoutes()
    val history: Flow<List<HistoryLocationEntity>> = locationDao.getRecentHistory()

    suspend fun saveLocation(location: SavedLocationEntity): Long =
        locationDao.insertSavedLocation(location)

    suspend fun updateLocation(location: SavedLocationEntity) =
        locationDao.updateSavedLocation(location)

    suspend fun deleteLocation(id: Long) =
        locationDao.deleteSavedLocationById(id)

    suspend fun saveRoute(route: SavedRouteEntity): Long =
        locationDao.insertSavedRoute(route)

    suspend fun deleteRoute(id: Long) =
        locationDao.deleteSavedRouteById(id)

    suspend fun recordHistory(label: String, lat: Double, lng: Double, alt: Double, mode: String) {
        locationDao.insertHistory(
            HistoryLocationEntity(
                label = label,
                latitude = lat,
                longitude = lng,
                altitude = alt,
                mode = mode
            )
        )
    }

    suspend fun clearHistory() = locationDao.clearHistory()

    fun getDeveloperPresets(): List<PresetLocation> = listOf(
        PresetLocation(
            name = "Apple Park",
            city = "Cupertino, CA",
            country = "USA",
            latitude = 37.334900,
            longitude = -122.009020,
            altitude = 55.0,
            category = "Tech Hubs"
        ),
        PresetLocation(
            name = "Googleplex HQ",
            city = "Mountain View, CA",
            country = "USA",
            latitude = 37.422000,
            longitude = -122.084100,
            altitude = 12.0,
            category = "Tech Hubs"
        ),
        PresetLocation(
            name = "Times Square",
            city = "New York, NY",
            country = "USA",
            latitude = 40.758000,
            longitude = -73.985500,
            altitude = 15.0,
            category = "Landmarks"
        ),
        PresetLocation(
            name = "Shibuya Crossing",
            city = "Tokyo",
            country = "Japan",
            latitude = 35.659500,
            longitude = 139.700550,
            altitude = 32.0,
            category = "Landmarks"
        ),
        PresetLocation(
            name = "Piccadilly Circus",
            city = "London",
            country = "UK",
            latitude = 51.510100,
            longitude = -0.134500,
            altitude = 22.0,
            category = "Landmarks"
        ),
        PresetLocation(
            name = "Eiffel Tower",
            city = "Paris",
            country = "France",
            latitude = 48.858400,
            longitude = 2.294500,
            altitude = 35.0,
            category = "Landmarks"
        ),
        PresetLocation(
            name = "Sydney Opera House",
            city = "Sydney",
            country = "Australia",
            latitude = -33.856800,
            longitude = 151.215300,
            altitude = 5.0,
            category = "Landmarks"
        ),
        PresetLocation(
            name = "Marina Bay Sands",
            city = "Singapore",
            country = "Singapore",
            latitude = 1.283800,
            longitude = 103.860700,
            altitude = 8.0,
            category = "Landmarks"
        ),
        PresetLocation(
            name = "Brandenburg Gate",
            city = "Berlin",
            country = "Germany",
            latitude = 52.516300,
            longitude = 13.377700,
            altitude = 34.0,
            category = "Landmarks"
        ),
        PresetLocation(
            name = "Colosseum",
            city = "Rome",
            country = "Italy",
            latitude = 41.890200,
            longitude = 12.492200,
            altitude = 24.0,
            category = "Landmarks"
        ),
        PresetLocation(
            name = "Null Island (0, 0)",
            city = "Gulf of Guinea",
            country = "Atlantic Ocean",
            latitude = 0.0,
            longitude = 0.0,
            altitude = 0.0,
            category = "Edge Cases"
        ),
        PresetLocation(
            name = "North Pole",
            city = "Arctic Ocean",
            country = "International",
            latitude = 90.0,
            longitude = 0.0,
            altitude = 1.0,
            category = "Edge Cases"
        )
    )
}
