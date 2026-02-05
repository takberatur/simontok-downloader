package com.agcforge.videodownloader.utils

enum class PlatformType(val value: String, val displayName: String) {
    YOUTUBE("youtube", "YouTube"),
    INSTAGRAM("instagram", "Instagram"),
    TIKTOK("tiktok", "TikTok"),
    FACEBOOK("facebook", "Facebook"),
    TWITTER("twitter", "Twitter"),
    VIMEO("vimeo", "Vimeo"),
    DAILYMOTION("dailymotion", "Dailymotion"),
    RUMBLE("rumble", "Rumble");

    companion object {
        fun fromString(value: String): PlatformType? {
            return entries.find { it.value.equals(value, ignoreCase = true) }
        }
    }
}