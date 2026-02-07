package com.agcforge.videodownloader.helper.converter

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Conversion Manager
 *
 * Manages multiple video to audio conversions with queue system
 *
 * Features:
 * - Queue management
 * - Progress tracking for all conversions
 * - Pause/Resume support
 * - Cancel support
 * - Conversion history
 *
 * Usage:
 * ```
 * val manager = ConversionManager(context)
 *
 * // Add conversion to queue
 * val taskId = manager.addToQueue(
 *     sourceVideoPath = "/path/to/video.mp4",
 *     config = ConversionConfig(...)
 * )
 *
 * // Observe progress
 * manager.conversionState.collect { state ->
 *     when (state) {
 *         is ConversionState.Converting -> {
 *             Log.d("Progress", "${state.progress}%")
 *         }
 *         is ConversionState.Completed -> {
 *             Log.d("Success", state.outputPath)
 *         }
 *     }
 * }
 * ```
 */
class ConversionManager(private val context: Context) {

    private val TAG = "ConversionManager"

    private val scope = CoroutineScope(Dispatchers.IO)
    @SuppressLint("UnsafeOptInUsageError")
    private val converter = VideoToAudioConverter(context)

    // Conversion queue
    private val conversionQueue = mutableListOf<ConversionTask>()
    private var currentTask: ConversionTask? = null
    private var currentJob: Job? = null

    // State flow for UI observation
    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    // Conversion history
    private val _conversionHistory = MutableStateFlow<List<ConversionHistoryItem>>(emptyList())
    val conversionHistory: StateFlow<List<ConversionHistoryItem>> = _conversionHistory.asStateFlow()

    /**
     * Conversion task
     */
    data class ConversionTask @OptIn(UnstableApi::class) constructor
        (
        val id: String = UUID.randomUUID().toString(),
        val sourceVideoPath: String,
        val config: AdvancedVideoToAudioConverter.ConversionConfig,
        val priority: Int = 0
    )

    /**
     * Conversion state
     */
    sealed class ConversionState {
        object Idle : ConversionState()
        data class Queued(val queueSize: Int) : ConversionState()
        data class Converting(
            val taskId: String,
            val sourceVideoPath: String,
            val progress: Int
        ) : ConversionState()
        data class Completed(
            val taskId: String,
            val outputPath: String,
            val durationMs: Long
        ) : ConversionState()
        data class Failed(
            val taskId: String,
            val error: String
        ) : ConversionState()
        data class Cancelled(val taskId: String) : ConversionState()
    }

    /**
     * Conversion history item
     */
    data class ConversionHistoryItem(
        val id: String,
        val sourceVideoPath: String,
        val outputPath: String?,
        val success: Boolean,
        val error: String?,
        val timestamp: Long,
        val durationMs: Long
    )

    /**
     * Add conversion to queue
     */
    @UnstableApi
    fun addToQueue(
        sourceVideoPath: String,
        config: AdvancedVideoToAudioConverter.ConversionConfig,
        priority: Int = 0
    ): String {

        val task = ConversionTask(
            sourceVideoPath = sourceVideoPath,
            config = config,
            priority = priority
        )

        synchronized(conversionQueue) {
            conversionQueue.add(task)
            // Sort by priority (higher priority first)
            conversionQueue.sortByDescending { it.priority }
        }

        _conversionState.value = ConversionState.Queued(conversionQueue.size)

        Log.d(TAG, "Task ${task.id} added to queue. Queue size: ${conversionQueue.size}")

        // Start processing if not already converting
        if (currentTask == null) {
            processNextTask()
        }

        return task.id
    }

    /**
     * Add multiple conversions to queue
     */
    @UnstableApi
    fun addBatchToQueue(
        sourceVideoPaths: List<String>,
        config: AdvancedVideoToAudioConverter.ConversionConfig,
        priority: Int = 0
    ): List<String> {

        val taskIds = sourceVideoPaths.map { videoPath ->
            val task = ConversionTask(
                sourceVideoPath = videoPath,
                config = config,
                priority = priority
            )

            synchronized(conversionQueue) {
                conversionQueue.add(task)
            }

            task.id
        }

        synchronized(conversionQueue) {
            conversionQueue.sortByDescending { it.priority }
        }

        _conversionState.value = ConversionState.Queued(conversionQueue.size)

        Log.d(TAG, "${taskIds.size} tasks added to queue")

        if (currentTask == null) {
            processNextTask()
        }

        return taskIds
    }

    /**
     * Process next task in queue
     */
    @UnstableApi
    private fun processNextTask() {
        synchronized(conversionQueue) {
            if (conversionQueue.isEmpty()) {
                currentTask = null
                _conversionState.value = ConversionState.Idle
                Log.d(TAG, "Queue empty, idle")
                return
            }

            currentTask = conversionQueue.removeAt(0)
        }

        val task = currentTask ?: return

        Log.d(TAG, "Processing task ${task.id}: ${task.sourceVideoPath}")

        currentJob = scope.launch {
            try {
                val startTime = System.currentTimeMillis()

                // Convert video
                converter.convertVideoToAudio(
                    sourceVideoPath = task.sourceVideoPath,
                    outputFormat = task.config.outputFormat,
                    audioQuality = task.config.audioQuality,
                    outputFileName = task.config.customFileName,
                    onProgress = { progress ->
                        _conversionState.value = ConversionState.Converting(
                            taskId = task.id,
                            sourceVideoPath = task.sourceVideoPath,
                            progress = progress
                        )
                    },
                    onSuccess = { outputPath ->
                        val endTime = System.currentTimeMillis()
                        val duration = endTime - startTime

                        Log.d(TAG, "Task ${task.id} completed: $outputPath")

                        _conversionState.value = ConversionState.Completed(
                            taskId = task.id,
                            outputPath = outputPath,
                            durationMs = duration
                        )

                        // Add to history
                        addToHistory(
                            ConversionHistoryItem(
                                id = task.id,
                                sourceVideoPath = task.sourceVideoPath,
                                outputPath = outputPath,
                                success = true,
                                error = null,
                                timestamp = System.currentTimeMillis(),
                                durationMs = duration
                            )
                        )

                        // Process next task
                        processNextTask()
                    },
                    onError = { error ->
                        val endTime = System.currentTimeMillis()
                        val duration = endTime - startTime

                        Log.e(TAG, "Task ${task.id} failed: $error")

                        _conversionState.value = ConversionState.Failed(
                            taskId = task.id,
                            error = error
                        )

                        // Add to history
                        addToHistory(
                            ConversionHistoryItem(
                                id = task.id,
                                sourceVideoPath = task.sourceVideoPath,
                                outputPath = null,
                                success = false,
                                error = error,
                                timestamp = System.currentTimeMillis(),
                                durationMs = duration
                            )
                        )

                        // Process next task
                        processNextTask()
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error processing task: ${e.message}", e)

                _conversionState.value = ConversionState.Failed(
                    taskId = task.id,
                    error = e.message ?: "Unknown error"
                )

                processNextTask()
            }
        }
    }

    /**
     * Cancel specific task
     */
    @UnstableApi
    fun cancelTask(taskId: String): Boolean {
        // Check if it's the current task
        if (currentTask?.id == taskId) {
            currentJob?.cancel()
            converter.cancelConversion()

            _conversionState.value = ConversionState.Cancelled(taskId)

            currentTask = null
            processNextTask()
            return true
        }

        // Remove from queue
        synchronized(conversionQueue) {
            val removed = conversionQueue.removeIf { it.id == taskId }
            if (removed) {
                _conversionState.value = ConversionState.Queued(conversionQueue.size)
            }
            return removed
        }
    }

    /**
     * Cancel all tasks
     */
    @UnstableApi
    fun cancelAll() {
        // Cancel current task
        currentJob?.cancel()
        converter.cancelConversion()
        currentTask = null

        // Clear queue
        synchronized(conversionQueue) {
            conversionQueue.clear()
        }

        _conversionState.value = ConversionState.Idle

        Log.d(TAG, "All tasks cancelled")
    }

    /**
     * Get queue size
     */
    fun getQueueSize(): Int {
        synchronized(conversionQueue) {
            return conversionQueue.size
        }
    }

    /**
     * Get current task
     */
    fun getCurrentTask(): ConversionTask? = currentTask

    /**
     * Get all queued tasks
     */
    fun getQueuedTasks(): List<ConversionTask> {
        synchronized(conversionQueue) {
            return conversionQueue.toList()
        }
    }

    /**
     * Add item to history
     */
    private fun addToHistory(item: ConversionHistoryItem) {
        val currentHistory = _conversionHistory.value.toMutableList()
        currentHistory.add(0, item) // Add to beginning

        // Keep only last 50 items
        if (currentHistory.size > 50) {
            currentHistory.removeAt(currentHistory.size - 1)
        }

        _conversionHistory.value = currentHistory
    }

    /**
     * Clear history
     */
    fun clearHistory() {
        _conversionHistory.value = emptyList()
    }

    /**
     * Release resources
     */
    @UnstableApi
    fun release() {
        cancelAll()
        converter.release()
    }
}