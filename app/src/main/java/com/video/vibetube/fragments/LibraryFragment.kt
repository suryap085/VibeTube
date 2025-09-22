package com.video.vibetube.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.video.vibetube.R
import com.video.vibetube.activity.YouTubePlayerActivity
import com.video.vibetube.adapters.LibraryPagerAdapter
import com.video.vibetube.models.Video
import com.video.vibetube.utils.UserDataManager
import kotlinx.coroutines.launch

/**
 * YouTube Policy Compliant Library Fragment
 * 
 * Features:
 * - Watch History with resume functionality
 * - Favorites management with categories
 * - Custom playlists
 * - User consent management
 * - Data deletion controls
 */
class LibraryFragment : Fragment() {
    
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var userDataManager: UserDataManager
    
    companion object {
        fun newInstance(): LibraryFragment {
            return LibraryFragment()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_library, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        userDataManager = UserDataManager(requireContext())
        
        initViews(view)
        checkUserConsent()
    }
    
    private fun initViews(view: View) {
        tabLayout = view.findViewById(R.id.libraryTabLayout)
        viewPager = view.findViewById(R.id.libraryViewPager)
        
        setupViewPager()
    }
    
    private fun setupViewPager() {
        val adapter = LibraryPagerAdapter(requireActivity())
        viewPager.adapter = adapter
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "History"
                1 -> "Favorites"
                2 -> "Playlists"
                3 -> "Settings"
                else -> ""
            }
        }.attach()
    }
    
    /**
     * YouTube Policy Compliance: User Consent Management
     * Users must explicitly consent to data collection and storage
     */
    private fun checkUserConsent() {
        if (!userDataManager.hasUserConsent()) {
            showConsentDialog()
        }
    }
    
    private fun showConsentDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enable Library Features")
            .setMessage(
                "VibeTube can save your watch history and favorites to enhance your experience. " +
                "This data is stored locally on your device and can be deleted at any time.\n\n" +
                "• Watch history helps you resume videos\n" +
                "• Favorites let you save videos for later\n" +
                "• Custom playlists organize your content\n\n" +
                "Your data will be automatically deleted after 30 days of inactivity. " +
                "You can delete all data immediately in Settings."
            )
            .setPositiveButton("Enable") { _, _ ->
                userDataManager.setUserConsent(true)
                refreshLibraryContent()
            }
            .setNegativeButton("Not Now") { _, _ ->
                showLimitedLibraryView()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun refreshLibraryContent() {
        // Refresh all tabs to show user data
        (viewPager.adapter as? LibraryPagerAdapter)?.notifyDataSetChanged()
    }
    
    private fun showLimitedLibraryView() {
        // Show message about enabling features
        Toast.makeText(
            requireContext(),
            "Enable library features in Settings to save your watch history and favorites",
            Toast.LENGTH_LONG
        ).show()
    }
    
    /**
     * Helper method to convert library items to Video objects for player
     */
    fun createVideoFromHistoryItem(historyItem: com.video.vibetube.models.WatchHistoryItem): Video {
        return Video(
            videoId = historyItem.videoId,
            title = historyItem.title,
            description = "",
            thumbnail = historyItem.thumbnail,
            channelTitle = historyItem.channelTitle,
            publishedAt = "",
            duration = historyItem.duration,
            channelId = historyItem.channelId
        )
    }
    
    fun createVideoFromFavoriteItem(favoriteItem: com.video.vibetube.models.FavoriteItem): Video {
        return Video(
            videoId = favoriteItem.videoId,
            title = favoriteItem.title,
            description = "",
            thumbnail = favoriteItem.thumbnail,
            channelTitle = favoriteItem.channelTitle,
            publishedAt = "",
            duration = favoriteItem.duration,
            channelId = favoriteItem.channelId
        )
    }
    
    /**
     * Play video with resume functionality for watch history
     */
    fun playVideoWithResume(video: Video, resumePosition: Float = 0.0f) {
        val intent = Intent(requireContext(), YouTubePlayerActivity::class.java).apply {
            putParcelableArrayListExtra("VIDEOS", ArrayList(listOf(video)))
            putExtra("CURRENT_INDEX", 0)
            if (resumePosition > 0.0f) {
                putExtra("RESUME_POSITION", resumePosition)
            }
        }
        startActivity(intent)
    }
    
    /**
     * Show data management options (YouTube Policy Compliance)
     */
    fun showDataManagementDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manage Your Data")
            .setMessage(
                "You have full control over your library data:\n\n" +
                "• All data is stored locally on your device\n" +
                "• Data is automatically deleted after 30 days\n" +
                "• You can delete all data immediately\n" +
                "• No data is shared with third parties"
            )
            .setPositiveButton("Delete All Data") { _, _ ->
                confirmDataDeletion()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun confirmDataDeletion() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete All Library Data")
            .setMessage(
                "This will permanently delete:\n" +
                "• Watch history\n" +
                "• Favorites\n" +
                "• Custom playlists\n\n" +
                "This action cannot be undone."
            )
            .setPositiveButton("Delete") { _, _ ->
                deleteAllUserData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteAllUserData() {
        lifecycleScope.launch {
            userDataManager.deleteAllUserData()
            Toast.makeText(
                requireContext(),
                "All library data has been deleted",
                Toast.LENGTH_SHORT
            ).show()
            refreshLibraryContent()
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (userDataManager.hasUserConsent()) {
            refreshLibraryContent()
        }
    }
}
