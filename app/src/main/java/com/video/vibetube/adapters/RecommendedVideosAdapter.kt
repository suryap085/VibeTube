package com.video.vibetube.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.video.vibetube.R
import com.video.vibetube.models.Video
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.Utility
import com.video.vibetube.utils.AdManager
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

/**
 * YouTube Policy Compliant Recommended Videos Adapter
 * 
 * This adapter displays recommended videos while ensuring compliance
 * with YouTube's terms of service:
 * - Only displays videos from user's local data and preferences
 * - Respects user consent and privacy settings
 * - No unauthorized data collection or sharing
 * - Maintains proper attribution to original YouTube content
 */
class RecommendedVideosAdapter(
    private val videos: MutableList<Video>,
    private val onVideoClick: (Video) -> Unit,
    private val lifecycleOwner: LifecycleOwner,
    private val userDataManager: UserDataManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val listItems = mutableListOf<Any>()

    companion object {
        private const val VIEW_TYPE_VIDEO = 0
        private const val VIEW_TYPE_REWARDED_AD = 1
        private const val AD_FREQUENCY = 6 // Show ad every 6 recommended videos
    }

    override fun getItemViewType(position: Int): Int {
        return if (listItems[position] is Video) VIEW_TYPE_VIDEO else VIEW_TYPE_REWARDED_AD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_REWARDED_AD -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_rewarded_ad, parent, false)
                RewardedAdViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_recommended_video, parent, false)
                RecommendedVideoViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is RecommendedVideoViewHolder -> {
                val video = listItems[position] as Video
                holder.bind(video)
            }
            is RewardedAdViewHolder -> holder.bind()
        }
    }

    override fun getItemCount(): Int = listItems.size

    /**
     * Update videos list with ads and notify adapter
     */
    fun updateVideos(newVideos: List<Video>) {
        videos.clear()
        videos.addAll(newVideos)
        updateListWithAds()
        notifyDataSetChanged()
    }

    /**
     * Update list items with ads interspersed
     */
    private fun updateListWithAds() {
        listItems.clear()
        var adCounter = 0
        videos.forEach { video ->
            listItems.add(video)
            adCounter++
            if (adCounter % AD_FREQUENCY == 0) {
                listItems.add(Any()) // Ad placeholder
            }
        }
    }

    inner class RecommendedVideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.videoCard)
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.thumbnailImageView)
        private val playIconImageView: ImageView = itemView.findViewById(R.id.playIconImageView)
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val channelTextView: TextView = itemView.findViewById(R.id.channelTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        private val publishedTextView: TextView = itemView.findViewById(R.id.publishedTextView)
        private val viewsTextView: TextView = itemView.findViewById(R.id.viewsTextView)
        private val favoriteButton: MaterialButton = itemView.findViewById(R.id.favoriteButton)
        private val recommendationReasonTextView: TextView = itemView.findViewById(R.id.recommendationReasonTextView)

        fun bind(video: Video) {
            // Set video information
            titleTextView.text = video.title
            channelTextView.text = video.channelTitle
            durationTextView.text = video.duration

            // Hide view count for recommended videos (not available in our model)
            viewsTextView.visibility = View.GONE

            // Format published date
            publishedTextView.text = if (video.publishedAt.isNotEmpty()) {
                val formattedDate = Utility.formatDate(video.publishedAt)
                if (formattedDate == "Unknown") "Recently" else formattedDate
            } else {
                "Recently"
            }

            // Load thumbnail with Glide
            Glide.with(itemView.context)
                .load(video.thumbnail)
                .transform(RoundedCorners(16))
                .placeholder(R.drawable.ic_video_placeholder)
                .error(R.drawable.ic_video_placeholder)
                .into(thumbnailImageView)

            // Set recommendation reason (YouTube Policy Compliance: Transparent recommendations)
            setRecommendationReason(video)

            // Setup favorite button
            setupFavoriteButton(video)

            // Set click listeners
            cardView.setOnClickListener {
                onVideoClick(video)
            }
        }

        /**
         * Set recommendation reason for transparency
         * YouTube Policy Compliance: Users should understand why content is recommended
         */
        private fun setRecommendationReason(video: Video) {
            lifecycleOwner.lifecycleScope.launch {
                try {
                    val reason = determineRecommendationReason(video)
                    recommendationReasonTextView.text = reason
                    recommendationReasonTextView.visibility = if (reason.isNotEmpty()) View.VISIBLE else View.GONE
                } catch (e: Exception) {
                    recommendationReasonTextView.visibility = View.GONE
                }
            }
        }

        /**
         * Determine why this video was recommended (for transparency)
         * YouTube Policy Compliance: Transparent recommendation logic
         */
        private suspend fun determineRecommendationReason(video: Video): String {
            try {
                val watchHistory = userDataManager.getWatchHistory()
                val favoriteVideos = userDataManager.getFavorites()
                
                // Check if from frequently watched channel
                val channelWatchCount = watchHistory.count { it.channelId == video.channelId }
                if (channelWatchCount >= 3) {
                    return "From ${video.channelTitle} • You watch this channel often"
                }
                
                // Check if from favorited channel
                val hasFavoriteFromChannel = favoriteVideos.any { it.channelId == video.channelId }
                if (hasFavoriteFromChannel) {
                    return "From ${video.channelTitle} • You have favorites from this channel"
                }
                
                // Check if similar category
                val categoryWatchCount = watchHistory.count { it.channelTitle == video.channelTitle }
                if (categoryWatchCount >= 2) {
                    return "Similar to your watched videos"
                }

                // Check if similar duration to completed videos
                val completedVideos = watchHistory.filter { it.isCompleted }
                if (completedVideos.isNotEmpty()) {
                    val avgCompletedDuration = completedVideos.map { Utility.parseAnyDurationToSeconds(it.duration) }.average()
                    val videoDurationSeconds = Utility.parseAnyDurationToSeconds(video.duration)
                    if (videoDurationSeconds > 0 && kotlin.math.abs(videoDurationSeconds - avgCompletedDuration) < avgCompletedDuration * 0.5) {
                        return "Similar length to videos you complete"
                    }
                }
                
                return "Recommended for you"
                
            } catch (e: Exception) {
                return ""
            }
        }

        /**
         * Get user-friendly category name
         */
        private fun getCategoryDisplayName(categoryId: String?): String {
            return when (categoryId) {
                "music" -> "music"
                "education" -> "educational"
                "gaming" -> "gaming"
                "diy" -> "DIY"
                "entertainment" -> "entertainment"
                "comedy" -> "comedy"
                "movies" -> "movie"
                else -> "similar"
            }
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
                    // Handle error silently
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
                            // Add to favorites with recommendation context
                            val success = userDataManager.addToFavorites(video, sourceContext = "recommendations")
                            if (success) {
                                updateFavoriteButtonState(true)
                                showToast("Added to favorites")
                            } else {
                                showToast("Already in favorites or limit reached")
                            }
                        }
                    } catch (e: Exception) {
                        showToast("Failed to update favorites")
                    }
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

    /**
     * RewardedAdViewHolder for rewarded ads in recommended videos
     */
    inner class RewardedAdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val watchAdButton: MaterialButton = itemView.findViewById(R.id.watchAdButton)
        private val skipAdButton: MaterialButton = itemView.findViewById(R.id.skipAdButton)
        private val loadingLayout: View = itemView.findViewById(R.id.loadingLayout)
        private val errorLayout: View = itemView.findViewById(R.id.errorLayout)
        private val errorTextView: TextView = itemView.findViewById(R.id.errorTextView)

        private var rewardedAd: RewardedAd? = null
        private val adManager = AdManager(itemView.context)

        fun bind() {
            android.util.Log.d("RecommendedVideosAdapter", "RewardedAdViewHolder bind() called at position: $adapterPosition")

            showDefaultState()
            setupClickListeners()
            loadRewardedAd()
        }

        private fun showDefaultState() {
            watchAdButton.visibility = View.VISIBLE
            skipAdButton.visibility = View.VISIBLE
            loadingLayout.visibility = View.GONE
            errorLayout.visibility = View.GONE
            watchAdButton.isEnabled = false
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
                    android.util.Log.w("RecommendedVideosAdapter", "Rewarded ad not ready")
                    showErrorState("Ad not ready, please try again")
                }
            }

            skipAdButton.setOnClickListener {
                android.util.Log.d("RecommendedVideosAdapter", "User skipped rewarded ad")
                hideAdItem()
            }
        }

        private fun loadRewardedAd() {
            showLoadingState()

            adManager.loadRewardedAd(
                onSuccess = { ad ->
                    android.util.Log.d("RecommendedVideosAdapter", "Rewarded ad loaded successfully at position: $adapterPosition")
                    rewardedAd = ad
                    showReadyState()
                },
                onFailure = { error ->
                    android.util.Log.w("RecommendedVideosAdapter", "Rewarded ad failed to load at position $adapterPosition: ${error.message}")
                    showErrorState("Ad not available")
                },
                contentContext = AdManager.NON_YOUTUBE_CONTEXT
            )
        }

        private fun showRewardedAd(ad: RewardedAd) {
            adManager.showRewardedAd(
                rewardedAd = ad,
                onRewardEarned = {
                    android.util.Log.d("RecommendedVideosAdapter", "User earned reward from ad at position: $adapterPosition")
                    showSuccessMessage()
                    loadRewardedAd()
                },
                onAdClosed = {
                    android.util.Log.d("RecommendedVideosAdapter", "Rewarded ad closed at position: $adapterPosition")
                    hideAdItem()
                }
            )
        }

        private fun showSuccessMessage() {
            watchAdButton.text = "✓ Reward Earned!"
            watchAdButton.isEnabled = false

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
