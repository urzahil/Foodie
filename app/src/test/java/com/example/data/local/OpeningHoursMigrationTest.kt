package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpeningHoursMigrationTest {
    @Test fun `migration removes unverified schedules and preserves user data`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "opening-hours-migration-test.db"
        context.deleteDatabase(name)
        val legacy = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE restaurants (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                name TEXT NOT NULL, address TEXT NOT NULL, location TEXT NOT NULL,
                                price TEXT NOT NULL, cuisine TEXT NOT NULL,
                                longitude REAL NOT NULL, latitude REAL NOT NULL,
                                phoneNumber TEXT NOT NULL, url TEXT NOT NULL, websiteUrl TEXT NOT NULL,
                                award TEXT NOT NULL, greenStar INTEGER NOT NULL,
                                facilitiesAndServices TEXT NOT NULL, description TEXT NOT NULL,
                                openingHours TEXT NOT NULL, localImagePath TEXT, imageUrl TEXT,
                                imageLastDownloaded INTEGER NOT NULL, isFavorite INTEGER NOT NULL,
                                favoriteTimestamp INTEGER NOT NULL, isVisited INTEGER NOT NULL,
                                visitedTimestamp INTEGER NOT NULL, visitedNotes TEXT NOT NULL
                            )
                        """.trimIndent())
                        for (column in listOf("award", "location", "isFavorite", "isVisited")) {
                            db.execSQL("CREATE INDEX index_restaurants_$column ON restaurants ($column)")
                        }
                        db.execSQL("""
                            INSERT INTO restaurants VALUES
                            (1, 'Test', 'Address', 'Paris', '€', 'French', 2.0, 48.0,
                             '', 'https://guide.michelin.com/en/restaurant/test', '', '1 Star', 0,
                             '', '', 'Invented hours', '/cache/image.jpg', NULL, 100, 1, 200, 1, 300, 'Keep notes')
                        """.trimIndent())
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        legacy.writableDatabase
        legacy.close()
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            // Opening through Room also validates the migrated schema against RestaurantEntity.
            val dao = database.restaurantDao()
            val restaurant = requireNotNull(dao.getRestaurantByIdDirect(1))
            assertEquals("", restaurant.openingHours)
            assertEquals(0L, restaurant.openingHoursLastFetched)
            assertTrue(restaurant.isFavorite)
            assertEquals(200L, restaurant.favoriteTimestamp)
            assertTrue(restaurant.isVisited)
            assertEquals(300L, restaurant.visitedTimestamp)
            assertEquals("Keep notes", restaurant.visitedNotes)
            assertEquals("/cache/image.jpg", restaurant.localImagePath)

            dao.updateOpeningHours(1, "Sunday\nClosed", 400)
            dao.updateImageData(1, "/cache/new.jpg", "https://example.com/image.jpg", 500)
            val updated = requireNotNull(dao.getRestaurantByIdDirect(1))
            assertEquals("Sunday\nClosed", updated.openingHours)
            assertEquals(400L, updated.openingHoursLastFetched)
            assertEquals(500L, updated.imageLastDownloaded)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
