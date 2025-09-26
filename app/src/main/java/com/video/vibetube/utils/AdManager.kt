package com.video.vibetube.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Enhanced AdManager for VibeTube - YouTube Policy Compliant
 *
 * Features:
 * - Advanced frequency capping and user experience optimization
 * - YouTube Policy compliance for ad placement
 * - Performance optimization with memory management
 * - Comprehensive error handling and fallback strategies
 * - User preference controls and consent management
 * - Analytics and performance tracking
 */
class AdManager(private val context: Context) {
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var interstitialAdCounter = 0
    private val prefs: SharedPreferences = context.getSharedPreferences("ad_manager_prefs", Context.MODE_PRIVATE)
    private val adLoadingCache = ConcurrentHashMap<String, Boolean>()
    private val adFailureCount = ConcurrentHashMap<String, Int>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Frequency capping settings
    private val INTERSTITIAL_AD_FREQUENCY = 3
    private val NATIVE_AD_FREQUENCY = 6
    private val MAX_ADS_PER_SESSION = 15
    private val MIN_TIME_BETWEEN_INTERSTITIALS = 60000L // 1 minute
    private val MAX_AD_FAILURES_BEFORE_COOLDOWN = 3
    private val AD_FAILURE_COOLDOWN_TIME = 300000L // 5 minutes

    companion object {
        const val TAG = "AdManager"

        // Production Ad Unit IDs - YouTube Policy Compliant
        //const val BANNER_AD_UNIT_ID = "ca-app-pub-8519418185028322/1079794018"
        const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        //const val NATIVE_AD_UNIT_ID = "ca-app-pub-8519418185028322/2640287692"
        const val NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

        // Test Ad Unit IDs for development
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        //const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

        // Testing mode flag
        var TESTING_MODE = false

        // User preference keys
        const val PREF_BANNER_ADS_ENABLED = "banner_ads_enabled"
        const val PREF_NATIVE_ADS_ENABLED = "native_ads_enabled"
        const val PREF_INTERSTITIAL_ADS_ENABLED = "interstitial_ads_enabled"
        const val PREF_REWARDED_ADS_ENABLED = "rewarded_ads_enabled"
        const val PREF_PERSONALIZED_ADS = "personalized_ads_enabled"
        const val PREF_LAST_INTERSTITIAL_TIME = "last_interstitial_time"
        const val PREF_SESSION_AD_COUNT = "session_ad_count"

        // YouTube Policy compliance flags
        const val YOUTUBE_CONTENT_CONTEXT = "youtube_content"
        const val NON_YOUTUBE_CONTEXT = "general_content"
    }

    /**
     * Enhanced banner ad loading with user preferences and error handling
     */
    fun loadBannerAd(adView: AdView, contentContext: String = NON_YOUTUBE_CONTEXT) {
        if (!isBannerAdsEnabled() || isAdLoadingCooldown("banner")) {
            adView.visibility = android.view.View.GONE
            return
        }

        val cacheKey = "banner_${adView.hashCode()}"
        if (adLoadingCache[cacheKey] == true) return

        adLoadingCache[cacheKey] = true

        val adRequest = buildAdRequest(contentContext)

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                super.onAdLoaded()
                Log.d(TAG, "Banner ad loaded successfully")
                adLoadingCache.remove(cacheKey)
                resetAdFailureCount("banner")
                incrementSessionAdCount()
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                super.onAdFailedToLoad(loadAdError)
                Log.w(TAG, "Banner ad failed to load: ${loadAdError.message}")
                adLoadingCache.remove(cacheKey)
                handleAdFailure("banner", loadAdError)
                adView.visibility = android.view.View.GONE
            }

            override fun onAdClicked() {
                super.onAdClicked()
                Log.d(TAG, "Banner ad clicked")
                trackAdInteraction("banner", "click")
            }
        }

        adView.loadAd(adRequest)
    }

    /**
     * Enhanced interstitial ad loading with frequency capping
     */
    fun loadInterstitialAd(contentContext: String = NON_YOUTUBE_CONTEXT) {
        if (!isInterstitialAdsEnabled() || isAdLoadingCooldown("interstitial")) {
            return
        }

        val cacheKey = "interstitial"
        if (adLoadingCache[cacheKey] == true) return

        adLoadingCache[cacheKey] = true

        val adRequest = buildAdRequest(contentContext)

        InterstitialAd.load(context, INTERSTITIAL_AD_UNIT_ID, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.w(TAG, "Interstitial ad failed to load: ${adError.message}")
                adLoadingCache.remove(cacheKey)
                handleAdFailure("interstitial", adError)
                interstitialAd = null
            }

            override fun onAdLoaded(ad: InterstitialAd) {
                Log.d(TAG, "Interstitial ad loaded successfully")
                adLoadingCache.remove(cacheKey)
                resetAdFailureCount("interstitial")
                interstitialAd = ad

                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad dismissed")
                        interstitialAd = null
                        prefs.edit().putLong(PREF_LAST_INTERSTITIAL_TIME, System.currentTimeMillis()).apply()
                        incrementSessionAdCount()
                        trackAdInteraction("interstitial", "dismiss")
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        Log.w(TAG, "Interstitial ad failed to show: ${adError.message}")
                        interstitialAd = null
                        handleAdFailure("interstitial", adError)
                    }

                    override fun onAdShowedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad showed")
                        trackAdInteraction("interstitial", "show")
                    }

                    override fun onAdClicked() {
                        Log.d(TAG, "Interstitial ad clicked")
                        trackAdInteraction("interstitial", "click")
                    }
                }
            }
        })
    }

    fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }
            }
        )
    }

    fun showInterstitialAd() {
        interstitialAdCounter++
        if (interstitialAdCounter >= INTERSTITIAL_AD_FREQUENCY && interstitialAd != null) {
            interstitialAd?.show(context as Activity)
            interstitialAdCounter = 0
        }
    }

    /**
     * Enhanced rewarded ad loading with YouTube Policy compliance
     */
    fun loadRewardedAd(
        onSuccess: (RewardedAd) -> Unit,
        onFailure: (LoadAdError) -> Unit,
        contentContext: String = NON_YOUTUBE_CONTEXT
    ) {
        if (!isRewardedAdsEnabled() && !TESTING_MODE) {
            Log.w(TAG, "Rewarded ads are disabled by user preference")
            onFailure(LoadAdError(0, "Rewarded ads disabled by user", "", null, null))
            return
        }

        if (isAdLoadingCooldown("rewarded") && !TESTING_MODE) {
            Log.w(TAG, "Rewarded ads are in cooldown period")
            onFailure(LoadAdError(0, "Ad loading in cooldown", "", null, null))
            return
        }

        val cacheKey = "rewarded_${System.currentTimeMillis()}"
        if (adLoadingCache[cacheKey] == true) return

        adLoadingCache[cacheKey] = true
        Log.d(TAG, "Loading rewarded ad with context: $contentContext")

        val adRequest = buildEnhancedAdRequest(contentContext)

        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Rewarded ad failed to load - Error Code: ${adError.code}, Message: ${adError.message}")
                    adLoadingCache.remove(cacheKey)
                    handleAdFailure("rewarded", adError)
                    onFailure(adError)
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Rewarded ad loaded successfully")
                    adLoadingCache.remove(cacheKey)
                    resetAdFailureCount("rewarded")
                    incrementSessionAdCount()
                    trackAdInteraction("rewarded", "load")

                    // Set up ad callbacks
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdClicked() {
                            Log.d(TAG, "Rewarded ad clicked")
                            trackAdInteraction("rewarded", "click")
                        }

                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Rewarded ad dismissed")
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.e(TAG, "Rewarded ad failed to show: ${adError.message}")
                        }

                        override fun onAdImpression() {
                            Log.d(TAG, "Rewarded ad impression recorded")
                            trackAdInteraction("rewarded", "impression")
                        }

                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "Rewarded ad showed full screen content")
                        }
                    }

                    onSuccess(ad)
                }
            }
        )
    }

    fun showRewardedAd(rewardedAd: RewardedAd, onRewardEarned: () -> Unit, onAdClosed: () -> Unit = {}) {
        if (context is Activity) {
            rewardedAd.show(context) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onRewardEarned()
            }

            // Update the callback to handle ad closure
            rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad dismissed")
                    onAdClosed()
                }
            }
        } else {
            Log.e(TAG, "Cannot show rewarded ad: context is not an Activity")
        }
    }

    @Deprecated("Use the new loadRewardedAd method instead")
    fun showRewardedAd(onRewardEarned: () -> Unit) {
        rewardedAd?.let { ad ->
            ad.show(context as Activity) { rewardItem ->
                onRewardEarned()
                loadRewardedAd() // Load next rewarded ad
            }
        }
    }

    private fun setupInterstitialCallbacks() {
        interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadInterstitialAd()
            }
        }
    }

    /**
     * Enhanced native ad loading with YouTube Policy compliance
     */
    fun loadNativeAd(
        onSuccess: (NativeAd) -> Unit,
        onFailure: (LoadAdError) -> Unit,
        contentContext: String = NON_YOUTUBE_CONTEXT
    ) {
        // Temporarily disable frequency capping for testing
        Log.d(TAG, "loadNativeAd called - Native ads enabled: ${isNativeAdsEnabled()}, Cooldown: ${isAdLoadingCooldown("native")}")

        if (!isNativeAdsEnabled() && !TESTING_MODE) {
            Log.w(TAG, "Native ads are disabled by user preference")
            onFailure(LoadAdError(0, "Native ads disabled by user", "", null, null))
            return
        }

        if (isAdLoadingCooldown("native") && !TESTING_MODE) {
            Log.w(TAG, "Native ads are in cooldown period")
            onFailure(LoadAdError(0, "Ad loading in cooldown", "", null, null))
            return
        }

        val cacheKey = "native_${System.currentTimeMillis()}"
        if (adLoadingCache[cacheKey] == true) return

        adLoadingCache[cacheKey] = true

        val adLoader = AdLoader.Builder(context, NATIVE_AD_UNIT_ID)
            .forNativeAd { nativeAd ->
                Log.d(TAG, "Native ad loaded successfully")
                adLoadingCache.remove(cacheKey)
                resetAdFailureCount("native")
                incrementSessionAdCount()
                trackAdInteraction("native", "load")
                onSuccess(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.e(TAG, "Native ad failed to load - Error Code: ${adError.code}, Message: ${adError.message}, Domain: ${adError.domain}")
                    Log.e(TAG, "Native ad failure details - Cause: ${adError.cause}, Response Info: ${adError.responseInfo}")

                    // Enhanced error handling for common "No fill" scenarios
                    when (adError.code) {
                        3 -> Log.w(TAG, "No fill - No ads available for this request")
                        0 -> Log.w(TAG, "Internal error - AdMob internal issue")
                        1 -> Log.w(TAG, "Invalid request - Check ad unit ID and request parameters")
                        2 -> Log.w(TAG, "Network error - Check internet connection")
                        else -> Log.w(TAG, "Unknown error code: ${adError.code}")
                    }

                    adLoadingCache.remove(cacheKey)
                    handleAdFailure("native", adError)
                    onFailure(adError)
                }

                override fun onAdClicked() {
                    super.onAdClicked()
                    Log.d(TAG, "Native ad clicked")
                    trackAdInteraction("native", "click")
                }

                override fun onAdOpened() {
                    super.onAdOpened()
                    Log.d(TAG, "Native ad opened")
                }

                override fun onAdClosed() {
                    super.onAdClosed()
                    Log.d(TAG, "Native ad closed")
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .setVideoOptions(
                        VideoOptions.Builder()
                            .setStartMuted(true)
                            .setCustomControlsRequested(false)
                            .build()
                    )
                    .build()
            )
            .build()

        // Enhanced ad request for better fill rates
        val adRequest = buildEnhancedAdRequest(contentContext)
        Log.d(TAG, "Loading native ad with enhanced request")
        adLoader.loadAd(adRequest)
    }

    /**
     * Enhanced ad request builder specifically for native ads to improve fill rates
     */
    private fun buildEnhancedAdRequest(contentContext: String): AdRequest {
        val builder = AdRequest.Builder()

        // Add multiple keywords to improve targeting and fill rate
        val keywords = mutableListOf<String>()

        if (contentContext == YOUTUBE_CONTENT_CONTEXT) {
            keywords.addAll(listOf("video", "entertainment", "youtube", "streaming", "content"))
        } else {
            keywords.addAll(listOf("mobile", "android", "app", "entertainment", "media", "social"))
        }

        // Add all keywords
        keywords.forEach { keyword ->
            builder.addKeyword(keyword)
        }

        // Respect user's personalized ads preference
        if (!isPersonalizedAdsEnabled()) {
            val extras = android.os.Bundle()
            extras.putString("npa", "1")
            builder.addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, extras)
        }

        // Set request agent
        builder.setRequestAgent("VibeTube_Native_1.0")

        // Add content URL for better targeting (if available)
        try {
            builder.setContentUrl("https://vibetube.app")
        } catch (e: Exception) {
            Log.w(TAG, "Could not set content URL: ${e.message}")
        }

        return builder.build()
    }


    fun handleConsent(activity: Activity) {
        val params = ConsentRequestParameters.Builder().build()

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        consentInformation.requestConsentInfoUpdate(
            activity, // ✅ must be Activity, not Context
            params,
            {
                if (consentInformation.isConsentFormAvailable) {
                    loadConsentForm(activity)
                }
            },
            { formError ->
                Log.e(TAG, "Consent error: ${formError.message}")
            }
        )
    }

    private fun loadConsentForm(activity: Activity) {
        UserMessagingPlatform.loadConsentForm(
            activity,
            { consentForm ->
                val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
                if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    consentForm.show(activity) { formError ->
                        formError?.let {
                            Log.e(TAG, "Consent form error: ${it.message}")
                        }
                        loadConsentForm(activity) // reload if needed
                    }
                }
            },
            { formError ->
                Log.e(TAG, "Consent form error: ${formError.message}")
            }
        )
    }

    fun resumeBannerAd(adView: AdView?) = adView?.resume()
    fun pauseBannerAd(adView: AdView?) = adView?.pause()
    fun destroyBannerAd(adView: AdView?) = adView?.destroy()

    // Enhanced helper methods for YouTube Policy compliance and user experience

    /**
     * Build ad request with YouTube Policy compliance
     */
    private fun buildAdRequest(contentContext: String): AdRequest {
        val builder = AdRequest.Builder()

        // Add content context for YouTube Policy compliance
        if (contentContext == YOUTUBE_CONTENT_CONTEXT) {
            // Special handling for YouTube content context
            builder.addKeyword("video_content")
            builder.addKeyword("entertainment")
        } else {
            // General content keywords to improve fill rate
            builder.addKeyword("mobile_app")
            builder.addKeyword("android")
            builder.addKeyword("entertainment")
        }

        // Respect user's personalized ads preference
        if (!isPersonalizedAdsEnabled()) {
            // Request non-personalized ads
            val extras = android.os.Bundle()
            extras.putString("npa", "1")
            builder.addNetworkExtrasBundle(com.google.ads.mediation.admob.AdMobAdapter::class.java, extras)
        }

        // Add request agent for better targeting
        builder.setRequestAgent("VibeTube_Android_1.0")

        return builder.build()
    }

    /**
     * Check if banner ads are enabled by user preference
     */
    private fun isBannerAdsEnabled(): Boolean {
        return prefs.getBoolean(PREF_BANNER_ADS_ENABLED, true) && !isSessionAdLimitReached()
    }

    /**
     * Check if native ads are enabled by user preference
     */
    private fun isNativeAdsEnabled(): Boolean {
        return prefs.getBoolean(PREF_NATIVE_ADS_ENABLED, true) && !isSessionAdLimitReached()
    }

    /**
     * Check if rewarded ads are enabled by user preference
     */
    private fun isRewardedAdsEnabled(): Boolean {
        return prefs.getBoolean(PREF_REWARDED_ADS_ENABLED, true) && !isSessionAdLimitReached()
    }

    /**
     * Check if interstitial ads are enabled by user preference
     */
    private fun isInterstitialAdsEnabled(): Boolean {
        val enabled = prefs.getBoolean(PREF_INTERSTITIAL_ADS_ENABLED, true)
        val lastShownTime = prefs.getLong(PREF_LAST_INTERSTITIAL_TIME, 0)
        val timeSinceLastShown = System.currentTimeMillis() - lastShownTime

        return enabled &&
               !isSessionAdLimitReached() &&
               timeSinceLastShown >= MIN_TIME_BETWEEN_INTERSTITIALS
    }

    /**
     * Check if personalized ads are enabled
     */
    private fun isPersonalizedAdsEnabled(): Boolean {
        return prefs.getBoolean(PREF_PERSONALIZED_ADS, true)
    }

    /**
     * Check if ad loading is in cooldown due to failures
     */
    private fun isAdLoadingCooldown(adType: String): Boolean {
        val failureCount = adFailureCount[adType] ?: 0
        if (failureCount < MAX_AD_FAILURES_BEFORE_COOLDOWN) return false

        val lastFailureTime = prefs.getLong("${adType}_last_failure_time", 0)
        val timeSinceFailure = System.currentTimeMillis() - lastFailureTime

        return timeSinceFailure < AD_FAILURE_COOLDOWN_TIME
    }

    /**
     * Check if session ad limit is reached
     */
    private fun isSessionAdLimitReached(): Boolean {
        val sessionAdCount = prefs.getInt(PREF_SESSION_AD_COUNT, 0)
        return sessionAdCount >= MAX_ADS_PER_SESSION
    }

    /**
     * Handle ad loading failures with cooldown logic
     */
    private fun handleAdFailure(adType: String, error: Any) {
        val currentCount = adFailureCount[adType] ?: 0
        adFailureCount[adType] = currentCount + 1

        prefs.edit()
            .putLong("${adType}_last_failure_time", System.currentTimeMillis())
            .apply()

        Log.w(TAG, "Ad failure for $adType (count: ${currentCount + 1}): $error")
    }

    /**
     * Reset ad failure count on successful load
     */
    private fun resetAdFailureCount(adType: String) {
        adFailureCount.remove(adType)
        prefs.edit().remove("${adType}_last_failure_time").apply()
    }

    /**
     * Increment session ad count
     */
    private fun incrementSessionAdCount() {
        val currentCount = prefs.getInt(PREF_SESSION_AD_COUNT, 0)
        prefs.edit().putInt(PREF_SESSION_AD_COUNT, currentCount + 1).apply()
    }

    /**
     * Track ad interactions for analytics
     */
    private fun trackAdInteraction(adType: String, action: String) {
        Log.d(TAG, "Ad interaction: $adType - $action")
        // Add analytics tracking here if needed
    }

    /**
     * Reset session ad count (call on app start)
     */
    fun resetSessionAdCount() {
        prefs.edit().putInt(PREF_SESSION_AD_COUNT, 0).apply()
    }

    /**
     * Get user ad preferences for settings UI
     */
    fun getAdPreferences(): Map<String, Boolean> {
        return mapOf(
            PREF_BANNER_ADS_ENABLED to prefs.getBoolean(PREF_BANNER_ADS_ENABLED, true),
            PREF_NATIVE_ADS_ENABLED to prefs.getBoolean(PREF_NATIVE_ADS_ENABLED, true),
            PREF_INTERSTITIAL_ADS_ENABLED to prefs.getBoolean(PREF_INTERSTITIAL_ADS_ENABLED, true),
            PREF_REWARDED_ADS_ENABLED to prefs.getBoolean(PREF_REWARDED_ADS_ENABLED, true),
            PREF_PERSONALIZED_ADS to prefs.getBoolean(PREF_PERSONALIZED_ADS, true)
        )
    }

    /**
     * Update user ad preferences
     */
    fun updateAdPreferences(preferences: Map<String, Boolean>) {
        val editor = prefs.edit()
        preferences.forEach { (key, value) ->
            editor.putBoolean(key, value)
        }
        editor.apply()
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        scope.cancel()
        adLoadingCache.clear()
        adFailureCount.clear()
        interstitialAd = null
        rewardedAd = null
    }
}
