package com.example.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
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

    private const val TAG = "LocationHelper"

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
                    val cityName = reverseGeocodeName(context, location.latitude, location.longitude)
                        ?: "Current Location"

                    return@withContext LocationTarget(
                        name = cityName,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        isGpsLocation = true
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Unable to get current location", e)
            }
            return@withContext null
        }

    suspend fun geocodeLocation(context: Context, query: String): List<LocationTarget> {
        if (query.isBlank()) return emptyList()
        val addresses = geocode(
            context,
            legacy = {
                @Suppress("DEPRECATION")
                getFromLocationName(query, 5)
            },
            modern = { geocoder, onResult -> nameLookupApi33(geocoder, query, onResult) }
        )
        return addresses.map { addr ->
            val title = listOfNotNull(
                addr.featureName,
                addr.locality,
                addr.adminArea,
                addr.countryName
            ).distinct().joinToString(", ")

            LocationTarget(
                name = title.ifBlank { query },
                latitude = addr.latitude,
                longitude = addr.longitude,
                isGpsLocation = false
            )
        }
    }

    private suspend fun reverseGeocodeName(context: Context, lat: Double, lng: Double): String? {
        val addr = geocode(
            context,
            legacy = {
                @Suppress("DEPRECATION")
                getFromLocation(lat, lng, 1)
            },
            modern = { geocoder, onResult -> reverseLookupApi33(geocoder, lat, lng, onResult) }
        ).firstOrNull() ?: return null
        return addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: "Near Me"
    }

    /**
     * Runs a [Geocoder] lookup without blocking the caller: API 33+ uses the asynchronous
     * listener API, older versions run the blocking call on [Dispatchers.IO]. Returns an
     * empty list when no geocoder is available or the lookup fails.
     */
    private suspend fun geocode(
        context: Context,
        legacy: Geocoder.() -> List<Address>?,
        modern: (Geocoder, (List<Address>) -> Unit) -> Unit
    ): List<Address> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    try {
                        modern(geocoder) { addresses ->
                            if (continuation.isActive) continuation.resume(addresses)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resume(emptyList())
                    }
                }
            } else {
                withContext(Dispatchers.IO) { geocoder.legacy().orEmpty() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Geocoding failed", e)
            emptyList()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun nameLookupApi33(
        geocoder: Geocoder,
        query: String,
        onResult: (List<Address>) -> Unit
    ) {
        geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) = onResult(addresses)
            override fun onError(errorMessage: String?) = onResult(emptyList())
        })
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun reverseLookupApi33(
        geocoder: Geocoder,
        lat: Double,
        lng: Double,
        onResult: (List<Address>) -> Unit
    ) {
        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) = onResult(addresses)
            override fun onError(errorMessage: String?) = onResult(emptyList())
        })
    }
}
