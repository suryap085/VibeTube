package com.video.vibetube.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.textview.MaterialTextView
import com.video.vibetube.R
import com.video.vibetube.models.ChannelVideosSection
import com.video.vibetube.models.Video

class ChannelsSectionAdapter(
    private val channelSections: List<ChannelVideosSection>,
    private val onVideoClick: (channelIndex: Int, video: Video) -> Unit,
    private val onLoadMore: (channelIndex: Int) -> Unit,
    private val onSeeMore: (channelId: String, channelTitle: String) -> Unit,
) : RecyclerView.Adapter<ChannelsSectionAdapter.ChannelSectionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelSectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel_section, parent, false)
        return ChannelSectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelSectionViewHolder, position: Int) {
        holder.bind(channelSections[position], position)
    }

    override fun getItemCount(): Int = channelSections.size

    inner class ChannelSectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val channelTitleTextView: MaterialTextView =
            itemView.findViewById(R.id.channelTitleTextView)
        private val shimmerChannelTitle: ShimmerFrameLayout =
            itemView.findViewById(R.id.shimmerChannelTitle)
        private val shimmerVideos: ShimmerFrameLayout = itemView.findViewById(R.id.shimmerVideos)
        private val recyclerViewVideos: RecyclerView =
            itemView.findViewById(R.id.recyclerViewVideos)
        private val seeMoreButton: ImageView =
            itemView.findViewById(R.id.seeMoreButton)

        private val videoAdapter = HorizontalVideoAdapter()

        init {
            recyclerViewVideos.layoutManager =
                LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
            recyclerViewVideos.adapter = videoAdapter

            recyclerViewVideos.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(rv, dx, dy)
                    val lm = rv.layoutManager as LinearLayoutManager
                    val visibleCount = lm.childCount
                    val totalCount = lm.itemCount
                    val firstVisible = lm.findFirstVisibleItemPosition()
                    val position = bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) return

                    val section = channelSections[position]
                    if (!section.isLoading && section.nextPageToken.isNotEmpty()) {
                        if (visibleCount + firstVisible >= totalCount - 3) {
                            onLoadMore(position)
                        }
                    }
                }
            })
        }

        fun bind(section: ChannelVideosSection, position: Int) {
            if (section.isLoading && section.videos.isEmpty()) {
                // Show shimmer, hide actual views
                shimmerChannelTitle.visibility = View.VISIBLE
                shimmerChannelTitle.startShimmer()
                channelTitleTextView.visibility = View.GONE

                shimmerVideos.visibility = View.VISIBLE
                shimmerVideos.startShimmer()
                recyclerViewVideos.visibility = View.GONE
                seeMoreButton.visibility = View.GONE
            } else {
                // Hide shimmer, show actual views
                shimmerChannelTitle.stopShimmer()
                shimmerChannelTitle.visibility = View.GONE
                channelTitleTextView.visibility = View.VISIBLE
                channelTitleTextView.text = section.channelTitle

                shimmerVideos.stopShimmer()
                shimmerVideos.visibility = View.GONE
                recyclerViewVideos.visibility = View.VISIBLE

                seeMoreButton.visibility = View.VISIBLE
                seeMoreButton.setOnClickListener {
                    onSeeMore(section.channelId, section.channelTitle)
                }

                /*if (videoAdapter.itemCount == 0) {
                    videoAdapter.setVideos(section.videos)
                }*/
                
                // Save scroll position
                val firstVisible = (recyclerViewVideos.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()

                videoAdapter.setVideos(section.videos)

                // Restore scroll position
                recyclerViewVideos.scrollToPosition(if (firstVisible == RecyclerView.NO_POSITION) 0 else firstVisible)
            }

            videoAdapter.onVideoClick = { video ->
                // Forward click events
                onVideoClick(position, video)
            }
        }

        fun appendVideos(newVideos: List<Video>) {
            videoAdapter.addVideos(newVideos)
        }
    }
}
