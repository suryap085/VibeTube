package com.video.vibetube.utils

import android.content.Context
import androidx.core.content.edit

class UploadsPlaylistCache(context: Context) {
    private val prefs = context.getSharedPreferences("uploads_playlist_cache", Context.MODE_PRIVATE)
    private val SEP = "|"
    private val UPLOADS_CACHE_VALIDITY_MS = 7 * 24 * 3600_000L // 7 days

    fun getPlaylistId(channelId: String): String? {
        val cached = prefs.getString(channelId, null) ?: return null
        val parts = cached.split(SEP)
        if (parts.size < 2) return null
        val playlistId = parts[0]
        val timestamp = parts[1].toLongOrNull() ?: return null
        // Check expiration (7 days here)
        if (System.currentTimeMillis() - timestamp > UPLOADS_CACHE_VALIDITY_MS) return null
        return playlistId
    }

    fun putPlaylistId(channelId: String, playlistId: String) {
        val cacheVal = "$playlistId$SEP${System.currentTimeMillis()}"
        prefs.edit { putString(channelId, cacheVal) }
    }
}