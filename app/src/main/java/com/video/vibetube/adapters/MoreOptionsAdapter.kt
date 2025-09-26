package com.video.vibetube.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.video.vibetube.R

data class MoreOption(
    val iconResId: Int,
    val label: String,
    val position: Int
)

class MoreOptionsAdapter(
    private val options: List<MoreOption>,
    private val onOptionClick: (Int) -> Unit
) : RecyclerView.Adapter<MoreOptionsAdapter.OptionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_more_option, parent, false)
        return OptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        holder.bind(options[position])
    }

    override fun getItemCount(): Int = options.size

    inner class OptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        private val labelTextView: TextView = itemView.findViewById(R.id.labelTextView)

        fun bind(option: MoreOption) {
            labelTextView.text = option.label
            labelTextView.setCompoundDrawablesWithIntrinsicBounds(0, option.iconResId, 0, 0)
            cardView.setOnClickListener {
                onOptionClick(option.position)
            }
        }
    }
}