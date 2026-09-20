package com.hiosdra.hreader.adapter.persistence

import com.hiosdra.hreader.adapter.persistence.room.dao.ArticlePageSnapshotDao
import com.hiosdra.hreader.adapter.persistence.room.dao.ArticleRecordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ArticlePageCacheMaintenance(
    private val snapshotDao: ArticlePageSnapshotDao,
    private val articleRecordDao: ArticleRecordDao,
    private val files: ArticlePageFileStore
) {
    suspend fun cleanupOrphaned() = withContext(Dispatchers.IO) {
        val referencedDirectories = mutableSetOf<String>()
        var afterEntryId = Long.MIN_VALUE
        while (true) {
            val snapshots = snapshotDao.getBatch(afterEntryId, DELETE_CHUNK)
            if (snapshots.isEmpty()) break
            val currentEntryIds = articleRecordDao.getExistingIds(snapshots.map { it.entryId.toString() })
                .mapNotNull(String::toLongOrNull)
                .toHashSet()
            val entriesToDelete = mutableListOf<Long>()
            snapshots.forEach { snapshot ->
                val directory = files.pageDirectory(snapshot.entryId, snapshot.directoryPath)
                val hasIndex = directory?.resolve(ArticlePageFiles.INDEX_FILE)?.isFile == true
                when {
                    !hasIndex && !files.hasTemporaryPageDirectory(snapshot.entryId) -> {
                        directory?.deleteRecursively()
                        entriesToDelete += snapshot.entryId
                    }
                    !hasIndex -> {
                        runCatching { directory?.canonicalPath }
                            .getOrNull()
                            ?.let(referencedDirectories::add)
                    }
                    hasIndex && snapshot.entryId in currentEntryIds -> {
                        runCatching { directory.canonicalPath }
                            .getOrNull()
                            ?.let(referencedDirectories::add)
                    }
                    hasIndex -> {
                        directory.deleteRecursively()
                        entriesToDelete += snapshot.entryId
                    }
                }
            }
            if (entriesToDelete.isNotEmpty()) snapshotDao.deleteForEntries(entriesToDelete)
            afterEntryId = snapshots.last().entryId
        }

        files.files()
            .filterNot { file ->
                files.isTemporary(file) ||
                    runCatching { file.canonicalPath in referencedDirectories }.getOrDefault(true)
            }
            .forEach { it.deleteRecursively() }
    }

    suspend fun removeTemporaryFiles() = withContext(Dispatchers.IO) {
        files.files()
            .filter(files::isTemporary)
            .forEach { it.deleteRecursively() }
    }

    private companion object {
        const val DELETE_CHUNK = 500
    }
}
