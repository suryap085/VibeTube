package com.video.vibetube.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.material.appbar.MaterialToolbar
import com.video.vibetube.BuildConfig
import com.video.vibetube.R
import com.video.vibetube.adapters.ChannelVideosAdapter
import com.video.vibetube.models.Video
import com.video.vibetube.network.createYouTubeService
import com.video.vibetube.utils.AdManager
import com.video.vibetube.utils.PlaylistManager
import com.video.vibetube.utils.QuotaManager
import com.video.vibetube.utils.SearchVideoCacheManager
import com.video.vibetube.utils.SocialManager
import com.video.vibetube.utils.UploadsPlaylistCache
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.Utility.parseDuration
import kotlinx.coroutines.*
import androidx.core.content.edit

class ChannelVideosActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var adapter: ChannelVideosAdapter
    private val youtubeService = createYouTubeService()
    private lateinit var quotaManager: QuotaManager
    private lateinit var uploadsPlaylistCache: UploadsPlaylistCache
    private lateinit var videosCache: SearchVideoCacheManager
    private lateinit var userDataManager: UserDataManager
    private lateinit var socialManager: SocialManager
    private lateinit var playlistManager: PlaylistManager
    private val pageTokenPrefs by lazy { getSharedPreferences("video_page_tokens", MODE_PRIVATE) }
    private val videos = mutableListOf<Video>()
    private val listItems = mutableListOf<Any>()
    private var nextPageToken: String? = ""
    private var playlistId: String? = null
    private var isLoading = false
    private lateinit var channelId: String
    private lateinit var channelTitle: String
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var adManager: AdManager
    private var adView: AdView? = null
    private lateinit var adContainer: FrameLayout
    private var isBannerVisible = true
    private var bottomInset = 0
    private var bannerHeightPixels = 0


    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_video_list)

        channelId = intent.getStringExtra("CHANNEL_ID") ?: ""
        channelTitle = intent.getStringExtra("CHANNEL_TITLE") ?: ""
        if (channelId.isBlank()) {
            Toast.makeText(this, "Channel ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        toolbar = findViewById(R.id.topAppBar)
        toolbar.title = channelTitle
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        adContainer = findViewById(R.id.ad_container)

        // Hide container initially to prevent it from being drawn before it's positioned.
        adContainer.visibility = View.GONE

        val displayMetrics = resources.displayMetrics
        bannerHeightPixels = (50 * displayMetrics.density).toInt()

        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val bannerAdsEnabled = prefs.getBoolean("banner_ads_enabled", true)

        if (!bannerAdsEnabled) {
            adContainer.visibility = View.GONE
        } else {
            // Setup insets listener to properly position the banner
            ViewCompat.setOnApplyWindowInsetsListener(adContainer) { view, insets ->
                val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                bottomInset = systemBarInsets.bottom

                // Set proper margin to position banner above system navigation bar
                val layoutParams = view.layoutParams as ViewGroup.MarginLayoutParams
                layoutParams.bottomMargin = bottomInset
                view.layoutParams = layoutParams

                // Setup banner ad after insets are applied
                if (adView == null) {
                    setupBannerAd()
                    // Show the container after everything is properly setup
                    adContainer.post {
                        adContainer.visibility = View.VISIBLE
                        adContainer.alpha = 1f
                        adContainer.scaleY = 1f
                        isBannerVisible = true
                        // Set initial RecyclerView padding to account for visible banner
                        recyclerView.setPadding(
                            recyclerView.paddingLeft,
                            recyclerView.paddingTop,
                            recyclerView.paddingRight,
                            bottomInset + bannerHeightPixels + 16
                        )
                    }
                }

                insets
            }
        }

        // Initialize managers
        userDataManager = UserDataManager(this)
        socialManager = SocialManager.getInstance(this)
        playlistManager = PlaylistManager.getInstance(this)

        adapter = ChannelVideosAdapter(
            listItems,
            { video ->
                val currentIndex = videos.indexOfFirst { it.videoId == video.videoId }
                if (currentIndex == -1) {
                    Toast.makeText(this, "Video not found.", Toast.LENGTH_SHORT).show()
                    return@ChannelVideosAdapter
                }
                val intent = Intent(this, YouTubePlayerActivity::class.java).apply {
                    putParcelableArrayListExtra("VIDEOS", ArrayList(videos))
                    putExtra("CURRENT_INDEX", currentIndex)
                }
                startActivity(intent)
            },
            this, // lifecycleOwner
            userDataManager,
            socialManager,
            playlistManager
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        quotaManager = QuotaManager(this)
        uploadsPlaylistCache = UploadsPlaylistCache(this)
        videosCache = SearchVideoCacheManager(this)
        adManager = AdManager(this)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)

                if (bannerAdsEnabled) {
                    // Simple liftOnScroll behavior: hide on down-scroll, show on up-scroll
                    if (dy > 0) { // Scrolling down - show banner
                        showBanner()
                    } else if (dy < 0) { // Scrolling up - hide banner
                        hideBanner()
                    }
                }

                // Handle pagination
                val lm = rv.layoutManager as LinearLayoutManager
                val visibleCount = lm.childCount
                val totalCount = lm.itemCount
                val firstVisible = lm.findFirstVisibleItemPosition()
                if (!isLoading && !nextPageToken.isNullOrEmpty() &&
                    visibleCount + firstVisible >= totalCount - 3
                ) {
                    loadMoreVideos()
                }
            }
        })

        loadMoreVideos()
        setupSwipeRefresh()
    }

    override fun onPause() {
        adView?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        adView?.resume()
    }

    override fun onDestroy() {
        adView?.destroy()
        activityScope.cancel()
        super.onDestroy()
    }

    private fun hideBanner() {
        if (!isBannerVisible) return
        isBannerVisible = false

        // Animate the banner container to collapse its height
        adContainer.animate()
            .alpha(0f)
            .scaleY(0f)
            .setDuration(200)
            .withEndAction {
                adContainer.visibility = View.GONE
                // Adjust RecyclerView padding when banner is hidden
                recyclerView.setPadding(
                    recyclerView.paddingLeft,
                    recyclerView.paddingTop,
                    recyclerView.paddingRight,
                    bottomInset + 16 // Just nav bar height + original padding
                )
            }
            .start()
    }

    private fun showBanner() {
        if (isBannerVisible) return
        isBannerVisible = true

        // Show the container and animate it to appear
        adContainer.visibility = View.VISIBLE
        adContainer.alpha = 0f
        adContainer.scaleY = 0f

        // Set RecyclerView padding to account for visible banner
        recyclerView.setPadding(
            recyclerView.paddingLeft,
            recyclerView.paddingTop,
            recyclerView.paddingRight,
            bottomInset + bannerHeightPixels + 16 // Nav bar + banner + original padding
        )

        // Animate banner to full visibility
        adContainer.animate()
            .alpha(1f)
            .scaleY(1f)
            .setDuration(200)
            .start()
    }

    private fun setupBannerAd() {
        adView = AdView(this).apply {
            adUnitId = AdManager.BANNER_AD_UNIT_ID
            setAdSize(AdSize.BANNER)
        }
        adContainer.addView(adView)
        adManager.loadBannerAd(adView!!)
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            if (channelId.isNotEmpty()) {
                activityScope.launch {
                    val pid = playlistId ?: getUploadsPlaylistIdWithCache(channelId)
                    if (pid != null) {
                        videosCache.clearCacheForQuery(pid)
                        pageTokenPrefs.edit { remove(pid) }
                    }
                    clearSearchResults()
                    loadMoreVideos()
                }
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearSearchResults() {
        videos.clear()
        listItems.clear()
        adapter.notifyDataSetChanged()
        nextPageToken = ""
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateAdapterList() {
        listItems.clear()
        var adCounter = 0
        videos.forEach { video ->
            listItems.add(video)
            adCounter++
            if (adCounter % ChannelVideosAdapter.AD_FREQUENCY == 0) {
                listItems.add(Any()) // Ad placeholder
            }
        }
        adapter.notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadMoreVideos() {
        if (isLoading) return
        if (quotaManager.isQuotaExceeded()) {
            Toast.makeText(this, "API Quota reached", Toast.LENGTH_SHORT).show()
            swipeRefreshLayout.isRefreshing = false
            return
        }
        isLoading = true
        if (!swipeRefreshLayout.isRefreshing)
            progressBar.visibility = View.VISIBLE
        activityScope.launch {
            try {
                if (playlistId == null) {
                    playlistId = getUploadsPlaylistIdWithCache(channelId)
                }
                if (playlistId == null) throw Exception("Uploads playlist not found.")
                val currentPlaylistId = playlistId!!

                if (videos.isEmpty()) {
                    val cachedVideos = videosCache.getSearchResults(currentPlaylistId)
                    if (cachedVideos != null && cachedVideos.isNotEmpty()) {
                        val cachedToken = pageTokenPrefs.getString(currentPlaylistId, null)
                        videos.addAll(cachedVideos)
                        nextPageToken = cachedToken
                        updateAdapterList()

                        isLoading = false
                        progressBar.visibility = View.GONE
                        swipeRefreshLayout.isRefreshing = false
                        return@launch
                    }
                }

                val (newVideos, nextToken) = fetchPlaylistVideos(
                    currentPlaylistId,
                    nextPageToken
                )
                if (newVideos.isNotEmpty()) {
                    val videoIds = newVideos.map { it.videoId }
                    val durations = fetchDurations(videoIds)
                    newVideos.forEach { it.duration = durations[it.videoId] ?: "" }
                    videos.addAll(newVideos)
                    nextPageToken = nextToken

                    videosCache.saveSearchResults(currentPlaylistId, videos)
                    if (nextToken != null) {
                        pageTokenPrefs.edit { putString(currentPlaylistId, nextToken) }
                    } else {
                        pageTokenPrefs.edit { remove(currentPlaylistId) }
                    }
                    updateAdapterList()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@ChannelVideosActivity,
                    "Failed to load videos: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                isLoading = false
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private suspend fun getUploadsPlaylistIdWithCache(channelId: String): String? =
        withContext(Dispatchers.IO) {
            val cachedId = uploadsPlaylistCache.getPlaylistId(channelId)
            if (cachedId != null) return@withContext cachedId
            val fetched = fetchUploadsPlaylistId(channelId)
            if (fetched != null) uploadsPlaylistCache.putPlaylistId(channelId, fetched)
            fetched
        }

    private suspend fun fetchUploadsPlaylistId(channelId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val response = youtubeService.getChannelDetails(
                    part = "contentDetails",
                    id = channelId,
                    key = BuildConfig.YOUTUBE_API_KEY
                )
                response.items.firstOrNull()?.contentDetails?.relatedPlaylists?.uploads
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun fetchPlaylistVideos(
        playlistId: String,
        pageToken: String?
    ): Pair<List<Video>, String?> = withContext(Dispatchers.IO) {
        try {
            val response = youtubeService.getPlaylistItems(
                part = "snippet,contentDetails",
                playlistId = playlistId,
                maxResults = 50,
                pageToken = pageToken ?: "",
                key = BuildConfig.YOUTUBE_API_KEY
            )
            val videoList = response.items.mapNotNull { item ->
                val videoId = item.contentDetails?.videoId
                if (videoId != null) {
                    Video(
                        videoId = videoId,
                        title = item.snippet.title,
                        description = item.snippet.description,
                        thumbnail = item.snippet.thumbnails.maxres?.url
                            ?: item.snippet.thumbnails.standard?.url
                            ?: item.snippet.thumbnails.high?.url
                            ?: item.snippet.thumbnails.medium?.url
                            ?: item.snippet.thumbnails.default?.url ?: "",
                        channelTitle = item.snippet.channelTitle,
                        publishedAt = item.snippet.publishedAt,
                        duration = "",
                        categoryId = "",
                        channelId = item.snippet.channelId
                    )
                } else {
                    null
                }
            }
            Pair(videoList, response.nextPageToken)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), null)
        }
    }

    private suspend fun fetchDurations(videoIds: List<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (videoIds.isEmpty()) return@withContext emptyMap()
            try {
                val response = youtubeService.getVideoDetails(
                    part = "contentDetails",
                    id = videoIds.joinToString(","),
                    key = BuildConfig.YOUTUBE_API_KEY
                )
                response.items.associate {
                    it.id to (parseDuration(it.contentDetails.duration))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyMap()
            }
        }
}