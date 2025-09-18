package com.video.vibetube.fragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.video.vibetube.BuildConfig
import com.video.vibetube.R
import com.video.vibetube.activity.YouTubePlayerActivity
import com.video.vibetube.adapters.VideoAdapter
import com.video.vibetube.models.Video
import com.video.vibetube.models.YouTubeVideoDetailsResponse
import com.video.vibetube.network.createYouTubeService
import com.video.vibetube.utils.AdManager
import com.video.vibetube.utils.NetworkMonitor
import com.video.vibetube.utils.QuotaManager
import com.video.vibetube.utils.Utility.parseDurationToSeconds
import com.video.vibetube.utils.YouTubeCompliantCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseVideoFragment : Fragment() {

    protected lateinit var recyclerView: RecyclerView
    protected lateinit var swipeRefreshLayout: SwipeRefreshLayout
    protected lateinit var progressBar: ProgressBar
    protected lateinit var adManager: AdManager

    protected val videoAdapter = VideoAdapter()
    protected val youtubeService = createYouTubeService()

    protected var currentQuery: String = ""
    protected var currentCategoryId: String = ""
    protected var currentChannelId: String = ""
    protected var uploadsPlaylistId: String = ""
    protected var nextPageToken: String = ""
    protected var isLoading: Boolean = false
    protected var videoClickCount: Int = 0

    // Managers and monitors
    private lateinit var cacheManager: YouTubeCompliantCacheManager
    private lateinit var quotaManager: QuotaManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var toolbar: MaterialToolbar
    private var hasApiQuotaExceeded = false

    companion object {
        private const val TAG = "BaseVideoFragment"
        private const val API_KEY = BuildConfig.YOUTUBE_API_KEY
        private const val IS_DEBUG = true
    }

    // Abstract methods that subclasses must implement
    protected abstract fun getFragmentTitle(): String
    protected abstract fun getDefaultCategoryId(): String

    // New abstract method for channel ID support
    protected open fun getDefaultChannelId(): String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup toolbar
        toolbar = view.findViewById(R.id.topAppBar)
        toolbar.title = getFragmentTitle()
        val appCompatActivity = activity as? AppCompatActivity
        if (appCompatActivity?.supportActionBar == null || appCompatActivity.supportActionBar?.hashCode() != toolbar.hashCode()) {
            appCompatActivity?.setSupportActionBar(toolbar)
        }

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.clear()
                menuInflater.inflate(R.menu.menu_search, menu)
                val searchItem = menu.findItem(R.id.action_search)
                val searchView = searchItem.actionView as SearchView

                searchView.queryHint = "Search ${getFragmentTitle().lowercase()}"
                searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(query: String?): Boolean {
                        query?.let {
                            performSearch(it)
                            searchView.clearFocus()
                            searchItem.collapseActionView()
                        }
                        return true
                    }

                    override fun onQueryTextChange(newText: String?): Boolean = false
                })
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return false
            }
        }, viewLifecycleOwner)

        initializeComponents()
        initViews(view)
        setupRecyclerView()
        setupAds()

        swipeRefreshLayout.setOnRefreshListener {
            onSwipeToRefresh()
        }
    }

    private fun initializeComponents() {
        adManager = AdManager(requireContext())
        cacheManager = YouTubeCompliantCacheManager(requireContext())
        quotaManager = QuotaManager(requireContext())
        networkMonitor = NetworkMonitor(requireContext())
        hasApiQuotaExceeded = quotaManager.isQuotaExceeded()
        if (IS_DEBUG) {
            val status = quotaManager.getQuotaStatus()
            Log.d(TAG, "Quota Status: ${status.used}/${status.total} (${status.percentUsed}%)")
        }
    }

    private fun initViews(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        recyclerView = view.findViewById(R.id.recyclerView)
        progressBar = view.findViewById(R.id.progressBar)
        view.findViewById<TextView?>(R.id.titleTextView)?.text = getFragmentTitle()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = videoAdapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                if (!isLoading && nextPageToken.isNotEmpty()) {
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - 3) {
                        loadMoreVideos()
                    }
                }
            }
        })

        videoAdapter.onVideoClick = { video -> handleVideoClick(video) }
    }

    private fun handleVideoClick(video: Video) {
        videoClickCount++
        if (videoClickCount % 5 == 0) {
            adManager.showInterstitialAd()
        }
        val context = requireContext()
        val videoList = videoAdapter.videoList
        val currentIndex = videoList.indexOfFirst { it.videoId == video.videoId }
        if (currentIndex == -1) {
            Toast.makeText(context, "Video not found", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(context, YouTubePlayerActivity::class.java).apply {
            putParcelableArrayListExtra("VIDEOS", ArrayList(videoList))
            putExtra("CURRENT_INDEX", currentIndex)
        }
        startActivity(intent)
    }

    private fun performSearch(query: String) {
        val sanitizedQuery = sanitizeSearchQuery(query)
        if (sanitizedQuery.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter valid keywords", Toast.LENGTH_SHORT)
                .show()
            return
        }
        currentQuery = sanitizedQuery
        currentCategoryId = ""
        currentChannelId = ""
        uploadsPlaylistId = ""
        nextPageToken = ""
        videoAdapter.clearVideos()
        searchVideos(currentQuery, "")
    }

    protected fun loadVideosByCategory(categoryId: String) {
        if (categoryId.isBlank()) {
            showToast("Category ID cannot be empty.")
            return
        }
        currentQuery = ""
        currentCategoryId = categoryId
        currentChannelId = ""
        uploadsPlaylistId = ""
        nextPageToken = ""
        videoAdapter.clearVideos()
        getVideosByCategoryFromApi(categoryId, "")
    }

    protected fun loadVideosByChannel(channelId: String) {
        if (channelId.isBlank()) {
            showToast("Channel ID cannot be empty.")
            return
        }
        currentQuery = ""
        currentCategoryId = ""
        currentChannelId = channelId
        uploadsPlaylistId = ""
        nextPageToken = ""
        videoAdapter.clearVideos()
        getChannelUploadsPlaylistId(channelId)
    }

    private fun sanitizeSearchQuery(query: String): String {
        return query.replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(100)
    }

    private fun setupAds() {
        videoAdapter.setupAds(adManager)
    }

    private fun onSwipeToRefresh() {
        videoAdapter.clearVideos()
        nextPageToken = ""
        when {
            currentQuery.isNotEmpty() -> searchVideos(currentQuery, "")
            currentChannelId.isNotEmpty() && uploadsPlaylistId.isNotEmpty() -> getVideosFromPlaylistApi(
                uploadsPlaylistId,
                ""
            )

            currentChannelId.isNotEmpty() -> getChannelUploadsPlaylistId(currentChannelId)
            currentCategoryId.isNotEmpty() -> getVideosByCategoryFromApi(currentCategoryId, "")
            else -> swipeRefreshLayout.isRefreshing = false
        }
        if (IS_DEBUG) {
            Log.d(
                TAG,
                "Swipe refresh triggered. Query: $currentQuery, Category: $currentCategoryId, Channel: $currentChannelId"
            )
        }
    }

    private fun loadMoreVideos() {
        if (isLoading || nextPageToken.isEmpty()) {
            if (IS_DEBUG) {
                Log.d(
                    TAG,
                    "Pagination skipped. isLoading: $isLoading, nextPageToken: '$nextPageToken'"
                )
            }
            return
        }
        if (IS_DEBUG) {
            Log.d(
                TAG,
                "Loading more videos. Category: $currentCategoryId, Channel: $currentChannelId, Query: $currentQuery"
            )
        }
        when {
            currentCategoryId.isNotEmpty() -> getVideosByCategoryFromApi(
                currentCategoryId,
                nextPageToken
            )

            currentChannelId.isNotEmpty() && uploadsPlaylistId.isNotEmpty() -> getVideosFromPlaylistApi(
                uploadsPlaylistId,
                nextPageToken
            )

            currentQuery.isNotEmpty() -> searchVideos(currentQuery, nextPageToken)
        }
    }

    private fun searchVideos(query: String, pageToken: String) {
        if (!networkMonitor.isConnected()) {
            showToast("No internet connection.")
            swipeRefreshLayout.isRefreshing = false
            return
        }
        if (hasApiQuotaExceeded || !quotaManager.canMakeApiCall()) {
            showQuotaExceededState()
            swipeRefreshLayout.isRefreshing = false
            return
        }
        val isFirstPage = pageToken.isEmpty()
        if (isFirstPage) {
            val cacheVideos = cacheManager.getSessionVideos(query, pageToken, "")
            if (cacheVideos.isNotEmpty()) {
                videoAdapter.setVideos(cacheVideos)
                if (IS_DEBUG) Log.d(
                    TAG,
                    "Displayed cached first page -- will also fetch network for nextPageToken"
                )
            }
        }
        isLoading = true
        if (!swipeRefreshLayout.isRefreshing) progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    youtubeService.searchVideos(q = query, pageToken = pageToken, key = API_KEY)
                }
                val videos = response.items.map {
                    Video(
                        videoId = it.id.videoId,
                        title = it.snippet.title,
                        description = it.snippet.description,
                        thumbnail = it.snippet.thumbnails.maxres?.url
                            ?: it.snippet.thumbnails.standard?.url
                            ?: it.snippet.thumbnails.high?.url
                            ?: it.snippet.thumbnails.medium?.url
                            ?: it.snippet.thumbnails.default?.url ?: "",
                        channelTitle = it.snippet.channelTitle,
                        publishedAt = it.snippet.publishedAt,
                        duration = "",
                        categoryId = ""
                    )
                }
                cacheManager.cacheSessionVideos(
                    query,
                    videos,
                    pageToken,
                    response.nextPageToken ?: "",
                    ""
                )
                if (isFirstPage) {
                    videoAdapter.setVideos(videos)
                } else {
                    videoAdapter.addVideos(videos)
                }
                nextPageToken = response.nextPageToken ?: ""
                quotaManager.recordApiCall("searchVideos", 100)
                if (IS_DEBUG) {
                    Log.d(
                        TAG,
                        "Search videos loaded. Count: ${videos.size}, NextToken: '$nextPageToken'"
                    )
                }
            } catch (e: Exception) {
                handleApiError(e, "searchVideos", query)
            } finally {
                isLoading = false
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun getVideosByCategoryFromApi(categoryId: String, pageToken: String) {
        if (!networkMonitor.isConnected()) {
            showToast("No internet connection.")
            swipeRefreshLayout.isRefreshing = false
            return
        }
        if (hasApiQuotaExceeded || !quotaManager.canMakeApiCall()) {
            showQuotaExceededState()
            swipeRefreshLayout.isRefreshing = false
            return
        }
        val isFirstPage = pageToken.isEmpty()
        if (isFirstPage) {
            val cacheVideos = cacheManager.getSessionVideos("", pageToken, categoryId)
            if (cacheVideos.isNotEmpty()) {
                videoAdapter.setVideos(cacheVideos)
                if (IS_DEBUG) Log.d(
                    TAG,
                    "Displayed cached first page category -- will also fetch network for nextPageToken"
                )
            }
        }
        isLoading = true
        if (!swipeRefreshLayout.isRefreshing) progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    youtubeService.getVideosByCategory(
                        videoCategoryId = categoryId,
                        pageToken = pageToken,
                        key = API_KEY
                    )
                }
                val videos = response.items.mapNotNull { item ->
                    val durationSeconds = parseDurationToSeconds(item.contentDetails.duration)
                    if (durationSeconds >= 50) {
                        Video(
                            videoId = item.id,
                            title = item.snippet?.title ?: "",
                            description = item.snippet?.description ?: "",
                            thumbnail = item.snippet?.thumbnails?.maxres?.url
                                ?: item.snippet?.thumbnails?.standard?.url
                                ?: item.snippet?.thumbnails?.high?.url
                                ?: item.snippet?.thumbnails?.medium?.url
                                ?: item.snippet?.thumbnails?.default?.url ?: "",
                            channelTitle = item.snippet?.channelTitle ?: "",
                            publishedAt = item.snippet?.publishedAt ?: "",
                            duration = item.contentDetails.duration,
                            categoryId = categoryId
                        )
                    } else null
                }
                cacheManager.cacheSessionVideos(
                    "",
                    videos,
                    pageToken,
                    response.nextPageToken ?: "",
                    categoryId
                )
                if (isFirstPage) {
                    videoAdapter.setVideos(videos)
                } else {
                    videoAdapter.addVideos(videos)
                }
                nextPageToken = response.nextPageToken ?: ""
                quotaManager.recordApiCall("getVideosByCategory", 1)
                if (IS_DEBUG) {
                    Log.d(
                        TAG,
                        "Category videos loaded. Count: ${videos.size}, NextToken: '$nextPageToken'"
                    )
                }
            } catch (e: Exception) {
                handleApiError(e, "getVideosByCategoryFromApi", categoryId)
            } finally {
                isLoading = false
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun getChannelUploadsPlaylistId(channelId: String) {
        if (!networkMonitor.isConnected()) {
            showToast("No internet connection.")
            swipeRefreshLayout.isRefreshing = false
            return
        }
        if (hasApiQuotaExceeded || !quotaManager.canMakeApiCall()) {
            showQuotaExceededState()
            swipeRefreshLayout.isRefreshing = false
            return
        }
        if (!swipeRefreshLayout.isRefreshing) progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    youtubeService.getChannelDetails(
                        part = "contentDetails",
                        id = channelId,
                        key = API_KEY
                    )
                }
                if (response.items.isNotEmpty()) {
                    uploadsPlaylistId = response.items[0].contentDetails.relatedPlaylists.uploads
                    quotaManager.recordApiCall("getChannelDetails", 1)
                    getVideosFromPlaylistApi(uploadsPlaylistId, "")
                } else {
                    showToast("Channel not found or not accessible.")
                    progressBar.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false
                }
            } catch (e: Exception) {
                handleApiError(e, "getChannelUploadsPlaylistId", channelId)
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun getVideosFromPlaylistApi(playlistId: String, pageToken: String) {
        if (!networkMonitor.isConnected()) {
            showToast("No internet connection.")
            swipeRefreshLayout.isRefreshing = false
            return
        }
        if (hasApiQuotaExceeded || !quotaManager.canMakeApiCall()) {
            showQuotaExceededState()
            swipeRefreshLayout.isRefreshing = false
            return
        }
        val isFirstPage = pageToken.isEmpty()
        if (isFirstPage) {
            val cacheKey = "playlist_$playlistId"
            val cacheVideos = cacheManager.getSessionVideos(cacheKey, pageToken, "")
            if (cacheVideos.isNotEmpty()) {
                videoAdapter.setVideos(cacheVideos)
                if (IS_DEBUG) Log.d(
                    TAG,
                    "Displayed cached first page playlist -- will also fetch network for nextPageToken"
                )
            }
        }
        isLoading = true
        if (!swipeRefreshLayout.isRefreshing) progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    youtubeService.getPlaylistItems(
                        part = "snippet,contentDetails",
                        playlistId = playlistId,
                        maxResults = 20,
                        pageToken = pageToken,
                        key = API_KEY
                    )
                }
                val videoIds =
                    response.items.mapNotNull { if (it.snippet.resourceId.kind == "youtube#video") it.snippet.resourceId.videoId else null }
                fetchVideoDetails(videoIds) { detailsResponse ->
                    lifecycleScope.launch {
                        try {
                            if (detailsResponse != null) {
                                val videos = response.items.mapNotNull { item ->
                                    val videoId = item.snippet.resourceId.videoId
                                    val videoDetails =
                                        detailsResponse.items.find { it.id == videoId }
                                    val durationSeconds = videoDetails?.let {
                                        parseDurationToSeconds(it.contentDetails.duration)
                                    } ?: 0
                                    if (durationSeconds >= 50) {
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
                                            duration = videoDetails?.contentDetails?.duration ?: "",
                                            categoryId = "",
                                            channelId = item.snippet.channelId
                                        )
                                    } else null
                                }
                                val cacheKey = "playlist_$playlistId"
                                cacheManager.cacheSessionVideos(
                                    cacheKey,
                                    videos,
                                    pageToken,
                                    response.nextPageToken ?: "",
                                    ""
                                )
                                if (isFirstPage) {
                                    videoAdapter.setVideos(videos)
                                } else {
                                    videoAdapter.addVideos(videos)
                                }
                                nextPageToken = response.nextPageToken ?: ""
                                quotaManager.recordApiCall("getPlaylistItems", 1)
                                if (IS_DEBUG) {
                                    Log.d(
                                        TAG,
                                        "Playlist videos loaded. Count: ${videos.size}, NextToken: '$nextPageToken'"
                                    )
                                }
                            }
                        } finally {
                            isLoading = false
                            progressBar.visibility = View.GONE
                            swipeRefreshLayout.isRefreshing = false
                        }
                    }
                }
            } catch (e: Exception) {
                handleApiError(e, "getVideosFromPlaylistApi", playlistId)
                isLoading = false
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun showQuotaExceededState() {
        showToast("API quota exceeded. Please try again tomorrow.")
        swipeRefreshLayout.isRefreshing = false
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        swipeRefreshLayout.isRefreshing = false
    }

    private fun handleApiError(
        e: Exception,
        methodName: String,
        queryOrCategoryOrChannelId: String
    ) {
        Log.e(TAG, "Error in $methodName for $queryOrCategoryOrChannelId: ${e.message}", e)
        showToast("Failed to load videos. Please try again later.")
        swipeRefreshLayout.isRefreshing = false
    }

    protected fun fetchVideoDetails(
        videoIds: List<String>,
        onResult: (YouTubeVideoDetailsResponse?) -> Unit
    ) {
        if (videoIds.isEmpty()) {
            onResult(null)
            return
        }
        if (!networkMonitor.isConnected()) {
            showToast("No internet connection.")
            onResult(null)
            return
        }
        if (quotaManager.isQuotaExceeded() || !quotaManager.canMakeApiCall()) {
            showQuotaExceededState()
            onResult(null)
            return
        }
        val idsChunk = videoIds.take(50).joinToString(",")
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    youtubeService.getVideoDetails(
                        part = "contentDetails",
                        id = idsChunk,
                        key = BuildConfig.YOUTUBE_API_KEY
                    )
                }
                quotaManager.recordApiCall("getVideoDetails", 1)
                onResult(response)
            } catch (e: Exception) {
                handleApiError(e, "fetchVideoDetails", idsChunk)
                onResult(null)
            }
        }
    }

}
