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

// User Engagement Data Models
data class WatchHistoryItem(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val channelTitle: String,
    val channelId: String,
    val duration: String,
    val watchedAt: Long = System.currentTimeMillis(),
    val watchProgress: Float = 0.0f, // 0.0 to 1.0
    val watchDuration: Long = 0L, // milliseconds watched
    val isCompleted: Boolean = false
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readFloat(),
        parcel.readLong(),
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(videoId)
        parcel.writeString(title)
        parcel.writeString(thumbnail)
        parcel.writeString(channelTitle)
        parcel.writeString(channelId)
        parcel.writeString(duration)
        parcel.writeLong(watchedAt)
        parcel.writeFloat(watchProgress)
        parcel.writeLong(watchDuration)
        parcel.writeByte(if (isCompleted) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<WatchHistoryItem> {
        override fun createFromParcel(parcel: Parcel): WatchHistoryItem {
            return WatchHistoryItem(parcel)
        }
        override fun newArray(size: Int): Array<WatchHistoryItem?> {
            return arrayOfNulls(size)
        }
    }
}

data class FavoriteItem(
    val videoId: String,
    val title: String,
    val thumbnail: String,
    val channelTitle: String,
    val channelId: String,
    val duration: String,
    val addedAt: Long = System.currentTimeMillis(),
    val category: String = "default" // User-defined category
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong(),
        parcel.readString() ?: "default"
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(videoId)
        parcel.writeString(title)
        parcel.writeString(thumbnail)
        parcel.writeString(channelTitle)
        parcel.writeString(channelId)
        parcel.writeString(duration)
        parcel.writeLong(addedAt)
        parcel.writeString(category)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<FavoriteItem> {
        override fun createFromParcel(parcel: Parcel): FavoriteItem {
            return FavoriteItem(parcel)
        }
        override fun newArray(size: Int): Array<FavoriteItem?> {
            return arrayOfNulls(size)
        }
    }
}

data class FavoriteChannelItem(
    val channelId: String,
    val channelTitle: String,
    val channelDescription: String,
    val thumbnail: String,
    val addedAt: Long = System.currentTimeMillis()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(channelId)
        parcel.writeString(channelTitle)
        parcel.writeString(channelDescription)
        parcel.writeString(thumbnail)
        parcel.writeLong(addedAt)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<FavoriteChannelItem> {
        override fun createFromParcel(parcel: Parcel): FavoriteChannelItem {
            return FavoriteChannelItem(parcel)
        }
        override fun newArray(size: Int): Array<FavoriteChannelItem?> {
            return arrayOfNulls(size)
        }
    }
}

data class FavoritePlaylistItem(
    val playlistId: String,
    val playlistTitle: String,
    val playlistDescription: String,
    val thumbnail: String,
    val channelTitle: String,
    val channelId: String,
    val addedAt: Long = System.currentTimeMillis()
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(playlistId)
        parcel.writeString(playlistTitle)
        parcel.writeString(playlistDescription)
        parcel.writeString(thumbnail)
        parcel.writeString(channelTitle)
        parcel.writeString(channelId)
        parcel.writeLong(addedAt)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<FavoritePlaylistItem> {
        override fun createFromParcel(parcel: Parcel): FavoritePlaylistItem {
            return FavoritePlaylistItem(parcel)
        }
        override fun newArray(size: Int): Array<FavoritePlaylistItem?> {
            return arrayOfNulls(size)
        }
    }
}

data class UserPlaylist(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val videoIds: MutableList<String> = mutableListOf(), // Keep for backward compatibility
    val videos: MutableList<Video> = mutableListOf(), // Store full video objects
    val isPublic: Boolean = false,
    val thumbnailUrl: String = ""
)

/**
 * Data class representing a content category section
 * YouTube Policy Compliance: Uses only predefined categories and channels
 */
data class CategorySection(
    val id: String,
    val name: String,
    val description: String,
    val iconRes: Int,
    val colorRes: Int,
    val channels: List<Pair<String, String>> = emptyList() // (channelId, channelName)
)