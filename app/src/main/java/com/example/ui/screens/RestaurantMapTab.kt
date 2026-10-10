package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.RestaurantListItem
import com.example.location.LocationHelper
import com.example.location.LocationTarget
import com.example.ui.FoodieViewModel
import com.example.ui.RestaurantWithDistance
import com.example.ui.components.AwardBadge
import com.example.ui.theme.MichelinGold
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

private const val TAG = "RestaurantMapTab"

@Composable
fun RestaurantMapTab(
    restaurants: List<RestaurantWithDistance>,
    activeLocation: LocationTarget?,
    mapRecenterTrigger: Long = 0L,
    viewModel: FoodieViewModel,
    onRestaurantClick: (RestaurantListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var mapsInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            MapsInitializer.initialize(
                context.applicationContext,
                MapsInitializer.Renderer.LATEST
            ) {
                mapsInitialized = true
            }
            mapsInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize maps", e)
            mapsInitialized = true
        }
    }

    val initialPos = viewModel.lastMapCameraPosition?.target ?: LatLng(
        activeLocation?.latitude ?: 48.8566,
        activeLocation?.longitude ?: 2.3522
    )
    val initialZoom = viewModel.lastMapCameraPosition?.zoom ?: 12f

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialPos, initialZoom)
    }

    LaunchedEffect(cameraPositionState.position) {
        viewModel.lastMapCameraPosition = cameraPositionState.position
    }

    var selectedPreviewRestaurant by remember { mutableStateOf<RestaurantWithDistance?>(null) }

    LaunchedEffect(mapRecenterTrigger) {
        if (mapRecenterTrigger > 0L && mapRecenterTrigger > viewModel.lastHandledRecenterTrigger && activeLocation != null) {
            viewModel.lastHandledRecenterTrigger = mapRecenterTrigger
            val target = LatLng(activeLocation.latitude, activeLocation.longitude)
            try {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(target, 12f),
                    durationMs = 800
                )
            } catch (e: Exception) {
                try {
                    cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(target, 12f))
                } catch (_: Exception) {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(target, 12f)
                }
            }
        }
    }

    val icon3Stars = remember(mapsInitialized) { getCategoryMarker("3 Stars") }
    val icon2Stars = remember(mapsInitialized) { getCategoryMarker("2 Stars") }
    val icon1Star = remember(mapsInitialized) { getCategoryMarker("1 Star") }
    val iconBib = remember(mapsInitialized) { getCategoryMarker("Bib Gourmand") }
    val iconSelected = remember(mapsInitialized) { getCategoryMarker("Selected") }

    Box(modifier = modifier
        .fillMaxSize()
        .testTag("restaurant_map")) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                mapType = MapType.NORMAL,
                isMyLocationEnabled = false
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = true,
                myLocationButtonEnabled = false
            )
        ) {
            restaurants.forEach { item ->
                val r = item.restaurant
                val markerIcon = when {
                    r.award.contains("3 Star", ignoreCase = true) -> icon3Stars
                    r.award.contains("2 Star", ignoreCase = true) -> icon2Stars
                    r.award.contains("1 Star", ignoreCase = true) -> icon1Star
                    r.award.contains("Bib", ignoreCase = true) -> iconBib
                    else -> iconSelected
                }

                Marker(
                    state = MarkerState(position = LatLng(r.latitude, r.longitude)),
                    title = r.name,
                    snippet = "${r.award} • ${r.cuisine}",
                    icon = markerIcon,
                    onClick = {
                        selectedPreviewRestaurant = item
                        true
                    }
                )
            }
        }

        // Compact interactive preview pill for selected marker
        AnimatedVisibility(
            visible = selectedPreviewRestaurant != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            selectedPreviewRestaurant?.let { item ->
                val r = item.restaurant
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRestaurantClick(r) }
                        .testTag("map_marker_preview_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = r.name,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                AwardBadge(award = r.award, compact = true)
                            }

                            IconButton(
                                onClick = { selectedPreviewRestaurant = null },
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close preview",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = r.cuisine.ifBlank { "Michelin Dining" },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            if (r.price.isNotBlank()) {
                                Text(
                                    text = "•",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = r.price,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (item.distanceKm != null) {
                                Text(
                                    text = "•",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "${LocationHelper.formatDistance(item.distanceKm)} away",
                                    color = MichelinGold,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Re-Center on active location button
        if (activeLocation != null) {
            FloatingActionButton(
                onClick = {
                    coroutineScope.launch {
                        val target = LatLng(activeLocation.latitude, activeLocation.longitude)
                        try {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(target, 12f),
                                durationMs = 600
                            )
                        } catch (_: Exception) {
                            cameraPositionState.position =
                                CameraPosition.fromLatLngZoom(target, 12f)
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 16.dp)
                    .size(46.dp)
                    .testTag("map_recenter_fab"),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Re-center map",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

private fun getCategoryMarker(category: String): BitmapDescriptor? {
    return try {
        createCategoryMarkerBitmap(category)
    } catch (e: Throwable) {
        Log.w(TAG, "Custom marker failed, falling back to default marker", e)
        try {
            val hue = when {
                category.contains("3 Star", ignoreCase = true) -> BitmapDescriptorFactory.HUE_YELLOW
                category.contains("2 Star", ignoreCase = true) -> BitmapDescriptorFactory.HUE_RED
                category.contains("1 Star", ignoreCase = true) -> BitmapDescriptorFactory.HUE_ROSE
                category.contains("Bib", ignoreCase = true) -> BitmapDescriptorFactory.HUE_ORANGE
                else -> BitmapDescriptorFactory.HUE_AZURE
            }
            BitmapDescriptorFactory.defaultMarker(hue)
        } catch (_: Throwable) {
            null
        }
    }
}

private fun createCategoryMarkerBitmap(category: String): BitmapDescriptor {
    val width = 84
    val height = 98
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val (pinColor, text, textColor) = when {
        category.contains("3 Star", ignoreCase = true) ->
            Triple(0xFFB71C1C.toInt(), "3★", 0xFFFFD700.toInt())

        category.contains("2 Star", ignoreCase = true) ->
            Triple(0xFFDA291C.toInt(), "2★", 0xFFFFFFFF.toInt())

        category.contains("1 Star", ignoreCase = true) ->
            Triple(0xFFE53935.toInt(), "1★", 0xFFFFFFFF.toInt())

        category.contains("Bib", ignoreCase = true) ->
            Triple(0xFFE65100.toInt(), "BIB", 0xFFFFFFFF.toInt())

        else ->
            Triple(0xFF37474F.toInt(), "SEL", 0xFFB0BEC5.toInt())
    }

    // Shadow
    paint.color = 0x55000000
    canvas.drawCircle(42f, 42f, 38f, paint)

    // Pin Body
    paint.color = pinColor
    canvas.drawCircle(42f, 38f, 36f, paint)

    // White outline
    paint.color = 0xFFFFFFFF.toInt()
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    canvas.drawCircle(42f, 38f, 36f, paint)

    // Pin pointer triangle
    val path = android.graphics.Path()
    path.moveTo(28f, 60f)
    path.lineTo(42f, 94f)
    path.lineTo(56f, 60f)
    path.close()
    paint.style = Paint.Style.FILL
    paint.color = pinColor
    canvas.drawPath(path, paint)

    // Center text label
    paint.color = textColor
    paint.textSize = if (text.length > 2) 20f else 24f
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true

    val fontMetrics = paint.fontMetrics
    val baseline = 38f - (fontMetrics.ascent + fontMetrics.descent) / 2
    canvas.drawText(text, 42f, baseline, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
