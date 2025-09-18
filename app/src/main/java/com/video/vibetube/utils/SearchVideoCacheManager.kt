package com.video.vibetube.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.video.vibetube.models.Video
import java.io.File
import java.util.concurrent.TimeUnit

class SearchVideoCacheManager(private val context: Context) {

    private val cacheDir = context.cacheDir
    private val gson = Gson()
    private val cacheExpiry = TimeUnit.DAYS.toMillis(1)

    fun getSearchResults(query: String): List<Video>? {
        val file = getCacheFile(query)
        if (file.exists()) {
            val lastModified = file.lastModified()
            if (System.currentTimeMillis() - lastModified < cacheExpiry) {
                val json = file.readText()
                val type = object : TypeToken<List<Video>>() {}.type
                return gson.fromJson(json, type)
            } else {
                file.delete()
            }
        }
        return null
    }

    fun saveSearchResults(query: String, results: List<Video>) {
        val file = getCacheFile(query)
        val json = gson.toJson(results)
        file.writeText(json)
    }

    fun appendSearchResults(query: String, newResults: List<Video>) {
        val existingResults = getSearchResults(query)?.toMutableList() ?: mutableListOf()
        existingResults.addAll(newResults)
        saveSearchResults(query, existingResults)
    }

    fun clearCacheForQuery(query: String) {
        val file = getCacheFile(query)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun getCacheFile(query: String): File {
        val fileName = "search_video_${query.hashCode()}.json"
        return File(cacheDir, fileName)
    }
}
