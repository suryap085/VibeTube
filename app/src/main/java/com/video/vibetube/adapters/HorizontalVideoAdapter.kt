package com.video.vibetube.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.video.vibetube.R
import com.video.vibetube.models.Video

class HorizontalVideoAdapter : RecyclerView.Adapter<HorizontalVideoAdapter.VideoViewHolder>() {

    private val videoList = mutableListOf<Video>()

    var onVideoClick: ((Video) -> Unit)? = null

    fun setVideos(videos: List<Video>) {
        videoList.clear()
        videoList.addAll(videos)
        notifyDataSetChanged()
    }

    fun addVideos(videos: List<Video>) {
        val insertIndex = videoList.size
        videoList.addAll(videos)
        notifyItemRangeInserted(insertIndex, videos.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_multi_channel, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(videoList[position])
    }

    override fun getItemCount(): Int = videoList.size

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnailImageView: ImageView = itemView.findViewById(R.id.videoThumbnail)
        private val titleTextView: TextView = itemView.findViewById(R.id.videoTitle)
        private val channelTextView: TextView = itemView.findViewById(R.id.videoChannel)
        private val durationTextView: TextView = itemView.findViewById(R.id.videoDuration)

        fun bind(video: Video) {
            titleTextView.text = video.title
            channelTextView.text = video.channelTitle
            durationTextView.text = video.duration.ifEmpty { "" }

            // Load thumbnail using Glide or your preferred image loading library
            Glide.with(itemView.context)
                .load(video.thumbnail)
                .placeholder(R.drawable.thumbnail_placeholder)
                .centerCrop()
                .into(thumbnailImageView)

            itemView.setOnClickListener {
                onVideoClick?.invoke(video)
            }
        }
    }
}