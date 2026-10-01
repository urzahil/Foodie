package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MichelinGold
import com.example.ui.theme.MichelinRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CuisineDropdownFilter(
    availableCuisines: List<String>,
    selectedCuisine: String?,
    onCuisineSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var cuisineSearchQuery by remember { mutableStateOf("") }

    val filteredCuisines = remember(availableCuisines, cuisineSearchQuery) {
        if (cuisineSearchQuery.isBlank()) {
            availableCuisines
        } else {
            availableCuisines.filter { it.contains(cuisineSearchQuery.trim(), ignoreCase = true) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
    ) {
        // Dropdown Trigger Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (selectedCuisine != null) Color(0xFF2E240B) else DarkSurface)
                .border(
                    width = 1.dp,
                    color = if (selectedCuisine != null) MichelinGold else Color(0xFF383838),
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable {
                    cuisineSearchQuery = ""
                    expanded = true
                }
                .padding(horizontal = 10.dp)
                .testTag("cuisine_dropdown_trigger"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.RestaurantMenu,
                    contentDescription = null,
                    tint = if (selectedCuisine != null) MichelinGold else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedCuisine != null) "Cuisine: $selectedCuisine" else "All Cuisines",
                    color = if (selectedCuisine != null) MichelinGold else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selectedCuisine != null) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectedCuisine != null) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear cuisine filter",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onCuisineSelected(null) }
                            .testTag("clear_cuisine_filter")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = "Open cuisine dropdown",
                    tint = if (selectedCuisine != null) MichelinGold else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Dropdown Menu (compact list)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                cuisineSearchQuery = ""
            },
            modifier = Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .heightIn(max = 350.dp)
                .background(DarkSurfaceVariant)
                .border(1.dp, Color(0xFF444444), RoundedCornerShape(12.dp))
                .testTag("cuisine_dropdown_menu")
        ) {
            // Search Bar inside dropdown with centered text
            if (availableCuisines.size > 8) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    BasicTextField(
                        value = cuisineSearchQuery,
                        onValueChange = { cuisineSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .testTag("cuisine_search_field"),
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MichelinGold),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkSurface)
                                    .border(
                                        width = 1.dp,
                                        color = if (cuisineSearchQuery.isNotEmpty()) MichelinGold else Color(0xFF444444),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (cuisineSearchQuery.isEmpty()) {
                                        Text(
                                            text = "Search cuisine...",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    innerTextField()
                                }
                                if (cuisineSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { cuisineSearchQuery = "" },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear search",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    )
                }
                HorizontalDivider(color = Color(0xFF383838), thickness = 0.5.dp)
            }

            // "All Cuisines" Reset Option (compact)
            DropdownMenuItem(
                text = {
                    Text(
                        text = "All Cuisines",
                        color = if (selectedCuisine == null) MichelinGold else TextPrimary,
                        fontWeight = if (selectedCuisine == null) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.5.sp
                    )
                },
                trailingIcon = {
                    if (selectedCuisine == null) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MichelinGold,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                },
                onClick = {
                    onCuisineSelected(null)
                    expanded = false
                    cuisineSearchQuery = ""
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .testTag("cuisine_option_all")
            )

            HorizontalDivider(color = Color(0xFF383838), thickness = 0.5.dp)

            // Filtered Cuisines List (compact)
            if (filteredCuisines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No cuisines found",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                filteredCuisines.forEach { cuisine ->
                    val isSelected = selectedCuisine.equals(cuisine, ignoreCase = true)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = cuisine,
                                color = if (isSelected) MichelinGold else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MichelinGold,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        },
                        onClick = {
                            onCuisineSelected(cuisine)
                            expanded = false
                            cuisineSearchQuery = ""
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .testTag("cuisine_option_$cuisine")
                    )
                }
            }
        }
    }
}
