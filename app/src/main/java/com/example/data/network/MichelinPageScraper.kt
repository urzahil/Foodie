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
            if (!isImageExpired(restaurant)) {
                return@withContext restaurant
            }

            var scrapedImageUrl: String? = null

            if (restaurant.url.isNotBlank() && restaurant.url.startsWith("http")) {
                // 1. First attempt: Direct fetch of restaurant page
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

                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        val html = response.body?.string().orEmpty()

                        // Extract from first <img> with prod-pics.guide.michelin.com
                        val matcher = PROD_PICS_PATTERN.matcher(html)
                        if (matcher.find()) {
                            val hash = matcher.group(1)
                            scrapedImageUrl = "https://prod-pics.guide.michelin.com/api/public/content/$hash.jpg"
                        }

                        // Also check specifically for ci-src / data-src
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
                                        scrapedImageUrl = "https://prod-pics.guide.michelin.com/api/public/content/$hash.jpg"
                                        break
                                    }
                                }
                            }
                        }

                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Direct scraping failed for ${restaurant.name}: ${e.message}")
                }

                // 2. Second attempt: Reader proxy if direct fetch did not yield the image
                if (scrapedImageUrl.isNullOrBlank()) {
                    try {
                        val proxyUrl = "https://r.jina.ai/${restaurant.url}"
                        val proxyReq = Request.Builder()
                            .url(proxyUrl)
                            .header("User-Agent", "Mozilla/5.0")
                            .build()
                        val proxyResp = client.newCall(proxyReq).execute()
                        if (proxyResp.isSuccessful) {
                            val content = proxyResp.body?.string().orEmpty()
                            val matcher = PROD_PICS_PATTERN.matcher(content)
                            if (matcher.find()) {
                                val hash = matcher.group(1)
                                scrapedImageUrl = "https://prod-pics.guide.michelin.com/api/public/content/$hash.jpg"
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Proxy scraper failed for ${restaurant.name}: ${e.message}")
                    }
                }
            }

            // Determine final image URL: strictly official Michelin prod-pics images, no placeholders
            val finalImageUrl = when {
                !scrapedImageUrl.isNullOrBlank() -> scrapedImageUrl
                !restaurant.imageUrl.isNullOrBlank() && !restaurant.imageUrl.contains("unsplash.com") -> restaurant.imageUrl
                else -> null
            }

            // Download and save image locally
            var localPath: String? = null
            if (!finalImageUrl.isNullOrBlank()) {
                try {
                    localPath = downloadImageToFile(restaurant.id, finalImageUrl)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed downloading image for ${restaurant.name}: ${e.message}")
                }
            }

            val now = System.currentTimeMillis()
            restaurantDao.updateImageData(
                id = restaurant.id,
                localPath = localPath ?: restaurant.localImagePath,
                imageUrl = finalImageUrl ?: restaurant.imageUrl,
                timestamp = now
            )

            return@withContext restaurant.copy(
                localImagePath = localPath ?: restaurant.localImagePath,
                imageUrl = finalImageUrl ?: restaurant.imageUrl,
                imageLastDownloaded = now
            )
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
