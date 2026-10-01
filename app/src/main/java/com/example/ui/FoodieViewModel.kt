package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FoodieApp
import com.example.data.local.RestaurantEntity
import com.example.data.network.SyncState
import com.example.data.repository.AutocompleteSuggestion
import com.example.data.repository.RestaurantRepository
import com.example.location.LocationHelper
import com.example.location.LocationTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MichelinAwardFilter(val label: String, val dbMatch: String) {
    THREE_STARS("3 Stars", "3 Stars"),
    TWO_STARS("2 Stars", "2 Stars"),
    ONE_STAR("1 Star", "1 Star"),
    BIB_GOURMAND("Bib Gourmand", "Bib Gourmand"),
    SELECTED("Selected", "Selected Restaurants")
}

enum class SpecialListMode {
    ALL,
    FAVORITES_ONLY,
    VISITED_ONLY
}

data class RestaurantWithDistance(
    val restaurant: RestaurantEntity,
    val distanceKm: Double?
)

data class UiState(
    val searchQuery: String = "",
    val activeLocation: LocationTarget? = null,
    val mapRecenterTrigger: Long = 0L,
    val selectedFilters: Set<MichelinAwardFilter> = emptySet(),
    val availableCuisines: List<String> = emptyList(),
    val selectedCuisine: String? = null,
    val specialMode: SpecialListMode = SpecialListMode.ALL,
    val selectedTabIndex: Int = 0, // 0 = List, 1 = Map
    val autocompleteSuggestions: List<AutocompleteSuggestion> = emptyList(),
    val isSearchingAutocomplete: Boolean = false,
    val isLocatingGps: Boolean = false,
    val gpsErrorMessage: String? = null,
    val selectedRestaurantForDetails: RestaurantEntity? = null,
    val showBackupDialog: Boolean = false,
    val backupJsonText: String? = null,
    val toastMessage: String? = null
)

class FoodieViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RestaurantRepository =
        (application as FoodieApp).repository

    private val prefs =
        application.getSharedPreferences("foodie_ui_state", Context.MODE_PRIVATE)

    val syncState: StateFlow<SyncState> = repository.syncState

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Scroll state preservation across tabs & navigation
    var preservedListIndex: Int = 0
    var preservedListOffset: Int = 0

    // Map camera position preservation across tabs & navigation
    var lastMapCameraPosition: com.google.android.gms.maps.model.CameraPosition? = null
    var lastHandledRecenterTrigger: Long = 0L

    private var autocompleteJob: Job? = null
    private val downloadingIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    init {
        restoreLastState()
        viewModelScope.launch {
            repository.syncIfNeeded(force = false)
            repository.refreshExpiredImagesOnStartup()
        }
    }

    fun ensureRestaurantImageDownloaded(restaurant: RestaurantEntity) {
        if (repository.isImageExpired(restaurant) && downloadingIds.add(restaurant.id)) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    repository.fetchRestaurantDetails(restaurant)
                } catch (e: Exception) {
                    android.util.Log.w("FoodieViewModel", "Image download failed for ${restaurant.name}: ${e.message}")
                } finally {
                    downloadingIds.remove(restaurant.id)
                }
            }
        }
    }

    fun onRestaurantsListed(restaurants: List<RestaurantEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            // Prioritize first 25 listed items
            for (r in restaurants.take(25)) {
                if (repository.isImageExpired(r) && downloadingIds.add(r.id)) {
                    try {
                        repository.fetchRestaurantDetails(r)
                    } catch (_: Exception) {}
                    finally {
                        downloadingIds.remove(r.id)
                    }
                }
            }
        }
    }

    private fun restoreLastState() {
        val lastQuery = prefs.getString("last_search_query", "") ?: ""
        val lastLocName = prefs.getString("last_loc_name", null)
        val lastLat = prefs.getFloat("last_loc_lat", Float.MIN_VALUE)
        val lastLng = prefs.getFloat("last_loc_lng", Float.MIN_VALUE)
        val lastTab = prefs.getInt("last_selected_tab", 0)

        val targetLoc = if (lastLocName != null && lastLat != Float.MIN_VALUE && lastLng != Float.MIN_VALUE) {
            LocationTarget(
                name = lastLocName,
                latitude = lastLat.toDouble(),
                longitude = lastLng.toDouble()
            )
        } else {
            // Default to Paris, a premier Michelin destination, if first open
            LocationTarget(
                name = "Paris, France",
                latitude = 48.8566,
                longitude = 2.3522
            )
        }

        _uiState.value = _uiState.value.copy(
            searchQuery = lastQuery,
            activeLocation = targetLoc,
            selectedTabIndex = lastTab
        )
    }

    private fun saveCurrentState() {
        val s = _uiState.value
        prefs.edit().apply {
            putString("last_search_query", s.searchQuery)
            putInt("last_selected_tab", s.selectedTabIndex)
            s.activeLocation?.let {
                putString("last_loc_name", it.name)
                putFloat("last_loc_lat", it.latitude.toFloat())
                putFloat("last_loc_lng", it.longitude.toFloat())
            }
            apply()
        }
    }

    // Combine raw restaurants from Room with filters and 30km distance limit
    val filteredRestaurants: StateFlow<List<RestaurantWithDistance>> = combine(
        repository.allRestaurants,
        _uiState
    ) { all, state ->
        withContext(Dispatchers.Default) {
            var list = all

            // Special list mode (Favorites / Visited)
            list = when (state.specialMode) {
                SpecialListMode.FAVORITES_ONLY -> list.filter { it.isFavorite }
                SpecialListMode.VISITED_ONLY -> list.filter { it.isVisited }
                SpecialListMode.ALL -> list
            }

            // Award Filters (multi-select)
            if (state.selectedFilters.isNotEmpty()) {
                val dbMatches = state.selectedFilters.map { it.dbMatch }
                list = list.filter { r ->
                    dbMatches.any { match -> r.award.contains(match, ignoreCase = true) }
                }
            }

            // Text search if entered (restaurant name, cuisine, location, or address)
            if (state.searchQuery.isNotBlank() && state.searchQuery.length >= 2) {
                val q = state.searchQuery.lowercase().trim()
                list = list.filter { r ->
                    r.name.lowercase().contains(q) ||
                            r.cuisine.lowercase().contains(q) ||
                            r.location.lowercase().contains(q) ||
                            r.address.lowercase().contains(q)
                }
            }

            // Distance calculation & 30km limit
            val loc = state.activeLocation
            val baseList: List<RestaurantWithDistance> = if (loc != null) {
                val withDist = list.mapNotNull { r ->
                    val dist = LocationHelper.calculateDistanceKm(
                        loc.latitude, loc.longitude,
                        r.latitude, r.longitude
                    )
                    // If in Special mode (Favorites/Visited) or general location search:
                    if (state.specialMode != SpecialListMode.ALL) {
                        RestaurantWithDistance(r, dist)
                    } else if (dist <= LocationHelper.MAX_RADIUS_KM) {
                        RestaurantWithDistance(r, dist)
                    } else {
                        null
                    }
                }
                withDist.sortedBy { it.distanceKm }
            } else {
                list.map { RestaurantWithDistance(it, null) }
            }

            // Extract unique cuisines strictly from the results
            val cuisinesForResults = baseList
                .flatMap { item ->
                    item.restaurant.cuisine.split(",", "/", "&")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                }
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)

            // Keep availableCuisines synchronized with the results
            if (state.availableCuisines != cuisinesForResults) {
                val selectedStillValid = state.selectedCuisine != null &&
                        cuisinesForResults.any { it.equals(state.selectedCuisine, ignoreCase = true) }
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    _uiState.value = _uiState.value.copy(
                        availableCuisines = cuisinesForResults,
                        selectedCuisine = if (selectedStillValid) state.selectedCuisine else null
                    )
                }
            }

            // Finally, filter by cuisine if selected
            if (!state.selectedCuisine.isNullOrBlank()) {
                val selected = state.selectedCuisine.trim().lowercase()
                baseList.filter { r ->
                    r.restaurant.cuisine.lowercase().contains(selected)
                }
            } else {
                baseList
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        autocompleteJob?.cancel()

        if (query.trim().length >= 2) {
            _uiState.value = _uiState.value.copy(isSearchingAutocomplete = true)
            autocompleteJob = viewModelScope.launch {
                delay(250) // Debounce
                val results = repository.searchAutocomplete(query)
                _uiState.value = _uiState.value.copy(
                    autocompleteSuggestions = results,
                    isSearchingAutocomplete = false
                )
            }
        } else {
            _uiState.value = _uiState.value.copy(
                autocompleteSuggestions = emptyList(),
                isSearchingAutocomplete = false
            )
        }
        saveCurrentState()
    }

    /**
     * Requirement: When clicking on the search tab/field, clear old text automatically.
     */
    fun onSearchFieldClicked() {
        if (_uiState.value.searchQuery.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                searchQuery = "",
                autocompleteSuggestions = emptyList()
            )
            saveCurrentState()
        }
    }

    fun onAutocompleteSelected(suggestion: AutocompleteSuggestion) {
        val target = LocationTarget(
            name = suggestion.title,
            latitude = suggestion.latitude,
            longitude = suggestion.longitude,
            isGpsLocation = false
        )
        lastMapCameraPosition = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
            com.google.android.gms.maps.model.LatLng(target.latitude, target.longitude),
            12f
        )
        _uiState.value = _uiState.value.copy(
            activeLocation = target,
            mapRecenterTrigger = System.currentTimeMillis(),
            searchQuery = "",
            autocompleteSuggestions = emptyList(),
            isSearchingAutocomplete = false
        )
        saveCurrentState()
    }

    fun onNearMeClicked() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLocatingGps = true, gpsErrorMessage = null)
            val loc = LocationHelper.getCurrentLocation(getApplication())
            if (loc != null) {
                lastMapCameraPosition = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                    com.google.android.gms.maps.model.LatLng(loc.latitude, loc.longitude),
                    12f
                )
                _uiState.value = _uiState.value.copy(
                    activeLocation = loc,
                    mapRecenterTrigger = System.currentTimeMillis(),
                    searchQuery = "",
                    autocompleteSuggestions = emptyList(),
                    isLocatingGps = false,
                    toastMessage = "Located: ${loc.name}"
                )
                saveCurrentState()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLocatingGps = false,
                    gpsErrorMessage = "Unable to fetch GPS location. Please check location permissions or select a city."
                )
            }
        }
    }

    fun setLocationManually(name: String, lat: Double, lng: Double) {
        val target = LocationTarget(name, lat, lng)
        lastMapCameraPosition = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
            com.google.android.gms.maps.model.LatLng(lat, lng),
            12f
        )
        _uiState.value = _uiState.value.copy(
            activeLocation = target,
            mapRecenterTrigger = System.currentTimeMillis(),
            searchQuery = "",
            autocompleteSuggestions = emptyList()
        )
        saveCurrentState()
    }

    fun toggleFilter(filter: MichelinAwardFilter) {
        val current = _uiState.value.selectedFilters.toMutableSet()
        if (current.contains(filter)) {
            current.remove(filter)
        } else {
            current.add(filter)
        }
        _uiState.value = _uiState.value.copy(selectedFilters = current)
    }

    fun clearAllFilters() {
        _uiState.value = _uiState.value.copy(selectedFilters = emptySet(), selectedCuisine = null)
    }

    fun selectCuisine(cuisine: String?) {
        _uiState.value = _uiState.value.copy(selectedCuisine = cuisine)
    }

    fun setSpecialMode(mode: SpecialListMode) {
        _uiState.value = _uiState.value.copy(specialMode = mode)
    }

    fun selectTab(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTabIndex = tabIndex)
        saveCurrentState()
    }

    fun openRestaurantDetails(restaurant: RestaurantEntity) {
        _uiState.value = _uiState.value.copy(selectedRestaurantForDetails = restaurant)
        // Trigger background scrape for fresh opening hours and downloaded image if needed
        viewModelScope.launch {
            val updated = repository.fetchRestaurantDetails(restaurant)
            if (_uiState.value.selectedRestaurantForDetails?.id == restaurant.id) {
                _uiState.value = _uiState.value.copy(selectedRestaurantForDetails = updated)
            }
        }
    }

    fun closeRestaurantDetails() {
        _uiState.value = _uiState.value.copy(selectedRestaurantForDetails = null)
    }

    fun toggleFavorite(restaurant: RestaurantEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(restaurant)
            // If details is currently open for this restaurant, update it
            if (_uiState.value.selectedRestaurantForDetails?.id == restaurant.id) {
                _uiState.value = _uiState.value.copy(
                    selectedRestaurantForDetails = restaurant.copy(
                        isFavorite = !restaurant.isFavorite
                    )
                )
            }
        }
    }

    fun toggleVisited(restaurant: RestaurantEntity, notes: String = "") {
        viewModelScope.launch {
            repository.toggleVisited(restaurant, notes)
            if (_uiState.value.selectedRestaurantForDetails?.id == restaurant.id) {
                _uiState.value = _uiState.value.copy(
                    selectedRestaurantForDetails = restaurant.copy(
                        isVisited = !restaurant.isVisited,
                        visitedNotes = notes
                    )
                )
            }
        }
    }

    fun showBackupDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showBackupDialog = show)
        if (show) {
            viewModelScope.launch {
                val json = repository.exportFavoritesAndVisitedJson()
                _uiState.value = _uiState.value.copy(backupJsonText = json)
            }
        }
    }

    fun onBackupCompleted(success: Boolean, message: String) {
        _uiState.value = _uiState.value.copy(
            showBackupDialog = false,
            toastMessage = message
        )
    }

    fun importBackup(jsonString: String) {
        viewModelScope.launch {
            val count = repository.importFavoritesAndVisitedJson(jsonString)
            if (count >= 0) {
                _uiState.value = _uiState.value.copy(
                    showBackupDialog = false,
                    toastMessage = "Successfully imported $count restaurant bookmarks!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    toastMessage = "Failed to import JSON: Invalid format"
                )
            }
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null, gpsErrorMessage = null)
    }
}
