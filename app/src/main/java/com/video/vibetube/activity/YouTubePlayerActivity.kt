package com.video.vibetube.activity

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.facebook.shimmer.ShimmerFrameLayout
import com.pierfrancescosoffritti.androidyoutubeplayer.core.customui.DefaultPlayerUiController
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.video.vibetube.BuildConfig
import com.video.vibetube.R
import com.video.vibetube.adapters.RelatedVideosAdapter
import com.video.vibetube.models.Video
import com.video.vibetube.models.YouTubeSearchItem
import com.video.vibetube.network.YouTubeApiService
import com.video.vibetube.network.createYouTubeService
import com.video.vibetube.utils.SocialManager
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.Utility
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class YouTubePlayerActivity : AppCompatActivity() {

    companion object {
        private const val YOUTUBE_BASE_URL = "https://www.youtube.com/watch?v="
    }

    private lateinit var youTubePlayerView: YouTubePlayerView
    private lateinit var titleTextView: TextView
    private lateinit var channelTextView: TextView
    private lateinit var nextButton: ImageButton
    private lateinit var previousButton: ImageButton
    private lateinit var playerFavoriteButton: ImageButton
    private lateinit var playerPlaylistButton: ImageButton
    private lateinit var playerShareButton: ImageButton
    private lateinit var headerLayout: LinearLayout
    private lateinit var videoInfoLayout: LinearLayout
    private lateinit var playerContainer: ConstraintLayout
    private lateinit var relatedVideosRecyclerView: RecyclerView
    private lateinit var relatedVideosTitle: TextView
    private lateinit var shimmerViewContainer: ShimmerFrameLayout

    private var currentVideoIndex = 0
    private lateinit var videos: List<Video>
    private lateinit var relatedVideoList: List<YouTubeSearchItem>
    private var youTubePlayer: YouTubePlayer? = null
    private var isFullscreen = false
    private var playerReady = false

    private var uiController: DefaultPlayerUiController? = null
    private lateinit var relatedVideosAdapter: RelatedVideosAdapter
    private val apiService: YouTubeApiService by lazy { createYouTubeService() }

    // User Engagement Features
    private lateinit var userDataManager: UserDataManager
    private lateinit var playlistManager: com.video.vibetube.utils.PlaylistManager
    private lateinit var socialManager: SocialManager
    private var watchStartTime: Long = 0L
    private var lastProgressUpdate: Long = 0L
    private var currentVideoProgress: Float = 0.0f
    private var videoDuration: Float = 0.0f
    private var progressTrackingJob: Job? = null
    private var hasStartedTracking: Boolean = false

    // Resume functionality
    private var resumePosition: Float = 0f
    private var isFromHistory: Boolean = false

    // Cumulative watch duration tracking
    private var baseWatchDuration: Long = 0L // Previous watch duration from history

    private val onBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isFullscreen) {
                exitFullscreen()
            } else {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_player)

        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        relatedVideoList = emptyList()

        // Initialize managers
        userDataManager = UserDataManager(this)
        playlistManager = com.video.vibetube.utils.PlaylistManager.getInstance(this)
        socialManager = SocialManager.getInstance(this)

        setupViews()
        loadVideoData()
        setupYouTubePlayer()
    }

    private fun setupViews() {
        youTubePlayerView = findViewById(R.id.youtube_player_view)
        titleTextView = findViewById(R.id.video_title)
        channelTextView = findViewById(R.id.channel_name)
        nextButton = findViewById(R.id.next_button)
        previousButton = findViewById(R.id.previous_button)
        playerFavoriteButton = findViewById(R.id.player_favorite_button)
        playerPlaylistButton = findViewById(R.id.player_playlist_button)
        playerShareButton = findViewById(R.id.player_share_button)
        headerLayout = findViewById(R.id.layout_back)
        videoInfoLayout = findViewById(R.id.layout_next_prev)
        playerContainer = findViewById(R.id.player_container)
        relatedVideosRecyclerView = findViewById(R.id.related_videos_recycler_view)
        relatedVideosTitle = findViewById(R.id.related_videos_title)
        shimmerViewContainer = findViewById(R.id.shimmer_view_container)

        lifecycle.addObserver(youTubePlayerView)

        setupRelatedVideos()

        nextButton.setOnClickListener { playNextVideo() }
        previousButton.setOnClickListener { playPreviousVideo() }
        findViewById<View>(R.id.back_button).setOnClickListener {
            if (isFullscreen) exitFullscreen() else finish()
        }

        titleTextView.setOnLongClickListener {
            openVideoInYouTubeApp()
            true
        }

        setupActionButtons()
    }

    private fun setupRelatedVideos() {
        relatedVideosAdapter = RelatedVideosAdapter { clickedVideo ->
            val video = Video(
                videoId = clickedVideo.id.videoId,
                title = clickedVideo.snippet.title,
                description = clickedVideo.snippet.description,
                thumbnail = clickedVideo.snippet.thumbnails.high?.url,
                channelTitle = clickedVideo.snippet.channelTitle,
                publishedAt = clickedVideo.snippet.publishedAt,
                duration = clickedVideo.duration, // Use the fetched duration
                categoryId = "",
                channelId = clickedVideo.snippet.channelId,
            )
            val intent = Intent(this, YouTubePlayerActivity::class.java).apply {
                putParcelableArrayListExtra("VIDEOS", arrayListOf(video))
                putExtra("CURRENT_INDEX", 0)
            }
            startActivity(intent)
            finish()
        }

        relatedVideosRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@YouTubePlayerActivity)
            adapter = relatedVideosAdapter
        }
    }

    /**
     * Setup action buttons for video player
     * YouTube Policy Compliance: Only operates on user's local data
     */
    private fun setupActionButtons() {
        // Setup favorite button
        playerFavoriteButton.setOnClickListener {
            if (videos.isNotEmpty() && currentVideoIndex in videos.indices) {
                val currentVideo = videos[currentVideoIndex]
                lifecycleScope.launch {
                    try {
                        val isFavorite = userDataManager.isFavorite(currentVideo.videoId)
                        if (isFavorite) {
                            userDataManager.removeFromFavorites(currentVideo.videoId)
                            updateFavoriteButtonState(false)
                            Toast.makeText(this@YouTubePlayerActivity, "Removed from favorites", Toast.LENGTH_SHORT).show()
                        } else {
                            val success = userDataManager.addToFavorites(currentVideo, sourceContext = "player")
                            if (success) {
                                updateFavoriteButtonState(true)
                                Toast.makeText(this@YouTubePlayerActivity, "Added to favorites", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@YouTubePlayerActivity, "Already in favorites or limit reached", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (_: Exception) {
                        Toast.makeText(this@YouTubePlayerActivity, "Failed to update favorites", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Setup playlist button
        playerPlaylistButton.setOnClickListener {
            if (videos.isNotEmpty() && currentVideoIndex in videos.indices) {
                val currentVideo = videos[currentVideoIndex]
                playlistManager.showAddToPlaylistDialog(
                    video = currentVideo,
                    lifecycleOwner = this@YouTubePlayerActivity,
                    onSuccess = { addedPlaylists ->
                        if (addedPlaylists.isNotEmpty()) {
                            Toast.makeText(
                                this@YouTubePlayerActivity,
                                "Added to ${addedPlaylists.joinToString(", ")}",
                                Toast.LENGTH_SHORT
                            ).show()
                            updatePlaylistButtonState(currentVideo)
                        }
                    },
                    onError = { error ->
                        Toast.makeText(this@YouTubePlayerActivity, error, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Setup share button
        playerShareButton.setOnClickListener {
            if (videos.isNotEmpty() && currentVideoIndex in videos.indices) {
                val currentVideo = videos[currentVideoIndex]
                try {
                    socialManager.shareVideo(currentVideo)
                } catch (_: Exception) {
                    Toast.makeText(this@YouTubePlayerActivity, "Failed to share video", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadVideoData() {
        try {
            videos = intent.getParcelableArrayListExtra("VIDEOS") ?: emptyList()
            currentVideoIndex = intent.getIntExtra("CURRENT_INDEX", 0)

            // Extract resume parameters
            resumePosition = intent.getFloatExtra("RESUME_POSITION", 0f)
            isFromHistory = intent.getBooleanExtra("FROM_HISTORY", false)

            if (videos.isNotEmpty()) {
                updateNavigationButtons()
            } else {
                Toast.makeText(this, "No videos to play", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            //finish()
        }
    }

    private fun setupYouTubePlayer() {
        val options = IFramePlayerOptions.Builder()
            .controls(1)
            .rel(0)
            .fullscreen(1)
            .ivLoadPolicy(1)
            .ccLoadPolicy(1)
            .build()

        youTubePlayerView.addFullscreenListener(object : FullscreenListener {
            override fun onEnterFullscreen(fullscreenView: View, exitFullscreen: () -> Unit) {
                isFullscreen = true

                // Determine orientation based on video duration
                val isShort = isCurrentVideoShort()
                requestedOrientation = if (isShort) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }

                // Debug logging
                if (BuildConfig.DEBUG) {
                    val currentVideo = if (videos.isNotEmpty() && currentVideoIndex in videos.indices) {
                        videos[currentVideoIndex]
                    } else null
                    Log.d("YouTubePlayer", "FullscreenListener - Video: ${currentVideo?.title}, Duration: ${currentVideo?.duration}, IsShort: $isShort, Orientation: ${if (isShort) "Portrait" else "Landscape"}")
                }
                headerLayout.isVisible = false
                videoInfoLayout.isVisible = false
                relatedVideosTitle.isVisible = false
                relatedVideosRecyclerView.isVisible = false
                shimmerViewContainer.isVisible = false

                fullscreenView.tag = "YouTubeFullScreenTag"
                (window.decorView as? ViewGroup)?.addView(
                    fullscreenView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, window.decorView)
                    .hide(WindowInsetsCompat.Type.systemBars())
            }

            override fun onExitFullscreen() {
                isFullscreen = false
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                headerLayout.isVisible = true
                videoInfoLayout.isVisible = true
                if (relatedVideosAdapter.itemCount > 0) {
                    relatedVideosTitle.isVisible = true
                    relatedVideosRecyclerView.isVisible = true
                }

                val decor = window.decorView as? ViewGroup
                val tag = "YouTubeFullScreenTag"
                val childToRemove = (0 until (decor?.childCount ?: 0))
                    .mapNotNull { decor?.getChildAt(it) }
                    .firstOrNull { it.tag == tag }
                childToRemove?.let { decor?.removeView(it) }
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        })

        youTubePlayerView.initialize(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                this@YouTubePlayerActivity.youTubePlayer = youTubePlayer
                playerReady = true
                youTubePlayerView.post {
                    setupCustomUI(youTubePlayer)
                    playCurrentVideo()
                }
            }

            override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                handlePlaybackError(error)
            }

            override fun onStateChange(
                youTubePlayer: YouTubePlayer,
                state: PlayerConstants.PlayerState
            ) {
                handlePlayerStateChange(youTubePlayer, state)
            }

            override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                videoDuration = duration
            }

            override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                updateWatchProgress(second)
            }
        }, options)
    }

    private fun handlePlaybackError(error: PlayerConstants.PlayerError) {
        when (error) {
            PlayerConstants.PlayerError.VIDEO_NOT_FOUND -> {
                Toast.makeText(
                    this@YouTubePlayerActivity,
                    "Video not available",
                    Toast.LENGTH_SHORT
                ).show()
                playNextVideo()
            }

            PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER -> {
                Toast.makeText(
                    this@YouTubePlayerActivity,
                    "Video cannot be played in embedded player",
                    Toast.LENGTH_LONG
                ).show()
                openVideoInYouTubeApp()
            }

            else -> {
                Toast.makeText(
                    this@YouTubePlayerActivity,
                    "Playback error occurred",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupCustomUI(player: YouTubePlayer) {
        try {
            uiController = DefaultPlayerUiController(youTubePlayerView, player)
            uiController?.setFullscreenButtonClickListener { if (isFullscreen) exitFullscreen() else setFullscreenMode() }
            uiController?.apply {
                showFullscreenButton(true)
                showVideoTitle(true)
                showYouTubeButton(true)
                showSeekBar(true)
                showBufferingProgress(true)
                showDuration(true)
                showCurrentTime(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setFullscreenMode() {
        if (isFullscreen) return
        isFullscreen = true

        // Determine orientation based on video duration
        val isShort = isCurrentVideoShort()
        requestedOrientation = if (isShort) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        // Debug logging to help identify issues
        if (BuildConfig.DEBUG) {
            val currentVideo = if (videos.isNotEmpty() && currentVideoIndex in videos.indices) {
                videos[currentVideoIndex]
            } else null
            Log.d("YouTubePlayer", "Fullscreen mode - Video: ${currentVideo?.title}, Duration: ${currentVideo?.duration}, IsShort: $isShort, Orientation: ${if (isShort) "Portrait" else "Landscape"}")
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        headerLayout.isVisible = false
        videoInfoLayout.isVisible = false
        relatedVideosTitle.isVisible = false
        relatedVideosRecyclerView.isVisible = false
        shimmerViewContainer.isVisible = false

        (playerContainer.layoutParams as ConstraintLayout.LayoutParams).apply {
            height = ConstraintLayout.LayoutParams.MATCH_PARENT
            playerContainer.layoutParams = this
        }
        youTubePlayerView.postDelayed({ uiController?.showUi(true) }, 300)
    }

    private fun exitFullscreen() {
        if (!isFullscreen) return
        isFullscreen = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        headerLayout.isVisible = true
        videoInfoLayout.isVisible = true
        if (relatedVideosAdapter.itemCount > 0) {
            relatedVideosTitle.isVisible = true
            relatedVideosRecyclerView.isVisible = true
        }

        (playerContainer.layoutParams as ConstraintLayout.LayoutParams).apply {
            height = ConstraintLayout.LayoutParams.WRAP_CONTENT
            playerContainer.layoutParams = this
        }
        youTubePlayerView.postDelayed({ uiController?.showUi(true) }, 300)
    }

    private fun playCurrentVideo() {
        // Reset tracking state for new video
        resetTrackingState()

        // Determine start position: use resume position for first video from history, 0f for others
        val startPosition = if (isFromHistory && currentVideoIndex == 0 && resumePosition > 0f) {
            resumePosition
        } else {
            0f
        }

        if (relatedVideoList.isNotEmpty()) {
            if (!playerReady || currentVideoIndex !in relatedVideoList.indices) return
            val currentVideo: YouTubeSearchItem = relatedVideoList[currentVideoIndex]
            if (currentVideo.id.videoId.isBlank()) {
                Toast.makeText(this, "Invalid video", Toast.LENGTH_SHORT).show()
                return
            }
            youTubePlayer?.loadVideo(currentVideo.id.videoId, startPosition)
            updateVideoInfoRelated(currentVideo)
            updateNavigationButtons()
        } else if (videos.isNotEmpty()) {
            if (!playerReady || currentVideoIndex !in videos.indices) return
            val currentVideo = videos[currentVideoIndex]
            if (currentVideo.videoId.isBlank()) {
                Toast.makeText(this, "Invalid video", Toast.LENGTH_SHORT).show()
                return
            }
            youTubePlayer?.loadVideo(currentVideo.videoId, startPosition)
            updateVideoInfo(currentVideo)
            updateNavigationButtons()
            fetchRelatedVideos(currentVideo.title)

            // Show resume toast if starting from saved position
            if (startPosition > 0f) {
                val resumeTime = formatSecondsToTime(startPosition)
                Toast.makeText(this, "Resuming from $resumeTime", Toast.LENGTH_SHORT).show()
            }
        }

        // Reset resume position after first video to prevent affecting navigation
        if (currentVideoIndex == 0) {
            resumePosition = 0f
            isFromHistory = false
        }
    }

    private fun fetchRelatedVideos(videoTitle: String) {
        // Start loading: show shimmer and hide the list
        shimmerViewContainer.startShimmer()
        shimmerViewContainer.isVisible = true
        relatedVideosRecyclerView.isVisible = false
        relatedVideosTitle.isVisible = false

        lifecycleScope.launch {
            try {
                // Step 1: Fetch related videos
                val relatedVideosResponse = apiService.getRelatedVideos(
                    q = videoTitle,
                    apiKey = BuildConfig.YOUTUBE_API_KEY
                )

                if (relatedVideosResponse.items.isNotEmpty()) {
                    val videoIds = relatedVideosResponse.items.joinToString(",") { it.id.videoId }

                    // Step 2: Fetch content details (including duration) for these videos
                    val contentDetailsResponse = apiService.getVideoContentDetails(
                        part = "contentDetails",
                        id = videoIds,
                        key = BuildConfig.YOUTUBE_API_KEY
                    )

                    val durationMap =
                        contentDetailsResponse.items.associate { it.id to it.contentDetails.duration }

                    // Step 3: Merge durations into the related video list
                    val videosWithDuration = relatedVideosResponse.items.map { video ->
                        video.apply {
                            duration = durationMap[video.id.videoId].toString()
                        }
                    }
                    relatedVideoList = videosWithDuration
                    relatedVideosAdapter.updateVideos(videosWithDuration)

                    // Success: hide shimmer, show list
                    shimmerViewContainer.stopShimmer()
                    shimmerViewContainer.isVisible = false
                    relatedVideosRecyclerView.isVisible = true
                    relatedVideosTitle.isVisible = true
                    nextButton.isVisible = true
                } else {
                    // No related videos found
                    shimmerViewContainer.stopShimmer()
                    shimmerViewContainer.isVisible = false
                    relatedVideosRecyclerView.isVisible = false
                    relatedVideosTitle.isVisible = false
                }
            } catch (e: Exception) {
                // Error: hide everything
                shimmerViewContainer.stopShimmer()
                shimmerViewContainer.isVisible = false
                relatedVideosRecyclerView.isVisible = false
                relatedVideosTitle.isVisible = false
                Log.e("YouTubePlayerActivity", "Failed to fetch related videos", e)
            }
        }
    }

    private fun playNextVideo() {
        // Save current video progress before switching
        finishWatchTracking()

        val hasNextInVideos = videos.isNotEmpty() && currentVideoIndex < videos.size - 1
        val hasNextInRelated =
            relatedVideoList.isNotEmpty() && currentVideoIndex < relatedVideoList.size - 1

        if (hasNextInVideos || hasNextInRelated) {
            currentVideoIndex++
            playCurrentVideo()
        } else {
            Toast.makeText(this, "No more videos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playPreviousVideo() {
        // Save current video progress before switching
        finishWatchTracking()

        if (currentVideoIndex > 0) {
            currentVideoIndex--
            playCurrentVideo()
        } else {
            Toast.makeText(this, "Already at first video", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateVideoInfo(video: Video) {
        titleTextView.text = video.title
        channelTextView.text =
            if (video.channelTitle.isNotBlank()) "by ${video.channelTitle}" else ""

        // Update action button states
        updateFavoriteButtonState(video)
        updatePlaylistButtonState(video)
    }

    /**
     * Update favorite button state based on current video
     */
    private fun updateFavoriteButtonState(video: Video) {
        lifecycleScope.launch {
            try {
                val isFavorite = userDataManager.isFavorite(video.videoId)
                updateFavoriteButtonState(isFavorite)
            } catch (_: Exception) {
                // Handle error silently
            }
        }
    }

    /**
     * Update favorite button visual state
     */
    private fun updateFavoriteButtonState(isFavorite: Boolean) {
        if (isFavorite) {
            playerFavoriteButton.setImageResource(R.drawable.ic_favorite)
            playerFavoriteButton.setColorFilter(
                androidx.core.content.ContextCompat.getColor(this, R.color.primary)
            )
        } else {
            playerFavoriteButton.setImageResource(R.drawable.ic_favorite_border)
            playerFavoriteButton.setColorFilter(
                androidx.core.content.ContextCompat.getColor(this, R.color.white)
            )
        }
    }

    /**
     * Update playlist button state based on current video
     */
    private fun updatePlaylistButtonState(video: Video) {
        lifecycleScope.launch {
            try {
                val isInPlaylist = playlistManager.isVideoInAnyPlaylist(video.videoId)
                playerPlaylistButton.setImageResource(
                    if (isInPlaylist) R.drawable.ic_playlist_add_check else R.drawable.ic_playlist_add
                )
                playerPlaylistButton.setColorFilter(
                    if (isInPlaylist)
                        androidx.core.content.ContextCompat.getColor(this@YouTubePlayerActivity, R.color.primary)
                    else
                        androidx.core.content.ContextCompat.getColor(this@YouTubePlayerActivity, R.color.white)
                )
            } catch (_: Exception) {
                // Handle error silently
            }
        }
    }

    private fun updateVideoInfoRelated(video: YouTubeSearchItem) {
        titleTextView.text = video.snippet.title
        channelTextView.text =
            if (video.snippet.channelTitle.isNotBlank()) "by ${video.snippet.channelTitle}" else ""
    }

    private fun updateNavigationButtons() {
        previousButton.isVisible = currentVideoIndex > 0

        val hasNextInVideos = videos.isNotEmpty() && currentVideoIndex < videos.size - 1
        val hasNextInRelated =
            relatedVideoList.isNotEmpty() && currentVideoIndex < relatedVideoList.size - 1

        nextButton.isVisible = hasNextInVideos || hasNextInRelated
    }

    private fun openVideoInYouTubeApp() {
        if (videos.isNotEmpty() && currentVideoIndex in videos.indices) {
            val currentVideo = videos[currentVideoIndex]
            try {
                val youtubeIntent =
                    Intent(Intent.ACTION_VIEW, "vnd.youtube:${currentVideo.videoId}".toUri())
                youtubeIntent.putExtra("force_fullscreen", false)
                startActivity(youtubeIntent)
            } catch (_: Exception) {
                val webIntent =
                    Intent(Intent.ACTION_VIEW, (YOUTUBE_BASE_URL + currentVideo.videoId).toUri())
                startActivity(webIntent)
            }
        }
    }

    private fun isCurrentVideoShort(): Boolean {
        // Check the main videos list first (primary video source)
        if (videos.isNotEmpty() && currentVideoIndex in videos.indices) {
            val currentVideo = videos[currentVideoIndex]
            if (currentVideo.duration.isNotEmpty()) {
                val durationSeconds = Utility.parseAnyDurationToSeconds(currentVideo.duration)
                val isShort = durationSeconds < 60

                // Debug logging
                if (BuildConfig.DEBUG) {
                    Log.d("YouTubePlayer", "Video duration check - Title: ${currentVideo.title}, Duration: ${currentVideo.duration}, Seconds: $durationSeconds, IsShort: $isShort")
                }

                return isShort
            } else if (BuildConfig.DEBUG) {
                Log.d("YouTubePlayer", "Main video has no duration - Title: ${currentVideo.title}")
            }
        }

        // Fallback to related videos list if main video doesn't have duration
        if (relatedVideoList.isNotEmpty() && currentVideoIndex in relatedVideoList.indices) {
            val durationStr = relatedVideoList[currentVideoIndex].duration
            if (durationStr.isNotEmpty()) {
                val durationSeconds = Utility.parseAnyDurationToSeconds(durationStr)
                val isShort = durationSeconds < 60

                // Debug logging
                if (BuildConfig.DEBUG) {
                    Log.d("YouTubePlayer", "Related video duration check - Duration: $durationStr, Seconds: $durationSeconds, IsShort: $isShort")
                }

                return isShort
            }
        }

        // Default to false (landscape) if no duration information is available
        if (BuildConfig.DEBUG) {
            Log.d("YouTubePlayer", "No duration information available - defaulting to landscape")
        }
        return false
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Only handle automatic orientation changes when already in fullscreen mode
        // This prevents interference with manual fullscreen button clicks
        if (isFullscreen) {
            val isShort = isCurrentVideoShort()
            val shouldBePortrait = isShort
            val shouldBeLandscape = !isShort

            if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT && shouldBeLandscape) {
                // Video should be in landscape but device is in portrait - exit fullscreen
                exitFullscreen()
            } else if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE && shouldBePortrait) {
                // Video should be in portrait but device is in landscape - exit fullscreen
                exitFullscreen()
            }
        }
    }

    /**
     * Handle YouTube player state changes and track watch history
     */
    private fun handlePlayerStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
        when (state) {
            PlayerConstants.PlayerState.PLAYING -> {
                startWatchTracking()
                uiController?.showUi(true)
            }
            PlayerConstants.PlayerState.PAUSED -> {
                pauseWatchTracking()
            }
            PlayerConstants.PlayerState.ENDED -> {
                finishWatchTracking()
                if (currentVideoIndex < relatedVideoList.size - 1) {
                    playNextVideo()
                }
            }
            PlayerConstants.PlayerState.BUFFERING -> {
                // Continue tracking during buffering
            }
            else -> {
                // Handle other states if needed
            }
        }
    }

    /**
     * Start tracking watch time and progress
     */
    private fun startWatchTracking() {
        if (!hasStartedTracking) {
            watchStartTime = System.currentTimeMillis()
            hasStartedTracking = true

            // Load existing watch duration for cumulative tracking
            if (videos.isNotEmpty() && currentVideoIndex < videos.size) {
                val currentVideo = videos[currentVideoIndex]
                lifecycleScope.launch {
                    try {
                        baseWatchDuration = userDataManager.getExistingWatchDuration(currentVideo.videoId)
                    } catch (e: Exception) {
                        baseWatchDuration = 0L
                        e.printStackTrace()
                    }
                }
            }
        }

        // Start progress tracking coroutine
        progressTrackingJob?.cancel()
        progressTrackingJob = lifecycleScope.launch {
            while (true) {
                delay(5000) // Update every 5 seconds
                saveWatchProgress()
            }
        }
    }

    /**
     * Pause watch tracking
     */
    private fun pauseWatchTracking() {
        progressTrackingJob?.cancel()
        saveWatchProgress()
    }

    /**
     * Finish watch tracking and save final progress
     */
    private fun finishWatchTracking() {
        progressTrackingJob?.cancel()
        saveWatchProgress()
        resetTrackingState()
    }

    /**
     * Update current watch progress
     */
    private fun updateWatchProgress(currentSecond: Float) {
        if (videoDuration > 0) {
            currentVideoProgress = currentSecond / videoDuration
            lastProgressUpdate = System.currentTimeMillis()
        }
    }

    /**
     * Save watch progress to UserDataManager
     */
    private fun saveWatchProgress() {
        if (!hasStartedTracking || videos.isEmpty() || currentVideoIndex >= videos.size) {
            return
        }

        val currentVideo = videos[currentVideoIndex]
        val currentSessionDuration = System.currentTimeMillis() - watchStartTime

        // Only save if user has watched for at least 10 seconds in current session
        if (currentSessionDuration >= 10000) {
            lifecycleScope.launch {
                try {
                    // Note: UserDataManager will handle cumulative duration calculation
                    // We pass only the current session duration, and it will add to existing duration
                    userDataManager.addToWatchHistory(
                        video = currentVideo,
                        watchProgress = currentVideoProgress.coerceIn(0.0f, 1.0f),
                        watchDuration = currentSessionDuration
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Silently handle errors - don't interrupt user experience
                }
            }
        }
    }

    /**
     * Reset tracking state for next video
     */
    private fun resetTrackingState() {
        hasStartedTracking = false
        watchStartTime = 0L
        currentVideoProgress = 0.0f
        videoDuration = 0.0f
        lastProgressUpdate = 0L
        baseWatchDuration = 0L
    }

    /**
     * Format seconds to readable time string (e.g., "5:30" or "1:05:30")
     */
    private fun formatSecondsToTime(seconds: Float): String {
        val totalSeconds = seconds.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%d:%02d", minutes, secs)
        }
    }

    override fun onDestroy() {
        // Save final watch progress before destroying
        finishWatchTracking()

        super.onDestroy()
        youTubePlayerView.release()
    }
}
