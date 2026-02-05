package com.agcforge.videodownloader.ui.component

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import com.agcforge.videodownloader.R
import com.airbnb.lottie.LottieAnimationView

class DownloadingDialog private constructor(
    context: Context,
    private val title: String,
    private val message: String? = null,
    private val positiveButtonText: String? = null,
    private val negativeButtonText: String? = null,
    private val isCancelable: Boolean = true,
    private val animationResId: Int? = null,
    private val animationAutoPlay: Boolean = true,
    private val animationLoop: Boolean = true
) : Dialog(context, R.style.DownloadingDialogStyle) {

    private lateinit var lottieAnimation: LottieAnimationView
    private lateinit var tvDownloadingTitle: TextView
    private lateinit var tvDownloadingMessage: TextView
    private lateinit var btnPositive: Button
    private lateinit var btnNegative: Button
    private lateinit var viewContainer: View

    private var onPositiveClick: (() -> Unit)? = null
    private var onNegativeClick: (() -> Unit)? = null
    private var onDismissListener: (() -> Unit)? = null

    init {
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setWindowAnimations(R.style.DownloadingDialogAnimation)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        viewContainer = LayoutInflater.from(context)
            .inflate(R.layout.dialog_download_processing, null)
        setContentView(viewContainer)


        setupWindow()
        initViews()
        setupContent()
        setupButtons()
        setupAnimation()
        // Set cancelable
        setCancelable(isCancelable)
        setCanceledOnTouchOutside(isCancelable)
    }

    private fun setupWindow() {
        window?.apply {
            val displayMetrics = context.resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.9).toInt()

            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

            // Set dialog position (optional - center by default)
            attributes = attributes?.apply {
                gravity = android.view.Gravity.CENTER
            }
        }
    }

    private fun initViews() {
        tvDownloadingTitle = viewContainer.findViewById(R.id.tvTitle)
        tvDownloadingMessage = viewContainer.findViewById(R.id.tvMessage)
        btnPositive = viewContainer.findViewById(R.id.btnPositive)
        btnNegative = viewContainer.findViewById(R.id.btnNegative)
        lottieAnimation = viewContainer.findViewById(R.id.lottieAnimation)
    }

    private fun setupContent() {
        tvDownloadingTitle.text = title
        if (message.isNullOrEmpty()) {
            tvDownloadingMessage.visibility = View.GONE
        } else {
            tvDownloadingMessage.text = message
            tvDownloadingMessage.visibility = View.VISIBLE
        }
    }

    private fun setupButtons() {
        if (!positiveButtonText.isNullOrEmpty()) {
            btnPositive.text = positiveButtonText
            btnPositive.visibility = View.VISIBLE
            btnPositive.setOnClickListener {
                onPositiveClick?.invoke()
                dismiss()
            }
        } else {
            btnPositive.visibility = View.GONE
        }

        if (!negativeButtonText.isNullOrEmpty()) {
            btnNegative.text = negativeButtonText
            btnNegative.visibility = View.VISIBLE
            btnNegative.setOnClickListener {
                onNegativeClick?.invoke()
                dismiss()
            }
        } else {
            btnNegative.visibility = View.GONE
        }

        // If no button is specified, add a default OK button.
        if (positiveButtonText.isNullOrEmpty() && negativeButtonText.isNullOrEmpty()) {
            btnNegative.visibility = View.VISIBLE
            btnNegative.text = context.getString(android.R.string.ok)
            btnNegative.setOnClickListener {
                dismiss()
            }
        }
    }

    private fun setupAnimation() {
        animationResId?.let { resId ->
            lottieAnimation.setAnimation(resId)
            lottieAnimation.visibility = View.VISIBLE

            if (animationAutoPlay) {
                lottieAnimation.playAnimation()
            }

            lottieAnimation.setRepeatCount(if (animationLoop) ValueAnimator.INFINITE else 0)
        } ?: run {
            lottieAnimation.visibility = View.GONE
        }
    }

    override fun dismiss() {
        super.dismiss()
        onDismissListener?.invoke()
    }

    fun updateTitle(newTitle: String) {
        tvDownloadingTitle.text = newTitle
    }

    fun updateMessage(newMessage: String) {
        tvDownloadingMessage.text = newMessage
        tvDownloadingMessage.visibility = View.VISIBLE
    }

    fun setAnimation(resId: Int, autoPlay: Boolean = true, loop: Boolean = true) {
        lottieAnimation.setAnimation(resId)
        if (autoPlay) {
            lottieAnimation.playAnimation()
        }
        lottieAnimation.repeatCount = if (loop) ValueAnimator.INFINITE else 0
        lottieAnimation.visibility = View.VISIBLE
    }

    fun stopAnimation() {
        lottieAnimation.cancelAnimation()
    }

    fun resumeAnimation() {
        lottieAnimation.resumeAnimation()
    }

    fun setOnPositiveClickListener(listener: () -> Unit) {
        onPositiveClick = listener
    }

    fun setOnNegativeClickListener(listener: () -> Unit) {
        onNegativeClick = listener
    }

    fun setOnDismissListener(listener: () -> Unit) {
        onDismissListener = listener
    }

    class Builder(private val context: Context) {
        private var title: String = ""
        private var message: String? = null
        private var positiveButtonText: String? = null
        private var negativeButtonText: String? = null
        private var isCancelable: Boolean = true
        private var animationResId: Int? = null
        private var animationAutoPlay: Boolean = true
        private var animationLoop: Boolean = true

        private var onPositiveClick: (() -> Unit)? = null
        private var onNegativeClick: (() -> Unit)? = null
        private var onDismissListener: (() -> Unit)? = null

        fun setTitle(title: String): Builder {
            this.title = title
            return this
        }

        fun setTitle(@StringRes titleResId: Int): Builder {
            this.title = context.getString(titleResId)
            return this
        }

        fun setMessage(message: String?): Builder {
            this.message = message
            return this
        }

        fun setMessage(@StringRes messageResId: Int): Builder {
            this.message = context.getString(messageResId)
            return this
        }

        fun setPositiveButton(text: String, onClick: (() -> Unit)? = null): Builder {
            this.positiveButtonText = text
            this.onPositiveClick = onClick
            return this
        }

        fun setPositiveButton(@StringRes textResId: Int, onClick: (() -> Unit)? = null): Builder {
            this.positiveButtonText = context.getString(textResId)
            this.onPositiveClick = onClick
            return this
        }

        fun setNegativeButton(text: String, onClick: (() -> Unit)? = null): Builder {
            this.negativeButtonText = text
            this.onNegativeClick = onClick
            return this
        }

        fun setNegativeButton(@StringRes textResId: Int, onClick: (() -> Unit)? = null): Builder {
            this.negativeButtonText = context.getString(textResId)
            this.onNegativeClick = onClick
            return this
        }

        fun setCancelable(cancelable: Boolean): Builder {
            this.isCancelable = cancelable
            return this
        }

        fun setAnimation(resId: Int, autoPlay: Boolean = true, loop: Boolean = true): Builder {
            this.animationResId = resId
            this.animationAutoPlay = autoPlay
            this.animationLoop = loop
            return this
        }

        fun setOnDismissListener(listener: () -> Unit): Builder {
            this.onDismissListener = listener
            return this
        }

        fun build(): DownloadingDialog {
            val dialog = DownloadingDialog(
                context = context,
                title = title,
                message = message,
                positiveButtonText = positiveButtonText,
                negativeButtonText = negativeButtonText,
                isCancelable = isCancelable,
                animationResId = animationResId,
                animationAutoPlay = animationAutoPlay,
                animationLoop = animationLoop
            )

            onPositiveClick?.let { dialog.onPositiveClick = it }
            onNegativeClick?.let { dialog.onNegativeClick = it }
            onDismissListener?.let { dialog.onDismissListener = it }

            return dialog
        }

        fun show(): DownloadingDialog {
            val dialog = build()
            dialog.show()
            return dialog
        }
    }

    companion object {
        fun create(context: Context): Builder {
            return Builder(context)
        }
    }
}