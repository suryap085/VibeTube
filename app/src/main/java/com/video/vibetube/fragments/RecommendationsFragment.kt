package com.video.vibetube.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.video.vibetube.R
import com.video.vibetube.activity.YouTubePlayerActivity
import com.video.vibetube.adapters.RecommendedVideosAdapter
import com.video.vibetube.models.Video
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.EngagementAnalytics
import com.video.vibetube.utils.NetworkMonitor
import com.video.vibetube.utils.SearchVideoCacheManager
import com.video.vibetube.integration.VibeTubeEnhancementIntegrator
import kotlinx.coroutines.launch

/**
 * YouTube Policy Compliant Recommendations Fragment
 * 
 * This fragment provides personalized content recommendations while ensuring
 * strict compliance with YouTube's terms of service:
 * 
 * COMPLIANCE FEATURES:
 * - Uses only LOCAL user data for recommendations (no external profiling)
 * - Respects user consent and privacy preferences
 * - No unauthorized data collection or sharing
 * - Recommendations based on user's own watch history only
 * - No cross-user data analysis or behavioral tracking
 * - Transparent recommendation logic
 * - User can opt-out of personalized recommendations
 * 
 * RECOMMENDATION LOGIC (Policy Compliant):
 * 1. Analyze user's local watch history for patterns
 * 2. Identify frequently watched channels
 * 3. Suggest recent videos from those channels
 * 4. Include videos from similar categories user has watched
 * 5. Respect user's completion rate preferences
 * 6. All data processing happens locally on device
 */
class RecommendationsFragment : Fragment() {

    private lateinit var userDataManager: UserDataManager
    private lateinit var engagementAnalytics: EngagementAnalytics
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var cacheManager: SearchVideoCacheManager
    private lateinit var recommendedVideosAdapter: RecommendedVideosAdapter
    private lateinit var enhancementIntegrator: VibeTubeEnhancementIntegrator
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var emptyStateLayout: View
    private lateinit var loadingStateLayout: View
    private lateinit var messageTextView: TextView
    private lateinit var enableRecommendationsButton: MaterialButton
    private lateinit var exploreChannelsButton: MaterialButton
    
    private val recommendedVideos = mutableListOf<Video>()
    private var isLoading = false

    companion object {
        fun newInstance(): RecommendationsFragment {
            return RecommendationsFragment()
        }
        
        private const val TAG = "RecommendationsFragment"
        private const val MIN_WATCH_HISTORY_FOR_RECOMMENDATIONS = 5
        private const val MAX_RECOMMENDATIONS = 50
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recommendations, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeManagers()
        initializeViews(view)
        setupRecyclerView()
        setupSwipeRefresh()
        setupClickListeners()
        
        checkUserConsentAndLoadRecommendations()
    }

    private fun initializeManagers() {
        userDataManager = UserDataManager.getInstance(requireContext())
        engagementAnalytics = EngagementAnalytics.getInstance(requireContext())
        networkMonitor = NetworkMonitor(requireContext())
        cacheManager = SearchVideoCacheManager(requireContext())
        enhancementIntegrator = VibeTubeEnhancementIntegrator.getInstance(requireContext())
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.recommendationsRecyclerView)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        loadingStateLayout = view.findViewById(R.id.loadingStateLayout)
        messageTextView = view.findViewById(R.id.messageTextView)
        enableRecommendationsButton = view.findViewById(R.id.enableRecommendationsButton)
        exploreChannelsButton = view.findViewById(R.id.exploreChannelsButton)
    }

    private fun setupRecyclerView() {
        recommendedVideosAdapter = RecommendedVideosAdapter(
            videos = recommendedVideos,
            onVideoClick = { video ->
                openVideoPlayer(video)
            },
            lifecycleOwner = this,
            userDataManager = userDataManager
        )
        
        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recommendedVideosAdapter
        }
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            if (!networkMonitor.isConnected()) {
                showMessage("No internet connection. Please check your connection.")
                swipeRefreshLayout.isRefreshing = false
            } else {
                loadRecommendations()
            }
        }
    }

    private fun setupClickListeners() {
        enableRecommendationsButton.setOnClickListener {
            showEnableRecommendationsDialog()
        }
        
        exploreChannelsButton.setOnClickListener {
            // Navigate back to main activity to explore channels
            requireActivity().finish()
        }
    }

    /**
     * Check user consent and load recommendations accordingly
     * YouTube Policy Compliance: Requires explicit user consent
     */
    private fun checkUserConsentAndLoadRecommendations() {
        lifecycleScope.launch {
            try {
                if (!userDataManager.hasUserConsent()) {
                    showConsentRequired()
                    return@launch
                }
                
                val watchHistory = userDataManager.getWatchHistory()
                if (watchHistory.size < MIN_WATCH_HISTORY_FOR_RECOMMENDATIONS) {
                    showInsufficientData()
                    return@launch
                }
                
                loadRecommendations()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error checking user consent", e)
                showMessage("Error loading recommendations. Please try again.")
            }
        }
    }

    /**
     * Load personalized recommendations based on local user data only
     * YouTube Policy Compliance: Uses only local data, no external profiling
     */
    private fun loadRecommendations() {
        if (isLoading) return
        
        lifecycleScope.launch {
            try {
                isLoading = true
                showLoading()
                
                if (!networkMonitor.isConnected()) {
                    showMessage("No internet connection. Please check your connection.")
                    return@launch
                }
                
                // Generate enhanced recommendations using ML and contextual discovery
                val recommendations = generateEnhancedRecommendations()

                Log.d(TAG, "Generated ${recommendations.size} recommendations")

                if (recommendations.isEmpty()) {
                    Log.d(TAG, "No recommendations generated, showing insufficient data message")
                    showInsufficientData()
                } else {
                    Log.d(TAG, "Showing ${recommendations.size} recommendations")
                    showRecommendations(recommendations)
                    engagementAnalytics.trackFeatureUsage("enhanced_recommendations_loaded")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recommendations", e)
                showMessage("Error loading recommendations. Please try again.")
            } finally {
                isLoading = false
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    /**
     * Generate recommendations using only local user data
     * YouTube Policy Compliance: No external data sources or cross-user analysis
     */
    private suspend fun generateLocalRecommendations(): List<Video> {
        try {
            val watchHistory = userDataManager.getWatchHistory()
            val favoriteVideos = userDataManager.getFavorites()
            
            // Analyze user preferences from local data only
            val preferredChannels = analyzePreferredChannels(watchHistory)
            val favoriteVideosList = favoriteVideos.map { favorite ->
            Video(
                videoId = favorite.videoId,
                title = favorite.title,
                description = "",
                thumbnail = favorite.thumbnail,
                channelTitle = favorite.channelTitle,
                publishedAt = "",
                duration = favorite.duration,
                categoryId = "",
                channelId = favorite.channelId
            )
        }
        val preferredCategories = analyzePreferredCategories(watchHistory, favoriteVideosList)
            val preferredDurations = analyzePreferredDurations(watchHistory)
            
            // Generate recommendations based on local analysis
            val recommendations = mutableListOf<Video>()
            
            // Add videos from preferred channels (if available in cache)
            recommendations.addAll(getVideosFromPreferredChannels(preferredChannels))
            
            // Add videos from preferred categories
            recommendations.addAll(getVideosFromPreferredCategories(preferredCategories))

            // If we have very few recommendations, add some fallback content
            if (recommendations.size < 5) {
                recommendations.addAll(getFallbackRecommendations())
            }

            // Remove duplicates and already watched videos
            val watchedVideoIds = watchHistory.map { it.videoId }.toSet()
            val uniqueRecommendations = recommendations
                .distinctBy { it.videoId }
                .filter { it.videoId !in watchedVideoIds }
                .take(MAX_RECOMMENDATIONS)
            
            Log.d(TAG, "Generated ${uniqueRecommendations.size} recommendations from local data")
            return uniqueRecommendations
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating local recommendations", e)
            return emptyList()
        }
    }

    /**
     * Analyze user's preferred channels from watch history
     * YouTube Policy Compliance: Uses only local watch history data
     */
    private fun analyzePreferredChannels(watchHistory: List<com.video.vibetube.models.WatchHistoryItem>): List<String> {
        return watchHistory
            .groupBy { it.channelId }
            .mapValues { (_, videos) -> 
                videos.sumOf { if (it.isCompleted) 2.0 else it.watchProgress.toDouble() }
            }
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .map { it.first }
    }

    /**
     * Analyze user's preferred categories from watch history and favorites
     * YouTube Policy Compliance: Uses only local user data
     */
    private fun analyzePreferredCategories(
        watchHistory: List<com.video.vibetube.models.WatchHistoryItem>,
        favoriteVideos: List<Video>
    ): List<String> {
        val categoryScores = mutableMapOf<String, Double>()
        
        // Score from watch history
        watchHistory.forEach { item ->
            val category = item.channelTitle
            val score = if (item.isCompleted) 2.0 else item.watchProgress.toDouble()
            categoryScores[category] = (categoryScores[category] ?: 0.0) + score
        }

        // Boost score from favorites
        favoriteVideos.forEach { video ->
            val category = video.channelTitle
            categoryScores[category] = (categoryScores[category] ?: 0.0) + 3.0
        }
        
        return categoryScores.toList()
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }

    /**
     * Analyze user's preferred video durations
     * YouTube Policy Compliance: Uses only local watch history data
     */
    private fun analyzePreferredDurations(watchHistory: List<com.video.vibetube.models.WatchHistoryItem>): Pair<Int, Int> {
        val completedVideos = watchHistory.filter { it.isCompleted }
        if (completedVideos.isEmpty()) return Pair(0, Int.MAX_VALUE)
        
        val durations = completedVideos.map { com.video.vibetube.utils.Utility.parseAnyDurationToSeconds(it.duration).toInt() }.sorted()
        val median = durations[durations.size / 2]

        return Pair(median / 2, median * 2) // Prefer videos within 50%-200% of median duration
    }

    /**
     * Get videos from preferred channels (using cached data only)
     * YouTube Policy Compliance: Uses only locally cached data
     */
    private suspend fun getVideosFromPreferredChannels(preferredChannels: List<String>): List<Video> {
        return try {
            val recommendedVideos = mutableListOf<Video>()

            // Get videos from each preferred channel using cache
            preferredChannels.take(5).forEach { channelTitle ->
                // Try to find cached videos for this channel
                val cachedVideos = findCachedVideosForChannel(channelTitle)
                if (cachedVideos.isNotEmpty()) {
                    // Add up to 3 videos from each preferred channel
                    recommendedVideos.addAll(cachedVideos.take(3))
                }
            }

            Log.d(TAG, "Found ${recommendedVideos.size} videos from preferred channels")
            recommendedVideos.distinctBy { it.videoId }.take(10)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting videos from preferred channels", e)
            emptyList()
        }
    }

    /**
     * Get videos from preferred categories (using cached data only)
     * YouTube Policy Compliance: Uses only locally cached data
     */
    private suspend fun getVideosFromPreferredCategories(preferredCategories: List<String>): List<Video> {
        return try {
            val categoryVideos = mutableListOf<Video>()

            // Get videos from watch history and favorites that match preferred categories
            val watchHistory = userDataManager.getWatchHistory()
            val favorites = userDataManager.getFavorites()

            // Convert favorites to videos for easier processing
            val favoriteVideos = favorites.map { favorite ->
                Video(
                    videoId = favorite.videoId,
                    title = favorite.title,
                    description = "",
                    thumbnail = favorite.thumbnail,
                    channelTitle = favorite.channelTitle,
                    publishedAt = "",
                    duration = favorite.duration,
                    categoryId = "",
                    channelId = favorite.channelId
                )
            }

            // Add favorite videos from preferred categories (channels)
            preferredCategories.forEach { categoryChannel ->
                val matchingFavorites = favoriteVideos.filter { it.channelTitle == categoryChannel }
                categoryVideos.addAll(matchingFavorites.take(2))
            }

            Log.d(TAG, "Found ${categoryVideos.size} videos from preferred categories")
            categoryVideos.distinctBy { it.videoId }.take(8)

        } catch (e: Exception) {
            Log.e(TAG, "Error getting videos from preferred categories", e)
            emptyList()
        }
    }

    /**
     * Find cached videos for a specific channel
     * Uses various cache sources to find videos from the channel
     */
    private suspend fun findCachedVideosForChannel(channelTitle: String): List<Video> {
        return try {
            val cachedVideos = mutableListOf<Video>()

            // Check if we have any cached videos from this channel in our search cache
            // This is a simplified approach - in a real implementation, you'd have
            // a more sophisticated channel-to-video mapping

            // For now, we'll use the user's watch history and favorites as a source
            val watchHistory = userDataManager.getWatchHistory()
            val favorites = userDataManager.getFavorites()

            // Get videos from watch history for this channel
            val historyVideos = watchHistory
                .filter { it.channelTitle == channelTitle }
                .map { historyItem ->
                    Video(
                        videoId = historyItem.videoId,
                        title = historyItem.title,
                        description = "",
                        thumbnail = historyItem.thumbnail,
                        channelTitle = historyItem.channelTitle,
                        publishedAt = "",
                        duration = historyItem.duration,
                        categoryId = "",
                        channelId = historyItem.channelId
                    )
                }

            // Get videos from favorites for this channel
            val favoriteVideos = favorites
                .filter { it.channelTitle == channelTitle }
                .map { favorite ->
                    Video(
                        videoId = favorite.videoId,
                        title = favorite.title,
                        description = "",
                        thumbnail = favorite.thumbnail,
                        channelTitle = favorite.channelTitle,
                        publishedAt = "",
                        duration = favorite.duration,
                        categoryId = "",
                        channelId = favorite.channelId
                    )
                }

            cachedVideos.addAll(historyVideos)
            cachedVideos.addAll(favoriteVideos)

            // Return unique videos
            cachedVideos.distinctBy { it.videoId }

        } catch (e: Exception) {
            Log.e(TAG, "Error finding cached videos for channel $channelTitle", e)
            emptyList()
        }
    }

    /**
     * Get fallback recommendations when user data is insufficient
     * Uses the user's most recent favorites and watch history
     */
    private suspend fun getFallbackRecommendations(): List<Video> {
        return try {
            val fallbackVideos = mutableListOf<Video>()

            // Get user's most recent favorites as fallback
            val favorites = userDataManager.getFavorites()
            if (favorites.isNotEmpty()) {
                val recentFavorites = favorites
                    .sortedByDescending { it.addedAt }
                    .take(5)
                    .map { favorite ->
                        Video(
                            videoId = favorite.videoId,
                            title = favorite.title,
                            description = "",
                            thumbnail = favorite.thumbnail,
                            channelTitle = favorite.channelTitle,
                            publishedAt = "",
                            duration = favorite.duration,
                            categoryId = "",
                            channelId = favorite.channelId
                        )
                    }
                fallbackVideos.addAll(recentFavorites)
            }

            // Get user's most recent completed videos as fallback
            val watchHistory = userDataManager.getWatchHistory()
            if (watchHistory.isNotEmpty()) {
                val recentCompleted = watchHistory
                    .filter { it.isCompleted }
                    .sortedByDescending { it.watchedAt }
                    .take(3)
                    .map { historyItem ->
                        Video(
                            videoId = historyItem.videoId,
                            title = historyItem.title,
                            description = "",
                            thumbnail = historyItem.thumbnail,
                            channelTitle = historyItem.channelTitle,
                            publishedAt = "",
                            duration = historyItem.duration,
                            categoryId = "",
                            channelId = historyItem.channelId
                        )
                    }
                fallbackVideos.addAll(recentCompleted)
            }

            Log.d(TAG, "Generated ${fallbackVideos.size} fallback recommendations")
            fallbackVideos.distinctBy { it.videoId }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating fallback recommendations", e)
            emptyList()
        }
    }

    private fun openVideoPlayer(video: Video) {
        val intent = Intent(requireContext(), YouTubePlayerActivity::class.java).apply {
            putParcelableArrayListExtra("VIDEOS", ArrayList(listOf(video)))
            putExtra("CURRENT_INDEX", 0)
        }
        startActivity(intent)
    }

    private fun showConsentRequired() {
        emptyStateLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        loadingStateLayout.visibility = View.GONE
        
        messageTextView.text = "Enable personalized recommendations to see content tailored for you"
        enableRecommendationsButton.visibility = View.VISIBLE
        exploreChannelsButton.visibility = View.VISIBLE
    }

    private fun showInsufficientData() {
        emptyStateLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        loadingStateLayout.visibility = View.GONE
        
        messageTextView.text = "Watch more videos to get personalized recommendations"
        enableRecommendationsButton.visibility = View.GONE
        exploreChannelsButton.visibility = View.VISIBLE
    }

    private fun showLoading() {
        loadingStateLayout.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        recyclerView.visibility = View.GONE
    }

    private fun showRecommendations(recommendations: List<Video>) {
        Log.d(TAG, "Showing ${recommendations.size} recommendations")

        // Update UI state first
        recyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        loadingStateLayout.visibility = View.GONE

        recommendedVideos.clear()
        recommendedVideos.addAll(recommendations)
        // Use updateVideos method to properly handle ads insertion
        recommendedVideosAdapter.updateVideos(recommendations)

        // Ensure UI state is correct
        if (recommendations.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
            showMessage("No recommendations available")
        }
    }

    private fun showMessage(message: String) {
        emptyStateLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        loadingStateLayout.visibility = View.GONE
        
        messageTextView.text = message
        enableRecommendationsButton.visibility = View.GONE
        exploreChannelsButton.visibility = View.VISIBLE
    }

    private fun showEnableRecommendationsDialog() {
        // Navigate to Library Settings to enable features
        Toast.makeText(requireContext(), "Go to Library → Settings to enable recommendations", Toast.LENGTH_LONG).show()
    }

    /**
     * Generate enhanced recommendations using ML and contextual discovery
     */
    private suspend fun generateEnhancedRecommendations(): List<Video> {
        return try {
            // Use enhanced recommendation system
            val contextualRecs = enhancementIntegrator.getEnhancedRecommendations(30)

            // Convert contextual recommendations to Video objects
            val enhancedVideos = contextualRecs.map { rec ->
                Video(
                    videoId = rec.video.videoId,
                    title = "${rec.video.title} • ${rec.reasons.firstOrNull() ?: "Recommended"}",
                    description = "${rec.video.description}\n\n💡 Why recommended: ${rec.reasons.joinToString(", ")}",
                    thumbnail = rec.video.thumbnail,
                    channelTitle = rec.video.channelTitle,
                    publishedAt = rec.video.publishedAt,
                    duration = rec.video.duration
                )
            }

            // If enhanced recommendations are empty, fall back to local recommendations
            if (enhancedVideos.isEmpty()) {
                generateLocalRecommendations()
            } else {
                enhancedVideos
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating enhanced recommendations, falling back to local", e)
            generateLocalRecommendations()
        }
    }
}
