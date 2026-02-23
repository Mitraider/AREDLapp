package com.example.aredlapp.utils

import android.net.Uri

object YouTubeUtils {
    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(url)
            when {
                url.contains("youtu.be/") -> uri.lastPathSegment
                url.contains("youtube.com/watch") -> uri.getQueryParameter("v")
                url.contains("youtube.com/embed/") -> uri.lastPathSegment
                url.contains("youtube.com/v/") -> uri.lastPathSegment
                else -> {
                    val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu.be/|/v/|/e/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%2F|youtu.be%2F|%2Fv%2F)[^#&?\\n]*"
                    val compiledPattern = java.util.regex.Pattern.compile(pattern)
                    val matcher = compiledPattern.matcher(url)
                    if (matcher.find()) matcher.group() else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getThumbnailUrl(videoId: String?): String? {
        return if (videoId != null) "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" else null
    }
}
