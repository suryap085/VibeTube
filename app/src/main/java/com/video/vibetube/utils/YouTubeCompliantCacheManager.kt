package com.video.vibetube.utils

import android.content.Context
import com.google.gson.Gson
import com.video.vibetube.models.Video
import java.util.concurrent.TimeUnit

class YouTubeCompliantCacheManager(private val context: Context) {
    private val sessionCache = mutableMapOf<String, SessionCacheEntry>()
    private val gson = Gson()

    data class SessionCacheEntry(
        val videos: List<Video>,
        val nextPageToken: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    // Session-based caching (YouTube compliant - max 30 minutes)
    fun getSessionVideos(query: String, pageToken: String = "", categoryId: String): List<Video> {
        val key = generateCacheKey(query, pageToken, categoryId)
        val entry = sessionCache[key] ?: return emptyList()

        // Session cache expires after 30 minutes (YouTube policy compliant)
        if (System.currentTimeMillis() - entry.timestamp > TimeUnit.MINUTES.toMillis(30)) {
            sessionCache.remove(key)
            return emptyList()
        }

        return entry.videos
    }

    fun cacheSessionVideos(query: String, videos: List<Video>, pageToken: String = "", nextPageToken: String = "", categoryId: String = "") {
        val key = generateCacheKey(query, pageToken, categoryId)
        sessionCache[key] = SessionCacheEntry(videos, nextPageToken)

        // Cleanup old entries to prevent memory leaks
        if (sessionCache.size > 50) { // Limit cache size
            cleanupOldEntries()
        }
    }

    fun getSessionNextPageToken(query: String, pageToken: String = "", categoryId: String = ""): String {
        val key = generateCacheKey(query, pageToken, categoryId)
        return sessionCache[key]?.nextPageToken ?: ""
    }

    fun cleanup() {
        // Clean expired entries
        cleanupOldEntries()
    }

    private fun cleanupOldEntries() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = sessionCache.filter {
            currentTime - it.value.timestamp > TimeUnit.MINUTES.toMillis(30)
        }.keys

        expiredKeys.forEach { sessionCache.remove(it) }

        // If still too many entries, remove oldest
        if (sessionCache.size > 50) {
            val oldestEntries = sessionCache.entries.sortedBy { it.value.timestamp }.take(20)
            oldestEntries.forEach { sessionCache.remove(it.key) }
        }
    }

    private fun generateCacheKey(query: String, pageToken: String, categoryId: String = ""): String {
        return "${query.lowercase().hashCode()}-${pageToken.hashCode()}-${categoryId.hashCode()}"
    }
}