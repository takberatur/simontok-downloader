package com.agcforge.videodownloader.ui.fragment

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.DownloadFormat
import com.agcforge.videodownloader.data.model.DownloadTask
import com.agcforge.videodownloader.data.model.Platform
import com.agcforge.videodownloader.databinding.FragmentHomeBinding
import com.agcforge.videodownloader.ui.component.FormatSelectionDialog
import com.agcforge.videodownloader.ui.component.DownloadingDialog
import com.agcforge.videodownloader.ui.viewmodel.HomeViewModel
import com.agcforge.videodownloader.utils.AppManager
import com.agcforge.videodownloader.utils.DownloadManagerCleaner
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.Resource
import com.agcforge.videodownloader.utils.showToast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.core.net.toUri
import com.agcforge.videodownloader.data.websocket.DownloadTaskEvent
import com.agcforge.videodownloader.ui.activities.BaseActivity
import com.agcforge.videodownloader.ui.adapter.HistoryAdapter
import com.agcforge.videodownloader.ui.component.AppAlertDialog
import com.agcforge.videodownloader.ui.component.DownloadSettingsDialog
import com.agcforge.videodownloader.helper.BannerAdsHelper
import com.agcforge.videodownloader.helper.NativeAdsHelper

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var historyAdapter: HistoryAdapter
    private var selectedPlatform: Platform? = null
    private var isSubmitting = false
    private lateinit var preferenceManager: PreferenceManager
    private var pendingDownloadUrl: String? = null
    private val pendingMp3Tasks = linkedMapOf<String, String>()
    private val mp3PollJobs = linkedMapOf<String, Job>()
    private var mp3ProcessingDialog: DownloadingDialog? = null
    private var lastSubmitType: String? = null

	private var bannerAdsHelper: BannerAdsHelper? = null
	private var nativeAdsHelper: NativeAdsHelper? = null

    private var allPlatforms: List<Platform> = emptyList()
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val url = pendingDownloadUrl
        pendingDownloadUrl = null
        if (granted && url != null) {
            enqueueDownload(url)
        } else if (!granted) {
            showDialogStatusDownload(AppAlertDialog.AlertDialogType.ERROR, getString(R.string.storage_permission_denied), null)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager(requireContext())

        setupRecyclerView()
        setupListeners()
        observeViewModel()
        observeHistoryNavigation()

        viewModel.loadPlatforms()
        updateDownloadButtonState()
		setupAds()
    }

	private fun setupAds() {
		val act = activity ?: return
		bannerAdsHelper = BannerAdsHelper(act)
		nativeAdsHelper = NativeAdsHelper(act)

		bannerAdsHelper?.loadAndAttachBanner(binding.adsBanner)
		nativeAdsHelper?.loadAndAttachNativeAd(binding.adsNative, NativeAdsHelper.NativeAdSize.SMALL)
	}

	override fun onResume() {
		super.onResume()
		bannerAdsHelper?.resume()
	}

	override fun onPause() {
		bannerAdsHelper?.pause()
		super.onPause()
	}

    private fun observeHistoryNavigation() {
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<String>("history_url")
            ?.observe(viewLifecycleOwner) { url ->
                binding.etUrl.setText(url)
                findNavController().currentBackStackEntry?.savedStateHandle?.remove<String>("history_url")
            }
    }

    @SuppressLint("SetTextI18n")
    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            onCopyClick = {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Video URL", it.originalUrl)
                clipboard.setPrimaryClip(clip)
                requireContext().showToast("URL copied!")

            },
            onShareClick = {
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_SUBJECT, it.title)
                    putExtra(Intent.EXTRA_TEXT, it.originalUrl)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)

            },
            onDeleteClick = { task ->
                lifecycleScope.launch {
                    preferenceManager.deleteHistoryItem(task)
                }
            }
        )

//        findNavController().previousBackStackEntry?.savedStateHandle?.set("history_task", task)
//        findNavController().popBackStack()

        binding.rvHistory.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
        binding.tvViewAll.setOnClickListener {
            val navController = findNavController()
            navController.navigate(R.id.historyFragment)
        }
    }

    private fun setupListeners() {
        binding.btnDownload.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (!isUrlValid(url)) {
                binding.tilUrl.error = getString(R.string.please_enter_valid_url)
                updateDownloadButtonState()
                return@setOnClickListener
            }


            binding.tilUrl.error = null

            showDownloadSettingsDialog(url)
        }

        binding.etUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (binding.tilUrl.error != null) binding.tilUrl.error = null
                updateDownloadButtonState()
            }
        })

        binding.etUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.tilUrl.error = null
            }
        }

        binding.btnPaste.setOnClickListener {
            val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrEmpty()) {
                    binding.etUrl.setText(text)
                    requireContext().showToast("Pasted: $text", Toast.LENGTH_SHORT)
                    updateDownloadButtonState()
                }
            }
        }
    }

    private fun showDownloadSettingsDialog(url: String) {
        if (allPlatforms.isEmpty()) {
            showDialogStatusDownload(AppAlertDialog.AlertDialogType.INFO, "Please wait, loading platforms...", null)
            return
        }

        handlePremiumDownload {
            DownloadSettingsDialog.create(
                context = requireContext(),
                url = url,
                platforms = allPlatforms,
                onSubmit = { selectedType, selectedPlatform ->
                    // Store selected platform
                    this.selectedPlatform = selectedPlatform
                    // Submit download request
                    submitDownloadRequest(url, selectedPlatform, selectedType)
                }
            ).show()
        }
    }

    private fun submitDownloadRequest(url: String, platform: Platform, type: String) {
        // Determine platform type based on selection
        val platformType = when {
            type == "audio" -> {
                // Use type that ends with "to-mp3" if available, otherwise use platform.type
                if (platform.type.endsWith("to-mp3", ignoreCase = true)) {
                    platform.type
                } else {
                    "${platform.type}-to-mp3"
                }
            }
            else -> platform.type
        }

        lastSubmitType = type
        viewModel.createDownload(url, platformType)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.platforms.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is Resource.Success -> {
                        binding.progressBar.visibility = View.GONE
                        resource.data?.let {
                            allPlatforms = it
//                            platformAdapter.submitList(it)
                        }
                    }
                    is Resource.Error -> {
                        binding.progressBar.visibility = View.GONE
                        requireContext().showToast(resource.message ?: getString(R.string.failed_to_load_platform))
                    }

                    else -> {}
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            preferenceManager.history.collect { items ->
                historyAdapter.submitList(items)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.downloadResult.collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        isSubmitting = true
                        binding.btnDownload.isEnabled = false
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is Resource.Success -> {
                        isSubmitting = false
                        binding.btnDownload.isEnabled = true
                        binding.progressBar.visibility = View.GONE

                        resource.data?.let { task ->
                            // Show format selection dialog if formats available
                            if (!task.formats.isNullOrEmpty()) {
                                showFormatSelectionDialog(task)
                                lastSubmitType = null
                                viewModel.clearDownloadResult()
                            } else {
                                val isMp3Task = lastSubmitType == "audio" || task.platformType.trim().lowercase().endsWith("-to-mp3") || task.format?.equals("mp3", ignoreCase = true) == true
                                lastSubmitType = null
                                if (isMp3Task) {
                                    val name = task.title?.takeIf { it.isNotBlank() } ?: "audio"
                                    val safeName = sanitizeFilenameBase(name)
                                    pendingMp3Tasks[task.id] = safeName
                                    startMp3Processing(task.id, safeName)
                                    binding.etUrl.text?.clear()
                                    updateDownloadButtonState()
                                    viewModel.clearDownloadResult()
                                    return@let
                                }

                                val directUrl = task.filePath?.let { sanitizeApiUrl(it) }
                                if (!directUrl.isNullOrBlank() && isUrlValid(directUrl)) {
                                    enqueueDownload(directUrl)
                                    requireContext().showToast(getString(R.string.download_started))
                                    binding.etUrl.text?.clear()
                                    updateDownloadButtonState()
                                    viewModel.clearDownloadResult()
                                } else {
                                    requireContext().showToast(getString(R.string.no_format_available_to_download))
                                    updateDownloadButtonState()
                                    viewModel.clearDownloadResult()
                                }
                            }
                        }
                    }
                    is Resource.Error -> {
                        isSubmitting = false
                        binding.btnDownload.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                        requireContext().showToast(resource.message ?: getString(R.string.download_failed))
                        updateDownloadButtonState()
                        lastSubmitType = null
                        viewModel.clearDownloadResult()
                    }
                    is Resource.Idle -> {
                        isSubmitting = false
                        binding.progressBar.visibility = View.GONE
                        updateDownloadButtonState()
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.realtimeDownloadEvent.collect { event ->
                when (event) {
                    is DownloadTaskEvent.StatusChanged -> {
                        val taskId = event.taskId
                        val pendingName = pendingMp3Tasks[taskId]
                        if (pendingName != null && event.status.equals("completed", ignoreCase = true)) {
                            pendingMp3Tasks.remove(taskId)
                            mp3PollJobs.remove(taskId)?.cancel()
                            ensureMp3DialogDismissed()
                            enqueueDownload(buildProxyMp3Url(taskId, pendingName))
                        }
                        if (pendingName != null && event.status.equals("failed", ignoreCase = true)) {
                            pendingMp3Tasks.remove(taskId)
                            mp3PollJobs.remove(taskId)?.cancel()
                            ensureMp3DialogDismissed()
                            showDialogStatusDownload(
                                AppAlertDialog.AlertDialogType.ERROR,
                                getString(R.string.download_failed),
                                event.errorMessage)
                        }
                    }
                    is DownloadTaskEvent.Failed -> {
                        if (pendingMp3Tasks.containsKey(event.taskId)) {
                            pendingMp3Tasks.remove(event.taskId)
                            mp3PollJobs.remove(event.taskId)?.cancel()
                            ensureMp3DialogDismissed()
                            showDialogStatusDownload(
                                AppAlertDialog.AlertDialogType.ERROR,
                                requireContext().getString(R.string.download_failed),
                                event.error)
                        }
                    }
                    else -> {}
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.realtimeDownloadEvent.collect { event ->
                when (event) {
                    is DownloadTaskEvent.StatusChanged -> {
                        val taskId = event.taskId
                        val pendingName = pendingMp3Tasks[taskId]
                        if (pendingName != null && event.status.equals("completed", ignoreCase = true)) {
                            pendingMp3Tasks.remove(taskId)
                            mp3PollJobs.remove(taskId)?.cancel()
                            ensureMp3DialogDismissed()
                            enqueueDownload(buildProxyMp3Url(taskId, pendingName))
                        }
                        if (pendingName != null && event.status.equals("failed", ignoreCase = true)) {
                            pendingMp3Tasks.remove(taskId)
                            mp3PollJobs.remove(taskId)?.cancel()
                            ensureMp3DialogDismissed()
                            showDialogStatusDownload(
                                AppAlertDialog.AlertDialogType.ERROR,
                                getString(R.string.download_failed),
                                event.errorMessage)
                        }
                    }
                    is DownloadTaskEvent.Failed -> {
                        if (pendingMp3Tasks.containsKey(event.taskId)) {
                            pendingMp3Tasks.remove(event.taskId)
                            mp3PollJobs.remove(event.taskId)?.cancel()
                            ensureMp3DialogDismissed()
                            showDialogStatusDownload(
                                AppAlertDialog.AlertDialogType.ERROR,
                                requireContext().getString(R.string.download_failed),
                                event.error)
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun isUrlValid(url: String): Boolean {
        if (url.isBlank()) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        return runCatching { url.toUri() }.isSuccess
    }

    private fun sanitizeApiUrl(raw: String): String {
        return raw.trim()
            .trim('`')
            .trim('"')
            .trim('\'')
            .trim()
    }

    private fun sanitizeFilenameBase(raw: String): String {
        return raw
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\n|\r|\t"), " ")
            .trim()
            .ifBlank { "download" }
            .take(80)
    }

    private fun startMp3Processing(taskId: String, filename: String) {
        showMp3ProcessingDialog(taskId)
        mp3PollJobs.remove(taskId)?.cancel()
        mp3PollJobs[taskId] = viewLifecycleOwner.lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            while (true) {
                val result = runCatching { viewModel.getDownloadTask(taskId) }.getOrElse { Result.failure(it) }
                result
                    .onSuccess { task ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            preferenceManager.updateStatusHistory(task)
                        }
                        val st = task.status.lowercase()
                        updateMp3DialogStatus(st)
                        when (st) {
                            "completed" -> {
                                pendingMp3Tasks.remove(taskId)
                                ensureMp3DialogDismissed()
                                enqueueDownload(buildProxyMp3Url(taskId, filename))
                                mp3PollJobs.remove(taskId)?.cancel()
                                return@launch
                            }
                            "failed" -> {
                                pendingMp3Tasks.remove(taskId)
                                ensureMp3DialogDismissed()
                                showDialogStatusDownload(
                                    AppAlertDialog.AlertDialogType.ERROR,
                                    getString(R.string.download_failed),
                                    task.errorMessage)
                                mp3PollJobs.remove(taskId)?.cancel()
                                return@launch
                            }
                        }
                    }
                    .onFailure {
                        updateMp3DialogStatus("processing")
                    }

                if (System.currentTimeMillis() - startedAt > 30 * 60 * 1000) {
                    pendingMp3Tasks.remove(taskId)
                    ensureMp3DialogDismissed()
                    showDialogStatusDownload(
                        AppAlertDialog.AlertDialogType.ERROR,
                        getString(R.string.audio_not_ready_to_download),
                        null)
                    mp3PollJobs.remove(taskId)?.cancel()
                    return@launch
                }
                delay(4_000)
            }
        }
    }

    private fun showMp3ProcessingDialog(taskId: String) {
        if (!isAdded) return
        if (mp3ProcessingDialog?.isShowing == true) return
        mp3ProcessingDialog = DownloadingDialog.create(requireContext())
            .setTitle(getString(R.string.audio_being_processed_to_download))
            .setMessage(getString(R.string.please_wait))
            .setAnimation(R.raw.cloud_data_backup, autoPlay = true, loop = true)
            .setCancelable(false)
            .setNegativeButton(requireContext().getString(R.string.cancel)) {
                pendingMp3Tasks.remove(taskId)
                mp3PollJobs.remove(taskId)?.cancel()
            }
            .show()
    }

    private fun updateMp3DialogStatus(status: String) {
        if (mp3ProcessingDialog?.isShowing != true) return
        val msg = when (status.lowercase()) {
            "queued" -> getString(R.string.in_the_queue)
            "processing" -> getString(R.string.processing)
            "completed" -> getString(R.string.done_preparing_download)
            "failed" -> getString(R.string.failed_to_process_audio)
            else -> getString(R.string.being_processed)
        }
        mp3ProcessingDialog?.updateMessage(msg)
    }

    private fun ensureMp3DialogDismissed() {
        runCatching {
            val dialog = mp3ProcessingDialog
            if (dialog != null && dialog.isShowing) {
                dialog.dismiss()
            }
        }
        mp3ProcessingDialog = null
    }

    private fun updateDownloadButtonState() {
        if (isSubmitting) {
            binding.btnDownload.isEnabled = false
            return
        }

        val url = binding.etUrl.text?.toString()?.trim().orEmpty()
        val enabled = url.isNotBlank()
        binding.btnDownload.isEnabled = enabled
    }

    private fun showFormatSelectionDialog(task: DownloadTask) {
        val formats = task.formats
        if (formats.isNullOrEmpty()) {
            showDialogStatusDownload(AppAlertDialog.AlertDialogType.ERROR, getString(R.string.no_format_available_to_download), null)
            return
        }

        FormatSelectionDialog.Builder(requireContext())
            .setTask(task)
            .setOnFormatSelected { selectedFormat ->
                val isMp3Task = task.format?.equals("mp3", ignoreCase = true) == true || task.platformType.lowercase().contains("mp3")
                if (isMp3Task) {
                    val name = task.title?.takeIf { it.isNotBlank() } ?: "audio"
                    val safeName = sanitizeFilenameBase(name)
                    pendingMp3Tasks[task.id] = safeName
                    startMp3Processing(task.id, safeName)
                } else {
                    enqueueDownload(buildProxyVideoUrl(task, selectedFormat))
                    showDialogStatusDownload(
                        AppAlertDialog.AlertDialogType.SUCCESS,
                        getString(R.string.download_started),
                        getString(R.string.download_started_please_check_background_status_bar))
                }

                viewLifecycleOwner.lifecycleScope.launch {
                    preferenceManager.updateStatusHistoryById(task.id, "completed")
                }

                binding.etUrl.text?.clear()
                updateDownloadButtonState()
            }
            .show()
    }

    private fun buildProxyVideoUrl(
        task: DownloadTask,
        format: DownloadFormat
    ): String {
        val base = AppManager.baseUrl
        val endpoint = if (base.endsWith("/")) {
            "${base}public-proxy/downloads/file/video"
        } else {
            "$base/public-proxy/downloads/file/video"
        }

        val formatId = format.formatId
        val resolution = format.height?.let { "${it}p" }
        val effectiveFormat = formatId ?: resolution

        val filename = task.title?.takeIf { it.isNotBlank() } ?: getString(R.string.download)
        val uriBuilder = endpoint.toUri().buildUpon()
            .appendQueryParameter("task_id", task.id)
            .appendQueryParameter("filename", filename)
        if (!effectiveFormat.isNullOrBlank()) {
            uriBuilder.appendQueryParameter("format_id", effectiveFormat)
        }
        return uriBuilder.build().toString()
    }

    private fun buildProxyMp3Url(taskId: String, filename: String): String {
        val base = AppManager.baseUrl
        val endpoint = if (base.endsWith("/")) {
            "${base}public-proxy/downloads/file/mp3"
        } else {
            "$base/public-proxy/downloads/file/mp3"
        }
        val finalName = if (filename.lowercase().endsWith(".mp3")) filename else "$filename.mp3"
        return endpoint.toUri().buildUpon()
            .appendQueryParameter("task_id", taskId)
            .appendQueryParameter("filename", finalName)
            .build()
            .toString()
    }

    private fun enqueueDownload(url: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            DownloadManagerCleaner.clearFailedDownloads(requireContext())
            val storageLocation = preferenceManager.storageLocation.first() ?: "app"
            val uri = url.toUri()
            val fileName = deriveDownloadFileName(uri)

            if (storageLocation == "downloads" && android.os.Build.VERSION.SDK_INT < 29) {
                val granted = ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    pendingDownloadUrl = url
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    return@launch
                }
            }

            val dm = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setMimeType(if (fileName.lowercase().endsWith(".mp3")) "audio/mpeg" else "video/mp4")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            if (storageLocation == "downloads") {
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            } else {
                request.setDestinationInExternalFilesDir(requireContext(), Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            try {
                dm.enqueue(request)
                showInterstitialFromBase{
                    showDialogStatusDownload(
                        AppAlertDialog.AlertDialogType.SUCCESS,
                        getString(R.string.download_started),
                        getString(R.string.download_started_please_check_background_status_bar))
                }
            } catch (e: Exception) {
                showDialogStatusDownload(
                    AppAlertDialog.AlertDialogType.ERROR,
                    getString(R.string.download_failed),
                    e.message)
            }
        }
    }

    private fun deriveDownloadFileName(uri: android.net.Uri): String {
        val defaultBase = "video_${System.currentTimeMillis()}"
        val filenameParam = uri.getQueryParameter("filename")?.takeIf { it.isNotBlank() }
        val lastSegment = uri.lastPathSegment?.takeIf { it.isNotBlank() }
        val baseNameRaw = filenameParam ?: lastSegment ?: defaultBase
        val taskIdSuffix = uri.getQueryParameter("task_id")?.takeIf { it.isNotBlank() }?.take(8)

        val extFromBase = baseNameRaw.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val expectedExt = when {
            uri.path?.contains("/mp3", ignoreCase = true) == true -> "mp3"
            extFromBase == "mp3" || extFromBase == "mp4" -> extFromBase
            else -> "mp4"
        }

        val baseWithoutExt = if (baseNameRaw.contains('.')) baseNameRaw.substringBeforeLast('.') else baseNameRaw
        val safeBase = baseWithoutExt
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\n|\r|\t"), " ")
            .trim()
            .ifBlank { defaultBase }
            .let { base ->
                if (!taskIdSuffix.isNullOrBlank()) {
                    "${base}_$taskIdSuffix"
                } else {
                    base
                }
            }
            .take(80)

        return "$safeBase.$expectedExt"
    }

    override fun onDestroyView() {
        mp3PollJobs.values.forEach { it.cancel() }
        mp3PollJobs.clear()
        pendingMp3Tasks.clear()
        ensureMp3DialogDismissed()

		bannerAdsHelper?.destroy()
		nativeAdsHelper?.destroy()
		bannerAdsHelper = null
		nativeAdsHelper = null

        super.onDestroyView()
        _binding = null
    }

    private fun handleFormatSelection(task: DownloadTask, selectedFormat: DownloadFormat) {
        val downloadUrl = buildProxyVideoUrl(task, selectedFormat)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_download))
            .setMessage(getString(R.string.confirm_download_message, selectedFormat.getQualityLabel(), selectedFormat.getFormatDescription()))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                enqueueDownload(downloadUrl)
                requireContext().showToast(getString(R.string.download_started))
                binding.etUrl.text?.clear()
                updateDownloadButtonState()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }


    private fun showDialogStatusDownload(type: AppAlertDialog.AlertDialogType, title: String?, message: String?) {
        val dialog = AppAlertDialog
            .Builder(requireContext())
            .setNegativeButtonText(getString(R.string.ok))
            .setType(type)

        if (title != null) {
            dialog.setTitle(title)
        }
        if (message != null) {
            dialog.setMessage(message)
        }
        dialog.show()
    }

    fun Fragment.getBaseActivity(): BaseActivity? {
        return activity as? BaseActivity
    }

    fun Fragment.showInterstitialFromBase(onClosed: () -> Unit) {
        getBaseActivity()?.showInterstitial(onClosed)
    }

    private fun handlePremiumDownload(onComplete: () -> Unit) {
        val base = activity as? BaseActivity
        if (base == null) {
            // if base activity null,
            onComplete()
            return
        }

//        if (isUserPremium) {
//            onComplete()
//            return
//        }

        base.showRewardAd { onRewarded ->
            if (onRewarded) {
                onComplete()
            } else {
                Toast.makeText(context, "Please watch the ad to download!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
