package com.hiosdra.hreader.presentation.navigation

import android.net.Uri
import android.os.Bundle

data class ArticleRouteArguments(
    val feedId: Long?,
    val startArticleId: Long,
    val includeRead: Boolean,
    val sessionStartMillis: Long
)

object Routes {
    const val FEED_ID_NONE: Long = -1L

    const val SERVER_SETUP = "server_setup"
    const val MAIN = "main"
    const val FEED = "feed/{feedId}"
    const val ADD_FEED = "add_feed?url={url}"

    const val ARTICLE = "article?feedId={feedId}&startId={startId}" +
        "&includeRead={includeRead}&session={session}"
    const val SETTINGS = "settings"
    const val SYNC_HEALTH = "settings/sync-health"
    const val TTS_SETTINGS = "settings/tts"
    const val LICENSES = "settings/licenses"

    fun addFeed(url: String? = null): String =
        if (url.isNullOrBlank()) "add_feed" else "add_feed?url=${Uri.encode(url)}"

    fun article(
        feedId: Long?,
        startArticleId: Long,
        includeRead: Boolean,
        sessionStartMillis: Long
    ): String = article(
        ArticleRouteArguments(
            feedId = feedId,
            startArticleId = startArticleId,
            includeRead = includeRead,
            sessionStartMillis = sessionStartMillis
        )
    )

    fun article(arguments: ArticleRouteArguments): String =
        "article?feedId=${arguments.feedId ?: FEED_ID_NONE}&startId=${arguments.startArticleId}" +
            "&includeRead=${arguments.includeRead}&session=${arguments.sessionStartMillis}"

    fun feed(feedId: Long): String = "feed/$feedId"
}

fun Bundle?.toArticleRouteArguments(): ArticleRouteArguments? {
    val arguments = this ?: return null
    val startArticleId = arguments.longOrNull("startId") ?: return null
    if (startArticleId <= 0L) return null
    val feedId = when {
        !arguments.containsKey("feedId") -> null
        arguments.longOrNull("feedId") == null -> return null
        else -> arguments.longOrNull("feedId")?.takeIf { it != Routes.FEED_ID_NONE }
    }
    val includeRead = arguments.booleanOrNull("includeRead")
        ?: if (arguments.containsKey("includeRead")) return null else false
    val sessionStartMillis = arguments.longOrNull("session")
        ?: if (arguments.containsKey("session")) return null else 0L
    return ArticleRouteArguments(
        feedId = feedId,
        startArticleId = startArticleId,
        includeRead = includeRead,
        sessionStartMillis = sessionStartMillis
    )
}

private fun Bundle.longOrNull(key: String): Long? =
    if (!containsKey(key)) null else runCatching { get(key) as? Long }.getOrNull()

private fun Bundle.booleanOrNull(key: String): Boolean? =
    if (!containsKey(key)) null else runCatching { get(key) as? Boolean }.getOrNull()
