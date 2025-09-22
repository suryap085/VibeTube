package com.video.vibetube

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.AchievementManager
import com.video.vibetube.utils.SocialManager
import com.video.vibetube.utils.EngagementAnalytics
import com.video.vibetube.adapters.LibraryPagerAdapter
import com.video.vibetube.fragments.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Before
import kotlinx.coroutines.runBlocking

/**
 * Integration test to verify VibeTube user engagement features
 */
@RunWith(AndroidJUnit4::class)
class IntegrationTest {
    
    private lateinit var context: Context
    private lateinit var userDataManager: UserDataManager
    private lateinit var achievementManager: AchievementManager
    private lateinit var socialManager: SocialManager
    private lateinit var engagementAnalytics: EngagementAnalytics
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Test manager initialization
        userDataManager = UserDataManager.getInstance(context)
        achievementManager = AchievementManager.getInstance(context)
        socialManager = SocialManager.getInstance(context)
        engagementAnalytics = EngagementAnalytics.getInstance(context)
    }
    
    @Test
    fun testManagerInitialization() {
        // Verify all managers initialize without crashing
        assertNotNull("UserDataManager should initialize", userDataManager)
        assertNotNull("AchievementManager should initialize", achievementManager)
        assertNotNull("SocialManager should initialize", socialManager)
        assertNotNull("EngagementAnalytics should initialize", engagementAnalytics)
    }
    
    @Test
    fun testFragmentInstantiation() {
        // Test that all fragments can be instantiated without crashing
        val watchHistoryFragment = WatchHistoryFragment.newInstance()
        val favoritesFragment = FavoritesFragment.newInstance()
        val playlistsFragment = PlaylistsFragment.newInstance()
        val achievementsFragment = AchievementsFragment.newInstance()
        val settingsFragment = LibrarySettingsFragment.newInstance()
        
        assertNotNull("WatchHistoryFragment should instantiate", watchHistoryFragment)
        assertNotNull("FavoritesFragment should instantiate", favoritesFragment)
        assertNotNull("PlaylistsFragment should instantiate", playlistsFragment)
        assertNotNull("AchievementsFragment should instantiate", achievementsFragment)
        assertNotNull("LibrarySettingsFragment should instantiate", settingsFragment)
    }
    
    @Test
    fun testUserDataManagerConsent() {
        // Test user consent functionality
        assertFalse("Initial consent should be false", userDataManager.hasUserConsent())
        
        userDataManager.setUserConsent(true)
        assertTrue("Consent should be true after setting", userDataManager.hasUserConsent())
        
        userDataManager.setUserConsent(false)
        assertFalse("Consent should be false after unsetting", userDataManager.hasUserConsent())
    }
    
    @Test
    fun testAchievementManagerGamification() {
        // Test gamification toggle
        assertTrue("Gamification should be enabled by default", achievementManager.isGamificationEnabled())
        
        achievementManager.setGamificationEnabled(false)
        assertFalse("Gamification should be disabled", achievementManager.isGamificationEnabled())
        
        achievementManager.setGamificationEnabled(true)
        assertTrue("Gamification should be enabled again", achievementManager.isGamificationEnabled())
    }
    
    @Test
    fun testAchievementsList() {
        // Test that achievements list is not empty
        val achievements = achievementManager.getAllAchievements()
        assertTrue("Achievements list should not be empty", achievements.isNotEmpty())
        
        // Verify achievement structure
        val firstAchievement = achievements.first()
        assertNotNull("Achievement ID should not be null", firstAchievement.id)
        assertNotNull("Achievement title should not be null", firstAchievement.title)
        assertNotNull("Achievement description should not be null", firstAchievement.description)
    }
    
    @Test
    fun testEngagementAnalyticsEnabled() {
        // Test analytics toggle
        assertTrue("Analytics should be enabled by default", engagementAnalytics.isAnalyticsEnabled())

        engagementAnalytics.setAnalyticsEnabled(false)
        assertFalse("Analytics should be disabled", engagementAnalytics.isAnalyticsEnabled())

        engagementAnalytics.setAnalyticsEnabled(true)
        assertTrue("Analytics should be enabled again", engagementAnalytics.isAnalyticsEnabled())
    }

    @Test
    fun testUserDataManagerMethods() {
        // Test that all required methods exist and work
        userDataManager.setUserConsent(true)
        assertTrue("User consent should be set", userDataManager.hasUserConsent())

        // Test that methods don't crash when called
        try {
            runBlocking {
                userDataManager.clearWatchHistory()
                userDataManager.getWatchHistory()
                userDataManager.getFavorites()
                userDataManager.getPlaylists()
                userDataManager.createPlaylist("Test", "Description")
                userDataManager.updatePlaylistInfo("test-id", "New Name", "New Description")
                userDataManager.deletePlaylist("test-id")
            }
        } catch (e: Exception) {
            fail("UserDataManager methods should not crash: ${e.message}")
        }
    }

    @Test
    fun testAchievementManagerMethods() {
        // Test that all required methods exist and work
        assertTrue("Gamification should be enabled by default", achievementManager.isGamificationEnabled())

        // Test notification settings
        achievementManager.setNotificationsEnabled(false)
        assertFalse("Notifications should be disabled", achievementManager.areNotificationsEnabled())

        achievementManager.setNotificationsEnabled(true)
        assertTrue("Notifications should be enabled", achievementManager.areNotificationsEnabled())

        // Test that methods don't crash when called
        try {
            runBlocking {
                achievementManager.resetAllProgress()
                achievementManager.exportAchievementData()
                achievementManager.getUserAchievements()
                achievementManager.getUserStats()
                achievementManager.getAchievementProgress()
            }
        } catch (e: Exception) {
            fail("AchievementManager methods should not crash: ${e.message}")
        }
    }

    @Test
    fun testSocialManagerMethods() {
        // Test that all required methods exist and work
        val testFavorites = listOf<com.video.vibetube.models.FavoriteItem>()

        try {
            socialManager.shareFavoritesList(testFavorites)
            socialManager.exportFavoritesList(testFavorites)
            socialManager.shareVideo("test-id", "Test Title")
        } catch (e: Exception) {
            fail("SocialManager methods should not crash: ${e.message}")
        }
    }
}
