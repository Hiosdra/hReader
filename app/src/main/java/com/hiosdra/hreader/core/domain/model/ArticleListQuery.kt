package com.hiosdra.hreader.core.domain.model

import java.time.Instant

data class ArticleListQuery(
    val feedId: Long? = null,
    val includeRead: Boolean = false,
    val searchQuery: String = "",
    val sessionStart: Instant = Instant.now()
) {
    fun withFeed(feedId: Long?, now: Instant): ArticleListQuery =
        if (feedId == this.feedId) this else copy(feedId = feedId, sessionStart = now)

    fun withIncludeRead(includeRead: Boolean): ArticleListQuery =
        if (includeRead == this.includeRead) this else copy(includeRead = includeRead)

    fun withSearch(searchQuery: String): ArticleListQuery =
        if (searchQuery == this.searchQuery) this else copy(searchQuery = searchQuery)

    fun withSessionRestarted(now: Instant): ArticleListQuery =
        if (includeRead) this else copy(sessionStart = now)
}
