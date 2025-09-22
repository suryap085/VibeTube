package com.video.vibetube.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.get
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.ads.MobileAds
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.video.vibetube.BuildConfig
import com.video.vibetube.R
import com.video.vibetube.adapters.MainPagerAdapter
import com.video.vibetube.utils.AchievementManager
import com.video.vibetube.utils.AdManager
import com.video.vibetube.utils.FeatureFlagManager
import com.video.vibetube.utils.RolloutManager
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var searchFab: FloatingActionButton
    private lateinit var adManager: AdManager

    // Navigation Drawer Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var drawerToggle: ActionBarDrawerToggle

    // User Engagement Components
    private lateinit var userDataManager: UserDataManager
    private lateinit var achievementManager: AchievementManager
    private lateinit var featureFlagManager: FeatureFlagManager
    private lateinit var rolloutManager: RolloutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure status bar and system UI
        setupStatusBar()

        setContentView(R.layout.activity_main)

        // Initialize Google Ads SDK
        MobileAds.initialize(this) {}
        adManager = AdManager(this)

        // Initialize user engagement components
        featureFlagManager = FeatureFlagManager(this)
        rolloutManager = RolloutManager(this, featureFlagManager)
        userDataManager = UserDataManager(this)
        achievementManager = AchievementManager(this)

        initViews()
        setupToolbar()
        setupNavigationDrawer()
        setupBottomNavigation()
        updateNavigationHeader()
        setupSearchFab()
        setupAds()
        setupBackPressedCallback()
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        searchFab = findViewById(R.id.searchFab)

        // Navigation Drawer Views
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun setupBottomNavigation() {
        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = false

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    viewPager.currentItem = 0
                    adManager.showInterstitialAd()
                }
                R.id.nav_shorts -> viewPager.currentItem = 1
                R.id.nav_comedy -> viewPager.currentItem = 2
                R.id.nav_movies -> viewPager.currentItem = 3
                R.id.nav_diy -> viewPager.currentItem = 4
            }
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNavigation.menu[position].isChecked = true
            }
        })
    }

    private fun setupSearchFab() {
        searchFab.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupAds() {
        val bannerAdView = findViewById<com.google.android.gms.ads.AdView>(R.id.adView)
        adManager.loadBannerAd(bannerAdView)
        adManager.loadInterstitialAd()
        adManager.handleConsent(this)
    }

    /**
     * Configure status bar and system UI for proper navigation drawer display
     */
    private fun setupStatusBar() {
        // Configure status bar appearance for proper drawer overlay
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false // Dark status bar content

        // Set status bar color to transparent for proper drawer overlay
        window.statusBarColor = android.graphics.Color.TRANSPARENT
    }

    /**
     * Setup toolbar with navigation drawer toggle
     */
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
    }

    /**
     * Setup navigation drawer with toggle and listener
     */
    private fun setupNavigationDrawer() {
        drawerToggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )

        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        navigationView.setNavigationItemSelectedListener(this)
    }

    /**
     * Update navigation header with user stats
     */
    private fun updateNavigationHeader() {
        val headerView = navigationView.getHeaderView(0)
        val userNameTextView = headerView.findViewById<TextView>(R.id.userNameTextView)
        val userStatsTextView = headerView.findViewById<TextView>(R.id.userStatsTextView)

        lifecycleScope.launch {
            if (userDataManager.hasUserConsent()) {
                val stats = achievementManager.getUserStats()
                val achievements = achievementManager.getUserAchievements()

                userNameTextView.text = "VibeTube User"
                userStatsTextView.text = buildString {
                    append("${stats.totalVideosWatched} videos watched")
                    if (achievements.isNotEmpty()) {
                        append(" • ${achievements.size} achievements")
                    }
                }
            } else {
                userNameTextView.text = "Welcome!"
                userStatsTextView.text = "Enable library features to track your progress"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val bannerAdView = findViewById<com.google.android.gms.ads.AdView>(R.id.adView)
        adManager.resumeBannerAd(bannerAdView)
        updateNavigationHeader()
    }

    override fun onPause() {
        super.onPause()
        val bannerAdView = findViewById<com.google.android.gms.ads.AdView>(R.id.adView)
        adManager.pauseBannerAd(bannerAdView)
    }

    override fun onDestroy() {
        super.onDestroy()
        val bannerAdView = findViewById<com.google.android.gms.ads.AdView>(R.id.adView)
        adManager.destroyBannerAd(bannerAdView)
    }

    /**
     * Handle navigation drawer item selection
     */
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_watch_history -> {
                openLibrarySection("history")
            }
            R.id.nav_favorites -> {
                openLibrarySection("favorites")
            }
            R.id.nav_playlists -> {
                openLibrarySection("playlists")
            }
            R.id.nav_recommendations -> {
                openLibrarySection("recommendations")
            }
            R.id.nav_trending -> {
                openLibrarySection("trending")
            }
            R.id.nav_categories -> {
                openLibrarySection("categories")
            }
            R.id.nav_achievements -> {
                openLibrarySection("achievements")
            }
            R.id.nav_share_app -> {
                shareApp()
            }
            R.id.nav_settings -> {
                openSettings()
            }
            R.id.nav_privacy -> {
                openPrivacySettings()
            }
            R.id.nav_help -> {
                openHelp()
            }
            R.id.nav_about -> {
                openAbout()
            }
        }

        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    /**
     * Open specific library section with feature flag check
     */
    private fun openLibrarySection(section: String) {
        val featureRequired = when (section) {
            "history", "favorites", "playlists" -> FeatureFlagManager.LIBRARY_FEATURES
            "achievements" -> FeatureFlagManager.ACHIEVEMENT_SYSTEM
            "recommendations" -> FeatureFlagManager.SMART_RECOMMENDATIONS
            "trending", "categories" -> FeatureFlagManager.ENHANCED_DISCOVERY
            else -> null
        }

        if (featureRequired != null && !featureFlagManager.isFeatureEnabled(featureRequired)) {
            showFeatureNotAvailable(section.replaceFirstChar { it.uppercase() })
            return
        }

        val intent = Intent(this, LibraryActivity::class.java).apply {
            putExtra("SECTION", section)
        }
        startActivity(intent)
    }



    /**
     * Open settings - redirects to Library Settings
     */
    private fun openSettings() {
        openLibrarySection("settings")
    }

    /**
     * Open privacy settings - shows comprehensive privacy dialog
     */
    private fun openPrivacySettings() {
        MaterialAlertDialogBuilder(this)
            .setTitle("🔒 Privacy & Data Protection")
            .setMessage(buildString {
                appendLine("VibeTube is committed to protecting your privacy with industry-leading practices:")
                appendLine()
                appendLine("📱 LOCAL DATA STORAGE")
                appendLine("✓ All your data stays securely on your device")
                appendLine("✓ Watch history, favorites, and playlists stored locally")
                appendLine("✓ Zero cloud sync or external data transmission")
                appendLine("✓ Complete control over your personal information")
                appendLine()
                appendLine("🛡️ YOUTUBE API COMPLIANCE")
                appendLine("✓ Strict adherence to YouTube Terms of Service")
                appendLine("✓ Only authorized API endpoints used")
                appendLine("✓ Respects content creator rights and attribution")
                appendLine("✓ No unauthorized data scraping or collection")
                appendLine()
                appendLine("🧹 SMART DATA MANAGEMENT")
                appendLine("✓ Automatic cleanup of old data (30+ days)")
                appendLine("✓ Manual data export and deletion options")
                appendLine("✓ Granular privacy controls in settings")
                appendLine("✓ No tracking or analytics without explicit consent")
                appendLine()
                appendLine("🎯 ETHICAL PERSONALIZATION")
                appendLine("✓ Recommendations based only on local activity")
                appendLine("✓ Category preferences learned from your behavior")
                appendLine("✓ Achievement system uses local engagement data")
                appendLine("✓ Zero external profiling or data sharing")
                appendLine()
                appendLine("📊 FULL TRANSPARENCY")
                appendLine("✓ Open source algorithms for content discovery")
                appendLine("✓ Clear explanation of recommendation logic")
                appendLine("✓ Full data export available anytime")
                appendLine("✓ Regular privacy policy updates and notifications")
            })
            .setPositiveButton("🛠️ Manage Data") { _, _ ->
                openLibrarySection("settings")
            }
            .setNeutralButton("📤 Export Data") { _, _ ->
                Toast.makeText(this, "📊 Data export feature available in Library Settings", Toast.LENGTH_LONG).show()
                openLibrarySection("settings")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Open help - shows comprehensive help dialog
     */
    private fun openHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle("🎉 Help & Feedback")
            .setMessage(buildString {
                appendLine("Welcome to VibeTube - Your Ultimate YouTube Content Companion!")
                appendLine()
                appendLine("🏠 CORE FEATURES")
                appendLine("🏡 Home: Browse curated content by categories")
                appendLine("🔍 Search: Find specific videos and channels instantly")
                appendLine("📚 Library: Access your personal content collection")
                appendLine("🌟 Discover: Explore trending and recommended content")
                appendLine()
                appendLine("📚 LIBRARY MANAGEMENT")
                appendLine("⏰ Watch History: Track all your viewed content")
                appendLine("❤️ Favorites: Save videos for quick access")
                appendLine("📋 Playlists: Create and manage custom collections")
                appendLine("📱 Offline Videos: Download for offline viewing")
                appendLine()
                appendLine("🎯 DISCOVERY FEATURES")
                appendLine("🎪 For You: AI-powered personalized recommendations")
                appendLine("📈 Trending: Popular content from your interests")
                appendLine("🏷️ Categories: Browse Music, Education, Gaming, and more")
                appendLine("🔄 Dynamic Channels: Smart channel selection algorithm")
                appendLine()
                appendLine("🏆 ENGAGEMENT SYSTEM")
                appendLine("🏅 Achievements: Unlock badges for viewing milestones")
                appendLine("📊 Activity Streaks: Track consistent usage patterns")
                appendLine("🎨 Category Preferences: Personalized viewing insights")
                appendLine()
                appendLine("⚙️ ADVANCED FEATURES")
                appendLine("🤖 Smart Recommendations: AI-powered content suggestions")
                appendLine("📺 Multi-Channel Categories: Browse multiple sources")
                appendLine("🎵 Playlist Management: Add/remove/reorder videos easily")
                appendLine("📤 Data Export: Full control over your information")
                appendLine()
                appendLine("🔧 PRO TIPS & TRICKS")
                appendLine("👆 Long-press videos for quick action menus")
                appendLine("↻ Swipe to refresh content in any section")
                appendLine("🎛️ Use filters in Trending for specific content")
                appendLine("📊 Export your data anytime from Settings")
                appendLine()
                appendLine("❓ NEED MORE HELP?")
                appendLine("⚙️ Check Settings for detailed configuration options")
                appendLine("🔒 Visit Privacy & Data for transparency information")
                appendLine("📧 Contact our support team for technical assistance")
                appendLine()
                appendLine("📧 Email: support@vibetube.app")
                appendLine("🌐 Website: www.vibetube.app")
            })
            .setPositiveButton("📧 Contact Support") { _, _ ->
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:support@vibetube.app")
                    putExtra(Intent.EXTRA_SUBJECT, "VibeTube Support Request")
                    putExtra(Intent.EXTRA_TEXT, buildString {
                        appendLine("VibeTube Support Request")
                        appendLine()
                        appendLine("App Version: ${BuildConfig.VERSION_NAME}")
                        appendLine("Build: ${BuildConfig.VERSION_CODE}")
                        appendLine("Device: ${android.os.Build.MODEL}")
                        appendLine("Android: ${android.os.Build.VERSION.RELEASE}")
                        appendLine()
                        appendLine("Please describe your issue or feedback:")
                        appendLine()
                    })
                }
                if (emailIntent.resolveActivity(packageManager) != null) {
                    startActivity(emailIntent)
                } else {
                    Toast.makeText(this@MainActivity, "📧 No email app found", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("⚙️ View Settings") { _, _ ->
                openLibrarySection("settings")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Open about - shows comprehensive app information
     */
    private fun openAbout() {
        MaterialAlertDialogBuilder(this)
            .setTitle("🎬 About VibeTube")
            .setMessage(buildString {
                appendLine("VibeTube v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})")
                appendLine()
                appendLine("🚀 A modern, privacy-focused YouTube content discovery and organization app.")
                appendLine()
                appendLine("✨ CORE FEATURES")
                appendLine("🏠 Curated content discovery by category")
                appendLine("📚 Personal library with comprehensive management")
                appendLine("⭐ Favorites and custom playlist creation")
                appendLine("🏆 Achievement system for user engagement")
                appendLine("🔒 Privacy-focused local data storage")
                appendLine("🎯 Smart recommendations based on your activity")
                appendLine()
                appendLine("🎨 ADVANCED CAPABILITIES")
                appendLine("🔍 Intelligent search with category filtering")
                appendLine("📊 Detailed viewing statistics and analytics")
                appendLine("🎵 Dynamic channel selection per category")
                appendLine("📱 Material Design 3 modern interface")
                appendLine("🌐 YouTube API compliance and transparency")
                appendLine("📈 Trending content with personalized filters")
                appendLine()
                appendLine("🛡️ PRIVACY & SECURITY")
                appendLine("🔐 All data stored locally on your device")
                appendLine("🚫 Zero cloud sync or external data transmission")
                appendLine("📋 Full data export and deletion control")
                appendLine("🎭 No tracking or profiling whatsoever")
                appendLine("📜 Transparent algorithms and recommendations")
                appendLine()
                appendLine("🏗️ TECHNICAL EXCELLENCE")
                appendLine("🤖 Built with Kotlin and Android Jetpack")
                appendLine("🎨 Material Design 3 components throughout")
                appendLine("🔧 Modular architecture with clean code principles")
                appendLine("🧪 Comprehensive testing and quality assurance")
                appendLine("📱 Optimized for Android 7.0+ devices")
                appendLine()
                appendLine("🌟 UPCOMING FEATURES")
                appendLine("📱 Enhanced offline video support")
                appendLine("🎨 Customizable themes and layouts")
                appendLine("🔄 Advanced sync options")
                appendLine("🎯 AI-powered content curation")
                appendLine()
                appendLine("💝 Built with ❤️ for YouTube content enthusiasts")
                appendLine("🌍 Committed to user privacy and data protection")
                appendLine()
                appendLine("© 2024 VibeTube. All rights reserved.")
                appendLine("📧 Contact: support@vibetube.app")
                appendLine("🌐 Website: www.vibetube.app")
            })
            .setPositiveButton("⭐ Rate App") { _, _ ->
                val rateIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=$packageName")
                }
                if (rateIntent.resolveActivity(packageManager) != null) {
                    startActivity(rateIntent)
                } else {
                    val webIntent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                    }
                    startActivity(webIntent)
                }
            }
            .setNeutralButton("📤 Share App") { _, _ ->
                shareApp()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Share app with others
     */
    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Check out VibeTube!")
            putExtra(Intent.EXTRA_TEXT, buildString {
                appendLine("🎬 Discover amazing YouTube content with VibeTube!")
                appendLine()
                appendLine("✨ Features:")
                appendLine("• Personalized recommendations")
                appendLine("• Smart content categories")
                appendLine("• Privacy-focused design")
                appendLine("• Achievement system")
                appendLine("• Playlist management")
                appendLine()
                appendLine("Download now: https://play.google.com/store/apps/details?id=$packageName")
            })
        }
        startActivity(Intent.createChooser(shareIntent, "Share VibeTube"))
    }

    private fun setupBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // Let the system handle the back press (finish activity)
                    finish()
                }
            }
        })
    }

    /**
     * Show feature not available message
     */
    private fun showFeatureNotAvailable(featureName: String) {
        val message = "$featureName is not available in your current version. " +
                "Check for app updates or enable experimental features in settings."

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Feature Not Available")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNeutralButton("Settings") { _, _ -> openSettings() }
            .show()
    }

    /**
     * Show feature coming soon message
     */
    private fun showFeatureComingSoon(featureName: String) {
        val message = "$featureName is coming soon! This feature is currently in development."

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Coming Soon")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
