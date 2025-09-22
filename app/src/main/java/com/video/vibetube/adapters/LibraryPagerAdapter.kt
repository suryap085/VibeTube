package com.video.vibetube.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.video.vibetube.fragments.WatchHistoryFragment
import com.video.vibetube.fragments.FavoritesFragment
import com.video.vibetube.fragments.PlaylistsFragment
import com.video.vibetube.fragments.AchievementsFragment
import com.video.vibetube.fragments.LibrarySettingsFragment
import com.video.vibetube.fragments.RecommendationsFragment
import com.video.vibetube.fragments.TrendingFragment
import com.video.vibetube.fragments.CategoriesFragment

/**
 * Library Pager Adapter for navigation drawer-based Library system
 * 
 * Provides different tab configurations based on the initial section requested
 */
class LibraryPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val initialSection: String? = null
) : FragmentStateAdapter(fragmentActivity) {

    companion object {
        private const val TAB_COUNT = 5
    }

    override fun getItemCount(): Int = TAB_COUNT

    override fun createFragment(position: Int): Fragment {
        return when (initialSection) {
            "history" -> createHistoryBasedTabs(position)
            "favorites" -> createFavoritesBasedTabs(position)
            "playlists" -> createPlaylistsBasedTabs(position)
            "achievements" -> createAchievementsBasedTabs(position)
            // Use dedicated fragments for Discover section
            "recommendations" -> createRecommendationsBasedTabs(position)
            "trending" -> createTrendingBasedTabs(position)
            "categories" -> createCategoriesBasedTabs(position)
            else -> createDefaultTabs(position)
        }
    }

    private fun createDefaultTabs(position: Int): Fragment {
        return when (position) {
            0 -> WatchHistoryFragment.newInstance()
            1 -> FavoritesFragment.newInstance()
            2 -> PlaylistsFragment.newInstance()
            3 -> AchievementsFragment.newInstance()
            4 -> LibrarySettingsFragment.newInstance()
            else -> WatchHistoryFragment.newInstance()
        }
    }

    private fun createHistoryBasedTabs(position: Int): Fragment {
        return when (position) {
            0 -> WatchHistoryFragment.newInstance()
            1 -> FavoritesFragment.newInstance()
            2 -> PlaylistsFragment.newInstance()
            3 -> AchievementsFragment.newInstance()
            4 -> LibrarySettingsFragment.newInstance()
            else -> WatchHistoryFragment.newInstance()
        }
    }

    private fun createFavoritesBasedTabs(position: Int): Fragment {
        return when (position) {
            0 -> FavoritesFragment.newInstance()
            1 -> WatchHistoryFragment.newInstance()
            2 -> PlaylistsFragment.newInstance()
            3 -> AchievementsFragment.newInstance()
            4 -> LibrarySettingsFragment.newInstance()
            else -> FavoritesFragment.newInstance()
        }
    }

    private fun createPlaylistsBasedTabs(position: Int): Fragment {
        return when (position) {
            0 -> PlaylistsFragment.newInstance()
            1 -> FavoritesFragment.newInstance()
            2 -> WatchHistoryFragment.newInstance()
            3 -> AchievementsFragment.newInstance()
            4 -> LibrarySettingsFragment.newInstance()
            else -> PlaylistsFragment.newInstance()
        }
    }

    private fun createAchievementsBasedTabs(position: Int): Fragment {
        return when (position) {
            0 -> AchievementsFragment.newInstance()
            1 -> WatchHistoryFragment.newInstance()
            2 -> FavoritesFragment.newInstance()
            3 -> PlaylistsFragment.newInstance()
            4 -> LibrarySettingsFragment.newInstance()
            else -> AchievementsFragment.newInstance()
        }
    }



    private fun createRecommendationsBasedTabs(position: Int): Fragment {
        return when (position) {
            0 -> RecommendationsFragment.newInstance()
            1 -> FavoritesFragment.newInstance()
            2 -> PlaylistsFragment.newInstance()
            3 -> AchievementsFragment.newInstance()
            4 -> LibrarySettingsFragment.newInstance()
            else -> RecommendationsFragment.newInstance()
        }
    }

    private fun createTrendingBasedTabs(position: Int): Fragment {
        return when (position) {
            0 -> TrendingFragment.newInstance()
            1 -> FavoritesFragment.newInstance()
            2 -> PlaylistsFragment.newInstance()
            3 -> AchievementsFragment.newInstance()
            4 -> LibrarySettingsFragment.newInstance()
            else -> TrendingFragment.newInstance()
        }
    }

    private fun createCategoriesBasedTabs(position: Int): Fragment {
        return when (position) {
            0 -> CategoriesFragment.newInstance()
            1 -> FavoritesFragment.newInstance()
            2 -> PlaylistsFragment.newInstance()
            3 -> AchievementsFragment.newInstance()
            4 -> LibrarySettingsFragment.newInstance()
            else -> CategoriesFragment.newInstance()
        }
    }



    /**
     * Get tab title based on position and initial section
     */
    fun getTabTitle(position: Int): String {
        return when (initialSection) {
            "history" -> getHistoryBasedTabTitle(position)
            "favorites" -> getFavoritesBasedTabTitle(position)
            "playlists" -> getPlaylistsBasedTabTitle(position)
            "achievements" -> getAchievementsBasedTabTitle(position)
            // For recommendations, trending, and categories, use custom titles for position 0
            "recommendations" -> getRecommendationsBasedTabTitle(position)
            "trending" -> getTrendingBasedTabTitle(position)
            "categories" -> getCategoriesBasedTabTitle(position)
            else -> getDefaultTabTitle(position)
        }
    }

    private fun getDefaultTabTitle(position: Int): String {
        return when (position) {
            0 -> "History"
            1 -> "Favorites"
            2 -> "Playlists"
            3 -> "Achievements"
            4 -> "Settings"
            else -> ""
        }
    }

    private fun getHistoryBasedTabTitle(position: Int): String {
        return when (position) {
            0 -> "History"
            1 -> "Favorites"
            2 -> "Playlists"
            3 -> "Achievements"
            4 -> "Settings"
            else -> ""
        }
    }

    private fun getFavoritesBasedTabTitle(position: Int): String {
        return when (position) {
            0 -> "Favorites"
            1 -> "History"
            2 -> "Playlists"
            3 -> "Achievements"
            4 -> "Settings"
            else -> ""
        }
    }

    private fun getPlaylistsBasedTabTitle(position: Int): String {
        return when (position) {
            0 -> "Playlists"
            1 -> "Favorites"
            2 -> "History"
            3 -> "Achievements"
            4 -> "Settings"
            else -> ""
        }
    }

    private fun getAchievementsBasedTabTitle(position: Int): String {
        return when (position) {
            0 -> "Achievements"
            1 -> "History"
            2 -> "Favorites"
            3 -> "Playlists"
            4 -> "Settings"
            else -> ""
        }
    }



    private fun getRecommendationsBasedTabTitle(position: Int): String {
        return when (position) {
            0 -> "For You"
            1 -> "Favorites"
            2 -> "Playlists"
            3 -> "Achievements"
            4 -> "Settings"
            else -> ""
        }
    }

    private fun getTrendingBasedTabTitle(position: Int): String {
        return when (position) {
            0 -> "Trending"
            1 -> "Favorites"
            2 -> "Playlists"
            3 -> "Achievements"
            4 -> "Settings"
            else -> ""
        }
    }

    private fun getCategoriesBasedTabTitle(position: Int): String {
        return when (position) {
            0 -> "Categories"
            1 -> "Favorites"
            2 -> "Playlists"
            3 -> "Achievements"
            4 -> "Settings"
            else -> ""
        }
    }
}
