package com.agcforge.videodownloader.ui.component

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import com.agcforge.videodownloader.R

class PlayerSelectionDialog(
    context: Context,
    private val type: PlayerType = PlayerType.VIDEO,
    private val onPlayerSelected: (String) -> Unit
) : Dialog(context) {

    enum class PlayerType {
        VIDEO,
        AUDIO
    }

    companion object {
        const val PLAYER_INTERNAL = "internal"
        const val PLAYER_EXTERNAL = "external"
    }

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        // I'm assuming you have a layout file named 'dialog_player_selection.xml'
        // with buttons 'btnInternalPlayer', 'btnExternalPlayer', and 'btnCancel'.
        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_player_selection, null)
        setContentView(view)

        window?.apply {
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        val tvDialogTitle: TextView = view.findViewById(R.id.tvDialogTitle)
        val btnInternalPlayer: Button = view.findViewById(R.id.btnInternalPlayer)
        val btnExternalPlayer: Button = view.findViewById(R.id.btnExternalPlayer)
        val btnCancel: ImageButton = view.findViewById(R.id.btnCancel)

        val titleVideoPlayer = "${context.getString(R.string.select_player)} ${context.getString(R.string.video)}"
        val titleAudioPlayer = "${context.getString(R.string.select_player)} ${context.getString(R.string.audio)}"

        tvDialogTitle.text = if (type == PlayerType.VIDEO) titleVideoPlayer else titleAudioPlayer

        btnInternalPlayer.setOnClickListener {
            onPlayerSelected(PLAYER_INTERNAL)
            dismiss()
        }

        btnExternalPlayer.setOnClickListener {
            onPlayerSelected(PLAYER_EXTERNAL)
            dismiss()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    class Builder(private val context: Context) {
        private var type: PlayerType = PlayerType.VIDEO
        private var onPlayerSelected: ((String) -> Unit)? = null

        fun setType(type: PlayerType) = apply {
            this.type = type
        }

        fun setOnPlayerSelected(listener: (String) -> Unit) = apply {
            this.onPlayerSelected = listener
        }

        fun build(): PlayerSelectionDialog {
            requireNotNull(onPlayerSelected) { "OnPlayerSelected listener must be set" }
            return PlayerSelectionDialog(context, type, onPlayerSelected!!)
        }

        fun show(): PlayerSelectionDialog {
            val dialog = build()
            dialog.show()
            return dialog
        }
    }
}
