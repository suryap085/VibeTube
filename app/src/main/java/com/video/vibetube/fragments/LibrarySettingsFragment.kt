package com.video.vibetube.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.video.vibetube.R
import com.video.vibetube.utils.UserDataManager
import com.video.vibetube.utils.AchievementManager
import com.video.vibetube.utils.EngagementAnalytics
import com.video.vibetube.utils.SocialManager
import kotlinx.coroutines.launch

class LibrarySettingsFragment : Fragment() {

    private lateinit var userDataManager: UserDataManager
    private lateinit var achievementManager: AchievementManager
    private lateinit var engagementAnalytics: EngagementAnalytics
    private lateinit var socialManager: SocialManager
    
    // Data Management Views
    private lateinit var watchHistoryCountText: TextView
    private lateinit var favoritesCountText: TextView
    private lateinit var playlistsCountText: TextView
    private lateinit var exportDataButton: MaterialButton
    private lateinit var clearHistoryButton: MaterialButton
    private lateinit var deleteAllDataButton: MaterialButton
    
    // Privacy Settings Views
    private lateinit var dataCollectionSwitch: MaterialSwitch
    private lateinit var analyticsSwitch: MaterialSwitch
    private lateinit var gamificationSwitch: MaterialSwitch
    
    // Notification Settings Views
    private lateinit var achievementNotificationsSwitch: MaterialSwitch
    private lateinit var weeklySummarySwitch: MaterialSwitch
    
    // About Section Views
    private lateinit var privacyPolicyButton: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_library_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeManagers()
        initializeViews(view)
        setupClickListeners()
        loadSettings()
        updateDataStats()
    }

    private fun initializeManagers() {
        userDataManager = UserDataManager.getInstance(requireContext())
        achievementManager = AchievementManager.getInstance(requireContext())
        engagementAnalytics = EngagementAnalytics.getInstance(requireContext())
        socialManager = SocialManager.getInstance(requireContext())
    }

    private fun initializeViews(view: View) {
        // Data Management
        watchHistoryCountText = view.findViewById(R.id.watchHistoryCountText)
        favoritesCountText = view.findViewById(R.id.favoritesCountText)
        playlistsCountText = view.findViewById(R.id.playlistsCountText)
        exportDataButton = view.findViewById(R.id.exportDataButton)
        clearHistoryButton = view.findViewById(R.id.clearHistoryButton)
        deleteAllDataButton = view.findViewById(R.id.deleteAllDataButton)
        
        // Privacy Settings
        dataCollectionSwitch = view.findViewById(R.id.dataCollectionSwitch)
        analyticsSwitch = view.findViewById(R.id.analyticsSwitch)
        gamificationSwitch = view.findViewById(R.id.gamificationSwitch)
        
        // Notification Settings
        achievementNotificationsSwitch = view.findViewById(R.id.achievementNotificationsSwitch)
        weeklySummarySwitch = view.findViewById(R.id.weeklySummarySwitch)
        
        // About Section
        privacyPolicyButton = view.findViewById(R.id.privacyPolicyButton)
    }

    private fun setupClickListeners() {
        // Data Management Actions
        exportDataButton.setOnClickListener {
            exportUserData()
        }

        clearHistoryButton.setOnClickListener {
            showClearHistoryConfirmation()
        }

        deleteAllDataButton.setOnClickListener {
            showDeleteAllDataConfirmation()
        }

        // Privacy Settings
        if (::dataCollectionSwitch.isInitialized) {
            dataCollectionSwitch.setOnCheckedChangeListener { _, isChecked ->
                updateDataCollectionSetting(isChecked)
            }
        }

        if (::analyticsSwitch.isInitialized) {
            analyticsSwitch.setOnCheckedChangeListener { _, isChecked ->
                updateAnalyticsSetting(isChecked)
            }
        }

        if (::gamificationSwitch.isInitialized) {
            gamificationSwitch.setOnCheckedChangeListener { _, isChecked ->
                updateGamificationSetting(isChecked)
            }
        }

        // Notification Settings
        if (::achievementNotificationsSwitch.isInitialized) {
            achievementNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
                updateAchievementNotificationsSetting(isChecked)
            }
        }

        if (::weeklySummarySwitch.isInitialized) {
            weeklySummarySwitch.setOnCheckedChangeListener { _, isChecked ->
                updateWeeklySummarySetting(isChecked)
            }
        }

        // About Section
        privacyPolicyButton.setOnClickListener {
            openPrivacyPolicy()
        }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            try {
                // Ensure switches are properly initialized before setting values
                if (::dataCollectionSwitch.isInitialized) {
                    dataCollectionSwitch.isChecked = userDataManager.isDataCollectionEnabled()
                }
                if (::analyticsSwitch.isInitialized) {
                    analyticsSwitch.isChecked = engagementAnalytics.isAnalyticsEnabled()
                }
                if (::gamificationSwitch.isInitialized) {
                    gamificationSwitch.isChecked = achievementManager.isGamificationEnabled()
                }
                if (::achievementNotificationsSwitch.isInitialized) {
                    achievementNotificationsSwitch.isChecked = achievementManager.areNotificationsEnabled()
                }
                if (::weeklySummarySwitch.isInitialized) {
                    weeklySummarySwitch.isChecked = userDataManager.isWeeklySummaryEnabled()
                }
            } catch (e: Exception) {
                showError("Failed to load settings")
            }
        }
    }

    private fun updateDataStats() {
        lifecycleScope.launch {
            try {
                val watchHistoryCount = userDataManager.getWatchHistory().size
                val favoritesCount = userDataManager.getFavorites().size
                val playlistsCount = userDataManager.getPlaylists().size

                watchHistoryCountText.text = watchHistoryCount.toString()
                favoritesCountText.text = favoritesCount.toString()
                playlistsCountText.text = playlistsCount.toString()
            } catch (e: Exception) {
                // Handle error silently for stats
            }
        }
    }

    private fun exportUserData() {
        lifecycleScope.launch {
            try {
                val exportedData = userDataManager.exportUserData()
                if (exportedData.isNotEmpty()) {
                    // Create intent to share the exported data
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, exportedData)
                        putExtra(Intent.EXTRA_SUBJECT, "VibeTube User Data Export")
                    }
                    startActivity(Intent.createChooser(shareIntent, "Export User Data"))

                    engagementAnalytics.trackFeatureUsage("user_data_exported")
                } else {
                    showError("No data to export")
                }
            } catch (e: Exception) {
                showError("Failed to export data")
            }
        }
    }

    private fun showClearHistoryConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Watch History")
            .setMessage("Are you sure you want to clear all watch history? This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                clearWatchHistory()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearWatchHistory() {
        lifecycleScope.launch {
            try {
                userDataManager.clearWatchHistory()
                updateDataStats()
                engagementAnalytics.trackFeatureUsage("watch_history_cleared_from_settings")
            } catch (e: Exception) {
                showError("Failed to clear watch history")
            }
        }
    }

    private fun showDeleteAllDataConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete All Data")
            .setMessage("Are you sure you want to delete ALL your data including watch history, favorites, playlists, and achievements? This action cannot be undone.")
            .setPositiveButton("Delete All") { _, _ ->
                showFinalDeleteConfirmation()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFinalDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Final Confirmation")
            .setMessage("This will permanently delete all your VibeTube data. Are you absolutely sure?")
            .setPositiveButton("Yes, Delete Everything") { _, _ ->
                deleteAllUserData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAllUserData() {
        lifecycleScope.launch {
            try {
                userDataManager.clearAllData()
                achievementManager.resetAllProgress()
                engagementAnalytics.setAnalyticsEnabled(false)
                engagementAnalytics.setAnalyticsEnabled(true)
                updateDataStats()
                engagementAnalytics.trackFeatureUsage("all_user_data_deleted")

                // Navigate back to main activity
                requireActivity().finish()
            } catch (e: Exception) {
                showError("Failed to delete all data")
            }
        }
    }

    private fun updateDataCollectionSetting(enabled: Boolean) {
        lifecycleScope.launch {
            try {
                userDataManager.setDataCollectionEnabled(enabled)
                engagementAnalytics.trackFeatureUsage("data_collection_toggled")
                
                if (!enabled) {
                    // If data collection is disabled, also disable analytics and gamification
                    analyticsSwitch.isChecked = false
                    gamificationSwitch.isChecked = false
                    updateAnalyticsSetting(false)
                    updateGamificationSetting(false)
                }
                

            } catch (e: Exception) {
                showError("Failed to update data collection setting")
                dataCollectionSwitch.isChecked = !enabled // Revert on error
            }
        }
    }

    private fun updateAnalyticsSetting(enabled: Boolean) {
        lifecycleScope.launch {
            try {
                engagementAnalytics.setAnalyticsEnabled(enabled)
                engagementAnalytics.trackFeatureUsage("analytics_toggled")
                

            } catch (e: Exception) {
                showError("Failed to update analytics setting")
                analyticsSwitch.isChecked = !enabled // Revert on error
            }
        }
    }

    private fun updateGamificationSetting(enabled: Boolean) {
        lifecycleScope.launch {
            try {
                achievementManager.setGamificationEnabled(enabled)
                engagementAnalytics.trackFeatureUsage("gamification_toggled_from_settings")
                
                if (!enabled) {
                    // Also disable achievement notifications
                    achievementNotificationsSwitch.isChecked = false
                    updateAchievementNotificationsSetting(false)
                }
                

            } catch (e: Exception) {
                showError("Failed to update gamification setting")
                gamificationSwitch.isChecked = !enabled // Revert on error
            }
        }
    }

    private fun updateAchievementNotificationsSetting(enabled: Boolean) {
        lifecycleScope.launch {
            try {
                achievementManager.setNotificationsEnabled(enabled)
                engagementAnalytics.trackFeatureUsage("achievement_notifications_toggled_from_settings")
                

            } catch (e: Exception) {
                showError("Failed to update notification setting")
                achievementNotificationsSwitch.isChecked = !enabled // Revert on error
            }
        }
    }

    private fun updateWeeklySummarySetting(enabled: Boolean) {
        lifecycleScope.launch {
            try {
                userDataManager.setWeeklySummaryEnabled(enabled)
                engagementAnalytics.trackFeatureUsage("weekly_summary_toggled")
                

            } catch (e: Exception) {
                showError("Failed to update weekly summary setting")
                weeklySummarySwitch.isChecked = !enabled // Revert on error
            }
        }
    }

    private fun openPrivacyPolicy() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://vibetube.app/privacy"))
            startActivity(intent)
            lifecycleScope.launch {
                engagementAnalytics.trackFeatureUsage("privacy_policy_opened")
            }
        } catch (e: Exception) {
            showError("Unable to open privacy policy")
        }
    }

    private fun showError(message: String) {
        // Error handling without toast - could be replaced with other UI feedback
    }

    override fun onResume() {
        super.onResume()
        updateDataStats()
    }

    companion object {
        fun newInstance(): LibrarySettingsFragment {
            return LibrarySettingsFragment()
        }
    }
}
