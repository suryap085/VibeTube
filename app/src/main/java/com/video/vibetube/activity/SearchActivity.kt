package com.video.vibetube.activity

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.video.vibetube.R
import com.video.vibetube.adapters.SearchPagerAdapter
import com.video.vibetube.utils.AdManager
import com.google.android.gms.ads.AdView
import com.google.android.material.appbar.MaterialToolbar

class SearchActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var searchToolbar: MaterialToolbar

    // Enhanced Ad Management for Search
    private lateinit var adManager: AdManager
    private var bannerAdView: AdView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        // Initialize Enhanced Ad Management
        adManager = AdManager(this)
        adManager.handleConsent(this)

        initViews()
        setupToolbar()
        setupAds()
        setupViewPager()
    }

    private fun setupToolbar() {
        setSupportActionBar(searchToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        searchToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.searchTabLayout)
        viewPager = findViewById(R.id.searchViewPager)
        searchToolbar = findViewById(R.id.searchToolbar)
    }

    private fun setupViewPager() {
        val adapter = SearchPagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "All"
                1 -> "Videos"
                2 -> "Channels"
                3 -> "Playlists"
                else -> ""
            }
        }.attach()
    }

    /**
     * Setup ads for Search Activity with YouTube Policy compliance
     */
    private fun setupAds() {
        // Find banner ad container in layout (if exists)
        bannerAdView = findViewById<AdView?>(R.id.searchBannerAd)
        bannerAdView?.let { adView ->
            // Load banner ad with search content context
            adManager.loadBannerAd(adView, AdManager.NON_YOUTUBE_CONTEXT)
        }

        // Load interstitial ad for strategic placement between search sessions
        adManager.loadInterstitialAd(AdManager.NON_YOUTUBE_CONTEXT)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        bannerAdView?.let { adManager.resumeBannerAd(it) }
    }

    override fun onPause() {
        bannerAdView?.let { adManager.pauseBannerAd(it) }
        super.onPause()
    }

    override fun onDestroy() {
        bannerAdView?.let { adManager.destroyBannerAd(it) }
        super.onDestroy()
    }
}
