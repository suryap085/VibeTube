package com.video.vibetube.fragments

import android.os.Bundle
import android.view.View

class TrendingSongsFragment : BaseVideoFragment() {
    override fun getDefaultCategoryId(): String = "10"
    override fun getFragmentTitle(): String = "Songs"
    override fun getDefaultChannelId(): String = "UCjvgGbPPn-FgYeguc5nxG4A"
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // You can choose between loading by category or by channel
        val defaultChannelId = getDefaultChannelId()

        if (defaultChannelId.isNotEmpty()) {
            // Load videos from a specific channel (more quota-efficient if you have a specific channel)
            loadVideosByChannel(defaultChannelId)
        } else {
            // Load videos by category (existing functionality)
            loadVideosByCategory(getDefaultCategoryId())
        }
    }
}