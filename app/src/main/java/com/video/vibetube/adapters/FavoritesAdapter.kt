package com.video.vibetube.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.video.vibetube.R
import com.video.vibetube.models.FavoriteItem
import com.video.vibetube.utils.UserDataManager
import java.text.SimpleDateFormat
import java.util.*

class FavoritesAdapter(
    private val onItemClick: (FavoriteItem) -> Unit,
    private val onItemLongClick: (FavoriteItem) -> Unit,
    private val onFavoriteClick: (FavoriteItem) -> Unit,
    private val onShareClick: (FavoriteItem) -> Unit,
    private val onPlaylistClick: ((FavoriteItem) -> Unit)? = null
) : RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder>() {

    private var favorites = mutableListOf<FavoriteItem>()
    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<String>()
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    inner class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.favoriteCard)
        val thumbnailImageView: ImageView = itemView.findViewById(R.id.videoThumbnail)
        val titleTextView: TextView = itemView.findViewById(R.id.videoTitle)
        val channelTextView: TextView = itemView.findViewById(R.id.channelName)
        val durationTextView: TextView = itemView.findViewById(R.id.videoDuration)
        val addedDateTextView: TextView = itemView.findViewById(R.id.addedDate)
        val favoriteButton: MaterialButton = itemView.findViewById(R.id.favoriteButton)
        val playlistButton: MaterialButton = itemView.findViewById(R.id.playlistButton)
        val shareButton: MaterialButton = itemView.findViewById(R.id.shareButton)
        val categoryChip: TextView = itemView.findViewById(R.id.categoryChip)
        val selectionCheckBox: CheckBox = itemView.findViewById(R.id.selectionCheckBox)

        fun bind(favorite: FavoriteItem) {
            titleTextView.text = favorite.title
            channelTextView.text = favorite.channelTitle
            durationTextView.text = favorite.duration
            addedDateTextView.text = "Added ${formatAddedDate(favorite.addedAt)}"

            // Handle category display with proper fallback
            val displayCategory = if (favorite.category.isBlank() || favorite.category == "default") {
                "General"
            } else {
                favorite.category.replaceFirstChar { it.uppercase() }
            }
            categoryChip.text = displayCategory

            // Set category chip color
            setCategoryChipColor(favorite.category)

            // Load thumbnail using Glide
            if (favorite.thumbnail.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(favorite.thumbnail)
                    .placeholder(R.drawable.ic_video_placeholder)
                    .error(R.drawable.ic_video_placeholder)
                    .into(thumbnailImageView)
            } else {
                thumbnailImageView.setImageResource(R.drawable.ic_video_placeholder)
            }

            // Selection mode handling
            if (isSelectionMode) {
                selectionCheckBox.visibility = View.VISIBLE
                selectionCheckBox.isChecked = selectedItems.contains(favorite.videoId)
                
                // Highlight selected items
                if (selectedItems.contains(favorite.videoId)) {
                    cardView.strokeWidth = 4
                    cardView.strokeColor = itemView.context.getColor(R.color.primary)
                } else {
                    cardView.strokeWidth = 0
                }
            } else {
                selectionCheckBox.visibility = View.GONE
                cardView.strokeWidth = 0
            }

            // Click listeners
            itemView.setOnClickListener {
                onItemClick(favorite)
            }

            itemView.setOnLongClickListener {
                onItemLongClick(favorite)
                true
            }

            favoriteButton.setOnClickListener {
                onFavoriteClick(favorite)
            }

            playlistButton.setOnClickListener {
                onPlaylistClick?.invoke(favorite)
            }

            shareButton.setOnClickListener {
                onShareClick(favorite)
            }

            selectionCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedItems.add(favorite.videoId)
                } else {
                    selectedItems.remove(favorite.videoId)
                }
                
                // Update card appearance
                if (isChecked) {
                    cardView.strokeWidth = 4
                    cardView.strokeColor = itemView.context.getColor(R.color.primary)
                } else {
                    cardView.strokeWidth = 0
                }
            }
        }

        private fun setCategoryChipColor(category: String) {
            val colorRes = when (category.lowercase()) {
                "music" -> R.color.category_music
                "education" -> R.color.category_education
                "gaming" -> R.color.category_gaming
                "diy" -> R.color.category_diy
                "entertainment" -> R.color.category_entertainment
                "default", "" -> R.color.category_default
                else -> R.color.category_default
            }

            categoryChip.setBackgroundColor(itemView.context.getColor(colorRes))
        }

        private fun formatDuration(seconds: Long): String {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60

            return when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
                else -> String.format("%d:%02d", minutes, secs)
            }
        }

        private fun formatAddedDate(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val date = Date(timestamp)

            return when {
                diff < 24 * 60 * 60 * 1000 -> "today"
                diff < 2 * 24 * 60 * 60 * 1000 -> "yesterday"
                diff < 7 * 24 * 60 * 60 * 1000 -> {
                    val days = (diff / (24 * 60 * 60 * 1000)).toInt()
                    "$days days ago"
                }
                diff < 30 * 24 * 60 * 60 * 1000 -> {
                    val weeks = (diff / (7 * 24 * 60 * 60 * 1000)).toInt()
                    if (weeks == 1) "1 week ago" else "$weeks weeks ago"
                }
                else -> "on ${dateFormatter.format(date)}"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_video, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(favorites[position])
    }

    override fun getItemCount(): Int = favorites.size

    fun updateFavorites(newFavorites: List<FavoriteItem>) {
        favorites.clear()
        favorites.addAll(newFavorites)
        notifyDataSetChanged()
    }

    fun removeFavorite(videoId: String) {
        val index = favorites.indexOfFirst { it.videoId == videoId }
        if (index != -1) {
            favorites.removeAt(index)
            selectedItems.remove(videoId)
            notifyItemRemoved(index)
        }
    }

    fun addFavorite(favorite: FavoriteItem) {
        favorites.add(0, favorite) // Add to beginning
        notifyItemInserted(0)
    }

    fun updateFavorite(favorite: FavoriteItem) {
        val index = favorites.indexOfFirst { it.videoId == favorite.videoId }
        if (index != -1) {
            favorites[index] = favorite
            notifyItemChanged(index)
        }
    }

    // Selection mode methods
    fun setSelectionMode(enabled: Boolean) {
        isSelectionMode = enabled
        if (!enabled) {
            selectedItems.clear()
        }
        notifyDataSetChanged()
    }

    fun toggleSelection(videoId: String) {
        if (selectedItems.contains(videoId)) {
            selectedItems.remove(videoId)
        } else {
            selectedItems.add(videoId)
        }
        
        val index = favorites.indexOfFirst { it.videoId == videoId }
        if (index != -1) {
            notifyItemChanged(index)
        }
    }

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    fun getSelectedCount(): Int = selectedItems.size

    fun getSelectedItems(): List<FavoriteItem> {
        return favorites.filter { selectedItems.contains(it.videoId) }
    }

    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(favorites.map { it.videoId })
        notifyDataSetChanged()
    }

    fun isItemSelected(videoId: String): Boolean {
        return selectedItems.contains(videoId)
    }

    fun getFavorite(position: Int): FavoriteItem? {
        return if (position in 0 until favorites.size) {
            favorites[position]
        } else {
            null
        }
    }

    fun getFavorites(): List<FavoriteItem> = favorites.toList()

    fun isEmpty(): Boolean = favorites.isEmpty()

    fun getItemByVideoId(videoId: String): FavoriteItem? {
        return favorites.find { it.videoId == videoId }
    }
}
