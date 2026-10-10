package com.example.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MichelinPageScraperTest {

    @Test
    fun parseOpeningHours_doesNotInventMissingDaysAsClosed() {
        val html = """
            <div>Monday 18:30-22:30</div>
            <div>Wednesday 19:00-23:00</div>
            <div>Sunday closed</div>
        """.trimIndent()

        assertEquals(
            """
            Monday: 18:30–22:30
            Wednesday: 19:00–23:00
            Sunday: Closed
            """.trimIndent(),
            MichelinPageScraper.parseOpeningHours(html)
        )
    }

    @Test
    fun parseOpeningHours_ignoresDayNamesWithoutOpeningHours() {
        val html = """
            <div>Our restaurant is a great place for Monday celebrations.</div>
            <div>Location</div>
            <div>Tuesday 12:00-14:00</div>
        """.trimIndent()

        assertEquals(
            "Tuesday: 12:00–14:00",
            MichelinPageScraper.parseOpeningHours(html)
        )
    }

    @Test
    fun parseOpeningHours_returnsNullWhenPageContainsNoHours() {
        assertNull(
            MichelinPageScraper.parseOpeningHours(
                "<div>Monday</div><div>Tuesday</div><div>No opening information.</div>"
            )
        )
    }
}
