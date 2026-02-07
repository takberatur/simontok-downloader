package com.agcforge.videodownloader.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

object MediaStorePublisher {
	fun publishAudioToDownloads(context: Context, sourceFile: File, displayName: String? = null): Uri? {
		if (!sourceFile.exists()) return null
		val resolver = context.contentResolver
		val fileName = (displayName?.takeIf { it.isNotBlank() } ?: sourceFile.name).trim()
		val lower = fileName.lowercase()
		val mimeType = when {
			lower.endsWith(".m4a") -> "audio/mp4"
			lower.endsWith(".aac") -> "audio/aac"
			lower.endsWith(".wav") -> "audio/wav"
			lower.endsWith(".flac") -> "audio/flac"
			lower.endsWith(".ogg") -> "audio/ogg"
			else -> "application/octet-stream"
		}

		val values = ContentValues().apply {
			put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
			put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/VideoDownloader")
				put(MediaStore.MediaColumns.IS_PENDING, 1)
			}
		}

		val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			MediaStore.Downloads.EXTERNAL_CONTENT_URI
		} else {
			MediaStore.Files.getContentUri("external")
		}

		val uri = resolver.insert(collection, values) ?: return null
		try {
			resolver.openOutputStream(uri, "w")?.use { out ->
				FileInputStream(sourceFile).use { input ->
					val buffer = ByteArray(8192)
					var read: Int
					while (input.read(buffer).also { read = it } != -1) {
						out.write(buffer, 0, read)
					}
				}
			} ?: run {
				resolver.delete(uri, null, null)
				return null
			}

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
				resolver.update(uri, done, null, null)
			}
			return uri
		} catch (_: Exception) {
			runCatching { resolver.delete(uri, null, null) }
			return null
		}
	}
}

