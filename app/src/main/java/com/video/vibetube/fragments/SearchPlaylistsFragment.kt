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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
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
import com.video.vibetube.utils.SocialManager
import com.video.vibetube.utils.SearchPlaylistsCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchPlaylistsFragment : Fragment() {

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
    private lateinit var socialManager: SocialManager
    private lateinit var cacheManager: SearchPlaylistsCacheManager

    private val playlistResults = mutableListOf<YouTubeSearchItem>()
    private var nextPageToken = ""
    private var isLoading = false
    private var searchJob: Job? = null
    private var currentQuery = ""
    private lateinit var playlistsAdapter: PlaylistsAdapter

    companion object {
        private const val TAG = "SearchPlaylistsFragment"
        private const val SEARCH_DELAY = 500L
        private const val MAX_RESULTS = 50
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize dependencies first
        quotaManager = QuotaManager(requireContext())
        networkMonitor = NetworkMonitor(requireContext())
        socialManager = SocialManager.getInstance(requireContext())
        cacheManager = SearchPlaylistsCacheManager(requireContext())

        initViews(view)
        setupRecyclerView()
        setupSearchFunctionality()
        setupSwipeRefresh()
    }

    private fun initViews(view: View) {
        searchEditText = view.findViewById(R.id.searchEditText)
        searchEditText.hint = getString(R.string.search_playlists)
        tvEmpty = view.findViewById(R.id.tv_empty)
        tvEmpty.hint = getString(R.string.search_for_youtube_playlists)
        clearButton = view.findViewById(R.id.clearButton)
        recyclerView = view.findViewById(R.id.searchResultsRecyclerView)
        progressBar = view.findViewById(R.id.searchProgressBar)
        swipeRefreshLayout = view.findViewById(R.id.searchSwipeRefresh)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
    }

    private fun setupRecyclerView() {
        playlistsAdapter = PlaylistsAdapter(
            playlists = playlistResults,
            onPlaylistClick = { playlist ->
                val intent = Intent(requireContext(), ChannelVideosActivity::class.java).apply {
                    putExtra("CHANNEL_ID", playlist.snippet.channelId)
                    putExtra("CHANNEL_TITLE", playlist.snippet.channelTitle)
                }
                startActivity(intent)
            },
            onLoadMore = { loadMoreResults() },
            lifecycleOwner = this@SearchPlaylistsFragment,
            socialManager = socialManager
        )

        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = playlistsAdapter
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

    @SuppressLint("NotifyDataSetChanged")
    private fun performSearch(query: String) {
        if (query.isEmpty() || isLoading) return

        val cachedResults = cacheManager.getSearchResults(query)
        if (cachedResults != null) {
            playlistResults.clear()
            playlistResults.addAll(cachedResults)
            playlistsAdapter.notifyDataSetChanged()
            emptyStateLayout.visibility = if (cachedResults.isEmpty()) View.VISIBLE else View.GONE
            return
        }

        if (!networkMonitor.isConnected()) {
            Toast.makeText(requireContext(), "No Internet Connection", Toast.LENGTH_SHORT).show()
            return
        }
        if (quotaManager.isQuotaExceeded()) {
            Toast.makeText(requireContext(), "API Quota Exceeded", Toast.LENGTH_SHORT).show()
            return
        }

        isLoading = true
        nextPageToken = ""
        if (!swipeRefreshLayout.isRefreshing)
            progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    youtubeService.searchVideos(
                        q = query,
                        type = "playlist",
                        maxResults = MAX_RESULTS,
                        pageToken = "",
                        key = BuildConfig.YOUTUBE_API_KEY
                    )
                }

                playlistResults.clear()
                playlistResults.addAll(response.items)
                nextPageToken = response.nextPageToken ?: ""

                cacheManager.saveSearchResults(query, playlistResults)
                playlistsAdapter.notifyDataSetChanged()
                quotaManager.recordApiCall("searchVideos", 100)

                Log.d(
                    TAG,
                    "Playlist search completed. Found ${response.items.size} playlists for query: '$query'"
                )

            } catch (e: Exception) {
                Log.e(TAG, "Playlist search failed for query: '$query'", e)
                safeShowToast("Search failed: ${e.message}")
            } finally {
                emptyStateLayout.visibility = View.GONE
                isLoading = false
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun loadMoreResults() {
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
                        type = "playlist",
                        maxResults = MAX_RESULTS,
                        pageToken = nextPageToken,
                        key = BuildConfig.YOUTUBE_API_KEY
                    )
                }

                val startIndex = playlistResults.size
                playlistResults.addAll(response.items)
                nextPageToken = response.nextPageToken ?: ""

                cacheManager.appendResults(currentQuery, response.items)
                playlistsAdapter.notifyItemRangeInserted(startIndex, response.items.size)
                quotaManager.recordApiCall("playlist", 100)

                Log.d(
                    BaseSearchFragment.Companion.TAG,
                    "Loaded ${response.items.size} more videos. Total: ${playlistResults.size}"
                )

            } catch (e: Exception) {
                Log.e(TAG, "Load more failed", e)
                Toast.makeText(requireContext(), "Failed to load more playlist", Toast.LENGTH_SHORT)
                    .show()
            } finally {
                isLoading = false
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun clearSearchResults() {
        emptyStateLayout.visibility = View.VISIBLE
        playlistResults.clear()
        playlistsAdapter.notifyDataSetChanged()
        if (!swipeRefreshLayout.isRefreshing)
            currentQuery = ""
        nextPageToken = ""
        searchJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
    }

    private class PlaylistsAdapter(
        private val playlists: List<YouTubeSearchItem>,
        private val onPlaylistClick: (YouTubeSearchItem) -> Unit,
        private val onLoadMore: () -> Unit,
        private val lifecycleOwner: LifecycleOwner,
        private val socialManager: SocialManager
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        companion object {
            private const val VIEW_TYPE_VIDEO = 0
            private const val VIEW_TYPE_LOADING = 1
        }

        override fun getItemViewType(position: Int): Int {
            return if (position == playlists.size) VIEW_TYPE_LOADING else VIEW_TYPE_VIDEO
        }

        inner class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val playlistThumbnail: ImageView = itemView.findViewById(R.id.channelThumbnail)

            //val playlistTitle: TextView = itemView.findViewById(R.id.playlistTitle)
            val channelTitle: TextView = itemView.findViewById(R.id.channelTitle)
            val playlistDescription: TextView = itemView.findViewById(R.id.channelDescription)
            private val shareButton: ImageButton = itemView.findViewById(R.id.shareButton)

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
                    .into(playlistThumbnail)

                channelTitle.text = channel.snippet.channelTitle
                playlistDescription.text = channel.snippet.description

                // Setup share button
                setupShareButton(channel)
            }



            /**
             * Setup share button functionality
             */
            private fun setupShareButton(playlist: YouTubeSearchItem) {
                shareButton.setOnClickListener {
                    try {
                        socialManager.sharePlaylist(playlist.id.videoId, playlist.snippet.title)
                    } catch (e: Exception) {
                        Log.e("SearchPlaylistsAdapter", "Error sharing playlist", e)
                        showToast("Failed to share playlist")
                    }
                }
            }



            /**
             * Show toast message
             */
            private fun showToast(message: String) {
                Toast.makeText(itemView.context, message, Toast.LENGTH_SHORT).show()
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
                    PlaylistViewHolder(view)
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
                is PlaylistViewHolder -> {
                    val channel = playlists[position]
                    holder.bind(channel)
                    holder.itemView.setOnClickListener { onPlaylistClick(channel) }
                }

                is LoadingViewHolder -> {
                    // Trigger load more when loading view becomes visible
                    onLoadMore()
                }
            }
        }

        override fun getItemCount(): Int {
            // Add 1 for loading view if there are videos
            return playlists.size + if (playlists.isNotEmpty()) 1 else 0
        }
    }

    private fun safeShowToast(message: String) {
        if (isAdded && context != null) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }
}
