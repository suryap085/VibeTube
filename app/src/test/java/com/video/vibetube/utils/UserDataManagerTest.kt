package com.video.vibetube.utils

import android.content.Context
import android.content.SharedPreferences
import com.video.vibetube.models.Video
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

/**
 * Unit tests for UserDataManager
 * 
 * Tests YouTube Policy Compliance:
 * - User consent management
 * - Data retention limits
 * - Local storage only
 * - Data deletion capabilities
 */
@RunWith(MockitoJUnitRunner::class)
class UserDataManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var userDataManager: UserDataManager

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

    @Before
    fun setup() {
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
        `when`(mockEditor.remove(anyString())).thenReturn(mockEditor)

        userDataManager = UserDataManager(mockContext)
    }

    @Test
    fun `test user consent management`() {
        // Test default consent state
        `when`(mockSharedPreferences.getBoolean("user_data_consent", false)).thenReturn(false)
        assertFalse(userDataManager.hasUserConsent())

        // Test setting consent
        userDataManager.setUserConsent(true)
        verify(mockEditor).putBoolean("user_data_consent", true)
        verify(mockEditor).putLong(eq("last_cleanup"), anyLong())
        verify(mockEditor).apply()
    }

    @Test
    fun `test watch history without consent`() = runTest {
        `when`(mockSharedPreferences.getBoolean("user_data_consent", false)).thenReturn(false)

        userDataManager.addToWatchHistory(testVideo, 0.5f, 30000L)
        
        // Should not save data without consent
        verify(mockEditor, never()).putString(eq("watch_history"), anyString())
    }

    @Test
    fun `test watch history with consent`() = runTest {
        `when`(mockSharedPreferences.getBoolean("user_data_consent", false)).thenReturn(true)
        `when`(mockSharedPreferences.getString("watch_history", null)).thenReturn(null)

        userDataManager.addToWatchHistory(testVideo, 0.5f, 30000L)
        
        // Should save data with consent
        verify(mockEditor).putString(eq("watch_history"), anyString())
        verify(mockEditor).apply()
    }

    @Test
    fun `test favorites limit enforcement`() = runTest {
        `when`(mockSharedPreferences.getBoolean("user_data_consent", false)).thenReturn(true)
        `when`(mockSharedPreferences.getString("favorites", null)).thenReturn(null)

        val result = userDataManager.addToFavorites(testVideo)
        assertTrue(result)

        // Test duplicate prevention
        `when`(mockSharedPreferences.getString("favorites", null)).thenReturn(
            """[{"videoId":"test123","title":"Test Video","thumbnail":"https://example.com/thumb.jpg","channelTitle":"Test Channel","channelId":"channel123","duration":"5:30","addedAt":1234567890,"category":"default"}]"""
        )

        val duplicateResult = userDataManager.addToFavorites(testVideo)
        assertFalse(duplicateResult)
    }

    @Test
    fun `test data deletion compliance`() = runTest {
        userDataManager.deleteAllUserData()

        verify(mockEditor).remove("watch_history")
        verify(mockEditor).remove("favorites")
        verify(mockEditor).remove("user_playlists")
        verify(mockEditor).remove("user_data_consent")
        verify(mockEditor).remove("last_cleanup")
        verify(mockEditor).apply()
    }

    @Test
    fun `test watch history size limit`() = runTest {
        `when`(mockSharedPreferences.getBoolean("user_data_consent", false)).thenReturn(true)
        
        // Create a large history JSON to simulate max size
        val largeHistoryJson = buildString {
            append("[")
            repeat(1001) { index ->
                if (index > 0) append(",")
                append("""{"videoId":"video$index","title":"Video $index","thumbnail":"","channelTitle":"Channel","channelId":"channel","duration":"5:00","watchedAt":${System.currentTimeMillis()},"watchProgress":1.0,"watchDuration":300000,"isCompleted":true}""")
            }
            append("]")
        }
        
        `when`(mockSharedPreferences.getString("watch_history", null)).thenReturn(largeHistoryJson)

        userDataManager.addToWatchHistory(testVideo, 1.0f, 300000L)
        
        // Should still save but with size limit enforced
        verify(mockEditor).putString(eq("watch_history"), anyString())
    }

    @Test
    fun `test playlist creation`() = runTest {
        `when`(mockSharedPreferences.getString("user_playlists", null)).thenReturn(null)

        val playlist = userDataManager.createPlaylist("Test Playlist", "Test Description")
        
        assertNotNull(playlist.id)
        assertEquals("Test Playlist", playlist.name)
        assertEquals("Test Description", playlist.description)
        assertTrue(playlist.videoIds.isEmpty())
        
        verify(mockEditor).putString(eq("user_playlists"), anyString())
        verify(mockEditor).apply()
    }

    @Test
    fun `test video addition to playlist`() = runTest {
        `when`(mockSharedPreferences.getBoolean("user_data_consent", false)).thenReturn(true)
        
        val playlistJson = """[{"id":"playlist123","name":"Test Playlist","description":"","createdAt":1234567890,"updatedAt":1234567890,"videoIds":[],"isPublic":false,"thumbnailUrl":""}]"""
        `when`(mockSharedPreferences.getString("user_playlists", null)).thenReturn(playlistJson)

        val result = userDataManager.addVideoToPlaylist("playlist123", "video123")
        assertTrue(result)
        
        verify(mockEditor).putString(eq("user_playlists"), anyString())
        verify(mockEditor).apply()
    }

    @Test
    fun `test duplicate video prevention in playlist`() = runTest {
        `when`(mockSharedPreferences.getBoolean("user_data_consent", false)).thenReturn(true)
        
        val playlistJson = """[{"id":"playlist123","name":"Test Playlist","description":"","createdAt":1234567890,"updatedAt":1234567890,"videoIds":["video123"],"isPublic":false,"thumbnailUrl":""}]"""
        `when`(mockSharedPreferences.getString("user_playlists", null)).thenReturn(playlistJson)

        val result = userDataManager.addVideoToPlaylist("playlist123", "video123")
        assertFalse(result)
    }

    @Test
    fun `test favorites removal`() = runTest {
        `when`(mockSharedPreferences.getBoolean("user_data_consent", false)).thenReturn(true)
        
        val favoritesJson = """[{"videoId":"test123","title":"Test Video","thumbnail":"https://example.com/thumb.jpg","channelTitle":"Test Channel","channelId":"channel123","duration":"5:30","addedAt":1234567890,"category":"default"}]"""
        `when`(mockSharedPreferences.getString("favorites", null)).thenReturn(favoritesJson)

        userDataManager.removeFromFavorites("test123")
        
        verify(mockEditor).putString(eq("favorites"), anyString())
        verify(mockEditor).apply()
    }

    @Test
    fun `test watch history removal`() = runTest {
        `when`(mockSharedPreferences.getBoolean("user_data_consent", false)).thenReturn(true)
        
        val historyJson = """[{"videoId":"test123","title":"Test Video","thumbnail":"https://example.com/thumb.jpg","channelTitle":"Test Channel","channelId":"channel123","duration":"5:30","watchedAt":1234567890,"watchProgress":0.5,"watchDuration":30000,"isCompleted":false}]"""
        `when`(mockSharedPreferences.getString("watch_history", null)).thenReturn(historyJson)

        userDataManager.removeFromWatchHistory("test123")
        
        verify(mockEditor).putString(eq("watch_history"), anyString())
        verify(mockEditor).apply()
    }

    @Test
    fun `test clear all data methods`() = runTest {
        userDataManager.clearWatchHistory()
        verify(mockEditor).remove("watch_history")
        
        userDataManager.clearFavorites()
        verify(mockEditor).remove("favorites")
        
        verify(mockEditor, times(2)).apply()
    }

    @Test
    fun `test data retrieval without consent`() = runTest {
        `when`(mockSharedPreferences.getBoolean("user_data_consent", false)).thenReturn(false)

        val history = userDataManager.getWatchHistory()
        val favorites = userDataManager.getFavorites()
        val playlists = userDataManager.getPlaylists()

        assertTrue(history.isEmpty())
        assertTrue(favorites.isEmpty())
        assertTrue(playlists.isEmpty())
    }

    @Test
    fun `test favorite status check`() = runTest {
        `when`(mockSharedPreferences.getBoolean("user_data_consent", false)).thenReturn(true)
        
        val favoritesJson = """[{"videoId":"test123","title":"Test Video","thumbnail":"https://example.com/thumb.jpg","channelTitle":"Test Channel","channelId":"channel123","duration":"5:30","addedAt":1234567890,"category":"default"}]"""
        `when`(mockSharedPreferences.getString("favorites", null)).thenReturn(favoritesJson)

        val isFavorite = userDataManager.isFavorite("test123")
        assertTrue(isFavorite)

        val isNotFavorite = userDataManager.isFavorite("nonexistent")
        assertFalse(isNotFavorite)
    }
}
