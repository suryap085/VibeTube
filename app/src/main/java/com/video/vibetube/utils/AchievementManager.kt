package com.video.vibetube.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.video.vibetube.models.WatchHistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * YouTube Policy Compliant Achievement System
 * 
 * Compliance Features:
 * - Based on user's own viewing data only
 * - No artificial engagement incentives
 * - Transparent achievement criteria
 * - User can disable gamification
 * - No rewards that manipulate YouTube metrics
 */
class AchievementManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "achievements", Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val notificationManager = AchievementNotificationManager(context)
    
    companion object {
        private const val KEY_ACHIEVEMENTS = "user_achievements"
        private const val KEY_STATS = "user_stats"
        private const val KEY_GAMIFICATION_ENABLED = "gamification_enabled"

        @Volatile
        private var INSTANCE: AchievementManager? = null

        fun getInstance(context: Context): AchievementManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AchievementManager(context).also { INSTANCE = it }
            }
        }
    }
    
    data class Achievement(
        val id: String,
        val title: String,
        val description: String,
        val iconResource: String,
        val category: AchievementCategory,
        val criteria: AchievementCriteria,
        val isUnlocked: Boolean = false,
        val unlockedAt: Long = 0L,
        val progress: Float = 0f,
        val maxProgress: Float = 1f
    )
    
    data class UserStats(
        val totalVideosWatched: Int = 0,
        val totalWatchTime: Long = 0L, // milliseconds
        val videosCompleted: Int = 0,
        val uniqueChannelsWatched: Int = 0,
        val favoriteVideos: Int = 0,
        val playlistsCreated: Int = 0,
        val consecutiveDaysActive: Int = 0,
        val longestWatchSession: Long = 0L,
        val firstVideoWatchedAt: Long = 0L,
        val lastActiveDate: Long = System.currentTimeMillis(),
        val watchedChannelIds: Set<String> = emptySet() // Track unique channel IDs
    )
    
    enum class AchievementCategory {
        VIEWING, EXPLORATION, ORGANIZATION, CONSISTENCY, MILESTONES
    }
    
    data class AchievementCriteria(
        val type: CriteriaType,
        val threshold: Float,
        val timeframe: Long = 0L // milliseconds, 0 = all time
    )
    
    enum class CriteriaType {
        VIDEOS_WATCHED, WATCH_TIME, VIDEOS_COMPLETED, UNIQUE_CHANNELS,
        FAVORITES_ADDED, PLAYLISTS_CREATED, CONSECUTIVE_DAYS, SESSION_LENGTH
    }
    
    /**
     * Check if gamification is enabled
     */
    fun isGamificationEnabled(): Boolean {
        return prefs.getBoolean(KEY_GAMIFICATION_ENABLED, true)
    }
    
    /**
     * Enable/disable gamification features
     */
    fun setGamificationEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_GAMIFICATION_ENABLED, enabled) }
    }
    
    /**
     * Get all available achievements
     */
    fun getAllAchievements(): List<Achievement> {
        return listOf(
            // Viewing Achievements
            Achievement(
                id = "first_video",
                title = "First Steps",
                description = "Watch your first video",
                iconResource = "ic_play_circle",
                category = AchievementCategory.VIEWING,
                criteria = AchievementCriteria(CriteriaType.VIDEOS_WATCHED, 1f)
            ),
            Achievement(
                id = "video_explorer",
                title = "Video Explorer",
                description = "Watch 10 videos",
                iconResource = "ic_explore",
                category = AchievementCategory.VIEWING,
                criteria = AchievementCriteria(CriteriaType.VIDEOS_WATCHED, 10f)
            ),
            Achievement(
                id = "binge_watcher",
                title = "Binge Watcher",
                description = "Watch 50 videos",
                iconResource = "ic_movie",
                category = AchievementCategory.VIEWING,
                criteria = AchievementCriteria(CriteriaType.VIDEOS_WATCHED, 50f)
            ),
            Achievement(
                id = "video_master",
                title = "Video Master",
                description = "Watch 100 videos",
                iconResource = "ic_star",
                category = AchievementCategory.VIEWING,
                criteria = AchievementCriteria(CriteriaType.VIDEOS_WATCHED, 100f)
            ),
            
            // Completion Achievements
            Achievement(
                id = "completionist",
                title = "Completionist",
                description = "Finish 10 videos completely",
                iconResource = "ic_check_circle",
                category = AchievementCategory.VIEWING,
                criteria = AchievementCriteria(CriteriaType.VIDEOS_COMPLETED, 10f)
            ),
            Achievement(
                id = "dedicated_viewer",
                title = "Dedicated Viewer",
                description = "Finish 25 videos completely",
                iconResource = "ic_verified",
                category = AchievementCategory.VIEWING,
                criteria = AchievementCriteria(CriteriaType.VIDEOS_COMPLETED, 25f)
            ),
            
            // Exploration Achievements
            Achievement(
                id = "channel_explorer",
                title = "Channel Explorer",
                description = "Watch videos from 10 different channels",
                iconResource = "ic_diversity",
                category = AchievementCategory.EXPLORATION,
                criteria = AchievementCriteria(CriteriaType.UNIQUE_CHANNELS, 10f)
            ),
            Achievement(
                id = "content_curator",
                title = "Content Curator",
                description = "Watch videos from 25 different channels",
                iconResource = "ic_collections",
                category = AchievementCategory.EXPLORATION,
                criteria = AchievementCriteria(CriteriaType.UNIQUE_CHANNELS, 25f)
            ),
            
            // Organization Achievements
            Achievement(
                id = "first_favorite",
                title = "First Favorite",
                description = "Add your first video to favorites",
                iconResource = "ic_favorite",
                category = AchievementCategory.ORGANIZATION,
                criteria = AchievementCriteria(CriteriaType.FAVORITES_ADDED, 1f)
            ),
            Achievement(
                id = "collector",
                title = "Collector",
                description = "Add 10 videos to favorites",
                iconResource = "ic_bookmark",
                category = AchievementCategory.ORGANIZATION,
                criteria = AchievementCriteria(CriteriaType.FAVORITES_ADDED, 10f)
            ),
            Achievement(
                id = "playlist_creator",
                title = "Playlist Creator",
                description = "Create your first playlist",
                iconResource = "ic_playlist_add",
                category = AchievementCategory.ORGANIZATION,
                criteria = AchievementCriteria(CriteriaType.PLAYLISTS_CREATED, 1f)
            ),
            
            // Time-based Achievements
            Achievement(
                id = "hour_watcher",
                title = "Hour Watcher",
                description = "Watch 1 hour of content",
                iconResource = "ic_schedule",
                category = AchievementCategory.MILESTONES,
                criteria = AchievementCriteria(CriteriaType.WATCH_TIME, TimeUnit.HOURS.toMillis(1).toFloat())
            ),
            Achievement(
                id = "marathon_viewer",
                title = "Marathon Viewer",
                description = "Watch 10 hours of content",
                iconResource = "ic_timer",
                category = AchievementCategory.MILESTONES,
                criteria = AchievementCriteria(CriteriaType.WATCH_TIME, TimeUnit.HOURS.toMillis(10).toFloat())
            ),
            
            // Consistency Achievements
            Achievement(
                id = "daily_viewer",
                title = "Daily Viewer",
                description = "Watch videos for 3 consecutive days",
                iconResource = "ic_today",
                category = AchievementCategory.CONSISTENCY,
                criteria = AchievementCriteria(CriteriaType.CONSECUTIVE_DAYS, 3f)
            ),
            Achievement(
                id = "weekly_regular",
                title = "Weekly Regular",
                description = "Watch videos for 7 consecutive days",
                iconResource = "ic_date_range",
                category = AchievementCategory.CONSISTENCY,
                criteria = AchievementCriteria(CriteriaType.CONSECUTIVE_DAYS, 7f)
            )
        )
    }
    
    /**
     * Get user's unlocked achievements
     */
    suspend fun getUserAchievements(): List<Achievement> {
        if (!isGamificationEnabled()) return emptyList()
        
        return withContext(Dispatchers.IO) {
            val json = prefs.getString(KEY_ACHIEVEMENTS, null) ?: return@withContext emptyList()
            val type = object : TypeToken<List<Achievement>>() {}.type
            try {
                gson.fromJson<List<Achievement>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Get user statistics
     */
    suspend fun getUserStats(): UserStats {
        return withContext(Dispatchers.IO) {
            val json = prefs.getString(KEY_STATS, null) ?: return@withContext UserStats()
            try {
                gson.fromJson(json, UserStats::class.java) ?: UserStats()
            } catch (e: Exception) {
                UserStats()
            }
        }
    }
    
    /**
     * Update user statistics and check for new achievements
     */
    suspend fun updateStats(
        videosWatched: Int = 0,
        watchTime: Long = 0L,
        videosCompleted: Int = 0,
        uniqueChannels: Set<String> = emptySet(),
        favoritesAdded: Int = 0,
        playlistsCreated: Int = 0
    ): List<Achievement> {
        if (!isGamificationEnabled()) return emptyList()
        
        return withContext(Dispatchers.IO) {
            val currentStats = getUserStats()

            // Properly track unique channels by combining existing and new channels
            val allWatchedChannels = currentStats.watchedChannelIds + uniqueChannels

            val newStats = currentStats.copy(
                totalVideosWatched = currentStats.totalVideosWatched + videosWatched,
                totalWatchTime = currentStats.totalWatchTime + watchTime,
                videosCompleted = currentStats.videosCompleted + videosCompleted,
                uniqueChannelsWatched = allWatchedChannels.size,
                favoriteVideos = currentStats.favoriteVideos + favoritesAdded,
                playlistsCreated = currentStats.playlistsCreated + playlistsCreated,
                consecutiveDaysActive = calculateConsecutiveDays(currentStats.lastActiveDate, currentStats.consecutiveDaysActive),
                lastActiveDate = System.currentTimeMillis(),
                watchedChannelIds = allWatchedChannels
            )
            
            saveUserStats(newStats)
            checkForNewAchievements(newStats)
        }
    }
    
    /**
     * Check for newly unlocked achievements
     */
    private suspend fun checkForNewAchievements(stats: UserStats): List<Achievement> {
        val allAchievements = getAllAchievements()
        val unlockedAchievements = getUserAchievements().map { it.id }.toSet()
        val newlyUnlocked = mutableListOf<Achievement>()
        
        allAchievements.forEach { achievement ->
            if (!unlockedAchievements.contains(achievement.id)) {
                val isUnlocked = when (achievement.criteria.type) {
                    CriteriaType.VIDEOS_WATCHED -> stats.totalVideosWatched >= achievement.criteria.threshold
                    CriteriaType.WATCH_TIME -> stats.totalWatchTime >= achievement.criteria.threshold
                    CriteriaType.VIDEOS_COMPLETED -> stats.videosCompleted >= achievement.criteria.threshold
                    CriteriaType.UNIQUE_CHANNELS -> stats.uniqueChannelsWatched >= achievement.criteria.threshold
                    CriteriaType.FAVORITES_ADDED -> stats.favoriteVideos >= achievement.criteria.threshold
                    CriteriaType.PLAYLISTS_CREATED -> stats.playlistsCreated >= achievement.criteria.threshold
                    CriteriaType.CONSECUTIVE_DAYS -> stats.consecutiveDaysActive >= achievement.criteria.threshold
                    CriteriaType.SESSION_LENGTH -> stats.longestWatchSession >= achievement.criteria.threshold
                }
                
                if (isUnlocked) {
                    val unlockedAchievement = achievement.copy(
                        isUnlocked = true,
                        unlockedAt = System.currentTimeMillis(),
                        progress = achievement.maxProgress
                    )
                    newlyUnlocked.add(unlockedAchievement)
                }
            }
        }
        
        if (newlyUnlocked.isNotEmpty()) {
            val currentAchievements = getUserAchievements().toMutableList()
            currentAchievements.addAll(newlyUnlocked)
            saveUserAchievements(currentAchievements)

            // Show notifications for newly unlocked achievements
            newlyUnlocked.forEach { achievement ->
                notificationManager.showAchievementNotification(achievement)
            }

            // Check for milestone notifications
            val totalAchievements = currentAchievements.size
            checkForMilestoneNotifications(totalAchievements)
        }

        return newlyUnlocked
    }
    
    /**
     * Get achievement progress for display
     */
    suspend fun getAchievementProgress(): List<Achievement> {
        if (!isGamificationEnabled()) return emptyList()
        
        val stats = getUserStats()
        val unlockedAchievements = getUserAchievements().associateBy { it.id }
        
        return getAllAchievements().map { achievement ->
            val unlocked = unlockedAchievements[achievement.id]
            if (unlocked != null) {
                unlocked
            } else {
                val progress = when (achievement.criteria.type) {
                    CriteriaType.VIDEOS_WATCHED -> stats.totalVideosWatched.toFloat()
                    CriteriaType.WATCH_TIME -> stats.totalWatchTime.toFloat()
                    CriteriaType.VIDEOS_COMPLETED -> stats.videosCompleted.toFloat()
                    CriteriaType.UNIQUE_CHANNELS -> stats.uniqueChannelsWatched.toFloat()
                    CriteriaType.FAVORITES_ADDED -> stats.favoriteVideos.toFloat()
                    CriteriaType.PLAYLISTS_CREATED -> stats.playlistsCreated.toFloat()
                    CriteriaType.CONSECUTIVE_DAYS -> stats.consecutiveDaysActive.toFloat()
                    CriteriaType.SESSION_LENGTH -> stats.longestWatchSession.toFloat()
                }

                // Format description for time-based achievements to show progress in readable format
                val formattedDescription = if (achievement.criteria.type == CriteriaType.WATCH_TIME) {
                    val currentMinutes = (stats.totalWatchTime / 1000 / 60).toInt()
                    val targetMinutes = (achievement.criteria.threshold / 1000 / 60).toInt()
                    val targetHours = targetMinutes / 60
                    val targetText = if (targetHours > 0) {
                        "${targetHours} hour${if (targetHours > 1) "s" else ""}"
                    } else {
                        "${targetMinutes} minute${if (targetMinutes > 1) "s" else ""}"
                    }
                    "${achievement.description} (${currentMinutes}/${targetMinutes} minutes)"
                } else {
                    achievement.description
                }
                
                achievement.copy(
                    description = formattedDescription,
                    progress = minOf(progress, achievement.criteria.threshold),
                    maxProgress = achievement.criteria.threshold
                )
            }
        }
    }
    
    // Helper methods
    private fun calculateConsecutiveDays(lastActiveDate: Long, currentConsecutiveDays: Int): Int {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()

        // Get today's date (start of day)
        calendar.timeInMillis = now
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis

        // Get last active date (start of day)
        calendar.timeInMillis = lastActiveDate
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val lastActiveStart = calendar.timeInMillis

        val daysDiff = TimeUnit.MILLISECONDS.toDays(todayStart - lastActiveStart)

        return when {
            lastActiveDate == 0L -> 1 // First day ever
            daysDiff == 0L -> currentConsecutiveDays // Same day - don't increment
            daysDiff == 1L -> currentConsecutiveDays + 1 // Next day - increment
            else -> 1 // Gap in days - reset to 1 (today becomes day 1)
        }
    }
    
    private fun saveUserStats(stats: UserStats) {
        val json = gson.toJson(stats)
        prefs.edit { putString(KEY_STATS, json) }
    }
    
    private fun saveUserAchievements(achievements: List<Achievement>) {
        val json = gson.toJson(achievements)
        prefs.edit { putString(KEY_ACHIEVEMENTS, json) }
    }

    /**
     * Check for milestone notifications
     */
    private fun checkForMilestoneNotifications(totalAchievements: Int) {
        val milestoneText = when (totalAchievements) {
            5 -> "First 5 achievements unlocked!"
            10 -> "10 achievements unlocked!"
            15 -> "15 achievements unlocked!"
            20 -> "Achievement Master - 20 unlocked!"
            else -> null
        }

        milestoneText?.let { text ->
            notificationManager.showMilestoneNotification(text, totalAchievements)
        }
    }

    /**
     * Get notification manager for external access
     */
    fun getNotificationManager(): AchievementNotificationManager {
        return notificationManager
    }

    /**
     * Reset specific achievements that may have been incorrectly unlocked
     */
    suspend fun resetIncorrectAchievements() {
        withContext(Dispatchers.IO) {
            val currentStats = getUserStats()
            val currentAchievements = getUserAchievements().toMutableList()
            val achievementsToRemove = mutableListOf<String>()

            // Debug: Log current stats
            android.util.Log.d("AchievementManager", "Current stats - Watch time: ${currentStats.totalWatchTime}ms (${currentStats.totalWatchTime / (1000 * 60 * 60)}h ${(currentStats.totalWatchTime / (1000 * 60)) % 60}m), Consecutive days: ${currentStats.consecutiveDaysActive}")

            // Check Marathon Viewer (10 hours = 36,000,000 milliseconds)
            if (currentStats.totalWatchTime < TimeUnit.HOURS.toMillis(10)) {
                achievementsToRemove.add("marathon_viewer")
                android.util.Log.d("AchievementManager", "Removing Marathon Viewer - insufficient watch time")
            }

            // Check Daily Viewer (3 consecutive days)
            if (currentStats.consecutiveDaysActive < 3) {
                achievementsToRemove.add("daily_viewer")
                android.util.Log.d("AchievementManager", "Removing Daily Viewer - insufficient consecutive days")
            }

            // Check Weekly Regular (7 consecutive days)
            if (currentStats.consecutiveDaysActive < 7) {
                achievementsToRemove.add("weekly_regular")
                android.util.Log.d("AchievementManager", "Removing Weekly Regular - insufficient consecutive days")
            }

            // Remove incorrectly unlocked achievements
            if (achievementsToRemove.isNotEmpty()) {
                android.util.Log.d("AchievementManager", "Removing ${achievementsToRemove.size} incorrectly unlocked achievements: $achievementsToRemove")
                currentAchievements.removeAll { achievement ->
                    achievementsToRemove.contains(achievement.id)
                }
                saveUserAchievements(currentAchievements)
            }
        }
    }

    /**
     * Get current user stats for debugging
     */
    suspend fun getCurrentUserStats(): UserStats {
        return withContext(Dispatchers.IO) {
            getUserStats()
        }
    }

    /**
     * Check if achievement notifications are enabled
     */
    fun areNotificationsEnabled(): Boolean {
        return prefs.getBoolean("notifications_enabled", true)
    }

    /**
     * Enable or disable achievement notifications
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("notifications_enabled", enabled) }
    }

    /**
     * Reset all achievement progress
     */
    suspend fun resetAllProgress() {
        withContext(Dispatchers.IO) {
            prefs.edit {
                remove(KEY_ACHIEVEMENTS)
                remove(KEY_STATS)
            }
        }
    }

    /**
     * Get current stats for debugging
     */
    suspend fun getStatsDebugInfo(): String {
        val stats = getUserStats()
        return buildString {
            appendLine("=== Achievement Stats Debug ===")
            appendLine("Videos Watched: ${stats.totalVideosWatched}")
            appendLine("Watch Time: ${stats.totalWatchTime / 1000}s")
            appendLine("Videos Completed: ${stats.videosCompleted}")
            appendLine("Unique Channels: ${stats.uniqueChannelsWatched}")
            appendLine("Channel IDs: ${stats.watchedChannelIds}")
            appendLine("Favorite Videos: ${stats.favoriteVideos}")
            appendLine("Playlists Created: ${stats.playlistsCreated}")
            appendLine("Consecutive Days: ${stats.consecutiveDaysActive}")
            appendLine("Last Active: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(stats.lastActiveDate))}")
        }
    }

    /**
     * Export achievement data for sharing
     */
    suspend fun exportAchievementData() {
        val achievements = getUserAchievements()
        val stats = getUserStats()

        val exportText = buildString {
            appendLine("VibeTube Achievement Export")
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine()
            appendLine("=== STATISTICS ===")
            appendLine("Videos Watched: ${stats.totalVideosWatched}")
            appendLine("Total Watch Time: ${stats.totalWatchTime / 1000 / 60} minutes")
            appendLine("Videos Completed: ${stats.videosCompleted}")
            appendLine("Unique Channels: ${stats.uniqueChannelsWatched}")
            appendLine("Favorite Videos: ${stats.favoriteVideos}")
            appendLine("Playlists Created: ${stats.playlistsCreated}")
            appendLine("Consecutive Days: ${stats.consecutiveDaysActive}")
            appendLine()
            appendLine("=== ACHIEVEMENTS (${achievements.size} unlocked) ===")
            achievements.forEach { achievement ->
                appendLine("🏆 ${achievement.title}")
                appendLine("   ${achievement.description}")
                appendLine("   Unlocked: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(achievement.unlockedAt))}")
                appendLine()
            }
            appendLine("Exported from VibeTube")
        }

        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, exportText)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "VibeTube Achievement Export")
        }

        context.startActivity(android.content.Intent.createChooser(shareIntent, "Export Achievements"))
    }
}
