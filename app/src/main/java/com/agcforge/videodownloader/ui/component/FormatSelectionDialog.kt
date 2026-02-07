package com.agcforge.videodownloader.ui.component

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.DownloadFormat
import com.agcforge.videodownloader.data.model.DownloadTask
import com.agcforge.videodownloader.data.model.FormatMerger
import com.agcforge.videodownloader.ui.adapter.FormatSelectionAdapter
import com.bumptech.glide.Glide

class FormatSelectionDialog(
    context: Context,
    private val task: DownloadTask,
    private val onFormatSelected: (DownloadFormat) -> Unit
) : Dialog(context) {

    private lateinit var tvDialogTitle: TextView

    private lateinit var ivPlatformThumbnail: ImageView
    private lateinit var tvVideoTitle: TextView
    private lateinit var tvVideoDuration: TextView
    private lateinit var rvFormats: RecyclerView
    private lateinit var btnCancel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_format_selection, null)
        setContentView(view)

        // Initialize views
        initViews(view)

        // Setup dialog
        setupDialog()

        // Populate data
        populateData()
    }

    private fun initViews(view: android.view.View) {
        tvDialogTitle = view.findViewById(R.id.tvDialogTitle)
        ivPlatformThumbnail = view.findViewById(R.id.ivVideoThumbnail)
        tvVideoTitle = view.findViewById(R.id.tvVideoTitle)
        tvVideoDuration = view.findViewById(R.id.tvVideoDuration)
        rvFormats = view.findViewById(R.id.rvFormats)
        btnCancel = view.findViewById(R.id.btnCancel)
    }

    private fun setupDialog() {
        // Set dialog window attributes
        window?.apply {
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        // Setup RecyclerView
        rvFormats.apply {
            layoutManager = LinearLayoutManager(context)
            setHasFixedSize(true)
        }

        // Setup cancel button
        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateData() {
        // Set video title
        tvVideoTitle.text = task.title ?: "Unknown Title"

        Glide.with(context)
            .load(task.thumbnailUrl)
            .placeholder(R.drawable.ic_video)
            .error(R.drawable.ic_video)
            .into(ivPlatformThumbnail)

        // Set video duration
        tvVideoDuration.text = "Duration: ${task.getFormattedDuration()}"

        val mergedFormats = buildFormatList(task)
        val adapter = FormatSelectionAdapter(mergedFormats, task) { selectedFormat ->
            onFormatSelected(selectedFormat)
            dismiss()
        }

        rvFormats.adapter = adapter
    }

	private fun buildFormatList(task: DownloadTask): List<DownloadFormat> {
		val out = mutableListOf<DownloadFormat>()
		task.formats.orEmpty().forEach { f ->
			val cleanUrl = normalizeUrl(f.url)
			val withHeight = if (f.height == null) f.copy(height = f.extractHeight()) else f
			out.add(withHeight.copy(url = cleanUrl))
		}

		val fp = task.filePath?.let { normalizeUrl(it) }
		if (!fp.isNullOrBlank()) {
			out.add(FormatMerger.createFormatFromTask(task, fp))
		}

		return out
			.filter { it.url.isNotBlank() }
			.distinctBy { it.url }
			.sortedWith(
				compareByDescending<DownloadFormat> { if (it.formatId.equals("best", ignoreCase = true)) Int.MAX_VALUE else (it.height ?: it.extractHeight() ?: 0) }
					.thenByDescending { it.filesize ?: 0L }
			)
	}

	private fun normalizeUrl(raw: String): String {
		return raw.trim()
			.trim('`')
			.trim('"')
			.trim('\'')
			.trim()
	}

    /**
     * Builder pattern for easier dialog creation
     */
    class Builder(private val context: Context) {
        private var task: DownloadTask? = null
        private var onFormatSelected: ((DownloadFormat) -> Unit)? = null

        fun setTask(task: DownloadTask) = apply {
            this.task = task
        }

        fun setOnFormatSelected(listener: (DownloadFormat) -> Unit) = apply {
            this.onFormatSelected = listener
        }

        fun build(): FormatSelectionDialog {
            requireNotNull(task) { "DownloadTask must be set" }
            requireNotNull(onFormatSelected) { "OnFormatSelected listener must be set" }
            return FormatSelectionDialog(context, task!!, onFormatSelected!!)
        }

        fun show(): FormatSelectionDialog {
            val dialog = build()
            dialog.show()
            return dialog
        }
    }
}
