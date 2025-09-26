package com.video.vibetube.activity

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.video.vibetube.R
import com.video.vibetube.sync.CrossDeviceSyncManager
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Engaging Splash Screen with automatic sync and smooth animations
 * YouTube Policy Compliant - handles user data sync securely
 */
class SplashScreenActivity : AppCompatActivity() {

    private lateinit var logoImageView: ImageView
    private lateinit var appNameTextView: TextView
    private lateinit var taglineTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var syncStatusTextView: TextView

    private lateinit var syncManager: CrossDeviceSyncManager
    private lateinit var userDataManager: UserDataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set fullscreen for immersive experience
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        setContentView(R.layout.activity_splash_screen)

        // Initialize components
        syncManager = CrossDeviceSyncManager.getInstance(this)
        userDataManager = UserDataManager.getInstance(this)

        initViews()
        startAnimations()
        performAutoSync()
    }

    private fun initViews() {
        logoImageView = findViewById(R.id.logoImageView)
        appNameTextView = findViewById(R.id.appNameTextView)
        taglineTextView = findViewById(R.id.taglineTextView)
        progressBar = findViewById(R.id.progressBar)
        syncStatusTextView = findViewById(R.id.syncStatusTextView)

        // Load app logo with Glide for better performance
        Glide.with(this)
            .load(R.mipmap.ic_launcher_round)
            .circleCrop()
            .into(logoImageView)
    }

    private fun startAnimations() {
        // Create engaging entrance animations
        val logoAnim = ObjectAnimator.ofFloat(logoImageView, "alpha", 0f, 1f).apply {
            duration = 800
        }

        val logoScaleX = ObjectAnimator.ofFloat(logoImageView, "scaleX", 0.5f, 1f).apply {
            duration = 1000
        }

        val logoScaleY = ObjectAnimator.ofFloat(logoImageView, "scaleY", 0.5f, 1f).apply {
            duration = 1000
        }

        val appNameAnim = ObjectAnimator.ofFloat(appNameTextView, "alpha", 0f, 1f).apply {
            duration = 600
            startDelay = 400
        }

        val appNameSlide = ObjectAnimator.ofFloat(appNameTextView, "translationY", 50f, 0f).apply {
            duration = 800
            startDelay = 400
        }

        val taglineAnim = ObjectAnimator.ofFloat(taglineTextView, "alpha", 0f, 1f).apply {
            duration = 600
            startDelay = 800
        }

        val taglineSlide = ObjectAnimator.ofFloat(taglineTextView, "translationY", 30f, 0f).apply {
            duration = 600
            startDelay = 800
        }

        // Combine animations
        AnimatorSet().apply {
            playTogether(
                logoAnim, logoScaleX, logoScaleY,
                appNameAnim, appNameSlide,
                taglineAnim, taglineSlide
            )
            start()
        }
    }

    private fun performAutoSync() {
        lifecycleScope.launch {
            try {
                // Show initial loading state
                syncStatusTextView.text = getString(R.string.initializing_vibetube)
                progressBar.visibility = View.VISIBLE

                // Small delay for smooth animation
                delay(1500)

                // Check if user has sync enabled and perform automatic sync
                if (syncManager.isSyncEnabled() && userDataManager.hasUserConsent()) {
                    syncStatusTextView.text = getString(R.string.syncing_data)

                    // Perform automatic sync with timeout
                    val syncResult = try {
                        syncManager.syncUserData()
                    } catch (_: Exception) {
                        // If sync fails, continue to login activity
                        syncStatusTextView.text = getString(R.string.loading_app)
                        delay(1000)
                        proceedToLoginActivity()
                        return@launch
                    }

                    if (syncResult.isSuccess) {
                        syncStatusTextView.text = getString(R.string.sync_complete)
                    } else {
                        syncStatusTextView.text = getString(R.string.loading_app)
                    }
                } else {
                    syncStatusTextView.text = getString(R.string.loading_app)
                }

                // Brief pause before transitioning
                delay(1000)

            } catch (_: Exception) {
                // If anything fails, just proceed to login activity
                syncStatusTextView.text = getString(R.string.loading_app)
                delay(500)
            }

            proceedToLoginActivity()
        }
    }

    private fun proceedToLoginActivity() {
        // Create smooth exit animation
        val exitAnim = ObjectAnimator.ofFloat(logoImageView, "alpha", 1f, 0f).apply {
            duration = 300
        }

        val exitAnim2 = ObjectAnimator.ofFloat(appNameTextView, "alpha", 1f, 0f).apply {
            duration = 300
        }

        val exitAnim3 = ObjectAnimator.ofFloat(taglineTextView, "alpha", 1f, 0f).apply {
            duration = 300
        }

        AnimatorSet().apply {
            playTogether(exitAnim, exitAnim2, exitAnim3)
            start()
        }

        // Start login activity after animation
        android.os.Handler(mainLooper).postDelayed({
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            // Add smooth transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 300)
    }
}