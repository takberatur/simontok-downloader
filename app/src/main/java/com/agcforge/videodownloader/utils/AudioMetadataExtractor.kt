package com.agcforge.videodownloader.utils

import android.content.Context
import android.net.Uri
import com.agcforge.videodownloader.data.model.AudioMetadata
import java.io.File

class AudioMetadataExtractor {
    companion object {
        fun extractMetadata(context: Context, audioUri: Uri): AudioMetadata {
            val retriever = android.media.MediaMetadataRetriever()

            try {
                retriever.setDataSource(context, audioUri)

                val title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: getFileNameFromUri(context, audioUri)

                val artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: "Unknown Artist"

                val album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?: "Unknown Album"

                val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L

                val albumArt = retriever.embeddedPicture

                return AudioMetadata(title, artist, album, duration, albumArt)

            } catch (e: Exception) {
                return AudioMetadata(
                    getFileNameFromUri(context, audioUri),
                    "Unknown Artist",
                    "Unknown Album",
                    0L,
                    null
                )
            } finally {
                retriever.release()
            }
        }

        private fun getFileNameFromUri(context: Context, uri: Uri): String {
            var fileName = "Unknown"

            if (uri.scheme == "file") {
                fileName = File(uri.path ?: "").name
            } else {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            fileName = cursor.getString(displayNameIndex)
                        }
                    }
                }
            }

            return fileName.replace(".mp3", "").replace(".m4a", "")
        }
    }
}