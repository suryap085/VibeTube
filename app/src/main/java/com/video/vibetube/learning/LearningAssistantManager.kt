package com.video.vibetube.learning

import android.content.Context
import com.video.vibetube.models.Video
import com.video.vibetube.models.WatchHistoryItem
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Phase 3: Learning Assistant Manager for VibeTube
 * 
 * Features:
 * - Learning goal creation and tracking
 * - Skill progress analysis based on educational content
 * - Learning session management with focus tracking
 * - Educational content curation and recommendations
 * - Milestone tracking and achievement integration
 */
class LearningAssistantManager(
    private val context: Context,
    private val userDataManager: UserDataManager
) {
    
    companion object {
        private const val MIN_LEARNING_DURATION = 300000L // 5 minutes in milliseconds
        private const val FOCUS_THRESHOLD = 0.8f // 80% completion for focused learning
        private const val SKILL_LEVEL_THRESHOLD = 5 // Videos needed to advance skill level
    }
    
    data class LearningGoal(
        val id: String,
        val title: String,
        val description: String,
        val category: String,
        val targetSkillLevel: SkillLevel,
        val targetCompletionDate: Long,
        val createdAt: Long,
        val isActive: Boolean,
        val progress: LearningProgress
    )
    
    data class LearningProgress(
        val currentSkillLevel: SkillLevel,
        val videosWatched: Int,
        val totalWatchTime: Long, // milliseconds
        val completionRate: Double, // 0.0 to 1.0
        val focusScore: Double, // 0.0 to 1.0
        val lastActivity: Long,
        val milestones: List<Milestone>
    )
    
    enum class SkillLevel(val displayName: String, val order: Int) {
        BEGINNER("Beginner", 1),
        INTERMEDIATE("Intermediate", 2),
        ADVANCED("Advanced", 3),
        EXPERT("Expert", 4)
    }
    
    data class Milestone(
        val id: String,
        val title: String,
        val description: String,
        val achievedAt: Long,
        val skillLevel: SkillLevel
    )
    
    data class LearningSession(
        val id: String,
        val goalId: String?,
        val startTime: Long,
        val endTime: Long,
        val videosWatched: List<String>, // Video IDs
        val totalWatchTime: Long,
        val focusScore: Double,
        val category: String,
        val notes: String = ""
    )
    
    data class SkillAssessment(
        val category: String,
        val currentLevel: SkillLevel,
        val progress: Double, // 0.0 to 1.0 towards next level
        val strengths: List<String>,
        val improvementAreas: List<String>,
        val recommendedContent: List<Video>,
        val estimatedTimeToNextLevel: Long // milliseconds
    )
    
    data class LearningInsights(
        val totalLearningTime: Long,
        val averageSessionDuration: Long,
        val focusScore: Double,
        val skillProgression: Map<String, SkillLevel>,
        val learningStreak: Int, // Days
        val weeklyGoalProgress: Double,
        val recommendations: List<String>
    )
    
    /**
     * Create a new learning goal
     */
    suspend fun createLearningGoal(
        title: String,
        description: String,
        category: String,
        targetSkillLevel: SkillLevel,
        targetDays: Int
    ): LearningGoal {
        return withContext(Dispatchers.IO) {
            val goalId = UUID.randomUUID().toString()
            val targetDate = System.currentTimeMillis() + (targetDays * 24 * 60 * 60 * 1000L)
            
            val goal = LearningGoal(
                id = goalId,
                title = title,
                description = description,
                category = category,
                targetSkillLevel = targetSkillLevel,
                targetCompletionDate = targetDate,
                createdAt = System.currentTimeMillis(),
                isActive = true,
                progress = LearningProgress(
                    currentSkillLevel = SkillLevel.BEGINNER,
                    videosWatched = 0,
                    totalWatchTime = 0L,
                    completionRate = 0.0,
                    focusScore = 0.0,
                    lastActivity = System.currentTimeMillis(),
                    milestones = emptyList()
                )
            )
            
            saveLearningGoal(goal)
            goal
        }
    }
    
    /**
     * Track learning progress from watch history
     */
    suspend fun updateLearningProgress(videoId: String, watchDuration: Long, completionRate: Float) {
        withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) return@withContext
            
            val watchHistory = userDataManager.getWatchHistory()
            val video = watchHistory.find { it.videoId == videoId } ?: return@withContext
            
            if (isEducationalContent(video)) {
                val category = inferLearningCategory(video.title)
                updateSkillProgress(category, video, watchDuration, completionRate)
                updateActiveGoals(category, video, watchDuration, completionRate)
            }
        }
    }
    
    /**
     * Get current learning insights
     */
    suspend fun getLearningInsights(): LearningInsights {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext getEmptyInsights()
            }
            
            val watchHistory = userDataManager.getWatchHistory()
            val educationalVideos = watchHistory.filter { isEducationalContent(it) }
            
            if (educationalVideos.isEmpty()) {
                return@withContext getEmptyInsights()
            }
            
            val totalLearningTime = educationalVideos.sumOf { it.watchDuration }
            val averageSessionDuration = if (educationalVideos.isNotEmpty()) {
                totalLearningTime / educationalVideos.size
            } else 0L
            
            val focusScore = calculateOverallFocusScore(educationalVideos)
            val skillProgression = analyzeSkillProgression(educationalVideos)
            val learningStreak = calculateLearningStreak(educationalVideos)
            val weeklyProgress = calculateWeeklyGoalProgress()
            val recommendations = generateLearningRecommendations(educationalVideos, skillProgression)
            
            LearningInsights(
                totalLearningTime = totalLearningTime,
                averageSessionDuration = averageSessionDuration,
                focusScore = focusScore,
                skillProgression = skillProgression,
                learningStreak = learningStreak,
                weeklyGoalProgress = weeklyProgress,
                recommendations = recommendations
            )
        }
    }
    
    /**
     * Assess skill level in a specific category
     */
    suspend fun assessSkillLevel(category: String): SkillAssessment {
        return withContext(Dispatchers.IO) {
            val watchHistory = userDataManager.getWatchHistory()
            val categoryVideos = watchHistory.filter { 
                isEducationalContent(it) && inferLearningCategory(it.title) == category 
            }
            
            if (categoryVideos.isEmpty()) {
                return@withContext getBeginnerAssessment(category)
            }
            
            val currentLevel = determineSkillLevel(categoryVideos)
            val progress = calculateProgressToNextLevel(categoryVideos, currentLevel)
            val strengths = identifyStrengths(categoryVideos)
            val improvementAreas = identifyImprovementAreas(categoryVideos, currentLevel)
            val estimatedTime = estimateTimeToNextLevel(categoryVideos, currentLevel)
            
            SkillAssessment(
                category = category,
                currentLevel = currentLevel,
                progress = progress,
                strengths = strengths,
                improvementAreas = improvementAreas,
                recommendedContent = emptyList(), // Would be populated from recommendations
                estimatedTimeToNextLevel = estimatedTime
            )
        }
    }
    
    /**
     * Start a focused learning session
     */
    suspend fun startLearningSession(goalId: String? = null, category: String): String {
        return withContext(Dispatchers.IO) {
            val sessionId = UUID.randomUUID().toString()
            val session = LearningSession(
                id = sessionId,
                goalId = goalId,
                startTime = System.currentTimeMillis(),
                endTime = 0L,
                videosWatched = emptyList(),
                totalWatchTime = 0L,
                focusScore = 0.0,
                category = category
            )
            
            saveLearningSession(session)
            sessionId
        }
    }
    
    /**
     * End a learning session and calculate metrics
     */
    suspend fun endLearningSession(sessionId: String): LearningSession? {
        return withContext(Dispatchers.IO) {
            val session = getLearningSession(sessionId) ?: return@withContext null
            val endTime = System.currentTimeMillis()
            
            // Calculate session metrics
            val sessionDuration = endTime - session.startTime
            val focusScore = calculateSessionFocusScore(session.videosWatched, sessionDuration)
            
            val updatedSession = session.copy(
                endTime = endTime,
                focusScore = focusScore
            )
            
            saveLearningSession(updatedSession)
            
            // Update goal progress if applicable
            session.goalId?.let { goalId ->
                updateGoalFromSession(goalId, updatedSession)
            }
            
            updatedSession
        }
    }
    
    private fun isEducationalContent(video: WatchHistoryItem): Boolean {
        val title = video.title.lowercase()
        val educationalKeywords = listOf(
            "tutorial", "how to", "learn", "course", "lesson", "guide", "explained",
            "basics", "introduction", "beginner", "advanced", "masterclass", "training",
            "education", "teach", "instruction", "demo", "walkthrough", "step by step"
        )
        
        return educationalKeywords.any { keyword -> title.contains(keyword) } ||
                video.duration.let { parseDurationToMinutes(it) > 10 } // Longer videos more likely educational
    }
    
    private fun inferLearningCategory(title: String): String {
        val titleLower = title.lowercase()
        return when {
            titleLower.contains("programming") || titleLower.contains("coding") || titleLower.contains("software") -> "Programming"
            titleLower.contains("design") || titleLower.contains("photoshop") || titleLower.contains("ui") -> "Design"
            titleLower.contains("business") || titleLower.contains("marketing") || titleLower.contains("entrepreneur") -> "Business"
            titleLower.contains("language") || titleLower.contains("english") || titleLower.contains("spanish") -> "Languages"
            titleLower.contains("math") || titleLower.contains("physics") || titleLower.contains("science") -> "Science & Math"
            titleLower.contains("music") || titleLower.contains("guitar") || titleLower.contains("piano") -> "Music"
            titleLower.contains("cooking") || titleLower.contains("recipe") || titleLower.contains("chef") -> "Cooking"
            titleLower.contains("fitness") || titleLower.contains("workout") || titleLower.contains("yoga") -> "Fitness"
            titleLower.contains("art") || titleLower.contains("drawing") || titleLower.contains("painting") -> "Art"
            titleLower.contains("photography") || titleLower.contains("camera") || titleLower.contains("photo") -> "Photography"
            else -> "General Learning"
        }
    }
    
    private fun determineSkillLevel(videos: List<WatchHistoryItem>): SkillLevel {
        val completedVideos = videos.filter { it.watchProgress >= FOCUS_THRESHOLD }
        val totalWatchTime = videos.sumOf { it.watchDuration }
        val averageCompletion = videos.map { it.watchProgress }.average()
        
        return when {
            completedVideos.size >= 20 && totalWatchTime > 20 * 60 * 60 * 1000L && averageCompletion > 0.9 -> SkillLevel.EXPERT
            completedVideos.size >= 15 && totalWatchTime > 10 * 60 * 60 * 1000L && averageCompletion > 0.8 -> SkillLevel.ADVANCED
            completedVideos.size >= 8 && totalWatchTime > 5 * 60 * 60 * 1000L && averageCompletion > 0.7 -> SkillLevel.INTERMEDIATE
            else -> SkillLevel.BEGINNER
        }
    }
    
    private fun calculateProgressToNextLevel(videos: List<WatchHistoryItem>, currentLevel: SkillLevel): Double {
        val completedVideos = videos.filter { it.watchProgress >= FOCUS_THRESHOLD }.size
        val totalWatchTime = videos.sumOf { it.watchDuration }
        
        val nextLevelRequirements = when (currentLevel) {
            SkillLevel.BEGINNER -> Pair(8, 5 * 60 * 60 * 1000L) // 8 videos, 5 hours
            SkillLevel.INTERMEDIATE -> Pair(15, 10 * 60 * 60 * 1000L) // 15 videos, 10 hours
            SkillLevel.ADVANCED -> Pair(20, 20 * 60 * 60 * 1000L) // 20 videos, 20 hours
            SkillLevel.EXPERT -> return 1.0 // Already at max level
        }
        
        val videoProgress = (completedVideos.toDouble() / nextLevelRequirements.first.toDouble()).coerceAtMost(1.0)
        val timeProgress = (totalWatchTime.toDouble() / nextLevelRequirements.second.toDouble()).coerceAtMost(1.0)
        
        return (videoProgress + timeProgress) / 2.0
    }
    
    private fun identifyStrengths(videos: List<WatchHistoryItem>): List<String> {
        val strengths = mutableListOf<String>()
        
        val averageCompletion = videos.map { it.watchProgress }.average()
        if (averageCompletion > 0.8) {
            strengths.add("High completion rate - you stay focused on learning content")
        }
        
        val longVideos = videos.filter { parseDurationToMinutes(it.duration) > 30 }
        if (longVideos.size > videos.size * 0.5) {
            strengths.add("Comfortable with in-depth, comprehensive content")
        }
        
        val recentVideos = videos.filter { 
            System.currentTimeMillis() - it.watchedAt < 7 * 24 * 60 * 60 * 1000L 
        }
        if (recentVideos.size > 3) {
            strengths.add("Consistent learning habit - active in recent days")
        }
        
        return strengths.ifEmpty { listOf("Building foundational knowledge") }
    }
    
    private fun identifyImprovementAreas(videos: List<WatchHistoryItem>, currentLevel: SkillLevel): List<String> {
        val improvements = mutableListOf<String>()
        
        val averageCompletion = videos.map { it.watchProgress }.average()
        if (averageCompletion < 0.6) {
            improvements.add("Try shorter videos or break learning into smaller sessions")
        }
        
        val advancedVideos = videos.filter { 
            it.title.lowercase().contains("advanced") || it.title.lowercase().contains("expert")
        }
        if (currentLevel == SkillLevel.INTERMEDIATE && advancedVideos.isEmpty()) {
            improvements.add("Challenge yourself with more advanced content")
        }
        
        val practicalVideos = videos.filter { 
            it.title.lowercase().contains("project") || it.title.lowercase().contains("hands-on")
        }
        if (practicalVideos.size < videos.size * 0.3) {
            improvements.add("Include more hands-on, practical learning content")
        }
        
        return improvements.ifEmpty { listOf("Continue building on your current progress") }
    }
    
    private fun estimateTimeToNextLevel(videos: List<WatchHistoryItem>, currentLevel: SkillLevel): Long {
        if (currentLevel == SkillLevel.EXPERT) return 0L
        
        val recentLearningRate = calculateRecentLearningRate(videos)
        val progressNeeded = 1.0 - calculateProgressToNextLevel(videos, currentLevel)
        
        // Estimate based on current learning pace
        val estimatedDays = (progressNeeded * 30).toLong() // Rough estimate
        return estimatedDays * 24 * 60 * 60 * 1000L
    }
    
    private fun calculateRecentLearningRate(videos: List<WatchHistoryItem>): Double {
        val recentVideos = videos.filter { 
            System.currentTimeMillis() - it.watchedAt < 30 * 24 * 60 * 60 * 1000L // Last 30 days
        }
        
        return if (recentVideos.isNotEmpty()) {
            recentVideos.size.toDouble() / 30.0 // Videos per day
        } else 0.1 // Default slow rate
    }
    
    private fun calculateOverallFocusScore(videos: List<WatchHistoryItem>): Double {
        if (videos.isEmpty()) return 0.0
        return videos.map { it.watchProgress.toDouble() }.average()
    }
    
    private fun analyzeSkillProgression(videos: List<WatchHistoryItem>): Map<String, SkillLevel> {
        val categoryVideos = videos.groupBy { inferLearningCategory(it.title) }
        return categoryVideos.mapValues { (_, categoryVids) ->
            determineSkillLevel(categoryVids)
        }
    }
    
    private fun calculateLearningStreak(videos: List<WatchHistoryItem>): Int {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_YEAR)
        var streak = 0
        var currentDay = today
        
        val dailyLearning = videos.groupBy { video ->
            calendar.timeInMillis = video.watchedAt
            calendar.get(Calendar.DAY_OF_YEAR)
        }
        
        while (dailyLearning.containsKey(currentDay)) {
            streak++
            currentDay--
        }
        
        return streak
    }
    
    private fun calculateWeeklyGoalProgress(): Double {
        // This would integrate with actual learning goals
        return 0.7 // Placeholder
    }
    
    private fun generateLearningRecommendations(
        videos: List<WatchHistoryItem>,
        skillProgression: Map<String, SkillLevel>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (videos.isEmpty()) {
            recommendations.add("Start your learning journey by watching educational content")
            return recommendations
        }
        
        val averageCompletion = videos.map { it.watchProgress }.average()
        if (averageCompletion < 0.7) {
            recommendations.add("Try setting aside dedicated time for focused learning")
        }
        
        val categories = skillProgression.keys
        if (categories.size < 3) {
            recommendations.add("Explore new learning categories to broaden your skills")
        }
        
        val recentVideos = videos.filter { 
            System.currentTimeMillis() - it.watchedAt < 7 * 24 * 60 * 60 * 1000L 
        }
        if (recentVideos.isEmpty()) {
            recommendations.add("Resume your learning routine - consistency is key!")
        }
        
        return recommendations.take(3)
    }
    
    private fun calculateSessionFocusScore(videoIds: List<String>, sessionDuration: Long): Double {
        // Calculate focus based on video completion and session length
        return 0.8 // Placeholder implementation
    }
    
    private fun updateSkillProgress(category: String, video: WatchHistoryItem, watchDuration: Long, completionRate: Float) {
        // Update skill progress tracking
    }
    
    private fun updateActiveGoals(category: String, video: WatchHistoryItem, watchDuration: Long, completionRate: Float) {
        // Update progress for active learning goals
    }
    
    private fun updateGoalFromSession(goalId: String, session: LearningSession) {
        // Update goal progress from completed session
    }
    
    private fun saveLearningGoal(goal: LearningGoal) {
        // Save goal to local storage
    }
    
    private fun saveLearningSession(session: LearningSession) {
        // Save session to local storage
    }
    
    private fun getLearningSession(sessionId: String): LearningSession? {
        // Retrieve session from local storage
        return null
    }
    
    private fun parseDurationToMinutes(duration: String): Double {
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                2 -> parts[0].toDouble() + parts[1].toDouble() / 60.0
                3 -> parts[0].toDouble() * 60 + parts[1].toDouble() + parts[2].toDouble() / 60.0
                else -> 5.0
            }
        } catch (e: Exception) {
            5.0
        }
    }
    
    private fun getEmptyInsights(): LearningInsights {
        return LearningInsights(
            totalLearningTime = 0L,
            averageSessionDuration = 0L,
            focusScore = 0.0,
            skillProgression = emptyMap(),
            learningStreak = 0,
            weeklyGoalProgress = 0.0,
            recommendations = listOf("Start watching educational content to track your learning progress!")
        )
    }
    
    private fun getBeginnerAssessment(category: String): SkillAssessment {
        return SkillAssessment(
            category = category,
            currentLevel = SkillLevel.BEGINNER,
            progress = 0.0,
            strengths = listOf("Ready to start learning!"),
            improvementAreas = listOf("Begin with foundational content in $category"),
            recommendedContent = emptyList(),
            estimatedTimeToNextLevel = 30 * 24 * 60 * 60 * 1000L // 30 days
        )
    }
}
