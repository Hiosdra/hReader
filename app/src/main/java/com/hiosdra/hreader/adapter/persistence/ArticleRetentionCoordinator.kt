package com.hiosdra.hreader.adapter.persistence

import android.util.Log
import com.hiosdra.hreader.core.application.port.out.ArticleRetentionStore
import java.time.Duration
import java.time.Instant

private val READ_ARTICLE_RETENTION = Duration.ofDays(30)

internal class ArticleRetentionCoordinator(
    private val retention: ArticleRetentionStore
) {
    suspend fun pruneExpiredReadArticles() {
        val removed = retention.deleteReadArticlesBefore(Instant.now().minus(READ_ARTICLE_RETENTION))
        if (removed > 0) Log.d(TAG, "Pruned $removed read articles past the retention window")
    }

    private companion object {
        const val TAG = "ArticleRetentionCoordinator"
    }
}
