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
 * YouTube Policy Compliant Trending Videos Adapter
 *
 * This adapter displays trending videos while ensuring compliance
 * with YouTube's terms of service:
 * - Only displays videos from predefined channels
 * - Respects user consent and privacy settings
 * - No unauthorized data collection or sharing
 * - Maintains proper attribution to original YouTube content
 */
class TrendingVideosAdapter(
    private val videos: MutableList<Video>,
    private val onVideoClick: (Video) -> Unit,
    private val lifecycleOwner: LifecycleOwner,
    private val userDataManager: UserDataManager
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val listItems = mutableListOf<Any>()

    companion object {
        private const val VIEW_TYPE_VIDEO = 0
        private const val VIEW_TYPE_REWARDED_AD = 1
        private const val AD_FREQUENCY = 8 // Show ad every 8 trending videos
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
                    .inflate(R.layout.item_trending_video, parent, false)
                TrendingVideoViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TrendingVideoViewHolder -> {
                val video = listItems[position] as Video
                val rank = videos.indexOf(video) + 1
                holder.bind(video, rank)
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

    inner class TrendingVideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.videoCard)
        private val rankTextView: TextView = itemView.findViewById(R.id.rankTextView)
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.thumbnailImageView)
        private val playIconImageView: ImageView = itemView.findViewById(R.id.playIconImageView)
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val channelTextView: TextView = itemView.findViewById(R.id.channelTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        private val publishedTextView: TextView = itemView.findViewById(R.id.publishedTextView)
        private val viewsTextView: TextView = itemView.findViewById(R.id.viewsTextView)
        private val favoriteButton: MaterialButton = itemView.findViewById(R.id.favoriteButton)
        private val trendingIndicator: ImageView = itemView.findViewById(R.id.trendingIndicator)

        fun bind(video: Video, rank: Int) {
            // Set ranking
            rankTextView.text = "#$rank"

            // Set video information
            titleTextView.text = video.title
            channelTextView.text = video.channelTitle
            durationTextView.text = video.duration

            // Show trending rank (view count not available in our model)
            viewsTextView.text = "Trending #$rank"

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

            // Set trending indicator based on rank
            setTrendingIndicator(rank)

            // Setup favorite button
            setupFavoriteButton(video)

            // Set click listeners
            cardView.setOnClickListener {
                onVideoClick(video)
            }
        }

        /**
         * Set trending indicator based on ranking
         */
        private fun setTrendingIndicator(rank: Int) {
            when {
                rank <= 3 -> {
                    trendingIndicator.visibility = View.VISIBLE
                    trendingIndicator.setImageResource(R.drawable.ic_trending_up)
                    trendingIndicator.setColorFilter(
                        itemView.context.getColor(R.color.error)
                    )
                }
                rank <= 10 -> {
                    trendingIndicator.visibility = View.VISIBLE
                    trendingIndicator.setImageResource(R.drawable.ic_trending_up)
                    trendingIndicator.setColorFilter(
                        itemView.context.getColor(R.color.warning)
                    )
                }
                else -> {
                    trendingIndicator.visibility = View.VISIBLE
                    trendingIndicator.setImageResource(R.drawable.ic_trending_up)
                    trendingIndicator.setColorFilter(
                        itemView.context.getColor(R.color.primary)
                    )
                }
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
                            // Add to favorites with trending context
                            val success = userDataManager.addToFavorites(video, sourceContext = "trending")
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
     * RewardedAdViewHolder for rewarded ads in trending videos
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
            android.util.Log.d("TrendingVideosAdapter", "RewardedAdViewHolder bind() called at position: $adapterPosition")

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
                    android.util.Log.w("TrendingVideosAdapter", "Rewarded ad not ready")
                    showErrorState("Ad not ready, please try again")
                }
            }

            skipAdButton.setOnClickListener {
                android.util.Log.d("TrendingVideosAdapter", "User skipped rewarded ad")
                hideAdItem()
            }
        }

        private fun loadRewardedAd() {
            showLoadingState()

            adManager.loadRewardedAd(
                onSuccess = { ad ->
                    android.util.Log.d("TrendingVideosAdapter", "Rewarded ad loaded successfully at position: $adapterPosition")
                    rewardedAd = ad
                    showReadyState()
                },
                onFailure = { error ->
                    android.util.Log.w("TrendingVideosAdapter", "Rewarded ad failed to load at position $adapterPosition: ${error.message}")
                    showErrorState("Ad not available")
                },
                contentContext = AdManager.NON_YOUTUBE_CONTEXT
            )
        }

        private fun showRewardedAd(ad: RewardedAd) {
            adManager.showRewardedAd(
                rewardedAd = ad,
                onRewardEarned = {
                    android.util.Log.d("TrendingVideosAdapter", "User earned reward from ad at position: $adapterPosition")
                    showSuccessMessage()
                    loadRewardedAd()
                },
                onAdClosed = {
                    android.util.Log.d("TrendingVideosAdapter", "Rewarded ad closed at position: $adapterPosition")
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
