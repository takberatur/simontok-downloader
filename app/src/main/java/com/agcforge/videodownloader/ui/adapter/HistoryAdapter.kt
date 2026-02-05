package com.agcforge.videodownloader.ui.adapter

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
import com.agcforge.videodownloader.helper.BannerAdsHelper
import com.agcforge.videodownloader.helper.NativeAdsHelper
import com.agcforge.videodownloader.utils.formatDate
import com.agcforge.videodownloader.utils.formatFileSize
import com.agcforge.videodownloader.utils.loadImage

class HistoryAdapter(
	private val onCopyClick: (DownloadTask) -> Unit,
	private val onShareClick: (DownloadTask) -> Unit,
	private val onDeleteClick: (DownloadTask) -> Unit
) : ListAdapter<HistoryAdapter.HistoryListItem, RecyclerView.ViewHolder>(Diff()) {

	init {
		setHasStableIds(true)
	}

	enum class AdSlotType {
		NATIVE_SMALL,
		NATIVE_MEDIUM,
		BANNER
	}

	data class AdInsertionConfig(
		val startAfter: Int = 3,
		val interval: Int = 5,
		val maxAds: Int? = null
	)

	sealed interface HistoryListItem {
		val stableId: Long
	}

	data class TaskItem(val task: DownloadTask) : HistoryListItem {
		override val stableId: Long = task.id.hashCode().toLong()
	}

	data class AdItem(val slotId: Long, val slotType: AdSlotType) : HistoryListItem {
		override val stableId: Long = (slotId shl 3) + slotType.ordinal
	}

	private var lastTasks: List<DownloadTask> = emptyList()
	private var nativeAdsHelper: NativeAdsHelper? = null
	private var bannerAdsHelper: BannerAdsHelper? = null
	private var adSlotType: AdSlotType = AdSlotType.NATIVE_SMALL
	private var adConfig: AdInsertionConfig? = null

	fun enableAds(
		nativeAdsHelper: NativeAdsHelper? = null,
		bannerAdsHelper: BannerAdsHelper? = null,
		slotType: AdSlotType = AdSlotType.NATIVE_SMALL,
		config: AdInsertionConfig = AdInsertionConfig()
	) {
		this.nativeAdsHelper = nativeAdsHelper
		this.bannerAdsHelper = bannerAdsHelper
		this.adSlotType = slotType
		this.adConfig = config
		submitTasks(lastTasks)
	}

	fun submitTasks(tasks: List<DownloadTask>) {
		lastTasks = tasks
		submitList(buildDisplayList(tasks))
	}

	private fun buildDisplayList(tasks: List<DownloadTask>): List<HistoryListItem> {
		val cfg = adConfig
		if (cfg == null || cfg.interval <= 0 || cfg.startAfter <= 0 || (nativeAdsHelper == null && bannerAdsHelper == null)) {
			return tasks.map { TaskItem(it) }
		}
		val maxAds = cfg.maxAds?.coerceAtLeast(0)
		val out = ArrayList<HistoryListItem>()
		var slot = 0L
		for (i in tasks.indices) {
			out.add(TaskItem(tasks[i]))
			val n = i + 1
			if (n >= cfg.startAfter) {
				val offset = n - cfg.startAfter
				if (offset % cfg.interval == 0) {
					if (maxAds == null || slot < maxAds) {
						slot += 1
						out.add(AdItem(slotId = slot, slotType = adSlotType))
					}
				}
			}
		}
		return out
	}

	override fun getItemId(position: Int): Long {
		return getItem(position).stableId
	}

	override fun getItemViewType(position: Int): Int {
		return when (getItem(position)) {
			is TaskItem -> VIEW_TYPE_TASK
			is AdItem -> VIEW_TYPE_AD
		}
	}

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
		return when (viewType) {
			VIEW_TYPE_AD -> {
				val view = LayoutInflater.from(parent.context)
					.inflate(R.layout.item_recycler_ad_container, parent, false)
				AdViewHolder(view)
			}
			else -> {
				val binding = ItemDownloadHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
				TaskViewHolder(binding, onCopyClick, onShareClick, onDeleteClick)
			}
		}
	}

	override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
		when (val item = getItem(position)) {
			is TaskItem -> (holder as TaskViewHolder).bind(item.task)
			is AdItem -> (holder as AdViewHolder).bind(nativeAdsHelper, bannerAdsHelper, item.slotType)
		}
	}

	class TaskViewHolder(
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

	class Diff : DiffUtil.ItemCallback<HistoryListItem>() {
		override fun areItemsTheSame(oldItem: HistoryListItem, newItem: HistoryListItem): Boolean {
			return oldItem.stableId == newItem.stableId
		}

		override fun areContentsTheSame(oldItem: HistoryListItem, newItem: HistoryListItem): Boolean {
			return oldItem == newItem
		}
	}

	companion object {
		private const val VIEW_TYPE_TASK = 0
		private const val VIEW_TYPE_AD = 1
	}
}
