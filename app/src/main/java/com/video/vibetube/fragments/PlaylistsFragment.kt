package com.video.vibetube.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.video.vibetube.R
import com.video.vibetube.adapters.PlaylistAdapter
import com.video.vibetube.models.UserPlaylist
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.EngagementAnalytics
import com.video.vibetube.utils.SocialManager
import com.video.vibetube.integration.VibeTubeEnhancementIntegrator
import kotlinx.coroutines.launch

class PlaylistsFragment : Fragment() {

    private lateinit var userDataManager: UserDataManager
    private lateinit var engagementAnalytics: EngagementAnalytics
    private lateinit var socialManager: SocialManager
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var enhancementIntegrator: VibeTubeEnhancementIntegrator
    
    private lateinit var playlistsRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var loadingStateLayout: View
    private lateinit var totalPlaylistsText: TextView
    private lateinit var totalVideosText: TextView
    private lateinit var sortPlaylistsButton: MaterialButton
    private lateinit var createPlaylistFab: ExtendedFloatingActionButton
    private lateinit var createFirstPlaylistButton: MaterialButton

    private var currentSortOrder = SortOrder.CREATED_DESC

    enum class SortOrder {
        CREATED_DESC, CREATED_ASC, NAME_ASC, NAME_DESC, SIZE_DESC, SIZE_ASC, UPDATED_DESC
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_playlists, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeManagers()
        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()
        loadPlaylists()
    }

    private fun initializeManagers() {
        userDataManager = UserDataManager.getInstance(requireContext())
        engagementAnalytics = EngagementAnalytics.getInstance(requireContext())
        socialManager = SocialManager.getInstance(requireContext())
        enhancementIntegrator = VibeTubeEnhancementIntegrator.getInstance(requireContext())
    }

    private fun initializeViews(view: View) {
        playlistsRecyclerView = view.findViewById(R.id.playlistsRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        loadingStateLayout = view.findViewById(R.id.loadingStateLayout)
        totalPlaylistsText = view.findViewById(R.id.totalPlaylistsText)
        totalVideosText = view.findViewById(R.id.totalVideosText)
        sortPlaylistsButton = view.findViewById(R.id.sortPlaylistsButton)
        createPlaylistFab = view.findViewById(R.id.createPlaylistFab)
        createFirstPlaylistButton = view.findViewById(R.id.createFirstPlaylistButton)
    }

    private fun setupRecyclerView() {
        playlistAdapter = PlaylistAdapter(
            onItemClick = { playlist ->
                handlePlaylistClick(playlist)
            },
            onEditClick = { playlist ->
                showEditPlaylistDialog(playlist)
            },
            onDeleteClick = { playlist ->
                showDeletePlaylistDialog(playlist)
            },
            onShareClick = { playlist ->
                handleSharePlaylist(playlist)
            },
            onPlayClick = { playlist ->
                handlePlayPlaylist(playlist)
            }
        )
        
        playlistsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = playlistAdapter
        }
    }

    private fun setupClickListeners() {
        sortPlaylistsButton.setOnClickListener {
            showSortDialog()
        }

        createPlaylistFab.setOnClickListener {
            showCreatePlaylistDialog()
        }

        createFirstPlaylistButton.setOnClickListener {
            showCreatePlaylistDialog()
        }

        // Add long click for smart playlist generation
        createPlaylistFab.setOnLongClickListener {
            generateSmartPlaylists()
            true
        }
    }

    private fun loadPlaylists() {
        showLoadingState()
        
        lifecycleScope.launch {
            try {
                // Check user consent first
                if (!userDataManager.hasUserConsent()) {
                    showEmptyState()
                    showError("To use playlists, please go to Library → Enable Library Features")
                    return@launch
                }

                val playlists = userDataManager.getPlaylists()
                val sortedPlaylists = applySorting(playlists)

                updateStats(playlists)

                if (sortedPlaylists.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent(sortedPlaylists)
                }

                engagementAnalytics.trackFeatureUsage("playlists_viewed")
            } catch (e: Exception) {
                showError("Failed to load playlists: ${e.message}")
            }
        }
    }

    private fun applySorting(playlists: List<UserPlaylist>): List<UserPlaylist> {
        return when (currentSortOrder) {
            SortOrder.CREATED_DESC -> playlists.sortedByDescending { it.createdAt }
            SortOrder.CREATED_ASC -> playlists.sortedBy { it.createdAt }
            SortOrder.NAME_ASC -> playlists.sortedBy { it.name }
            SortOrder.NAME_DESC -> playlists.sortedByDescending { it.name }
            SortOrder.SIZE_DESC -> playlists.sortedByDescending { it.videoIds.size }
            SortOrder.SIZE_ASC -> playlists.sortedBy { it.videoIds.size }
            SortOrder.UPDATED_DESC -> playlists.sortedByDescending { it.updatedAt }
        }
    }

    private fun updateStats(playlists: List<UserPlaylist>) {
        val totalVideos = playlists.sumOf { it.videoIds.size }
        totalPlaylistsText.text = playlists.size.toString()
        totalVideosText.text = totalVideos.toString()
    }

    private fun showSortDialog() {
        val sortOptions = arrayOf(
            "Created (Newest)",
            "Created (Oldest)",
            "Name (A-Z)",
            "Name (Z-A)",
            "Size (Largest)",
            "Size (Smallest)",
            "Updated (Recent)"
        )
        val currentIndex = SortOrder.values().indexOf(currentSortOrder)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Sort Playlists")
            .setSingleChoiceItems(sortOptions, currentIndex) { dialog, which ->
                currentSortOrder = SortOrder.values()[which]
                loadPlaylists()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreatePlaylistDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_create_playlist, null)
        
        val nameInputLayout = dialogView.findViewById<TextInputLayout>(R.id.playlistNameInputLayout)
        val nameEditText = dialogView.findViewById<TextInputEditText>(R.id.playlistNameEditText)
        val descriptionInputLayout = dialogView.findViewById<TextInputLayout>(R.id.playlistDescriptionInputLayout)
        val descriptionEditText = dialogView.findViewById<TextInputEditText>(R.id.playlistDescriptionEditText)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Create Playlist")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = nameEditText.text.toString().trim()
                val description = descriptionEditText.text.toString().trim()
                
                if (name.isNotEmpty()) {
                    createPlaylist(name, description)
                } else {
                    nameInputLayout.error = "Playlist name is required"
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditPlaylistDialog(playlist: UserPlaylist) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_create_playlist, null)
        
        val nameInputLayout = dialogView.findViewById<TextInputLayout>(R.id.playlistNameInputLayout)
        val nameEditText = dialogView.findViewById<TextInputEditText>(R.id.playlistNameEditText)
        val descriptionInputLayout = dialogView.findViewById<TextInputLayout>(R.id.playlistDescriptionInputLayout)
        val descriptionEditText = dialogView.findViewById<TextInputEditText>(R.id.playlistDescriptionEditText)

        nameEditText.setText(playlist.name)
        descriptionEditText.setText(playlist.description)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Playlist")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = nameEditText.text.toString().trim()
                val description = descriptionEditText.text.toString().trim()
                
                if (name.isNotEmpty()) {
                    updatePlaylist(playlist.id, name, description)
                } else {
                    nameInputLayout.error = "Playlist name is required"
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeletePlaylistDialog(playlist: UserPlaylist) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Playlist")
            .setMessage("Are you sure you want to delete \"${playlist.name}\"? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deletePlaylist(playlist)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createPlaylist(name: String, description: String) {
        lifecycleScope.launch {
            try {
                userDataManager.createPlaylist(name, description)
                loadPlaylists()
                engagementAnalytics.trackFeatureUsage("playlist_created")
                Toast.makeText(requireContext(), "Playlist created", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Failed to create playlist")
            }
        }
    }

    private fun updatePlaylist(playlistId: String, name: String, description: String) {
        lifecycleScope.launch {
            try {
                userDataManager.updatePlaylistInfo(playlistId, name, description)
                loadPlaylists()
                engagementAnalytics.trackFeatureUsage("playlist_updated")
                Toast.makeText(requireContext(), "Playlist updated", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Failed to update playlist")
            }
        }
    }

    private fun deletePlaylist(playlist: UserPlaylist) {
        lifecycleScope.launch {
            try {
                userDataManager.deletePlaylist(playlist.id)
                loadPlaylists()
                engagementAnalytics.trackFeatureUsage("playlist_deleted")
                Toast.makeText(requireContext(), "Playlist deleted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Failed to delete playlist")
            }
        }
    }

    private fun handlePlaylistClick(playlist: UserPlaylist) {
        try {
            val intent = Intent(requireContext(), com.video.vibetube.activity.PlaylistDetailActivity::class.java).apply {
                putExtra(com.video.vibetube.activity.PlaylistDetailActivity.EXTRA_PLAYLIST_ID, playlist.id)
            }
            startActivity(intent)
            lifecycleScope.launch {
                engagementAnalytics.trackFeatureUsage("playlist_opened")
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to open playlist: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSharePlaylist(playlist: UserPlaylist) {
        lifecycleScope.launch {
            try {
                socialManager.sharePlaylist(playlist, emptyList())
                engagementAnalytics.trackFeatureUsage("playlist_shared")
            } catch (e: Exception) {
                showError("Failed to share playlist")
            }
        }
    }

    private fun handlePlayPlaylist(playlist: UserPlaylist) {
        if (playlist.videoIds.isEmpty()) {
            Toast.makeText(requireContext(), "Playlist is empty", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            engagementAnalytics.trackFeatureUsage("playlist_played")
        }
        // Start playing the first video in the playlist
        // Implementation depends on your video player activity
    }

    private fun showLoadingState() {
        loadingStateLayout.visibility = View.VISIBLE
        playlistsRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
        createPlaylistFab.visibility = View.GONE
    }

    private fun showContent(playlists: List<UserPlaylist>) {
        loadingStateLayout.visibility = View.GONE
        playlistsRecyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        createPlaylistFab.visibility = View.VISIBLE
        
        playlistAdapter.updatePlaylists(playlists)
    }

    private fun showEmptyState() {
        loadingStateLayout.visibility = View.GONE
        playlistsRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
        createPlaylistFab.visibility = View.GONE
    }

    private fun showError(message: String) {
        // Check if fragment is still attached to avoid crashes
        if (!isAdded || context == null) return

        loadingStateLayout.visibility = View.GONE
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    /**
     * Generate smart playlists using ML and content analysis
     */
    private fun generateSmartPlaylists() {
        lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "🤖 Generating smart playlists...", Toast.LENGTH_SHORT).show()

                val smartPlaylists = enhancementIntegrator.generateEnhancedPlaylists()

                if (smartPlaylists.isNotEmpty()) {
                    // Actually save the smart playlists to user data
                    var savedCount = 0
                    smartPlaylists.forEach { smartPlaylist ->
                        try {
                            // Create playlist using UserDataManager
                            val createdPlaylist = userDataManager.createPlaylist(
                                name = smartPlaylist.name,
                                description = smartPlaylist.description
                            )

                            // Add videos to the playlist
                            smartPlaylist.videos.forEach { video ->
                                userDataManager.addVideoToPlaylist(createdPlaylist.id, video)
                            }

                            savedCount++

                        } catch (e: Exception) {
                            // Continue with other playlists if one fails
                            // Log error but don't break the loop
                        }
                    }

                    val message = buildString {
                        appendLine("🎯 Successfully created $savedCount smart playlists:")
                        smartPlaylists.take(5).forEach { playlist ->
                            appendLine("• ${playlist.name} (${playlist.videos.size} videos)")
                        }
                        if (smartPlaylists.size > 5) {
                            appendLine("... and ${smartPlaylists.size - 5} more!")
                        }
                        appendLine()
                        appendLine("These playlists will automatically update based on your viewing patterns.")
                    }

                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("🤖 Smart Playlists Created")
                        .setMessage(message)
                        .setPositiveButton("Great!") { dialog, _ ->
                            dialog.dismiss()
                            loadPlaylists() // Refresh the list to show new playlists
                        }
                        .show()

                    // Track the feature usage
                    engagementAnalytics.trackFeatureUsage("smart_playlists_generated")

                } else {
                    Toast.makeText(requireContext(), "No smart playlists could be generated. Watch more videos to improve recommendations!", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Smart playlist generation failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        fun newInstance(): PlaylistsFragment {
            return PlaylistsFragment()
        }
    }
}
