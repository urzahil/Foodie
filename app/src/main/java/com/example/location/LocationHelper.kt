package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LocationTarget(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isGpsLocation: Boolean = false
)

object LocationHelper {

    const val MAX_RADIUS_KM = 30.0

    /**
     * Calculates distance in kilometers between two lat/lng coordinates using the Haversine formula.
     */
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth's radius in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun formatDistance(km: Double): String {
        return if (km < 1.0) {
            "${(km * 1000).toInt()} m"
        } else {
            String.format(Locale.US, "%.1f km", km)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): LocationTarget? =
        withContext(Dispatchers.IO) {
            val fusedClient: FusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(context)

            try {
                val cts = CancellationTokenSource()
                val location: Location? = suspendCancellableCoroutine { continuation ->
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc ->
                            if (loc != null) {
                                continuation.resume(loc)
                            } else {
                                // Try last known location as fallback
                                fusedClient.lastLocation.addOnSuccessListener { lastLoc ->
                                    continuation.resume(lastLoc)
                                }.addOnFailureListener {
                                    continuation.resume(null)
                                }
                            }
                        }
                        .addOnFailureListener {
                            continuation.resume(null)
                        }

                    continuation.invokeOnCancellation {
                        cts.cancel()
                    }
                }

                if (location != null) {
                    var cityName = "Current Location"
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        val addresses =
                            geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            cityName =
                                addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Near Me"
                        }
                    } catch (_: Exception) {
                    }

                    return@withContext LocationTarget(
                        name = cityName,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        isGpsLocation = true
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }

    suspend fun geocodeLocation(context: Context, query: String): List<LocationTarget> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val results = mutableListOf<LocationTarget>()
                val addresses = geocoder.getFromLocationName(query, 5)
                if (!addresses.isNullOrEmpty()) {
                    for (addr in addresses) {
                        val title = listOfNotNull(
                            addr.featureName,
                            addr.locality,
                            addr.adminArea,
                            addr.countryName
                        ).distinct().joinToString(", ")

                        results.add(
                            LocationTarget(
                                name = if (title.isNotBlank()) title else query,
                                latitude = addr.latitude,
                                longitude = addr.longitude,
                                isGpsLocation = false
                            )
                        )
                    }
                }
                return@withContext results
            } catch (e: Exception) {
                return@withContext emptyList()
            }
        }
}
