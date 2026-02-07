package com.agcforge.videodownloader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.DownloadFormat
import com.agcforge.videodownloader.utils.formatFileSize

class DownloadFormatAdapter(
    private val onFormatClick: (DownloadFormat) -> Unit
) : ListAdapter<DownloadFormat, DownloadFormatAdapter.FormatViewHolder>(FormatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download_format, parent, false)
        return FormatViewHolder(view, onFormatClick)
    }

    override fun onBindViewHolder(holder: FormatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FormatViewHolder(
        itemView: View,
        private val onFormatClick: (DownloadFormat) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvQuality: TextView = itemView.findViewById(R.id.tvQuality)
        private val tvFormat: TextView = itemView.findViewById(R.id.tvFormat)
        private val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)

        fun bind(format: DownloadFormat) {
            tvQuality.text = format.getQualityLabel()
            tvFormat.text = format.ext?.uppercase() ?: "MP4"
            tvFileSize.text = format.filesize?.formatFileSize() ?: "Unknown"

            itemView.setOnClickListener { onFormatClick(format) }
        }
    }

    private class FormatDiffCallback : DiffUtil.ItemCallback<DownloadFormat>() {
        override fun areItemsTheSame(oldItem: DownloadFormat, newItem: DownloadFormat): Boolean {
            return oldItem.formatId == newItem.formatId
        }

        override fun areContentsTheSame(oldItem: DownloadFormat, newItem: DownloadFormat): Boolean {
            return oldItem == newItem
        }
    }
}