package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

// Lightweight projection used by the list/map screens; intentionally excludes long detail fields.

data class RestaurantListItem(
    val id: Long,
    val sourceKey: String,
    val name: String,
    val address: String,
    val location: String,
    val price: String,
    val cuisine: String,
    val longitude: Double,
    val latitude: Double,
    val award: String,
    val greenStar: Boolean,
    val imageUrl: String?,
    val isFavorite: Boolean,
    val isVisited: Boolean
)

data class CityLocationRow(
    val location: String,
    val latitude: Double,
    val longitude: Double
)

data class ImportedUserState(
    val sourceKey: String,
    val isFavorite: Boolean,
    val favoriteTimestamp: Long,
    val isVisited: Boolean,
    val visitedTimestamp: Long,
    val visitedNotes: String
)

@Dao
interface RestaurantDao {

    @Query("""
        SELECT id, sourceKey, name, address, location, price, cuisine,
               longitude, latitude, award, greenStar, imageUrl, isFavorite, isVisited
        FROM restaurants
        ORDER BY name ASC
    """)
    fun getAllListItems(): Flow<List<RestaurantListItem>>

    @Query("""
        SELECT id, sourceKey, name, address, location, price, cuisine,
               longitude, latitude, award, greenStar, imageUrl, isFavorite, isVisited
        FROM restaurants
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLng AND :maxLng
        ORDER BY name ASC
    """)
    fun observeInBox(
        minLat: Double, maxLat: Double, minLng: Double, maxLng: Double
    ): Flow<List<RestaurantListItem>>

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

    @Query("UPDATE restaurants SET isFavorite = NOT isFavorite, favoriteTimestamp = CASE WHEN isFavorite = 0 THEN :timestamp ELSE 0 END WHERE id = :id")
    suspend fun toggleFavorite(id: Long, timestamp: Long)

    @Query("UPDATE restaurants SET isFavorite = :isFav, favoriteTimestamp = :timestamp WHERE sourceKey = :sourceKey")
    suspend fun updateFavoriteBySourceKey(sourceKey: String, isFav: Boolean, timestamp: Long)

    @Query("UPDATE restaurants SET isVisited = :isVisited, visitedTimestamp = :timestamp, visitedNotes = :notes WHERE sourceKey = :sourceKey")
    suspend fun updateVisitedBySourceKey(sourceKey: String, isVisited: Boolean, timestamp: Long, notes: String)

    @Query("UPDATE restaurants SET isFavorite = :isFav, favoriteTimestamp = :timestamp WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFav: Boolean, timestamp: Long)

    @Query("""
        UPDATE restaurants
        SET isVisited = NOT isVisited,
            visitedTimestamp = CASE WHEN isVisited = 0 THEN :timestamp ELSE 0 END,
            visitedNotes = CASE WHEN isVisited = 0 THEN :notes ELSE '' END
        WHERE id = :id
    """)
    suspend fun toggleVisited(id: Long, timestamp: Long, notes: String)

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

    @Query("""
        SELECT location, AVG(latitude) AS latitude, AVG(longitude) AS longitude
        FROM restaurants
        WHERE location != ''
        GROUP BY location
        ORDER BY location ASC
    """)
    suspend fun getCityLocations(): List<CityLocationRow>

    @Query("SELECT DISTINCT location FROM restaurants WHERE location != '' ORDER BY location ASC")
    suspend fun getUniqueLocations(): List<String>

    @Query("SELECT DISTINCT cuisine FROM restaurants WHERE cuisine != '' ORDER BY cuisine ASC")
    suspend fun getUniqueCuisines(): List<String>

    @Query("SELECT DISTINCT cuisine FROM restaurants WHERE cuisine != '' ORDER BY cuisine ASC")
    fun getUniqueCuisinesFlow(): Flow<List<String>>

    @Query("UPDATE restaurants SET isFavorite = 0, favoriteTimestamp = 0, isVisited = 0, visitedTimestamp = 0, visitedNotes = ''")
    suspend fun clearAllFavoritesAndVisited()

    @Query("""
        INSERT INTO restaurants (
            sourceKey, name, address, location, price, cuisine, longitude, latitude,
            phoneNumber, url, websiteUrl, award, greenStar, facilitiesAndServices,
            description, catalogueLastSeen
        ) VALUES (
            :sourceKey, :name, :address, :location, :price, :cuisine, :longitude, :latitude,
            :phoneNumber, :url, :websiteUrl, :award, :greenStar, :facilitiesAndServices,
            :description, :syncToken
        )
        ON CONFLICT(sourceKey) DO UPDATE SET
            name = excluded.name,
            address = excluded.address,
            location = excluded.location,
            price = excluded.price,
            cuisine = excluded.cuisine,
            longitude = excluded.longitude,
            latitude = excluded.latitude,
            phoneNumber = excluded.phoneNumber,
            url = excluded.url,
            websiteUrl = excluded.websiteUrl,
            award = excluded.award,
            greenStar = excluded.greenStar,
            facilitiesAndServices = excluded.facilitiesAndServices,
            description = excluded.description,
            catalogueLastSeen = excluded.catalogueLastSeen
    """)
    suspend fun upsertCatalogue(
        sourceKey: String, name: String, address: String, location: String, price: String,
        cuisine: String, longitude: Double, latitude: Double, phoneNumber: String, url: String,
        websiteUrl: String, award: String, greenStar: Boolean, facilitiesAndServices: String,
        description: String, syncToken: Long
    )

    @Query("DELETE FROM restaurants WHERE catalogueLastSeen != :syncToken")
    suspend fun deleteStaleCatalogueRows(syncToken: Long)

    @Transaction
    suspend fun applyCatalogue(restaurants: List<RestaurantEntity>, syncToken: Long) {
        restaurants.forEach { r ->
            upsertCatalogue(
                r.sourceKey, r.name, r.address, r.location, r.price, r.cuisine,
                r.longitude, r.latitude, r.phoneNumber, r.url, r.websiteUrl, r.award,
                r.greenStar, r.facilitiesAndServices, r.description, syncToken
            )
        }
        deleteStaleCatalogueRows(syncToken)
    }

    @Transaction
    suspend fun restoreUserState(states: List<ImportedUserState>) {
        clearAllFavoritesAndVisited()
        states.forEach { state ->
            if (state.isFavorite) {
                updateFavoriteBySourceKey(state.sourceKey, true, state.favoriteTimestamp)
            }
            if (state.isVisited) {
                updateVisitedBySourceKey(
                    state.sourceKey, true, state.visitedTimestamp, state.visitedNotes
                )
            }
        }
    }

    @Query("DELETE FROM restaurants")
    suspend fun deleteAll()
}
