package com.hiosdra.hreader.data.model

import java.time.Instant

/**
 * Everything that decides which articles a list shows, in one value.
 *
 * [sessionStart] is when the reader opened the list. Articles read after it stay on screen even
 * with [includeRead] off, so a row does not vanish from under the finger that just ticked it —
 * the list is read a page at a time now, and there is no longer a full list in memory to hold one
 * back by hand.
 */
data class ArticleListQuery(
    val feedId: Long? = null,
    val starredOnly: Boolean = false,
    val includeRead: Boolean = false,
    val searchQuery: String = "",
    val sessionStart: Instant = Instant.now()
) {
    /**
     * Switching feed starts a new visit, so articles read in the previous one stop being held on
     * screen. Asking for the same feed again changes nothing at all — re-stamping [sessionStart]
     * there would rebuild the list every time the screen was recomposed with the same argument.
     */
    fun withFeed(feedId: Long?, now: Instant): ArticleListQuery =
        if (feedId == this.feedId) this else copy(feedId = feedId, sessionStart = now)

    fun withStarredOnly(starredOnly: Boolean, now: Instant): ArticleListQuery =
        if (starredOnly == this.starredOnly) this else copy(starredOnly = starredOnly, sessionStart = now)

    /**
     * Showing read articles does not restart the visit: the reader is widening what is on screen,
     * and nothing that was visible should move.
     */
    fun withIncludeRead(includeRead: Boolean): ArticleListQuery =
        if (includeRead == this.includeRead) this else copy(includeRead = includeRead)

    fun withSearch(searchQuery: String): ArticleListQuery =
        if (searchQuery == this.searchQuery) this else copy(searchQuery = searchQuery)
}
