package com.video.vibetube.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.video.vibetube.R
import com.video.vibetube.models.UserPlaylist
import com.video.vibetube.models.Video
import com.video.vibetube.sync.CrossDeviceSyncManager
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Login Activity for Google Authentication
 * YouTube Policy Compliant - handles user authentication securely
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var syncManager: CrossDeviceSyncManager
    private lateinit var userDataManager: UserDataManager

    // UI Components
    private lateinit var googleSignInButton: MaterialButton
    private lateinit var skipLoginButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var welcomeText: TextView

    companion object {
        private const val TAG = "LoginActivity"
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
                showError(getString(R.string.google_sign_in_failed, e.message ?: "Unknown error"))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize managers
        syncManager = CrossDeviceSyncManager.getInstance(this)
        userDataManager = UserDataManager.getInstance(this)

        initViews()
        setupClickListeners()
        checkExistingLogin()
    }

    private fun initViews() {
        googleSignInButton = findViewById(R.id.googleSignInButton)
        skipLoginButton = findViewById(R.id.skipLoginButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        welcomeText = findViewById(R.id.welcomeText)
    }

    private fun setupClickListeners() {
        googleSignInButton.setOnClickListener {
            signIn()
        }

        skipLoginButton.setOnClickListener {
            proceedToMainActivity()
        }
    }

    private fun checkExistingLogin() {
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.isAnonymous) {
            // User is already logged in, proceed directly to main activity
            statusText.text = getString(R.string.welcome_back, currentUser.displayName ?: "User")
            progressBar.visibility = View.VISIBLE
            googleSignInButton.visibility = View.GONE
            skipLoginButton.visibility = View.GONE

            lifecycleScope.launch {
                delay(1000) // Brief welcome message
                proceedToMainActivity()
            }
        } else {
            // User needs to log in
            welcomeText.text = getString(R.string.welcome_to_vibetube)
            statusText.text = getString(R.string.sign_in_to_sync)
        }
    }

    private fun signIn() {
        val flag=userDataManager.hasUserConsent()
        if (!userDataManager.hasUserConsent()) {
            showConsentDialog()
            return
        }

        proceedWithSignIn()
    }

    private fun proceedWithSignIn() {
        showProgress(getString(R.string.signing_in))

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun showConsentDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.consent_required))
            .setMessage(getString(R.string.consent_message))
            .setPositiveButton(getString(R.string.i_agree)) { _, _ ->
                userDataManager.setUserConsent(true)
                proceedWithSignIn()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    showSuccess(getString(R.string.signed_in_as, user?.displayName ?: "User"))

                    // Start automatic sync after successful login
                    performAutoSync()
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    showError(getString(R.string.authentication_failed, task.exception?.message ?: "Unknown error"))
                    hideProgress()
                }
            }
    }

    private fun performAutoSync() {
        showProgress(getString(R.string.syncing_your_data))

        lifecycleScope.launch {
            try {
                val result = syncManager.fetchAndDisplayAllUserData(this@LoginActivity)
                if (result.isSuccess) {
                    val userData = result.getOrNull()
                    Log.d(
                        TAG,
                        "Successfully fetched user data: ${userData?.watchHistory?.size} history, ${userData?.favorites?.size} favorites, ${userData?.playlists?.size} playlists"
                    )
                    for (watchHistory in userData?.watchHistory!!) {
                        userDataManager.addToWatchHistory(watchHistory.toVideo(),
                            watchHistory.watchPosition, watchHistory.watchDuration)
                    }
                    for (favorites in userData.favorites) {
                        userDataManager.addToFavorites(favorites.toVideo(), sourceContext = "channel")
                    }

                    // Ensure playlists exist locally before adding videos
                    val currentPlaylists = userDataManager.getPlaylistsInternal().toMutableList()
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
                    showSuccess(getString(R.string.sync_complete_success))
                    delay(1000) // Brief success message
                    proceedToMainActivity()
                } else {
                    showError(getString(R.string.sync_failed_continue))
                    delay(2000) // Show error briefly then proceed
                    proceedToMainActivity()
                }
            } catch (_: Exception) {
                showError(getString(R.string.sync_failed_continue))
                delay(2000)
                proceedToMainActivity()
            }
        }
    }

    private fun proceedToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun showProgress(message: String) {
        progressBar.visibility = View.VISIBLE
        statusText.text = message
        googleSignInButton.isEnabled = false
        skipLoginButton.isEnabled = false
    }

    private fun hideProgress() {
        progressBar.visibility = View.GONE
        googleSignInButton.isEnabled = true
        skipLoginButton.isEnabled = true
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