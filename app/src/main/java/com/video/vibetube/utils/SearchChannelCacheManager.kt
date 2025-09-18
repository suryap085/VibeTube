package com.video.vibetube.utils

import android.content.Context
import com.google.gson.Gson
import com.video.vibetube.models.YouTubeSearchItem
import java.util.concurrent.TimeUnit

class SearchChannelCacheManager(private val context: Context) {
    private val sessionCache = mutableMapOf<String, SessionCacheEntry>()
    private val gson = Gson()

    data class SessionCacheEntry(
        val items: List<YouTubeSearchItem>,
        val nextPageToken: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun getSearchResults(query: String): SessionCacheEntry? {
        val key = generateCacheKey(query)
        val entry = sessionCache[key] ?: return null

        if (System.currentTimeMillis() - entry.timestamp > TimeUnit.MINUTES.toMillis(30)) {
            sessionCache.remove(key)
            return null
        }

        return entry
    }

    fun saveSearchResults(query: String, items: List<YouTubeSearchItem>, nextPageToken: String) {
        val key = generateCacheKey(query)
        sessionCache[key] = SessionCacheEntry(items, nextPageToken)

        if (sessionCache.size > 50) {
            cleanupOldEntries()
        }
    }

    fun clearCacheForQuery(query: String) {
        val key = generateCacheKey(query)
        sessionCache.remove(key)
    }

    fun cleanup() {
        cleanupOldEntries()
    }

    private fun cleanupOldEntries() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = sessionCache.filter {
            currentTime - it.value.timestamp > TimeUnit.MINUTES.toMillis(30)
        }.keys

        expiredKeys.forEach { sessionCache.remove(it) }

        if (sessionCache.size > 50) {
            val oldestEntries = sessionCache.entries.sortedBy { it.value.timestamp }.take(20)
            oldestEntries.forEach { sessionCache.remove(it.key) }
        }
    }

    private fun generateCacheKey(query: String): String {
        return query.lowercase().hashCode().toString()
    }
}
