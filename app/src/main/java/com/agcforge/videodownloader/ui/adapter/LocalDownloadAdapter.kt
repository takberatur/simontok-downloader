package com.agcforge.videodownloader.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.LocalDownloadItem
import com.agcforge.videodownloader.utils.LocalDownloadsScanner
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

class LocalDownloadAdapter(
    private val context: Context,
    private val onOpenClick: (LocalDownloadItem) -> Unit,
    private val onDeleteClick: (LocalDownloadItem) -> Unit
) : RecyclerView.Adapter<LocalDownloadAdapter.ViewHolder>() {

    private val items = mutableListOf<LocalDownloadItem>()
    private val loadingThumbnails = mutableSetOf<Long>()
    private val thumbnailJobs = mutableMapOf<Long, Job>()


    private val glideOptions = RequestOptions()
        .transform(RoundedCorners(12))
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .override(300, 300)
        .centerCrop()
        .placeholder(R.drawable.ic_media_play)
        .error(R.drawable.ic_media_play)
        .frame(1000000)

    fun addItems(newItems: List<LocalDownloadItem>) {
        val startPosition = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(startPosition, newItems.size)
    }

    fun clearItems() {
        val itemCount = items.size
        items.clear()
        loadingThumbnails.clear()

        thumbnailJobs.values.forEach { it.cancel() }
        thumbnailJobs.clear()

        notifyItemRangeRemoved(0, itemCount)
    }

    fun removeItem(itemId: Long) {
        val index = items.indexOfFirst { it.id == itemId }
        if (index != -1) {
            items.removeAt(index)
            loadingThumbnails.remove(itemId)

            thumbnailJobs[itemId]?.cancel()
            thumbnailJobs.remove(itemId)

            notifyItemRemoved(index)
        }
    }

    fun updateItemThumbnail(itemId: Long, thumbnail: ByteArray) {
        val index = items.indexOfFirst { it.id == itemId }
        if (index != -1) {
            val item = items[index]
            val updatedItem = item.copy(thumbnail = thumbnail)
            items[index] = updatedItem
            loadingThumbnails.remove(itemId)

            LocalDownloadsScanner.ThumbnailCache.put(itemId, thumbnail)

            notifyItemChanged(index)
        }
    }

    fun getItems(): List<LocalDownloadItem> = items.toList()

    fun getItemAt(position: Int): LocalDownloadItem? {
        return if (position in 0 until items.size) items[position] else null
    }

    fun isThumbnailLoading(itemId: Long): Boolean = loadingThumbnails.contains(itemId)

    fun markThumbnailLoading(itemId: Long) {
        loadingThumbnails.add(itemId)
    }

    fun markThumbnailLoaded(itemId: Long) {
        loadingThumbnails.remove(itemId)
        thumbnailJobs.remove(itemId)
    }

    private suspend fun loadThumbnailFromStorage(item: LocalDownloadItem): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                LocalDownloadsScanner
                    .loadThumbnailForItem(context, item)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun loadThumbnailForVisibleItem(item: LocalDownloadItem, position: Int) {
        if (item.thumbnail != null || loadingThumbnails.contains(item.id)) {
            return
        }

        LocalDownloadsScanner.ThumbnailCache.get(item.id)?.let { cachedThumbnail ->
            updateItemThumbnail(item.id, cachedThumbnail)
            return
        }

        val job = CoroutineScope(Dispatchers.IO).launch {
            delay((position % 10) * 50L)

            val thumbnail = loadThumbnailFromStorage(item)

            withContext(Dispatchers.Main) {
                thumbnail?.let {
                    updateItemThumbnail(item.id, it)
                }
                markThumbnailLoaded(item.id)
            }
        }

        thumbnailJobs[item.id] = job
        markThumbnailLoading(item.id)
    }

    fun cancelAllThumbnailLoading() {
        thumbnailJobs.values.forEach { it.cancel() }
        thumbnailJobs.clear()
        loadingThumbnails.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_local_download, parent, false)
        return ViewHolder(view, onOpenClick, onDeleteClick, this)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, position)
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        Glide.with(context).clear(holder.ivThumbnail)

        val item = holder.getItem()
        item?.let {
            thumbnailJobs[it.id]?.cancel()
            thumbnailJobs.remove(it.id)
            loadingThumbnails.remove(it.id)
        }
    }

    class ViewHolder(
        itemView: View,
        private val onOpenClick: (LocalDownloadItem) -> Unit,
        private val onDeleteClick: (LocalDownloadItem) -> Unit,
        private val adapter: LocalDownloadAdapter
    ) : RecyclerView.ViewHolder(itemView) {

        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val ivTypeIcon: ImageView = itemView.findViewById(R.id.ivTypeIcon)
        private val btnOpen: MaterialButton = itemView.findViewById(R.id.btnOpen)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)

        private var currentItem: LocalDownloadItem? = null

        fun getItem(): LocalDownloadItem? = currentItem

        @SuppressLint("SetTextI18n")
        fun bind(item: LocalDownloadItem, position: Int) {
            currentItem = item

            tvFileName.text = item.displayName
            tvFileSize.text = item.getFormattedSize()
            tvDuration.text = item.getFormattedDuration()
            tvDate.text = item.getFormattedDate()

            ivTypeIcon.setImageResource(
                if (item.isVideo()) R.drawable.ic_video else R.drawable.ic_audiotrack
            )

            handleThumbnailWithGlide(item)

            itemView.setOnClickListener { onOpenClick(item) }
            btnOpen.setOnClickListener { onOpenClick(item) }
            btnDelete.setOnClickListener { onDeleteClick(item) }
            btnDelete.visibility = if (item.filePath != null) View.VISIBLE else View.GONE
        }

        private fun handleThumbnailWithBytes(item: LocalDownloadItem, position: Int) {
            when {
                item.thumbnail != null -> {
                    showThumbnailFromBytes(item.thumbnail!!)
                    progressBar.visibility = View.GONE
                }
                adapter.isThumbnailLoading(item.id) -> {
                    showPlaceholder(item)
                    progressBar.visibility = View.VISIBLE
                }
                else -> {
                    showPlaceholder(item)
                    progressBar.visibility = View.GONE

                    if (adapterPosition == position) {
                        loadThumbnailAsync(item, position)
                    }
                }
            }
        }

        private fun handleThumbnailWithGlide(item: LocalDownloadItem) {
            progressBar.visibility = View.VISIBLE

            Glide.with(itemView.context)
                .asBitmap() // Ambil sebagai bitmap
                .load(item.uri ?: item.filePath)
                .apply(adapter.glideOptions)
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                        progressBar.visibility = View.GONE
                        return false
                    }

                    override fun onResourceReady(resource: Bitmap, model: Any?, target: Target<Bitmap>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        progressBar.visibility = View.GONE
                        return false
                    }
                })
                .into(ivThumbnail)
        }

        private fun showThumbnailFromBytes(thumbnailBytes: ByteArray) {
            try {
                println("DEBUG [Adapter]: Showing thumbnail, size: ${thumbnailBytes.size} bytes")

                val bitmap = BitmapFactory.decodeStream(ByteArrayInputStream(thumbnailBytes))
                println("DEBUG [Adapter]: Bitmap decoded: ${bitmap.width}x${bitmap.height}, hasAlpha: ${bitmap.hasAlpha()}")

                // Coba tampilkan langsung tanpa Glide dulu untuk test
                ivThumbnail.post {
                    println("DEBUG [Adapter]: ImageView dimensions: ${ivThumbnail.width}x${ivThumbnail.height}")
                    println("DEBUG [Adapter]: ImageView visibility: ${ivThumbnail.visibility}")
                    println("DEBUG [Adapter]: ImageView scaleType: ${ivThumbnail.scaleType}")

                    // Test 1: Tampilkan langsung
                    ivThumbnail.setImageBitmap(bitmap)
                    ivThumbnail.invalidate()

                    // Tunggu 1 detik, lalu coba dengan Glide
                    ivThumbnail.postDelayed({
                        println("DEBUG [Adapter]: Trying with Glide...")
                        Glide.with(itemView.context)
                            .load(bitmap)
                            .apply(adapter.glideOptions)
                            .listener(object : RequestListener<Drawable> {
                                override fun onLoadFailed(
                                    e: GlideException?,
                                    model: Any?,
                                    target: Target<Drawable>?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    println("DEBUG [Adapter]: Glide load failed: ${e?.message}")
                                    e?.logRootCauses("DEBUG [Adapter]")
                                    return false
                                }

                                override fun onResourceReady(
                                    resource: Drawable?,
                                    model: Any?,
                                    target: Target<Drawable>?,
                                    dataSource: DataSource?,
                                    isFirstResource: Boolean
                                ): Boolean {
                                    println("DEBUG [Adapter]: Glide load successful, drawable: $resource")
                                    println("DEBUG [Adapter]: Drawable bounds: ${resource?.bounds}")
                                    return false
                                }
                            })
                            .into(ivThumbnail)
                    }, 1000)
                }

            } catch (e: Exception) {
                println("DEBUG [Adapter]: Error showing thumbnail: ${e.message}")
                showPlaceholder(currentItem ?: return)
            }
        }

        private fun showPlaceholder(item: LocalDownloadItem) {
            val placeholder = if (item.isVideo()) {
                R.drawable.ic_media_play
            } else {
                R.drawable.ic_media_play
            }

            Glide.with(itemView.context)
                .load(placeholder)
                .apply(adapter.glideOptions)
                .into(ivThumbnail)
        }

        private fun loadThumbnailAsync(item: LocalDownloadItem, position: Int) {
            adapter.markThumbnailLoading(item.id)

            progressBar.visibility = View.VISIBLE

            val job = CoroutineScope(Dispatchers.IO).launch {
                try {
                    delay((position % 5) * 100L)

                    val thumbnail = LocalDownloadsScanner.loadThumbnailForItem(
                        itemView.context,
                        item
                    )

                    withContext(Dispatchers.Main) {
                        if (adapterPosition == position && currentItem?.id == item.id) {
                            if (thumbnail != null) {
                                adapter.updateItemThumbnail(item.id, thumbnail)
                                progressBar.visibility = View.GONE
                            } else {
                                showPlaceholder(item)
                                progressBar.visibility = View.GONE
                            }
                            adapter.markThumbnailLoaded(item.id)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (adapterPosition == position && currentItem?.id == item.id) {
                            showPlaceholder(item)
                            progressBar.visibility = View.GONE
                            adapter.markThumbnailLoaded(item.id)
                        }
                    }
                }
            }
            adapter.thumbnailJobs[item.id] = job
        }
    }

}

@SuppressLint("DefaultLocale")
private fun Long.formatDuration(): String {
    val minutes = this / 60000
    val seconds = (this % 60000) / 1000
    return String.format("%02d:%02d", minutes, seconds)
}