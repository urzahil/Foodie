package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FoodieApp
import com.example.data.local.RestaurantEntity
import com.example.data.local.RestaurantListItem
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class MichelinAwardFilter(val label: String, val dbMatch: String) {
    THREE_STARS("3 Stars", "3 Stars"),
    TWO_STARS("2 Stars", "2 Stars"),
    ONE_STAR("1 Star", "1 Star"),
    BIB_GOURMAND("Bib Gourmand", "Bib Gourmand"),
    SELECTED("Selected", "Selected Restaurants")
}

enum class PriceFilter(val level: Int, val label: String) {
    ONE_DOLLAR(1, "$"),
    TWO_DOLLARS(2, "$$"),
    THREE_DOLLARS(3, "$$$"),
    FOUR_DOLLARS(4, "$$$$")
}

enum class SpecialListMode {
    ALL,
    FAVORITES_ONLY,
    VISITED_ONLY
}

data class RestaurantWithDistance(
    val restaurant: RestaurantListItem,
    val distanceKm: Double?
)

private data class FilterParams(
    val query: String,
    val location: LocationTarget?,
    val awards: Set<MichelinAwardFilter>,
    val prices: Set<PriceFilter>,
    val cuisine: String?,
    val mode: SpecialListMode
)

private data class FilteredResult(
    val items: List<RestaurantWithDistance>,
    val availableCuisines: List<String>
)

data class UiState(
    /** Search text currently being edited; does not affect the restaurant list until submitted. */
    val searchInputQuery: String = "",
    /** Last search text that was actually submitted. */
    val searchQuery: String = "",
    val activeLocation: LocationTarget? = null,
    val mapRecenterTrigger: Long = 0L,
    val listResetTrigger: Long = 0L,
    val selectedFilters: Set<MichelinAwardFilter> = emptySet(),
    val selectedPriceFilters: Set<PriceFilter> = emptySet(),
    val selectedCuisine: String? = null,
    val specialMode: SpecialListMode = SpecialListMode.ALL,
    val selectedTabIndex: Int = 0, // 0 = List, 1 = Map
    val autocompleteSuggestions: List<AutocompleteSuggestion> = emptyList(),
    val isSearchingAutocomplete: Boolean = false,
    val isLocatingGps: Boolean = false,
    val gpsErrorMessage: String? = null,
    val selectedRestaurantId: Long? = null,
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

    private val _lastCatalogueSyncTime = MutableStateFlow(repository.getLastCatalogueSyncTime())
    val lastCatalogueSyncTime: StateFlow<Long> = _lastCatalogueSyncTime.asStateFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Scroll state preservation across tabs & navigation
    var preservedListIndex: Int = 0
    var preservedListOffset: Int = 0

    // Map camera position preservation across tabs & navigation
    var lastMapCameraPosition: com.google.android.gms.maps.model.CameraPosition? = null
    var lastHandledRecenterTrigger: Long = 0L

    private var autocompleteJob: Job? = null
    private val imageDownloadSemaphore = Semaphore(3)
    private val imageRetryAfter = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    init {
        restoreLastState()
        viewModelScope.launch {
            // Sync metadata only. Images/details are fetched on demand for visible
            // restaurants or when the user opens a restaurant.
            repository.syncIfNeeded(force = false)
            _lastCatalogueSyncTime.value = repository.getLastCatalogueSyncTime()
        }
    }

    private fun restoreLastState() {
        val lastQuery = prefs.getString("last_search_query", "") ?: ""
        val lastLocName = prefs.getString("last_loc_name", null)
        val lastLat = prefs.getFloat("last_loc_lat", Float.MIN_VALUE)
        val lastLng = prefs.getFloat("last_loc_lng", Float.MIN_VALUE)
        val lastTab = prefs.getInt("last_selected_tab", 0)

        val targetLoc =
            if (lastLocName != null && lastLat != Float.MIN_VALUE && lastLng != Float.MIN_VALUE) {
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
            searchInputQuery = "",
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

    private val filterParams: StateFlow<FilterParams> = _uiState
        .map {
            FilterParams(
                query = it.searchQuery,
                location = it.activeLocation,
                awards = it.selectedFilters,
                prices = it.selectedPriceFilters,
                cuisine = it.selectedCuisine,
                mode = it.specialMode
            )
        }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            FilterParams("", null, emptySet(), emptySet(), null, SpecialListMode.ALL)
        )

    private fun candidatesFor(params: FilterParams): kotlinx.coroutines.flow.Flow<List<RestaurantListItem>> {
        val loc = params.location
        if (params.mode != SpecialListMode.ALL || loc == null) {
            return repository.allRestaurants
        }

        val radius = LocationHelper.MAX_RADIUS_KM
        val latDelta = radius / 111.0
        val cosLat = kotlin.math.cos(Math.toRadians(loc.latitude)).coerceAtLeast(0.01)
        val lngDelta = radius / (111.0 * cosLat)
        val minLat = (loc.latitude - latDelta).coerceIn(-90.0, 90.0)
        val maxLat = (loc.latitude + latDelta).coerceIn(-90.0, 90.0)
        val minLng = loc.longitude - lngDelta
        val maxLng = loc.longitude + lngDelta

        return if (minLng < -180.0 || maxLng > 180.0) {
            // Dateline-crossing locations are rare; fall back to the slim projection
            // rather than issuing an incorrect longitude range.
            repository.allRestaurants
        } else {
            repository.observeInBox(minLat, maxLat, minLng, maxLng)
        }
    }

    private fun applyFilters(
        candidates: List<RestaurantListItem>,
        params: FilterParams
    ): FilteredResult {
        var list = candidates

        if (params.mode == SpecialListMode.FAVORITES_ONLY) {
            list = list.filter { it.isFavorite }
        } else if (params.mode == SpecialListMode.VISITED_ONLY) {
            list = list.filter { it.isVisited }
        }

        if (params.awards.isNotEmpty()) {
            val matches = params.awards.map { it.dbMatch }
            list = list.filter { r -> matches.any { r.award.contains(it, ignoreCase = true) } }
        }

        if (params.prices.isNotEmpty()) {
            list = list.filter { r -> matchesPrice(r.price, params.prices) }
        }

        if (params.query.isNotBlank() && params.query.length >= 2) {
            val q = params.query.lowercase().trim()
            list = list.filter {
                it.name.lowercase().contains(q) ||
                        it.cuisine.lowercase().contains(q) ||
                        it.location.lowercase().contains(q) ||
                        it.address.lowercase().contains(q)
            }
        }

        val loc = params.location
        val withDistance = if (loc != null) {
            list.mapNotNull { r ->
                val distance = LocationHelper.calculateDistanceKm(
                    loc.latitude, loc.longitude, r.latitude, r.longitude
                )
                if (params.mode != SpecialListMode.ALL || distance <= LocationHelper.MAX_RADIUS_KM) {
                    RestaurantWithDistance(r, distance)
                } else null
            }.sortedBy { it.distanceKm }
        } else {
            list.map { RestaurantWithDistance(it, null) }
        }

        val cuisines = withDistance
            .flatMap { it.restaurant.cuisine.split(",", "/", "&") }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        val finalItems = if (!params.cuisine.isNullOrBlank()) {
            val selected = params.cuisine.trim().lowercase()
            withDistance.filter { it.restaurant.cuisine.lowercase().contains(selected) }
        } else {
            withDistance
        }

        return FilteredResult(finalItems, cuisines)
    }

    private val filteredResult: StateFlow<FilteredResult> = filterParams
        .flatMapLatest { params ->
            candidatesFor(params)
                .map { candidates -> applyFilters(candidates, params) }
                .flowOn(Dispatchers.Default)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            FilteredResult(emptyList(), emptyList())
        )

    val filteredRestaurants: StateFlow<List<RestaurantWithDistance>> = filteredResult
        .map { it.items }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableCuisines: StateFlow<List<String>> = filteredResult
        .map { it.availableCuisines }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedRestaurant: StateFlow<RestaurantEntity?> = _uiState
        .map { it.selectedRestaurantId }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.getRestaurantById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun ensureRestaurantImageDownloaded(restaurant: RestaurantListItem) {
        if (restaurant.imageUrl?.isNotBlank() == true) return
        val now = System.currentTimeMillis()
        val retryAt = imageRetryAfter[restaurant.id] ?: 0L
        if (now < retryAt) return

        viewModelScope.launch {
            imageDownloadSemaphore.withPermit {
                if (restaurant.imageUrl?.isNotBlank() == true) return@withPermit
                try {
                    withContext(Dispatchers.IO) {
                        val current = repository.getRestaurantById(restaurant.id).first()
                            ?: return@withContext
                        repository.fetchRestaurantDetails(current)
                    }
                    imageRetryAfter.remove(restaurant.id)
                } catch (e: Exception) {
                    imageRetryAfter[restaurant.id] = System.currentTimeMillis() + 10 * 60 * 1000L
                    android.util.Log.w(
                        "FoodieViewModel",
                        "Image metadata fetch failed for ${restaurant.name}: ${e.message}"
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        // Keep the current restaurant list intact while the user is typing.
        _uiState.value = _uiState.value.copy(
            searchInputQuery = query
        )
        autocompleteJob?.cancel()

        if (query.trim().length >= 2) {
            _uiState.value = _uiState.value.copy(isSearchingAutocomplete = true)
            autocompleteJob = viewModelScope.launch {
                delay(250)
                val results = repository.searchAutocomplete(query)
                // Ignore stale autocomplete results if the user has already changed
                // the text again.
                if (_uiState.value.searchInputQuery == query) {
                    _uiState.value = _uiState.value.copy(
                        autocompleteSuggestions = results,
                        isSearchingAutocomplete = false
                    )
                }
            }
        } else {
            _uiState.value = _uiState.value.copy(
                autocompleteSuggestions = emptyList(),
                isSearchingAutocomplete = false
            )
        }
    }

    fun submitSearch() {
        val query = _uiState.value.searchInputQuery.trim()
        val suggestions = _uiState.value.autocompleteSuggestions

        if (suggestions.isNotEmpty()) {
            onAutocompleteSelected(suggestions.first())
            return
        }

        // No location suggestion: commit the typed text as the restaurant search.
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            searchInputQuery = query,
            autocompleteSuggestions = emptyList(),
            isSearchingAutocomplete = false,
            listResetTrigger = _uiState.value.listResetTrigger + 1
        )
        saveCurrentState()
    }

    fun cancelSearch() {
        // Discard the unsubmitted text and leave the currently displayed restaurants
        // exactly as they are.
        autocompleteJob?.cancel()
        _uiState.value = _uiState.value.copy(
            searchInputQuery = "",
            autocompleteSuggestions = emptyList(),
            isSearchingAutocomplete = false
        )
    }

    /**
     * Requirement: When clicking on the search tab/field, clear old text automatically.
     */
    fun onSearchFieldClicked() {
        // Starting a new search clears only the editable draft. The executed
        // search and its restaurant list remain until the new search is submitted.
        if (_uiState.value.searchInputQuery.isNotEmpty()) {
            cancelSearch()
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
            listResetTrigger = _uiState.value.listResetTrigger + 1,
            searchQuery = "",
            searchInputQuery = "",
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
                lastMapCameraPosition =
                    com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
                        com.google.android.gms.maps.model.LatLng(loc.latitude, loc.longitude),
                        12f
                    )
                _uiState.value = _uiState.value.copy(
                    activeLocation = loc,
                    mapRecenterTrigger = System.currentTimeMillis(),
                    listResetTrigger = _uiState.value.listResetTrigger + 1,
                    searchQuery = "",
                    searchInputQuery = "",
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
            listResetTrigger = _uiState.value.listResetTrigger + 1,
            searchQuery = "",
            searchInputQuery = "",
            autocompleteSuggestions = emptyList()
        )
        saveCurrentState()
    }

    private fun matchesPrice(priceStr: String, selectedPrices: Set<PriceFilter>): Boolean {
        if (selectedPrices.isEmpty()) return true
        if (priceStr.isBlank()) return false

        val levels = selectedPrices.map { it.level }.toSet()

        val currencySymbols = setOf('$', '€', '£', '¥', '￥', '₩', '₺', '₹')
        val symbolCount = priceStr.count { it in currencySymbols }
        if (symbolCount in 1..4) {
            return levels.contains(symbolCount)
        }

        if (priceStr.contains("$$$$") || priceStr.contains("€€€€") || priceStr.contains("££££") || priceStr.contains("¥¥¥¥")) {
            return levels.contains(4)
        }
        if (priceStr.contains("$$$") || priceStr.contains("€€€") || priceStr.contains("£££") || priceStr.contains("¥¥¥")) {
            return levels.contains(3)
        }
        if (priceStr.contains("$$") || priceStr.contains("€€") || priceStr.contains("££") || priceStr.contains("¥¥")) {
            return levels.contains(2)
        }
        if (priceStr.contains("$") || priceStr.contains("€") || priceStr.contains("£") || priceStr.contains("¥")) {
            return levels.contains(1)
        }

        val trimmed = priceStr.trim()
        if (trimmed.length in 1..4 && trimmed.none { it.isDigit() }) {
            return levels.contains(trimmed.length)
        }

        return false
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

    fun togglePriceFilter(filter: PriceFilter) {
        val current = _uiState.value.selectedPriceFilters.toMutableSet()
        if (current.contains(filter)) {
            current.remove(filter)
        } else {
            current.add(filter)
        }
        _uiState.value = _uiState.value.copy(selectedPriceFilters = current)
    }

    fun clearAwardAndPriceFilters() {
        _uiState.value = _uiState.value.copy(
            selectedFilters = emptySet(),
            selectedPriceFilters = emptySet()
        )
    }

    fun clearAllFilters() {
        _uiState.value = _uiState.value.copy(
            selectedFilters = emptySet(),
            selectedPriceFilters = emptySet(),
            selectedCuisine = null
        )
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

    fun openRestaurantDetails(restaurant: RestaurantListItem) {
        _uiState.value = _uiState.value.copy(selectedRestaurantId = restaurant.id)
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.getRestaurantById(restaurant.id).first() ?: return@launch
            repository.fetchRestaurantDetails(current)
        }
    }

    fun closeRestaurantDetails() {
        _uiState.value = _uiState.value.copy(selectedRestaurantId = null)
    }

    fun toggleFavorite(restaurant: RestaurantListItem) {
        viewModelScope.launch { repository.toggleFavorite(restaurant.id) }
    }

    fun toggleFavorite(restaurant: RestaurantEntity) {
        viewModelScope.launch { repository.toggleFavorite(restaurant.id) }
    }

    fun toggleVisited(restaurant: RestaurantListItem, notes: String = "") {
        viewModelScope.launch { repository.toggleVisited(restaurant.id, notes) }
    }

    fun toggleVisited(restaurant: RestaurantEntity, notes: String = "") {
        viewModelScope.launch { repository.toggleVisited(restaurant.id, notes) }
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
