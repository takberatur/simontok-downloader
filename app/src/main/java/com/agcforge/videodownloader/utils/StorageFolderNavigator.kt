package com.agcforge.videodownloader.utils

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract

object StorageFolderNavigator {
	private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

	fun openStorageFolder(context: Context, storageLocation: String) {
		val primaryDocId = when (storageLocation) {
			"downloads" -> "primary:Download"
			else -> "primary:Android/data/${context.packageName}/files/Download"
		}

		if (openDocumentFolder(context, primaryDocId)) return

		if (storageLocation != "downloads") {
			if (openDocumentFolder(context, "primary:Download")) return
		}

		val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		runCatching { context.startActivity(intent) }
	}

	private fun openDocumentFolder(context: Context, documentId: String): Boolean {
		return runCatching {
			val uri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)
			val intent = Intent(Intent.ACTION_VIEW)
			intent.setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			context.startActivity(intent)
			true
		}.getOrDefault(false)
	}
}
