package com.video.vibetube.utils

import android.content.Context
import com.video.vibetube.models.YouTubeSearchItem
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.concurrent.TimeUnit

class SearchPlaylistsCacheManager(context: Context) {

    private val cacheDir = File(context.cacheDir, "search_playlists_cache")
    private val cacheExpiry = TimeUnit.DAYS.toMillis(1) // Cache for 1 day

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun getSearchResults(query: String): List<YouTubeSearchItem>? {
        val file = getCacheFile(query)
        if (file.exists()) {
            val lastModified = file.lastModified()
            if (System.currentTimeMillis() - lastModified < cacheExpiry) {
                try {
                    ObjectInputStream(file.inputStream()).use { stream ->
                        @Suppress("UNCHECKED_CAST")
                        return stream.readObject() as? List<YouTubeSearchItem>
                    }
                } catch (e: Exception) {
                    // Log the exception and delete the corrupted cache file
                    e.printStackTrace()
                    file.delete()
                }
            } else {
                file.delete()
            }
        }
        return null
    }

    fun saveSearchResults(query: String, results: List<YouTubeSearchItem>) {
        val file = getCacheFile(query)
        try {
            ObjectOutputStream(file.outputStream()).use { stream ->
                stream.writeObject(ArrayList(results))
            }
        } catch (e: Exception) {
            // Log the exception and delete the potentially corrupted cache file
            e.printStackTrace()
            if (file.exists()) {
                file.delete()
            }
        }
    }

    fun clearCacheForQuery(query: String) {
        val file = getCacheFile(query)
        if (file.exists()) {
            file.delete()
        }
    }

    fun appendResults(query: String, newResults: List<YouTubeSearchItem>) {
        val currentResults = getSearchResults(query)?.toMutableList() ?: mutableListOf()
        currentResults.addAll(newResults)
        saveSearchResults(query, currentResults)
    }

    private fun getCacheFile(query: String): File {
        return File(cacheDir, query.hashCode().toString())
    }
}
