package com.video.vibetube.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Phase 1: Performance Optimizer for VibeTube
 * 
 * Features:
 * - Memory management with LRU cache for images
 * - RecyclerView optimization
 * - Background task management
 * - Performance metrics tracking
 * - YouTube policy compliant (local processing only)
 */
class Phase1PerformanceOptimizer private constructor(private val context: Context) {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Memory Management
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8 // Use 1/8th of available memory for cache
    
    private val imageCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }
    
    // Performance Metrics
    private val performanceMetrics = ConcurrentHashMap<String, AtomicLong>()
    private var startupTime: Long = 0
    private var lastMemoryCheck: Long = 0
    
    // RecyclerView Optimization
    private val recyclerViewOptimizations = mutableMapOf<String, RecyclerViewConfig>()
    
    companion object {
        @Volatile
        private var INSTANCE: Phase1PerformanceOptimizer? = null
        
        fun getInstance(context: Context): Phase1PerformanceOptimizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Phase1PerformanceOptimizer(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Performance targets from roadmap
        const val TARGET_STARTUP_TIME_MS = 2000L
        const val TARGET_OPERATION_TIME_MS = 5000L
        const val MEMORY_CHECK_INTERVAL_MS = 30000L // 30 seconds
    }
    
    data class RecyclerViewConfig(
        val prefetchItemCount: Int = 4,
        val hasFixedSize: Boolean = true,
        val isNestedScrollingEnabled: Boolean = false
    )
    
    data class PerformanceReport(
        val startupTime: Long,
        val memoryUsage: Long,
        val cacheHitRate: Double,
        val operationTimes: Map<String, Long>,
        val recommendations: List<String>
    )
    
    init {
        startupTime = System.currentTimeMillis()
        initializeMetrics()
        scheduleMemoryChecks()
    }
    
    private fun initializeMetrics() {
        performanceMetrics["cache_hits"] = AtomicLong(0)
        performanceMetrics["cache_misses"] = AtomicLong(0)
        performanceMetrics["background_tasks"] = AtomicLong(0)
        performanceMetrics["ui_operations"] = AtomicLong(0)
    }
    
    private fun scheduleMemoryChecks() {
        backgroundScope.launch {
            while (isActive) {
                delay(MEMORY_CHECK_INTERVAL_MS)
                checkMemoryUsage()
            }
        }
    }
    
    /**
     * Optimize memory usage by clearing caches if needed
     */
    fun optimizeMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryPercentage = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
        
        if (memoryPercentage > 80) {
            // Clear half of the image cache
            imageCache.trimToSize(cacheSize / 2)
            
            // Suggest garbage collection
            System.gc()
        }
        
        lastMemoryCheck = System.currentTimeMillis()
    }
    
    /**
     * Cache bitmap with automatic memory management
     */
    fun cacheBitmap(key: String, bitmap: Bitmap) {
        imageCache.put(key, bitmap)
    }
    
    /**
     * Get cached bitmap
     */
    fun getCachedBitmap(key: String): Bitmap? {
        val bitmap = imageCache.get(key)
        if (bitmap != null) {
            performanceMetrics["cache_hits"]?.incrementAndGet()
        } else {
            performanceMetrics["cache_misses"]?.incrementAndGet()
        }
        return bitmap
    }
    
    /**
     * Optimize RecyclerView performance
     */
    fun optimizeRecyclerView(
        recyclerView: RecyclerView,
        config: RecyclerViewConfig = RecyclerViewConfig()
    ) {
        recyclerView.apply {
            setHasFixedSize(config.hasFixedSize)
            isNestedScrollingEnabled = config.isNestedScrollingEnabled
            
            // Set item view cache size for better scrolling performance
            setItemViewCacheSize(config.prefetchItemCount)
            
            // Enable drawing cache for smoother scrolling
            isDrawingCacheEnabled = true
            drawingCacheQuality = android.view.View.DRAWING_CACHE_QUALITY_HIGH
        }
        
        recyclerViewOptimizations[recyclerView.toString()] = config
    }
    
    /**
     * Execute background task with performance tracking
     */
    fun <T> executeBackgroundTask(
        taskName: String,
        task: suspend () -> T,
        onComplete: ((T) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        performanceMetrics["background_tasks"]?.incrementAndGet()
        
        backgroundScope.launch {
            val startTime = System.currentTimeMillis()
            
            try {
                val result = task()
                val duration = System.currentTimeMillis() - startTime
                
                performanceMetrics[taskName] = AtomicLong(duration)
                
                onComplete?.let { callback ->
                    mainHandler.post { callback(result) }
                }
            } catch (e: Exception) {
                onError?.let { callback ->
                    mainHandler.post { callback(e) }
                }
            }
        }
    }
    
    /**
     * Track UI operation performance
     */
    fun trackUIOperation(operationName: String, operation: () -> Unit) {
        val startTime = System.currentTimeMillis()
        performanceMetrics["ui_operations"]?.incrementAndGet()
        
        operation()
        
        val duration = System.currentTimeMillis() - startTime
        performanceMetrics["ui_$operationName"] = AtomicLong(duration)
    }
    
    /**
     * Get performance report
     */
    fun getPerformanceReport(): PerformanceReport {
        val runtime = Runtime.getRuntime()
        val memoryUsage = runtime.totalMemory() - runtime.freeMemory()
        
        val cacheHits = performanceMetrics["cache_hits"]?.get() ?: 0
        val cacheMisses = performanceMetrics["cache_misses"]?.get() ?: 0
        val totalCacheRequests = cacheHits + cacheMisses
        val cacheHitRate = if (totalCacheRequests > 0) {
            cacheHits.toDouble() / totalCacheRequests.toDouble()
        } else 0.0
        
        val operationTimes = performanceMetrics
            .filter { !it.key.startsWith("cache_") && !it.key.startsWith("background_") && !it.key.startsWith("ui_operations") }
            .mapValues { it.value.get() }
        
        val recommendations = generateRecommendations(memoryUsage, cacheHitRate, operationTimes)
        
        return PerformanceReport(
            startupTime = System.currentTimeMillis() - startupTime,
            memoryUsage = memoryUsage,
            cacheHitRate = cacheHitRate,
            operationTimes = operationTimes,
            recommendations = recommendations
        )
    }
    
    private fun generateRecommendations(
        memoryUsage: Long,
        cacheHitRate: Double,
        operationTimes: Map<String, Long>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Memory recommendations
        val maxMemory = Runtime.getRuntime().maxMemory()
        val memoryPercentage = (memoryUsage.toDouble() / maxMemory.toDouble()) * 100
        
        if (memoryPercentage > 70) {
            recommendations.add("High memory usage detected (${memoryPercentage.toInt()}%). Consider clearing caches.")
        }
        
        // Cache recommendations
        if (cacheHitRate < 0.5) {
            recommendations.add("Low cache hit rate (${(cacheHitRate * 100).toInt()}%). Consider increasing cache size.")
        }
        
        // Operation time recommendations
        operationTimes.forEach { (operation, time) ->
            if (time > TARGET_OPERATION_TIME_MS) {
                recommendations.add("Operation '$operation' took ${time}ms. Consider optimization.")
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("Performance is optimal!")
        }
        
        return recommendations
    }
    
    private fun checkMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryPercentage = (usedMemory.toDouble() / maxMemory.toDouble()) * 100
        
        if (memoryPercentage > 85) {
            optimizeMemoryUsage()
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        backgroundScope.cancel()
        imageCache.evictAll()
        recyclerViewOptimizations.clear()
        performanceMetrics.clear()
    }
}
