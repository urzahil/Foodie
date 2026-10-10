package com.example.ui.screens

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.network.SyncState
import com.example.ui.FoodieViewModel
import com.example.ui.SpecialListMode
import com.example.ui.components.CuisineDropdownFilter
import com.example.ui.components.ExportImportDialog
import com.example.ui.components.MichelinFilterChips
import com.example.ui.components.SearchBarWithNearMe
import com.example.ui.theme.GreenStarColor
import com.example.ui.theme.MichelinGold
import com.example.ui.theme.MichelinRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val lastCatalogueSyncTime by viewModel.lastCatalogueSyncTime.collectAsStateWithLifecycle()

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

    // Details observes the selected restaurant by ID, so Room remains the source of truth.
    selectedRestaurant?.let { details ->
        RestaurantDetailsScreen(
            restaurant = details,
            onBack = { viewModel.closeRestaurantDetails() },
            onToggleFavorite = { viewModel.toggleFavorite(details) },
            onToggleVisited = { notes -> viewModel.toggleVisited(details, notes) }
        )
        return
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("main_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Foodie",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = " 2.0",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        if (uiState.specialMode != SpecialListMode.ALL) {
                            Text(
                                text = if (uiState.specialMode == SpecialListMode.FAVORITES_ONLY) " • Favourites" else " • Visited",
                                color = if (uiState.specialMode == SpecialListMode.FAVORITES_ONLY) MichelinRed else GreenStarColor,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                },
                actions = {
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
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "All Restaurants",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.RestaurantMenu,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    viewModel.setSpecialMode(SpecialListMode.ALL)
                                    showMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "My Favourites",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = MichelinRed
                                    )
                                },
                                onClick = {
                                    viewModel.setSpecialMode(SpecialListMode.FAVORITES_ONLY)
                                    showMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Visited Places",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = GreenStarColor
                                    )
                                },
                                onClick = {
                                    viewModel.setSpecialMode(SpecialListMode.VISITED_ONLY)
                                    showMenu = false
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Backup Favourites/Visited (JSON)",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Save,
                                        contentDescription = null,
                                        tint = MichelinGold
                                    )
                                },
                                onClick = {
                                    viewModel.showBackupDialog(true)
                                    showMenu = false
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            "Last refresh",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = if (lastCatalogueSyncTime > 0L) {
                                                SimpleDateFormat(
                                                    "d MMM yyyy, HH:mm",
                                                    Locale.getDefault()
                                                )
                                                    .format(Date(lastCatalogueSyncTime))
                                            } else {
                                                "Never"
                                            },
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                },
                                enabled = false,
                                onClick = {}
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            PrimaryTabRow(
                selectedTabIndex = uiState.selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = uiState.selectedTabIndex == 0,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("List", style = MaterialTheme.typography.labelLarge) },
                    icon = {
                        Icon(
                            Icons.Default.FormatListBulleted,
                            contentDescription = "List View",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("tab_list")
                )
                Tab(
                    selected = uiState.selectedTabIndex == 1,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Map", style = MaterialTheme.typography.labelLarge) },
                    icon = {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = "Map View",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("tab_map")
                )
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
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = s.message,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { s.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }
                }

                is SyncState.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
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

            // Filter row under search bar: 3 Stars, 2 Stars, 1 Star, Bib Gourmand, Selected + Price ($ to $$$$) + Reset
            MichelinFilterChips(
                selectedFilters = uiState.selectedFilters,
                onToggleFilter = { viewModel.toggleFilter(it) },
                selectedPriceFilters = uiState.selectedPriceFilters,
                onTogglePriceFilter = { viewModel.togglePriceFilter(it) },
                onClearFilters = { viewModel.clearAwardAndPriceFilters() }
            )

            // Cuisine dropdown filter
            CuisineDropdownFilter(
                availableCuisines = availableCuisines,
                selectedCuisine = uiState.selectedCuisine,
                onCuisineSelected = { viewModel.selectCuisine(it) }
            )

            // Content Tabs: List (tab 0) and Map (tab 1)
            Box(modifier = Modifier
                .fillMaxSize()
                .weight(1f)) {
                if (uiState.selectedTabIndex == 0) {
                    RestaurantListTab(
                        restaurants = filteredList,
                        activeLocation = uiState.activeLocation,
                        viewModel = viewModel,
                        onRestaurantClick = { viewModel.openRestaurantDetails(it) },
                        onSelectCity = { name, lat, lng ->
                            viewModel.setLocationManually(
                                name,
                                lat,
                                lng
                            )
                        }
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
