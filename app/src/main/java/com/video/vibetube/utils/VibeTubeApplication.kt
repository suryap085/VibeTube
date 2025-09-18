package com.video.vibetube.utils

import android.app.Application

class VibeTubeApplication : Application() {
    override fun onCreate() {
        super.onCreate()


        // Clean up old quota data on app start
        QuotaManager(this).cleanupOldQuotaData()
    }
}