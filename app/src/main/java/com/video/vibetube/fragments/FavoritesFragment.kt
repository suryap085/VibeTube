package com.video.vibetube.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.video.vibetube.R
import com.video.vibetube.adapters.FavoritesAdapter
import com.video.vibetube.models.FavoriteItem
import com.video.vibetube.models.Video
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.EngagementAnalytics
import com.video.vibetube.utils.SocialManager
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {

    private lateinit var userDataManager: UserDataManager
    private lateinit var engagementAnalytics: EngagementAnalytics
    private lateinit var socialManager: SocialManager
    private lateinit var playlistManager: com.video.vibetube.utils.PlaylistManager
    private lateinit var favoritesAdapter: FavoritesAdapter

    private lateinit var favoritesRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var loadingStateLayout: View
    private lateinit var categoryChipGroup: ChipGroup
    private lateinit var sortFavoritesButton: MaterialButton
    private lateinit var bulkActionsFab: ExtendedFloatingActionButton
    private lateinit var discoverVideosButton: MaterialButton

    private var currentCategory = "All"
    private var currentSortOrder = SortOrder.DATE_ADDED_DESC
    private var isSelectionMode = false

    enum class SortOrder {
        DATE_ADDED_DESC, DATE_ADDED_ASC, TITLE_ASC, TITLE_DESC, DURATION_DESC, DURATION_ASC
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_favorites, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeManagers()
        initializeViews(view)
        setupRecyclerView()
        setupChipGroup()
        setupClickListeners()
        loadFavorites()
    }

    private fun initializeManagers() {
        userDataManager = UserDataManager.getInstance(requireContext())
        engagementAnalytics = EngagementAnalytics.getInstance(requireContext())
        socialManager = SocialManager.getInstance(requireContext())
        playlistManager = com.video.vibetube.utils.PlaylistManager.getInstance(requireContext())
    }

    private fun initializeViews(view: View) {
        favoritesRecyclerView = view.findViewById(R.id.favoritesRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        loadingStateLayout = view.findViewById(R.id.loadingStateLayout)
        categoryChipGroup = view.findViewById(R.id.categoryChipGroup)
        sortFavoritesButton = view.findViewById(R.id.sortFavoritesButton)
        bulkActionsFab = view.findViewById(R.id.bulkActionsFab)
        discoverVideosButton = view.findViewById(R.id.discoverVideosButton)
    }

    private fun setupRecyclerView() {
        favoritesAdapter = FavoritesAdapter(
            onItemClick = { favorite ->
                if (isSelectionMode) {
                    favoritesAdapter.toggleSelection(favorite.videoId)
                    updateBulkActionsVisibility()
                } else {
                    handleVideoClick(favorite)
                }
            },
            onItemLongClick = { favorite ->
                if (!isSelectionMode) {
                    enterSelectionMode()
                    favoritesAdapter.toggleSelection(favorite.videoId)
                    updateBulkActionsVisibility()
                }
            },
            onFavoriteClick = { favorite ->
                handleRemoveFromFavorites(favorite)
            },
            onShareClick = { favorite ->
                handleShareVideo(favorite)
            },
            onPlaylistClick = { favorite ->
                handleAddToPlaylist(favorite)
            }
        )

        favoritesRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = favoritesAdapter
        }
    }

    private fun setupChipGroup() {
        categoryChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val selectedChip = group.findViewById<Chip>(checkedIds[0])
                currentCategory = selectedChip.text.toString()
                Log.d(TAG, "Category selected: $currentCategory")
                loadFavorites()
            } else {
                // If no chip is selected, default to "All"
                currentCategory = "All"
                Log.d(TAG, "No category selected, defaulting to: $currentCategory")
                loadFavorites()
            }
        }
    }

    private fun setupClickListeners() {
        sortFavoritesButton.setOnClickListener {
            showSortDialog()
        }

        bulkActionsFab.setOnClickListener {
            if (isSelectionMode) {
                showBulkActionsDialog()
            } else {
                enterSelectionMode()
            }
        }

        discoverVideosButton.setOnClickListener {
            // Navigate to home tab
            requireActivity().finish()
        }
    }

    private fun loadFavorites() {
        showLoadingState()

        lifecycleScope.launch {
            try {
                val favorites = userDataManager.getFavorites()

                // Update chip visibility based on available categories
                updateChipVisibility(favorites)

                val filteredAndSorted = applyFilterAndSort(favorites)

                if (filteredAndSorted.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent(filteredAndSorted)
                }

                engagementAnalytics.trackFeatureUsage("favorites_viewed")
            } catch (e: Exception) {
                showError("Failed to load favorites")
            }
        }
    }

    private fun updateChipVisibility(favorites: List<FavoriteItem>) {
        // Get unique categories from favorites
        val availableCategories = favorites.map { favorite ->
            when (favorite.category.lowercase()) {
                "music" -> "Music"
                "education" -> "Education"
                "entertainment" -> "Entertainment"
                "gaming" -> "Gaming"
                "diy" -> "DIY"
                else -> favorite.category.replaceFirstChar { it.uppercase() }
            }
        }.toSet()

        // Show/hide chips based on available categories
        view?.findViewById<Chip>(R.id.chipMusic)?.visibility =
            if (availableCategories.contains("Music")) View.VISIBLE else View.GONE
        view?.findViewById<Chip>(R.id.chipEducation)?.visibility =
            if (availableCategories.contains("Education")) View.VISIBLE else View.GONE
        view?.findViewById<Chip>(R.id.chipEntertainment)?.visibility =
            if (availableCategories.contains("Entertainment")) View.VISIBLE else View.GONE
        view?.findViewById<Chip>(R.id.chipGaming)?.visibility =
            if (availableCategories.contains("Gaming")) View.VISIBLE else View.GONE
        view?.findViewById<Chip>(R.id.chipDiy)?.visibility =
            if (availableCategories.contains("DIY")) View.VISIBLE else View.GONE
    }

    private fun applyFilterAndSort(favorites: List<FavoriteItem>): List<FavoriteItem> {
        Log.d(TAG, "Applying filter for category: $currentCategory")
        Log.d(TAG, "Total favorites: ${favorites.size}")

        // Log all categories for debugging
        favorites.forEach { favorite ->
            Log.d(TAG, "Favorite: ${favorite.title} - Category: '${favorite.category}'")
        }

        var filtered = if (currentCategory == "All") {
            favorites
        } else {
            // Case-insensitive comparison to match chip text with stored category
            favorites.filter {
                it.category.equals(currentCategory, ignoreCase = true)
            }
        }

        Log.d(TAG, "Filtered favorites: ${filtered.size}")

        return when (currentSortOrder) {
            SortOrder.DATE_ADDED_DESC -> filtered.sortedByDescending { it.addedAt }
            SortOrder.DATE_ADDED_ASC -> filtered.sortedBy { it.addedAt }
            SortOrder.TITLE_ASC -> filtered.sortedBy { it.title }
            SortOrder.TITLE_DESC -> filtered.sortedByDescending { it.title }
            SortOrder.DURATION_DESC -> filtered.sortedByDescending { it.duration }
            SortOrder.DURATION_ASC -> filtered.sortedBy { it.duration }
        }
    }

    private fun showSortDialog() {
        val sortOptions = arrayOf(
            "Date Added (Newest)",
            "Date Added (Oldest)",
            "Title (A-Z)",
            "Title (Z-A)",
            "Duration (Longest)",
            "Duration (Shortest)"
        )
        val currentIndex = SortOrder.values().indexOf(currentSortOrder)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort Favorites")
            .setSingleChoiceItems(sortOptions, currentIndex) { dialog, which ->
                currentSortOrder = SortOrder.values()[which]
                updateSortButtonText()
                loadFavorites()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSortButtonText() {
        sortFavoritesButton.text = when (currentSortOrder) {
            SortOrder.DATE_ADDED_DESC -> "Date Added"
            SortOrder.DATE_ADDED_ASC -> "Date Added"
            SortOrder.TITLE_ASC -> "Title"
            SortOrder.TITLE_DESC -> "Title"
            SortOrder.DURATION_DESC -> "Duration"
            SortOrder.DURATION_ASC -> "Duration"
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        favoritesAdapter.setSelectionMode(true)
        bulkActionsFab.text = "Actions"
        bulkActionsFab.setIconResource(R.drawable.ic_more_vert)
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        favoritesAdapter.setSelectionMode(false)
        favoritesAdapter.clearSelection()
        bulkActionsFab.text = "Select"
        bulkActionsFab.setIconResource(R.drawable.ic_check_box)
        updateBulkActionsVisibility()
    }

    private fun updateBulkActionsVisibility() {
        val selectedCount = favoritesAdapter.getSelectedCount()
        if (selectedCount > 0) {
            bulkActionsFab.visibility = View.VISIBLE
            bulkActionsFab.text = "Actions ($selectedCount)"
        } else if (isSelectionMode) {
            bulkActionsFab.visibility = View.VISIBLE
            bulkActionsFab.text = "Cancel"
            bulkActionsFab.setIconResource(R.drawable.ic_close)
        } else {
            bulkActionsFab.visibility = View.GONE
        }
    }

    private fun showBulkActionsDialog() {
        val selectedItems = favoritesAdapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            exitSelectionMode()
            return
        }

        val actions = arrayOf("Share Selected", "Remove from Favorites", "Export List", "Cancel")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bulk Actions (${selectedItems.size} items)")
            .setItems(actions) { dialog, which ->
                when (which) {
                    0 -> shareSelectedFavorites(selectedItems)
                    1 -> removeSelectedFavorites(selectedItems)
                    2 -> exportSelectedFavorites(selectedItems)
                    3 -> exitSelectionMode()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun shareSelectedFavorites(favorites: List<FavoriteItem>) {
        lifecycleScope.launch {
            try {
                socialManager.shareFavoritesList(favorites)
                engagementAnalytics.trackFeatureUsage("favorites_bulk_shared")
                exitSelectionMode()
            } catch (e: Exception) {
                showError("Failed to share favorites")
            }
        }
    }

    private fun removeSelectedFavorites(favorites: List<FavoriteItem>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remove Favorites")
            .setMessage("Remove ${favorites.size} videos from favorites?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    try {
                        favorites.forEach { favorite ->
                            userDataManager.removeFromFavorites(favorite.videoId)
                        }
                        loadFavorites()
                        engagementAnalytics.trackFeatureUsage("favorites_bulk_removed")
                        exitSelectionMode()
                        Toast.makeText(requireContext(), "Removed ${favorites.size} favorites", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        showError("Failed to remove favorites")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportSelectedFavorites(favorites: List<FavoriteItem>) {
        lifecycleScope.launch {
            try {
                socialManager.exportFavoritesList(favorites)
                engagementAnalytics.trackFeatureUsage("favorites_exported")
                exitSelectionMode()
                Toast.makeText(requireContext(), "Favorites list exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Failed to export favorites")
            }
        }
    }

    private fun handleVideoClick(favorite: FavoriteItem) {
        lifecycleScope.launch {
            engagementAnalytics.trackFeatureUsage("favorite_video_clicked")
        }

        // Convert FavoriteItem to Video object for YouTubePlayerActivity
        val video = convertFavoriteToVideo(favorite)

        // Launch video player with the favorite video
        val intent = Intent(requireContext(), com.video.vibetube.activity.YouTubePlayerActivity::class.java).apply {
            putParcelableArrayListExtra("VIDEOS", ArrayList(listOf(video)))
            putExtra("CURRENT_INDEX", 0)
        }
        startActivity(intent)
    }

    /**
     * Convert FavoriteItem to Video object for video player and playlist operations
     */
    private fun convertFavoriteToVideo(favorite: FavoriteItem): com.video.vibetube.models.Video {
        return com.video.vibetube.models.Video(
            videoId = favorite.videoId,
            title = favorite.title,
            description = "", // Not stored in favorites
            thumbnail = favorite.thumbnail,
            channelTitle = favorite.channelTitle,
            publishedAt = "", // Not stored in favorites
            duration = favorite.duration,
            channelId = favorite.channelId
        )
    }

    private fun handleRemoveFromFavorites(favorite: FavoriteItem) {
        lifecycleScope.launch {
            try {
                userDataManager.removeFromFavorites(favorite.videoId)
                loadFavorites()
                engagementAnalytics.trackFeatureUsage("favorite_removed")
                Toast.makeText(requireContext(), "Removed from favorites", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Failed to remove favorite")
            }
        }
    }

    private fun handleShareVideo(favorite: FavoriteItem) {
        lifecycleScope.launch {
            try {
                socialManager.shareVideo(favorite.videoId, favorite.title)
                engagementAnalytics.trackFeatureUsage("favorite_shared")
            } catch (e: Exception) {
                showError("Failed to share video")
            }
        }
    }

    private fun handleAddToPlaylist(favorite: FavoriteItem) {
        // Convert FavoriteItem to Video for playlist manager
        val video = convertFavoriteToVideo(favorite)

        playlistManager.showAddToPlaylistDialog(
            video = video,
            lifecycleOwner = this,
            onSuccess = { addedPlaylists ->
                if (addedPlaylists.isNotEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "Added to ${addedPlaylists.joinToString(", ")}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onError = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        )
    }



    private fun showLoadingState() {
        loadingStateLayout.visibility = View.VISIBLE
        favoritesRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
        bulkActionsFab.visibility = View.GONE
    }

    private fun showContent(favorites: List<FavoriteItem>) {
        loadingStateLayout.visibility = View.GONE
        favoritesRecyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        bulkActionsFab.visibility = if (favorites.isNotEmpty()) View.VISIBLE else View.GONE

        favoritesAdapter.updateFavorites(favorites)
    }

    private fun showEmptyState() {
        loadingStateLayout.visibility = View.GONE
        favoritesRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
        bulkActionsFab.visibility = View.GONE
    }

    private fun showError(message: String) {
        // Check if fragment is still attached to avoid crashes
        if (!isAdded || context == null) return

        loadingStateLayout.visibility = View.GONE
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isSelectionMode) {
            exitSelectionMode()
        }
    }

    companion object {
        private const val TAG = "FavoritesFragment"

        fun newInstance(): FavoritesFragment {
            return FavoritesFragment()
        }
    }
}
