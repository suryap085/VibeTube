package com.video.vibetube.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.video.vibetube.R
import com.video.vibetube.models.CategorySection
import com.video.vibetube.utils.UserDataManager

/**
 * YouTube Policy Compliant Categories Adapter
 * 
 * This adapter displays content categories while ensuring compliance
 * with YouTube's terms of service:
 * - Only displays predefined categories and channels
 * - Respects user consent and privacy settings
 * - No unauthorized data collection or sharing
 * - Maintains proper attribution to original YouTube content
 */
class CategoriesAdapter(
    private val categories: MutableList<CategorySection>,
    private val onCategoryClick: (CategorySection) -> Unit,
    private val onChannelClick: (String, String) -> Unit,
    private val lifecycleOwner: LifecycleOwner,
    private val userDataManager: UserDataManager
) : RecyclerView.Adapter<CategoriesAdapter.CategoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.categoryCard)
        private val iconImageView: ImageView = itemView.findViewById(R.id.categoryIcon)
        private val nameTextView: TextView = itemView.findViewById(R.id.categoryName)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.categoryDescription)
        private val channelCountTextView: TextView = itemView.findViewById(R.id.channelCount)

        fun bind(category: CategorySection) {
            // Set category information
            nameTextView.text = category.name
            descriptionTextView.text = category.description

            // Set channel count with more accurate description
            val channelCount = category.channels.size
            channelCountTextView.text = if (channelCount == 1) {
                "1 channel available"
            } else {
                "Best of $channelCount channels"
            }
            
            // Set category icon
            iconImageView.setImageResource(category.iconRes)
            
            // Set category color theme
            val categoryColor = ContextCompat.getColor(itemView.context, category.colorRes)
            iconImageView.setColorFilter(categoryColor)
            cardView.strokeColor = categoryColor
            
            // Set click listener
            cardView.setOnClickListener {
                onCategoryClick(category)
            }
        }
    }
}
