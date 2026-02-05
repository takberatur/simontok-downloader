package com.agcforge.videodownloader.ui.fragment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.agcforge.videodownloader.R
import com.agcforge.videodownloader.data.model.LocalDownloadItem
import com.agcforge.videodownloader.databinding.FragmentDownloadsBinding
import com.agcforge.videodownloader.ui.activities.player.AudioPlayerActivity
import com.agcforge.videodownloader.ui.activities.player.VideoPlayerActivity
import com.agcforge.videodownloader.ui.adapter.LocalDownloadAdapter
import com.agcforge.videodownloader.ui.component.PlayerSelectionDialog
import com.agcforge.videodownloader.utils.LocalDownloadsScanner
import com.agcforge.videodownloader.utils.PreferenceManager
import com.agcforge.videodownloader.utils.StorageFolderNavigator
import com.agcforge.videodownloader.utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.agcforge.videodownloader.ui.component.AppAlertDialog

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var preferenceManager: PreferenceManager
    private lateinit var adapter: LocalDownloadAdapter

    private var isLoading = false
    private var hasMoreItems = true
    private var currentOffset = 0
    private val pageSize = 20

    private val readPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        resetAndLoadDownloads()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager(requireContext())

        setupRecyclerView()
        setupSwipeRefresh()
        binding.btnOpenStorageFolder.setOnClickListener { openStorageFolder() }

        resetAndLoadDownloads()
    }

    private fun openStorageFolder() {
        viewLifecycleOwner.lifecycleScope.launch {
            val location = preferenceManager.storageLocation.first() ?: "app"
            StorageFolderNavigator.openStorageFolder(requireContext(), location)
        }
    }

    private fun setupRecyclerView() {
        adapter = LocalDownloadAdapter(
            context = requireContext(),
            onOpenClick = { item ->
                openLocalFile(item)
            },
            onDeleteClick = { item ->
                showDeleteConfirmation(item)
            }
        )

        binding.rvDownloads.apply {
            adapter = this@DownloadsFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                private var lastVisibleRange = Pair(-1, -1)

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)

                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val firstVisible = layoutManager.findFirstVisibleItemPosition()
                    val lastVisible = layoutManager.findLastVisibleItemPosition()

                    if (firstVisible != lastVisibleRange.first || lastVisible != lastVisibleRange.second) {
                        lastVisibleRange = Pair(firstVisible, lastVisible)
                        loadThumbnailsForVisibleRange(firstVisible, lastVisible)
                    }

                    if (!isLoading && hasMoreItems) {
                        val visibleItemCount = layoutManager.childCount
                        val totalItemCount = layoutManager.itemCount

                        if ((visibleItemCount + firstVisible) >= totalItemCount - 5) {
                            loadMoreDownloads()
                        }
                    }
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)

                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                        val firstVisible = layoutManager.findFirstVisibleItemPosition()
                        val lastVisible = layoutManager.findLastVisibleItemPosition()
                        loadThumbnailsForVisibleRange(firstVisible, lastVisible)
                    }
                }
            })

            itemAnimator = null
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            resetAndLoadDownloads()
        }
    }

    private fun resetAndLoadDownloads() {
        currentOffset = 0
        hasMoreItems = true
        adapter.clearItems()
        loadLocalDownloads()
    }

    @SuppressLint("SuspiciousIndentation")
    private fun loadLocalDownloads() {
        if (isLoading) return

        isLoading = true
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        println("DEBUG: Loading downloads - offset: $currentOffset, hasMore: $hasMoreItems")

        viewLifecycleOwner.lifecycleScope.launch {
            val location = preferenceManager.storageLocation.first() ?: "app"

            println("DEBUG: Storage location: $location")

            if (location == "downloads" && !hasRequiredReadPermissions()) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                binding.tvEmpty.visibility = View.VISIBLE
                requestReadPermissions()
                isLoading = false
                return@launch
            }

            try {
                println("DEBUG: Calling scanPaged with limit=$pageSize, offset=$currentOffset")

                val result = LocalDownloadsScanner.scanPaged(
                    context = requireContext(),
                    location = location,
                    limit = pageSize,
                    offset = currentOffset
                )

                println("DEBUG: Got ${result.items.size} items, hasMore: ${result.hasMore}")

                if (result.items.isNotEmpty()) {
                    adapter.addItems(result.items)
                    binding.rvDownloads.visibility = View.VISIBLE
                    binding.tvEmpty.visibility = View.GONE

                    binding.rvDownloads.post {
                        val layoutManager = binding.rvDownloads.layoutManager as LinearLayoutManager
                        val firstVisible = layoutManager.findFirstVisibleItemPosition()
                        val lastVisible = layoutManager.findLastVisibleItemPosition()

                        if (firstVisible >= 0 && lastVisible >= firstVisible) {
                            loadThumbnailsForVisibleRange(firstVisible, lastVisible)
                        }
                    }

                    if (result.items.isNotEmpty()) {
                        println("DEBUG: First item: ${result.items[0].displayName}")
                    }
                } else if (currentOffset == 0) {
                    println("DEBUG: No items found at offset 0")
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.rvDownloads.visibility = View.GONE
                    binding.tvEmpty.text = getString(R.string.no_media_files_found)
                }

                hasMoreItems = result.hasMore
                currentOffset = result.nextOffset

                println("DEBUG: Updated - currentOffset: $currentOffset, hasMore: $hasMoreItems")

                if (result.items.isNotEmpty()) {
                    loadThumbnailsForVisibleItems()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                println("DEBUG: Error loading downloads: ${e.message}")
                if (isAdded) {
                    requireContext().showToast(getString(R.string.failed_to_load_downloads, e.message.toString()))
                }
            } finally {
                if (_binding != null) {
                    binding.progressBar.visibility = View.GONE
                    binding.swipeRefresh.isRefreshing = false
                }
                isLoading = false
            }
        }

    }

    private fun loadMoreDownloads() {
        if (!hasMoreItems || isLoading) return
        loadLocalDownloads()
    }

    private fun loadThumbnailsForVisibleItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            val layoutManager = binding.rvDownloads.layoutManager as LinearLayoutManager
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()

            if (firstVisible >= 0 && lastVisible >= firstVisible && firstVisible < adapter.itemCount) {
                val endIndex = (lastVisible + 1).coerceAtMost(adapter.itemCount)

                for (i in firstVisible until endIndex) {
                    val item = adapter.getItems()[i]
                    if (item.thumbnail == null) {
                        val thumbnail = LocalDownloadsScanner.loadThumbnailForItem(
                            requireContext(),
                            item
                        )
                        thumbnail?.let {
                            adapter.updateItemThumbnail(item.id, it)
                        }
                    }
                }
            }
        }
    }

    private fun loadThumbnailsForVisibleRange(firstVisible: Int, lastVisible: Int) {
        if (firstVisible !in 0..lastVisible) return

        for (i in firstVisible..lastVisible) {
            val item = adapter.getItemAt(i)
            item?.let {
                // Adapter will handle thumbnail loading automatically
                // By onBindViewHolder and loadThumbnailForVisibleItem
            }
        }
    }

    private fun showDeleteConfirmation(item: LocalDownloadItem) {

        AppAlertDialog.Builder(requireContext())
            .setType(AppAlertDialog.AlertDialogType.WARNING)
            .setTitle(getString(R.string.delete_file))
            .setMessage(getString(R.string.title_dialog_delete_file, item.displayName))
            .setPositiveButtonText(requireContext().getString(R.string.yes))
            .setNegativeButtonText(requireContext().getString(R.string.cancel))
            .setOnPositiveClick {
                if(checkDeletePermission(item)){
                    deleteFile(item)
                }
            }
            .show()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun deleteFile(item: LocalDownloadItem) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            try {
                println("DEBUG [Delete]: Starting delete process for: ${item.displayName}")

                if (checkDeletePermission(item)) {
                    val deleted = deleteFileFromStorage(item)

                    if (deleted) {
                        adapter.removeItem(item.id)

                        requireContext().showToast(getString(R.string.delete_file_success))

                        if (adapter.itemCount == 0) {
                            binding.tvEmpty.visibility = View.VISIBLE
                            binding.rvDownloads.visibility = View.GONE
                        }

                        // Refresh MediaStore
                        refreshMediaStore(item.filePath)

                    } else {
                        showDeleteHelpDialog(item)
                    }
                } else {
                    requireContext().showToast(getString(R.string.manage_external_storage_permission_denied))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                requireContext().showToast(getString(R.string.error, e.message))
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }


    private suspend fun deleteFileFromStorage(item: LocalDownloadItem): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                println("DEBUG [Delete]: Attempting to delete file: ${item.displayName}")
                println("DEBUG [Delete]: File path: ${item.filePath}")
                println("DEBUG [Delete]: URI: ${item.uri}")

                if (deleteViaMediaStore(item)) {
                    println("DEBUG [Delete]: Success via MediaStore")
                    return@withContext true
                }

                if (deleteViaFileSystem(item)) {
                    println("DEBUG [Delete]: Success via FileSystem")
                    return@withContext true
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    if (deleteViaLegacyMethod(item)) {
                        println("DEBUG [Delete]: Success via Legacy method")
                        return@withContext true
                    }
                }

                println("DEBUG [Delete]: All delete methods failed")
                false

            } catch (e: Exception) {
                println("DEBUG [Delete]: Error: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    private fun deleteViaMediaStore(item: LocalDownloadItem): Boolean {
        return try {
            if (item.uri.toString().contains("content://media")) {
                val rowsDeleted = requireContext().contentResolver.delete(
                    item.uri,
                    null,
                    null
                )
                println("DEBUG [Delete]: MediaStore rows deleted: $rowsDeleted")
                rowsDeleted > 0
            } else {
                false
            }
        } catch (e: SecurityException) {
            println("DEBUG [Delete]: SecurityException - need MANAGE_EXTERNAL_STORAGE permission")
            false
        } catch (e: Exception) {
            println("DEBUG [Delete]: MediaStore delete error: ${e.message}")
            false
        }
    }

    private fun deleteViaFileSystem(item: LocalDownloadItem): Boolean {
        return try {
            item.filePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    println("DEBUG [Delete]: File exists: true, isFile: ${file.isFile}, canWrite: ${file.canWrite()}")

                    val isAppPrivateStorage = path.contains(requireContext().filesDir.absolutePath) ||
                            path.contains(requireContext().externalCacheDir?.absolutePath ?: "") ||
                            path.contains(requireContext().getExternalFilesDir(null)?.absolutePath ?: "")

                    if (isAppPrivateStorage) {
                        val deleted = file.delete()
                        println("DEBUG [Delete]: App storage delete result: $deleted")
                        deleted
                    } else {
                        when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                                if (Environment.isExternalStorageManager()) {
                                    val deleted = file.delete()
                                    println("DEBUG [Delete]: Android 11+ external storage delete result: $deleted")
                                    deleted
                                } else {
                                    println("DEBUG [Delete]: Need MANAGE_EXTERNAL_STORAGE permission")
                                    false
                                }
                            }

                            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                                if (ContextCompat.checkSelfPermission(
                                        requireContext(),
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val deleted = file.delete()
                                    println("DEBUG [Delete]: Android 10 external storage delete result: $deleted")
                                    deleted
                                } else {
                                    println("DEBUG [Delete]: Need WRITE_EXTERNAL_STORAGE permission")
                                    false
                                }
                            }

                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                                if (ContextCompat.checkSelfPermission(
                                        requireContext(),
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    val deleted = file.delete()
                                    println("DEBUG [Delete]: Android 6-9 external storage delete result: $deleted")
                                    deleted
                                } else {
                                    println("DEBUG [Delete]: Need WRITE_EXTERNAL_STORAGE permission")
                                    false
                                }
                            }

                            else -> {
                                val deleted = file.delete()
                                println("DEBUG [Delete]: Legacy external storage delete result: $deleted")
                                deleted
                            }
                        }
                    }
                } else {
                    println("DEBUG [Delete]: File doesn't exist at path")
                    false
                }
            } ?: false
        } catch (e: SecurityException) {
            println("DEBUG [Delete]: SecurityException - permission denied")
            false
        } catch (e: Exception) {
            println("DEBUG [Delete]: FileSystem delete error: ${e.message}")
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun deleteViaLegacyMethod(item: LocalDownloadItem): Boolean {
        return try {
            item.filePath?.let { path ->
                val file = File(path)
                if (file.exists() && file.delete()) {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI.also { uri ->
                        requireContext().contentResolver.delete(
                            uri,
                            MediaStore.MediaColumns.DATA + "=?",
                            arrayOf(path)
                        )
                    }
                    true
                } else {
                    false
                }
            } ?: false
        } catch (e: Exception) {
            println("DEBUG [Delete]: Legacy delete error: ${e.message}")
            false
        }
    }
    private fun hasRequiredReadPermissions(): Boolean {
        val result = if (android.os.Build.VERSION.SDK_INT >= 33) {
            val videoPerm = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            val audioPerm = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            println("DEBUG Permissions: READ_MEDIA_VIDEO: $videoPerm, READ_MEDIA_AUDIO: $audioPerm")
            videoPerm && audioPerm
        } else {
            val storagePerm = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            println("DEBUG Permissions: READ_EXTERNAL_STORAGE: $storagePerm")
            storagePerm
        }
        return result
    }

    private fun requestReadPermissions() {
        val perms = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        readPermissionsLauncher.launch(perms)
    }

    @OptIn(UnstableApi::class)
    private fun openLocalFile(item: LocalDownloadItem) {
        val isVideo = item.mimeType?.startsWith("video/") == true
        val playerType = if (isVideo) {
            PlayerSelectionDialog.PlayerType.VIDEO
        } else {
            PlayerSelectionDialog.PlayerType.AUDIO
        }

        PlayerSelectionDialog.Builder(requireContext())
            .setType(playerType)
            .setOnPlayerSelected { player ->
                when (player) {
                    PlayerSelectionDialog.PLAYER_INTERNAL -> {
                        if (isVideo) {
                            VideoPlayerActivity.start(requireContext(), item.uri.toString(), item.displayName)
                        } else {
                            AudioPlayerActivity.start(requireContext(), item.uri.toString(), item.displayName)
                        }
                    }
                    PlayerSelectionDialog.PLAYER_EXTERNAL -> {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.setDataAndType(item.uri, item.mimeType)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { startActivity(Intent.createChooser(intent, requireContext().getString(
                            R.string.open_with))) }
                            .onFailure {
                                requireContext().showToast(it.message ?: requireContext().getString(R.string.error_unknown))
                            }
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter.cancelAllThumbnailLoading()
        LocalDownloadsScanner.ThumbnailCache.clear()
        _binding = null
    }

    private val requestDeletePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // Retry delete
                pendingDeleteItem?.let { item ->
                    deleteFile(item)
                }
            } else {
                requireContext().showToast("Permission denied. Cannot delete file.")
            }
            pendingDeleteItem = null
        }

    private val manageExternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                pendingDeleteItem?.let { item ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val deleted = deleteFileFromStorage(item)
                        if (deleted) {
                            adapter.removeItem(item.id)
                            requireContext().showToast(requireContext().getString(R.string.delete_file_success))

                            if (adapter.itemCount == 0) {
                                binding.tvEmpty.visibility = View.VISIBLE
                                binding.rvDownloads.visibility = View.GONE
                            }
                        } else {
                            requireContext().showToast(requireContext().getString(R.string.delete_file_failed))
                        }
                    }
                }
            } else {
                requireContext().showToast(requireContext().getString(R.string.manage_external_storage_permission_denied))
            }
        }
        pendingDeleteItem = null
    }

    private var pendingDeleteItem: LocalDownloadItem? = null

    private fun checkDeletePermission(item: LocalDownloadItem): Boolean {
        return when {
            // Android 11+ (API 30+) - Scoped Storage
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    true
                } else {
                    pendingDeleteItem = item
                    showManageExternalStoragePermissionDialog(item)
                    false
                }
            }

            // Android 10 (API 29) - Request WRITE_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    true
                } else {
                    pendingDeleteItem = item
                    requestDeletePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    false
                }
            }

            // Android 6-9 (API 23-28) - Request WRITE_EXTERNAL_STORAGE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                if (ContextCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    true
                } else {
                    pendingDeleteItem = item
                    requestDeletePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    false
                }
            }

            // Android < 6 - Permission auto granted
            else -> true
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun showDeleteHelpDialog(item: LocalDownloadItem) {

        AppAlertDialog.Builder(requireContext())
            .setType(AppAlertDialog.AlertDialogType.ERROR)
            .setTitle("Cannot Delete File")
            .setMessage(
                """
            Unable to delete '${item.displayName}'.
            
            Possible reasons:
            1. File is in use by another app
            2. App doesn't have permission to delete from this location
            3. File is read-only
            
            Solution:
            • Delete the file manually from your file manager
            • File location: ${item.filePath ?: "Unknown"}
            """.trimIndent()
            )
            .setPositiveButtonText("Open File Location")
            .setNegativeButtonText("OK")
            .setOnPositiveClick { openManageExternalStorageSettings() }
            .setOnNegativeClick { deleteUsingSAF(item) }
            .show()
    }

    private fun showManageExternalStoragePermissionDialog(item: LocalDownloadItem) {
        AppAlertDialog.Builder(requireContext())
            .setType(AppAlertDialog.AlertDialogType.WARNING)
            .setTitle(getString(R.string.permission_required))
            .setMessage(
                """
            To delete files from the Download folder on Android 11 and above, 
            app requires 'Manage All Files' permission'.
            
            Please grant this permission in system settings.
            """.trimIndent()
            )
            .setPositiveButtonText("Open Settings")
            .setNegativeButtonText("Cancel")
            .setOnPositiveClick {
                openManageExternalStorageSettings()
            }
            .setOnNegativeClick {
                pendingDeleteItem = null
            }
            .show()
    }

    private fun openFileLocation(item: LocalDownloadItem) {
        item.filePath?.let { path ->
            try {
                val file = File(path)
                val parent = file.parentFile

                val intent = Intent(Intent.ACTION_VIEW)
                val uri = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.fileprovider",
                    parent
                )
                intent.setDataAndType(uri, "resource/folder")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(intent)
            } catch (e: Exception) {
                requireContext().showToast(getString(R.string.cannot_open_file_location))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun openManageExternalStorageSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = "package:${requireContext().packageName}".toUri()
            manageExternalStorageLauncher.launch(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            manageExternalStorageLauncher.launch(intent)
        }
    }

    private fun refreshMediaStore(filePath: String?) {
        filePath?.let { path ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaScannerConnection.scanFile(
                    requireContext(),
                    arrayOf(path),
                    null,
                    null
                )
            } else {
                try {
                    requireContext().sendBroadcast(
                        Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(File(path)))
                    )
                } catch (e: Exception) {
                    println("DEBUG [Delete]: Error refreshing MediaStore: ${e.message}")
                }
            }
        }
    }

    private fun deleteUsingSAF(item: LocalDownloadItem) {
        pendingDeleteItem = item

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.putExtra("android.content.extra.SHOW_ADVANCED", true)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

        safDeleteLauncher.launch(intent)
    }

    private val safDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { treeUri ->
                val documentFile = DocumentFile.fromTreeUri(requireContext(), treeUri)
                pendingDeleteItem?.let { item ->
                    deleteWithDocumentFile(documentFile, item)
                }
            }
        } else {
            requireContext().showToast(requireContext().getString(R.string.access_folder_canceled))
        }
        pendingDeleteItem = null
    }

    private fun deleteWithDocumentFile(documentFile: DocumentFile?, item: LocalDownloadItem) {
        if (documentFile == null) {
            requireContext().showToast(getString(R.string.access_folder_canceled))
            return
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val targetFile = documentFile.findFile(item.displayName)
                if (targetFile != null && targetFile.exists()) {
                    val deleted = targetFile.delete()
                    withContext(Dispatchers.Main) {
                        if (deleted) {
                            adapter.removeItem(item.id)
                            requireContext().showToast(getString(R.string.delete_file_success))

                            if (adapter.itemCount == 0) {
                                binding.tvEmpty.visibility = View.VISIBLE
                                binding.rvDownloads.visibility = View.GONE
                            }

                            refreshMediaStore(item.filePath)
                        } else {
                            requireContext().showToast(getString(R.string.delete_file_failed))
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    requireContext().showToast("Error: ${e.message}")
                }
            }
        }
    }
}