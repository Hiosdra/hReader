package com.hiosdra.hreader.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.hiosdra.hreader.data.local.entity.ArticleBody
import com.hiosdra.hreader.data.local.entity.ArticleEntity
import com.hiosdra.hreader.data.local.entity.ArticleListItem
import com.hiosdra.hreader.data.local.entity.ArticleWithFeed
import com.hiosdra.hreader.data.local.entity.FeedUnreadCount
import com.hiosdra.hreader.data.local.entity.PendingStar
import com.hiosdra.hreader.data.local.entity.PendingStatus
import com.hiosdra.hreader.data.local.entity.PrefetchTarget
import com.hiosdra.hreader.data.model.ArticleStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

private const val LIST_COLUMNS =
    "a.id AS id, a.title AS title, a.author AS author, a.url AS url, " +
        "a.publishedAt AS publishedAt, a.preview AS preview, a.readingTime AS readingTime, " +
        "a.enclosures AS enclosures, a.status AS status, a.starred AS starred, " +
        "a.backlogFetchedAt AS backlogFetchedAt, a.feedId AS feedId, " +
        "f.title AS feedTitle, f.siteUrl AS feedSiteUrl, f.feedUrl AS feedUrl"

private const val FROM_ARTICLES_WITH_FEED = "FROM articles a LEFT JOIN feeds f ON f.id = a.feedId"

/**
 * Which articles the list shows.
 *
 * The last clause is what keeps an article on screen after it is ticked read: [sessionStart] is
 * when the reader opened this list, and anything read since then stays put. It used to be a set of
 * ids held in the view model, which no longer works once the list is read a page at a time — and a
 * changing id set would invalidate the query on every tick, rebuilding the list under the finger.
 */
private const val VISIBILITY_FILTER =
    "(:feedId IS NULL OR a.feedId = :feedId) " +
        "AND (:starredOnly = 0 OR a.starred = 1) " +
        "AND (:includeRead = 1 OR (a.status IS NULL OR a.status != :readStatus) " +
        "OR (a.readAt IS NOT NULL AND a.readAt >= :sessionStart))"

/**
 * Publication dates are not unique — feeds stamp a whole batch with one time, and an entry that
 * carries no date at all lands on the epoch. Paging reads by offset, so without a tiebreaker two
 * articles sharing a date can swap places between one page and the next, which shows one of them
 * twice and drops the other. The id is arbitrary but total.
 */
private const val LIST_ORDER = "ORDER BY a.publishedAt ASC, a.id ASC"

private const val MATCHES_SEARCH =
    "(a.rowid IN (SELECT rowid FROM articles_fts WHERE articles_fts MATCH :ftsQuery) " +
        "OR LOWER(f.title) LIKE :titleQuery)"

@Dao
interface ArticleDao {
    /**
     * One statement for the whole list, feed included. The previous version read full entities and
     * then looked the feed up per row, which was a query per article on every emission — and held
     * every cached article body in memory to show a title and a preview.
     */
    @Query(
        "SELECT $LIST_COLUMNS $FROM_ARTICLES_WITH_FEED " +
            "WHERE $VISIBILITY_FILTER $LIST_ORDER"
    )
    fun pageArticles(
        feedId: Long?,
        starredOnly: Boolean,
        includeRead: Boolean,
        sessionStart: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): PagingSource<Int, ArticleListItem>

    /**
     * [ftsQuery] runs against the full-text index over titles, authors and bodies; [titleQuery] is
     * a plain LIKE so a search also matches the name of the feed, which is not an article column.
     */
    @Query(
        "SELECT $LIST_COLUMNS $FROM_ARTICLES_WITH_FEED " +
            "WHERE $VISIBILITY_FILTER AND $MATCHES_SEARCH $LIST_ORDER"
    )
    fun pageSearchResults(
        feedId: Long?,
        starredOnly: Boolean,
        includeRead: Boolean,
        sessionStart: Instant,
        ftsQuery: String,
        titleQuery: String,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): PagingSource<Int, ArticleListItem>

    /**
     * The ids of the same list, in the same order, for the reader to page through. Loading the rows
     * would pull down every column of every article to use one of them.
     */
    @Query(
        "SELECT a.id $FROM_ARTICLES_WITH_FEED WHERE $VISIBILITY_FILTER $LIST_ORDER"
    )
    suspend fun getListIds(
        feedId: Long?,
        starredOnly: Boolean,
        includeRead: Boolean,
        sessionStart: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<String>

    @Query(
        "SELECT a.id $FROM_ARTICLES_WITH_FEED " +
            "WHERE (:feedId IS NULL OR a.feedId = :feedId) " +
            "AND (:starredOnly = 0 OR a.starred = 1) " +
            "AND (a.status IS NULL OR a.status != :readStatus)"
    )
    suspend fun getUnreadIds(
        feedId: Long?,
        starredOnly: Boolean,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<String>

    /**
     * Counted in SQLite rather than over the loaded page: the list no longer holds every article,
     * and the unread total is about the whole list rather than about what is on screen.
     */
    @Query(
        "SELECT COUNT(*) FROM articles a " +
            "WHERE (:feedId IS NULL OR a.feedId = :feedId) " +
            "AND (:starredOnly = 0 OR a.starred = 1) " +
            "AND (a.status IS NULL OR a.status != :readStatus)"
    )
    fun observeUnreadCountFor(
        feedId: Long?,
        starredOnly: Boolean,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM articles a " +
            "WHERE (:feedId IS NULL OR a.feedId = :feedId) " +
            "AND (:starredOnly = 0 OR a.starred = 1) AND a.status = :readStatus"
    )
    fun observeReadCountFor(
        feedId: Long?,
        starredOnly: Boolean,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Flow<Int>

    @Query(
        "SELECT $LIST_COLUMNS, a.content AS content $FROM_ARTICLES_WITH_FEED " +
            "WHERE a.id IN (:ids) ORDER BY a.publishedAt ASC"
    )
    fun getArticlesWithFeedByIds(ids: List<String>): Flow<List<ArticleWithFeed>>

    @Query(
        "SELECT id, content FROM articles WHERE preview IS NULL AND content IS NOT NULL LIMIT :limit"
    )
    suspend fun getArticlesMissingPreview(limit: Int): List<ArticleBody>

    @Query("UPDATE articles SET preview = :preview WHERE id = :id")
    suspend fun setPreview(id: String, preview: String)

    @Query("UPDATE articles SET fullContent = :content WHERE id = :id")
    suspend fun setFullContent(id: String, content: String)

    /**
     * Upsert rather than insert-or-replace. REPLACE resolves a conflict by deleting the old row and
     * inserting a new one, which hands the article a new rowid — and `articles_fts` is an external
     * content index keyed on that rowid. SQLite only fires delete triggers for a REPLACE when
     * recursive triggers are on, which Room does not enable, so every re-synced article used to
     * leave its old index entry behind pointing at a rowid that no longer exists. Search then
     * matched articles that no longer contained the term and missed ones that did.
     */
    @Upsert
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("DELETE FROM articles")
    suspend fun clearAll()

    /**
     * The only way to change a status: it always queues the change for the backend. [readAt] is
     * the moment the article was read, or null when it is being marked unread again. An article
     * that was already read keeps its original read time — "mark all as read" sweeps over entries
     * that are already read, and restamping them would keep pushing back their retention.
     */
    @Query(
        "UPDATE articles SET status = :status, pendingSync = 1, " +
            "readAt = CASE WHEN :readAt IS NULL THEN NULL ELSE COALESCE(readAt, :readAt) END " +
            "WHERE id IN (:ids)"
    )
    suspend fun updateStatusForIds(ids: List<String>, status: ArticleStatus, readAt: Instant?)

    /**
     * Dequeues only rows still holding the status that was pushed. If the user flipped the status
     * again while the request was in flight, the newer value stays queued instead of being lost.
     */
    @Query("UPDATE articles SET pendingSync = 0 WHERE id IN (:ids) AND status = :pushedStatus")
    suspend fun clearPendingSync(ids: List<String>, pushedStatus: ArticleStatus)

    @Query("SELECT id, status FROM articles WHERE pendingSync = 1")
    suspend fun getPendingStatuses(): List<PendingStatus>

    /**
     * Of [ids], the ones still read and read no later than [readBefore]. Undoing a bulk "mark as
     * read" uses it to leave alone anything the reader has read since — every article the action
     * touched carries its timestamp, and one read afterwards carries a later one.
     */
    @Query(
        "SELECT id FROM articles WHERE id IN (:ids) AND status = :readStatus " +
            "AND readAt IS NOT NULL AND readAt <= :readBefore"
    )
    suspend fun getIdsReadNoLaterThan(
        ids: List<String>,
        readBefore: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<String>

    @Query("UPDATE articles SET starred = :starred, starredPendingSync = 1 WHERE id IN (:ids)")
    suspend fun updateStarredForIds(ids: List<String>, starred: Boolean)

    @Query("UPDATE articles SET starredPendingSync = 0 WHERE id IN (:ids) AND starred = :pushedStarred")
    suspend fun clearStarredPendingSync(ids: List<String>, pushedStarred: Boolean)

    @Query("SELECT id, starred FROM articles WHERE starredPendingSync = 1")
    suspend fun getPendingStars(): List<PendingStar>

    /**
     * Backlog articles are excluded: they were downloaded to stock up for a trip, not because the
     * backend returned them as unread, so the reconciliation that drops "no longer returned" rows
     * would delete every one of them on the next full sync. Starred articles are excluded for the
     * same reason — a star is a request to keep the article around.
     */
    @Query(
        "SELECT id FROM articles WHERE (status IS NULL OR status != :readStatus) " +
            "AND pendingSync = 0 AND starredPendingSync = 0 " +
            "AND backlogFetchedAt IS NULL AND starred = 0"
    )
    suspend fun getSyncedUnreadIds(readStatus: ArticleStatus = ArticleStatus.READ): List<String>

    @Query("DELETE FROM articles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM articles WHERE feedId = :feedId")
    suspend fun deleteByFeedId(feedId: Long)

    /** A starred article is kept past its retention window: the star is what asks for that. */
    @Query(
        "DELETE FROM articles WHERE status = :readStatus AND pendingSync = 0 " +
            "AND starredPendingSync = 0 AND starred = 0 " +
            "AND readAt IS NOT NULL AND readAt < :readBefore"
    )
    suspend fun deleteArticlesReadBefore(
        readBefore: Instant,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): Int

    /**
     * Read backlog articles are prefetched too. They exist precisely to be read without a
     * connection, and their status says nothing about whether they have been read on this device.
     */
    @Query(
        "SELECT id, url, enclosures FROM articles " +
            "WHERE (status IS NULL OR status != :readStatus) " +
            "OR backlogFetchedAt IS NOT NULL OR starred = 1"
    )
    suspend fun getPrefetchTargets(readStatus: ArticleStatus = ArticleStatus.READ): List<PrefetchTarget>

    @Query("SELECT * FROM articles WHERE id IN (:ids)")
    suspend fun getArticlesImmediate(ids: List<String>): List<ArticleEntity>

    @Query("SELECT id FROM articles")
    suspend fun getAllIds(): List<String>

    @Query("SELECT COUNT(*) FROM articles")
    fun observeArticleCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE status IS NULL OR status != :readStatus")
    fun observeUnreadCount(readStatus: ArticleStatus = ArticleStatus.READ): Flow<Int>

    @Query("SELECT COUNT(*) FROM articles WHERE backlogFetchedAt IS NOT NULL")
    fun observeBacklogCount(): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM articles WHERE (status IS NULL OR status != 'READ') " +
            "OR backlogFetchedAt IS NOT NULL OR starred = 1"
    )
    fun observeOfflineTargetCount(): Flow<Int>

    @Query(
        "SELECT feedId, COUNT(*) AS unreadCount FROM articles " +
            "WHERE status IS NULL OR status != :readStatus GROUP BY feedId"
    )
    fun observeUnreadCountsPerFeed(readStatus: ArticleStatus = ArticleStatus.READ): Flow<List<FeedUnreadCount>>
}
