package com.video.vibetube.quality

import android.content.Context
import com.video.vibetube.models.Video
import com.video.vibetube.models.WatchHistoryItem
import com.video.vibetube.models.FavoriteItem
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 3: Content Quality Analyzer for VibeTube
 * 
 * Features:
 * - Personal content quality scoring based on user engagement
 * - Quality trend analysis over time
 * - Content recommendation filtering by quality thresholds
 * - Educational vs entertainment quality assessment
 * - Creator quality consistency tracking
 */
class ContentQualityAnalyzer(
    private val context: Context,
    private val userDataManager: UserDataManager
) {
    
    data class QualityScore(
        val overall: Double, // 0.0 to 1.0
        val engagement: Double,
        val educational: Double,
        val entertainment: Double,
        val production: Double,
        val relevance: Double,
        val confidence: Double // How confident we are in this score
    )
    
    data class QualityInsights(
        val averageQuality: Double,
        val qualityTrend: QualityTrend,
        val topQualityChannels: List<ChannelQuality>,
        val qualityByCategory: Map<String, Double>,
        val recommendations: List<QualityRecommendation>
    )
    
    enum class QualityTrend {
        IMPROVING, STABLE, DECLINING
    }
    
    data class ChannelQuality(
        val channelId: String,
        val channelTitle: String,
        val averageQuality: Double,
        val consistency: Double, // How consistent the quality is
        val videosAnalyzed: Int
    )
    
    data class QualityRecommendation(
        val type: String,
        val title: String,
        val description: String,
        val impact: String
    )
    
    /**
     * Analyze content quality for a specific video
     */
    suspend fun analyzeVideoQuality(video: Video): QualityScore {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext getDefaultQualityScore()
            }
            
            val watchHistory = userDataManager.getWatchHistory()
            val userEngagement = getUserEngagementWithVideo(video.videoId, watchHistory)
            
            val engagementScore = calculateEngagementScore(userEngagement)
            val educationalScore = calculateEducationalScore(video)
            val entertainmentScore = calculateEntertainmentScore(video)
            val productionScore = calculateProductionScore(video)
            val relevanceScore = calculateRelevanceScore(video, watchHistory)
            
            val overallScore = (engagementScore * 0.3 + 
                              educationalScore * 0.2 + 
                              entertainmentScore * 0.2 + 
                              productionScore * 0.15 + 
                              relevanceScore * 0.15)
            
            val confidence = calculateConfidence(userEngagement, watchHistory.size)
            
            QualityScore(
                overall = overallScore,
                engagement = engagementScore,
                educational = educationalScore,
                entertainment = entertainmentScore,
                production = productionScore,
                relevance = relevanceScore,
                confidence = confidence
            )
        }
    }
    
    /**
     * Get comprehensive quality insights
     */
    suspend fun getQualityInsights(): QualityInsights {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext getEmptyInsights()
            }
            
            val watchHistory = userDataManager.getWatchHistory()
            val favorites = userDataManager.getFavorites()
            
            if (watchHistory.isEmpty()) {
                return@withContext getEmptyInsights()
            }
            
            val averageQuality = calculateAverageQuality(watchHistory)
            val qualityTrend = analyzeQualityTrend(watchHistory)
            val topChannels = analyzeChannelQuality(watchHistory)
            val categoryQuality = analyzeQualityByCategory(watchHistory)
            val recommendations = generateQualityRecommendations(watchHistory, averageQuality)
            
            QualityInsights(
                averageQuality = averageQuality,
                qualityTrend = qualityTrend,
                topQualityChannels = topChannels,
                qualityByCategory = categoryQuality,
                recommendations = recommendations
            )
        }
    }
    
    /**
     * Filter recommendations by quality threshold
     */
    suspend fun filterByQuality(videos: List<Video>, minQuality: Double): List<Pair<Video, QualityScore>> {
        return withContext(Dispatchers.IO) {
            videos.mapNotNull { video ->
                val quality = analyzeVideoQuality(video)
                if (quality.overall >= minQuality) {
                    Pair(video, quality)
                } else null
            }.sortedByDescending { it.second.overall }
        }
    }
    
    private fun getUserEngagementWithVideo(videoId: String, watchHistory: List<WatchHistoryItem>): WatchHistoryItem? {
        return watchHistory.find { it.videoId == videoId }
    }
    
    private fun calculateEngagementScore(engagement: WatchHistoryItem?): Double {
        if (engagement == null) return 0.5 // Default neutral score
        
        val completionScore = engagement.watchProgress.toDouble()
        val durationScore = calculateDurationEngagementScore(engagement.duration, engagement.watchDuration)
        
        return (completionScore * 0.7 + durationScore * 0.3).coerceIn(0.0, 1.0)
    }
    
    private fun calculateDurationEngagementScore(totalDuration: String, watchedDuration: Long): Double {
        val totalMinutes = parseDurationToMinutes(totalDuration)
        val watchedMinutes = watchedDuration / (60 * 1000.0)
        
        return if (totalMinutes > 0) {
            (watchedMinutes / totalMinutes).coerceIn(0.0, 1.0)
        } else 0.5
    }
    
    private fun calculateEducationalScore(video: Video): Double {
        val title = video.title.lowercase()
        val description = video.description.lowercase()
        
        val educationalKeywords = listOf(
            "tutorial", "how to", "learn", "course", "lesson", "guide", "explained",
            "basics", "introduction", "beginner", "advanced", "masterclass", "training",
            "education", "teach", "instruction", "demo", "walkthrough", "step by step",
            "tips", "tricks", "skills", "knowledge", "study", "academic"
        )
        
        val keywordMatches = educationalKeywords.count { keyword ->
            title.contains(keyword) || description.contains(keyword)
        }
        
        val durationScore = calculateEducationalDurationScore(video.duration)
        val titleScore = (keywordMatches.toDouble() / educationalKeywords.size.toDouble()).coerceAtMost(1.0)
        
        return (titleScore * 0.7 + durationScore * 0.3).coerceIn(0.0, 1.0)
    }
    
    private fun calculateEducationalDurationScore(duration: String): Double {
        val minutes = parseDurationToMinutes(duration)
        return when {
            minutes in 5.0..60.0 -> 1.0 // Optimal educational length
            minutes in 2.0..5.0 -> 0.7 // Short but can be educational
            minutes in 60.0..120.0 -> 0.8 // Long form educational
            else -> 0.4 // Too short or too long
        }
    }
    
    private fun calculateEntertainmentScore(video: Video): Double {
        val title = video.title.lowercase()
        val description = video.description.lowercase()
        
        val entertainmentKeywords = listOf(
            "funny", "comedy", "hilarious", "entertainment", "fun", "amazing",
            "incredible", "awesome", "epic", "viral", "trending", "popular",
            "reaction", "review", "unboxing", "vlog", "challenge", "prank"
        )
        
        val keywordMatches = entertainmentKeywords.count { keyword ->
            title.contains(keyword) || description.contains(keyword)
        }
        
        val titleScore = (keywordMatches.toDouble() / entertainmentKeywords.size.toDouble()).coerceAtMost(1.0)
        val durationScore = calculateEntertainmentDurationScore(video.duration)
        
        return (titleScore * 0.6 + durationScore * 0.4).coerceIn(0.0, 1.0)
    }
    
    private fun calculateEntertainmentDurationScore(duration: String): Double {
        val minutes = parseDurationToMinutes(duration)
        return when {
            minutes in 3.0..20.0 -> 1.0 // Optimal entertainment length
            minutes in 1.0..3.0 -> 0.6 // Very short
            minutes in 20.0..45.0 -> 0.8 // Longer entertainment
            else -> 0.4 // Too short or too long
        }
    }
    
    private fun calculateProductionScore(video: Video): Double {
        // Infer production quality from title and description patterns
        val title = video.title
        val description = video.description
        
        var score = 0.5 // Base score
        
        // Title quality indicators
        if (title.length in 20..80) score += 0.1 // Good title length
        if (title.count { it.isUpperCase() } < title.length * 0.5) score += 0.1 // Not all caps
        if (!title.contains("!!!") && !title.contains("???")) score += 0.1 // No excessive punctuation
        
        // Description quality indicators
        if (description.length > 100) score += 0.1 // Has substantial description
        if (description.contains("http")) score += 0.1 // Has links (often indicates effort)
        
        // Channel quality indicators (would need more data in real implementation)
        if (video.channelTitle.isNotEmpty()) score += 0.1
        
        return score.coerceIn(0.0, 1.0)
    }
    
    private fun calculateRelevanceScore(video: Video, watchHistory: List<WatchHistoryItem>): Double {
        if (watchHistory.isEmpty()) return 0.5
        
        val videoCategory = inferCategoryFromTitle(video.title)
        val userCategories = watchHistory.map { inferCategoryFromTitle(it.title) }
        val categoryFrequency = userCategories.count { it == videoCategory }.toDouble() / userCategories.size
        
        val channelFrequency = watchHistory.count { it.channelId == video.channelId }.toDouble() / watchHistory.size
        
        return (categoryFrequency * 0.6 + channelFrequency * 0.4).coerceIn(0.0, 1.0)
    }
    
    private fun calculateConfidence(engagement: WatchHistoryItem?, historySize: Int): Double {
        val engagementConfidence = if (engagement != null) 0.8 else 0.3
        val dataConfidence = (historySize.toDouble() / 50.0).coerceAtMost(1.0) // More data = more confidence
        
        return (engagementConfidence * 0.6 + dataConfidence * 0.4).coerceIn(0.0, 1.0)
    }
    
    private fun calculateAverageQuality(watchHistory: List<WatchHistoryItem>): Double {
        if (watchHistory.isEmpty()) return 0.5
        
        val qualityScores = watchHistory.map { item ->
            val video = Video(
                videoId = item.videoId,
                title = item.title,
                description = "",
                thumbnail = item.thumbnail,
                channelTitle = item.channelTitle,
                publishedAt = "",
                duration = item.duration,
                channelId = item.channelId
            )
            // Simplified quality calculation for performance
            item.watchProgress.toDouble() // Use engagement as proxy for quality
        }
        
        return qualityScores.average()
    }
    
    private fun analyzeQualityTrend(watchHistory: List<WatchHistoryItem>): QualityTrend {
        if (watchHistory.size < 10) return QualityTrend.STABLE
        
        val sortedHistory = watchHistory.sortedBy { it.watchedAt }
        val recentVideos = sortedHistory.takeLast(10)
        val olderVideos = sortedHistory.dropLast(10).takeLast(10)
        
        val recentQuality = recentVideos.map { it.watchProgress.toDouble() }.average()
        val olderQuality = olderVideos.map { it.watchProgress.toDouble() }.average()
        
        return when {
            recentQuality > olderQuality + 0.1 -> QualityTrend.IMPROVING
            recentQuality < olderQuality - 0.1 -> QualityTrend.DECLINING
            else -> QualityTrend.STABLE
        }
    }
    
    private fun analyzeChannelQuality(watchHistory: List<WatchHistoryItem>): List<ChannelQuality> {
        return watchHistory
            .groupBy { it.channelId }
            .filter { it.value.size >= 3 } // Only channels with 3+ videos
            .map { (channelId, videos) ->
                val averageQuality = videos.map { it.watchProgress.toDouble() }.average()
                val qualityVariance = calculateVariance(videos.map { it.watchProgress.toDouble() })
                val consistency = 1.0 - qualityVariance // Lower variance = higher consistency
                
                ChannelQuality(
                    channelId = channelId,
                    channelTitle = videos.first().channelTitle,
                    averageQuality = averageQuality,
                    consistency = consistency.coerceIn(0.0, 1.0),
                    videosAnalyzed = videos.size
                )
            }
            .sortedByDescending { it.averageQuality }
            .take(10)
    }
    
    private fun analyzeQualityByCategory(watchHistory: List<WatchHistoryItem>): Map<String, Double> {
        return watchHistory
            .groupBy { inferCategoryFromTitle(it.title) }
            .mapValues { (_, videos) ->
                videos.map { it.watchProgress.toDouble() }.average()
            }
    }
    
    private fun generateQualityRecommendations(
        watchHistory: List<WatchHistoryItem>,
        averageQuality: Double
    ): List<QualityRecommendation> {
        val recommendations = mutableListOf<QualityRecommendation>()
        
        if (averageQuality < 0.6) {
            recommendations.add(QualityRecommendation(
                type = "improve_selection",
                title = "Explore higher quality content",
                description = "Your average content engagement is below 60%. Try exploring different creators or categories.",
                impact = "Better content discovery and viewing satisfaction"
            ))
        }
        
        val categories = watchHistory.groupBy { inferCategoryFromTitle(it.title) }
        val lowQualityCategories = categories.filter { (_, videos) ->
            videos.map { it.watchProgress.toDouble() }.average() < 0.5
        }
        
        if (lowQualityCategories.isNotEmpty()) {
            recommendations.add(QualityRecommendation(
                type = "category_improvement",
                title = "Refine category preferences",
                description = "Some content categories show lower engagement. Consider exploring new creators in these areas.",
                impact = "More satisfying content in your preferred categories"
            ))
        }
        
        val inconsistentChannels = analyzeChannelQuality(watchHistory).filter { it.consistency < 0.6 }
        if (inconsistentChannels.isNotEmpty()) {
            recommendations.add(QualityRecommendation(
                type = "channel_curation",
                title = "Curate your channel subscriptions",
                description = "Some channels show inconsistent quality. Consider being more selective.",
                impact = "More consistent high-quality content"
            ))
        }
        
        return recommendations
    }
    
    private fun calculateVariance(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average()
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
    
    private fun getDefaultQualityScore(): QualityScore {
        return QualityScore(
            overall = 0.5,
            engagement = 0.5,
            educational = 0.5,
            entertainment = 0.5,
            production = 0.5,
            relevance = 0.5,
            confidence = 0.3
        )
    }
    
    private fun getEmptyInsights(): QualityInsights {
        return QualityInsights(
            averageQuality = 0.5,
            qualityTrend = QualityTrend.STABLE,
            topQualityChannels = emptyList(),
            qualityByCategory = emptyMap(),
            recommendations = listOf(
                QualityRecommendation(
                    type = "start_watching",
                    title = "Start building your quality profile",
                    description = "Watch more content to help us analyze quality patterns",
                    impact = "Personalized quality insights and recommendations"
                )
            )
        )
    }
}
