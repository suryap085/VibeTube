package com.video.vibetube.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.video.vibetube.BuildConfig
import com.video.vibetube.R
import com.video.vibetube.activity.ChannelVideosActivity
import com.video.vibetube.activity.YouTubePlayerActivity
import com.video.vibetube.adapters.ChannelsSectionAdapter
import com.video.vibetube.models.ChannelVideosSection
import com.video.vibetube.models.Video
import com.video.vibetube.network.createYouTubeService
import com.video.vibetube.utils.NetworkMonitor
import com.video.vibetube.utils.QuotaManager
import com.video.vibetube.utils.UploadsPlaylistCache
import com.video.vibetube.utils.Utility.parseDuration
import com.video.vibetube.utils.YouTubeCompliantCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VlogsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageTextView: TextView
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var channelsSectionAdapter: ChannelsSectionAdapter
    private val youtubeService = createYouTubeService()
    private lateinit var quotaManager: QuotaManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var cacheManager: YouTubeCompliantCacheManager

    private val channelSections = mutableListOf<ChannelVideosSection>()
    private var channels: List<Pair<String, String>> = emptyList()

    private val uploadsPlaylistCache by lazy {
        UploadsPlaylistCache(requireContext())
    }

    companion object {
        private const val ARG_CHANNEL_IDS = "channel_ids"
        private const val ARG_CHANNEL_TITLES = "channel_titles"
        private const val QUOTA_COST_CHANNEL_DETAILS = 1
        private const val QUOTA_COST_PLAYLIST_ITEMS = 1
        private const val QUOTA_COST_VIDEO_DETAILS = 1
        private const val TAG = "VlogsFragment"

        fun newInstance(channels: List<Pair<String, String>>): VlogsFragment {
            val fragment = VlogsFragment()
            val args = Bundle()
            val channelIds = ArrayList(channels.map { it.first })
            val channelTitles = ArrayList(channels.map { it.second })
            args.putStringArrayList(ARG_CHANNEL_IDS, channelIds)
            args.putStringArrayList(ARG_CHANNEL_TITLES, channelTitles)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ids = arguments?.getStringArrayList(ARG_CHANNEL_IDS) ?: arrayListOf()
        val titles = arguments?.getStringArrayList(ARG_CHANNEL_TITLES) ?: arrayListOf()
        channels = ids.zip(titles)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_multi_channel_videos, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerViewChannelsSections)
        messageTextView = view.findViewById(R.id.messageTextView)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        quotaManager = QuotaManager(requireContext())
        networkMonitor = NetworkMonitor(requireContext())
        cacheManager = YouTubeCompliantCacheManager(requireContext())


        /* if (!networkMonitor.isConnected()) {
              updateUIVisibility("No internet connection. Please check your connection.")
              return
          }*/

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, statusBarHeight, 0, 0)
            insets
        }

        initializeChannelSections()
        setupRecyclerView()
        loadAllChannelsInitially()
        setupSwipeToRefresh()

    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupSwipeToRefresh() {
        swipeRefreshLayout.setOnRefreshListener {
            if (!networkMonitor.isConnected()) {
                updateUIVisibility("No internet connection. Please check your connection.")
                if (swipeRefreshLayout.isRefreshing) swipeRefreshLayout.isRefreshing = false
            } else {
                recyclerView.visibility = View.GONE
                messageTextView.visibility = View.GONE
                initializeChannelSections()
                channelsSectionAdapter.notifyDataSetChanged()
                loadAllChannelsInitially()
            }

        }
    }

    private fun initializeChannelSections() {
        channelSections.clear()
        channels.forEach { (channelId, channelTitle) ->
            channelSections.add(
                ChannelVideosSection(
                    channelId = channelId,
                    channelTitle = channelTitle
                )
            )
        }
    }

    private fun setupRecyclerView() {
        channelsSectionAdapter = ChannelsSectionAdapter(
            channelSections,
            onVideoClick = { channelIndex, video ->
                openVideoPlayer(channelSections[channelIndex].videos, video)
            },
            onLoadMore = { channelIndex ->
                loadMoreVideosForChannel(channelIndex)
            },
            onSeeMore = { channelId, channelTitle ->
                val intent = Intent(requireContext(), ChannelVideosActivity::class.java).apply {
                    putExtra("CHANNEL_ID", channelId)
                    putExtra("CHANNEL_TITLE", channelTitle)
                }
                startActivity(intent)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = channelsSectionAdapter
    }

    private fun updateUIVisibility(customMessage: String? = null) {
        lifecycleScope.launch(Dispatchers.Main) {
            if (this@VlogsFragment::swipeRefreshLayout.isInitialized && swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }
            val allFinishedLoading = channelSections.none { it.isLoading }
            if (allFinishedLoading) {
                if (this@VlogsFragment::swipeRefreshLayout.isInitialized && swipeRefreshLayout.isRefreshing) {
                    swipeRefreshLayout.isRefreshing = false
                }
            }

            val hasAnyVideos = channelSections.any { it.videos.isNotEmpty() }

            if (hasAnyVideos) {
                recyclerView.visibility = View.VISIBLE
                messageTextView.visibility = View.GONE
            } else {
                if (customMessage != null || allFinishedLoading) {
                    recyclerView.visibility = View.GONE
                    messageTextView.visibility = View.VISIBLE
                    val message = when {
                        customMessage != null -> customMessage
                        !networkMonitor.isConnected() -> "No internet connection. Please check your connection."
                        quotaManager.getQuotaStatus().isExceeded -> "API quota exceeded. Please try again later."
                        else -> "No videos found."
                    }
                    messageTextView.text = message
                }
            }
        }
    }

    private fun loadAllChannelsInitially() {
        if (channelSections.isEmpty()) {
            updateUIVisibility()
            return
        }
        channelSections.forEachIndexed { index, _ ->
            loadMoreVideosForChannel(index)
        }
    }

    private fun loadMoreVideosForChannel(channelIndex: Int) {
        val section = channelSections[channelIndex]

        if (section.isLoading || (section.videos.isNotEmpty() && section.nextPageToken.isEmpty())) {
            updateUIVisibility()
            return
        }

        section.isLoading = true

        lifecycleScope.launch {
            try {
                val cacheKey = "${section.channelId}_${section.nextPageToken}"
                val cachedVideos =
                    cacheManager.getSessionVideos(cacheKey, section.nextPageToken, "")

                if (cachedVideos.isNotEmpty()) {
                    handleVideosLoaded(
                        channelIndex,
                        cachedVideos,
                        cacheManager.getSessionNextPageToken(cacheKey)
                    )
                    return@launch
                }

                if (!canMakeApiCall()) {
                    section.isLoading = false
                    updateUIVisibility()
                    return@launch
                }

                loadVideosFromApi(channelIndex)

            } catch (e: Exception) {
                handleLoadingError(channelIndex, e)
            }
        }
    }

    private suspend fun loadVideosFromApi(channelIndex: Int) {
        val section = channelSections[channelIndex]
        try {
            if (section.playlistId == null) {
                section.playlistId = getUploadsPlaylistIdWithCache(section.channelId)
                quotaManager.recordApiCall("getChannelDetails", QUOTA_COST_CHANNEL_DETAILS)
            }

            if (section.playlistId == null) {
                throw Exception("Uploads playlist not found")
            }

            val (newVideos, nextPageToken) = fetchPlaylistVideos(
                section.playlistId!!,
                section.nextPageToken.ifEmpty { null })
            quotaManager.recordApiCall("getPlaylistItems", QUOTA_COST_PLAYLIST_ITEMS)

            if (newVideos.isNotEmpty()) {
                val videoIds = newVideos.map { it.videoId }
                val durations = fetchDurations(videoIds)
                quotaManager.recordApiCall("getVideoDetails", QUOTA_COST_VIDEO_DETAILS)

                newVideos.forEach { video ->
                    video.duration = durations[video.videoId] ?: ""
                }

                val cacheKey = "${section.channelId}_${section.nextPageToken}"
                cacheManager.cacheSessionVideos(
                    cacheKey,
                    newVideos,
                    section.nextPageToken,
                    nextPageToken ?: "",
                    ""
                )

                handleVideosLoaded(channelIndex, newVideos, nextPageToken)
            } else {
                section.isLoading = false
                section.nextPageToken = ""
                updateUIVisibility()
            }

        } catch (e: Exception) {
            throw e
        }
    }

    private fun handleVideosLoaded(
        channelIndex: Int,
        newVideos: List<Video>,
        nextPageToken: String?
    ) {
        val section = channelSections[channelIndex]
        val wasEmpty = section.videos.isEmpty()

        section.videos.addAll(newVideos)
        section.nextPageToken = nextPageToken ?: ""
        section.isLoading = false

        if (wasEmpty) {
            channelsSectionAdapter.notifyItemChanged(channelIndex)
        } else {
            val vh = recyclerView.findViewHolderForAdapterPosition(channelIndex)
            if (vh is ChannelsSectionAdapter.ChannelSectionViewHolder) {
                vh.appendVideos(newVideos)
            }
        }
        updateUIVisibility()
    }

    private fun handleLoadingError(channelIndex: Int, error: Exception) {
        val section = channelSections[channelIndex]
        section.isLoading = false

        val errorMessage = when {
            error.message?.contains("quota", ignoreCase = true) == true ->
                "API quota exceeded. Please try again later."

            !networkMonitor.isConnected() ->
                "No internet connection. Please check your connection."

            else ->
                "Failed to load videos. Please try again later."
        }
        Log.e(TAG, "Loading error for channel ${section.channelTitle}: $errorMessage", error)
        updateUIVisibility(errorMessage)
        if (swipeRefreshLayout.isRefreshing) swipeRefreshLayout.isRefreshing = false
    }

    private fun canMakeApiCall(): Boolean {
        if (!networkMonitor.isConnected()) {
            updateUIVisibility("No internet connection. Please check your connection.")
            if (swipeRefreshLayout.isRefreshing) swipeRefreshLayout.isRefreshing = false
            return false
        }

        val quotaStatus = quotaManager.getQuotaStatus()
        if (quotaStatus.isExceeded) {
            updateUIVisibility("Daily API quota exceeded. Using cached data only.")
            if (swipeRefreshLayout.isRefreshing) swipeRefreshLayout.isRefreshing = false
            return false
        }
        return true
    }

    private suspend fun getUploadsPlaylistIdWithCache(channelId: String): String? {
        val cachedId = uploadsPlaylistCache.getPlaylistId(channelId)
        if (cachedId != null) return cachedId

        val fetched = fetchUploadsPlaylistId(channelId)
        if (fetched != null) {
            uploadsPlaylistCache.putPlaylistId(channelId, fetched)
        }
        return fetched
    }

    private suspend fun fetchUploadsPlaylistId(channelId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val response = youtubeService.getChannelDetails(
                    "contentDetails",
                    channelId,
                    BuildConfig.YOUTUBE_API_KEY
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
                "snippet,contentDetails",
                playlistId,
                50,
                pageToken ?: "",
                BuildConfig.YOUTUBE_API_KEY
            )
            val videoList = response.items.mapNotNull { item ->
                item.contentDetails?.videoId?.let {
                    Video(
                        videoId = it,
                        title = item.snippet.title,
                        description = item.snippet.description,
                        thumbnail = item.snippet.thumbnails.maxres?.url
                            ?: item.snippet.thumbnails.standard?.url
                            ?: item.snippet.thumbnails.high?.url ?: "",
                        channelTitle = item.snippet.channelTitle,
                        publishedAt = item.snippet.publishedAt,
                        duration = "",
                        categoryId = "",
                        channelId = item.snippet.channelId
                    )
                }
            }
            videoList to response.nextPageToken
        } catch (e: Exception) {
            throw e
        }
    }

    private suspend fun fetchDurations(videoIds: List<String>): Map<String, String> {
        if (videoIds.isEmpty()) return emptyMap()
        return try {
            videoIds.chunked(50).flatMap { chunk ->
                val idsParam = chunk.joinToString(",")
                val response = withContext(Dispatchers.IO) {
                    youtubeService.getVideoDetails(
                        "contentDetails",
                        idsParam,
                        BuildConfig.YOUTUBE_API_KEY
                    )
                }
                response.items.map { it.id to parseDuration(it.contentDetails.duration) }
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun openVideoPlayer(videos: List<Video>, clickedVideo: Video) {
        val currentIndex = videos.indexOfFirst { it.videoId == clickedVideo.videoId }
        if (currentIndex == -1) {
            Toast.makeText(requireContext(), "Video not found.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(requireContext(), YouTubePlayerActivity::class.java).apply {
            putParcelableArrayListExtra("VIDEOS", ArrayList(videos))
            putExtra("CURRENT_INDEX", currentIndex)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        cacheManager.cleanup()
    }

    override fun onDestroy() {
        super.onDestroy()
        cacheManager.cleanup()
    }
}