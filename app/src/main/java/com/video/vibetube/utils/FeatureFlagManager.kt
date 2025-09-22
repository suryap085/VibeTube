package com.video.vibetube.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

/**
 * YouTube Policy Compliant Feature Flag Manager
 * 
 * Compliance Features:
 * - Gradual rollout for safe deployment
 * - User can opt-out of experimental features
 * - No A/B testing on user data
 * - Transparent feature availability
 * - Local configuration only
 */
class FeatureFlagManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "feature_flags", Context.MODE_PRIVATE
    )
    private val gson = Gson()
    
    companion object {
        // Feature Flag Keys
        const val LIBRARY_FEATURES = "library_features"
        const val ACHIEVEMENT_SYSTEM = "achievement_system"
        const val SOCIAL_SHARING = "social_sharing"
        const val SMART_RECOMMENDATIONS = "smart_recommendations"
        const val ANALYTICS_TRACKING = "analytics_tracking"
        const val NOTIFICATION_SYSTEM = "notification_system"
        const val CONTENT_CURATION = "content_curation"
        const val ENHANCED_DISCOVERY = "enhanced_discovery"
        
        // Rollout Configuration Keys
        private const val KEY_ROLLOUT_CONFIG = "rollout_config"
        private const val KEY_USER_TIER = "user_tier"
        private const val KEY_EXPERIMENTAL_FEATURES = "experimental_features_enabled"
        private const val KEY_FEATURE_OVERRIDES = "feature_overrides"
        
        // User Tiers for Gradual Rollout
        const val TIER_ALPHA = "alpha"      // 1% - Internal testing
        const val TIER_BETA = "beta"       // 10% - Early adopters
        const val TIER_STABLE = "stable"   // 100% - General availability
    }
    
    data class FeatureFlag(
        val key: String,
        val name: String,
        val description: String,
        val defaultEnabled: Boolean = false,
        val minimumTier: String = TIER_STABLE,
        val requiresConsent: Boolean = false,
        val dependencies: List<String> = emptyList()
    )
    
    data class RolloutConfig(
        val version: String = "1.0.0",
        val alphaPercentage: Int = 1,
        val betaPercentage: Int = 10,
        val stablePercentage: Int = 100,
        val lastUpdated: Long = System.currentTimeMillis()
    )
    
    private val featureFlags = mapOf(
        LIBRARY_FEATURES to FeatureFlag(
            key = LIBRARY_FEATURES,
            name = "Library Features",
            description = "Personal watch history, favorites, and playlists",
            defaultEnabled = true,
            minimumTier = TIER_STABLE,
            requiresConsent = false
        ),
        ACHIEVEMENT_SYSTEM to FeatureFlag(
            key = ACHIEVEMENT_SYSTEM,
            name = "Achievement System",
            description = "Gamification with achievements and progress tracking",
            defaultEnabled = true,
            minimumTier = TIER_STABLE,
            requiresConsent = false
        ),
        SOCIAL_SHARING to FeatureFlag(
            key = SOCIAL_SHARING,
            name = "Social Sharing",
            description = "Share videos, playlists, and achievements",
            defaultEnabled = true,
            minimumTier = TIER_STABLE,
            requiresConsent = false
        ),
        SMART_RECOMMENDATIONS to FeatureFlag(
            key = SMART_RECOMMENDATIONS,
            name = "Smart Recommendations",
            description = "AI-powered content recommendations",
            defaultEnabled = true,
            minimumTier = TIER_STABLE,
            requiresConsent = false,
            dependencies = emptyList()
        ),
        ANALYTICS_TRACKING to FeatureFlag(
            key = ANALYTICS_TRACKING,
            name = "Analytics Tracking",
            description = "Local engagement analytics and insights",
            defaultEnabled = true,
            minimumTier = TIER_STABLE,
            requiresConsent = true
        ),
        NOTIFICATION_SYSTEM to FeatureFlag(
            key = NOTIFICATION_SYSTEM,
            name = "Achievement Notifications",
            description = "Push notifications for achievements and milestones",
            defaultEnabled = true,
            minimumTier = TIER_STABLE,
            requiresConsent = false
        ),
        CONTENT_CURATION to FeatureFlag(
            key = CONTENT_CURATION,
            name = "Content Curation",
            description = "Curated content collections and themes",
            defaultEnabled = false,
            minimumTier = TIER_BETA,
            requiresConsent = false
        ),
        ENHANCED_DISCOVERY to FeatureFlag(
            key = ENHANCED_DISCOVERY,
            name = "Enhanced Discovery",
            description = "Advanced search and discovery features",
            defaultEnabled = true,
            minimumTier = TIER_STABLE,
            requiresConsent = false
        )
    )
    
    init {
        initializeUserTier()
        initializeRolloutConfig()
    }
    
    /**
     * Check if a feature is enabled for the current user
     */
    fun isFeatureEnabled(featureKey: String): Boolean {
        val flag = featureFlags[featureKey] ?: return false
        
        // Check if user has opted out of experimental features
        if (!isExperimentalFeaturesEnabled() && flag.minimumTier != TIER_STABLE) {
            return false
        }
        
        // Check user tier eligibility
        if (!isUserEligibleForTier(flag.minimumTier)) {
            return false
        }
        
        // Check dependencies
        if (!areDependenciesMet(flag.dependencies)) {
            return false
        }
        
        // Check consent requirements
        if (flag.requiresConsent && !hasRequiredConsent(featureKey)) {
            return false
        }
        
        // Check for manual overrides
        val overrides = getFeatureOverrides()
        if (overrides.containsKey(featureKey)) {
            return overrides[featureKey] == true
        }
        
        return flag.defaultEnabled
    }
    
    /**
     * Get all available features for the current user
     */
    fun getAvailableFeatures(): List<FeatureFlag> {
        return featureFlags.values.filter { flag ->
            isUserEligibleForTier(flag.minimumTier) &&
            areDependenciesMet(flag.dependencies)
        }
    }
    
    /**
     * Get enabled features for the current user
     */
    fun getEnabledFeatures(): List<FeatureFlag> {
        return featureFlags.values.filter { flag ->
            isFeatureEnabled(flag.key)
        }
    }
    
    /**
     * Manually override a feature flag (for testing/debugging)
     */
    fun setFeatureOverride(featureKey: String, enabled: Boolean) {
        val overrides = getFeatureOverrides().toMutableMap()
        overrides[featureKey] = enabled
        saveFeatureOverrides(overrides)
    }
    
    /**
     * Clear feature override
     */
    fun clearFeatureOverride(featureKey: String) {
        val overrides = getFeatureOverrides().toMutableMap()
        overrides.remove(featureKey)
        saveFeatureOverrides(overrides)
    }
    
    /**
     * Clear all feature overrides
     */
    fun clearAllOverrides() {
        prefs.edit { remove(KEY_FEATURE_OVERRIDES) }
    }
    
    /**
     * Enable/disable experimental features for user
     */
    fun setExperimentalFeaturesEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_EXPERIMENTAL_FEATURES, enabled) }
    }
    
    /**
     * Check if user has experimental features enabled
     */
    fun isExperimentalFeaturesEnabled(): Boolean {
        return prefs.getBoolean(KEY_EXPERIMENTAL_FEATURES, false)
    }
    
    /**
     * Get user's current tier
     */
    fun getUserTier(): String {
        return prefs.getString(KEY_USER_TIER, TIER_STABLE) ?: TIER_STABLE
    }
    
    /**
     * Force user tier (for testing)
     */
    fun setUserTier(tier: String) {
        prefs.edit { putString(KEY_USER_TIER, tier) }
    }
    
    /**
     * Get rollout configuration
     */
    fun getRolloutConfig(): RolloutConfig {
        val json = prefs.getString(KEY_ROLLOUT_CONFIG, null)
        return if (json != null) {
            try {
                gson.fromJson(json, RolloutConfig::class.java) ?: RolloutConfig()
            } catch (e: Exception) {
                RolloutConfig()
            }
        } else {
            RolloutConfig()
        }
    }
    
    /**
     * Update rollout configuration
     */
    fun updateRolloutConfig(config: RolloutConfig) {
        val json = gson.toJson(config)
        prefs.edit { putString(KEY_ROLLOUT_CONFIG, json) }
        
        // Re-evaluate user tier based on new config
        initializeUserTier()
    }
    
    /**
     * Get feature flag details
     */
    fun getFeatureFlag(featureKey: String): FeatureFlag? {
        return featureFlags[featureKey]
    }
    
    /**
     * Get all feature flags
     */
    fun getAllFeatureFlags(): Map<String, FeatureFlag> {
        return featureFlags
    }
    
    /**
     * Get feature status summary for debugging
     */
    fun getFeatureStatusSummary(): Map<String, Any> {
        return mapOf(
            "user_tier" to getUserTier(),
            "experimental_enabled" to isExperimentalFeaturesEnabled(),
            "enabled_features" to getEnabledFeatures().map { it.key },
            "available_features" to getAvailableFeatures().map { it.key },
            "overrides" to getFeatureOverrides(),
            "rollout_config" to getRolloutConfig()
        )
    }
    
    // Private helper methods
    
    private fun initializeUserTier() {
        if (!prefs.contains(KEY_USER_TIER)) {
            val tier = determineUserTier()
            prefs.edit { putString(KEY_USER_TIER, tier) }
        }
    }
    
    private fun initializeRolloutConfig() {
        if (!prefs.contains(KEY_ROLLOUT_CONFIG)) {
            val config = RolloutConfig()
            val json = gson.toJson(config)
            prefs.edit { putString(KEY_ROLLOUT_CONFIG, json) }
        }
    }
    
    private fun determineUserTier(): String {
        val config = getRolloutConfig()
        
        // Use a deterministic hash based on device/app info
        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
        
        val hash = deviceId.hashCode()
        val percentage = Math.abs(hash % 100)
        
        return when {
            percentage < config.alphaPercentage -> TIER_ALPHA
            percentage < config.betaPercentage -> TIER_BETA
            else -> TIER_STABLE
        }
    }
    
    private fun isUserEligibleForTier(requiredTier: String): Boolean {
        val userTier = getUserTier()
        
        return when (requiredTier) {
            TIER_ALPHA -> userTier == TIER_ALPHA
            TIER_BETA -> userTier == TIER_ALPHA || userTier == TIER_BETA
            TIER_STABLE -> true
            else -> false
        }
    }
    
    private fun areDependenciesMet(dependencies: List<String>): Boolean {
        return dependencies.all { dependency ->
            isFeatureEnabled(dependency)
        }
    }
    
    private fun hasRequiredConsent(featureKey: String): Boolean {
        // Check if user has given consent for features that require it
        // Use the same SharedPreferences file as UserDataManager
        val userDataPrefs = context.getSharedPreferences("user_engagement_data", Context.MODE_PRIVATE)
        return userDataPrefs.getBoolean("user_data_consent", false)
    }
    
    private fun getFeatureOverrides(): Map<String, Boolean> {
        val json = prefs.getString(KEY_FEATURE_OVERRIDES, null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Boolean>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun saveFeatureOverrides(overrides: Map<String, Boolean>) {
        val json = gson.toJson(overrides)
        prefs.edit { putString(KEY_FEATURE_OVERRIDES, json) }
    }
}
