package com.video.vibetube.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.video.vibetube.R
import com.video.vibetube.models.Video
import com.video.vibetube.utils.AdManager

class VideoAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val items = mutableListOf<Any>()
    private lateinit var adManager: AdManager
    var onVideoClick: ((Video) -> Unit)? = null

    companion object {
        private const val TYPE_VIDEO = 0
        private const val TYPE_AD = 1
        private const val AD_FREQUENCY = 5 // Show ad every 5 items
    }

    fun setupAds(adManager: AdManager) {
        this.adManager = adManager
    }

    // Add this property
    var videoList: List<Video> = emptyList()
        private set

    // Update setVideos method
    @SuppressLint("NotifyDataSetChanged")
    fun setVideos(newVideos: List<Video>) {
        videoList = newVideos
        items.clear()
        addVideosWithAds(newVideos)
        notifyDataSetChanged()
    }

    // Update addVideos method
    fun addVideos(newVideos: List<Video>) {
        videoList = videoList + newVideos
        val startPosition = items.size
        addVideosWithAds(newVideos)
        notifyItemRangeInserted(startPosition, newVideos.size)
    }

    private fun addVideosWithAds(videos: List<Video>) {
        for (i in videos.indices) {
            items.add(videos[i])
            // Add ad placeholder every AD_FREQUENCY items
            if ((items.size) % AD_FREQUENCY == 0) {
                items.add("AD_PLACEHOLDER")
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearVideos() {
        videoList = emptyList()
        items.clear()
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is String) TYPE_AD else TYPE_VIDEO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_AD -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_native_ad, parent, false)
                AdViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_video, parent, false)
                VideoViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is VideoViewHolder -> holder.bind(items[position] as Video)
            is AdViewHolder -> {
                // Native ad will be loaded here
                // This is a placeholder for native ads
            }
        }
    }

    override fun getItemCount(): Int = items.size

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.thumbnailImageView)
        private val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        private val channelTextView: TextView = itemView.findViewById(R.id.channelTextView)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
        private val publishedTextView: TextView = itemView.findViewById(R.id.publishedTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)

        fun bind(video: Video) {
            titleTextView.text = video.title
            channelTextView.text = video.channelTitle
            descriptionTextView.text = video.description
            publishedTextView.text = formatPublishedDate(video.publishedAt)

            // Set duration
            if (video.duration.isNotEmpty()) {
                durationTextView.text = formatDuration(video.duration)
                durationTextView.visibility = View.VISIBLE
            } else {
                durationTextView.visibility = View.GONE
            }

            Glide.with(itemView.context)
                .load(video.thumbnail)
                .placeholder(R.drawable.ic_placeholder)
                .into(thumbnailImageView)

            itemView.setOnClickListener {
                onVideoClick?.invoke(video)
            }
        }

        private fun formatPublishedDate(dateString: String): String {
            return try {
                val parts = dateString.split("T")[0].split("-")
                "${parts[2]}/${parts[1]}/${parts[0]}"
            } catch (_: Exception) {
                "Recently"
            }
        }

        @SuppressLint("DefaultLocale")
        private fun formatDuration(duration: String): String {
            return try {
                // Parse ISO 8601 duration format (PT4M13S -> 4:13)
                val regex = "PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?".toRegex()
                val matchResult = regex.find(duration)

                if (matchResult != null) {
                    val hours = matchResult.groupValues[1].toIntOrNull() ?: 0
                    val minutes = matchResult.groupValues[2].toIntOrNull() ?: 0
                    val seconds = matchResult.groupValues[3].toIntOrNull() ?: 0

                    return when {
                        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
                        else -> String.format("%d:%02d", minutes, seconds)
                    }
                }
                "Live"
            } catch (_: Exception) {
                "N/A"
            }
        }
    }

    inner class AdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Native ad view holder
        // Implementation depends on your native ad layout
    }
}