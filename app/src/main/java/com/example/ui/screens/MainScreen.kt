package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.network.SyncState
import com.example.ui.FoodieViewModel
import com.example.ui.SpecialListMode
import com.example.ui.components.CuisineDropdownFilter
import com.example.ui.components.ExportImportDialog
import com.example.ui.components.MichelinFilterChips
import com.example.ui.components.SearchBarWithNearMe
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.GreenStarColor
import com.example.ui.theme.MichelinGold
import com.example.ui.theme.MichelinRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: FoodieViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredList by viewModel.filteredRestaurants.collectAsStateWithLifecycle()
    val availableCuisines by viewModel.availableCuisines.collectAsStateWithLifecycle()
    val selectedRestaurant by viewModel.selectedRestaurant.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Runtime location permission launcher for Near Me
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.onNearMeClicked()
        } else {
            Toast.makeText(
                context,
                "Location permission denied. You can still search any city manually.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    LaunchedEffect(uiState.gpsErrorMessage) {
        uiState.gpsErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }

    // Details observes the selected restaurant by ID, so favorite/visited/image/hour updates
    // are always rendered from the Room source of truth.
    if (selectedRestaurant != null) {
        RestaurantDetailsScreen(
            restaurant = selectedRestaurant,
            onBack = { viewModel.closeRestaurantDetails() },
            onToggleFavorite = { viewModel.toggleFavorite(selectedRestaurant) },
            onToggleVisited = { notes -> viewModel.toggleVisited(selectedRestaurant, notes) }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("main_screen"),
        containerColor = DarkBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(
                color = DarkBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Foodie",
                            color = TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = " 2.0",
                            color = MichelinRed,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (uiState.specialMode != SpecialListMode.ALL) {
                            Text(
                                text = if (uiState.specialMode == SpecialListMode.FAVORITES_ONLY) " • Favourites" else " • Visited",
                                color = if (uiState.specialMode == SpecialListMode.FAVORITES_ONLY) MichelinRed else GreenStarColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("top_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options menu",
                                tint = Color.White
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(DarkSurfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Restaurants", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.RestaurantMenu, contentDescription = null, tint = MichelinRed) },
                                onClick = {
                                    viewModel.setSpecialMode(SpecialListMode.ALL)
                                    showMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("My Favourites", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Favorite, contentDescription = null, tint = MichelinRed) },
                                onClick = {
                                    viewModel.setSpecialMode(SpecialListMode.FAVORITES_ONLY)
                                    showMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Visited Places", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenStarColor) },
                                onClick = {
                                    viewModel.setSpecialMode(SpecialListMode.VISITED_ONLY)
                                    showMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Backup Favourites/Visited (JSON)", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null, tint = MichelinGold) },
                                onClick = {
                                    viewModel.showBackupDialog(true)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Presentation in Two Tabs: List and Map (compact height)
            Surface(
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF2C2C2C)),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab 0: List
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { viewModel.selectTab(0) }
                            .testTag("tab_list"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (uiState.selectedTabIndex == 0) Color(0xFF3F0B09) else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatListBulleted,
                                contentDescription = "List View",
                                tint = if (uiState.selectedTabIndex == 0) MichelinRed else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "List",
                                color = if (uiState.selectedTabIndex == 0) MichelinRed else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Tab 1: Map
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable { viewModel.selectTab(1) }
                            .testTag("tab_map"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (uiState.selectedTabIndex == 1) Color(0xFF3F0B09) else Color.Transparent)
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "Map View",
                                tint = if (uiState.selectedTabIndex == 1) MichelinRed else TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Map",
                                color = if (uiState.selectedTabIndex == 1) MichelinRed else TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Sync status banner if syncing
            when (val s = syncState) {
                is SyncState.Syncing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF261212))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MichelinRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = s.message,
                                color = Color(0xFFFFCDD2),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = MichelinRed,
                            trackColor = Color(0xFF4A1916)
                        )
                    }
                }
                is SyncState.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF3E1210))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.message,
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                else -> {}
            }

            // Top Search Bar with Autocomplete & Near Me button
            SearchBarWithNearMe(
                query = uiState.searchInputQuery,
                onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                onSearchSubmitted = { viewModel.submitSearch() },
                onSearchCancelled = { viewModel.cancelSearch() },
                onSearchFieldClicked = { viewModel.onSearchFieldClicked() },
                activeLocation = uiState.activeLocation,
                suggestions = uiState.autocompleteSuggestions,
                onSuggestionSelected = { viewModel.onAutocompleteSelected(it) },
                isSearchingAutocomplete = uiState.isSearchingAutocomplete,
                isLocatingGps = uiState.isLocatingGps,
                onNearMeClicked = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            )

            // Filter row under search bar: 3 Stars, 2 Stars, 1 Star, Bib Gourmand, Selected
            MichelinFilterChips(
                selectedFilters = uiState.selectedFilters,
                onToggleFilter = { viewModel.toggleFilter(it) }
            )

            // Cuisine dropdown filter
            CuisineDropdownFilter(
                availableCuisines = availableCuisines,
                selectedCuisine = uiState.selectedCuisine,
                onCuisineSelected = { viewModel.selectCuisine(it) }
            )

            // Content Tabs: List (tab 0) and Map (tab 1)
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (uiState.selectedTabIndex == 0) {
                    RestaurantListTab(
                        restaurants = filteredList,
                        activeLocation = uiState.activeLocation,
                        viewModel = viewModel,
                        onRestaurantClick = { viewModel.openRestaurantDetails(it) },
                        onSelectCity = { name, lat, lng -> viewModel.setLocationManually(name, lat, lng) }
                    )
                } else {
                    RestaurantMapTab(
                        restaurants = filteredList,
                        activeLocation = uiState.activeLocation,
                        mapRecenterTrigger = uiState.mapRecenterTrigger,
                        viewModel = viewModel,
                        onRestaurantClick = { viewModel.openRestaurantDetails(it) }
                    )
                }
            }
        }
    }

    // Export / Import Backup Dialog
    if (uiState.showBackupDialog) {
        ExportImportDialog(
            jsonText = uiState.backupJsonText,
            onDismiss = { viewModel.showBackupDialog(false) },
            onImport = { json -> viewModel.importBackup(json) },
            onBackupResult = { success, msg -> viewModel.onBackupCompleted(success, msg) }
        )
    }
}
