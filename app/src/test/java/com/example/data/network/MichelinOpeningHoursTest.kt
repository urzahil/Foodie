package com.example.data.network

import com.example.data.local.RestaurantDao
import com.example.data.local.RestaurantEntity
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class MichelinOpeningHoursTest {
    private val sunday = """
        <div class="col col-6 col-lg-6">
          <div class="card-borderline js-match-height" style="">
            <div class="card-borderline__content">
              <div class="card--title">Sunday</div>
              <div class="card--content ">12:30 PM-2:30 PM</div>
              <div class="card--content ">7:30 PM-9:30 PM</div>
            </div>
          </div>
        </div>
    """.trimIndent()

    @Test fun `parses supplied Michelin card and preserves both services`() {
        assertEquals("Sunday\n12:30 PM-2:30 PM\n7:30 PM-9:30 PM",
            MichelinOpeningHours.parseOpeningHours(sunday))
    }

    @Test fun `preserves daily order closed days nested markup and entities`() {
        val html = """
            <div class="card-borderline__content">
              <div class="card--title">Monday</div>
              <div class="card--content"><span>Closed</span></div>
            </div>
            <div class="card-borderline__content">
              <div class="card--title">Tuesday</div>
              <div class="card--content"><span>12:00&nbsp;PM</span>–2:00 PM</div>
            </div>
        """ + sunday
        assertEquals("Monday\nClosed\n\nTuesday\n12:00 PM–2:00 PM\n\nSunday\n12:30 PM-2:30 PM\n7:30 PM-9:30 PM",
            MichelinOpeningHours.parseOpeningHours(html))
    }

    @Test fun `does not invent hours from unrelated cards or structured data`() {
        assertNull(MichelinOpeningHours.parseOpeningHours("""
            <script type="application/ld+json">{"openingHours":"Mo-Su 09:00-23:00"}</script>
            <div class="card-borderline__content">
              <div class="card--title">Location</div><div class="card--content">Paris</div>
            </div>
            <div class="card-borderline__content"><div class="card--title">Monday</div></div>
        """))
    }

    @Test fun `first details request persists hours and reuse survives loader recreation`() = runBlocking {
        val fixture = Fixture()
        fixture.loader.refreshIfNeeded(1)
        assertEquals(1, fixture.requests.get())
        assertEquals("Sunday\n12:30 PM-2:30 PM\n7:30 PM-9:30 PM", fixture.row.openingHours)
        assertEquals(fixture.now, fixture.row.openingHoursLastFetched)
        // A new instance reads the database, rather than relying on an in-memory freshness flag.
        fixture.newLoader().refreshIfNeeded(1)
        assertEquals(1, fixture.requests.get())
    }

    @Test fun `refreshes only after thirty days independent of image freshness`() = runBlocking {
        val fixture = Fixture()
        fixture.loader.refreshIfNeeded(1)
        fixture.now += MichelinOpeningHours.CACHE_DURATION_MS
        fixture.row = fixture.row.copy(localImagePath = null, imageLastDownloaded = 0)
        fixture.loader.refreshIfNeeded(1)
        assertEquals(1, fixture.requests.get())
        fixture.now++
        fixture.body = sunday.replace("12:30 PM-2:30 PM", "1:00 PM-3:00 PM")
        fixture.loader.refreshIfNeeded(1)
        assertEquals(2, fixture.requests.get())
        assertTrue(fixture.row.openingHours.contains("1:00 PM-3:00 PM"))
        assertEquals(fixture.now, fixture.row.openingHoursLastFetched)
    }

    @Test fun `failed and unparseable refreshes preserve cache and can retry`() = runBlocking {
        val fixture = Fixture()
        fixture.loader.refreshIfNeeded(1)
        val saved = fixture.row
        fixture.now += MichelinOpeningHours.CACHE_DURATION_MS + 1
        fixture.fail = true
        fixture.loader.refreshIfNeeded(1)
        assertEquals(saved, fixture.row)
        fixture.fail = false
        fixture.status = 503
        fixture.loader.refreshIfNeeded(1)
        assertEquals(saved, fixture.row)
        fixture.status = 200
        fixture.body = "<html>Access denied</html>"
        fixture.loader.refreshIfNeeded(1)
        assertEquals(saved, fixture.row)
        fixture.body = sunday
        fixture.loader.refreshIfNeeded(1)
        assertEquals(fixture.now, fixture.row.openingHoursLastFetched)
    }

    @Test fun `missing first response leaves hours unavailable`() = runBlocking {
        val fixture = Fixture()
        fixture.body = "<html>No opening hours</html>"
        fixture.loader.refreshIfNeeded(1)
        assertEquals("", fixture.row.openingHours)
        assertEquals(0L, fixture.row.openingHoursLastFetched)
    }

    @Test fun `rejects non Michelin sources and redirects off site`() = runBlocking {
        for (url in listOf("https://example.com/en/restaurant/test", "http://guide.michelin.com/en/restaurant/test",
            "https://guide.michelin.com.evil.test/en/restaurant/test", "https://guide.michelin.com/en/article/test",
            "https://r.jina.ai/https://guide.michelin.com/en/restaurant/test")) {
            val fixture = Fixture()
            fixture.row = fixture.row.copy(url = url)
            fixture.loader.refreshIfNeeded(1)
            assertEquals(0, fixture.requests.get())
        }
        val fixture = Fixture()
        fixture.status = 302
        fixture.redirect = "https://example.com/en/restaurant/test"
        fixture.loader.refreshIfNeeded(1)
        assertEquals(1, fixture.requests.get())
        assertEquals(0L, fixture.row.openingHoursLastFetched)
    }

    @Test fun `follows redirect to another Michelin restaurant page`() = runBlocking {
        val fixture = Fixture()
        fixture.redirect = "/en/france/paris/restaurant/renamed"
        fixture.status = 302
        fixture.loader.refreshIfNeeded(1)
        assertEquals(2, fixture.requests.get())
        assertTrue(fixture.row.openingHours.isNotBlank())
    }

    @Test fun `simultaneous details requests share persisted refresh`() = runBlocking {
        val fixture = Fixture()
        List(5) { async { fixture.loader.refreshIfNeeded(1) } }.awaitAll()
        assertEquals(1, fixture.requests.get())
    }

    private inner class Fixture {
        var now = 100_000L
        var row = RestaurantEntity(1, "Test", "Address", "Paris", "€", "French", 2.0, 48.0,
            "", "https://guide.michelin.com/en/france/paris/restaurant/test", "", "1 Star", false, "", "")
        var body = sunday
        var status = 200
        var fail = false
        var redirect: String? = null
        val requests = AtomicInteger()
        // Only these two DAO operations are allowed: unrelated writes make the test fail.
        val dao = Proxy.newProxyInstance(RestaurantDao::class.java.classLoader,
            arrayOf(RestaurantDao::class.java)) { _, method, args ->
            when (method.name) {
                "getRestaurantByIdDirect" -> row
                "updateOpeningHours" -> {
                    row = row.copy(openingHours = args[1] as String, openingHoursLastFetched = args[2] as Long)
                    Unit
                }
                else -> error("Unexpected DAO operation: ${method.name}")
            }
        } as RestaurantDao
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            val count = requests.incrementAndGet()
            if (fail) throw IOException("offline")
            val response = Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (status == 302 && count > 1) 200 else status).message("fixture")
                .body(body.toResponseBody())
            redirect?.let { response.header("Location", it) }
            response.build()
        }.build()
        fun newLoader() = MichelinOpeningHours(dao, client) { now }
        val loader = newLoader()
    }
}
