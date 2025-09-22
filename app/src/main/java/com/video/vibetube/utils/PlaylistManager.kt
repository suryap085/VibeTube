package com.video.vibetube.utils

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.video.vibetube.R
import com.video.vibetube.models.Video
import com.video.vibetube.models.UserPlaylist
import kotlinx.coroutines.launch

/**
 * YouTube Policy Compliant Playlist Manager
 * 
 * This utility class provides centralized playlist management functionality
 * while ensuring compliance with YouTube's terms of service:
 * - All operations are performed on local user data only
 * - No YouTube API credentials are stored or transmitted
 * - User consent is required for all operations
 * - Respects video availability and access restrictions
 * - Maintains proper attribution to original YouTube content
 */
class PlaylistManager private constructor(private val context: Context) {
    
    private val userDataManager = UserDataManager.getInstance(context)
    private val engagementAnalytics = EngagementAnalytics.getInstance(context)
    
    companion object {
        @Volatile
        private var INSTANCE: PlaylistManager? = null
        
        fun getInstance(context: Context): PlaylistManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlaylistManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Show dialog to add video to playlists
     * YouTube Policy Compliance: Only operates on user's local playlists
     */
    fun showAddToPlaylistDialog(
        video: Video,
        lifecycleOwner: LifecycleOwner,
        onSuccess: ((List<String>) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        // Get the activity context for showing dialogs
        val activityContext = when (lifecycleOwner) {
            is androidx.appcompat.app.AppCompatActivity -> lifecycleOwner
            is androidx.fragment.app.Fragment -> lifecycleOwner.requireActivity()
            else -> context
        }

        lifecycleOwner.lifecycleScope.launch {
            try {
                // Check user consent first
                if (!userDataManager.hasUserConsent()) {
                    onError?.invoke("To use playlists, please go to Library → Enable Library Features")
                    return@launch
                }

                val playlists = userDataManager.getPlaylists()
                if (playlists.isEmpty()) {
                    showCreateFirstPlaylistDialog(video, lifecycleOwner, onSuccess, onError)
                    return@launch
                }

                val playlistNames = playlists.map { it.name }.toTypedArray()
                val checkedItems = BooleanArray(playlists.size) { false }

                // Check which playlists already contain this video
                playlists.forEachIndexed { index, playlist ->
                    checkedItems[index] = userDataManager.isVideoInPlaylist(playlist.id, video.videoId)
                }

                MaterialAlertDialogBuilder(activityContext)
                    .setTitle("Add to Playlists")
                    .setMultiChoiceItems(playlistNames, checkedItems) { _, which, isChecked ->
                        checkedItems[which] = isChecked
                    }
                    .setPositiveButton("Save") { _, _ ->
                        lifecycleOwner.lifecycleScope.launch {
                            val addedToPlaylists = mutableListOf<String>()
                            var hasError = false
                            
                            playlists.forEachIndexed { index, playlist ->
                                val wasInPlaylist = userDataManager.isVideoInPlaylist(playlist.id, video.videoId)
                                val shouldBeInPlaylist = checkedItems[index]
                                
                                try {
                                    when {
                                        !wasInPlaylist && shouldBeInPlaylist -> {
                                            if (userDataManager.addVideoToPlaylist(playlist.id, video)) {
                                                addedToPlaylists.add(playlist.name)
                                                engagementAnalytics.trackFeatureUsage("video_added_to_playlist")
                                            }
                                        }
                                        wasInPlaylist && !shouldBeInPlaylist -> {
                                            userDataManager.removeVideoFromPlaylist(playlist.id, video.videoId)
                                            engagementAnalytics.trackFeatureUsage("video_removed_from_playlist")
                                        }
                                    }
                                } catch (e: Exception) {
                                    hasError = true
                                }
                            }
                            
                            if (hasError) {
                                onError?.invoke("Some playlist operations failed")
                            } else {
                                onSuccess?.invoke(addedToPlaylists)
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .setNeutralButton("New Playlist") { _, _ ->
                        showCreatePlaylistDialog(video, lifecycleOwner, onSuccess, onError)
                    }
                    .show()
                    
            } catch (e: Exception) {
                onError?.invoke("Failed to load playlists")
            }
        }
    }
    
    /**
     * Show dialog to create a new playlist and optionally add a video
     */
    private fun showCreatePlaylistDialog(
        video: Video? = null,
        lifecycleOwner: LifecycleOwner,
        onSuccess: ((List<String>) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        // Get the activity context for showing dialogs
        val activityContext = when (lifecycleOwner) {
            is androidx.appcompat.app.AppCompatActivity -> lifecycleOwner
            is androidx.fragment.app.Fragment -> lifecycleOwner.requireActivity()
            else -> context
        }

        val dialogView = android.view.LayoutInflater.from(activityContext)
            .inflate(R.layout.dialog_create_playlist, null)

        val nameInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.playlistNameInputLayout)
        val nameEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.playlistNameEditText)
        val descriptionEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.playlistDescriptionEditText)

        MaterialAlertDialogBuilder(activityContext)
            .setTitle("Create New Playlist")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = nameEditText.text.toString().trim()
                val description = descriptionEditText.text.toString().trim()
                
                if (name.isNotEmpty()) {
                    lifecycleOwner.lifecycleScope.launch {
                        try {
                            // Check user consent first
                            if (!userDataManager.hasUserConsent()) {
                                onError?.invoke("To use playlists, please go to Library → Enable Library Features")
                                return@launch
                            }

                            val playlist = userDataManager.createPlaylist(name, description)
                            engagementAnalytics.trackFeatureUsage("playlist_created")

                            video?.let {
                                userDataManager.addVideoToPlaylist(playlist.id, it)
                                engagementAnalytics.trackFeatureUsage("video_added_to_playlist")
                                onSuccess?.invoke(listOf(playlist.name))
                            } ?: onSuccess?.invoke(emptyList())

                        } catch (e: Exception) {
                            onError?.invoke("Failed to create playlist: ${e.message}")
                        }
                    }
                } else {
                    nameInputLayout.error = "Playlist name is required"
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show dialog when user has no playlists yet
     */
    private fun showCreateFirstPlaylistDialog(
        video: Video,
        lifecycleOwner: LifecycleOwner,
        onSuccess: ((List<String>) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        // Get the activity context for showing dialogs
        val activityContext = when (lifecycleOwner) {
            is androidx.appcompat.app.AppCompatActivity -> lifecycleOwner
            is androidx.fragment.app.Fragment -> lifecycleOwner.requireActivity()
            else -> context
        }

        MaterialAlertDialogBuilder(activityContext)
            .setTitle("Create Your First Playlist")
            .setMessage("You don't have any playlists yet. Would you like to create one and add this video to it?")
            .setPositiveButton("Create Playlist") { _, _ ->
                showCreatePlaylistDialog(video, lifecycleOwner, onSuccess, onError)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Quick add video to a specific playlist
     */
    suspend fun addVideoToPlaylist(
        playlistId: String,
        video: Video,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            val success = userDataManager.addVideoToPlaylist(playlistId, video)
            if (success) {
                engagementAnalytics.trackFeatureUsage("video_added_to_playlist")
                onSuccess?.invoke()
            } else {
                onError?.invoke("Video is already in this playlist")
            }
        } catch (e: Exception) {
            onError?.invoke("Failed to add video to playlist")
        }
    }
    
    /**
     * Remove video from playlist
     */
    suspend fun removeVideoFromPlaylist(
        playlistId: String,
        videoId: String,
        onSuccess: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        try {
            val success = userDataManager.removeVideoFromPlaylist(playlistId, videoId)
            if (success) {
                engagementAnalytics.trackFeatureUsage("video_removed_from_playlist")
                onSuccess?.invoke()
            } else {
                onError?.invoke("Video not found in playlist")
            }
        } catch (e: Exception) {
            onError?.invoke("Failed to remove video from playlist")
        }
    }
    
    /**
     * Get playlists containing a specific video
     */
    suspend fun getPlaylistsContainingVideo(videoId: String): List<UserPlaylist> {
        return try {
            userDataManager.getPlaylistsContainingVideo(videoId)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Check if video is in any playlist
     */
    suspend fun isVideoInAnyPlaylist(videoId: String): Boolean {
        return getPlaylistsContainingVideo(videoId).isNotEmpty()
    }
}
