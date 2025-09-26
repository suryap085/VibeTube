package com.video.vibetube.ml

import android.content.Context
import com.video.vibetube.models.FavoriteItem
import com.video.vibetube.models.UserPlaylist
import com.video.vibetube.models.WatchHistoryItem
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Phase 2: Smart Content Organizer for VibeTube
 * 
 * Features:
 * - K-means clustering for video categorization
 * - Smart playlist generation based on content similarity
 * - Content organization improvement suggestions
 * - Local ML processing maintaining YouTube policy compliance
 */
class SmartContentOrganizer(
    private val context: Context,
    private val userDataManager: UserDataManager
) {
    
    companion object {
        private const val MAX_CLUSTERS = 8
        private const val MIN_CLUSTER_SIZE = 3
        private const val MAX_ITERATIONS = 50
        private const val CONVERGENCE_THRESHOLD = 0.01
    }
    
    data class ContentCluster(
        val id: String,
        val name: String,
        val description: String,
        val videos: List<VideoFeature>,
        val centroid: List<Double>,
        val confidence: Double
    )
    
    data class VideoFeature(
        val videoId: String,
        val title: String,
        val channelTitle: String,
        val duration: String,
        val features: List<Double>, // Feature vector for clustering
        val category: String,
        val watchProgress: Float = 0.0f
    )
    
    data class OrganizationSuggestion(
        val type: String, // "create_playlist", "merge_categories", "split_category"
        val title: String,
        val description: String,
        val confidence: Double,
        val actionData: Map<String, Any>
    )
    
    data class SmartPlaylistSuggestion(
        val name: String,
        val description: String,
        val videos: List<VideoFeature>,
        val reason: String,
        val confidence: Double
    )
    
    /**
     * Organize content using K-means clustering
     */
    suspend fun organizeContent(): List<ContentCluster> {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext emptyList()
            }
            
            val watchHistory = userDataManager.getWatchHistory()
            val favorites = userDataManager.getFavorites()
            
            if (watchHistory.isEmpty() && favorites.isEmpty()) {
                return@withContext emptyList()
            }
            
            // Convert videos to feature vectors
            val videoFeatures = createVideoFeatures(watchHistory, favorites)
            
            if (videoFeatures.size < MIN_CLUSTER_SIZE) {
                return@withContext listOf(createSingleCluster(videoFeatures))
            }
            
            // Determine optimal number of clusters
            val optimalClusters = determineOptimalClusters(videoFeatures)
            
            // Perform K-means clustering
            val clusters = performKMeansClustering(videoFeatures, optimalClusters)
            
            // Name and describe clusters
            clusters.mapIndexed { index, cluster ->
                val clusterName = generateClusterName(cluster)
                val clusterDescription = generateClusterDescription(cluster)
                val confidence = calculateClusterConfidence(cluster, videoFeatures)
                
                ContentCluster(
                    id = "cluster_$index",
                    name = clusterName,
                    description = clusterDescription,
                    videos = cluster,
                    centroid = calculateCentroid(cluster),
                    confidence = confidence
                )
            }
        }
    }
    
    /**
     * Generate smart playlist suggestions
     */
    suspend fun generateSmartPlaylistSuggestions(): List<SmartPlaylistSuggestion> {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext emptyList()
            }
            
            val clusters = organizeContent()
            val suggestions = mutableListOf<SmartPlaylistSuggestion>()
            
            clusters.forEach { cluster ->
                if (cluster.videos.size >= MIN_CLUSTER_SIZE && cluster.confidence > 0.6) {
                    val suggestion = SmartPlaylistSuggestion(
                        name = "Smart Playlist: ${cluster.name}",
                        description = cluster.description,
                        videos = cluster.videos,
                        reason = "Based on content similarity and viewing patterns",
                        confidence = cluster.confidence
                    )
                    suggestions.add(suggestion)
                }
            }
            
            // Add additional suggestions based on specific patterns
            suggestions.addAll(generatePatternBasedSuggestions())
            
            suggestions.sortedByDescending { it.confidence }.take(5)
        }
    }
    
    /**
     * Generate organization improvement suggestions
     */
    suspend fun generateOrganizationSuggestions(): List<OrganizationSuggestion> {
        return withContext(Dispatchers.IO) {
            if (!userDataManager.hasUserConsent()) {
                return@withContext emptyList()
            }
            
            val suggestions = mutableListOf<OrganizationSuggestion>()
            val favorites = userDataManager.getFavorites()
            val playlists = userDataManager.getPlaylists()
            
            // Analyze current organization
            suggestions.addAll(analyzeFavoriteOrganization(favorites))
            suggestions.addAll(analyzePlaylistOrganization(playlists))
            suggestions.addAll(suggestNewCategories(favorites))
            
            suggestions.sortedByDescending { it.confidence }.take(8)
        }
    }
    
    private fun createVideoFeatures(
        watchHistory: List<WatchHistoryItem>,
        favorites: List<FavoriteItem>
    ): List<VideoFeature> {
        val features = mutableListOf<VideoFeature>()
        
        // Process watch history
        watchHistory.forEach { item ->
            val feature = VideoFeature(
                videoId = item.videoId,
                title = item.title,
                channelTitle = item.channelTitle,
                duration = item.duration,
                features = extractFeatures(item.title, item.channelTitle, item.duration),
                category = inferCategoryFromTitle(item.title),
                watchProgress = item.watchProgress
            )
            features.add(feature)
        }
        
        // Process favorites
        favorites.forEach { item ->
            if (features.none { it.videoId == item.videoId }) {
                val feature = VideoFeature(
                    videoId = item.videoId,
                    title = item.title,
                    channelTitle = item.channelTitle,
                    duration = item.duration,
                    features = extractFeatures(item.title, item.channelTitle, item.duration),
                    category = item.category.ifEmpty { inferCategoryFromTitle(item.title) },
                    watchProgress = 1.0f // Favorites are considered fully "watched"
                )
                features.add(feature)
            }
        }
        
        return features
    }
    
    private fun extractFeatures(title: String, channelTitle: String, duration: String): List<Double> {
        val features = mutableListOf<Double>()
        
        // Title-based features
        val titleWords = title.lowercase().split(" ")
        features.add(titleWords.size.toDouble()) // Title length
        features.add(if (title.contains("tutorial", true)) 1.0 else 0.0)
        features.add(if (title.contains("review", true)) 1.0 else 0.0)
        features.add(if (title.contains("music", true)) 1.0 else 0.0)
        features.add(if (title.contains("game", true) || title.contains("gaming", true)) 1.0 else 0.0)
        features.add(if (title.contains("news", true)) 1.0 else 0.0)
        features.add(if (title.contains("comedy", true) || title.contains("funny", true)) 1.0 else 0.0)
        
        // Duration-based features
        val durationMinutes = parseDurationToMinutes(duration)
        features.add(durationMinutes)
        features.add(if (durationMinutes < 5) 1.0 else 0.0) // Short video
        features.add(if (durationMinutes > 30) 1.0 else 0.0) // Long video
        
        // Channel-based features
        features.add(channelTitle.length.toDouble())
        features.add(if (channelTitle.contains("official", true)) 1.0 else 0.0)
        
        return features
    }
    
    private fun parseDurationToMinutes(duration: String): Double {
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                2 -> parts[0].toDouble() + parts[1].toDouble() / 60.0 // MM:SS
                3 -> parts[0].toDouble() * 60 + parts[1].toDouble() + parts[2].toDouble() / 60.0 // HH:MM:SS
                else -> 5.0 // Default 5 minutes
            }
        } catch (e: Exception) {
            5.0 // Default 5 minutes
        }
    }
    
    private fun inferCategoryFromTitle(title: String): String {
        val titleLower = title.lowercase()
        return when {
            titleLower.contains("music") || titleLower.contains("song") -> "Music"
            titleLower.contains("tutorial") || titleLower.contains("how to") -> "Education"
            titleLower.contains("game") || titleLower.contains("gaming") -> "Gaming"
            titleLower.contains("news") || titleLower.contains("breaking") -> "News"
            titleLower.contains("comedy") || titleLower.contains("funny") -> "Comedy"
            titleLower.contains("tech") || titleLower.contains("review") -> "Technology"
            titleLower.contains("cooking") || titleLower.contains("recipe") -> "Food"
            titleLower.contains("travel") || titleLower.contains("vlog") -> "Travel"
            titleLower.contains("fitness") || titleLower.contains("workout") -> "Health & Fitness"
            titleLower.contains("diy") || titleLower.contains("craft") -> "DIY & Crafts"
            else -> "Entertainment"
        }
    }
    
    private fun determineOptimalClusters(videos: List<VideoFeature>): Int {
        val maxClusters = minOf(MAX_CLUSTERS, videos.size / MIN_CLUSTER_SIZE)
        return maxOf(2, maxClusters)
    }
    
    private fun performKMeansClustering(videos: List<VideoFeature>, k: Int): List<List<VideoFeature>> {
        if (videos.isEmpty() || k <= 0) return emptyList()
        
        val featureDimension = videos.first().features.size
        var centroids = initializeCentroids(k, featureDimension, videos)
        var clusters = assignToClusters(videos, centroids)
        
        repeat(MAX_ITERATIONS) {
            val newCentroids = updateCentroids(clusters)
            val newClusters = assignToClusters(videos, newCentroids)
            
            if (hasConverged(centroids, newCentroids)) {
                return newClusters
            }
            
            centroids = newCentroids
            clusters = newClusters
        }
        
        return clusters
    }
    
    private fun initializeCentroids(k: Int, dimension: Int, videos: List<VideoFeature>): List<List<Double>> {
        val centroids = mutableListOf<List<Double>>()
        val random = kotlin.random.Random.Default
        
        repeat(k) {
            val centroid = mutableListOf<Double>()
            repeat(dimension) { dim ->
                val values = videos.map { it.features[dim] }
                val min = values.minOrNull() ?: 0.0
                val max = values.maxOrNull() ?: 1.0
                centroid.add(random.nextDouble(min, max))
            }
            centroids.add(centroid)
        }
        
        return centroids
    }
    
    private fun assignToClusters(videos: List<VideoFeature>, centroids: List<List<Double>>): List<List<VideoFeature>> {
        val clusters = List(centroids.size) { mutableListOf<VideoFeature>() }
        
        videos.forEach { video ->
            val closestCentroid = centroids.indices.minByOrNull { centroidIndex ->
                euclideanDistance(video.features, centroids[centroidIndex])
            } ?: 0
            clusters[closestCentroid].add(video)
        }
        
        return clusters
    }
    
    private fun updateCentroids(clusters: List<List<VideoFeature>>): List<List<Double>> {
        return clusters.map { cluster ->
            if (cluster.isEmpty()) {
                List(cluster.firstOrNull()?.features?.size ?: 0) { 0.0 }
            } else {
                val dimension = cluster.first().features.size
                List(dimension) { dim ->
                    cluster.map { it.features[dim] }.average()
                }
            }
        }
    }
    
    private fun euclideanDistance(features1: List<Double>, features2: List<Double>): Double {
        return sqrt(features1.zip(features2) { a, b -> (a - b).pow(2) }.sum())
    }
    
    private fun hasConverged(oldCentroids: List<List<Double>>, newCentroids: List<List<Double>>): Boolean {
        return oldCentroids.zip(newCentroids).all { (old, new) ->
            euclideanDistance(old, new) < CONVERGENCE_THRESHOLD
        }
    }
    
    private fun calculateCentroid(cluster: List<VideoFeature>): List<Double> {
        if (cluster.isEmpty()) return emptyList()
        
        val dimension = cluster.first().features.size
        return List(dimension) { dim ->
            cluster.map { it.features[dim] }.average()
        }
    }
    
    private fun generateClusterName(cluster: List<VideoFeature>): String {
        if (cluster.isEmpty()) return "Empty Cluster"
        
        val categories = cluster.map { it.category }
        val mostCommonCategory = categories.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        
        return mostCommonCategory ?: "Mixed Content"
    }
    
    private fun generateClusterDescription(cluster: List<VideoFeature>): String {
        if (cluster.isEmpty()) return "No videos in this cluster"
        
        val avgDuration = cluster.map { parseDurationToMinutes(it.duration) }.average()
        val channels = cluster.map { it.channelTitle }.distinct()
        val categories = cluster.map { it.category }.distinct()
        
        return "Contains ${cluster.size} videos with average duration of ${avgDuration.toInt()} minutes from ${channels.size} channels across ${categories.size} categories"
    }
    
    private fun calculateClusterConfidence(cluster: List<VideoFeature>, allVideos: List<VideoFeature>): Double {
        if (cluster.isEmpty()) return 0.0
        
        val centroid = calculateCentroid(cluster)
        val intraClusterDistance = cluster.map { euclideanDistance(it.features, centroid) }.average()
        val totalDistance = allVideos.map { euclideanDistance(it.features, centroid) }.average()
        
        return (1.0 - (intraClusterDistance / totalDistance)).coerceIn(0.0, 1.0)
    }
    
    private fun createSingleCluster(videos: List<VideoFeature>): ContentCluster {
        return ContentCluster(
            id = "cluster_0",
            name = "All Content",
            description = "All your videos in one collection",
            videos = videos,
            centroid = calculateCentroid(videos),
            confidence = 1.0
        )
    }
    
    private suspend fun generatePatternBasedSuggestions(): List<SmartPlaylistSuggestion> {
        val suggestions = mutableListOf<SmartPlaylistSuggestion>()
        val watchHistory = userDataManager.getWatchHistory()
        
        // Recently watched videos
        val recentVideos = watchHistory
            .filter { System.currentTimeMillis() - it.watchedAt < 7 * 24 * 60 * 60 * 1000L } // Last 7 days
            .map { VideoFeature(it.videoId, it.title, it.channelTitle, it.duration, 
                extractFeatures(it.title, it.channelTitle, it.duration), 
                inferCategoryFromTitle(it.title), it.watchProgress) }
        
        if (recentVideos.size >= 3) {
            suggestions.add(SmartPlaylistSuggestion(
                name = "Recently Watched",
                description = "Videos you've watched in the past week",
                videos = recentVideos,
                reason = "Based on recent viewing activity",
                confidence = 0.8
            ))
        }
        
        return suggestions
    }
    
    private fun analyzeFavoriteOrganization(favorites: List<FavoriteItem>): List<OrganizationSuggestion> {
        val suggestions = mutableListOf<OrganizationSuggestion>()
        
        val uncategorized = favorites.filter { it.category.isEmpty() || it.category == "default" }
        if (uncategorized.size > 5) {
            suggestions.add(OrganizationSuggestion(
                type = "categorize_favorites",
                title = "Organize Uncategorized Favorites",
                description = "You have ${uncategorized.size} uncategorized favorites that could be organized",
                confidence = 0.9,
                actionData = mapOf("count" to uncategorized.size)
            ))
        }
        
        return suggestions
    }
    
    private fun analyzePlaylistOrganization(playlists: List<UserPlaylist>): List<OrganizationSuggestion> {
        val suggestions = mutableListOf<OrganizationSuggestion>()
        
        val largePlaylist = playlists.find { it.videos.size > 50 }
        if (largePlaylist != null) {
            suggestions.add(OrganizationSuggestion(
                type = "split_playlist",
                title = "Split Large Playlist",
                description = "Consider splitting '${largePlaylist.name}' (${largePlaylist.videos.size} videos) into smaller, themed playlists",
                confidence = 0.7,
                actionData = mapOf("playlistId" to largePlaylist.id, "videoCount" to largePlaylist.videos.size)
            ))
        }
        
        return suggestions
    }
    
    private fun suggestNewCategories(favorites: List<FavoriteItem>): List<OrganizationSuggestion> {
        val suggestions = mutableListOf<OrganizationSuggestion>()
        
        val categories = favorites.map { inferCategoryFromTitle(it.title) }
        val categoryCount = categories.groupingBy { it }.eachCount()
        
        categoryCount.forEach { (category, count) ->
            if (count >= 3 && favorites.none { it.category == category }) {
                suggestions.add(OrganizationSuggestion(
                    type = "create_category",
                    title = "Create '$category' Category",
                    description = "You have $count videos that could be organized under '$category'",
                    confidence = 0.8,
                    actionData = mapOf("category" to category, "count" to count)
                ))
            }
        }
        
        return suggestions
    }
}
