package com.video.vibetube.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * YouTube Policy Compliant Engagement Analytics
 * 
 * Compliance Features:
 * - Local analytics only (no external tracking)
 * - User privacy focused
 * - No personal data collection
 * - Transparent data usage
 * - User can disable analytics
 */
class EngagementAnalytics(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "engagement_analytics", Context.MODE_PRIVATE
    )
    private val gson = Gson()
    
    companion object {
        private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
        private const val KEY_SESSION_DATA = "session_data"
        private const val KEY_ENGAGEMENT_METRICS = "engagement_metrics"
        private const val KEY_FEATURE_USAGE = "feature_usage"
        private const val KEY_DAILY_STATS = "daily_stats"

        // Session tracking
        private const val SESSION_TIMEOUT_MS = 30 * 60 * 1000L // 30 minutes

        @Volatile
        private var INSTANCE: EngagementAnalytics? = null

        fun getInstance(context: Context): EngagementAnalytics {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EngagementAnalytics(context).also { INSTANCE = it }
            }
        }
    }
    
    data class SessionData(
        val sessionId: String = java.util.UUID.randomUUID().toString(),
        val startTime: Long = System.currentTimeMillis(),
        val endTime: Long = 0L,
        val videosWatched: Int = 0,
        val featuresUsed: MutableSet<String> = mutableSetOf(),
        val achievementsUnlocked: Int = 0,
        val socialShares: Int = 0,
        val searchQueries: Int = 0
    )
    
    data class EngagementMetrics(
        val totalSessions: Int = 0,
        val totalSessionTime: Long = 0L,
        val averageSessionTime: Long = 0L,
        val totalVideosWatched: Int = 0,
        val totalAchievementsUnlocked: Int = 0,
        val totalSocialShares: Int = 0,
        val totalSearchQueries: Int = 0,
        val libraryFeatureUsage: Int = 0,
        val recommendationClicks: Int = 0,
        val favoriteActions: Int = 0,
        val playlistActions: Int = 0,
        val lastUpdated: Long = System.currentTimeMillis()
    )
    
    data class FeatureUsage(
        val featureName: String,
        val usageCount: Int = 0,
        val firstUsed: Long = System.currentTimeMillis(),
        val lastUsed: Long = System.currentTimeMillis()
    )
    
    data class DailyStats(
        val date: String, // YYYY-MM-DD format
        val videosWatched: Int = 0,
        val sessionTime: Long = 0L,
        val featuresUsed: Set<String> = emptySet(),
        val achievementsUnlocked: Int = 0
    )
    
    private var currentSession: SessionData? = null
    
    /**
     * Check if analytics are enabled
     */
    fun isAnalyticsEnabled(): Boolean {
        return prefs.getBoolean(KEY_ANALYTICS_ENABLED, true)
    }
    
    /**
     * Enable/disable analytics
     */
    fun setAnalyticsEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ANALYTICS_ENABLED, enabled) }
        if (!enabled) {
            clearAllAnalyticsData()
        }
    }
    
    /**
     * Start a new session
     */
    suspend fun startSession() {
        if (!isAnalyticsEnabled()) return
        
        withContext(Dispatchers.IO) {
            currentSession = SessionData()
            saveCurrentSession()
        }
    }
    
    /**
     * End current session
     */
    suspend fun endSession() {
        if (!isAnalyticsEnabled()) return
        
        withContext(Dispatchers.IO) {
            currentSession?.let { session ->
                val endedSession = session.copy(endTime = System.currentTimeMillis())
                updateEngagementMetrics(endedSession)
                updateDailyStats(endedSession)
                currentSession = null
                clearCurrentSession()
            }
        }
    }
    
    /**
     * Track video watch event
     */
    suspend fun trackVideoWatch(videoId: String, watchDuration: Long, completed: Boolean) {
        if (!isAnalyticsEnabled()) return
        
        withContext(Dispatchers.IO) {
            currentSession?.let { session ->
                currentSession = session.copy(
                    videosWatched = session.videosWatched + 1
                )
                saveCurrentSession()
            }
            
            trackFeatureUsage("video_watch")
            if (completed) {
                trackFeatureUsage("video_completed")
            }
        }
    }
    
    /**
     * Track feature usage
     */
    suspend fun trackFeatureUsage(featureName: String) {
        if (!isAnalyticsEnabled()) return
        
        withContext(Dispatchers.IO) {
            currentSession?.let { session ->
                session.featuresUsed.add(featureName)
                saveCurrentSession()
            }
            
            updateFeatureUsage(featureName)
        }
    }
    
    /**
     * Track achievement unlock
     */
    suspend fun trackAchievementUnlock(achievementId: String) {
        if (!isAnalyticsEnabled()) return
        
        withContext(Dispatchers.IO) {
            currentSession?.let { session ->
                currentSession = session.copy(
                    achievementsUnlocked = session.achievementsUnlocked + 1
                )
                saveCurrentSession()
            }
            
            trackFeatureUsage("achievement_unlocked")
        }
    }
    
    /**
     * Track social sharing
     */
    suspend fun trackSocialShare(shareType: String, contentType: String) {
        if (!isAnalyticsEnabled()) return
        
        withContext(Dispatchers.IO) {
            currentSession?.let { session ->
                currentSession = session.copy(
                    socialShares = session.socialShares + 1
                )
                saveCurrentSession()
            }
            
            trackFeatureUsage("social_share_$shareType")
            trackFeatureUsage("share_$contentType")
        }
    }
    
    /**
     * Track search query
     */
    suspend fun trackSearchQuery(query: String, resultCount: Int) {
        if (!isAnalyticsEnabled()) return
        
        withContext(Dispatchers.IO) {
            currentSession?.let { session ->
                currentSession = session.copy(
                    searchQueries = session.searchQueries + 1
                )
                saveCurrentSession()
            }
            
            trackFeatureUsage("search_query")
            if (resultCount > 0) {
                trackFeatureUsage("search_successful")
            }
        }
    }
    
    /**
     * Track library feature usage
     */
    suspend fun trackLibraryFeature(action: String, section: String) {
        if (!isAnalyticsEnabled()) return
        
        withContext(Dispatchers.IO) {
            trackFeatureUsage("library_$section")
            trackFeatureUsage("library_action_$action")
        }
    }
    
    /**
     * Track recommendation interaction
     */
    suspend fun trackRecommendationClick(recommendationType: String, position: Int) {
        if (!isAnalyticsEnabled()) return
        
        withContext(Dispatchers.IO) {
            trackFeatureUsage("recommendation_click")
            trackFeatureUsage("recommendation_$recommendationType")
        }
    }
    
    /**
     * Get engagement metrics
     */
    suspend fun getEngagementMetrics(): EngagementMetrics {
        if (!isAnalyticsEnabled()) return EngagementMetrics()
        
        return withContext(Dispatchers.IO) {
            val json = prefs.getString(KEY_ENGAGEMENT_METRICS, null) ?: return@withContext EngagementMetrics()
            try {
                gson.fromJson(json, EngagementMetrics::class.java) ?: EngagementMetrics()
            } catch (e: Exception) {
                EngagementMetrics()
            }
        }
    }
    
    /**
     * Get feature usage statistics
     */
    suspend fun getFeatureUsageStats(): List<FeatureUsage> {
        if (!isAnalyticsEnabled()) return emptyList()
        
        return withContext(Dispatchers.IO) {
            val json = prefs.getString(KEY_FEATURE_USAGE, null) ?: return@withContext emptyList()
            val type = object : TypeToken<List<FeatureUsage>>() {}.type
            try {
                gson.fromJson<List<FeatureUsage>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Get daily statistics for the past week
     */
    suspend fun getWeeklyStats(): List<DailyStats> {
        if (!isAnalyticsEnabled()) return emptyList()
        
        return withContext(Dispatchers.IO) {
            val json = prefs.getString(KEY_DAILY_STATS, null) ?: return@withContext emptyList()
            val type = object : TypeToken<List<DailyStats>>() {}.type
            try {
                val allStats = gson.fromJson<List<DailyStats>>(json, type) ?: emptyList()
                // Return last 7 days
                allStats.takeLast(7)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Get engagement summary for display
     */
    suspend fun getEngagementSummary(): Map<String, Any> {
        if (!isAnalyticsEnabled()) return emptyMap()

        val metrics = getEngagementMetrics()
        val weeklyStats = getWeeklyStats()
        val featureStats = getFeatureUsageStats()

        return mapOf(
            "total_sessions" to metrics.totalSessions,
            "average_session_time" to formatDuration(metrics.averageSessionTime),
            "total_videos_watched" to metrics.totalVideosWatched,
            "total_achievements" to metrics.totalAchievementsUnlocked,
            "weekly_videos" to weeklyStats.sumOf { it.videosWatched },
            "weekly_session_time" to formatDuration(weeklyStats.sumOf { it.sessionTime }),
            "most_used_feature" to (featureStats.maxByOrNull { it.usageCount }?.featureName ?: "None"),
            "engagement_score" to calculateEngagementScore(metrics, weeklyStats)
        )
    }
    
    // Private helper methods
    
    private fun saveCurrentSession() {
        currentSession?.let { session ->
            val json = gson.toJson(session)
            prefs.edit { putString(KEY_SESSION_DATA, json) }
        }
    }
    
    private fun clearCurrentSession() {
        prefs.edit { remove(KEY_SESSION_DATA) }
    }
    
    private fun updateEngagementMetrics(session: SessionData) {
        val currentMetrics = runCatching {
            val json = prefs.getString(KEY_ENGAGEMENT_METRICS, null)
            json?.let { gson.fromJson(it, EngagementMetrics::class.java) }
        }.getOrNull() ?: EngagementMetrics()
        
        val sessionDuration = session.endTime - session.startTime
        val newTotalTime = currentMetrics.totalSessionTime + sessionDuration
        val newTotalSessions = currentMetrics.totalSessions + 1
        
        val updatedMetrics = currentMetrics.copy(
            totalSessions = newTotalSessions,
            totalSessionTime = newTotalTime,
            averageSessionTime = newTotalTime / newTotalSessions,
            totalVideosWatched = currentMetrics.totalVideosWatched + session.videosWatched,
            totalAchievementsUnlocked = currentMetrics.totalAchievementsUnlocked + session.achievementsUnlocked,
            totalSocialShares = currentMetrics.totalSocialShares + session.socialShares,
            totalSearchQueries = currentMetrics.totalSearchQueries + session.searchQueries,
            lastUpdated = System.currentTimeMillis()
        )
        
        val json = gson.toJson(updatedMetrics)
        prefs.edit { putString(KEY_ENGAGEMENT_METRICS, json) }
    }
    
    private fun updateFeatureUsage(featureName: String) {
        val currentUsage = runCatching {
            val json = prefs.getString(KEY_FEATURE_USAGE, null)
            val type = object : TypeToken<List<FeatureUsage>>() {}.type
            json?.let { gson.fromJson<List<FeatureUsage>>(it, type) }
        }.getOrNull()?.toMutableList() ?: mutableListOf()
        
        val existingFeature = currentUsage.find { it.featureName == featureName }
        if (existingFeature != null) {
            val index = currentUsage.indexOf(existingFeature)
            currentUsage[index] = existingFeature.copy(
                usageCount = existingFeature.usageCount + 1,
                lastUsed = System.currentTimeMillis()
            )
        } else {
            currentUsage.add(FeatureUsage(featureName, 1))
        }
        
        val json = gson.toJson(currentUsage)
        prefs.edit { putString(KEY_FEATURE_USAGE, json) }
    }
    
    private fun updateDailyStats(session: SessionData) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        val currentStats = runCatching {
            val json = prefs.getString(KEY_DAILY_STATS, null)
            val type = object : TypeToken<List<DailyStats>>() {}.type
            json?.let { gson.fromJson<List<DailyStats>>(it, type) }
        }.getOrNull()?.toMutableList() ?: mutableListOf()
        
        val todayStats = currentStats.find { it.date == today }
        val sessionDuration = session.endTime - session.startTime
        
        if (todayStats != null) {
            val index = currentStats.indexOf(todayStats)
            currentStats[index] = todayStats.copy(
                videosWatched = todayStats.videosWatched + session.videosWatched,
                sessionTime = todayStats.sessionTime + sessionDuration,
                featuresUsed = todayStats.featuresUsed + session.featuresUsed,
                achievementsUnlocked = todayStats.achievementsUnlocked + session.achievementsUnlocked
            )
        } else {
            currentStats.add(
                DailyStats(
                    date = today,
                    videosWatched = session.videosWatched,
                    sessionTime = sessionDuration,
                    featuresUsed = session.featuresUsed,
                    achievementsUnlocked = session.achievementsUnlocked
                )
            )
        }
        
        // Keep only last 30 days
        if (currentStats.size > 30) {
            currentStats.removeAt(0)
        }
        
        val json = gson.toJson(currentStats)
        prefs.edit { putString(KEY_DAILY_STATS, json) }
    }
    
    private fun formatDuration(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }
    
    private fun calculateEngagementScore(metrics: EngagementMetrics, weeklyStats: List<DailyStats>): Int {
        // Simple engagement score calculation (0-100)
        val sessionScore = minOf(metrics.totalSessions * 2, 30)
        val videoScore = minOf(metrics.totalVideosWatched, 25)
        val achievementScore = minOf(metrics.totalAchievementsUnlocked * 3, 20)
        val consistencyScore = minOf(weeklyStats.count { it.videosWatched > 0 } * 3, 15)
        val featureScore = minOf(metrics.libraryFeatureUsage, 10)
        
        return sessionScore + videoScore + achievementScore + consistencyScore + featureScore
    }
    
    private fun clearAllAnalyticsData() {
        prefs.edit {
            remove(KEY_SESSION_DATA)
            remove(KEY_ENGAGEMENT_METRICS)
            remove(KEY_FEATURE_USAGE)
            remove(KEY_DAILY_STATS)
        }
    }
}
