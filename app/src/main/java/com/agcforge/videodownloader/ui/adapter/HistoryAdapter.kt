package com.agcforge.videodownloader.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.DownloadTask
import com.agcforge.videodownloader.databinding.ItemDownloadHistoryBinding
import com.agcforge.videodownloader.utils.formatDate
import com.agcforge.videodownloader.utils.formatFileSize
import com.agcforge.videodownloader.utils.loadImage


class HistoryAdapter(
    private val onCopyClick: (DownloadTask) -> Unit,
    private val onShareClick: (DownloadTask) -> Unit,
    private val onDeleteClick: (DownloadTask) -> Unit
) : ListAdapter<DownloadTask, HistoryAdapter.ViewHolder>(DownloadTaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDownloadHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onCopyClick, onShareClick, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    class ViewHolder(
        private val binding: ItemDownloadHistoryBinding,
        private val onCopyClick: (DownloadTask) -> Unit,
        private val onShareClick: (DownloadTask) -> Unit,
        private val onDeleteClick: (DownloadTask) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadTask) {
            binding.ivThumbnail.loadImage(item.thumbnailUrl)
            binding.tvTitle.text = item.title
            binding.tvPlatform.text = item.platformType.uppercase()
            binding.tvDuration.text = item.getFormattedDuration()
            binding.tvFileSize.text = item.fileSize?.formatFileSize() ?: "N/A"
            binding.tvStatus.text = item.status.uppercase()
            binding.tvDate.text = item.createdAt.formatDate()
            val statusColor = when (item.status.lowercase()) {
                "completed" -> android.R.color.holo_green_dark
                "failed" -> android.R.color.holo_red_dark
                "processing" -> android.R.color.holo_orange_dark
                else -> android.R.color.darker_gray
            }
            binding.tvStatus.setTextColor(binding.root.context.getColor(statusColor))

            binding.btnMore.setOnClickListener { showPopupMenu(it, item) }
        }

        private fun showPopupMenu(view: View, item: DownloadTask) {
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.menu_download_action)
            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_copy_url -> {
                        onCopyClick(item)
                        true
                    }
                    R.id.action_share -> {
                        onShareClick(item)
                        true
                    }
                    R.id.action_delete -> {
                        onDeleteClick(item)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    class DownloadTaskDiffCallback : DiffUtil.ItemCallback<DownloadTask>() {
        override fun areItemsTheSame(oldItem: DownloadTask, newItem: DownloadTask): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: DownloadTask,
            newItem: DownloadTask
        ): Boolean {
            return oldItem == newItem
        }
    }
}
