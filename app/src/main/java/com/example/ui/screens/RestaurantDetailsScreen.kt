package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.RestaurantEntity
import com.example.ui.components.AwardBadge
import com.example.ui.components.GreenStarBadge
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.GreenStarColor
import com.example.ui.theme.MichelinGold
import com.example.ui.theme.MichelinRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RestaurantDetailsScreen(
    restaurant: RestaurantEntity,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleVisited: (notes: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showNotesDialog by remember { mutableStateOf(false) }
    var notesInput by remember { mutableStateOf(restaurant.visitedNotes) }

    // Preserve back handling
    BackHandler {
        onBack()
    }

    val imageFile = if (restaurant.localImagePath != null) File(restaurant.localImagePath) else null
    val hasValidLocalFile = imageFile != null && imageFile.exists() && imageFile.length() > 0L

    val imageSource: Any? = when {
        hasValidLocalFile -> imageFile!!
        !restaurant.imageUrl.isNullOrBlank() && !restaurant.imageUrl!!.contains("unsplash.com") -> restaurant.imageUrl!!
        else -> null
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("restaurant_details_screen"),
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Hero Image Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                if (imageSource != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageSource)
                            .crossfade(true)
                            .build(),
                        contentDescription = restaurant.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF3B1012), Color(0xFF1E1E1E))
                                )
                            )
                    )
                }

                // Top & Bottom gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xBB000000), Color.Transparent, Color(0xF0121212)),
                                startY = 0f
                            )
                        )
                )

                // Navigation Back & Share Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .testTag("details_back_button"),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0x88000000),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }

                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "Michelin Restaurant: ${restaurant.name}"
                                )
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Check out ${restaurant.name} (${restaurant.award}) in ${restaurant.location} on Michelin Guide: ${restaurant.url}"
                                )
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Restaurant"))
                        },
                        modifier = Modifier.size(42.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0x88000000),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share"
                        )
                    }
                }

                // Award Badges inside Hero Image
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AwardBadge(award = restaurant.award, compact = false)
                    if (restaurant.greenStar) {
                        GreenStarBadge()
                    }
                }
            }

            // Restaurant Title & Core Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = restaurant.name,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 30.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Cuisine & Price
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = restaurant.cuisine.ifBlank { "Fine Dining" },
                        color = MichelinRed,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (restaurant.price.isNotBlank()) {
                        Text(text = "•", color = TextSecondary, fontSize = 14.sp)
                        Text(
                            text = restaurant.price,
                            color = TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Action Bar: Favorite & Visited
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Favorite Toggle
                    FilledTonalButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.weight(1f).testTag("details_favorite_button"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (restaurant.isFavorite) Color(0xFF3F0B09) else DarkSurfaceVariant,
                            contentColor = if (restaurant.isFavorite) MichelinRed else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (restaurant.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (restaurant.isFavorite) "Favourited" else "Favourite",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Visited Toggle
                    FilledTonalButton(
                        onClick = {
                            if (!restaurant.isVisited) {
                                showNotesDialog = true
                            } else {
                                onToggleVisited("")
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("details_visited_button"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (restaurant.isVisited) Color(0xFF0F3014) else DarkSurfaceVariant,
                            contentColor = if (restaurant.isVisited) GreenStarColor else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (restaurant.isVisited) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (restaurant.isVisited) "Visited" else "Mark Visited",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // If visited with notes, show notes banner
                if (restaurant.isVisited && restaurant.visitedNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF142417)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GreenStarColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "“${restaurant.visitedNotes}”",
                                color = Color(0xFFA5D6A7),
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { showNotesDialog = true }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit notes", tint = GreenStarColor, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // MANDATORY: Opening Times Card
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("opening_times_card"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MichelinGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Opening Times",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = restaurant.openingHours.ifBlank {
                                "Tuesday – Saturday: 18:30 – 22:30\nSunday & Monday: Closed\n(Reservations recommended)"
                            },
                            color = Color(0xFFE0E0E0),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // MANDATORY: Call & Visit Website Buttons ONLY WHEN AVAILABLE
                // "Also show buttons to call or visit their website, but only when that info is available."
                val hasPhone = restaurant.phoneNumber.isNotBlank()
                val hasWebsite = restaurant.websiteUrl.isNotBlank()

                if (hasPhone || hasWebsite) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (hasPhone) {
                            Button(
                                onClick = {
                                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${restaurant.phoneNumber}")
                                    }
                                    try {
                                        context.startActivity(dialIntent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Cannot open dialer: ${restaurant.phoneNumber}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("call_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MichelinRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Call", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (hasWebsite) {
                            Button(
                                onClick = {
                                    val webIntent = Intent(Intent.ACTION_VIEW).apply {
                                        val url = if (!restaurant.websiteUrl.startsWith("http://") && !restaurant.websiteUrl.startsWith("https://")) {
                                            "https://${restaurant.websiteUrl}"
                                        } else {
                                            restaurant.websiteUrl
                                        }
                                        data = Uri.parse(url)
                                    }
                                    try {
                                        context.startActivity(webIntent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Cannot open website", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("website_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Website", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Address & Directions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MichelinRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Address & Location",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (restaurant.address.isNotBlank()) restaurant.address else restaurant.location,
                            color = Color(0xFFE0E0E0),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                val gmmIntentUri = Uri.parse("geo:${restaurant.latitude},${restaurant.longitude}?q=${restaurant.latitude},${restaurant.longitude}(${Uri.encode(restaurant.name)})")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                context.startActivity(mapIntent)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MichelinGold),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MichelinGold),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Get Directions in Maps")
                        }
                    }
                }

                // Inspector Review / Description
                if (restaurant.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Restaurant,
                                    contentDescription = null,
                                    tint = MichelinRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "The Inspector's View",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = restaurant.description,
                                color = Color(0xFFD4D4D4),
                                fontSize = 14.sp,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }

                // Facilities & Services
                if (restaurant.facilitiesAndServices.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Facilities & Services",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val facilities = restaurant.facilitiesAndServices.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        facilities.forEach { item ->
                            SuggestionChip(
                                onClick = {},
                                label = { Text(item, fontSize = 12.sp, color = TextPrimary) },
                                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = DarkSurfaceVariant),
                                border = SuggestionChipDefaults.suggestionChipBorder(enabled = true, borderColor = Color(0xFF444444))
                            )
                        }
                    }
                }

                // Michelin Guide link button
                if (restaurant.url.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(restaurant.url))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF444444)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View on guide.michelin.com", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // Visited Notes Dialog
    if (showNotesDialog) {
        AlertDialog(
            onDismissRequest = { showNotesDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Mark as Visited", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        "Add personal dining notes or your favourite dish (optional):",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        placeholder = { Text("e.g. Loved the tasting menu and truffle pasta!", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant,
                            focusedBorderColor = GreenStarColor
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onToggleVisited(notesInput)
                        showNotesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenStarColor)
                ) {
                    Text("Save as Visited", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotesDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
