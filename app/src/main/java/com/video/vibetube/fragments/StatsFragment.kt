package com.video.vibetube.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.video.vibetube.R
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.AchievementManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * StatsFragment - Displays comprehensive user statistics and analytics
 * 
 * Features:
 * - Watch time analytics with visual charts
 * - Channel engagement statistics
 * - Category preferences breakdown
 * - Achievement progress overview
 * - Weekly/monthly activity trends
 * - Data export functionality
 * 
 * YouTube Policy Compliance:
 * - Uses only local user data
 * - No external analytics or tracking
 * - Transparent data usage
 * - User-controlled data retention
 */
class StatsFragment : Fragment() {

    // UI Components
    private lateinit var loadingIndicator: CircularProgressIndicator
    private lateinit var statsContainer: View
    private lateinit var emptyStateLayout: View
    
    // Overview Stats Cards
    private lateinit var totalWatchTimeCard: MaterialCardView
    private lateinit var totalVideosCard: MaterialCardView
    private lateinit var uniqueChannelsCard: MaterialCardView
    private lateinit var achievementsCard: MaterialCardView
    
    // Overview Stats Text Views
    private lateinit var totalWatchTimeText: TextView
    private lateinit var totalWatchTimeSubtext: TextView
    private lateinit var totalVideosText: TextView
    private lateinit var totalVideosSubtext: TextView
    private lateinit var uniqueChannelsText: TextView
    private lateinit var uniqueChannelsSubtext: TextView
    private lateinit var achievementsText: TextView
    private lateinit var achievementsSubtext: TextView
    
    // Detailed Stats
    private lateinit var avgSessionTimeText: TextView
    private lateinit var completionRateText: TextView
    private lateinit var favoriteRatioText: TextView
    private lateinit var streakDaysText: TextView
    private lateinit var firstVideoDateText: TextView
    private lateinit var lastActiveText: TextView
    
    // Category Breakdown
    private lateinit var categoryStatsRecyclerView: RecyclerView
    
    // Action Buttons
    private lateinit var exportDataButton: MaterialButton
    private lateinit var refreshStatsButton: MaterialButton
    
    // Data Managers
    private lateinit var userDataManager: UserDataManager
    private lateinit var achievementManager: AchievementManager
    
    companion object {
        fun newInstance(): StatsFragment {
            return StatsFragment()
        }
        
        private const val TAG = "StatsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeManagers()
        initializeViews(view)
        setupClickListeners()
        
        loadUserStats()
    }

    private fun initializeManagers() {
        userDataManager = UserDataManager.getInstance(requireContext())
        achievementManager = AchievementManager.getInstance(requireContext())
    }

    private fun initializeViews(view: View) {
        // Loading and container views
        loadingIndicator = view.findViewById(R.id.loadingIndicator)
        statsContainer = view.findViewById(R.id.statsContainer)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        
        // Overview stats cards
        totalWatchTimeCard = view.findViewById(R.id.totalWatchTimeCard)
        totalVideosCard = view.findViewById(R.id.totalVideosCard)
        uniqueChannelsCard = view.findViewById(R.id.uniqueChannelsCard)
        achievementsCard = view.findViewById(R.id.achievementsCard)
        
        // Overview stats text views
        totalWatchTimeText = view.findViewById(R.id.totalWatchTimeText)
        totalWatchTimeSubtext = view.findViewById(R.id.totalWatchTimeSubtext)
        totalVideosText = view.findViewById(R.id.totalVideosText)
        totalVideosSubtext = view.findViewById(R.id.totalVideosSubtext)
        uniqueChannelsText = view.findViewById(R.id.uniqueChannelsText)
        uniqueChannelsSubtext = view.findViewById(R.id.uniqueChannelsSubtext)
        achievementsText = view.findViewById(R.id.achievementsText)
        achievementsSubtext = view.findViewById(R.id.achievementsSubtext)
        
        // Detailed stats
        avgSessionTimeText = view.findViewById(R.id.avgSessionTimeText)
        completionRateText = view.findViewById(R.id.completionRateText)
        favoriteRatioText = view.findViewById(R.id.favoriteRatioText)
        streakDaysText = view.findViewById(R.id.streakDaysText)
        firstVideoDateText = view.findViewById(R.id.firstVideoDateText)
        lastActiveText = view.findViewById(R.id.lastActiveText)
        
        // Category breakdown
        categoryStatsRecyclerView = view.findViewById(R.id.categoryStatsRecyclerView)
        categoryStatsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        // Action buttons
        exportDataButton = view.findViewById(R.id.exportDataButton)
        refreshStatsButton = view.findViewById(R.id.refreshStatsButton)
    }

    private fun setupClickListeners() {
        refreshStatsButton.setOnClickListener {
            loadUserStats()
        }
        
        exportDataButton.setOnClickListener {
            exportUserData()
        }
        
        // Card click listeners for detailed views
        totalWatchTimeCard.setOnClickListener {
            showWatchTimeDetails()
        }
        
        totalVideosCard.setOnClickListener {
            showVideoDetails()
        }
        
        uniqueChannelsCard.setOnClickListener {
            showChannelDetails()
        }
        
        achievementsCard.setOnClickListener {
            showAchievementDetails()
        }
    }

    /**
     * Load and display user statistics
     * YouTube Policy Compliance: Uses only local user data
     */
    private fun loadUserStats() {
        lifecycleScope.launch {
            try {
                showLoading()

                // Get user stats and data
                val userStats = achievementManager.getUserStats()
                val watchHistory = userDataManager.getWatchHistory()
                val favorites = userDataManager.getFavorites()
                val playlists = userDataManager.getPlaylists()
                val achievements = achievementManager.getUserAchievements()

                // Always show stats, even if empty (better UX)
                // Display overview stats
                displayOverviewStats(userStats, watchHistory, favorites, achievements)

                // Display detailed stats
                displayDetailedStats(userStats, watchHistory, favorites, playlists)

                // Display category breakdown
                displayCategoryBreakdown(watchHistory, favorites)

                showStats()

            } catch (e: Exception) {
                // Log error for debugging
                android.util.Log.e("StatsFragment", "Error loading stats", e)
                showEmptyState()
            }
        }
    }

    private fun displayOverviewStats(
        userStats: AchievementManager.UserStats,
        watchHistory: List<com.video.vibetube.models.WatchHistoryItem>,
        favorites: List<com.video.vibetube.models.FavoriteItem>,
        achievements: List<AchievementManager.Achievement>
    ) {
        // Total watch time
        val watchTimeHours = userStats.totalWatchTime / (1000 * 60 * 60)
        val watchTimeMinutes = (userStats.totalWatchTime / (1000 * 60)) % 60
        totalWatchTimeText.text = if (watchTimeHours > 0 || watchTimeMinutes > 0) {
            "${watchTimeHours}h ${watchTimeMinutes}m"
        } else {
            "0m"
        }
        totalWatchTimeSubtext.text = "Total watch time"

        // Total videos
        totalVideosText.text = userStats.totalVideosWatched.toString()
        totalVideosSubtext.text = if (userStats.videosCompleted > 0) {
            "${userStats.videosCompleted} completed"
        } else {
            "No videos completed yet"
        }

        // Unique channels
        uniqueChannelsText.text = userStats.uniqueChannelsWatched.toString()
        uniqueChannelsSubtext.text = if (userStats.uniqueChannelsWatched > 0) {
            "Channels explored"
        } else {
            "Start exploring channels"
        }

        // Achievements
        val unlockedAchievements = achievements.count { it.isUnlocked }
        val totalAchievements = achievements.size
        achievementsText.text = "$unlockedAchievements/$totalAchievements"
        achievementsSubtext.text = if (unlockedAchievements > 0) {
            "Achievements unlocked"
        } else {
            "Start unlocking achievements"
        }
    }

    private fun displayDetailedStats(
        userStats: AchievementManager.UserStats,
        watchHistory: List<com.video.vibetube.models.WatchHistoryItem>,
        favorites: List<com.video.vibetube.models.FavoriteItem>,
        playlists: List<com.video.vibetube.models.UserPlaylist>
    ) {
        // Average session time
        val avgSessionMs = if (watchHistory.isNotEmpty()) {
            watchHistory.map { it.watchDuration }.average()
        } else 0.0
        val avgSessionMinutes = (avgSessionMs / (1000 * 60)).toInt()
        avgSessionTimeText.text = if (avgSessionMinutes > 0) {
            "$avgSessionMinutes minutes"
        } else {
            "No data yet"
        }
        
        // Completion rate
        val completionRate = if (userStats.totalVideosWatched > 0) {
            (userStats.videosCompleted.toFloat() / userStats.totalVideosWatched * 100).toInt()
        } else 0
        completionRateText.text = if (userStats.totalVideosWatched > 0) {
            "$completionRate%"
        } else {
            "No data yet"
        }

        // Favorite ratio
        val favoriteRatio = if (userStats.totalVideosWatched > 0) {
            (favorites.size.toFloat() / userStats.totalVideosWatched * 100).toInt()
        } else 0
        favoriteRatioText.text = if (userStats.totalVideosWatched > 0) {
            "$favoriteRatio%"
        } else {
            "No favorites yet"
        }

        // Streak days
        streakDaysText.text = if (userStats.consecutiveDaysActive > 0) {
            "${userStats.consecutiveDaysActive} days"
        } else {
            "Start your streak!"
        }
        
        // First video date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        if (userStats.firstVideoWatchedAt > 0) {
            firstVideoDateText.text = dateFormat.format(Date(userStats.firstVideoWatchedAt))
        } else {
            firstVideoDateText.text = "Watch your first video"
        }

        // Last active
        val daysSinceActive = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - userStats.lastActiveDate
        )
        lastActiveText.text = when {
            daysSinceActive == 0L -> "Active today"
            daysSinceActive == 1L -> "Active yesterday"
            daysSinceActive < 7 -> "Active $daysSinceActive days ago"
            userStats.lastActiveDate > 0 -> "Active on ${dateFormat.format(Date(userStats.lastActiveDate))}"
            else -> "Not active yet"
        }
    }

    private fun displayCategoryBreakdown(
        watchHistory: List<com.video.vibetube.models.WatchHistoryItem>,
        favorites: List<com.video.vibetube.models.FavoriteItem>
    ) {
        // Analyze category preferences from watch history and favorites
        val categoryStats = mutableMapOf<String, CategoryStat>()

        // Process watch history
        watchHistory.forEach { item ->
            val category = inferCategoryFromContent(item.title, item.channelTitle)
            val stat = categoryStats.getOrPut(category) { CategoryStat(category, 0, 0, 0L) }
            stat.videoCount++
            stat.watchTime += item.watchDuration
        }

        // Process favorites
        favorites.forEach { item ->
            val category = inferCategoryFromContent(item.title, item.channelTitle)
            val stat = categoryStats.getOrPut(category) { CategoryStat(category, 0, 0, 0L) }
            stat.favoriteCount++
        }

        // Create adapter for category stats
        val sortedStats = categoryStats.values.sortedByDescending { it.videoCount }
        // TODO: Implement CategoryStatsAdapter
        // categoryStatsRecyclerView.adapter = CategoryStatsAdapter(sortedStats)
    }

    private fun inferCategoryFromContent(title: String, channelTitle: String): String {
        val content = "$title $channelTitle".lowercase()

        return when {
            content.contains(Regex("music|song|album|artist|band|concert|lyrics")) -> "Music"
            content.contains(Regex("game|gaming|play|minecraft|fortnite|fps|rpg|stream")) -> "Gaming"
            content.contains(Regex("learn|education|tutorial|how to|explain|science|math|history")) -> "Education"
            content.contains(Regex("diy|craft|build|make|experiment|science|tech|review")) -> "DIY & Science"
            content.contains(Regex("funny|comedy|laugh|joke|humor|meme|prank")) -> "Comedy"
            content.contains(Regex("movie|film|entertainment|show|drama|series|tv")) -> "Entertainment"
            else -> "Entertainment" // Default category
        }
    }

    private fun showWatchTimeDetails() {
        // TODO: Show detailed watch time breakdown
    }

    private fun showVideoDetails() {
        // TODO: Show detailed video statistics
    }

    private fun showChannelDetails() {
        // TODO: Show channel engagement details
    }

    private fun showAchievementDetails() {
        // TODO: Navigate to achievements fragment
    }

    private fun exportUserData() {
        lifecycleScope.launch {
            try {
                val exportData = userDataManager.exportUserData()
                // TODO: Implement data export functionality
                // For now, just show a message
                android.widget.Toast.makeText(
                    requireContext(),
                    "Export functionality coming soon",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Failed to export data",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showLoading() {
        loadingIndicator.visibility = View.VISIBLE
        statsContainer.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
    }

    private fun showStats() {
        loadingIndicator.visibility = View.GONE
        statsContainer.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
    }

    private fun showEmptyState() {
        loadingIndicator.visibility = View.GONE
        statsContainer.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
    }

    /**
     * Data class for category statistics
     */
    private data class CategoryStat(
        val category: String,
        var videoCount: Int,
        var favoriteCount: Int,
        var watchTime: Long
    )
}
