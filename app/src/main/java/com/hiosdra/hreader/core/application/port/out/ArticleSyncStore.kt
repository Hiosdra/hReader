package com.hiosdra.hreader.core.application.port.out

import com.hiosdra.hreader.core.application.sync.ArticleSyncResult

interface ArticleSyncStore {
    suspend fun refreshArticles(forceFullSync: Boolean = false): ArticleSyncResult
}
