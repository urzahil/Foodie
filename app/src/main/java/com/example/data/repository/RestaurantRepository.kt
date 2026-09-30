package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.RestaurantDao
import com.example.data.local.RestaurantEntity
import com.example.data.network.MichelinCsvDownloader
import com.example.data.network.MichelinPageScraper
import com.example.data.network.SyncState
import com.example.location.LocationHelper
import com.example.location.LocationTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class AutocompleteSuggestion(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val isCity: Boolean = true
)

class RestaurantRepository(
    private val context: Context,
    private val dao: RestaurantDao
) {
    companion object {
        private const val TAG = "RestaurantRepository"
    }

    private val downloader = MichelinCsvDownloader(context, dao)
    private val scraper = MichelinPageScraper(context, dao)

    val syncState: StateFlow<SyncState> = downloader.syncState

    val allRestaurants: Flow<List<RestaurantEntity>> = dao.getAllRestaurants()
    val favorites: Flow<List<RestaurantEntity>> = dao.getFavorites()
    val visited: Flow<List<RestaurantEntity>> = dao.getVisited()

    val uniqueCuisines: Flow<List<String>> = dao.getUniqueCuisinesFlow().map { list ->
        list.flatMap { entry ->
            entry.split(",", "/", "&")
                .map { it.trim() }
                .filter { it.isNotBlank() }
        }.distinct().sorted()
    }

    // In-memory cache of city centroids from Michelin database
    @Volatile
    private var cachedCityLocations: List<LocationTarget> = emptyList()

    suspend fun syncIfNeeded(force: Boolean = false): Boolean {
        val result = downloader.syncIfNeeded(force)
        if (cachedCityLocations.isEmpty()) {
            loadCityLocations()
        }
        return result
    }

    suspend fun refreshExpiredImagesOnStartup() = withContext(Dispatchers.IO) {
        try {
            val all = dao.getAllDirect()
            val expired = all.filter { isImageExpired(it) }
            if (expired.isEmpty()) return@withContext

            val sorted = expired.sortedWith(
                compareByDescending<RestaurantEntity> { it.isFavorite }
                    .thenByDescending { it.isVisited }
            )

            for (restaurant in sorted) {
                try {
                    scraper.fetchAndStoreDetails(restaurant)
                } catch (e: Exception) {
                    Log.w(TAG, "Startup image refresh failed for ${restaurant.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing images on startup", e)
        }
    }

    private suspend fun loadCityLocations() = withContext(Dispatchers.IO) {
        val all = dao.getAllDirect()
        val byCity = all.groupBy { it.location }.filterKeys { it.isNotBlank() }
        cachedCityLocations = byCity.map { (city, list) ->
            val avgLat = list.map { it.latitude }.average()
            val avgLng = list.map { it.longitude }.average()
            LocationTarget(name = city, latitude = avgLat, longitude = avgLng)
        }
    }

    suspend fun searchAutocomplete(query: String): List<AutocompleteSuggestion> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()

            val suggestions = mutableListOf<AutocompleteSuggestion>()

            // 1. Search in cached Michelin cities
            if (cachedCityLocations.isEmpty()) {
                loadCityLocations()
            }
            val matchingCities = cachedCityLocations.filter {
                it.name.contains(q, ignoreCase = true)
            }.take(5)

            matchingCities.forEach { city ->
                suggestions.add(
                    AutocompleteSuggestion(
                        title = city.name,
                        subtitle = "Michelin destination",
                        latitude = city.latitude,
                        longitude = city.longitude,
                        isCity = true
                    )
                )
            }

            // 2. Geocoder search for any global place/address
            val geocoded = LocationHelper.geocodeLocation(context, q)
            geocoded.forEach { target ->
                if (suggestions.none { s -> LocationHelper.calculateDistanceKm(s.latitude, s.longitude, target.latitude, target.longitude) < 5.0 }) {
                    suggestions.add(
                        AutocompleteSuggestion(
                            title = target.name,
                            subtitle = "Location",
                            latitude = target.latitude,
                            longitude = target.longitude,
                            isCity = false
                        )
                    )
                }
            }

            return@withContext suggestions.take(8)
        }

    fun getRestaurantById(id: Long): Flow<RestaurantEntity?> = dao.getRestaurantById(id)

    suspend fun toggleFavorite(restaurant: RestaurantEntity) = withContext(Dispatchers.IO) {
        val newFav = !restaurant.isFavorite
        dao.updateFavorite(
            restaurant.id,
            newFav,
            if (newFav) System.currentTimeMillis() else 0L
        )
    }

    suspend fun toggleVisited(restaurant: RestaurantEntity, notes: String = "") = withContext(Dispatchers.IO) {
        val newVisited = !restaurant.isVisited
        dao.updateVisited(
            restaurant.id,
            newVisited,
            if (newVisited) System.currentTimeMillis() else 0L,
            if (newVisited) notes else ""
        )
    }

    fun isImageExpired(restaurant: RestaurantEntity): Boolean {
        return scraper.isImageExpired(restaurant)
    }

    suspend fun fetchRestaurantDetails(restaurant: RestaurantEntity): RestaurantEntity {
        return scraper.fetchAndStoreDetails(restaurant)
    }

    /**
     * Exports all favorite and visited restaurants as a standard JSON string.
     */
    suspend fun exportFavoritesAndVisitedJson(): String = withContext(Dispatchers.IO) {
        val all = dao.getAllDirect()
        val exported = all.filter { it.isFavorite || it.isVisited }

        val root = JSONObject()
        root.put("app", "Foodie 2.0")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("totalItems", exported.size)

        val array = JSONArray()
        for (item in exported) {
            val obj = JSONObject()
            obj.put("name", item.name)
            obj.put("url", item.url)
            obj.put("address", item.address)
            obj.put("location", item.location)
            obj.put("isFavorite", item.isFavorite)
            obj.put("favoriteTimestamp", item.favoriteTimestamp)
            obj.put("isVisited", item.isVisited)
            obj.put("visitedTimestamp", item.visitedTimestamp)
            obj.put("visitedNotes", item.visitedNotes)
            array.put(obj)
        }
        root.put("restaurants", array)
        return@withContext root.toString(2)
    }

    /**
     * Imports favorites and visited from a JSON string.
     * Returns the count of successfully updated records.
     */
    suspend fun importFavoritesAndVisitedJson(jsonString: String): Int = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val array = root.optJSONArray("restaurants") ?: return@withContext 0
            val all = dao.getAllDirect()

            // Overwrite any existing favourites and visited
            dao.clearAllFavoritesAndVisited()

            // Index local DB by url and name
            val byUrl = all.associateBy { it.url }
            val byName = all.associateBy { it.name.lowercase().trim() }

            var updatedCount = 0
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val url = obj.optString("url")
                val name = obj.optString("name")
                val isFav = obj.optBoolean("isFavorite", false)
                val favTime = obj.optLong("favoriteTimestamp", System.currentTimeMillis())
                val isVisited = obj.optBoolean("isVisited", false)
                val visitedTime = obj.optLong("visitedTimestamp", System.currentTimeMillis())
                val notes = obj.optString("visitedNotes", "")

                val target = byUrl[url] ?: byName[name.lowercase().trim()]
                if (target != null) {
                    if (isFav) {
                        dao.updateFavorite(target.id, true, favTime)
                    }
                    if (isVisited) {
                        dao.updateVisited(target.id, true, visitedTime, notes)
                    }
                    updatedCount++
                }
            }
            return@withContext updatedCount
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import JSON", e)
            return@withContext -1
        }
    }
}
