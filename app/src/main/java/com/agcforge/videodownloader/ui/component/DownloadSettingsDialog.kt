package com.agcforge.videodownloader.ui.component

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.Platform
import com.agcforge.videodownloader.databinding.DialogDownloadSettingsBinding
import com.agcforge.videodownloader.utils.loadImage
import com.google.android.material.bottomsheet.BottomSheetDialog

class DownloadSettingsDialog private constructor(
    context: Context,
    private val url: String,
    private val platforms: List<Platform>,
    private val onSubmit: (selectedType: String, selectedPlatform: Platform) -> Unit
) : BottomSheetDialog(context) {

    private lateinit var binding: DialogDownloadSettingsBinding
    private var selectedType: String = "video" // Default: video
    private var selectedPlatform: Platform? = null
    private lateinit var platformAdapter: PlatformSelectionAdapter

    companion object {
        fun create(
            context: Context,
            url: String,
            platforms: List<Platform>,
            onSubmit: (type: String, platform: Platform) -> Unit
        ): DownloadSettingsDialog {
            return DownloadSettingsDialog(context, url, platforms, onSubmit)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogDownloadSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupListeners()
        updatePlatformList()
    }

    private fun setupUI() {
        binding.tvUrl.text = url

        // Setup RecyclerView for platforms
        platformAdapter = PlatformSelectionAdapter { platform ->
            selectedPlatform = platform
            updateSubmitButton()
        }

        binding.rvPlatforms.apply {
            adapter = platformAdapter
            layoutManager = LinearLayoutManager(context)
        }

        // Initially select video type
        binding.rbVideo.isChecked = true
        selectedType = "video"
    }

    private fun setupListeners() {
        // Type selection
        binding.rgType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbVideo -> {
                    selectedType = "video"
                    updatePlatformList()
                }
                R.id.rbAudio -> {
                    selectedType = "audio"
                    updatePlatformList()
                }
            }
            selectedPlatform = null
            updateSubmitButton()
        }

        // Submit button
        binding.btnSubmit.setOnClickListener {
            val platform = selectedPlatform
            if (platform != null) {
                onSubmit(selectedType, platform)
                dismiss()
            }
        }

        // Cancel button
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun updatePlatformList() {
        val filteredPlatforms = platforms.filter { platform ->
            when (selectedType) {
                "video" -> {
                    // Video: category == "video" OR type NOT ending with "to-mp3"
                    platform.category.equals("video", ignoreCase = true) ||
                            !platform.type.endsWith("to-mp3", ignoreCase = true)
                }
                "audio" -> {
                    // Audio: category == "audio" OR type ending with "to-mp3"
                    platform.category.equals("audio", ignoreCase = true) ||
                            platform.type.endsWith("to-mp3", ignoreCase = true)
                }
                else -> false
            }
        }

        platformAdapter.submitList(filteredPlatforms)

        // Show empty state if no platforms
        if (filteredPlatforms.isEmpty()) {
            binding.tvNoPlatforms.visibility = View.VISIBLE
            binding.rvPlatforms.visibility = View.GONE
        } else {
            binding.tvNoPlatforms.visibility = View.GONE
            binding.rvPlatforms.visibility = View.VISIBLE
        }
    }

    private fun updateSubmitButton() {
        binding.btnSubmit.isEnabled = selectedPlatform != null
    }

    // Platform Adapter
    private inner class PlatformSelectionAdapter(
        private val onPlatformClick: (Platform) -> Unit
    ) : RecyclerView.Adapter<PlatformSelectionAdapter.ViewHolder>() {

        private var platforms = listOf<Platform>()
        private var selectedPosition = -1

        fun submitList(newPlatforms: List<Platform>) {
            platforms = newPlatforms
            selectedPosition = -1
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_platform_selection, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(platforms[position], position == selectedPosition)
        }

        override fun getItemCount() = platforms.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivPlatformThumbnail)
            private val tvName: TextView = itemView.findViewById(R.id.tvPlatformName)
            private val tvType: TextView = itemView.findViewById(R.id.tvPlatformType)
            private val rbSelect: RadioButton = itemView.findViewById(R.id.rbSelect)
            private val ivPremium: ImageView = itemView.findViewById(R.id.ivPremiumBadge)

            fun bind(platform: Platform, isSelected: Boolean) {
                ivThumbnail.loadImage(platform.thumbnailUrl)
                tvName.text = platform.name
                tvType.text = platform.type.uppercase()
                rbSelect.isChecked = isSelected
                ivPremium.visibility = if (platform.isPremium) View.VISIBLE else View.GONE

                itemView.setOnClickListener {
                    val oldPosition = selectedPosition
                    selectedPosition = adapterPosition
                    notifyItemChanged(oldPosition)
                    notifyItemChanged(selectedPosition)
                    onPlatformClick(platform)
                }
            }
        }
    }
}