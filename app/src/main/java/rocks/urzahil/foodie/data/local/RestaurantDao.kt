package rocks.urzahil.foodie.data.local

import androidx.room.Dao
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

/** Identity fields used to match backup entries against the catalogue. */
data class RestaurantMatchKeys(
    val sourceKey: String,
    val url: String,
    val name: String,
    val location: String
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

    @Query(
        """
        SELECT id, sourceKey, name, address, location, price, cuisine,
               longitude, latitude, award, greenStar, imageUrl, isFavorite, isVisited
        FROM restaurants
        ORDER BY name ASC
    """
    )
    fun getAllListItems(): Flow<List<RestaurantListItem>>

    @Query(
        """
        SELECT id, sourceKey, name, address, location, price, cuisine,
               longitude, latitude, award, greenStar, imageUrl, isFavorite, isVisited
        FROM restaurants
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLng AND :maxLng
        ORDER BY name ASC
    """
    )
    fun observeInBox(
        minLat: Double, maxLat: Double, minLng: Double, maxLng: Double
    ): Flow<List<RestaurantListItem>>

    @Query(
        """
        SELECT id, sourceKey, name, address, location, price, cuisine,
               longitude, latitude, award, greenStar, imageUrl, isFavorite, isVisited
        FROM restaurants
        WHERE isFavorite = 1
        ORDER BY name ASC
    """
    )
    fun observeFavoriteListItems(): Flow<List<RestaurantListItem>>

    @Query(
        """
        SELECT id, sourceKey, name, address, location, price, cuisine,
               longitude, latitude, award, greenStar, imageUrl, isFavorite, isVisited
        FROM restaurants
        WHERE isVisited = 1
        ORDER BY name ASC
    """
    )
    fun observeVisitedListItems(): Flow<List<RestaurantListItem>>

    @Query("SELECT * FROM restaurants")
    suspend fun getAllDirect(): List<RestaurantEntity>

    @Query("SELECT * FROM restaurants WHERE isFavorite = 1 OR isVisited = 1")
    suspend fun getFavoritesAndVisited(): List<RestaurantEntity>

    @Query("SELECT sourceKey, url, name, location FROM restaurants")
    suspend fun getMatchKeys(): List<RestaurantMatchKeys>

    @Query("SELECT * FROM restaurants WHERE id = :id LIMIT 1")
    fun getRestaurantById(id: Long): Flow<RestaurantEntity?>

    @Query("SELECT COUNT(*) FROM restaurants")
    suspend fun getCount(): Int

    @Query("UPDATE restaurants SET isFavorite = NOT isFavorite, favoriteTimestamp = CASE WHEN isFavorite = 0 THEN :timestamp ELSE 0 END WHERE id = :id")
    suspend fun toggleFavorite(id: Long, timestamp: Long)

    @Query("UPDATE restaurants SET isFavorite = :isFav, favoriteTimestamp = :timestamp WHERE sourceKey = :sourceKey")
    suspend fun updateFavoriteBySourceKey(sourceKey: String, isFav: Boolean, timestamp: Long)

    @Query("UPDATE restaurants SET isVisited = :isVisited, visitedTimestamp = :timestamp, visitedNotes = :notes WHERE sourceKey = :sourceKey")
    suspend fun updateVisitedBySourceKey(
        sourceKey: String,
        isVisited: Boolean,
        timestamp: Long,
        notes: String
    )

    @Query(
        """
        UPDATE restaurants
        SET isVisited = NOT isVisited,
            visitedTimestamp = CASE WHEN isVisited = 0 THEN :timestamp ELSE 0 END,
            visitedNotes = CASE WHEN isVisited = 0 THEN :notes ELSE '' END
        WHERE id = :id
    """
    )
    suspend fun toggleVisited(id: Long, timestamp: Long, notes: String)

    @Query("UPDATE restaurants SET visitedNotes = :notes WHERE id = :id AND isVisited = 1")
    suspend fun updateVisitedNotes(id: Long, notes: String)

    @Query(
        """
        UPDATE restaurants
        SET imageUrl = :imageUrl,
            openingHours = CASE
                WHEN :openingHours IS NOT NULL AND :openingHours != '' THEN :openingHours
                ELSE openingHours
            END,
            openingHoursLastFetched = CASE
                WHEN :hoursFetched = 1 THEN :timestamp
                ELSE openingHoursLastFetched
            END
        WHERE id = :id
    """
    )
    suspend fun updateScrapedDetails(
        id: Long,
        imageUrl: String?,
        timestamp: Long,
        openingHours: String?,
        hoursFetched: Boolean
    )

    @Query(
        """
        SELECT location, AVG(latitude) AS latitude, AVG(longitude) AS longitude
        FROM restaurants
        WHERE location != ''
        GROUP BY location
        ORDER BY location ASC
    """
    )
    suspend fun getCityLocations(): List<CityLocationRow>

    @Query("UPDATE restaurants SET isFavorite = 0, favoriteTimestamp = 0, isVisited = 0, visitedTimestamp = 0, visitedNotes = ''")
    suspend fun clearAllFavoritesAndVisited()

    @Query(
        """
        INSERT INTO restaurants (
            sourceKey, name, address, location, price, cuisine, longitude, latitude,
            phoneNumber, url, websiteUrl, award, greenStar, facilitiesAndServices,
            description, openingHours, openingHoursLastFetched, imageLastDownloaded,
            isFavorite, favoriteTimestamp, isVisited, visitedTimestamp, visitedNotes,
            catalogueLastSeen
        ) VALUES (
            :sourceKey, :name, :address, :location, :price, :cuisine, :longitude, :latitude,
            :phoneNumber, :url, :websiteUrl, :award, :greenStar, :facilitiesAndServices,
            :description, '', 0, 0, 0, 0, 0, 0, '', :syncToken
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
    """
    )
    suspend fun upsertCatalogue(
        sourceKey: String, name: String, address: String, location: String, price: String,
        cuisine: String, longitude: Double, latitude: Double, phoneNumber: String, url: String,
        websiteUrl: String, award: String, greenStar: Boolean, facilitiesAndServices: String,
        description: String, syncToken: Long
    )

    /**
     * Removes catalogue rows that disappeared upstream. Rows the user has favourited or
     * visited are kept so a catalogue change can never destroy their data.
     */
    @Query(
        """
        DELETE FROM restaurants
        WHERE catalogueLastSeen != :syncToken AND isFavorite = 0 AND isVisited = 0
    """
    )
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
}
