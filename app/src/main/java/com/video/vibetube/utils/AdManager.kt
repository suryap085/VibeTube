package com.video.vibetube.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class AdManager(private val context: Context) {
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var interstitialAdCounter = 0
    private val INTERSTITIAL_AD_FREQUENCY = 3

    companion object {
        const val TAG = "AdManager"
        // Test Ad Unit IDs - Replace with real ones for production
        //const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        const val BANNER_AD_UNIT_ID = "ca-app-pub-8519418185028322/1079794018"
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        //const val NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
        const val NATIVE_AD_UNIT_ID = "ca-app-pub-8519418185028322/2640287692"
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }

    fun loadBannerAd(adView: AdView) {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        adView.adListener = object : AdListener() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e(TAG, "Banner ad failed: ${adError.message}")
            }

            override fun onAdLoaded() {
                Log.d(TAG, "Banner ad loaded successfully")
            }
        }
    }

    fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    interstitialAd = null
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    setupInterstitialCallbacks()
                }
            }
        )
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

    fun loadNativeAd(onSuccess: (NativeAd) -> Unit, onFailure: (LoadAdError) -> Unit) {
        val adLoader = AdLoader.Builder(context, NATIVE_AD_UNIT_ID)
            .forNativeAd { ad: NativeAd ->
                onSuccess(ad)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    onFailure(adError)
                }
            })
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
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
}
