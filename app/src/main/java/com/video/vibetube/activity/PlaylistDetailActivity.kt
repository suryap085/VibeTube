package com.video.vibetube.activity

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.video.vibetube.R
import com.video.vibetube.adapters.PlaylistVideosAdapter
import com.video.vibetube.models.UserPlaylist
import com.video.vibetube.models.Video
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.EngagementAnalytics
import com.video.vibetube.utils.SocialManager
import kotlinx.coroutines.launch

/**
 * YouTube Policy Compliant Playlist Detail Activity
 * 
 * This activity provides detailed playlist management functionality
 * while ensuring compliance with YouTube's terms of service:
 * - All operations are performed on local user data only
 * - No YouTube API credentials are stored or transmitted
 * - User consent is required for all operations
 * - Respects video availability and access restrictions
 * - Maintains proper attribution to original YouTube content
 */
class PlaylistDetailActivity : AppCompatActivity() {
    
    private lateinit var toolbar: MaterialToolbar
    private lateinit var videosRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var loadingStateLayout: View
    private lateinit var playAllButton: MaterialButton
    private lateinit var shuffleButton: MaterialButton
    private lateinit var addVideosFab: ExtendedFloatingActionButton
    
    private lateinit var userDataManager: UserDataManager
    private lateinit var engagementAnalytics: EngagementAnalytics
    private lateinit var socialManager: SocialManager
    private lateinit var videosAdapter: PlaylistVideosAdapter
    
    private var playlist: UserPlaylist? = null
    private var videos = mutableListOf<Video>()
    
    companion object {
        const val EXTRA_PLAYLIST_ID = "playlist_id"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_detail)
        
        initializeManagers()
        setupViews()
        setupRecyclerView()
        setupClickListeners()
        loadPlaylistData()
    }
    
    private fun initializeManagers() {
        userDataManager = UserDataManager.getInstance(this)
        engagementAnalytics = EngagementAnalytics.getInstance(this)
        socialManager = SocialManager.getInstance(this)
    }
    
    private fun setupViews() {
        toolbar = findViewById(R.id.toolbar)
        videosRecyclerView = findViewById(R.id.videosRecyclerView)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)
        loadingStateLayout = findViewById(R.id.loadingStateLayout)
        playAllButton = findViewById(R.id.playAllButton)
        shuffleButton = findViewById(R.id.shuffleButton)
        addVideosFab = findViewById(R.id.addVideosFab)
        
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }
    
    private fun setupRecyclerView() {
        videosAdapter = PlaylistVideosAdapter(
            videos = videos,
            onVideoClick = { video, position ->
                playVideoFromPlaylist(position)
            },
            onRemoveClick = { video ->
                removeVideoFromPlaylist(video)
            },
            onMoveUp = { position ->
                moveVideo(position, position - 1)
            },
            onMoveDown = { position ->
                moveVideo(position, position + 1)
            }
        )
        
        videosRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@PlaylistDetailActivity)
            adapter = videosAdapter
        }
        
        // Setup drag and drop for reordering
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                moveVideo(fromPosition, toPosition)
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position < videos.size) {
                    removeVideoFromPlaylist(videos[position])
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(videosRecyclerView)
    }
    
    private fun setupClickListeners() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
        
        playAllButton.setOnClickListener {
            if (videos.isNotEmpty()) {
                playVideoFromPlaylist(0)
            }
        }
        
        shuffleButton.setOnClickListener {
            if (videos.isNotEmpty()) {
                val shuffledVideos = videos.shuffled()
                playVideos(shuffledVideos, 0)
            }
        }
        
        addVideosFab.setOnClickListener {
            showAddVideosDialog()
        }
    }
    
    private fun loadPlaylistData() {
        showLoadingState()
        
        val playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID)
        if (playlistId == null) {
            showError("Invalid playlist")
            return
        }
        
        lifecycleScope.launch {
            try {
                val playlists = userDataManager.getPlaylists()
                playlist = playlists.find { it.id == playlistId }
                
                if (playlist == null) {
                    showError("Playlist not found")
                    return@launch
                }
                
                toolbar.title = playlist!!.name
                toolbar.subtitle = "${playlist!!.videos.size} videos"

                // Load videos from stored video objects
                loadVideos(playlist!!.videos)
                
                engagementAnalytics.trackFeatureUsage("playlist_detail_viewed")
                
            } catch (e: Exception) {
                showError("Failed to load playlist")
            }
        }
    }
    
    private fun loadVideos(playlistVideos: List<Video>) {
        lifecycleScope.launch {
            try {
                videos.clear()
                videos.addAll(playlistVideos)

                if (videos.isEmpty()) {
                    showEmptyState()
                } else {
                    showContent()
                }

            } catch (e: Exception) {
                showError("Failed to load videos: ${e.message}")
            }
        }
    }
    
    private fun playVideoFromPlaylist(startIndex: Int) {
        playVideos(videos, startIndex)
    }
    
    private fun playVideos(videoList: List<Video>, startIndex: Int) {
        if (videoList.isEmpty()) return
        
        val intent = Intent(this, YouTubePlayerActivity::class.java).apply {
            putParcelableArrayListExtra("VIDEOS", ArrayList(videoList))
            putExtra("CURRENT_INDEX", startIndex)
        }
        startActivity(intent)
    }
    
    private fun removeVideoFromPlaylist(video: Video) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove Video")
            .setMessage("Remove \"${video.title}\" from this playlist?")
            .setPositiveButton("Remove") { _, _ ->
                lifecycleScope.launch {
                    try {
                        playlist?.let { pl ->
                            val success = userDataManager.removeVideoFromPlaylist(pl.id, video.videoId)
                            if (success) {
                                videos.remove(video)
                                videosAdapter.notifyDataSetChanged()
                                updateToolbarSubtitle()
                                
                                if (videos.isEmpty()) {
                                    showEmptyState()
                                }
                                
                                Toast.makeText(this@PlaylistDetailActivity, "Video removed", Toast.LENGTH_SHORT).show()
                                engagementAnalytics.trackFeatureUsage("video_removed_from_playlist")
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@PlaylistDetailActivity, "Failed to remove video", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun moveVideo(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || fromPosition >= videos.size || 
            toPosition < 0 || toPosition >= videos.size || 
            fromPosition == toPosition) return
        
        lifecycleScope.launch {
            try {
                playlist?.let { pl ->
                    val success = userDataManager.reorderVideoInPlaylist(pl.id, fromPosition, toPosition)
                    if (success) {
                        val movedVideo = videos.removeAt(fromPosition)
                        videos.add(toPosition, movedVideo)
                        videosAdapter.notifyItemMoved(fromPosition, toPosition)
                        engagementAnalytics.trackFeatureUsage("playlist_video_reordered")
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@PlaylistDetailActivity, "Failed to reorder videos", Toast.LENGTH_SHORT).show()
                // Reload to restore correct order
                loadPlaylistData()
            }
        }
    }
    
    private fun updateToolbarSubtitle() {
        toolbar.subtitle = "${videos.size} videos"
    }
    
    private fun showLoadingState() {
        loadingStateLayout.visibility = View.VISIBLE
        videosRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
    }
    
    private fun showContent() {
        loadingStateLayout.visibility = View.GONE
        videosRecyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        videosAdapter.notifyDataSetChanged()
    }
    
    private fun showEmptyState() {
        loadingStateLayout.visibility = View.GONE
        videosRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
    }
    
    private fun showError(message: String) {
        loadingStateLayout.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    /**
     * Show enhanced dialog for adding videos to playlist with Material Design 3
     */
    private fun showAddVideosDialog() {
        val options = arrayOf(
            "⭐ From Favorites",
            "⏰ From Watch History",
            "🔍 Search for Videos"
        )

        val descriptions = arrayOf(
            "Add videos from your favorites collection",
            "Add videos from your watch history",
            "Search and discover new videos to add"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("📹 Add Videos to Playlist")
            .setIcon(R.drawable.ic_playlist_add)
            .setSingleChoiceItems(options, -1) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> showVideoSelectionFromFavorites()
                    1 -> showVideoSelectionFromHistory()
                    2 -> openSearchForVideos()
                }
            }
            .setNeutralButton("📊 View Stats") { _, _ ->
                showPlaylistStats()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show video selection from user's favorites
     */
    private fun showVideoSelectionFromFavorites() {
        lifecycleScope.launch {
            try {
                val favorites = userDataManager.getFavorites()
                val availableVideos = favorites.map { favoriteItem ->
                    Video(
                        videoId = favoriteItem.videoId,
                        title = favoriteItem.title,
                        description = "",
                        thumbnail = favoriteItem.thumbnail,
                        channelTitle = favoriteItem.channelTitle,
                        publishedAt = favoriteItem.addedAt.toString(),
                        duration = favoriteItem.duration,
                        channelId = favoriteItem.channelId
                    )
                }.filter { video ->
                    !videos.any { it.videoId == video.videoId }
                }

                if (availableVideos.isEmpty()) {
                    MaterialAlertDialogBuilder(this@PlaylistDetailActivity)
                        .setTitle("⭐ No Favorites Available")
                        .setMessage("You don't have any favorite videos that aren't already in this playlist.\n\nTip: Add some videos to your favorites first, then come back to add them to your playlist!")
                        .setPositiveButton("Browse Favorites") { _, _ ->
                            // Navigate to favorites
                            val intent = Intent(this@PlaylistDetailActivity, MainActivity::class.java).apply {
                                putExtra("open_section", "favorites")
                            }
                            startActivity(intent)
                        }
                        .setNegativeButton("OK", null)
                        .show()
                    return@launch
                }

                showVideoSelectionDialog(availableVideos, "Select from Favorites")

            } catch (e: Exception) {
                Toast.makeText(this@PlaylistDetailActivity,
                    "Error loading favorites", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Show video selection from user's watch history
     */
    private fun showVideoSelectionFromHistory() {
        lifecycleScope.launch {
            try {
                val watchHistory = userDataManager.getWatchHistory()
                val historyVideos = watchHistory.map { historyItem ->
                    Video(
                        videoId = historyItem.videoId,
                        title = historyItem.title,
                        description = "",
                        thumbnail = historyItem.thumbnail,
                        channelTitle = historyItem.channelTitle,
                        publishedAt = historyItem.watchedAt.toString(),
                        duration = historyItem.duration,
                        channelId = historyItem.channelId
                    )
                }

                val availableVideos = historyVideos.filter { video ->
                    !videos.any { it.videoId == video.videoId }
                }.distinctBy { it.videoId }

                if (availableVideos.isEmpty()) {
                    MaterialAlertDialogBuilder(this@PlaylistDetailActivity)
                        .setTitle("⏰ No Watch History Available")
                        .setMessage("You don't have any videos in your watch history that aren't already in this playlist.\n\nTip: Watch some videos first, then come back to add them to your playlist!")
                        .setPositiveButton("Browse Videos") { _, _ ->
                            // Navigate to main activity
                            val intent = Intent(this@PlaylistDetailActivity, MainActivity::class.java)
                            startActivity(intent)
                        }
                        .setNegativeButton("OK", null)
                        .show()
                    return@launch
                }

                showVideoSelectionDialog(availableVideos, "Select from Watch History")

            } catch (e: Exception) {
                Toast.makeText(this@PlaylistDetailActivity,
                    "Error loading watch history", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Open search activity for finding new videos with enhanced UX
     */
    private fun openSearchForVideos() {
        MaterialAlertDialogBuilder(this)
            .setTitle("🔍 Search for Videos")
            .setMessage("You'll be taken to the main app where you can search for videos. When you find videos you like, add them to your favorites or watch them, then return here to add them to this playlist.")
            .setPositiveButton("🔍 Open Search") { _, _ ->
                val intent = Intent(this, MainActivity::class.java).apply {
                    putExtra("open_search", true)
                    putExtra("playlist_id", playlist?.id)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            }
            .setNeutralButton("📱 Browse Home") { _, _ ->
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Show enhanced dialog for selecting multiple videos with improved UX
     */
    private fun showVideoSelectionDialog(availableVideos: List<Video>, title: String) {
        val selectedVideos = mutableSetOf<Video>()

        // Create enhanced video titles with better formatting
        val videoTitles = availableVideos.map { video ->
            buildString {
                append("🎬 ${video.title}")
                if (video.channelTitle.isNotEmpty()) {
                    append("\n📺 ${video.channelTitle}")
                }
                if (video.duration.isNotEmpty()) {
                    append(" • ⏱️ ${video.duration}")
                }
            }
        }.toTypedArray()

        val checkedItems = BooleanArray(availableVideos.size) { false }

        MaterialAlertDialogBuilder(this)
            .setTitle("📋 $title")
            .setIcon(R.drawable.ic_playlist_add)
            .setMultiChoiceItems(videoTitles, checkedItems) { _, which: Int, isChecked: Boolean ->
                if (isChecked) {
                    selectedVideos.add(availableVideos[which])
                } else {
                    selectedVideos.remove(availableVideos[which])
                }
            }
            .setPositiveButton("➕ Add Selected") { _, _ ->
                addVideosToPlaylist(selectedVideos.toList())
            }
            .setNeutralButton("✅ Select All") { dialog: DialogInterface, _: Int ->
                // Select all videos
                selectedVideos.clear()
                selectedVideos.addAll(availableVideos)
                addVideosToPlaylist(selectedVideos.toList())
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Add selected videos to the playlist
     */
    private fun addVideosToPlaylist(videosToAdd: List<Video>) {
        if (videosToAdd.isEmpty()) {
            Toast.makeText(this, "No videos selected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                playlist?.let { pl ->
                    var addedCount = 0
                    videosToAdd.forEach { video ->
                        val success = userDataManager.addVideoToPlaylist(pl.id, video)
                        if (success) {
                            videos.add(video)
                            addedCount++
                        }
                    }

                    if (addedCount > 0) {
                        videosAdapter.notifyDataSetChanged()
                        showContent() // Update UI state

                        val message = if (addedCount == 1) {
                            "Added 1 video to playlist"
                        } else {
                            "Added $addedCount videos to playlist"
                        }
                        Toast.makeText(this@PlaylistDetailActivity, message, Toast.LENGTH_SHORT).show()

                        engagementAnalytics.trackFeatureUsage("videos_added_to_playlist")
                    } else {
                        Toast.makeText(this@PlaylistDetailActivity,
                            "Failed to add videos", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@PlaylistDetailActivity,
                    "Error adding videos to playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Show playlist statistics dialog
     */
    private fun showPlaylistStats() {
        val totalVideos = videos.size
        val totalDuration = videos.sumOf { video ->
            // Parse duration string to seconds (simplified)
            try {
                val parts = video.duration.split(":")
                when (parts.size) {
                    2 -> parts[0].toInt() * 60 + parts[1].toInt() // MM:SS
                    3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt() // HH:MM:SS
                    else -> 0
                }
            } catch (e: Exception) {
                0
            }
        }

        val hours = totalDuration / 3600
        val minutes = (totalDuration % 3600) / 60
        val durationText = when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "< 1m"
        }

        val uniqueChannels = videos.map { it.channelTitle }.distinct().size
        val createdDate = playlist?.createdAt?.let {
            java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(it)
        } ?: "Unknown"

        MaterialAlertDialogBuilder(this)
            .setTitle("📊 Playlist Statistics")
            .setIcon(R.drawable.ic_analytics)
            .setMessage(buildString {
                appendLine("📋 Playlist: ${playlist?.name ?: "Unknown"}")
                appendLine()
                appendLine("📹 Total Videos: $totalVideos")
                appendLine("⏱️ Total Duration: $durationText")
                appendLine("📺 Unique Channels: $uniqueChannels")
                appendLine("📅 Created: $createdDate")
                appendLine()
                if (totalVideos > 0) {
                    appendLine("📈 Average video length: ${totalDuration / totalVideos / 60}m")
                    appendLine("🎯 Most common channel: ${videos.groupBy { it.channelTitle }.maxByOrNull { it.value.size }?.key ?: "N/A"}")
                }
            })
            .setPositiveButton("📤 Share Stats") { _, _ ->
                sharePlaylistStats(totalVideos, durationText, uniqueChannels)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Share playlist statistics
     */
    private fun sharePlaylistStats(totalVideos: Int, duration: String, channels: Int) {
        val shareText = buildString {
            appendLine("🎬 My VibeTube Playlist: ${playlist?.name}")
            appendLine()
            appendLine("📹 $totalVideos videos")
            appendLine("⏱️ $duration total duration")
            appendLine("📺 $channels unique channels")
            appendLine()
            appendLine("Created with VibeTube 📱")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "My VibeTube Playlist Stats")
        }

        startActivity(Intent.createChooser(shareIntent, "Share Playlist Stats"))
    }
}
