package com.hiosdra.hreader.core.application.port.out

import java.time.Instant

interface ArticleRetentionStore {
    suspend fun deleteReadArticlesBefore(before: Instant): Int
}
