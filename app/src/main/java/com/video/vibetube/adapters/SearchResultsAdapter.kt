package com.video.vibetube.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.video.vibetube.R
import com.video.vibetube.models.Video
import com.video.vibetube.utils.Utility.formatDate

class SearchResultsAdapter(
    private val videos: List<Video>,
    private val onVideoClick: (Video) -> Unit,
    private val onLoadMore: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

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

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.videoThumbnail)
        private val titleTextView: TextView = itemView.findViewById(R.id.videoTitle)
        private val channelTextView: TextView = itemView.findViewById(R.id.channelName)
        private val publishedTextView: TextView = itemView.findViewById(R.id.publishedDate)
        private val durationTextView: TextView = itemView.findViewById(R.id.videoDuration)

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
        }
    }

    class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        
        init {
            progressBar.visibility = View.VISIBLE
        }
    }
}
