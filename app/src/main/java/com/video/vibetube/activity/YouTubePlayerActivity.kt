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
import com.video.vibetube.utils.Utility
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

    private fun loadVideoData() {
        try {
            videos = intent.getParcelableArrayListExtra("VIDEOS") ?: emptyList()
            currentVideoIndex = intent.getIntExtra("CURRENT_INDEX", 0)
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
                requestedOrientation = if (isCurrentVideoShort()) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
                if (state == PlayerConstants.PlayerState.ENDED && currentVideoIndex < relatedVideoList.size - 1) {
                    playNextVideo()
                }
                if (state == PlayerConstants.PlayerState.PLAYING) {
                    uiController?.showUi(true)
                }
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
        requestedOrientation = if (isCurrentVideoShort()) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
        if (relatedVideoList.isNotEmpty()) {
            if (!playerReady || currentVideoIndex !in relatedVideoList.indices) return
            val currentVideo: YouTubeSearchItem = relatedVideoList[currentVideoIndex]
            if (currentVideo.id.videoId.isBlank()) {
                Toast.makeText(this, "Invalid video", Toast.LENGTH_SHORT).show()
                return
            }
            youTubePlayer?.loadVideo(currentVideo.id.videoId, 0f)
            updateVideoInfoRelated(currentVideo)
            updateNavigationButtons()
            //fetchRelatedVideos(currentVideo.snippet.title)
        } else if (videos.isNotEmpty()) {
            if (!playerReady || currentVideoIndex !in videos.indices) return
            val currentVideo = videos[currentVideoIndex]
            if (currentVideo.videoId.isBlank()) {
                Toast.makeText(this, "Invalid video", Toast.LENGTH_SHORT).show()
                return
            }
            youTubePlayer?.loadVideo(currentVideo.videoId, 0f)
            updateVideoInfo(currentVideo)
            updateNavigationButtons()
            fetchRelatedVideos(currentVideo.title)
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
        if (relatedVideoList.isEmpty() || currentVideoIndex !in relatedVideoList.indices) return false
        val durationStr = relatedVideoList[currentVideoIndex].duration
        return Utility.parseAnyDurationToSeconds(durationStr) < 60
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (!isFullscreen) setFullscreenMode()
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            if (isFullscreen) exitFullscreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        youTubePlayerView.release()
    }
}
