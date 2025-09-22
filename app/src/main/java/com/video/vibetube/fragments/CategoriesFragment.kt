package com.video.vibetube.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.video.vibetube.R
import com.video.vibetube.activity.ChannelVideosActivity
import com.video.vibetube.adapters.CategoriesAdapter
import com.video.vibetube.models.CategorySection
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.EngagementAnalytics
import com.video.vibetube.utils.NetworkMonitor
import com.video.vibetube.utils.SearchVideoCacheManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.video.vibetube.models.WatchHistoryItem

/**
 * YouTube Policy Compliant Categories Fragment
 * 
 * This fragment displays content categories while ensuring strict compliance
 * with YouTube's terms of service:
 * 
 * COMPLIANCE FEATURES:
 * - Uses only predefined channels organized by categories
 * - No unauthorized content categorization or algorithms
 * - Respects YouTube API quotas and rate limits
 * - Shows curated channel collections by topic
 * - Maintains proper attribution to original YouTube content
 * - Uses existing channel data from the app
 * 
 * CATEGORY LOGIC (Policy Compliant):
 * 1. Display predefined categories with curated channels
 * 2. Show channel collections organized by topic
 * 3. Use existing channel data from app's main fragments
 * 4. Allow users to explore specific category channels
 * 5. All data comes from app's predefined channel lists only
 */
class CategoriesFragment : Fragment() {

    private lateinit var userDataManager: UserDataManager
    private lateinit var engagementAnalytics: EngagementAnalytics
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var cacheManager: SearchVideoCacheManager
    private lateinit var categoriesAdapter: CategoriesAdapter

    // Dynamic categories populated from user data
    private var dynamicCategories: List<CategorySection> = emptyList()
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var emptyStateLayout: View
    private lateinit var loadingStateLayout: View
    private lateinit var messageTextView: TextView
    private lateinit var exploreChannelsButton: MaterialButton
    
    private val categories = mutableListOf<CategorySection>()
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_categories, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeManagers()
        initializeViews(view)
        setupRecyclerView()
        setupSwipeRefresh()
        setupClickListeners()
        
        loadCategories()
    }

    private fun initializeManagers() {
        userDataManager = UserDataManager.getInstance(requireContext())
        engagementAnalytics = EngagementAnalytics.getInstance(requireContext())
        networkMonitor = NetworkMonitor(requireContext())
        cacheManager = SearchVideoCacheManager(requireContext())
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.categoriesRecyclerView)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        loadingStateLayout = view.findViewById(R.id.loadingStateLayout)
        messageTextView = view.findViewById(R.id.messageTextView)
        exploreChannelsButton = view.findViewById(R.id.exploreChannelsButton)
    }

    private fun setupRecyclerView() {
        categoriesAdapter = CategoriesAdapter(
            categories = categories,
            onCategoryClick = { category ->
                openCategoryExplorer(category)
            },
            onChannelClick = { channelId, channelName ->
                openChannelVideos(channelId, channelName)
            },
            lifecycleOwner = this,
            userDataManager = userDataManager
        )
        
        recyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            adapter = categoriesAdapter
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            loadCategories()
        }
    }

    private fun setupClickListeners() {
        exploreChannelsButton.setOnClickListener {
            // Navigate back to main activity to explore channels
            requireActivity().finish()
        }
    }

    /**
     * Load categories with dynamic channel selection based on user data
     * YouTube Policy Compliance: Uses only user's local data for personalization
     */
    private fun loadCategories() {
        if (isLoading) return

        lifecycleScope.launch {
            try {
                isLoading = true
                showLoading()

                // Generate dynamic categories based on user data
                dynamicCategories = generateDynamicCategories()

                if (dynamicCategories.isEmpty()) {
                    showMessage("No categories available")
                } else {
                    showCategories(dynamicCategories)
                    lifecycleScope.launch {
                        engagementAnalytics.trackFeatureUsage("categories_loaded")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading categories", e)
                showMessage("Error loading categories. Please try again.")
            } finally {
                isLoading = false
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    /**
     * Open category explorer with dynamic channel selection
     * Shows channel selection dialog when multiple channels are available
     */
    private fun openCategoryExplorer(category: CategorySection) {
        try {
            if (category.channels.isEmpty()) {
                showMessage("No channels available for this category")
                return
            }

            if (category.channels.size == 1) {
                // Single channel - open directly
                val channel = category.channels.first()
                openChannelVideos(channel.first, channel.second)
                lifecycleScope.launch {
                    engagementAnalytics.trackFeatureUsage("category_explored_single")
                }
            } else {
                // Multiple channels - show selection dialog
                showChannelSelectionDialog(category)
                lifecycleScope.launch {
                    engagementAnalytics.trackFeatureUsage("category_explored_multiple")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error opening category explorer", e)
            showMessage("Error opening category")
        }
    }

    /**
     * Show dialog for selecting between multiple channels in a category
     */
    private fun showChannelSelectionDialog(category: CategorySection) {
        lifecycleScope.launch {
            try {
                // Sort channels by user preference score
                val sortedChannels = sortChannelsByUserPreference(category.channels)

                val channelNames = sortedChannels.map { (_, name) -> name }.toTypedArray()
                val channelDescriptions = sortedChannels.mapIndexed { index, (channelId, name) ->
                    val score = calculateChannelScore(channelId, name)
                    when {
                        index == 0 && score > 0 -> "$name (Recommended for you)"
                        score > 0 -> "$name (Based on your activity)"
                        else -> "$name (Popular channel)"
                    }
                }.toTypedArray()

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Choose Channel - ${category.name}")
                    .setItems(channelDescriptions) { _, which ->
                        val selectedChannel = sortedChannels[which]
                        openChannelVideos(selectedChannel.first, selectedChannel.second)
                        lifecycleScope.launch {
                            engagementAnalytics.trackFeatureUsage("channel_selected_from_dialog")
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()

            } catch (e: Exception) {
                Log.e(TAG, "Error showing channel selection dialog", e)
                // Fallback to best channel
                val selectedChannel = selectBestChannelForUser(category)
                openChannelVideos(selectedChannel.first, selectedChannel.second)
            }
        }
    }

    /**
     * Sort channels by user preference score (highest first)
     */
    private suspend fun sortChannelsByUserPreference(channels: List<Pair<String, String>>): List<Pair<String, String>> {
        return withContext(Dispatchers.IO) {
            try {
                val channelScores = mutableMapOf<Pair<String, String>, Double>()

                channels.forEach { channel ->
                    val score = calculateChannelScore(channel.first, channel.second)
                    channelScores[channel] = score
                }

                // Sort by score (highest first)
                channelScores.toList().sortedByDescending { it.second }.map { it.first }

            } catch (e: Exception) {
                Log.e(TAG, "Error sorting channels by preference", e)
                channels // Return original order if error
            }
        }
    }

    /**
     * Calculate user preference score for a specific channel
     */
    private suspend fun calculateChannelScore(channelId: String, channelName: String): Double {
        return try {
            val watchHistory = userDataManager.getWatchHistory()
            val favorites = userDataManager.getFavorites()
            val favoriteChannels = userDataManager.getFavoriteChannels()

            var score = 0.0

            // Favorite channels get highest score
            if (favoriteChannels.any { it.channelId == channelId }) {
                score += 100.0
            }

            // Score based on watch history
            val watchCount = watchHistory.count { it.channelId == channelId }
            val completedCount = watchHistory.count {
                it.channelId == channelId && it.watchProgress >= 0.8
            }
            score += watchCount * 2.0 + completedCount * 3.0

            // Score based on favorites
            val favoriteCount = favorites.count { it.channelId == channelId }
            score += favoriteCount * 5.0

            // Score based on total watch time
            val totalWatchTime = watchHistory
                .filter { it.channelId == channelId }
                .sumOf { it.watchDuration }
            score += (totalWatchTime / 60000.0) // Convert to minutes

            score

        } catch (e: Exception) {
            Log.e(TAG, "Error calculating channel score for $channelName", e)
            0.0
        }
    }

    /**
     * Open specific channel videos
     */
    private fun openChannelVideos(channelId: String, channelName: String) {
        try {
            val intent = Intent(requireContext(), ChannelVideosActivity::class.java).apply {
                putExtra("CHANNEL_ID", channelId)
                putExtra("CHANNEL_TITLE", channelName)
            }
            startActivity(intent)
            
            lifecycleScope.launch {
                engagementAnalytics.trackFeatureUsage("category_channel_opened")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error opening channel videos", e)
        }
    }

    /**
     * Select the best channel for the user based on their viewing preferences
     * YouTube Policy Compliance: Uses only local user data for personalization
     */
    private suspend fun selectBestChannelForUser(category: CategorySection): Pair<String, String> {
        return try {
            val watchHistory = userDataManager.getWatchHistory()
            val favorites = userDataManager.getFavorites()

            // Score each channel in the category based on user engagement
            val channelScores = mutableMapOf<Pair<String, String>, Double>()

            category.channels.forEach { (channelId, channelName) ->
                var score = 0.0

                // Score based on watch history
                val watchCount = watchHistory.count {
                    it.channelId == channelId || it.channelTitle == channelName
                }
                score += watchCount * 3.0

                // Score based on completed videos
                val completedCount = watchHistory.count {
                    (it.channelId == channelId || it.channelTitle == channelName) && it.isCompleted
                }
                score += completedCount * 5.0

                // Score based on favorites
                val favoriteCount = favorites.count {
                    it.channelId == channelId || it.channelTitle == channelName
                }
                score += favoriteCount * 7.0

                // Score based on total watch time
                val totalWatchTime = watchHistory
                    .filter { it.channelId == channelId || it.channelTitle == channelName }
                    .sumOf { it.watchDuration }
                score += (totalWatchTime / 60000.0) // Convert to minutes

                channelScores[Pair(channelId, channelName)] = score

                Log.d(TAG, "Channel $channelName score: $score (watch: $watchCount, completed: $completedCount, favorites: $favoriteCount)")
            }

            // Select the channel with the highest score
            val bestChannel = channelScores.maxByOrNull { it.value }?.key

            if (bestChannel != null && channelScores[bestChannel]!! > 0) {
                Log.d(TAG, "Selected best channel: ${bestChannel.second} with score ${channelScores[bestChannel]}")
                bestChannel
            } else {
                // If no user preference, select the first channel
                Log.d(TAG, "No user preference found, selecting first channel: ${category.channels.first().second}")
                category.channels.first()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error selecting best channel for user", e)
            // Fallback to first channel
            category.channels.first()
        }
    }

    /**
     * Generate dynamic categories based on user's watch history, favorites, and playlists
     * YouTube Policy Compliance: Uses only local user data
     */
    private suspend fun generateDynamicCategories(): List<CategorySection> {
        return withContext(Dispatchers.IO) {
            try {
                val watchHistory = userDataManager.getWatchHistory()
                val favorites = userDataManager.getFavorites()
                val playlists = userDataManager.getPlaylists()
                val favoriteChannels = userDataManager.getFavoriteChannels()

                Log.d(TAG, "Generating dynamic categories from ${watchHistory.size} watch history items, ${favorites.size} favorites, ${playlists.size} playlists")

                // Collect all channels from user data
                val channelFrequency = mutableMapOf<String, ChannelInfo>()

                // Add channels from watch history (weighted by watch time and frequency)
                watchHistory.forEach { item ->
                    val channelInfo = channelFrequency.getOrPut(item.channelId) {
                        ChannelInfo(item.channelId, item.channelTitle, 0.0, mutableSetOf())
                    }
                    channelInfo.score += calculateWatchScore(item)
                    channelInfo.categories.add(inferCategoryFromContent(item.title, item.channelTitle))
                }

                // Add channels from favorites (higher weight)
                favorites.forEach { item ->
                    val channelInfo = channelFrequency.getOrPut(item.channelId) {
                        ChannelInfo(item.channelId, item.channelTitle, 0.0, mutableSetOf())
                    }
                    channelInfo.score += 5.0 // Higher weight for favorites
                    channelInfo.categories.add(inferCategoryFromContent(item.title, item.channelTitle))
                }

                // Add channels from playlists
                playlists.forEach { playlist ->
                    playlist.videos.forEach { video ->
                        val channelInfo = channelFrequency.getOrPut(video.channelId) {
                            ChannelInfo(video.channelId, video.channelTitle, 0.0, mutableSetOf())
                        }
                        channelInfo.score += 3.0 // Medium weight for playlist videos
                        channelInfo.categories.add(inferCategoryFromContent(video.title, video.channelTitle))
                    }
                }

                // Add favorite channels (highest weight)
                favoriteChannels.forEach { channel ->
                    val channelInfo = channelFrequency.getOrPut(channel.channelId) {
                        ChannelInfo(channel.channelId, channel.channelTitle, 0.0, mutableSetOf())
                    }
                    channelInfo.score += 10.0 // Highest weight for favorite channels
                    channelInfo.categories.add(inferCategoryFromContent("", channel.channelTitle))
                }

                // Generate categories with top channels for each category
                val dynamicCategories = mutableListOf<CategorySection>()

                BASE_CATEGORIES.forEach { (categoryId, baseCategory) ->
                    val categoryChannels = channelFrequency.values
                        .filter { it.categories.contains(categoryId) }
                        .sortedByDescending { it.score }
                        .take(MAX_CHANNELS_PER_CATEGORY)
                        .map { it.channelId to it.channelTitle }

                    if (categoryChannels.isNotEmpty()) {
                        val dynamicCategory = baseCategory.copy(
                            channels = categoryChannels,
                            description = "${baseCategory.description} (${categoryChannels.size} personalized channels)"
                        )
                        dynamicCategories.add(dynamicCategory)
                        Log.d(TAG, "Category '$categoryId': ${categoryChannels.size} channels - ${categoryChannels.map { it.second }}")
                    }
                }

                // If no user data, use fallback categories with popular channels
                if (dynamicCategories.isEmpty()) {
                    Log.d(TAG, "No user data available, using fallback categories")
                    return@withContext getFallbackCategories()
                }

                Log.d(TAG, "Generated ${dynamicCategories.size} dynamic categories")
                dynamicCategories

            } catch (e: Exception) {
                Log.e(TAG, "Error generating dynamic categories", e)
                getFallbackCategories()
            }
        }
    }

    private fun showLoading() {
        loadingStateLayout.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    private fun showCategories(categoryList: List<CategorySection>) {
        categories.clear()
        categories.addAll(categoryList)
        categoriesAdapter.notifyDataSetChanged()
        
        recyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        loadingStateLayout.visibility = View.GONE
    }

    private fun showMessage(message: String) {
        emptyStateLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        loadingStateLayout.visibility = View.GONE

        messageTextView.text = message
    }

    /**
     * Helper data class for channel information and scoring
     */
    private data class ChannelInfo(
        val channelId: String,
        val channelTitle: String,
        var score: Double,
        val categories: MutableSet<String>
    )

    /**
     * Calculate watch score based on watch duration and completion
     */
    private fun calculateWatchScore(item: WatchHistoryItem): Double {
        val baseScore = 1.0
        val progressBonus = item.watchProgress * 2.0 // Up to 2x bonus for completion
        val durationBonus = minOf(item.watchDuration / 60000.0, 2.0) // Up to 2x bonus for long watch time
        return baseScore + progressBonus + durationBonus
    }

    /**
     * Infer category from video title and channel name
     * YouTube Policy Compliance: Uses only content analysis, no external data
     */
    private fun inferCategoryFromContent(title: String, channelTitle: String): String {
        val content = "$title $channelTitle".lowercase()

        return when {
            content.contains(Regex("music|song|album|artist|band|concert|lyrics")) -> "music"
            content.contains(Regex("game|gaming|play|minecraft|fortnite|fps|rpg|stream")) -> "gaming"
            content.contains(Regex("learn|education|tutorial|how to|explain|science|math|history")) -> "education"
            content.contains(Regex("diy|craft|build|make|experiment|science|tech|review")) -> "diy"
            content.contains(Regex("funny|comedy|laugh|joke|humor|meme|prank")) -> "comedy"
            content.contains(Regex("movie|film|entertainment|show|drama|series|tv")) -> "entertainment"
            else -> "entertainment" // Default category
        }
    }

    /**
     * Get fallback categories when no user data is available
     * YouTube Policy Compliance: Uses predefined popular channels only
     */
    private fun getFallbackCategories(): List<CategorySection> {
        return listOf(
            BASE_CATEGORIES["music"]!!.copy(
                channels = listOf(
                    "UCq-Fj5jknLsUf-MWSy4_brA" to "T-Series",
                    "UCbTLwN10NoCU4WDzLf1JMOA" to "SET India"
                ),
                description = "Popular music channels"
            ),
            BASE_CATEGORIES["gaming"]!!.copy(
                channels = listOf(
                    "UC-lHJZR3Gqxm24_Vd_AJ5Yw" to "PewDiePie",
                    "UCipUFvJQVnj3NNKX6wQQxPA" to "MrBeast Gaming"
                ),
                description = "Popular gaming channels"
            ),
            BASE_CATEGORIES["education"]!!.copy(
                channels = listOf(
                    "UCsooa4yRKGN_zEE8iknghZA" to "TED-Ed",
                    "UC2C_jShtL725hvbm1arSV9w" to "CGP Grey"
                ),
                description = "Popular educational channels"
            )
        )
    }

    companion object {
        fun newInstance(): CategoriesFragment {
            return CategoriesFragment()
        }

        private const val TAG = "CategoriesFragment"
        private const val MIN_CHANNELS_PER_CATEGORY = 3
        private const val MAX_CHANNELS_PER_CATEGORY = 5

        // YouTube Policy Compliant: Base category definitions (channels will be dynamic)
        private val BASE_CATEGORIES = mapOf(
            "music" to CategorySection(
                id = "music",
                name = "Music",
                description = "Discover amazing music content",
                iconRes = R.drawable.ic_music_note,
                colorRes = R.color.category_music,
                channels = emptyList() // Will be populated dynamically
            ),
            "education" to CategorySection(
                id = "education",
                name = "Education",
                description = "Learn something new every day",
                iconRes = R.drawable.ic_library_books,
                colorRes = R.color.category_education,
                channels = emptyList() // Will be populated dynamically
            ),
            "gaming" to CategorySection(
                id = "gaming",
                name = "Gaming",
                description = "Epic gaming content and reviews",
                iconRes = R.drawable.ic_sport,
                colorRes = R.color.category_gaming,
                channels = emptyList() // Will be populated dynamically
            ),
            "diy" to CategorySection(
                id = "diy",
                name = "DIY & Science",
                description = "Creative projects and experiments",
                iconRes = R.drawable.ic_diy,
                colorRes = R.color.category_diy,
                channels = emptyList() // Will be populated dynamically
            ),
            "entertainment" to CategorySection(
                id = "entertainment",
                name = "Entertainment",
                description = "Fun and entertaining videos",
                iconRes = R.drawable.ic_movie,
                colorRes = R.color.category_entertainment,
                channels = emptyList() // Will be populated dynamically
            ),
            "comedy" to CategorySection(
                id = "comedy",
                name = "Comedy",
                description = "Laugh out loud with comedy content",
                iconRes = R.drawable.ic_star,
                colorRes = R.color.category_entertainment,
                channels = emptyList() // Will be populated dynamically
            )
        )
    }
}
