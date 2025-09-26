package com.video.vibetube.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.video.vibetube.models.Video
import com.video.vibetube.ml.PredictiveRecommendationEngine
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Phase 2: Contextual Discovery Manager for VibeTube
 * 
 * Features:
 * - Time-aware content suggestions based on user patterns
 * - Available time matching for optimal content selection
 * - Situation-based filtering (commute, break, evening)
 * - Mood and device context consideration
 * - Battery and network-aware recommendations
 */
class ContextualDiscoveryManager(
    private val context: Context,
    private val userDataManager: UserDataManager,
    private val predictiveEngine: PredictiveRecommendationEngine
) {
    
    companion object {
        private const val MORNING_START = 6
        private const val AFTERNOON_START = 12
        private const val EVENING_START = 18
        private const val NIGHT_START = 22
    }
    
    data class DiscoveryContext(
        val timeOfDay: TimeOfDay,
        val availableMinutes: Int,
        val situation: Situation,
        val deviceContext: DeviceContext,
        val userMood: UserMood = UserMood.NEUTRAL
    )
    
    enum class TimeOfDay {
        MORNING, AFTERNOON, EVENING, NIGHT
    }
    
    enum class Situation {
        COMMUTE, // Short videos, offline-friendly
        WORK_BREAK, // Quick, energizing content
        LEISURE_TIME, // Any content type
        BEDTIME, // Calm, relaxing content
        LEARNING_TIME, // Educational content
        EXERCISE, // Fitness or motivational content
        COOKING, // Cooking tutorials or background content
        UNKNOWN
    }
    
    enum class UserMood {
        FOCUSED, // Educational, tutorial content
        RELAXED, // Entertainment, music
        ENERGETIC, // Gaming, comedy, action
        CURIOUS, // Discovery, new topics
        NEUTRAL // Balanced mix
    }
    
    data class DeviceContext(
        val batteryLevel: Int, // 0-100
        val isCharging: Boolean,
        val networkType: NetworkType,
        val isHeadphonesConnected: Boolean,
        val screenSize: ScreenSize
    )
    
    enum class NetworkType {
        WIFI, MOBILE_HIGH_SPEED, MOBILE_LOW_SPEED, OFFLINE
    }
    
    enum class ScreenSize {
        SMALL, MEDIUM, LARGE
    }
    
    data class ContextualRecommendation(
        val video: Video,
        val contextScore: Double, // How well it fits current context
        val reasons: List<String>,
        val adaptations: List<String> // Suggested viewing adaptations
    )
    
    /**
     * Get contextually appropriate recommendations
     */
    suspend fun getContextualRecommendations(
        availableMinutes: Int,
        maxResults: Int = 15
    ): List<ContextualRecommendation> {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext emptyList()
            }
            
            val discoveryContext = buildCurrentContext(availableMinutes)
            val recommendationContext = PredictiveRecommendationEngine.RecommendationContext(
                timeOfDay = getCurrentHour(),
                availableTime = availableMinutes,
                recentCategories = getRecentCategories(),
                mood = mapMoodToString(discoveryContext.userMood)
            )
            
            // Get base predictions
            val predictions = predictiveEngine.generatePredictiveRecommendations(
                recommendationContext, maxResults * 2
            )
            
            // Apply contextual filtering and scoring
            val contextualRecommendations = predictions.map { prediction ->
                scoreContextualFit(prediction.video, discoveryContext, prediction.reasons)
            }
            
            // Sort by context score and return top results
            contextualRecommendations
                .sortedByDescending { it.contextScore }
                .take(maxResults)
        }
    }
    
    /**
     * Get situation-specific recommendations
     */
    suspend fun getSituationRecommendations(
        situation: Situation,
        availableMinutes: Int,
        maxResults: Int = 10
    ): List<ContextualRecommendation> {
        return withContext(Dispatchers.IO) {
            val context = buildCurrentContext(availableMinutes).copy(situation = situation)
            
            when (situation) {
                Situation.COMMUTE -> getCommuteRecommendations(context, maxResults)
                Situation.WORK_BREAK -> getWorkBreakRecommendations(context, maxResults)
                Situation.BEDTIME -> getBedtimeRecommendations(context, maxResults)
                Situation.LEARNING_TIME -> getLearningRecommendations(context, maxResults)
                Situation.EXERCISE -> getExerciseRecommendations(context, maxResults)
                Situation.COOKING -> getCookingRecommendations(context, maxResults)
                else -> getContextualRecommendations(availableMinutes, maxResults)
            }
        }
    }
    
    private fun buildCurrentContext(availableMinutes: Int): DiscoveryContext {
        val timeOfDay = getCurrentTimeOfDay()
        val situation = inferSituation(timeOfDay, availableMinutes)
        val deviceContext = getDeviceContext()
        val userMood = inferUserMood(timeOfDay, situation)
        
        return DiscoveryContext(
            timeOfDay = timeOfDay,
            availableMinutes = availableMinutes,
            situation = situation,
            deviceContext = deviceContext,
            userMood = userMood
        )
    }
    
    private fun getCurrentTimeOfDay(): TimeOfDay {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in MORNING_START until AFTERNOON_START -> TimeOfDay.MORNING
            in AFTERNOON_START until EVENING_START -> TimeOfDay.AFTERNOON
            in EVENING_START until NIGHT_START -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }
    
    private fun getCurrentHour(): Int {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }
    
    private fun inferSituation(timeOfDay: TimeOfDay, availableMinutes: Int): Situation {
        return when {
            timeOfDay == TimeOfDay.MORNING && availableMinutes <= 15 -> Situation.COMMUTE
            timeOfDay in listOf(TimeOfDay.AFTERNOON, TimeOfDay.EVENING) && availableMinutes <= 10 -> Situation.WORK_BREAK
            timeOfDay == TimeOfDay.NIGHT && availableMinutes <= 30 -> Situation.BEDTIME
            availableMinutes >= 60 -> Situation.LEISURE_TIME
            else -> Situation.UNKNOWN
        }
    }
    
    private fun getDeviceContext(): DeviceContext {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkType = getNetworkType(connectivityManager)
        
        return DeviceContext(
            batteryLevel = 75, // Would need battery manager in real implementation
            isCharging = false, // Would need battery manager
            networkType = networkType,
            isHeadphonesConnected = false, // Would need audio manager
            screenSize = ScreenSize.MEDIUM // Would need display metrics
        )
    }
    
    private fun getNetworkType(connectivityManager: ConnectivityManager): NetworkType {
        val network = connectivityManager.activeNetwork ?: return NetworkType.OFFLINE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.OFFLINE
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                    NetworkType.MOBILE_HIGH_SPEED
                } else {
                    NetworkType.MOBILE_LOW_SPEED
                }
            }
            else -> NetworkType.OFFLINE
        }
    }
    
    private fun inferUserMood(timeOfDay: TimeOfDay, situation: Situation): UserMood {
        return when {
            situation == Situation.LEARNING_TIME -> UserMood.FOCUSED
            situation == Situation.BEDTIME -> UserMood.RELAXED
            situation == Situation.EXERCISE -> UserMood.ENERGETIC
            timeOfDay == TimeOfDay.MORNING -> UserMood.ENERGETIC
            timeOfDay == TimeOfDay.EVENING -> UserMood.RELAXED
            else -> UserMood.NEUTRAL
        }
    }
    
    private suspend fun getRecentCategories(): List<String> {
        val recentHistory = userDataManager.getWatchHistory()
            .filter { System.currentTimeMillis() - it.watchedAt < 24 * 60 * 60 * 1000L } // Last 24 hours
            .takeLast(10)
        
        return recentHistory.map { inferCategoryFromTitle(it.title) }
    }
    
    private fun scoreContextualFit(
        video: Video,
        context: DiscoveryContext,
        baseReasons: List<String>
    ): ContextualRecommendation {
        var contextScore = 0.0
        val reasons = baseReasons.toMutableList()
        val adaptations = mutableListOf<String>()
        
        // Time appropriateness
        val timeScore = scoreTimeAppropriate(video, context.timeOfDay)
        contextScore += timeScore * 0.3
        if (timeScore > 0.7) {
            reasons.add("Perfect for ${context.timeOfDay.name.lowercase()} viewing")
        }
        
        // Duration fit
        val durationScore = scoreDurationFit(video, context.availableMinutes)
        contextScore += durationScore * 0.4
        if (durationScore > 0.8) {
            reasons.add("Fits perfectly in your available time")
        } else if (durationScore < 0.5) {
            adaptations.add("Consider watching in segments")
        }
        
        // Situation appropriateness
        val situationScore = scoreSituationFit(video, context.situation)
        contextScore += situationScore * 0.2
        if (situationScore > 0.7) {
            reasons.add("Great for ${context.situation.name.lowercase().replace('_', ' ')}")
        }
        
        // Device context
        val deviceScore = scoreDeviceContext(video, context.deviceContext)
        contextScore += deviceScore * 0.1
        if (deviceScore < 0.5) {
            when (context.deviceContext.networkType) {
                NetworkType.MOBILE_LOW_SPEED -> adaptations.add("Download for offline viewing")
                NetworkType.OFFLINE -> adaptations.add("Available offline")
                else -> {}
            }
        }
        
        return ContextualRecommendation(
            video = video,
            contextScore = contextScore.coerceIn(0.0, 1.0),
            reasons = reasons,
            adaptations = adaptations
        )
    }
    
    private fun scoreTimeAppropriate(video: Video, timeOfDay: TimeOfDay): Double {
        val category = inferCategoryFromTitle(video.title)
        
        return when (timeOfDay) {
            TimeOfDay.MORNING -> when (category) {
                "News", "Education", "Health & Fitness" -> 0.9
                "Music", "Technology" -> 0.7
                "Comedy", "Entertainment" -> 0.5
                else -> 0.6
            }
            TimeOfDay.AFTERNOON -> when (category) {
                "Education", "Technology", "DIY & Crafts" -> 0.8
                "News", "Food" -> 0.7
                "Entertainment", "Gaming" -> 0.6
                else -> 0.7
            }
            TimeOfDay.EVENING -> when (category) {
                "Entertainment", "Comedy", "Music" -> 0.9
                "Gaming", "Travel" -> 0.8
                "Education", "News" -> 0.6
                else -> 0.7
            }
            TimeOfDay.NIGHT -> when (category) {
                "Music", "Comedy" -> 0.8
                "Education" -> 0.7 // Light educational content
                "News", "Gaming" -> 0.4 // Too stimulating
                else -> 0.6
            }
        }
    }
    
    private fun scoreDurationFit(video: Video, availableMinutes: Int): Double {
        val videoDuration = parseDurationToMinutes(video.duration)
        
        return when {
            videoDuration <= availableMinutes * 0.8 -> 1.0 // Perfect fit with buffer
            videoDuration <= availableMinutes -> 0.8 // Exact fit
            videoDuration <= availableMinutes * 1.2 -> 0.6 // Slightly over
            videoDuration <= availableMinutes * 1.5 -> 0.4 // Can watch partially
            else -> 0.2 // Too long
        }
    }
    
    private fun scoreSituationFit(video: Video, situation: Situation): Double {
        val category = inferCategoryFromTitle(video.title)
        val duration = parseDurationToMinutes(video.duration)
        
        return when (situation) {
            Situation.COMMUTE -> when {
                duration <= 15 && category in listOf("News", "Comedy", "Music") -> 0.9
                duration <= 10 -> 0.7
                else -> 0.3
            }
            Situation.WORK_BREAK -> when {
                duration <= 10 && category in listOf("Comedy", "Music", "Entertainment") -> 0.9
                duration <= 5 -> 0.8
                else -> 0.4
            }
            Situation.BEDTIME -> when {
                category in listOf("Music", "Education") && duration <= 30 -> 0.8
                category == "Comedy" && duration <= 20 -> 0.6
                else -> 0.4
            }
            Situation.LEARNING_TIME -> when {
                category in listOf("Education", "Technology", "DIY & Crafts") -> 0.9
                else -> 0.3
            }
            Situation.EXERCISE -> when {
                category in listOf("Health & Fitness", "Music") -> 0.9
                else -> 0.2
            }
            Situation.COOKING -> when {
                category == "Food" -> 0.9
                category == "Music" && duration >= 20 -> 0.7
                else -> 0.3
            }
            else -> 0.7
        }
    }
    
    private fun scoreDeviceContext(video: Video, deviceContext: DeviceContext): Double {
        var score = 1.0
        
        // Battery consideration
        if (deviceContext.batteryLevel < 20 && !deviceContext.isCharging) {
            score *= 0.7 // Prefer shorter content
        }
        
        // Network consideration
        when (deviceContext.networkType) {
            NetworkType.MOBILE_LOW_SPEED -> score *= 0.6
            NetworkType.OFFLINE -> score *= 0.3
            else -> {} // No penalty for good connection
        }
        
        return score
    }
    
    private suspend fun getCommuteRecommendations(
        context: DiscoveryContext,
        maxResults: Int
    ): List<ContextualRecommendation> {
        // Focus on short, engaging content suitable for commuting
        return getContextualRecommendations(15, maxResults)
            .filter { it.video.duration.let { dur -> parseDurationToMinutes(dur) <= 15 } }
    }
    
    private suspend fun getWorkBreakRecommendations(
        context: DiscoveryContext,
        maxResults: Int
    ): List<ContextualRecommendation> {
        // Focus on quick, energizing content
        return getContextualRecommendations(10, maxResults)
            .filter { 
                val category = inferCategoryFromTitle(it.video.title)
                category in listOf("Comedy", "Music", "Entertainment")
            }
    }
    
    private suspend fun getBedtimeRecommendations(
        context: DiscoveryContext,
        maxResults: Int
    ): List<ContextualRecommendation> {
        // Focus on calm, relaxing content
        return getContextualRecommendations(30, maxResults)
            .filter { 
                val category = inferCategoryFromTitle(it.video.title)
                category in listOf("Music", "Education") // Calm categories
            }
    }
    
    private suspend fun getLearningRecommendations(
        context: DiscoveryContext,
        maxResults: Int
    ): List<ContextualRecommendation> {
        // Focus on educational content
        return getContextualRecommendations(context.availableMinutes, maxResults)
            .filter { 
                val category = inferCategoryFromTitle(it.video.title)
                category in listOf("Education", "Technology", "DIY & Crafts")
            }
    }
    
    private suspend fun getExerciseRecommendations(
        context: DiscoveryContext,
        maxResults: Int
    ): List<ContextualRecommendation> {
        // Focus on fitness and motivational content
        return getContextualRecommendations(context.availableMinutes, maxResults)
            .filter { 
                val category = inferCategoryFromTitle(it.video.title)
                category in listOf("Health & Fitness", "Music")
            }
    }
    
    private suspend fun getCookingRecommendations(
        context: DiscoveryContext,
        maxResults: Int
    ): List<ContextualRecommendation> {
        // Focus on cooking tutorials and background content
        return getContextualRecommendations(context.availableMinutes, maxResults)
            .filter { 
                val category = inferCategoryFromTitle(it.video.title)
                category in listOf("Food", "Music")
            }
    }
    
    private fun mapMoodToString(mood: UserMood): String {
        return when (mood) {
            UserMood.FOCUSED -> "focused"
            UserMood.RELAXED -> "relaxed"
            UserMood.ENERGETIC -> "energetic"
            UserMood.CURIOUS -> "discovery"
            UserMood.NEUTRAL -> "neutral"
        }
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
}
