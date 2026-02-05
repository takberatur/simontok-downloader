package com.agcforge.videodownloader.ui.component

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.agcforge.videodownloader.R

class AppAlertDialog(
    context: Context,
    private val type: AlertDialogType = AlertDialogType.INFO,
    private val title: String,
    private val message: String,
    private val positiveButtonText: String? = null,
    private val negativeButtonText: String? = null,
    private val onPositiveClick: () -> Unit = {},
    private val onNegativeClick: () -> Unit = {}
): Dialog(context)  {

    enum class AlertDialogType {
        INFO,
        WARNING,
        ERROR,
        SUCCESS
    }

    private lateinit var ivIconAlert: ImageView
    private lateinit var tvAlertTitle: TextView
    private lateinit var tvAlertMessage: TextView
    private lateinit var btnPositive: Button
    private lateinit var btnNegative: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        val view = LayoutInflater.from(context)
            .inflate(R.layout.dialog_alert, null)
        setContentView(view)

        initViews(view)

        setupDialog()
    }

    private fun initViews(view: android.view.View) {
        ivIconAlert = view.findViewById(R.id.ivIconAlert)
        tvAlertTitle = view.findViewById(R.id.tvAlertTitle)
        tvAlertMessage = view.findViewById(R.id.tvAlertMessage)
        btnPositive = view.findViewById(R.id.btnPositive)
        btnNegative = view.findViewById(R.id.btnNegative)
    }

    private fun setupDialog() {
        window?.apply {
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        val iconAlert = when (type) {
            AlertDialogType.INFO -> R.drawable.ic_alert_default
            AlertDialogType.WARNING -> R.drawable.ic_warning
            AlertDialogType.ERROR -> R.drawable.ic_error
            AlertDialogType.SUCCESS -> R.drawable.ic_success
        }

        ivIconAlert.setImageResource(iconAlert)

        tvAlertTitle.text = title
        tvAlertMessage.text = message

        if (positiveButtonText != null) {
            btnPositive.text = positiveButtonText
            btnPositive.visibility = View.VISIBLE
            btnPositive.setOnClickListener {
                onPositiveClick()
                dismiss()
            }
        } else {
            btnPositive.visibility = View.GONE
        }

        if (negativeButtonText != null) {
            btnNegative.text = negativeButtonText
            btnNegative.visibility = View.VISIBLE
            btnNegative.setOnClickListener {
                onNegativeClick()
                dismiss()
            }
        } else {
            btnNegative.visibility = View.GONE
        }

        if (positiveButtonText == null && negativeButtonText == null) {
            btnNegative.visibility = View.VISIBLE
            btnNegative.text = context.getString(android.R.string.ok)
            btnNegative.setOnClickListener {
                dismiss()
            }
        }
    }

    class Builder(private val context: Context) {
        private var type: AlertDialogType = AlertDialogType.INFO
        private var title: String = ""
        private var message: String = ""
        private var positiveButtonText: String? = null
        private var negativeButtonText: String? = null

        private var onPositiveClick: () -> Unit = {}
        private var onNegativeClick: () -> Unit = {}

        fun setType(type: AlertDialogType) = apply {
            this.type = type
        }

        fun setTitle(title: String) = apply {
            this.title = title
        }

        fun setMessage(message: String) = apply {
            this.message = message
        }

        fun setPositiveButtonText(text: String) = apply {
            this.positiveButtonText = text
        }

        fun setNegativeButtonText(text: String) = apply {
            this.negativeButtonText = text
        }

        fun setOnPositiveClick(listener: () -> Unit) = apply {
            this.onPositiveClick = listener
        }

        fun setOnNegativeClick(listener: () -> Unit) = apply {
            this.onNegativeClick = listener
        }


        fun build(): AppAlertDialog {
            return AppAlertDialog(context, type, title, message, positiveButtonText, negativeButtonText, onPositiveClick, onNegativeClick)
        }

        fun show(): AppAlertDialog {
            val dialog = build()
            dialog.show()
            return dialog
        }
    }
}