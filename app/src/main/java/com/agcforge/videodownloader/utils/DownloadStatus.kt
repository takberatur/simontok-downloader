package com.agcforge.videodownloader.utils

enum class DownloadStatus(val value: String) {
    PENDING("pending"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
    DOWNLOADING("downloading");

    companion object {
        fun fromString(value: String): DownloadStatus {
            return entries.find { it.value == value } ?: PENDING
        }
    }
}