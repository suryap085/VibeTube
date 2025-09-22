package com.video.vibetube.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.video.vibetube.R
import com.video.vibetube.models.Video

/**
 * YouTube Policy Compliant Playlist Videos Adapter
 * 
 * This adapter displays videos within a playlist while ensuring
 * compliance with YouTube's terms of service:
 * - Only displays user's local playlist data
 * - Maintains proper attribution to original YouTube content
 * - Respects video availability and access restrictions
 */
class PlaylistVideosAdapter(
    private val videos: MutableList<Video>,
    private val onVideoClick: (Video, Int) -> Unit,
    private val onRemoveClick: (Video) -> Unit,
    private val onMoveUp: ((Int) -> Unit)? = null,
    private val onMoveDown: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<PlaylistVideosAdapter.VideoViewHolder>() {

    inner class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.videoCard)
        val thumbnailImageView: ImageView = itemView.findViewById(R.id.videoThumbnail)
        val titleTextView: TextView = itemView.findViewById(R.id.videoTitle)
        val channelTextView: TextView = itemView.findViewById(R.id.channelName)
        val durationTextView: TextView = itemView.findViewById(R.id.videoDuration)
        val positionTextView: TextView = itemView.findViewById(R.id.positionNumber)
        val removeButton: ImageButton = itemView.findViewById(R.id.removeButton)
        val moveUpButton: ImageButton = itemView.findViewById(R.id.moveUpButton)
        val moveDownButton: ImageButton = itemView.findViewById(R.id.moveDownButton)
        val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)

        fun bind(video: Video, position: Int) {
            titleTextView.text = video.title
            channelTextView.text = video.channelTitle
            durationTextView.text = video.duration.ifEmpty { "0:00" }
            positionTextView.text = (position + 1).toString()

            // Load thumbnail
            Glide.with(itemView.context)
                .load(video.thumbnail)
                .placeholder(R.drawable.ic_video_placeholder)
                .error(R.drawable.ic_video_placeholder)
                .into(thumbnailImageView)

            // Setup click listeners
            cardView.setOnClickListener {
                onVideoClick(video, position)
            }

            removeButton.setOnClickListener {
                onRemoveClick(video)
            }

            // Setup move buttons
            moveUpButton.apply {
                visibility = if (position > 0 && onMoveUp != null) View.VISIBLE else View.GONE
                setOnClickListener { onMoveUp?.invoke(position) }
            }

            moveDownButton.apply {
                visibility = if (position < videos.size - 1 && onMoveDown != null) View.VISIBLE else View.GONE
                setOnClickListener { onMoveDown?.invoke(position) }
            }

            // Show drag handle if reordering is supported
            dragHandle.visibility = if (onMoveUp != null || onMoveDown != null) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_video, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(videos[position], position)
    }

    override fun getItemCount(): Int = videos.size

    fun updateVideos(newVideos: List<Video>) {
        videos.clear()
        videos.addAll(newVideos)
        notifyDataSetChanged()
    }

    fun removeVideo(position: Int) {
        if (position in 0 until videos.size) {
            videos.removeAt(position)
            notifyItemRemoved(position)
            // Update position numbers for remaining items
            notifyItemRangeChanged(position, videos.size - position)
        }
    }

    fun moveVideo(fromPosition: Int, toPosition: Int) {
        if (fromPosition in 0 until videos.size && toPosition in 0 until videos.size) {
            val movedVideo = videos.removeAt(fromPosition)
            videos.add(toPosition, movedVideo)
            notifyItemMoved(fromPosition, toPosition)
            
            // Update position numbers
            val start = minOf(fromPosition, toPosition)
            val count = maxOf(fromPosition, toPosition) - start + 1
            notifyItemRangeChanged(start, count)
        }
    }

    fun getVideo(position: Int): Video? {
        return if (position in 0 until videos.size) videos[position] else null
    }

    fun getVideos(): List<Video> = videos.toList()

    fun isEmpty(): Boolean = videos.isEmpty()

    fun getVideoPosition(videoId: String): Int {
        return videos.indexOfFirst { it.videoId == videoId }
    }

    fun addVideo(video: Video, position: Int = videos.size) {
        val insertPosition = position.coerceIn(0, videos.size)
        videos.add(insertPosition, video)
        notifyItemInserted(insertPosition)
        // Update position numbers for items after the inserted position
        notifyItemRangeChanged(insertPosition, videos.size - insertPosition)
    }

    fun addVideos(newVideos: List<Video>, position: Int = videos.size) {
        val insertPosition = position.coerceIn(0, videos.size)
        videos.addAll(insertPosition, newVideos)
        notifyItemRangeInserted(insertPosition, newVideos.size)
        // Update position numbers for items after the inserted videos
        notifyItemRangeChanged(insertPosition, videos.size - insertPosition)
    }

    fun clearVideos() {
        val size = videos.size
        videos.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun replaceVideo(position: Int, newVideo: Video) {
        if (position in 0 until videos.size) {
            videos[position] = newVideo
            notifyItemChanged(position)
        }
    }

    fun swapVideos(position1: Int, position2: Int) {
        if (position1 in 0 until videos.size && position2 in 0 until videos.size) {
            val temp = videos[position1]
            videos[position1] = videos[position2]
            videos[position2] = temp
            notifyItemChanged(position1)
            notifyItemChanged(position2)
        }
    }

    fun findVideo(videoId: String): Video? {
        return videos.find { it.videoId == videoId }
    }

    fun containsVideo(videoId: String): Boolean {
        return videos.any { it.videoId == videoId }
    }

    fun getVideoCount(): Int = videos.size

    fun getDuration(): String {
        // Calculate total duration of all videos
        var totalSeconds = 0
        videos.forEach { video ->
            totalSeconds += parseDurationToSeconds(video.duration)
        }
        return formatSecondsToTime(totalSeconds)
    }

    private fun parseDurationToSeconds(duration: String): Int {
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                1 -> parts[0].toInt() // seconds only
                2 -> parts[0].toInt() * 60 + parts[1].toInt() // mm:ss
                3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt() // hh:mm:ss
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun formatSecondsToTime(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%d:%02d", minutes, seconds)
        }
    }
}
