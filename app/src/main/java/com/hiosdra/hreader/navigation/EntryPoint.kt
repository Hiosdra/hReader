package com.hiosdra.hreader.navigation

/**
 * What the app was opened to do. A launcher tap means the article list; a shared link or the
 * launcher shortcut means subscribing, and skipping the list on the way there is the point.
 */
sealed interface EntryPoint {
    data object ArticleList : EntryPoint

    /** [url] is the address that was shared in, or null when the shortcut was used. */
    data class AddFeed(val url: String?) : EntryPoint
}
