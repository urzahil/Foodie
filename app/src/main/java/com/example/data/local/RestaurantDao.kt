package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RestaurantDao {

    @Query("SELECT * FROM restaurants ORDER BY name ASC")
    fun getAllRestaurants(): Flow<List<RestaurantEntity>>

    @Query("SELECT * FROM restaurants")
    suspend fun getAllDirect(): List<RestaurantEntity>

    @Query("SELECT * FROM restaurants WHERE id = :id LIMIT 1")
    fun getRestaurantById(id: Long): Flow<RestaurantEntity?>

    @Query("SELECT * FROM restaurants WHERE id = :id LIMIT 1")
    suspend fun getRestaurantByIdDirect(id: Long): RestaurantEntity?

    @Query("SELECT COUNT(*) FROM restaurants")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(restaurants: List<RestaurantEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(restaurant: RestaurantEntity): Long

    @Query("UPDATE restaurants SET isFavorite = :isFav, favoriteTimestamp = :timestamp WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFav: Boolean, timestamp: Long)

    @Query("UPDATE restaurants SET isVisited = :isVisited, visitedTimestamp = :timestamp, visitedNotes = :notes WHERE id = :id")
    suspend fun updateVisited(id: Long, isVisited: Boolean, timestamp: Long, notes: String)

    @Query("""
        UPDATE restaurants
        SET localImagePath = :localPath,
            imageUrl = :imageUrl,
            imageLastDownloaded = CASE
                WHEN :imageDownloaded = 1 THEN :timestamp
                ELSE imageLastDownloaded
            END,
            openingHours = CASE
                WHEN :openingHours IS NOT NULL AND :openingHours != '' THEN :openingHours
                ELSE openingHours
            END,
            openingHoursLastFetched = CASE
                WHEN :hoursFetched = 1 THEN :timestamp
                ELSE openingHoursLastFetched
            END
        WHERE id = :id
    """)
    suspend fun updateImageData(
        id: Long,
        localPath: String?,
        imageUrl: String?,
        timestamp: Long,
        imageDownloaded: Boolean,
        openingHours: String?,
        hoursFetched: Boolean
    )

    @Query("SELECT * FROM restaurants WHERE isFavorite = 1 ORDER BY favoriteTimestamp DESC")
    fun getFavorites(): Flow<List<RestaurantEntity>>

    @Query("SELECT * FROM restaurants WHERE isVisited = 1 ORDER BY visitedTimestamp DESC")
    fun getVisited(): Flow<List<RestaurantEntity>>

    @Query("SELECT DISTINCT location FROM restaurants WHERE location != '' ORDER BY location ASC")
    suspend fun getUniqueLocations(): List<String>

    @Query("SELECT DISTINCT cuisine FROM restaurants WHERE cuisine != '' ORDER BY cuisine ASC")
    suspend fun getUniqueCuisines(): List<String>

    @Query("SELECT DISTINCT cuisine FROM restaurants WHERE cuisine != '' ORDER BY cuisine ASC")
    fun getUniqueCuisinesFlow(): Flow<List<String>>

    @Query("UPDATE restaurants SET isFavorite = 0, favoriteTimestamp = 0, isVisited = 0, visitedTimestamp = 0, visitedNotes = ''")
    suspend fun clearAllFavoritesAndVisited()

    @Query("DELETE FROM restaurants")
    suspend fun deleteAll()
}
