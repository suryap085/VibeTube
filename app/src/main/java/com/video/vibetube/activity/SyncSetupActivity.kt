package com.video.vibetube.activity

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.video.vibetube.R
import com.video.vibetube.models.UserPlaylist
import com.video.vibetube.models.Video
import com.video.vibetube.sync.CrossDeviceSyncManager
import com.video.vibetube.sync.UserProfileManager
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.launch

/**
 * Activity for setting up cross-device sync with Google Sign-In
 * YouTube Policy Compliant - only syncs user metadata, not YouTube content
 */
class SyncSetupActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var syncManager: CrossDeviceSyncManager
    private lateinit var profileManager: UserProfileManager
    private lateinit var userDataManager: UserDataManager

    // UI Components
    private lateinit var signInButton: Button
    private lateinit var toolbar: MaterialToolbar
    private lateinit var syncButton: Button
    private lateinit var signOutButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var userInfoLayout: View
    private lateinit var userNameText: TextView
    private lateinit var userEmailText: TextView
    private lateinit var userImageView: ImageView

    companion object {
        private const val TAG = "SyncSetupActivity"
        private const val RC_SIGN_IN = 9001
    }

    fun CrossDeviceSyncManager.SyncableVideo.toVideo(): Video {
        return Video(
            videoId = this.videoId,
            title = this.title,
            description = "",
            thumbnail = this.thumbnailUrl,
            channelTitle = this.channelTitle,
            publishedAt = "",
            duration = this.duration,
            categoryId = "",
            channelId = "",
        )
    }


    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                Log.d(TAG, "firebaseAuthWithGoogle:" + account.id)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Log.w(TAG, "Google sign in failed", e)
                showError("Google sign in failed: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_setup)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize managers
        syncManager = CrossDeviceSyncManager.getInstance(this)
        profileManager = UserProfileManager.getInstance(this)
        userDataManager = UserDataManager.getInstance(this)

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        initViews()
        setUpToolBar()
        setupClickListeners()
        updateUI()
    }

    private fun setUpToolBar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }


private fun initViews() {
    signInButton = findViewById(R.id.signInButton)
    syncButton = findViewById(R.id.syncButton)
    signOutButton = findViewById(R.id.signOutButton)
    progressBar = findViewById(R.id.progressBar)
    statusText = findViewById(R.id.statusText)
    userInfoLayout = findViewById(R.id.userInfoLayout)
    userNameText = findViewById(R.id.userNameText)
    userEmailText = findViewById(R.id.userEmailText)
    userImageView = findViewById(R.id.userImageView)
    toolbar = findViewById(R.id.toolbar)
}

private fun setupClickListeners() {
    signInButton.setOnClickListener {
        signIn()
    }

    syncButton.setOnClickListener {
        performSync()
    }

    signOutButton.setOnClickListener {
        signOut()
    }

    findViewById<Button>(R.id.backButton).setOnClickListener {
        finish()
    }
}

private fun signIn() {
    if (!userDataManager.hasUserConsent()) {
        showConsentDialog()
        return
    }

    val signInIntent = googleSignInClient.signInIntent
    signInLauncher.launch(signInIntent)
}

private fun showConsentDialog() {
    MaterialAlertDialogBuilder(this)
        .setTitle("Cross-Device Sync")
        .setMessage(
            "To enable cross-device sync, we need your consent to:\n\n" +
                    "â€¢ Store your preferences and settings in the cloud\n" +
                    "â€¢ Sync your playlists and favorites (video IDs only)\n" +
                    "â€¢ Sync your watch history metadata\n\n" +
                    "We will NOT store any YouTube content or violate YouTube's policies. " +
                    "Only your personal data and preferences will be synced."
        )
        .setPositiveButton("I Agree") { _, _ ->
            userDataManager.setUserConsent(true)
            signIn()
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun firebaseAuthWithGoogle(idToken: String) {
    showProgress("Signing in...")

    val credential = GoogleAuthProvider.getCredential(idToken, null)
    auth.signInWithCredential(credential)
        .addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                Log.d(TAG, "signInWithCredential:success")
                val user = auth.currentUser
                //updateUI()
                showSuccess("Signed in as ${user?.displayName}")
                lifecycleScope.launch {
                    showProgress("Fetching your data...")
                    val result = syncManager.fetchAndDisplayAllUserData(this@SyncSetupActivity)
                    hideProgress()
                    if (result.isSuccess) {
                        val userData = result.getOrNull()
                        Log.d(
                            TAG,
                            "Successfully fetched user data: ${userData?.watchHistory?.size} history, ${userData?.favorites?.size} favorites, ${userData?.playlists?.size} playlists"
                        )
                        for (watchHistory in userData?.watchHistory!!) {
                            userDataManager.addToWatchHistory(
                                watchHistory.toVideo(),
                                watchHistory.watchPosition, watchHistory.watchDuration
                            )
                        }
                        for (favorites in userData.favorites) {
                            userDataManager.addToFavorites(
                                favorites.toVideo(),
                                sourceContext = "channel"
                            )
                        }

                        // Ensure playlists exist locally before adding videos
                        val currentPlaylists =
                            userDataManager.getPlaylistsInternal().toMutableList()
                        val currentPlaylistIds = currentPlaylists.map { it.id }.toSet()

                        for (syncedPlaylist in userData.playlists) {
                            if (!currentPlaylistIds.contains(syncedPlaylist.id)) {
                                val userPlaylist = UserPlaylist(
                                    id = syncedPlaylist.id,
                                    name = syncedPlaylist.name,
                                    description = syncedPlaylist.description,
                                    createdAt = syncedPlaylist.createdAt,
                                    updatedAt = syncedPlaylist.updatedAt,
                                    videoIds = mutableListOf(),
                                    videos = mutableListOf()
                                )
                                currentPlaylists.add(userPlaylist)
                            }
                        }
                        userDataManager.savePlaylists(currentPlaylists)

                        for (playlist in userData.playlists) {
                            // Since playlists now contain full video metadata, add all videos directly
                            for (video in playlist.videos) {
                                userDataManager.addVideoToPlaylist(playlist.id, video.toVideo())
                            }
                        }
                        showSuccess("Data loaded successfully!")
                        updateUI()
                    } else {
                        val error = result.exceptionOrNull()
                        showError("Failed to fetch data: ${error?.message}")
                    }
                }

            } else {
                Log.w(TAG, "signInWithCredential:failure", task.exception)
                showError("Authentication failed: ${task.exception?.message}")
            }
            hideProgress()
        }
}

private fun performSync() {
    showProgress("Syncing your data...")

    lifecycleScope.launch {
        try {
            val result = syncManager.syncUserData()
            if (result.isSuccess) {
                showSuccess("Sync completed successfully!")
            } else {
                showError("Sync failed: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            showError("Sync failed: ${e.message}")
        } finally {
            hideProgress()
        }
    }
}

private fun signOut() {
    MaterialAlertDialogBuilder(this)
        .setTitle("Sign Out")
        .setMessage("Are you sure you want to sign out? This will disable cross-device sync.")
        .setPositiveButton("Sign Out") { _, _ ->
            lifecycleScope.launch {
                syncManager.signOut()
                googleSignInClient.signOut()
                updateUI()
                showSuccess("Signed out successfully")
            }
        }
        .setNegativeButton("Cancel", null)
        .show()
}

private fun updateUI() {
    val user = auth.currentUser

    if (user != null) {
        // User is signed in
        signInButton.visibility = View.GONE
        userInfoLayout.visibility = View.VISIBLE
        syncButton.visibility = View.VISIBLE
        signOutButton.visibility = View.VISIBLE

        userNameText.text = user.displayName ?: "Unknown User"
        userEmailText.text = user.email ?: ""

        // Load profile image
        lifecycleScope.launch {
            val photoUrl = profileManager.getProfilePhotoUrl()
            if (photoUrl != null) {
                Glide.with(this@SyncSetupActivity)
                    .load(photoUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_account_circle)
                    .into(userImageView)
            } else {
                userImageView.setImageResource(R.drawable.ic_account_circle)
            }
        }

        statusText.text = "Cross-device sync is enabled"
    } else {
        // User is not signed in
        signInButton.visibility = View.VISIBLE
        userInfoLayout.visibility = View.GONE
        syncButton.visibility = View.GONE
        signOutButton.visibility = View.GONE
        statusText.text = "Sign in to enable cross-device sync"
    }
}

private fun showProgress(message: String) {
    progressBar.visibility = View.VISIBLE
    statusText.text = message
    signInButton.isEnabled = false
    syncButton.isEnabled = false
    signOutButton.isEnabled = false
}

private fun hideProgress() {
    progressBar.visibility = View.GONE
    signInButton.isEnabled = true
    syncButton.isEnabled = true
    signOutButton.isEnabled = true
}

private fun showSuccess(message: String) {
    statusText.text = message
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

private fun showError(message: String) {
    statusText.text = message
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
}