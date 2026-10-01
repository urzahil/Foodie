package com.example.data.network

import android.content.Context
import android.util.Log
import com.example.data.local.RestaurantDao
import com.example.data.local.RestaurantEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

sealed class SyncState {
    object Idle : SyncState()
    data class Syncing(val progress: Float, val count: Int, val message: String) : SyncState()
    data class Success(val count: Int) : SyncState()
    data class Error(val message: String) : SyncState()
}

class MichelinCsvDownloader(
    private val context: Context,
    private val restaurantDao: RestaurantDao
) {
    companion object {
        private const val TAG = "MichelinCsvDownloader"
        private const val CSV_URL =
            "https://raw.githubusercontent.com/ngshiheng/michelin-my-maps/refs/heads/main/data/michelin_my_maps.csv"
        private const val PREFS_NAME = "foodie_prefs"
        private const val KEY_LAST_SYNC = "last_csv_sync_time"
        const val ONE_MONTH_MS = 30L * 24 * 60 * 60 * 1000L

        fun stableRestaurantKey(name: String, url: String, location: String, lat: Double, lng: Double): String {
            return url.trim().ifBlank {
                "${name}|${location}|${lat}|${lng}".trim()
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun getLastSyncTime(): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_SYNC, 0L)
    }

    private fun setLastSyncTime(timestamp: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp).apply()
    }

    suspend fun syncIfNeeded(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val count = restaurantDao.getCount()
        val lastSync = getLastSyncTime()
        val now = System.currentTimeMillis()
        val olderThanMonth = (now - lastSync) > ONE_MONTH_MS

        if (count > 0 && !olderThanMonth && !force) {
            _syncState.value = SyncState.Success(count)
            return@withContext true
        }

        performSync()
    }

    suspend fun performSync(): Boolean = withContext(Dispatchers.IO) {
        _syncState.value = SyncState.Syncing(0f, 0, "Connecting to Michelin Guide database...")
        try {
            val existingState = restaurantDao.getAllDirect().associateBy(
                { it.sourceKey },
                {
                    RestaurantState(
                        id = it.id,
                        isFavorite = it.isFavorite,
                        favoriteTimestamp = it.favoriteTimestamp,
                        isVisited = it.isVisited,
                        visitedTimestamp = it.visitedTimestamp,
                        visitedNotes = it.visitedNotes,
                        localImagePath = it.localImagePath,
                        imageUrl = it.imageUrl,
                        imageLastDownloaded = it.imageLastDownloaded,
                        openingHours = it.openingHours
                    )
                }
            )

            val request = Request.Builder()
                .url(CSV_URL)
                .header("User-Agent", "Foodie2.0/Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _syncState.value = SyncState.Error("HTTP Error: ${response.code}")
                    return@withContext false
                }

                val body = response.body ?: run {
                    _syncState.value = SyncState.Error("Empty response body")
                    return@withContext false
                }

                _syncState.value = SyncState.Syncing(0.1f, 0, "Parsing Michelin restaurants...")

                BufferedReader(
                    InputStreamReader(body.byteStream(), Charsets.UTF_8),
                    32768
                ).use { reader ->
                    val batch = ArrayList<RestaurantEntity>(500)
                    var totalParsed = 0
                    val estimatedTotal = 19600

                    parseCsv(reader) { row ->
                        if (row.size >= 11) {
                            val name = row.getOrNull(0)?.trim().orEmpty()
                            val address = row.getOrNull(1)?.trim().orEmpty()
                            val location = row.getOrNull(2)?.trim().orEmpty()
                            val price = row.getOrNull(3)?.trim().orEmpty()
                            val cuisine = row.getOrNull(4)?.trim().orEmpty()
                            val lng = row.getOrNull(5)?.trim()?.toDoubleOrNull() ?: 0.0
                            val lat = row.getOrNull(6)?.trim()?.toDoubleOrNull() ?: 0.0
                            val phone = row.getOrNull(7)?.trim().orEmpty()
                            val url = row.getOrNull(8)?.trim().orEmpty()
                            val website = row.getOrNull(9)?.trim().orEmpty()
                            val award = row.getOrNull(10)?.trim().orEmpty()
                            val greenStar = row.getOrNull(11)?.trim() == "1"
                            val facilities = row.getOrNull(12)?.trim().orEmpty()
                            val description = row.getOrNull(13)?.trim().orEmpty()

                            if (name.isNotEmpty() && (lat != 0.0 || lng != 0.0)) {
                                val sourceKey = stableRestaurantKey(name, url, location, lat, lng)
                                val previous = existingState[sourceKey]

                                batch.add(
                                    RestaurantEntity(
                                        id = previous?.id ?: 0L,
                                        sourceKey = sourceKey,
                                        name = name,
                                        address = address,
                                        location = location,
                                        price = price,
                                        cuisine = cuisine,
                                        longitude = lng,
                                        latitude = lat,
                                        phoneNumber = phone,
                                        url = url,
                                        websiteUrl = website,
                                        award = award,
                                        greenStar = greenStar,
                                        facilitiesAndServices = facilities,
                                        description = description,
                                        openingHours = previous?.openingHours.orEmpty(),
                                        localImagePath = previous?.localImagePath,
                                        imageUrl = previous?.imageUrl,
                                        imageLastDownloaded = previous?.imageLastDownloaded ?: 0L,
                                        isFavorite = previous?.isFavorite ?: false,
                                        favoriteTimestamp = previous?.favoriteTimestamp ?: 0L,
                                        isVisited = previous?.isVisited ?: false,
                                        visitedTimestamp = previous?.visitedTimestamp ?: 0L,
                                        visitedNotes = previous?.visitedNotes.orEmpty()
                                    )
                                )
                            }
                        }

                        if (batch.size >= 500) {
                            restaurantDao.insertAll(batch)
                            totalParsed += batch.size
                            batch.clear()
                            val progress = 0.1f +
                                (0.85f * (totalParsed.toFloat() / estimatedTotal).coerceAtMost(1f))
                            _syncState.value = SyncState.Syncing(
                                progress,
                                totalParsed,
                                "Saved ${totalParsed} restaurants..."
                            )
                        }
                    }

                    if (batch.isNotEmpty()) {
                        restaurantDao.insertAll(batch)
                        totalParsed += batch.size
                        batch.clear()
                    }

                    setLastSyncTime(System.currentTimeMillis())
                    _syncState.value = SyncState.Success(totalParsed)
                    Log.d(TAG, "Sync complete. Total restaurants: ${totalParsed}")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing CSV", e)
            _syncState.value = SyncState.Error("Sync error: ${e.localizedMessage}")
            false
        }
    }

    private data class RestaurantState(
        val id: Long,
        val isFavorite: Boolean,
        val favoriteTimestamp: Long,
        val isVisited: Boolean,
        val visitedTimestamp: Long,
        val visitedNotes: String,
        val localImagePath: String?,
        val imageUrl: String?,
        val imageLastDownloaded: Long,
        val openingHours: String
    )

    private inline fun parseCsv(reader: BufferedReader, onRow: (List<String>) -> Unit) {
        val row = ArrayList<String>(14)
        val sb = StringBuilder(128)
        var inQuotes = false
        var isFirstLine = true

        while (true) {
            val cInt = reader.read()
            if (cInt == -1) {
                if (sb.isNotEmpty() || row.isNotEmpty()) {
                    row.add(sb.toString())
                    if (!isFirstLine) onRow(row)
                }
                break
            }
            val c = cInt.toChar()

            if (c == '"') {
                if (inQuotes) {
                    reader.mark(1)
                    val nextC = reader.read()
                    if (nextC == '"'.code) {
                        sb.append('"')
                    } else {
                        inQuotes = false
                        reader.reset()
                    }
                } else {
                    inQuotes = true
                }
            } else if (c == ',' && !inQuotes) {
                row.add(sb.toString())
                sb.setLength(0)
            } else if ((c == '\n' || c == '\r') && !inQuotes) {
                if (c == '\r') {
                    reader.mark(1)
                    val nextC = reader.read()
                    if (nextC != '\n'.code) reader.reset()
                }
                row.add(sb.toString())
                sb.setLength(0)

                if (isFirstLine) {
                    isFirstLine = false
                } else if (row.isNotEmpty() && (row.size > 1 || row[0].isNotBlank())) {
                    onRow(row)
                }
                row.clear()
            } else {
                sb.append(c)
            }
        }
    }
}
