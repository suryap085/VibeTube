package com.video.vibetube.wellness

import android.content.Context
import com.video.vibetube.models.WatchHistoryItem
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Phase 3: Digital Wellness Manager for VibeTube
 * 
 * Features:
 * - Viewing balance analysis and healthy usage tracking
 * - Break reminders and mindful viewing suggestions
 * - Screen time insights with daily/weekly summaries
 * - Content diversity monitoring for balanced consumption
 * - Wellness goals and achievement integration
 */
class DigitalWellnessManager(
    private val context: Context,
    private val userDataManager: UserDataManager
) {
    
    companion object {
        private const val RECOMMENDED_DAILY_LIMIT = 2 * 60 * 60 * 1000L // 2 hours in milliseconds
        private const val BREAK_REMINDER_INTERVAL = 30 * 60 * 1000L // 30 minutes
        private const val BINGE_THRESHOLD = 3 * 60 * 60 * 1000L // 3 hours continuous
        private const val HEALTHY_SESSION_LENGTH = 45 * 60 * 1000L // 45 minutes
    }
    
    data class WellnessInsights(
        val dailyScreenTime: Long, // milliseconds
        val weeklyScreenTime: Long,
        val averageSessionLength: Long,
        val wellnessScore: Double, // 0.0 to 1.0
        val contentDiversityScore: Double,
        val breakFrequency: Double, // breaks per hour
        val bingeWatchingRisk: BingeRisk,
        val recommendations: List<WellnessRecommendation>,
        val achievements: List<WellnessAchievement>
    )
    
    enum class BingeRisk {
        LOW, MODERATE, HIGH
    }
    
    data class WellnessRecommendation(
        val type: RecommendationType,
        val title: String,
        val description: String,
        val actionText: String,
        val priority: Priority
    )
    
    enum class RecommendationType {
        TAKE_BREAK, DIVERSIFY_CONTENT, REDUCE_SESSION_LENGTH, 
        SET_DAILY_LIMIT, MINDFUL_VIEWING, OFFLINE_ACTIVITY
    }
    
    enum class Priority {
        HIGH, MEDIUM, LOW
    }
    
    data class WellnessAchievement(
        val id: String,
        val title: String,
        val description: String,
        val achievedAt: Long,
        val category: WellnessCategory
    )
    
    enum class WellnessCategory {
        BALANCED_VIEWING, CONTENT_DIVERSITY, MINDFUL_BREAKS, 
        HEALTHY_SESSIONS, LEARNING_FOCUS
    }
    
    data class ViewingSession(
        val startTime: Long,
        val endTime: Long,
        val videosWatched: Int,
        val totalWatchTime: Long,
        val categories: Set<String>,
        val breaksTaken: Int
    )
    
    data class WellnessGoal(
        val id: String,
        val type: WellnessGoalType,
        val targetValue: Long, // milliseconds for time-based goals
        val currentProgress: Long,
        val deadline: Long,
        val isActive: Boolean
    )
    
    enum class WellnessGoalType {
        DAILY_LIMIT, WEEKLY_LIMIT, SESSION_LENGTH, 
        CONTENT_DIVERSITY, BREAK_FREQUENCY
    }
    
    /**
     * Get comprehensive wellness insights
     */
    suspend fun getWellnessInsights(): WellnessInsights {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext getEmptyInsights()
            }
            
            val watchHistory = userDataManager.getWatchHistory()
            if (watchHistory.isEmpty()) {
                return@withContext getEmptyInsights()
            }
            
            val dailyScreenTime = calculateDailyScreenTime(watchHistory)
            val weeklyScreenTime = calculateWeeklyScreenTime(watchHistory)
            val averageSessionLength = calculateAverageSessionLength(watchHistory)
            val wellnessScore = calculateWellnessScore(watchHistory)
            val contentDiversityScore = calculateContentDiversityScore(watchHistory)
            val breakFrequency = calculateBreakFrequency(watchHistory)
            val bingeRisk = assessBingeWatchingRisk(watchHistory)
            val recommendations = generateWellnessRecommendations(watchHistory, wellnessScore)
            val achievements = checkWellnessAchievements(watchHistory)
            
            WellnessInsights(
                dailyScreenTime = dailyScreenTime,
                weeklyScreenTime = weeklyScreenTime,
                averageSessionLength = averageSessionLength,
                wellnessScore = wellnessScore,
                contentDiversityScore = contentDiversityScore,
                breakFrequency = breakFrequency,
                bingeWatchingRisk = bingeRisk,
                recommendations = recommendations,
                achievements = achievements
            )
        }
    }
    
    /**
     * Check if user should take a break
     */
    suspend fun shouldSuggestBreak(): Boolean {
        return withContext(Dispatchers.IO) {
            val recentSession = getCurrentSession()
            val sessionDuration = recentSession?.let { 
                System.currentTimeMillis() - it.startTime 
            } ?: 0L
            
            sessionDuration > BREAK_REMINDER_INTERVAL
        }
    }
    
    /**
     * Get personalized break suggestion
     */
    suspend fun getBreakSuggestion(): WellnessRecommendation {
        return withContext(Dispatchers.IO) {
            val currentSession = getCurrentSession()
            val sessionDuration = currentSession?.let { 
                System.currentTimeMillis() - it.startTime 
            } ?: 0L
            
            when {
                sessionDuration > BINGE_THRESHOLD -> WellnessRecommendation(
                    type = RecommendationType.TAKE_BREAK,
                    title = "Time for a longer break!",
                    description = "You've been watching for over 3 hours. Consider taking a longer break.",
                    actionText = "Take a 15-minute break",
                    priority = Priority.HIGH
                )
                sessionDuration > HEALTHY_SESSION_LENGTH -> WellnessRecommendation(
                    type = RecommendationType.TAKE_BREAK,
                    title = "Quick break time",
                    description = "A short break can help you stay focused and refreshed.",
                    actionText = "Take a 5-minute break",
                    priority = Priority.MEDIUM
                )
                else -> WellnessRecommendation(
                    type = RecommendationType.MINDFUL_VIEWING,
                    title = "Mindful viewing",
                    description = "You're doing great! Keep enjoying your content mindfully.",
                    actionText = "Continue watching",
                    priority = Priority.LOW
                )
            }
        }
    }
    
    /**
     * Track viewing session for wellness analysis
     */
    suspend fun trackViewingSession(videoId: String, watchDuration: Long) {
        withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) return@withContext
            
            val currentTime = System.currentTimeMillis()
            updateCurrentSession(videoId, watchDuration, currentTime)
            checkForWellnessAlerts(currentTime)
        }
    }
    
    private fun calculateDailyScreenTime(watchHistory: List<WatchHistoryItem>): Long {
        val today = Calendar.getInstance()
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        return watchHistory
            .filter { it.watchedAt >= startOfDay }
            .sumOf { it.watchDuration }
    }
    
    private fun calculateWeeklyScreenTime(watchHistory: List<WatchHistoryItem>): Long {
        val weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        return watchHistory
            .filter { it.watchedAt >= weekAgo }
            .sumOf { it.watchDuration }
    }
    
    private fun calculateAverageSessionLength(watchHistory: List<WatchHistoryItem>): Long {
        val sessions = groupIntoSessions(watchHistory)
        return if (sessions.isNotEmpty()) {
            sessions.map { it.totalWatchTime }.average().toLong()
        } else 0L
    }
    
    private fun groupIntoSessions(watchHistory: List<WatchHistoryItem>): List<ViewingSession> {
        val sessions = mutableListOf<ViewingSession>()
        val sortedHistory = watchHistory.sortedBy { it.watchedAt }
        
        var currentSession: MutableList<WatchHistoryItem> = mutableListOf()
        var lastWatchTime = 0L
        
        sortedHistory.forEach { video ->
            // If more than 30 minutes gap, start new session
            if (video.watchedAt - lastWatchTime > 30 * 60 * 1000L && currentSession.isNotEmpty()) {
                sessions.add(createSessionFromVideos(currentSession))
                currentSession.clear()
            }
            
            currentSession.add(video)
            lastWatchTime = video.watchedAt + video.watchDuration
        }
        
        if (currentSession.isNotEmpty()) {
            sessions.add(createSessionFromVideos(currentSession))
        }
        
        return sessions
    }
    
    private fun createSessionFromVideos(videos: List<WatchHistoryItem>): ViewingSession {
        val startTime = videos.minOf { it.watchedAt }
        val endTime = videos.maxOf { it.watchedAt + it.watchDuration }
        val totalWatchTime = videos.sumOf { it.watchDuration }
        val categories = videos.map { inferCategoryFromTitle(it.title) }.toSet()
        
        return ViewingSession(
            startTime = startTime,
            endTime = endTime,
            videosWatched = videos.size,
            totalWatchTime = totalWatchTime,
            categories = categories,
            breaksTaken = 0 // Would need additional tracking
        )
    }
    
    private fun calculateWellnessScore(watchHistory: List<WatchHistoryItem>): Double {
        val dailyTime = calculateDailyScreenTime(watchHistory)
        val sessions = groupIntoSessions(watchHistory)
        val contentDiversity = calculateContentDiversityScore(watchHistory)
        
        // Score components
        val timeScore = calculateTimeScore(dailyTime)
        val sessionScore = calculateSessionScore(sessions)
        val diversityScore = contentDiversity
        
        return (timeScore * 0.4 + sessionScore * 0.3 + diversityScore * 0.3).coerceIn(0.0, 1.0)
    }
    
    private fun calculateTimeScore(dailyTime: Long): Double {
        return when {
            dailyTime <= RECOMMENDED_DAILY_LIMIT -> 1.0
            dailyTime <= RECOMMENDED_DAILY_LIMIT * 1.5 -> 0.7
            dailyTime <= RECOMMENDED_DAILY_LIMIT * 2 -> 0.4
            else -> 0.2
        }
    }
    
    private fun calculateSessionScore(sessions: List<ViewingSession>): Double {
        if (sessions.isEmpty()) return 1.0
        
        val averageSessionLength = sessions.map { it.totalWatchTime }.average()
        return when {
            averageSessionLength <= HEALTHY_SESSION_LENGTH -> 1.0
            averageSessionLength <= HEALTHY_SESSION_LENGTH * 1.5 -> 0.7
            averageSessionLength <= HEALTHY_SESSION_LENGTH * 2 -> 0.4
            else -> 0.2
        }
    }
    
    private fun calculateContentDiversityScore(watchHistory: List<WatchHistoryItem>): Double {
        if (watchHistory.isEmpty()) return 1.0
        
        val categories = watchHistory.map { inferCategoryFromTitle(it.title) }.distinct()
        val diversityRatio = categories.size.toDouble() / watchHistory.size.toDouble()
        
        return (diversityRatio * 5).coerceIn(0.0, 1.0) // Scale to 0-1
    }
    
    private fun calculateBreakFrequency(watchHistory: List<WatchHistoryItem>): Double {
        val sessions = groupIntoSessions(watchHistory)
        if (sessions.isEmpty()) return 0.0
        
        val totalSessionTime = sessions.sumOf { it.totalWatchTime }
        val totalBreaks = sessions.sumOf { it.breaksTaken }
        
        return if (totalSessionTime > 0) {
            (totalBreaks.toDouble() / (totalSessionTime.toDouble() / (60 * 60 * 1000L))) // Breaks per hour
        } else 0.0
    }
    
    private fun assessBingeWatchingRisk(watchHistory: List<WatchHistoryItem>): BingeRisk {
        val sessions = groupIntoSessions(watchHistory)
        val longSessions = sessions.filter { it.totalWatchTime > BINGE_THRESHOLD }
        val recentLongSessions = sessions.filter { 
            it.startTime > System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L) &&
            it.totalWatchTime > BINGE_THRESHOLD
        }
        
        return when {
            recentLongSessions.size >= 3 -> BingeRisk.HIGH
            recentLongSessions.size >= 1 || longSessions.size >= 5 -> BingeRisk.MODERATE
            else -> BingeRisk.LOW
        }
    }
    
    private fun generateWellnessRecommendations(
        watchHistory: List<WatchHistoryItem>,
        wellnessScore: Double
    ): List<WellnessRecommendation> {
        val recommendations = mutableListOf<WellnessRecommendation>()
        
        val dailyTime = calculateDailyScreenTime(watchHistory)
        if (dailyTime > RECOMMENDED_DAILY_LIMIT) {
            recommendations.add(WellnessRecommendation(
                type = RecommendationType.SET_DAILY_LIMIT,
                title = "Consider setting a daily limit",
                description = "You've watched ${dailyTime / (60 * 60 * 1000L)} hours today. Consider setting a daily limit.",
                actionText = "Set daily limit",
                priority = Priority.HIGH
            ))
        }
        
        val sessions = groupIntoSessions(watchHistory)
        val averageSessionLength = if (sessions.isNotEmpty()) {
            sessions.map { it.totalWatchTime }.average()
        } else 0.0
        
        if (averageSessionLength > HEALTHY_SESSION_LENGTH) {
            recommendations.add(WellnessRecommendation(
                type = RecommendationType.REDUCE_SESSION_LENGTH,
                title = "Try shorter viewing sessions",
                description = "Breaking up viewing into shorter sessions can improve focus and well-being.",
                actionText = "Set session reminders",
                priority = Priority.MEDIUM
            ))
        }
        
        val contentDiversity = calculateContentDiversityScore(watchHistory)
        if (contentDiversity < 0.3) {
            recommendations.add(WellnessRecommendation(
                type = RecommendationType.DIVERSIFY_CONTENT,
                title = "Explore different content types",
                description = "Diversifying your content can provide a more balanced viewing experience.",
                actionText = "Discover new categories",
                priority = Priority.MEDIUM
            ))
        }
        
        if (wellnessScore > 0.8) {
            recommendations.add(WellnessRecommendation(
                type = RecommendationType.MINDFUL_VIEWING,
                title = "Great viewing habits!",
                description = "You're maintaining healthy viewing patterns. Keep it up!",
                actionText = "Continue current habits",
                priority = Priority.LOW
            ))
        }
        
        return recommendations.sortedByDescending { it.priority }
    }
    
    private fun checkWellnessAchievements(watchHistory: List<WatchHistoryItem>): List<WellnessAchievement> {
        val achievements = mutableListOf<WellnessAchievement>()
        
        val wellnessScore = calculateWellnessScore(watchHistory)
        if (wellnessScore > 0.8) {
            achievements.add(WellnessAchievement(
                id = "balanced_viewer",
                title = "Balanced Viewer",
                description = "Maintained healthy viewing habits",
                achievedAt = System.currentTimeMillis(),
                category = WellnessCategory.BALANCED_VIEWING
            ))
        }
        
        val contentDiversity = calculateContentDiversityScore(watchHistory)
        if (contentDiversity > 0.7) {
            achievements.add(WellnessAchievement(
                id = "content_explorer",
                title = "Content Explorer",
                description = "Explored diverse content categories",
                achievedAt = System.currentTimeMillis(),
                category = WellnessCategory.CONTENT_DIVERSITY
            ))
        }
        
        return achievements
    }
    
    private fun getCurrentSession(): ViewingSession? {
        // Would track current active session
        return null
    }
    
    private fun updateCurrentSession(videoId: String, watchDuration: Long, currentTime: Long) {
        // Update current session tracking
    }
    
    private fun checkForWellnessAlerts(currentTime: Long) {
        // Check if any wellness alerts should be triggered
    }
    
    private fun inferCategoryFromTitle(title: String): String {
        val titleLower = title.lowercase()
        return when {
            titleLower.contains("music") || titleLower.contains("song") -> "Music"
            titleLower.contains("tutorial") || titleLower.contains("how to") -> "Education"
            titleLower.contains("game") || titleLower.contains("gaming") -> "Gaming"
            titleLower.contains("news") || titleLower.contains("breaking") -> "News"
            titleLower.contains("comedy") || titleLower.contains("funny") -> "Comedy"
            titleLower.contains("tech") || titleLower.contains("review") -> "Technology"
            titleLower.contains("cooking") || titleLower.contains("recipe") -> "Food"
            titleLower.contains("travel") || titleLower.contains("vlog") -> "Travel"
            titleLower.contains("fitness") || titleLower.contains("workout") -> "Health & Fitness"
            titleLower.contains("diy") || titleLower.contains("craft") -> "DIY & Crafts"
            else -> "Entertainment"
        }
    }
    
    private fun getEmptyInsights(): WellnessInsights {
        return WellnessInsights(
            dailyScreenTime = 0L,
            weeklyScreenTime = 0L,
            averageSessionLength = 0L,
            wellnessScore = 1.0,
            contentDiversityScore = 1.0,
            breakFrequency = 0.0,
            bingeWatchingRisk = BingeRisk.LOW,
            recommendations = listOf(
                WellnessRecommendation(
                    type = RecommendationType.MINDFUL_VIEWING,
                    title = "Start your wellness journey",
                    description = "Begin watching content to track your digital wellness",
                    actionText = "Start watching",
                    priority = Priority.LOW
                )
            ),
            achievements = emptyList()
        )
    }
}
