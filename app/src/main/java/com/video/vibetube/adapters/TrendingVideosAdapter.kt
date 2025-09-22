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
) : RecyclerView.Adapter<TrendingVideosAdapter.TrendingVideoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrendingVideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trending_video, parent, false)
        return TrendingVideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TrendingVideoViewHolder, position: Int) {
        holder.bind(videos[position], position + 1)
    }

    override fun getItemCount(): Int = videos.size

    /**
     * Update videos list and notify adapter
     */
    fun updateVideos(newVideos: List<Video>) {
        videos.clear()
        videos.addAll(newVideos)
        notifyDataSetChanged()
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
}
