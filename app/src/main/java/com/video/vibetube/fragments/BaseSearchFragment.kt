package com.video.vibetube.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.video.vibetube.BuildConfig
import com.video.vibetube.R
import com.video.vibetube.activity.YouTubePlayerActivity
import com.video.vibetube.adapters.SearchResultsAdapter
import com.video.vibetube.models.Video
import com.video.vibetube.network.createYouTubeService
import com.video.vibetube.utils.NetworkMonitor
import com.video.vibetube.utils.QuotaManager
import com.video.vibetube.utils.SearchVideoCacheManager
import com.video.vibetube.utils.SocialManager
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.Utility.parseDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseSearchFragment : Fragment() {

    protected lateinit var searchEditText: EditText
    protected lateinit var clearButton: ImageView
    protected lateinit var recyclerView: RecyclerView
    protected lateinit var progressBar: ProgressBar
    protected lateinit var swipeRefreshLayout: SwipeRefreshLayout
    protected lateinit var searchResultsAdapter: SearchResultsAdapter
    protected lateinit var emptyStateLayout: LinearLayout

    protected val youtubeService = createYouTubeService()
    protected lateinit var quotaManager: QuotaManager
    protected lateinit var networkMonitor: NetworkMonitor
    protected lateinit var cacheManager: SearchVideoCacheManager
    protected lateinit var userDataManager: UserDataManager
    protected lateinit var socialManager: SocialManager

    protected val searchResults = mutableListOf<Video>()
    protected var nextPageToken = ""
    protected var isLoading = false
    protected var searchJob: Job? = null
    protected var currentQuery = ""

    companion object {
        const val TAG = "BaseSearchFragment"
        const val SEARCH_DELAY = 500L
        const val MAX_RESULTS = 50
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(getLayoutResource(), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupSearchFunctionality()
        setupSwipeRefresh()
    }

    protected abstract fun getLayoutResource(): Int

    private fun initViews(view: View) {
        searchEditText = view.findViewById(R.id.searchEditText)
        clearButton = view.findViewById(R.id.clearButton)
        recyclerView = view.findViewById(R.id.searchResultsRecyclerView)
        progressBar = view.findViewById(R.id.searchProgressBar)
        swipeRefreshLayout = view.findViewById(R.id.searchSwipeRefresh)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)

        quotaManager = QuotaManager(requireContext())
        networkMonitor = NetworkMonitor(requireContext())
        cacheManager = SearchVideoCacheManager(requireContext())
        userDataManager = UserDataManager.getInstance(requireContext())
        socialManager = SocialManager.getInstance(requireContext())
    }

    private fun setupRecyclerView() {
        searchResultsAdapter = SearchResultsAdapter(
            videos = searchResults,
            onVideoClick = { video -> openVideoPlayer(video) },
            onLoadMore = { loadMoreResults() },
            lifecycleOwner = this,
            userDataManager = userDataManager,
            socialManager = socialManager
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = searchResultsAdapter
    }

    private fun setupSearchFunctionality() {
        clearButton.setOnClickListener {
            searchEditText.text.clear()
            clearSearchResults()
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                searchJob?.cancel()

                if (query.isEmpty()) {
                    clearSearchResults()
                } else {
                    emptyStateLayout.visibility = View.GONE
                    searchJob = lifecycleScope.launch {
                        delay(SEARCH_DELAY)
                        if (query.isNotEmpty()) {
                            currentQuery = query
                            performSearch(currentQuery)
                        }
                    }
                }
            }
        })
    }

    private fun setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            if (currentQuery.isNotEmpty()) {
                cacheManager.clearCacheForQuery(currentQuery)
                performSearch(currentQuery)
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    protected abstract fun performSearch(query: String)

    protected fun loadMoreResults() {
        if (isLoading || nextPageToken.isEmpty() || currentQuery.isEmpty()) return
        if (!networkMonitor.isConnected()) {
            safeShowToast("No Internet Connection")
            return
        }
        if (quotaManager.isQuotaExceeded()) {
            safeShowToast("API Quota Exceeded")
            return
        }

        isLoading = true

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    youtubeService.searchVideos(
                        q = currentQuery,
                        maxResults = MAX_RESULTS,
                        pageToken = nextPageToken,
                        key = BuildConfig.YOUTUBE_API_KEY
                    )
                }

                val videos = response.items.map { item ->
                    Video(
                        videoId = item.id.videoId,
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
                }

                val videoIds = videos.map { it.videoId }
                val durations = fetchVideoDurations(videoIds)
                videos.forEach { video ->
                    video.duration = durations[video.videoId] ?: ""
                }

                cacheManager.appendSearchResults(currentQuery, videos)
                val startIndex = searchResults.size
                searchResults.addAll(videos)
                nextPageToken = response.nextPageToken ?: ""

                searchResultsAdapter.notifyItemRangeInserted(startIndex, videos.size)
                quotaManager.recordApiCall("searchVideos", 100)

                Log.d(TAG, "Loaded ${videos.size} more videos. Total: ${searchResults.size}")

            } catch (e: Exception) {
                Log.e(TAG, "Load more failed", e)
                Toast.makeText(requireContext(), "Failed to load more results", Toast.LENGTH_SHORT)
                    .show()
            } finally {
                isLoading = false
            }
        }
    }

    protected suspend fun fetchVideoDurations(videoIds: List<String>): Map<String, String> {
        if (videoIds.isEmpty()) return emptyMap()

        return try {
            val idsParam = videoIds.joinToString(",")
            val response = withContext(Dispatchers.IO) {
                youtubeService.getVideoDetails(
                    part = "contentDetails",
                    id = idsParam,
                    key = BuildConfig.YOUTUBE_API_KEY
                )
            }

            response.items.associate { item ->
                val duration = parseDuration(item.contentDetails.duration)
                item.id to duration
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch video durations", e)
            emptyMap()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    protected fun clearSearchResults() {
        searchResults.clear()
        searchResultsAdapter.notifyDataSetChanged()
        if (!swipeRefreshLayout.isRefreshing)
            currentQuery = ""
        nextPageToken = ""
        emptyStateLayout.visibility = View.VISIBLE
        searchJob?.cancel()
    }

    private fun openVideoPlayer(video: Video) {
        val intent = Intent(requireContext(), YouTubePlayerActivity::class.java).apply {
            putParcelableArrayListExtra("VIDEOS", ArrayList(searchResults))
            putExtra("CURRENT_INDEX", searchResults.indexOf(video))
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
    }

    protected fun safeShowToast(message: String) {
        if (isAdded && context != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
