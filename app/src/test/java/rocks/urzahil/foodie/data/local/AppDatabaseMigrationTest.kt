package rocks.urzahil.foodie.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"
    private var room: AppDatabase? = null

    @After
    fun tearDown() {
        room?.close()
        context.deleteDatabase(dbName)
    }

    /** Creates a database exactly as Room wrote it at [version], from the committed schema. */
    private fun createDatabase(version: Int, fill: SQLiteDatabase.() -> Unit) {
        val schema = JSONObject(
            File("schemas/${AppDatabase::class.java.name}/$version.json").readText()
        ).getJSONObject("database")
        val file = context.getDatabasePath(dbName).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(
                        indices.getJSONObject(j).getString("createSql")
                            .replace("\${TABLE_NAME}", table)
                    )
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = version
            db.fill()
        }
    }

    private fun SQLiteDatabase.insertRestaurant(key: String, imageUrl: String?) {
        execSQL(
            """
            INSERT INTO restaurants (
                sourceKey, name, address, location, price, cuisine, longitude, latitude,
                phoneNumber, url, websiteUrl, award, greenStar, facilitiesAndServices,
                description, openingHours, openingHoursLastFetched, imageUrl,
                imageLastDownloaded, isFavorite, favoriteTimestamp, isVisited,
                visitedTimestamp, visitedNotes, catalogueLastSeen
            ) VALUES (?, ?, '', 'Paris', '', 'French', 2.0, 48.0, '', ?, '', '1 Star', 0, '',
                      '', '', 0, ?, 0, 1, 5, 0, 0, '', 1)
            """.trimIndent(),
            arrayOf(key, "Name $key", "https://guide.michelin.com/$key", imageUrl)
        )
    }

    private fun SupportSQLiteDatabase.imageUrlOf(key: String): String? =
        query("SELECT imageUrl FROM restaurants WHERE sourceKey = ?", arrayOf(key)).use {
            it.moveToFirst()
            if (it.isNull(0)) null else it.getString(0)
        }

    @Test
    fun `migration 4 to 5 clears Unsplash placeholders and keeps everything else`() {
        val michelin = "https://prod-pics.guide.michelin.com/api/public/content/abc.jpg"
        createDatabase(4) {
            insertRestaurant("unsplash", "https://images.unsplash.com/photo-123?w=800")
            insertRestaurant("michelin", michelin)
            insertRestaurant("none", null)
        }

        // Opening runs the migration, then Room validates the result against the v5 entities.
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
            .also { room = it }
            .openHelper.writableDatabase

        assertEquals(5, db.version)
        assertNull(db.imageUrlOf("unsplash"))
        assertEquals(michelin, db.imageUrlOf("michelin"))
        assertNull(db.imageUrlOf("none"))
        db.query("SELECT COUNT(*) FROM restaurants WHERE isFavorite = 1").use {
            it.moveToFirst()
            assertEquals(3, it.getInt(0))
        }
    }
}
