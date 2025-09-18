package com.video.vibetube.models

import android.os.Parcel
import android.os.Parcelable

data class Video(
    val videoId: String,
    val title: String,
    val description: String,
    val thumbnail: String?,
    val channelTitle: String,
    val publishedAt: String,
    var duration: String = "", // Added duration field
    val categoryId: String = "", // Added category ID field
    val channelId: String = "" // Added channel ID field
): Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(videoId)
        parcel.writeString(title)
        parcel.writeString(description)
        parcel.writeString(thumbnail)
        parcel.writeString(channelTitle)
        parcel.writeString(publishedAt)
        parcel.writeString(duration)
        parcel.writeString(categoryId)
        parcel.writeString(channelId)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Video> {
        override fun createFromParcel(parcel: Parcel): Video {
            return Video(parcel)
        }

        override fun newArray(size: Int): Array<Video?> {
            return arrayOfNulls(size)
        }
    }
}

data class YouTubeSearchResponse(
    val items: List<YouTubeSearchItem>,
    val nextPageToken: String?
)


data class YouTubeSearchItem(
    val id: YouTubeVideoId,
    val snippet: YouTubeSnippet,
    var duration: String = "" // Duration will be fetched separately
)

data class YouTubeVideoId(
    val videoId: String
)

data class YouTubeSnippet(
    val title: String,
    val description: String,
    val thumbnails: YouTubeThumbnails,
    val channelTitle: String,
    val publishedAt: String,
    val channelId: String = ""
)

data class YouTubeThumbnails(
    val default: YouTubeThumbnail?,
    val medium: YouTubeThumbnail?,
    val high: YouTubeThumbnail?,
    val standard: YouTubeThumbnail?,
    val maxres: YouTubeThumbnail?
)

data class YouTubeThumbnail(
    val url: String
)

// Existing data classes for video details (duration)
data class YouTubeVideoDetailsResponse(
    val kind: String,
    val etag: String,
    val items: List<YouTubeVideoDetailsItem>,
    val pageInfo: YouTubePageInfo,
    val nextPageToken: String? = null,
    val prevPageToken: String? = null
)

data class YouTubeVideoDetailsItem(
    val kind: String,
    val etag: String,
    val id: String,
    val contentDetails: YouTubeContentDetails,
    val snippet: YouTubeSnippet? = null
)

data class YouTubeContentDetails(
    val duration: String,
    val dimension: String,
    val definition: String,
    val caption: String,
    val licensedContent: Boolean,
    val projection: String
)

data class YouTubeVideoCategoriesResponse(
    val kind: String,
    val etag: String,
    val items: List<YouTubeVideoCategoryItem>,
    val pageInfo: YouTubePageInfo
)

data class YouTubeVideoCategoryItem(
    val kind: String,
    val etag: String,
    val id: String,
    val snippet: YouTubeCategorySnippet
)

data class YouTubeCategorySnippet(
    val title: String,
    val assignable: Boolean,
    val channelId: String
)

data class YouTubePageInfo(
    val totalResults: Int,
    val resultsPerPage: Int
)

// New data classes for channel-based operations
data class YouTubeChannelResponse(
    val kind: String,
    val etag: String,
    val items: List<YouTubeChannelItem>,
    val pageInfo: YouTubePageInfo
)

data class YouTubeChannelItem(
    val kind: String,
    val etag: String,
    val id: String,
    val contentDetails: YouTubeChannelContentDetails
)

data class YouTubeChannelContentDetails(
    val relatedPlaylists: YouTubeRelatedPlaylists
)

data class YouTubeRelatedPlaylists(
    val uploads: String
)

// Data classes for playlist items (uploads playlist)
data class YouTubePlaylistItemsResponse(
    val kind: String,
    val etag: String,
    val nextPageToken: String?,
    val prevPageToken: String?,
    val pageInfo: YouTubePageInfo,
    val items: List<YouTubePlaylistItem>
)

data class YouTubePlaylistItem(
    val kind: String,
    val etag: String,
    val id: String,
    val snippet: YouTubePlaylistItemSnippet,
    val contentDetails: YouTubePlaylistItemContentDetails? = null
)

data class YouTubePlaylistItemSnippet(
    val publishedAt: String,
    val channelId: String,
    val title: String,
    val description: String,
    val thumbnails: YouTubeThumbnails,
    val channelTitle: String,
    val playlistId: String,
    val position: Int,
    val resourceId: YouTubeResourceId
)

data class YouTubeResourceId(
    val kind: String,
    val videoId: String
)

data class YouTubePlaylistItemContentDetails(
    val videoId: String,
    val startAt: String?,
    val endAt: String?,
    val note: String?,
    val videoPublishedAt: String?
)

data class ChannelVideosSection(
    val channelId: String,
    val channelTitle: String,
    val videos: MutableList<Video> = mutableListOf(),
    var nextPageToken: String = "",
    var isLoading: Boolean = false,
    var playlistId: String? = null
)