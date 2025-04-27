package com.hiosdra.hreader.data.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ContentSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {
    private val repository: ArticleRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            repository.refreshArticles()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
