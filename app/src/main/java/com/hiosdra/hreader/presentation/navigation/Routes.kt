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
    const val TRAVEL_MODE = "travel_mode"
    const val FEED = "feed/{feedId}"
    const val ADD_FEED = "add_feed?url={url}"

    /**
     * The reader is told which list to open and where in it to start, not what the list contains.
     * It used to carry every article id inline: a few thousand cached articles made a route string
     * tens of kilobytes long, which then travels into the saved instance state on every rotation.
     */
    const val ARTICLE = "article?feedId={feedId}&startId={startId}" +
        "&includeRead={includeRead}&session={session}"
    const val SETTINGS = "settings"
    const val SYNC_HEALTH = "settings/sync-health"
    const val TTS_SETTINGS = "settings/tts"

    /** [url] is a site or feed address shared into the app; the argument is optional without it. */
    fun addFeed(url: String? = null): String =
        if (url.isNullOrBlank()) "add_feed" else "add_feed?url=${Uri.encode(url)}"

    /**
     * [sessionStartMillis] is when the list was opened. The reader has to page through exactly the
     * articles the list was showing, and that includes the ones read during this visit, which are
     * on screen only because they were read after that moment.
     */
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
