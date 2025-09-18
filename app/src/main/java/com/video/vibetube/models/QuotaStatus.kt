package com.video.vibetube.models

data class QuotaStatus(
    val used: Int,
    val remaining: Int,
    val total: Int,
    val percentUsed: Int,
    val isExceeded: Boolean
) {
    val isNearLimit: Boolean get() = percentUsed >= 90
    val isWarningLevel: Boolean get() = percentUsed >= 75

    fun getStatusMessage(): String {
        return when {
            isExceeded -> "Quota exceeded"
            isNearLimit -> "Approaching limit"
            isWarningLevel -> "High usage"
            else -> "Normal usage"
        }
    }

    fun getRecommendedAction(): String {
        return when {
            isExceeded -> "Use cached data only"
            isNearLimit -> "Limit new requests"
            isWarningLevel -> "Reduce API calls"
            else -> "Normal operation"
        }
    }
}