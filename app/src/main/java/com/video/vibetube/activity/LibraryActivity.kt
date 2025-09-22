package com.video.vibetube.activity

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.video.vibetube.R
import com.video.vibetube.adapters.LibraryPagerAdapter
import com.video.vibetube.models.Video
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.AchievementManager
import com.video.vibetube.utils.SocialManager
import kotlinx.coroutines.launch

/**
 * Library Activity - Central hub for all user engagement features
 * 
 * Features:
 * - Watch History with resume functionality
 * - Favorites management with categories
 * - Custom playlists
 * - Achievements and stats
 * - Recommendations and trending content
 * - Content discovery and curation
 */
class LibraryActivity : AppCompatActivity() {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    
    // User Engagement Components
    private lateinit var userDataManager: UserDataManager
    private lateinit var achievementManager: AchievementManager
    private lateinit var socialManager: SocialManager
    
    private var initialSection: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_library)
        
        // Get initial section from intent
        initialSection = intent.getStringExtra("SECTION")
        
        // Initialize components
        userDataManager = UserDataManager.getInstance(this)
        achievementManager = AchievementManager.getInstance(this)
        socialManager = SocialManager.getInstance(this)
        
        initViews()
        setupToolbar()
        checkUserConsent()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.libraryToolbar)
        tabLayout = findViewById(R.id.libraryTabLayout)
        viewPager = findViewById(R.id.libraryViewPager)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        
        // Set title based on initial section
        toolbar.title = when (initialSection) {
            "history" -> "Watch History"
            "favorites" -> "Favorites"
            "playlists" -> "My Playlists"
            "recommendations" -> "For You"
            "trending" -> "Trending"
            "categories" -> "Categories"
            "achievements" -> "Achievements"
            else -> "Library"
        }
    }
    
    private fun checkUserConsent() {
        if (!userDataManager.hasUserConsent()) {
            showConsentDialog()
        } else {
            setupViewPager()
        }
    }
    
    private fun setupViewPager() {
        val adapter = LibraryPagerAdapter(this, initialSection)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            // Use the adapter's getTabTitle method for correct dynamic titles
            tab.text = adapter.getTabTitle(position)
        }.attach()

        // Navigate to specific section if requested
        navigateToSection(initialSection)
    }
    
    private fun navigateToSection(section: String?) {
        val position = getPositionForSection(section)
        viewPager.setCurrentItem(position, false)
    }

    /**
     * Get the correct tab position for a section based on the current tab arrangement
     */
    private fun getPositionForSection(section: String?): Int {
        return when (initialSection) {
            "history" -> when (section) {
                "history" -> 0
                "favorites" -> 1
                "playlists" -> 2
                "achievements" -> 3
                "settings" -> 4
                else -> 0
            }
            "favorites" -> when (section) {
                "favorites" -> 0
                "history" -> 1
                "playlists" -> 2
                "achievements" -> 3
                "settings" -> 4
                else -> 0
            }
            "playlists" -> when (section) {
                "playlists" -> 0
                "favorites" -> 1
                "history" -> 2
                "achievements" -> 3
                "settings" -> 4
                else -> 0
            }
            "achievements" -> when (section) {
                "achievements" -> 0
                "history" -> 1
                "favorites" -> 2
                "playlists" -> 3
                "settings" -> 4
                else -> 0
            }

            // For recommendations, trending, and categories, use default structure
            // The content will be customized within the fragments based on initialSection
            "recommendations", "trending", "categories" -> when (section) {
                "history" -> 0
                "favorites" -> 1
                "playlists" -> 2
                "achievements" -> 3
                "settings" -> 4
                "recommendations", "trending", "categories" -> 0
                else -> 0
            }
            else -> when (section) {
                "history" -> 0
                "favorites" -> 1
                "playlists" -> 2
                "achievements" -> 3
                "settings" -> 4
                else -> 0
            }
        }
    }
    
    /**
     * YouTube Policy Compliance: User Consent Management
     */
    private fun showConsentDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Enable Library Features")
            .setMessage(
                "VibeTube can save your watch history and favorites to enhance your experience. " +
                "This data is stored locally on your device and can be deleted at any time.\n\n" +
                "• Watch history helps you resume videos\n" +
                "• Favorites let you save videos for later\n" +
                "• Custom playlists organize your content\n" +
                "• Achievements track your progress\n\n" +
                "Your data will be automatically deleted after 30 days of inactivity. " +
                "You can delete all data immediately in Settings."
            )
            .setPositiveButton("Enable") { _, _ ->
                userDataManager.setUserConsent(true)
                setupViewPager()
                Toast.makeText(this, "Library features enabled!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Not Now") { _, _ ->
                showLimitedLibraryView()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showLimitedLibraryView() {
        // Show limited functionality message
        MaterialAlertDialogBuilder(this)
            .setTitle("Limited Functionality")
            .setMessage(
                "Without enabling library features, you can still:\n\n" +
                "• Browse trending content\n" +
                "• Explore categories\n" +
                "• Share videos\n\n" +
                "Enable library features anytime in Settings to unlock:\n" +
                "• Watch history\n" +
                "• Favorites\n" +
                "• Playlists\n" +
                "• Achievements"
            )
            .setPositiveButton("Continue") { _, _ ->
                setupViewPager()
            }
            .setNegativeButton("Go Back") { _, _ ->
                finish()
            }
            .show()
    }
    
    /**
     * Helper method to convert library items to Video objects for player
     */
    fun createVideoFromHistoryItem(historyItem: com.video.vibetube.models.WatchHistoryItem): Video {
        return Video(
            videoId = historyItem.videoId,
            title = historyItem.title,
            description = "",
            thumbnail = historyItem.thumbnail,
            channelTitle = historyItem.channelTitle,
            publishedAt = "",
            duration = historyItem.duration,
            channelId = historyItem.channelId
        )
    }
    
    fun createVideoFromFavoriteItem(favoriteItem: com.video.vibetube.models.FavoriteItem): Video {
        return Video(
            videoId = favoriteItem.videoId,
            title = favoriteItem.title,
            description = "",
            thumbnail = favoriteItem.thumbnail,
            channelTitle = favoriteItem.channelTitle,
            publishedAt = "",
            duration = favoriteItem.duration,
            channelId = favoriteItem.channelId
        )
    }
    
    /**
     * Play video with resume functionality for watch history
     */
    fun playVideoWithResume(video: Video, resumePosition: Float = 0.0f) {
        val intent = Intent(this, YouTubePlayerActivity::class.java).apply {
            putParcelableArrayListExtra("VIDEOS", ArrayList(listOf(video)))
            putExtra("CURRENT_INDEX", 0)
            if (resumePosition > 0.0f) {
                putExtra("RESUME_POSITION", resumePosition)
            }
        }
        startActivity(intent)
    }
    
    /**
     * Share video using SocialManager
     */
    fun shareVideo(video: Video, customMessage: String = "") {
        socialManager.shareVideo(video, customMessage)
    }
    
    /**
     * Add video to favorites
     */
    fun addToFavorites(video: Video, category: String = "", sourceContext: String = "library") {
        lifecycleScope.launch {
            val success = userDataManager.addToFavorites(video, category, sourceContext)
            if (success) {
                Toast.makeText(this@LibraryActivity, "Added to favorites", Toast.LENGTH_SHORT).show()
                // Achievement updates are now handled automatically by UserDataManager
            } else {
                Toast.makeText(this@LibraryActivity, "Already in favorites or limit reached", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * Remove video from favorites
     */
    fun removeFromFavorites(videoId: String) {
        lifecycleScope.launch {
            userDataManager.removeFromFavorites(videoId)
            Toast.makeText(this@LibraryActivity, "Removed from favorites", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Show data management options (YouTube Policy Compliance)
     */
    fun showDataManagementDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Manage Your Data")
            .setMessage(
                "You have full control over your library data:\n\n" +
                "• All data is stored locally on your device\n" +
                "• Data is automatically deleted after 30 days\n" +
                "• You can delete all data immediately\n" +
                "• No data is shared with third parties"
            )
            .setPositiveButton("Delete All Data") { _, _ ->
                confirmDataDeletion()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun confirmDataDeletion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete All Library Data")
            .setMessage(
                "This will permanently delete:\n" +
                "• Watch history\n" +
                "• Favorites\n" +
                "• Custom playlists\n" +
                "• Achievement progress\n\n" +
                "This action cannot be undone."
            )
            .setPositiveButton("Delete") { _, _ ->
                deleteAllUserData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteAllUserData() {
        lifecycleScope.launch {
            userDataManager.deleteAllUserData()
            achievementManager.setGamificationEnabled(false)
            Toast.makeText(
                this@LibraryActivity,
                "All library data has been deleted",
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (userDataManager.hasUserConsent()) {
            // Refresh content if needed
            (viewPager.adapter as? LibraryPagerAdapter)?.notifyDataSetChanged()
        }
    }
}
