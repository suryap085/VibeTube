package com.video.vibetube.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.video.vibetube.R
import com.video.vibetube.adapters.AchievementAdapter

import com.video.vibetube.utils.AchievementManager
import com.video.vibetube.utils.EngagementAnalytics
import kotlinx.coroutines.launch

class AchievementsFragment : Fragment() {

    private lateinit var achievementManager: AchievementManager
    private lateinit var engagementAnalytics: EngagementAnalytics
    private lateinit var achievementAdapter: AchievementAdapter
    
    private lateinit var achievementsRecyclerView: RecyclerView
    private lateinit var emptyStateLayout: View
    private lateinit var loadingStateLayout: View
    private lateinit var achievementProgressText: TextView
    private lateinit var achievementLevelText: TextView
    private lateinit var overallProgressBar: LinearProgressIndicator
    private lateinit var categoryFilterChipGroup: ChipGroup
    private lateinit var achievementSettingsFab: FloatingActionButton
    private lateinit var startWatchingButton: com.google.android.material.button.MaterialButton

    private var currentFilter = FilterType.ALL
    private var allAchievements: List<AchievementManager.Achievement> = emptyList()

    enum class FilterType {
        ALL, VIEWING, EXPLORATION, ORGANIZATION, CONSISTENCY, MILESTONES, UNLOCKED, LOCKED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_achievements, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeManagers()
        initializeViews(view)
        setupRecyclerView()
        setupChipGroup()
        setupClickListeners()
        loadAchievements()
    }

    private fun initializeManagers() {
        achievementManager = AchievementManager.getInstance(requireContext())
        engagementAnalytics = EngagementAnalytics.getInstance(requireContext())
    }

    private fun initializeViews(view: View) {
        achievementsRecyclerView = view.findViewById(R.id.achievementsRecyclerView)
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout)
        loadingStateLayout = view.findViewById(R.id.loadingStateLayout)
        achievementProgressText = view.findViewById(R.id.achievementProgressText)
        achievementLevelText = view.findViewById(R.id.achievementLevelText)
        overallProgressBar = view.findViewById(R.id.overallProgressBar)
        categoryFilterChipGroup = view.findViewById(R.id.categoryFilterChipGroup)
        achievementSettingsFab = view.findViewById(R.id.achievementSettingsFab)
        startWatchingButton = view.findViewById(R.id.startWatchingButton)
    }

    private fun setupRecyclerView() {
        achievementAdapter = AchievementAdapter(
            onItemClick = { achievement ->
                handleAchievementClick(achievement)
            }
        )
        
        achievementsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = achievementAdapter
        }
    }

    private fun setupChipGroup() {
        categoryFilterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val selectedChip = group.findViewById<Chip>(checkedIds[0])
                currentFilter = when (selectedChip.id) {
                    R.id.chipAllAchievements -> FilterType.ALL
                    R.id.chipViewing -> FilterType.VIEWING
                    R.id.chipExploration -> FilterType.EXPLORATION
                    R.id.chipOrganization -> FilterType.ORGANIZATION
                    R.id.chipConsistency -> FilterType.CONSISTENCY
                    R.id.chipMilestones -> FilterType.MILESTONES
                    else -> FilterType.ALL
                }
                applyFilter()
            }
        }
    }

    private fun setupClickListeners() {
        achievementSettingsFab.setOnClickListener {
            showAchievementSettings()
        }

        startWatchingButton.setOnClickListener {
            // Navigate to home tab
            requireActivity().finish()
        }
    }

    private fun loadAchievements() {
        showLoadingState()

        lifecycleScope.launch {
            try {
                // Reset any incorrectly unlocked achievements first
                achievementManager.resetIncorrectAchievements()

                // Use getAchievementProgress() instead of getAllAchievements() to get actual progress data
                allAchievements = achievementManager.getAchievementProgress()
                updateProgressHeader()
                applyFilter()

                engagementAnalytics.trackFeatureUsage("achievements_viewed")
            } catch (e: Exception) {
                showError("Failed to load achievements")
            }
        }
    }

    private fun updateProgressHeader() {
        val unlockedCount = allAchievements.count { it.isUnlocked }
        val totalCount = allAchievements.size
        val progressPercentage = if (totalCount > 0) (unlockedCount * 100) / totalCount else 0

        achievementProgressText.text = "$unlockedCount / $totalCount Achievements"
        overallProgressBar.progress = progressPercentage

        // Update level based on progress
        achievementLevelText.text = when {
            progressPercentage == 0 -> "Beginner Explorer"
            progressPercentage < 25 -> "Casual Viewer"
            progressPercentage < 50 -> "Active User"
            progressPercentage < 75 -> "Dedicated Fan"
            progressPercentage < 100 -> "VibeTube Expert"
            else -> "Achievement Master"
        }
    }

    private fun applyFilter() {
        val filteredAchievements = when (currentFilter) {
            FilterType.ALL -> allAchievements
            FilterType.VIEWING -> allAchievements.filter { it.category.name == "VIEWING" }
            FilterType.EXPLORATION -> allAchievements.filter { it.category.name == "EXPLORATION" }
            FilterType.ORGANIZATION -> allAchievements.filter { it.category.name == "ORGANIZATION" }
            FilterType.CONSISTENCY -> allAchievements.filter { it.category.name == "CONSISTENCY" }
            FilterType.MILESTONES -> allAchievements.filter { it.category.name == "MILESTONES" }
            FilterType.UNLOCKED -> allAchievements.filter { it.isUnlocked }
            FilterType.LOCKED -> allAchievements.filter { !it.isUnlocked }
        }

        if (filteredAchievements.isEmpty() && currentFilter == FilterType.ALL) {
            showEmptyState()
        } else {
            showContent(filteredAchievements)
        }
    }

    private fun showAchievementSettings() {
        val options = arrayOf(
            "Enable Achievement Notifications",
            "Disable Gamification",
            "Reset Achievement Progress",
            "Export Achievement Data",
            "Debug Stats Info"
        )

        val checkedItems = booleanArrayOf(
            achievementManager.areNotificationsEnabled(),
            !achievementManager.isGamificationEnabled(),
            false,
            false,
            false
        )

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Achievement Settings")
            .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                when (which) {
                    0 -> toggleAchievementNotifications(isChecked)
                    1 -> toggleGamification(!isChecked)
                    2 -> if (isChecked) showResetConfirmation()
                    3 -> if (isChecked) exportAchievementData()
                    4 -> if (isChecked) showDebugStats()
                }
            }
            .setPositiveButton("Done", null)
            .show()
    }

    private fun toggleAchievementNotifications(enabled: Boolean) {
        lifecycleScope.launch {
            try {
                achievementManager.setNotificationsEnabled(enabled)
                engagementAnalytics.trackFeatureUsage("achievement_notifications_toggled")
                val message = if (enabled) "Notifications enabled" else "Notifications disabled"
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Failed to update notification settings")
            }
        }
    }

    private fun toggleGamification(enabled: Boolean) {
        lifecycleScope.launch {
            try {
                achievementManager.setGamificationEnabled(enabled)
                engagementAnalytics.trackFeatureUsage("gamification_toggled")
                val message = if (enabled) "Gamification enabled" else "Gamification disabled"
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                
                if (!enabled) {
                    // If gamification is disabled, navigate back
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            } catch (e: Exception) {
                showError("Failed to update gamification settings")
            }
        }
    }

    private fun showResetConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reset Achievement Progress")
            .setMessage("Are you sure you want to reset all achievement progress? This action cannot be undone.")
            .setPositiveButton("Reset") { _, _ ->
                resetAchievementProgress()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetAchievementProgress() {
        lifecycleScope.launch {
            try {
                achievementManager.resetAllProgress()
                loadAchievements()
                engagementAnalytics.trackFeatureUsage("achievements_reset")
                Toast.makeText(requireContext(), "Achievement progress reset", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Failed to reset achievement progress")
            }
        }
    }

    private fun exportAchievementData() {
        lifecycleScope.launch {
            try {
                achievementManager.exportAchievementData()
                engagementAnalytics.trackFeatureUsage("achievements_exported")
                Toast.makeText(requireContext(), "Achievement data exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                showError("Failed to export achievement data")
            }
        }
    }

    private fun handleAchievementClick(achievement: AchievementManager.Achievement) {
        lifecycleScope.launch {
            engagementAnalytics.trackFeatureUsage("achievement_details_viewed")
        }

        // Show achievement details dialog
        val message = if (achievement.isUnlocked) {
            "Unlocked on ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(achievement.unlockedAt))}\n\n${achievement.description}"
        } else {
            "${achievement.description}\n\nProgress: ${achievement.progress.toInt()}/${achievement.maxProgress.toInt()}"
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(achievement.title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLoadingState() {
        loadingStateLayout.visibility = View.VISIBLE
        achievementsRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.GONE
    }

    private fun showContent(achievements: List<AchievementManager.Achievement>) {
        loadingStateLayout.visibility = View.GONE
        achievementsRecyclerView.visibility = View.VISIBLE
        emptyStateLayout.visibility = View.GONE
        
        achievementAdapter.updateAchievements(achievements)
    }

    private fun showEmptyState() {
        loadingStateLayout.visibility = View.GONE
        achievementsRecyclerView.visibility = View.GONE
        emptyStateLayout.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        // Check if fragment is still attached to avoid crashes
        if (!isAdded || context == null) return

        loadingStateLayout.visibility = View.GONE
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun showDebugStats() {
        lifecycleScope.launch {
            try {
                val debugInfo = achievementManager.getStatsDebugInfo()
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Debug Stats")
                    .setMessage(debugInfo)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Copy") { _, _ ->
                        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Debug Stats", debugInfo)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(requireContext(), "Debug info copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            } catch (e: Exception) {
                showError("Failed to get debug stats")
            }
        }
    }

    companion object {
        fun newInstance(): AchievementsFragment {
            return AchievementsFragment()
        }
    }
}
