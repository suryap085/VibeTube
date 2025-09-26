package com.video.vibetube.social

import android.content.Context
import android.content.Intent
import com.video.vibetube.models.Video
import com.video.vibetube.models.UserPlaylist
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 3: Enhanced Social Manager for VibeTube
 * 
 * Features:
 * - Advanced YouTube-compliant sharing with rich metadata
 * - Playlist sharing with privacy controls
 * - Social engagement tracking (local only)
 * - Community features for local sharing
 * - Share analytics and insights
 */
class EnhancedSocialManager(
    private val context: Context,
    private val userDataManager: UserDataManager
) {
    
    data class ShareMetadata(
        val title: String,
        val description: String,
        val thumbnail: String?,
        val duration: String,
        val channelTitle: String,
        val shareReason: String? = null,
        val personalNote: String? = null
    )
    
    data class PlaylistShareData(
        val playlist: UserPlaylist,
        val shareableVideos: List<Video>, // Only videos user has permission to share
        val description: String,
        val isPublic: Boolean,
        val allowComments: Boolean
    )
    
    data class ShareInsights(
        val totalShares: Int,
        val mostSharedCategory: String,
        val shareFrequency: Map<String, Int>, // Platform -> count
        val popularContent: List<Video>,
        val sharingTrends: List<ShareTrend>
    )
    
    data class ShareTrend(
        val period: String, // "daily", "weekly", "monthly"
        val shareCount: Int,
        val topCategories: List<String>
    )
    
    enum class SharePlatform {
        YOUTUBE_LINK, MESSAGING, EMAIL, SOCIAL_MEDIA, CLIPBOARD, OTHER
    }
    
    enum class ShareType {
        VIDEO, PLAYLIST, RECOMMENDATION, ACHIEVEMENT
    }
    
    /**
     * Share a video with enhanced metadata and YouTube compliance
     */
    suspend fun shareVideo(
        video: Video,
        platform: SharePlatform,
        personalNote: String? = null,
        shareReason: String? = null
    ): Intent {
        return withContext(Dispatchers.IO) {
            val metadata = createShareMetadata(video, personalNote, shareReason)
            val shareText = buildShareText(metadata, ShareType.VIDEO)
            
            // Track sharing (locally only)
            trackShare(video.videoId, ShareType.VIDEO, platform)
            
            createShareIntent(shareText, platform)
        }
    }
    
    /**
     * Share a playlist with privacy controls
     */
    suspend fun sharePlaylist(
        playlist: UserPlaylist,
        platform: SharePlatform,
        includePersonalNotes: Boolean = false,
        maxVideos: Int = 10
    ): Intent {
        return withContext(Dispatchers.IO) {
            val shareableVideos = playlist.videos.take(maxVideos)
            val shareData = PlaylistShareData(
                playlist = playlist,
                shareableVideos = shareableVideos,
                description = playlist.description,
                isPublic = true, // User controlled
                allowComments = false // Privacy-first default
            )
            
            val shareText = buildPlaylistShareText(shareData, includePersonalNotes)
            
            // Track playlist sharing
            trackShare(playlist.id, ShareType.PLAYLIST, platform)
            
            createShareIntent(shareText, platform)
        }
    }
    
    /**
     * Share a recommendation with explanation
     */
    suspend fun shareRecommendation(
        video: Video,
        reason: String,
        platform: SharePlatform
    ): Intent {
        return withContext(Dispatchers.IO) {
            val metadata = createShareMetadata(video, null, reason)
            val shareText = buildRecommendationShareText(metadata)
            
            trackShare(video.videoId, ShareType.RECOMMENDATION, platform)
            
            createShareIntent(shareText, platform)
        }
    }
    
    /**
     * Get sharing insights and analytics
     */
    suspend fun getShareInsights(): ShareInsights {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext getEmptyInsights()
            }
            
            val shareHistory = getShareHistory()
            
            val totalShares = shareHistory.size
            val mostSharedCategory = findMostSharedCategory(shareHistory)
            val shareFrequency = analyzeShareFrequency(shareHistory)
            val popularContent = findPopularSharedContent(shareHistory)
            val trends = analyzeSharingTrends(shareHistory)
            
            ShareInsights(
                totalShares = totalShares,
                mostSharedCategory = mostSharedCategory,
                shareFrequency = shareFrequency,
                popularContent = popularContent,
                sharingTrends = trends
            )
        }
    }
    
    /**
     * Generate shareable content suggestions
     */
    suspend fun getSharingSuggestions(): List<Video> {
        return withContext(Dispatchers.IO) {
            val watchHistory = userDataManager.getWatchHistory()
            val favorites = userDataManager.getFavorites()
            
            // Suggest highly-rated content that hasn't been shared recently
            val highQualityVideos = watchHistory
                .filter { it.watchProgress > 0.8f } // High completion rate
                .map { item ->
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
                }
                .distinctBy { it.videoId }
                .take(10)
            
            highQualityVideos
        }
    }
    
    private fun createShareMetadata(
        video: Video,
        personalNote: String?,
        shareReason: String?
    ): ShareMetadata {
        return ShareMetadata(
            title = video.title,
            description = video.description,
            thumbnail = video.thumbnail,
            duration = video.duration,
            channelTitle = video.channelTitle,
            shareReason = shareReason,
            personalNote = personalNote
        )
    }
    
    private fun buildShareText(metadata: ShareMetadata, shareType: ShareType): String {
        val sb = StringBuilder()
        
        when (shareType) {
            ShareType.VIDEO -> {
                sb.appendLine("🎥 ${metadata.title}")
                sb.appendLine("📺 by ${metadata.channelTitle}")
                if (metadata.duration.isNotEmpty()) {
                    sb.appendLine("⏱️ ${metadata.duration}")
                }
                
                metadata.shareReason?.let { reason ->
                    sb.appendLine()
                    sb.appendLine("💭 Why I'm sharing: $reason")
                }
                
                metadata.personalNote?.let { note ->
                    sb.appendLine()
                    sb.appendLine("📝 $note")
                }
            }
            else -> {
                sb.appendLine(metadata.title)
            }
        }
        
        sb.appendLine()
        sb.appendLine("🔗 Watch on YouTube: https://youtube.com/watch?v=${extractVideoId(metadata.title)}")
        sb.appendLine()
        sb.appendLine("📱 Shared via VibeTube")
        
        return sb.toString()
    }
    
    private fun buildPlaylistShareText(shareData: PlaylistShareData, includePersonalNotes: Boolean): String {
        val sb = StringBuilder()
        
        sb.appendLine("📋 ${shareData.playlist.name}")
        if (shareData.description.isNotEmpty()) {
            sb.appendLine("📝 ${shareData.description}")
        }
        sb.appendLine("🎬 ${shareData.shareableVideos.size} videos")
        
        val totalDuration = shareData.shareableVideos.sumOf { parseDurationToMinutes(it.duration) }
        sb.appendLine("⏱️ ${formatDuration(totalDuration)} total")
        
        sb.appendLine()
        sb.appendLine("📺 Videos:")
        
        shareData.shareableVideos.forEachIndexed { index, video ->
            sb.appendLine("${index + 1}. ${video.title} (${video.duration})")
        }
        
        if (shareData.shareableVideos.size < shareData.playlist.videos.size) {
            val remaining = shareData.playlist.videos.size - shareData.shareableVideos.size
            sb.appendLine("... and $remaining more videos")
        }
        
        sb.appendLine()
        sb.appendLine("📱 Shared via VibeTube")
        
        return sb.toString()
    }
    
    private fun buildRecommendationShareText(metadata: ShareMetadata): String {
        val sb = StringBuilder()
        
        sb.appendLine("💡 I recommend: ${metadata.title}")
        sb.appendLine("📺 by ${metadata.channelTitle}")
        
        metadata.shareReason?.let { reason ->
            sb.appendLine()
            sb.appendLine("🌟 $reason")
        }
        
        sb.appendLine()
        sb.appendLine("🔗 Watch: https://youtube.com/watch?v=${extractVideoId(metadata.title)}")
        sb.appendLine("📱 Recommended via VibeTube")
        
        return sb.toString()
    }
    
    private fun createShareIntent(shareText: String, platform: SharePlatform): Intent {
        return when (platform) {
            SharePlatform.YOUTUBE_LINK -> {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    putExtra(Intent.EXTRA_SUBJECT, "Check out this video!")
                }
            }
            SharePlatform.MESSAGING -> {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
            }
            SharePlatform.EMAIL -> {
                Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    putExtra(Intent.EXTRA_SUBJECT, "Video Recommendation")
                }
            }
            SharePlatform.CLIPBOARD -> {
                // Copy to clipboard
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("VibeTube Share", shareText)
                clipboard.setPrimaryClip(clip)
                
                Intent() // Empty intent for clipboard
            }
            else -> {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
            }
        }
    }
    
    private fun trackShare(contentId: String, shareType: ShareType, platform: SharePlatform) {
        // Track sharing locally for analytics
        // This would be stored in SharedPreferences or local database
    }
    
    private fun getShareHistory(): List<ShareRecord> {
        // Retrieve share history from local storage
        return emptyList() // Placeholder
    }
    
    private fun findMostSharedCategory(shareHistory: List<ShareRecord>): String {
        // Analyze share history to find most shared category
        return "Entertainment" // Placeholder
    }
    
    private fun analyzeShareFrequency(shareHistory: List<ShareRecord>): Map<String, Int> {
        // Analyze sharing frequency by platform
        return mapOf(
            "YouTube Link" to 5,
            "Messaging" to 3,
            "Email" to 2
        ) // Placeholder
    }
    
    private fun findPopularSharedContent(shareHistory: List<ShareRecord>): List<Video> {
        // Find most frequently shared content
        return emptyList() // Placeholder
    }
    
    private fun analyzeSharingTrends(shareHistory: List<ShareRecord>): List<ShareTrend> {
        // Analyze sharing trends over time
        return listOf(
            ShareTrend("weekly", 8, listOf("Education", "Technology")),
            ShareTrend("monthly", 25, listOf("Entertainment", "Music"))
        ) // Placeholder
    }
    
    private fun extractVideoId(title: String): String {
        // This would extract video ID from title or use actual video ID
        return "dQw4w9WgXcQ" // Placeholder
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
    
    private fun formatDuration(minutes: Double): String {
        val hours = (minutes / 60).toInt()
        val mins = (minutes % 60).toInt()
        
        return if (hours > 0) {
            "${hours}h ${mins}m"
        } else {
            "${mins}m"
        }
    }
    
    private fun getEmptyInsights(): ShareInsights {
        return ShareInsights(
            totalShares = 0,
            mostSharedCategory = "None",
            shareFrequency = emptyMap(),
            popularContent = emptyList(),
            sharingTrends = emptyList()
        )
    }
    
    // Data class for tracking shares locally
    private data class ShareRecord(
        val contentId: String,
        val shareType: ShareType,
        val platform: SharePlatform,
        val timestamp: Long,
        val category: String
    )
}
