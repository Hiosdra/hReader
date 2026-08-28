package com.hiosdra.hreader.core.application.port.out

interface ArticleSyncStore {
    suspend fun refreshArticles(forceFullSync: Boolean = false)
}
