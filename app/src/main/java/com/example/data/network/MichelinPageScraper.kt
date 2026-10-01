package com.example.data.network

import android.content.Context
import android.util.Log
import com.example.data.local.RestaurantDao
import com.example.data.local.RestaurantEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class MichelinPageScraper(
    private val context: Context,
    private val restaurantDao: RestaurantDao
) {
    companion object {
        private const val TAG = "MichelinPageScraper"
        private const val ONE_MONTH_MS = 30L * 24 * 60 * 60 * 1000L

        // Regex matching the official Michelin image format:
        // https://prod-pics.guide.michelin.com/api/public/content/<hash>.<ext>
        val PROD_PICS_PATTERN = Pattern.compile(
            """https://prod-pics\.guide\.michelin\.com/api/public/content/([a-zA-Z0-9_-]+)\.(?:jpg|jpeg|png|webp)""",
            Pattern.CASE_INSENSITIVE
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val imagesDir: File by lazy {
        val dir = File(context.filesDir, "michelin_images")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun isImageExpired(restaurant: RestaurantEntity): Boolean {
        if (restaurant.localImagePath == null) return true
        val file = File(restaurant.localImagePath)
        if (!file.exists() || file.length() == 0L) return true
        val age = System.currentTimeMillis() - restaurant.imageLastDownloaded
        return age > ONE_MONTH_MS
    }

    suspend fun fetchAndStoreDetails(restaurant: RestaurantEntity): RestaurantEntity =
        withContext(Dispatchers.IO) {
            val imageExpired = isImageExpired(restaurant)
            val hoursExpired = isOpeningHoursExpired(restaurant)

            if (!imageExpired && !hoursExpired) {
                return@withContext restaurant
            }

            var scrapedImageUrl: String? = null
            var extractedHours: String? = null

            if (restaurant.url.isNotBlank() && restaurant.url.startsWith("http")) {
                try {
                    val request = Request.Builder()
                        .url(restaurant.url)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        )
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val html = response.body?.string().orEmpty()

                            if (imageExpired) {
                                val matcher = PROD_PICS_PATTERN.matcher(html)
                                if (matcher.find()) {
                                    val hash = matcher.group(1)
                                    scrapedImageUrl = "https://prod-pics.guide.michelin.com/api/public/content/\$hash.jpg"
                                }

                                if (scrapedImageUrl.isNullOrBlank()) {
                                    val ciSrcPattern = Pattern.compile(
                                        """<img[^>]+(?:ci-src|data-src|src)=["']([^"']+)["']""",
                                        Pattern.CASE_INSENSITIVE
                                    ).matcher(html)
                                    while (ciSrcPattern.find()) {
                                        val urlCandidate = ciSrcPattern.group(1).replace("&amp;", "&")
                                        if (urlCandidate.contains("prod-pics.guide.michelin.com")) {
                                            val m = PROD_PICS_PATTERN.matcher(urlCandidate)
                                            if (m.find()) {
                                                val hash = m.group(1)
                                                scrapedImageUrl = "https://prod-pics.guide.michelin.com/api/public/content/\$hash.jpg"
                                                break
                                            }
                                        }
                                    }
                                }
                            }

                            if (hoursExpired) {
                                extractedHours = parseOpeningHours(html)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Direct scraping failed for \${restaurant.name}: \${e.message}")
                }

                if ((imageExpired && scrapedImageUrl.isNullOrBlank()) ||
                    (hoursExpired && extractedHours.isNullOrBlank())) {
                    try {
                        val proxyUrl = "https://r.jina.ai/\${restaurant.url}"
                        val proxyReq = Request.Builder()
                            .url(proxyUrl)
                            .header("User-Agent", "Mozilla/5.0")
                            .build()

                        client.newCall(proxyReq).execute().use { proxyResp ->
                            if (proxyResp.isSuccessful) {
                                val content = proxyResp.body?.string().orEmpty()

                                if (imageExpired && scrapedImageUrl.isNullOrBlank()) {
                                    val matcher = PROD_PICS_PATTERN.matcher(content)
                                    if (matcher.find()) {
                                        val hash = matcher.group(1)
                                        scrapedImageUrl = "https://prod-pics.guide.michelin.com/api/public/content/\$hash.jpg"
                                    }
                                }

                                if (hoursExpired && extractedHours.isNullOrBlank()) {
                                    extractedHours = parseOpeningHours(content)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Proxy scraper failed for \${restaurant.name}: \${e.message}")
                    }
                }
            }

            val finalImageUrl = when {
                !imageExpired -> restaurant.imageUrl
                !scrapedImageUrl.isNullOrBlank() -> scrapedImageUrl
                !restaurant.imageUrl.isNullOrBlank() && !restaurant.imageUrl.contains("unsplash.com") -> restaurant.imageUrl
                else -> null
            }

            val finalHours = extractedHours?.takeIf { it.isNotBlank() }
            val hoursFetched = !finalHours.isNullOrBlank()

            var localPath: String? = restaurant.localImagePath
            var imageDownloaded = false
            if (imageExpired && !finalImageUrl.isNullOrBlank()) {
                try {
                    localPath = downloadImageToFile(restaurant.id, finalImageUrl)
                    imageDownloaded = true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed downloading image for \${restaurant.name}: \${e.message}")
                }
            }

            val timestamp = System.currentTimeMillis()
            val storedImageTimestamp = if (imageDownloaded) timestamp else restaurant.imageLastDownloaded
            val storedHoursTimestamp = if (hoursFetched) timestamp else restaurant.openingHoursLastFetched
            val storedHours = finalHours ?: restaurant.openingHours

            restaurantDao.updateImageData(
                id = restaurant.id,
                localPath = localPath,
                imageUrl = finalImageUrl,
                timestamp = timestamp,
                imageDownloaded = imageDownloaded,
                openingHours = storedHours,
                hoursFetched = hoursFetched
            )

            return@withContext restaurant.copy(
                localImagePath = localPath,
                imageUrl = finalImageUrl,
                imageLastDownloaded = storedImageTimestamp,
                openingHours = storedHours,
                openingHoursLastFetched = storedHoursTimestamp
            )
        }

    private fun isOpeningHoursExpired(restaurant: RestaurantEntity): Boolean {
        if (restaurant.openingHoursLastFetched <= 0L) return true
        return System.currentTimeMillis() - restaurant.openingHoursLastFetched > ONE_MONTH_MS
    }

    private fun parseOpeningHours(html: String): String? {
        try {
            val dayCardPattern = Pattern.compile(
                """<div[^>]*class=["'][^"']*card-borderline[^"']*["'][^>]*>.*?<div[^>]*class=["'][^"']*card--title[^"']*["'][^>]*>\s*([^<]+?)\s*</div>(.*?)</div>\s*</div>""",
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val contentPattern = Pattern.compile(
                """<div[^>]*class=["'][^"']*card--content[^"']*["'][^>]*>\s*([^<]+?)\s*</div>""",
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )

            val result = linkedMapOf<String, MutableList<String>>()
            val cards = dayCardPattern.matcher(html)

            while (cards.find()) {
                val day = normalizeDay(cards.group(1)) ?: continue
                val periods = mutableListOf<String>()
                val periodMatcher = contentPattern.matcher(cards.group(2))

                while (periodMatcher.find()) {
                    val period = cleanHtmlText(periodMatcher.group(1))
                    if (period.isNotBlank()) periods += period
                }

                result.getOrPut(day) { mutableListOf() }.addAll(periods)
            }

            if (result.isNotEmpty()) {
                return result.entries.joinToString("\n") { entry ->
                    val periods = entry.value
                    entry.key + ": " + if (periods.isEmpty()) "Closed" else periods.joinToString(", ")
                }
            }

            val ldMatcher = Pattern.compile(
                """"openingHours":\s*(\[[^\]]+\]|"[^"]+")""",
                Pattern.CASE_INSENSITIVE
            ).matcher(html)

            if (ldMatcher.find()) {
                return ldMatcher.group(1)
                    .replace("\"", "")
                    .replace("[", "")
                    .replace("]", "")
                    .replace(",", "\n")
                    .trim()
            }

            val text = html
                .replace(Regex("""<[^>]+>"""), "\n")
                .replace("&nbsp;", " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

            val dayPattern = Pattern.compile(
                """(?i)\b(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b\s+([^\n]{1,120}?)(?=\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\b|$)"""
            )
            val fallback = dayPattern.matcher(text)
            val fallbackResult = linkedMapOf<String, String>()

            while (fallback.find()) {
                val day = normalizeDay(fallback.group(1)) ?: continue
                val value = cleanHtmlText(fallback.group(2))
                if (value.isNotBlank()) fallbackResult[day] = value
            }

            if (fallbackResult.isNotEmpty()) {
                return fallbackResult.entries.joinToString("\n") { entry ->
                    entry.key + ": " + entry.value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing opening hours", e)
        }
        return null
    }

    private fun normalizeDay(value: String): String? {
        return when (value.trim().lowercase()) {
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

    private fun cleanHtmlText(value: String): String {
        return value
            .replace(Regex("""\s+"""), " ")
            .replace("&nbsp;", " ")
            .trim()
    }

    private fun downloadImageToFile(restaurantId: Long, imageUrl: String): String {
        val targetFile = File(imagesDir, "restaurant_$restaurantId.jpg")
        val req = Request.Builder()
            .url(imageUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Image download HTTP ${response.code}")
            val body = response.body ?: throw Exception("Empty image body")
            FileOutputStream(targetFile).use { fos ->
                body.byteStream().copyTo(fos)
            }
        }
        return targetFile.absolutePath
    }
}
