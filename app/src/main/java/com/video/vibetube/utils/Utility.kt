package com.video.vibetube.utils

import android.annotation.SuppressLint
import java.text.SimpleDateFormat
import java.util.*

object Utility {
     fun parseDurationToSeconds(duration: String): Long {
        // ISO 8601 duration format: PT#H#M#S, e.g., PT1M30S
        val regex = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
        val matchResult = regex.matchEntire(duration) ?: return 0
        val (hours, minutes, seconds) = matchResult.destructured
        val h = hours.toLongOrNull() ?: 0L
        val m = minutes.toLongOrNull() ?: 0L
        val s = seconds.toLongOrNull() ?: 0L
        return h * 3600 + m * 60 + s
    }

     @SuppressLint("DefaultLocale")
     fun parseDuration(isoDuration: String): String {
        val regex = Regex("PT(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9]+)S)?")
        val match = regex.matchEntire(isoDuration) ?: return "0:00"
        val hours = match.groups[1]?.value?.toIntOrNull() ?: 0
        val minutes = match.groups[2]?.value?.toIntOrNull() ?: 0
        val seconds = match.groups[3]?.value?.toIntOrNull() ?: 0
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * Parses a duration string in various formats to seconds.
     * Supports:
     *   - ISO 8601 (e.g., PT1M2S, PT59S, PT1H2M3S)
     *   - mm:ss (e.g., 01:02)
     *   - hh:mm:ss (e.g., 01:02:03)
     *   - plain seconds (e.g., 59)
     */
    fun parseAnyDurationToSeconds(duration: String): Long {
        val trimmed = duration.trim()
        if (trimmed.isEmpty()) return 0L
        // Try ISO 8601
        if (trimmed.startsWith("PT")) {
            return parseDurationToSeconds(trimmed)
        }
        // Try hh:mm:ss or mm:ss
        if (":" in trimmed) {
            val parts = trimmed.split(":").map { it.toLongOrNull() ?: 0L }
            return when (parts.size) {
                2 -> parts[0] * 60 + parts[1] // mm:ss
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2] // hh:mm:ss
                else -> 0L
            }
        }
        // Try plain seconds
        return trimmed.toLongOrNull() ?: 0L
    }

    fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(dateString)
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (_: Exception) {
            "Unknown"
        }
    }
}