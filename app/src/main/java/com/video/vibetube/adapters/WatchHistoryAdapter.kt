package com.video.vibetube.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.video.vibetube.R
import com.video.vibetube.models.WatchHistoryItem
import java.text.SimpleDateFormat
import java.util.*

class WatchHistoryAdapter(
    private val onItemClick: (WatchHistoryItem) -> Unit,
    private val onResumeClick: (WatchHistoryItem) -> Unit,
    private val onDeleteClick: (WatchHistoryItem) -> Unit
) : RecyclerView.Adapter<WatchHistoryAdapter.WatchHistoryViewHolder>() {

    private var historyItems = mutableListOf<WatchHistoryItem>()
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

    inner class WatchHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnailImageView: ImageView = itemView.findViewById(R.id.videoThumbnail)
        val titleTextView: TextView = itemView.findViewById(R.id.videoTitle)
        val channelTextView: TextView = itemView.findViewById(R.id.channelName)
        val durationTextView: TextView = itemView.findViewById(R.id.videoDuration)
        val watchedDateTextView: TextView = itemView.findViewById(R.id.watchedDate)
        val progressIndicator: LinearProgressIndicator = itemView.findViewById(R.id.watchProgress)
        val progressTextView: TextView = itemView.findViewById(R.id.progressText)
        val resumeButton: MaterialButton = itemView.findViewById(R.id.resumeButton)
        val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)
        val categoryChip: TextView = itemView.findViewById(R.id.categoryChip)
        val completedBadge: ImageView = itemView.findViewById(R.id.completedBadge)

        fun bind(historyItem: WatchHistoryItem) {
            titleTextView.text = historyItem.title
            channelTextView.text = historyItem.channelTitle
            durationTextView.text = historyItem.duration
            watchedDateTextView.text = formatWatchedDate(historyItem.watchedAt)
            categoryChip.text = "Video" // WatchHistoryItem doesn't have category, using default

            // Set progress
            val progressPercentage = (historyItem.watchProgress * 100).toInt()

            progressIndicator.progress = progressPercentage
            // Convert milliseconds to seconds for formatDuration
            val watchedSeconds = historyItem.watchDuration / 1000
            progressTextView.text = "${formatDuration(watchedSeconds)} / ${historyItem.duration}"

            // Show/hide completed badge
            if (historyItem.isCompleted) {
                completedBadge.visibility = View.VISIBLE
                resumeButton.text = "Watch Again"
                resumeButton.setIconResource(R.drawable.ic_replay)
            } else {
                completedBadge.visibility = View.GONE
                resumeButton.text = "Resume"
                resumeButton.setIconResource(R.drawable.ic_play_arrow)
            }

            // Set category chip color
            setCategoryChipColor("Video")

            // Load thumbnail using Glide
            if (historyItem.thumbnail.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(historyItem.thumbnail)
                    .placeholder(R.drawable.ic_video_placeholder)
                    .error(R.drawable.ic_video_placeholder)
                    .into(thumbnailImageView)
            } else {
                thumbnailImageView.setImageResource(R.drawable.ic_video_placeholder)
            }

            // Click listeners
            itemView.setOnClickListener {
                onItemClick(historyItem)
            }

            resumeButton.setOnClickListener {
                onResumeClick(historyItem)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(historyItem)
            }
        }

        private fun setCategoryChipColor(category: String) {
            val colorRes = when (category.lowercase()) {
                "music" -> R.color.category_music
                "education" -> R.color.category_education
                "gaming" -> R.color.category_gaming
                "diy" -> R.color.category_diy
                "entertainment" -> R.color.category_entertainment
                else -> R.color.category_default
            }
            
            categoryChip.setBackgroundColor(itemView.context.getColor(colorRes))
        }

        /**
         * Format duration from seconds to readable time string
         * @param seconds Duration in seconds
         * @return Formatted string like "5:30" or "1:05:30"
         */
        private fun formatDuration(seconds: Long): String {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60

            return when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
                else -> String.format("%d:%02d", minutes, secs)
            }
        }

        /**
         * Format duration from string (already formatted)
         * @param duration Pre-formatted duration string like "10:30"
         * @return The same formatted string
         */
        private fun formatDuration(duration: String): String {
            // Duration is already formatted as string, return as-is
            return duration
        }

        private fun formatWatchedDate(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val date = Date(timestamp)

            return when {
                diff < 24 * 60 * 60 * 1000 -> { // Less than 24 hours
                    "Today at ${timeFormatter.format(date)}"
                }
                diff < 2 * 24 * 60 * 60 * 1000 -> { // Less than 48 hours
                    "Yesterday at ${timeFormatter.format(date)}"
                }
                diff < 7 * 24 * 60 * 60 * 1000 -> { // Less than a week
                    val dayOfWeek = SimpleDateFormat("EEEE", Locale.getDefault()).format(date)
                    "$dayOfWeek at ${timeFormatter.format(date)}"
                }
                else -> {
                    dateFormatter.format(date)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WatchHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_watch_history, parent, false)
        return WatchHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: WatchHistoryViewHolder, position: Int) {
        holder.bind(historyItems[position])
    }

    override fun getItemCount(): Int = historyItems.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateItems(newItems: List<WatchHistoryItem>) {
        historyItems.clear()
        historyItems.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeItem(videoId: String) {
        val index = historyItems.indexOfFirst { it.videoId == videoId }
        if (index != -1) {
            historyItems.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun addItem(item: WatchHistoryItem) {
        historyItems.add(0, item) // Add to beginning
        notifyItemInserted(0)
    }

    fun updateItem(item: WatchHistoryItem) {
        val index = historyItems.indexOfFirst { it.videoId == item.videoId }
        if (index != -1) {
            historyItems[index] = item
            notifyItemChanged(index)
        }
    }

    fun clearItems() {
        val size = historyItems.size
        historyItems.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun getItem(position: Int): WatchHistoryItem? {
        return if (position in 0 until historyItems.size) {
            historyItems[position]
        } else {
            null
        }
    }

    fun getItems(): List<WatchHistoryItem> = historyItems.toList()
}
