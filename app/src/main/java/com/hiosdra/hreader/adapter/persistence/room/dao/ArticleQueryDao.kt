package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleListItem
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleReaderItem
import com.hiosdra.hreader.core.domain.model.ArticleStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

private const val LIST_COLUMNS =
    "a.id AS id, a.title AS title, a.author AS author, a.url AS url, " +
        "a.publishedAt AS publishedAt, a.preview AS preview, a.readingTime AS readingTime, " +
        "a.leadImageUrl AS leadImageUrl, a.status AS status, " +
        "a.backlogFetchedAt AS backlogFetchedAt, a.feedId AS feedId, " +
        "f.title AS feedTitle, f.siteUrl AS feedSiteUrl, f.feedUrl AS feedUrl, " +
        "f.autoMarkRead AS feedAutoMarkRead"

private const val FROM_ARTICLES_WITH_FEED = "FROM articles a LEFT JOIN feeds f ON f.id = a.feedId"
private const val FROM_ARTICLES = "FROM articles a"
private const val SESSION_VISIBILITY_FILTER =
    "(:includeRead = 1 OR (a.status IS NULL OR a.status != :readStatus) " +
        "OR (a.readAt IS NOT NULL AND a.readAt >= :sessionStart))"
private const val VISIBILITY_FILTER = "(:feedId IS NULL OR a.feedId = :feedId) AND $SESSION_VISIBILITY_FILTER"
private const val FEED_VISIBILITY_FILTER = "a.feedId = :feedId AND $SESSION_VISIBILITY_FILTER"
private const val LIST_ORDER = "ORDER BY a.publishedAt ASC, a.id ASC"
private const val MATCHES_SEARCH =
    "a.rowid IN (SELECT rowid FROM articles_fts WHERE articles_fts MATCH :ftsQuery " +
        "UNION SELECT matchedArticles.rowid FROM articles matchedArticles " +
        "WHERE matchedArticles.feedId IN (SELECT matchedFeeds.id FROM feeds matchedFeeds " +
        "WHERE LOWER(matchedFeeds.title) LIKE :titleQuery))"

data class ArticleCountsRow(
    val unreadCount: Int,
    val readCount: Int
)

@Dao
interface ArticleQueryDao {
    @Query("SELECT $LIST_COLUMNS $FROM_ARTICLES_WITH_FEED WHERE $SESSION_VISIBILITY_FILTER $LIST_ORDER")
    fun pageAllArticles(
        includeRead: Boolean,
        sessionStart: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): PagingSource<Int, ArticleListItem>

    @Query(
        "SELECT $LIST_COLUMNS $FROM_ARTICLES_WITH_FEED " +
            "WHERE $FEED_VISIBILITY_FILTER $LIST_ORDER"
    )
    fun pageFeedArticles(
        feedId: Long,
        includeRead: Boolean,
        sessionStart: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): PagingSource<Int, ArticleListItem>

    @Query(
        "SELECT $LIST_COLUMNS $FROM_ARTICLES_WITH_FEED " +
            "WHERE $SESSION_VISIBILITY_FILTER AND $MATCHES_SEARCH $LIST_ORDER"
    )
    fun pageSearchResultsAcrossFeeds(
        includeRead: Boolean,
        sessionStart: Instant,
        ftsQuery: String,
        titleQuery: String,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): PagingSource<Int, ArticleListItem>

    @Query(
        "SELECT $LIST_COLUMNS $FROM_ARTICLES_WITH_FEED " +
            "WHERE $FEED_VISIBILITY_FILTER AND $MATCHES_SEARCH $LIST_ORDER"
    )
    fun pageSearchResultsInFeed(
        feedId: Long,
        includeRead: Boolean,
        sessionStart: Instant,
        ftsQuery: String,
        titleQuery: String,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): PagingSource<Int, ArticleListItem>

    @Query("SELECT COUNT(*) $FROM_ARTICLES WHERE $VISIBILITY_FILTER")
    suspend fun countList(
        feedId: Long?,
        includeRead: Boolean,
        sessionStart: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Int

    @Query(
        "SELECT COUNT(*) $FROM_ARTICLES WHERE $VISIBILITY_FILTER " +
            "AND (a.publishedAt < :publishedAt OR " +
            "(a.publishedAt = :publishedAt AND a.id < :articleId))"
    )
    suspend fun countArticlesBefore(
        articleId: String,
        publishedAt: Instant,
        feedId: Long?,
        includeRead: Boolean,
        sessionStart: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Int

    @Query(
        "SELECT COUNT(*) $FROM_ARTICLES WHERE $VISIBILITY_FILTER " +
            "AND a.id = :articleId"
    )
    suspend fun countVisibleArticle(
        articleId: String,
        feedId: Long?,
        includeRead: Boolean,
        sessionStart: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Int

    @Query("SELECT publishedAt FROM articles WHERE id = :articleId")
    suspend fun getPublishedAt(articleId: String): Instant?

    @Query(
        "SELECT a.id $FROM_ARTICLES WHERE $VISIBILITY_FILTER " +
            "$LIST_ORDER LIMIT :limit OFFSET :offset"
    )
    suspend fun getListWindow(
        feedId: Long?,
        includeRead: Boolean,
        sessionStart: Instant,
        limit: Int,
        offset: Int,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<String>

    @Query(
        "SELECT a.id $FROM_ARTICLES WHERE $VISIBILITY_FILTER " +
            "AND (a.publishedAt < :publishedAt OR " +
            "(a.publishedAt = :publishedAt AND a.id < :articleId)) " +
            "ORDER BY a.publishedAt DESC, a.id DESC LIMIT :limit"
    )
    suspend fun getListWindowBefore(
        articleId: String,
        publishedAt: Instant,
        feedId: Long?,
        includeRead: Boolean,
        sessionStart: Instant,
        limit: Int,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<String>

    @Query(
        "SELECT a.id $FROM_ARTICLES WHERE $VISIBILITY_FILTER " +
            "AND (a.publishedAt > :publishedAt OR " +
            "(a.publishedAt = :publishedAt AND a.id > :articleId)) " +
            "ORDER BY a.publishedAt ASC, a.id ASC LIMIT :limit"
    )
    suspend fun getListWindowAfter(
        articleId: String,
        publishedAt: Instant,
        feedId: Long?,
        includeRead: Boolean,
        sessionStart: Instant,
        limit: Int,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<String>

    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN a.status IS NULL OR a.status != :readStatus THEN 1 ELSE 0 END), 0) " +
            "AS unreadCount, " +
            "COALESCE(SUM(CASE WHEN a.status = :readStatus THEN 1 ELSE 0 END), 0) AS readCount " +
            "FROM articles a"
    )
    fun observeCountsAcrossFeeds(
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Flow<ArticleCountsRow>

    @Query(
        "SELECT " +
            "COALESCE(SUM(CASE WHEN a.status IS NULL OR a.status != :readStatus THEN 1 ELSE 0 END), 0) " +
            "AS unreadCount, " +
            "COALESCE(SUM(CASE WHEN a.status = :readStatus THEN 1 ELSE 0 END), 0) AS readCount " +
            "FROM articles a WHERE a.feedId = :feedId"
    )
    fun observeCountsInFeed(
        feedId: Long,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Flow<ArticleCountsRow>

    @Query(
        "SELECT a.id AS id, a.title AS title, a.author AS author, a.url AS url, " +
            "a.publishedAt AS publishedAt, a.preview AS preview, a.readingTime AS readingTime, " +
            "a.enclosures AS enclosures, a.status AS status, " +
            "a.backlogFetchedAt AS backlogFetchedAt, a.feedId AS feedId, " +
            "f.title AS feedTitle, f.siteUrl AS feedSiteUrl, f.feedUrl AS feedUrl, " +
            "f.autoMarkRead AS feedAutoMarkRead " +
            "$FROM_ARTICLES_WITH_FEED " +
            "WHERE a.id IN (:ids) ORDER BY a.publishedAt ASC, a.id ASC"
    )
    fun getArticlesWithFeedByIds(ids: List<String>): Flow<List<ArticleReaderItem>>
}
