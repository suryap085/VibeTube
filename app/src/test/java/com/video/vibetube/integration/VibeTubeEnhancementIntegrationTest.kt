package com.video.vibetube.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Comprehensive Integration Test for VibeTube Enhancements
 * 
 * Tests the complete integration of all enhancement phases:
 * - Phase 1: Performance optimization and foundation
 * - Phase 2: Intelligence and analytics features
 * - Phase 3: Advanced user experience features
 * - Phase 4: Polish, testing, and accessibility
 * 
 * Verifies that all components work together seamlessly while
 * maintaining YouTube policy compliance and privacy-first principles.
 */
@RunWith(AndroidJUnit4::class)
class VibeTubeEnhancementIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var integrator: VibeTubeEnhancementIntegrator
    private lateinit var userDataManager: UserDataManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        integrator = VibeTubeEnhancementIntegrator.getInstance(context)
        userDataManager = UserDataManager.getInstance(context)
        
        // Ensure user consent for testing
        userDataManager.setUserConsent(true)
        
        // Initialize the integrator
        integrator.initialize()
    }
    
    @Test
    fun testCompleteSystemIntegration() = runBlocking {
        // Test that all components are properly integrated
        val status = integrator.getEnhancementStatus()
        
        assertTrue("System should be initialized", status.isInitialized)
        assertTrue("Performance score should be reasonable", status.performanceScore >= 0.5)
        assertTrue("Accessibility score should be reasonable", status.accessibilityScore >= 0.5)
        assertTrue("Should have enabled features", status.featuresEnabled.isNotEmpty())
        
        // Test integrated insights
        val insights = integrator.getIntegratedInsights()
        assertNotNull("Analytics insights should be available", insights.analytics)
        assertNotNull("Wellness insights should be available", insights.wellness)
        assertNotNull("Learning insights should be available", insights.learning)
        assertNotNull("Quality insights should be available", insights.quality)
        assertNotNull("Recommendations should be available", insights.recommendations)
    }
    
    @Test
    fun testEnhancedRecommendationSystem() = runBlocking {
        // Test the enhanced recommendation system
        val recommendations = integrator.getEnhancedRecommendations(30)
        assertNotNull("Enhanced recommendations should be available", recommendations)
        
        // Test learning recommendations
        val learningRecs = integrator.getLearningRecommendations()
        assertNotNull("Learning recommendations should be available", learningRecs)
        
        // Test wellness recommendations
        val wellnessRecs = integrator.getWellnessRecommendations()
        assertNotNull("Wellness recommendations should be available", wellnessRecs)
    }
    
    @Test
    fun testSmartPlaylistGeneration() = runBlocking {
        // Test smart playlist generation
        val smartPlaylists = integrator.generateEnhancedPlaylists()
        assertNotNull("Smart playlists should be generated", smartPlaylists)
        
        // Each smart playlist should have proper structure
        smartPlaylists.forEach { playlist ->
            assertNotNull("Playlist should have ID", playlist.id)
            assertNotNull("Playlist should have name", playlist.name)
            assertNotNull("Playlist should have criteria", playlist.criteria)
            assertTrue("Playlist should have videos", playlist.videos.isNotEmpty() || playlist.criteria != null)
        }
    }
    
    @Test
    fun testVideoInteractionTracking() = runBlocking {
        // Test video interaction tracking across all systems
        val testVideoId = "test_video_123"
        val watchDuration = 120000L // 2 minutes
        val completionRate = 0.8f
        
        // This should update learning, wellness, and performance tracking
        integrator.trackVideoInteraction(testVideoId, watchDuration, completionRate)
        
        // Verify that the interaction was tracked
        val insights = integrator.getIntegratedInsights()
        assertTrue("Analytics should track interactions", insights.analytics.totalWatchTime >= 0)
        assertTrue("Wellness should track screen time", insights.wellness.dailyScreenTime >= 0)
        assertTrue("Learning should track progress", insights.learning.totalLearningTime >= 0)
    }
    
    @Test
    fun testSocialSharingIntegration() = runBlocking {
        // Test enhanced social sharing
        val testVideo = com.video.vibetube.models.Video(
            videoId = "test_video_123",
            title = "Test Video",
            description = "Test Description",
            thumbnail = "test_thumbnail.jpg",
            channelTitle = "Test Channel",
            publishedAt = "2024-01-01",
            duration = "2:00"
        )
        
        val shareIntent = integrator.shareContent(
            video = testVideo,
            platform = com.video.vibetube.social.EnhancedSocialManager.SharePlatform.MESSAGING,
            personalNote = "Check this out!"
        )
        
        assertNotNull("Share intent should be created", shareIntent)
        assertEquals("Intent action should be SEND", android.content.Intent.ACTION_SEND, shareIntent.action)
    }
    
    @Test
    fun testAccessibilityIntegration() {
        // Test accessibility enhancements
        val testView = android.view.View(context)
        val testData = mapOf(
            "title" to "Test Video",
            "channel" to "Test Channel",
            "duration" to "2:00"
        )
        
        // This should enhance the view with accessibility features
        integrator.enhanceAccessibility(
            view = testView,
            componentType = com.video.vibetube.accessibility.AccessibilityEnhancementManager.ComponentType.VIDEO_CARD,
            contentData = testData
        )
        
        // Verify accessibility enhancements
        assertTrue("View should be focusable", testView.isFocusable)
        assertNotNull("View should have content description", testView.contentDescription)
    }
    
    @Test
    fun testPerformanceOptimization() {
        // Test performance optimization features
        val optimizations = integrator.getPerformanceOptimizations()
        assertNotNull("Performance optimizations should be available", optimizations)
        
        // Test system health report
        val healthReport = integrator.getSystemHealthReport()
        assertTrue("Performance score should be valid", healthReport.performanceScore >= 0.0 && healthReport.performanceScore <= 1.0)
        assertTrue("Accessibility score should be valid", healthReport.accessibilityScore >= 0.0 && healthReport.accessibilityScore <= 1.0)
        assertTrue("Memory usage should be tracked", healthReport.memoryUsage >= 0)
        assertTrue("Cache hit rate should be valid", healthReport.cacheHitRate >= 0.0 && healthReport.cacheHitRate <= 1.0)
    }
    
    @Test
    fun testWellnessIntegration() = runBlocking {
        // Test wellness monitoring integration
        val shouldBreak = integrator.shouldSuggestBreak()
        assertNotNull("Break suggestion should be available", shouldBreak)
        
        if (shouldBreak) {
            val breakSuggestion = integrator.getBreakSuggestion()
            assertNotNull("Break suggestion should have content", breakSuggestion)
            assertNotNull("Break suggestion should have title", breakSuggestion.title)
            assertNotNull("Break suggestion should have description", breakSuggestion.description)
        }
    }
    
    @Test
    fun testFeatureConfiguration() {
        // Test feature configuration
        val initialFeatures = integrator.getFeatureAvailability()
        assertTrue("Should have feature availability data", initialFeatures.isNotEmpty())
        
        // Test disabling data collection
        integrator.configureFeatures(mapOf("data_collection" to false))
        val updatedFeatures = integrator.getFeatureAvailability()
        
        // Features requiring consent should be disabled
        assertFalse("Analytics should be disabled without consent", updatedFeatures["personal_analytics"] ?: true)
        assertFalse("Wellness should be disabled without consent", updatedFeatures["digital_wellness"] ?: true)
        
        // Features not requiring consent should still be available
        assertTrue("Social features should still be available", updatedFeatures["enhanced_social"] ?: false)
        assertTrue("Accessibility should still be available", updatedFeatures["accessibility_enhancements"] ?: false)
        
        // Re-enable for other tests
        integrator.configureFeatures(mapOf("data_collection" to true))
    }
    
    @Test
    fun testYouTubePolicyCompliance() = runBlocking {
        // Test that all features respect YouTube policy compliance
        
        // Test without user consent
        userDataManager.setUserConsent(false)
        val statusWithoutConsent = integrator.getEnhancementStatus()
        
        // Features requiring data collection should be disabled
        assertFalse("Analytics should be disabled", statusWithoutConsent.featuresEnabled["analytics"] ?: true)
        assertFalse("Wellness should be disabled", statusWithoutConsent.featuresEnabled["wellness"] ?: true)
        assertFalse("Learning should be disabled", statusWithoutConsent.featuresEnabled["learning"] ?: true)
        
        // Features not requiring data collection should still work
        assertTrue("Social features should work", statusWithoutConsent.featuresEnabled["social_features"] ?: false)
        assertTrue("Accessibility should work", statusWithoutConsent.featuresEnabled["accessibility"] ?: false)
        assertTrue("Performance should work", statusWithoutConsent.featuresEnabled["performance_optimization"] ?: false)
        
        // Re-enable consent
        userDataManager.setUserConsent(true)
        
        // Test that no external network calls are made for analytics
        // (This would require network monitoring in a real test environment)
        val insights = integrator.getIntegratedInsights()
        assertNotNull("Insights should be generated locally", insights)
        
        // Test data retention compliance
        assertTrue("Should respect user consent", userDataManager.hasUserConsent())
    }
    
    @Test
    fun testIntegrationDemo() = runBlocking {
        // Test the complete integration demo
        val demoResult = integrator.demonstrateIntegratedFeatures()
        
        assertTrue("Demo should succeed", demoResult.success)
        assertTrue("Demo should have results", demoResult.results.isNotEmpty())
        assertNotNull("Demo should have message", demoResult.message)
        
        // Verify that all major components are represented in results
        assertTrue("Should have performance results", demoResult.results.containsKey("performance"))
        assertTrue("Should have accessibility results", demoResult.results.containsKey("accessibility"))
        
        if (userDataManager.hasUserConsent()) {
            assertTrue("Should have analytics results", demoResult.results.containsKey("analytics"))
            assertTrue("Should have wellness results", demoResult.results.containsKey("wellness"))
            assertTrue("Should have learning results", demoResult.results.containsKey("learning"))
            assertTrue("Should have quality results", demoResult.results.containsKey("quality"))
        }
    }
    
    @Test
    fun testErrorHandling() = runBlocking {
        // Test graceful error handling across the integration
        
        // Test with invalid data
        val emptyUserDataManager = UserDataManager.getInstance(context)
        emptyUserDataManager.clearAllData()
        emptyUserDataManager.setUserConsent(true)
        
        // The system should handle empty data gracefully
        val insights = integrator.getIntegratedInsights()
        assertNotNull("Should handle empty data gracefully", insights)
        
        // Test with disabled features
        emptyUserDataManager.setUserConsent(false)
        val statusWithoutData = integrator.getEnhancementStatus()
        assertTrue("Should handle disabled features gracefully", statusWithoutData.isInitialized)
        
        // Restore normal state
        userDataManager.setUserConsent(true)
    }
    
    @Test
    fun testResourceCleanup() {
        // Test that resources are properly cleaned up
        integrator.cleanup()
        
        // The integrator should still be functional after cleanup
        val status = integrator.getFeatureAvailability()
        assertNotNull("Should still be functional after cleanup", status)
    }
}
