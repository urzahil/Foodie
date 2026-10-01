package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "restaurants",
    indices = [
        Index(value = ["award"]),
        Index(value = ["location"]),
        Index(value = ["isFavorite"]),
        Index(value = ["isVisited"])
    ]
)
data class RestaurantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String,
    val location: String,
    val price: String,
    val cuisine: String,
    val longitude: Double,
    val latitude: Double,
    val phoneNumber: String,
    val url: String,
    val websiteUrl: String,
    val award: String,
    val greenStar: Boolean,
    val facilitiesAndServices: String,
    val description: String,
    val openingHours: String = "",
    @ColumnInfo(defaultValue = "0")
    val openingHoursLastFetched: Long = 0L,
    val localImagePath: String? = null,
    val imageUrl: String? = null,
    val imageLastDownloaded: Long = 0L,
    val isFavorite: Boolean = false,
    val favoriteTimestamp: Long = 0L,
    val isVisited: Boolean = false,
    val visitedTimestamp: Long = 0L,
    val visitedNotes: String = ""
)
