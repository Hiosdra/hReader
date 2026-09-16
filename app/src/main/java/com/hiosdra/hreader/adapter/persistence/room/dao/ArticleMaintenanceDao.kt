package com.hiosdra.hreader.adapter.persistence.room.dao

import androidx.room.Dao
import androidx.room.Query
import com.hiosdra.hreader.adapter.persistence.room.entity.AiOverviewPrefetchTarget
import com.hiosdra.hreader.adapter.persistence.room.entity.ArticleBody
import com.hiosdra.hreader.adapter.persistence.room.entity.PrefetchTarget
import com.hiosdra.hreader.core.domain.model.ArticleStatus

@Dao
interface ArticleMaintenanceDao {
    @Query("SELECT id, content FROM articles WHERE preview IS NULL AND content IS NOT NULL LIMIT :limit")
    suspend fun getArticlesMissingPreview(limit: Int): List<ArticleBody>

    @Query("UPDATE articles SET preview = :preview WHERE id = :id")
    suspend fun setPreview(id: String, preview: String)

    @Query(
        "SELECT id, url, enclosures FROM articles " +
            "WHERE (status IS NULL OR status != :readStatus) " +
            "OR backlogFetchedAt IS NOT NULL " +
            "ORDER BY CASE WHEN (status IS NULL OR status != :readStatus) THEN 0 " +
            "ELSE 1 END, publishedAt DESC, id DESC"
    )
    suspend fun getPrefetchTargets(readStatus: ArticleStatus = ArticleStatus.READ): List<PrefetchTarget>

    @Query(
        "SELECT a.id, a.url, a.enclosures FROM articles a " +
            "LEFT JOIN article_contents c ON c.entryId = CAST(a.id AS INTEGER) " +
            "WHERE ((a.status IS NULL OR a.status != :readStatus) OR a.backlogFetchedAt IS NOT NULL) " +
            "AND (c.entryId IS NULL OR c.source != 'FULL' OR (:downloadAllImages = 1 AND c.allImagesPrepared = 0)) " +
            "ORDER BY CASE WHEN (a.status IS NULL OR a.status != :readStatus) THEN 0 ELSE 1 END, " +
            "a.publishedAt DESC, a.id DESC LIMIT :limit"
    )
    suspend fun getPrefetchTargetsMissingContent(
        limit: Int,
        downloadAllImages: Boolean,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<PrefetchTarget>

    @Query(
        "SELECT id, url, enclosures FROM articles " +
            "WHERE ((status IS NULL OR status != :readStatus) OR backlogFetchedAt IS NOT NULL) " +
            "AND enclosures != '' " +
            "ORDER BY CASE WHEN (status IS NULL OR status != :readStatus) THEN 0 ELSE 1 END, " +
            "publishedAt DESC, id DESC LIMIT :limit"
    )
    suspend fun getPrefetchTargetsWithEnclosures(
        limit: Int,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<PrefetchTarget>

    @Query(
        "SELECT a.id, a.url, a.enclosures FROM articles a " +
            "LEFT JOIN article_page_snapshots p ON p.entryId = CAST(a.id AS INTEGER) " +
            "AND p.originalUrl = a.url AND p.isComplete = 1 " +
            "WHERE ((a.status IS NULL OR a.status != :readStatus) OR a.backlogFetchedAt IS NOT NULL) " +
            "AND p.entryId IS NULL " +
            "ORDER BY CASE WHEN (a.status IS NULL OR a.status != :readStatus) THEN 0 ELSE 1 END, " +
            "a.publishedAt DESC, a.id DESC LIMIT :limit"
    )
    suspend fun getPrefetchTargetsMissingPages(
        limit: Int,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<PrefetchTarget>

    @Query(
        "SELECT COUNT(*) FROM articles a " +
            "LEFT JOIN article_page_snapshots p ON p.entryId = CAST(a.id AS INTEGER) " +
            "AND p.originalUrl = a.url AND p.isComplete = 1 " +
            "WHERE ((a.status IS NULL OR a.status != :readStatus) OR a.backlogFetchedAt IS NOT NULL) " +
            "AND p.entryId IS NULL"
    )
    suspend fun countPrefetchTargetsMissingPages(readStatus: ArticleStatus = ArticleStatus.READ): Int

    @Query(
        "SELECT a.id AS id, a.title AS title, a.url AS url " +
            "FROM articles a INNER JOIN feeds f ON f.id = a.feedId " +
            "WHERE f.preloadAiOverview = 1 " +
            "AND ((a.status IS NULL OR a.status != :readStatus) OR a.backlogFetchedAt IS NOT NULL) " +
            "ORDER BY CASE WHEN (a.status IS NULL OR a.status != :readStatus) THEN 0 ELSE 1 END, " +
            "a.publishedAt DESC, a.id DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getAiOverviewPrefetchTargets(
        limit: Int,
        offset: Int,
        readStatus: ArticleStatus = ArticleStatus.READ
    ): List<AiOverviewPrefetchTarget>
}
