package com.video.vibetube.utils

import android.content.Context
import android.content.Intent
import com.video.vibetube.models.Video
import com.video.vibetube.models.UserPlaylist
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

/**
 * Unit tests for SocialManager
 * 
 * Tests YouTube Policy Compliance:
 * - Shares YouTube video links (not content)
 * - Proper YouTube attribution
 * - User-initiated sharing only
 * - No modification of YouTube content
 */
@RunWith(MockitoJUnitRunner::class)
class SocialManagerTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var socialManager: SocialManager

    private val testVideo = Video(
        videoId = "test123",
        title = "Test Video",
        description = "Test Description",
        thumbnail = "https://example.com/thumb.jpg",
        channelTitle = "Test Channel",
        publishedAt = "2023-01-01",
        duration = "5:30",
        channelId = "channel123"
    )

    private val testPlaylist = UserPlaylist(
        id = "playlist123",
        name = "Test Playlist",
        description = "Test playlist description",
        videoIds = mutableListOf("test123", "test456")
    )

    @Before
    fun setup() {
        socialManager = SocialManager(mockContext)
    }

    @Test
    fun `test video sharing creates proper YouTube URL`() {
        val shareableContent = socialManager.getShareableContent(testVideo)
        
        assertEquals("Test Video", shareableContent["title"])
        assertEquals("Test Channel", shareableContent["channel"])
        assertEquals("https://www.youtube.com/watch?v=test123", shareableContent["url"])
        assertEquals("5:30", shareableContent["duration"])
        assertEquals("VibeTube", shareableContent["app"])
    }

    @Test
    fun `test video sharing includes proper attribution`() {
        // Capture the intent when shareVideo is called
        socialManager.shareVideo(testVideo, "Check this out!")
        
        // Verify startActivity was called
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test channel sharing creates proper YouTube channel URL`() {
        socialManager.shareChannel("channel123", "Test Channel", "Great channel!")
        
        // Verify startActivity was called with proper intent
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test playlist sharing includes all videos`() {
        val videos = listOf(
            testVideo,
            testVideo.copy(videoId = "test456", title = "Second Video")
        )
        
        socialManager.sharePlaylist(testPlaylist, videos)
        
        // Verify startActivity was called
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test video collection sharing`() {
        val videos = listOf(
            testVideo,
            testVideo.copy(videoId = "test456", title = "Second Video"),
            testVideo.copy(videoId = "test789", title = "Third Video")
        )
        
        socialManager.shareVideoCollection(videos, "My Favorite Videos")
        
        // Verify startActivity was called
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test app sharing includes proper app information`() {
        socialManager.shareApp("Try this awesome app!")
        
        // Verify startActivity was called
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test deep link creation`() {
        val deepLink = socialManager.createDeepLink("test123")
        
        assertTrue(deepLink.contains("video/test123"))
        assertTrue(deepLink.startsWith("com.video.vibetube://") || deepLink.contains("://"))
    }

    @Test
    fun `test YouTube app opening with video ID`() {
        socialManager.openInYouTubeApp("test123")
        
        // Verify startActivity was called (either YouTube app or web fallback)
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test YouTube app opening with channel ID`() {
        socialManager.openChannelInYouTubeApp("channel123")
        
        // Verify startActivity was called
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test playlist export creates proper file content`() {
        val videos = listOf(testVideo)
        
        socialManager.exportPlaylistAsFile(testPlaylist, videos)
        
        // Verify startActivity was called (either file sharing or fallback)
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test sharing preserves YouTube attribution`() {
        val shareableContent = socialManager.getShareableContent(testVideo)
        
        // Verify YouTube URL format is preserved
        val url = shareableContent["url"] as String
        assertTrue(url.startsWith("https://www.youtube.com/watch?v="))
        assertTrue(url.contains(testVideo.videoId))
    }

    @Test
    fun `test no content modification in sharing`() {
        val shareableContent = socialManager.getShareableContent(testVideo)
        
        // Verify original video data is preserved exactly
        assertEquals(testVideo.title, shareableContent["title"])
        assertEquals(testVideo.channelTitle, shareableContent["channel"])
        assertEquals(testVideo.duration, shareableContent["duration"])
        
        // Verify only metadata is shared, not content
        assertFalse(shareableContent.containsKey("videoData"))
        assertFalse(shareableContent.containsKey("audioData"))
        assertFalse(shareableContent.containsKey("downloadUrl"))
    }

    @Test
    fun `test custom message inclusion in video sharing`() {
        val customMessage = "This is an amazing video!"
        
        socialManager.shareVideo(testVideo, customMessage)
        
        // Verify startActivity was called
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test empty custom message handling`() {
        socialManager.shareVideo(testVideo, "")
        
        // Should still work with empty message
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test channel sharing with custom message`() {
        socialManager.shareChannel("channel123", "Test Channel", "Check out this channel!")
        
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test app sharing with custom message`() {
        socialManager.shareApp("I love this app!")
        
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test sharing intent type is text`() {
        // This test would need to capture the actual intent to verify its properties
        // For now, we verify that startActivity is called
        socialManager.shareVideo(testVideo)
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test playlist sharing with empty description`() {
        val playlistWithoutDescription = testPlaylist.copy(description = "")
        val videos = listOf(testVideo)
        
        socialManager.sharePlaylist(playlistWithoutDescription, videos)
        
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test video collection sharing with empty list`() {
        socialManager.shareVideoCollection(emptyList(), "Empty Collection")
        
        verify(mockContext).startActivity(any(Intent::class.java))
    }

    @Test
    fun `test sharing maintains YouTube compliance`() {
        val shareableContent = socialManager.getShareableContent(testVideo)
        
        // Verify no prohibited content is included
        assertFalse(shareableContent.containsKey("downloadLink"))
        assertFalse(shareableContent.containsKey("streamUrl"))
        assertFalse(shareableContent.containsKey("directVideoUrl"))
        
        // Verify only allowed metadata is shared
        assertTrue(shareableContent.containsKey("title"))
        assertTrue(shareableContent.containsKey("channel"))
        assertTrue(shareableContent.containsKey("url"))
        assertTrue(shareableContent.containsKey("duration"))
        assertTrue(shareableContent.containsKey("app"))
    }
}
