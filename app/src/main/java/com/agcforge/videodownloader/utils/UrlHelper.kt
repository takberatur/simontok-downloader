package com.agcforge.videodownloader.utils

import com.agcforge.videodownloader.data.model.Platform
import java.net.URLEncoder
import java.util.regex.Pattern

object UrlHelper {

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

    /**
     * Search engines
     */
    enum class SearchEngine(val baseUrl: String) {
        GOOGLE("https://www.google.com/search?q="),
        BING("https://www.bing.com/search?q="),
        DUCKDUCKGO("https://duckduckgo.com/?q="),
        YAHOO("https://search.yahoo.com/search?p="),
        YANDEX("https://yandex.com/search/?text=")
    }

    /**
     * Check if text is a valid URL
     */
    fun isValidUrl(text: String): Boolean {
        val urlPattern = Pattern.compile(
            "^(https?://)?" + // Protocol (optional)
                    "((([a-zA-Z0-9\\-]+\\.)+[a-zA-Z]{2,})|" + // Domain name
                    "(\\d{1,3}\\.){3}\\d{1,3})" + // OR IP address
                    "(:\\d+)?" + // Port (optional)
                    "(/.*)?$" // Path (optional)
        )
        return urlPattern.matcher(text).matches()
    }

    /**
     * Check if text looks like a domain (has dot, no spaces)
     */
    fun isLikelyDomain(text: String): Boolean {
        return text.contains(".") &&
                !text.contains(" ") &&
                !text.startsWith(".") &&
                !text.endsWith(".")
    }

    /**
     * Process input - return URL or search query URL
     */
    fun processInput(
        input: String,
        searchEngine: SearchEngine = SearchEngine.GOOGLE
    ): String {
        return when {
            // Already has protocol
            input.startsWith("http://", ignoreCase = true) ||
                    input.startsWith("https://", ignoreCase = true) -> {
                input
            }

            // Looks like a domain
            isLikelyDomain(input) -> {
                "https://$input"
            }

            // Special cases - known domains without TLD
            input.equals("localhost", ignoreCase = true) -> {
                "http://localhost"
            }

            // Otherwise, search
            else -> {
                getSearchUrl(input, searchEngine)
            }
        }
    }

    /**
     * Get search URL for query
     */
    fun getSearchUrl(query: String, searchEngine: SearchEngine = SearchEngine.GOOGLE): String {
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        return "${searchEngine.baseUrl}$encodedQuery"
    }

    /**
     * Extract domain from URL
     */
    fun extractDomain(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            uri.host
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if URL is HTTPS
     */
    fun isSecure(url: String): Boolean {
        return url.startsWith("https://", ignoreCase = true)
    }

    /**
     * Convert HTTP to HTTPS
     */
    fun forceHttps(url: String): String {
        return if (url.startsWith("http://", ignoreCase = true)) {
            url.replaceFirst("http://", "https://", ignoreCase = true)
        } else {
            url
        }
    }

    /**
     * Check if URL is a search engine result
     */
    fun isSearchEngineUrl(url: String): Boolean {
        val domain = extractDomain(url)?.lowercase() ?: return false
        return domain.contains("google.com") ||
                domain.contains("bing.com") ||
                domain.contains("duckduckgo.com") ||
                domain.contains("yahoo.com") ||
                domain.contains("yandex.com")
    }

    /**
     * Clean URL - remove tracking parameters
     */
    fun cleanUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            val cleanQuery = uri.query?.split("&")
                ?.filter { param ->
                    // Remove tracking parameters
                    !param.startsWith("utm_") &&
                            !param.startsWith("fbclid=") &&
                            !param.startsWith("gclid=")
                }
                ?.joinToString("&")

            val builder = StringBuilder()
            builder.append(uri.scheme).append("://")
            builder.append(uri.host)

            if (uri.port != -1) {
                builder.append(":").append(uri.port)
            }

            if (uri.path != null) {
                builder.append(uri.path)
            }

            if (!cleanQuery.isNullOrEmpty()) {
                builder.append("?").append(cleanQuery)
            }

            builder.toString()

        } catch (e: Exception) {
            url
        }
    }
}
