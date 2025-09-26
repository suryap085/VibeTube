package com.video.vibetube.ml

import android.content.Context
import com.video.vibetube.models.Video
import com.video.vibetube.models.WatchHistoryItem
import com.video.vibetube.models.FavoriteItem
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.RecommendationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Phase 2: Predictive Recommendation Engine for VibeTube
 * 
 * Features:
 * - Advanced user profiling and preference learning
 * - Predictive engagement scoring using local ML
 * - Diversity filtering to avoid echo chambers
 * - Multi-factor recommendation confidence scoring
 * - Builds upon existing RecommendationEngine with enhanced capabilities
 */
class PredictiveRecommendationEngine(
    private val context: Context,
    private val userDataManager: UserDataManager,
    private val baseRecommendationEngine: RecommendationEngine
) {
    
    companion object {
        private const val LEARNING_RATE = 0.1
        private const val DECAY_FACTOR = 0.95
        private const val MIN_CONFIDENCE_THRESHOLD = 0.3
        private const val DIVERSITY_WEIGHT = 0.3
        private const val FRESHNESS_WEIGHT = 0.2
        private const val ENGAGEMENT_WEIGHT = 0.5
    }
    
    data class UserProfile(
        val preferredCategories: Map<String, Double>, // Category -> preference score
        val preferredChannels: Map<String, Double>, // Channel -> preference score
        val preferredDurations: DurationPreference,
        val viewingTimePatterns: Map<Int, Double>, // Hour -> activity score
        val engagementPatterns: EngagementPattern,
        val diversityScore: Double, // 0.0 to 1.0
        val lastUpdated: Long
    )
    
    data class DurationPreference(
        val shortVideos: Double, // < 5 minutes
        val mediumVideos: Double, // 5-20 minutes
        val longVideos: Double // > 20 minutes
    )
    
    data class EngagementPattern(
        val averageWatchProgress: Double,
        val completionRate: Double,
        val skipRate: Double,
        val replayRate: Double
    )
    
    data class PredictiveRecommendation(
        val video: Video,
        val engagementScore: Double, // Predicted engagement 0.0 to 1.0
        val confidenceScore: Double, // Confidence in prediction 0.0 to 1.0
        val diversityScore: Double, // How different from recent content 0.0 to 1.0
        val reasons: List<String>, // Human-readable reasons
        val factors: Map<String, Double> // Factor contributions
    )
    
    data class RecommendationContext(
        val timeOfDay: Int, // Hour 0-23
        val availableTime: Int, // Minutes available
        val recentCategories: List<String>, // Recently watched categories
        val mood: String = "neutral" // "focused", "relaxed", "discovery"
    )
    
    /**
     * Generate predictive recommendations with enhanced scoring
     */
    suspend fun generatePredictiveRecommendations(
        context: RecommendationContext,
        maxResults: Int = 20
    ): List<PredictiveRecommendation> {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext emptyList()
            }
            
            // Build user profile
            val userProfile = buildUserProfile()
            
            // Get base recommendations from existing engine
            val baseRecommendations = baseRecommendationEngine.getPersonalizedRecommendations("user", maxResults * 2)
            
            if (baseRecommendations.isEmpty()) {
                return@withContext emptyList()
            }
            
            // Score each recommendation
            val scoredRecommendations = baseRecommendations.map { video ->
                scorePredictiveRecommendation(video, userProfile, context)
            }
            
            // Apply diversity filtering
            val diversifiedRecommendations = applyDiversityFiltering(scoredRecommendations, context)
            
            // Sort by combined score and return top results
            diversifiedRecommendations
                .filter { it.confidenceScore > MIN_CONFIDENCE_THRESHOLD }
                .sortedByDescending { calculateCombinedScore(it) }
                .take(maxResults)
        }
    }
    
    /**
     * Build comprehensive user profile from local data
     */
    suspend fun buildUserProfile(): UserProfile {
        return withContext(Dispatchers.IO) {
            val watchHistory = userDataManager.getWatchHistory()
            val favorites = userDataManager.getFavorites()
            
            if (watchHistory.isEmpty()) {
                return@withContext getDefaultProfile()
            }
            
            val preferredCategories = analyzePreferredCategories(watchHistory, favorites)
            val preferredChannels = analyzePreferredChannels(watchHistory, favorites)
            val durationPreference = analyzeDurationPreferences(watchHistory)
            val timePatterns = analyzeViewingTimePatterns(watchHistory)
            val engagementPatterns = analyzeEngagementPatterns(watchHistory)
            val diversityScore = calculateDiversityScore(watchHistory)
            
            UserProfile(
                preferredCategories = preferredCategories,
                preferredChannels = preferredChannels,
                preferredDurations = durationPreference,
                viewingTimePatterns = timePatterns,
                engagementPatterns = engagementPatterns,
                diversityScore = diversityScore,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
    
    private fun scorePredictiveRecommendation(
        video: Video,
        userProfile: UserProfile,
        context: RecommendationContext
    ): PredictiveRecommendation {
        val factors = mutableMapOf<String, Double>()
        val reasons = mutableListOf<String>()
        
        // Category preference score
        val category = inferCategoryFromVideo(video)
        val categoryScore = userProfile.preferredCategories[category] ?: 0.0
        factors["category"] = categoryScore
        if (categoryScore > 0.7) {
            reasons.add("Matches your interest in $category content")
        }
        
        // Channel preference score
        val channelScore = userProfile.preferredChannels[video.channelId] ?: 0.0
        factors["channel"] = channelScore
        if (channelScore > 0.6) {
            reasons.add("From ${video.channelTitle}, a channel you enjoy")
        }
        
        // Duration preference score
        val durationScore = calculateDurationScore(video.duration, userProfile.preferredDurations, context.availableTime)
        factors["duration"] = durationScore
        if (durationScore > 0.8) {
            reasons.add("Perfect length for your viewing preferences")
        }
        
        // Time-based score
        val timeScore = userProfile.viewingTimePatterns[context.timeOfDay] ?: 0.5
        factors["time"] = timeScore
        
        // Freshness score (prefer newer content)
        val freshnessScore = calculateFreshnessScore(video.publishedAt)
        factors["freshness"] = freshnessScore
        if (freshnessScore > 0.8) {
            reasons.add("Recently published content")
        }
        
        // Diversity score (how different from recent content)
        val diversityScore = calculateContentDiversityScore(video, context.recentCategories)
        factors["diversity"] = diversityScore
        if (diversityScore > 0.7) {
            reasons.add("Offers variety from your recent viewing")
        }
        
        // Calculate engagement prediction
        val engagementScore = predictEngagement(factors, userProfile.engagementPatterns)
        
        // Calculate confidence
        val confidenceScore = calculateConfidence(factors, userProfile)
        
        if (reasons.isEmpty()) {
            reasons.add("Recommended based on your viewing patterns")
        }
        
        return PredictiveRecommendation(
            video = video,
            engagementScore = engagementScore,
            confidenceScore = confidenceScore,
            diversityScore = diversityScore,
            reasons = reasons,
            factors = factors
        )
    }
    
    private fun analyzePreferredCategories(
        watchHistory: List<WatchHistoryItem>,
        favorites: List<FavoriteItem>
    ): Map<String, Double> {
        val categoryScores = mutableMapOf<String, Double>()
        
        // Analyze watch history with engagement weighting
        watchHistory.forEach { item ->
            val category = inferCategoryFromTitle(item.title)
            val engagementWeight = item.watchProgress.toDouble()
            categoryScores[category] = categoryScores.getOrDefault(category, 0.0) + engagementWeight
        }
        
        // Boost categories from favorites
        favorites.forEach { item ->
            val category = item.category.ifEmpty { inferCategoryFromTitle(item.title) }
            categoryScores[category] = categoryScores.getOrDefault(category, 0.0) + 1.5 // Favorites get higher weight
        }
        
        // Normalize scores
        val maxScore = categoryScores.values.maxOrNull() ?: 1.0
        return categoryScores.mapValues { (it.value / maxScore).coerceIn(0.0, 1.0) }
    }
    
    private fun analyzePreferredChannels(
        watchHistory: List<WatchHistoryItem>,
        favorites: List<FavoriteItem>
    ): Map<String, Double> {
        val channelScores = mutableMapOf<String, Double>()
        
        // Analyze watch history
        watchHistory.forEach { item ->
            val engagementWeight = item.watchProgress.toDouble()
            channelScores[item.channelId] = channelScores.getOrDefault(item.channelId, 0.0) + engagementWeight
        }
        
        // Boost channels from favorites
        favorites.forEach { item ->
            channelScores[item.channelId] = channelScores.getOrDefault(item.channelId, 0.0) + 1.5
        }
        
        // Normalize scores
        val maxScore = channelScores.values.maxOrNull() ?: 1.0
        return channelScores.mapValues { (it.value / maxScore).coerceIn(0.0, 1.0) }
    }
    
    private fun analyzeDurationPreferences(watchHistory: List<WatchHistoryItem>): DurationPreference {
        val shortVideos = watchHistory.filter { parseDurationToMinutes(it.duration) < 5 }
        val mediumVideos = watchHistory.filter { parseDurationToMinutes(it.duration) in 5.0..20.0 }
        val longVideos = watchHistory.filter { parseDurationToMinutes(it.duration) > 20 }
        
        val shortScore = if (shortVideos.isNotEmpty()) shortVideos.map { it.watchProgress }.average() else 0.0
        val mediumScore = if (mediumVideos.isNotEmpty()) mediumVideos.map { it.watchProgress }.average() else 0.0
        val longScore = if (longVideos.isNotEmpty()) longVideos.map { it.watchProgress }.average() else 0.0
        
        return DurationPreference(shortScore, mediumScore, longScore)
    }
    
    private fun analyzeViewingTimePatterns(watchHistory: List<WatchHistoryItem>): Map<Int, Double> {
        val hourCounts = mutableMapOf<Int, Int>()
        val calendar = java.util.Calendar.getInstance()
        
        watchHistory.forEach { item ->
            calendar.timeInMillis = item.watchedAt
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            hourCounts[hour] = hourCounts.getOrDefault(hour, 0) + 1
        }
        
        val maxCount = hourCounts.values.maxOrNull() ?: 1
        return (0..23).associateWith { hour ->
            (hourCounts[hour] ?: 0).toDouble() / maxCount.toDouble()
        }
    }
    
    private fun analyzeEngagementPatterns(watchHistory: List<WatchHistoryItem>): EngagementPattern {
        if (watchHistory.isEmpty()) {
            return EngagementPattern(0.0, 0.0, 0.0, 0.0)
        }
        
        val averageProgress = watchHistory.map { it.watchProgress }.average()
        val completionRate = watchHistory.count { it.isCompleted }.toDouble() / watchHistory.size
        val skipRate = watchHistory.count { it.watchProgress < 0.1f }.toDouble() / watchHistory.size
        val replayRate = 0.0 // Would need additional data to calculate
        
        return EngagementPattern(averageProgress, completionRate, skipRate, replayRate)
    }
    
    private fun calculateDiversityScore(watchHistory: List<WatchHistoryItem>): Double {
        if (watchHistory.size < 5) return 1.0
        
        val categories = watchHistory.map { inferCategoryFromTitle(it.title) }.distinct()
        val channels = watchHistory.map { it.channelId }.distinct()
        
        val categoryDiversity = categories.size.toDouble() / watchHistory.size.toDouble()
        val channelDiversity = channels.size.toDouble() / watchHistory.size.toDouble()
        
        return ((categoryDiversity + channelDiversity) / 2.0).coerceIn(0.0, 1.0)
    }
    
    private fun calculateDurationScore(
        videoDuration: String,
        preferences: DurationPreference,
        availableTime: Int
    ): Double {
        val durationMinutes = parseDurationToMinutes(videoDuration)
        
        // Check if video fits available time
        val timeScore = if (durationMinutes <= availableTime) 1.0 else 0.5
        
        // Check duration preference
        val preferenceScore = when {
            durationMinutes < 5 -> preferences.shortVideos
            durationMinutes <= 20 -> preferences.mediumVideos
            else -> preferences.longVideos
        }
        
        return (timeScore * 0.6 + preferenceScore * 0.4).coerceIn(0.0, 1.0)
    }
    
    private fun calculateFreshnessScore(publishedAt: String): Double {
        // Simple freshness calculation - would need proper date parsing in real implementation
        return 0.7 // Default freshness score
    }
    
    private fun calculateContentDiversityScore(video: Video, recentCategories: List<String>): Double {
        val videoCategory = inferCategoryFromVideo(video)
        val recentCategoryCount = recentCategories.count { it == videoCategory }
        
        return when {
            recentCategoryCount == 0 -> 1.0 // Completely new category
            recentCategoryCount <= 2 -> 0.7 // Some variety
            else -> 0.3 // Too much of same category
        }
    }
    
    private fun predictEngagement(factors: Map<String, Double>, patterns: EngagementPattern): Double {
        // Simple linear combination for engagement prediction
        val baseScore = factors.values.average()
        val patternBoost = patterns.averageWatchProgress * 0.3
        
        return (baseScore + patternBoost).coerceIn(0.0, 1.0)
    }
    
    private fun calculateConfidence(factors: Map<String, Double>, profile: UserProfile): Double {
        // Confidence based on data availability and factor consistency
        val factorVariance = calculateVariance(factors.values.toList())
        val dataRichness = minOf(1.0, profile.preferredCategories.size / 5.0)
        
        return (1.0 - factorVariance) * dataRichness
    }
    
    private fun calculateVariance(values: List<Double>): Double {
        if (values.isEmpty()) return 1.0
        val mean = values.average()
        return values.map { (it - mean).pow(2) }.average()
    }
    
    private fun applyDiversityFiltering(
        recommendations: List<PredictiveRecommendation>,
        context: RecommendationContext
    ): List<PredictiveRecommendation> {
        // Ensure diversity in final recommendations
        val diversified = mutableListOf<PredictiveRecommendation>()
        val usedCategories = mutableSetOf<String>()
        val usedChannels = mutableSetOf<String>()
        
        recommendations.sortedByDescending { it.engagementScore }.forEach { rec ->
            val category = inferCategoryFromVideo(rec.video)
            val channel = rec.video.channelId
            
            val categoryCount = usedCategories.count { it == category }
            val channelCount = usedChannels.count { it == channel }
            
            // Allow some repetition but encourage diversity
            if (categoryCount < 3 && channelCount < 2) {
                diversified.add(rec)
                usedCategories.add(category)
                usedChannels.add(channel)
            }
        }
        
        return diversified
    }
    
    private fun calculateCombinedScore(recommendation: PredictiveRecommendation): Double {
        return recommendation.engagementScore * ENGAGEMENT_WEIGHT +
                recommendation.diversityScore * DIVERSITY_WEIGHT +
                recommendation.confidenceScore * (1.0 - ENGAGEMENT_WEIGHT - DIVERSITY_WEIGHT)
    }
    
    private fun inferCategoryFromVideo(video: Video): String {
        return inferCategoryFromTitle(video.title)
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
    
    private fun getDefaultProfile(): UserProfile {
        return UserProfile(
            preferredCategories = mapOf("Entertainment" to 0.5),
            preferredChannels = emptyMap(),
            preferredDurations = DurationPreference(0.5, 0.5, 0.5),
            viewingTimePatterns = (0..23).associateWith { 0.5 },
            engagementPatterns = EngagementPattern(0.5, 0.5, 0.5, 0.0),
            diversityScore = 1.0,
            lastUpdated = System.currentTimeMillis()
        )
    }
}
