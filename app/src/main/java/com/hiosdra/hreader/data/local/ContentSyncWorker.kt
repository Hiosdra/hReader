package com.hiosdra.hreader.data.local

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ContentSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params), KoinComponent {
    private val repository: ArticleRepository by inject()

    companion object {
        private const val TAG = "ContentSyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting ContentSyncWorker")

        return try {
            Log.d(TAG, "Refreshing articles from remote source")
            repository.refreshArticles()
            Log.i(TAG, "ContentSyncWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ContentSyncWorker failed: ${e.message}", e)
            Result.retry()
        }
    }
}
