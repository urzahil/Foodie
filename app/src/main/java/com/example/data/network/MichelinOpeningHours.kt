package com.example.data.network

import com.example.data.local.RestaurantDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Opening hours are fetched only by the details-view path, independently of image refreshes. */
class MichelinOpeningHours(
    private val dao: RestaurantDao,
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val now: () -> Long = System::currentTimeMillis
) {
    // Validate every redirect before issuing another request; hours never come from a proxy.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val refreshMutex = Mutex()

    suspend fun refreshIfNeeded(restaurantId: Long) = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            // Read persisted freshness under the lock so repeated opens do not refetch fresh hours.
            val restaurant = dao.getRestaurantByIdDirect(restaurantId) ?: return@withLock
            if (restaurant.openingHoursLastFetched > 0L &&
                now() - restaurant.openingHoursLastFetched <= CACHE_DURATION_MS
            ) return@withLock

            val url = restaurant.url.toHttpUrlOrNull()?.takeIf(::isRestaurantPage)
                ?: return@withLock
            try {
                val html = fetchPage(url) ?: return@withLock
                val hours = parseOpeningHours(html) ?: return@withLock
                currentCoroutineContext().ensureActive()
                dao.updateOpeningHours(restaurantId, hours, now())
            } catch (_: IOException) {
                // Keep previously verified hours on failure. Retry on a later details view.
            }
        }
    }

    private suspend fun fetchPage(initialUrl: HttpUrl): String? {
        var url = initialUrl
        repeat(5) {
            currentCoroutineContext().ensureActive()
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) Foodie/2.0")
                .header("Accept", "text/html")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) return response.body?.string()
                if (response.code !in listOf(301, 302, 303, 307, 308)) return null
                url = response.header("Location")?.let(url::resolve)
                    ?.takeIf(::isRestaurantPage) ?: return null
            }
        }
        return null
    }

    companion object {
        const val CACHE_DURATION_MS = 30L * 24 * 60 * 60 * 1000

        private fun isRestaurantPage(url: HttpUrl): Boolean =
            url.isHttps && url.host == "guide.michelin.com" && url.port == 443 &&
                url.pathSegments.contains("restaurant") &&
                url.username.isEmpty() && url.password.isEmpty()

        /** Read only Michelin's daily cards, retaining multiple services and explicit closed days. */
        internal fun parseOpeningHours(html: String): String? {
            val weekdays = setOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            val days = linkedMapOf<String, List<String>>()
            for (card in Jsoup.parse(html).select(".card-borderline__content")) {
                val title = card.selectFirst(".card--title")?.text()?.trim() ?: continue
                val day = weekdays.firstOrNull { it.equals(title, ignoreCase = true) } ?: continue
                val periods = card.select(".card--content").map { it.text().trim() }
                    .filter { it.isNotBlank() }.distinct()
                if (periods.isNotEmpty()) days[day] = periods
            }
            return days.takeIf { it.isNotEmpty() }?.entries?.joinToString("\n\n") { (day, periods) ->
                "$day\n${periods.joinToString("\n")}"
            }
        }
    }
}
