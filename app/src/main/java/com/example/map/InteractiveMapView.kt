package com.example.map

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.data.model.GeoPoint
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.SecondaryCyan
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.tan

@Composable
fun InteractiveMapView(
    centerLat: Double,
    centerLng: Double,
    zoomLevel: Float,
    mockLat: Double?,
    mockLng: Double?,
    mockBearing: Float = 0f,
    isSpoofing: Boolean = false,
    waypoints: List<GeoPoint> = emptyList(),
    geofenceRadiusMeters: Float = 0f,
    onMapMoved: (newLat: Double, newLng: Double, newZoom: Float) -> Unit,
    onMapTapped: (lat: Double, lng: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tileDownloader = remember { MapTileDownloader(context) }
    val tileBitmaps = remember { mutableStateMapOf<String, Bitmap>() }

    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    val intZoom = zoomLevel.roundToInt().coerceIn(1, 18)

    // Load tiles for current view
    LaunchedEffect(centerLat, centerLng, intZoom) {
        val (centerX, centerY) = latLngToTileXY(centerLat, centerLng, intZoom)
        val range = 2
        for (dx in -range..range) {
            for (dy in -range..range) {
                val tx = (centerX + dx).toInt()
                val ty = (centerY + dy).toInt()
                val maxTile = (1 shl intZoom) - 1
                if (tx in 0..maxTile && ty in 0..maxTile) {
                    val key = "${intZoom}_${tx}_${ty}"
                    if (!tileBitmaps.containsKey(key)) {
                        coroutineScope.launch {
                            val bitmap = tileDownloader.getTileBitmap(intZoom, tx, ty)
                            if (bitmap != null) {
                                tileBitmaps[key] = bitmap
                            }
                        }
                    }
                }
            }
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .pointerInput(centerLat, centerLng, zoomLevel) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newZoom = (zoomLevel + (zoom - 1f) * 2f).coerceIn(2f, 18f)
                    val worldSize = 256.0 * (2.0.pow(zoomLevel.toDouble()))
                    val dLng = -(pan.x / worldSize) * 360.0
                    val dLat = (pan.y / worldSize) * 180.0 * cos(Math.toRadians(centerLat))

                    val nextLat = (centerLat + dLat).coerceIn(-85.0, 85.0)
                    val nextLng = ((centerLng + dLng + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                    onMapMoved(nextLat, nextLng, newZoom)
                }
            }
            .pointerInput(centerLat, centerLng, zoomLevel) {
                detectTapGestures { tapOffset ->
                    val (w, h) = Pair(size.width.toFloat(), size.height.toFloat())
                    val tappedGeo = screenOffsetToLatLng(
                        tapOffset,
                        Offset(w / 2f, h / 2f),
                        centerLat,
                        centerLng,
                        zoomLevel
                    )
                    onMapTapped(tappedGeo.first, tappedGeo.second)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
            val tileSize = 256f

            // 1. Draw OpenStreetMap Tiles
            val (tileXFrac, tileYFrac) = latLngToTileXY(centerLat, centerLng, intZoom)
            val scaleFactor = 2.0.pow((zoomLevel - intZoom).toDouble()).toFloat()
            val scaledTileSize = tileSize * scaleFactor

            val minDx = -floor(size.width / (2 * scaledTileSize)).toInt() - 1
            val maxDx = floor(size.width / (2 * scaledTileSize)).toInt() + 1
            val minDy = -floor(size.height / (2 * scaledTileSize)).toInt() - 1
            val maxDy = floor(size.height / (2 * scaledTileSize)).toInt() + 1

            val maxTileIndex = (1 shl intZoom) - 1

            for (dx in minDx..maxDx) {
                for (dy in minDy..maxDy) {
                    val tileX = (floor(tileXFrac) + dx).toInt()
                    val tileY = (floor(tileYFrac) + dy).toInt()

                    if (tileX in 0..maxTileIndex && tileY in 0..maxTileIndex) {
                        val key = "${intZoom}_${tileX}_${tileY}"
                        val bitmap = tileBitmaps[key]

                        val offsetX = canvasCenter.x + (tileX - tileXFrac.toFloat()) * scaledTileSize
                        val offsetY = canvasCenter.y + (tileY - tileYFrac.toFloat()) * scaledTileSize

                        if (bitmap != null && !bitmap.isRecycled) {
                            drawImage(
                                image = bitmap.asImageBitmap(),
                                dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt()),
                                dstSize = IntSize(scaledTileSize.roundToInt(), scaledTileSize.roundToInt())
                            )
                        } else {
                            // Sleek placeholder tile grid
                            drawRect(
                                color = gridColor,
                                topLeft = Offset(offsetX, offsetY),
                                size = androidx.compose.ui.geometry.Size(scaledTileSize, scaledTileSize),
                                style = Stroke(width = 1f)
                            )
                        }
                    }
                }
            }

            // 2. Draw Geofence radius circle if enabled
            if (geofenceRadiusMeters > 0 && mockLat != null && mockLng != null) {
                val mockScreenPos = latLngToScreenOffset(
                    mockLat, mockLng, canvasCenter, centerLat, centerLng, zoomLevel
                )
                val pixelsPerMeter = metersToPixels(centerLat, zoomLevel)
                val pixelRadius = (geofenceRadiusMeters * pixelsPerMeter).toFloat()

                drawCircle(
                    color = SecondaryCyan.copy(alpha = 0.18f),
                    radius = pixelRadius,
                    center = mockScreenPos
                )
                drawCircle(
                    color = SecondaryCyan,
                    radius = pixelRadius,
                    center = mockScreenPos,
                    style = Stroke(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                    )
                )
            }

            // 3. Draw Route Polyline & Waypoints if any
            if (waypoints.size >= 2) {
                val path = Path()
                waypoints.forEachIndexed { index, wp ->
                    val screenPos = latLngToScreenOffset(
                        wp.latitude, wp.longitude, canvasCenter, centerLat, centerLng, zoomLevel
                    )
                    if (index == 0) {
                        path.moveTo(screenPos.x, screenPos.y)
                    } else {
                        path.lineTo(screenPos.x, screenPos.y)
                    }
                }

                // Shadow path
                drawPath(
                    path = path,
                    color = Color.Black.copy(alpha = 0.4f),
                    style = Stroke(width = 8f)
                )
                // Main route path
                drawPath(
                    path = path,
                    color = PrimaryBlueLight,
                    style = Stroke(width = 5f)
                )

                // Draw waypoint circles with index
                waypoints.forEachIndexed { index, wp ->
                    val screenPos = latLngToScreenOffset(
                        wp.latitude, wp.longitude, canvasCenter, centerLat, centerLng, zoomLevel
                    )
                    val isStart = index == 0
                    val isEnd = index == waypoints.size - 1
                    val markerColor = when {
                        isStart -> SuccessGreen
                        isEnd -> Color(0xFFEF4444)
                        else -> PrimaryBlue
                    }

                    drawCircle(color = Color.White, radius = 14f, center = screenPos)
                    drawCircle(color = markerColor, radius = 11f, center = screenPos)

                    drawIntoCanvas { canvas ->
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                        canvas.nativeCanvas.drawText(
                            (index + 1).toString(),
                            screenPos.x,
                            screenPos.y + 8f,
                            paint
                        )
                    }
                }
            }

            // 4. Draw Active Mock Location Pin (if spoofing or set)
            if (mockLat != null && mockLng != null) {
                val mockScreenPos = latLngToScreenOffset(
                    mockLat, mockLng, canvasCenter, centerLat, centerLng, zoomLevel
                )

                if (isSpoofing) {
                    // Glowing radar wave
                    drawCircle(
                        color = SecondaryCyan.copy(alpha = pulseAlpha),
                        radius = pulseRadius,
                        center = mockScreenPos
                    )
                }

                // Outer halo
                drawCircle(
                    color = if (isSpoofing) SuccessGreen else PrimaryBlueLight,
                    radius = 16f,
                    center = mockScreenPos
                )
                drawCircle(
                    color = Color.White,
                    radius = 12f,
                    center = mockScreenPos
                )
                drawCircle(
                    color = if (isSpoofing) SuccessGreen else PrimaryBlue,
                    radius = 8f,
                    center = mockScreenPos
                )

                // Direction pointer arrow if moving with bearing
                if (mockBearing != 0f) {
                    val arrowLength = 28f
                    val rad = Math.toRadians((mockBearing - 90).toDouble())
                    val arrowTip = Offset(
                        mockScreenPos.x + (arrowLength * cos(rad)).toFloat(),
                        mockScreenPos.y + (arrowLength * sin(rad)).toFloat()
                    )
                    drawLine(
                        color = if (isSpoofing) SuccessGreen else PrimaryBlue,
                        start = mockScreenPos,
                        end = arrowTip,
                        strokeWidth = 4f
                    )
                }
            }

            // 5. Draw Center Reticle / Crosshair (Target selector)
            drawReticle(canvasCenter, PrimaryBlueLight)

            // 6. Draw Coordinate HUD in top right of map
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#94A3B8")
                    textSize = 28f
                    isAntiAlias = true
                }
                val coordText = String.format(Locale.US, "Lat: %.5f  Lng: %.5f | Zoom: %.1fx", centerLat, centerLng, zoomLevel)
                canvas.nativeCanvas.drawText(coordText, 32f, size.height - 32f, paint)
            }
        }
    }
}

private fun DrawScope.drawReticle(center: Offset, color: Color) {
    val size = 22f
    val gap = 6f
    // Cross lines
    drawLine(color = color, start = Offset(center.x - size, center.y), end = Offset(center.x - gap, center.y), strokeWidth = 2.5f)
    drawLine(color = color, start = Offset(center.x + gap, center.y), end = Offset(center.x + size, center.y), strokeWidth = 2.5f)
    drawLine(color = color, start = Offset(center.x, center.y - size), end = Offset(center.x, center.y - gap), strokeWidth = 2.5f)
    drawLine(color = color, start = Offset(center.x, center.y + gap), end = Offset(center.x, center.y + size), strokeWidth = 2.5f)
    // Center point
    drawCircle(color = color, radius = 3.5f, center = center)
    // Outer circle
    drawCircle(color = color.copy(alpha = 0.4f), radius = size, center = center, style = Stroke(width = 1.5f))
}

fun latLngToTileXY(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
    val n = 2.0.pow(zoom.toDouble())
    val x = ((lng + 180.0) / 360.0) * n
    val latRad = Math.toRadians(lat)
    val y = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0) * n
    return Pair(x, y)
}

fun latLngToScreenOffset(
    targetLat: Double,
    targetLng: Double,
    canvasCenter: Offset,
    mapCenterLat: Double,
    mapCenterLng: Double,
    zoom: Float
): Offset {
    val worldSize = 256.0 * (2.0.pow(zoom.toDouble()))
    val targetX = ((targetLng + 180.0) / 360.0) * worldSize
    val centerX = ((mapCenterLng + 180.0) / 360.0) * worldSize

    val targetLatRad = Math.toRadians(targetLat)
    val targetY = ((1.0 - ln(tan(targetLatRad) + 1.0 / cos(targetLatRad)) / PI) / 2.0) * worldSize

    val centerLatRad = Math.toRadians(mapCenterLat)
    val centerY = ((1.0 - ln(tan(centerLatRad) + 1.0 / cos(centerLatRad)) / PI) / 2.0) * worldSize

    return Offset(
        (canvasCenter.x + (targetX - centerX)).toFloat(),
        (canvasCenter.y + (targetY - centerY)).toFloat()
    )
}

fun screenOffsetToLatLng(
    screenOffset: Offset,
    canvasCenter: Offset,
    mapCenterLat: Double,
    mapCenterLng: Double,
    zoom: Float
): Pair<Double, Double> {
    val worldSize = 256.0 * (2.0.pow(zoom.toDouble()))
    val centerLatRad = Math.toRadians(mapCenterLat)
    val centerX = ((mapCenterLng + 180.0) / 360.0) * worldSize
    val centerY = ((1.0 - ln(tan(centerLatRad) + 1.0 / cos(centerLatRad)) / PI) / 2.0) * worldSize

    val targetX = centerX + (screenOffset.x - canvasCenter.x)
    val targetY = centerY + (screenOffset.y - canvasCenter.y)

    val lng = (targetX / worldSize) * 360.0 - 180.0
    val n = PI - 2.0 * PI * (targetY / worldSize)
    val lat = Math.toDegrees(atan(sinh(n)))

    return Pair(lat.coerceIn(-85.0, 85.0), lng.coerceIn(-180.0, 180.0))
}

fun metersToPixels(lat: Double, zoom: Float): Double {
    val metersPerPixel = (156543.03392 * cos(Math.toRadians(lat))) / (2.0.pow(zoom.toDouble()))
    return if (metersPerPixel > 0) 1.0 / metersPerPixel else 0.01
}
