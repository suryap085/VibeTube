package com.video.vibetube.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.video.vibetube.BuildConfig
import com.video.vibetube.BuildConfig.DAILY_QUOTA_LIMIT
import com.video.vibetube.models.QuotaStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class QuotaManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("quota_manager", Context.MODE_PRIVATE)
    private val dailyLimit = DAILY_QUOTA_LIMIT

    fun canMakeApiCall(): Boolean {
        return getDailyUsage() < dailyLimit && !isQuotaExceeded()
    }

    fun recordApiCall(operation: String, cost: Int) {
        val today = getTodayKey()
        val currentUsage = prefs.getInt(today, 0)
        val newUsage = currentUsage + cost
        prefs.edit().putInt(today, newUsage).apply()

        // Log for monitoring (only in debug)
        if (BuildConfig.IS_DEBUG) {
            Log.d("QuotaManager", "API call: $operation, cost: $cost, daily total: $newUsage/$dailyLimit")
        }

        // Auto-mark as exceeded if limit reached
        if (newUsage >= dailyLimit) {
            markQuotaExceeded()
        }

        // Warn when approaching limit
        if (newUsage > dailyLimit * 0.9) { // 90% threshold
            Log.w("QuotaManager", "Approaching daily quota limit: $newUsage/$dailyLimit")
        }
    }

    fun getDailyUsage(): Int {
        return prefs.getInt(getTodayKey(), 0)
    }

    fun getRemainingQuota(): Int {
        return kotlin.math.max(0, dailyLimit - getDailyUsage())
    }

    fun isQuotaExceeded(): Boolean {
        val today = getTodayKey()
        return getDailyUsage() >= dailyLimit ||
                prefs.getBoolean("quota_exceeded_$today", false)
    }

    fun markQuotaExceeded() {
        val today = getTodayKey()
        prefs.edit().putBoolean("quota_exceeded_$today", true).apply()

        if (BuildConfig.IS_DEBUG) {
            Log.e("QuotaManager", "Daily quota exceeded!")
        }
    }

    private fun getTodayKey(): String {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return dateFormat.format(calendar.time)
    }

    // Enhanced quota status
    fun getQuotaStatus(): QuotaStatus {
        val usage = getDailyUsage()
        val remaining = getRemainingQuota()
        val percentUsed = if (dailyLimit > 0) (usage.toFloat() / dailyLimit * 100).toInt() else 0

        return QuotaStatus(
            used = usage,
            remaining = remaining,
            total = dailyLimit,
            percentUsed = percentUsed,
            isExceeded = isQuotaExceeded()
        )
    }

    // Get quota reset time (next midnight)
    fun getQuotaResetTime(): Long {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    // Clean up old quota data (keep last 7 days)
    fun cleanupOldQuotaData() {
        val allKeys = prefs.all.keys
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // Keep last 7 days
        val keepDays = (0..6).map { days ->
            calendar.apply {
                timeInMillis = System.currentTimeMillis()
                add(Calendar.DAY_OF_MONTH, -days)
            }
            dateFormat.format(calendar.time)
        }.toSet()

        val keysToRemove = allKeys.filter { key ->
            when {
                key.startsWith("usage_") -> {
                    val date = key.removePrefix("usage_")
                    date !in keepDays
                }
                key.startsWith("quota_exceeded_") -> {
                    val date = key.removePrefix("quota_exceeded_")
                    date !in keepDays
                }
                else -> false
            }
        }

        val editor = prefs.edit()
        keysToRemove.forEach { editor.remove(it) }
        editor.apply()

        if (BuildConfig.IS_DEBUG && keysToRemove.isNotEmpty()) {
            Log.d("QuotaManager", "Cleaned up ${keysToRemove.size} old quota entries")
        }
    }
}