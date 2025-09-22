package com.video.vibetube

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.video.vibetube.activity.LibraryActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for LibraryActivity
 * 
 * Tests YouTube Policy Compliance:
 * - User consent flow
 * - Data privacy controls
 * - Feature accessibility
 * - Navigation behavior
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class LibraryActivityInstrumentedTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Clear any existing user data for clean test state
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        val achievementPrefs = context.getSharedPreferences("achievements", Context.MODE_PRIVATE)
        achievementPrefs.edit().clear().apply()
    }

    @Test
    fun testLibraryActivityLaunch() {
        val intent = Intent(context, LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Verify activity launches successfully
            onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testUserConsentDialog() {
        val intent = Intent(context, LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Should show consent dialog for first-time users
            onView(withText("Enable Library Features")).check(matches(isDisplayed()))
            onView(withText("VibeTube can save your watch history")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testConsentAcceptance() {
        val intent = Intent(context, LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Accept consent
            onView(withText("Enable")).perform(click())
            
            // Should show library tabs
            onView(withId(R.id.tabLayout)).check(matches(isDisplayed()))
            onView(withId(R.id.viewPager)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testConsentDecline() {
        val intent = Intent(context, LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Decline consent
            onView(withText("Not Now")).perform(click())
            
            // Should show privacy-focused message
            onView(withText("Privacy Mode")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testTabNavigation() {
        // Set up consent first
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Test tab navigation
            onView(withText("History")).perform(click())
            onView(withText("Favorites")).perform(click())
            onView(withText("Playlists")).perform(click())
            onView(withText("Achievements")).perform(click())
        }
    }

    @Test
    fun testHistoryTabContent() {
        // Set up consent and some test data
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        intent.putExtra("SECTION", "history")
        
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Should show history tab
            onView(withText("History")).check(matches(isDisplayed()))
            
            // Should show empty state initially
            onView(withText("No watch history yet")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testFavoritesTabContent() {
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        intent.putExtra("SECTION", "favorites")
        
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            onView(withText("Favorites")).check(matches(isDisplayed()))
            onView(withText("No favorites yet")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testPlaylistsTabContent() {
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        intent.putExtra("SECTION", "playlists")
        
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            onView(withText("Playlists")).check(matches(isDisplayed()))
            onView(withText("No playlists yet")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testAchievementsTabContent() {
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        intent.putExtra("SECTION", "achievements")
        
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            onView(withText("Achievements")).check(matches(isDisplayed()))
            // Should show achievement list
            onView(withText("First Steps")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testSettingsTabContent() {
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        intent.putExtra("SECTION", "settings")
        
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            onView(withText("Settings")).check(matches(isDisplayed()))
            onView(withText("Manage Your Data")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testDataDeletionFlow() {
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        intent.putExtra("SECTION", "settings")
        
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Navigate to data management
            onView(withText("Delete All Data")).perform(click())
            
            // Should show confirmation dialog
            onView(withText("Delete All Library Data?")).check(matches(isDisplayed()))
            onView(withText("This action cannot be undone")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testDataDeletionConfirmation() {
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        intent.putExtra("SECTION", "settings")
        
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            onView(withText("Delete All Data")).perform(click())
            onView(withText("Delete")).perform(click())
            
            // Should show confirmation message
            onView(withText("All library data has been deleted")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testToolbarNavigation() {
        val intent = Intent(context, LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Test back button
            onView(withContentDescription("Navigate up")).perform(click())
            
            // Activity should finish (can't easily test this in Espresso)
        }
    }

    @Test
    fun testGamificationToggle() {
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        intent.putExtra("SECTION", "settings")
        
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Find and toggle gamification setting
            onView(withText("Achievement System")).perform(click())
            
            // Should show toggle or confirmation
            onView(withText("Disable")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testNotificationSettings() {
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        intent.putExtra("SECTION", "settings")
        
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Test notification settings
            onView(withText("Notifications")).perform(click())
            
            // Should show notification options
            onView(withText("Achievement Notifications")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testPrivacyInformation() {
        val intent = Intent(context, LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Should show privacy information in consent dialog
            onView(withText("This data is stored locally")).check(matches(isDisplayed()))
            onView(withText("30 days of inactivity")).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testAccessibilityLabels() {
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Test that important UI elements have accessibility labels
            onView(withId(R.id.toolbar)).check(matches(hasContentDescription()))
            onView(withId(R.id.tabLayout)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testEmptyStateMessages() {
        val prefs = context.getSharedPreferences("user_data", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_data_consent", true).apply()
        
        val intent = Intent(context, LibraryActivity::class.java)
        ActivityScenario.launch<LibraryActivity>(intent).use { scenario ->
            // Test each tab shows appropriate empty state
            onView(withText("History")).perform(click())
            onView(withText("Start watching videos")).check(matches(isDisplayed()))
            
            onView(withText("Favorites")).perform(click())
            onView(withText("Tap the heart icon")).check(matches(isDisplayed()))
            
            onView(withText("Playlists")).perform(click())
            onView(withText("Create your first playlist")).check(matches(isDisplayed()))
        }
    }
}
