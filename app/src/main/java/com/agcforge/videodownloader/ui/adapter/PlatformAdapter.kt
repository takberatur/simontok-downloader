package com.agcforge.videodownloader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.Platform
import com.agcforge.videodownloader.utils.loadImage
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView

class PlatformAdapter(
    private val onItemClick: (Platform) -> Unit
) : ListAdapter<Platform, PlatformAdapter.PlatformViewHolder>(PlatformDiffCallback()) {

    private var selectedItem: Platform? = null

    fun setSelection(platform: Platform) {
        val previousItem = selectedItem
        selectedItem = platform

        previousItem?.let {
            val previousPosition = currentList.indexOf(it)
            if (previousPosition != -1) {
                notifyItemChanged(previousPosition)
            }
        }

        val newPosition = currentList.indexOf(platform)
        if (newPosition != -1) {
            notifyItemChanged(newPosition)
        }
    }

    fun clearSelection() {
        val previousItem = selectedItem
        selectedItem = null
        previousItem?.let {
            val position = currentList.indexOf(it)
            if (position != -1) {
                notifyItemChanged(position)
            }
        }
    }

    fun getSelectedItem(): Platform? = selectedItem

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlatformViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_platform, parent, false)
        return PlatformViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: PlatformViewHolder, position: Int) {
        val platform = getItem(position)
        val isSelected = selectedItem?.id == platform.id

        holder.itemView.isSelected = selectedItem?.id == platform.id
        holder.bind(platform, isSelected)
    }

    inner class PlatformViewHolder(
        itemView: View,
        private val onItemClick: (Platform) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardPlatform)
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivPlatformThumbnail)
        private val tvName: TextView = itemView.findViewById(R.id.tvPlatformName)
        private val tvType: TextView = itemView.findViewById(R.id.tvPlatformType)
        private val ivPremium: ImageView = itemView.findViewById(R.id.ivPremiumBadge)

        private val ivBadgeMp3: ImageView = itemView.findViewById(R.id.ivBadgeMp3)

        private val selectedColor = ContextCompat.getColor(itemView.context, R.color.primary)
        private val defaultColor = ContextCompat.getColor(itemView.context, R.color.surface)

        fun bind(platform: Platform, isSelected: Boolean) {
            if (isSelected) {
                cardView.setCardBackgroundColor(selectedColor)
                cardView.strokeWidth = 4
                cardView.strokeColor = ContextCompat.getColor(itemView.context, R.color.text_primary)
                tvName.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
                tvType.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
            } else {
                cardView.setCardBackgroundColor(defaultColor)
                cardView.strokeWidth = 1
                tvName.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
                tvType.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_secondary))
            }

            if (platform.category === "audio" || platform.type.endsWith("to-mp3")) {
                Glide.with(itemView.context)
                    .load(platform.thumbnailUrl)
                    .placeholder(R.drawable.ic_video)
                    .error(R.drawable.ic_error_rectangle)
                    .into(ivThumbnail)
            } else {
                Glide.with(itemView.context)
                    .load(platform.thumbnailUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_video)
                    .error(R.drawable.ic_error_rectangle)
                    .into(ivThumbnail)
            }

            tvName.text = platform.name
            tvType.text = platform.type.uppercase()
            ivPremium.visibility = if (platform.isPremium) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onItemClick(platform)
                updateSelection(platform)
            }
        }

        private fun updateSelection(platform: Platform) {
            val previousSelected = this@PlatformAdapter.selectedItem

            if (previousSelected?.id != platform.id) {
                this@PlatformAdapter.setSelection(platform)
            } else {
                // this@PlatformAdapter.clearSelection()
            }
        }
    }

    private class PlatformDiffCallback : DiffUtil.ItemCallback<Platform>() {
        override fun areItemsTheSame(oldItem: Platform, newItem: Platform): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Platform, newItem: Platform): Boolean {
            return oldItem == newItem
        }
    }
}