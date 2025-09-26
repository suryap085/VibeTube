package com.video.vibetube.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.video.vibetube.R
import com.video.vibetube.models.UserPlaylist
import com.video.vibetube.utils.UserDataManager
import java.text.SimpleDateFormat
import java.util.*

class PlaylistAdapter(
    private val onItemClick: (UserPlaylist) -> Unit,
    private val onEditClick: (UserPlaylist) -> Unit,
    private val onDeleteClick: (UserPlaylist) -> Unit,
    private val onShareClick: (UserPlaylist) -> Unit,
    private val onPlayClick: (UserPlaylist) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    private var playlists = mutableListOf<UserPlaylist>()
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.playlistCard)
        val thumbnailImageView: ImageView = itemView.findViewById(R.id.playlistThumbnail)
        val nameTextView: TextView = itemView.findViewById(R.id.playlistName)
        val descriptionTextView: TextView = itemView.findViewById(R.id.playlistDescription)
        val videoCountTextView: TextView = itemView.findViewById(R.id.videoCount)
        val createdDateTextView: TextView = itemView.findViewById(R.id.createdDate)
        val updatedDateTextView: TextView = itemView.findViewById(R.id.updatedDate)
        val playButton: MaterialButton = itemView.findViewById(R.id.playButton)
        val editButton: MaterialButton = itemView.findViewById(R.id.editButton)
        val shareButton: MaterialButton = itemView.findViewById(R.id.shareButton)
        val deleteButton: MaterialButton = itemView.findViewById(R.id.deleteButton)
        val emptyPlaylistIcon: ImageView = itemView.findViewById(R.id.emptyPlaylistIcon)

        fun bind(playlist: UserPlaylist) {
            nameTextView.text = playlist.name
            
            // Handle description
            if (playlist.description.isNotEmpty()) {
                descriptionTextView.text = playlist.description
                descriptionTextView.visibility = View.VISIBLE
            } else {
                descriptionTextView.visibility = View.GONE
            }

            // Video count
            val videoCount = playlist.videoIds.size
            videoCountTextView.text = when (videoCount) {
                0 -> "Empty playlist"
                1 -> "1 video"
                else -> "$videoCount videos"
            }

            // Dates
            createdDateTextView.text = "Created ${formatDate(playlist.createdAt)}"
            
          /*  if (playlist.updatedAt != playlist.createdAt) {
                updatedDateTextView.text = "Updated ${formatDate(playlist.updatedAt)}"
                updatedDateTextView.visibility = View.VISIBLE
            } else {
                updatedDateTextView.visibility = View.GONE
            }*/

            // Handle empty playlist
            if (videoCount == 0) {
                emptyPlaylistIcon.visibility = View.VISIBLE
                thumbnailImageView.visibility = View.GONE
                playButton.isEnabled = false
                playButton.alpha = 0.5f
            } else {
                emptyPlaylistIcon.visibility = View.GONE
                thumbnailImageView.visibility = View.VISIBLE
                playButton.isEnabled = true
                playButton.alpha = 1.0f
                
                // Load thumbnail (placeholder implementation)
                // In a real app, you would load the thumbnail of the first video
                thumbnailImageView.setImageResource(R.drawable.ic_playlist_placeholder)
            }

            // Click listeners
            itemView.setOnClickListener {
                onItemClick(playlist)
            }

            playButton.setOnClickListener {
                if (videoCount > 0) {
                    onPlayClick(playlist)
                }
            }

            editButton.setOnClickListener {
                onEditClick(playlist)
            }

            shareButton.setOnClickListener {
                onShareClick(playlist)
            }

            deleteButton.setOnClickListener {
                onDeleteClick(playlist)
            }
        }

        private fun formatDate(timestamp: Long): String {
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
                diff < 365 * 24 * 60 * 60 * 1000 -> {
                    val months = (diff / (30L * 24 * 60 * 60 * 1000)).toInt()
                    if (months == 1) "1 month ago" else "$months months ago"
                }
                else -> "on ${dateFormatter.format(date)}"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        holder.bind(playlists[position])
    }

    override fun getItemCount(): Int = playlists.size

    fun updatePlaylists(newPlaylists: List<UserPlaylist>) {
        playlists.clear()
        playlists.addAll(newPlaylists)
        notifyDataSetChanged()
    }

    fun addPlaylist(playlist: UserPlaylist) {
        playlists.add(0, playlist) // Add to beginning
        notifyItemInserted(0)
    }

    fun removePlaylist(playlistId: String) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index != -1) {
            playlists.removeAt(index)
            notifyItemRemoved(index)
        }
    }

    fun updatePlaylist(playlist: UserPlaylist) {
        val index = playlists.indexOfFirst { it.id == playlist.id }
        if (index != -1) {
            playlists[index] = playlist
            notifyItemChanged(index)
        }
    }

    fun getPlaylist(position: Int): UserPlaylist? {
        return if (position in 0 until playlists.size) {
            playlists[position]
        } else {
            null
        }
    }

    fun getPlaylistById(playlistId: String): UserPlaylist? {
        return playlists.find { it.id == playlistId }
    }

    fun getPlaylists(): List<UserPlaylist> = playlists.toList()

    fun isEmpty(): Boolean = playlists.isEmpty()

    fun getPlaylistPosition(playlistId: String): Int {
        return playlists.indexOfFirst { it.id == playlistId }
    }

    fun movePlaylist(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(playlists, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(playlists, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    fun clearPlaylists() {
        val size = playlists.size
        playlists.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun getPlaylistsCount(): Int = playlists.size

    fun getTotalVideosCount(): Int = playlists.sumOf { it.videoIds.size }

    fun getEmptyPlaylistsCount(): Int = playlists.count { it.videoIds.isEmpty() }

    fun getNonEmptyPlaylistsCount(): Int = playlists.count { it.videoIds.isNotEmpty() }
}
