package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RestaurantEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun restaurantDao(): RestaurantDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE restaurants ADD COLUMN sourceKey TEXT NOT NULL DEFAULT ''"
                )

                // Collapse duplicates created by the old auto-generated-ID sync.
                database.execSQL(
                    """
                    DELETE FROM restaurants
                    WHERE rowid NOT IN (
                        SELECT MIN(rowid)
                        FROM restaurants
                        GROUP BY CASE
                            WHEN url != '' THEN url
                            ELSE name || '|' || location || '|' || latitude || '|' || longitude
                        END
                    )
                    """.trimIndent()
                )

                database.execSQL(
                    """
                    UPDATE restaurants
                    SET sourceKey = CASE
                        WHEN url != '' THEN url
                        ELSE name || '|' || location || '|' || latitude || '|' || longitude
                    END
                    """.trimIndent()
                )

                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_restaurants_sourceKey ON restaurants(sourceKey)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE restaurants ADD COLUMN openingHoursLastFetched INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "foodie_michelin.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
