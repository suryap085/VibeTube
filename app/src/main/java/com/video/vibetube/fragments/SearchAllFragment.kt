package com.video.vibetube.fragments

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.video.vibetube.BuildConfig
import com.video.vibetube.R
import com.video.vibetube.models.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchAllFragment : BaseSearchFragment() {

    override fun getLayoutResource(): Int = R.layout.fragment_search

    @SuppressLint("NotifyDataSetChanged")
    override fun performSearch(query: String) {
        if (query.isEmpty() || isLoading) return

        val cachedResults = cacheManager.getSearchResults(query)
        if (cachedResults != null) {
            searchResults.clear()
            searchResults.addAll(cachedResults)
            searchResultsAdapter.notifyDataSetChanged()
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

        currentQuery = query
        isLoading = true
        nextPageToken = ""
        
        if (!swipeRefreshLayout.isRefreshing) {
            progressBar.visibility = View.VISIBLE
        }
        
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    youtubeService.searchVideos(
                        q = query,
                        maxResults = MAX_RESULTS,
                        pageToken = "",
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

                cacheManager.saveSearchResults(query, videos)
                searchResults.clear()
                searchResults.addAll(videos)
                nextPageToken = response.nextPageToken ?: ""
                
                searchResultsAdapter.notifyDataSetChanged()
                quotaManager.recordApiCall("searchVideos", 100)
                
                Log.d(TAG, "Search completed. Found ${videos.size} videos for query: '$query'")
                
            } catch (e: Exception) {
                Log.e(TAG, "Search failed for query: '$query'", e)
                Toast.makeText(requireContext(), "Search failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                progressBar.visibility = View.GONE
                swipeRefreshLayout.isRefreshing = false
                emptyStateLayout.visibility=View.GONE
            }
        }
    }
}
