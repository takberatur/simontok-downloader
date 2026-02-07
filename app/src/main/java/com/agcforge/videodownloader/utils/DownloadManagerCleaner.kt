package com.agcforge.videodownloader.utils

import android.app.DownloadManager
import android.content.Context

object DownloadManagerCleaner {
	fun clearFailedDownloads(context: Context): Int {
		return runCatching {
			val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
			val query = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_FAILED)
			val ids = ArrayList<Long>()
			dm.query(query)?.use { cursor ->
				val idCol = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
				while (cursor.moveToNext()) {
					ids.add(cursor.getLong(idCol))
				}
			}
			if (ids.isNotEmpty()) {
				dm.remove(*ids.toLongArray())
			}
			ids.size
		}.getOrDefault(0)
	}
}
