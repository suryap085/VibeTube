package com.video.vibetube.adapters

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import com.google.android.material.button.MaterialButton
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.video.vibetube.R
import com.video.vibetube.models.Video
import com.video.vibetube.utils.AdManager
import com.video.vibetube.utils.PlaylistManager
import com.video.vibetube.utils.SocialManager
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.Utility
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

class ChannelVideosAdapter(
    private val listItems: MutableList<Any>,
    private val onVideoClick: (Video) -> Unit,
    private val lifecycleOwner: LifecycleOwner,
    private val userDataManager: UserDataManager,
    private val socialManager: SocialManager,
    private val playlistManager: PlaylistManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    companion object {
        private const val VIDEO_VIEW_TYPE = 0
        private const val REWARDED_AD_VIEW_TYPE = 1
        const val AD_FREQUENCY = 6
    }

    override fun getItemViewType(position: Int): Int {
        return if (listItems[position] is Video) VIDEO_VIEW_TYPE else REWARDED_AD_VIEW_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIDEO_VIEW_TYPE) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
            VideoViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rewarded_ad, parent, false)
            RewardedAdViewHolder(view)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIDEO_VIEW_TYPE) {
            (holder as VideoViewHolder).bind(listItems[position] as Video)
        } else {
            (holder as RewardedAdViewHolder).bind()
        }
    }

    override fun getItemCount(): Int = listItems.size

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.thumbnailImageView)
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val channelTextView: TextView = itemView.findViewById(R.id.channelTextView)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
        private val publishedTextView: TextView = itemView.findViewById(R.id.publishedTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        private val favoriteButton: MaterialButton = itemView.findViewById(R.id.favoriteButton)
        private val playlistButton: MaterialButton = itemView.findViewById(R.id.playlistButton)
        private val shareButton: MaterialButton = itemView.findViewById(R.id.shareButton)

        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("DefaultLocale")
        fun bind(video: Video) {
            titleTextView.text = video.title
            channelTextView.text = video.channelTitle
            descriptionTextView.text = video.description
            publishedTextView.text = OffsetDateTime.parse(video.publishedAt).toLocalDate().toString()
            if (video.duration.isNotEmpty()) {
                val durationInSeconds = Utility.parseAnyDurationToSeconds(video.duration)
                val minutes = durationInSeconds / 60
                val seconds = durationInSeconds % 60
                durationTextView.text = String.format("%d:%02d", minutes, seconds)
                durationTextView.visibility = View.VISIBLE
            } else {
                durationTextView.visibility = View.GONE
            }
            Glide.with(itemView).load(video.thumbnail).into(thumbnailImageView)
            itemView.setOnClickListener { onVideoClick(video) }

            // Setup favorite button
            setupFavoriteButton(video)

            // Setup playlist button
            setupPlaylistButton(video)

            // Setup share button
            setupShareButton(video)
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
                    Log.e("ChannelVideosAdapter", "Error checking favorite status", e)
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
                            // Add to favorites with channel context
                            val success = userDataManager.addToFavorites(video, sourceContext = "channel")
                            if (success) {
                                updateFavoriteButtonState(true)
                                showToast("Added to favorites")
                            } else {
                                showToast("Library Features is not enabled or Already in favorites or limit reached")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChannelVideosAdapter", "Error toggling favorite", e)
                        showToast("Failed to update favorites")
                    }
                }
            }
        }

        /**
         * Setup playlist button with YouTube Policy compliance
         * - Only stores video metadata (not content)
         * - User-initiated action only
         * - Respects user consent
         */
        private fun setupPlaylistButton(video: Video) {
            // Update button state based on whether video is in any playlist
            lifecycleOwner.lifecycleScope.launch {
                try {
                    val isInPlaylist = playlistManager.isVideoInAnyPlaylist(video.videoId)
                    playlistButton.setIconResource(
                        if (isInPlaylist) R.drawable.ic_playlist_add_check else R.drawable.ic_playlist_add
                    )
                    playlistButton.setIconTintResource(
                        if (isInPlaylist) R.color.primary else R.color.text_secondary
                    )
                } catch (e: Exception) {
                    Log.e("ChannelVideosAdapter", "Error checking playlist status", e)
                }
            }

            playlistButton.setOnClickListener {
                try {
                    playlistManager.showAddToPlaylistDialog(
                        video = video,
                        lifecycleOwner = lifecycleOwner,
                        onSuccess = { addedPlaylists ->
                            if (addedPlaylists.isNotEmpty()) {
                                showToast("Added to ${addedPlaylists.joinToString(", ")}")
                                // Update button state after successful addition
                                setupPlaylistButton(video)
                            }
                        },
                        onError = { error ->
                            showToast("Failed to add to playlist: $error")
                        }
                    )
                } catch (e: Exception) {
                    Log.e("ChannelVideosAdapter", "Error adding to playlist", e)
                    showToast("Failed to add to playlist")
                }
            }
        }

        /**
         * Setup share button with YouTube Policy compliance
         * - Shares YouTube video links only (not content)
         * - Proper YouTube attribution
         * - User-initiated sharing only
         */
        private fun setupShareButton(video: Video) {
            shareButton.setOnClickListener {
                try {
                    // Use SocialManager for YouTube Policy compliant sharing
                    socialManager.shareVideo(video.videoId, video.title)

                    // Optional: Show confirmation
                    showToast("Sharing video...")
                } catch (e: Exception) {
                    Log.e("ChannelVideosAdapter", "Error sharing video", e)
                    showToast("Failed to share video")
                }
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

    inner class RewardedAdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val watchAdButton: MaterialButton = itemView.findViewById(R.id.watchAdButton)
        private val skipAdButton: MaterialButton = itemView.findViewById(R.id.skipAdButton)
        private val loadingLayout: View = itemView.findViewById(R.id.loadingLayout)
        private val errorLayout: View = itemView.findViewById(R.id.errorLayout)
        private val errorTextView: TextView = itemView.findViewById(R.id.errorTextView)

        private var rewardedAd: RewardedAd? = null
        private val adManager = AdManager(itemView.context)

        fun bind() {
            Log.d("ChannelVideosAdapter", "RewardedAdViewHolder bind() called at position: $adapterPosition")

            // Reset UI state
            showDefaultState()

            // Set up click listeners
            setupClickListeners()

            // Pre-load rewarded ad
            loadRewardedAd()
        }

        private fun showDefaultState() {
            watchAdButton.visibility = View.VISIBLE
            skipAdButton.visibility = View.VISIBLE
            loadingLayout.visibility = View.GONE
            errorLayout.visibility = View.GONE
            watchAdButton.isEnabled = false // Disabled until ad loads
        }

        private fun showLoadingState() {
            watchAdButton.visibility = View.GONE
            skipAdButton.visibility = View.VISIBLE
            loadingLayout.visibility = View.VISIBLE
            errorLayout.visibility = View.GONE
        }

        private fun showErrorState(message: String) {
            watchAdButton.visibility = View.GONE
            skipAdButton.visibility = View.VISIBLE
            loadingLayout.visibility = View.GONE
            errorLayout.visibility = View.VISIBLE
            errorTextView.text = message
        }

        private fun showReadyState() {
            watchAdButton.visibility = View.VISIBLE
            skipAdButton.visibility = View.VISIBLE
            loadingLayout.visibility = View.GONE
            errorLayout.visibility = View.GONE
            watchAdButton.isEnabled = true
        }

        private fun setupClickListeners() {
            watchAdButton.setOnClickListener {
                rewardedAd?.let { ad ->
                    showRewardedAd(ad)
                } ?: run {
                    Log.w("ChannelVideosAdapter", "Rewarded ad not ready")
                    showErrorState("Ad not ready, please try again")
                }
            }

            skipAdButton.setOnClickListener {
                Log.d("ChannelVideosAdapter", "User skipped rewarded ad")
                // Hide the ad item gracefully
                hideAdItem()
            }
        }

        private fun loadRewardedAd() {
            showLoadingState()

            adManager.loadRewardedAd(
                onSuccess = { ad ->
                    Log.d("ChannelVideosAdapter", "Rewarded ad loaded successfully at position: $adapterPosition")
                    rewardedAd = ad
                    showReadyState()
                },
                onFailure = { error ->
                    Log.w("ChannelVideosAdapter", "Rewarded ad failed to load at position $adapterPosition: ${error.message}")
                    showErrorState("Ad not available")
                },
                contentContext = AdManager.YOUTUBE_CONTENT_CONTEXT
            )
        }

        private fun showRewardedAd(ad: RewardedAd) {
            adManager.showRewardedAd(
                rewardedAd = ad,
                onRewardEarned = {
                    Log.d("ChannelVideosAdapter", "User earned reward from ad at position: $adapterPosition")
                    // Show success message or update UI
                    showSuccessMessage()
                    // Load next ad for future use
                    loadRewardedAd()
                },
                onAdClosed = {
                    Log.d("ChannelVideosAdapter", "Rewarded ad closed at position: $adapterPosition")
                    // Optionally hide the ad item after viewing
                    hideAdItem()
                }
            )
        }

        private fun showSuccessMessage() {
            // Temporarily show success state
            watchAdButton.text = "✓ Reward Earned!"
            watchAdButton.isEnabled = false

            // Reset after delay
            itemView.postDelayed({
                hideAdItem()
            }, 2000)
        }

        private fun hideAdItem() {
            itemView.visibility = View.GONE
            val params = itemView.layoutParams
            params.height = 0
            itemView.layoutParams = params
        }


    }
}
