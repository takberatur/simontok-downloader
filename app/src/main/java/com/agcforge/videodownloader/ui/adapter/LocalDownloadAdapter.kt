package com.agcforge.videodownloader.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
<<<<<<< HEAD
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.LocalDownloadItem
import com.agcforge.videodownloader.helper.ads.BannerAdsHelper
import com.agcforge.videodownloader.helper.ads.NativeAdsHelper
=======
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
import com.agcforge.videodownloader.helper.BannerAdsHelper
import com.agcforge.videodownloader.helper.NativeAdsHelper
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
import com.agcforge.videodownloader.utils.LocalDownloadsScanner
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
<<<<<<< HEAD
=======
import com.google.android.material.button.MaterialButton
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
<<<<<<< HEAD
import java.io.File
=======
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444

class LocalDownloadAdapter(
    private val context: Context,
    private val onOpenClick: (LocalDownloadItem) -> Unit,
<<<<<<< HEAD
    private val onConvertClick: (LocalDownloadItem) -> Unit,
=======
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
    private val onDeleteClick: (LocalDownloadItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

	enum class AdSlotType {
		NATIVE_SMALL,
		NATIVE_MEDIUM,
		BANNER
	}

	data class AdInsertionConfig(
		val startAfter: Int = 3,
		val interval: Int = 6,
		val maxAds: Int? = null
	)

	private var nativeAdsHelper: NativeAdsHelper? = null
	private var bannerAdsHelper: BannerAdsHelper? = null
	private var adSlotType: AdSlotType = AdSlotType.NATIVE_SMALL
	private var adConfig: AdInsertionConfig? = null

<<<<<<< HEAD
	@SuppressLint("NotifyDataSetChanged")
    fun enableAds(
=======
	fun enableAds(
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
		nativeAdsHelper: NativeAdsHelper? = null,
		bannerAdsHelper: BannerAdsHelper? = null,
		slotType: AdSlotType = AdSlotType.NATIVE_SMALL,
		config: AdInsertionConfig = AdInsertionConfig()
	) {
		this.nativeAdsHelper = nativeAdsHelper
		this.bannerAdsHelper = bannerAdsHelper
		this.adSlotType = slotType
		this.adConfig = config
		notifyDataSetChanged()
	}

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

<<<<<<< HEAD
    @SuppressLint("NotifyDataSetChanged")
=======
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
    fun addItems(newItems: List<LocalDownloadItem>) {
		val startPosition = items.size
		items.addAll(newItems)
		if (isAdsEnabled()) {
			notifyDataSetChanged()
		} else {
			notifyItemRangeInserted(startPosition, newItems.size)
		}
    }

<<<<<<< HEAD
    @SuppressLint("NotifyDataSetChanged")
=======
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
    fun clearItems() {
        val itemCount = items.size
        items.clear()
        loadingThumbnails.clear()

        thumbnailJobs.values.forEach { it.cancel() }
        thumbnailJobs.clear()

		if (isAdsEnabled()) {
			notifyDataSetChanged()
		} else {
			notifyItemRangeRemoved(0, itemCount)
		}
    }

<<<<<<< HEAD
    @SuppressLint("NotifyDataSetChanged")
=======
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
    fun removeItem(itemId: Long) {
        val index = items.indexOfFirst { it.id == itemId }
        if (index != -1) {
            items.removeAt(index)
            loadingThumbnails.remove(itemId)

            thumbnailJobs[itemId]?.cancel()
            thumbnailJobs.remove(itemId)

			if (isAdsEnabled()) {
				notifyDataSetChanged()
			} else {
				notifyItemRemoved(index)
			}
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

			val adapterPos = if (isAdsEnabled()) adapterPositionForContentIndex(index) else index
			notifyItemChanged(adapterPos)
        }
    }

    fun getItems(): List<LocalDownloadItem> = items.toList()

    fun getItemAt(position: Int): LocalDownloadItem? {
		if (!isAdsEnabled()) {
			return if (position in 0 until items.size) items[position] else null
		}
		if (position !in 0 until itemCount) return null
		if (isAdPosition(position)) return null
		val contentIndex = contentIndexForAdapterPosition(position)
		return items.getOrNull(contentIndex)
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

	private fun isAdsEnabled(): Boolean {
		val cfg = adConfig ?: return false
		return cfg.interval > 0 && cfg.startAfter > 0 && (nativeAdsHelper != null || bannerAdsHelper != null)
	}

	private fun maxAdsForSize(contentSize: Int): Int {
		val cfg = adConfig ?: return 0
		if (!isAdsEnabled()) return 0
		if (contentSize < cfg.startAfter) return 0
		val computed = 1 + ((contentSize - cfg.startAfter) / cfg.interval)
		val capped = cfg.maxAds?.let { computed.coerceAtMost(it.coerceAtLeast(0)) } ?: computed
		return capped.coerceAtLeast(0)
	}

	private fun isAdPosition(adapterPosition: Int): Boolean {
		val cfg = adConfig ?: return false
		if (!isAdsEnabled()) return false
		if (adapterPosition < cfg.startAfter) return false
		val step = cfg.interval + 1
		val diff = adapterPosition - cfg.startAfter
		if (diff % step != 0) return false
		val slotIndex = diff / step
		return slotIndex in 0 until maxAdsForSize(items.size)
	}

	private fun adsBeforeAdapterPosition(adapterPosition: Int): Int {
		val cfg = adConfig ?: return 0
		if (!isAdsEnabled()) return 0
		if (adapterPosition <= cfg.startAfter) return 0
		val step = cfg.interval + 1
		val diff = adapterPosition - cfg.startAfter - 1
		val count = 1 + (diff / step)
		return count.coerceAtMost(maxAdsForSize(items.size))
	}

	private fun contentIndexForAdapterPosition(adapterPosition: Int): Int {
		val adsBefore = adsBeforeAdapterPosition(adapterPosition)
		return adapterPosition - adsBefore
	}

	private fun adapterPositionForContentIndex(contentIndex: Int): Int {
		val cfg = adConfig ?: return contentIndex
		if (!isAdsEnabled()) return contentIndex
		val n = contentIndex + 1
		if (n <= cfg.startAfter) return contentIndex
		val adsBefore = 1 + ((n - cfg.startAfter - 1) / cfg.interval)
		val capped = adsBefore.coerceAtMost(maxAdsForSize(items.size))
		return contentIndex + capped
	}

	override fun getItemViewType(position: Int): Int {
		return if (isAdsEnabled() && isAdPosition(position)) VIEW_TYPE_AD else VIEW_TYPE_ITEM
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return if (viewType == VIEW_TYPE_AD) {
			val view = LayoutInflater.from(parent.context)
				.inflate(R.layout.item_recycler_ad_container, parent, false)
			AdViewHolder(view)
		} else {
			val view = LayoutInflater.from(parent.context)
				.inflate(R.layout.item_local_download, parent, false)
<<<<<<< HEAD
			ItemViewHolder(view, onOpenClick, onConvertClick, onDeleteClick, this)
=======
			ItemViewHolder(view, onOpenClick, onDeleteClick, this)
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
		}
    }

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		if (holder is AdViewHolder) {
			holder.bind(nativeAdsHelper, bannerAdsHelper, adSlotType)
			return
		}
		val contentIndex = if (isAdsEnabled()) contentIndexForAdapterPosition(position) else position
		val item = items[contentIndex]
		(holder as ItemViewHolder).bind(item, contentIndex)
    }

	override fun getItemCount(): Int {
		val contentSize = items.size
		return contentSize + maxAdsForSize(contentSize)
	}

	override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
		super.onViewRecycled(holder)
		if (holder is AdViewHolder) return
		val itemHolder = holder as ItemViewHolder
		Glide.with(context).clear(itemHolder.ivThumbnail)

		val item = itemHolder.getItem()
        item?.let {
            thumbnailJobs[it.id]?.cancel()
            thumbnailJobs.remove(it.id)
            loadingThumbnails.remove(it.id)
        }
    }

    class ItemViewHolder(
        itemView: View,
        private val onOpenClick: (LocalDownloadItem) -> Unit,
<<<<<<< HEAD
        private val onConvertClick: (LocalDownloadItem) -> Unit,
=======
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
        private val onDeleteClick: (LocalDownloadItem) -> Unit,
        private val adapter: LocalDownloadAdapter
    ) : RecyclerView.ViewHolder(itemView) {

        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
        private val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val ivTypeIcon: ImageView = itemView.findViewById(R.id.ivTypeIcon)
<<<<<<< HEAD
        private val btnMore: ImageButton = itemView.findViewById(R.id.btnMore)
=======
        private val btnOpen: MaterialButton = itemView.findViewById(R.id.btnOpen)
        private val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
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

<<<<<<< HEAD
            itemView.setOnClickListener { showPopupMenu(it, item) }
            btnMore.setOnClickListener { showPopupMenu(it, item) }
        }

        private fun showPopupMenu(view: View, item: LocalDownloadItem) {
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.menu_history_item)

            try {
                val fields = popup.javaClass.declaredFields
                for (field in fields) {
                    if ("mPopup" == field.name) {
                        field.isAccessible = true
                        val menuPopupHelper = field.get(popup)
                        val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                        val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                        setForceIcons.invoke(menuPopupHelper, true)
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val menu = popup.menu
            if (item.isAudio()) {
                menu.findItem(R.id.action_convert_to_mp3).isVisible = false
            } else {
                menu.findItem(R.id.action_convert_to_mp3).isVisible = true
            }

            val deleteItem = menu.findItem(R.id.action_delete)

            deleteItem?.let {
                val s = SpannableString(it.title)
                s.setSpan(ForegroundColorSpan(Color.RED), 0, s.length, 0)
                it.title = s

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.iconTintList = ColorStateList.valueOf(Color.RED)
                }
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.action_open -> {
                        onOpenClick(item)
                        true
                    }
                    R.id.action_convert_to_mp3 -> {
                        onConvertClick(item)
                        true
                    }
                    R.id.action_share -> {
                        shareFile(item)
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
=======
            itemView.setOnClickListener { onOpenClick(item) }
            btnOpen.setOnClickListener { onOpenClick(item) }
            btnDelete.setOnClickListener { onDeleteClick(item) }
            btnDelete.visibility = if (item.filePath != null) View.VISIBLE else View.GONE
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
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
<<<<<<< HEAD

        private fun shareFile(item: LocalDownloadItem) {
            if(!item.isVideo() && !item.isAudio()) return

            if(item.filePath?.isNotEmpty() == false) return

            val file = File(item.filePath)
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(
                    itemView.context,
                    itemView.context.packageName + ".fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = if (item.isVideo()) "video/*" else "audio/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                itemView.context.startActivity(Intent.createChooser(shareIntent, itemView.context.getString(R.string.share)))
            }
        }

=======
>>>>>>> d9441acea1800f24d98ca8ff996508019a679444
    }

	class AdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		private val container: ViewGroup = itemView.findViewById(R.id.adContainer)
		private var isLoaded = false

		fun bind(nativeAdsHelper: NativeAdsHelper?, bannerAdsHelper: BannerAdsHelper?, slotType: AdSlotType) {
			if (isLoaded) return
			isLoaded = true
			when (slotType) {
				AdSlotType.BANNER -> bannerAdsHelper?.loadAndAttachBanner(container)
				AdSlotType.NATIVE_MEDIUM -> nativeAdsHelper?.loadAndAttachNativeAd(container, NativeAdsHelper.NativeAdSize.MEDIUM)
				AdSlotType.NATIVE_SMALL -> nativeAdsHelper?.loadAndAttachNativeAd(container, NativeAdsHelper.NativeAdSize.SMALL)
			}
		}
	}

	companion object {
		private const val VIEW_TYPE_ITEM = 0
		private const val VIEW_TYPE_AD = 1
	}

}

@SuppressLint("DefaultLocale")
private fun Long.formatDuration(): String {
    val minutes = this / 60000
    val seconds = (this % 60000) / 1000
    return String.format("%02d:%02d", minutes, seconds)
}
