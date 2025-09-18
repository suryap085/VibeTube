package com.video.vibetube.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.video.vibetube.BuildConfig
import com.video.vibetube.R
import com.video.vibetube.activity.ChannelVideosActivity
import com.video.vibetube.models.YouTubeSearchItem
import com.video.vibetube.network.createYouTubeService
import com.video.vibetube.utils.NetworkMonitor
import com.video.vibetube.utils.QuotaManager
import com.video.vibetube.utils.SearchChannelCacheManager
import com.video.vibetube.utils.UploadsPlaylistCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchChannelsFragment : Fragment() {

    private lateinit var searchEditText: EditText
    private lateinit var tvEmpty: TextView
    private lateinit var clearButton: ImageView
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private val youtubeService = createYouTubeService()
    private lateinit var quotaManager: QuotaManager
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var cacheManager: SearchChannelCacheManager
    private lateinit var uploadsCache: UploadsPlaylistCache


    private val channelResults = mutableListOf<YouTubeSearchItem>()
    private var nextPageToken = ""
    private var isLoading = false
    private var searchJob: Job? = null
    private var currentQuery = ""
    private lateinit var channelsAdapter: ChannelsAdapter

    companion object {
        private const val TAG = "SearchChannelsFragment"
        private const val SEARCH_DELAY = 500L
        private const val MAX_RESULTS = 50
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        //return inflater.inflate(R.layout.fragment_search_channels, container, false)
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupSearchFunctionality()
        setupSwipeRefresh()
    }

    private fun initViews(view: View) {
        searchEditText = view.findViewById(R.id.searchEditText)
        searchEditText.hint = getString(R.string.search_channels)
        tvEmpty = view.findViewById(R.id.tv_empty)
        tvEmpty.hint=getString(R.string.search_for_youtube_channels)
        clearButton = view.findViewById(R.id.clearButton)
        recyclerView = view.findViewById(R.id.searchResultsRecyclerView)
        progressBar = view.findViewById(R.id.searchProgressBar)
        swipeRefreshLayout = view.findViewById(R.id.searchSwipeRefresh)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)

        quotaManager = QuotaManager(requireContext())
        networkMonitor = NetworkMonitor(requireContext())
        cacheManager = SearchChannelCacheManager(requireContext())
        uploadsCache = UploadsPlaylistCache(requireContext())
    }

    private fun setupRecyclerView() {
        channelsAdapter = ChannelsAdapter(
            channels = channelResults,
            onChannelClick = { channel ->
                val intent = Intent(requireContext(), ChannelVideosActivity::class.java).apply {
                    putExtra("CHANNEL_ID", channel.snippet.channelId)
                    putExtra("CHANNEL_TITLE", channel.snippet.channelTitle)
                }
                startActivity(intent)
            },
            onLoadMore = { loadMoreResults() }
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = channelsAdapter
    }

    private fun loadMoreResults() {
        if (isLoading || nextPageToken.isEmpty() || currentQuery.isEmpty()) return
        if (!networkMonitor.isConnected()) {
            Toast.makeText(requireContext(), "No Internet Connection", Toast.LENGTH_SHORT).show()
            return
        }
        if (quotaManager.isQuotaExceeded()) {
            Toast.makeText(requireContext(), "API Quota Exceeded", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    youtubeService.searchVideos(
                        q = currentQuery,
                        type = "channel",
                        maxResults = MAX_RESULTS,
                        pageToken = nextPageToken,
                        key = BuildConfig.YOUTUBE_API_KEY
                    )
                }

                val startIndex = channelResults.size
                channelResults.addAll(response.items)
                nextPageToken = response.nextPageToken ?: ""
                cacheManager.saveSearchResults(currentQuery, channelResults, nextPageToken)
                channelsAdapter.notifyItemRangeInserted(startIndex, response.items.size)
                quotaManager.recordApiCall("channelsList", 100)

            } catch (e: Exception) {
                Log.e(TAG, "Load more failed", e)
                Toast.makeText(requireContext(), "Failed to load more results", Toast.LENGTH_SHORT)
                    .show()
            } finally {
                isLoading = false
            }
        }
    }

    private fun setupSearchFunctionality() {
        clearButton.setOnClickListener {
            searchEditText.text.clear()
            clearSearchResults()
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString()?.trim() ?: ""
                searchJob?.cancel()

                if (query.isEmpty()) {
                    clearSearchResults()
                } else {
                    emptyStateLayout.visibility = View.GONE
                    searchJob = lifecycleScope.launch {
                        delay(SEARCH_DELAY)
                        if (query.isNotEmpty()) {
                            currentQuery=query
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
                clearSearchResults()
                lifecycleScope.launch {
                cacheManager.clearCacheForQuery(currentQuery)
                }
                performSearch(currentQuery)
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun performSearch(query: String) {
        if (query.isEmpty() || isLoading) return

        lifecycleScope.launch {
            val cachedResult = cacheManager.getSearchResults(query)
            if (cachedResult != null) {
                channelResults.clear()
                channelResults.addAll(cachedResult.items)
                nextPageToken = cachedResult.nextPageToken
                channelsAdapter.notifyDataSetChanged()
                emptyStateLayout.visibility = if (channelResults.isEmpty()) View.VISIBLE else View.GONE
                return@launch
            }


            if (!networkMonitor.isConnected()) {
                Toast.makeText(requireContext(), "No Internet Connection", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (quotaManager.isQuotaExceeded()) {
                Toast.makeText(requireContext(), "API Quota Exceeded", Toast.LENGTH_SHORT).show()
                return@launch
            }

            //currentQuery = query
            isLoading = true
            nextPageToken = ""

            if (!swipeRefreshLayout.isRefreshing) {
                progressBar.visibility = View.VISIBLE
            }

            try {
                val response = withContext(Dispatchers.IO) {
                    youtubeService.searchVideos(
                        q = query,
                        type = "channel",
                        maxResults = MAX_RESULTS,
                        pageToken = "",
                        key = BuildConfig.YOUTUBE_API_KEY
                    )
                }

                channelResults.clear()
                channelResults.addAll(response.items)
                nextPageToken = response.nextPageToken ?: ""

                cacheManager.saveSearchResults(query, channelResults, nextPageToken)
                channelsAdapter.notifyDataSetChanged()
                quotaManager.recordApiCall("searchVideos", 100)

                Log.d(
                    TAG,
                    "Channel search completed. Found ${response.items.size} channels for query: '$query'"
                )

            } catch (e: Exception) {
                Log.e(TAG, "Channel search failed for query: '$query'", e)
                Toast.makeText(
                    requireContext(),
                    "Search failed: ${e.message}",
                    Toast.LENGTH_SHORT
                )
                    .show()
            } finally {
                isLoading = false
                emptyStateLayout.visibility = View.GONE
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearSearchResults() {
        emptyStateLayout.visibility = View.VISIBLE
        channelResults.clear()
        channelsAdapter.notifyDataSetChanged()
        if (!swipeRefreshLayout.isRefreshing)
            currentQuery = ""
        nextPageToken = ""
        searchJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
    }

    private class ChannelsAdapter(
        private val channels: List<YouTubeSearchItem>,
        private val onChannelClick: (YouTubeSearchItem) -> Unit,
        private val onLoadMore: () -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val VIEW_TYPE_VIDEO = 0
            private const val VIEW_TYPE_LOADING = 1
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == channels.size) VIEW_TYPE_LOADING else VIEW_TYPE_VIDEO
        }

        class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val channelThumbnail: ImageView = itemView.findViewById(R.id.channelThumbnail)
            val channelTitle: TextView = itemView.findViewById(R.id.channelTitle)
            val channelDescription: TextView = itemView.findViewById(R.id.channelDescription)

            fun bind(channel: YouTubeSearchItem) {
                // Load thumbnail
                Glide.with(itemView.context)
                    .load(
                        channel.snippet.thumbnails.high?.url
                            ?: channel.snippet.thumbnails.medium?.url
                    )
                    .placeholder(R.drawable.ic_placeholder)
                    .error(R.drawable.ic_placeholder)
                    .circleCrop()
                    .into(channelThumbnail)

                channelTitle.text = channel.snippet.channelTitle
                channelDescription.text = channel.snippet.description

            }
        }

        class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)

            init {
                progressBar.visibility = View.VISIBLE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_VIDEO -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_search_channel, parent, false)
                    ChannelViewHolder(view)
                }

                VIEW_TYPE_LOADING -> {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_loading, parent, false)
                    LoadingViewHolder(view)
                }

                else -> throw IllegalArgumentException("Invalid view type")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is ChannelViewHolder -> {
                    val channel = channels[position]
                    holder.bind(channel)
                    holder.itemView.setOnClickListener { onChannelClick(channel) }
                }

                is LoadingViewHolder -> {
                    // Trigger load more when loading view becomes visible
                    onLoadMore()
                }
            }
        }

        override fun getItemCount(): Int {
            // Add 1 for loading view if there are videos
            return channels.size + if (channels.isNotEmpty()) 1 else 0
        }
    }
}