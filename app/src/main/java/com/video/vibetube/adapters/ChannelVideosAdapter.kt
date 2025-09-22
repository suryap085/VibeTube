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
        private const val AD_VIEW_TYPE = 1
        const val AD_FREQUENCY = 6
    }

    override fun getItemViewType(position: Int): Int {
        return if (listItems[position] is Video) VIDEO_VIEW_TYPE else AD_VIEW_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIDEO_VIEW_TYPE) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false)
            VideoViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_native_ad, parent, false)
            AdViewHolder(view)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (getItemViewType(position) == VIDEO_VIEW_TYPE) {
            (holder as VideoViewHolder).bind(listItems[position] as Video)
        } else {
            (holder as AdViewHolder).bind()
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

    inner class AdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val adView: NativeAdView = itemView.findViewById(R.id.native_ad_view)

        fun bind() {
            val adManager = AdManager(itemView.context)
            adManager.loadNativeAd(
                onSuccess = { nativeAd ->
                    // We are executing on the main thread, no need to post.
                    populateNativeAdView(nativeAd, adView)
                    itemView.visibility = View.VISIBLE
                    val params = itemView.layoutParams
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    itemView.layoutParams = params
                },
                onFailure = { loadAdError ->
                    // This is the fix for the blank space!
                    Log.e("ChannelVideosAdapter", "Native ad failed to load: ${loadAdError.message}")
                    // Hide the view completely on failure.
                    itemView.visibility = View.GONE
                    val params = itemView.layoutParams
                    params.height = 0
                    itemView.layoutParams = params
                }
            )
        }

        private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
            // Set the media view.
            adView.mediaView = adView.findViewById(R.id.ad_media)

            // Set other assets.
            adView.headlineView = adView.findViewById(R.id.ad_headline)
            adView.bodyView = adView.findViewById(R.id.ad_body)
            adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
            adView.iconView = adView.findViewById(R.id.ad_app_icon)
            adView.priceView = adView.findViewById(R.id.ad_price)
            adView.starRatingView = adView.findViewById(R.id.ad_stars)
            adView.storeView = adView.findViewById(R.id.ad_store)
            adView.advertiserView = adView.findViewById(R.id.ad_advertiser)

            // The headline and media content are guaranteed to be in every NativeAd.
            (adView.headlineView as TextView).text = nativeAd.headline
            adView.mediaView?.mediaContent = nativeAd.mediaContent

            // These assets aren't guaranteed to be in every NativeAd, so it's important to
            // check before trying to display them.
            nativeAd.body?.let {
                (adView.bodyView as TextView).text = it
                adView.bodyView?.visibility = View.VISIBLE
            } ?: run {
                adView.bodyView?.visibility = View.GONE
            }

            nativeAd.callToAction?.let {
                (adView.callToActionView as Button).text = it
                adView.callToActionView?.visibility = View.VISIBLE
            } ?: run {
                adView.callToActionView?.visibility = View.GONE
            }

            nativeAd.icon?.let {
                (adView.iconView as ImageView).setImageDrawable(it.drawable)
                adView.iconView?.visibility = View.VISIBLE
            } ?: run {
                adView.iconView?.visibility = View.GONE
            }

            nativeAd.price?.let {
                (adView.priceView as TextView).text = it
                adView.priceView?.visibility = View.VISIBLE
            } ?: run {
                adView.priceView?.visibility = View.GONE
            }

            nativeAd.store?.let {
                (adView.storeView as TextView).text = it
                adView.storeView?.visibility = View.VISIBLE
            } ?: run {
                adView.storeView?.visibility = View.GONE
            }

            nativeAd.starRating?.let { rating ->
                if (rating > 0) {
                    (adView.starRatingView as RatingBar).rating = rating.toFloat()
                    adView.starRatingView?.visibility = View.VISIBLE
                } else {
                    adView.starRatingView?.visibility = View.GONE
                }
            } ?: run {
                adView.starRatingView?.visibility = View.GONE
            }

            nativeAd.advertiser?.let {
                (adView.advertiserView as TextView).text = it
                adView.advertiserView?.visibility = View.VISIBLE
            } ?: run {
                adView.advertiserView?.visibility = View.GONE
            }

            // This method tells the Google Mobile Ads SDK that you have finished populating your
            // native ad view with this native ad.
            adView.setNativeAd(nativeAd)
        }
    }
}
