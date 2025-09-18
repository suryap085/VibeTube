package com.video.vibetube.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.video.vibetube.R
import com.video.vibetube.models.YouTubeSearchItem
import com.video.vibetube.utils.Utility
import java.util.regex.Pattern

class RelatedVideosAdapter(
    private val videos: MutableList<YouTubeSearchItem> = mutableListOf(),
    private val onVideoClickListener: (YouTubeSearchItem) -> Unit
) : RecyclerView.Adapter<RelatedVideosAdapter.VideoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_related_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.bind(video)
    }

    override fun getItemCount(): Int = videos.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateVideos(newVideos: List<YouTubeSearchItem>) {
        videos.clear()
        videos.addAll(newVideos)
        notifyDataSetChanged()
    }

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.video_thumbnail)
        private val title: TextView = itemView.findViewById(R.id.video_title)
        private val channelTitle: TextView = itemView.findViewById(R.id.channel_title)
        private val durationTextView: TextView = itemView.findViewById(R.id.video_duration)

        fun bind(video: YouTubeSearchItem) {
            title.text = video.snippet.title
            channelTitle.text = video.snippet.channelTitle

            Glide.with(itemView.context)
                .load(video.snippet.thumbnails.high?.url)
                .placeholder(R.drawable.thumbnail_placeholder)
                .error(R.drawable.thumbnail_placeholder)
                .into(thumbnail)

            if (video.duration.isNotBlank()) {
                durationTextView.text = Utility.parseDuration(video.duration)
                durationTextView.isVisible = true
            } else {
                durationTextView.isVisible = false
            }

            itemView.setOnClickListener {
                onVideoClickListener(video)
            }
        }

        /*private fun formatDuration(duration: String): String {
            val pattern = Pattern.compile("PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?")
            val matcher = pattern.matcher(duration)
            if (matcher.matches()) {
                val hours = matcher.group(1)?.toInt() ?: 0
                val minutes = matcher.group(2)?.toInt() ?: 0
                val seconds = matcher.group(3)?.toInt() ?: 0

                return when {
                    hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
                    else -> String.format("%d:%02d", minutes, seconds)
                }
            }
            return ""
        }*/
    }
}
