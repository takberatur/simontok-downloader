package com.agcforge.videodownloader.utils

import com.agcforge.videodownloader.data.model.Platform
import java.util.regex.Pattern

object UrlValidator {

    enum class PlatformType {
        YOUTUBE,
        FACEBOOK,
        TWITTER_X,
        TIKTOK,
        INSTAGRAM,
        RUMBLE,
        VIMEO,
        DAILYMOTION,
        SNACKVIDEO,
        LINKEDIN,
        BAIDU_VIDEO,
        PINTEREST,
        TWITCH,
        SNAPCHAT,
        ANY_VIDEO_PLATFORM,
        YOUTUBE_TO_MP3,
        FACEBOOK_TO_MP3,
        TWITTER_X_TO_MP3,
        TIKTOK_TO_MP3,
        INSTAGRAM_TO_MP3,
        RUMBLE_TO_MP3,
        VIMEO_TO_MP3,
        DAILYMOTION_TO_MP3,
        SNACKVIDEO_TO_MP3,
        LINKEDIN_TO_MP3,
        BAIDU_VIDEO_TO_MP3,
        PINTEREST_TO_MP3,
        TWITCH_TO_MP3,
        SNAPCHAT_TO_MP3,
    }

    data class ValidatedUrl(
        val platform: PlatformType,
        val url: String,
        val isShortUrl: Boolean = false,
        val videoId: String? = null,
        val reelId: String? = null,
        val clipId: String? = null
    )

    private val patterns = mapOf(
        PlatformType.YOUTUBE to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11})"),
            Pattern.compile("(?:https?://)?(?:www\\.)?youtube\\.com/shorts/([a-zA-Z0-9_-]{11})")
        ),
        PlatformType.FACEBOOK to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?(?:facebook\\.com|fb\\.watch)/(?:reel|watch|video)/(\\d+)"),
            Pattern.compile("(?:https?://)?(?:www\\.)?fb\\.com/(?:reel|watch|video)/(\\d+)")
        ),
        PlatformType.TWITTER_X to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?(?:twitter\\.com|x\\.com)/\\w+/status/(\\d+)"),
            Pattern.compile("(?:https?://)?(?:www\\.)?t\\.co/[a-zA-Z0-9]+")
        ),
        PlatformType.TIKTOK to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?tiktok\\.com/@[^/]+/video/(\\d+)"),
            Pattern.compile("(?:https?://)?(?:www\\.)?vm\\.tiktok\\.com/[a-zA-Z0-9]+"),
            Pattern.compile("(?:https?://)?(?:www\\.)?vt\\.tiktok\\.com/[a-zA-Z0-9]+")
        ),
        PlatformType.INSTAGRAM to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?instagram\\.com/(?:reel|p|reels)/([a-zA-Z0-9_-]+)/?"),
            Pattern.compile("(?:https?://)?(?:www\\.)?instagr\\.am/(?:reel|p|reels)/([a-zA-Z0-9_-]+)/?")
        ),
        PlatformType.RUMBLE to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?rumble\\.com/(?:video-)?([a-zA-Z0-9_-]+)\\.html")
        ),
        PlatformType.VIMEO to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?vimeo\\.com/(\\d+)"),
            Pattern.compile("(?:https?://)?(?:www\\.)?vimeo\\.com/[^/]+/(\\d+)")
        ),
        PlatformType.DAILYMOTION to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?dailymotion\\.com/(?:video|embed)/([a-zA-Z0-9]+)"),
            Pattern.compile("(?:https?://)?(?:www\\.)?dai\\.ly/([a-zA-Z0-9]+)")
        ),
        PlatformType.SNACKVIDEO to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?snackvideo\\.com/@[^/]+/video/(\\d+)"),
            Pattern.compile("(?:https?://)?(?:www\\.)?sck\\.io/[a-zA-Z0-9]+")
        ),
        PlatformType.LINKEDIN to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?linkedin\\.com/(?:posts|pulse)/[^/]+/activity-(\\d+)-[a-zA-Z0-9]+")
        ),
        PlatformType.BAIDU_VIDEO to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?sv\\.baidu\\.com/v\\?vid=(\\d+)")
        ),
        PlatformType.PINTEREST to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?pinterest\\.(?:com|fr|de|it|es|ru|jp)/(?:pin|pin/[^/]+)/(\\d+)"),
            Pattern.compile("(?:https?://)?(?:www\\.)?pin\\.it/([a-zA-Z0-9]+)")
        ),
        PlatformType.TWITCH to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?twitch\\.tv/[^/]+/clip/([a-zA-Z0-9_-]+)"),
            Pattern.compile("(?:https?://)?(?:www\\.)?clips\\.twitch\\.tv/([a-zA-Z0-9_-]+)")
        ),
        PlatformType.SNAPCHAT to listOf(
            Pattern.compile("(?:https?://)?(?:www\\.)?snapchat\\.com/add/[^/]+/spotlight/([a-zA-Z0-9_-]+)"),
            Pattern.compile("(?:https?://)?(?:www\\.)?snapchat\\.com/spotlight/([a-zA-Z0-9_-]+)")
        )
    )

    private val shortUrlDomains = setOf(
        "youtu.be",
        "fb.watch",
        "fb.com",
        "t.co",
        "vm.tiktok.com",
        "vt.tiktok.com",
        "instagr.am",
        "dai.ly",
        "sck.io",
        "pin.it",
        "clips.twitch.tv"
    )

    fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    fun detectUrl(url: String): ValidatedUrl? {
        val cleanUrl = url.trim()

        for ((platform, patternList) in patterns) {
            for (pattern in patternList) {
                val matcher = pattern.matcher(cleanUrl)
                if (matcher.find()) {
                    val videoId = matcher.group(1) ?: continue
                    val isShortUrl = isShortUrl(cleanUrl)

                    return when (platform) {
                        PlatformType.YOUTUBE -> ValidatedUrl(
                            platform = PlatformType.YOUTUBE,
                            url = cleanUrl,
                            isShortUrl = isShortUrl,
                            videoId = videoId
                        )
                        PlatformType.FACEBOOK -> ValidatedUrl(
                            platform = PlatformType.FACEBOOK,
                            url = cleanUrl,
                            isShortUrl = isShortUrl,
                            reelId = videoId
                        )
                        PlatformType.TIKTOK -> ValidatedUrl(
                            platform = PlatformType.TIKTOK,
                            url = cleanUrl,
                            isShortUrl = isShortUrl,
                            videoId = videoId
                        )
                        PlatformType.INSTAGRAM -> ValidatedUrl(
                            platform = PlatformType.INSTAGRAM,
                            url = cleanUrl,
                            isShortUrl = isShortUrl,
                            reelId = videoId
                        )
                        PlatformType.TWITCH -> ValidatedUrl(
                            platform = PlatformType.TWITCH,
                            url = cleanUrl,
                            isShortUrl = isShortUrl,
                            clipId = videoId
                        )
                        else -> ValidatedUrl(
                            platform = platform,
                            url = cleanUrl,
                            isShortUrl = isShortUrl,
                            videoId = videoId
                        )
                    }
                }
            }
        }

        return null
    }

    private fun isShortUrl(url: String): Boolean {
        return shortUrlDomains.any { domain ->
            url.contains(domain, ignoreCase = true)
        }
    }

    fun extractUrlsFromText(text: String): List<ValidatedUrl> {
        val urlPattern = Pattern.compile(
            "(https?://)?(www\\.)?([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(/[^\\s]*)?"
        )

        val matcher = urlPattern.matcher(text)
        val detectedUrls = mutableListOf<ValidatedUrl>()

        while (matcher.find()) {
            val url = matcher.group()
            detectUrl(url)?.let {
                detectedUrls.add(it)
            }
        }

        return detectedUrls
    }

    fun getPlatformName(platform: PlatformType): String {
        return when (platform) {
            PlatformType.YOUTUBE -> "YouTube"
            PlatformType.FACEBOOK -> "Facebook"
            PlatformType.TWITTER_X -> "X (Twitter)"
            PlatformType.TIKTOK -> "TikTok"
            PlatformType.INSTAGRAM -> "Instagram"
            PlatformType.RUMBLE -> "Rumble"
            PlatformType.VIMEO -> "Vimeo"
            PlatformType.DAILYMOTION -> "Dailymotion"
            PlatformType.SNACKVIDEO -> "SnackVideo"
            PlatformType.LINKEDIN -> "LinkedIn"
            PlatformType.BAIDU_VIDEO -> "Baidu Video"
            PlatformType.PINTEREST -> "Pinterest"
            PlatformType.TWITCH -> "Twitch"
            PlatformType.SNAPCHAT -> "Snapchat"
            PlatformType.ANY_VIDEO_PLATFORM -> "Any Video Platform"
            else -> {
                "Any Video Platform"
            }
        }
    }

    fun normalizeUrl(detectedUrl: ValidatedUrl): String {
        return when (detectedUrl.platform) {
            PlatformType.YOUTUBE -> {
                if (detectedUrl.isShortUrl) {
                    "https://youtube.com/watch?v=${detectedUrl.videoId}"
                } else {
                    detectedUrl.url
                }
            }
            PlatformType.INSTAGRAM -> {
                if (detectedUrl.isShortUrl) {
                    "https://instagram.com/reel/${detectedUrl.reelId}"
                } else {
                    detectedUrl.url
                }
            }
            PlatformType.TIKTOK -> {
                if (detectedUrl.isShortUrl) {
                    "https://tiktok.com/@username/video/${detectedUrl.videoId}"
                } else {
                    detectedUrl.url
                }
            }
            // Tambahkan platform lainnya sesuai kebutuhan
            else -> detectedUrl.url
        }
    }
}
