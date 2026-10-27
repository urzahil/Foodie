package rocks.urzahil.foodie.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RestaurantEntity::class],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun restaurantDao(): RestaurantDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE restaurants ADD COLUMN sourceKey TEXT NOT NULL DEFAULT ''"
                )

                // Collapse duplicates created by the old auto-generated-ID sync.
                db.execSQL(
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

                db.execSQL(
                    """
                    UPDATE restaurants
                    SET sourceKey = CASE
                        WHEN url != '' THEN url
                        ELSE name || '|' || location || '|' || latitude || '|' || longitude
                    END
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_restaurants_sourceKey ON restaurants(sourceKey)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE restaurants ADD COLUMN openingHoursLastFetched INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE restaurants ADD COLUMN catalogueLastSeen INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_restaurants_latitude_longitude ON restaurants(latitude, longitude)"
                )
            }
        }

        /**
         * An early version stored Unsplash placeholder photos as image URLs. A non-blank URL
         * is never re-scraped, so clear them to let the real Michelin photo be fetched.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE restaurants SET imageUrl = NULL WHERE imageUrl LIKE '%unsplash.com%'"
                )
            }
        }

        internal val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "foodie_michelin.db"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
