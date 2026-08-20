package com.hiosdra.hreader.presentation.navigation

import android.net.Uri

object Routes {
    const val FEED_ID_NONE: Long = -1L

    const val SERVER_SETUP = "server_setup"
    const val MAIN = "main"
    const val FEED = "feed/{feedId}"
    const val ADD_FEED = "add_feed?url={url}"

    /**
     * The reader is told which list to open and where in it to start, not what the list contains.
     * It used to carry every article id inline: a few thousand cached articles made a route string
     * tens of kilobytes long, which then travels into the saved instance state on every rotation.
     */
    const val ARTICLE = "article?feedId={feedId}&startId={startId}&starred={starred}" +
        "&includeRead={includeRead}&session={session}"
    const val SETTINGS = "settings"
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
        starredOnly: Boolean,
        includeRead: Boolean,
        sessionStartMillis: Long
    ): String = "article?feedId=${feedId ?: FEED_ID_NONE}&startId=$startArticleId" +
        "&starred=$starredOnly&includeRead=$includeRead&session=$sessionStartMillis"

    fun feed(feedId: Long): String = "feed/$feedId"
}
