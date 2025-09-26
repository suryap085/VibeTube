package com.video.vibetube.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.video.vibetube.R
import com.video.vibetube.adapters.WatchHistoryAdapter
import com.video.vibetube.models.WatchHistoryItem
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.EngagementAnalytics
import kotlinx.coroutines.launch

class WatchHistoryFragment : Fragment() {

    private lateinit var userDataManager: UserDataManager
    private lateinit var engagementAnalytics: EngagementAnalytics
    private lateinit var watchHistoryAdapter: WatchHistoryAdapter

    private lateinit var watchHistoryRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var loadingStateLayout: View
    private lateinit var filterButton: MaterialButton
    private lateinit var sortButton: MaterialButton
    private lateinit var clearHistoryFab: FloatingActionButton
    private lateinit var exploreVideosButton: MaterialButton

    private var currentSortOrder = SortOrder.RECENT_FIRST
    private var currentFilter = FilterType.ALL

    enum class SortOrder {
        RECENT_FIRST, OLDEST_FIRST, DURATION_LONG, DURATION_SHORT
    }

    enum class FilterType {
        ALL, COMPLETED, INCOMPLETE, MUSIC, EDUCATION, GAMING, DIY
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_watch_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeManagers()
        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadWatchHistory()
    }

    private fun initializeManagers() {
        userDataManager = UserDataManager.getInstance(requireContext())
        engagementAnalytics = EngagementAnalytics.getInstance(requireContext())
    }

    private fun initializeViews(view: View) {
        watchHistoryRecyclerView = view.findViewById(R.id.watchHistoryRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        loadingStateLayout = view.findViewById(R.id.loadingStateLayout)
        filterButton = view.findViewById(R.id.filterButton)
        sortButton = view.findViewById(R.id.sortButton)
        clearHistoryFab = view.findViewById(R.id.clearHistoryFab)
        exploreVideosButton = view.findViewById(R.id.exploreVideosButton)
    }

    private fun setupRecyclerView() {
        watchHistoryAdapter = WatchHistoryAdapter(
            onItemClick = { historyItem ->
                handleVideoClick(historyItem)
            },
            onResumeClick = { historyItem ->
                handleResumeVideo(historyItem)
            },
            onDeleteClick = { historyItem ->
                handleDeleteHistoryItem(historyItem)
            }
        )

        watchHistoryRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = watchHistoryAdapter
        }
    }

    private fun setupClickListeners() {
        filterButton.setOnClickListener {
            showFilterDialog()
        }

        sortButton.setOnClickListener {
            showSortDialog()
        }

        clearHistoryFab.setOnClickListener {
            showClearHistoryDialog()
        }

        exploreVideosButton.setOnClickListener {
            // Navigate to home tab
            requireActivity().finish()
        }
    }

    private fun loadWatchHistory() {
        showLoadingState()

        lifecycleScope.launch {
            try {
                val historyItems = userDataManager.getWatchHistory()
                val filteredAndSorted = applyFilterAndSort(historyItems)

                if (filteredAndSorted.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent(filteredAndSorted)
                }

                engagementAnalytics.trackFeatureUsage("watch_history_viewed")
            } catch (e: Exception) {
                showError("Failed to load watch history")
            }
        }
    }

    private fun applyFilterAndSort(items: List<WatchHistoryItem>): List<WatchHistoryItem> {
        var filtered = when (currentFilter) {
            FilterType.ALL -> items
            FilterType.COMPLETED -> items.filter { it.isCompleted }
            FilterType.INCOMPLETE -> items.filter { !it.isCompleted }
            FilterType.MUSIC -> items.filter { it.channelTitle.contains("Music", ignoreCase = true) }
            FilterType.EDUCATION -> items.filter { it.channelTitle.contains("Education", ignoreCase = true) }
            FilterType.GAMING -> items.filter { it.channelTitle.contains("Gaming", ignoreCase = true) }
            FilterType.DIY -> items.filter { it.channelTitle.contains("DIY", ignoreCase = true) }
        }

        return when (currentSortOrder) {
            SortOrder.RECENT_FIRST -> filtered.sortedByDescending { it.watchedAt }
            SortOrder.OLDEST_FIRST -> filtered.sortedBy { it.watchedAt }
            SortOrder.DURATION_LONG -> filtered.sortedByDescending { it.duration }
            SortOrder.DURATION_SHORT -> filtered.sortedBy { it.duration }
        }
    }

    private fun showFilterDialog() {
        val filterOptions = arrayOf("All", "Completed", "Incomplete", "Music", "Education", "Gaming", "DIY")
        val currentIndex = FilterType.values().indexOf(currentFilter)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter History")
            .setSingleChoiceItems(filterOptions, currentIndex) { dialog, which ->
                currentFilter = FilterType.values()[which]
                filterButton.text = filterOptions[which]
                loadWatchHistory()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSortDialog() {
        val sortOptions = arrayOf("Recent First", "Oldest First", "Longest Duration", "Shortest Duration")
        val currentIndex = SortOrder.values().indexOf(currentSortOrder)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort History")
            .setSingleChoiceItems(sortOptions, currentIndex) { dialog, which ->
                currentSortOrder = SortOrder.values()[which]
                sortButton.text = sortOptions[which]
                loadWatchHistory()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearHistoryDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Watch History")
            .setMessage("Are you sure you want to clear all watch history? This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                clearAllHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearAllHistory() {
        lifecycleScope.launch {
            try {
                userDataManager.clearWatchHistory()
                loadWatchHistory()
                engagementAnalytics.trackFeatureUsage("watch_history_cleared")
                Toast.makeText(requireContext(), "Watch history cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Failed to clear history")
            }
        }
    }

    private fun handleVideoClick(historyItem: WatchHistoryItem) {
        lifecycleScope.launch {
            engagementAnalytics.trackFeatureUsage("watch_history_video_clicked")
        }

        // Convert WatchHistoryItem to Video object for YouTubePlayerActivity
        val video = convertHistoryToVideo(historyItem)

        // Auto-resume if video has significant progress (>5% and not completed)
        val shouldAutoResume = historyItem.watchProgress > 0.05f && !historyItem.isCompleted

        val intent = Intent(requireContext(), com.video.vibetube.activity.YouTubePlayerActivity::class.java).apply {
            putParcelableArrayListExtra("VIDEOS", ArrayList(listOf(video)))
            putExtra("CURRENT_INDEX", 0)

            if (shouldAutoResume) {
                val resumePositionSeconds = calculateResumePosition(historyItem)
                putExtra("RESUME_POSITION", resumePositionSeconds)
                putExtra("FROM_HISTORY", true)
            }
        }
        startActivity(intent)
    }

    /**
     * Convert WatchHistoryItem to Video object for video player
     */
    private fun convertHistoryToVideo(historyItem: WatchHistoryItem): com.video.vibetube.models.Video {
        return com.video.vibetube.models.Video(
            videoId = historyItem.videoId,
            title = historyItem.title,
            description = "", // Not stored in history
            thumbnail = historyItem.thumbnail,
            channelTitle = historyItem.channelTitle,
            publishedAt = "", // Not stored in history
            duration = historyItem.duration,
            channelId = historyItem.channelId
        )
    }

    private fun handleResumeVideo(historyItem: WatchHistoryItem) {
        lifecycleScope.launch {
            engagementAnalytics.trackFeatureUsage("watch_history_resume_clicked")
        }

        // Convert WatchHistoryItem to Video object for YouTubePlayerActivity
        val video = convertHistoryToVideo(historyItem)

        // Calculate resume position in seconds
        val resumePositionSeconds = calculateResumePosition(historyItem)

        // Launch video player with resume position
        val intent = Intent(requireContext(), com.video.vibetube.activity.YouTubePlayerActivity::class.java).apply {
            putParcelableArrayListExtra("VIDEOS", ArrayList(listOf(video)))
            putExtra("CURRENT_INDEX", 0)
            putExtra("RESUME_POSITION", resumePositionSeconds)
            putExtra("FROM_HISTORY", true)
        }
        startActivity(intent)
    }

    /**
     * Calculate resume position in seconds from watch progress
     */
    private fun calculateResumePosition(historyItem: WatchHistoryItem): Float {
        // Parse duration string (e.g., "10:30" or "1:05:30") to seconds
        val durationSeconds = parseDurationToSeconds(historyItem.duration)

        // Calculate resume position: progress (0.0-1.0) * total duration
        return (historyItem.watchProgress * durationSeconds).coerceAtLeast(0f)
    }

    /**
     * Parse duration string to seconds
     * Supports formats: "5:30" (5 minutes 30 seconds) or "1:05:30" (1 hour 5 minutes 30 seconds)
     */
    private fun parseDurationToSeconds(duration: String): Float {
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                2 -> {
                    // Format: "MM:SS"
                    val minutes = parts[0].toInt()
                    val seconds = parts[1].toInt()
                    (minutes * 60 + seconds).toFloat()
                }
                3 -> {
                    // Format: "HH:MM:SS"
                    val hours = parts[0].toInt()
                    val minutes = parts[1].toInt()
                    val seconds = parts[2].toInt()
                    (hours * 3600 + minutes * 60 + seconds).toFloat()
                }
                else -> 0f
            }
        } catch (e: Exception) {
            0f // Default to start if parsing fails
        }
    }

    private fun handleDeleteHistoryItem(historyItem: WatchHistoryItem) {
        lifecycleScope.launch {
            try {
                userDataManager.removeFromWatchHistory(historyItem.videoId)
                loadWatchHistory()
                engagementAnalytics.trackFeatureUsage("watch_history_item_deleted")
                Toast.makeText(requireContext(), "Removed from history", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Failed to remove item")
            }
        }
    }

    private fun showLoadingState() {
        loadingStateLayout.visibility = View.VISIBLE
        watchHistoryRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
        clearHistoryFab.visibility = View.GONE
    }

    private fun showContent(items: List<WatchHistoryItem>) {
        loadingStateLayout.visibility = View.GONE
        watchHistoryRecyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        clearHistoryFab.visibility = View.VISIBLE

        watchHistoryAdapter.updateItems(items)
    }

    private fun showEmptyState() {
        loadingStateLayout.visibility = View.GONE
        watchHistoryRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
        clearHistoryFab.visibility = View.GONE
    }

    private fun showError(message: String) {
        // Check if fragment is still attached to avoid crashes
        if (!isAdded || context == null) return

        loadingStateLayout.visibility = View.GONE
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    companion object {
        fun newInstance(): WatchHistoryFragment {
            return WatchHistoryFragment()
        }
    }
}
