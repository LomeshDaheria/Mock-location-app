package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SavedLocationEntity
import com.example.data.local.SavedRouteEntity
import com.example.data.model.GeoPoint
import com.example.data.model.MockEngineState
import com.example.data.model.MockProviderStatus
import com.example.data.model.PresetLocation
import com.example.data.model.SimulationMode
import com.example.data.model.TransportMode
import com.example.data.repository.LocationRepository
import com.example.map.MapTileDownloader
import com.example.map.SearchResult
import com.example.service.MockLocationEngine
import com.example.service.MockLocationForegroundService
import com.example.simulation.JoystickController
import com.example.simulation.JoystickState
import com.example.simulation.RouteSimulationStatus
import com.example.simulation.RouteSimulator
import com.example.util.LocationVerifier
import com.example.util.VerificationStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos

enum class AppTab(val title: String) {
    MAP("Map"),
    ROUTES("Routes"),
    JOYSTICK("Joystick"),
    SAVED("Saved"),
    VERIFY("Diagnostics")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val database = AppDatabase.getInstance(context)
    private val repository = LocationRepository(database.locationDao())
    val mockEngine = MockLocationEngine.getInstance(context)
    val routeSimulator = RouteSimulator(mockEngine, viewModelScope)
    val joystickController = JoystickController(mockEngine, viewModelScope)
    val locationVerifier = LocationVerifier(context)
    private val tileDownloader = MapTileDownloader(context)

    val engineState: StateFlow<MockEngineState> = mockEngine.engineState
    val routeState: StateFlow<RouteSimulationStatus> = routeSimulator.status
    val joystickState: StateFlow<JoystickState> = joystickController.joystickState
    val verifierState: StateFlow<VerificationStatus> = locationVerifier.status

    val savedLocations: StateFlow<List<SavedLocationEntity>> = repository.savedLocations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedRoutes: StateFlow<List<SavedRouteEntity>> = repository.savedRoutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val presets: List<PresetLocation> = repository.getDeveloperPresets()

    // UI Navigation & View State
    private val _selectedTab = MutableStateFlow(AppTab.MAP)
    val selectedTab: StateFlow<AppTab> = _selectedTab.asStateFlow()

    // Map viewport state (defaults to San Francisco Silicon Valley)
    var mapCenterLat = MutableStateFlow(37.774929)
    var mapCenterLng = MutableStateFlow(-122.419416)
    var mapZoom = MutableStateFlow(14.5f)

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    // Route building state
    private val _routeWaypoints = MutableStateFlow<List<GeoPoint>>(emptyList())
    val routeWaypoints: StateFlow<List<GeoPoint>> = _routeWaypoints.asStateFlow()

    private val _selectedTransportMode = MutableStateFlow(TransportMode.WALK)
    val selectedTransportMode: StateFlow<TransportMode> = _selectedTransportMode.asStateFlow()

    private val _customSpeedKmh = MutableStateFlow(5f)
    val customSpeedKmh: StateFlow<Float> = _customSpeedKmh.asStateFlow()

    // Quick Toast / Alert message
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        // Check initial mock provider status
        mockEngine.testMockProviderStatus()
    }

    fun selectTab(tab: AppTab) {
        _selectedTab.value = tab
    }

    fun dismissUserMessage() {
        _userMessage.value = null
    }

    fun updateMapViewport(lat: Double, lng: Double, zoom: Float) {
        mapCenterLat.value = lat
        mapCenterLng.value = lng
        mapZoom.value = zoom
    }

    fun jumpToLocation(lat: Double, lng: Double, zoom: Float = 15f, teleportNow: Boolean = false) {
        mapCenterLat.value = lat
        mapCenterLng.value = lng
        mapZoom.value = zoom
        if (teleportNow) {
            teleportToCoordinates(lat, lng, "Teleported to $lat, $lng")
        }
    }

    fun teleportToCoordinates(lat: Double, lng: Double, label: String = "Teleported location") {
        val currentAlt = engineState.value.altitude
        val success = mockEngine.startSpoofing(
            initialLat = lat,
            initialLng = lng,
            altitude = currentAlt,
            speedKmh = 0f,
            bearing = engineState.value.bearing,
            mode = SimulationMode.FIXED
        )
        if (success) {
            MockLocationForegroundService.startService(context)
            viewModelScope.launch {
                repository.recordHistory(label, lat, lng, currentAlt, "FIXED")
            }
            _userMessage.value = "GPS spoofed to: ${String.format("%.4f, %.4f", lat, lng)}"
        } else {
            _userMessage.value = "Mock permission required. Please enable mock location app in Developer Options."
        }
    }

    fun stopSpoofing() {
        mockEngine.stopSpoofing()
        routeSimulator.pause()
        MockLocationForegroundService.stopService(context)
        _userMessage.value = "Mock location stopped. GPS restored."
    }

    fun nudgeCoordinates(direction: String, meters: Double) {
        val lat = engineState.value.currentLat
        val lng = engineState.value.currentLng

        // 1 deg lat = ~111,320m
        val dLat = meters / 111320.0
        val dLng = meters / (111320.0 * cos(Math.toRadians(lat)))

        val (newLat, newLng) = when (direction) {
            "NORTH" -> Pair(lat + dLat, lng)
            "SOUTH" -> Pair(lat - dLat, lng)
            "EAST" -> Pair(lat, lng + dLng)
            "WEST" -> Pair(lat, lng - dLng)
            else -> Pair(lat, lng)
        }

        mockEngine.updateCoordinates(newLat, newLng)
        mapCenterLat.value = newLat
        mapCenterLng.value = newLng
    }

    fun updateAltitude(altitude: Double) {
        mockEngine.updateCoordinates(
            lat = engineState.value.currentLat,
            lng = engineState.value.currentLng,
            altitude = altitude
        )
    }

    fun updateAccuracy(accuracy: Float) {
        // Update accuracy parameter
        val current = engineState.value
        mockEngine.updateCoordinates(
            lat = current.currentLat,
            lng = current.currentLng,
            altitude = current.altitude
        )
    }

    fun toggleGpsJitter(enabled: Boolean) {
        mockEngine.setGpsJitterEnabled(enabled)
    }

    // Search
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.trim().length >= 2) {
            searchJob = viewModelScope.launch {
                delay(350)
                _isSearching.value = true
                val results = tileDownloader.searchLocations(query)
                _searchResults.value = results
                _isSearching.value = false
            }
        } else {
            _searchResults.value = emptyList()
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    // Route Planner
    fun addRouteWaypoint(lat: Double = mapCenterLat.value, lng: Double = mapCenterLng.value) {
        val newPoint = GeoPoint(lat, lng, 15.0, "Point ${_routeWaypoints.value.size + 1}")
        val updated = _routeWaypoints.value + newPoint
        _routeWaypoints.value = updated
        routeSimulator.setWaypoints(updated, _customSpeedKmh.value)
    }

    fun removeRouteWaypoint(index: Int) {
        val current = _routeWaypoints.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _routeWaypoints.value = current
            routeSimulator.setWaypoints(current, _customSpeedKmh.value)
        }
    }

    fun clearRouteWaypoints() {
        routeSimulator.pause()
        _routeWaypoints.value = emptyList()
        routeSimulator.setWaypoints(emptyList())
    }

    fun setTransportMode(mode: TransportMode) {
        _selectedTransportMode.value = mode
        _customSpeedKmh.value = mode.defaultSpeedKmh
        routeSimulator.setSpeed(mode.defaultSpeedKmh)
    }

    fun setCustomSpeed(speedKmh: Float) {
        _customSpeedKmh.value = speedKmh
        routeSimulator.setSpeed(speedKmh)
    }

    fun toggleRouteLooping(loop: Boolean) {
        mockEngine.setRouteLooping(loop)
        routeSimulator.setLooping(loop)
    }

    fun startRouteSimulation() {
        if (_routeWaypoints.value.size < 2) {
            _userMessage.value = "Please add at least 2 waypoints to simulate a route."
            return
        }
        routeSimulator.startOrResume(autoStartEngine = true)
        MockLocationForegroundService.startService(context)
    }

    fun pauseRouteSimulation() {
        routeSimulator.pause()
    }

    fun seekRouteSimulation(progress: Float) {
        routeSimulator.seekToProgress(progress)
    }

    fun saveCurrentRoute(title: String, description: String = "") {
        if (_routeWaypoints.value.size < 2) return
        viewModelScope.launch {
            val jsonArray = JSONArray()
            _routeWaypoints.value.forEach { pt ->
                val obj = JSONObject().apply {
                    put("lat", pt.latitude)
                    put("lng", pt.longitude)
                    put("alt", pt.altitude)
                    put("name", pt.name)
                }
                jsonArray.put(obj)
            }
            val totalDist = routeSimulator.status.value.totalDistanceMeters
            repository.saveRoute(
                SavedRouteEntity(
                    title = title,
                    description = description,
                    waypointsJson = jsonArray.toString(),
                    totalDistanceMeters = totalDist,
                    transportMode = _selectedTransportMode.value.name,
                    speedKmh = _customSpeedKmh.value
                )
            )
            _userMessage.value = "Route '$title' saved successfully."
        }
    }

    fun loadSavedRoute(route: SavedRouteEntity) {
        try {
            val jsonArray = JSONArray(route.waypointsJson)
            val points = mutableListOf<GeoPoint>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                points.add(
                    GeoPoint(
                        latitude = obj.optDouble("lat", 0.0),
                        longitude = obj.optDouble("lng", 0.0),
                        altitude = obj.optDouble("alt", 15.0),
                        name = obj.optString("name", "Point ${i + 1}")
                    )
                )
            }
            _routeWaypoints.value = points
            val mode = TransportMode.entries.find { it.name == route.transportMode } ?: TransportMode.WALK
            _selectedTransportMode.value = mode
            _customSpeedKmh.value = route.speedKmh
            routeSimulator.setWaypoints(points, route.speedKmh)
            if (points.isNotEmpty()) {
                mapCenterLat.value = points[0].latitude
                mapCenterLng.value = points[0].longitude
                mapZoom.value = 15f
            }
            _selectedTab.value = AppTab.ROUTES
            _userMessage.value = "Loaded route: ${route.title}"
        } catch (_: Exception) {
            _userMessage.value = "Error parsing route waypoints."
        }
    }

    fun deleteSavedRoute(id: Long) {
        viewModelScope.launch {
            repository.deleteRoute(id)
        }
    }

    // Saved Locations
    fun saveLocationBookmark(
        name: String,
        note: String,
        lat: Double = mapCenterLat.value,
        lng: Double = mapCenterLng.value,
        tag: String = "General",
        radiusMeters: Float = 50f
    ) {
        viewModelScope.launch {
            repository.saveLocation(
                SavedLocationEntity(
                    name = name,
                    note = note,
                    latitude = lat,
                    longitude = lng,
                    tag = tag,
                    radiusMeters = radiusMeters
                )
            )
            _userMessage.value = "Saved '$name' to bookmarks."
        }
    }

    fun deleteSavedLocation(id: Long) {
        viewModelScope.launch {
            repository.deleteLocation(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Verification
    fun toggleVerificationListening(start: Boolean) {
        if (start) {
            locationVerifier.startVerification(engineState.value.currentLat, engineState.value.currentLng)
        } else {
            locationVerifier.stopVerification()
        }
    }
}
