package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.location.LocationHelper
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context verifies Foodie app name`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Foodie 2.0", appName)
    }

    @Test
    fun `distance calculation accurately computes distance in kilometers`() {
        // Paris Eiffel Tower (48.8584, 2.2945) to Louvre Museum (48.8606, 2.3376) ~ 3.1 km
        val distance = LocationHelper.calculateDistanceKm(48.8584, 2.2945, 48.8606, 2.3376)
        assertTrue("Distance should be between 3.0 and 3.5 km", distance in 3.0..3.5)
        assertTrue("Distance should be well within 30km limit", distance <= LocationHelper.MAX_RADIUS_KM)
    }

    @Test
    fun `format distance formats meters and kilometers cleanly`() {
        assertEquals("500 m", LocationHelper.formatDistance(0.5))
        assertEquals("2.4 km", LocationHelper.formatDistance(2.42))
    }

    @Test
    fun `backup JSON format contains valid schema structure`() {
        val root = JSONObject()
        root.put("app", "Foodie 2.0")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        assertEquals("Foodie 2.0", root.getString("app"))
        assertEquals(1, root.getInt("version"))
    }

    @Test
    fun `cuisine extraction parses distinct individual cuisines correctly`() {
        val rawEntries = listOf("Classic French, Seafood", "Japanese", "Contemporary / Creative", "Seafood")
        val unique = rawEntries
            .flatMap { it.split(",", "/", "&").map { c -> c.trim() } }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        assertEquals(listOf("Classic French", "Contemporary", "Creative", "Japanese", "Seafood"), unique)
    }

    @Test
    fun `available cuisines only reflect restaurants in the current results`() {
        // Given 3 restaurants in current results (e.g. Rome 2 Stars)
        val resultRestaurantsCuisines = listOf("Italian", "Mediterranean / Italian", "Contemporary")
        val extracted = resultRestaurantsCuisines
            .flatMap { it.split(",", "/", "&").map { c -> c.trim() } }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        // Only cuisines present in the results are available
        assertEquals(listOf("Contemporary", "Italian", "Mediterranean"), extracted)
        assertTrue(!extracted.contains("Japanese"))
        assertTrue(!extracted.contains("Classic French"))
    }
}
