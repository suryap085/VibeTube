package com.video.vibetube.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.video.vibetube.R
import com.video.vibetube.activity.LibraryActivity


/**
 * YouTube Policy Compliant Achievement Notification Manager
 * 
 * Compliance Features:
 * - User-controlled notifications (can be disabled)
 * - No spam or excessive notifications
 * - Clear notification content
 * - Respects system notification settings
 * - No misleading or clickbait content
 */
class AchievementNotificationManager(private val context: Context) {
    
    companion object {
        private const val CHANNEL_ID = "achievement_notifications"
        private const val CHANNEL_NAME = "Achievement Notifications"
        private const val CHANNEL_DESCRIPTION = "Notifications for unlocked achievements"
        private const val NOTIFICATION_ID_BASE = 1000
        
        // Notification preferences
        private const val PREF_NOTIFICATIONS_ENABLED = "achievement_notifications_enabled"
        private const val PREF_SOUND_ENABLED = "achievement_sound_enabled"
        private const val PREF_VIBRATION_ENABLED = "achievement_vibration_enabled"
    }
    
    private val notificationManager = NotificationManagerCompat.from(context)
    private val prefs = context.getSharedPreferences("achievement_notifications", Context.MODE_PRIVATE)
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Create notification channel for achievements (Android 8.0+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                lightColor = context.getColor(R.color.primary)
                enableVibration(true)
                setShowBadge(true)
            }
            
            val systemNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemNotificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show achievement unlock notification
     */
    fun showAchievementNotification(achievement: AchievementManager.Achievement) {
        if (!areNotificationsEnabled()) return
        if (!notificationManager.areNotificationsEnabled()) return

        val intent = Intent(context, LibraryActivity::class.java).apply {
            putExtra("SECTION", "achievements")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            achievement.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_achievement)
            .setContentTitle("🏆 Achievement Unlocked!")
            .setContentText(achievement.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${achievement.title}\n${achievement.description}")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setColor(context.getColor(R.color.primary))
            .apply {
                if (isSoundEnabled()) {
                    setDefaults(NotificationCompat.DEFAULT_SOUND)
                }
                if (isVibrationEnabled()) {
                    setVibrate(longArrayOf(0, 250, 250, 250))
                }
            }
            .build()
        
        try {
            notificationManager.notify(
                NOTIFICATION_ID_BASE + achievement.id.hashCode(),
                notification
            )
        } catch (e: SecurityException) {
            // Handle case where notification permission is revoked
        }
    }
    
    /**
     * Show milestone notification (e.g., "10 achievements unlocked!")
     */
    fun showMilestoneNotification(milestoneText: String, achievementCount: Int) {
        if (!areNotificationsEnabled()) return
        if (!notificationManager.areNotificationsEnabled()) return
        
        val intent = Intent(context, LibraryActivity::class.java).apply {
            putExtra("SECTION", "achievements")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            milestoneText.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_star)
            .setContentTitle("🌟 Milestone Reached!")
            .setContentText(milestoneText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$milestoneText\n\nYou've unlocked $achievementCount achievements so far. Keep exploring!")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setColor(context.getColor(R.color.primary))
            .apply {
                if (isSoundEnabled()) {
                    setDefaults(NotificationCompat.DEFAULT_SOUND)
                }
                if (isVibrationEnabled()) {
                    setVibrate(longArrayOf(0, 500, 250, 500))
                }
            }
            .build()
        
        try {
            notificationManager.notify(
                NOTIFICATION_ID_BASE + milestoneText.hashCode(),
                notification
            )
        } catch (e: SecurityException) {
            // Handle case where notification permission is revoked
        }
    }
    
    /**
     * Show weekly summary notification
     */
    fun showWeeklySummaryNotification(videosWatched: Int, newAchievements: Int) {
        if (!areNotificationsEnabled()) return
        if (!notificationManager.areNotificationsEnabled()) return
        if (videosWatched == 0) return // Don't show if no activity
        
        val intent = Intent(context, LibraryActivity::class.java).apply {
            putExtra("SECTION", "stats")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            "weekly_summary".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val summaryText = buildString {
            append("This week you watched $videosWatched videos")
            if (newAchievements > 0) {
                append(" and unlocked $newAchievements new achievements")
            }
            append("!")
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_analytics)
            .setContentTitle("📊 Your Weekly Summary")
            .setContentText(summaryText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$summaryText\n\nTap to see your detailed stats and progress.")
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setColor(context.getColor(R.color.primary))
            .build()
        
        try {
            notificationManager.notify(
                NOTIFICATION_ID_BASE + "weekly_summary".hashCode(),
                notification
            )
        } catch (e: SecurityException) {
            // Handle case where notification permission is revoked
        }
    }
    
    /**
     * Cancel all achievement notifications
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
    
    /**
     * Cancel specific achievement notification
     */
    fun cancelAchievementNotification(achievementId: String) {
        notificationManager.cancel(NOTIFICATION_ID_BASE + achievementId.hashCode())
    }
    
    // Notification Preferences
    
    fun areNotificationsEnabled(): Boolean {
        return prefs.getBoolean(PREF_NOTIFICATIONS_ENABLED, true)
    }
    
    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_NOTIFICATIONS_ENABLED, enabled).apply()
    }
    
    fun isSoundEnabled(): Boolean {
        return prefs.getBoolean(PREF_SOUND_ENABLED, true)
    }
    
    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_SOUND_ENABLED, enabled).apply()
    }
    
    fun isVibrationEnabled(): Boolean {
        return prefs.getBoolean(PREF_VIBRATION_ENABLED, true)
    }
    
    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_VIBRATION_ENABLED, enabled).apply()
    }
    
    /**
     * Check if system notifications are enabled for the app
     */
    fun areSystemNotificationsEnabled(): Boolean {
        return notificationManager.areNotificationsEnabled()
    }
    
    /**
     * Get notification settings summary for display in settings
     */
    fun getNotificationSettingsSummary(): String {
        return when {
            !areSystemNotificationsEnabled() -> "Disabled in system settings"
            !areNotificationsEnabled() -> "Disabled"
            else -> {
                val features = mutableListOf<String>()
                if (isSoundEnabled()) features.add("Sound")
                if (isVibrationEnabled()) features.add("Vibration")
                if (features.isEmpty()) "Enabled (Silent)" else "Enabled (${features.joinToString(", ")})"
            }
        }
    }
}
