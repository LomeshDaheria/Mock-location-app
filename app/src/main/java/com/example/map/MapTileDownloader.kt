package com.example.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class SearchResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val type: String = "Location"
)

class MapTileDownloader(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // 20 MB in-memory tile cache
    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(20 * 1024 * 1024) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }

    private val diskCacheDir = File(context.cacheDir, "osm_tiles").apply {
        if (!exists()) mkdirs()
    }

    suspend fun getTileBitmap(z: Int, x: Int, y: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "${z}_${x}_${y}"

        // Check RAM cache
        memoryCache.get(key)?.let { return@withContext it }

        // Check Disk cache
        val diskFile = File(diskCacheDir, "$key.png")
        if (diskFile.exists() && diskFile.length() > 0) {
            try {
                val bitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(key, bitmap)
                    return@withContext bitmap
                }
            } catch (_: Exception) {}
        }

        // Fetch from OpenStreetMap tile servers
        try {
            val url = "https://tile.openstreetmap.org/$z/$x/$y.png"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AndroidMockLocationTester/1.0 (TestingApp)")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        memoryCache.put(key, bitmap)
                        try {
                            FileOutputStream(diskFile).use { it.write(bytes) }
                        } catch (_: Exception) {}
                        return@withContext bitmap
                    }
                }
            }
        } catch (_: Exception) {
            // Offline or network error: gracefully return null
        }
        return@withContext null
    }

    suspend fun searchLocations(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // Check if query is direct coordinates e.g. "37.7749, -122.4194"
        val coordPattern = Regex("""^([+-]?\d+(?:\.\d+)?)[,\s]+([+-]?\d+(?:\.\d+)?)$""")
        coordPattern.matchEntire(trimmed)?.let { match ->
            val lat = match.groupValues[1].toDoubleOrNull()
            val lng = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                return@withContext listOf(
                    SearchResult(
                        displayName = "Coordinates: $lat, $lng",
                        latitude = lat,
                        longitude = lng,
                        type = "Direct Coordinate"
                    )
                )
            }
        }

        // Query Nominatim OpenStreetMap API
        try {
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=6&addressdetails=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "AndroidMockLocationTester/1.0")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(body)
                val results = mutableListOf<SearchResult>()
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val displayName = item.optString("display_name", "")
                    val lat = item.optDouble("lat", 0.0)
                    val lon = item.optDouble("lon", 0.0)
                    val type = item.optString("type", "Location")
                    if (displayName.isNotEmpty() && lat != 0.0 && lon != 0.0) {
                        results.add(SearchResult(displayName, lat, lon, type))
                    }
                }
                return@withContext results
            }
        } catch (_: Exception) {
            // Ignore search network exceptions
        }
        return@withContext emptyList()
    }
}
