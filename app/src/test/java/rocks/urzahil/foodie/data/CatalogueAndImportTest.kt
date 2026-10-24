package rocks.urzahil.foodie.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import rocks.urzahil.foodie.data.local.AppDatabase
import rocks.urzahil.foodie.data.local.RestaurantDao
import rocks.urzahil.foodie.data.local.RestaurantEntity
import rocks.urzahil.foodie.data.repository.RestaurantRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogueAndImportTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: RestaurantDao
    private lateinit var repository: RestaurantRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.restaurantDao()
        repository = RestaurantRepository(context, dao)
    }

    @After
    fun tearDown() = db.close()

    private fun restaurant(key: String) = RestaurantEntity(
        sourceKey = key, name = "Name $key", address = "", location = "Paris",
        price = "", cuisine = "French", longitude = 2.0, latitude = 48.0, phoneNumber = "",
        url = "https://guide.michelin.com/$key", websiteUrl = "", award = "1 Star",
        greenStar = false, facilitiesAndServices = "", description = ""
    )

    @Test
    fun `catalogue sync removes stale rows but keeps favourites and visited`() = runBlocking {
        dao.applyCatalogue(listOf("a", "b", "c", "d").map(::restaurant), syncToken = 1L)
        val rows = dao.getAllDirect().associateBy { it.sourceKey }
        dao.toggleFavorite(rows.getValue("b").id, 10L)
        dao.toggleVisited(rows.getValue("c").id, 10L, "great")

        // Second sync only contains "a": b is a favourite, c visited, d is plain stale.
        dao.applyCatalogue(listOf(restaurant("a")), syncToken = 2L)

        val remaining = dao.getAllDirect().map { it.sourceKey }.toSet()
        assertEquals(setOf("a", "b", "c"), remaining)
        assertTrue(dao.getAllDirect().first { it.sourceKey == "b" }.isFavorite)
        assertEquals("great", dao.getAllDirect().first { it.sourceKey == "c" }.visitedNotes)
    }

    private fun backup(vararg entries: JSONObject) = JSONObject()
        .put("version", 2)
        .put("restaurants", JSONArray(entries.toList()))
        .toString()

    private fun entry(url: String) = JSONObject()
        .put("url", url).put("isFavorite", true).put("favoriteTimestamp", 5L)

    @Test
    fun `import restores matching entries and skips unknown ones`() = runBlocking {
        dao.applyCatalogue(listOf(restaurant("a"), restaurant("b")), syncToken = 1L)

        val result = repository.importFavoritesAndVisitedJson(
            backup(entry("https://guide.michelin.com/a"), entry("https://guide.michelin.com/gone"))
        )

        assertNotNull(result)
        assertEquals(1, result!!.restored)
        assertEquals(1, result.skipped)
        assertTrue(dao.getAllDirect().first { it.sourceKey == "a" }.isFavorite)
    }

    @Test
    fun `import with no matches fails and leaves existing user state alone`() = runBlocking {
        dao.applyCatalogue(listOf(restaurant("a")), syncToken = 1L)
        dao.toggleFavorite(dao.getAllDirect().single().id, 10L)

        val result = repository.importFavoritesAndVisitedJson(
            backup(entry("https://guide.michelin.com/gone"))
        )

        assertNull(result)
        assertTrue(dao.getAllDirect().single().isFavorite)
    }

    @Test
    fun `export includes sourceKey and round-trips through import`() = runBlocking {
        dao.applyCatalogue(listOf(restaurant("a")), syncToken = 1L)
        dao.toggleFavorite(dao.getAllDirect().single().id, 10L)

        val json = repository.exportFavoritesAndVisitedJson()
        assertEquals("a", JSONObject(json).getJSONArray("restaurants").getJSONObject(0)
            .getString("sourceKey"))

        dao.clearAllFavoritesAndVisited()
        val result = repository.importFavoritesAndVisitedJson(json)
        assertEquals(1, result!!.restored)
        assertTrue(dao.getAllDirect().single().isFavorite)
    }
}
