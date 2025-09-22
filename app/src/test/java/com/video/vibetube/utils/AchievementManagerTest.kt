package com.video.vibetube.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*

/**
 * Unit tests for AchievementManager
 * 
 * Tests YouTube Policy Compliance:
 * - No artificial engagement incentives
 * - User can disable gamification
 * - Transparent achievement criteria
 * - Based on user's own viewing data only
 */
@RunWith(MockitoJUnitRunner::class)
class AchievementManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences

    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var achievementManager: AchievementManager

    @Before
    fun setup() {
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)

        achievementManager = AchievementManager(mockContext)
    }

    @Test
    fun `test gamification can be disabled`() {
        // Test default state
        `when`(mockSharedPreferences.getBoolean("gamification_enabled", true)).thenReturn(true)
        assertTrue(achievementManager.isGamificationEnabled())

        // Test disabling gamification
        achievementManager.setGamificationEnabled(false)
        verify(mockEditor).putBoolean("gamification_enabled", false)
        verify(mockEditor).apply()
    }

    @Test
    fun `test achievements not returned when gamification disabled`() = runTest {
        `when`(mockSharedPreferences.getBoolean("gamification_enabled", true)).thenReturn(false)

        val achievements = achievementManager.getUserAchievements()
        assertTrue(achievements.isEmpty())

        val progress = achievementManager.getAchievementProgress()
        assertTrue(progress.isEmpty())
    }

    @Test
    fun `test achievement criteria are transparent`() {
        val allAchievements = achievementManager.getAllAchievements()
        
        // Verify all achievements have clear criteria
        allAchievements.forEach { achievement ->
            assertNotNull(achievement.id)
            assertNotNull(achievement.title)
            assertNotNull(achievement.description)
            assertNotNull(achievement.criteria)
            assertTrue(achievement.criteria.threshold > 0)
        }
    }

    @Test
    fun `test achievement categories are properly defined`() {
        val allAchievements = achievementManager.getAllAchievements()
        
        val categories = allAchievements.map { it.category }.distinct()
        
        // Verify we have expected categories
        assertTrue(categories.contains(AchievementManager.AchievementCategory.VIEWING))
        assertTrue(categories.contains(AchievementManager.AchievementCategory.EXPLORATION))
        assertTrue(categories.contains(AchievementManager.AchievementCategory.ORGANIZATION))
        assertTrue(categories.contains(AchievementManager.AchievementCategory.CONSISTENCY))
        assertTrue(categories.contains(AchievementManager.AchievementCategory.MILESTONES))
    }

    @Test
    fun `test stats update triggers achievement check`() = runTest {
        `when`(mockSharedPreferences.getBoolean("gamification_enabled", true)).thenReturn(true)
        `when`(mockSharedPreferences.getString("user_achievements", null)).thenReturn(null)
        `when`(mockSharedPreferences.getString("user_stats", null)).thenReturn(null)

        val newAchievements = achievementManager.updateStats(
            videosWatched = 1,
            watchTime = 300000L,
            videosCompleted = 1
        )

        // Should unlock "First Steps" achievement
        assertTrue(newAchievements.isNotEmpty())
        assertEquals("first_video", newAchievements.first().id)
        
        verify(mockEditor, atLeastOnce()).putString(eq("user_achievements"), anyString())
        verify(mockEditor, atLeastOnce()).putString(eq("user_stats"), anyString())
    }

    @Test
    fun `test achievement progress calculation`() = runTest {
        `when`(mockSharedPreferences.getBoolean("gamification_enabled", true)).thenReturn(true)
        
        // Mock user stats
        val statsJson = """{"totalVideosWatched":5,"totalWatchTime":1500000,"videosCompleted":3,"uniqueChannelsWatched":2,"favoriteVideos":1,"playlistsCreated":0,"consecutiveDaysActive":1,"longestWatchSession":0,"firstVideoWatchedAt":0,"lastActiveDate":${System.currentTimeMillis()}}"""
        `when`(mockSharedPreferences.getString("user_stats", null)).thenReturn(statsJson)
        `when`(mockSharedPreferences.getString("user_achievements", null)).thenReturn(null)

        val progress = achievementManager.getAchievementProgress()
        
        // Find the "Video Explorer" achievement (10 videos)
        val videoExplorerProgress = progress.find { it.id == "video_explorer" }
        assertNotNull(videoExplorerProgress)
        assertEquals(5f, videoExplorerProgress!!.progress)
        assertEquals(10f, videoExplorerProgress.maxProgress)
    }

    @Test
    fun `test no duplicate achievements`() = runTest {
        `when`(mockSharedPreferences.getBoolean("gamification_enabled", true)).thenReturn(true)
        
        // Mock already unlocked achievement
        val existingAchievementJson = """[{"id":"first_video","title":"First Steps","description":"Watch your first video","iconResource":"ic_play_circle","category":"VIEWING","criteria":{"type":"VIDEOS_WATCHED","threshold":1.0,"timeframe":0},"isUnlocked":true,"unlockedAt":1234567890,"progress":1.0,"maxProgress":1.0}]"""
        `when`(mockSharedPreferences.getString("user_achievements", null)).thenReturn(existingAchievementJson)
        
        val statsJson = """{"totalVideosWatched":1,"totalWatchTime":300000,"videosCompleted":1,"uniqueChannelsWatched":1,"favoriteVideos":0,"playlistsCreated":0,"consecutiveDaysActive":1,"longestWatchSession":0,"firstVideoWatchedAt":0,"lastActiveDate":${System.currentTimeMillis()}}"""
        `when`(mockSharedPreferences.getString("user_stats", null)).thenReturn(statsJson)

        val newAchievements = achievementManager.updateStats(videosWatched = 1)
        
        // Should not unlock the same achievement again
        assertTrue(newAchievements.none { it.id == "first_video" })
    }

    @Test
    fun `test achievement unlocking thresholds`() {
        val allAchievements = achievementManager.getAllAchievements()
        
        // Test specific achievement thresholds
        val firstVideo = allAchievements.find { it.id == "first_video" }
        assertEquals(1f, firstVideo?.criteria?.threshold)
        
        val videoExplorer = allAchievements.find { it.id == "video_explorer" }
        assertEquals(10f, videoExplorer?.criteria?.threshold)
        
        val bingeWatcher = allAchievements.find { it.id == "binge_watcher" }
        assertEquals(50f, bingeWatcher?.criteria?.threshold)
        
        val completionist = allAchievements.find { it.id == "completionist" }
        assertEquals(10f, completionist?.criteria?.threshold)
    }

    @Test
    fun `test user stats initialization`() = runTest {
        `when`(mockSharedPreferences.getString("user_stats", null)).thenReturn(null)

        val stats = achievementManager.getUserStats()
        
        // Should return default stats
        assertEquals(0, stats.totalVideosWatched)
        assertEquals(0L, stats.totalWatchTime)
        assertEquals(0, stats.videosCompleted)
        assertEquals(0, stats.uniqueChannelsWatched)
        assertEquals(0, stats.favoriteVideos)
        assertEquals(0, stats.playlistsCreated)
        assertEquals(0, stats.consecutiveDaysActive)
        assertEquals(0L, stats.longestWatchSession)
    }

    @Test
    fun `test stats accumulation`() = runTest {
        `when`(mockSharedPreferences.getBoolean("gamification_enabled", true)).thenReturn(true)
        
        // Initial stats
        val initialStatsJson = """{"totalVideosWatched":5,"totalWatchTime":1500000,"videosCompleted":3,"uniqueChannelsWatched":2,"favoriteVideos":1,"playlistsCreated":0,"consecutiveDaysActive":1,"longestWatchSession":0,"firstVideoWatchedAt":0,"lastActiveDate":${System.currentTimeMillis()}}"""
        `when`(mockSharedPreferences.getString("user_stats", null)).thenReturn(initialStatsJson)
        `when`(mockSharedPreferences.getString("user_achievements", null)).thenReturn("[]")

        achievementManager.updateStats(
            videosWatched = 2,
            watchTime = 600000L,
            videosCompleted = 1,
            uniqueChannels = setOf("channel1", "channel2", "channel3"),
            favoritesAdded = 1
        )

        // Verify stats are accumulated correctly
        verify(mockEditor).putString(eq("user_stats"), contains("\"totalVideosWatched\":7"))
        verify(mockEditor).putString(eq("user_stats"), contains("\"totalWatchTime\":2100000"))
        verify(mockEditor).putString(eq("user_stats"), contains("\"videosCompleted\":4"))
        verify(mockEditor).putString(eq("user_stats"), contains("\"uniqueChannelsWatched\":3"))
        verify(mockEditor).putString(eq("user_stats"), contains("\"favoriteVideos\":2"))
    }

    @Test
    fun `test achievement types coverage`() {
        val allAchievements = achievementManager.getAllAchievements()
        
        // Verify we have achievements for all criteria types
        val criteriaTypes = allAchievements.map { it.criteria.type }.distinct()
        
        assertTrue(criteriaTypes.contains(AchievementManager.CriteriaType.VIDEOS_WATCHED))
        assertTrue(criteriaTypes.contains(AchievementManager.CriteriaType.VIDEOS_COMPLETED))
        assertTrue(criteriaTypes.contains(AchievementManager.CriteriaType.UNIQUE_CHANNELS))
        assertTrue(criteriaTypes.contains(AchievementManager.CriteriaType.FAVORITES_ADDED))
        assertTrue(criteriaTypes.contains(AchievementManager.CriteriaType.PLAYLISTS_CREATED))
        assertTrue(criteriaTypes.contains(AchievementManager.CriteriaType.WATCH_TIME))
        assertTrue(criteriaTypes.contains(AchievementManager.CriteriaType.CONSECUTIVE_DAYS))
    }
}
