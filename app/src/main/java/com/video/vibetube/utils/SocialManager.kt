package com.video.vibetube.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.video.vibetube.BuildConfig
import com.video.vibetube.models.Video
import com.video.vibetube.models.UserPlaylist
import java.io.File
import java.io.FileWriter

/**
 * YouTube Policy Compliant Social Sharing Manager
 * 
 * Compliance Features:
 * - Shares YouTube video links (not content)
 * - Proper YouTube attribution
 * - User-initiated sharing only
 * - No modification of YouTube content
 */
class SocialManager(private val context: Context) {
    
    companion object {
        private const val YOUTUBE_BASE_URL = "https://www.youtube.com/watch?v="
        private const val YOUTUBE_CHANNEL_URL = "https://www.youtube.com/channel/"
        private const val YOUTUBE_PLAYLIST_URL = "https://www.youtube.com/playlist?list="
        private const val APP_NAME = "VibeTube"

        @Volatile
        private var INSTANCE: SocialManager? = null

        fun getInstance(context: Context): SocialManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SocialManager(context).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Share a single video with proper YouTube attribution
     * YouTube Policy Compliant: Shares links, not content
     */
    fun shareVideo(video: Video, customMessage: String = "") {
        val videoUrl = YOUTUBE_BASE_URL + video.videoId
        val shareText = buildString {
            if (customMessage.isNotEmpty()) {
                append(customMessage)
                append("\n\n")
            }
            append("🎥 ${video.title}")
            append("\n")
            append("📺 ${video.channelTitle}")
            append("\n")
            append("🔗 $videoUrl")
            append("\n\n")
            append("Shared via $APP_NAME")
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Check out this video: ${video.title}")
        }
        
        val chooser = Intent.createChooser(shareIntent, "Share Video")
        context.startActivity(chooser)
    }

    /**
     * Share video with videoId and title (overloaded method)
     * YouTube Policy Compliant: Shares links, not content
     */
    fun shareVideo(videoId: String, title: String, customMessage: String = "") {
        val videoUrl = YOUTUBE_BASE_URL + videoId
        val shareText = buildString {
            if (customMessage.isNotEmpty()) {
                append(customMessage)
                append("\n\n")
            }
            append("🎥 $title\n")
            append("Watch on YouTube: $videoUrl\n\n")
            append("Shared via $APP_NAME")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }

        val chooser = Intent.createChooser(shareIntent, "Share Video")
        context.startActivity(chooser)
    }

    /**
     * Share multiple videos as a collection
     */
    fun shareVideoCollection(videos: List<Video>, collectionName: String = "Video Collection") {
        val shareText = buildString {
            append("🎬 $collectionName\n\n")
            
            videos.forEachIndexed { index, video ->
                append("${index + 1}. ${video.title}\n")
                append("   📺 ${video.channelTitle}\n")
                append("   🔗 ${YOUTUBE_BASE_URL}${video.videoId}\n\n")
            }
            
            append("Shared via $APP_NAME")
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, collectionName)
        }
        
        val chooser = Intent.createChooser(shareIntent, "Share Collection")
        context.startActivity(chooser)
    }
    
    /**
     * Share a custom playlist
     */
    fun sharePlaylist(playlist: UserPlaylist, videos: List<Video>) {
        val shareText = buildString {
            append("📋 ${playlist.name}\n")
            if (playlist.description.isNotEmpty()) {
                append("${playlist.description}\n")
            }
            append("\n")
            
            videos.forEachIndexed { index, video ->
                append("${index + 1}. ${video.title}\n")
                append("   📺 ${video.channelTitle}\n")
                append("   🔗 ${YOUTUBE_BASE_URL}${video.videoId}\n\n")
            }
            
            append("Created and shared via $APP_NAME")
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Playlist: ${playlist.name}")
        }
        
        val chooser = Intent.createChooser(shareIntent, "Share Playlist")
        context.startActivity(chooser)
    }
    
    /**
     * Share channel information
     */
    fun shareChannel(channelId: String, channelTitle: String, customMessage: String = "") {
        val channelUrl = YOUTUBE_CHANNEL_URL + channelId
        val shareText = buildString {
            if (customMessage.isNotEmpty()) {
                append(customMessage)
                append("\n\n")
            }
            append("📺 Check out this YouTube channel:")
            append("\n")
            append("🎬 $channelTitle")
            append("\n")
            append("🔗 $channelUrl")
            append("\n\n")
            append("Shared via $APP_NAME")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "YouTube Channel: $channelTitle")
        }

        val chooser = Intent.createChooser(shareIntent, "Share Channel")
        context.startActivity(chooser)
    }

    /**
     * Share YouTube playlist by ID and title
     * YouTube Policy Compliant: Shares links, not content
     */
    fun sharePlaylist(playlistId: String, playlistTitle: String, customMessage: String = "") {
        val playlistUrl = YOUTUBE_PLAYLIST_URL + playlistId
        val shareText = buildString {
            if (customMessage.isNotEmpty()) {
                append(customMessage)
                append("\n\n")
            }
            append("📋 Check out this YouTube playlist:")
            append("\n")
            append("🎵 $playlistTitle")
            append("\n")
            append("🔗 $playlistUrl")
            append("\n\n")
            append("Shared via $APP_NAME")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "YouTube Playlist: $playlistTitle")
        }

        val chooser = Intent.createChooser(shareIntent, "Share Playlist")
        context.startActivity(chooser)
    }
    
    /**
     * Export playlist as a file for sharing
     * YouTube Policy Compliant: Exports links and metadata only
     */
    fun exportPlaylistAsFile(playlist: UserPlaylist, videos: List<Video>) {
        try {
            val fileName = "playlist_${playlist.name.replace(Regex("[^A-Za-z0-9]"), "_")}.txt"
            val file = File(context.cacheDir, fileName)
            
            FileWriter(file).use { writer ->
                writer.write("Playlist: ${playlist.name}\n")
                writer.write("Description: ${playlist.description}\n")
                writer.write("Created: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(playlist.createdAt))}\n")
                writer.write("Videos: ${videos.size}\n")
                writer.write("Generated by: $APP_NAME\n\n")
                writer.write("=" + "=".repeat(49) + "\n\n")
                
                videos.forEachIndexed { index, video ->
                    writer.write("${index + 1}. ${video.title}\n")
                    writer.write("   Channel: ${video.channelTitle}\n")
                    writer.write("   Duration: ${video.duration}\n")
                    writer.write("   URL: ${YOUTUBE_BASE_URL}${video.videoId}\n\n")
                }
            }
            
            val fileUri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                file
            )
            
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "Playlist: ${playlist.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooser = Intent.createChooser(shareIntent, "Export Playlist")
            context.startActivity(chooser)
            
        } catch (e: Exception) {
            // Fallback to text sharing
            sharePlaylist(playlist, videos)
        }
    }
    
    /**
     * Share app recommendation
     */
    fun shareApp(customMessage: String = "") {
        val appUrl = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
        val shareText = buildString {
            if (customMessage.isNotEmpty()) {
                append(customMessage)
                append("\n\n")
            }
            append("🎥 Check out $APP_NAME - A great way to discover and organize YouTube content!")
            append("\n")
            append("📱 Download: $appUrl")
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Check out $APP_NAME")
        }
        
        val chooser = Intent.createChooser(shareIntent, "Share $APP_NAME")
        context.startActivity(chooser)
    }
    
    /**
     * Open video in external YouTube app
     * YouTube Policy Compliant: Directs to official YouTube app
     */
    fun openInYouTubeApp(videoId: String) {
        try {
            val youtubeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
            youtubeIntent.putExtra("force_fullscreen", false)
            context.startActivity(youtubeIntent)
        } catch (e: Exception) {
            // Fallback to web browser
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(YOUTUBE_BASE_URL + videoId))
            context.startActivity(webIntent)
        }
    }
    
    /**
     * Open channel in external YouTube app
     */
    fun openChannelInYouTubeApp(channelId: String) {
        try {
            val youtubeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube://user/$channelId"))
            context.startActivity(youtubeIntent)
        } catch (e: Exception) {
            // Fallback to web browser
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(YOUTUBE_CHANNEL_URL + channelId))
            context.startActivity(webIntent)
        }
    }
    
    /**
     * Create shareable deep link for app content
     */
    fun createDeepLink(videoId: String): String {
        return "${BuildConfig.APPLICATION_ID}://video/$videoId"
    }
    
    /**
     * Share a list of favorite videos
     * YouTube Policy Compliant: Shares links only
     */
    fun shareFavoritesList(favorites: List<com.video.vibetube.models.FavoriteItem>) {
        if (favorites.isEmpty()) {
            return
        }

        val shareText = buildString {
            appendLine("Check out my favorite videos from $APP_NAME:")
            appendLine()
            favorites.take(10).forEach { favorite -> // Limit to 10 items to avoid long messages
                appendLine("🎥 ${favorite.title}")
                appendLine("   by ${favorite.channelTitle}")
                appendLine("   ${YOUTUBE_BASE_URL}${favorite.videoId}")
                appendLine()
            }
            if (favorites.size > 10) {
                appendLine("...and ${favorites.size - 10} more!")
                appendLine()
            }
            appendLine("Shared via $APP_NAME")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "My Favorite Videos from $APP_NAME")
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Favorites"))
    }

    /**
     * Export favorites list to external storage or sharing
     * YouTube Policy Compliant: Exports metadata only
     */
    fun exportFavoritesList(favorites: List<com.video.vibetube.models.FavoriteItem>) {
        if (favorites.isEmpty()) {
            return
        }

        val exportText = buildString {
            appendLine("VibeTube Favorites Export")
            appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("Total Videos: ${favorites.size}")
            appendLine()
            appendLine("=" + "=".repeat(49))
            appendLine()

            favorites.forEach { favorite ->
                appendLine("Title: ${favorite.title}")
                appendLine("Channel: ${favorite.channelTitle}")
                appendLine("Duration: ${favorite.duration}")
                appendLine("Category: ${favorite.category}")
                appendLine("Added: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(favorite.addedAt))}")
                appendLine("URL: ${YOUTUBE_BASE_URL}${favorite.videoId}")
                appendLine("-" + "-".repeat(29))
                appendLine()
            }

            appendLine("Exported from $APP_NAME")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, exportText)
            putExtra(Intent.EXTRA_SUBJECT, "VibeTube Favorites Export")
        }

        context.startActivity(Intent.createChooser(shareIntent, "Export Favorites"))
    }

    /**
     * Get sharing statistics (for analytics)
     */
    fun getShareableContent(video: Video): Map<String, String> {
        return mapOf(
            "title" to video.title,
            "channel" to video.channelTitle,
            "url" to (YOUTUBE_BASE_URL + video.videoId),
            "duration" to video.duration,
            "app" to APP_NAME
        )
    }
}
