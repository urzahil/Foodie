package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.RestaurantListItem
import com.example.location.LocationTarget
import com.example.ui.FoodieViewModel
import com.example.ui.RestaurantWithDistance
import com.example.ui.components.RestaurantCard

private val POPULAR_MICHELIN_CITIES = listOf(
    Triple("Paris", 48.8566, 2.3522),
    Triple("Tokyo", 35.6762, 139.6503),
    Triple("New York", 40.7128, -74.0060),
    Triple("London", 51.5074, -0.1278),
    Triple("Rome", 41.9028, 12.4964),
    Triple("San Francisco", 37.7749, -122.4194),
    Triple("Singapore", 1.3521, 103.8198),
    Triple("Kyoto", 35.0116, 135.7681)
)

@Composable
fun RestaurantListTab(
    restaurants: List<RestaurantWithDistance>,
    activeLocation: LocationTarget?,
    viewModel: FoodieViewModel,
    onRestaurantClick: (RestaurantListItem) -> Unit,
    onSelectCity: (String, Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    // Preserve scroll position across tab switches and back navigation
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.preservedListIndex,
        initialFirstVisibleItemScrollOffset = viewModel.preservedListOffset
    )

    LaunchedEffect(uiState.listResetTrigger) {
        listState.scrollToItem(0)
        viewModel.preservedListIndex = 0
        viewModel.preservedListOffset = 0
    }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        viewModel.preservedListIndex = listState.firstVisibleItemIndex
        viewModel.preservedListOffset = listState.firstVisibleItemScrollOffset
    }

    if (restaurants.isEmpty()) {
        EmptyListState(
            activeLocation = activeLocation,
            onSelectCity = onSelectCity,
            onNearMeClicked = { viewModel.onNearMeClicked() },
            modifier = modifier
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .testTag("restaurant_list"),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${restaurants.size} restaurants ordered by distance",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            items(
                items = restaurants,
                key = { it.restaurant.id }
            ) { item ->
                RestaurantCard(
                    restaurant = item.restaurant,
                    distanceKm = item.distanceKm,
                    onClick = { onRestaurantClick(item.restaurant) },
                    onToggleFavorite = { viewModel.toggleFavorite(item.restaurant) },
                    onToggleVisited = { viewModel.toggleVisited(item.restaurant) },
                    onEnsureImageDownloaded = { viewModel.ensureRestaurantImageDownloaded(item.restaurant) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun EmptyListState(
    activeLocation: LocationTarget?,
    onSelectCity: (String, Double, Double) -> Unit,
    onNearMeClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(36.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Michelin Restaurants Found",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No restaurants found within 30km of ${activeLocation?.name ?: "this location"} matching your current filters.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onNearMeClicked,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.NearMe, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Find Near My GPS", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Or explore culinary capitals:",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(POPULAR_MICHELIN_CITIES) { (city, lat, lng) ->
                AssistChip(
                    onClick = { onSelectCity(city, lat, lng) },
                    label = {
                        Text(
                            city,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    border = AssistChipDefaults.assistChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
    }
}
