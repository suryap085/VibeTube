package com.video.vibetube.adapters

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.video.vibetube.R
import com.video.vibetube.utils.AchievementManager
import java.text.SimpleDateFormat
import java.util.*

class AchievementAdapter(
    private val onItemClick: (AchievementManager.Achievement) -> Unit
) : RecyclerView.Adapter<AchievementAdapter.AchievementViewHolder>() {

    private var achievements = mutableListOf<AchievementManager.Achievement>()
    private val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    inner class AchievementViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.achievementCard)
        val iconImageView: ImageView = itemView.findViewById(R.id.achievementIcon)
        val titleTextView: TextView = itemView.findViewById(R.id.achievementTitle)
        val descriptionTextView: TextView = itemView.findViewById(R.id.achievementDescription)
        val progressIndicator: LinearProgressIndicator = itemView.findViewById(R.id.achievementProgress)
        val progressTextView: TextView = itemView.findViewById(R.id.progressText)
        val unlockedBadge: ImageView = itemView.findViewById(R.id.unlockedBadge)
        val unlockedDateTextView: TextView = itemView.findViewById(R.id.unlockedDate)
        val categoryChip: TextView = itemView.findViewById(R.id.categoryChip)
        val difficultyIndicator: View = itemView.findViewById(R.id.difficultyIndicator)

        fun bind(achievement: AchievementManager.Achievement) {
            titleTextView.text = achievement.title
            descriptionTextView.text = achievement.description
            categoryChip.text = getCategoryDisplayName(achievement.category.name)

            // Set category chip color
            setCategoryChipColor(achievement.category.name)

            // Set achievement icon
            setAchievementIcon(achievement)

            if (achievement.isUnlocked) {
                // Unlocked achievement
                unlockedBadge.visibility = View.VISIBLE
                unlockedDateTextView.visibility = View.VISIBLE
                progressIndicator.visibility = View.GONE
                progressTextView.visibility = View.GONE
                
                unlockedDateTextView.text = "Unlocked ${formatUnlockedDate(achievement.unlockedAt)}"
                
                // Style for unlocked achievement
                cardView.setCardBackgroundColor(itemView.context.getColor(R.color.achievement_unlocked_background))
                cardView.strokeColor = itemView.context.getColor(R.color.achievement_unlocked_stroke)
                cardView.strokeWidth = 2
                
                titleTextView.setTextColor(itemView.context.getColor(R.color.achievement_unlocked_title))
                descriptionTextView.setTextColor(itemView.context.getColor(R.color.achievement_unlocked_description))
                
                iconImageView.alpha = 1.0f
                
            } else {
                // Locked achievement
                unlockedBadge.visibility = View.GONE
                unlockedDateTextView.visibility = View.GONE
                progressIndicator.visibility = View.VISIBLE
                progressTextView.visibility = View.VISIBLE
                
                // Calculate progress
                val progressPercentage = if (achievement.maxProgress > 0) {
                    ((achievement.progress / achievement.maxProgress) * 100).toInt()
                } else {
                    0
                }

                progressIndicator.progress = progressPercentage

                // Format progress text based on achievement type
                val progressText = if (achievement.id == "hour_watcher" || achievement.id == "marathon_viewer") {
                    // Time-based achievements - show in minutes/hours
                    val currentMinutes = (achievement.progress / 1000 / 60).toInt()
                    val targetMinutes = (achievement.maxProgress / 1000 / 60).toInt()
                    val targetHours = targetMinutes / 60

                    if (targetHours > 0) {
                        val currentHours = currentMinutes / 60
                        val currentRemainingMinutes = currentMinutes % 60
                        val currentTimeText = if (currentHours > 0) {
                            "${currentHours}h ${currentRemainingMinutes}m"
                        } else {
                            "${currentMinutes}m"
                        }
                        "$currentTimeText / ${targetHours}h"
                    } else {
                        "${currentMinutes}m / ${targetMinutes}m"
                    }
                } else {
                    // Regular achievements - show as numbers
                    "${achievement.progress.toInt()} / ${achievement.maxProgress.toInt()}"
                }

                progressTextView.text = progressText
                
                // Style for locked achievement
                cardView.setCardBackgroundColor(itemView.context.getColor(R.color.achievement_locked_background))
                cardView.strokeWidth = 0
                
                titleTextView.setTextColor(itemView.context.getColor(R.color.achievement_locked_title))
                descriptionTextView.setTextColor(itemView.context.getColor(R.color.achievement_locked_description))
                
                iconImageView.alpha = 0.5f
            }

            // Click listener
            itemView.setOnClickListener {
                onItemClick(achievement)
                
                // Add click animation
                val scaleDown = ObjectAnimator.ofFloat(cardView, "scaleX", 1.0f, 0.95f)
                scaleDown.duration = 100
                val scaleUp = ObjectAnimator.ofFloat(cardView, "scaleX", 0.95f, 1.0f)
                scaleUp.duration = 100
                scaleDown.start()
                scaleUp.startDelay = 100
                scaleUp.start()
            }
        }

        private fun setCategoryChipColor(category: String) {
            val colorRes = when (category.lowercase()) {
                "viewing" -> R.color.achievement_category_viewing
                "exploration" -> R.color.achievement_category_exploration
                "organization" -> R.color.achievement_category_organization
                "consistency" -> R.color.achievement_category_consistency
                "milestones" -> R.color.achievement_category_milestones
                else -> R.color.achievement_category_default
            }
            
            categoryChip.setBackgroundColor(itemView.context.getColor(colorRes))
        }

        private fun setDifficultyIndicator(difficulty: String) {
            val colorRes = when (difficulty.lowercase()) {
                "easy" -> R.color.difficulty_easy
                "medium" -> R.color.difficulty_medium
                "hard" -> R.color.difficulty_hard
                "expert" -> R.color.difficulty_expert
                else -> R.color.difficulty_default
            }
            
            difficultyIndicator.setBackgroundColor(itemView.context.getColor(colorRes))
        }

        private fun setAchievementIcon(achievement: AchievementManager.Achievement) {
            val iconRes = when {
                achievement.isUnlocked -> getUnlockedIcon(achievement.category.name)
                else -> getLockedIcon(achievement.category.name)
            }

            iconImageView.setImageResource(iconRes)
        }

        private fun getUnlockedIcon(category: String): Int {
            return when (category.lowercase()) {
                "viewing" -> R.drawable.ic_star
                "exploration" -> R.drawable.ic_search
                "organization" -> R.drawable.ic_library_books
                "consistency" -> R.drawable.ic_check_circle
                "milestones" -> R.drawable.ic_trophy
                else -> R.drawable.ic_achievement
            }
        }

        private fun getLockedIcon(category: String): Int {
            return when (category.lowercase()) {
                "viewing" -> R.drawable.ic_star
                "exploration" -> R.drawable.ic_search
                "organization" -> R.drawable.ic_library_books
                "consistency" -> R.drawable.ic_check_circle
                "milestones" -> R.drawable.ic_trophy
                else -> R.drawable.ic_achievement
            }
        }

        private fun getCategoryDisplayName(category: String): String {
            return when (category.lowercase()) {
                "viewing" -> "Viewing"
                "exploration" -> "Exploration"
                "organization" -> "Organization"
                "consistency" -> "Consistency"
                "milestones" -> "Milestones"
                else -> category.replaceFirstChar { it.uppercase() }
            }
        }

        private fun formatUnlockedDate(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            val date = Date(timestamp)

            return when {
                diff < 24 * 60 * 60 * 1000 -> "today"
                diff < 2 * 24 * 60 * 60 * 1000 -> "yesterday"
                diff < 7 * 24 * 60 * 60 * 1000 -> {
                    val days = (diff / (24 * 60 * 60 * 1000)).toInt()
                    "$days days ago"
                }
                diff < 30 * 24 * 60 * 60 * 1000 -> {
                    val weeks = (diff / (7 * 24 * 60 * 60 * 1000)).toInt()
                    if (weeks == 1) "1 week ago" else "$weeks weeks ago"
                }
                else -> "on ${dateFormatter.format(date)}"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AchievementViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_achievement, parent, false)
        return AchievementViewHolder(view)
    }

    override fun onBindViewHolder(holder: AchievementViewHolder, position: Int) {
        holder.bind(achievements[position])
    }

    override fun getItemCount(): Int = achievements.size

    fun updateAchievements(newAchievements: List<AchievementManager.Achievement>) {
        achievements.clear()
        achievements.addAll(newAchievements)
        notifyDataSetChanged()
    }

    fun updateAchievement(achievement: AchievementManager.Achievement) {
        val index = achievements.indexOfFirst { it.id == achievement.id }
        if (index != -1) {
            val oldAchievement = achievements[index]
            achievements[index] = achievement
            
            // Animate if newly unlocked
            if (!oldAchievement.isUnlocked && achievement.isUnlocked) {
                notifyItemChanged(index, "unlock_animation")
            } else {
                notifyItemChanged(index)
            }
        }
    }

    fun getAchievement(position: Int): AchievementManager.Achievement? {
        return if (position in 0 until achievements.size) {
            achievements[position]
        } else {
            null
        }
    }

    fun getAchievements(): List<AchievementManager.Achievement> = achievements.toList()

    fun getUnlockedCount(): Int = achievements.count { it.isUnlocked }

    fun getTotalCount(): Int = achievements.size

    fun getProgressPercentage(): Int {
        val total = getTotalCount()
        return if (total > 0) {
            (getUnlockedCount() * 100) / total
        } else {
            0
        }
    }

    fun getAchievementsByCategory(category: String): List<AchievementManager.Achievement> {
        return achievements.filter { it.category.name.equals(category, ignoreCase = true) }
    }

    fun getUnlockedAchievements(): List<AchievementManager.Achievement> {
        return achievements.filter { it.isUnlocked }
    }

    fun getLockedAchievements(): List<AchievementManager.Achievement> {
        return achievements.filter { !it.isUnlocked }
    }
}
