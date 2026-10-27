package rocks.urzahil.foodie.data.network

import android.util.Log
import rocks.urzahil.foodie.data.local.RestaurantDao
import rocks.urzahil.foodie.data.local.RestaurantEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MichelinPageScraper(
    private val restaurantDao: RestaurantDao
) {
    companion object {
        private const val TAG = "MichelinPageScraper"
        private const val ONE_MONTH_MS = 30L * 24 * 60 * 60 * 1000L

        private val ALL_DAYS = listOf(
            "Monday", "Tuesday", "Wednesday", "Thursday",
            "Friday", "Saturday", "Sunday"
        )

        // Patterns are compiled once: a Michelin page is large and is parsed on every
        // details/image fetch.
        private val DAY_PATTERN = Pattern.compile(
            "\\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday|Mon|Tue|Tues|Wed|Thu|Thur|Thurs|Fri|Sat|Sun)\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val TIME_PATTERN = Pattern.compile(
            "\\b(?:[01]?\\d|2[0-3]):[0-5]\\d(?:\\s*[AaPp][Mm])?\\s*(?:[-–—]|to)\\s*(?:[01]?\\d|2[0-3]):[0-5]\\d(?:\\s*[AaPp][Mm])?\\b|\\b(?:1[0-2]|0?[1-9])\\s*[AaPp][Mm]\\s*(?:[-–—]|to)\\s*(?:1[0-2]|0?[1-9])\\s*[AaPp][Mm]\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val JSON_LD_OPENING_HOURS = Pattern.compile(
            """\"openingHours\"\s*:\s*(\[[^\]]+\]|\"[^\"]+\")""",
            Pattern.CASE_INSENSITIVE
        )
        private val QUOTED_VALUE = Regex("""["\']([^"\']+)["\']""")
        private val CLOSED = Regex("\\bclosed\\b", RegexOption.IGNORE_CASE)
        private val WHITESPACE = Regex("\\s+")
        private val DASH = Regex("\\s*[-–—]\\s*")
        private val TO = Regex("\\s+to\\s+", RegexOption.IGNORE_CASE)

        private val BR_TAG = Regex("(?i)<br\\s*/?>")
        private val BLOCK_END_TAG = Regex("(?i)</(div|li|p|tr|td|th|section|article)>")
        private val ANY_TAG = Regex("<[^>]+>")
        private val HORIZONTAL_SPACE = Regex("[ \\t]+")
        private val SPACE_AFTER_NEWLINE = Regex("\\n[ \\t]+")
        private val SPACE_BEFORE_NEWLINE = Regex("[ \\t]+\\n")

        // Regex matching the official Michelin image format:
        // https://prod-pics.guide.michelin.com/api/public/content/<hash>.<ext>
        private val PROD_PICS_PATTERN = Pattern.compile(
            """https://prod-pics\.guide\.michelin\.com/api/public/content/([a-zA-Z0-9_-]+)\.(?:jpg|jpeg|png|webp)""",
            Pattern.CASE_INSENSITIVE
        )

        internal fun parseOpeningHours(html: String): String? {
            try {
                val result = linkedMapOf<String, MutableList<String>>()

                fun addTimes(day: String, text: String) {
                    val matcher = TIME_PATTERN.matcher(text)
                    while (matcher.find()) {
                        val normalized = matcher.group()
                            .replace(WHITESPACE, " ")
                            .replace(DASH, "–")
                            .replace(TO, "–")
                            .trim()
                        result.getOrPut(day) { mutableListOf() }.add(normalized)
                    }
                }

                val text = htmlFragmentToText(html)
                val dayMatcher = DAY_PATTERN.matcher(text)
                val dayRanges = mutableListOf<Triple<String, Int, Int>>()
                while (dayMatcher.find()) {
                    val day = normalizeDay(dayMatcher.group(1)) ?: continue
                    dayRanges.add(Triple(day, dayMatcher.start(), dayMatcher.end()))
                }

                for (index in dayRanges.indices) {
                    val (day, _, startOffset) = dayRanges[index]
                    // The next weekday's *start* is the boundary of this row. Using
                    // its end would pull the next row's opening hours into this one.
                    val endOffset =
                        if (index + 1 < dayRanges.size) dayRanges[index + 1].second else text.length
                    val section = text.substring(startOffset, endOffset)

                    // Only treat a day token as an opening-hours row when the Michelin
                    // page actually provides hours or explicitly says that it is closed.
                    // This prevents day names appearing in descriptions/navigation from
                    // being interpreted as restaurant hours.
                    val hasTimeRange = TIME_PATTERN.matcher(section).find()
                    val isClosed = CLOSED.containsMatchIn(section)
                    if (hasTimeRange || isClosed) {
                        if (isClosed && !hasTimeRange) {
                            result.getOrPut(day) { mutableListOf() }
                        }
                        addTimes(day, section)
                    }
                }

                // Also inspect JSON-LD openingHours values, but extract only time ranges from them.
                val ld = JSON_LD_OPENING_HOURS.matcher(html)
                while (ld.find()) {
                    val raw = ld.group(1) ?: continue
                    val values = QUOTED_VALUE.findAll(raw)
                        .map { it.groupValues[1] }
                        .toList()
                    for (value in values) {
                        val m = DAY_PATTERN.matcher(value)
                        if (!m.find()) continue
                        val day = normalizeDay(m.group(1)) ?: continue
                        addTimes(day, value.substring(m.end()))
                    }
                }

                if (result.isEmpty()) return null

                // Render only days explicitly present on the Michelin page. Missing days
                // are unknown, not evidence that the restaurant is closed.
                return ALL_DAYS
                    .filter { result.containsKey(it) }
                    .joinToString("\n") { day ->
                        val unique = result[day].orEmpty().distinct()
                        "$day: " + if (unique.isEmpty()) "Closed" else unique.joinToString(", ")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing opening hours", e)
                return null
            }
        }

        /** Returns the canonical .jpg URL of the first Michelin image in [content], if any. */
        private fun extractImageUrl(content: String): String? {
            val matcher = PROD_PICS_PATTERN.matcher(content)
            if (!matcher.find()) return null
            return "https://prod-pics.guide.michelin.com/api/public/content/${matcher.group(1)}.jpg"
        }

        private fun htmlFragmentToText(value: String): String {
            return value
                .replace(BR_TAG, "\n")
                .replace(BLOCK_END_TAG, "\n")
                .replace(ANY_TAG, " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&ndash;", "–")
                .replace("&mdash;", "—")
                .replace(HORIZONTAL_SPACE, " ")
                .replace(SPACE_AFTER_NEWLINE, "\n")
                .replace(SPACE_BEFORE_NEWLINE, "\n")
                .trim()
        }

        private fun normalizeDay(value: String?): String? {
            return when (value?.trim()?.lowercase()) {
                "monday", "mon", "mo" -> "Monday"
                "tuesday", "tue", "tues", "tu" -> "Tuesday"
                "wednesday", "wed", "we" -> "Wednesday"
                "thursday", "thu", "thur", "thurs", "th" -> "Thursday"
                "friday", "fri", "fr" -> "Friday"
                "saturday", "sat", "sa" -> "Saturday"
                "sunday", "sun", "su" -> "Sunday"
                else -> null
            }
        }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Discovers the restaurant's image URL and opening hours from its Michelin page and
     * stores them. Image bytes are not downloaded here; Coil owns the image disk cache.
     */
    suspend fun fetchAndStoreDetails(restaurant: RestaurantEntity): RestaurantEntity =
        withContext(Dispatchers.IO) {
            val imageMissing = restaurant.imageUrl.isNullOrBlank()
            val hoursExpired = isOpeningHoursExpired(restaurant)

            if (!imageMissing && !hoursExpired) {
                return@withContext restaurant
            }

            var scrapedImageUrl: String? = null
            var extractedHours: String? = null

            if (restaurant.url.startsWith("http")) {
                try {
                    val request = Request.Builder()
                        .url(restaurant.url)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        )
                        .header(
                            "Accept",
                            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                        )
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val html = response.body.string()
                            if (imageMissing) scrapedImageUrl = extractImageUrl(html)
                            if (hoursExpired) extractedHours = parseOpeningHours(html)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Direct scraping failed for ${restaurant.name}: ${e.message}")
                }

                if ((imageMissing && scrapedImageUrl == null) ||
                    (hoursExpired && extractedHours.isNullOrBlank())
                ) {
                    try {
                        val proxyUrl = "https://r.jina.ai/${restaurant.url}"
                        val proxyReq = Request.Builder()
                            .url(proxyUrl)
                            .header("User-Agent", "Mozilla/5.0")
                            .build()

                        client.newCall(proxyReq).execute().use { proxyResp ->
                            if (proxyResp.isSuccessful) {
                                val content = proxyResp.body.string()
                                if (imageMissing && scrapedImageUrl == null) {
                                    scrapedImageUrl = extractImageUrl(content)
                                }
                                if (hoursExpired && extractedHours.isNullOrBlank()) {
                                    extractedHours = parseOpeningHours(content)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Proxy scraper failed for ${restaurant.name}: ${e.message}")
                    }
                }
            }

            val finalImageUrl = if (imageMissing) scrapedImageUrl else restaurant.imageUrl
            val finalHours = extractedHours?.takeIf { it.isNotBlank() }
            val timestamp = System.currentTimeMillis()

            // A null finalHours leaves the stored opening hours untouched.
            restaurantDao.updateScrapedDetails(
                id = restaurant.id,
                imageUrl = finalImageUrl,
                timestamp = timestamp,
                openingHours = finalHours,
                hoursFetched = finalHours != null
            )

            return@withContext restaurant.copy(
                imageUrl = finalImageUrl,
                openingHours = finalHours ?: restaurant.openingHours,
                openingHoursLastFetched =
                    if (finalHours != null) timestamp else restaurant.openingHoursLastFetched
            )
        }

    private fun isOpeningHoursExpired(restaurant: RestaurantEntity): Boolean {
        if (restaurant.openingHoursLastFetched <= 0L) return true
        return System.currentTimeMillis() - restaurant.openingHoursLastFetched > ONE_MONTH_MS
    }
}
