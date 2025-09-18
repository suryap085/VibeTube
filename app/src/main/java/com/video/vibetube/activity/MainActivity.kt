package com.video.vibetube.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.video.vibetube.R
import com.video.vibetube.adapters.MainPagerAdapter
import com.video.vibetube.utils.AdManager
import androidx.core.view.get

class MainActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var searchFab: FloatingActionButton
    private lateinit var adManager: AdManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Google Ads SDK
        MobileAds.initialize(this) {}
        adManager = AdManager(this)

        initViews()
        setupBottomNavigation()
        setupSearchFab()
        setupAds()
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        searchFab = findViewById(R.id.searchFab)
    }

    private fun setupBottomNavigation() {
        val adapter = MainPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = false

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    viewPager.currentItem = 0
                    adManager.showInterstitialAd()
                }
                R.id.nav_shorts -> viewPager.currentItem = 1
                R.id.nav_comedy -> viewPager.currentItem = 2
                R.id.nav_movies -> viewPager.currentItem = 3
                R.id.nav_diy -> viewPager.currentItem = 4
            }
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNavigation.menu[position].isChecked = true
            }
        })
    }

    private fun setupSearchFab() {
        searchFab.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupAds() {
        val bannerAdView = findViewById<com.google.android.gms.ads.AdView>(R.id.adView)
        adManager.loadBannerAd(bannerAdView)
        adManager.loadInterstitialAd()
        adManager.handleConsent(this)
    }

    override fun onResume() {
        super.onResume()
        val bannerAdView = findViewById<com.google.android.gms.ads.AdView>(R.id.adView)
        adManager.resumeBannerAd(bannerAdView)
    }

    override fun onPause() {
        super.onPause()
        val bannerAdView = findViewById<com.google.android.gms.ads.AdView>(R.id.adView)
        adManager.pauseBannerAd(bannerAdView)
    }

    override fun onDestroy() {
        super.onDestroy()
        val bannerAdView = findViewById<com.google.android.gms.ads.AdView>(R.id.adView)
        adManager.destroyBannerAd(bannerAdView)
    }
}
