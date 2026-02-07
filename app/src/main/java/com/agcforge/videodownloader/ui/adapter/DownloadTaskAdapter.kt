package com.agcforge.videodownloader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.DownloadTask
import com.agcforge.videodownloader.utils.formatDate
import com.agcforge.videodownloader.utils.formatFileSize
import com.agcforge.videodownloader.utils.loadImage

class DownloadTaskAdapter(
    private val onItemClick: (DownloadTask) -> Unit,
    private val onDownloadClick: (DownloadTask) -> Unit
) : ListAdapter<DownloadTask, DownloadTaskAdapter.DownloadViewHolder>(DownloadTaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DownloadViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download_task, parent, false)
        return DownloadViewHolder(view, onItemClick, onDownloadClick)
    }

    override fun onBindViewHolder(holder: DownloadViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DownloadViewHolder(
        itemView: View,
        private val onItemClick: (DownloadTask) -> Unit,
        private val onDownloadClick: (DownloadTask) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvPlatform: TextView = itemView.findViewById(R.id.tvPlatform)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val btnDownload: View = itemView.findViewById(R.id.btnDownload)

        fun bind(task: DownloadTask) {
            ivThumbnail.loadImage(task.thumbnailUrl)
            tvTitle.text = task.title ?: "Unknown Title"
            tvPlatform.text = task.platformType.uppercase()
            tvDuration.text = task.getFormattedDuration()
            tvFileSize.text = task.fileSize?.formatFileSize() ?: "N/A"
            tvStatus.text = task.status.uppercase()
            tvDate.text = task.createdAt.formatDate()

            // Status color
            val statusColor = when (task.status.lowercase()) {
                "completed" -> android.R.color.holo_green_dark
                "failed" -> android.R.color.holo_red_dark
                "processing" -> android.R.color.holo_orange_dark
                else -> android.R.color.darker_gray
            }
            tvStatus.setTextColor(itemView.context.getColor(statusColor))

            itemView.setOnClickListener { onItemClick(task) }
            btnDownload.setOnClickListener { onDownloadClick(task) }
        }
    }

    private class DownloadTaskDiffCallback : DiffUtil.ItemCallback<DownloadTask>() {
        override fun areItemsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
            return oldItem == newItem
        }
    }
}