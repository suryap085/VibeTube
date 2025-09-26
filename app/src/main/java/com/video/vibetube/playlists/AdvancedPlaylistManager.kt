package com.video.vibetube.playlists

import android.content.Context
import com.video.vibetube.models.UserPlaylist
import com.video.vibetube.models.Video
import com.video.vibetube.models.WatchHistoryItem
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.ml.SmartContentOrganizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Phase 3: Advanced Playlist Manager for VibeTube
 * 
 * Features:
 * - Smart playlist generation based on content analysis
 * - Playlist templates for common use cases
 * - Automatic playlist organization and maintenance
 * - Collaborative playlist features (local sharing)
 * - Playlist analytics and optimization suggestions
 */
class AdvancedPlaylistManager(
    private val context: Context,
    private val userDataManager: UserDataManager,
    private val contentOrganizer: SmartContentOrganizer
) {
    
    companion object {
        private const val MAX_SMART_PLAYLIST_SIZE = 50
        private const val MIN_PLAYLIST_SIZE_FOR_ANALYSIS = 5
    }
    
    data class SmartPlaylist(
        val id: String,
        val name: String,
        val description: String,
        val type: PlaylistType,
        val videos: List<Video>,
        val autoUpdate: Boolean,
        val criteria: PlaylistCriteria,
        val createdAt: Long,
        val lastUpdated: Long
    )
    
    enum class PlaylistType {
        SMART_CATEGORY, // Based on content category
        SMART_MOOD, // Based on viewing mood/time
        SMART_LEARNING, // Educational content progression
        SMART_DURATION, // Based on available time
        TEMPLATE, // From predefined template
        MANUAL // User-created manual playlist
    }
    
    data class PlaylistCriteria(
        val categories: List<String> = emptyList(),
        val channels: List<String> = emptyList(),
        val durationRange: Pair<Int, Int>? = null, // min, max minutes
        val qualityThreshold: Double = 0.0,
        val maxAge: Long? = null, // milliseconds
        val excludeWatched: Boolean = false
    )
    
    data class PlaylistTemplate(
        val id: String,
        val name: String,
        val description: String,
        val icon: String,
        val criteria: PlaylistCriteria,
        val suggestedSize: Int
    )
    
    data class PlaylistAnalytics(
        val playlistId: String,
        val totalVideos: Int,
        val totalDuration: Long,
        val averageQuality: Double,
        val completionRate: Double,
        val categoryDistribution: Map<String, Int>,
        val recommendations: List<PlaylistRecommendation>
    )
    
    data class PlaylistRecommendation(
        val type: String,
        val title: String,
        val description: String,
        val action: String
    )
    
    /**
     * Generate smart playlists based on user content
     */
    suspend fun generateSmartPlaylists(): List<SmartPlaylist> {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext emptyList()
            }
            
            val smartPlaylists = mutableListOf<SmartPlaylist>()
            
            // Generate category-based playlists
            smartPlaylists.addAll(generateCategoryPlaylists())
            
            // Generate mood-based playlists
            smartPlaylists.addAll(generateMoodPlaylists())
            
            // Generate learning playlists
            smartPlaylists.addAll(generateLearningPlaylists())
            
            // Generate duration-based playlists
            smartPlaylists.addAll(generateDurationPlaylists())
            
            smartPlaylists.take(8) // Limit to 8 smart playlists
        }
    }
    
    /**
     * Create playlist from template
     */
    suspend fun createFromTemplate(template: PlaylistTemplate): SmartPlaylist? {
        return withContext(Dispatchers.IO) {
            val videos = findVideosMatchingCriteria(template.criteria, template.suggestedSize)
            
            if (videos.isEmpty()) return@withContext null
            
            SmartPlaylist(
                id = UUID.randomUUID().toString(),
                name = template.name,
                description = template.description,
                type = PlaylistType.TEMPLATE,
                videos = videos,
                autoUpdate = true,
                criteria = template.criteria,
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Get available playlist templates
     */
    fun getPlaylistTemplates(): List<PlaylistTemplate> {
        return listOf(
            PlaylistTemplate(
                id = "quick_watch",
                name = "Quick Watch",
                description = "Short videos for when you have limited time",
                icon = "⚡",
                criteria = PlaylistCriteria(durationRange = Pair(1, 10)),
                suggestedSize = 20
            ),
            PlaylistTemplate(
                id = "deep_dive",
                name = "Deep Dive",
                description = "Longer, in-depth content for focused viewing",
                icon = "🔍",
                criteria = PlaylistCriteria(durationRange = Pair(20, 120)),
                suggestedSize = 15
            ),
            PlaylistTemplate(
                id = "learning_path",
                name = "Learning Path",
                description = "Educational content organized for skill building",
                icon = "📚",
                criteria = PlaylistCriteria(categories = listOf("Education", "Technology", "DIY & Crafts")),
                suggestedSize = 25
            ),
            PlaylistTemplate(
                id = "entertainment_mix",
                name = "Entertainment Mix",
                description = "Variety of entertaining content",
                icon = "🎭",
                criteria = PlaylistCriteria(categories = listOf("Comedy", "Entertainment", "Music")),
                suggestedSize = 30
            ),
            PlaylistTemplate(
                id = "high_quality",
                name = "High Quality",
                description = "Only your highest-rated content",
                icon = "⭐",
                criteria = PlaylistCriteria(qualityThreshold = 0.8),
                suggestedSize = 20
            ),
            PlaylistTemplate(
                id = "recent_discoveries",
                name = "Recent Discoveries",
                description = "Recently published content from your interests",
                icon = "🆕",
                criteria = PlaylistCriteria(maxAge = 7 * 24 * 60 * 60 * 1000L), // 7 days
                suggestedSize = 25
            )
        )
    }
    
    /**
     * Analyze playlist performance and provide recommendations
     */
    suspend fun analyzePlaylist(playlistId: String): PlaylistAnalytics? {
        return withContext(Dispatchers.IO) {
            val playlist = userDataManager.getPlaylists().find { it.id == playlistId }
                ?: return@withContext null
            
            val watchHistory = userDataManager.getWatchHistory()
            val playlistVideos = playlist.videos
            
            val totalDuration = playlistVideos.sumOf { parseDurationToMinutes(it.duration) * 60 * 1000 }.toLong()
            val watchedVideos = playlistVideos.filter { video ->
                watchHistory.any { it.videoId == video.videoId }
            }
            
            val completionRate = if (playlistVideos.isNotEmpty()) {
                watchedVideos.size.toDouble() / playlistVideos.size.toDouble()
            } else 0.0
            
            val categoryDistribution = playlistVideos
                .groupBy { inferCategoryFromTitle(it.title) }
                .mapValues { it.value.size }
            
            val averageQuality = calculatePlaylistQuality(playlistVideos, watchHistory)
            val recommendations = generatePlaylistRecommendations(playlist, watchHistory)
            
            PlaylistAnalytics(
                playlistId = playlistId,
                totalVideos = playlistVideos.size,
                totalDuration = totalDuration,
                averageQuality = averageQuality,
                completionRate = completionRate,
                categoryDistribution = categoryDistribution,
                recommendations = recommendations
            )
        }
    }
    
    /**
     * Optimize existing playlist
     */
    suspend fun optimizePlaylist(playlistId: String): UserPlaylist? {
        return withContext(Dispatchers.IO) {
            val playlist = userDataManager.getPlaylists().find { it.id == playlistId }
                ?: return@withContext null

            val watchHistory = userDataManager.getWatchHistory()
            val optimizedVideos = optimizeVideoOrder(playlist.videos, watchHistory)

            playlist.copy(videos = optimizedVideos.toMutableList())
        }
    }
    
    private suspend fun generateCategoryPlaylists(): List<SmartPlaylist> {
        val watchHistory = userDataManager.getWatchHistory()
        val favorites = userDataManager.getFavorites()
        
        val categories = (watchHistory.map { inferCategoryFromTitle(it.title) } +
                         favorites.map { it.category.ifEmpty { inferCategoryFromTitle(it.title) } })
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= 3 } // Only categories with 3+ videos
            .keys
        
        return categories.map { category ->
            val criteria = PlaylistCriteria(categories = listOf(category))
            val videos = findVideosMatchingCriteria(criteria, 25)
            
            SmartPlaylist(
                id = "smart_${category.lowercase().replace(" ", "_")}",
                name = "Smart $category",
                description = "Your $category content organized intelligently",
                type = PlaylistType.SMART_CATEGORY,
                videos = videos,
                autoUpdate = true,
                criteria = criteria,
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            )
        }.filter { it.videos.isNotEmpty() }
    }
    
    private suspend fun generateMoodPlaylists(): List<SmartPlaylist> {
        val playlists = mutableListOf<SmartPlaylist>()
        
        // Morning Energy playlist
        val morningCriteria = PlaylistCriteria(
            categories = listOf("Music", "News", "Health & Fitness"),
            durationRange = Pair(3, 15)
        )
        val morningVideos = findVideosMatchingCriteria(morningCriteria, 20)
        if (morningVideos.isNotEmpty()) {
            playlists.add(SmartPlaylist(
                id = "smart_morning_energy",
                name = "Morning Energy",
                description = "Perfect content to start your day",
                type = PlaylistType.SMART_MOOD,
                videos = morningVideos,
                autoUpdate = true,
                criteria = morningCriteria,
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            ))
        }
        
        // Evening Relaxation playlist
        val eveningCriteria = PlaylistCriteria(
            categories = listOf("Music", "Comedy", "Entertainment"),
            durationRange = Pair(5, 30)
        )
        val eveningVideos = findVideosMatchingCriteria(eveningCriteria, 20)
        if (eveningVideos.isNotEmpty()) {
            playlists.add(SmartPlaylist(
                id = "smart_evening_relax",
                name = "Evening Relaxation",
                description = "Unwind with these calming videos",
                type = PlaylistType.SMART_MOOD,
                videos = eveningVideos,
                autoUpdate = true,
                criteria = eveningCriteria,
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            ))
        }
        
        return playlists
    }
    
    private suspend fun generateLearningPlaylists(): List<SmartPlaylist> {
        val learningCriteria = PlaylistCriteria(
            categories = listOf("Education", "Technology", "DIY & Crafts"),
            qualityThreshold = 0.6
        )
        val learningVideos = findVideosMatchingCriteria(learningCriteria, 30)
        
        return if (learningVideos.isNotEmpty()) {
            listOf(SmartPlaylist(
                id = "smart_learning_journey",
                name = "Learning Journey",
                description = "Educational content curated for skill development",
                type = PlaylistType.SMART_LEARNING,
                videos = learningVideos,
                autoUpdate = true,
                criteria = learningCriteria,
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            ))
        } else emptyList()
    }
    
    private suspend fun generateDurationPlaylists(): List<SmartPlaylist> {
        val playlists = mutableListOf<SmartPlaylist>()
        
        // Quick Bites (under 5 minutes)
        val quickCriteria = PlaylistCriteria(durationRange = Pair(1, 5))
        val quickVideos = findVideosMatchingCriteria(quickCriteria, 25)
        if (quickVideos.isNotEmpty()) {
            playlists.add(SmartPlaylist(
                id = "smart_quick_bites",
                name = "Quick Bites",
                description = "Short videos for quick viewing",
                type = PlaylistType.SMART_DURATION,
                videos = quickVideos,
                autoUpdate = true,
                criteria = quickCriteria,
                createdAt = System.currentTimeMillis(),
                lastUpdated = System.currentTimeMillis()
            ))
        }
        
        return playlists
    }
    
    private suspend fun findVideosMatchingCriteria(criteria: PlaylistCriteria, maxSize: Int): List<Video> {
        val watchHistory = userDataManager.getWatchHistory()
        val favorites = userDataManager.getFavorites()
        
        // Combine videos from watch history and favorites
        val allVideos = (watchHistory.map { item ->
            Video(
                videoId = item.videoId,
                title = item.title,
                description = "",
                thumbnail = item.thumbnail,
                channelTitle = item.channelTitle,
                publishedAt = "",
                duration = item.duration,
                channelId = item.channelId
            )
        } + favorites.map { favorite ->
            Video(
                videoId = favorite.videoId,
                title = favorite.title,
                description = "",
                thumbnail = favorite.thumbnail,
                channelTitle = favorite.channelTitle,
                publishedAt = "",
                duration = favorite.duration,
                channelId = favorite.channelId
            )
        }).distinctBy { it.videoId }
        
        return allVideos.filter { video ->
            matchesCriteria(video, criteria, watchHistory)
        }.take(maxSize)
    }
    
    private fun matchesCriteria(video: Video, criteria: PlaylistCriteria, watchHistory: List<WatchHistoryItem>): Boolean {
        // Category filter
        if (criteria.categories.isNotEmpty()) {
            val videoCategory = inferCategoryFromTitle(video.title)
            if (videoCategory !in criteria.categories) return false
        }
        
        // Channel filter
        if (criteria.channels.isNotEmpty()) {
            if (video.channelId !in criteria.channels) return false
        }
        
        // Duration filter
        criteria.durationRange?.let { (min, max) ->
            val duration = parseDurationToMinutes(video.duration)
            if (duration < min || duration > max) return false
        }
        
        // Quality filter
        if (criteria.qualityThreshold > 0) {
            val engagement = watchHistory.find { it.videoId == video.videoId }?.watchProgress ?: 0.5f
            if (engagement < criteria.qualityThreshold) return false
        }
        
        // Exclude watched filter
        if (criteria.excludeWatched) {
            if (watchHistory.any { it.videoId == video.videoId }) return false
        }
        
        return true
    }
    
    private fun calculatePlaylistQuality(videos: List<Video>, watchHistory: List<WatchHistoryItem>): Double {
        if (videos.isEmpty()) return 0.0
        
        val qualityScores = videos.mapNotNull { video ->
            watchHistory.find { it.videoId == video.videoId }?.watchProgress?.toDouble()
        }
        
        return if (qualityScores.isNotEmpty()) {
            qualityScores.average()
        } else 0.5 // Default quality for unwatched videos
    }
    
    private fun generatePlaylistRecommendations(
        playlist: UserPlaylist,
        watchHistory: List<WatchHistoryItem>
    ): List<PlaylistRecommendation> {
        val recommendations = mutableListOf<PlaylistRecommendation>()
        
        if (playlist.videos.size > 50) {
            recommendations.add(PlaylistRecommendation(
                type = "size_optimization",
                title = "Consider splitting this playlist",
                description = "Large playlists can be overwhelming. Consider creating themed sub-playlists.",
                action = "Split playlist"
            ))
        }
        
        val watchedCount = playlist.videos.count { video ->
            watchHistory.any { it.videoId == video.videoId }
        }
        val completionRate = watchedCount.toDouble() / playlist.videos.size.toDouble()
        
        if (completionRate < 0.3) {
            recommendations.add(PlaylistRecommendation(
                type = "content_relevance",
                title = "Low engagement detected",
                description = "Many videos in this playlist haven't been watched. Consider reviewing the content.",
                action = "Review content"
            ))
        }
        
        val categories = playlist.videos.map { inferCategoryFromTitle(it.title) }.distinct()
        if (categories.size > 5) {
            recommendations.add(PlaylistRecommendation(
                type = "category_focus",
                title = "Consider focusing the theme",
                description = "This playlist covers many categories. A more focused theme might be more useful.",
                action = "Focus theme"
            ))
        }
        
        return recommendations
    }
    
    private fun optimizeVideoOrder(videos: List<Video>, watchHistory: List<WatchHistoryItem>): List<Video> {
        // Sort by engagement score (watched videos first, then by completion rate)
        return videos.sortedWith { a, b ->
            val aEngagement = watchHistory.find { it.videoId == a.videoId }?.watchProgress ?: 0f
            val bEngagement = watchHistory.find { it.videoId == b.videoId }?.watchProgress ?: 0f
            bEngagement.compareTo(aEngagement)
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
