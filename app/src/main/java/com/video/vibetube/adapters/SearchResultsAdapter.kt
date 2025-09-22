package com.video.vibetube.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.video.vibetube.R
import com.video.vibetube.models.Video
import com.video.vibetube.utils.SocialManager
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.Utility.formatDate
import kotlinx.coroutines.launch

class SearchResultsAdapter(
    private val videos: List<Video>,
    private val onVideoClick: (Video) -> Unit,
    private val onLoadMore: () -> Unit,
    private val lifecycleOwner: LifecycleOwner,
    private val userDataManager: UserDataManager,
    private val socialManager: SocialManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private lateinit var playlistManager: com.video.vibetube.utils.PlaylistManager

    companion object {
        private const val VIEW_TYPE_VIDEO = 0
        private const val VIEW_TYPE_LOADING = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == videos.size) VIEW_TYPE_LOADING else VIEW_TYPE_VIDEO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_VIDEO -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_search_video, parent, false)
                VideoViewHolder(view)
            }
            VIEW_TYPE_LOADING -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_loading, parent, false)
                LoadingViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is VideoViewHolder -> {
                val video = videos[position]
                holder.bind(video)
                holder.itemView.setOnClickListener { onVideoClick(video) }
            }
            is LoadingViewHolder -> {
                // Trigger load more when loading view becomes visible
                onLoadMore()
            }
        }
    }

    override fun getItemCount(): Int {
        // Add 1 for loading view if there are videos
        return videos.size + if (videos.isNotEmpty()) 1 else 0
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.videoThumbnail)
        private val titleTextView: TextView = itemView.findViewById(R.id.videoTitle)
        private val channelTextView: TextView = itemView.findViewById(R.id.channelName)
        private val publishedTextView: TextView = itemView.findViewById(R.id.publishedDate)
        private val durationTextView: TextView = itemView.findViewById(R.id.videoDuration)
        private val favoriteButton: MaterialButton = itemView.findViewById(R.id.favoriteButton)
        private val shareButton: MaterialButton = itemView.findViewById(R.id.shareButton)
        private val playlistButton: MaterialButton = itemView.findViewById(R.id.playlistButton)

        init {
            // Initialize PlaylistManager
            if (!::playlistManager.isInitialized) {
                playlistManager = com.video.vibetube.utils.PlaylistManager.getInstance(itemView.context)
            }
        }

        fun bind(video: Video) {
            // Load thumbnail
            Glide.with(itemView.context)
                .load(video.thumbnail)
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_placeholder)
                .into(thumbnailImageView)

            titleTextView.text = video.title
            channelTextView.text = video.channelTitle
            publishedTextView.text = formatDate(video.publishedAt)

            // Show duration if available
            if (video.duration.isNotEmpty()) {
                durationTextView.text = video.duration
                durationTextView.visibility = View.VISIBLE
            } else {
                durationTextView.visibility = View.GONE
            }

            // Setup favorite button
            setupFavoriteButton(video)

            // Setup share button
            setupShareButton(video)

            // Setup playlist button
            setupPlaylistButton(video)
        }

        /**
         * Setup favorite button with YouTube Policy compliance
         * - Only stores video metadata (not content)
         * - User-initiated action only
         * - Respects user consent
         */
        private fun setupFavoriteButton(video: Video) {
            // Check current favorite status and update UI
            lifecycleOwner.lifecycleScope.launch {
                try {
                    val isFavorite = userDataManager.isFavorite(video.videoId)
                    updateFavoriteButtonState(isFavorite)
                } catch (e: Exception) {
                    Log.e("SearchResultsAdapter", "Error checking favorite status", e)
                }
            }

            // Set click listener for favorite toggle
            favoriteButton.setOnClickListener {
                lifecycleOwner.lifecycleScope.launch {
                    try {
                        val currentlyFavorite = userDataManager.isFavorite(video.videoId)

                        if (currentlyFavorite) {
                            // Remove from favorites
                            userDataManager.removeFromFavorites(video.videoId)
                            updateFavoriteButtonState(false)
                            showToast("Removed from favorites")
                        } else {
                            // Add to favorites with search context
                            val success = userDataManager.addToFavorites(video, sourceContext = "search")
                            if (success) {
                                updateFavoriteButtonState(true)
                                showToast("Added to favorites")
                            } else {
                                showToast("Already in favorites or limit reached")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SearchResultsAdapter", "Error toggling favorite", e)
                        showToast("Failed to update favorites")
                    }
                }
            }
        }

        /**
         * Setup share button functionality
         */
        private fun setupShareButton(video: Video) {
            shareButton.setOnClickListener {
                try {
                    socialManager.shareVideo(video)
                } catch (e: Exception) {
                    Log.e("SearchResultsAdapter", "Error sharing video", e)
                    showToast("Failed to share video")
                }
            }
        }

        /**
         * Setup playlist button functionality
         * YouTube Policy Compliance: Only operates on user's local playlists
         */
        private fun setupPlaylistButton(video: Video) {
            // Update button state based on whether video is in any playlist
            lifecycleOwner.lifecycleScope.launch {
                val isInPlaylist = playlistManager.isVideoInAnyPlaylist(video.videoId)
                playlistButton.setIconResource(
                    if (isInPlaylist) R.drawable.ic_playlist_add_check else R.drawable.ic_playlist_add
                )
                playlistButton.setIconTintResource(
                    if (isInPlaylist) R.color.primary else R.color.text_secondary
                )
            }

            playlistButton.setOnClickListener {
                playlistManager.showAddToPlaylistDialog(
                    video = video,
                    lifecycleOwner = lifecycleOwner,
                    onSuccess = { addedPlaylists ->
                        if (addedPlaylists.isNotEmpty()) {
                            showToast("Added to ${addedPlaylists.joinToString(", ")}")
                            // Update button state
                            setupPlaylistButton(video)
                        }
                    },
                    onError = { error ->
                        showToast(error)
                    }
                )
            }
        }

        /**
         * Update favorite button visual state
         */
        private fun updateFavoriteButtonState(isFavorite: Boolean) {
            if (isFavorite) {
                favoriteButton.setIconResource(R.drawable.ic_favorite)
                favoriteButton.setIconTintResource(R.color.primary)
                favoriteButton.contentDescription = "Remove from favorites"
            } else {
                favoriteButton.setIconResource(R.drawable.ic_favorite_border)
                favoriteButton.setIconTintResource(R.color.text_secondary)
                favoriteButton.contentDescription = "Add to favorites"
            }
        }

        /**
         * Show toast message
         */
        private fun showToast(message: String) {
            Toast.makeText(itemView.context, message, Toast.LENGTH_SHORT).show()
        }
    }

    class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        
        init {
            progressBar.visibility = View.VISIBLE
        }
    }
}
