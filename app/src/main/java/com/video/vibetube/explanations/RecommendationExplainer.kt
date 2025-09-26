package com.video.vibetube.explanations

import android.content.Context
import com.video.vibetube.models.Video
import com.video.vibetube.ml.PredictiveRecommendationEngine
import com.video.vibetube.discovery.ContextualDiscoveryManager
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 2: Recommendation Explainer for VibeTube
 * 
 * Features:
 * - User-friendly explanation generation for recommendations
 * - Transparent AI reasoning with confidence levels
 * - Actionable insights for users to understand and control recommendations
 * - Privacy-focused explanations using only local data
 */
class RecommendationExplainer(
    private val context: Context,
    private val userDataManager: UserDataManager
) {
    
    data class RecommendationExplanation(
        val video: Video,
        val primaryReason: String,
        val secondaryReasons: List<String>,
        val confidenceLevel: ConfidenceLevel,
        val confidencePercentage: Int, // 0-100
        val dataUsed: List<DataSource>,
        val userActions: List<UserAction>,
        val improvementTips: List<String>
    )
    
    enum class ConfidenceLevel {
        HIGH, MEDIUM, LOW
    }
    
    data class DataSource(
        val type: String, // "watch_history", "favorites", "time_patterns", etc.
        val description: String,
        val influence: Double // 0.0 to 1.0
    )
    
    data class UserAction(
        val action: String, // "like", "dislike", "not_interested", "more_like_this"
        val description: String,
        val impact: String // What this action will do
    )
    
    data class ExplanationContext(
        val showTechnicalDetails: Boolean = false,
        val showDataSources: Boolean = true,
        val showConfidence: Boolean = true,
        val language: String = "en"
    )
    
    /**
     * Generate comprehensive explanation for a recommendation
     */
    suspend fun explainRecommendation(
        video: Video,
        predictionData: PredictiveRecommendationEngine.PredictiveRecommendation,
        context: ExplanationContext = ExplanationContext()
    ): RecommendationExplanation {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext getPrivacyExplanation(video)
            }
            
            val primaryReason = generatePrimaryReason(predictionData)
            val secondaryReasons = generateSecondaryReasons(predictionData)
            val confidenceLevel = determineConfidenceLevel(predictionData.confidenceScore)
            val confidencePercentage = (predictionData.confidenceScore * 100).toInt()
            val dataSources = identifyDataSources(predictionData)
            val userActions = generateUserActions(video)
            val improvementTips = generateImprovementTips(predictionData)
            
            RecommendationExplanation(
                video = video,
                primaryReason = primaryReason,
                secondaryReasons = secondaryReasons,
                confidenceLevel = confidenceLevel,
                confidencePercentage = confidencePercentage,
                dataUsed = dataSources,
                userActions = userActions,
                improvementTips = improvementTips
            )
        }
    }
    
    /**
     * Generate explanation for contextual recommendations
     */
    suspend fun explainContextualRecommendation(
        video: Video,
        contextualRec: ContextualDiscoveryManager.ContextualRecommendation,
        context: ExplanationContext = ExplanationContext()
    ): RecommendationExplanation {
        return withContext(Dispatchers.IO) {
            val primaryReason = "Recommended based on your current situation and available time"
            val secondaryReasons = contextualRec.reasons
            val confidenceLevel = determineConfidenceLevel(contextualRec.contextScore)
            val confidencePercentage = (contextualRec.contextScore * 100).toInt()
            
            val dataSources = listOf(
                DataSource("current_time", "Current time of day", 0.3),
                DataSource("available_time", "Your available viewing time", 0.4),
                DataSource("viewing_patterns", "Your typical viewing patterns", 0.3)
            )
            
            val userActions = generateUserActions(video)
            val improvementTips = contextualRec.adaptations.ifEmpty { 
                listOf("Perfect for your current situation!")
            }
            
            RecommendationExplanation(
                video = video,
                primaryReason = primaryReason,
                secondaryReasons = secondaryReasons,
                confidenceLevel = confidenceLevel,
                confidencePercentage = confidencePercentage,
                dataUsed = dataSources,
                userActions = userActions,
                improvementTips = improvementTips
            )
        }
    }
    
    /**
     * Generate simple explanation for basic recommendations
     */
    suspend fun explainSimpleRecommendation(
        video: Video,
        reasons: List<String>
    ): RecommendationExplanation {
        return withContext(Dispatchers.IO) {
            val primaryReason = reasons.firstOrNull() ?: "Recommended based on your interests"
            val secondaryReasons = reasons.drop(1)
            
            RecommendationExplanation(
                video = video,
                primaryReason = primaryReason,
                secondaryReasons = secondaryReasons,
                confidenceLevel = ConfidenceLevel.MEDIUM,
                confidencePercentage = 70,
                dataUsed = listOf(
                    DataSource("user_preferences", "Your viewing preferences", 1.0)
                ),
                userActions = generateUserActions(video),
                improvementTips = listOf("Watch more videos to improve recommendations")
            )
        }
    }
    
    private fun generatePrimaryReason(prediction: PredictiveRecommendationEngine.PredictiveRecommendation): String {
        val factors = prediction.factors
        val maxFactor = factors.maxByOrNull { it.value }
        
        return when (maxFactor?.key) {
            "category" -> {
                val category = inferCategoryFromTitle(prediction.video.title)
                "You enjoy $category content"
            }
            "channel" -> "From ${prediction.video.channelTitle}, a channel you like"
            "duration" -> "Perfect length for your viewing preferences"
            "time" -> "Great for watching at this time"
            "freshness" -> "Recently published content you might enjoy"
            "diversity" -> "Something different from your recent viewing"
            else -> "Matches your viewing patterns"
        }
    }
    
    private fun generateSecondaryReasons(prediction: PredictiveRecommendationEngine.PredictiveRecommendation): List<String> {
        val reasons = mutableListOf<String>()
        val factors = prediction.factors
        
        // Add reasons based on factor scores
        factors.forEach { (factor, score) ->
            if (score > 0.6) {
                when (factor) {
                    "category" -> {
                        val category = inferCategoryFromTitle(prediction.video.title)
                        if (!reasons.any { it.contains(category) }) {
                            reasons.add("Matches your interest in $category")
                        }
                    }
                    "channel" -> {
                        if (!reasons.any { it.contains("channel") }) {
                            reasons.add("From a creator you follow")
                        }
                    }
                    "duration" -> {
                        if (!reasons.any { it.contains("length") }) {
                            reasons.add("Good length for your typical viewing")
                        }
                    }
                    "freshness" -> {
                        if (!reasons.any { it.contains("recent") }) {
                            reasons.add("Recently uploaded")
                        }
                    }
                    "diversity" -> {
                        if (!reasons.any { it.contains("variety") }) {
                            reasons.add("Adds variety to your viewing")
                        }
                    }
                }
            }
        }
        
        // Add engagement-based reasons
        if (prediction.engagementScore > 0.8) {
            reasons.add("High likelihood you'll enjoy this")
        }
        
        if (prediction.diversityScore > 0.7) {
            reasons.add("Expands your content horizons")
        }
        
        return reasons.take(3) // Limit to 3 secondary reasons
    }
    
    private fun determineConfidenceLevel(score: Double): ConfidenceLevel {
        return when {
            score >= 0.8 -> ConfidenceLevel.HIGH
            score >= 0.5 -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    }
    
    private fun identifyDataSources(prediction: PredictiveRecommendationEngine.PredictiveRecommendation): List<DataSource> {
        val sources = mutableListOf<DataSource>()
        val factors = prediction.factors
        
        if (factors["category"]?.let { it > 0.3 } == true) {
            sources.add(DataSource(
                "watch_history",
                "Videos you've watched before",
                factors["category"] ?: 0.0
            ))
        }
        
        if (factors["channel"]?.let { it > 0.3 } == true) {
            sources.add(DataSource(
                "favorites",
                "Channels and videos you've liked",
                factors["channel"] ?: 0.0
            ))
        }
        
        if (factors["time"]?.let { it > 0.3 } == true) {
            sources.add(DataSource(
                "time_patterns",
                "Your typical viewing times",
                factors["time"] ?: 0.0
            ))
        }
        
        if (factors["duration"]?.let { it > 0.3 } == true) {
            sources.add(DataSource(
                "duration_preferences",
                "Your preferred video lengths",
                factors["duration"] ?: 0.0
            ))
        }
        
        return sources.sortedByDescending { it.influence }
    }
    
    private fun generateUserActions(video: Video): List<UserAction> {
        return listOf(
            UserAction(
                "like",
                "👍 Like this recommendation",
                "We'll recommend more similar content"
            ),
            UserAction(
                "dislike",
                "👎 Not interested",
                "We'll recommend less content like this"
            ),
            UserAction(
                "not_interested",
                "🚫 Not interested in this topic",
                "We'll avoid this topic in future recommendations"
            ),
            UserAction(
                "more_like_this",
                "➕ More like this",
                "We'll prioritize similar content and channels"
            ),
            UserAction(
                "save_for_later",
                "🔖 Save for later",
                "Add to your Watch Later playlist"
            )
        )
    }
    
    private fun generateImprovementTips(prediction: PredictiveRecommendationEngine.PredictiveRecommendation): List<String> {
        val tips = mutableListOf<String>()
        
        if (prediction.confidenceScore < 0.5) {
            tips.add("Watch more videos to help us understand your preferences better")
        }
        
        if (prediction.diversityScore < 0.3) {
            tips.add("Try exploring new categories to discover more content you might enjoy")
        }
        
        val factors = prediction.factors
        if (factors["category"]?.let { it < 0.3 } == true) {
            tips.add("Like or favorite videos to help us learn your content preferences")
        }
        
        if (factors["channel"]?.let { it < 0.3 } == true) {
            tips.add("Follow channels you enjoy to get more recommendations from them")
        }
        
        if (tips.isEmpty()) {
            tips.add("Your recommendations are well-tuned! Keep watching to maintain quality.")
        }
        
        return tips.take(2) // Limit to 2 tips
    }
    
    private fun getPrivacyExplanation(video: Video): RecommendationExplanation {
        return RecommendationExplanation(
            video = video,
            primaryReason = "Recommended content",
            secondaryReasons = listOf("Enable data collection to see personalized explanations"),
            confidenceLevel = ConfidenceLevel.LOW,
            confidencePercentage = 0,
            dataUsed = emptyList(),
            userActions = listOf(
                UserAction(
                    "enable_data",
                    "Enable personalized recommendations",
                    "Allow VibeTube to analyze your viewing patterns locally"
                )
            ),
            improvementTips = listOf("Enable data collection in settings for better recommendations")
        )
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
    
    /**
     * Generate explanation summary for UI display
     */
    fun generateExplanationSummary(explanation: RecommendationExplanation): String {
        val confidence = when (explanation.confidenceLevel) {
            ConfidenceLevel.HIGH -> "highly confident"
            ConfidenceLevel.MEDIUM -> "confident"
            ConfidenceLevel.LOW -> "somewhat confident"
        }
        
        return "We're $confidence (${explanation.confidencePercentage}%) that you'll enjoy this because ${explanation.primaryReason.lowercase()}."
    }
    
    /**
     * Generate detailed explanation for settings/help
     */
    fun generateDetailedExplanation(explanation: RecommendationExplanation): String {
        val sb = StringBuilder()
        
        sb.appendLine("🎯 Why this was recommended:")
        sb.appendLine("• ${explanation.primaryReason}")
        
        if (explanation.secondaryReasons.isNotEmpty()) {
            explanation.secondaryReasons.forEach { reason ->
                sb.appendLine("• $reason")
            }
        }
        
        sb.appendLine()
        sb.appendLine("📊 Confidence: ${explanation.confidencePercentage}% (${explanation.confidenceLevel.name.lowercase()})")
        
        if (explanation.dataUsed.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📈 Based on:")
            explanation.dataUsed.forEach { source ->
                val percentage = (source.influence * 100).toInt()
                sb.appendLine("• ${source.description} ($percentage%)")
            }
        }
        
        if (explanation.improvementTips.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("💡 Tips:")
            explanation.improvementTips.forEach { tip ->
                sb.appendLine("• $tip")
            }
        }
        
        return sb.toString()
    }
}
