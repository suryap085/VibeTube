package com.video.vibetube.network

import com.video.vibetube.models.YouTubeChannelResponse
import com.video.vibetube.models.YouTubePlaylistItemsResponse
import com.video.vibetube.models.YouTubeSearchResponse
import com.video.vibetube.models.YouTubeVideoDetailsResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApiService {
    @GET("search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") q: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 50,
        @Query("regionCode") regionCode: String = "IN",
        @Query("pageToken") pageToken: String = "",
        @Query("key") key: String
    ): YouTubeSearchResponse

    @GET("videos")
    suspend fun getVideosByCategory(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("chart") chart: String = "mostPopular",
        @Query("videoCategoryId") videoCategoryId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String = "",
        @Query("key") key: String
    ): YouTubeVideoDetailsResponse

    @GET("videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "contentDetails",
        @Query("id") id: String,
        @Query("key") key: String
    ): YouTubeVideoDetailsResponse

    // New methods for channel-based video retrieval (quota-efficient)
    @GET("channels")
    suspend fun getChannelDetails(
        @Query("part") part: String = "contentDetails",
        @Query("id") id: String,
        @Query("key") key: String
    ): YouTubeChannelResponse
    
    @GET("playlistItems")
    suspend fun getPlaylistItems(
        @Query("part") part: String = "snippet,contentDetails",
        @Query("playlistId") playlistId: String,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String = "",
        @Query("key") key: String
    ): YouTubePlaylistItemsResponse

    @GET("search")
    suspend fun getRelatedVideos(
        @Query("part") part: String = "snippet",
        @Query("q") q: String,
        @Query("type") type: String = "video",
        @Query("key") apiKey: String,
        @Query("maxResults") maxResults: Int = 50
    ): YouTubeSearchResponse

    @GET("videos")
    suspend fun getVideoContentDetails(
        @Query("part") part: String = "contentDetails",
        @Query("id") id: String, // Comma-separated list of video IDs
        @Query("key") key: String
    ): YouTubeVideoDetailsResponse
}

fun createYouTubeService(): YouTubeApiService {
    return Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/youtube/v3/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(YouTubeApiService::class.java)
}