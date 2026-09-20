package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleContentDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRecordDao
import com.hiosdra.hreader.core.application.port.out.ArticleAiOverviewStore
import com.hiosdra.hreader.core.application.port.out.CacheMaintenanceStore
import com.hiosdra.hreader.core.application.port.out.CredibilityStore

internal class CacheMaintenanceRepository(
    private val articleRecordDao: ArticleRecordDao,
    private val articleContentDao: ArticleContentDao,
    private val credibilityStore: CredibilityStore,
    private val articleAiOverviewStore: ArticleAiOverviewStore,
    private val imageMaintenance: ArticleImageMaintenance,
    private val pageMaintenance: ArticlePageCacheMaintenance
) : CacheMaintenanceStore {
    override suspend fun maintain() {
        val currentEntryIds = articleRecordDao.getAllIds().mapNotNull { it.toLongOrNull() }.toHashSet()
        credibilityStore.cleanupOrphanedReports(currentEntryIds)
        articleAiOverviewStore.cleanupOrphaned(currentEntryIds)

        while (true) {
            val orphanedContent = articleContentDao.getOrphanedEntryIds(DELETE_CHUNK)
            if (orphanedContent.isEmpty()) break
            articleContentDao.deleteArticlesContent(orphanedContent)
        }

        imageMaintenance.cleanupOrphaned()
        pageMaintenance.cleanupOrphaned()
        imageMaintenance.enforceBudget()
        imageMaintenance.repairFiles()
        pageMaintenance.removeTemporaryFiles()
    }

    private companion object {
        const val DELETE_CHUNK = 500
    }
}
