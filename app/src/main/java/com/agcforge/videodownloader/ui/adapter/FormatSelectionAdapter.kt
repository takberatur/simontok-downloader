package com.agcforge.videodownloader.ui.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.DownloadFormat
import com.agcforge.videodownloader.data.model.DownloadTask
import com.bumptech.glide.Glide

class FormatSelectionAdapter(
    private var formats: List<DownloadFormat>,
    private val task: DownloadTask? = null,
    private val onFormatSelected: (DownloadFormat) -> Unit
) : RecyclerView.Adapter<FormatSelectionAdapter.FormatViewHolder>() {

    init {
        this.formats = prepareFormatsList(formats)
    }

    private fun prepareFormatsList(input: List<DownloadFormat>): List<DownloadFormat> {
        val normalized = input.map { format ->
            val cleanUrl = normalizeUrl(format.url)
            val updated = if (format.height == null) format.copy(height = format.extractHeight()) else format
            updated.copy(url = cleanUrl)
        }

        return normalized
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_format_selection, parent, false)
        return FormatViewHolder(view)
    }

    override fun onBindViewHolder(holder: FormatViewHolder, position: Int) {
        val format = formats[position]
        val context = holder.itemView.context
        holder.bind(context, format, task)
    }

    override fun getItemCount(): Int {
        return formats.size
    }

    inner class FormatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val formatNameTextView: TextView = itemView.findViewById(R.id.tvFormatLabel)
        private val tvDirectBadge: TextView = itemView.findViewById(R.id.tvDirectBadge)
        private val tvExtension: TextView = itemView.findViewById(R.id.tvExtension)
        private val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)

        private val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
        private val tvCodecInfo: TextView = itemView.findViewById(R.id.tvCodecInfo)


        fun bind(context: Context, format: DownloadFormat, task: DownloadTask?) {
			val directUrl = task?.filePath?.let { normalizeUrl(it) }
			val isDirect = !directUrl.isNullOrBlank() && normalizeUrl(format.url) == directUrl

            formatNameTextView.text = if (format.formatId.equals("best", ignoreCase = true)) {
                "Best"
            } else {
                format.getQualityLabel()
            }
            tvDirectBadge.visibility = if (isDirect) View.VISIBLE else View.GONE
            tvExtension.text = format.ext?.uppercase() ?: task?.format?.uppercase() ?: "MP4"
            tvFileSize.text = format.getFileSizeFormatted()
            tvCodecInfo.text = format.getCodecInfo()

            task?.thumbnailUrl?.let { thumbnailUrl ->
                Glide.with(context)
                    .load(thumbnailUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_media_play)
                    .error(R.drawable.ic_media_play)
                    .into(ivThumbnail)
            } ?: run {
                ivThumbnail.setImageResource(R.drawable.ic_video)
            }

            itemView.setOnClickListener {
                onFormatSelected(format)
            }
        }

    }
}
