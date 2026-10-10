package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.MichelinAwardFilter
import com.example.ui.PriceFilter
import com.example.ui.theme.MichelinRed

@Composable
fun MichelinFilterChips(
    selectedFilters: Set<MichelinAwardFilter>,
    onToggleFilter: (MichelinAwardFilter) -> Unit,
    modifier: Modifier = Modifier,
    selectedPriceFilters: Set<PriceFilter> = emptySet(),
    onTogglePriceFilter: (PriceFilter) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // First Row: Award Filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 3 Stars Filter Item
            MichelinFilterItem(
                selected = selectedFilters.contains(MichelinAwardFilter.THREE_STARS),
                onClick = { onToggleFilter(MichelinAwardFilter.THREE_STARS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_3_stars")
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-1.5).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_michelin_star),
                            contentDescription = "3 Stars",
                            tint = if (selectedFilters.contains(MichelinAwardFilter.THREE_STARS)) MichelinRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // 2 Stars Filter Item
            MichelinFilterItem(
                selected = selectedFilters.contains(MichelinAwardFilter.TWO_STARS),
                onClick = { onToggleFilter(MichelinAwardFilter.TWO_STARS) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_2_stars")
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-1.5).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(2) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_michelin_star),
                            contentDescription = "2 Stars",
                            tint = if (selectedFilters.contains(MichelinAwardFilter.TWO_STARS)) MichelinRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // 1 Star Filter Item
            MichelinFilterItem(
                selected = selectedFilters.contains(MichelinAwardFilter.ONE_STAR),
                onClick = { onToggleFilter(MichelinAwardFilter.ONE_STAR) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_1_star")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_michelin_star),
                    contentDescription = "1 Star",
                    tint = if (selectedFilters.contains(MichelinAwardFilter.ONE_STAR)) MichelinRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp)
                )
            }

            // Bib Gourmand Filter Item
            MichelinFilterItem(
                selected = selectedFilters.contains(MichelinAwardFilter.BIB_GOURMAND),
                onClick = { onToggleFilter(MichelinAwardFilter.BIB_GOURMAND) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_bib_gourmand")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_bib_gourmand),
                    contentDescription = "Bib Gourmand",
                    tint = if (selectedFilters.contains(MichelinAwardFilter.BIB_GOURMAND)) MichelinRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Selected Filter Item
            MichelinFilterItem(
                selected = selectedFilters.contains(MichelinAwardFilter.SELECTED),
                onClick = { onToggleFilter(MichelinAwardFilter.SELECTED) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("filter_selected")
            ) {
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = "Selected",
                    tint = if (selectedFilters.contains(MichelinAwardFilter.SELECTED)) MichelinRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        // Second Row: Price Filters ($ to $$$$)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PriceFilter.entries.forEach { priceFilter ->
                val selected = selectedPriceFilters.contains(priceFilter)
                MichelinFilterItem(
                    selected = selected,
                    onClick = { onTogglePriceFilter(priceFilter) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_price_${priceFilter.level}")
                ) {
                    Text(
                        text = priceFilter.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) MichelinRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MichelinFilterItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val containerBg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val borderCol = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}
