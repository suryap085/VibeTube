package com.video.vibetube.analytics

import android.content.Context
import com.video.vibetube.models.FavoriteItem
import com.video.vibetube.models.WatchHistoryItem
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Phase 2: Personal Analytics Manager for VibeTube
 * 
 * Features:
 * - Comprehensive viewing insights and pattern analysis
 * - Category preference tracking with trend analysis
 * - Time-based viewing behavior analysis
 * - Local-only processing maintaining YouTube policy compliance
 * - Privacy-first analytics with user consent
 */
class PersonalAnalyticsManager(
    private val context: Context,
    private val userDataManager: UserDataManager
) {
    
    companion object {
        private const val MILLISECONDS_PER_HOUR = 3600000L
        private const val MILLISECONDS_PER_DAY = 86400000L
        private const val DAYS_PER_WEEK = 7
        private const val MIN_SESSIONS_FOR_PATTERN = 5
    }
    
    data class ViewingInsights(
        val totalWatchTime: Long, // milliseconds
        val averageSessionDuration: Long, // milliseconds
        val totalVideosWatched: Int,
        val completionRate: Double, // 0.0 to 1.0
        val favoriteChannels: List<ChannelInsight>,
        val categoryPreferences: List<CategoryInsight>,
        val viewingPatterns: ViewingPatterns,
        val weeklyTrends: WeeklyTrends,
        val recommendations: List<String>
    )
    
    data class ChannelInsight(
        val channelId: String,
        val channelTitle: String,
        val videosWatched: Int,
        val totalWatchTime: Long,
        val averageCompletionRate: Double,
        val lastWatched: Long
    )
    
    data class CategoryInsight(
        val category: String,
        val videosCount: Int,
        val watchTimePercentage: Double,
        val averageRating: Double, // based on completion rates
        val trend: String // "increasing", "stable", "decreasing"
    )
    
    data class ViewingPatterns(
        val preferredTimeOfDay: String, // "morning", "afternoon", "evening", "night"
        val preferredDayOfWeek: String,
        val averageSessionsPerDay: Double,
        val bingWatchingTendency: Double, // 0.0 to 1.0
        val consistencyScore: Double // 0.0 to 1.0
    )
    
    data class WeeklyTrends(
        val thisWeekWatchTime: Long,
        val lastWeekWatchTime: Long,
        val changePercentage: Double,
        val thisWeekVideos: Int,
        val lastWeekVideos: Int,
        val videoCountChange: Double
    )
    
    /**
     * Generate comprehensive viewing insights from local data
     */
    suspend fun generateViewingInsights(): ViewingInsights {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext getEmptyInsights()
            }
            
            val watchHistory = userDataManager.getWatchHistory()
            val favorites = userDataManager.getFavorites()
            
            if (watchHistory.isEmpty()) {
                return@withContext getEmptyInsights()
            }
            
            val totalWatchTime = watchHistory.sumOf { it.watchDuration }
            val totalVideos = watchHistory.size
            val averageSessionDuration = if (totalVideos > 0) totalWatchTime / totalVideos else 0L
            val completionRate = calculateCompletionRate(watchHistory)
            
            val channelInsights = analyzeChannelPreferences(watchHistory)
            val categoryInsights = analyzeCategoryPreferences(watchHistory, favorites)
            val viewingPatterns = analyzeViewingPatterns(watchHistory)
            val weeklyTrends = analyzeWeeklyTrends(watchHistory)
            val recommendations = generatePersonalRecommendations(
                watchHistory, channelInsights, categoryInsights, viewingPatterns
            )
            
            ViewingInsights(
                totalWatchTime = totalWatchTime,
                averageSessionDuration = averageSessionDuration,
                totalVideosWatched = totalVideos,
                completionRate = completionRate,
                favoriteChannels = channelInsights,
                categoryPreferences = categoryInsights,
                viewingPatterns = viewingPatterns,
                weeklyTrends = weeklyTrends,
                recommendations = recommendations
            )
        }
    }
    
    private fun calculateCompletionRate(watchHistory: List<WatchHistoryItem>): Double {
        if (watchHistory.isEmpty()) return 0.0
        
        val completedVideos = watchHistory.count { it.isCompleted || it.watchProgress >= 0.9f }
        return completedVideos.toDouble() / watchHistory.size.toDouble()
    }
    
    private fun analyzeChannelPreferences(watchHistory: List<WatchHistoryItem>): List<ChannelInsight> {
        return watchHistory
            .groupBy { it.channelId }
            .map { (channelId, videos) ->
                val channelTitle = videos.first().channelTitle
                val totalWatchTime = videos.sumOf { it.watchDuration }
                val averageCompletion = videos.map { it.watchProgress }.average()
                val lastWatched = videos.maxOf { it.watchedAt }
                
                ChannelInsight(
                    channelId = channelId,
                    channelTitle = channelTitle,
                    videosWatched = videos.size,
                    totalWatchTime = totalWatchTime,
                    averageCompletionRate = averageCompletion,
                    lastWatched = lastWatched
                )
            }
            .sortedByDescending { it.totalWatchTime }
            .take(10) // Top 10 channels
    }
    
    private fun analyzeCategoryPreferences(
        watchHistory: List<WatchHistoryItem>,
        favorites: List<FavoriteItem>
    ): List<CategoryInsight> {
        // Infer categories from favorites and watch history
        val categoryData = mutableMapOf<String, MutableList<Pair<Long, Double>>>()
        
        // Analyze favorites categories
        favorites.forEach { favorite ->
            val category = favorite.category.ifEmpty { "General" }
            categoryData.getOrPut(category) { mutableListOf() }
                .add(Pair(0L, 1.0)) // Favorites get high rating
        }
        
        // Analyze watch history by inferring categories from titles
        watchHistory.forEach { item ->
            val category = inferCategoryFromTitle(item.title)
            categoryData.getOrPut(category) { mutableListOf() }
                .add(Pair(item.watchDuration, item.watchProgress.toDouble()))
        }
        
        val totalWatchTime = watchHistory.sumOf { it.watchDuration }
        
        return categoryData.map { (category, data) ->
            val watchTime = data.sumOf { it.first }
            val watchTimePercentage = if (totalWatchTime > 0) {
                (watchTime.toDouble() / totalWatchTime.toDouble()) * 100
            } else 0.0
            val averageRating = data.map { it.second }.average()
            val trend = calculateCategoryTrend(category, watchHistory)
            
            CategoryInsight(
                category = category,
                videosCount = data.size,
                watchTimePercentage = watchTimePercentage,
                averageRating = averageRating,
                trend = trend
            )
        }
        .sortedByDescending { it.watchTimePercentage }
        .take(8) // Top 8 categories
    }
    
    private fun inferCategoryFromTitle(title: String): String {
        val titleLower = title.lowercase()
        return when {
            titleLower.contains("music") || titleLower.contains("song") || titleLower.contains("album") -> "Music"
            titleLower.contains("tutorial") || titleLower.contains("how to") || titleLower.contains("learn") -> "Education"
            titleLower.contains("game") || titleLower.contains("gaming") || titleLower.contains("gameplay") -> "Gaming"
            titleLower.contains("news") || titleLower.contains("breaking") || titleLower.contains("update") -> "News"
            titleLower.contains("comedy") || titleLower.contains("funny") || titleLower.contains("humor") -> "Comedy"
            titleLower.contains("tech") || titleLower.contains("review") || titleLower.contains("unboxing") -> "Technology"
            titleLower.contains("cooking") || titleLower.contains("recipe") || titleLower.contains("food") -> "Food"
            titleLower.contains("travel") || titleLower.contains("vlog") || titleLower.contains("adventure") -> "Travel"
            titleLower.contains("fitness") || titleLower.contains("workout") || titleLower.contains("health") -> "Health & Fitness"
            titleLower.contains("diy") || titleLower.contains("craft") || titleLower.contains("build") -> "DIY & Crafts"
            else -> "Entertainment"
        }
    }
    
    private fun calculateCategoryTrend(category: String, watchHistory: List<WatchHistoryItem>): String {
        val now = System.currentTimeMillis()
        val twoWeeksAgo = now - (14 * MILLISECONDS_PER_DAY)
        val oneWeekAgo = now - (7 * MILLISECONDS_PER_DAY)
        
        val recentVideos = watchHistory.filter { 
            inferCategoryFromTitle(it.title) == category && it.watchedAt > twoWeeksAgo 
        }
        
        if (recentVideos.size < 4) return "stable" // Not enough data
        
        val lastWeekCount = recentVideos.count { it.watchedAt > oneWeekAgo }
        val previousWeekCount = recentVideos.count { it.watchedAt in twoWeeksAgo..oneWeekAgo }
        
        return when {
            lastWeekCount > previousWeekCount * 1.2 -> "increasing"
            lastWeekCount < previousWeekCount * 0.8 -> "decreasing"
            else -> "stable"
        }
    }
    
    private fun analyzeViewingPatterns(watchHistory: List<WatchHistoryItem>): ViewingPatterns {
        if (watchHistory.size < MIN_SESSIONS_FOR_PATTERN) {
            return ViewingPatterns("evening", "weekend", 0.0, 0.0, 0.0)
        }
        
        val calendar = Calendar.getInstance()
        val hourCounts = mutableMapOf<Int, Int>()
        val dayCounts = mutableMapOf<Int, Int>()
        val dailySessions = mutableMapOf<String, Int>()
        
        watchHistory.forEach { item ->
            calendar.timeInMillis = item.watchedAt
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val dateKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            
            hourCounts[hour] = hourCounts.getOrDefault(hour, 0) + 1
            dayCounts[dayOfWeek] = dayCounts.getOrDefault(dayOfWeek, 0) + 1
            dailySessions[dateKey] = dailySessions.getOrDefault(dateKey, 0) + 1
        }
        
        val preferredHour = hourCounts.maxByOrNull { it.value }?.key ?: 20
        val preferredDay = dayCounts.maxByOrNull { it.value }?.key ?: Calendar.SATURDAY
        
        val preferredTimeOfDay = when (preferredHour) {
            in 6..11 -> "morning"
            in 12..17 -> "afternoon"
            in 18..22 -> "evening"
            else -> "night"
        }
        
        val preferredDayOfWeek = when (preferredDay) {
            Calendar.SATURDAY, Calendar.SUNDAY -> "weekend"
            else -> "weekday"
        }
        
        val averageSessionsPerDay = if (dailySessions.isNotEmpty()) {
            dailySessions.values.average()
        } else 0.0
        
        val bingWatchingTendency = calculateBingeWatchingTendency(watchHistory)
        val consistencyScore = calculateConsistencyScore(dailySessions)
        
        return ViewingPatterns(
            preferredTimeOfDay = preferredTimeOfDay,
            preferredDayOfWeek = preferredDayOfWeek,
            averageSessionsPerDay = averageSessionsPerDay,
            bingWatchingTendency = bingWatchingTendency,
            consistencyScore = consistencyScore
        )
    }
    
    private fun calculateBingeWatchingTendency(watchHistory: List<WatchHistoryItem>): Double {
        // Group videos by day and calculate sessions
        val calendar = Calendar.getInstance()
        val dailyWatchTime = mutableMapOf<String, Long>()
        
        watchHistory.forEach { item ->
            calendar.timeInMillis = item.watchedAt
            val dateKey = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            dailyWatchTime[dateKey] = dailyWatchTime.getOrDefault(dateKey, 0L) + item.watchDuration
        }
        
        if (dailyWatchTime.isEmpty()) return 0.0
        
        val averageDailyTime = dailyWatchTime.values.average()
        val longSessions = dailyWatchTime.values.count { it > averageDailyTime * 2 }
        
        return (longSessions.toDouble() / dailyWatchTime.size.toDouble()).coerceIn(0.0, 1.0)
    }
    
    private fun calculateConsistencyScore(dailySessions: Map<String, Int>): Double {
        if (dailySessions.size < 7) return 0.0 // Need at least a week of data
        
        val sessionCounts = dailySessions.values
        val average = sessionCounts.average()
        val variance = sessionCounts.map { (it - average) * (it - average) }.average()
        val standardDeviation = kotlin.math.sqrt(variance)
        
        // Lower standard deviation = higher consistency
        val consistencyScore = 1.0 - (standardDeviation / (average + 1.0)).coerceIn(0.0, 1.0)
        return consistencyScore
    }
    
    private fun analyzeWeeklyTrends(watchHistory: List<WatchHistoryItem>): WeeklyTrends {
        val now = System.currentTimeMillis()
        val oneWeekAgo = now - (7 * MILLISECONDS_PER_DAY)
        val twoWeeksAgo = now - (14 * MILLISECONDS_PER_DAY)
        
        val thisWeekVideos = watchHistory.filter { it.watchedAt > oneWeekAgo }
        val lastWeekVideos = watchHistory.filter { it.watchedAt in twoWeeksAgo..oneWeekAgo }
        
        val thisWeekWatchTime = thisWeekVideos.sumOf { it.watchDuration }
        val lastWeekWatchTime = lastWeekVideos.sumOf { it.watchDuration }
        
        val changePercentage = if (lastWeekWatchTime > 0) {
            ((thisWeekWatchTime - lastWeekWatchTime).toDouble() / lastWeekWatchTime.toDouble()) * 100
        } else if (thisWeekWatchTime > 0) 100.0 else 0.0
        
        val videoCountChange = if (lastWeekVideos.isNotEmpty()) {
            ((thisWeekVideos.size - lastWeekVideos.size).toDouble() / lastWeekVideos.size.toDouble()) * 100
        } else if (thisWeekVideos.isNotEmpty()) 100.0 else 0.0
        
        return WeeklyTrends(
            thisWeekWatchTime = thisWeekWatchTime,
            lastWeekWatchTime = lastWeekWatchTime,
            changePercentage = changePercentage,
            thisWeekVideos = thisWeekVideos.size,
            lastWeekVideos = lastWeekVideos.size,
            videoCountChange = videoCountChange
        )
    }
    
    private fun generatePersonalRecommendations(
        watchHistory: List<WatchHistoryItem>,
        channelInsights: List<ChannelInsight>,
        categoryInsights: List<CategoryInsight>,
        viewingPatterns: ViewingPatterns
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Viewing time recommendations
        val totalHours = watchHistory.sumOf { it.watchDuration } / MILLISECONDS_PER_HOUR
        when {
            totalHours > 40 -> recommendations.add("Consider taking breaks between videos for better focus")
            totalHours < 2 -> recommendations.add("Explore more content in your favorite categories")
            else -> recommendations.add("Your viewing time looks balanced")
        }
        
        // Category diversity recommendations
        if (categoryInsights.size < 3) {
            recommendations.add("Try exploring new content categories for variety")
        } else if (categoryInsights.first().watchTimePercentage > 70) {
            recommendations.add("Consider diversifying your content consumption")
        }
        
        // Completion rate recommendations
        val completionRate = watchHistory.count { it.isCompleted }.toDouble() / watchHistory.size
        when {
            completionRate < 0.3 -> recommendations.add("Try shorter videos or content that better matches your interests")
            completionRate > 0.8 -> recommendations.add("You're great at finding engaging content!")
            else -> recommendations.add("Your content selection seems well-matched to your interests")
        }
        
        // Viewing pattern recommendations
        if (viewingPatterns.bingWatchingTendency > 0.7) {
            recommendations.add("Consider setting viewing time limits for healthier habits")
        }
        
        if (viewingPatterns.consistencyScore > 0.8) {
            recommendations.add("Your consistent viewing schedule is excellent!")
        }
        
        // Channel recommendations
        if (channelInsights.isNotEmpty()) {
            val topChannel = channelInsights.first()
            recommendations.add("You really enjoy ${topChannel.channelTitle} - check out their latest uploads")
        }
        
        return recommendations.take(5) // Limit to 5 recommendations
    }
    
    private fun getEmptyInsights(): ViewingInsights {
        return ViewingInsights(
            totalWatchTime = 0L,
            averageSessionDuration = 0L,
            totalVideosWatched = 0,
            completionRate = 0.0,
            favoriteChannels = emptyList(),
            categoryPreferences = emptyList(),
            viewingPatterns = ViewingPatterns("evening", "weekend", 0.0, 0.0, 0.0),
            weeklyTrends = WeeklyTrends(0L, 0L, 0.0, 0, 0, 0.0),
            recommendations = listOf("Start watching videos to see your personalized insights!")
        )
    }
}
