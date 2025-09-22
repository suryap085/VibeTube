package com.video.vibetube.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.video.vibetube.models.FavoriteChannelItem
import com.video.vibetube.models.FavoriteItem
import com.video.vibetube.models.FavoritePlaylistItem
import com.video.vibetube.models.UserPlaylist
import com.video.vibetube.models.Video
import com.video.vibetube.models.WatchHistoryItem
import com.video.vibetube.models.YouTubeSearchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * YouTube Policy Compliant User Data Manager
 * 
 * Compliance Features:
 * - Local storage only (no YouTube credentials stored)
 * - User-controlled data deletion within 7 days
 * - 30-day automatic cleanup for inactive data
 * - Explicit user consent required for all operations
 */
class UserDataManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "user_engagement_data", Context.MODE_PRIVATE
    )
    private val gson = Gson()

    // Achievement manager for tracking user progress
    private val achievementManager: AchievementManager by lazy {
        AchievementManager.getInstance(context)
    }
    
    companion object {
        private const val KEY_WATCH_HISTORY = "watch_history"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_FAVORITE_CHANNELS = "favorite_channels"
        private const val KEY_FAVORITE_PLAYLISTS = "favorite_playlists"
        private const val KEY_PLAYLISTS = "user_playlists"
        private const val KEY_USER_CONSENT = "user_data_consent"
        private const val KEY_LAST_CLEANUP = "last_cleanup"

        // YouTube Policy Compliance: 30-day data retention limit
        private const val DATA_RETENTION_DAYS = 30L
        private const val MAX_HISTORY_ITEMS = 1000
        private const val MAX_FAVORITES = 500
        private const val MAX_FAVORITE_CHANNELS = 100
        private const val MAX_FAVORITE_PLAYLISTS = 200

        @Volatile
        private var INSTANCE: UserDataManager? = null

        fun getInstance(context: Context): UserDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserDataManager(context).also { INSTANCE = it }
            }
        }
    }

    /**
     * Maps YouTube category IDs to user-friendly category names
     * Based on YouTube Data API v3 video categories
     */
    private fun mapCategoryIdToName(categoryId: String): String {
        return when (categoryId) {
            "1" -> "entertainment"
            "2" -> "education"
            "10" -> "music"
            "15" -> "entertainment" // Pets & Animals -> Entertainment
            "17" -> "entertainment" // Sports -> Entertainment
            "19" -> "entertainment" // Travel & Events -> Entertainment
            "20" -> "gaming"
            "22" -> "entertainment" // People & Blogs -> Entertainment
            "23" -> "entertainment" // Comedy -> Entertainment
            "24" -> "entertainment" // Entertainment
            "25" -> "entertainment" // News & Politics -> Entertainment
            "26" -> "diy" // Howto & Style -> DIY
            "27" -> "education"
            "28" -> "education" // Science & Technology -> Education
            else -> "default"
        }
    }

    /**
     * Maps source context (fragment/activity names) to categories
     */
    private fun mapSourceContextToCategory(sourceContext: String): String {
        return when {
            sourceContext.contains("music", ignoreCase = true) -> "music"
            sourceContext.contains("comedy", ignoreCase = true) -> "entertainment"
            sourceContext.contains("movie", ignoreCase = true) -> "entertainment"
            sourceContext.contains("diy", ignoreCase = true) -> "diy"
            sourceContext.contains("education", ignoreCase = true) -> "education"
            sourceContext.contains("gaming", ignoreCase = true) -> "gaming"
            sourceContext.contains("home", ignoreCase = true) -> "entertainment"
            sourceContext.contains("shortseries", ignoreCase = true) -> "entertainment"
            else -> "default"
        }
    }

    /**
     * Infers category from video title using keyword matching
     */
    private fun inferCategoryFromTitle(title: String): String {
        val lowerTitle = title.lowercase()
        return when {
            // Education keywords
            lowerTitle.contains("tutorial") || lowerTitle.contains("how to") ||
            lowerTitle.contains("learn") || lowerTitle.contains("course") ||
            lowerTitle.contains("lesson") || lowerTitle.contains("guide") ||
            lowerTitle.contains("explained") || lowerTitle.contains("teaching") -> "education"

            // Music keywords
            lowerTitle.contains("music") || lowerTitle.contains("song") ||
            lowerTitle.contains("album") || lowerTitle.contains("cover") ||
            lowerTitle.contains("remix") || lowerTitle.contains("beat") ||
            lowerTitle.contains("lyrics") || lowerTitle.contains("acoustic") -> "music"

            // Gaming keywords
            lowerTitle.contains("game") || lowerTitle.contains("gaming") ||
            lowerTitle.contains("gameplay") || lowerTitle.contains("playthrough") ||
            lowerTitle.contains("walkthrough") || lowerTitle.contains("speedrun") ||
            lowerTitle.contains("review") && (lowerTitle.contains("game") || lowerTitle.contains("ps5") || lowerTitle.contains("xbox")) -> "gaming"

            // DIY keywords
            lowerTitle.contains("diy") || lowerTitle.contains("craft") ||
            lowerTitle.contains("build") || lowerTitle.contains("make") ||
            lowerTitle.contains("recipe") || lowerTitle.contains("cooking") ||
            lowerTitle.contains("repair") || lowerTitle.contains("fix") -> "diy"

            // Entertainment keywords
            lowerTitle.contains("comedy") || lowerTitle.contains("funny") ||
            lowerTitle.contains("humor") || lowerTitle.contains("joke") ||
            lowerTitle.contains("movie") || lowerTitle.contains("trailer") ||
            lowerTitle.contains("review") || lowerTitle.contains("reaction") ||
            lowerTitle.contains("vlog") || lowerTitle.contains("prank") -> "entertainment"

            else -> "default"
        }
    }

    // User Consent Management (YouTube Policy Requirement)
    fun hasUserConsent(): Boolean {
        return prefs.getBoolean(KEY_USER_CONSENT, false)
    }
    
    fun setUserConsent(consent: Boolean) {
        prefs.edit {
            putBoolean(KEY_USER_CONSENT, consent)
            if (consent) {
                putLong(KEY_LAST_CLEANUP, System.currentTimeMillis())
            }
        }
    }
    
    // Watch History Management
    suspend fun addToWatchHistory(video: Video, watchProgress: Float, watchDuration: Long) {
        if (!hasUserConsent()) return

        withContext(Dispatchers.IO) {
            val currentHistory = getWatchHistoryInternal().toMutableList()

            // Find existing entry for this video
            val existingEntry = currentHistory.find { it.videoId == video.videoId }

            // Calculate cumulative watch duration
            val cumulativeWatchDuration = if (existingEntry != null) {
                // Add new watch duration to existing duration
                existingEntry.watchDuration + watchDuration
            } else {
                // First time watching this video
                watchDuration
            }

            val historyItem = WatchHistoryItem(
                videoId = video.videoId,
                title = video.title,
                thumbnail = video.thumbnail ?: "",
                channelTitle = video.channelTitle,
                channelId = video.channelId,
                duration = video.duration,
                watchProgress = watchProgress,
                watchDuration = cumulativeWatchDuration,
                isCompleted = watchProgress >= 0.9f
            )

            // Remove existing entry for this video to avoid duplicates
            currentHistory.removeAll { it.videoId == video.videoId }

            // Add updated entry at the beginning
            currentHistory.add(0, historyItem)

            // Limit history size (performance and storage optimization)
            if (currentHistory.size > MAX_HISTORY_ITEMS) {
                currentHistory.subList(MAX_HISTORY_ITEMS, currentHistory.size).clear()
            }

            saveWatchHistory(currentHistory)

            // Update achievements for video watching
            try {
                val isVideoCompleted = watchProgress >= 0.9f
                val uniqueChannels = if (video.channelId.isNotEmpty()) setOf(video.channelId) else emptySet()

                // Only count as "video watched" if this is the first time watching this video
                // or if significant progress has been made (to avoid counting every progress save)
                val isFirstTimeWatching = existingEntry == null
                val hasSignificantProgress = watchProgress >= 0.1f // At least 10% watched

                // Only count video completion if it wasn't completed before
                val wasAlreadyCompleted = existingEntry?.isCompleted == true
                val newlyCompleted = isVideoCompleted && !wasAlreadyCompleted

                achievementManager.updateStats(
                    videosWatched = if (isFirstTimeWatching && hasSignificantProgress) 1 else 0,
                    watchTime = watchDuration, // Always count watch time
                    videosCompleted = if (newlyCompleted) 1 else 0,
                    uniqueChannels = if (isFirstTimeWatching) uniqueChannels else emptySet()
                )
            } catch (e: Exception) {
                // Don't fail the main operation if achievement update fails
                e.printStackTrace()
            }
        }
    }
    
    suspend fun getWatchHistory(): List<WatchHistoryItem> {
        if (!hasUserConsent()) return emptyList()
        
        return withContext(Dispatchers.IO) {
            cleanupExpiredData()
            getWatchHistoryInternal()
        }
    }
    
    suspend fun removeFromWatchHistory(videoId: String) {
        if (!hasUserConsent()) return
        
        withContext(Dispatchers.IO) {
            val currentHistory = getWatchHistoryInternal().toMutableList()
            currentHistory.removeAll { it.videoId == videoId }
            saveWatchHistory(currentHistory)
        }
    }
    
    suspend fun clearWatchHistory() {
        withContext(Dispatchers.IO) {
            prefs.edit { remove(KEY_WATCH_HISTORY) }
        }
    }

    /**
     * Get existing watch duration for a specific video
     * @param videoId The video ID to check
     * @return Existing watch duration in milliseconds, or 0 if not found
     */
    suspend fun getExistingWatchDuration(videoId: String): Long {
        return withContext(Dispatchers.IO) {
            val history = getWatchHistoryInternal()
            history.find { it.videoId == videoId }?.watchDuration ?: 0L
        }
    }
    
    // Favorites Management
    suspend fun addToFavorites(video: Video, category: String = "", sourceContext: String = ""): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            // Determine category from multiple sources in order of preference
            val finalCategory = when {
                category.isNotEmpty() -> category
                video.categoryId.isNotEmpty() -> mapCategoryIdToName(video.categoryId)
                sourceContext.isNotEmpty() -> mapSourceContextToCategory(sourceContext)
                else -> inferCategoryFromTitle(video.title)
            }

            val favoriteItem = FavoriteItem(
                videoId = video.videoId,
                title = video.title,
                thumbnail = video.thumbnail ?: "",
                channelTitle = video.channelTitle,
                channelId = video.channelId,
                duration = video.duration,
                category = finalCategory
            )
            
            val currentFavorites = getFavoritesInternal().toMutableList()
            
            // Check if already exists
            if (currentFavorites.any { it.videoId == video.videoId }) {
                return@withContext false
            }
            
            // Check favorites limit
            if (currentFavorites.size >= MAX_FAVORITES) {
                return@withContext false
            }
            
            currentFavorites.add(0, favoriteItem)
            saveFavorites(currentFavorites)

            // Update achievements for adding favorites
            try {
                achievementManager.updateStats(favoritesAdded = 1)
            } catch (e: Exception) {
                // Don't fail the main operation if achievement update fails
                e.printStackTrace()
            }

            true
        }
    }
    
    suspend fun removeFromFavorites(videoId: String) {
        if (!hasUserConsent()) return
        
        withContext(Dispatchers.IO) {
            val currentFavorites = getFavoritesInternal().toMutableList()
            currentFavorites.removeAll { it.videoId == videoId }
            saveFavorites(currentFavorites)
        }
    }
    
    suspend fun getFavorites(): List<FavoriteItem> {
        if (!hasUserConsent()) return emptyList()

        return withContext(Dispatchers.IO) {
            cleanupExpiredData()
            migrateFavoritesCategories()
            getFavoritesInternal()
        }
    }

    /**
     * Migrates existing favorites with "default" category to better categories
     * based on title analysis
     */
    private suspend fun migrateFavoritesCategories() {
        val favorites = getFavoritesInternal().toMutableList()
        var hasChanges = false

        for (i in favorites.indices) {
            val favorite = favorites[i]
            if (favorite.category == "default" || favorite.category.isBlank()) {
                val newCategory = inferCategoryFromTitle(favorite.title)
                if (newCategory != "default") {
                    favorites[i] = favorite.copy(category = newCategory)
                    hasChanges = true
                }
            }
        }

        if (hasChanges) {
            saveFavorites(favorites)
        }
    }
    
    suspend fun isFavorite(videoId: String): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            getFavoritesInternal().any { it.videoId == videoId }
        }
    }

    /**
     * Manually refresh categories for all existing favorites
     * Useful for testing or when category logic is updated
     */
    suspend fun refreshFavoritesCategories(): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            val favorites = getFavoritesInternal().toMutableList()
            var hasChanges = false

            for (i in favorites.indices) {
                val favorite = favorites[i]
                val newCategory = inferCategoryFromTitle(favorite.title)
                if (newCategory != favorite.category && newCategory != "default") {
                    favorites[i] = favorite.copy(category = newCategory)
                    hasChanges = true
                }
            }

            if (hasChanges) {
                saveFavorites(favorites)
            }
            hasChanges
        }
    }
    
    suspend fun clearFavorites() {
        withContext(Dispatchers.IO) {
            prefs.edit { remove(KEY_FAVORITES) }
        }
    }
    
    // Playlist Management
    suspend fun createPlaylist(name: String, description: String = ""): UserPlaylist {
        val playlist = UserPlaylist(
            name = name,
            description = description
        )
        
        withContext(Dispatchers.IO) {
            val currentPlaylists = getPlaylistsInternal().toMutableList()
            currentPlaylists.add(playlist)
            savePlaylists(currentPlaylists)

            // Update achievements for creating playlists
            try {
                achievementManager.updateStats(playlistsCreated = 1)
            } catch (e: Exception) {
                // Don't fail the main operation if achievement update fails
                e.printStackTrace()
            }
        }

        return playlist
    }
    
    suspend fun getPlaylists(): List<UserPlaylist> {
        if (!hasUserConsent()) return emptyList()

        return withContext(Dispatchers.IO) {
            val playlists = getPlaylistsInternal()
            // Migrate playlists that only have videoIds but no video objects
            migratePlaylistsIfNeeded(playlists)
        }
    }

    private suspend fun migratePlaylistsIfNeeded(playlists: List<UserPlaylist>): List<UserPlaylist> {
        val needsMigration = playlists.any { it.videoIds.isNotEmpty() && it.videos.isEmpty() }
        if (!needsMigration) return playlists

        val migratedPlaylists = playlists.map { playlist ->
            if (playlist.videoIds.isNotEmpty() && playlist.videos.isEmpty()) {
                // Create placeholder videos from IDs for backward compatibility
                val placeholderVideos = playlist.videoIds.map { videoId ->
                    Video(
                        videoId = videoId,
                        title = "Video (${videoId.take(8)}...)",
                        description = "",
                        thumbnail = "",
                        channelTitle = "Unknown Channel",
                        publishedAt = "",
                        duration = "0:00"
                    )
                }
                playlist.copy(videos = placeholderVideos.toMutableList())
            } else {
                playlist
            }
        }

        // Save migrated playlists
        savePlaylists(migratedPlaylists)
        return migratedPlaylists
    }
    
    suspend fun addVideoToPlaylist(playlistId: String, videoId: String): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            val playlists = getPlaylistsInternal().toMutableList()
            val playlistIndex = playlists.indexOfFirst { it.id == playlistId }
            if (playlistIndex == -1) return@withContext false

            val playlist = playlists[playlistIndex]
            if (playlist.videoIds.contains(videoId)) return@withContext false

            // Create new mutable list to avoid modifying the original
            val newVideoIds = playlist.videoIds.toMutableList().apply { add(videoId) }
            val updatedPlaylist = playlist.copy(
                videoIds = newVideoIds,
                updatedAt = System.currentTimeMillis()
            )

            playlists[playlistIndex] = updatedPlaylist
            savePlaylists(playlists)
            true
        }
    }

    suspend fun addVideoToPlaylist(playlistId: String, video: Video): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            val playlists = getPlaylistsInternal().toMutableList()
            val playlistIndex = playlists.indexOfFirst { it.id == playlistId }
            if (playlistIndex == -1) return@withContext false

            val playlist = playlists[playlistIndex]
            if (playlist.videos.any { it.videoId == video.videoId }) return@withContext false

            // Create new mutable lists to avoid modifying the original
            val newVideoIds = playlist.videoIds.toMutableList().apply { add(video.videoId) }
            val newVideos = playlist.videos.toMutableList().apply { add(video) }
            val updatedPlaylist = playlist.copy(
                videoIds = newVideoIds,
                videos = newVideos,
                updatedAt = System.currentTimeMillis()
            )

            playlists[playlistIndex] = updatedPlaylist
            savePlaylists(playlists)
            true
        }
    }

    suspend fun removeVideoFromPlaylist(playlistId: String, videoId: String): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            val playlists = getPlaylistsInternal().toMutableList()
            val playlistIndex = playlists.indexOfFirst { it.id == playlistId }
            if (playlistIndex == -1) return@withContext false

            val playlist = playlists[playlistIndex]
            if (!playlist.videoIds.contains(videoId)) return@withContext false

            // Create new mutable lists to avoid modifying the original
            val newVideoIds = playlist.videoIds.toMutableList().apply { remove(videoId) }
            val newVideos = playlist.videos.toMutableList().apply { removeAll { it.videoId == videoId } }
            val updatedPlaylist = playlist.copy(
                videoIds = newVideoIds,
                videos = newVideos,
                updatedAt = System.currentTimeMillis()
            )

            playlists[playlistIndex] = updatedPlaylist
            savePlaylists(playlists)
            true
        }
    }

    suspend fun reorderVideoInPlaylist(playlistId: String, fromIndex: Int, toIndex: Int): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            val playlists = getPlaylistsInternal().toMutableList()
            val playlistIndex = playlists.indexOfFirst { it.id == playlistId }
            if (playlistIndex == -1) return@withContext false

            val playlist = playlists[playlistIndex]
            if (fromIndex < 0 || fromIndex >= playlist.videos.size ||
                toIndex < 0 || toIndex >= playlist.videos.size) return@withContext false

            val newVideoIds = playlist.videoIds.toMutableList()
            val newVideos = playlist.videos.toMutableList()

            val movedVideoId = newVideoIds.removeAt(fromIndex)
            val movedVideo = newVideos.removeAt(fromIndex)

            newVideoIds.add(toIndex, movedVideoId)
            newVideos.add(toIndex, movedVideo)

            val updatedPlaylist = playlist.copy(
                videoIds = newVideoIds,
                videos = newVideos,
                updatedAt = System.currentTimeMillis()
            )

            playlists[playlistIndex] = updatedPlaylist
            savePlaylists(playlists)
            true
        }
    }

    suspend fun isVideoInPlaylist(playlistId: String, videoId: String): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            val playlists = getPlaylistsInternal()
            val playlist = playlists.find { it.id == playlistId } ?: return@withContext false
            playlist.videoIds.contains(videoId)
        }
    }

    suspend fun getPlaylistsContainingVideo(videoId: String): List<UserPlaylist> {
        if (!hasUserConsent()) return emptyList()

        return withContext(Dispatchers.IO) {
            getPlaylistsInternal().filter { it.videoIds.contains(videoId) }
        }
    }
    
    // YouTube Policy Compliance: Data Deletion (7-day requirement)
    suspend fun deleteAllUserData() {
        withContext(Dispatchers.IO) {
            prefs.edit {
                remove(KEY_WATCH_HISTORY)
                remove(KEY_FAVORITES)
                remove(KEY_PLAYLISTS)
                remove(KEY_USER_CONSENT)
                remove(KEY_LAST_CLEANUP)
            }
        }
    }

    suspend fun updatePlaylistInfo(playlistId: String, name: String, description: String): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            val playlists = getPlaylistsInternal().toMutableList()
            val playlistIndex = playlists.indexOfFirst { it.id == playlistId }

            if (playlistIndex != -1) {
                val updatedPlaylist = playlists[playlistIndex].copy(
                    name = name,
                    description = description,
                    updatedAt = System.currentTimeMillis()
                )
                playlists[playlistIndex] = updatedPlaylist
                savePlaylists(playlists)
                true
            } else {
                false
            }
        }
    }

    suspend fun deletePlaylist(playlistId: String): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            val playlists = getPlaylistsInternal().toMutableList()
            val removed = playlists.removeAll { it.id == playlistId }
            if (removed) {
                savePlaylists(playlists)
            }
            removed
        }
    }

    // YouTube Policy Compliance: 30-day automatic cleanup
    private suspend fun cleanupExpiredData() {
        val lastCleanup = prefs.getLong(KEY_LAST_CLEANUP, 0)
        val now = System.currentTimeMillis()
        
        // Run cleanup daily
        if (now - lastCleanup < TimeUnit.DAYS.toMillis(1)) return
        
        val cutoffTime = now - TimeUnit.DAYS.toMillis(DATA_RETENTION_DAYS)
        
        // Cleanup watch history
        val history = getWatchHistoryInternal().filter { it.watchedAt > cutoffTime }
        saveWatchHistory(history)
        
        // Cleanup favorites
        val favorites = getFavoritesInternal().filter { it.addedAt > cutoffTime }
        saveFavorites(favorites)
        
        prefs.edit { putLong(KEY_LAST_CLEANUP, now) }
    }
    
    // Internal helper methods
    private fun getWatchHistoryInternal(): List<WatchHistoryItem> {
        val json = prefs.getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<WatchHistoryItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveWatchHistory(history: List<WatchHistoryItem>) {
        val json = gson.toJson(history)
        prefs.edit { putString(KEY_WATCH_HISTORY, json) }
    }
    
    private fun getFavoritesInternal(): List<FavoriteItem> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        val type = object : TypeToken<List<FavoriteItem>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveFavorites(favorites: List<FavoriteItem>) {
        val json = gson.toJson(favorites)
        prefs.edit { putString(KEY_FAVORITES, json) }
    }
    
    private fun getPlaylistsInternal(): List<UserPlaylist> {
        val json = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        val type = object : TypeToken<List<UserPlaylist>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun savePlaylists(playlists: List<UserPlaylist>) {
        val json = gson.toJson(playlists)
        prefs.edit { putString(KEY_PLAYLISTS, json) }
    }

    fun isDataCollectionEnabled(): Boolean {
        return prefs.getBoolean("data_collection_enabled", true)
    }

    fun setDataCollectionEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("data_collection_enabled", enabled) }
    }

    fun isWeeklySummaryEnabled(): Boolean {
        return prefs.getBoolean("weekly_summary_enabled", true)
    }

    fun setWeeklySummaryEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("weekly_summary_enabled", enabled) }
    }

    fun exportUserData(): String {
        val data = mapOf(
            "watchHistory" to getWatchHistoryInternal(),
            "favorites" to getFavoritesInternal(),
            "playlists" to getPlaylistsInternal(),
            "settings" to mapOf(
                "dataCollectionEnabled" to isDataCollectionEnabled(),
                "weeklySummaryEnabled" to isWeeklySummaryEnabled()
            )
        )
        return gson.toJson(data)
    }

    fun clearAllData() {
        prefs.edit().clear().apply()
    }

    // Add getUserPlaylists method for compatibility
    fun getUserPlaylists(): List<UserPlaylist> {
        return getPlaylistsInternal()
    }

    // Add saveUserPlaylists method for compatibility
    fun saveUserPlaylists(playlists: List<UserPlaylist>) {
        savePlaylists(playlists)
    }

    // Channel Favorites Management
    suspend fun addToFavoriteChannels(channel: YouTubeSearchItem): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            val favoriteChannel = FavoriteChannelItem(
                channelId = channel.snippet.channelId,
                channelTitle = channel.snippet.channelTitle,
                channelDescription = channel.snippet.description,
                thumbnail = channel.snippet.thumbnails.high?.url
                    ?: channel.snippet.thumbnails.medium?.url ?: ""
            )

            val currentChannels = getFavoriteChannelsInternal().toMutableList()

            // Check if already exists
            if (currentChannels.any { it.channelId == channel.snippet.channelId }) {
                return@withContext false
            }

            // Check limit
            if (currentChannels.size >= MAX_FAVORITE_CHANNELS) {
                return@withContext false
            }

            currentChannels.add(0, favoriteChannel)
            saveFavoriteChannels(currentChannels)
            true
        }
    }

    suspend fun removeFromFavoriteChannels(channelId: String) {
        if (!hasUserConsent()) return

        withContext(Dispatchers.IO) {
            val currentChannels = getFavoriteChannelsInternal().toMutableList()
            currentChannels.removeAll { it.channelId == channelId }
            saveFavoriteChannels(currentChannels)
        }
    }

    suspend fun isFavoriteChannel(channelId: String): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            getFavoriteChannelsInternal().any { it.channelId == channelId }
        }
    }

    suspend fun getFavoriteChannels(): List<FavoriteChannelItem> {
        if (!hasUserConsent()) return emptyList()

        return withContext(Dispatchers.IO) {
            cleanupExpiredData()
            getFavoriteChannelsInternal()
        }
    }

    private fun getFavoriteChannelsInternal(): List<FavoriteChannelItem> {
        val json = prefs.getString(KEY_FAVORITE_CHANNELS, null) ?: return emptyList()
        val type = object : TypeToken<List<FavoriteChannelItem>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveFavoriteChannels(channels: List<FavoriteChannelItem>) {
        prefs.edit {
            putString(KEY_FAVORITE_CHANNELS, gson.toJson(channels))
        }
    }

    // Playlist Favorites Management
    suspend fun addToFavoritePlaylists(playlist: YouTubeSearchItem): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            val favoritePlaylist = FavoritePlaylistItem(
                playlistId = playlist.id.videoId, // For playlists, this would be playlist ID
                playlistTitle = playlist.snippet.title,
                playlistDescription = playlist.snippet.description,
                thumbnail = playlist.snippet.thumbnails.high?.url
                    ?: playlist.snippet.thumbnails.medium?.url ?: "",
                channelTitle = playlist.snippet.channelTitle,
                channelId = playlist.snippet.channelId
            )

            val currentPlaylists = getFavoritePlaylistsInternal().toMutableList()

            // Check if already exists
            if (currentPlaylists.any { it.playlistId == playlist.id.videoId }) {
                return@withContext false
            }

            // Check limit
            if (currentPlaylists.size >= MAX_FAVORITE_PLAYLISTS) {
                return@withContext false
            }

            currentPlaylists.add(0, favoritePlaylist)
            saveFavoritePlaylists(currentPlaylists)
            true
        }
    }

    suspend fun removeFromFavoritePlaylists(playlistId: String) {
        if (!hasUserConsent()) return

        withContext(Dispatchers.IO) {
            val currentPlaylists = getFavoritePlaylistsInternal().toMutableList()
            currentPlaylists.removeAll { it.playlistId == playlistId }
            saveFavoritePlaylists(currentPlaylists)
        }
    }

    suspend fun isFavoritePlaylist(playlistId: String): Boolean {
        if (!hasUserConsent()) return false

        return withContext(Dispatchers.IO) {
            getFavoritePlaylistsInternal().any { it.playlistId == playlistId }
        }
    }

    suspend fun getFavoritePlaylists(): List<FavoritePlaylistItem> {
        if (!hasUserConsent()) return emptyList()

        return withContext(Dispatchers.IO) {
            cleanupExpiredData()
            getFavoritePlaylistsInternal()
        }
    }

    private fun getFavoritePlaylistsInternal(): List<FavoritePlaylistItem> {
        val json = prefs.getString(KEY_FAVORITE_PLAYLISTS, null) ?: return emptyList()
        val type = object : TypeToken<List<FavoritePlaylistItem>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun saveFavoritePlaylists(playlists: List<FavoritePlaylistItem>) {
        prefs.edit {
            putString(KEY_FAVORITE_PLAYLISTS, gson.toJson(playlists))
        }
    }
}
